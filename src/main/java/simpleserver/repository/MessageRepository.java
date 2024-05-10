package simpleserver.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simpleserver.dto.Message;

import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ArrayBlockingQueue;

public class MessageRepository implements Runnable {
    private final static Logger LOGGER = LoggerFactory.getLogger(MessageRepository.class);

    private final ArrayBlockingQueue<Message> messagesToSave;
    private final String filePath;

    public MessageRepository(String filePath) {
        messagesToSave = new ArrayBlockingQueue<>(50);
        this.filePath = filePath;
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownFormatting));
    }


    public void saveMessage(Message message) {
        LOGGER.debug("Received message to save: {}", message);
        messagesToSave.add(message);
    }

    @Override
    public void run() {
        while (true) {
            while (!messagesToSave.isEmpty()) {
                LOGGER.debug("MessageRepository has a message to save");

                try (FileWriter fw = new FileWriter(filePath, true)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();

                    String json = gson.toJson(messagesToSave.take());
                    fw.write(json + ',');

                    fw.close();
                    LOGGER.info("Saved message to file successfully!");
                } catch (IOException | InterruptedException e) {
                    LOGGER.warn("File not found, or thread interrupted!: {}", e.toString());
                }
            }
        }
    }


    public static void startupFormatting(String filePath) {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "rw")) {
            long fileLength = file.length();
            if (fileLength > 0) {
                file.seek(fileLength - 1);
                int lastChar = file.readByte();
                if (lastChar == ']') {
                    file.setLength(fileLength - 1);
                    if (file.length() > 1)
                        file.write(',');
                }
            }
            LOGGER.debug("Startup formatting successful");
        } catch (IOException e) {
            LOGGER.warn("Unable to delete ']' from file {}", e.toString());
        }
    }

    public void shutdownFormatting() {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "rw")) {
            long fileLength = file.length();
            if (fileLength > 0) {
                file.seek(fileLength - 1);

                int lastChar = file.readByte();
                if (lastChar == ',') {
                    file.setLength(fileLength - 1);
                    file.write(']');
                }
            }
            LOGGER.debug("Shutdown formatting successful");
        } catch (IOException e) {
            LOGGER.warn("Unable to append ']' to file {}", e.toString());
        }
    }
}
