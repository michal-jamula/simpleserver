package simpleserver.service;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.client.SimpleClient;
import simpleserver.dto.RegisteredUserCredentials;
import simpleserver.repository.UserRepository;

import java.nio.channels.SocketChannel;
import java.util.HashMap;


@Getter
public class UserService {
    private final static Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private final HashMap<SocketChannel, String> connectedClients;
    private final MessageService messageService;
    private final UserRepository userRepository;


    public UserService(MessageService messageService, UserRepository userRepository) {
        this.userRepository = userRepository;
        this.messageService = messageService;
        this.connectedClients = new HashMap<>();
        //Runtime.getRuntime().addShutdownHook(new Thread(this::saveRegisteredUsers));
    }

    public boolean verifyUser(SimpleClient client) {
        return StringUtils.isNotBlank(client.getUsername()) ||
                StringUtils.isNotBlank(client.getPassword());
    }

    public LoginResult loginUser(SocketChannel socketChannel, String username, String password) {
        if (userRepository.getAllUsers().stream()
                .anyMatch(user -> user.username().equals(username) && user.password().equals(password))) {

            if (connectedClients.containsValue(username))
                return LoginResult.USER_ALREADY_LOGGED_IN;

            var connectedUser = SimpleClient.builder()
                    .username(username)
                    .socketChannel(socketChannel)
                    .build();

            connectedClients.put(socketChannel, username);
            messageService.addClient(connectedUser);

            return LoginResult.LOGIN_SUCCESS;
        }

        return LoginResult.USER_NOT_FOUND;
    }

    public boolean registerNewUser(String username, String password) {
        var clientCredential = new RegisteredUserCredentials(username, password);

        for (RegisteredUserCredentials credentials : userRepository.getAllUsers()) {
            if (credentials.username().equals(username)) {
                LOGGER.info("Client with {} is already registered", username);
                return false;
            }
        }

        userRepository.addUser(clientCredential);
        LOGGER.info("Added new user to list of registered users");
        return true;
    }

    public void disconnectClient(SocketChannel channel) {
        connectedClients.remove(channel);
    }

    public void updateClient(SimpleClient client) {
        if (connectedClients.containsKey(client.getSocketChannel())) {
            connectedClients.put(client.getSocketChannel(), client.getUsername());
        }
    }

}
