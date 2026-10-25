package de.illunium.custom.groups;

import de.illunium.custom.core.command.Chat;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.ModuleContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Main-thread live sessions, one per group. SQL only sees complete byte snapshots. */
final class GroupChestService implements AutoCloseable, GroupChangeListener {
    private static final String RECOVERY = "Gruppenkiste gesperrt: Speicherfehler. Bitte einen Admin informieren; vor einer Wiederherstellung ein Backup sichern.";

    private static final class Session {
        final long id;
        CompletableFuture<Session> loading;
        GroupChestHolder holder;
        byte[] snapshot;
        long revision, savedRevision;
        int pendingOpens;
        boolean checkpointScheduled, failed;
        Session(long id) { this.id = id; }
    }

    private final ModuleContext context;
    private final GroupService groups;
    private final GroupChestRepository repository;
    private final Map<Long, Session> sessions = new HashMap<>();
    private final BukkitTask timer;
    private boolean active = true;

    GroupChestService(ModuleContext context, GroupService groups) {
        this.context = context;
        this.groups = groups;
        repository = new GroupChestRepository(context.database());
        groups.onChange(this);
        groups.guardDisband(id -> {
            if (sessions.containsKey(id)) throw new UserException("Gruppenkiste zuerst leeren und alle Ansichten schließen; Speicherung abwarten und erneut bestätigen.");
        });
        timer = context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), () -> {
            for (Session session : List.copyOf(sessions.values())) if (session.holder != null) checkpoint(session);
        }, 20, 20);
    }

    void open(Player player) {
        if (!active) throw new UserException("Gruppenkiste ist deaktiviert.");
        Group group = groups.groupOf(player.getUniqueId()).orElseThrow(() -> new UserException("Du bist in keiner Gruppe."));
        if (groups.disbanding(group.id())) throw new UserException("Die Gruppe wird gerade aufgelöst.");
        Session session = sessions.get(group.id());
        if (session == null) {
            session = new Session(group.id());
            sessions.put(group.id(), session);
            Session created = session;
            byte[] empty = GroupChestCodec.encode(new ItemStack[GroupChestCodec.SLOTS]);
            session.loading = repository.load(group.id(), empty).thenApplyAsync(stored -> {
                if (!active || sessions.get(created.id) != created) throw new UserException("Gruppenkiste ist deaktiviert.");
                ItemStack[] items = GroupChestCodec.decode(stored.contents());
                if (GroupChestCodec.occupied(items) != stored.occupied() || stored.revision() < 0)
                    throw new IllegalArgumentException("Group chest metadata mismatch: " + created.id);
                created.holder = new GroupChestHolder(group);
                created.holder.getInventory().setContents(items);
                created.snapshot = GroupChestCodec.encode(items);
                created.revision = created.savedRevision = stored.revision();
                return created;
            }, context.mainThread());
        }
        Session requested = session;
        requested.pendingOpens++;
        session.loading.whenCompleteAsync((loaded, error) -> {
            try {
                if (!active) return;
                if (error != null) { fail(requested, error); throw new UserException(RECOVERY); }
                if (!player.isOnline()) return;
                if (!player.hasPermission("illuniumcustom.use")) throw new UserException("Keine Berechtigung.");
                Group current = groups.groupOf(player.getUniqueId()).filter(g -> g.id() == loaded.id)
                        .orElseThrow(() -> new UserException("Du bist nicht mehr in dieser Gruppe."));
                if (loaded.failed) throw new UserException(RECOVERY);
                if (sessions.get(loaded.id) != loaded || groups.disbanding(loaded.id))
                    throw new UserException("Gruppenkiste hat sich geändert; /gec erneut aufrufen.");
                if (current.members().size() < 4) {
                    if (GroupChestCodec.occupied(loaded.holder.getInventory().getContents()) == 0)
                        throw new UserException("Gruppen-Enderchest ab 4 Mitgliedern verfügbar (aktuell " + current.members().size() + "/4).");
                    player.sendMessage(Chat.info("Nur Entnahme: Deine Gruppe hat weniger als 4 Mitglieder."));
                }
                player.openInventory(loaded.holder.getInventory());
            } catch (UserException e) {
                if (player.isOnline()) player.sendMessage(Chat.error(e.getMessage()));
            } catch (RuntimeException e) {
                fail(requested, e);
                if (player.isOnline()) player.sendMessage(Chat.error(RECOVERY));
            } finally {
                requested.pendingOpens--;
                evict(requested);
            }
        }, context.mainThread());
    }

    boolean mayUse(UUID player, Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof GroupChestHolder holder)) return false;
        Session session = sessions.get(holder.groupId());
        return active && session != null && !session.failed && session.holder == holder && holder.getInventory() == inventory
                && !groups.disbanding(holder.groupId())
                && groups.groupOf(player).filter(g -> g.id() == holder.groupId()).isPresent();
    }

    boolean withdrawalOnly(UUID player) {
        return groups.groupOf(player).map(g -> g.members().size() < 4).orElse(true);
    }

    void afterInteraction(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof GroupChestHolder holder)) return;
        Session session = sessions.get(holder.groupId());
        if (session == null || session.holder != holder || session.checkpointScheduled || !active) return;
        session.checkpointScheduled = true;
        context.mainThread().execute(() -> {
            session.checkpointScheduled = false;
            if (active && sessions.get(session.id) == session) checkpoint(session);
        });
    }

    void closeNextTick(HumanEntity player, Inventory inventory) {
        context.mainThread().execute(() -> {
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
        });
    }

    private void checkpoint(Session session) {
        if (session.failed) return;
        try {
            ItemStack[] items = session.holder.getInventory().getContents();
            byte[] bytes = GroupChestCodec.encode(items);
            if (!Arrays.equals(bytes, session.snapshot)) {
                session.snapshot = bytes;
                long revision = ++session.revision;
                var save = repository.save(session.id, new GroupChestRepository.Snapshot(bytes, GroupChestCodec.occupied(items), revision));
                // Log on the DB thread too: scheduler callbacks are dropped during shutdown.
                save.whenComplete((unused, error) -> {
                    if (error != null) context.plugin().getLogger().log(Level.SEVERE,
                            "Group chest " + session.id + " revision " + revision + " could not be saved; preserve DB/backups", error);
                });
                save.whenCompleteAsync((unused, error) -> {
                    if (!active || sessions.get(session.id) != session) return;
                    if (error != null) { fail(session, error); return; }
                    session.savedRevision = revision;
                    evict(session);
                }, context.mainThread());
            }
            evict(session);
        } catch (RuntimeException e) { fail(session, e); }
    }

    private void evict(Session session) {
        if (!session.failed && session.holder != null && session.pendingOpens == 0 && !session.checkpointScheduled
                && session.holder.getInventory().getViewers().isEmpty() && session.savedRevision == session.revision)
            sessions.remove(session.id, session);
    }

    private void fail(Session session, Throwable error) {
        if (session.failed) return;
        session.failed = true;
        context.plugin().getLogger().log(Level.SEVERE, "Group chest " + session.id + " locked; preserve stored contents and restore a verified backup", error);
        if (session.holder == null) return;
        for (HumanEntity viewer : List.copyOf(session.holder.getInventory().getViewers())) {
            viewer.sendMessage(Chat.error(RECOVERY));
            viewer.closeInventory();
        }
    }

    @Override public void changed(Group oldGroup, Group newGroup) {
        if (!active || oldGroup == null) return;
        Session session = sessions.get(oldGroup.id());
        if (session == null || session.holder == null) return;
        Inventory inventory = session.holder.getInventory();
        for (HumanEntity viewer : List.copyOf(inventory.getViewers())) {
            if (newGroup == null || !newGroup.members().containsKey(viewer.getUniqueId())) viewer.closeInventory();
            else if (newGroup.members().size() < 4 && oldGroup.members().size() >= 4)
                viewer.sendMessage(Chat.info("Gruppenkiste jetzt nur zur Entnahme verfügbar (weniger als 4 Mitglieder)."));
        }
        afterInteraction(inventory);
    }

    @Override public void close() {
        active = false;
        timer.cancel();
        for (Session session : List.copyOf(sessions.values())) {
            if (session.holder == null) continue;
            for (HumanEntity viewer : List.copyOf(session.holder.getInventory().getViewers())) viewer.closeInventory();
            // Capture after returning cursors; Database.close drains these final writes.
            checkpoint(session);
        }
    }
}
