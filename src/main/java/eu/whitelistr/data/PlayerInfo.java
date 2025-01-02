package eu.whitelistr.data;

import eu.whitelistr.network.WClient;

public class PlayerInfo {
    private String ip;
    private String username;
    private String uuid;
    private String connectionAddress;
    private long timestamp;
    private String serverUUID;

    public PlayerInfo(String ip, String username, String uuid, String connectionAddress, long timestamp, String serverUUID) {
        this.ip = ip;
        this.username = username;
        this.uuid = uuid;
        this.connectionAddress = connectionAddress;
        this.timestamp = timestamp;
        this.serverUUID = serverUUID;
    }

    public String getIp() {
        return ip;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getConnectionAddress() {
        return connectionAddress;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getServerId() {
        return serverUUID;
    }

    public String toJson() {
        return WClient.gson.toJson(this);
    }
}

