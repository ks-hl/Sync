package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.packet.Packet;
import dev.kshl.kshlib.concurrent.ConcurrentMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager implements NetEventHandler.PacketConsumer {

    private final SyncNetCore sync;
    private final SyncCore plugin;
    private final ConcurrentMap<HashMap<UUID, PlayerData>, UUID, PlayerData> players = new ConcurrentMap<>(new HashMap<>());
    private int lastHash;

    public UserManager(SyncCore plugin, SyncNetCore client) {
        this.sync = client;
        this.plugin = plugin;

        if (sync instanceof SyncServer && plugin instanceof SyncCoreProxy syncCoreProxy) {
            plugin.print("Registering hash task");
            plugin.scheduleAsync(() -> {
                try {
                    Set<UUID> remove = new HashSet<>();
                    players.forEach((uuid, data) -> {
                        if (!syncCoreProxy.isOnline(uuid)) remove.add(uuid);
                    });
                    remove.forEach(this::removePlayer);
                    sendHash();
                } catch (Exception e) {
                    plugin.print(null, e);
                }
            }, 1000, 2000);
        }
    }

    public void sendHash() throws IOException {
        if (!(sync instanceof SyncServer)) throw new IllegalStateException("Can't send hash from client");

        int hash = UserManager.this.hashCode();
        plugin.debug("Considering hash, " + hash);
        if (hash == lastHash) {
            plugin.debug("same hash");
            return;
        }
        plugin.debug("sending");
        lastHash = hash;
        sync.send(new Packet(null, PacketType.PLAYER_DATA, new JSONObject().put("hash", hash)));
    }

    @Override
    public void execute(String server, Packet packet) {
        if (packet.getPayload().has("hash") && !packet.isResponse() && plugin.getSync() instanceof SyncClient) {
            int hash = packet.getPayload().getInt("hash");
            int myHash = hashCode();
            if (hash != myHash) request();
        } else if (packet.getPayload().has("update")) {
            String field = packet.getPayload().getString("update");
            UUID of = UUID.fromString(packet.getPayload().getString("uuid"));
            PlayerData data = getPlayer(of);
            if (data == null) {
                plugin.warning("Tried to update " + field + " of " + of + ", but no PlayerData was found.");
                return;
            }
            if (!field.startsWith("custom.") && packet.getForward() != null && !data.getServer().equals(packet.getForward())) {
                plugin.warning(packet.getForward() + " tried to update " + data.getName() + "'s data on server " + data.getServer());
                return;
            }
            data.handleUpdate(field, packet.getPayload());
        } else if (packet.getPayload().has("request") && plugin.getSync() instanceof SyncServer) {
            try {
                sendPlayers(server, packet);
            } catch (IOException e) {
                plugin.print(null, e);
            }
        } else if (packet.getPayload().has("join") || packet.getPayload().has("set")) {
            boolean set = packet.getPayload().has("set");
            JSONArray array = packet.getPayload().getJSONArray(set ? "set" : "join");
            players.consume(players -> {
                if (set) players.clear();
                players.putAll(getPlayerData(array));
            });
        } else if (packet.getPayload().has("quit")) {
            JSONArray array = packet.getPayload().getJSONArray("quit");
            players.consume(players -> array.toList().forEach(uuid -> players.remove(UUID.fromString((String) uuid))));
        }
    }

    @CheckReturnValue
    private Map<UUID, PlayerData> getPlayerData(JSONArray arr) {
        Map<UUID, PlayerData> list = new HashMap<>();
        arr.forEach(o -> {
            try {
                PlayerData data = new PlayerData(plugin, (JSONObject) o);
                list.put(data.getUUID(), data);
                plugin.onNewPlayerData(data);
            } catch (JSONException e) {
                plugin.print("Malformed PlayerData packet: " + ((JSONObject) o).toString(2), e);
            }
        });
        return list;
    }

    protected void sendUpdatePacket(JSONObject o) throws IOException {
        sync.send("all", new Packet(null, PacketType.PLAYER_DATA, o));
    }

    public void sendPlayers(@Nullable String server, @Nullable Packet requester) throws IOException {
        if (sync instanceof SyncClient) return; // clients shouldn't be sending player-data

        Collection<PlayerData> players = getPlayers(plugin.getSync().getName()).values();
        JSONObject payload = new JSONObject().put("set", new JSONArray(players.stream().map(PlayerData::toJSON).collect(Collectors.toList())));
        Packet packet;
        if (requester == null) {
            packet = new Packet(null, PacketType.PLAYER_DATA, payload);
        } else {
            packet = requester.createResponse(payload);
        }
        sync.send(server, packet);
    }

    @Nullable
    public PlayerData getPlayer(String name) {
        return getPlayer(d -> d.getName().equalsIgnoreCase(name));
    }

    @CheckReturnValue
    @Nullable
    public PlayerData getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    @CheckReturnValue
    @Nullable
    public PlayerData getPlayer(Predicate<PlayerData> predicate) {
        return players.function(map -> map.values().stream().filter(predicate).findAny().orElse(null));
    }

    public void makeFormattedString(Consumer<String> lineConsumer, BiConsumer<String, String> hoverConsumer) {
        players.consume(players -> {
            if (players.isEmpty()) {
                lineConsumer.accept("No servers");
                return;
            }
            lineConsumer.accept("Network Player Data:");
            Map<String, Map<UUID, PlayerData>> playersByServer = new HashMap<>();
            for (Entry<UUID, PlayerData> entry : players.entrySet()) {
                playersByServer.computeIfAbsent(entry.getValue().getServer(), a -> new HashMap<>()).put(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Map<UUID, PlayerData>> entry : playersByServer.entrySet()) {
                lineConsumer.accept("\n§6§l" + entry.getKey() + "§7: ");

                if (entry.getValue().isEmpty()) {
                    lineConsumer.accept("None\n");
                    continue;
                }
                lineConsumer.accept("\n");

                List<PlayerData> data = new ArrayList<>(entry.getValue().values());
                for (int i = 0; i < data.size(); i++) {
                    PlayerData playerData = data.get(i);
                    hoverConsumer.accept("§7" + playerData.getName(), playerData.toFormattedString());
                    if (i < data.size() - 1) {
                        lineConsumer.accept(", ");
                    }
                }
            }
        });
    }

    public void addPlayer(String name, UUID uuid, String server, boolean sendPacket) {
        PlayerData data = new PlayerData(plugin, server, name, uuid, false);

        players.put(uuid, data);

        if (!sendPacket) return;

        plugin.debug("Sending join for " + data.getName());
        plugin.runAsync(() -> {
            try {
                sync.send("all", new Packet(null, PacketType.PLAYER_DATA, new JSONObject().put("join", new JSONArray().put(data.toJSON()))));
            } catch (JSONException | IOException e) {
                plugin.print(null, e);
            }

            plugin.onNewPlayerData(data);
        });
    }

    public void removePlayer(UUID uuid) {
        plugin.debug("Sending quit for " + uuid.toString());
        players.remove(uuid);
        plugin.runAsync(() -> {
            try {
                sync.send("all", new Packet(null, PacketType.PLAYER_DATA, new JSONObject().put("quit", new JSONArray().put(uuid.toString()))));
            } catch (JSONException | IOException e) {
                plugin.print(null, e);
            }
        });
    }

    protected void request() {
        if (sync instanceof SyncServer) throw new IllegalStateException("Can't send request for user data from server");
        try {
            sync.send(null, new Packet(null, PacketType.PLAYER_DATA, new JSONObject().put("request", 1)));
        } catch (JSONException | IOException e) {
            plugin.print(null, e);
        }
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<PlayerData> getAllPlayerData() {
        return players.function(players -> new HashSet<>(players.values()));
    }

    @CheckReturnValue
    public Map<UUID, PlayerData> getPlayers(String server) {
        if ("proxy".equals(server)) server = null;
        if (server != null && !plugin.getSync().getServers().contains(server) && !server.equals(plugin.getSync().getName()))
            throw new IllegalArgumentException("Unknown server: " + server);
        final String server_ = server;
        return players.function(players -> {
            if (server_ == null) return new HashMap<>(players);
            return players.entrySet().stream().filter(entry -> entry.getValue().getServer().equals(server_)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
    }

    @Override
    public int hashCode() {
        return players.hashCode();
    }
}
