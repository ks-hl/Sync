package dev.heliosares.sync.spigot;

import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

public class VanishListener implements Listener {

    private final SyncSpigot plugin;

    public VanishListener(SyncSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerVanish(PlayerHideEvent e) {
        Objects.requireNonNull(plugin.getSync().getUserManager().getPlayer(e.getPlayer().getUniqueId())).setVanished(true);
    }

    @EventHandler
    public void onPlayerShow(PlayerShowEvent e) {
        Objects.requireNonNull(plugin.getSync().getUserManager().getPlayer(e.getPlayer().getUniqueId())).setVanished(false);
    }
}
