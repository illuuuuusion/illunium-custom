package de.illunium.custom.groups;

import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.core.command.UserException;
import de.illunium.custom.core.module.ModuleContext;
import de.illunium.custom.storage.Database;
import java.lang.reflect.Proxy;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Opt-in Paper integration probe. Uses synthetic viewers; never substitutes for real client E2E. */
public final class GroupChestPaperProbe extends JavaPlugin {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private Database db;
    private GroupService groups;
    private GroupChestService chests;
    private GroupChestInventoryListener listener;
    private ModuleContext context;
    private Group group;
    private final List<Viewer> viewers = new ArrayList<>();
    private ItemStack[] special;

    @Override public void onEnable() {
        try {
            db = Database.open(getDataPath().resolve("probe.db"));
            groups = GroupService.load(db, tasks::add, InstantSource.system(), (p,g) -> {});
            context = new ModuleContext(this, new YamlConfiguration(), db, null, null,
                    new VisibilityService(getServer()), null, tasks::add);
            chests = new GroupChestService(context, groups);
            listener = new GroupChestInventoryListener(chests);
            getServer().getPluginManager().registerEvents(listener, this);
            getServer().getScheduler().runTaskTimer(this, () -> {
                Runnable task;
                while ((task = tasks.poll()) != null) task.run();
            }, 1, 1);
            special = specialItems();
            verify(java.util.Arrays.equals(special, GroupChestCodec.decode(GroupChestCodec.encode(special))), "NBT special-item round trip");
            var restart = groups.byName("Restart");
            if (restart.isPresent()) {
                var stored = new GroupChestRepository(db).load(restart.orElseThrow().id(), new byte[0]).join();
                verify(java.util.Arrays.equals(special, GroupChestCodec.decode(stored.contents())), "NBT after real server restart");
                getLogger().info("PAPER_PROBE_RESTART_PASS: all special items survived stop/start");
                Bukkit.shutdown();
                return;
            }
            for (int i = 0; i < 4; i++) viewers.add(new Viewer("Probe" + i));
            var a = viewers.getFirst();
            CompletableFuture<Group> setup = groups.create(a.id, "Probe", "PRB");
            for (Viewer viewer : viewers.subList(1, 4)) setup = setup
                    .thenCompose(g -> groups.invite(a.id, viewer.id)).thenCompose(g -> groups.accept(viewer.id, "Probe"));
            setup.thenAccept(g -> {
                group = g;
                // All four open calls overlap while the first DB load is still pending.
                viewers.forEach(v -> chests.open(v.player));
                verify(blocked(() -> groups.adminDisband(g.id())), "loading disband guard");
                await(() -> viewers.stream().allMatch(v -> v.top != null), this::shared);
            }).exceptionally(this::failed);
        } catch (Throwable error) { failed(error); }
    }

