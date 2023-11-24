package dev.heliosares.sync.spigot;

import dev.heliosares.sync.utils.FormulaParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SpigotFormulaParser extends FormulaParser {

    public SpigotFormulaParser(String equation) {
        super(equation);
    }

    @Override
    public boolean hasPermission(String uuidOrName, String node) {
        Player player = Bukkit.getPlayer(uuidOrName);
        if (player != null) return player.hasPermission(node);

        try {
            player = Bukkit.getPlayer(UUID.fromString(uuidOrName));
            if (player != null) return player.hasPermission(node);
        } catch (IllegalArgumentException ignored) {
        }

        return false;
    }
}
