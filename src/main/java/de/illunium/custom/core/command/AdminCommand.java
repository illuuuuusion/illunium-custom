package de.illunium.custom.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;

/** {@code /illuniumcustom} ({@code /ic}): admin diagnostics. */
public final class AdminCommand implements BasicCommand {

    private final String status;

    public AdminCommand(String version, int schemaVersion, List<String> enabledModules) {
        this.status = "IlluniumCustom " + version + " | schema v" + schemaVersion
                + " | modules: " + (enabledModules.isEmpty() ? "none" : String.join(", ", enabledModules));
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            source.getSender().sendMessage(Component.text(status));
        } else {
            source.getSender().sendMessage(Component.text("Usage: /ic status"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return args.length <= 1 && "status".startsWith(args.length == 0 ? "" : args[0].toLowerCase())
                ? List.of("status")
                : List.of();
    }

    @Override
    public String permission() {
        return "illuniumcustom.admin";
    }
}
