package raft.api.model;

import raft.api.command.Command;

public class ClientRequestParam {

    private Command command;

    public ClientRequestParam() {
    }

    public ClientRequestParam(Command command) {
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }
}
