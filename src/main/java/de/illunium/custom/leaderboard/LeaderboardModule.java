package de.illunium.custom.leaderboard;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import de.illunium.custom.playtime.PlaytimeReadService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

public final class LeaderboardModule implements FeatureModule, BasicCommand {
    private final PlaytimeReadService playtime;
    private VanillaStatisticReader vanilla;
    private ModuleContext context;
    private LeaderboardRepository repository;
    private CustomMetricTracker tracker;
    private BukkitTask timer;
    private long refreshAt;
    private int cacheSeconds;
    private boolean active, refreshing;
    private Queue<UUID> candidates;
    private UUID current;
    private VanillaStatisticReader.Cursor cursor;
    private Map<UUID, String> names;
    private Map<LeaderboardDefinition, List<RankingSnapshot.Value>> building;
    private Map<LeaderboardDefinition, RankingSnapshot> snapshots = Map.of();

    public LeaderboardModule(PlaytimeReadService playtime) { this.playtime = playtime; }
    @Override public String id() { return "leaderboard"; }

    @Override public void enable(ModuleContext context) {
        Object seconds = context.settings().get("cache-seconds", 60);
        if (!(seconds instanceof Integer n) || n < 30 || n > 3600) throw new IllegalArgumentException("leaderboard.cache-seconds must be 30..3600");
        cacheSeconds = n;
        this.context = context;
        vanilla = new VanillaStatisticReader();
        repository = new LeaderboardRepository(context.database());
        tracker = new CustomMetricTracker(repository, context.plugin().getLogger());
        context.plugin().getServer().getOnlinePlayers().forEach(tracker::peak);
        context.plugin().getServer().getPluginManager().registerEvents(tracker, context.plugin());
        context.plugin().registerCommand("leaderboard", "Survival-Ranglisten", List.of("lb"), this);
        active = true;
        timer = context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), this::tick, 1, 1);
    }

    private void tick() {
        if (context.plugin().getServer().getCurrentTick() % 100 == 0) tracker.flush();
        if (!refreshing && System.nanoTime() >= refreshAt) {
            try { refresh(); } catch (RuntimeException e) { failed(e); }
        }
        if (candidates == null) return;
        try {
            if (cursor == null) {
                current = candidates.poll();
                if (current == null) { finish(); return; }
                cursor = vanilla.cursor(context.plugin().getServer().getOfflinePlayer(current));
            }
            if (cursor.step()) {
                cursor.values().forEach((definition, value) -> add(definition, current, value));
                cursor = null;
            }
        } catch (RuntimeException e) {
            context.plugin().getLogger().log(Level.WARNING, "Could not read statistics for " + current, e);
            cursor = null;
        }
    }

    private void refresh() {
        refreshing = true;
        tracker.flush();
        names = new HashMap<>(context.identities().knownPlayers());
        for (OfflinePlayer player : context.plugin().getServer().getOfflinePlayers()) {
            if (player.hasPlayedBefore()) names.putIfAbsent(player.getUniqueId(), name(player));
        }
        context.plugin().getServer().getOnlinePlayers().forEach(p -> names.put(p.getUniqueId(), p.getName()));
        var metrics = repository.load();
        var times = playtime.available() ? playtime.snapshot() : CompletableFuture.completedFuture(Map.<UUID, Long>of());
        metrics.thenCombine(times, (custom, totals) -> new Sources(custom, totals)).whenCompleteAsync((sources, error) -> {
            if (!active) return;
            if (error != null) { failed(error); return; }
            building = new EnumMap<>(LeaderboardDefinition.class);
            for (var definition : LeaderboardDefinition.values()) building.put(definition, new ArrayList<>());
            sources.custom.forEach((id, value) -> {
                names.putIfAbsent(id, shortId(id));
                add(LeaderboardDefinition.PLACED, id, value.placed()); add(LeaderboardDefinition.TOTEMS, id, value.totems());
                add(LeaderboardDefinition.SPAWN, id, value.spawn()); add(LeaderboardDefinition.PEAKLEVEL, id, value.peak());
            });
            sources.times.forEach((id, value) -> { names.putIfAbsent(id, shortId(id)); add(LeaderboardDefinition.PLAYTIME, id, value); });
            candidates = new ArrayDeque<>(names.keySet());
        }, context.mainThread());
    }

    private record Sources(Map<UUID, LeaderboardRepository.Metrics> custom, Map<UUID, Long> times) { }
    private void add(LeaderboardDefinition d, UUID id, long value) {
        building.get(d).add(new RankingSnapshot.Value(id, names.getOrDefault(id, shortId(id)), value));
    }
    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static String name(OfflinePlayer p) { return p.getName() == null ? shortId(p.getUniqueId()) : p.getName(); }

    private void finish() {
        candidates = null;
        Map<LeaderboardDefinition, List<RankingSnapshot.Value>> numbers = Map.copyOf(building);
        building = null;
        CompletableFuture.supplyAsync(() -> {
            Map<LeaderboardDefinition, RankingSnapshot> result = new EnumMap<>(LeaderboardDefinition.class);
            numbers.forEach((d, values) -> result.put(d, new RankingSnapshot(values)));
            return Map.copyOf(result);
        }).whenCompleteAsync((result, error) -> {
            if (!active) return;
            if (error != null) { failed(error); return; }
            snapshots = result; refreshing = false;
            refreshAt = System.nanoTime() + cacheSeconds * 1_000_000_000L;
        }, context.mainThread());
    }
    private void failed(Throwable error) {
        context.plugin().getLogger().log(Level.SEVERE, "Leaderboard refresh failed; keeping previous snapshot", error);
        refreshing = false; candidates = null; cursor = null;
        refreshAt = System.nanoTime() + cacheSeconds * 1_000_000_000L;
    }
    @Override public void disable() {
        active = false;
        if (timer != null) timer.cancel();
        if (tracker != null) {
            HandlerList.unregisterAll(tracker);
            context.plugin().getServer().getOnlinePlayers().forEach(tracker::peak);
            tracker.close();
        }
        snapshots = Map.of(); candidates = null; cursor = null; building = null;
    }
    private Set<UUID> excluded() {
        Set<UUID> ids = context.plugin().getServer().getOperators().stream().map(OfflinePlayer::getUniqueId).collect(Collectors.toSet());
        ids.addAll(context.visibility().hidden());
        return ids;
    }
    private List<RankingSnapshot.Row> rows(LeaderboardDefinition d) {
        return snapshots.getOrDefault(d, new RankingSnapshot(List.of())).excluding(excluded());
    }
    static int page(String input, int pages) {
        try {
            int page = Integer.parseInt(input);
            if (page >= 1 && page <= pages) return page;
        } catch (NumberFormatException ignored) { }
        throw new UserException("Bitte eine Seite von 1 bis " + pages + " wählen.");
    }
    @Override public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (!sender.hasPermission(permission())) { sender.sendMessage(Chat.error("Keine Berechtigung.")); return; }
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Chat.info("Illunium » Survival-Leaderboards"));
            for (var d : LeaderboardDefinition.values()) sender.sendMessage(Chat.info(d.title + " — /leaderboard " + d.key()
                    + (d == LeaderboardDefinition.PLAYTIME && !playtime.available() ? " (nicht verfügbar)" : ""))
                    .clickEvent(ClickEvent.runCommand("/leaderboard " + d.key())));
            return;
        }
        var definition = LeaderboardDefinition.find(args[0]);
        if (definition == null || args.length > 2) { sender.sendMessage(Chat.error("Verfügbare Kategorien: " + Arrays.stream(LeaderboardDefinition.values()).map(LeaderboardDefinition::key).collect(Collectors.joining(", ")))); return; }
        if (definition == LeaderboardDefinition.PLAYTIME && !playtime.available()) { sender.sendMessage(Chat.info("Spielzeit nicht verfügbar: Playtime-Modul deaktiviert.")); return; }
        try {
            List<RankingSnapshot.Row> rows = rows(definition);
            int pages = Math.max(1, (rows.size() + 9) / 10), page = page(args.length == 2 ? args[1] : "1", pages);
            sender.sendMessage(Chat.info("Illunium » Leaderboard: " + definition.title));
            sender.sendMessage(Chat.info(definition.history + " · Cache, keine Livewerte"));
            if (snapshots.isEmpty()) sender.sendMessage(Chat.info("Statistiken werden vorbereitet. Bitte später erneut aufrufen."));
            else if (rows.isEmpty()) sender.sendMessage(Chat.info("Noch keine Einträge."));
            rows.stream().skip((page - 1L) * 10).limit(10).forEach(r -> sender.sendMessage(Chat.info("#" + r.rank() + "  " + r.name() + " — " + definition.format(r.value()))));
            sender.sendMessage(Chat.info("Seite " + page + "/" + pages + (page < pages ? " · /leaderboard " + definition.key() + " " + (page + 1) : "")));
            if (sender instanceof Player player) {
                var own = rows.stream().filter(r -> r.player().equals(player.getUniqueId())).findFirst();
                sender.sendMessage(Chat.info(excluded().contains(player.getUniqueId()) ? "OP-Konten nehmen nicht am öffentlichen Ranking teil."
                        : own.map(r -> "Dein Platz: #" + r.rank() + " — " + definition.format(r.value())).orElse("Du hast noch keinen Eintrag")));
            }
        } catch (UserException e) { sender.sendMessage(Chat.error(e.getMessage())); }
    }
    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(permission())) return List.of();
        if (args.length <= 1) return Chat.complete(args, java.util.stream.Stream.concat(java.util.stream.Stream.of("list"), Arrays.stream(LeaderboardDefinition.values()).map(LeaderboardDefinition::key)));
        var d = LeaderboardDefinition.find(args[0]);
        return args.length == 2 && d != null ? Chat.complete(args, IntStream.rangeClosed(1, Math.max(1, (rows(d).size() + 9) / 10)).mapToObj(Integer::toString)) : List.of();
    }
    @Override public String permission() { return "illuniumcustom.use"; }
}
