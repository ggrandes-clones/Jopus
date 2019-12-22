package opus;

import celt.Jfloat_cast;

/* Copyright (c) 2011 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// opus_multistream_decoder.c

/** @defgroup opus_multistream Opus Multistream API
 * @{
 *
 * The multistream API allows individual Opus streams to be combined into a
 * single packet, enabling support for up to 255 channels. Unlike an
 * elementary Opus stream, the encoder and decoder must negotiate the channel
 * configuration before the decoder can successfully interpret the data in the
 * packets produced by the encoder. Some basic information, such as packet
 * duration, can be computed without any special negotiation.
 *
 * The format for multistream Opus packets is defined in the
 * <a href="https://tools.ietf.org/html/draft-ietf-codec-oggopus">Ogg
 * encapsulation specification</a> and is based on the self-delimited Opus
 * framing described in Appendix B of <a href="https://tools.ietf.org/html/rfc6716">RFC 6716</a>.
 * Normal Opus packets are just a degenerate case of multistream Opus packets,
 * and can be encoded or decoded with the multistream API by setting
 * <code>streams</code> to <code>1</code> when initializing the encoder or
 * decoder.
 *
 * Multistream Opus streams can contain up to 255 elementary Opus streams.
 * These may be either "uncoupled" or "coupled", indicating that the decoder
 * is configured to decode them to either 1 or 2 channels, respectively.
 * The streams are ordered so that all coupled streams appear at the
 * beginning.
 *
 * A <code>mapping</code> table defines which decoded channel <code>i</code>
 * should be used for each input/output (I/O) channel <code>j</code>. This table is
 * typically provided as an unsigned char array.
 * Let <code>i = mapping[j]</code> be the index for I/O channel <code>j</code>.
 * If <code>i < 2*coupled_streams</code>, then I/O channel <code>j</code> is
 * encoded as the left channel of stream <code>(i/2)</code> if <code>i</code>
 * is even, or  as the right channel of stream <code>(i/2)</code> if
 * <code>i</code> is odd. Otherwise, I/O channel <code>j</code> is encoded as
 * mono in stream <code>(i - coupled_streams)</code>, unless it has the special
 * value 255, in which case it is omitted from the encoding entirely (the
 * decoder will reproduce it as silence). Each value <code>i</code> must either
 * be the special value 255 or be less than <code>streams + coupled_streams</code>.
 *
 * The output channels specified by the encoder
 * should use the
 * <a href="https://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-810004.3.9">Vorbis
 * channel ordering</a>. A decoder may wish to apply an additional permutation
 * to the mapping the encoder used to achieve a different output channel
 * order (e.g. for outputing in WAV order).
 *
 * Each multistream packet contains an Opus packet for each stream, and all of
 * the Opus packets in a single multistream packet must have the same
 * duration. Therefore the duration of a multistream packet can be extracted
 * from the TOC sequence of the first stream, which is located at the
 * beginning of the packet, just like an elementary Opus stream:
 *
 * @code
 * int nb_samples;
 * int nb_frames;
 * nb_frames = opus_packet_get_nb_frames(data, len);
 * if (nb_frames < 1)
 *   return nb_frames;
 * nb_samples = opus_packet_get_samples_per_frame(data, 48000) * nb_frames;
 * @endcode
 *
 * The general encoding and decoding process proceeds exactly the same as in
 * the normal @ref opus_encoder and @ref opus_decoder APIs.
 * See their documentation for an overview of how to use the corresponding
 * mul
 */
public final class JOpusMSDecoder {
	//private static final String CLASS_NAME = "JOpusMSDecoder";

	/** This is the actual decoder CTL ID number.
	  * It should not be used directly by applications.
	  * In general, SETs should be even and GETs should be odd.*/
	private static final int OPUS_MULTISTREAM_GET_DECODER_STATE_REQUEST = 5122;
	public static final int OPUS_MULTISTREAM_GET_DECODER_STATE = OPUS_MULTISTREAM_GET_DECODER_STATE_REQUEST;

