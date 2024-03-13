package simpleserver.util;

import com.google.gson.JsonObject;

public class JsonResponse{

    public static JsonObject serverResponse(String status, String message) {
        JsonObject response = new JsonObject();

        response.addProperty("status", status);
        response.addProperty("message", message);

        return response;
    }
}
