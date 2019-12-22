package libenc;


/** Callback functions for accessing the stream. */
/**
 * Callback functions
 * These are the callbacks that can be implemented for an encoder.
 */
public interface IOpusEncCallbacks {
	/** Callback for writing to the stream. */
	//ope_write_func write;
	/** Callback for closing the stream. */
	//ope_close_func close;

	/** Called for writing a page.
	 @param user_data user-defined data passed to the callback
	 @param ptr       buffer to be written
	 @param len       number of bytes to be written
	 @return          error code
	 @retval 0        success
	 @retval 1        failure
	 */
	// abstract int ope_write_func(Object user_data, final byte[] ptr, int len);
	public boolean write(/*Object user_data, */final Joggp_page_aux data);

	/** Called for closing a stream.
	 @param user_data user-defined data passed to the callback
	 @return          error code
	 @retval 0        success
	 \retval 1        failure
	 */
	// boolean ope_close_func(/*Object user_data*/);
	public boolean close(/*Object user_data*/);

	/** Called on every packet encoded (including header).
	 @param user_data   user-defined data passed to the callback
	 @param packet_ptr  packet data
	 @param packet_len  number of bytes in the packet
	 @param flags       optional flags (none defined for now so zero)
	 */
	// void ope_packet_func(/*Object user_data, */final byte[] packet_ptr, int packet_len, int flags);
	// public void packet(/*Object user_data, */final byte[] packet_ptr, int packet_len, int flags);
}