    private void shared() {
        Inventory inventory = viewers.getFirst().top;
        verify(viewers.stream().allMatch(v -> v.top == inventory), "one live inventory for all viewers");
        inventory.setContents(special);
        chests.afterInteraction(inventory);
        Viewer kicked = viewers.getLast();
        groups.kick(viewers.getFirst().id, kicked.id).thenAccept(g -> {
            verify(kicked.top == null, "kicked viewer closed");
            verify(!chests.mayUse(kicked.id, inventory), "kicked viewer denied");
            verify(chests.withdrawalOnly(viewers.getFirst().id), "four to three applies to unchanged members");
            var view = viewers.getFirst().view();
            var place = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL);
            listener.click(place); verify(place.isCancelled(), "deposit cancelled in open view");
            var pickup = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            listener.click(pickup); verify(!pickup.isCancelled(), "withdrawal allowed");
            var drag = new InventoryDragEvent(view, ItemStack.of(Material.DIAMOND), ItemStack.of(Material.DIAMOND, 2), false,
                    java.util.Map.of(0, ItemStack.of(Material.DIAMOND)));
            listener.drag(drag); verify(drag.isCancelled(), "top drag denied");
            verify(blocked(() -> groups.adminDisband(group.id())), "live disband guard");
            groups.invite(viewers.getFirst().id, kicked.id).thenCompose(invited -> groups.accept(kicked.id, "Probe"))
                    .thenCompose(joined -> {
                        verify(!chests.withdrawalOnly(viewers.getFirst().id), "three to four restores deposits");
                        var restored = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL);
                        listener.click(restored); verify(!restored.isCancelled(), "deposit enabled again");
                        return groups.kick(viewers.getFirst().id, kicked.id);
                    }).thenAccept(removed -> {
                        viewers.forEach(Viewer::close);
                        await(() -> sessionCount() == 0, this::reload);
                    }).exceptionally(this::failed);
        }).exceptionally(this::failed);
    }

    private void reload() {
        var repository = new GroupChestRepository(db);
        var stored = repository.load(group.id(), GroupChestCodec.encode(new ItemStack[27])).join();
        verify(java.util.Arrays.equals(special, GroupChestCodec.decode(stored.contents())), "last-close persisted metadata");
        chests.open(viewers.getFirst().player);
        await(() -> viewers.getFirst().top != null, () -> {
            Inventory inventory = viewers.getFirst().top;
            verify(java.util.Arrays.equals(special, inventory.getContents()), "three-member recovery access");
            inventory.clear(); chests.afterInteraction(inventory); viewers.getFirst().close();
            await(() -> sessionCount() == 0, () -> groups.adminDisband(group.id()).thenAccept(g -> {
                verify(groups.groupOf(viewers.getFirst().id).isEmpty(), "empty chest disband succeeds");
                groups.create(viewers.getFirst().id, "New", "NEW").thenAccept(fresh -> {
                    group = fresh;
                    chests.open(viewers.getFirst().player);
                    await(() -> sessionCount() == 0, this::shutdownFlush);
                }).exceptionally(this::failed);
            }).exceptionally(this::failed));
        });
    }

    private void shutdownFlush() {
        verify(viewers.getFirst().top == null, "one-member empty chest stays locked after ID reuse");
        CompletableFuture<Group> setup = CompletableFuture.completedFuture(group);
        for (Viewer viewer : viewers.subList(1, 4)) setup = setup.thenCompose(g -> groups.invite(viewers.getFirst().id, viewer.id))
                .thenCompose(g -> groups.accept(viewer.id, "New"));
        setup.thenAccept(g -> {
            chests.open(viewers.getFirst().player);
            await(() -> viewers.getFirst().top != null, () -> {
                viewers.getFirst().top.setContents(special);
                chests.close(); // No checkpoint tick in between: final shutdown snapshot is required.
                var repository = new GroupChestRepository(db);
                verify(java.util.Arrays.equals(special, GroupChestCodec.decode(repository.load(group.id(), new byte[0]).join().contents())), "shutdown flush");
                chests = new GroupChestService(context, groups);
                chests.open(viewers.getFirst().player);
                await(() -> viewers.getFirst().top != null, this::saveFailure);
            });
        }).exceptionally(this::failed);
    }

    private void saveFailure() {
        Inventory inventory = viewers.getFirst().top;
        db.transaction(c -> {
            try (var s = c.createStatement()) { s.executeUpdate("CREATE TRIGGER probe_fail BEFORE UPDATE ON group_chests BEGIN SELECT RAISE(ABORT, 'injected save failure'); END"); }
            return null;
        }).join();
        inventory.setItem(5, ItemStack.of(Material.EMERALD));
        chests.afterInteraction(inventory);
        await(() -> viewers.getFirst().top == null, () -> {
            verify(!chests.mayUse(viewers.getFirst().id, inventory), "save failure locks access");
            verify(sessionCount() == 1 && blocked(() -> groups.adminDisband(group.id())), "failure retains cache and blocks disband");
            chests.close();
            db.transaction(c -> {
                try (var s = c.createStatement()) {
                    s.executeUpdate("DROP TRIGGER probe_fail");
                    s.executeUpdate("UPDATE group_chests SET contents = X'010203'");
                }
                return null;
            }).join();
            chests = new GroupChestService(context, groups);
            chests.open(viewers.getFirst().player);
            getServer().getScheduler().runTaskLater(this, () -> guarded(() -> {
                verify(viewers.getFirst().top == null && sessionCount() == 1, "corrupt payload locked");
                var snapshot = new GroupChestRepository(db).load(group.id(), new byte[0]).join();
                verify(java.util.Arrays.equals(new byte[]{1,2,3}, snapshot.contents()), "corrupt payload preserved");
                groups.create(UUID.randomUUID(), "Restart", "RST").thenAccept(restart -> {
                    var repository = new GroupChestRepository(db);
                    repository.load(restart.id(), GroupChestCodec.encode(new ItemStack[27])).join();
                    repository.save(restart.id(), new GroupChestRepository.Snapshot(GroupChestCodec.encode(special), GroupChestCodec.occupied(special), 1)).join();
                    getLogger().info("PAPER_PROBE_PASS: NBT, shared load, live membership, input guards, close/reopen, ID reuse, shutdown, save failure, corrupt payload; restart to verify persisted special items");
                    Bukkit.shutdown();
                }).exceptionally(this::failed);
            }), 5);
        });
    }

    private ItemStack[] specialItems() {
        ItemStack[] items = new ItemStack[27];
        items[0] = ItemStack.of(Material.DIAMOND_SWORD);
        items[0].editMeta(meta -> {
            meta.displayName(Component.text("Test • ä 😀")); meta.lore(List.of(Component.text("Custom lore")));
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "probe"), PersistentDataType.STRING, "unchanged");
        });
        items[0].addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        items[1] = ItemStack.of(Material.SHULKER_BOX);
        items[1].editMeta(BlockStateMeta.class, meta -> {
            ShulkerBox box = (ShulkerBox)meta.getBlockState(); box.getInventory().setItem(3, ItemStack.of(Material.DIAMOND, 37)); meta.setBlockState(box);
        });
        items[2] = ItemStack.of(Material.WRITTEN_BOOK);
        items[2].editMeta(BookMeta.class, meta -> { meta.setTitle("Probe"); meta.setAuthor("Illunium"); meta.pages(List.of(Component.text("Seite ä 😀"))); });
        items[3] = ItemStack.of(Material.PAPER, 7);
        items[3].setData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_STACK_SIZE, 16);
        items[3].setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_NAME, Component.text("Data component"));
        items[26] = ItemStack.of(Material.DIAMOND, 64);
        return items;
    }

    private final class Viewer {
        final UUID id = UUID.randomUUID();
        final Player player;
        Inventory top;
        Viewer(String name) {
            player = (Player)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id; case "getName" -> name; case "isOnline", "hasPermission" -> true;
                case "openInventory" -> { close(); top = (Inventory)args[0]; top.getViewers().add((Player)proxy); yield view(); }
                case "closeInventory" -> { close(); yield null; }
                case "getOpenInventory" -> view();
                case "sendMessage" -> null;
                case "equals" -> proxy == args[0]; case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
        InventoryView view() {
            return (InventoryView)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InventoryView.class}, (p,m,a) -> switch (m.getName()) {
                case "getTopInventory" -> top; case "getPlayer" -> player; case "getType" -> InventoryType.CHEST;
                case "convertSlot" -> (int)a[0] < 27 ? a[0] : (int)a[0] - 27;
                case "getInventory" -> (int)a[0] < 27 ? top : null;
                default -> throw new UnsupportedOperationException(m.getName());
            });
        }
        void close() {
            if (top == null) return;
            Inventory old = top; top = null; old.getViewers().remove(player); chests.afterInteraction(old);
        }
    }

    private int sessionCount() {
        try {
            var field = GroupChestService.class.getDeclaredField("sessions"); field.setAccessible(true);
            return ((java.util.Map<?,?>)field.get(chests)).size();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static boolean blocked(Runnable action) {
        try { action.run(); return false; } catch (UserException expected) { return true; }
    }
    private void await(BooleanSupplier condition, Runnable next) { await(condition, next, 100); }
    private void await(BooleanSupplier condition, Runnable next, int remaining) {
        getServer().getScheduler().runTaskLater(this, () -> guarded(() -> {
            if (condition.getAsBoolean()) next.run();
            else if (remaining > 0) await(condition, next, remaining - 1);
            else throw new AssertionError("probe timeout");
        }), 1);
    }
    private static void verify(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void guarded(Runnable action) { try { action.run(); } catch (Throwable e) { failed(e); } }
    private <T> T failed(Throwable error) { getLogger().log(Level.SEVERE, "PAPER_PROBE_FAIL", error); Bukkit.shutdown(); return null; }
    @Override public void onDisable() { if (chests != null) chests.close(); if (db != null) db.close(); }
}
