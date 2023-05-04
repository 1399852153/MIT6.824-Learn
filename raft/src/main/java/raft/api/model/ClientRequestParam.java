package raft.api.model;

import raft.api.command.Command;

public class ClientRequestParam {

    private final Command command;

    public ClientRequestParam(Command command) {
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }
}
