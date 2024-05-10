package simpleserver.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.util.JsonResponse;
import simpleserver.util.StatusEnum;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class ServerRequests {
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientResponder.class);
    private final HashMap<String, JsonObject> serverRequests;
    private final LocalDateTime startupTime;

    public ServerRequests (LocalDateTime startupTime) {
        this.startupTime = startupTime;

        this.serverRequests = new HashMap<>();

        serverRequests.put("ping", pingBack());
        serverRequests.put("uptime", uptime());
        serverRequests.put("info", info());
        serverRequests.put("help", help());
        serverRequests.put("unknown", unknownCommand());
    }


    public JsonObject getResponse(String request) {
        return serverRequests.getOrDefault(request, unknownCommand());
    }

    private static JsonObject pingBack() {
        return JsonResponse.serverResponse(StatusEnum.SUCCESS, "PONG");
    }

    private JsonObject uptime() {
        Duration serverUptime = Duration.between(startupTime, LocalDateTime.now());
        var response = JsonResponse.serverResponse(StatusEnum.SUCCESS, "Server uptime command");
        response.addProperty("seconds", serverUptime.toSeconds());

        return response;
    }

    private static JsonObject unknownCommand() {
        return JsonResponse.serverResponse(StatusEnum.ERROR, "unknown server command");
    }

    private static JsonObject help() {
        JsonObject response = JsonResponse.serverResponse(StatusEnum.SUCCESS, "available commands");
        var commands = new ArrayList<>(List.of("help",
                "info",
                "ping",
                "uptime",
                "message (username) (message of any length)",
                "open",
                "login (username) (password)",
                "stop"));

        response.add("commands", new Gson().toJsonTree(commands));

        return response;
    }

    private JsonObject info() {
        Properties properties = new Properties();
        try {
            properties.load(SimpleServer.class.getClassLoader().getResourceAsStream("application.properties"));
        } catch (NullPointerException | IOException e) {
            LOGGER.warn("Unable to locate project version");
            return JsonResponse.serverResponse(StatusEnum.ERROR, "Server is unable to find the version");
        }
        var response = JsonResponse.serverResponse(StatusEnum.SUCCESS, "info request");

        response.addProperty("serverVersion", properties.get("version").toString());
        response.addProperty("creationDate", startupTime.format(DateTimeFormatter.ISO_DATE));

        LOGGER.debug("Info method called successfully");
        return response;
    }



}
