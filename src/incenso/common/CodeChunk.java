package incenso.common;

import java.io.Serializable;

/**
 * A generic interface implemented by all code fragments transferred over the protocol.
 *
 * @author Kamila Szewczyk
 */
public interface CodeChunk extends Serializable {
    /**
     * Perform an operation.
     *
     * Note: This method may or may not have side effects, you get to choose.
     * You can't rely on the remote state if you're aiming at maximum fault tolerance.
     *
     * @param data Operation input
     * @return Operation output
     */
    Serializable process(Serializable data);
}
