package simpleserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import simpleserver.client.SimpleClient;
import simpleserver.dto.Message;
import simpleserver.repository.MessageRepository;
import simpleserver.util.StatusEnum;

import java.lang.reflect.Field;
import java.util.HashMap;
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
    private HashMap<SimpleClient, LinkedList<Message>> unreadMessages;

    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException{
        Field unreadMessagesField = MessageService.class.getDeclaredField("unreadMessages");
        unreadMessagesField.setAccessible(true);

        unreadMessages = (HashMap<SimpleClient, LinkedList<Message>>) unreadMessagesField.get(messageService);


        this.connectedUser = SimpleClient.builder()
                .username("connectedUser")
                .build();

        unreadMessages.put(connectedUser, new LinkedList<>());
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
        assertThat(unreadMessages.get(newUser)).isNotNull();
        assertThat(unreadMessages.get(newUser)).hasSize(0);

    }

    @Test
    void sendMessageSuccessful() {
        //given
        Message inputMessage = new Message("connectedUser", "sender", "message Payload");

        var unreadMessageSize = unreadMessages.get(connectedUser).size();

        //when
        var jsonResponse = messageService.sendMessage(inputMessage);

        //then
        Mockito.verify(messageRepository, times(1)).saveMessage(inputMessage);
        assertThat(unreadMessages.get(connectedUser).size()).isEqualTo(unreadMessageSize + 1);
        assertThat(jsonResponse.has("status")).isTrue();
        assertThat(jsonResponse.has("message")).isTrue();
        assertThat(jsonResponse.get("status").getAsString()).isEqualTo(StatusEnum.SUCCESS.toString());
    }

    @Test
    void sendMessageMailboxFull() {
        //given

        IntStream.rangeClosed(0, 4).forEach(message -> unreadMessages.get(connectedUser).add(mock(Message.class)));
        var unreadMessageSize = unreadMessages.get(connectedUser).size();
        Message inputMessage = new Message("connectedUser", "sender", "message Payload");

        //when
        var jsonResponse = messageService.sendMessage(inputMessage);

        //then
        verifyNoInteractions(messageRepository);
        assertThat(unreadMessages.get(connectedUser).size()).isEqualTo(unreadMessageSize);
        assertThat(jsonResponse.has("status")).isTrue();
        assertThat(jsonResponse.has("message")).isTrue();
        assertThat(jsonResponse.get("status").getAsString()).isEqualTo(StatusEnum.ERROR.toString());
    }


    @Test
    void openMessageSuccessful() {
        //given
        var expectedMessage = new Message(connectedUser.getUsername(), "sender", "message Payload");

        unreadMessages.get(connectedUser).add(expectedMessage);


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