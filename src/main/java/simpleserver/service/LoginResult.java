package simpleserver.service;

public enum LoginResult {
    LOGIN_SUCCESS,
    USER_NOT_FOUND,
    USER_ALREADY_LOGGED_IN;


    @Override
    public String toString() {
        return name().toLowerCase().replace('_', ' ');
    }
}
