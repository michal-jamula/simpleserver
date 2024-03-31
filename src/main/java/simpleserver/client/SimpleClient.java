package simpleserver.client;

import com.google.gson.*;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
import simpleserver.util.JsonResponse;
import simpleserver.util.LoggingUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Data
@Builder
public class SimpleClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleClient.class.getName());
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private String password;
    private UserAuthority authority;
    private boolean isLoggedIn;
    private final Gson gson = new Gson();
    private SocketChannel socketChannel;

    public static void main(String[] args) {
        LoggingUtil.initLogManager();
        SimpleClient.builder().build().connectToServer();
    }

    private void connectToServer() {
        this.username = "";
        this.password = "";
        this.authority = UserAuthority.USER;
        this.isLoggedIn = false;

        try {
            InetSocketAddress serverAddress = new InetSocketAddress("localhost", 5000);
            socketChannel = SocketChannel.open(serverAddress);

            reader = new BufferedReader(Channels.newReader(socketChannel, StandardCharsets.UTF_8));
            writer = new PrintWriter(Channels.newWriter(socketChannel, StandardCharsets.UTF_8));

            ExecutorService readThread = Executors.newSingleThreadExecutor();
            readThread.execute(new IncomingReader());

            LOGGER.info("Connection with server established");
            BufferedReader clientOptionReader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                var serverRequest = new JsonObject();

                var message = clientOptionReader.readLine();
                var messageArray = message.split(" ");

                var userProperty = JsonResponse.userResponse(username, password, authority, isLoggedIn);

                serverRequest.addProperty("user", gson.toJsonTree(userProperty).toString());

                serverRequest.addProperty("request", messageArray[0]);

                if (messageArray[0].equals("message")) {
                    var receiverId = message.split(" ")[1];
                    var messagePayload = String.join(" ", Arrays.copyOfRange(messageArray, 2, messageArray.length));

                    var userMessage = new Message(receiverId, username, messagePayload);

                    var jsonMessage = gson.toJson(userMessage);
                    serverRequest.addProperty("messageObject", jsonMessage);
                } else if (messageArray[0].equals("login") || messageArray[0].equals("register")) {
                    try {
                        String username = messageArray[1];
                        String password = messageArray[2];
                        serverRequest.addProperty(messageArray[0].concat("Username") ,username);
                        serverRequest.addProperty(messageArray[0].concat("Password"), password);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.out.println("When using 'Login' or 'Register' need to provide username and password");
                        continue;
                    }
                }

                LOGGER.debug("Sending message to server: {}", serverRequest);
                messageServer(serverRequest.toString());
            }
        } catch (IOException e) {
            LOGGER.info("Connection with server cannot be established, or server disconnected");
            System.exit(0);
        }
    }

    private void messageServer(String message) {
        writer.println(message);
        writer.flush();
        LOGGER.debug("message sent to server: {}", message);
    }

    public class IncomingReader implements Runnable {
        @Override
        public void run() {
            LOGGER.info("Client reader startup successful");
            String message;
            try {
                while ((message = reader.readLine()) != null) {
                    LOGGER.debug("Received message: {}", message);
                    var jsonMessage = gson.fromJson(message, JsonObject.class);

                    if (jsonMessage.has("messageObject")) {
                        System.out.println("New message: " + jsonMessage.get("messageObject").getAsString());
                    } else {
                        System.out.println("Server response: " + message);

                        if (jsonMessage.get("status").getAsString().equals("success") &&
                            jsonMessage.get("message").getAsString().equals("Successfully Logged In")) {

                            setUsername(jsonMessage.get("loginUsername").getAsString());
                            setPassword(jsonMessage.get("loginPassword").getAsString());
                        } else if (jsonMessage.get("status").getAsString().equals("success") &&
                                jsonMessage.get("message").getAsString().contains("Sucessfully Registered")) {

                            setUsername(jsonMessage.get("registerUsername").getAsString());
                            setPassword(jsonMessage.get("registerPassword").getAsString());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Lost connection with server. Exit client.", e);
                System.exit(1);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleClient that = (SimpleClient) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
}