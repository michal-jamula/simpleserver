package simpleserver.exceptions;

public class ClientAlreadyLoggedInException extends Exception{

    public ClientAlreadyLoggedInException(String message) {
        super(message);
    }
}
