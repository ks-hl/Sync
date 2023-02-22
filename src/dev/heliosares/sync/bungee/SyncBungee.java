package dev.heliosares.sync.bungee;

import dev.heliosares.sync.BungeeSender;
import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.heliosares.sync.utils.EncryptionRSA;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class SyncBungee extends Plugin implements SyncCoreProxy {
    private static SyncBungee instance;
    protected Configuration config;
    SyncServer sync;
    boolean debug;
    private EncryptionRSA encryptionRSA;

    public static SyncBungee getInstance() {
        return instance;
    }

    public static void tell(CommandSender sender, String msg) {
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
    }

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();

        print("Enabling");
        getProxy().getPluginManager().registerCommand(this, new MSyncCommand("msync", this));
        getProxy().getPluginManager().registerCommand(this, new MTellCommand("mtell", this));
        File file = new File(getDataFolder(), "private.key");
        if (!file.exists()) {
            print("Key does not exist, regenerating...");
            File publicKeyFile = new File(getDataFolder(), "public.key");
            try {
                boolean ignored = file.createNewFile();
                if (!publicKeyFile.exists()) {
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                KeyPair pair = EncryptionRSA.generate();
                EncryptionRSA.write(file, pair.getPrivate());
                EncryptionRSA.write(publicKeyFile, pair.getPublic());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            print("Keys generated successfully. Please copy 'Sync/public.key' to all Spigot servers");
        }
        try {
            encryptionRSA = new EncryptionRSA(EncryptionRSA.loadPrivateKey(file));
        } catch (FileNotFoundException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
        sync = new SyncServer(this, encryptionRSA);
        sync.start(config.getInt("port", 8001));

        sync.getEventHandler().registerListener(Packets.MESSAGE.id, null, (server, packet) -> {
            @Nullable String msg = packet.getPayload().optString("msg", null);
            @Nullable String json = packet.getPayload().optString("json", null);
            @Nullable String node = packet.getPayload().optString("node", null);
            @Nullable String to = packet.getPayload().optString("to", null);
            boolean others_only = packet.getPayload().optBoolean("others_only");
            if (to != null) {
                ProxiedPlayer toPlayer = getProxy().getPlayer(packet.getPayload().getString("to"));
                if (toPlayer == null) {
                    try {
                        toPlayer = getProxy().getPlayer(UUID.fromString(to));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (toPlayer != null && (node == null || toPlayer.hasPermission(node))) tell(toPlayer, msg);
            } else {
                Consumer<ProxiedPlayer> send;
                if (msg != null) send = p -> tell(p, msg);
                else if (json != null) {
                    BaseComponent[] base = ComponentSerializer.parse(json);
                    send = p -> p.sendMessage(base);
                } else return;
                ServerInfo ignore = others_only ? getProxy().getServerInfo(server) : null;
                getProxy().getPlayers().stream()
                        .filter(p -> !p.getServer().getInfo().equals(ignore))
                        .filter(p -> node == null || p.hasPermission(node))
                        .forEach(send);
            }

        });
        sync.getEventHandler().registerListener(Packets.COMMAND.id, null, (server, packet) -> {
            try {
                String message = packet.getPayload().getString("command");

                print("Executing (from " + server + "): /" + message);

                message = message.replace("%server%", getSync().getName());

                Result playerR = CommandParser.parse("-p", message);

                CommandSender sender;
                if (playerR.value() == null) {
                    sender = new CustomBungeeCommandSender(s -> runAsync(() -> getSync().send(packet.createResponse(new JSONObject().put("msg", "§8[§7From " + SyncAPI.getInstance().getSync().getName() + "§8] §r" + s)))));
                } else {
                    sender = getProxy().getPlayer(playerR.value());
                    if (sender == null) {
                        getSync().send(packet.createResponse(new JSONObject().put("msg", "§8[§7From " + SyncAPI.getInstance().getSync().getName() + "§8] §cPlayer not found.")));
                        return;
                    }
                }
                debug("out: " + playerR.remaining());
                getProxy().getPluginManager().dispatchCommand(sender, playerR.remaining());
            } catch (Exception e) {
                getLogger().warning("Error while parsing: ");
                print(e);
            }
        });

        getProxy().getScheduler().schedule(this, () -> sync.keepalive(), 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        print("Closing");
        if (sync != null) {
            sync.close();
        }
    }

    public void loadConfig() {
        if (!getDataFolder().exists()) {
            boolean ignored = getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                print(e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            print(e);
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
        getProxy().getScheduler().runAsync(this, run);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getProxy().getScheduler().schedule(this, run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServerNameByPort(int port) {
        for (Entry<String, ServerInfo> info : getProxy().getServers().entrySet()) {
            if (info.getValue().getSocketAddress() instanceof InetSocketAddress) {
                if (port == ((InetSocketAddress) info.getValue().getSocketAddress()).getPort()) {
                    return info.getKey();
                }
            }
        }
        return null;
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
        ProxiedPlayer player = getProxy().getPlayer(name);
        return player == null ? null : new BungeeSender(this, player);
    }

    @Override
    public void dispatchCommand(MySender sender, String command) {
        getProxy().getPluginManager().dispatchCommand((CommandSender) sender.getSender(), command);
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public SyncServer getSync() {
        return sync;
    }

    @Override
    public List<PlayerData> getPlayers() {
        return null;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.BUNGEE;
    }
}
