package mapreduce.common;

public class KVPair {

    private final String key;
    private final String value;

    public KVPair(String key, String value) {
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
