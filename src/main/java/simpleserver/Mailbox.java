package simpleserver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        LOGGER.debug("New client added to mailbox: {}", client.getUsername());
    }

    public JsonObject sendMessage(Message message) {
        var response = new JsonObject();
        SimpleClient clientComparison = new SimpleClient(message.receiverId());
        LOGGER.debug("New Message received: {}", message);

        if (!unreadMessages.containsKey(clientComparison)) {
            response.addProperty("status", "error");
            response.addProperty("message", "Client is not registered");
            LOGGER.info("Receiving client is not registered in the mailbox");
            return response;
        }

        if (unreadMessages.get(clientComparison).size() < 5) {
            unreadMessages.get(clientComparison).add(message);
            response.addProperty("status", "success");
            response.addProperty("message", "Message sent successfully");
            LOGGER.info("Message added to queue successfully: {}", message);
        } else {
            response.addProperty("status", "error");
            response.addProperty("message", "Client Mailbox is full");
            LOGGER.info("Client mailbox is full, returning message.");
        }
        return response;
    }

    public void removeClient(SimpleClient client) {
        unreadMessages.remove(client);
    }


    public JsonObject openMessage(SimpleClient client) {
        var response = new JsonObject();
        var message = unreadMessages.get(client).pop();

        response.addProperty("messageObject", gson.toJson(message));
        LOGGER.info("Opening message: {}", gson.toJson(message));
        return response;
    }
}
