package simpleserver;

import com.google.gson.*;
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
    private final HashMap<SocketChannel, String> connectedClients;
    private final LocalDateTime startupTime = LocalDateTime.now();
    private final Gson gson = new Gson();

    public SimpleServer () {
        this.connectedClients = new HashMap<>();
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
        SocketChannel socketChannel;

        public ClientHandler(SocketChannel clientSocket) {
            socketChannel = clientSocket;
            reader = new BufferedReader(Channels.newReader(socketChannel, StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            LOGGER.debug("New ClientHandler started");
            String message;
            try {
                while((message = reader.readLine()) != null) {

                    var jsonMessage = new Gson().fromJson(message, JsonObject.class);
                    var response = new JsonObject();
                    LOGGER.info("Received message: {}", message);

                    if (jsonMessage.has("serverRequest")) {
                        switch (jsonMessage.get("serverRequest").getAsString()) {
                            case "ping" :
                                pingBack(socketChannel);
                                break;
                            case "uptime" :
                                uptime(socketChannel);
                                break;
                            case "info" :
                                info(socketChannel);
                                break;
                            case "help" :
                                help(socketChannel);
                                break;
                            case "stop" :
                                LOGGER.warn("Server received stop command. Exiting program");
                                System.exit(0);
                            default:
                                response.addProperty("message", "unknownCommand");
                                sendJsonResponse(socketChannel, response);
                        }
                    } else if (jsonMessage.has("messageObject")) {
                        try {
                            Message messageObject = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);
                            response.addProperty("success", "message sent");
                            LOGGER.info("Received and successfully handled a direct message: " + messageObject);

                        } catch (JsonSyntaxException | IllegalStateException e) {
                            LOGGER.warn("Couldn't create a Message object from users' data: {}", e.toString());
                            response.addProperty("serverError", "The server could not parse this message");
                        }
                        sendJsonResponse(socketChannel, response);
                    } else {
                        response.addProperty("message", "unknownCommand");
                        sendJsonResponse(socketChannel, response);
                    }
                }
            } catch (IOException exception) {
                connectedClients.remove(this.socketChannel);
                LOGGER.info("Client disconnected from the server. Remaining clients: {}", connectedClients.size());
            } catch (Exception e) {
                LOGGER.error("Caught unexpected exception, fix asap: {}", e.toString());
            }
        }
    }
}