	private final JChannelLayout layout = new JChannelLayout();
	/* Decoder states go here */
	/** java added. coupled decoders */
	//private JOpusDecoder mCoupledDecoders[] = null;
	/** java added. stream decoders */
	//private JOpusDecoder mStreamDecoders[] = null;
	/** java added. decoders */
	private JOpusDecoder mDecoders[] = null;
	//
	public JOpusMSDecoder() {
	}
	/**
	 * java. to replace code:
	 * <pre>
	 * int decsize = OpusMSDecoder.opus_multistream_decoder_get_size( 1, 1 );
	 * OpusMSDecoder *dec = (OpusMSDecoder*)malloc( decsize );
	 * </pre>
	 * @param nb_streams
	 * @param nb_coupled_streams
	 * @throws IllegalArgumentException
	 */
	/*public JOpusMSDecoder(final int nb_streams, final int nb_coupled_streams) throws IllegalArgumentException {
		if( nb_streams < 1 || nb_coupled_streams > nb_streams || nb_coupled_streams < 0 ) {
			throw new IllegalArgumentException();
		}
	}*/
	/* DECODER */
/* #if defined(ENABLE_HARDENING) || defined(ENABLE_ASSERTIONS)
	static void validate_ms_decoder(OpusMSDecoder *st)
	{
		validate_layout(&st->layout);
	}
	#define VALIDATE_MS_DECODER(st) validate_ms_decoder(st)
#else
	#define VALIDATE_MS_DECODER(st)
#endif */

	/** Gets the size of an <code>OpusMSDecoder</code> structure.
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
	/*int opus_multistream_decoder_get_size(final int nb_streams, final int nb_coupled_streams)
	{
		int coupled_size;
		int mono_size;

		if( nb_streams < 1 || nb_coupled_streams > nb_streams || nb_coupled_streams < 0 ) {
			return 0;
		}
		coupled_size = opus_decoder_get_size( 2 );
		mono_size = opus_decoder_get_size( 1 );
		return align( sizeof( OpusMSDecoder ) )
				+ nb_coupled_streams * align( coupled_size )
				+ (nb_streams - nb_coupled_streams) * align( mono_size );
	}*/

