package incenso.common;

/**
 * The keepalive packet. Used to ensure connection between the client and server.
 * The server is expected to respond with an instance of such.
 */
public class PacketKeepalive implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_KEEPALIVE;
    }
}
