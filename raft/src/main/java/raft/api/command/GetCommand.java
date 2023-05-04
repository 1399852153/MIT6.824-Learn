package raft.api.command;

public class GetCommand implements Command{

    private final String key;

    public GetCommand(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
