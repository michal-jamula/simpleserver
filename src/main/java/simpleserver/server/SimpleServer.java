package simpleserver.server;

import com.google.gson.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.client.SimpleClient;
import simpleserver.repository.UserRepository;
import simpleserver.service.MessageService;
import simpleserver.service.UserService;
import simpleserver.util.JsonResponse;
import simpleserver.util.LoggingUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Getter
public class SimpleServer {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleServer.class);

    private final LocalDateTime startupTime = LocalDateTime.now();
    private final Gson gson = new Gson();
    private final HashMap<String, ServerRequest> serverActions;
    private final UserService userService;
    private final MessageService messageService;


    public SimpleServer(UserService userService, MessageService messageService) {
        this.messageService = messageService;
        this.userService = userService;
        this.serverActions = new HashMap<>();

        serverActions.put("ping", this::pingBack);
        serverActions.put("uptime", this::uptime);
        serverActions.put("info", this::info);
        serverActions.put("help", this::help);
        serverActions.put("open", (client) -> sendJsonResponse(client, messageService.openMessage(client)));
        serverActions.put("stop", (client) -> System.exit(0));
    }

    public static void main(String[] args){
        LoggingUtil.initLogManager();

        UserRepository userRepository = new UserRepository("registeredUsers.json");
        MessageService messageService = new MessageService("successfulMessages.json");
        UserService userService = new UserService(messageService, userRepository);

        new SimpleServer(userService, messageService).start();
    }


    public void start() {
        ExecutorService readThread = Executors.newCachedThreadPool();

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(5000));

            LOGGER.info("Server is up and running");
            while (serverChannel.isOpen()) {
                SocketChannel clientSocket = serverChannel.accept();
                userService.getConnectedClients().put(clientSocket, null);

                readThread.submit(new ClientRequestHandler(this, clientSocket, userService, messageService));
                LOGGER.info("Server received a new client");
            }
        } catch (IOException e) {
            LOGGER.error("Server unable to start at port: 5000. Terminating server");
            System.exit(0);
        }
    }

    private void pingBack(SimpleClient client) {
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

    void unknownCommand(SimpleClient client) {
        sendJsonResponse(client, JsonResponse.serverResponse("error", "unknown server command"));
    }

    void sendJsonResponse(SimpleClient client, JsonObject response) {
        var writer = new PrintWriter(Channels.newOutputStream(client.getSocketChannel()));
        String jsonResponse = new Gson().toJson(response);
        writer.println(jsonResponse);
        writer.flush();
        LOGGER.debug("sendJsonResponse method called successfully: " + response);
    }

    private void help(SimpleClient client) {
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
}