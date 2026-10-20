package de.illunium.custom.recipes;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

/** Custom crafting recipes. Vanilla recipes are never touched; only keys in our namespace are added/removed. */
public final class RecipesModule implements FeatureModule {

    private final List<NamespacedKey> registered = new ArrayList<>();
    private Server server;

    @Override
    public String id() {
        return "recipes";
    }

    /** Extension point: append further recipes here. */
    static List<CraftingRecipe> recipes(Plugin plugin) {
        ShapelessRecipe quartz = new ShapelessRecipe(
                new NamespacedKey(plugin, "quartz_from_quartz_block"), ItemStack.of(Material.QUARTZ, 4));
        quartz.addIngredient(Material.QUARTZ_BLOCK);
        return List.of(quartz);
    }

    @Override
    public void enable(ModuleContext context) {
        server = context.plugin().getServer();
        for (CraftingRecipe recipe : recipes(context.plugin())) {
            // A leftover from an earlier enable in the same JVM (e.g. /reload) is replaced, not duplicated.
            server.removeRecipe(recipe.getKey());
            if (!server.addRecipe(recipe)) {
                throw new IllegalStateException("Could not register recipe " + recipe.getKey());
            }
            registered.add(recipe.getKey());
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
