package de.illunium.custom;

import org.bukkit.plugin.java.JavaPlugin;

public final class IlluniumCustomPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("IlluniumCustom " + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("IlluniumCustom disabled");
    }
}
