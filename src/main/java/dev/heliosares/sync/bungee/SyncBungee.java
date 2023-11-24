package dev.heliosares.sync.bungee;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.heliosares.sync.utils.EncryptionRSA;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public class SyncBungee extends Plugin implements SyncCoreProxy, Listener {
    private static SyncBungee instance;
    protected Configuration config;
    private SyncServer sync;
    boolean debug;

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
        getProxy().getPluginManager().registerListener(this, this);

        sync = new SyncServer(this);
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
            boolean others_only = messagePacket.otherServersOnly().get();
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
                getProxy().getPlayers().stream().filter(p -> !p.getServer().getInfo().equals(ignore)).filter(p -> node == null || p.hasPermission(node)).forEach(send);
            }

        });
        getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (server, packet) -> {
            if (!(packet instanceof CommandPacket commandPacket)) return;
            try {
                String message = commandPacket.command().get();

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
                print(e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
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

    @EventHandler
    public void on(LoginEvent e) {
        getSync().getUserManager().addPlayer(e.getConnection().getName(), e.getConnection().getUniqueId(), "proxy", true);
    }

    @EventHandler
    public void on(PlayerDisconnectEvent e) {
        getSync().getUserManager().removePlayer(e.getPlayer().getUniqueId());
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
}
