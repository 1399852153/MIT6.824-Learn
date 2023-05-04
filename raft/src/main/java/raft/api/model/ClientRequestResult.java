package raft.api.model;

import myrpc.common.URLAddress;

public class ClientRequestResult {

    /**
     * get读请求的返回值，set写请求时为null
     * */
    private String value;

    /**
     * 如果请求的节点不是leader，该节点会返回它认为的当前leader的服务地址
     * */
    private URLAddress leaderAddress;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public URLAddress getLeaderAddress() {
        return leaderAddress;
    }

    public void setLeaderAddress(URLAddress leaderAddress) {
        this.leaderAddress = leaderAddress;
    }
}
