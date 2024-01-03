package dev.heliosares.sync.spigot;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.net.PlayerData;
import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EssentialsListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(AfkStatusChangeEvent e) {
        PlayerData playerData = SyncAPI.getPlayer(e.getAffected().getUUID());
        if (playerData == null) return;
        playerData.setAFK(e.getValue());
    }
}
