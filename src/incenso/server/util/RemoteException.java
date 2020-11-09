package incenso.server.util;

/**
 * A generic exception to signalize a fault on the remote side, or an exception while communicating with it.
 * Usually has a cause.
 *
 * @author Kamila Szewczyk
 */
public class RemoteException extends Exception {
    public RemoteException(String msg) {
        super(msg);
    }
    public RemoteException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
