package opus;

/* Copyright (c) 2011 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// repacketizer.c

/** @defgroup opus_repacketizer Repacketizer
 * @{
 *
 * The repacketizer can be used to merge multiple Opus packets into a single
 * packet or alternatively to split Opus packets that have previously been
 * merged. Splitting valid Opus packets is always guaranteed to succeed,
 * whereas merging valid packets only succeeds if all frames have the same
 * mode, bandwidth, and frame size, and when the total duration of the merged
 * packet is no more than 120 ms. The 120 ms limit comes from the
 * specification and limits decoder memory requirements at a point where
 * framing overhead becomes negligible.
 *
 * The repacketizer currently only operates on elementary Opus
 * streams. It will not manipualte multistream packets successfully, except in
 * the degenerate case where they consist of data from a single stream.
 *
 * The repacketizing process starts with creating a repacketizer state, either
 * by calling opus_repacketizer_create() or by allocating the memory yourself,
 * e.g.,
 * @code
 * OpusRepacketizer *rp;
 * rp = (OpusRepacketizer*)malloc(opus_repacketizer_get_size());
 * if (rp != NULL)
 *     opus_repacketizer_init(rp);
 * @endcode
 *
 * Then the application should submit packets with opus_repacketizer_cat(),
 * extract new packets with opus_repacketizer_out() or
 * opus_repacketizer_out_range(), and then reset the state for the next set of
 * input packets via opus_repacketizer_init().
 *
 * For example, to split a sequence of packets into individual frames:
 * @code
 * unsigned char *data;
 * int len;
 * while (get_next_packet(&data, &len))
 * {
 *   unsigned char out[1276];
 *   opus_int32 out_len;
 *   int nb_frames;
 *   int err;
 *   int i;
 *   err = opus_repacketizer_cat(rp, data, len);
 *   if (err != OPUS_OK)
 *   {
 *     release_packet(data);
 *     return err;
 *   }
 *   nb_frames = opus_repacketizer_get_nb_frames(rp);
 *   for (i = 0; i < nb_frames; i++)
 *   {
 *     out_len = opus_repacketizer_out_range(rp, i, i+1, out, sizeof(out));
 *     if (out_len < 0)
 *     {
 *        release_packet(data);
 *        return (int)out_len;
 *     }
 *     output_next_packet(out, out_len);
 *   }
 *   opus_repacketizer_init(rp);
 *   release_packet(data);
 * }
 * @endcode
 *
 * Alternatively, to combine a sequence of frames into packets that each
 * contain up to <code>TARGET_DURATION_MS</code> milliseconds of data:
 * @code
 * // The maximum number of packets with duration TARGET_DURATION_MS occurs
 * // when the frame size is 2.5 ms, for a total of (TARGET_DURATION_MS*2/5)
 * // packets.
 * unsigned char *data[(TARGET_DURATION_MS*2/5)+1];
 * opus_int32 len[(TARGET_DURATION_MS*2/5)+1];
 * int nb_packets;
 * unsigned char out[1277*(TARGET_DURATION_MS*2/2)];
 * opus_int32 out_len;
 * int prev_toc;
 * nb_packets = 0;
 * while (get_next_packet(data+nb_packets, len+nb_packets))
 * {
 *   int nb_frames;
 *   int err;
 *   nb_frames = opus_packet_get_nb_frames(data[nb_packets], len[nb_packets]);
 *   if (nb_frames < 1)
 *   {
 *     release_packets(data, nb_packets+1);
 *     return nb_frames;
 *   }
 *   nb_frames += opus_repacketizer_get_nb_frames(rp);
 *   // If adding the next packet would exceed our target, or it has an
 *   // incompatible TOC sequence, output the packets we already have before
 *   // submitting it.
 *   // N.B., The nb_packets > 0 check ensures we've submitted at least one
 *   // packet since the last call to opus_repacketizer_init(). Otherwise a
 *   // single packet longer than TARGET_DURATION_MS would cause us to try to
 *   // output an (invalid) empty packet. It also ensures that prev_toc has
 *   // been set to a valid value. Additionally, len[nb_packets] > 0 is
 *   // guaranteed by the call to opus_packet_get_nb_frames() above, so the
 *   // reference to data[nb_packets][0] should be valid.
 *   if (nb_packets > 0 && (
 *       ((prev_toc & 0xFC) != (data[nb_packets][0] & 0xFC)) ||
 *       opus_packet_get_samples_per_frame(data[nb_packets], 48000)*nb_frames >
 *       TARGET_DURATION_MS*48))
 *   {
 *     out_len = opus_repacketizer_out(rp, out, sizeof(out));
 *     if (out_len < 0)
 *     {
 *        release_packets(data, nb_packets+1);
 *        return (int)out_len;
 *     }
 *     output_next_packet(out, out_len);
 *     opus_repacketizer_init(rp);
 *     release_packets(data, nb_packets);
 *     data[0] = data[nb_packets];
 *     len[0] = len[nb_packets];
 *     nb_packets = 0;
 *   }
 *   err = opus_repacketizer_cat(rp, data[nb_packets], len[nb_packets]);
 *   if (err != OPUS_OK)
 *   {
 *     release_packets(data, nb_packets+1);
 *     return err;
 *   }
 *   prev_toc = data[nb_packets][0];
 *   nb_packets++;
 * }
 * // Output the final, partial packet.
 * if (nb_packets > 0)
 * {
 *   out_len = opus_repacketizer_out(rp, out, sizeof(out));
 *   release_packets(data, nb_packets);
 *   if (out_len < 0)
 *     return (int)out_len;
 *   output_next_packet(out, out_len);
 * }
 * @endcode
 *
 * An alternate way of merging packets is to simply call opus_repacketizer_cat()
 * unconditionally until it fails. At that point, the merged packet can be
 * obtained with opus_repacketizer_out() and the input packet for which
 * opus_repacketizer_cat() needs to be re-added to a newly reinitialized
 * repacketizer state.
 */
