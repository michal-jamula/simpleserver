package simpleserver.exceptions;

public class UserAlreadyLoggedInException extends Exception{

    public UserAlreadyLoggedInException(String message) {
        super(message);
    }
}
