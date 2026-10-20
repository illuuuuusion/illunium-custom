package de.illunium.custom.core.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;

/** {@code /illuniumcustom} ({@code /ic}): admin diagnostics plus subcommands contributed by modules. */
public final class AdminCommand implements BasicCommand {

    private final String version;
    private final int schemaVersion;
    private final List<String> enabledModules;
    private final Map<String, BasicCommand> subcommands = new LinkedHashMap<>();

    /** {@code enabledModules} is read on each {@code /ic status}, so it may still be filled during startup. */
    public AdminCommand(String version, int schemaVersion, List<String> enabledModules) {
        this.version = version;
        this.schemaVersion = schemaVersion;
        this.enabledModules = enabledModules;
    }

    /** Adds {@code /ic <name> ...}; the handler gets the arguments after {@code name}. Gated by {@link #permission()}. */
    public void register(String name, BasicCommand handler) {
        subcommands.put(name, handler);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) throws CommandSyntaxException {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            source.getSender().sendMessage(Component.text("IlluniumCustom " + version + " | schema v" + schemaVersion
                    + " | modules: " + (enabledModules.isEmpty() ? "none" : String.join(", ", enabledModules))));
            return;
        }
        BasicCommand handler = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (handler == null) {
            source.getSender().sendMessage(Component.text("Usage: /ic " + String.join("|", names().toList())));
            return;
        }
        handler.execute(source, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return Chat.complete(args, names());
        }
        BasicCommand handler = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return handler == null ? List.of() : handler.suggest(source, Arrays.copyOfRange(args, 1, args.length));
    }

    private Stream<String> names() {
        return Stream.concat(Stream.of("status"), subcommands.keySet().stream());
    }

    @Override
    public String permission() {
        return "illuniumcustom.admin";
    }
}