	/** Intialize a previously allocated decoder state object.
	  * The memory pointed to by \a st must be at least the size returned by
	  * opus_multistream_encoder_get_size().
	  * This is intended for applications which use their own allocator instead of
	  * malloc.
	  * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	  * @see opus_multistream_decoder_create
	  * @see opus_multistream_deocder_get_size
	  * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state to initialize.
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
	  * @param mapping [in] <code>const unsigned char[channels]</code>: Mapping from
	  *                    coded channels to output channels, as described in
	  *                    @ref opus_multistream.
	  * @returns #OPUS_OK on success, or an error code (see @ref opus_errorcodes)
	  *          on failure.
	  */
	public final int opus_multistream_decoder_init(
			final int Fs,
			final int channels,
			final int streams,
			final int coupled_streams,
			final char[] mapping
			)
	{
		if( (channels > 255) || (channels < 1) || (coupled_streams > streams) ||
				(streams < 1) || (coupled_streams < 0) || (streams > 255 - coupled_streams)) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		this.layout.nb_channels = channels;
		this.layout.nb_streams = streams;
		this.layout.nb_coupled_streams = coupled_streams;

		final char[] maps = this.layout.mapping;
		for( int i = 0, n = this.layout.nb_channels; i < n; i++ ) {
			maps[i] = mapping[i];
		}
		if( 0 == this.layout.validate_layout() ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		/*
		char* ptr = (char*)st + align(sizeof(OpusMSDecoder));
		int coupled_size = opus_decoder_get_size( 2 );
		int mono_size = opus_decoder_get_size( 1 );
		*/

		//st.mCoupledDecoders = new JOpusDecoder[ st.layout.nb_coupled_streams ];// java
		//st.mStreamDecoders = new JOpusDecoder[ st.layout.nb_streams ];// java
		this.mDecoders = new JOpusDecoder[ streams ];// java

		int i;
		for( i = 0; i < coupled_streams; i++ )
		{
			final JOpusDecoder ptr = new JOpusDecoder();// java
			final int ret = ptr.opus_decoder_init( Fs, 2 );
			if( ret != Jopus_defines.OPUS_OK ) {
				return ret;
			}
			//st.mCoupledDecoders[i] = ptr;// java
			this.mDecoders[i] = ptr;// java
			//ptr += align( coupled_size );
		}
		for( ; i < streams; i++ )
		{
			final JOpusDecoder ptr = new JOpusDecoder();// java
			final int ret = ptr.opus_decoder_init( Fs, 1 );
			if( ret != Jopus_defines.OPUS_OK ) {
				return ret;
			}
			// st.mStreamDecoders[i] = ptr;// java
			this.mDecoders[i] = ptr;// java
			//ptr += align( mono_size );
		}
		return Jopus_defines.OPUS_OK;
	}

	/** Allocates and initializes a multistream decoder state.
	  * Call opus_multistream_decoder_destroy() to release
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
	  * @param mapping [in] <code>const unsigned char[channels]</code>: Mapping from
	  *                    coded channels to output channels, as described in
	  *                    @ref opus_multistream.
	  * @param error [out] <tt>int *</tt>: Returns #OPUS_OK on success, or an error
	  *                                   code (see @ref opus_errorcodes) on
	  *                                   failure.
	  */
	public static final JOpusMSDecoder opus_multistream_decoder_create(
			final int Fs,
			final int channels,
			final int streams,
			final int coupled_streams,
			final char[] mapping,
			final int[] error
			)
	{
		if( (channels > 255) || (channels < 1) || (coupled_streams > streams) ||
				(streams < 1) || (coupled_streams < 0) || (streams > 255 - coupled_streams) )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_BAD_ARG;
			}
			return null;
		}
		// st = (OpusMSDecoder *)opus_alloc( opus_multistream_decoder_get_size( streams, coupled_streams ) );
		JOpusMSDecoder st = new JOpusMSDecoder();
		/* if( st == null )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		final int ret = st.opus_multistream_decoder_init( Fs, channels, streams, coupled_streams, mapping );
		if( null != error ) {
			error[0] = ret;
		}
		if( ret != Jopus_defines.OPUS_OK )
		{
			//opus_free( st );
			st = null;
		}
		return st;
	}

	private static final int opus_multistream_packet_validate(final byte[] data, int doffset,// java
			int len, final int nb_streams, final int Fs)
	{
		final short size[] = new short[48];
		int samples = 0;

		// java changed: out_toc, payload_offset, packet_offset replaced by Jopus_packet_data_aux
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();
		for( int s = 0; s < nb_streams; s++ )
		{
			if( len <= 0 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			// final int packet_offset;
			// final byte toc;
			final int count = Jopus.opus_packet_parse_impl( data, doffset, len, s != nb_streams - 1,
					// toc,
					null, 0, size, 0,
					// null, packet_offset );
					aux );//
			if( count < 0 ) {
				return count;
			}
			final int tmp_samples = JOpusDecoder.opus_packet_get_nb_samples( data, doffset, aux.mPacketOffset, Fs );
			if( s != 0 && samples != tmp_samples ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			samples = tmp_samples;
			doffset += aux.mPacketOffset;
			len -= aux.mPacketOffset;
		}
		return samples;
	}

	final int opus_multistream_decode_native(
			final byte[] data, int doffset,// java
			int len,
			final Object pcm, final int poffset,// java
			final Iopus_copy_channel_out copy_channel_out,
			int frame_size,
			final boolean decode_fec,
			final boolean soft_clip,
			final Object user_data
			)
	{
		// ALLOC_STACK;

		// VALIDATE_MS_DECODER(st);
		if( frame_size <= 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final Object request[] = new Object[1];// java helper to request parameters
		/* Limit frame_size to avoid excessive stack allocations. */
		if( Jopus_defines.OPUS_OK != opus_multistream_decoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, request ) ) {// ;//Fs );
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}
		final int Fs = ((Integer)request[0]).intValue();// java
		final int v = Fs / 25 * 3;// java
		frame_size = frame_size < v ? frame_size : v;

		/*
		char* ptr = (char*)st + align( sizeof( OpusMSDecoder ) );
		int coupled_size = opus_decoder_get_size( 2 );
		int mono_size = opus_decoder_get_size( 1 );
		 */
		final boolean do_plc = (len == 0);

		if( len < 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}
		final JChannelLayout ch_layout = this.layout;// java
		final int nb_streams = ch_layout.nb_streams;// java
		if( ! do_plc && len < (nb_streams << 1) - 1 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_INVALID_PACKET;
		}
		if( ! do_plc )
		{
			final int ret = opus_multistream_packet_validate( data, doffset, len, nb_streams, Fs );
			if( ret < 0 )
			{
				// RESTORE_STACK;
				return ret;
			} else if( ret > frame_size )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
			}
		}
		final float[] buf = new float[frame_size << 1];
		// java changed: out_toc, payload_offset, packet_offset replaced by Jopus_packet_data_aux
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();
		for( int s = 0; s < nb_streams; s++ )
		{
			// (OpusDecoder*)ptr;
			//ptr += (s < st.layout.nb_coupled_streams) ? align( coupled_size ) : align( mono_size );

			if( ! do_plc && len <= 0 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			final JOpusDecoder dec = this.mDecoders[s];
			// final int packet_offset = 0;
			aux.mPacketOffset = 0;// java
			final int ret = dec.opus_decode_native( data, doffset, len, buf, frame_size, decode_fec, s != nb_streams - 1,
							// packet_offset,
							aux,// java replaced packet_offset
							soft_clip );
			doffset += aux.mPacketOffset;
			len -= aux.mPacketOffset;
			if( ret <= 0 )
			{
				// RESTORE_STACK;
				return ret;
			}
			frame_size = ret;
			if( s < ch_layout.nb_coupled_streams )
			{
				int chan, prev;
				prev = -1;
				/* Copy "left" audio to the channel(s) where it belongs */
				while( (chan = ch_layout.get_left_channel( s, prev )) != -1 )
				{
					copy_channel_out.copy_channel_out( pcm, poffset, ch_layout.nb_channels, chan, buf, 0, 2, frame_size, user_data );
					prev = chan;
				}
				prev = -1;
				/* Copy "right" audio to the channel(s) where it belongs */
				while( (chan = ch_layout.get_right_channel( s, prev )) != -1 )
				{
					copy_channel_out.copy_channel_out( pcm, poffset, ch_layout.nb_channels, chan, buf, 1, 2, frame_size, user_data );
					prev = chan;
				}
			} else {
				int chan;
				int prev = -1;
				/* Copy audio to the channel(s) where it belongs */
				while( (chan = ch_layout.get_mono_channel( s, prev )) != -1 )
				{
					copy_channel_out.copy_channel_out( pcm, poffset, ch_layout.nb_channels, chan, buf, 0, 1, frame_size, user_data );
					prev = chan;
				}
			}
		}
		/* Handle muted channels */
		for( int c = 0, n = ch_layout.nb_channels; c < n; c++ )
		{
			if( ch_layout.mapping[c] == 255 )
			{
				copy_channel_out.copy_channel_out( pcm, poffset, ch_layout.nb_channels, c, null, 0, 0, frame_size, user_data );
			}
		}
		// RESTORE_STACK;
		return frame_size;
	}
