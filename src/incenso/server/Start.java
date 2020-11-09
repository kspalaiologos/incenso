package incenso.server;

import incenso.common.CodeChunk;
import incenso.server.transport.IncensoServer;

import java.io.IOException;
import java.io.Serializable;
import java.util.stream.IntStream;

public class Start {
    public static class Printer implements CodeChunk {
        @Override
        public Serializable process(Serializable data) {
            IntStream.range(1, 10).forEach(z -> System.out.println(((String) data) + " " + z));
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            // Start listening for incoming connections.
            IncensoServer srv = new IncensoServer(1234);

            // Wait until we have two clients available.
            while(srv.clientCount() != 5)
                ;

            System.err.println("Got 5 clients!");

            // Stop accepting new connections.
            srv.close();

            // Send a hello world program to each of the clients.
            srv.clientForEach(x -> x.scheduleNew(Printer.class, "Hello, world!")
                    .unwrap(a -> System.out.println("Sent code to client!"))
                    .orElse(a -> a.printStackTrace()));

            // Close all connections.
            srv.dispose();
        } catch (IOException e) {
            e.printStackTrace(); // Poor man's error handling.
        }
    }
}
