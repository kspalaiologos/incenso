package incenso.client;

import incenso.common.*;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * An entry point for the Incenso client.
 *
 * @author Kamila Szewczyk
 */
public class Start {
    /**
     * A private logger instance for the client.
     */
    private static Logger log = Logger.getLogger("Incenso");

    /**
     * HashMap to associate module names with class loaders.
     * This allows us to provide quick lookup functionality for unlink and upload packets.
     *
     * This HashMap will contain the only instance of ClassInjector available, which means
     * unlinking it will end up sooner or later unlinking all the classes resolved by it.
     */
    private static HashMap<String, ClassInjector> modules;

    /**
     * Milliseconds since last keepalive packet.
     */
    private static long beat = 0;

    /**
     * A custom classloader which exposes the `resolveClass' functionality.
     *
     * This is needed because we don't get sent raw class bytes (to then use a normal classloader), rather,
     * `Class' objects. The class is already defined, but it's <i>not resolved</i> yet.
     */
    private static class ClassInjector extends ClassLoader {
        public void inject(Class<?> c) {
            this.resolveClass(c);
        }
    }

    /**
     * Main channel of the server <=> client communication.
     * Processes a single packet a time.
     *
     * TODO: There are exceptions that can pop up here, but we don't handle them all, not obeying the protocol.
     *
     * @param out Output stream
     * @param in Input stream
     * @return true if looping is desired, false if not.
     */
    private static boolean messageLoop(ObjectOutputStream out, ObjectInputStream in) {
        try {
            Object message = in.readObject();
            Packet p;

            try {
                p = (Packet) message;
            } catch(Exception e) {
                // Note: We're aiming to be fault tolerant here, so we're silently ignoring incorrect packets
                // hoping that the server will eventually send something that makes sense.
                log.warning("Received a malformed packet.");
                e.printStackTrace();
                return true;
            }

            switch(p.getType()) {
                case PACKET_HANDSHAKE: {
                    // Handshake requires us to share our system specs.
                    // Query them now.
                    String jvmVersion = System.getProperty("java.version");
                    String jvmVendor = System.getProperty("java.vendor");
                    long maxMemory = Runtime.getRuntime().maxMemory() / 1024;
                    long maxStorage = new File(".").getUsableSpace() / 1000;
                    int availableProcessors = Runtime.getRuntime().availableProcessors();

                    // Check the server and client JVM version.
                    // Differences may result in strange behavior, so resolve that now.

                    // This is a single time check, so it's acceptable to fail now, as it's
                    // too early on to maintain connection at all costs.
                    if(!jvmVersion.equalsIgnoreCase(((PacketHandshake) p).jvmVersion())) {
                        log.severe("JVM version mismatch; got " + jvmVersion
                                + ", remote server has " + ((PacketHandshake) p).jvmVersion());
                        return false;
                    }

                    // JVM vendor mismatch.
                    // This one is treated leniently and it may or may not cause problems in the future.
                    // If something bad happens, smack a `return false;` there and s/warning/severe/;
                    if(!jvmVendor.equalsIgnoreCase(((PacketHandshake) p).jvmVendor())) {
                        log.warning("JVM vendor mismatch; got " + jvmVendor
                                + ", remote server has " + ((PacketHandshake) p).jvmVendor());
                    }

                    // Poke back the handshake packet.
                    out.writeObject(
                            new PacketHandshake(jvmVersion, jvmVendor, maxMemory, maxStorage, availableProcessors));
                    out.flush();

                    // Log the operation
                    log.info("Handshake requested.");

                    break;
                }

                case PACKET_INJECT: {
                    // Inject (resolve) a class object which logically belongs to a given module.
                    PacketInject packet = (PacketInject) p;

                    String name = packet.getName();

                    try {
                        Class<?> data = packet.getClassData();

                        // If the module hasn't been registered yet, register it.
                        if(!modules.containsKey(packet.getModule()))
                            modules.put(packet.getModule(), new ClassInjector());

                        // Register the class via module's classloader.
                        modules.get(packet.getModule()).inject(data);

                        log.info("Successfully injected " + name);
                        out.writeBoolean(true);
                        out.flush();
                    } catch(Exception e) {
                        // If something bad happened, report back to the server.
                        log.warning("Attempt scheduled by the remote server to load class `" +
                                name + "' has failed.");
                        e.printStackTrace();
                        out.writeBoolean(false);
                        out.flush();
                    }

                    break;
                }

                case PACKET_KEEPALIVE: {
                    // A keepalive packet. The server is expected to send it periodically to make
                    // sure the connection is stable and that the client is still connected.
                    // We measure the time between keepalive packets.

                    log.info("[" + (System.currentTimeMillis() - beat) + "us] Heartbeat.");
                    beat = System.currentTimeMillis();

                    // Echo back the same packet.
                    out.writeObject(new PacketKeepalive());
                    out.flush();
                    break;
                }

                case PACKET_GOODBYE: {
                    // Disconnection: Quit the client and log the message.
                    log.info("The server has requested disconnection.");
                    return false;
                }

                case PACKET_EXECUTE: {
                    // Execute an already injected class.
                    PacketExecute packet = (PacketExecute) p;

                    String name = packet.getName();

                    try {
                        // Instantiate the class on the clientside.
                        Class<?> c = packet.getClassData();
                        CodeChunk chunk = (CodeChunk) c.getDeclaredConstructor().newInstance();

                        // Log a message to indicate successful instantiation.
                        log.info("Processing chunk: " + name);

                        // Tell the server about successful operation.
                        out.writeBoolean(true);
                        out.flush();

                        // Read the data
                        Serializable obj = (Serializable) in.readObject();

                        // Acknowledge reading the data
                        log.info("Received data.");

                        // Start processing
                        long start = System.currentTimeMillis();
                        Serializable result = chunk.process(obj);
                        long end = System.currentTimeMillis();

                        // Tell the server about successful processing.
                        out.writeBoolean(true);
                        out.flush();

                        log.info("Operation finished in " + (end - start) + "ms.");

                        // Send back the success message.
                        out.writeObject(result);
                        out.flush();
                    } catch(Exception e) {
                        // If something bad happened, tell server about it.
                        // TODO: If an I/O exception occurs, then we leave the transmission in a bogus state.
                        log.warning("Attempt scheduled by the remote server to execute class `" +
                                name + "' has failed.");
                        out.writeBoolean(false);
                        out.writeObject(e);
                        out.flush();
                    }

                    break;
                }

                case PACKET_GC: {
                    // The server may request a GC cycle. This can happen for multiple reasons,
                    // one of which may be module unlinking. To ensure classes are no longer resolved,
                    // the classloader has to be unloaded, and triggering the garbage collection is the only
                    // way to do that.
                    log.info("GC cycle requested.");
                    System.gc();
                    break;
                }

                case PACKET_UNLINK: {
                    // Unlinking all classes from a given module.
                    // Note this may not take effect immediately.
                    String moduleName = ((PacketUnlink) p).getName();

                    log.warning("Requested to unlink all classes from " + moduleName);

                    // Check if a given module exists. Write back true/false depending on that.
                    if(modules.containsKey(moduleName)) {
                        modules.remove(moduleName);
                        out.writeBoolean(true);
                    } else {
                        out.writeBoolean(false);
                    }

                    break;
                }
            }

            return true;
        } catch(ClassNotFoundException e) {
            // That one we're treating leniently - just loop again.
            log.warning("Received object of unknown type.");
            e.printStackTrace();
            return true;
        } catch(IOException e) {
            // I/O exception usually means things are beyond recognition and it's better to just quit.
            log.severe("I/O exception.");
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        if(args.length != 2) {
            System.err.println("Incenso " + Version.VERSION + ". Copyright (C) 2020 Kamila Szewczyk.");
            System.err.println("Invocation:");
            System.err.println("  java -cp incenso.jar incenso.client.Start <server> <port> ");
            System.exit(1);
        }

        try {
            // Open the connection and I/O streams.
            // TODO: Wrap a GZIP compression over that.
            Socket connection = new Socket(args[0], Integer.parseInt(args[1]));
            ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(connection.getInputStream());

            beat = System.currentTimeMillis();

            // Process the message loop.
            while(messageLoop(out, in))
                ;

            // When messageLoop returns false, then clean up.
            out.close();
            in.close();
            connection.close();
            log.info("Quitting!");
        } catch (UnknownHostException e) {
            log.severe("Unknown host: " + args[0] + ":" + args[1]);
        } catch (IOException e) {
            log.severe("I/O exception.");
            e.printStackTrace();
        }
    }
}
