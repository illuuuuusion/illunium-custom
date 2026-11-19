package de.illunium.custom.emotes;

import de.illunium.custom.core.module.FeatureModule;
import de.illunium.custom.core.module.ModuleContext;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * {@code :alias:} emotes in public chat and {@code /g} (Patch v1.5 §2). Public chat keeps the signed original; only a
 * message that actually contains a known alias gets new display content (the vanilla client marks it as modified).
 * Chat completions are added per player and removed again on disable, never replaced wholesale.
 */
public final class EmotesModule implements FeatureModule, Listener {

    private volatile EmoteFormatter publicChat = EmoteFormatter.NONE;
    private EmoteFormatter groupChat = EmoteFormatter.NONE;
    private List<String> completions = List.of();
    private ModuleContext context;

    @Override
    public String id() {
        return "emotes";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        ConfigurationSection settings = context.settings();
        if (settings.contains("aliases") && !settings.isConfigurationSection("aliases"))
            throw new IllegalArgumentException("emotes.aliases must be a section of alias: symbol");
        EmoteFormatter formatter = EmoteFormatter.parse(settings.getConfigurationSection("aliases"));
        publicChat = bool(settings, "enabled-in-public-chat") ? formatter : EmoteFormatter.NONE;
        groupChat = bool(settings, "enabled-in-group-chat") ? formatter : EmoteFormatter.NONE;
        completions = bool(settings, "tab-completion") ? formatter.completions() : List.of();
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        context.plugin().getServer().getOnlinePlayers().forEach(this::addCompletions);
    }

    /** For {@code /g}; {@link EmoteFormatter#NONE} while this module or its group chat switch is off. */
    public EmoteFormatter groupChat() {
        return groupChat;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        String shown = publicChat.format(typed);
        if (!shown.equals(typed)) event.message(Component.text(shown));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        addCompletions(event.getPlayer());
    }

    private void addCompletions(Player player) {
        if (!completions.isEmpty()) player.addCustomChatCompletions(completions);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (context != null && !completions.isEmpty()) {
            context.plugin().getServer().getOnlinePlayers().forEach(p -> p.removeCustomChatCompletions(completions));
        }
        publicChat = EmoteFormatter.NONE;
        groupChat = EmoteFormatter.NONE;
    }

    private static boolean bool(ConfigurationSection settings, String key) {
        if (settings.get(key, true) instanceof Boolean value) return value;
        throw new IllegalArgumentException("emotes." + key + " must be true or false");
    }
}