/*
// #if !defined(DISABLE_FLOAT_API)
	private static final void opus_copy_channel_out_float (
			final float[] float_dst,
			final int dst_stride,
			final int dst_channel,
			final float[] src,
			final int src_stride,
			final int frame_size,
			final Object user_data
			)
	{
		if( src != null )
		{
			for( int i = 0; i < frame_size; i++ ) {
// #if defined( FIXED_POINT )
//				float_dst[i * dst_stride + dst_channel] = (1 / 32768.f) * src[i * src_stride];
// #else
				float_dst[i * dst_stride + dst_channel] = src[i * src_stride];
// #endif
			}
		}
		else
		{
			for( int i = 0; i < frame_size; i++ ) {
				float_dst[i * dst_stride + dst_channel] = 0;
			}
		}
	}
// #endif
*/
	private static final class Jcopy_channel_out_float extends Iopus_copy_channel_out {
		@Override
		final void copy_channel_out(final Object dst, int doffset, final int dst_stride, final int dst_channel, final float[] src, int srcoffset, final int src_stride, int frame_size, final Object user_data) {
			final float[] float_dst = (float[])dst;
			frame_size *= dst_stride;// java
			doffset += dst_channel;// java
			frame_size += doffset;// java
			if( src != null )
			{
				for( ; doffset < frame_size; doffset += dst_stride, srcoffset += src_stride ) {
					float_dst[doffset] = src[srcoffset];
				}
				return;
			}
			for( ; doffset < frame_size; doffset += dst_stride ) {
				float_dst[doffset] = 0;
			}
		}
	}
	private static final Jcopy_channel_out_float opus_copy_channel_out_float = new Jcopy_channel_out_float();
