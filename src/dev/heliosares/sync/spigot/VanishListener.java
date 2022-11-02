package dev.heliosares.sync.spigot;

import org.bukkit.event.EventHandler;
import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;

import org.bukkit.event.Listener;

public class VanishListener implements Listener {

	private final SyncSpigot plugin;

	public VanishListener(SyncSpigot plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlayerVanish(PlayerHideEvent e) {
		plugin.getSync().getUserManager().updatePlayer(plugin.getPlayerData(e.getPlayer(), true));
	}

	@EventHandler
	public void onPlayerShow(PlayerShowEvent e) {
		plugin.getSync().getUserManager().updatePlayer(plugin.getPlayerData(e.getPlayer(), false));
	}
}