public final class JOpusRepacketizer {
	private byte toc;
	private int nb_frames;
	/**
	 * java changed: offsets. added byte[] data. using: data[ frames[ index] ]
	 */
	private final int frames[] = new int[48];// java changed: offsets. data[ frames[ i ] ]
	/**
	 * java added. using: data[ frames[ index] ]
	 */
	private byte[] data;// java added. pointer to byte buffer
	private final short len[] = new short[48];
	private int framesize;
	//
	/** Gets the size of an <code>OpusRepacketizer</code> structure.
	  * @returns The size in bytes.
	  */
	/*private static final int opus_repacketizer_get_size()
	{
		return sizeof( OpusRepacketizer );
	}*/

	/** (Re)initializes a previously allocated repacketizer state.
	  * The state must be at least the size returned by opus_repacketizer_get_size().
	  * This can be used for applications which use their own allocator instead of
	  * malloc().
	  * It must also be called to reset the queue of packets waiting to be
	  * repacketized, which is necessary if the maximum packet duration of 120 ms
	  * is reached or if you wish to submit packets with a different Opus
	  * configuration (coding mode, audio bandwidth, frame size, or channel count).
	  * Failure to do so will prevent a new packet from being added with
	  * opus_repacketizer_cat().
	  * @see opus_repacketizer_create
	  * @see opus_repacketizer_get_size
	  * @see opus_repacketizer_cat
	  * @param rp <tt>OpusRepacketizer*</tt>: The repacketizer state to
	  *                                       (re)initialize.
	  * @returns A pointer to the same repacketizer state that was passed in.
	  */
	public final JOpusRepacketizer opus_repacketizer_init()
	{
		this.nb_frames = 0;
		return this;
	}

	/** Allocates memory and initializes the new repacketizer with
	 * opus_repacketizer_init().
	  */
	public static final JOpusRepacketizer opus_repacketizer_create()
	{
		final JOpusRepacketizer rp = new JOpusRepacketizer();
		//if( rp == null) {
		//	return null;
		//}
		return rp.opus_repacketizer_init();
	}

	/** Frees an <code>OpusRepacketizer</code> allocated by
	  * opus_repacketizer_create().
	  * @param[in] rp <tt>OpusRepacketizer*</tt>: State to be freed.
	  */
	/*private static final void opus_repacketizer_destroy(final JOpusRepacketizer rp)
	{
		opus_free( rp );
	}*/