/*
	private static final void opus_copy_channel_out_short(
			final short[] short_dst,
			final int dst_stride,
			final int dst_channel,
			final float[] src,
			final int src_stride,
			final int frame_size,
			final Object user_data
			)
	{
		if( src != null )
		{
			for( int i = 0; i < frame_size; i++ ) {
// #if defined(FIXED_POINT)
//				short_dst[i * dst_stride + dst_channel] = src[i * src_stride];
// #else
				short_dst[i * dst_stride + dst_channel] = Jfloat_cast.FLOAT2INT16( src[i * src_stride] );
// #endif
			}
		}
		else
		{
			for( int i = 0; i < frame_size; i++ ) {
				short_dst[i * dst_stride + dst_channel] = 0;
			}
		}
	}
*/
	private static final class Jcopy_channel_out_short extends Iopus_copy_channel_out {

		@Override
		final void copy_channel_out(final Object dst, int doffset, final int dst_stride, final int dst_channel, final float[] src, int srcoffset, final int src_stride, int frame_size, final Object user_data) {
			final short[] short_dst = (short[])dst;
			frame_size *= dst_stride;// java
			doffset += dst_channel;// java
			frame_size += doffset;// java
			if( src != null )
			{
				for( ; doffset < frame_size; doffset += dst_stride, srcoffset += src_stride ) {
					// short_dst[doffset] = Jfloat_cast.FLOAT2INT16( src[srcoffset] );
					float x = src[srcoffset];
					x *= Jfloat_cast.CELT_SIG_SCALE;
					x = x >= -32768 ? x : -32768;
					x = x <=  32767 ? x :  32767;
					short_dst[doffset] = (short)Math.floor( .5 + (double)x );
				}
				return;
			}
			for( ; doffset < frame_size; doffset += dst_stride ) {
				short_dst[doffset] = 0;
			}
		}

	}
	private static final Jcopy_channel_out_short opus_copy_channel_out_short = new Jcopy_channel_out_short();


