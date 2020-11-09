package incenso.common;

/**
 * The disconnection packet. A graceful way to close the connection between the client and the server.
 *
 * @author Kamila Szewczyk
 */
public class PacketGoodbye implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_GOODBYE;
    }
}
