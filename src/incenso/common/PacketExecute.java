package incenso.common;

/**
 * Execute packet - schedule execution of an already linked class to the server.
 * Holds the class name and the class object.
 *
 * TODO: Because class name can be extracted from the class object, we can probably drop this field.
 *
 * @author Kamila Szewczyk
 */
public class PacketExecute implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_EXECUTE;
    }

    // Multiple pointless getters and setters and a field constructor.
    private String name;
    private Class<? extends CodeChunk> data;

    public String getName() {
        return name;
    }

    public Class<? extends CodeChunk> getClassData() {
        return data;
    }

    public PacketExecute(String name, Class<? extends CodeChunk> data) {
        this.name = name;
        this.data = data;
    }
}
