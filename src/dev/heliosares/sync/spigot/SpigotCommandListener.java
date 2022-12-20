package dev.heliosares.sync.spigot;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.FormulaParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpigotCommandListener implements CommandExecutor, TabCompleter {
    private final SyncCore plugin;

    public SpigotCommandListener(SyncCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, Command cmd, @Nonnull String commandLabel, @Nonnull String[] args) {
        if (cmd.getLabel().equalsIgnoreCase("psync")) {
            if (!sender.hasPermission("sync.psync")) {
                sender.sendMessage("§cNo permission");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§cInvalid syntax");
                return true;
            }

            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("-debug")) {
                    plugin.setDebug(!plugin.debug());
                    if (plugin.debug())
                        sender.sendMessage("§aDebug enabled");
                    else
                        sender.sendMessage("§cDebug disabled");
                    return true;
                } else if (args[0].equalsIgnoreCase("-list")) {
                    sender.sendMessage(plugin.getSync().getUserManager().toFormattedString());
                    return true;
                }
            }
            plugin.runAsync(() -> {
                try {
                    plugin.getSync().send(new Packet(null, Packets.COMMAND.id,
                            new JSONObject().put("command", CommandParser.concat(0, args))));
                } catch (Exception e) {
                    sender.sendMessage("§cAn error occured");
                    plugin.print(e);
                    return;
                }
                sender.sendMessage("§aCommand sent.");
            });
            return true;
        } else if (cmd.getLabel().equalsIgnoreCase("if")) {
            if (!sender.hasPermission("sync.if")) {
                sender.sendMessage("§cNo permission");
                return true;
            }
            StringBuilder condition = new StringBuilder();
            StringBuilder commandIf = new StringBuilder();
            StringBuilder commandElse = new StringBuilder();
            boolean then = false;
            boolean el = false;
            for (String part : args) {
                if (part.equalsIgnoreCase("then")) {
                    if (condition.length() == 0) {
                        sender.sendMessage("§cNo condition provided");
                        return true;
                    }
                    then = true;
                    continue;
                }
                part += " ";
                if (then) {
                    if (part.equalsIgnoreCase("else ")) {
                        el = true;
                        continue;
                    }
                    if (el) {
                        commandElse.append(part);
                    } else {
                        commandIf.append(part);
                    }
                } else {
                    condition.append(part);
                }
            }
            if (!then) {
                sender.sendMessage("§cNo 'then' provided.");
                return true;
            }
            if (commandIf.length() == 0) {
                sender.sendMessage("§cNo command provided.");
                return true;
            }
            FormulaParser parser = new FormulaParser(condition.toString());

            parser.setVariable("$online-players", () -> Bukkit.getOnlinePlayers().size());
            parser.setVariable("$sender", sender::getName);
            parser.setVariable("$server", () -> plugin.getSync().getName());

            String command;
            boolean state;
            try {
                state = parser.solve() == 1;
            } catch (RuntimeException e) {
                sender.sendMessage("§c" + e.getMessage());
                return true;
            }
            if (state) command = commandIf.toString();
            else command = commandElse.toString();

            if (command.length() > 0) {
                Arrays.stream(command.split(";")).map(String::trim).filter(c -> !c.isEmpty()).forEach(commandPart -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parser.replaceVariables(commandPart.trim()));
                    } catch (Exception e) {
                        sender.sendMessage("§c" + e.getMessage());
                    }
                });
            }
            return true;
        } else if (cmd.getLabel().equalsIgnoreCase("mtell")) {
            if (!sender.hasPermission("sync.mtell")) {
                sender.sendMessage("§cNo permission");
                return true;
            }


            StringBuilder msgBuilder = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) msgBuilder.append(" ").append(args[i]);
            final String msg = msgBuilder.toString();

            Player targetLocal = Bukkit.getPlayer(args[0]);
            if (targetLocal != null) {
                targetLocal.sendMessage(msg);
                return true;
            }

            plugin.runAsync(() -> {
                PlayerData target = SyncAPI.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found");
                    return;
                }
                try {
                    target.sendMessage(msg);
                } catch (Exception e) {
                    sender.sendMessage("§cAn error occured");
                    plugin.print(e);
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 0) {
            return out;
        }
        if (command.getLabel().equalsIgnoreCase("psync") && sender.hasPermission("sync.psync")) {
            if (args.length == 1) {
                out.add("-list");
            }
            if (args.length > 1 && args[args.length - 2].equalsIgnoreCase("-s")) {
                out.addAll(plugin.getSync().getServers());
            } else {
                out.add("-s");
                out.add("-p");
            }
        }
        return CommandParser.tab(out, args[args.length - 1]);
    }
}
