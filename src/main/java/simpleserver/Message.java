package simpleserver;

import java.util.Objects;

public record Message (String receiverId, String senderId, String message) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message1 = (Message) o;
        return Objects.equals(receiverId, message1.receiverId) && Objects.equals(senderId, message1.senderId) && Objects.equals(message, message1.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiverId, senderId, message);
    }
}

