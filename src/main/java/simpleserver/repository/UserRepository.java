package simpleserver.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.dto.RegisteredUserCredentials;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class UserRepository {
    private final static Logger LOGGER = LoggerFactory.getLogger(UserRepository.class);

    private final ArrayList<RegisteredUserCredentials> registeredUsers;
    private final String filePath;

    public UserRepository(String filePath) {
        registeredUsers = loadUsersFromFile(filePath);
        this.filePath = filePath;

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveRegisteredUsers));
    }

    public ArrayList<RegisteredUserCredentials> getAllUsers() {
        return registeredUsers;
    }

    public void addUser(RegisteredUserCredentials userCredentials) {
        registeredUsers.add(userCredentials);
    }

    private void saveRegisteredUsers() {
        try {
            FileWriter fw = new FileWriter(filePath);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(registeredUsers);
            fw.write(json);
            fw.close();
            System.out.println("Saved users to local file successfully.");
        } catch (IOException e) {
            System.out.println("File not found, cannot save users");
        } catch (NullPointerException e) {
            System.out.println("Not saving users to file - file writer is null");
        }
    }


    private ArrayList<RegisteredUserCredentials> loadUsersFromFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            LOGGER.warn("Invalid file path: {}", filePath);
            return new ArrayList<>();
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            LOGGER.warn("File does not exist: {}", filePath);
            return new ArrayList<>();
        }

        try {
            String data = Files.readString(path);
            if (data == null || data.isEmpty()) {
                LOGGER.warn("File is empty, file path: {}", filePath);
                return new ArrayList<>();
            }

            Type registeredUserType = new TypeToken<ArrayList<RegisteredUserCredentials>>() {}.getType();
            ArrayList<RegisteredUserCredentials> registeredUsersFromFile = new Gson().fromJson(data, registeredUserType);
            LOGGER.info("Successfully read registered users from file");
            return registeredUsersFromFile;
        } catch (IOException e) {
            LOGGER.warn("Able to access file, but unable to read users. Probably file formatting is wrong");
            return new ArrayList<>();
        }
    }
}
