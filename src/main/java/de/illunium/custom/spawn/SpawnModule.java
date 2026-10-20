package de.illunium.custom.spawn;

import static de.illunium.custom.core.command.Chat.error;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * {@code /spawn}: teleports to the world spawn that vanilla {@code /setworldspawn} manages, read on every use; never a
 * personal bed or anchor spawn, no cooldown or cost. A blocked spawn is refused instead of teleporting into blocks.
 * A sitting player is unseated by the sit module's teleport listener.
 */
public final class SpawnModule implements FeatureModule {

    private Server server;

    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public void enable(ModuleContext context) {
        server = context.plugin().getServer();
        Objects.requireNonNull(server.getRespawnWorld(), "no spawn world is loaded");
        context.plugin().registerCommand("spawn", "Teleport to the world spawn", List.of(), new SpawnCommand());
    }

    @Override
    public void disable() {
        // Nothing is kept between commands.
    }

    /** The spawn block if a player fits there, else the top of that column, else null. Chunk must be loaded. */
    static Location safeSpot(Location spawn) {
        Location spot = spawn.toBlockLocation().add(0.5, 0, 0.5).setRotation(spawn.getYaw(), spawn.getPitch());
        if (fits(spot.getBlock())) {
            return spot;
        }
        Block top = spawn.getWorld().getHighestBlockAt(spot).getRelative(BlockFace.UP);
        return fits(top) ? top.getLocation().add(0.5, 0, 0.5).setRotation(spawn.getYaw(), spawn.getPitch()) : null;
    }

    private static boolean fits(Block feet) {
        return standable(feet.getRelative(BlockFace.DOWN), feet, feet.getRelative(BlockFace.UP));
    }

    /** Solid ground, then two free blocks that are neither liquid nor fire. */
    static boolean standable(Block below, Block feet, Block head) {
        return !below.isPassable() && !below.isLiquid() && free(feet) && free(head);
    }

    private static boolean free(Block block) {
        return block.isPassable() && !block.isLiquid() && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE;
    }

    private final class SpawnCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                source.getSender().sendMessage(error("Only players can do that."));
                return;
            }
            Location spawn = server.getRespawnWorld().getSpawnLocation();
            // Loads just the spawn chunk off-thread; the callback runs on the main thread.
            spawn.getWorld().getChunkAtAsync(spawn).thenAccept(chunk -> {
                if (!player.isConnected()) {
                    return;
                }
                Location target = safeSpot(spawn);
                if (target == null) {
                    player.sendMessage(error("The spawn is blocked right now. Please tell an admin."));
                } else if (!player.teleport(target, PlayerTeleportEvent.TeleportCause.COMMAND)) {
                    player.sendMessage(error("Teleport was cancelled."));
                }
            });
        }

        @Override
        public String permission() {
            return "illuniumcustom.use";
        }
    }
}
