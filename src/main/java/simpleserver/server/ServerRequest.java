package simpleserver.server;

import simpleserver.client.SimpleClient;

@FunctionalInterface
public interface ServerRequest {
    void execute(SimpleClient client);
}
