package incenso.common;

/**
 * A handshake packet. The first packet sent from server to client and vice versa.
 * The server is expected to send this packet with only fields `version' and `vendor' set.
 * The client is expected to send this packet with all fields set.
 *
 * The server can set the `ram', `storage' and `cpus' fields to zero.
 *
 * @author Kamila Szewczyk
 */
public class PacketHandshake implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_HANDSHAKE;
    }

    private String version;
    private String vendor;
    private long ram;
    private long storage;
    private int cpus;

    public String jvmVersion() {
        return version;
    }

    public String jvmVendor() {
        return vendor;
    }

    public long getMaxRAM() {
        return ram;
    }

    public long getStorageSize() {
        return storage;
    }

    public int getCPUs() {
        return cpus;
    }

    public PacketHandshake(String version, String vendor, long ram, long storage, int cpus) {
        this.cpus = cpus;
        this.storage = storage;
        this.ram = ram;
        this.vendor = vendor;
        this.version = version;
    }
}
