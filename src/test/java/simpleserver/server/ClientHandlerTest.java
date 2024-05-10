//package simpleserver.server;
//
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Disabled;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.slf4j.Logger;
//import simpleserver.dto.Message;
//import simpleserver.client.SimpleClient;
//import simpleserver.client.UserAuthority;
//import simpleserver.util.JsonResponse;
//
//import java.io.BufferedReader;
//import java.io.IOException;
//import java.io.Reader;
//import java.lang.reflect.Method;
//import java.nio.channels.Channels;
//import java.nio.channels.SocketChannel;
//import java.util.HashMap;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.*;
//
//
//@ExtendWith(MockitoExtension.class)
//class ClientHandlerTest {
//    Logger LOGGER = mock(Logger.class);
//    SimpleServer server = mock(SimpleServer.class);
//    Mailbox mailbox = mock(Mailbox.class);
//    BufferedReader reader = mock(BufferedReader.class);
//
//    ClientHandler clientHandler = new ClientHandler(
//            server,
//            mailbox,
//            mock(SocketChannel.class)
//    );
//    SimpleClient client;
//
//    JsonObject basicClientResponse;
//
//    @BeforeEach
//    void setup() {
//        client = SimpleClient.builder()
//                .username("user")
//                .password("password")
//                .isLoggedIn(false)
//                .authority(UserAuthority.USER)
//                .socketChannel(mock(SocketChannel.class))
//                .build();
//
//        clientHandler.setClient(client);
//
//        basicClientResponse = new JsonObject();
//        basicClientResponse.addProperty("user", new Gson().toJsonTree(JsonResponse.userResponse(
//                client.getUsername(), client.getPassword(), client.getAuthority(), client.isLoggedIn())).toString());
//
//    }
//    @Test
//    void processMessageRequest() throws Exception {
//        //given
//        JsonObject jsonMessage = new JsonObject();
//        jsonMessage.addProperty("request", "message");
//
//        var userMessage = new Message("receivingUser", "user", "new message for receiving user");
//        var messageObject = new Gson().toJson(userMessage);
//        jsonMessage.addProperty("messageObject", messageObject);
//
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processMessageRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, jsonMessage);
//
//
//        //then
//        var message = verify(mailbox).sendMessage(userMessage);
//        verify(server).sendJsonResponse(client, message);
//    }
//
//    @Test
//    void updateClientInfoFromRequest2() throws Exception {
//        // Mock the connected clients map
//        HashMap<SocketChannel, String> connectedClients = mock(HashMap.class);
//        when(server.getConnectedClients()).thenReturn(connectedClients);
//
//        // Prepare the rest of your test setup...
//        var updatedClient = SimpleClient.builder()
//                .username("updatedClient")
//                .password("updatedPassword")
//                .isLoggedIn(true)
//                .authority(UserAuthority.USER)
//                .build();
//
//        JsonObject jsonMessage = new JsonObject();
//        var userResponse = JsonResponse.userResponse(updatedClient.getUsername(),
//                updatedClient.getPassword(), updatedClient.getAuthority(), updatedClient.isLoggedIn());
//        jsonMessage.addProperty("user", new Gson().toJsonTree(userResponse).toString());
//
//        var voidClient = SimpleClient.builder()
//                .socketChannel(mock(SocketChannel.class))
//                .build();
//        clientHandler.setClient(voidClient);
//
//        // Invoke the method
//        Method method = ClientHandler.class.getDeclaredMethod("updateClientInfoFromRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, jsonMessage);
//
//        // Verify interactions
//        verify(connectedClients).put(any(SocketChannel.class), anyString());
//        assertThat(clientHandler.getClient()).isEqualTo(updatedClient);
//        assertThat(clientHandler.getClient().getPassword()).isEqualTo(updatedClient.getPassword());
//        assertThat(clientHandler.getClient().isLoggedIn()).isEqualTo(updatedClient.isLoggedIn());
//        assertThat(clientHandler.getClient().getAuthority()).isEqualTo(updatedClient.getAuthority());
//    }
//
//    @Test
//    void processClientLoginRequestShouldPass() throws Exception {
//        //given
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//        basicClientResponse.addProperty("loginUsername", client.getUsername());
//        basicClientResponse.addProperty("loginPassword", client.getPassword());
//        given(server.loginUser(client, client.getUsername(), client.getPassword())).willReturn(true);
//
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientLoginRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, basicClientResponse);
//
//        //then
//        verify(server).sendJsonResponse(any(SimpleClient.class), captor.capture());
//
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("success");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("Successfully Logged In");
//    }
//
//    @Test
//    void processClientLoginRequestShouldFail() throws Exception {
//        //given
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//        basicClientResponse.addProperty("loginUsername", client.getUsername());
//        basicClientResponse.addProperty("loginPassword", client.getPassword());
//        given(server.loginUser(client, client.getUsername(), client.getPassword())).willReturn(false);
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientLoginRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, basicClientResponse);
//
//        //then
//        verify(server).sendJsonResponse(any(SimpleClient.class), captor.capture());
//
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("error");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("Could not login");
//    }
//
//    @Test
//    void processClientLoginRequestWithWrongFormat() throws Exception {
//        //given
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//        basicClientResponse.addProperty("notLoginUsername", client.getUsername());
//        basicClientResponse.addProperty("wrongAPIStuffs", client.getPassword());
//        given(server.loginUser(client, client.getUsername(), client.getPassword())).willReturn(false);
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientLoginRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, basicClientResponse);
//
//        //then
//        verify(server).sendJsonResponse(any(SimpleClient.class), captor.capture());
//
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("error");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("Message not formatted properly. check API docs");
//    }
//
//    @Test
//    void processClientRegistrationFromRequest() throws Exception {
//        //given
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//
//        var jsonMessage = new JsonObject();
//        jsonMessage.addProperty("registerUsername", "newUsername");
//        jsonMessage.addProperty("registerPassword", "newPassword");
//
//        given(server.registerNewUser("newUsername", "newPassword")).willReturn(true);
//        given(server.loginUser(any(SimpleClient.class), anyString(), anyString())).willReturn(true);
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientRegistrationFromRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, jsonMessage);
//
//        //then
//        verify(server).sendJsonResponse(eq(client), captor.capture());
//
//
//        assertThat(client.getUsername()).isEqualTo("newUsername");
//        assertThat(client.getPassword()).isEqualTo("newPassword");
//        assertThat(captor.getValue().get("registerUsername").getAsString()).isEqualTo("newUsername");
//        assertThat(captor.getValue().get("registerPassword").getAsString()).isEqualTo("newPassword");
//        assertThat(captor.getValue().has("registerUsername")).isTrue();
//        assertThat(captor.getValue().has("registerPassword")).isTrue();
//    }
//
//
//    @Test
//    void processClientRegistrationFromRequestErrorLogging() throws Exception {
//        //given
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//
//        var jsonMessage = new JsonObject();
//        jsonMessage.addProperty("registerUsername", "newUsername");
//        jsonMessage.addProperty("registerPassword", "newPassword");
//
//        given(server.registerNewUser("newUsername", "newPassword")).willReturn(true);
//        given(server.loginUser(any(SimpleClient.class), anyString(), anyString())).willReturn(false);
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientRegistrationFromRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, jsonMessage);
//
//        //then
//        verify(server).sendJsonResponse(eq(client), captor.capture());
//
//
//        assertThat(client.getUsername()).isEqualTo("newUsername");
//        assertThat(client.getPassword()).isEqualTo("newPassword");
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("error");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("Client registered but can't login");
//    }
//
//    @Test
//    void processClientRegistrationFromRequestAlreadyRegistered() throws Exception {
//        //given
//        given(server.registerNewUser("newUsername", "newPassword")).willReturn(false);
//
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//
//        basicClientResponse.addProperty("registerUsername", "newUsername");
//        basicClientResponse.addProperty("registerPassword", "newPassword");
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientRegistrationFromRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, basicClientResponse);
//
//        //then
//        verify(server).sendJsonResponse(eq(client), captor.capture());
//
//
//        assertThat(client.getUsername()).isEqualTo("user");
//        assertThat(client.getPassword()).isEqualTo("password");
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("error");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("User already registered with this username");
//    }
//
//    @Test
//    void processClientRegistrationFromRequestWrongFormatting() throws Exception {
//        //given
//        given(server.registerNewUser("newUsername", "newPassword")).willReturn(false);
//
//        ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
//
//        basicClientResponse.addProperty("wrongProperty", "newUsername");
//        basicClientResponse.addProperty("anotherProperty", "newPassword");
//
//        //when
//        Method method = ClientHandler.class.getDeclaredMethod("processClientRegistrationFromRequest", JsonObject.class);
//        method.setAccessible(true);
//        method.invoke(clientHandler, basicClientResponse);
//
//        //then
//        verify(server).sendJsonResponse(eq(client), captor.capture());
//
//
//        assertThat(client.getUsername()).isEqualTo("user");
//        assertThat(client.getPassword()).isEqualTo("password");
//        assertThat(captor.getValue().get("status").getAsString()).isEqualTo("error");
//        assertThat(captor.getValue().get("message").getAsString()).isEqualTo("Error during registration, check API docs");
//    }
//
//    @Test
//    @Disabled
//    void runCallsUpdateClientInfoFromRequest() throws Exception{
//        //given
//        basicClientResponse.addProperty("request", "help");
//        given(reader.readLine()).willReturn(new Gson().toJsonTree(basicClientResponse).toString(), (String) null);
//        doNothing().when(server).verifyUser(any(SimpleClient.class));
//        doNothing().when(server).sendJsonResponse(any(SimpleClient.class), any(JsonObject.class));
//
//        //when
//        clientHandler.run();
//
//        //then
//        verify(server).getServerActions();
//    }
//
//
//}