	private final int opus_repacketizer_cat_impl(
			final byte[] bdata, final int doffset,// java
			final int length, final boolean self_delimited)
	{
		/* Set of check ToC */
		if( length < 1 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}
		if( this.nb_frames == 0 )
		{
			this.toc = bdata[doffset];
			this.framesize = Jopus.opus_packet_get_samples_per_frame( bdata, doffset, 8000 );
		} else if( ((int)this.toc & 0xFC) != ((int)bdata[0] & 0xFC) )
		{
			/*fprintf(stderr, "toc mismatch: 0x%x vs 0x%x\n", rp.toc, data[0]);*/
			return Jopus_defines.OPUS_INVALID_PACKET;
		}
		final int curr_nb_frames = JOpusDecoder.opus_packet_get_nb_frames( bdata, doffset, length );
		if( curr_nb_frames < 1 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}

		/* Check the 120 ms maximum packet size */
		if( (curr_nb_frames + this.nb_frames) * this.framesize > 960 )
		{
			return Jopus_defines.OPUS_INVALID_PACKET;
		}

		// unsigned final char tmp_toc;// FIXME why need tmp_toc?
		// Jopus_packet_data_aux aux = new Jopus_packet_data_aux();// java to get tmp_toc
		this.data = bdata;// java
		final int ret = Jopus.opus_packet_parse_impl( bdata, doffset, length, self_delimited,
				// &tmp_toc,
				this.frames, this.nb_frames, this.len, this.nb_frames,
				// null, null );
				null );// java aux: toc, payload_offset, packet_offset not uses
		if( ret < 1 ) {
			return ret;
		}

		this.nb_frames += curr_nb_frames;
		return Jopus_defines.OPUS_OK;
	}

	/** Add a packet to the current repacketizer state.
	  * This packet must match the configuration of any packets already submitted
	  * for repacketization since the last call to opus_repacketizer_init().
	  * This means that it must have the same coding mode, audio bandwidth, frame
	  * size, and channel count.
	  * This can be checked in advance by examining the top 6 bits of the first
	  * byte of the packet, and ensuring they match the top 6 bits of the first
	  * byte of any previously submitted packet.
	  * The total duration of audio in the repacketizer state also must not exceed
	  * 120 ms, the maximum duration of a single packet, after adding this packet.
	  *
	  * The contents of the current repacketizer state can be extracted into new
	  * packets using opus_repacketizer_out() or opus_repacketizer_out_range().
	  *
	  * In order to add a packet with a different configuration or to add more
	  * audio beyond 120 ms, you must clear the repacketizer state by calling
	  * opus_repacketizer_init().
	  * If a packet is too large to add to the current repacketizer state, no part
	  * of it is added, even if it contains multiple frames, some of which might
	  * fit.
	  * If you wish to be able to add parts of such packets, you should first use
	  * another repacketizer to split the packet into pieces and add them
	  * individually.
	  * @see opus_repacketizer_out_range
	  * @see opus_repacketizer_out
	  * @see opus_repacketizer_init
	  * @param rp <tt>OpusRepacketizer*</tt>: The repacketizer state to which to
	  *                                       add the packet.
	  * @param data [in] <tt>const unsigned char*</tt>: The packet data.
	  *                                                The application must ensure
	  *                                                this pointer remains valid
	  *                                                until the next call to
	  *                                                opus_repacketizer_init() or
	  *                                                opus_repacketizer_destroy().
	  * @param length <tt>opus_int32</tt>: The number of bytes in the packet data.
	  * @returns An error code indicating whether or not the operation succeeded.
	  * @retval #OPUS_OK The packet's contents have been added to the repacketizer
	  *                  state.
	  * @retval #OPUS_INVALID_PACKET The packet did not have a valid TOC sequence,
	  *                              the packet's TOC sequence was not compatible
	  *                              with previously submitted packets (because
	  *                              the coding mode, audio bandwidth, frame size,
	  *                              or channel count did not match), or adding
	  *                              this packet would increase the total amount of
	  *                              audio stored in the repacketizer state to more
	  *                              than 120 ms.
	  */
	public final int opus_repacketizer_cat(final byte[] bdata, final int doffset, final int length)
	{
		return opus_repacketizer_cat_impl( bdata, doffset, length, false );
	}

