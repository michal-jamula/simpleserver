package simpleserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import simpleserver.client.SimpleClient;
import simpleserver.dto.RegisteredUserCredentials;
import simpleserver.repository.UserRepository;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    private SimpleClient connectedRegisteredUser;
    private SimpleClient registeredUser;
    @Mock
    UserRepository userRepository;
    @Mock
    MessageService messageService;
    @InjectMocks
    UserService userService;


    @BeforeEach
    void setup() {
        connectedRegisteredUser = SimpleClient.builder()
                .username("connectedUser")
                .password("password")
                .build();

        registeredUser = SimpleClient.builder()
                        .username("registeredUser")
                        .password("password")
                        .build();

        userService.getConnectedClients().put(mock(SocketChannel.class), "connectedUser");
    }

    @Test
    void verify() {
        //when
        boolean trueUser = userService.verifyUser(connectedRegisteredUser);
        boolean falseUser = userService.verifyUser(mock(SimpleClient.class));

        //then
        assertThat(trueUser).isTrue();
        assertThat(falseUser).isFalse();
    }

    @Test
    void loginUserReturnsSuccess() {
        //given
        when(userRepository.getAllUsers()).thenReturn(new ArrayList<>(List.of(new RegisteredUserCredentials("registeredUser", "password"))));
        int connectedUsers = userService.getConnectedClients().size();

        //when
        var result = userService.loginUser(mock(SocketChannel.class),
                "registeredUser", "password");

        //then
        assertThat(result).isEqualTo(LoginResult.LOGIN_SUCCESS);
        assertThat(userService.getConnectedClients().size()).isEqualTo(connectedUsers + 1);
    }

    @Test
    void loginUserNotFound() {
        //given
        when(userRepository.getAllUsers()).thenReturn(new ArrayList<>(List.of(new RegisteredUserCredentials("registeredUser", "password"))));
        int connectedUsers = userService.getConnectedClients().size();

        //when
        var result = userService.loginUser(mock(SocketChannel.class),
                "unknownUser", "unknownUser");

        //then
        assertThat(result).isEqualTo(LoginResult.USER_NOT_FOUND);
        assertThat(userService.getConnectedClients().size()).isEqualTo(connectedUsers);
    }

    @Test
    void loginUserAlreadyConnected() {
        //given
        when(userRepository.getAllUsers()).thenReturn(new ArrayList<>(List.of(new RegisteredUserCredentials("connectedUser", "password"))));
        int connectedUsers = userService.getConnectedClients().size();

        //when
        var result = userService.loginUser(mock(SocketChannel.class), "connectedUser", "password");

        //then
        assertThat(result).isEqualTo(LoginResult.USER_ALREADY_LOGGED_IN);
        assertThat(userService.getConnectedClients().size()).isEqualTo(connectedUsers);
    }

    @Test
    void registerNewUserSuccessful() {
        //given
        when(userRepository.getAllUsers()).thenReturn(new ArrayList<>(List.of(new RegisteredUserCredentials("registeredUser", "password"))));
        var newUser = new RegisteredUserCredentials("newUser", "password");

        //when
        var result = userService.registerNewUser(newUser.username(), newUser.password());

        //then
        Mockito.verify(userRepository, times(1)).addUser(newUser);
        assertThat(result).isTrue();
    }

    @Test
    void registerNewUserFail() {
        //given
        when(userRepository.getAllUsers()).thenReturn(new ArrayList<>(List.of(new RegisteredUserCredentials("registeredUser", "password"))));

        //when
        var result = userService.registerNewUser(registeredUser.getUsername(), registeredUser.getPassword());

        //then
        Mockito.verify(userRepository, times(0)).addUser(registeredUser.toRUC());
        assertThat(result).isFalse();
    }

    @Test
    void disconnectClient() {
        //given
        var connectedClients = userService.getConnectedClients();
        SocketChannel mockChannel = mock(SocketChannel.class);
        connectedClients.put(mockChannel, "user");

        assertTrue(connectedClients.containsKey(mockChannel));

        //when
        userService.disconnectClient(mockChannel);

        //then
        assertThat(connectedClients.containsKey(mockChannel)).isFalse();
    }
}