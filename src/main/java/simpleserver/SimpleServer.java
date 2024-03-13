package simpleserver;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.util.JsonResponse;
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
    private final Mailbox mailbox;
    private final HashMap<String, ServerRequest> serverActions;
    private static final ArrayList<SimpleClient> registeredUsers = new ArrayList<>();

    public SimpleServer() {
        this.connectedClients = new HashMap<>();
        this.mailbox = new Mailbox();
        this.serverActions = new HashMap<>();

        serverActions.put("ping", SimpleServer::pingBack);
        serverActions.put("uptime", this::uptime);
        serverActions.put("info", this::info);
        serverActions.put("help", SimpleServer::help);
        serverActions.put("open", (client) -> sendJsonResponse(client, mailbox.openMessage(client)));
        serverActions.put("stop", (client) -> System.exit(0));
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
            LOGGER.error("Server unable to start at port: 5000. Terminating server");
            System.exit(0);
        }
    }

    private static void pingBack(SimpleClient client) {
        JsonObject response = new JsonObject();
        response.addProperty("response", "PONG");
        sendJsonResponse(client, response);
        LOGGER.debug("Ping method called successfully");
    }

    private void uptime(SimpleClient client) {
        JsonObject response = new JsonObject();
        Duration serverUptime = Duration.between(startupTime, LocalDateTime.now());
        response.addProperty("message", "ServerUptime");
        response.addProperty("seconds", serverUptime.toSeconds());

        sendJsonResponse(client, response);
        LOGGER.debug("Uptime method called successfully");
    }

    private static void unknownCommand(SimpleClient client) {
        sendJsonResponse(client, JsonResponse.serverResponse("error", "unknown server command"));
    }

    private static void sendJsonResponse(SimpleClient client, JsonObject response) {
        var writer = new PrintWriter(Channels.newOutputStream(client.getSocketChannel()));
        String jsonResponse = new Gson().toJson(response);
        writer.println(jsonResponse);
        writer.flush();
        LOGGER.debug("sendJsonResponse method called successfully: " + response);
    }

    private static void help(SimpleClient client) {
        JsonObject response = new JsonObject();
        response.addProperty("message", "Available commands");
        var commands = new ArrayList<>(List.of("help",
                "info",
                "ping",
                "uptime",
                "message (username) (message of any length)",
                "open",
                "login (username) (password)",
                "stop"));

        response.add("commands", new Gson().toJsonTree(commands));

        sendJsonResponse(client, response);
        LOGGER.debug("Help method called successfully");
    }

    private void info(SimpleClient client) {
        var response = new JsonObject();
        Properties properties = new Properties();
        try {
            properties.load(SimpleServer.class.getClassLoader().getResourceAsStream("application.properties"));
        } catch (NullPointerException | IOException e) {
            sendJsonResponse(client, JsonResponse.serverResponse("error", "Server is unable to find the version"));
            LOGGER.warn("Unable to locate project version");
            return;
        }

        response.addProperty("serverVersion", properties.get("version").toString());
        response.addProperty("creationDate", startupTime.format(DateTimeFormatter.ISO_DATE));

        sendJsonResponse(client, response);
        LOGGER.debug("Info method called successfully");
    }

    public class ClientHandler implements Runnable {
        BufferedReader reader;
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
                    LOGGER.debug("Received message: {}", message);

                    if (!client.isRegistered()) {
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
                        client.setRegistered(true);
                    }
                    try {

                        if (jsonMessage.has("serverRequest")) {
                            var clientRequest = jsonMessage.get("serverRequest").getAsString().trim().toLowerCase();

                            var action = serverActions.getOrDefault(clientRequest, SimpleServer::unknownCommand);
                            action.execute(this.client);
                            LOGGER.info("Successfully handled a server request");
                        } else if (jsonMessage.has("messageObject")) {
                            Message messageObject = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);

                            sendJsonResponse(client, mailbox.sendMessage(messageObject));
                            LOGGER.info("Successfully handled a DM");
                        } else {
                            sendJsonResponse(client, JsonResponse.serverResponse("error", "unknown command"));
                        }

                    } catch (JsonSyntaxException | IllegalStateException e) {
                        LOGGER.info("Couldn't create a Message object from users' data: {}", e.toString());
                        sendJsonResponse(client, JsonResponse.serverResponse("error", "The server could not parse this message"));
                    } catch (NoSuchElementException e) {
                        sendJsonResponse(client, JsonResponse.serverResponse("error", "mailbox is empty"));
                        LOGGER.info("Client tried to open message when it's empty");
                    }
                }
            } catch (IOException exception) {
                sendJsonResponse(client, JsonResponse.serverResponse("error", "client with the same username is already registered"));
                connectedClients.remove(client.getSocketChannel());
                mailbox.removeClient(this.client);
                LOGGER.info("Client disconnected from the server. Remaining clients: {}", connectedClients.size());
            } catch (Exception e) {
                LOGGER.error("Caught unexpected exception, fix asap: {}", e.toString());
            }
        }
    }
}