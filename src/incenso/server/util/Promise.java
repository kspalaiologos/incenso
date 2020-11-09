package incenso.server.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A promise - basic form of asynchronous operation utilized by the Incenso library.
 *
 * We can look at a promise from three viewpoints:
 * <ul>
 *     <li>The promise operation.</li>
 *     <li>The promise supplier.</li>
 *     <li>The promise receiver.</li>
 * </ul>
 *
 * The promise operation usually <code>extends Promise</code> and implements two methods - <code>onResolve</code>
 * and <code>process</code>. It can call <code>finish(X)</code> to mark the promise finished with value X, or
 * <code>fail(Y)</code> to mark the promise failed with value Y.
 *
 * TODO: A PROMISE CAN FINISH BEFORE UNWRAP AND ORELSE ARE REGISTERED. HANDLE THAT.
 *
 * @param <X> Finish type.
 * @param <Y> Fail type.
 */
public abstract class Promise<X, Y> {
    /**
     * Every promise can be:
     * <ul>
     *     <li>Finished - the operation completed successfully.</li>
     *     <li>Failed - the operation failed.</li>
     *     <li>Pending - the operation hasn't done it's work <i>yet</i>.</li>
     * </ul>
     */
    public enum Status {
        FINISHED, FAILED, PENDING
    }

    /**
     * Expected to be implemented by the Promise. Called before resolving the promise.
     * It's usually doing cleanup and unlocking the locks.
     *
     * It's guaranteed to be called from the same thread as `process'.
     */
    protected abstract void onResolve();

    /**
     * Expected to be implemented by the Promise. Called in a new thread soon after constructing the promise.
     */
    protected abstract void process();

    protected Object lock = new Object();

    private Consumer<X> cOk = null;
    private Consumer<Y> cFail = null;

    private AtomicReference<Status> promiseStatus;

    private final CountDownLatch resolveSync = new CountDownLatch(1);

    public Promise() {
        promiseStatus = new AtomicReference<>(Status.PENDING);
        new Thread(this::process, "Pending promise.").start();
    }

    public Promise<X,Y> unwrap(Consumer<X> x) {
        cOk = x;
        return this;
    }

    public Promise<X,Y> orElse(Consumer<Y> y) {
        cFail = y;
        return this;
    }

    protected void fail(Y y) {
        promiseStatus.set(Status.FAILED);

        if(cFail != null)
            cFail.accept(y);

        onResolve();

        resolveSync.countDown();
    }

    protected void finish(X x) {
        promiseStatus.set(Status.FINISHED);

        if(cOk != null)
            try {
                cOk.accept(x);
            } catch(Throwable t) {
                promiseStatus.set(Status.FAILED);
            }

        onResolve();

        resolveSync.countDown();
    }

    public void resolve() {
        try { resolveSync.await(); } catch(Exception e) { }
    }
}
