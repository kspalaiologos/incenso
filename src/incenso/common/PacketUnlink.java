package incenso.common;

/**
 * The unlink packet. Schedules the client to remove given module's classloader, causing all the loaded
 * classes to be unlinked. This may or may not take effect instantly, it may be needed to send a PacketGC
 * soon afterwards, but even that doesn't guarantee instant collection.
 *
 * @see PacketGC
 * @author Kamila Szewczyk
 */
public class PacketUnlink implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_UNLINK;
    }

    private String name;

    public String getName() {
        return name;
    }

    public PacketUnlink(String name) {
        this.name = name;
    }
}
