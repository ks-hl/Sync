package dev.heliosares.sync.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;

public class UserManager extends NetListener {

	private final SyncNetCore sync;
	private final SyncCore plugin;
	private final Map<String, List<PlayerData>> players = new HashMap<>();

	public UserManager(SyncCore plugin, SyncNetCore client) {
		super(Packets.PLAYER_DATA.id, null);
		this.sync = client;
		this.plugin = plugin;
	}

	@Override
	public void execute(String server, Packet packet) {
		System.out.println("USERMAN: " + packet.toString());
		if (packet.getPayload().has("request")) {
			if (packet.getPayload().getString("request").equalsIgnoreCase("all")) {
				try {
					sendPlayers(packet.getForward());
				} catch (IOException e) {
					plugin.print(e);
					return;
				}
			}
		} else {
			if (packet.getPayload().has("players")) {
				synchronized (players) {
					players.put(packet.getForward(),
							getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("players")));
				}
			} else {
				if (packet.getPayload().has("join")) {
					List<PlayerData> add = getPlayerData(packet.getForward(), packet.getPayload().getJSONArray("join"));
					List<PlayerData> current = players.get(packet.getForward());
					if (current == null) {
						current = add;
					} else {
						add.forEach(p -> quit(p.getServer(), p.getUUID()));
						current.addAll(add);
					}
					synchronized (players) {
						players.put(packet.getForward(), current);
					}
				} else if (packet.getPayload().has("quit")) {
					packet.getPayload().getJSONArray("quit").toList().stream().map(o -> (String) o)
							.forEach(uuid -> quit(packet.getForward(), UUID.fromString(uuid)));
				}

				if (packet.getPayload().has("hash")) {
					int hash = packet.getPayload().getInt("hash");
					int otherhash = 0;
					try {
						synchronized (players) {
							otherhash = hash(players.get(packet.getForward()));
						}
					} catch (NullPointerException e) {
					}
					if (hash != otherhash) {
						try {
							sync.send(packet.getForward(),
									new Packet(null, Packets.PLAYER_DATA.id, new JSONObject().put("request", "all")));
							plugin.warning("Hash mismatch! " + hash + "!=" + otherhash);
						} catch (JSONException | IOException e) {
							plugin.print(e);
						}
					}
				}
			}
		}
	}

	public void sendPlayers(@Nullable String server) throws IOException {
		if (sync instanceof SyncServer) {
			return;
		}
		List<PlayerData> players = plugin.getPlayers();
		sync.send(server,
				new Packet(null, Packets.PLAYER_DATA.id,
						new JSONObject()
								.put("players",
										new JSONArray(
												players.stream().map(p -> p.toJSON()).collect(Collectors.toList())))
								.put("hash", hash(players))));
	}

	private static int hash(List<PlayerData> players) {
		if (players == null || players.isEmpty()) {
			return 0;
		}
		return players.stream().map(p -> p.hashData()).reduce((a, b) -> a + b).get();
	}

	private boolean quit(String server, UUID uuid) {
		System.out.println("QUIT: " + uuid + " (" + server + ")");
		List<PlayerData> current = players.get(server);
		if (current != null) {
			Iterator<PlayerData> it = current.iterator();
			for (PlayerData data; it.hasNext();) {
				data = it.next();
				if (data.getUUID().equals(uuid)) {
					it.remove();
					return true;
				}
			}
		}
		return false;
	}

	private static List<PlayerData> getPlayerData(String server, JSONArray arr) {
		List<PlayerData> list = new ArrayList<>();
		arr.forEach(o -> {
			PlayerData data = getPlayerData(server, (JSONObject) o);
			if (data != null)
				list.add(data);
		});
		return list;
	}

	private static PlayerData getPlayerData(String server, JSONObject o) {
		try {
			return new PlayerData(server, o.getString("name"), o.getString("uuid"), o.getBoolean("v"));
		} catch (JSONException e) {
			return null;
		}
	}

	public Map<String, List<PlayerData>> getPlayers() {
		if (sync instanceof SyncServer) {
			return null;
		}
		Map<String, List<PlayerData>> out = new HashMap<>();
		synchronized (players) {
			players.forEach((k, v) -> out.put(k, Collections.unmodifiableList(v)));
		}
		return Collections.unmodifiableMap(out);
	}

	public String toFormattedString() {
		synchronized (players) {
			if (players.isEmpty()) {
				return "No servers";
			}
			String build = "";
			for (Entry<String, List<PlayerData>> entry : players.entrySet()) {
				build += "§6§l" + entry.getKey() + "§7: ";
				if (entry.getValue().isEmpty()) {
					build += "None\n";
					continue;
				}
				build += "\n";
				String line = "";
				for (PlayerData p : entry.getValue()) {
					if (line.length() > 100) {
						build += line + "\n";
						line = "";
					}
					line += (p.isVanished() ? "§c[V]§7" : "§7") + p.getName() + ", ";
				}
				if (line.length() > 0) {
					build += line + "\n";
				}
			}
			return build.isEmpty() ? "" : build.substring(0, build.length() - 1);
		}
	}

	public void periodic() {

	}
}
