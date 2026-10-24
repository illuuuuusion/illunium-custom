package de.illunium.custom.whiteliststaging;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/** No login hook: only an explicit launch/retry may grant access via the native whitelist. */
public final class WhitelistStagingModule implements FeatureModule, BasicCommand {
    private ModuleContext context;
    private WhitelistStageRepository repository;
    private ProfileResolver resolver;
    private NativeWhitelistGateway gateway;
    private boolean active, busy;
    @Override public String id() { return "whiteliststaging"; }
    @Override public void enable(ModuleContext context) {
        this.context = context;
        repository = new WhitelistStageRepository(context.database());
        resolver = new ProfileResolver(context.plugin().getServer(), context.mainThread());
        gateway = new NativeWhitelistGateway(context.plugin().getServer());
        active = true; busy = true;
        // Recovery acknowledges native successes only. Missing entries require an explicit retry; startup grants no access.
        repository.entries().thenComposeAsync(entries -> recover(entries.stream()
                .filter(e -> e.status().equals("APPLYING")).toList(), 0), context.mainThread()).whenCompleteAsync((v, error) -> {
            if (error != null) context.plugin().getLogger().log(Level.SEVERE, "Whitelist recovery failed; staging locked until restart", error);
            else busy = false;
        }, context.mainThread());
        context.admin().register("whitelist", this);
    }
    private CompletableFuture<Void> recover(List<WhitelistStageRepository.Entry> entries, int index) {
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("Module disabled"));
        if (index == entries.size()) return repository.finishInterrupted();
        var entry = entries.get(index);
        boolean present = gateway.verified(entry.profile());
        return repository.result(entry, entry.profile(), present ? "APPLIED" : "FAILED",
                present ? null : "Launch unterbrochen; native Freigabe fehlt. /ic whitelist retry", "RECOVERY")
                .thenComposeAsync(v -> recover(entries, index + 1), context.mainThread());
    }
    @Override public void disable() { active = false; if (resolver != null) resolver.close(); }
    static boolean allowed(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof Player player && player.isOp();
    }
    private void check(CommandSender sender) {
        if (!active) throw new UserException("Whitelist-Staging deaktiviert.");
        if (!allowed(sender)) throw new UserException("Nur Vanilla-OPs und die Serverkonsole dürfen das.");
    }
    private void idle() { if (busy) throw new UserException("Whitelist-Launch/Recovery läuft bereits; /ic whitelist status."); }
    private static String actor(CommandSender sender) { return sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE"; }
    private void audit(String actor, VerifiedProfile p, String action, String result) {
        context.plugin().getLogger().info("Whitelist audit: time=" + System.currentTimeMillis() + " actor=" + actor + " uuid=" + p.uuid() + " name=" + p.name() + " action=" + action + " result=" + result);
    }
    @Override public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            check(sender);
            if (args.length == 0) throw new UserException("/ic whitelist stage|unstage|staged|preview|launch|status|retry");
            String action = args[0].toLowerCase(Locale.ROOT);
            int required = List.of("stage", "unstage").contains(action) ? 2 : 1;
            if (args.length != required && !(action.equals("staged") && args.length == 2)) throw new UserException("Ungültige Argumente für /ic whitelist " + action);
            switch (action) {
                case "stage" -> stage(sender, args[1]);
                case "unstage" -> {
                    idle(); VerifiedProfile.validateName(args[1]);
                    context.reply(repository.unstage(args[1], actor(sender)), sender, p -> { audit(actor(sender), p, "UNSTAGE", "REMOVED_STAGING_ONLY"); sender.sendMessage(Chat.ok("Vormerkung entfernt; native Whitelist unverändert.")); });
                }
                case "staged", "preview" -> context.reply(repository.entries(), sender, entries -> {
                    check(sender);
                    long ready = entries.stream().filter(e -> e.status().equals("STAGED")).count();
                    long present = entries.stream().filter(e -> gateway.contains(e.profile())).count();
                    long errors = entries.stream().filter(e -> e.status().equals("FAILED")).count();
                    long applied = entries.stream().filter(e -> List.of("APPLIED", "ALREADY_PRESENT").contains(e.status())).count();
                    sender.sendMessage(Chat.info(entries.size() + " gesamt · " + ready + " STAGED · " + present + " nativ erlaubt · " + errors + " Fehler · " + applied + " abgeschlossen"));
                    if (action.equals("staged")) {
                        int pages = Math.max(1, (entries.size() + 9) / 10), page = page(args.length == 2 ? args[1] : "1", pages);
                        entries.stream().skip((page - 1L) * 10).limit(10).forEach(e -> sender.sendMessage(Chat.info(e.profile().name() + " · " + e.status()
                                + (gateway.contains(e.profile()) ? " · nativ erlaubt" : " · KEIN nativer Eintrag") + (e.error() == null ? "" : " · " + e.error()))));
                        sender.sendMessage(Chat.info("Seite " + page + "/" + pages));
                    }
                });
                case "launch", "retry" -> launch(sender, action.equals("retry"));
                case "status" -> context.reply(repository.latest(), sender, l -> {
                    check(sender);
                    sender.sendMessage(Chat.info(l == null ? "Noch kein Launch." : "Launch " + l.id() + " · " + l.state() + " · " + l.total() + " gesamt · " + l.applied() + " freigeschaltet · " + l.present() + " vorhanden · " + l.failed() + " Fehler · " + l.pending() + " offen"));
                });
                default -> throw new UserException("Unbekannter Whitelist-Staging-Befehl.");
            }
        } catch (UserException e) { sender.sendMessage(Chat.error(e.getMessage())); }
    }
    private void stage(CommandSender sender, String name) {
        VerifiedProfile.validateName(name);
        String actor = actor(sender);
        var future = resolver.name(name).thenComposeAsync(profile -> {
            check(sender);
            boolean present = gateway.contains(profile);
            return repository.stage(profile, actor, present).thenApply(status -> {
                audit(actor, profile, "STAGE", status);
                return profile.name() + " (" + profile.uuid() + "): " + status + (present ? " — bereits nativ freigeschaltet." : " — KEINE neue Zugangsfreigabe.");
            });
        }, context.mainThread());
        context.reply(future, sender, message -> sender.sendMessage(Chat.ok(message)));
    }
    private void launch(CommandSender sender, boolean retry) {
        idle(); busy = true;
        String id = UUID.randomUUID().toString(), actor = actor(sender);
        sender.sendMessage(Chat.info("Launch " + id + " gestartet; Snapshot wird eingefroren."));
        repository.claim(id, actor, retry).thenComposeAsync(entries -> process(entries, 0, sender, actor)
                .thenCompose(v -> repository.finish(id)), context.mainThread()).whenCompleteAsync((v, error) -> {
            if (!active) return;
            if (error != null) {
                // A commit may have failed after a native write. Keep the claim locked; restart performs reconciliation.
                context.plugin().getLogger().log(Level.SEVERE, "Whitelist launch interrupted; restart for reconciliation", error);
                sender.sendMessage(Chat.error("Launch unterbrochen. Status prüfen; Neustart zur sicheren Recovery erforderlich."));
            } else {
                busy = false;
                sender.sendMessage(Chat.info("Launch abgeschlossen. Details und Teilfehler: /ic whitelist status und staged."));
            }
        }, context.mainThread());
    }
    private CompletableFuture<Void> process(List<WhitelistStageRepository.Entry> entries, int index, CommandSender sender, String actor) {
        if (!active) return CompletableFuture.failedFuture(new IllegalStateException("Module disabled"));
        if (index == entries.size()) return CompletableFuture.completedFuture(null);
        var entry = entries.get(index);
        if (gateway.verified(entry.profile())) {
            audit(actor, entry.profile(), "APPLY", "ALREADY_PRESENT");
            return repository.result(entry, entry.profile(), "ALREADY_PRESENT", null, actor)
                    .thenComposeAsync(v -> process(entries, index + 1, sender, actor), context.mainThread());
        }
        return resolver.refresh(entry.profile()).handleAsync((profile, error) -> {
            if (!active) throw new IllegalStateException("Module disabled");
            String status = "FAILED", problem = null;
            if (error == null) {
                try {
                    boolean present = gateway.contains(profile);
                    gateway.add(profile);
                    status = present ? "ALREADY_PRESENT" : "APPLIED";
                } catch (RuntimeException e) { problem = e.getMessage(); }
            } else { profile = entry.profile(); problem = "Profilprüfung fehlgeschlagen: " + rootMessage(error); }
            audit(actor, profile, "APPLY", status + (problem == null ? "" : ": " + problem));
            if (problem != null) sender.sendMessage(Chat.error(profile.name() + ": " + problem));
            return repository.result(entry, profile, status, problem, actor);
        }, context.mainThread()).thenCompose(f -> f).thenComposeAsync(v -> process(entries, index + 1, sender, actor), context.mainThread());
    }
    private static String rootMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    private static int page(String value, int pages) {
        try { int n = Integer.parseInt(value); if (n >= 1 && n <= pages) return n; } catch (NumberFormatException ignored) { }
        throw new UserException("Bitte eine Seite von 1 bis " + pages + " wählen.");
    }
    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!active || !allowed(source.getSender())) return List.of();
        return args.length <= 1 ? Chat.complete(args, List.of("stage", "unstage", "staged", "preview", "launch", "status", "retry").stream()) : List.of();
    }
}
