package simpleserver.server;

import com.google.gson.JsonObject;
import simpleserver.client.SimpleClient;

@FunctionalInterface
public interface ClientRequest {
    void respond(SimpleClient client, JsonObject response);


    default void respond(SimpleClient client, String requestType) {
        var response = new JsonObject();
        response.addProperty("serverRequest", requestType);
        respond(client, response);
    }

}
