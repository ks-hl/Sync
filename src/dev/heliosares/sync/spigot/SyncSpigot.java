package dev.heliosares.sync.spigot;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SpigotSender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.heliosares.sync.utils.EncryptionRSA;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SyncSpigot extends JavaPlugin implements SyncCore, Listener {
    private static SyncSpigot instance;
    private SyncClient sync;
    private boolean debug;

    public static SyncSpigot getInstance() {
        return instance;
    }

    public static boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean())
                return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.getConfig().options().copyDefaults(true);
        this.saveDefaultConfig();

        SpigotCommandListener cmd = new SpigotCommandListener(this);
        Objects.requireNonNull(this.getCommand("psync")).setExecutor(cmd);
        Objects.requireNonNull(this.getCommand("psync")).setTabCompleter(cmd);
        Objects.requireNonNull(this.getCommand("if")).setExecutor(cmd);
        Objects.requireNonNull(this.getCommand("mtell")).setExecutor(cmd);
        this.getServer().getPluginManager().registerEvents(this, this);
        try {
            this.getServer().getPluginManager().registerEvents(new VanishListener(this), this);
        } catch (Throwable ignored) {
        }

        File keyFile = new File(getDataFolder(), "public.key");
        if (!keyFile.exists()) {
            warning("Key file does not exist. Please copy it from the proxy.");
            setEnabled(false);
            return;
        }

        try {
            sync = new SyncClient(this, new EncryptionRSA(EncryptionRSA.loadPublicKey(keyFile)));
        } catch (FileNotFoundException | InvalidKeySpecException e) {
            warning("Failed to load key file. Ensure it was correctly copied from the proxy.");
            print(e);
            setEnabled(false);
            return;
        }
        sync.start(getConfig().getInt("port", 8001), this.getServer().getPort());
        sync.getEventHandler().registerListener(Packets.PLAY_SOUND.id, null, (server, packet) -> {
            Sound sound;
            float pitch = 1f;
            float volume = 1f;
            if (packet.getPayload().has("pitch")) pitch = packet.getPayload().getFloat("pitch");
            if (packet.getPayload().has("volume")) volume = packet.getPayload().getFloat("volume");
            try {
                sound = Sound.valueOf(packet.getPayload().getString("sound"));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            if (packet.getPayload().has("to")) {
                Player to = getServer().getPlayer(packet.getPayload().getString("to"));
                if (to == null) return;
                to.playSound(to, sound, pitch, volume);
            } else {
                for (Player player : getServer().getOnlinePlayers()) {
                    player.playSound(player, sound, pitch, volume);
                }
            }
        });

        sync.getEventHandler().registerListener(Packets.COMMAND.id, null, (server, packet) -> {
            try {
                if (packet.isResponse()) return;

                String message = packet.getPayload().getString("command");

                if (message.equals("-kill")) {
                    print("Killing");
                    System.exit(0);
                    return;
                } else if (message.equals("-halt")) {
                    print("Halting");
                    Runtime.getRuntime().halt(0);
                }

                print("Executing (from " + packet.getOrigin() + "): /" + message);

                message = message.replace("%server%", getSync().getName());

                Result playerR = CommandParser.parse("-p", message);
                CommandSender sender;
                if (playerR.value() == null) {
                    sender = new CustomCommandSender(s -> runAsync(() -> {
                        String response = "§8[§7From " + SyncAPI.getInstance().getSync().getName() + "§8] " + s;
                        try {
                            getSync().send(packet.createResponse(new JSONObject().put("msg", response)));
                        } catch (IOException e) {
                            print(e);
                        }
                    }));
                } else {
                    sender = getServer().getPlayer(playerR.value());
                    message = playerR.remaining();
                    if (sender == null) return;
                }
                dispatchCommand(sender, message);
            } catch (Exception e) {
                getLogger().warning("Error while parsing: ");
                print(e);
            }
        });

        sync.getEventHandler().registerListener(Packets.TITLE.id, null, (server, packet) -> {
            String title = packet.getPayload().has("title") ? packet.getPayload().getString("title") : "";
            String subtitle = packet.getPayload().has("subtitle") ? packet.getPayload().getString("subtitle") : "";
            int fadein = packet.getPayload().has("fadein") ? packet.getPayload().getInt("fadein") : 0;
            int duration = packet.getPayload().has("duration") ? packet.getPayload().getInt("duration") : 60;
            int fadeout = packet.getPayload().has("fadeout") ? packet.getPayload().getInt("fadeout") : 0;
            Consumer<Player> showTitle = player -> player.sendTitle(title, subtitle, fadein, duration, fadeout);
            if (packet.getPayload().has("to")) {
                Player to = getServer().getPlayer(UUID.fromString(packet.getPayload().getString("to")));
                if (to == null) return;
                showTitle.accept(to);
            } else {
                getServer().getOnlinePlayers().forEach(showTitle);
            }
        });

        new BukkitRunnable() {

            @Override
            public void run() {
                if (!sync.isConnected()) {
                    return;
                }
                try {
                    sync.keepalive();
                } catch (Exception e) {
                    warning("Error while sending keepalive:");
                    print(e);
                }
            }
        }.runTaskTimerAsynchronously(this, 20, 20);
    }

    @Override
    public void onDisable() {
        if (sync != null) {
            sync.close();
        }
    }

    @Override
    public void print(String msg) {
        getLogger().info(msg);
    }

    @Override
    public void print(Throwable t) {
        getLogger().log(Level.WARNING, t.getMessage(), t);
    }

    @Override
    public void debug(String msg) {
        if (debug) {
            print(msg);
        }
    }

    @Override
    public void newThread(Runnable run) {
        new Thread(run).start();
    }

    @Override
    public void runAsync(Runnable run) {
        getServer().getScheduler().runTaskAsynchronously(this, run);
    }

    @Override
    public void warning(String msg) {
        getLogger().warning(msg);
    }

    @Override
    public boolean debug() {
        return debug;
    }

    @Override
    public MySender getSender(String name) {
        Player player = getServer().getPlayer(name);
        return player == null ? null : new SpigotSender(player);
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {
        getServer().getScheduler().runTask(this, () -> sender.execute(command));
    }

    public PlayerData getPlayerData(Player p, boolean vanished) {
        return new PlayerData(this.sync.getName(), p.getName(), p.getUniqueId().toString(), vanished);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sync.getUserManager().updatePlayer(getPlayerData(e.getPlayer(), isVanished(e.getPlayer())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sync.getUserManager().quitPlayer(e.getPlayer().getUniqueId());
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getServer().getScheduler().runTaskTimerAsynchronously(this, run, delay / 50, period / 50);
    }

    private void dispatchCommand(CommandSender sender, String command) {
        new BukkitRunnable() {
            @Override
            public void run() {
                getServer().dispatchCommand(sender, command);
            }
        }.runTask(this);
    }

    @Override
    public SyncClient getSync() {
        return sync;
    }

    @Override
    public List<PlayerData> getPlayers() {
        return getServer().getOnlinePlayers().stream().map(p -> getPlayerData(p, isVanished(p)))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAsync() {
        return !Bukkit.isPrimaryThread();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SPIGOT;
    }
}
