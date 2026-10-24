package de.illunium.custom;

import de.illunium.custom.appearance.AppearanceService;
import de.illunium.custom.core.VisibilityService;
import de.illunium.custom.core.command.AdminCommand;
import de.illunium.custom.core.config.PluginConfig;
import de.illunium.custom.core.identity.PlayerIdentityService;
import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import de.illunium.custom.core.module.Modules;
import de.illunium.custom.storage.Database;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

public final class IlluniumCustomPlugin extends JavaPlugin {

    private Database database;
    private AppearanceService appearance;
    private final Deque<FeatureModule> startedModules = new ArrayDeque<>();

    @Override
    public void onEnable() {
        try {
            start();
        } catch (Exception e) {
            // No partial start: a failing core or module disables the whole plugin.
            getLogger().log(Level.SEVERE, "Startup failed, disabling IlluniumCustom: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void start() throws Exception {
        saveDefaultConfig();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(getDataPath().resolve("config.yml").toFile());

        List<FeatureModule> modules = Modules.create();
        PluginConfig config = PluginConfig.load(yaml, modules.stream().map(FeatureModule::id).toList());

        database = Database.open(getDataPath().resolve(config.storageFile()));
        PlayerIdentityService identities = PlayerIdentityService.load(database, getLogger());
        getServer().getPluginManager().registerEvents(identities, this);
        for (Player player : getServer().getOnlinePlayers()) {
            identities.remember(player.getUniqueId(), player.getName());
        }
        appearance = AppearanceService.start(this);
        VisibilityService visibility = new VisibilityService(getServer());

        Executor mainThread = task -> {
            if (!isEnabled()) {
                return;
            }
            try {
                getServer().getScheduler().runTask(this, task);
            } catch (IllegalPluginAccessException ignored) {
                // Plugin is disabling; the DB work already finished, only the follow-up is dropped.
            }
        };

        List<String> enabled = new ArrayList<>();
        AdminCommand admin = new AdminCommand(getPluginMeta().getVersion(), database.schemaVersion(), enabled);
        for (FeatureModule module : modules) {
            if (!config.moduleEnabled(module.id())) {
                getLogger().info("Module " + module.id() + " disabled by config");
                continue;
            }
            if (yaml.contains(module.id()) && !yaml.isConfigurationSection(module.id())) {
                throw new IllegalArgumentException(module.id() + " must be a configuration section");
            }
            startedModules.push(module);
            module.enable(new ModuleContext(
                    this,
                    Objects.requireNonNullElseGet(yaml.getConfigurationSection(module.id()), () -> yaml.createSection(module.id())),
                    database,
                    identities,
                    appearance,
                    visibility,
                    admin,
                    mainThread
            ));
            enabled.add(module.id());
            getLogger().info("Module " + module.id() + " enabled");
        }

        registerCommand("illuniumcustom", "IlluniumCustom diagnostics", List.of("ic"), admin);
        getLogger().info("IlluniumCustom " + getPluginMeta().getVersion() + " enabled (schema v"
                + database.schemaVersion() + ", modules: " + enabled + ")");
    }

    @Override
    public void onDisable() {
        // Reverse order: modules flush their data through the still-open database first, then nametags are reset.
        while (!startedModules.isEmpty()) {
            FeatureModule module = startedModules.pop();
            try {
                module.disable();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Module " + module.id() + " failed to disable cleanly", e);
            }
        }
        if (appearance != null) {
            try {
                appearance.close();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Appearance did not clean up", e);
            }
            appearance = null;
        }
        if (database != null) {
            try {
                database.close();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Database did not close cleanly", e);
            }
            database = null;
        }
        getLogger().info("IlluniumCustom disabled");
    }
}
