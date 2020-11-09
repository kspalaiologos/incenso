package incenso.common;

/**
 * The injection packet, which instructs the server to link/resolve a given class to a given module.
 * TODO: Because the class object already contains the name, we can remove the name field to reduce the net overhead.
 *
 * @author Kamila Szewczyk
 */
public class PacketInject implements Packet {
    @Override
    public PacketType getType() {
        return PacketType.PACKET_INJECT;
    }

    private String name, module;
    private Class<?> data;

    public String getName() {
        return name;
    }

    public Class<?> getClassData() {
        return data;
    }

    public String getModule() {
        return module;
    }

    public PacketInject(String name, String module, Class<?> data) {
        this.name = name;
        this.data = data;
        this.module = module;
    }
}