	/** Return the total number of frames contained in packet data submitted to
	  * the repacketizer state so far via opus_repacketizer_cat() since the last
	  * call to opus_repacketizer_init() or opus_repacketizer_create().
	  * This defines the valid range of packets that can be extracted with
	  * opus_repacketizer_out_range() or opus_repacketizer_out().
	  * @param rp <tt>OpusRepacketizer*</tt>: The repacketizer state containing the
	  *                                       frames.
	  * @returns The total number of frames contained in the packet data submitted
	  *          to the repacketizer state.
	  */
	public final int opus_repacketizer_get_nb_frames()
	{
		return this.nb_frames;
	}

	private static final int encode_size(final int size, final byte[] data, int doffset)// java
	{
		if( size < 252 )
		{
			data[doffset] = (byte)size;
			return 1;
		}
		final int v = 252 + (size & 0x3);// java
		data[doffset++] = (byte)v;
		data[doffset] = (byte)((size - v) >> 2);
		return 2;
	}

	final int opus_repacketizer_out_range_impl(int begin, final int end,
					final byte[] bdata, final int doffset,// java
					int maxlen, final boolean self_delimited, final boolean pad)
	{
		if( begin < 0 || begin >= end || end > this.nb_frames )
		{
			/*fprintf(stderr, "%d %d %d\n", begin, end, rp.nb_frames);*/
			return Jopus_defines.OPUS_BAD_ARG;
		}
		int count = end - begin;

		final short[] rp_len = this.len;// java
		int len_ptr = begin;// rp_len[len]
		int tot_size = self_delimited ? 1 + (rp_len[len_ptr + count - 1] >= 252 ? 1 : 0) : 0;

		int ptr = doffset;// data[ptr]
		if( count == 1 )
		{
			/* Code 0 */
			tot_size += (int)rp_len[len_ptr] + 1;
			if( tot_size > maxlen ) {
				return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
			}
			bdata[ptr++] = (byte)(this.toc & 0xFC);
		} else if( count == 2 )
		{
			if( rp_len[len_ptr + 1] == rp_len[len_ptr] )
			{
				/* Code 1 */
				tot_size += ((int)rp_len[len_ptr] << 1) + 1;
				if( tot_size > maxlen ) {
					return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
				}
				bdata[ptr++] = (byte)((this.toc & 0xFC) | 0x1);
			} else {
				/* Code 2 */
				tot_size += (int)rp_len[len_ptr] + (int)rp_len[len_ptr + 1] + 2 + ((int)rp_len[len_ptr] >= 252 ? 1 : 0);
				if( tot_size > maxlen ) {
					return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
				}
				bdata[ptr++] = (byte)((this.toc & 0xFC) | 0x2);
				ptr += encode_size( (int)rp_len[len_ptr], bdata, ptr );
			}
		}
		if( count > 2 || (pad && tot_size < maxlen) )
		{
			/* Code 3 */
			int pad_amount = 0;

			/* Restart the process for the padding case */
			ptr = doffset;// data[ptr]
			if( self_delimited ) {
				tot_size = 1 + (rp_len[len_ptr + count - 1] >= 252 ? 1 : 0);
			} else {
				tot_size = 0;
			}
			boolean vbr = false;
			for( int i = 1; i < count; i++ )
			{
				if( rp_len[len_ptr + i] != rp_len[len_ptr] )
				{
					vbr = true;
					break;
				}
			}
			if( vbr )
			{
				tot_size += 2;
				final int ie = len_ptr + count - 1;// java
				for( int i = len_ptr; i < ie; i++ ) {
					final int v = (int)rp_len[i];// java
					tot_size += 1 + (v >= 252 ? 1 : 0) + v;
				}
				tot_size += (int)rp_len[ie];

				if( tot_size > maxlen ) {
					return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
				}
				bdata[ptr++] = (byte)((this.toc & 0xFC) | 0x3);
				bdata[ptr++] = (byte)(count | 0x80);
			} else {
				tot_size += count * (int)rp_len[len_ptr] + 2;
				if( tot_size > maxlen ) {
					return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
				}
				bdata[ptr++] = (byte)((this.toc & 0xFC) | 0x3);
				bdata[ptr++] = (byte)count;
			}
			pad_amount = pad ? (maxlen - tot_size) : 0;
			if( pad_amount != 0 )
			{
				bdata[doffset + 1] |= 0x40;
				final int nb_255s = (pad_amount - 1) / 255;
				for( int i = 0; i < nb_255s; i++ ) {
					bdata[ptr++] = (byte)255;
				}
				bdata[ptr++] = (byte)(pad_amount - 255 * nb_255s - 1);
				tot_size += pad_amount;
			}
			if( vbr )
			{
				for( int i = len_ptr, ie = len_ptr + count - 1; i < ie; i++ ) {
					ptr += encode_size( (int)rp_len[i], bdata, ptr );
				}
			}
		}
		if( self_delimited ) {
			final int sdlen = encode_size( (int)rp_len[len_ptr + count - 1], bdata, ptr );
			ptr += sdlen;
		}
		/* Copy the actual data */
		final int[] rp_frames = this.frames;
		// int frames = begin;// rp_frames[frames]
		final byte[] buf = this.data;// java rp.data[ rp.frames[i] ]
		for( count += len_ptr; len_ptr < count; len_ptr++ )
		{
			/* Using OPUS_MOVE() instead of OPUS_COPY() in case we're doing in-place
			   padding from opus_packet_pad or opus_packet_unpad(). */
			/* assert disabled because it's not valid in C. */
			/* celt_assert(frames[i] + len[i] <= data || ptr <= frames[i]); */
			final int length = (int)rp_len[len_ptr];// java
			System.arraycopy( buf, rp_frames[ begin++ ], bdata, ptr, length );
			ptr += length;
		}
		if( pad )
		{
			/* Fill padding with zeros. */
			maxlen += doffset;
			while( ptr < maxlen ) {
				bdata[ptr++] = 0;
			}
		}
		return tot_size;
	}

