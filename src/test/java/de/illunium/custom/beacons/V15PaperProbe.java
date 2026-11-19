package de.illunium.custom.beacons;

import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.core.command.AdminCommand;
import de.illunium.custom.core.module.ModuleContext;
import de.illunium.custom.recipes.RecipesModule;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Opt-in Paper probe for Patch v1.5: recipes (incl. a real furnace smelt) and the beacon world logic. Only on a
 * disposable server, as the only plugin. Needs no players, so it never replaces client E2E (menu, effects on players,
 * chat, tablist). Prints V15_PROBE_PASS, then V15_PROBE_RESTART_PASS after a second start of the same server.
 */
public final class V15PaperProbe extends JavaPlugin {

    private static final int BX = 10_008, BY = 200, BZ = 10_008; // inside chunk 625/625 incl. the tier-4 pyramid
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private World world;
    private BeaconModule beacons;
    private final RecipesModule recipes = new RecipesModule();

    @Override
    public void onEnable() {
        try {
            world = getServer().getWorlds().getFirst();
            if (Files.exists(getDataPath().resolve("restart-marker"))) {
                restart();
                return;
            }
            recipes();
            beacons();
        } catch (Throwable e) {
            fail(e);
        }
    }

    // --- recipes ---

    private void recipes() throws Exception {
        recipes.enable(context(new YamlConfiguration()));
        check(ownRecipes() == 6, "six own recipes, got " + ownRecipes());
        craft("4 quartz", new Material[]{Material.QUARTZ_BLOCK}, Material.QUARTZ, 4);
        craft("tuff square", grid(Material.BASALT, Material.GRAVEL, null, Material.GRAVEL, Material.BASALT), Material.TUFF, 4);
        craft("tuff spread", grid(null, null, Material.GRAVEL, null, Material.BASALT, null, Material.BASALT, null, Material.GRAVEL), Material.TUFF, 4);
        craft("cinnabar", grid(Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.RED_DYE), Material.CINNABAR, 4);
        craft("calcite", grid(Material.STONE, Material.BONE_MEAL, null, Material.BONE_MEAL, Material.STONE), Material.CALCITE, 2);
        craft("cobbled deepslate", grid(Material.COBBLESTONE, Material.BASALT, null, Material.BASALT, Material.COBBLESTONE), Material.COBBLED_DEEPSLATE, 4);
        for (Material[] wrong : new Material[][]{
                grid(Material.POLISHED_BASALT, Material.GRAVEL, null, Material.GRAVEL, Material.POLISHED_BASALT),
                grid(Material.BASALT, Material.BASALT, null, Material.BASALT, Material.GRAVEL),
                grid(Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.RED_DYE),
                grid(Material.POTENT_SULFUR, Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.RED_DYE),
                grid(Material.COBBLESTONE, Material.BONE_MEAL, null, Material.BONE_MEAL, Material.COBBLESTONE),
                grid(Material.SMOOTH_STONE, Material.BONE_MEAL, null, Material.BONE_MEAL, Material.SMOOTH_STONE),
                grid(Material.STONE, Material.STONE, null, Material.BONE_MEAL)}) {
            Recipe found = getServer().getCraftingRecipe(items(wrong), world);
            check(found == null || !((Keyed) found).getKey().getNamespace().equals(ownNamespace()), "no own recipe for " + java.util.Arrays.toString(wrong));
        }
        Recipe vanilla = getServer().getCraftingRecipe(items(grid(Material.QUARTZ, Material.QUARTZ, null, Material.QUARTZ, Material.QUARTZ)), world);
        check(vanilla != null && vanilla.getResult().getType() == Material.QUARTZ_BLOCK, "vanilla quartz block intact");

        Recipe recipe = getServer().getRecipe(new NamespacedKey(this, "dead_bush_from_fern"));
        check(recipe instanceof FurnaceRecipe f && f.getCookingTime() == 200 && f.getExperience() == 0f
                && f.getInputChoice().test(ItemStack.of(Material.FERN)) && f.getResult().equals(ItemStack.of(Material.DEAD_BUSH)), "furnace recipe values");
        List<CookingRecipe<?>> fernCooking = new ArrayList<>();
        for (Iterator<Recipe> it = getServer().recipeIterator(); it.hasNext(); ) {
            if (it.next() instanceof CookingRecipe<?> cooking && cooking.getInputChoice().test(ItemStack.of(Material.FERN))) fernCooking.add(cooking);
        }
        check(fernCooking.size() == 1 && fernCooking.getFirst() instanceof FurnaceRecipe, "only our furnace cooks fern: " + fernCooking);

        recipes.disable();
        check(ownRecipes() == 0, "disable removes own recipes");
        recipes.enable(context(new YamlConfiguration()));
        check(ownRecipes() == 6, "re-enable without duplicates");
    }

