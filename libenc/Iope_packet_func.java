package libenc;

/** ope_packet_func */
public interface Iope_packet_func {
	/** Called on every packet encoded (including header).
	 * @param user_data   user-defined data passed to the callback
	 * @param packet_ptr  packet data
	 * @param poffset     data offset, java added
	 * @param packet_len  number of bytes in the packet
	 * @param flags       optional flags (none defined for now so zero)
	 */
	public void ope_packet_func(/*Object user_data,*/ final byte[] packet_ptr, int poffset, int packet_len, int flags);
}