	/** Construct a new packet from data previously submitted to the repacketizer
	  * state via opus_repacketizer_cat().
	  * @param rp <tt>OpusRepacketizer*</tt>: The repacketizer state from which to
	  *                                       construct the new packet.
	  * @param begin <tt>int</tt>: The index of the first frame in the current
	  *                            repacketizer state to include in the output.
	  * @param end <tt>int</tt>: One past the index of the last frame in the
	  *                          current repacketizer state to include in the
	  *                          output.
	  * @param[out] data <tt>const unsigned char*</tt>: The buffer in which to
	  *                                                 store the output packet.
	  * @param maxlen <tt>opus_int32</tt>: The maximum number of bytes to store in
	  *                                    the output buffer. In order to guarantee
	  *                                    success, this should be at least
	  *                                    <code>1276</code> for a single frame,
	  *                                    or for multiple frames,
	  *                                    <code>1277*(end-begin)</code>.
	  *                                    However, <code>1*(end-begin)</code> plus
	  *                                    the size of all packet data submitted to
	  *                                    the repacketizer since the last call to
	  *                                    opus_repacketizer_init() or
	  *                                    opus_repacketizer_create() is also
	  *                                    sufficient, and possibly much smaller.
	  * @returns The total size of the output packet on success, or an error code
	  *          on failure.
	  * @retval #OPUS_BAD_ARG <code>[begin,end)</code> was an invalid range of
	  *                       frames (begin < 0, begin >= end, or end >
	  *                       opus_repacketizer_get_nb_frames()).
	  * @retval #OPUS_BUFFER_TOO_SMALL \a maxlen was insufficient to contain the
	  *                                complete output packet.
	  */
	public final int opus_repacketizer_out_range(final int begin, final int end, final byte[] bdata, final int maxlen)
	{
		return opus_repacketizer_out_range_impl( begin, end, bdata, 0, maxlen, false, false );
	}

