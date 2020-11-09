package incenso.common;

import java.io.Serializable;

/**
 * A generic packet interface.
 *
 * @author Kamila Szewczyk
 */
public interface Packet extends Serializable {
    /**
     * The only method a basic packet has to implement - identifying itself.
     * @return The packet type.
     *
     * @see PacketType
     */
    PacketType getType();
}