// #ifdef FIXED_POINT
	/** Decode a multistream Opus packet.
	 * @param st <tt>OpusMSDecoder*</tt>: Multistream decoder state.
	 * @param[in] data <tt>const unsigned char*</tt>: Input payload.
	 *                                                Use a <code>NULL</code>
	 *                                                pointer to indicate packet
	 *                                                loss.
	 * @param len <tt>opus_int32</tt>: Number of bytes in payload.
	 * @param[out] pcm <tt>opus_int16*</tt>: Output signal, with interleaved
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
/*	private static final int opus_multistream_decode(
			final JOpusMSDecoder st,
			const unsigned char *data,
			int len,
			short[] pcm,
			int frame_size,
			int decode_fec
			)
	{
		return opus_multistream_decode_native(st, data, len,
				pcm, opus_copy_channel_out_short, frame_size, decode_fec, 0, null );
	}
*/
// #ifndef DISABLE_FLOAT_API
	/** Decode a multistream Opus packet with floating point output.
	 * @param st <tt>OpusMSDecoder*</tt>: Multistream decoder state.
	 * @param[in] data <tt>const unsigned char*</tt>: Input payload.
	 *                                                Use a <code>NULL</code>
	 *                                                pointer to indicate packet
	 *                                                loss.
	 * @param len <tt>opus_int32</tt>: Number of bytes in payload.
	 * @param[out] pcm <tt>opus_int16*</tt>: Output signal, with interleaved
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
/*	private static final int opus_multistream_decode_float(final JOpusMSDecoder st, const unsigned char *data,
			int len, float[] pcm, int frame_size, int decode_fec)
	{
		return opus_multistream_decode_native( st, data, len,
				pcm, opus_copy_channel_out_float, frame_size, decode_fec, 0, null );
	}
#endif

#else
*/
	/** Decode a multistream Opus packet.
	  * @param st <tt>OpusMSDecoder*</tt>: Multistream decoder state.
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
	public final int opus_multistream_decode(
			final byte[] data, final int doffset,// java
			final int len,
			final short[] pcm, final int poffset,// java
			final int frame_size, final boolean decode_fec)
	{
		return opus_multistream_decode_native( data, doffset, len,
			pcm, poffset, opus_copy_channel_out_short, frame_size, decode_fec, true, null );
	}

	/** Decode a multistream Opus packet with floating point output.
	 * @param st <tt>OpusMSDecoder*</tt>: Multistream decoder state.
	 * @param data [in] <tt>const unsigned char*</tt>: Input payload.
	 *                                                Use a <code>NULL</code>
	 *                                                pointer to indicate packet
	 *                                                loss.
	 * @param len <tt>opus_int32</tt>: Number of bytes in payload.
	 * @param pcm [out] <tt>opus_val16*</tt>: Output signal, with interleaved
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
	public final int opus_multistream_decode_float(
			final byte[] data, final int doffset,// java
			final int len,
			final float[] pcm, final int poffset,// java
			final int frame_size,
			final boolean decode_fec
			)
	{
		return opus_multistream_decode_native( data, doffset, len,
				pcm, poffset, opus_copy_channel_out_float, frame_size, decode_fec, false, null );
	}
// #endif

	// java: the same as opus_multistream_decoder_ctl_va_list
	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	/** Perform a CTL function on a multistream Opus decoder.
	 *
	 * java: getters
	 *
	 * Generally the request and subsequent arguments are generated by a
	 * convenience macro.
	 * @param st <tt>OpusMSDecoder*</tt>: Multistream decoder state.
	 * @param request This and all remaining parameters should be replaced by one
	 *                of the convenience macros in @ref opus_genericctls,
	 *                @ref opus_decoderctls, or @ref opus_multistream_ctls.
	 * @see opus_genericctls
	 * @see opus_decoderctls
	 * @see opus_multistream_ctls
	 */
	public final int opus_multistream_decoder_ctl(final int request, final int streamId, final Object[] arg)
	{// getters
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case OPUS_MULTISTREAM_GET_DECODER_STATE_REQUEST:
			{
				if( streamId < 0 || streamId >= this.layout.nb_streams ) {
					// ret = Jopus_defines.OPUS_BAD_ARG;// FIXME why go on?
					return Jopus_defines.OPUS_BAD_ARG;// java changed
				}
				arg[0] = this.mDecoders[ streamId ];
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	// java added overload variant
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int opus_multistream_decoder_ctl(final int request, final Object[] arg)
	{
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_GET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_GET_SAMPLE_RATE_REQUEST:
		case Jopus_defines.OPUS_GET_GAIN_REQUEST:
		case Jopus_defines.OPUS_GET_LAST_PACKET_DURATION_REQUEST:
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				// For int32* GET params, just query the first stream
				ret = this.mDecoders[0].opus_decoder_ctl( request, arg );
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				final Object[] tmp = new Object[1];
				final JOpusDecoder[] decs = this.mDecoders;// java
				long value = 0;
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					ret = decs[s].opus_decoder_ctl( request, tmp );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
					value ^= ((Long)tmp[0]).longValue();
				}
				arg[0] = Long.valueOf( value );
			}
			break;
		default:
			/*fprintf( stderr, "unknown opus_decoder_ctl( ) request: %d", request );*/
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}
	// java added overload variant
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int opus_multistream_decoder_ctl(final int request, final int arg)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_GAIN_REQUEST:
			{
				/* This works for int32 params */
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					ret = this.mDecoders[s].opus_decoder_ctl( request, arg );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		default:
			/*fprintf( stderr, "unknown opus_decoder_ctl( ) request: %d", request );*/
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}
	// java added overload variant
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int opus_multistream_decoder_ctl(final int request, final boolean arg)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				/* This works for int32 params */
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					ret = this.mDecoders[s].opus_decoder_ctl( request, arg );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		default:
			/*fprintf( stderr, "unknown opus_decoder_ctl( ) request: %d", request );*/
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}
	// java added overload variant
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return status
	 */
	public final int opus_multistream_decoder_ctl(final int request) {
		int ret = Jopus_defines.OPUS_OK;
		switch( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					ret = this.mDecoders[s].opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		default:
			/*fprintf( stderr, "unknown opus_decoder_ctl( ) request: %d", request );*/
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}

	/** Frees an <code>OpusMSDecoder</code> allocated by
	 * opus_multistream_decoder_create().
	 * @param st <tt>OpusMSDecoder</tt>: Multistream decoder state to be freed.
	 */
	/*private static final void opus_multistream_decoder_destroy(final JOpusMSDecoder st)
	{// java: don't need. use st = null;
		opus_free( st );
	}*/

}