package simpleserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import simpleserver.Message;
import simpleserver.client.SimpleClient;
import simpleserver.repository.MessageRepository;

import java.util.LinkedList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock
    MessageRepository messageRepository;

    @InjectMocks
    MessageService messageService;

    private SimpleClient connectedUser;

    @BeforeEach
    void setup() {
        this.connectedUser = SimpleClient.builder()
                .username("connectedUser")
                .build();

        messageService.getUnreadMessages().put(connectedUser, new LinkedList<>());
    }

    @Test
    void addClient() {
        //given
        SimpleClient newUser = SimpleClient.builder()
                .username("newUser")
                .build();
        //when
        messageService.addClient(newUser);

        //then
        assertThat(messageService.getUnreadMessages().get(newUser)).isNotNull();
        assertThat(messageService.getUnreadMessages().get(newUser)).hasSize(0);

    }

    @Test
    void sendMessageSuccessful() {
        //given
        Message inputMessage = new Message("connectedUser", "sender", "message Payload");

        var unreadMessageSize = messageService.getUnreadMessages().get(connectedUser).size();

        //when
        var jsonResponse = messageService.sendMessage(inputMessage);

        //then
        Mockito.verify(messageRepository, times(1)).saveMessage(inputMessage);
        assertThat(messageService.getUnreadMessages().get(connectedUser).size()).isEqualTo(unreadMessageSize + 1);
        assertThat(jsonResponse.has("status")).isTrue();
        assertThat(jsonResponse.has("message")).isTrue();
        assertThat(jsonResponse.get("status").getAsString()).isEqualTo("success");
    }

    @Test
    void sendMessageMailboxFull() {
        //given
        IntStream.rangeClosed(0, 4).forEach(message -> {
            messageService.getUnreadMessages().get(connectedUser).add(mock(Message.class));
        });
        var unreadMessageSize = messageService.getUnreadMessages().get(connectedUser).size();
        Message inputMessage = new Message("connectedUser", "sender", "message Payload");


        //when
        var jsonResponse = messageService.sendMessage(inputMessage);

        //then
        verifyNoInteractions(messageRepository);
        assertThat(messageService.getUnreadMessages().get(connectedUser).size()).isEqualTo(unreadMessageSize);
        assertThat(jsonResponse.has("status")).isTrue();
        assertThat(jsonResponse.has("message")).isTrue();
        assertThat(jsonResponse.get("status").getAsString()).isEqualTo("error");
    }


    @Test
    void openMessageSuccessful() {
        //given
        var expectedMessage = new Message(connectedUser.getUsername(), "sender", "message Payload");
        messageService.getUnreadMessages().get(connectedUser).add(expectedMessage);

        //when
        var jsonResponse = messageService.openMessage(connectedUser);

        //then
        assertThat(jsonResponse.has("messageObject")).isTrue();
        assertThat(jsonResponse.get("message").getAsString()).isEqualTo("New message");
    }

    @Test
    void openMessageIsEmpty() {
        //given

        //when
        var jsonResponse = messageService.openMessage(connectedUser);

        //then
        assertThat(jsonResponse.has("messageObject")).isFalse();
        assertThat(jsonResponse.get("message").getAsString()).isEqualTo("No new messages");
    }
}