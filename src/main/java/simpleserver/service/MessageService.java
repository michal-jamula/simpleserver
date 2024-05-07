package simpleserver.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.client.SimpleClient;
import simpleserver.repository.MessageRepository;
import simpleserver.util.JsonResponse;

import java.util.*;

public class MessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageService.class);
    private final Gson gson = new Gson();
    private final HashMap<SimpleClient, LinkedList<Message>> unreadMessages;
    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.unreadMessages = new HashMap<>();
        this.messageRepository = messageRepository;
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


        if (unreadMessages.get(clientComparison).size() < 5) {
            unreadMessages.get(clientComparison).add(message);
            messageRepository.saveMessage(message);
            LOGGER.debug("message processed successfully, sending message to repo: {}", message);
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
