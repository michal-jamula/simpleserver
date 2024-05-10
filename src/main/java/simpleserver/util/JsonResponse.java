package simpleserver.util;

import com.google.gson.JsonObject;
import simpleserver.client.UserAuthority;

public class JsonResponse{

    //change status to enum
    public static JsonObject serverResponse(StatusEnum status, String message) {
        JsonObject response = new JsonObject();

        response.addProperty("status", status.toString());
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
