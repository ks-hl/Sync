package dev.heliosares.sync.net;

import org.json.JSONObject;

public class Packet {
	private final String channel;
	private final int packetid;
	private JSONObject payload;

	public Packet(String channel, int packetid, JSONObject payload) {
		this.channel = channel;
		this.packetid = packetid;
		this.payload = payload;
	}

	Packet(JSONObject packet) {
		packetid = packet.getInt("pid");
		if (packet.has("ch")) {
			channel = packet.getString("ch");
		} else {
			channel = null;
		}
		if (packet.has("pl")) {
			payload = packet.getJSONObject("pl");
		} else {
			payload = null;
		}
	}

	public String getChannel() {
		return channel;
	}

	public int getPacketId() {
		return packetid;
	}

	public JSONObject getPayload() {
		return payload;
	}

	@Override
	public String toString() {
		JSONObject json = new JSONObject();
		json.put("pid", packetid);
		if (channel != null) {
			json.put("ch", channel);
		}
		if (payload != null) {
			json.put("pl", payload);
		}
		return json.toString();
	}
}
