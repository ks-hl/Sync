package dev.heliosares.sync.bungee;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.bungee.event.ClientConnectedEvent;
import dev.heliosares.sync.bungee.event.ClientDisconnectedEvent;
import dev.heliosares.sync.net.DisconnectReason;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.HasPermissionPacket;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.kshl.kshlib.encryption.EncryptionRSA;
import dev.kshl.kshlib.misc.Objects2;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.chat.ComponentSerializer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class SyncBungee extends Plugin implements SyncCoreProxy, Listener {
    private static SyncBungee instance;
    protected Configuration config;
    boolean debug;
    private SyncServer sync;

    public SyncBungee() {
        try {
            SyncAPI.setInstance(this);
        } catch (IllegalStateException e) {
            print("Second instance", e);
        }
    }

    public static SyncBungee getInstance() {
        return instance;
    }

    public static void tell(CommandSender sender, String msg) {
        if (sender.equals(getInstance().getProxy().getConsole())) {
            msg = ChatColor.stripColor(msg);
        } else {
            msg = ChatColor.translateAlternateColorCodes('&', msg);
        }
        sender.sendMessage(TextComponent.fromLegacyText(msg));
    }

    @Override
    public void onEnable() {
        instance = this;
        loadConfig();

        print("Enabling");
        getProxy().getPluginManager().registerCommand(this, new MSyncCommand("msync", this));
        getProxy().getPluginManager().registerCommand(this, new MTellCommand("mtell", this));
        getProxy().getPluginManager().registerListener(this, this);

        var p2pHostSection = config.getSection("p2p-hosts");
        Map<String, String> p2pHosts = new HashMap<>();
        if (p2pHostSection != null) {
            for (String key : p2pHostSection.getKeys()) {
                p2pHosts.put(key, p2pHostSection.getString(key));
            }
        }
        File privateKeyFile = new File(getDataFolder(), "private.key");
        if (!privateKeyFile.exists()) {
            print("Key does not exist, generating...");
            File publicKeyFile = new File(getDataFolder(), "server.key");
            try {
                boolean ignored = privateKeyFile.createNewFile();
                if (!publicKeyFile.exists()) {
                    boolean ignored2 = publicKeyFile.createNewFile();
                }
                EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
                pair.privateKey().write(privateKeyFile);
                pair.publicKey().write(publicKeyFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            print("Keys generated successfully. Please copy 'plugins/Sync/server.key' to each server under 'plugins/Sync/server.key'.");
        }
        try {
            sync = new SyncServer(this, p2pHosts, EncryptionRSA.load(privateKeyFile));
        } catch (FileNotFoundException ignored) {
        } catch (InvalidKeySpecException e) {
            warning("Invalid 'server.key'.");
            throw new RuntimeException(e);
        }
        reloadKeys(false);

        for (ProxiedPlayer player : getProxy().getPlayers()) {
            getSync().getUserManager().addPlayer(player.getName(), player.getUniqueId(), player.getServer().getInfo().getName(), false);
        }

        getSync().start(config.getString("host", null), config.getInt("port", 8001));

        getSync().getEventHandler().registerListener(PacketType.MESSAGE, null, (server, packet) -> {
            if (!(packet instanceof MessagePacket messagePacket)) return;
            @Nullable String msg = messagePacket.msg().get();
            @Nullable String json = messagePacket.json().get();
            @Nullable String node = messagePacket.node().get();
            @Nullable String to = messagePacket.to().get();
            Boolean others_only = messagePacket.otherServersOnly().get();
            if (to != null) {
                ProxiedPlayer toPlayer = getPlayer(messagePacket.to().get());
                if (toPlayer != null && (node == null || toPlayer.hasPermission(node))) tell(toPlayer, msg);
            } else {
                Consumer<ProxiedPlayer> send;
                if (msg != null) send = p -> tell(p, msg);
                else if (json != null) {
                    BaseComponent[] base = ComponentSerializer.parse(json);
                    send = p -> p.sendMessage(base);
                } else return;
                ServerInfo ignore = (others_only != null && others_only) ? getProxy().getServerInfo(server) : null;
                getProxy().getPlayers().stream().filter(p -> !p.getServer().getInfo().equals(ignore)).filter(p -> node == null || p.hasPermission(node)).forEach(send);
            }

        });
        getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (server, packet) -> {
            if (!(packet instanceof CommandPacket commandPacket)) return;
            try {
                String message = commandPacket.command().get();
                if (message == null) return;

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
                print("Error while parsing", e);
            }
        });
        getSync().getEventHandler().registerListener(PacketType.HAS_PERMISSION, null, ((server, packet_) -> {
            if (packet_.isResponse()) return;
            if (!(packet_ instanceof HasPermissionPacket packet)) return;
            ProxiedPlayer player = getPlayer(packet.player().get());
            if (player == null) return;
            String node = packet.node().get();
            if (node == null) return;
            HasPermissionPacket response = packet.createResponse(new JSONObject());
            response.result().set(player.hasPermission(node));
            sync.send(response);
        }));

        // TODO this is a bandaid, find cause of players not getting added on join
        getProxy().getScheduler().schedule(this, () -> {
            Set<UUID> online = new HashSet<>(getProxy().getPlayers().stream().map(ProxiedPlayer::getUniqueId).toList());
            Set<UUID> remove = new HashSet<>();
            for (PlayerData data : getSync().getUserManager().getAllPlayerData()) {
                if (!online.remove(data.getUUID())) remove.add(data.getUUID());
            }
            for (UUID uuid : online) {
                warning("Secondary addition of " + uuid);
                ProxiedPlayer player = getProxy().getPlayer(uuid);
                addPlayerData(player.getName(), uuid);
            }
            for (UUID uuid : remove) {
                warning("Secondary removal of " + uuid);
                removePlayerData(uuid);
            }
            for (PlayerData data : getSync().getUserManager().getAllPlayerData()) {
                ProxiedPlayer player = getProxy().getPlayer(data.getUUID());
                if (player == null) continue;

                String server = Objects2.mapIfNotNull(player.getServer(), s -> s.getInfo().getName());
                if (Objects.equals(data.getServer(), server)) continue;

                warning("Secondary set server of " + data.getName() + " to " + server);
                data.setServer(server);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        print("Closing");
        if (getSync() != null) {
            getSync().close();
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
                print("Error while copying config.yml", e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            print("Error while loading config.yml", e);
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
        getProxy().getScheduler().runAsync(this, run);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getProxy().getScheduler().schedule(this, run, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void scheduleAsync(Runnable run, long delay) {
        getProxy().getScheduler().schedule(this, run, delay, TimeUnit.MILLISECONDS);
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
        getProxy().getPluginManager().dispatchCommand((CommandSender) sender.getSender(), command);
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public void onNewPlayerData(PlayerData data) {
    }

    public void reloadKeys(boolean print) {
        Set<EncryptionRSA> clientEncryptionRSA = new HashSet<>();
        File clientsDir = new File(getDataFolder(), "clients");
        if (clientsDir.exists()) {
            File[] files = clientsDir.listFiles();
            if (files != null) for (File listFile : files) {
                if (listFile.isFile() && listFile.getName().toLowerCase().endsWith(".public.key")) {
                    try {
                        EncryptionRSA rsa = EncryptionRSA.load(listFile);
                        clientEncryptionRSA.add(rsa);
                        if (print) print("Loaded key for " + rsa.getUser());
                    } catch (FileNotFoundException | InvalidKeySpecException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } else {
            boolean ignored = clientsDir.mkdir();
        }
        getSync().setClientEncryptionRSA(clientEncryptionRSA);
    }

    @Override
    public boolean hasWritePermission(String user) {
        return !config.getStringList("read-only").contains(user);
    }

    @Override
    public void callConnectEvent(String server, String ip, boolean readOnly) {
        getProxy().getPluginManager().callEvent(new ClientConnectedEvent(server, ip, readOnly));
    }

    @Override
    public void callDisconnectEvent(String server, DisconnectReason reason) {
        getProxy().getPluginManager().callEvent(new ClientDisconnectedEvent(server, reason));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return getProxy().getPlayer(uuid) != null;
    }

    private void addPlayerData(String name, UUID uuid) {
        getSync().getUserManager().addPlayer(name, uuid, "proxy", true);
    }

    private void removePlayerData(UUID uuid) {
        getSync().getUserManager().removePlayer(uuid);
    }

    @EventHandler
    public void on(LoginEvent e) {
        addPlayerData(e.getConnection().getName(), e.getConnection().getUniqueId());
    }

    @EventHandler
    public void on(PlayerDisconnectEvent e) {
        removePlayerData(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void on(ServerConnectedEvent e) {
        PlayerData data = getSync().getUserManager().getPlayer(e.getPlayer().getUniqueId());
        if (data == null) {
            warning("No player data for server switch of " + e.getPlayer().getName());
            return;
        }
        data.setServer(e.getServer().getInfo().getName());
    }

    private ProxiedPlayer getPlayer(String value) {
        ProxiedPlayer toPlayer = getProxy().getPlayer(value);
        if (toPlayer != null) return toPlayer;
        try {
            return getProxy().getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
    public PlatformType getPlatformType() {
        return PlatformType.BUNGEE;
    }
}
