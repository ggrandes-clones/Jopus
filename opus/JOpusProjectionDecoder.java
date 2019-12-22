package opus;

/* Copyright (c) 2017 Google Inc.
   Written by Andrew Allen */
/*
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/** Opus projection decoder state.
 * This contains the complete state of a projection Opus decoder.
 * It is position independent and can be freely copied.
 * @see opus_projection_decoder_create
 * @see opus_projection_decoder_init
 */
public final class JOpusProjectionDecoder {
	// private int demixing_matrix_size_in_bytes;
	/* Encoder states go here */
	private JMappingMatrix mDemixingMatrix;
	private JOpusMSDecoder mMultistreamDecoder;

// #if !defined(DISABLE_FLOAT_API)
	// private static void opus_projection_copy_channel_out_float
	private static final class Jopus_projection_copy_channel_out_float extends Iopus_copy_channel_out {
		@Override
		final void copy_channel_out
		(
			final Object dst, final int doffset,
			final int dst_stride,
			final int dst_channel,
			final float[] src, final int srcoffset,
			final int src_stride,
			final int frame_size,
			final Object user_data)
		{
			final float[] float_dst = (float[])dst;
			final JMappingMatrix matrix = (JMappingMatrix)user_data;

			if( dst_channel == 0 ) {
				for( int i = doffset + frame_size * dst_stride; --i >= 0; ) {
					float_dst[ i ] = 0;
				}
			}

			if( src != null ) {
				matrix.mapping_matrix_multiply_channel_out_float( src, srcoffset, dst_channel,
						src_stride, float_dst, doffset, dst_stride, frame_size );
			}
		}
	}
// #endif

	// private static void opus_projection_copy_channel_out_short
	private static final class Jopus_projection_copy_channel_out_short extends Iopus_copy_channel_out {
		@Override
		final void copy_channel_out
		(
				final Object dst, final int doffset,
				final int dst_stride,
				final int dst_channel,
				final float[] src, final int srcoffset,
				final int src_stride,
				final int frame_size,
				final Object user_data)
		{
			final short[] short_dst = (short[])dst;
			final JMappingMatrix matrix = (JMappingMatrix)user_data;
			if( dst_channel == 0 ) {
				for( int i = doffset + frame_size * dst_stride; --i >= 0; ) {
					short_dst[ i ] = 0;
				}
			}

			if( src != null ) {
				matrix.mapping_matrix_multiply_channel_out_short( src, srcoffset, dst_channel,
						src_stride, short_dst, doffset, dst_stride, frame_size );
			}
		}
	}

	private static final Jopus_projection_copy_channel_out_short opus_projection_copy_channel_out_short = new Jopus_projection_copy_channel_out_short();

	private static final Jopus_projection_copy_channel_out_float opus_projection_copy_channel_out_float = new Jopus_projection_copy_channel_out_float();
/*
	private final JMappingMatrix get_dec_demixing_matrix()
	{
		return mDemixingMatrix;
	}

	private final JOpusMSDecoder get_multistream_decoder()
	{
		return mMultistreamDecoder;
	}
*/
	/** Gets the size of an <code>OpusProjectionDecoder</code> structure.
	  * @param channels <tt>int</tt>: The total number of output channels.
	  *                               This must be no more than 255.
	  * @param streams <tt>int</tt>: The total number of streams coded in the
	  *                              input.
	  *                              This must be no more than 255.
	  * @param coupled_streams <tt>int</tt>: Number streams to decode as coupled
	  *                                      (2 channel) streams.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      coded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than 255.
	  * @returns The size in bytes on success, or a negative error code
	  *          (see @ref opus_errorcodes) on error.
	  */
	/* java: using members, private static int opus_projection_decoder_get_size(final int channels, final int streams, final int coupled_streams)
	{
		final int matrix_size = mapping_matrix_get_size( streams + coupled_streams, channels );
		if( 0 == matrix_size ) {
			return 0;
		}

		final int decoder_size = opus_multistream_decoder_get_size( streams, coupled_streams );
		if( 0 == decoder_size ) {
			return 0;
		}

		return align(sizeof(OpusProjectionDecoder)) + matrix_size + decoder_size;
	} */

