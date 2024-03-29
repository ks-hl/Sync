package dev.heliosares.sync.spigot;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.net.packet.HasPermissionPacket;
import dev.heliosares.sync.net.packet.PlaySoundPacket;
import dev.heliosares.sync.net.packet.ShowTitlePacket;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.kshl.kshlib.encryption.EncryptionRSA;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class SyncSpigot extends JavaPlugin implements SyncCore, Listener {
    private static SyncSpigot instance;
    private SyncClient sync;
    private boolean debug = false;
    private Plugin essentials;

    public SyncSpigot() {
        try {
            SyncAPI.setInstance(this);
        } catch (IllegalStateException e) {
            print("Second instance", e);
        }
    }

    public static SyncSpigot getInstance() {
        return instance;
    }

    public static boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
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

        if ((essentials = getServer().getPluginManager().getPlugin("Essentials")) != null) {
            this.getServer().getPluginManager().registerEvents(new EssentialsListener(), this);
        }

        File clientKeyFile = new File(getDataFolder(), "private.key");
        if (!clientKeyFile.exists()) {
            print("Key does not exist, regenerating...");
            File publicKeyFile = new File(getDataFolder(), "SERVER_NAME.public.key");
            try {
                boolean ignored = clientKeyFile.createNewFile();
                if (!publicKeyFile.exists()) {
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
                pair.privateKey().write(clientKeyFile);
                pair.publicKey().write(publicKeyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            print("Keys generated successfully. Please copy 'plugins/Sync/SERVER_NAME.public.key' to the proxy under 'plugins/Sync/clients/SERVER_NAME.public.key', renaming 'SERVER_NAME' to the server's name");
        }
        File serverKeyFile = new File(getDataFolder(), "server.key");
        if (!serverKeyFile.exists()) {
            warning("Please copy 'server.key' from proxy to Sync folder.");
            setEnabled(false);
            return;
        }

        try {
            sync = new SyncClient(this, EncryptionRSA.load(clientKeyFile), EncryptionRSA.load(serverKeyFile));
        } catch (FileNotFoundException | InvalidKeySpecException e) {
            print("Failed to load key file. Ensure it was correctly copied from the proxy.", e);
            setEnabled(false);
            return;
        }

        sync.start(getConfig().getString("host", null), getConfig().getInt("port", 8001));

        sync.getEventHandler().registerListener(PacketType.COMMAND, null, (server, packet) -> {
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
                            print(null, e);
                        }
                    }));
                } else {
                    sender = getPlayer(playerR.value());
                    message = playerR.remaining();
                    if (sender == null) return;
                }
                dispatchCommand(sender, message);
            } catch (Exception e) {
                print("Error while parsing", e);
            }
        });

        sync.getEventHandler().registerListener(PacketType.SHOW_TITLE, null, (server, packet) -> {
            if (!(packet instanceof ShowTitlePacket titlePacket)) return;
            Consumer<Player> showTitle = player -> player.sendTitle(titlePacket.title().get(), titlePacket.subtitle().get(), titlePacket.fadeIn().get(0), titlePacket.duration().get(60), titlePacket.fadeOut().get(0));
            String to = titlePacket.to().get();
            if (to != null) {
                Player toPlayer = getPlayer(to);
                if (toPlayer == null) return;
                showTitle.accept(toPlayer);
            } else {
                getServer().getOnlinePlayers().forEach(showTitle);
            }
        });
        sync.getEventHandler().registerListener(PacketType.PLAY_SOUND, null, (server, packet) -> {
            if (!(packet instanceof PlaySoundPacket soundPacket)) return;
            Sound sound;
            try {
                sound = Sound.valueOf(soundPacket.sound().get("").toUpperCase());
            } catch (IllegalArgumentException e) {
                warning("Invalid sound '" + soundPacket.sound().get() + "' from " + server);
                return;
            }
            Consumer<Player> playSound = player -> player.playSound(player.getEyeLocation(), sound, soundPacket.volume().get(1d).floatValue(), soundPacket.pitch().get(1d).floatValue());
            String to = soundPacket.to().get();
            if (to != null) {
                Player toPlayer = getPlayer(to);
                if (toPlayer == null) return;
                playSound.accept(toPlayer);
            } else {
                getServer().getOnlinePlayers().forEach(playSound);
            }
        });
        sync.getEventHandler().registerListener(PacketType.HAS_PERMISSION, null, ((server, packet_) -> {
            if (packet_.isResponse()) return;
            if (!(packet_ instanceof HasPermissionPacket packet)) return;
            Player player = getPlayer(packet.player().get());
            if (player == null) return;
            String node = packet.node().get();
            if (node == null) return;
            HasPermissionPacket response = packet.createResponse(new JSONObject());
            response.result().set(player.hasPermission(node));
            sync.send(response);
        }));

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> getServer().getOnlinePlayers().forEach(this::updatePlayerData), 10, 10);
    }

    public Player getPlayer(String key) {
        try {
            return getServer().getPlayer(UUID.fromString(key));
        } catch (IllegalArgumentException ignored) {
        }
        return getServer().getPlayer(key);
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
    public void print(String message, Throwable t) {
        if (message == null) message = "";
        else message += ": ";
        message += t.getMessage();
        getLogger().log(Level.WARNING, message, t);
    }

    @Override
    public void debug(String msg) {
        debug(() -> msg);
    }

    @Override
    public void debug(Supplier<String> msgSupplier) {
        if (debug) {
            print(msgSupplier.get());
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
    public void dispatchCommand(MySender sender, String command) {
        getServer().getScheduler().runTask(this, () -> sender.execute(command));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        updatePlayerData(e.getPlayer());
    }

    private void updatePlayerData(Player player) {
        PlayerData data = sync.getUserManager().getPlayer(player.getUniqueId());
        if (data == null) {
            warning("Player " + player.getName() + " joined consume no player data.");
            return;
        }
        data.setVanished(isVanished(player));
        data.setHealth(player.getHealth());
        data.setSaturation(player.getSaturation());
        data.setFood(player.getFoodLevel());
        data.setGameMode(player.getGameMode().toString());

        if (essentials != null && essentials instanceof Essentials essentials_) {
            User user = essentials_.getUser(player);
            data.setAFK(user.isAfk());
            data.setNickname(user.getNickname());
        }
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getServer().getScheduler().runTaskTimerAsynchronously(this, run, delay / 50L, period / 50L);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay) {
        getServer().getScheduler().runTaskLaterAsynchronously(this, run, delay / 50L + 1);
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
    public boolean isAsync() {
        return !Bukkit.isPrimaryThread();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.SPIGOT;
    }

    @Override
    public void onNewPlayerData(PlayerData data) {
        Player player = getServer().getPlayer(data.getUUID());
        if (player == null) return;
        data.setVanished(isVanished(player));
    }
}