	/** Construct a new packet from data previously submitted to the repacketizer
	  * state via opus_repacketizer_cat().
	  * This is a convenience routine that returns all the data submitted so far
	  * in a single packet.
	  * It is equivalent to calling
	  * @code
	  * opus_repacketizer_out_range(rp, 0, opus_repacketizer_get_nb_frames(rp),
	  *                             data, maxlen)
	  * @endcode
	  * @param rp <tt>OpusRepacketizer*</tt>: The repacketizer state from which to
	  *                                       construct the new packet.
	  * @param[out] data <tt>const unsigned char*</tt>: The buffer in which to
	  *                                                 store the output packet.
	  * @param maxlen <tt>opus_int32</tt>: The maximum number of bytes to store in
	  *                                    the output buffer. In order to guarantee
	  *                                    success, this should be at least
	  *                                    <code>1277*opus_repacketizer_get_nb_frames(rp)</code>.
	  *                                    However,
	  *                                    <code>1*opus_repacketizer_get_nb_frames(rp)</code>
	  *                                    plus the size of all packet data
	  *                                    submitted to the repacketizer since the
	  *                                    last call to opus_repacketizer_init() or
	  *                                    opus_repacketizer_create() is also
	  *                                    sufficient, and possibly much smaller.
	  * @returns The total size of the output packet on success, or an error code
	  *          on failure.
	  * @retval #OPUS_BUFFER_TOO_SMALL \a maxlen was insufficient to contain the
	  *                                complete output packet.
	  */
	public final int opus_repacketizer_out(final byte[] bdata, final int maxlen)
	{
		return opus_repacketizer_out_range_impl( 0, this.nb_frames, bdata, 0, maxlen, false, false );
	}

