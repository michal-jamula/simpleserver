package simpleserver.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.client.SimpleClient;

import java.io.PrintWriter;
import java.nio.channels.Channels;

public class ClientResponder implements ClientRequest {
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientResponder.class);
    private final Gson gson = new Gson();
    private final ServerRequests requests;

    public ClientResponder (ServerRequests requests) {
        this.requests = requests;
    }


    @Override
    public void respond(SimpleClient client, JsonObject response) {
        LOGGER.info("Sending message to user: {}", response);

        if (response.has("serverRequest")) {
            var request = response.get("serverRequest").getAsString();

            this.respond(client, requests.getResponse(request));
            return;
        }

        var writer = new PrintWriter(Channels.newOutputStream(client.getSocketChannel()));
        String jsonResponse = gson.toJson(response);
        writer.println(jsonResponse);
        writer.flush();
        LOGGER.debug("sent response to client: " + response);
    }
}
