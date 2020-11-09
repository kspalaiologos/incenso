package incenso.server.util;

import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * A basic event dispatcher.
 * Registers event handlers and allows broadcasting messages to them.
 *
 * @param <T>
 */
public class EventDispatcher<T> {
    /**
     * Current list of all event handlers.
     */
    private Vector<Consumer<T>> eventHandlers = new Vector<>();

    /**
     * A lock to ensure no parallel access to the event handlers vector.
     */
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Register an event handler.
     * It will start getting notified about broadcasts.
     * @param handler
     */
    public void register(Consumer<T> handler) {
        lock.writeLock().lock();
        eventHandlers.add(handler);
        lock.writeLock().unlock();
    }

    /**
     * Unregister an event handler.
     * It will no longer get notified about broadcasts.
     * @param handler
     */
    public void unregister(Consumer<T> handler) {
        lock.writeLock().lock();
        eventHandlers.remove(handler);
        lock.writeLock().unlock();
    }

    /**
     * Broadcast a message to all event handlers.
     * @param x
     */
    public void broadcast(T x) {
        lock.readLock().lock();
        eventHandlers.forEach(y -> y.accept(x));
        lock.readLock().unlock();
    }
}