	/** Pads a given Opus packet to a larger size (possibly changing the TOC sequence).
	  * @param data [in,out] <tt>const unsigned char*</tt>: The buffer containing the
	  *                                                   packet to pad.
	  * @param len <tt>opus_int32</tt>: The size of the packet.
	  *                                 This must be at least 1.
	  * @param new_len <tt>opus_int32</tt>: The desired size of the packet after padding.
	  *                                 This must be at least as large as len.
	  * @return an error code
	  * @retval #OPUS_OK \a on success.
	  * @retval #OPUS_BAD_ARG \a len was less than 1 or new_len was less than len.
	  * @retval #OPUS_INVALID_PACKET \a data did not contain a valid Opus packet.
	  */
	public static final int opus_packet_pad(final byte[] data, final int doffset, final int len, final int new_len)
	{// java doffset is added
		if( len < 1 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( len == new_len ) {
			return Jopus_defines.OPUS_OK;
		} else if( len > new_len ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		final JOpusRepacketizer rp = new JOpusRepacketizer().opus_repacketizer_init();
		/* Moving payload to the end of the packet so we can do in-place padding */
		final int doffset2 = doffset + new_len - len;// java
		System.arraycopy( data, doffset, data, doffset2, len );
		int ret = rp.opus_repacketizer_cat( data, doffset2, len );
		if( ret != Jopus_defines.OPUS_OK ) {
			return ret;
		}

		ret = rp.opus_repacketizer_out_range_impl( 0, rp.nb_frames, data, doffset, new_len, false, true );
		if( ret > 0 ) {
			return Jopus_defines.OPUS_OK;
		}// else {
			return ret;
		//}
	}

	/** Remove all padding from a given Opus packet and rewrite the TOC sequence to
	  * minimize space usage.
	  * @param[in,out] data <tt>const unsigned char*</tt>: The buffer containing the
	  *                                                   packet to strip.
	  * @param len <tt>opus_int32</tt>: The size of the packet.
	  *                                 This must be at least 1.
	  * @returns The new size of the output packet on success, or an error code
	  *          on failure.
	  * @retval #OPUS_BAD_ARG \a len was less than 1.
	  * @retval #OPUS_INVALID_PACKET \a data did not contain a valid Opus packet.
	  */
	public static final int opus_packet_unpad(final byte[] data, final int len)
	{

		if( len < 1 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		final JOpusRepacketizer rp = new JOpusRepacketizer().opus_repacketizer_init();
		int ret = rp.opus_repacketizer_cat( data, 0, len );
		if( ret < 0 ) {
			return ret;
		}
		ret = rp.opus_repacketizer_out_range_impl( 0, rp.nb_frames, data, 0, len, false, false );
		// celt_assert( ret > 0 && ret <= len );
		return ret;
	}

	/** Pads a given Opus multi-stream packet to a larger size (possibly changing the TOC sequence).
	  * @param[in,out] data <tt>const unsigned char*</tt>: The buffer containing the
	  *                                                   packet to pad.
	  * @param len <tt>opus_int32</tt>: The size of the packet.
	  *                                 This must be at least 1.
	  * @param new_len <tt>opus_int32</tt>: The desired size of the packet after padding.
	  *                                 This must be at least 1.
	  * @param nb_streams <tt>opus_int32</tt>: The number of streams (not channels) in the packet.
	  *                                 This must be at least as large as len.
	  * @returns an error code
	  * @retval #OPUS_OK \a on success.
	  * @retval #OPUS_BAD_ARG \a len was less than 1.
	  * @retval #OPUS_INVALID_PACKET \a data did not contain a valid Opus packet.
	  */
	public static final int opus_multistream_packet_pad(final byte[] data, int len, final int new_len, int nb_streams)
	{
		if( len < 1 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( len == new_len ) {
			return Jopus_defines.OPUS_OK;
		} else if( len > new_len ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int doffset = 0;// java
		final int amount = new_len - len;
		final short size[] = new short[48];
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();// java. to replace out_toc, payload_offset, packet_offset
		// unsigned final char toc;// FIXME toc never uses
		// final int packet_offset;// java replaced by aux
		/* Seek to last stream */
		nb_streams--;// java
		for( int s = 0; s < nb_streams; s++ )
		{
			if( len <= 0 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			final int count = Jopus.opus_packet_parse_impl( data, doffset, len, true,
					// &toc,
					null, 0, size, 0,
					// null, &packet_offset );
					aux );
			if( count < 0 ) {
				return count;
			}
			doffset += aux.mPacketOffset;
			len -= aux.mPacketOffset;
		}
		return opus_packet_pad( data, doffset, len, len + amount );
	}

	/** Remove all padding from a given Opus multi-stream packet and rewrite the TOC sequence to
	  * minimize space usage.
	  * @param[in,out] data <tt>const unsigned char*</tt>: The buffer containing the
	  *                                                   packet to strip.
	  * @param len <tt>opus_int32</tt>: The size of the packet.
	  *                                 This must be at least 1.
	  * @param nb_streams <tt>opus_int32</tt>: The number of streams (not channels) in the packet.
	  *                                 This must be at least 1.
	  * @returns The new size of the output packet on success, or an error code
	  *          on failure.
	  * @retval #OPUS_BAD_ARG \a len was less than 1 or new_len was less than len.
	  * @retval #OPUS_INVALID_PACKET \a data did not contain a valid Opus packet.
	  */
	public static final int opus_multistream_packet_unpad(final byte[] data, int len, final int nb_streams)
	{
		if( len < 1 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final JOpusRepacketizer rp = new JOpusRepacketizer();
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();// java. to replace out_toc, payload_offset, packet_offset
		// unsigned final char toc;// FIXME toc never uses
		// final int packet_offset;// java replaced by aux
		final short size[] = new short[48];
		int doffset = 0;// java data[doffset]
		int dst = doffset;// java data[dst]
		int dst_len = 0;
		/* Unpad all frames */
		final int n1 = nb_streams - 1;// java
		for( int s = 0; s < nb_streams; s++ )
		{
			if( len <= 0 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			rp.opus_repacketizer_init();
			final boolean self_delimited = (s != n1);
			int ret = Jopus.opus_packet_parse_impl( data, doffset, len, self_delimited,
					// &toc,
					null, 0, size, 0,
					//null, &packet_offset );
					aux );
			if( ret < 0 ) {
				return ret;
			}
			ret = rp.opus_repacketizer_cat_impl( data, doffset, aux.mPacketOffset, self_delimited );
			if( ret < 0 ) {
				return ret;
			}
			ret = rp.opus_repacketizer_out_range_impl( 0, rp.nb_frames, data, dst, len, self_delimited, false );
			if( ret < 0) {
				return ret;
			} else {
				dst_len += ret;
			}
			dst += ret;
			doffset += aux.mPacketOffset;
			len -= aux.mPacketOffset;
		}
		return dst_len;
	}

}