	/** Initialize a previously allocated projection decoder state object.
	  * The memory pointed to by \a st must be at least the size returned by
	  * opus_projection_decoder_get_size().
	  * This is intended for applications which use their own allocator instead of
	  * malloc.
	  * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	  * @see opus_projection_decoder_create
	  * @see opus_projection_deocder_get_size
	  * @param st <tt>OpusProjectionDecoder*</tt>: Projection encoder state to initialize.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate to decode at (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels to output.
	  *                               This must be at most 255.
	  *                               It may be different from the number of coded
	  *                               channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param streams <tt>int</tt>: The total number of streams coded in the
	  *                              input.
	  *                              This must be no more than 255.
	  * @param coupled_streams <tt>int</tt>: Number of streams to decode as coupled
	  *                                      (2 channel) streams.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      coded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than 255.
	  * @param demixing_matrix [in] <tt>const unsigned char[demixing_matrix_size]</tt>: Demixing matrix
	  *                         that mapping from coded channels to output channels,
	  *                         as described in @ref opus_projection and
	  *                         @ref opus_projection_ctls.
	  * @param demixing_matrix_size <tt>opus_int32</tt>: The size in bytes of the
	  *                                                  demixing matrix, as
	  *                                                  described in @ref
	  *                                                  opus_projection_ctls.
	  * @returns #OPUS_OK on success, or an error code (see @ref opus_errorcodes)
	  *          on failure.
	  */
	private final int opus_projection_decoder_init(final int Fs,
			final int channels, final int streams, final int coupled_streams,
			final char[] demixing_matrix)// java: using demixing_matrix.length , final int demixing_matrix_size)
	{
		// ALLOC_STACK;

		/* Verify supplied matrix size. */
		final int nb_input_streams = streams + coupled_streams;
		final int expected_matrix_size = nb_input_streams * channels * (Short.SIZE / 8);
		if( expected_matrix_size != demixing_matrix.length )// demixing_matrix_size )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		/* Convert demixing matrix input into internal format. */
		final int size = nb_input_streams * channels;// java
		final short buf[] = new short[ size ];
		for( int i = 0, di = 0; i < size; i++ )
		{
			// int s = (demixing_matrix[2 * i + 1] << 8) | demixing_matrix[2 * i];
			int s = demixing_matrix[ di++ ];
			s |= (demixing_matrix[ di++ ] << 8);
			s = ((s & 0xFFFF) ^ 0x8000) - 0x8000;
			buf[i] = (short)s;
		}

		/* Assign demixing matrix. */
		/* java don't need st.demixing_matrix_size_in_bytes = mapping_matrix_get_size( channels, nb_input_streams );
		if( 0 == st.demixing_matrix_size_in_bytes )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		} */

		this.mDemixingMatrix = new JMappingMatrix();// java
		this.mDemixingMatrix.mapping_matrix_init( channels, nb_input_streams, 0, buf );// demixing_matrix_size );

		final char mapping[] = new char[255];
		/* Set trivial mapping so each input channel pairs with a matrix column. */
		for( int i = 0; i < channels; i++ ) {
			mapping[i] = (char)i;
		}

		this.mMultistreamDecoder = new JOpusMSDecoder();// java
		final int ret = this.mMultistreamDecoder.opus_multistream_decoder_init( Fs, channels, streams, coupled_streams, mapping );
		// RESTORE_STACK;
		return ret;
	}

