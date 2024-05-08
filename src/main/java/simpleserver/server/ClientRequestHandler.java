package simpleserver.server;

import com.google.gson.*;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.client.SimpleClient;
import simpleserver.client.UserAuthority;
import simpleserver.service.LoginResult;
import simpleserver.service.MessageService;
import simpleserver.service.UserService;
import simpleserver.util.JsonResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

@Getter
public class ClientRequestHandler implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(ClientRequestHandler.class);
    private final BufferedReader reader;
    private SimpleClient client;
    private final Gson gson = new Gson();
    private final SimpleServer server;
    private final MessageService messageService;
    private final UserService userService;

    public ClientRequestHandler(SimpleServer server, SocketChannel clientSocket, UserService userService, MessageService messageService) {
        this.messageService = messageService;
        this.userService = userService;
        this.server = server;
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

                var jsonMessage = gson.fromJson(message, JsonObject.class);
                LOGGER.info("Received message: {}", message);

                if (!client.isLoggedIn() || StringUtils.isAnyBlank(client.getUsername(), client.getPassword()))
                    updateClientInfoFromRequest(jsonMessage);

                try {
                    String requestType = jsonMessage.get("request").getAsString();

                    switch (requestType) {
                        case "login":
                            processClientLoginRequest(jsonMessage);
                            break;
                        case "register":
                            processClientRegistrationFromRequest(jsonMessage);
                            break;
                        case "message":
                            processMessageRequest(jsonMessage);
                            break;
                        default:
                            ServerRequest action = server.getServerActions().getOrDefault(requestType, server::unknownCommand);
                            action.execute(this.client);
                            break;
                    }
                } catch (JsonSyntaxException | IllegalStateException e) {
                    LOGGER.info("Couldn't create a Message object from users' data: {}", e.toString());
                    server.sendJsonResponse(client, JsonResponse.serverResponse("error", "The server could not parse this message"));
                } catch (NoSuchElementException e) {
                    server.sendJsonResponse(client, JsonResponse.serverResponse("error", "mailbox is empty"));
                    LOGGER.info("Client tried to open message but it's empty");
                }
            }
        } catch (JsonIOException e) {
            server.sendJsonResponse(client, JsonResponse.serverResponse("error", "Server could not parse JSON message. Disconnecting"));
            LOGGER.warn("couldn't parse JSON message");
        } catch (IOException exception) {
            server.sendJsonResponse(client, JsonResponse.serverResponse("error", exception.getMessage()));
            userService.disconnectClient(client.getSocketChannel());
            LOGGER.info("Client disconnected from the server. Remaining clients: {}", userService.getConnectedClients().size());
        } catch (Exception e) {
            LOGGER.error("Caught unhandled exception exception, fix asap: {}", e.toString());
        } finally {
            server.sendJsonResponse(client, JsonResponse.serverResponse("error", "Ending connection"));
            userService.disconnectClient(this.client.getSocketChannel());

            try {
                client.getSocketChannel().close();
            } catch (IOException e) {
                LOGGER.warn("Exception while closing a client channel: {}", e.toString());
            }

        }




    }

    private void processMessageRequest(JsonObject jsonMessage){
        Message message = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);
        var jsonResponse = new JsonObject();


        if (!userService.userIsConnected(message.senderId())) {
            jsonResponse = JsonResponse.serverResponse("error", "Client error - client is not logged in");
            LOGGER.debug("message verification - sender ID is not logged in");

        } else if (!client.getUsername().equals(message.senderId())) {
            jsonResponse = JsonResponse.serverResponse("error", "Client error - username doesnt equal sender ID");
            LOGGER.debug("message verification - Client username != sender ID");


        } else if (!userService.userIsConnected(message.receiverId())) {
            jsonResponse = JsonResponse.serverResponse("error", "Recipient is not logged in or registered");
            LOGGER.debug("message verification - Receiver ID is not connected");

        } else {
            jsonResponse = messageService.sendMessage(message);
            LOGGER.debug("message verification - successfully sent message");
        }

        server.sendJsonResponse(client, jsonResponse);
        LOGGER.info("Successfully handled a message: {}", jsonResponse.toString());
    }

    private void processClientRegistrationFromRequest(JsonObject jsonMessage) {
        if (jsonMessage.has("registerUsername") && jsonMessage.has("registerPassword")) {
            if (userService.registerNewUser(jsonMessage.get("registerUsername").getAsString(), jsonMessage.get("registerPassword").getAsString())) {
                var clientUsername = jsonMessage.get("registerUsername").getAsString();
                var clientPassword = jsonMessage.get("registerPassword").getAsString();
                this.client.setUsername(clientUsername);
                this.client.setPassword(clientPassword);
                var loginResult = userService.loginUser(client.getSocketChannel(), clientUsername, clientPassword);
                if (loginResult == LoginResult.LOGIN_SUCCESS) {
                    var response = JsonResponse.serverResponse("success", "Sucessfully Registered and logged as new user");
                    response.addProperty("registerUsername", clientUsername);
                    response.addProperty("registerPassword", clientPassword);
                    server.sendJsonResponse(this.client, response);
                } else {
                    server.sendJsonResponse(this.client, JsonResponse.serverResponse("error", "user registered but unable to login"));
                    LOGGER.warn("New client successfully registered but unable to login");
                }
            } else {
                server.sendJsonResponse(this.client, JsonResponse.serverResponse("error", "User already registered with this username"));
            }
        } else {
            server.sendJsonResponse(this.client, JsonResponse.serverResponse("error", "Error during registration, check API docs"));
        }
    }

    private void updateClientInfoFromRequest(JsonObject jsonMessage) throws IllegalStateException{
        JsonObject jsonClient = JsonParser.parseString(jsonMessage.get("user").getAsString()).getAsJsonObject();
        this.client = SimpleClient.builder()
                .username(jsonClient.get("username").getAsString())
                .password(jsonClient.get("password").getAsString())
                .authority(UserAuthority.valueOf(jsonClient.get("authority").getAsString()))
                .isLoggedIn(jsonClient.get("isLoggedIn").getAsBoolean())
                .socketChannel(this.client.getSocketChannel())
                .build();

        userService.updateClient(client);

        LOGGER.debug("Registered new client with username: {}", client.getUsername());
        client.setLoggedIn(true);
    }

    private void processClientLoginRequest(JsonObject jsonMessage) {
        if (jsonMessage.has("loginUsername") && jsonMessage.has("loginPassword")) {
            this.client.setUsername(jsonMessage.get("loginUsername").getAsString());

            var loginResponse =userService.loginUser(client.getSocketChannel(), jsonMessage.get("loginUsername").getAsString(), jsonMessage.get("loginPassword").getAsString());
            if (loginResponse == LoginResult.LOGIN_SUCCESS) {
                var response = JsonResponse.serverResponse("success", "Successfully Logged In");
                response.addProperty("loginUsername", jsonMessage.get("loginUsername").getAsString());
                response.addProperty("loginPassword", jsonMessage.get("loginPassword").getAsString());
                server.sendJsonResponse(this.client, response);
            } else {
                server.sendJsonResponse(this.client, JsonResponse.serverResponse("error", loginResponse.toString()));
            }
        } else {
            server.sendJsonResponse(this.client, JsonResponse.serverResponse("error", "Message not formatted properly. check API docs"));
        }
    }

    public void setClient(SimpleClient client) {
        this.client = client;
    }
}