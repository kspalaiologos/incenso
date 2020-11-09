package incenso.common;

/**
 * Schedule a GC cycle.
 * May or may not take effect immediately.
 *
 * @author Kamila Szewczyk
 */
public class PacketGC implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_GC;
    }
}