	/** Allocates and initializes a projection decoder state.
	  * Call opus_projection_decoder_destroy() to release
	  * this object when finished.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate to decode at (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels to output.
	  *                               This must be at most 255.
	  *                               It may be different from the number of coded
	  *                               channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param streams <tt>int</tt>: The total number of streams coded in the
	  *                              input.
	  *                              This must be no more than 255.
	  * @param coupled_streams <tt>int</tt>: Number of streams to decode as coupled
	  *                                      (2 channel) streams.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      coded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than 255.
	  * @param demixing_matrix [in] <tt>const unsigned char[demixing_matrix_size]</tt>: Demixing matrix
	  *                         that mapping from coded channels to output channels,
	  *                         as described in @ref opus_projection and
	  *                         @ref opus_projection_ctls.
	  * @param demixing_matrix_size <tt>opus_int32</tt>: The size in bytes of the
	  *                                                  demixing matrix, as
	  *                                                  described in @ref
	  *                                                  opus_projection_ctls.
	  * @param error [out] <tt>int *</tt>: Returns #OPUS_OK on success, or an error
	  *                                   code (see @ref opus_errorcodes) on
	  *                                   failure.
	  */
	public static final JOpusProjectionDecoder opus_projection_decoder_create(
			final int Fs, final int channels, final int streams, final int coupled_streams,
			// final char[] demixing_matrix, final int demixing_matrix_size, final int[] error)
			final char[] demixing_matrix, final int[] error)
	{
		/* Allocate space for the projection decoder. */
		/* java don't need final int size = opus_projection_decoder_get_size( channels, streams, coupled_streams );
		if( 0 == size ) {
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		JOpusProjectionDecoder st = new JOpusProjectionDecoder();// (JOpusProjectionDecoder)opus_alloc(size);
		/* if( null == st )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		} */

		/* Initialize projection decoder with provided settings. */
		final int ret = st.opus_projection_decoder_init( Fs, channels, streams, coupled_streams, demixing_matrix );// , demixing_matrix_size );
		if( ret != Jopus_defines.OPUS_OK )
		{
			// opus_free( st );
			st = null;
		}
		if( null != error ) {
			error[0] = ret;
		}
		return st;
	}

/* #ifdef FIXED_POINT
	private static int opus_projection_decode(OpusProjectionDecoder *st, const unsigned char *data,
			opus_int32 len, opus_int16 *pcm, int frame_size,
			int decode_fec)
	{
		return opus_multistream_decode_native(get_multistream_decoder(st), data, len,
				pcm, opus_projection_copy_channel_out_short, frame_size, decode_fec, 0,
				get_dec_demixing_matrix(st));
	}
#else */

