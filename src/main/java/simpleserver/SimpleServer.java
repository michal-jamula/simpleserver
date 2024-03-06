package simpleserver;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServer {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleServer.class);
    private PrintWriter clientWriter;
    private final LocalDateTime startupTime = LocalDateTime.now();
    private final Gson gson = new Gson();

    public static void main(String[] args) {
        LoggingUtil.initLogManager();
        new SimpleServer().go();
    }

    public void go() {
        ExecutorService readThread = Executors.newSingleThreadExecutor();

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(5000));

            LOGGER.info("Server is up and running");
            while (serverChannel.isOpen()) {
                SocketChannel clientSocket = serverChannel.accept();
                clientWriter = new PrintWriter(Channels.newOutputStream(clientSocket));

                readThread.submit(new ClientHandler(clientSocket));
                LOGGER.info("Server received a new client");
            }

        } catch (IOException e) {
            LOGGER.error("Critical server exception. Terminating server: " + e);
            System.exit(0);
        }
    }

    private void pingBack() {
        JsonObject response = new JsonObject();
        response.addProperty("message", "PONG");
        sendJsonResponse(response);
        LOGGER.debug("Ping method called successfully");
    }

    private void uptime() {
        Duration serverUptime = Duration.between(startupTime, LocalDateTime.now());

        JsonObject response = new JsonObject();
        response.addProperty("message", "ServerUptime");
        response.addProperty("seconds", serverUptime.toSeconds());

        sendJsonResponse(response);
        LOGGER.debug("Uptime method called successfully");
    }

    private void sendJsonResponse(JsonObject response) {
        String jsonResponse = gson.toJson(response);
        clientWriter.println(jsonResponse);
        clientWriter.flush();
        LOGGER.debug("sendJsonResponse method called successfully: " + response);
    }

    private void help() {
        JsonObject response = new JsonObject();
        response.addProperty("message", "Available commands");
        JsonArray commands = new JsonArray();
        commands.add("help");
        commands.add("info");
        commands.add("ping");
        commands.add("uptime");
        commands.add("stop");
        response.add("commands", commands);

        sendJsonResponse(response);
        LOGGER.debug("Help method called successfully");
    }

    private void info() {
        Properties properties = new Properties();
        try {
            properties.load(this.getClass().getClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            LOGGER.warn("Unable to locate project version");
        }

        JsonObject response = new JsonObject();
        response.addProperty("serverVersion", properties.get("version").toString());
        response.addProperty("creationDate", startupTime.format(DateTimeFormatter.ISO_DATE));

        sendJsonResponse(response);
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
            LOGGER.info("New ClientHandler started");
            String message;
            try {
                while((message = reader.readLine()) != null) {
                    LOGGER.info("Received message: {}", message);

                    switch (message.toLowerCase()) {
                        case "ping" :
                            pingBack();
                            break;
                        case "uptime" :
                            uptime();
                            break;
                        case "info" :
                            info();
                            break;
                        case "help" :
                            help();
                            break;
                        case "stop" :
                            LOGGER.warn("Server received stop command. Exiting program");
                            System.exit(0);
                        default:
                            JsonObject response = new JsonObject();
                            response.addProperty("message", "unknownCommand");
                            sendJsonResponse(response);
                    }
                }
            } catch (IOException exception) {
                LOGGER.warn("Client disconnected from the server");
            }
        }
    }
}
