package libogg;

/**
 * ogg_packet is used to encapsulate the data and metadata belonging to a single raw Ogg/Vorbis packet
 * <p>Changes from C to Java:<p>
 * <code>byte data = packet_base[packet]</code><br>
 * <code>int data = ((int)packet_base[packet]) & 0xff</code><br><br>
 * <code>void ogg_packet_clear(Jogg_packet op)</code>
 * -> <code>Jogg_packet op = null</code>
 */
public final class Jogg_packet {
	public byte[] packet_base;
	public int packet;
	public int bytes;
	public boolean b_o_s;
	public boolean e_o_s;

	public long granulepos;
	/** sequence number for decode; the framing
	knows where there's a hole in the data,
	but we need coupling so that the codec
	(which is in a separate abstraction
	layer) also knows about the gap */
	public long packetno;
	//
	public Jogg_packet() {
	}
	public Jogg_packet(final Jogg_packet p) {
		copyFrom( p );
	}
	public final void copyFrom(final Jogg_packet p) {
		packet_base = p.packet_base;
		packet = p.packet;
		bytes = p.bytes;
		b_o_s = p.b_o_s;
		e_o_s = p.e_o_s;
		granulepos = p.granulepos;
		packetno = p.packetno;
	}
	public final void clear() {
		packet_base = null;
		packet = 0;
		bytes = 0;
		b_o_s = false;
		e_o_s = false;
		granulepos = 0;
		packetno = 0;
	}
}
