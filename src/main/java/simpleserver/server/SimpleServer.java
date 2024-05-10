package simpleserver.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.repository.MessageRepository;
import simpleserver.repository.UserRepository;
import simpleserver.service.MessageService;
import simpleserver.service.UserService;
import simpleserver.util.LoggingUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServer {
    private final static Logger LOGGER = LoggerFactory.getLogger(SimpleServer.class);
    private final UserService userService;
    private final MessageService messageService;
    private final ClientResponder clientResponder;


    public SimpleServer(UserService userService, MessageService messageService, ClientResponder clientResponder) {
        this.clientResponder = clientResponder;
        this.messageService = messageService;
        this.userService = userService;

    }

    public static void main(String[] args){
        LoggingUtil.initLogManager();

        String messageFilePath = "successfulMessages.json";
        String registeredUsersFilePath = "registeredUsers.json";

        var userRepository = new UserRepository(registeredUsersFilePath);
        var messageRepository = new MessageRepository(messageFilePath);

        var serverRequests = new ServerRequests(LocalDateTime.now());


        MessageRepository.startupFormatting(messageFilePath);
        new Thread(messageRepository).start();


        var messageService = new MessageService(messageRepository);
        var userService = new UserService(messageService, userRepository);
        var clientResponder = new ClientResponder(serverRequests);

        new SimpleServer(userService, messageService, clientResponder).start();
    }


    public void start() {
        ExecutorService readThread = Executors.newCachedThreadPool();

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(5000));

            LOGGER.info("Server is up and running");
            while (serverChannel.isOpen()) {
                SocketChannel clientSocket = serverChannel.accept();
                userService.getConnectedClients().put(clientSocket, null);

                readThread.submit(new ClientRequestHandler(clientResponder, clientSocket, userService, messageService));
                LOGGER.info("Server received a new client");
            }
        } catch (IOException e) {
            LOGGER.error("Server unable to start at port: 5000. Terminating server");
            System.exit(0);
        }
    }

}