package incenso.common;

/**
 * All the packet types recognized by the client.
 */
public enum PacketType {
    PACKET_HANDSHAKE, PACKET_INJECT, PACKET_EXECUTE, PACKET_KEEPALIVE, PACKET_GOODBYE,
    PACKET_GC, PACKET_UNLINK
}