    private void craft(String name, Material[] matrix, Material result, int amount) {
        Recipe recipe = getServer().getCraftingRecipe(items(matrix), world);
        check(recipe != null && ((Keyed) recipe).getKey().getNamespace().equals(ownNamespace()), name + ": own recipe matched");
        ItemStack crafted = getServer().craftItemResult(items(matrix), world).getResult();
        check(crafted.getType() == result && crafted.getAmount() == amount, name + ": got " + crafted);
    }

    private long ownRecipes() {
        long count = 0;
        for (Iterator<Recipe> it = getServer().recipeIterator(); it.hasNext(); ) {
            if (it.next() instanceof Keyed keyed && keyed.getKey().getNamespace().equals(ownNamespace())) count++;
        }
        return count;
    }

    private String ownNamespace() {
        return new NamespacedKey(this, "x").getNamespace();
    }

    private static Material[] grid(Material... slots) {
        return slots;
    }

    private static ItemStack[] items(Material[] slots) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) matrix[i] = i < slots.length && slots[i] != null ? ItemStack.of(slots[i]) : ItemStack.empty();
        return matrix;
    }

    // --- beacons ---

    private void beacons() {
        expectInvalid("beam-toggle-enabled: true", "beam-toggle-enabled");
        expectInvalid("effect-refresh-ticks: 10", "effect-refresh-ticks");
        expectInvalid("allow-covered-effects: \"yes\"", "allow-covered-effects");

        world.setChunkForceLoaded(BX >> 4, BZ >> 4, true);
        for (int y = BY - 5; y <= world.getMaxHeight() - 1; y++)
            for (int x = BX - 5; x <= BX + 5; x++)
                for (int z = BZ - 5; z <= BZ + 5; z++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        pyramid(Material.IRON_BLOCK);
        Block beacon = world.getBlockAt(BX, BY, BZ);
        beacon.setType(Material.BEACON, false);

        beacons = new BeaconModule();
        beacons.enable(context(yaml("effect-refresh-ticks: 20")));
        check(beacons.beacons.contains(BeaconModule.Pos.of(beacon)), "pre-existing beacon registered from loaded chunk");

        BeaconModule.Status open = beacons.status(beacon);
        check(!open.covered() && open.tier() == 4, "free sky, tier 4: " + open);
        roof(3, Material.AIR, false);
        roof(3, Material.STONE, true);
        roof(3, Material.TINTED_GLASS, true);
        roof(3, Material.GLASS, false);
        roof(3, Material.RED_STAINED_GLASS, false);
        roof(3, Material.BEDROCK, false);
        roof(3, Material.WATER, false);
        roof(60, Material.DEEPSLATE, true);
        roof(3, Material.OAK_SLAB, true); // superset: vanilla lets the beam pass, both then give the same effects
        roof(3, Material.AIR, false);
        getLogger().info("V15_PROBE_INFO leaves count as covered: " + covered(Material.OAK_LEAVES)
                + ", vanilla tier while open: " + ((Beacon) beacon.getState()).getTier());

        Beacon state = (Beacon) beacon.getState();
        state.setPrimaryEffect(PotionEffectType.HASTE);
        state.setSecondaryEffect(PotionEffectType.HASTE);
        state.update();
        world.getBlockAt(BX, BY + 3, BZ).setType(Material.STONE, false);
        // Never active yet, so the stored tier is 0 and Paper hides the secondary: only the snapshot can tell.
        BeaconModule.Status unknown = beacons.status(beacon);
        check(unknown.covered() && unknown.tier() == 4 && unknown.primary() == PotionEffectType.HASTE && unknown.secondary() == null,
                "stored tier 0 hides the secondary: " + unknown + " stored tier " + ((Beacon) beacon.getState()).getTier());
        beacons.rememberSelection(beacon, PotionEffectType.SPEED, PotionEffectType.SPEED);
        check(beacons.status(beacon).secondary() == null, "snapshot of another primary is ignored");
        beacons.rememberSelection(beacon, PotionEffectType.HASTE, PotionEffectType.HASTE);
        BeaconModule.Status covered = beacons.status(beacon);
        check(covered.covered() && covered.tier() == 4 && covered.primary() == PotionEffectType.HASTE
                && covered.grants().equals(List.of(new BeaconRules.Grant<>(PotionEffectType.HASTE, 1))), "covered tier 4 Haste II: " + covered);
        world.getBlockAt(BX + 3, BY - 3, BZ).setType(Material.DIRT, false);
        BeaconModule.Status lower = beacons.status(beacon);
        check(lower.tier() == 2 && lower.grants().equals(List.of(new BeaconRules.Grant<>(PotionEffectType.HASTE, 0))), "gap in layer 3: tier 2, Haste I: " + lower);
        world.getBlockAt(BX + 3, BY - 3, BZ).setType(Material.GOLD_BLOCK, false);
        check(beacons.status(beacon).tier() == 4, "mixed base materials count");

        check(beacons.owner(beacon).isEmpty(), "no owner without placement");
        Beacon raw = (Beacon) beacon.getState();
        raw.getPersistentDataContainer().set(new NamespacedKey(this, "beacon_owner"), PersistentDataType.STRING, "not-a-uuid");
        raw.update();
        check(beacons.owner(beacon).isEmpty(), "damaged owner entry grants nobody");
        beacons.setOwner(beacon, OWNER);
        check(beacons.owner(beacon).orElseThrow().equals(OWNER), "owner stored");
        check(((Beacon) beacon.getState()).getPrimaryEffect().getType() == PotionEffectType.HASTE, "owner write keeps the effect selection");

        registryFollowsChunks();
    }

    /** A second beacon in an ordinary chunk leaves the registry on unload and returns on load. */
    private void registryFollowsChunks() {
        int x = 20_008, z = 20_008;
        Block other = world.getBlockAt(x, BY, z);
        other.setType(Material.BEACON, false);
        BeaconModule.Pos pos = BeaconModule.Pos.of(other);
        check(!beacons.beacons.contains(pos), "setType alone does not register (no place event)");
        world.unloadChunk(x >> 4, z >> 4, true);
        await(() -> !world.isChunkLoaded(x >> 4, z >> 4), 400, "chunk unloaded", () -> {
            check(!beacons.beacons.contains(pos), "removed on unload");
            world.addPluginChunkTicket(x >> 4, z >> 4, this); // without a ticket the chunk unloads again at once
            await(() -> beacons.beacons.contains(pos), 40, "registered again on chunk load", this::furnace);
        });
    }

    private void furnace() {
        Block furnace = world.getBlockAt(BX + 5, BY - 5, BZ + 5);
        Block smoker = world.getBlockAt(BX - 5, BY - 5, BZ - 5);
        furnace.setType(Material.FURNACE, false);
        smoker.setType(Material.SMOKER, false);
        for (Block block : List.of(furnace, smoker)) {
            var inventory = ((org.bukkit.block.Furnace) block.getState()).getInventory();
            inventory.setSmelting(ItemStack.of(Material.FERN));
            inventory.setFuel(ItemStack.of(Material.COAL));
        }
        await(() -> ((Furnace) furnace.getState()).getInventory().getResult() != null, 400, "furnace smelted fern", () -> {
            Furnace done = (Furnace) furnace.getState();
            check(done.getInventory().getResult().equals(ItemStack.of(Material.DEAD_BUSH)), "one dead bush: " + done.getInventory().getResult());
            Furnace smoked = (Furnace) smoker.getState();
            check(smoked.getInventory().getResult() == null && smoked.getInventory().getSmelting().getType() == Material.FERN, "smoker does not cook fern");
            storedTierPath();
        });
    }

    /** Vanilla stores tier 4 while the sky is free; under a roof that stays and shows the selection without snapshot. */
    private void storedTierPath() {
        Block beacon = world.getBlockAt(BX, BY, BZ);
        world.getBlockAt(BX, BY + 3, BZ).setType(Material.AIR, false);
        await(() -> ((Beacon) beacon.getState()).getTier() == 4, 400, "vanilla tier 4 with free sky", () -> {
            Beacon state = (Beacon) beacon.getState();
            state.getPersistentDataContainer().remove(new NamespacedKey(this, "beacon_selection"));
            state.update();
            world.getBlockAt(BX, BY + 3, BZ).setType(Material.STONE, false);
            check(beacons.status(beacon).grants().equals(List.of(new BeaconRules.Grant<>(PotionEffectType.HASTE, 1))), "Haste II from stored tier 4");
            Beacon speed = (Beacon) beacon.getState();
            speed.setPrimaryEffect(PotionEffectType.SPEED);
            speed.setSecondaryEffect(PotionEffectType.REGENERATION);
            speed.update();
            check(beacons.status(beacon).grants().equals(List.of(new BeaconRules.Grant<>(PotionEffectType.SPEED, 0),
                    new BeaconRules.Grant<>(PotionEffectType.REGENERATION, 0))), "Speed + Regeneration from stored tier 4");
            Beacon haste = (Beacon) beacon.getState();
            haste.setPrimaryEffect(PotionEffectType.HASTE);
            haste.setSecondaryEffect(PotionEffectType.HASTE);
            haste.update();
            beacons.disable();
            try {
                Files.createDirectories(getDataPath());
                Files.writeString(getDataPath().resolve("restart-marker"), "beacon " + BX + " " + BY + " " + BZ);
            } catch (Exception e) {
                fail(e);
                return;
            }
            getLogger().info("V15_PROBE_PASS: six recipes incl. real furnace smelt, beacon config, tiers, roofs, selection snapshot and stored tier, owner PDC, chunk registry; restart to verify persistence");
            Bukkit.shutdown();
        });
    }

    private void restart() {
        beacons = new BeaconModule();
        beacons.enable(context(new YamlConfiguration()));
        Block beacon = world.getBlockAt(BX, BY, BZ);
        check(beacon.getType() == Material.BEACON, "beacon still there");
        check(beacons.beacons.contains(BeaconModule.Pos.of(beacon)), "registered after restart");
        BeaconModule.Status status = beacons.status(beacon);
        check(status.covered() && status.tier() == 4 && status.grants().equals(List.of(new BeaconRules.Grant<>(PotionEffectType.HASTE, 1))),
                "covered Haste II after restart: " + status);
        check(beacons.owner(beacon).orElseThrow().equals(OWNER), "owner after restart");
        getLogger().info("V15_PROBE_INFO vanilla tier while covered after restart: " + ((Beacon) beacon.getState()).getTier());
        beacons.disable();
        getLogger().info("V15_PROBE_RESTART_PASS: covered tier-4 beacon keeps Haste II and owner after a real restart");
        Bukkit.shutdown();
    }

    private void pyramid(Material material) {
        for (int layer = 1; layer <= 4; layer++)
            for (int x = BX - layer; x <= BX + layer; x++)
                for (int z = BZ - layer; z <= BZ + layer; z++) world.getBlockAt(x, BY - layer, z).setType(material, false);
    }

    private void roof(int height, Material material, boolean expected) {
        check(covered(material, height) == expected, material + " at +" + height + " covered=" + expected);
    }

    private boolean covered(Material material) {
        return covered(material, 3);
    }

    private boolean covered(Material material, int height) {
        Block roof = world.getBlockAt(BX, BY + height, BZ);
        roof.setType(material, false);
        boolean covered = BeaconModule.covered(world.getBlockAt(BX, BY, BZ));
        roof.setType(Material.AIR, false);
        return covered;
    }

    private void expectInvalid(String yaml, String key) {
        BeaconModule module = new BeaconModule();
        try {
            module.enable(context(yaml(yaml)));
            throw new AssertionError("accepted " + yaml);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(key), "error names " + key + ": " + expected.getMessage());
        } finally {
            module.disable();
        }
    }

    // --- plumbing ---

    private ModuleContext context(YamlConfiguration settings) {
        return new ModuleContext(this, settings, null, null, null, new VisibilityService(getServer()),
                new AdminCommand("probe", 0, new ArrayList<>()), task -> getServer().getScheduler().runTask(this, task));
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return yaml;
    }

    private void await(BooleanSupplier condition, int maxTicks, String what, Runnable then) {
        int[] ticks = {0};
        getServer().getScheduler().runTaskTimer(this, task -> {
            try {
                if (condition.getAsBoolean()) {
                    task.cancel();
                    then.run();
                } else if (++ticks[0] > maxTicks) {
                    task.cancel();
                    throw new AssertionError("timeout: " + what);
                }
            } catch (Throwable e) {
                task.cancel();
                fail(e);
            }
        }, 1, 1);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private void fail(Throwable error) {
        getLogger().log(Level.SEVERE, "V15_PROBE_FAIL", error);
        Bukkit.shutdown();
    }
}
