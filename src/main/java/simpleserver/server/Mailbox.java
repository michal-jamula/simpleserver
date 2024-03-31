package simpleserver.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.client.SimpleClient;
import simpleserver.util.JsonResponse;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class Mailbox {
    private static final Logger LOGGER = LoggerFactory.getLogger(Mailbox.class);
    private final Gson gson = new Gson();
    private final HashMap<SimpleClient, LinkedList<Message>> unreadMessages;
    private final ArrayBlockingQueue<Message> messagesToSave;

    public Mailbox() {
        this.unreadMessages = new HashMap<>();
        this.messagesToSave = new ArrayBlockingQueue<>(25);

        //Save messages to file
        var messageSaverThread = new Thread(new MessageSaver());
        messageSaverThread.start();
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
            messagesToSave.add(message);
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

    private class MessageSaver implements Runnable {
        private static final String filePath = "successfulMessages.json"; // Read this from application.properties?
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Message message = messagesToSave.take();
                    saveMessageToFile(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void saveMessageToFile(Message message) {
            try {
                Path path = Paths.get(filePath);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String messageJson = gson.toJson(message);

                // Check if the file already exists and is not empty
                boolean fileExists = Files.exists(path) && Files.size(path) > 0;

                //Formatting necessary to avoid reading the whole file and rewriting everything with an extra message.
                if (fileExists) {
                    RandomAccessFile file = new RandomAccessFile(filePath, "rw");
                    long length = file.length();
                    file.setLength(length - 1);
                    file.close();
                    Files.writeString(path, ",\n" + messageJson + "]", StandardOpenOption.APPEND);
                    LOGGER.info("Added new message to local file");
                } else {
                    // If the file doesn't exist or is empty, create a new array with the message
                    Files.writeString(path, "[\n" + messageJson + "]", StandardOpenOption.CREATE);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save message to file", e);
            }
        }
    }
}
