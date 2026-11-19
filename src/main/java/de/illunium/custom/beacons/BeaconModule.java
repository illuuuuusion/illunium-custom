package de.illunium.custom.beacons;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.BeaconInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Beacons (Patch v1.5 §1). Vanilla only powers a beacon whose column is free; for a covered beacon this module gives
 * the effects the player selected and paid for in the vanilla menu, with the vanilla tier, range, duration and level-II
 * rules, in loaded ticking chunks only. The owner (UUID of the actual placer) and a snapshot of the last paid
 * selection live in the beacon's own PDC; both are lost with the block, like any block entity data.
 *
 * <p>B1 (hiding the beam under a free sky) is BLOCKED: the vanilla client computes the beam itself from the blocks it
 * knows, so only fake client blocks could hide it. {@code beacons.beam-toggle-enabled} therefore only accepts false.
 */
public final class BeaconModule implements FeatureModule, Listener {

    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block block) {
            return new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** Vanilla scan result for one beacon at one moment. */
    record Status(boolean covered, int tier, PotionEffectType primary, PotionEffectType secondary) {
        List<BeaconRules.Grant<PotionEffectType>> grants() {
            return BeaconRules.grants(tier, primary, secondary);
        }
    }

    final Set<Pos> beacons = new HashSet<>(); // package-private for the Paper probe
    private ModuleContext context;
    private Server server;
    private NamespacedKey ownerKey;
    private NamespacedKey selectionKey;
    private boolean coveredEffects;
    private BukkitTask timer;

    @Override
    public String id() {
        return "beacons";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        this.server = context.plugin().getServer();
        coveredEffects = bool(context, "allow-covered-effects", true);
        if (bool(context, "beam-toggle-enabled", false))
            throw new IllegalArgumentException("beacons.beam-toggle-enabled must be false: hiding one beam under a free sky "
                    + "needs client-side fake blocks on Paper 26.3 (Patch v1.5 B1 is blocked, see docs/development.md)");
        Object refresh = context.settings().get("effect-refresh-ticks", 80);
        if (!(refresh instanceof Integer ticks) || ticks < 20 || ticks > 200)
            throw new IllegalArgumentException("beacons.effect-refresh-ticks must be 20..200 (shortest vanilla effect lasts 220 ticks)");
        ownerKey = new NamespacedKey(context.plugin(), "beacon_owner");
        selectionKey = new NamespacedKey(context.plugin(), "beacon_selection");

        server.getPluginManager().registerEvents(this, context.plugin());
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) track(chunk);
        }
        if (coveredEffects) timer = server.getScheduler().runTaskTimer(context.plugin(), this::powerCovered, ticks, ticks);
        BeaconCommand command = new BeaconCommand(this, context);
        context.plugin().registerCommand("beacon", "Beacon unter deinen Füßen anzeigen", List.of(), command);
        context.admin().register("beacon", command.admin());
    }

    @Override
    public void disable() {
        if (timer != null) timer.cancel();
        HandlerList.unregisterAll(this);
        beacons.clear();
    }

    // --- registry: beacons in loaded chunks, including ones placed before this plugin ---

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        track(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        UUID world = event.getWorld().getUID();
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        beacons.removeIf(p -> p.world().equals(world) && p.x() >> 4 == cx && p.z() >> 4 == cz);
    }

    private void track(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.BEACON, false)) {
            beacons.add(Pos.of(state.getBlock()));
        }
    }

    /** The real placer becomes owner; written next tick, after Paper applied the item's block entity data. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.BEACON) return;
        beacons.add(Pos.of(block));
        UUID placer = event.getPlayer().getUniqueId();
        server.getScheduler().runTask(context.plugin(), () -> {
            if (block.getType() == Material.BEACON) setOwner(block, placer);
        });
    }

    // --- covered beacons ---

    /**
     * The vanilla menu (and its server-side check) offers effects by the beacon's stored tier, which vanilla never
     * updates while the column is covered. Report the real pyramid tier to the open menu instead; that writes through
     * to the beacon, so a paid selection is stored and validated exactly as vanilla does.
     * {@code InventoryView.Property} is the only setter for it in the pinned API (BeaconView has none); its removal
     * must fail the build on an API upgrade rather than silently break covered selections.
     */
    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!coveredEffects || !(event.getInventory() instanceof BeaconInventory inventory) || inventory.getLocation() == null
                || !(event.getPlayer() instanceof Player player)) return;
        Block block = inventory.getLocation().getBlock();
        server.getScheduler().runTask(context.plugin(), () -> {
            InventoryView view = player.getOpenInventory();
            Location open = view.getType() == InventoryType.BEACON ? view.getTopInventory().getLocation() : null;
            if (open == null || !open.getBlock().equals(block) || block.getType() != Material.BEACON) return;
            Status status = status(block);
            if (status.covered()) view.setProperty(InventoryView.Property.LEVELS, status.tier());
        });
    }

    private void powerCovered() {
        for (Pos pos : Set.copyOf(beacons)) {
            World world = server.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                beacons.remove(pos);
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (block.getType() != Material.BEACON) {
                beacons.remove(pos);
                continue;
            }
            Chunk.LoadLevel level = block.getChunk().getLoadLevel();
            if (level != Chunk.LoadLevel.TICKING && level != Chunk.LoadLevel.ENTITY_TICKING) continue; // vanilla does not tick it either
            if (!covered(block)) continue; // free column: vanilla powers it itself
            Status status = status(block);
            if (status.grants().isEmpty()) continue;
            double range = BeaconRules.range(status.tier());
            int duration = BeaconRules.durationTicks(status.tier());
            BoundingBox box = new BoundingBox(pos.x() - range, pos.y() - range, pos.z() - range,
                    pos.x() + 1 + range, pos.y() + 1 + range + world.getMaxHeight() - world.getMinHeight(), pos.z() + 1 + range);
            for (var entity : world.getNearbyEntities(box, e -> e instanceof Player)) {
                for (var grant : status.grants()) {
                    // Like vanilla: a stronger or longer effect the player already has is kept.
                    ((Player) entity).addPotionEffect(new PotionEffect(grant.type(), duration, grant.amplifier(), true, true));
                }
            }
        }
    }

    // --- shared with the command ---

    Status status(Block beacon) {
        Beacon state = (Beacon) beacon.getState(false);
        World world = beacon.getWorld();
        int x = beacon.getX(), y = beacon.getY(), z = beacon.getZ();
        int tier = BeaconRules.tier(x, y, z, world.getMinHeight(), (bx, by, bz) ->
                world.isChunkLoaded(bx >> 4, bz >> 4) && Tag.BEACON_BASE_BLOCKS.isTagged(world.getBlockAt(bx, by, bz).getType()));
        PotionEffect primary = state.getPrimaryEffect();
        return new Status(covered(beacon), tier, type(primary), secondary(state, primary));
    }

    /**
     * Paper derives the readable secondary and the primary's amplifier from the beacon's stored tier, which vanilla does
     * not update while the column is covered. At stored tier 4 they still show the selection exactly (amplifier 1 means
     * primary = secondary); below that the snapshot of the last paid menu selection is used if it matches the primary.
     */
    private PotionEffectType secondary(Beacon state, PotionEffect primary) {
        if (primary == null) return null;
        if (state.getTier() >= BeaconRules.MAX_TIER) {
            return primary.getAmplifier() == 1 ? primary.getType() : type(state.getSecondaryEffect());
        }
        String[] selection = state.getPersistentDataContainer().getOrDefault(selectionKey, PersistentDataType.STRING, "").split(" ");
        if (selection.length != 2 || !selection[0].equals(primary.getType().getKey().toString()) || selection[1].equals("-")) return null;
        NamespacedKey secondary = NamespacedKey.fromString(selection[1]);
        return secondary == null ? null : Registry.MOB_EFFECT.get(secondary);
    }

    /** Snapshot of a paid vanilla menu selection, next tick once the beacon has applied it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSelect(PlayerChangeBeaconEffectEvent event) {
        Block block = event.getBeacon();
        PotionEffectType primary = event.getPrimary(), secondary = event.getSecondary();
        server.getScheduler().runTask(context.plugin(), () -> {
            if (block.getType() == Material.BEACON) rememberSelection(block, primary, secondary);
        });
    }

    void rememberSelection(Block beacon, PotionEffectType primary, PotionEffectType secondary) {
        Beacon state = (Beacon) beacon.getState();
        if (primary == null) state.getPersistentDataContainer().remove(selectionKey);
        else state.getPersistentDataContainer().set(selectionKey, PersistentDataType.STRING,
                primary.getKey() + " " + (secondary == null ? "-" : secondary.getKey().toString()));
        state.update(false, false);
    }

    boolean coveredEffects() {
        return coveredEffects;
    }

    /**
     * Whether the column above stops the beam. Vanilla stops at a block with light dampening 15 (solid render or tinted
     * glass) except bedrock, up to the surface heightmap. {@link BlockData#isOccluding()} is a superset of solid render,
     * so any mismatch only means vanilla gives the same effects as well, never that a beacon goes unpowered.
     */
    static boolean covered(Block beacon) {
        World world = beacon.getWorld();
        int top = world.getHighestBlockYAt(beacon.getX(), beacon.getZ(), HeightMap.WORLD_SURFACE);
        for (int y = beacon.getY() + 1; y <= top; y++) {
            BlockData data = world.getBlockAt(beacon.getX(), y, beacon.getZ()).getBlockData();
            Material type = data.getMaterial();
            if (type == Material.TINTED_GLASS || type != Material.BEDROCK && type != Material.BEACON && data.isOccluding()) return true;
        }
        return false;
    }

    /** Empty for beacons without a valid owner entry (placed before v1.5, by commands, or damaged data). */
    Optional<UUID> owner(Block beacon) {
        String raw = ((Beacon) beacon.getState(false)).getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return raw == null ? Optional.empty() : Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    void setOwner(Block beacon, UUID owner) {
        Beacon state = (Beacon) beacon.getState();
        state.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        state.update(false, false);
    }

    private static PotionEffectType type(PotionEffect effect) {
        return effect == null ? null : effect.getType();
    }

    private static boolean bool(ModuleContext context, String key, boolean fallback) {
        if (context.settings().get(key, fallback) instanceof Boolean value) return value;
        throw new IllegalArgumentException("beacons." + key + " must be true or false");
    }
}
