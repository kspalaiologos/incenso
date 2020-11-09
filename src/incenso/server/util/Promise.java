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
 * A promise has two callbacks - <code>unwrap</code> and <code>orElse</code>. Set them <i>only once</i>. Changing
 * the callback for an existing promise results in undefined behaviour.
 *
 * @param <X> Finish type.
 * @param <Y> Fail type.
 */
public abstract class Promise<X, Y> {
    /**
     * Every promise can be either:
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

    /**
     * Action executed when a promise finishes.
     */
    private Consumer<X> cOk = null;

    /**
     * Action executed when a promise fails.
     */
    private Consumer<Y> cFail = null;

    /**
     * Value of the promise, in case it finishes.
     * It's kept because actions are sometimes invoked after the promise finishes,
     * and the value has to be fed into the consumer.
     */
    private X okValue = null;

    /**
     * Value of the promise, in case it fails.
     * It's kept because actions are sometimes invoked after the promise fails,
     * and the value has to be fed into the consumer.
     */
    private Y failValue = null;

    /**
     * Atomic promise status. It can be read and set from multiple threads,
     * so we want to ensure synchronized access.
     */
    private AtomicReference<Status> promiseStatus;

    /**
     * A lock used by `resolve', which joins the promise thread into the current (waiting) thread.
     */
    private final CountDownLatch resolveSync = new CountDownLatch(1);

    /**
     * An empty promise constructor. Starts the promise thread and initializes the promise status to
     * <code>Status.PENDING</code>.
     */
    public Promise() {
        promiseStatus = new AtomicReference<>(Status.PENDING);
        new Thread(this::process, "Pending promise.").start();
    }

    /**
     * Unwrap the promise: When the promise finishes, execute a given callback with the finish value.
     * Either this or <code>orElse</code> is guaranteed to be called eventually.
     *
     * @return Current promise.
     */
    public Promise<X,Y> unwrap(Consumer<X> x) {
        cOk = x;

        if(okValue != null)
            cOk.accept(okValue);

        return this;
    }

    /**
     * Execute a consumer when the promise fails with a given fail value.
     * Either this or <code>unwrap</code> is guaranteed to be called eventually.
     *
     * @return Current promise.
     */
    public Promise<X,Y> orElse(Consumer<Y> y) {
        cFail = y;

        if(failValue != null)
            cFail.accept(failValue);

        return this;
    }

    /**
     * Internal promise API. Marks the promise as failed, sets the failure value,
     * schedules calling the failure consumer, notifies threads waiting for the promise
     * to be resolved and calls <code>onResolve</code> handlers.
     * @param y
     */
    protected void fail(Y y) {
        promiseStatus.set(Status.FAILED);

        failValue = y;

        if(cFail != null)
            cFail.accept(y);

        onResolve();

        resolveSync.countDown();
    }

    /**
     * Internal promise API. Marks the promise as finished, sets the finish value,
     * schedules calling the <code>unwrap</code> consumer and notifies threads waiting for the
     * promise to be resolved and calls <code>onResolve</code> handlers.
     * @param x
     */
    protected void finish(X x) {
        promiseStatus.set(Status.FINISHED);

        okValue = x;

        if(cOk != null)
            try {
                cOk.accept(x);
            } catch(Throwable t) {
                promiseStatus.set(Status.FAILED);
            }

        onResolve();

        resolveSync.countDown();
    }

    /**
     * Block the current thread until the promise is resolved.
     */
    public void resolve() {
        try { resolveSync.await(); } catch(Exception e) { }
    }
}
