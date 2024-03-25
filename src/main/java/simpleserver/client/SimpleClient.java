package simpleserver.client;

import com.google.gson.*;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.Message;
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
    final static Logger LOGGER = LoggerFactory.getLogger(SimpleClient.class.getName());
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
                var serverResponse = new JsonObject();

                var message = clientOptionReader.readLine();
                var messageArray = message.split(" ");

                var userProperty = new JsonObject();
                userProperty.addProperty("username", this.username);
                userProperty.addProperty("password", this.password);
                userProperty.addProperty("authority", this.authority.toString());
                userProperty.addProperty("isLoggedIn", this.isLoggedIn);

                serverResponse.addProperty("user", gson.toJsonTree(userProperty).toString());

                serverResponse.addProperty("request", messageArray[0]);

                if (messageArray[0].equals("message")) {
                    var receiverId = message.split(" ")[1];
                    var messagePayload = String.join(" ", Arrays.copyOfRange(messageArray, 2, messageArray.length));

                    var userMessage = new Message(receiverId, this.username, messagePayload);

                    var jsonMessage = gson.toJson(userMessage);
                    serverResponse.addProperty("messageObject", jsonMessage);
                } else if (messageArray[0].equals("login")) {
                    String loginUsername = messageArray[1];
                    String loginPassword = messageArray[2];
                    serverResponse.addProperty("loginUsername", loginUsername);
                    serverResponse.addProperty("loginPassword", loginPassword);

                }

                LOGGER.info("Sending message to server: {}", serverResponse);
                messageServer(serverResponse.toString());
            }
        } catch (IOException e) {
            LOGGER.warn("Connection with server cannot be established, or server disconnected");
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