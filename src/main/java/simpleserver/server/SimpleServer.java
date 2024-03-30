package simpleserver.server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.client.UserAuthority;
import simpleserver.client.SimpleClient;
import simpleserver.exceptions.ClientNotFoundException;
import simpleserver.exceptions.UserAlreadyLoggedInException;
import simpleserver.exceptions.UserVerificationException;
import simpleserver.util.JsonResponse;
import simpleserver.util.LoggingUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleServer {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleServer.class);
    private final HashMap<SocketChannel, String> connectedClients;
    private final LocalDateTime startupTime = LocalDateTime.now();
    private final Gson gson = new Gson();
    private final Mailbox mailbox;
    private final HashMap<String, ServerRequest> serverActions;
    private static final ArrayList<RegisteredUserCredentials> registeredUsers = new ArrayList<>();
    private static final ArrayList<String> onlineClients = new ArrayList<>();

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

        SimpleServer.loadRegisteredUsers();

    }

    public static void main(String[] args) {
        LoggingUtil.initLogManager();
        new SimpleServer().go();
    }

    private static void loadRegisteredUsers() {
        try {
            String data = Files.readString(Paths.get("registeredUsers.json"));
            var registeredUserType = new TypeToken<ArrayList<RegisteredUserCredentials>>(){}.getType();
            ArrayList<RegisteredUserCredentials> registeredUsersFromFile = new Gson().fromJson(data, registeredUserType);
            registeredUsers.addAll(registeredUsersFromFile);
        } catch (IOException e) {
            LOGGER.error("Unable to load registered users from file");
        }
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
        JsonObject response = JsonResponse.serverResponse("success", "PONG");
        sendJsonResponse(client, response);
        LOGGER.debug("Ping method called successfully");
    }

    private void uptime(SimpleClient client) {
        Duration serverUptime = Duration.between(startupTime, LocalDateTime.now());
        var response = JsonResponse.serverResponse("success", "Server uptime command");
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
        JsonObject response = JsonResponse.serverResponse("success", "available commands");
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
        Properties properties = new Properties();
        try {
            properties.load(SimpleServer.class.getClassLoader().getResourceAsStream("application.properties"));
        } catch (NullPointerException | IOException e) {
            sendJsonResponse(client, JsonResponse.serverResponse("error", "Server is unable to find the version"));
            LOGGER.warn("Unable to locate project version");
            return;
        }
        var response = JsonResponse.serverResponse("success", "info request");

        response.addProperty("serverVersion", properties.get("version").toString());
        response.addProperty("creationDate", startupTime.format(DateTimeFormatter.ISO_DATE));

        sendJsonResponse(client, response);
        LOGGER.debug("Info method called successfully");
    }

    private static void verifyUser (SimpleClient client) throws UserVerificationException {
        if (StringUtils.isBlank(client.getUsername()) ||
            StringUtils.isBlank(client.getPassword())) {
                throw new UserVerificationException("Client cannot be verified with blank username or password");
        }

        //Throw error when user tries to log by the same username twice
//                        if (connectedClients.entrySet().stream()
//                                .filter(entry -> client.getUsername().equals(entry.getValue()))
//                                .map(Map.Entry::getKey)
//                                .findFirst()
//                                .isPresent()) {
//
//                            LOGGER.debug("Client with username {} tried logging in twice", client.getUsername());
//                            throw new IOException("Client with this username is already logged in, disconnecting client");
//                        }
    }

    private boolean loginUser(SimpleClient client, String username, String password) {

        if (registeredUsers.stream()
                .anyMatch(user -> user.username().equals(username) && user.password().equals(password))) {
            mailbox.addClient(client);
            return true;
        } else
            return false;

    }

    public class ClientHandler implements Runnable {
        BufferedReader reader;
        private SimpleClient client;

        public ClientHandler(SocketChannel clientSocket) {
            reader = new BufferedReader(Channels.newReader(clientSocket, StandardCharsets.UTF_8));
            client = SimpleClient.builder()
                    .socketChannel(clientSocket)
                    .isLoggedIn(false)
                    .build();
        }

        @Override
        public void run() {
            LOGGER.debug("New ClientHandler started");
            String message;
            try {
                while ((message = reader.readLine()) != null) {

                    var jsonMessage = new Gson().fromJson(message, JsonObject.class);
                    LOGGER.debug("Received message: {}", message);

                    if (!client.isLoggedIn() || StringUtils.isAnyBlank(client.getUsername(), client.getPassword())) {
                        JsonObject jsonClient = JsonParser.parseString(jsonMessage.get("user").getAsString()).getAsJsonObject();
                        this.client = SimpleClient.builder()
                                .username(jsonClient.get("username").getAsString())
                                .password(jsonClient.get("password").getAsString())
                                .authority(UserAuthority.valueOf(jsonClient.get("authority").getAsString()))
                                .isLoggedIn(jsonClient.get("isLoggedIn").getAsBoolean())
                                .socketChannel(this.client.getSocketChannel())
                                .build();

//
                        connectedClients.put(client.getSocketChannel(), client.getUsername());


                        LOGGER.debug("Registered new client with username: {}", client.getUsername());
                        client.setLoggedIn(true);
                    }
                    try {
                        //Methods which don't require user authentication
                        if (jsonMessage.has("request") && !jsonMessage.get("request").isJsonNull() &&
                                serverActions.containsKey(jsonMessage.get("request").getAsString())) {
                            var clientRequest = jsonMessage.get("request").getAsString().trim().toLowerCase();

                            var action = serverActions.getOrDefault(clientRequest, SimpleServer::unknownCommand);
                            action.execute(this.client);
                            LOGGER.info("Successfully handled a server request");
                            continue;
                        } else if (jsonMessage.get("request").getAsString().equals("login")) {
                            if (jsonMessage.has("loginUsername") && jsonMessage.has("loginPassword")) {
                                this.client.setUsername(jsonMessage.get("loginUsername").getAsString());

                                if (loginUser(this.client, jsonMessage.get("loginUsername").getAsString(), jsonMessage.get("loginPassword").getAsString())) {
                                    registeredUsers.add(new RegisteredUserCredentials(this.client.getUsername(), this.client.getPassword()));
                                    var response = JsonResponse.serverResponse("success", "Successfully Logged In");
                                    response.addProperty("loginUsername", jsonMessage.get("loginUsername").getAsString());
                                    response.addProperty("loginPassword", jsonMessage.get("loginPassword").getAsString());
                                    sendJsonResponse(this.client, response);
                                } else {
                                    sendJsonResponse(this.client, JsonResponse.serverResponse("error", "Could not login"));
                                }
                            } else {
                                sendJsonResponse(this.client, JsonResponse.serverResponse("error", "Cannot login right now"));
                            }
                            continue;
                        }

                        SimpleServer.verifyUser(this.client);

                        //Methods which require user authentication
                        if (jsonMessage.has("messageObject")) {
                            Message messageObject = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);

                            sendJsonResponse(client, mailbox.sendMessage(messageObject));
                            LOGGER.info("Successfully handled a DM");
                        }  else {
                            sendJsonResponse(client, JsonResponse.serverResponse("error", "unknown command"));
                        }

                    } catch (UserVerificationException e) {
                        LOGGER.info("Unregistered user tried a server command.");
                        sendJsonResponse(client, JsonResponse.serverResponse("error", "User didnt pass verification. Register/Login to query the server"));
                    } catch (JsonSyntaxException | IllegalStateException e) {
                        LOGGER.info("Couldn't create a Message object from users' data: {}", e.toString());
                        sendJsonResponse(client, JsonResponse.serverResponse("error", "The server could not parse this message"));
                    } catch (NoSuchElementException e) {
                        sendJsonResponse(client, JsonResponse.serverResponse("error", "mailbox is empty"));
                        LOGGER.info("Client tried to open message but it's empty");
                    }
                }
            } catch (JsonIOException e) {
                sendJsonResponse(client, JsonResponse.serverResponse("error", "Server could not parse JSON message"));
                LOGGER.warn("couldn't parse JSON message");
            } catch (IOException exception) {
                sendJsonResponse(client, JsonResponse.serverResponse("error", exception.getMessage()));
                connectedClients.remove(client.getSocketChannel());
                mailbox.removeClient(this.client);
                LOGGER.info("Client disconnected from the server. Remaining clients: {}", connectedClients.size());
            } catch (Exception e) {
                LOGGER.error("Caught unhandled exception exception, fix asap: {}", e.toString());
            }
        }
    }
}