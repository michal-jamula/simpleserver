package simpleserver.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.client.SimpleClient;
import simpleserver.dto.Message;
import simpleserver.repository.MessageRepository;
import simpleserver.util.JsonResponse;
import simpleserver.util.StatusEnum;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.NoSuchElementException;


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
        unreadMessages.put(client, new LinkedList<>() {});
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
            return JsonResponse.serverResponse(StatusEnum.SUCCESS, "Message sent successfully");
        } else {
            LOGGER.info("Client mailbox is full, returning message.");
            return JsonResponse.serverResponse(StatusEnum.ERROR, "Client Mailbox is full");
        }
    }

    //TODO: client management - have a global list of connected clients?
//    public void removeClient(SimpleClient client) {
//        unreadMessages.remove(client);
//    }

    public JsonObject openMessage(SimpleClient client) {
        var response = new JsonObject();

        try  {
            var message = unreadMessages.get(client).pop();
            response = JsonResponse.serverResponse(StatusEnum.SUCCESS, "New message");
            response.addProperty("messageObject", gson.toJson(message));
            LOGGER.debug("Client successfully opened a new message");
        } catch (NoSuchElementException e) { // when message is empty
            response = JsonResponse.serverResponse(StatusEnum.SUCCESS, "No new messages");
            LOGGER.debug("Client tried to open message but it's empty");
        }
        return response;
    }
}
