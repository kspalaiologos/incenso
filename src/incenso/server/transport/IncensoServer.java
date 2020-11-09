package incenso.server.transport;

import incenso.server.util.EventDispatcher;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * IncensoServer is responsible for two things:
 * <ul>
 *     <li>Accepting new connections and registering them.</li>
 *     <li>Providing event-driven interface regarding connections and disconnections.</li>
 *     <li>Providing generalized access to all the clients.</li>
 * </ul>
 *
 * TODO: Granular access to clients.
 *
 * @author Kamila Szewczyk
 * @see Client
 * @see EventDispatcher
 */
public class IncensoServer {
    /**
     * A server socket instance, from which we will accept connections.
     */
    private ServerSocket s;

    /**
     * The poller thread, which will accept connections from the server socket.
     * A handle to it is needed to be able to gracefully interrupt and quit it.
     */
    private Thread poller;

    /**
     * A list of connected clients.
     */
    private ArrayList<Client> clients;

    /**
     * A lock to ensure no parallel accesses to the list.
     */
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The connection backlog - how many connections do we queue before dropping them.
     * The backlog can be left small because the connection overhead is quite low - creating
     * a Client instance is instant, because Client uses unresolved promises to ensure it's fully
     * asynchronous
     */
    private static final int SERVER_BACKLOG = 8;

    private EventDispatcher<Client> evtOnConnect = new EventDispatcher<>();
    private EventDispatcher<Client> evtOnDisconnect = new EventDispatcher<>();

    /**
     * @return the onConnect event dispatcher.
     */
    public EventDispatcher<Client> onConnect() { return evtOnConnect; }

    /**
     * @return the onDisconnect event dispatcher.
     */
    public EventDispatcher<Client> onDisconnect() { return evtOnDisconnect; }

    /**
     * Start the Incenso server, which will listen on a given port.
     * Construct the poller thread and start it.
     *
     * @param port
     * @throws IOException
     */
    public IncensoServer(int port) throws IOException {
        s = new ServerSocket(port, SERVER_BACKLOG);
        clients = new ArrayList<Client>();

        poller = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Client cl = new Client(IncensoServer.this, s.accept());

                        evtOnConnect.broadcast(cl);

                        lock.writeLock().lock();
                        clients.add(cl);
                        lock.writeLock().unlock();
                    }
                } catch(Throwable t) {
                    return;
                }
            }
        }, "Incenso Poller thread.");

        poller.start();
    }

    /**
     * Unlink a client from the client list.
     * Should be used exclusively by Client instances.
     * @param c
     */
    protected void clientUnlink(Client c) {
        lock.writeLock().lock();
        clients.remove(c);
        lock.writeLock().unlock();
    }

    /**
     * Stop listening for connections.
     * This action is final and it's not undoable.
     * @throws IOException
     */
    public void close() throws IOException {
        s.close();
    }

    /**
     * Disconnect all clients.
     */
    public void dispose() {
        lock.writeLock().lock();
        clients.forEach(x -> x.disconnect().resolve());
        clients.clear();
        lock.writeLock().unlock();
    }

    /**
     * @return the amount of clients connected to the server.
     */
    public int clientCount() {
        lock.readLock().lock();
        int size = clients.size();
        lock.readLock().unlock();
        return size;
    }

    /**
     * Execute a consumer over all clients.
     */
    public void clientForEach(Consumer<? super Client> action) {
        lock.writeLock().lock();
        clients.forEach(action);
        lock.writeLock().unlock();
    }
}
