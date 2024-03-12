package simpleserver;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class SimpleClient {
    final static Logger LOGGER = LoggerFactory.getLogger(SimpleClient.class.getName());
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private final Gson gson = new Gson();
    private SocketChannel socketChannel;

    public SimpleClient(String username) {
        this.username = username;
    }
    public SimpleClient (String username, SocketChannel socketChannel) {
        this.username = username;
        this.socketChannel = socketChannel;
    }

    public SimpleClient() {}

    private void connectToServer() {
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

                serverResponse.addProperty("username", this.username);

                if (messageArray[0].equals("message")) {
                    var receiverId = message.split(" ")[1];
                    var messagePayload = String.join(" ", Arrays.copyOfRange(messageArray, 2, messageArray.length));

                    var userMessage = new Message(receiverId, this.username, messagePayload);

                    var jsonMessage = gson.toJson(userMessage);
                    serverResponse.addProperty("messageObject", jsonMessage);
                } else {
                    serverResponse.addProperty("serverRequest", messageArray[0]);
                }
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
                    //LOGGER.debug("Received message: {}", message);
                    var jsonMessage = gson.fromJson(message, JsonObject.class);

                    if (jsonMessage.has("messageObject")) {
                        var messageObj = gson.fromJson(jsonMessage.get("messageObject").getAsString(), Message.class);
                        System.out.printf("New message from %s.\nMessage: %s\n", messageObj.senderId(), messageObj.message());
                    } else {
                        System.out.println("Server response: " + message);

                    }

                }
            } catch (IOException e) {
                LOGGER.warn("Lost connection with server. Exit client.", e);
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) {
        LoggingUtil.initLogManager();
        new SimpleClient("john").connectToServer();
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

    public String getUsername() {
        return username;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }
}