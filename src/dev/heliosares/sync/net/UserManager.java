package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager implements NetEventHandler.PacketConsumer {

    private final SyncNetCore sync;
    private final SyncCore plugin;

    /**
     * This field should not be used directly. Use {@link UserManager#withPlayers(Consumer)} instead.
     */
    private final Map<UUID, PlayerData> playersMap = new HashMap<>();
    private final ReentrantLock playersLock = new ReentrantLock();
    private int lastHash;

    public UserManager(SyncCore plugin, SyncNetCore client) {
        this.sync = client;
        this.plugin = plugin;

        if (sync instanceof SyncServer) {
            plugin.scheduleAsync(() -> {
                int hash = hashCode();
                if (hash == lastHash) return;

                try {
                    sync.send(new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("hash", hash)));
                    lastHash = hash;
                } catch (Exception e) {
                    plugin.print(e);
                }
            }, 3000, 3000);
        }
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
            if (packet.getForward() != null && !data.getServer().equals(packet.getForward())) {
                plugin.warning(packet.getForward() + " tried to update " + data.getName() + "'s data on server " + data.getServer());
                return;
            }
            data.handleUpdate(field, packet.getPayload().get(field));
        } else if (packet.getPayload().has("request") && plugin.getSync() instanceof SyncServer) {
            try {
                sendPlayers(packet.getForward(), packet);
            } catch (IOException e) {
                plugin.print(e);
            }
        } else if (packet.getPayload().has("join") || packet.getPayload().has("set")) {
            boolean set = packet.getPayload().has("set");
            JSONArray array = packet.getPayload().getJSONArray(set ? "set" : "join");
            withPlayers(players -> players.putAll(getPlayerData(array)));
        } else if (packet.getPayload().has("quit")) {
            JSONArray array = packet.getPayload().getJSONArray("quit");
            withPlayers(players -> array.toList().forEach(uuid -> players.remove(UUID.fromString((String) uuid))));
        }
    }

    @CheckReturnValue
    private Map<UUID, PlayerData> getPlayerData(JSONArray arr) {
        Map<UUID, PlayerData> list = new HashMap<>();
        arr.forEach(o -> {
            try {
                PlayerData data = new PlayerData(plugin, (JSONObject) o);
                list.put(data.getUUID(), data);
            } catch (JSONException ignored) {
            }
        });
        return list;
    }

    protected void sendUpdatePacket(JSONObject o) throws IOException {
        sync.send("all", new Packet(null, Packets.PLAYER_DATA.id, o));
    }

    private void withPlayers(Consumer<Map<UUID, PlayerData>> consumer) {
        try {
            if (!playersLock.tryLock(5000L, TimeUnit.MILLISECONDS)) return;
        } catch (InterruptedException e) {
            return;
        }
        try {
            consumer.accept(playersMap);
        } finally {
            playersLock.unlock();
        }
    }

    public void sendPlayers(@Nullable String server, @Nullable Packet requester) throws IOException {
        if (sync instanceof SyncClient) return; // clients shouldn't be sending player-data

        Collection<PlayerData> players = getPlayers(plugin.getSync().getName()).values();
        JSONObject payload = new JSONObject().put("set", new JSONArray(players.stream().map(PlayerData::toJSON).collect(Collectors.toList())));
        Packet packet;
        if (requester == null) {
            packet = new Packet(null, Packets.PLAYER_DATA.id, payload);
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
        AtomicReference<PlayerData> data = new AtomicReference<>();
        withPlayers(players -> data.set(players.get(uuid)));
        return data.get();
    }

    @CheckReturnValue
    @Nullable
    public PlayerData getPlayer(Predicate<PlayerData> predicate) {
        AtomicReference<PlayerData> data = new AtomicReference<>();
        withPlayers(players -> data.set(players.values().stream().filter(predicate).findFirst().orElse(null)));
        return data.get();
    }

    @CheckReturnValue
    public String toFormattedString() {
        AtomicReference<String> out = new AtomicReference<>();
        withPlayers(players -> {
            if (players.isEmpty()) {
                out.set("No servers");
                return;
            }
            Map<String, Map<UUID, PlayerData>> playersByServer = new HashMap<>();
            for (Entry<UUID, PlayerData> entry : players.entrySet()) {
                playersByServer.computeIfAbsent(entry.getValue().getServer(), a -> new HashMap<>()).put(entry.getKey(), entry.getValue());
            }
            StringBuilder build = new StringBuilder();
            for (Map.Entry<String, Map<UUID, PlayerData>> entry : playersByServer.entrySet()) {
                build.append("§6§l").append(entry.getKey()).append("§7: ");
                if (entry.getValue().isEmpty()) {
                    build.append("None\n");
                    continue;
                }
                build.append("\n");
                StringBuilder line = new StringBuilder();
                for (PlayerData p : entry.getValue().values()) {
                    if (line.length() > 100) {
                        build.append(line).append("\n");
                        line = new StringBuilder();
                    }
                    line.append(p.isVanished() ? "§c[V]§7" : "§7").append(p.getName()).append("§8[§7").append(p.getAlts() == null ? 0 : p.getAlts().size()).append("§8]").append(", ");
                }
                build.append(line).append("\n");
            }
            out.set(build.substring(0, build.length() - 1));
        });
        return out.get();
    }

    public void addPlayer(String name, UUID uuid, boolean sendPacket) {
        PlayerData data = new PlayerData(plugin, sync.getName(), name, uuid, false);
        withPlayers(players -> players.put(uuid, data));
        if (!sendPacket) return;
        plugin.debug("Sending join for " + data.getName());
        plugin.runAsync(() -> {
            try {
                sync.send("all", new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("join", new JSONArray().put(data.toJSON()))));
            } catch (JSONException | IOException e) {
                plugin.print(e);
            }
        });

    }

    public void removePlayer(UUID uuid) {
        plugin.debug("Sending quit for " + uuid.toString());
        plugin.runAsync(() -> {
            try {
                sync.send("all", new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("quit", new JSONArray().put(uuid.toString()))));
            } catch (JSONException | IOException e) {
                plugin.print(e);
            }
        });
    }

    protected void request() {
        try {
            sync.send(null, new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("request", 1)).setForward(sync.getName()));
        } catch (JSONException | IOException e) {
            plugin.print(e);
        }
    }

    @CheckReturnValue
    @SuppressWarnings("unused")
    public Set<PlayerData> getAllPlayerData() {
        AtomicReference<Set<PlayerData>> out = new AtomicReference<>();
        withPlayers(players -> out.set(new HashSet<>(players.values())));
        return out.get();
    }

    @CheckReturnValue
    public Map<UUID, PlayerData> getPlayers(String server) {
        if ("proxy".equals(server)) server = null;
        if (server != null && !plugin.getSync().getServers().contains(server) && !server.equals(plugin.getSync().getName()))
            throw new IllegalArgumentException("Unknown server: " + server);
        AtomicReference<Map<UUID, PlayerData>> out = new AtomicReference<>();
        final String server_ = server;
        withPlayers(players -> {
            if (server_ == null) out.set(new HashMap<>(players));
            else
                out.set(players.entrySet().stream().filter(entry -> entry.getValue().getServer().equals(server_)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        });
        return out.get();
    }

    @Override
    public int hashCode() {
        AtomicInteger hash = new AtomicInteger();
        withPlayers(players -> hash.set(players.values().stream().mapToInt(Object::hashCode).reduce(0, (a, b) -> a ^ b)));
        return hash.get();
    }
}
