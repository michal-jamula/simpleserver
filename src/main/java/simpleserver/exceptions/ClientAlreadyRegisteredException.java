package simpleserver.exceptions;

public class ClientAlreadyRegisteredException extends Exception{
    public ClientAlreadyRegisteredException(String message) {
        super(message);
    }
}
