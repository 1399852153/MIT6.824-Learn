package mapreduce.master;

import java.util.Objects;

public class WorkerAddress {

    private final String ip;
    private final int port;

    public WorkerAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkerAddress that = (WorkerAddress) o;
        return Objects.equals(ip, that.ip) && Objects.equals(port, that.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    @Override
    public String toString() {
        return "WorkerAddress{" +
            "ip='" + ip + '\'' +
            ", port=" + port +
            '}';
    }
}
