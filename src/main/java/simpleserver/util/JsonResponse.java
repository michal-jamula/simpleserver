package simpleserver.util;

import com.google.gson.JsonObject;
import simpleserver.client.UserAuthority;

public class JsonResponse{

    public static JsonObject serverResponse(String status, String message) {
        JsonObject response = new JsonObject();

        response.addProperty("status", status);
        response.addProperty("message", message);

        return response;
    }

    public static JsonObject userResponse(String username, String password, UserAuthority authority, boolean isLoggedIn) {
        var response = new JsonObject();

        response.addProperty("username", username);
        response.addProperty("password", password);
        response.addProperty("authority", authority.toString());
        response.addProperty("isLoggedIn", isLoggedIn);

        return response;
    }
}
