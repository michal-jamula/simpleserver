package simpleserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleClient {
    private BufferedReader reader;
    private PrintWriter writer;


    private void connectToServer() {
        try {
            InetSocketAddress serverAddress = new InetSocketAddress("localhost", 5000);
            SocketChannel socketChannel = SocketChannel.open(serverAddress);

            reader = new BufferedReader(Channels.newReader(socketChannel, StandardCharsets.UTF_8));
            writer = new PrintWriter(Channels.newWriter(socketChannel, StandardCharsets.UTF_8));

            ExecutorService readThread = Executors.newSingleThreadExecutor();
            readThread.execute(new IncomingReader());

            System.out.println("Connection with server established");


            while (true) {
                BufferedReader clientOptionReader = new BufferedReader(new InputStreamReader(System.in));

                messageServer(clientOptionReader.readLine());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void messageServer(String message) {
        writer.println(message);
        writer.flush();
    }

    public class IncomingReader implements Runnable {

        @Override
        public void run() {
            String message;

            try {
                while ((message = reader.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                System.err.println("Lost or couldn't establish connection with server. Exiting.");
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        new SimpleClient().connectToServer();

    }
}
