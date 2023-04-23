package raft.common.config;

public class RaftNodeConfig {

    private int serverId;
    private String ip;
    private int port;

    public RaftNodeConfig(int serverId) {
        this.serverId = serverId;
    }

    public RaftNodeConfig(int serverId, String ip, int port) {
        this.serverId = serverId;
        this.ip = ip;
        this.port = port;
    }

    public int getServerId() {
        return serverId;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
