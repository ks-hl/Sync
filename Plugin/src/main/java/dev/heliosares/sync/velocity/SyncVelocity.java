package dev.heliosares.sync.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.SyncVersion;
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
import dev.kshl.kshlib.platform.ColorTranslator;
import dev.kshl.kshlib.yaml.YamlConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.logging.Logger;

@Plugin(id = "sync", name = "Sync", version = SyncVersion.VERSION)
public class SyncVelocity implements SyncCoreProxy {
    private static SyncVelocity instance;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private YamlConfig config;
    boolean debug;
    private SyncServer sync;

    @Inject
    public SyncVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        try {
            SyncAPI.setInstance(this);
        } catch (IllegalStateException e) {
            print("Second instance", e);
        }
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public static SyncVelocity getInstance() {
        return instance;
    }

    public static void tell(CommandSource sender, String msg) {
        if (sender.equals(getInstance().getProxy().getConsoleCommandSource())) {
            msg = ColorTranslator.stripColor(msg);
        } else {
            msg = ColorTranslator.translateAlternateColorCodes(msg);
        }
        sender.sendMessage(Component.text(msg));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        loadConfig();

        print("Enabling");
        getProxy().getCommandManager().register("msync", new MSyncCommand(this));
        getProxy().getCommandManager().register("mtell", new MTellCommand(this));
//        getProxy().getEventManager().register(this, this);

        var p2pHostSection = config.getSection("p2p-hosts");
        Map<String, String> p2pHosts = new HashMap<>();
        if (p2pHostSection.isPresent()) {
            for (String key : p2pHostSection.get().getKeys(false)) {
                p2pHostSection.get().getString(key).ifPresent(val -> p2pHosts.put(key, val));
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

        for (Player player : getProxy().getAllPlayers()) {
            player.getCurrentServer().ifPresent(server ->
                    getSync().getUserManager().addPlayer(player.getUsername(), player.getUniqueId(), server.getServerInfo().getName(), false)
            );
        }

        getSync().start(config.getString("host").orElse(null), config.getInt("port").orElse(8001));

        getSync().getEventHandler().registerListener(PacketType.MESSAGE, null, (server, packet) -> {
            if (!(packet instanceof MessagePacket messagePacket)) return;
            @Nullable String msg = messagePacket.msg().get();
            @Nullable String json = messagePacket.json().get();
            @Nullable String node = messagePacket.node().get();
            @Nullable String to = messagePacket.to().get();
            Boolean others_only = messagePacket.otherServersOnly().get();
            if (to != null) {
                Player toPlayer = getPlayer(messagePacket.to().get());
                if (toPlayer != null && (node == null || toPlayer.hasPermission(node))) tell(toPlayer, msg);
            } else {
                Consumer<Player> send;
                if (msg != null) send = p -> tell(p, msg);
                else if (json != null) {
                    send = p -> p.sendMessage(JSONComponentSerializer.json().deserialize(json));
                } else return;
                ServerInfo ignore = (others_only != null && others_only) ? getProxy().getServer(server).map(RegisteredServer::getServerInfo).orElse(null) : null;
                getProxy().getAllPlayers().stream() //
                        .filter(p -> !p.getCurrentServer().map(server_ -> server_.getServerInfo().equals(ignore)).orElse(false)) //
                        .filter(p -> node == null || p.hasPermission(node)).forEach(send);
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

                CommandSource sender;
                if (playerR.value() == null) {
                    sender = new CustomVelocityCommandSender(getProxy().getConsoleCommandSource(), s -> runAsync(() -> getSync().send(packet.createResponse(new JSONObject().put("msg", "§8[§7From " + SyncAPI.getInstance().getSync().getName() + "§8] §r" + s)))));
                } else {
                    sender = getProxy().getPlayer(playerR.value()).orElse(null);
                    if (sender == null) {
                        getSync().send(packet.createResponse(new JSONObject().put("msg", "§8[§7From " + SyncAPI.getInstance().getSync().getName() + "§8] §cPlayer not found.")));
                        return;
                    }
                }
                debug("out: " + playerR.remaining());
                getProxy().getCommandManager().executeAsync(sender, playerR.remaining());
            } catch (Exception e) {
                print("Error while parsing", e);
            }
        });
        getSync().getEventHandler().registerListener(PacketType.HAS_PERMISSION, null, ((server, packet_) -> {
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

        // TODO this is a bandaid, find cause of players not getting added on join
        getProxy().getScheduler().buildTask(this, () -> {
            Set<UUID> online = new HashSet<>(getProxy().getAllPlayers().stream().map(Player::getUniqueId).toList());
            Set<UUID> remove = new HashSet<>();
            for (PlayerData data : getSync().getUserManager().getAllPlayerData()) {
                if (!online.remove(data.getUUID())) remove.add(data.getUUID());
            }
            for (UUID uuid : online) {
                warning("Secondary addition of " + uuid);
                getProxy().getPlayer(uuid).ifPresent(player -> addPlayerData(player.getUsername(), uuid));
            }
            for (UUID uuid : remove) {
                warning("Secondary removal of " + uuid);
                removePlayerData(uuid);
            }
            for (PlayerData data : getSync().getUserManager().getAllPlayerData()) {
                getProxy().getPlayer(data.getUUID()).flatMap(Player::getCurrentServer).ifPresent(server -> {
                    if (Objects.equals(data.getServer(), server.getServerInfo().getName())) return;

                    warning("Secondary set server of " + data.getName() + " to " + server);
                    data.setServer(server.getServerInfo().getName());
                });
            }
        }).repeat(3, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
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
            config = new YamlConfig(new File(getDataFolder(), "config.yml"));
            config.load(getResourceAsStream("config.yml"));
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
        getProxy().getScheduler().buildTask(this, run).schedule();
    }

    @Override
    public void scheduleAsync(Runnable run, long delay, long period) {
        getProxy().getScheduler().buildTask(this, run).delay(delay, TimeUnit.MILLISECONDS).repeat(period, TimeUnit.MILLISECONDS).schedule();
    }

    @Override
    public void scheduleAsync(Runnable run, long delay) {
        getProxy().getScheduler().buildTask(this, run).delay(delay, TimeUnit.MILLISECONDS);
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
        if (!(sender instanceof VelocitySender velocitySender)) return;
        getProxy().getCommandManager().executeAsync(velocitySender.getSender(), command);
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

    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public boolean hasWritePermission(String user) {
        return config.getStringList("read-only").filter(list -> list.contains(user)).isEmpty();
    }

    @Override
    public void callConnectEvent(String server, String ip, boolean readOnly) {
        // TODO
//        getProxy().getPluginManager().callEvent(new ClientConnectedEvent(server, ip, readOnly));
    }

    @Override
    public void callDisconnectEvent(String server, DisconnectReason reason) {
        // TODO
//        getProxy().getPluginManager().callEvent(new ClientDisconnectedEvent(server, reason));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return getProxy().getPlayer(uuid).isPresent();
    }

    private void addPlayerData(String name, UUID uuid) {
        getSync().getUserManager().addPlayer(name, uuid, "proxy", true);
    }

    private void removePlayerData(UUID uuid) {
        getSync().getUserManager().removePlayer(uuid);
    }

    @Subscribe
    public void on(LoginEvent e) {
        addPlayerData(e.getPlayer().getUsername(), e.getPlayer().getUniqueId());
    }

    @Subscribe
    public void on(DisconnectEvent e) {
        removePlayerData(e.getPlayer().getUniqueId());
    }

    @Subscribe
    public void on(ServerConnectedEvent e) {
        PlayerData data = getSync().getUserManager().getPlayer(e.getPlayer().getUniqueId());
        if (data == null) {
            warning("No player data for server switch of " + e.getPlayer().getUsername());
            return;
        }
        data.setServer(e.getServer().getServerInfo().getName());
    }

    private Player getPlayer(String value) {
        try {
            return getProxy().getPlayer(UUID.fromString(value)).orElse(null);
        } catch (IllegalArgumentException e) {
            return getProxy().getPlayer(value).orElse(null);
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
        return PlatformType.VELOCITY;
    }

    public ProxyServer getProxy() {
        return server;
    }

    public InputStream getResourceAsStream(String string) {
        return getClass().getClassLoader().getResourceAsStream(string);
    }
}