	/** Decode a projection Opus packet.
	  * @param st <tt>OpusProjectionDecoder*</tt>: Projection decoder state.
	  * @param data [in] <tt>const unsigned char*</tt>: Input payload.
	  *                                                Use a <code>NULL</code>
	  *                                                pointer to indicate packet
	  *                                                loss.
	  * @param len <tt>opus_int32</tt>: Number of bytes in payload.
	  * @param pcm [out] <tt>opus_int16*</tt>: Output signal, with interleaved
	  *                                       samples.
	  *                                       This must contain room for
	  *                                       <code>frame_size*channels</code>
	  *                                       samples.
	  * @param frame_size <tt>int</tt>: The number of samples per channel of
	  *                                 available space in \a pcm.
	  *                                 If this is less than the maximum packet duration
	  *                                 (120 ms; 5760 for 48kHz), this function will not be capable
	  *                                 of decoding some packets. In the case of PLC (data==NULL)
	  *                                 or FEC (decode_fec=1), then frame_size needs to be exactly
	  *                                 the duration of audio that is missing, otherwise the
	  *                                 decoder will not be in the optimal state to decode the
	  *                                 next incoming packet. For the PLC and FEC cases, frame_size
	  *                                 <b>must</b> be a multiple of 2.5 ms.
	  * @param decode_fec <tt>int</tt>: Flag (0 or 1) to request that any in-band
	  *                                 forward error correction data be decoded.
	  *                                 If no such data is available, the frame is
	  *                                 decoded as if it were lost.
	  * @returns Number of samples decoded on success or a negative error code
	  *          (see @ref opus_errorcodes) on failure.
	  */
	public final int opus_projection_decode(final byte[] data,
				final int len, final short[] pcm, final int frame_size, final boolean decode_fec)
	{
		//return opus_multistream_decode_native( mMultistreamDecoder, data, len,
		//		pcm, opus_projection_copy_channel_out_short, frame_size, decode_fec, 1,
		//		get_dec_demixing_matrix(st) );

		return mMultistreamDecoder.opus_multistream_decode_native( data, 0, len,
				pcm, 0, opus_projection_copy_channel_out_short, frame_size, decode_fec, true, mDemixingMatrix );
	}
// #endif

// #ifndef DISABLE_FLOAT_API
	/** Decode a projection Opus packet with floating point output.
	  * @param st <tt>OpusProjectionDecoder*</tt>: Projection decoder state.
	  * @param data [in] <tt>const unsigned char*</tt>: Input payload.
	  *                                                Use a <code>NULL</code>
	  *                                                pointer to indicate packet
	  *                                                loss.
	  * @param len <tt>opus_int32</tt>: Number of bytes in payload.
	  * @param pcm [out] <tt>opus_int16*</tt>: Output signal, with interleaved
	  *                                       samples.
	  *                                       This must contain room for
	  *                                       <code>frame_size*channels</code>
	  *                                       samples.
	  * @param frame_size <tt>int</tt>: The number of samples per channel of
	  *                                 available space in \a pcm.
	  *                                 If this is less than the maximum packet duration
	  *                                 (120 ms; 5760 for 48kHz), this function will not be capable
	  *                                 of decoding some packets. In the case of PLC (data==NULL)
	  *                                 or FEC (decode_fec=1), then frame_size needs to be exactly
	  *                                 the duration of audio that is missing, otherwise the
	  *                                 decoder will not be in the optimal state to decode the
	  *                                 next incoming packet. For the PLC and FEC cases, frame_size
	  *                                 <b>must</b> be a multiple of 2.5 ms.
	  * @param decode_fec <tt>int</tt>: Flag (0 or 1) to request that any in-band
	  *                                 forward error correction data be decoded.
	  *                                 If no such data is available, the frame is
	  *                                 decoded as if it were lost.
	  * @returns Number of samples decoded on success or a negative error code
	  *          (see @ref opus_errorcodes) on failure.
	  */
	public final int opus_projection_decode_float(final byte[] data,
				final int len, final float[] pcm, final int frame_size, final boolean decode_fec)
	{
		return mMultistreamDecoder.opus_multistream_decode_native( data, 0, len,
				pcm, 0, opus_projection_copy_channel_out_float, frame_size, decode_fec, false,
				mDemixingMatrix );// get_dec_demixing_matrix(st));
	}
// #endif

	/** Perform a CTL function on a projection Opus decoder.
	  *
	  * Generally the request and subsequent arguments are generated by a
	  * convenience macro.
	  * @param st <tt>OpusProjectionDecoder*</tt>: Projection decoder state.
	  * @param request This and all remaining parameters should be replaced by one
	  *                of the convenience macros in @ref opus_genericctls,
	  *                @ref opus_decoderctls, @ref opus_multistream_ctls, or
	  *                @ref opus_projection_ctls.
	  * @see opus_genericctls
	  * @see opus_decoderctls
	  * @see opus_multistream_ctls
	  * @see opus_projection_ctls
	  */
	final int opus_projection_decoder_ctl(final int request, final Object[] arg)
	{
		// va_list ap;
		int ret = Jopus_defines.OPUS_OK;

		// va_start(ap, request);
		ret = mMultistreamDecoder.opus_multistream_decoder_ctl(// opus_multistream_decoder_ctl_va_list( // get_multistream_decoder(st),
				request, arg );
		// va_end(ap);
		return ret;
	}

	/** Java: use st = null instead */
	/** Frees an <code>OpusProjectionDecoder</code> allocated by
	  * opus_projection_decoder_create().
	  * @param st <tt>OpusProjectionDecoder</tt>: Projection decoder state to be freed.
	  */
	/* private static void opus_projection_decoder_destroy(JOpusProjectionDecoder st)
	{
		opus_free(st);
	} */
}
