package simpleserver;

import com.google.gson.*;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.util.LoggingUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServer {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleServer.class);
    private final BidiMap<SocketChannel, String> connectedClients;
    private final LocalDateTime startupTime = LocalDateTime.now();
    private final Gson gson = new Gson();
    private final Mailbox mailbox;

    public SimpleServer() {
        this.connectedClients = new DualHashBidiMap<>();
        this.mailbox = new Mailbox();
    }

    public static void main(String[] args) {
        LoggingUtil.initLogManager();
        new SimpleServer().go();
    }

    public void go() {
        ExecutorService readThread = Executors.newCachedThreadPool();

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(5000));

            LOGGER.info("Server is up and running");
            while (serverChannel.isOpen()) {
                SocketChannel clientSocket = serverChannel.accept();

                connectedClients.put(clientSocket, null);

                readThread.submit(new ClientHandler(clientSocket));
                LOGGER.info("Server received a new client");
            }

        } catch (IOException e) {
            LOGGER.error("Critical server exception. Terminating server: " + e);
            System.exit(0);
        }
    }

    private void pingBack(SocketChannel clientChannel) {
        JsonObject response = new JsonObject();
        response.addProperty("message", "PONG");
        sendJsonResponse(clientChannel, response);
        LOGGER.debug("Ping method called successfully");
    }

    private void uptime(SocketChannel clientChannel) {
        Duration serverUptime = Duration.between(startupTime, LocalDateTime.now());

        JsonObject response = new JsonObject();
        response.addProperty("message", "ServerUptime");
        response.addProperty("seconds", serverUptime.toSeconds());

        sendJsonResponse(clientChannel, response);
        LOGGER.debug("Uptime method called successfully");
    }

    private void sendJsonResponse(SocketChannel clientSocket, JsonObject response) {
        var writer = new PrintWriter(Channels.newOutputStream(clientSocket));
        String jsonResponse = gson.toJson(response);
        writer.println(jsonResponse);
        writer.flush();
        LOGGER.debug("sendJsonResponse method called successfully: " + response);
    }

    private void help(SocketChannel clientChannel) {
        JsonObject response = new JsonObject();
        response.addProperty("message", "Available commands");
        JsonArray commands = new JsonArray();
        commands.add("help");
        commands.add("info");
        commands.add("ping");
        commands.add("uptime");
        commands.add("message <username> <message of any length>");
        commands.add("stop");
        response.add("commands", commands);

        sendJsonResponse(clientChannel, response);
        LOGGER.debug("Help method called successfully");
    }

    private void info(SocketChannel clientChannel) {
        Properties properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            LOGGER.warn("Unable to locate project version");
        }

        var response = new JsonObject();
        response.addProperty("serverVersion", properties.get("version").toString());
        response.addProperty("creationDate", startupTime.format(DateTimeFormatter.ISO_DATE));

        sendJsonResponse(clientChannel, response);
        LOGGER.debug("Info method called successfully");
    }

    public class ClientHandler implements Runnable {
        BufferedReader reader;
        boolean isRegistered = false;
        private final SimpleClient client;


        public ClientHandler(SocketChannel clientSocket) {
            reader = new BufferedReader(Channels.newReader(clientSocket, StandardCharsets.UTF_8));
            client = new SimpleClient();
            client.setSocketChannel(clientSocket);
        }

        @Override
        public void run() {
            LOGGER.debug("New ClientHandler started");
            String message;
            try {
                while ((message = reader.readLine()) != null) {

                    var jsonMessage = new Gson().fromJson(message, JsonObject.class);
                    var response = new JsonObject();
                    LOGGER.debug("Received message: {}", message);


                    if (!isRegistered) {
                        String username = jsonMessage.get("username").getAsString();
                        if (connectedClients.entrySet().stream()
                                .filter(entry -> username.equals(entry.getValue()))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .isPresent()) {

                            LOGGER.debug("Client with username {} already registered, disconnecting", username);
                            throw new IOException("Client already registered, disconnecting client");
                        }
                        connectedClients.put(client.getSocketChannel(), username);
                        mailbox.addClient(new SimpleClient(username, client.getSocketChannel()));
                        this.client.setUsername(username);
                        LOGGER.info("Registered new client with username: {}", username);
                        isRegistered = true;
                    }
                    try {
                        if (jsonMessage.has("serverRequest")) {
                            switch (jsonMessage.get("serverRequest").getAsString()) {
                                case "ping":
                                    pingBack(client.getSocketChannel());
                                    break;
                                case "uptime":
                                    uptime(client.getSocketChannel());
                                    break;
                                case "info":
                                    info(client.getSocketChannel());
                                    break;
                                case "help":
                                    help(client.getSocketChannel());
                                    break;
                                case "open":
                                    sendJsonResponse(client.getSocketChannel(), mailbox.openMessage(client));
                                    break;
                                case "stop":
                                    LOGGER.warn("Server received stop command. Exiting program");
                                    System.exit(0);
                                default:
                                    response.addProperty("message", "unknownCommand");
                                    sendJsonResponse(client.getSocketChannel(), response);
                            }
                        } else if (jsonMessage.has("messageObject")) {
                            Message messageObject = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);
                            var mailboxResponse = mailbox.sendMessage(messageObject);
                            mailboxResponse.entrySet().forEach(e ->
                                    response.addProperty(e.getKey(), e.getValue().getAsString()));
                            sendJsonResponse(client.getSocketChannel(), response);
                            LOGGER.info("Received and successfully handled a DM");
                        } else {
                            response.addProperty("message", "unknownCommand");
                            sendJsonResponse(client.getSocketChannel(), response);
                        }



                    } catch (JsonSyntaxException | IllegalStateException e) {
                        LOGGER.warn("Couldn't create a Message object from users' data: {}", e.toString());
                        response.addProperty("serverError", "The server could not parse this message");
                        sendJsonResponse(client.getSocketChannel(), response);
                    } catch (NoSuchElementException e) {
                        response.addProperty("status", "error");
                        response.addProperty("message", "mailbox is empty");
                        sendJsonResponse(client.getSocketChannel(), response);
                        LOGGER.info("Client tried to open message when it's empty");
                    }



                }
            } catch (IOException exception) {
                var response = new JsonObject();
                response.addProperty("status", "error");
                response.addProperty("message", "client with the same username already registered.");
                sendJsonResponse(this.client.getSocketChannel(), response);
                connectedClients.remove(client.getSocketChannel());
                mailbox.removeClient(this.client);
                LOGGER.info("Client disconnected from the server. Remaining clients: {}", connectedClients.size());
            } catch (Exception e) {
                LOGGER.error("Caught unexpected exception, fix asap: {}", e.toString());
            }
        }
    }
}
