package de.illunium.custom.recipes;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/** Custom recipes. Vanilla recipes are never touched; only keys in our namespace are added/removed. */
public final class RecipesModule implements FeatureModule {

    private final List<NamespacedKey> registered = new ArrayList<>();
    private Server server;

    @Override
    public String id() {
        return "recipes";
    }

    /** Every entry is {@link Keyed}. Extension point: append further recipes here. */
    static List<Recipe> recipes(Plugin plugin) {
        return List.of(
                shapeless(plugin, "quartz_from_quartz_block", Material.QUARTZ, 4, Material.QUARTZ_BLOCK),
                shapeless(plugin, "tuff_from_basalt_gravel", Material.TUFF, 4,
                        Material.BASALT, Material.BASALT, Material.GRAVEL, Material.GRAVEL),
                shapeless(plugin, "cinnabar_from_sulfur_dye", Material.CINNABAR, 4,
                        Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.SULFUR, Material.RED_DYE),
                shapeless(plugin, "calcite_from_stone_bonemeal", Material.CALCITE, 2,
                        Material.STONE, Material.STONE, Material.BONE_MEAL, Material.BONE_MEAL),
                shapeless(plugin, "deepslate_from_cobble_basalt", Material.COBBLED_DEEPSLATE, 4,
                        Material.COBBLESTONE, Material.COBBLESTONE, Material.BASALT, Material.BASALT),
                new FurnaceRecipe(new NamespacedKey(plugin, "dead_bush_from_fern"),
                        ItemStack.of(Material.DEAD_BUSH), Material.FERN, 0f, 200));
    }

    /** One {@code addIngredient} per item, so each listed material occupies its own grid slot. */
    private static ShapelessRecipe shapeless(Plugin plugin, String key, Material result, int amount, Material... ingredients) {
        ShapelessRecipe recipe = new ShapelessRecipe(new NamespacedKey(plugin, key), ItemStack.of(result, amount));
        for (Material ingredient : ingredients) {
            recipe.addIngredient(ingredient);
        }
        return recipe;
    }

    @Override
    public void enable(ModuleContext context) {
        server = context.plugin().getServer();
        for (Recipe recipe : recipes(context.plugin())) {
            NamespacedKey key = ((Keyed) recipe).getKey();
            // A leftover from an earlier enable in the same JVM (e.g. /reload) is replaced, not duplicated.
            server.removeRecipe(key);
            if (!server.addRecipe(recipe)) {
                throw new IllegalStateException("Could not register recipe " + key);
            }
            registered.add(key);
        }
    }

    @Override
    public void disable() {
        for (NamespacedKey key : registered) {
            server.removeRecipe(key);
        }
        registered.clear();
    }
}
