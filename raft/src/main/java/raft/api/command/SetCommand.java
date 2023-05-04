package raft.api.command;

/**
 * 写操作，把一个key设置为value
 * */
public class SetCommand implements Command{

    private final String key;
    private final String value;

    public SetCommand(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}

