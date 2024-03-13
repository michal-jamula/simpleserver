package simpleserver;

@FunctionalInterface
public interface ServerRequest {
    void execute(SimpleClient client);
}
