package simpleserver.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.client.SimpleClient;
import simpleserver.util.JsonResponse;

import java.util.*;

public class Mailbox {
    private final static Logger LOGGER = LoggerFactory.getLogger(Mailbox.class);
    private final Gson gson = new Gson();
    private final HashMap<SimpleClient, LinkedList<Message>> unreadMessages;

    public Mailbox() {
        this.unreadMessages = new HashMap<>();
    }

    public void addClient(SimpleClient client) {
        unreadMessages.put(client, new LinkedList<>() {
        });
        LOGGER.info("New client added to mailbox: {}", client.getUsername());
    }

    public JsonObject sendMessage(Message message) {
        var clientComparison = SimpleClient.builder()
                .username(message.receiverId())
                .build();
        LOGGER.debug("New Message received: {}", message);

        if (!unreadMessages.containsKey(clientComparison)) {
            LOGGER.info("Receiving client is not registered in the mailbox");
            return JsonResponse.serverResponse("error", "Client is not registered in the mailbox");
        }

        if (unreadMessages.get(clientComparison).size() < 5) {
            unreadMessages.get(clientComparison).add(message);
            LOGGER.debug("Message added to queue successfully: {}", message);
            return JsonResponse.serverResponse("success", "Message sent successfully");
        } else {
            LOGGER.info("Client mailbox is full, returning message.");
            return JsonResponse.serverResponse("error", "Client Mailbox is full");
        }
    }

    public void removeClient(SimpleClient client) {
        unreadMessages.remove(client);
    }

    public JsonObject openMessage(SimpleClient client) {
        var response = new JsonObject();
        var message = unreadMessages.get(client).pop();

        response.addProperty("messageObject", gson.toJson(message));
        LOGGER.debug("Successfully opened message");
        return response;
    }
}
