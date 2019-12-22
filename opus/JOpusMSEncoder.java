package opus;

import celt.JCELTEncoder;
import celt.JCELTMode;
import celt.Jcelt;
import celt.Jcelt_codec_API;
import celt.Jmath_ops;

/* Copyright (c) 2011 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// opus_multistream_encoder.c

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
 * The format for multistream Opus packets is defined in
 * <a href="https://tools.ietf.org/html/rfc7845">RFC 7845</a>
 * and is based on the self-delimited Opus framing described in Appendix B of
 * <a href="https://tools.ietf.org/html/rfc6716">RFC 6716</a>.
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
public final class JOpusMSEncoder {
	// private static final String CLASS_NAME = "JOpusMSEncoder";

	/** This is the actual encoder CTL ID number.
	  * It should not be used directly by applications.
	  * In general, SETs should be even and GETs should be odd.*/
	public static final int OPUS_MULTISTREAM_GET_ENCODER_STATE_REQUEST = 5120;
	public static final int OPUS_MULTISTREAM_GET_ENCODER_STATE = OPUS_MULTISTREAM_GET_ENCODER_STATE_REQUEST;

	private static final class JVorbisLayout {
		private final int nb_streams;
		private final int nb_coupled_streams;
		private final char mapping[];// = new char[8];
		//
		private JVorbisLayout(final int istreams, final int icoupled_streams, final char[] imapping) {
			nb_streams = istreams;
			nb_coupled_streams = icoupled_streams;
			/* final int count = imapping.length <= mapping.length ? imapping.length : mapping.length;// java: input may has size less than 8
			for( int i = 0; i < count; i++ ) {// is it possible doing just set, without copying?
				mapping[i] = imapping[i];
			}*/
			mapping = imapping;
		}
	}

	/** Index is nb_channel-1*/
	private static final JVorbisLayout vorbis_mappings[/* 8 */] = {
		new JVorbisLayout( 1, 0, new char[]{0} ),                      /* 1: mono */
		new JVorbisLayout( 1, 1, new char[]{0, 1} ),                   /* 2: stereo */
		new JVorbisLayout( 2, 1, new char[]{0, 2, 1} ),                /* 3: 1-d surround */
		new JVorbisLayout( 2, 2, new char[]{0, 1, 2, 3} ),             /* 4: quadraphonic surround */
		new JVorbisLayout( 3, 2, new char[]{0, 4, 1, 2, 3} ),          /* 5: 5-channel surround */
		new JVorbisLayout( 4, 2, new char[]{0, 4, 1, 2, 3, 5} ),       /* 6: 5.1 surround */
		new JVorbisLayout( 4, 3, new char[]{0, 4, 1, 2, 3, 5, 6} ),    /* 7: 6.1 surround */
		new JVorbisLayout( 5, 3, new char[]{0, 6, 1, 2, 3, 4, 5, 7} ), /* 8: 7.1 surround */
	};

//	typedef enum {
		private static final int MAPPING_TYPE_NONE = 0;
		private static final int MAPPING_TYPE_SURROUND = 1;
		private static final int MAPPING_TYPE_AMBISONICS = 2;
//	} MappingType;

//struct OpusMSEncoder {
	final JChannelLayout layout = new JChannelLayout();
	//int arch;
	private int lfe_stream;
	@SuppressWarnings("unused")
	private int application;// FIXME application never uses
	private int variable_duration;
	/* MappingType */ int mapping_type;
	private int bitrate_bps;
		/* Encoder states go here */
		/* then opus_val32 window_mem[channels*120]; */
		/* then opus_val32 preemph_mem[channels]; */
		// java
	private float window_mem[] = null;
	private float preemph_mem[] = null;
	private JOpusEncoder mEncoders[] = null;
//};

	private final float[] ms_get_preemph_mem()
	{
		return this.preemph_mem;
	}

	private final float[] ms_get_window_mem()
	{
		return this.window_mem;
	}


	private static boolean validate_ambisonics(final int nb_channels, final Jms_encoder_data_aux data)// final int[] nb_streams, final int[] nb_coupled_streams)
	{
		if( nb_channels < 1 || nb_channels > 227 ) {
			return false;
		}

		final int order_plus_one = Jmath_ops.isqrt32( nb_channels );
		final int acn_channels = order_plus_one * order_plus_one;
		int nondiegetic_channels = nb_channels - acn_channels;

		if( nondiegetic_channels != 0 && nondiegetic_channels != 2 ) {
			return false;
		}
		if( nondiegetic_channels != 0 ) {
			nondiegetic_channels = 1;
		}
		if( null != data ) {
			data.streams = acn_channels + nondiegetic_channels;
			data.coupled_streams = nondiegetic_channels;
		}
		return true;
	}

	private static final void channel_pos(final int channels, final int pos[/* 8 */])
	{
		/* Position in the mix: 0 don't mix, 1: left, 2: center, 3:right */
		if( channels == 4 )
		{
			pos[0] = 1;
			pos[1] = 3;
			pos[2] = 1;
			pos[3] = 3;
		} else if( channels == 3 || channels == 5 || channels == 6 )
		{
			pos[0] = 1;
			pos[1] = 2;
			pos[2] = 3;
			pos[3] = 1;
			pos[4] = 3;
			pos[5] = 0;
		} else if( channels == 7 )
		{
			pos[0] = 1;
			pos[1] = 2;
			pos[2] = 3;
			pos[3] = 1;
			pos[4] = 3;
			pos[5] = 2;
			pos[6] = 0;
		} else if( channels == 8 )
		{
			pos[0] = 1;
			pos[1] = 2;
			pos[2] = 3;
			pos[3] = 1;
			pos[4] = 3;
			pos[5] = 1;
			pos[6] = 3;
			pos[7] = 0;
		}
	}

// #if 1
	private static final float diff_table[/* 17 */] = {// FIXME len = 17, inited only 9
			0.5000000f, 0.2924813f, 0.1609640f, 0.0849625f,
			0.0437314f, 0.0221971f, 0.0111839f, 0.0056136f,
			0.0028123f,
			0, 0, 0, 0, 0, 0, 0, 0
		};
	/**
	 * Computes a rough approximation of log2(2^a + 2^b)
	 * @param a
	 * @param b
	 * @return log sum
	 */
	private static final float logSum(final float a, final float b)
	{
		float max;
		float diff;
		if( a > b )
		{
			max = a;
			diff = a - b;
		} else {
			max = b;
			diff = b - a;
		}
		if( ! (diff < 8.f) ) {
			return max;
		}
/* #ifdef FIXED_POINT
		low = SHR32( diff, DB_SHIFT - 1 );
		frac = SHL16( diff - SHL16( low, DB_SHIFT - 1 ), 16 - DB_SHIFT );
#else */
		diff *= 2f;// java
		final int low = (int)Math.floor( (double)diff );
		final float frac = diff - low;
// #endif
		return max + diff_table[low] + frac * (diff_table[low + 1] - diff_table[low]);
	}
/* #else
	private static final short logSum(final opus_val16 a, final opus_val16 b)
	{
		return log2( pow( 4, a ) + pow( 4, b ) ) / 2;
	}
#endif */

	private static final void surround_analysis(final JCELTMode celt_mode,
			final Object pcm, final int pcmoffset,// java
			final float[] bandLogE, final float[] mem, final float[] preemph_mem,
			final int len, final int overlap, final int channels, final int rate, final Iopus_copy_channel_in copy_channel_in//, int arch
	)
	{
		final int pos[] = new int[8];//{0};
		final float bandE[] = new float[21];
		final float maskLogE[][] = new float[3][21];
		// SAVE_STACK;

		final int upsample = Jcelt.resampling_factor( rate );
		final int frame_size = len * upsample;

		/* LM = log2(frame_size / 120) */
		int LM;
		for( LM = 0; LM < celt_mode.maxLM; LM++ ) {
			if( celt_mode.shortMdctSize << LM == frame_size ) {
				break;
			}
		}

		final float[] in = new float[frame_size + overlap];
		final float[] x = new float[len];
		final int freq_size = (960 <= frame_size ? 960 : frame_size);
		final float[] freq = new float[freq_size];

		channel_pos( channels, pos );

		for( int c = 0; c < 3; c++ ) {
			final float m[] = maskLogE[c];// java
			for( int i = 0; i < 21; i++ ) {
				m[i] = -28.f;
			}
		}

		final float tmpE[] = new float[21];// java: out of the loop
		for( int c = 0; c < channels; c++ )
		{
			// celt_assert(nb_frames*freq_size == frame_size);
			System.arraycopy( mem, c * overlap, in, 0, overlap );
			copy_channel_in.copy_channel_in( x, 0, 1, pcm, pcmoffset, channels, c, len, null );
			JCELTEncoder.celt_preemphasis( x, 0, in, overlap, frame_size, 1, upsample, celt_mode.preemph, preemph_mem, c, false );
// #ifndef FIXED_POINT
			{
				final float sum = Jcelt_codec_API.celt_inner_prod( in, 0, in, 0, frame_size + overlap );//, 0 );
				/* This should filter out both NaNs and ridiculous signals that could
				   cause NaNs further down. */
				if( !(sum < 1e18f) || Float.isNaN( sum ) )
				{
					//OPUS_CLEAR( in, frame_size + overlap );
					for( int i = 0, ie = frame_size + overlap; i < ie; i++ ) {
						in[i] = 0;
					}
					preemph_mem[c] = 0;
				}
			}
// #endif
			int i = 21;
			do {
				bandE[--i] = 0;
			} while( i > 0 );
			final int nb_frames = frame_size / freq_size;
			for( int frame = 0; frame < nb_frames; frame += 960 )// java modified
			{
				celt_mode.mdct.clt_mdct_forward( in, /* 960 * */frame, freq, 0, celt_mode.window,
						overlap, celt_mode.maxLM - LM, 1 );//, arch);
				if( upsample != 1 )
				{
					final int bound = freq_size / upsample;
					for( i = 0; i < bound; i++ ) {
						freq[i] *= upsample;
					}
					for( ; i < freq_size; i++ ) {
						freq[i] = 0;
					}
				}

				celt_mode.compute_band_energies( freq, tmpE, 21, 1, LM );// , arch);
				/* If we have multiple frames, take the max energy. */
				for( i = 0; i < 21; i++ ) {
					final float v1 = bandE[i];// java
					final float v2 = tmpE[i];// java
					bandE[i] = (v1 >= v2 ? v1 : v2);
				}
			}
			JCELTMode.amp2Log2( celt_mode.nbEBands /* celt_mode */, 21, 21, bandE, bandLogE, 21 * c, 1 );
			/* Apply spreading function with -6 dB/band going up and -12 dB/band going down. */
			int c21 = 21 * c;// java
			for( i = 1; i < 21; i++ ) {
				final int ic21 = c21 + i;// java
				final float v2 = bandLogE[ic21 - 1] - 1.f;// java
				final float v1 = bandLogE[ic21];// java
				bandLogE[ic21] = v1 >= v2 ? v1 : v2;
			}
			for( i = 19; i >= 0; i-- ) {
				final int ic21 = c21 + i;// java
				final float v2 = bandLogE[ic21 + 1] - 2.f;// java
				final float v1 = bandLogE[ic21];// java
				bandLogE[ic21] = v1 >= v2 ? v1 : v2;
			}
			if( pos[c] == 1 )
			{
				final float[] m = maskLogE[0];// java
				for( i = 0; i < 21; i++ ) {
					m[i] = logSum( m[i], bandLogE[c21++] );
				}
			} else if( pos[c] == 3 )
			{
				final float[] m = maskLogE[2];// java
				for( i = 0; i < 21; i++ ) {
					m[i] = logSum( m[i], bandLogE[c21++] );
				}
			} else if( pos[c] == 2 )
			{
				final float[] m0 = maskLogE[0];// java
				final float[] m2 = maskLogE[2];// java
				for( i = 0; i < 21; i++, c21++ )
				{
					final float v = bandLogE[c21] - .5f;// java
					m0[i] = logSum( m0[i], v );
					m2[i] = logSum( m2[i], v );
				}
			}
/* #if 0
			for( i = 0; i < 21; i++ )
				printf("%f ", bandLogE[21 * c + i]);
			float sum = 0;
			for( i = 0; i < 21; i++ )
				sum += bandLogE[21 * c + i];
			printf("%f ", sum / 21 );
#endif */
			System.arraycopy( in, frame_size, mem, c * overlap, overlap );
		}
		for( int i = 0; i < 21; i++ ) {
			final float v1 = maskLogE[0][i];// java
			final float v2 = maskLogE[2][i];// java
			maskLogE[1][i] = v1 <= v2 ? v1 : v2;
		}
		final float channel_offset = .5f * ((float)(1.442695040888963387 * Math.log( (double)(2.f / (float)(channels - 1)) )));
		for( int c = 0; c < 3; c++ ) {
			final float[] m = maskLogE[c];// java
			for( int i = 0; i < 21; i++ ) {
				m[i] += channel_offset;
			}
		}
/* #if 0
		for( c = 0; c < 3; c++ )
		{
			for( i = 0; i < 21; i++ )
				printf("%f ", maskLogE[c][i] );
		}
#endif */
		for( int c = 0; c < channels; c++ )
		{
			int c21 = 21 * c;// java
			int i;
			if( pos[c] != 0)
			{
				//opus_val16 *mask = &maskLogE[pos[c] - 1][0];
				final float[] mask = maskLogE[pos[c] - 1];

				for( i = 0; i < 21; i++, c21++ ) {
					bandLogE[c21] -= mask[i];
				}
			} else {
				for( i = c21, c21 += 21; i < c21; i++ ) {
					bandLogE[i] = 0;
				}
			}
/* #if 0
			for( i = 0; i < 21; i++ )
				printf("%f ", bandLogE[21 * c + i]);
			printf("\n");
#endif */
/* #if 0
			float sum = 0;
			for( i = 0; i < 21; i++ )
				sum += bandLogE[21 * c + i];
			printf("%f ", sum / (float)QCONST32( 21.f, DB_SHIFT ) );
			printf("\n");
#endif */
		}
		//RESTORE_STACK;
	}

	/** Gets the size of an OpusMSEncoder structure.
	 * @param streams <tt>int</tt>: The total number of streams to encode from the
	 *                              input.
	 *                              This must be no more than 255.
	 * @param coupled_streams <tt>int</tt>: Number of coupled (2 channel) streams
	 *                                      to encode.
	 *                                      This must be no larger than the total
	 *                                      number of streams.
	 *                                      Additionally, The total number of
	 *                                      encoded channels (<code>streams +
	 *                                      coupled_streams</code>) must be no
	 *                                      more than 255.
	 * @returns The size in bytes on success, or a negative error code
	 *          (see @ref opus_errorcodes) on error.
	 */
	/*private static final int opus_multistream_encoder_get_size(final int nb_streams, final int nb_coupled_streams)
	{
		if( nb_streams < 1 || nb_coupled_streams > nb_streams || nb_coupled_streams < 0 ) {
			return 0;
		}
		int coupled_size = opus_encoder_get_size( 2 );
		int mono_size = opus_encoder_get_size( 1 );
		return align( sizeof( JOpusMSEncoder ) )
				+ nb_coupled_streams * align( coupled_size )
				+ (nb_streams - nb_coupled_streams) * align( mono_size );
	}*/

	/*
	private static final int opus_multistream_surround_encoder_get_size(final int channels, final int mapping_family)
	{
		int nb_streams;
		int nb_coupled_streams;

		if( mapping_family == 0 )
		{
			if( channels == 1 )
			{
				nb_streams = 1;
				nb_coupled_streams = 0;
			} else if( channels == 2 )
			{
				nb_streams = 1;
				nb_coupled_streams = 1;
			} else {
				return 0;
			}
		} else if( mapping_family == 1 && channels <= 8 && channels >= 1 )
		{
			nb_streams = vorbis_mappings[channels - 1].nb_streams;
			nb_coupled_streams = vorbis_mappings[channels - 1].nb_coupled_streams;
		} else if( mapping_family == 255 )
		{
			nb_streams = channels;
			nb_coupled_streams = 0;
		} else if( mapping_family == 2 )
		{
			if( ! validate_ambisonics( channels, &nb_streams, &nb_coupled_streams ) )
				return 0;
		} else {
			return 0;
		}
		int size = opus_multistream_encoder_get_size( nb_streams, nb_coupled_streams );
		if( channels > 2 )
		{
			size += channels * (120 * sizeof( opus_val32 ) + sizeof( opus_val32 ) );
		}
		return size;
	}
	 */

	/**
	 *
	 * @param Fs
	 * @param channels
	 * @param streams
	 * @param coupled_streams
	 * @param mapping
	 * @param app_mode
	 * @param e_mapping_type
	 * @return
	 */
	private final int opus_multistream_encoder_init_impl(
		final int Fs, final int channels, final int streams, final int coupled_streams,
		final char[] mapping, final int app_mode, /* MappingType */ final int e_mapping_type
	)
	{
		if( (channels > 255) || (channels < 1) || (coupled_streams > streams) ||
				(streams < 1) || (coupled_streams < 0) || (streams > 255 - coupled_streams) ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final JChannelLayout ch_layout = this.layout;// java
		// st.arch = opus_select_arch();
		ch_layout.nb_channels = channels;
		ch_layout.nb_streams = streams;
		ch_layout.nb_coupled_streams = coupled_streams;
		if( e_mapping_type != MAPPING_TYPE_SURROUND ) {
			this.lfe_stream = -1;
		}
		this.bitrate_bps = Jopus_defines.OPUS_AUTO;
		this.application = app_mode;
		this.variable_duration = Jopus_defines.OPUS_FRAMESIZE_ARG;
		for( int i = 0, n = ch_layout.nb_channels; i < n; i++ ) {
			ch_layout.mapping[i] = mapping[i];
		}
		if( 0 == ch_layout.validate_layout() ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( mapping_type == MAPPING_TYPE_SURROUND &&
				! this.layout.validate_encoder_layout() ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( mapping_type == MAPPING_TYPE_AMBISONICS &&
				! validate_ambisonics( this.layout.nb_channels, null ) ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		/*
		char *ptr = (char*)st + align( sizeof( JOpusMSEncoder ) );
		final int coupled_size = opus_encoder_get_size( 2 );
		final int mono_size = opus_encoder_get_size( 1 );
		 */
		mEncoders = new JOpusEncoder[ streams ];// java
		int i;
		for( i = 0; i < coupled_streams; i++ )
		{
			final JOpusEncoder ptr = new JOpusEncoder();// java
			final int ret = ptr.opus_encoder_init( Fs, 2, app_mode );
			if( ret != Jopus_defines.OPUS_OK ) {
				return ret;
			}
			if( i == this.lfe_stream ) {
				ptr.opus_encoder_ctl( Jcelt.OPUS_SET_LFE, true );
			}
			this.mEncoders[i] = ptr;// java
			//ptr += align( coupled_size );
		}
		for( ; i < streams; i++ )
		{
			final JOpusEncoder ptr = new JOpusEncoder();// java
			final int ret = ptr.opus_encoder_init( Fs, 1, app_mode );
			if( i == this.lfe_stream ) {
				ptr.opus_encoder_ctl( Jcelt.OPUS_SET_LFE, true );
			}
			if( ret != Jopus_defines.OPUS_OK ) {
				return ret;
			}
			this.mEncoders[i] = ptr;// java
			//ptr += align( mono_size );
		}
		if( e_mapping_type == MAPPING_TYPE_SURROUND )
		{
			//OPUS_CLEAR( ms_get_preemph_mem( st ), channels );
			float[] mem = this.preemph_mem;
			for( i = 0; i < channels; i++ ) {
				mem[i] = 0;
			}
			//OPUS_CLEAR( ms_get_window_mem( st ), channels * 120 );
			mem = this.window_mem;
			final int ie = channels * 120;
			for( i = 0; i < ie; i++ ) {
				mem[i] = 0;
			}
		}
		this.mapping_type = e_mapping_type;
		return Jopus_defines.OPUS_OK;
	}

	/** Initialize a previously allocated multistream encoder state.
	  * The memory pointed to by \a st must be at least the size returned by
	  * opus_multistream_encoder_get_size().
	  * This is intended for applications which use their own allocator instead of
	  * malloc.
	  * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	  * @see opus_multistream_encoder_create
	  * @see opus_multistream_encoder_get_size
	  * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state to initialize.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate of the input signal (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels in the input signal.
	  *                               This must be at most 255.
	  *                               It may be greater than the number of
	  *                               coded channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param streams <tt>int</tt>: The total number of streams to encode from the
	  *                              input.
	  *                              This must be no more than the number of channels.
	  * @param coupled_streams <tt>int</tt>: Number of coupled (2 channel) streams
	  *                                      to encode.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      encoded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than the number of input channels.
	  * @param mapping <code>const unsigned char[channels]</code>: Mapping from
	  *                    encoded channels to input channels, as described in
	  *                    @ref opus_multistream. As an extra constraint, the
	  *                    multistream encoder does not allow encoding coupled
	  *                    streams for which one channel is unused since this
	  *                    is never a good idea.
	  * @param app_mode <tt>int</tt>: The target encoder application.
	  *                                  This must be one of the following:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @return #OPUS_OK on success, or an error code (see @ref opus_errorcodes)
	  *          on failure.
	  */
	final int opus_multistream_encoder_init(
		final int Fs,
		final int channels,
		final int streams,
		final int coupled_streams,
		final char[] mapping,
		final int app_mode
	)
	{
		return opus_multistream_encoder_init_impl( Fs, channels, streams,
				coupled_streams, mapping,
				app_mode, MAPPING_TYPE_NONE );
	}

	private final int opus_multistream_surround_encoder_init(
		final int Fs, final int channels, final int mapping_family,
		//final int[] streams, final int[] coupled_streams,
		final Jms_encoder_data_aux data,// java
		final char[] mapping, final int app_mode
	)
	{
		if( (channels > 255) || (channels < 1) ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		this.lfe_stream = -1;
		if( mapping_family == 0 )
		{
			if( channels == 1 )
			{
				data.streams = 1;
				data.coupled_streams = 0;
				mapping[0] = 0;
			} else if( channels == 2 )
			{
				data.streams = 1;
				data.coupled_streams = 1;
				mapping[0] = 0;
				mapping[1] = 1;
			} else {
				return Jopus_defines.OPUS_UNIMPLEMENTED;
			}
		} else if( mapping_family == 1 && channels <= 8 && channels >= 1 )
		{
			final int ch1 = channels - 1;// java
			data.streams = vorbis_mappings[ch1].nb_streams;
			final JVorbisLayout v = vorbis_mappings[ch1];// java
			data.coupled_streams = v.nb_coupled_streams;
			for( int i = 0; i < channels; i++ ) {
				mapping[i] = v.mapping[i];
			}
			if( channels >= 6 ) {
				this.lfe_stream = data.streams - 1;
			}
		} else if( mapping_family == 255 )
		{
			data.streams = channels;
			data.coupled_streams = 0;
			for( int i = 0; i < channels; i++ ) {
				mapping[i] = (char)i;
			}
		} else if( mapping_family == 2 )
		{
			if( ! validate_ambisonics( channels, data ) ) {
				return Jopus_defines.OPUS_BAD_ARG;
			}
			final int diff = data.streams - data.coupled_streams;// java
			final int s2 = data.coupled_streams << 1;// java
			for( int i = 0, ie = diff; i < ie; i++ ) {
				mapping[ i ] = (char)(i + s2);
			}
			for( int i = 0; i < s2; i++ ) {
				mapping[ i + diff ] = (char)i;
			}
		} else {
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}

		/* MappingType */ int e_mapping_type;
		if( channels > 2 && mapping_family == 1 ) {
			e_mapping_type = MAPPING_TYPE_SURROUND;
		} else if( mapping_family == 2 )
		{
			e_mapping_type = MAPPING_TYPE_AMBISONICS;
		} else
		{
			e_mapping_type = MAPPING_TYPE_NONE;
		}
		return opus_multistream_encoder_init_impl( Fs, channels, data.streams,
												data.coupled_streams, mapping,
												app_mode, e_mapping_type );
	}

	/** Allocates and initializes a multistream encoder state.
	  * Call opus_multistream_encoder_destroy() to release
	  * this object when finished.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate of the input signal (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels in the input signal.
	  *                               This must be at most 255.
	  *                               It may be greater than the number of
	  *                               coded channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param streams <tt>int</tt>: The total number of streams to encode from the
	  *                              input.
	  *                              This must be no more than the number of channels.
	  * @param coupled_streams <tt>int</tt>: Number of coupled (2 channel) streams
	  *                                      to encode.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      encoded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than the number of input channels.
	  * @param mapping [in] <code>const unsigned char[channels]</code>: Mapping from
	  *                    encoded channels to input channels, as described in
	  *                    @ref opus_multistream. As an extra constraint, the
	  *                    multistream encoder does not allow encoding coupled
	  *                    streams for which one channel is unused since this
	  *                    is never a good idea.
	  * @param application <tt>int</tt>: The target encoder application.
	  *                                  This must be one of the following:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @param error [out] <tt>int *</tt>: Returns #OPUS_OK on success, or an error
	  *                                   code (see @ref opus_errorcodes) on
	  *                                   failure.
	  * @return JOpusMSEncoder
	  */
	public static final JOpusMSEncoder opus_multistream_encoder_create(
		final int Fs,
		final int channels,
		final int streams,
		final int coupled_streams,
		final char[] mapping,
		final int application,
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
		//OpusMSEncoder st = (OpusMSEncoder)opus_alloc( opus_multistream_encoder_get_size( streams, coupled_streams ) );
		JOpusMSEncoder st = new JOpusMSEncoder();
		/*if( st == null )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		//st.window_mem = new float[channels * 120];
		//st.preemph_mem = new float[channels];
		final int ret = st.opus_multistream_encoder_init( Fs, channels, streams, coupled_streams, mapping, application );
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

	/**
	 *
	 * @param Fs
	 * @param channels
	 * @param mapping_family
	 * @param data
	 * @param mapping
	 * @param application
	 * @param error
	 * @return a JOpusMSEncoder object
	 */
	public static final JOpusMSEncoder opus_multistream_surround_encoder_create(
		final int Fs, final int channels, final int mapping_family,
		// final int[] streams, final int[] coupled_streams,
		final Jms_encoder_data_aux data,// java
		final char[] mapping, final int application, final int[] error
	)
	{
		if( (channels > 255) || (channels < 1) )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_BAD_ARG;
			}
			return null;
		}
		/*int size = opus_multistream_surround_encoder_get_size( channels, mapping_family );
		if( ! size )
		{
			if( null != error )
				error[0] = Jopus_defines.OPUS_UNIMPLEMENTED;
			return null;
		}*/
		//JOpusMSEncoder st = (JOpusMSEncoder)opus_alloc( size );
		JOpusMSEncoder st = new JOpusMSEncoder();
		/*if( st == null )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		st.window_mem = new float[channels * 120];
		st.preemph_mem = new float[channels];
		final int ret = st.opus_multistream_surround_encoder_init( Fs, channels, mapping_family,
				// streams, coupled_streams,
				data,// java
				mapping, application );
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

	private final void surround_rate_allocation(final int[] rate, final int frame_size, final int Fs)
	{
		final int nb_lfe = (this.lfe_stream != -1 ? 1 : 0);
		int nb_coupled = this.layout.nb_coupled_streams;
		int nb_uncoupled = this.layout.nb_streams - nb_coupled - nb_lfe;
		int nb_normal = (nb_coupled << 1) + nb_uncoupled;

		/* Give each non-LFE channel enough bits per channel for coding band energy. */
		int channel_offset = Fs / frame_size;
		channel_offset = (50 >= channel_offset ? 50 : channel_offset);// java see * 40 below

		int bitrate;
		if( this.bitrate_bps == Jopus_defines.OPUS_AUTO )
		{
			bitrate = nb_normal * (channel_offset + Fs + 10000) + 8000 * nb_lfe;
		} else if( this.bitrate_bps == Jopus_defines.OPUS_BITRATE_MAX )
		{
			bitrate = nb_normal * 300000 + nb_lfe * 128000;
		} else {
			bitrate = this.bitrate_bps;
		}

		/* Give LFE some basic stream_channel allocation but never exceed 1/20 of the
		   total rate for the non-energy part to avoid problems at really low rate. */
		// int lfe_offset = IMIN(bitrate / 20, 3000) + 15 * IMAX(50, Fs / frame_size);
		int total = bitrate / 20;// java
		total = (total <= 3000 ? total : 3000);
		int lfe_offset = 15 * channel_offset;// java
		lfe_offset += total;
		channel_offset *= 40;// java

		/* We give each stream (coupled or uncoupled) a starting bitrate.
		   This models the main saving of coupled channels over uncoupled. */
		int stream_offset = ((bitrate - channel_offset * nb_normal - lfe_offset * nb_lfe) / nb_normal) >>> 1;
		stream_offset = (20000 <= stream_offset ? 20000 : stream_offset);
		stream_offset = (0 >= stream_offset ? 0 : stream_offset);

		/* Coupled streams get twice the mono rate after the offset is allocated. */
		final int coupled_ratio = 512;// Q8
		/* Should depend on the bitrate, for now we assume LFE gets 1/8 the bits of mono */
		final int lfe_ratio = 32;// Q8

		total = (nb_uncoupled << 8)         /* mono */
				+ coupled_ratio * nb_coupled /* stereo */
				+ nb_lfe * lfe_ratio;
		final int channel_rate = (int)((((long)(bitrate - lfe_offset * nb_lfe - stream_offset * (nb_coupled + nb_uncoupled) - channel_offset * nb_normal)) << 8) / total);

		for( int i = 0, ie = this.layout.nb_streams; i < ie; i++ )
		{
			if( i < this.layout.nb_coupled_streams ) {
				// rate[i] = 2 * channel_offset + IMAX(0, stream_offset + (channel_rate * coupled_ratio >> 8));
				nb_coupled = stream_offset + (channel_rate * coupled_ratio >> 8);
				nb_coupled = (0 >= nb_coupled ? 0 : nb_coupled);
				nb_coupled += channel_offset << 1;
				rate[i] = nb_coupled;
			} else if( i != this.lfe_stream ) {
				// rate[i] = channel_offset + IMAX(0, stream_offset + channel_rate);
				nb_uncoupled = stream_offset + channel_rate;
				nb_uncoupled = (0 >= nb_uncoupled ? 0 : nb_uncoupled);
				nb_uncoupled += channel_offset;
				rate[i] = nb_uncoupled;
			} else {
				// rate[i] = IMAX(0, lfe_offset + (channel_rate * lfe_ratio >> 8));
				nb_normal = lfe_offset + (channel_rate * lfe_ratio >> 8);
				nb_normal = (0 >= nb_normal ? 0 : nb_normal);
				rate[i] = nb_normal;
			}
		}
	}

	private final void ambisonics_rate_allocation(
			// JOpusMSEncoder st,
			final int[] rate,
			final int frame_size,
			final int Fs
      )
	{
		final int layout_nb_stream = this.layout.nb_streams;// java
		final int nb_channels = layout_nb_stream + this.layout.nb_coupled_streams;

		int total_rate;
		if( this.bitrate_bps == Jopus_defines.OPUS_AUTO )
		{
			total_rate = (this.layout.nb_coupled_streams + layout_nb_stream) *
					(Fs + 60 * Fs / frame_size) + layout_nb_stream * 15000;
		} else if( this.bitrate_bps == Jopus_defines.OPUS_BITRATE_MAX )
		{
			total_rate = nb_channels * 320000;
		} else
		{
			total_rate = this.bitrate_bps;
		}

		/* Allocate equal number of bits to Ambisonic (uncoupled) and non-diegetic
		 * (coupled) streams */
		final int per_stream_rate = total_rate / layout_nb_stream;

		for( int i = 0; i < layout_nb_stream; i++ )
		{
			rate[i] = per_stream_rate;
		}
	}

	private final int rate_allocation(final int[] rate, final int frame_size)
	{
		final Object[] request = new Object[1];// java helper
		this.mEncoders[0].opus_encoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, request );
		final int Fs = ((Integer)request[0]).intValue();// java

		if( this.mapping_type == MAPPING_TYPE_AMBISONICS ) {
			ambisonics_rate_allocation( rate, frame_size, Fs );
		} else
		{
			surround_rate_allocation( rate, frame_size, Fs );
		}

		int rate_sum = 0;
		for( int i = 0, ie = this.layout.nb_streams; i < ie; i++ )
		{
			int r = rate[i];
			r = ( r >= 500 ? r : 500 );
			rate[i] = r;
			rate_sum += r;
		}
		return rate_sum;
	}

	/** Max size in case the encoder decides to return six frames (6 x 20 ms = 120 ms) */
	private static final int MS_FRAME_TMP = (6 * 1275 + 12);

	final int opus_multistream_encode_native
	(
		final Iopus_copy_channel_in copy_channel_in,
		final Object pcm, final int pcmoffset,// java
		final int analysis_frame_size,
		final byte[] data, int doffset,// java
		int max_data_bytes,
		final int lsb_depth,
		final Idownmix downmix,// final downmix_func downmix,
		final boolean float_api,
		final Object user_data
	)
	{
		// int doffset = 0;// java data[ doffset ]

		final int bitrates[] = new int[256];
		final float bandLogE[] = new float[42];
		// ALLOC_STACK;

		float[] mem = null;
		float[] preemphasis_mem = null;
		if( this.mapping_type == MAPPING_TYPE_SURROUND )
		{
			preemphasis_mem = this.preemph_mem;// ms_get_preemph_mem( st );
			mem = this.window_mem;// ms_get_window_mem( st );
		}

		// char *ptr = (char*)st + align( sizeof( JOpusMSEncoder ) );
		JOpusEncoder enc = this.mEncoders[0];
		final Object[] request = new Object[1];// java helper
		enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, request );
		final int Fs = ((Integer)request[0]).intValue();// java
		enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_VBR, request );
		final boolean vbr = ((Boolean)request[0]).booleanValue();// java
		enc.opus_encoder_ctl( Jcelt.CELT_GET_MODE, request );
		final JCELTMode celt_mode = (JCELTMode)request[0];// java

		final int frame_size = JOpusEncoder.frame_size_select( analysis_frame_size, this.variable_duration, Fs );
		if( frame_size <= 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final JChannelLayout ch_layout = this.layout;// java
		/* Smallest packet the encoder can produce. */
		int smallest_packet = (ch_layout.nb_streams << 1) - 1;
		/* 100 ms needs an extra byte per stream for the ToC. */
		if( Fs / frame_size == 10 ) {
			smallest_packet += this.layout.nb_streams;
		}
		if( max_data_bytes < smallest_packet )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
		}
		final float[] buf = new float[frame_size << 1];
		// coupled_size = opus_encoder_get_size( 2 );
		// mono_size = opus_encoder_get_size( 1 );

		final float[] bandSMR = new float[21 * ch_layout.nb_channels];
		if( this.mapping_type == MAPPING_TYPE_SURROUND )
		{
			surround_analysis( celt_mode, pcm, pcmoffset, bandSMR, mem, preemphasis_mem, frame_size, 120, ch_layout.nb_channels, Fs, copy_channel_in );//, st.arch );
		}

		/* Compute bitrate allocation between streams (this could be a lot better) */
		final int rate_sum = rate_allocation( bitrates, frame_size );

		if( ! vbr )
		{
			if( this.bitrate_bps == Jopus_defines.OPUS_AUTO )
			{
				final int v = 3 * rate_sum / (((3 * Fs) << 3) / frame_size);// java FIXME why need *3?
				max_data_bytes = max_data_bytes < v ? max_data_bytes : v;
			} else if( this.bitrate_bps != Jopus_defines.OPUS_BITRATE_MAX )
			{
				int v = 3 * this.bitrate_bps / (((3 * Fs) << 3) / frame_size);// java *3?
				v = smallest_packet >= v ? smallest_packet : v;
				max_data_bytes = max_data_bytes < v ? max_data_bytes : v;
			}
		}
		// ptr = (char*)st + align( sizeof( JOpusMSEncoder ) );
		for( int s = 0; s < ch_layout.nb_streams; s++ )
		{
			enc = this.mEncoders[s];
			/*if( s < st.layout.nb_coupled_streams ) {
				ptr += align( coupled_size );
			} else {
				ptr += align( mono_size );
			}*/
			enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrates[s] );
			if( this.mapping_type == MAPPING_TYPE_SURROUND )
			{
				int equiv_rate = this.bitrate_bps;
				if( frame_size * 50 < Fs ) {
					equiv_rate -= 60 * (Fs / frame_size - 50) * ch_layout.nb_channels;
				}
				if( equiv_rate > 10000 * ch_layout.nb_channels ) {
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_BANDWIDTH_FULLBAND );
				} else if( equiv_rate > 7000 * ch_layout.nb_channels ) {
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND );
				} else if( equiv_rate > 5000 * ch_layout.nb_channels ) {
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND );
				} else {
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND );
				}
				if( s < ch_layout.nb_coupled_streams )
				{
					/* To preserve the spatial image, force stereo CELT on coupled streams */
					enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_private.MODE_CELT_ONLY );
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, 2 );
				}
			}
 			else if( this.mapping_type == MAPPING_TYPE_AMBISONICS ) {
 				enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_private.MODE_CELT_ONLY );
 			}
		}

		final JOpusRepacketizer rp = new JOpusRepacketizer();
		/* Counting ToC */
		int tot_size = 0;
		final byte tmp_data[] = new byte[MS_FRAME_TMP];
		for( int s = 0; s < ch_layout.nb_streams; s++ )
		{
			rp.opus_repacketizer_init();
			enc = this.mEncoders[s];
			int c1, c2;
			if( s < ch_layout.nb_coupled_streams )
			{
				final int left = ch_layout.get_left_channel( s, -1 );
				final int right = ch_layout.get_right_channel( s, -1 );
				copy_channel_in.copy_channel_in( buf, 0, 2, pcm, pcmoffset, ch_layout.nb_channels, left, frame_size, user_data );
				copy_channel_in.copy_channel_in( buf, 1, 2, pcm, pcmoffset, ch_layout.nb_channels, right, frame_size, user_data );
				if( this.mapping_type == MAPPING_TYPE_SURROUND )
				{
					final int left21 = 21 * left;// java
					final int right21 = 21 * right;// java
					for( int i = 0; i < 21; i++ )
					{
						bandLogE[i] = bandSMR[left21 + i];
						bandLogE[21 + i] = bandSMR[right21 + i];
					}
				}
				c1 = left;
				c2 = right;
			} else {
				final int chan = ch_layout.get_mono_channel( s, -1 );
				copy_channel_in.copy_channel_in( buf, 0, 1, pcm, pcmoffset, ch_layout.nb_channels, chan, frame_size, user_data );
				if( this.mapping_type == MAPPING_TYPE_SURROUND )
				{
					final int chan21 = 21 * chan;// java
					for( int i = 0; i < 21; i++ ) {
						bandLogE[i] = bandSMR[chan21 + i];
					}
				}
				c1 = chan;
				c2 = -1;
			}
			if( this.mapping_type == MAPPING_TYPE_SURROUND ) {
				enc.opus_encoder_ctl( Jcelt.OPUS_SET_ENERGY_MASK, bandLogE );
			}
			/* number of bytes left (+Toc) */
			int curr_max = max_data_bytes - tot_size;
			/* Reserve one byte for the last stream and two for the others */
			int len = 2 * (ch_layout.nb_streams - s - 1) - 1;// java
			curr_max -= 0 >= len ? 0 : len;
			/* For 100 ms, reserve an extra byte per stream for the ToC */
			if( Fs / frame_size == 10 ) {
				curr_max -= this.layout.nb_streams - s - 1;
			}
			curr_max = curr_max < MS_FRAME_TMP ? curr_max : MS_FRAME_TMP;
			/* Repacketizer will add one or two bytes for self-delimited frames */
			if( s != ch_layout.nb_streams - 1 ) {
				curr_max -= curr_max > 253 ? 2 : 1;
			}
			if( ! vbr && s == ch_layout.nb_streams - 1 ) {
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, curr_max * ((Fs << 3) / frame_size) );
			}
			len = enc.opus_encode_native( buf, 0, frame_size, tmp_data, 0, curr_max, lsb_depth,
					pcm, pcmoffset, analysis_frame_size, c1, c2, ch_layout.nb_channels, downmix, float_api );
			if( len < 0 )
			{
				// RESTORE_STACK;
				return len;
			}
			/* We need to use the repacketizer to add the self-delimiting lengths
			   while taking into account the fact that the encoder can now return
			   more than one frame at a time (e.g. 60 ms CELT-only) */
			final int ret = rp.opus_repacketizer_cat( tmp_data, 0, len );
			/* If the opus_repacketizer_cat() fails, then something's seriously wrong
			 with the encoder. */
			if( ret != Jopus_defines.OPUS_OK )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}

			len = rp.opus_repacketizer_out_range_impl( 0, rp.opus_repacketizer_get_nb_frames(),
					data, doffset, max_data_bytes - tot_size, s != ch_layout.nb_streams - 1, ! vbr && s == ch_layout.nb_streams - 1 );
			doffset += len;
			tot_size += len;
		}
		/*printf("\n");*/
		// RESTORE_STACK;
		return tot_size;
	}
/*
// #if !defined( DISABLE_FLOAT_API )
	private static final void opus_copy_channel_in_float(
		final float[] dst,
		final int dst_stride,
		final float[] float_src,
		final int src_stride,
		int src_channel,
		int frame_size,
		Object user_data
		)
	{
		frame_size *= dst_stride;// java
		for( int i = 0; i < frame_size; i += dst_stride, src_channel += src_stride ) {
// #if defined(FIXED_POINT)
//			dst[i * dst_stride] = FLOAT2INT16( float_src[i * src_stride + src_channel] );
// #else
			dst[i] = float_src[src_channel];
// #endif
		}
	}
// #endif
*/
	private static final class Jopus_copy_channel_in_float extends Iopus_copy_channel_in {

		@Override
		final void copy_channel_in(final float[] dst, int doffset, final int dst_stride, final Object src, int scroffset, final int src_stride, final int src_channel, int frame_size, final Object user_data) {
			final float[] float_src = (float[]) src;
			frame_size *= dst_stride;// java
			frame_size += doffset;// java
			for( scroffset += src_channel; doffset < frame_size; doffset += dst_stride, scroffset += src_stride ) {
				dst[doffset] = float_src[scroffset];
			}
		}

	}
	private static final Jopus_copy_channel_in_float opus_copy_channel_in_float = new Jopus_copy_channel_in_float();
/*
	private static final void opus_copy_channel_in_short(
		final float[] dst,
		final int dst_stride,
		final short[] short_src,
		final int src_stride,
		int src_channel,
		int frame_size,
		Object user_data
	)
	{
		frame_size *= dst_stride;// java
		for( int i = 0; i < frame_size; i += dst_stride, src_channel += src_stride ) {
// #if defined( FIXED_POINT )
//			dst[i * dst_stride] = short_src[i * src_stride + src_channel];
// #else
			dst[i] = (1f / 32768.f) * short_src[src_channel];
// #endif
		}
	}
*/
	private static final class Jopus_copy_channel_in_short extends Iopus_copy_channel_in {

		@Override
		final void copy_channel_in(final float[] dst, int doffset, final int dst_stride, final Object src, int scroffset, final int src_stride, final int src_channel, int frame_size, final Object user_data) {
			final short[] short_src = (short[]) src;
			frame_size *= dst_stride;// java
			frame_size += doffset;// java
			for( scroffset += src_channel; doffset < frame_size; doffset += dst_stride, scroffset += src_stride ) {
				dst[doffset] = (1f / 32768.f) * short_src[scroffset];
			}
		}

	}
	private static final Jopus_copy_channel_in_short opus_copy_channel_in_short = new Jopus_copy_channel_in_short();


// #ifdef FIXED_POINT
	/** Encodes a multistream Opus frame.
	 * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state.
	 * @param pcm [in] <tt>const opus_int16*</tt>: The input signal as interleaved
	 *                                            samples.
	 *                                            This must contain
	 *                                            <code>frame_size*channels</code>
	 *                                            samples.
	 * @param frame_size <tt>int</tt>: Number of samples per channel in the input
	 *                                 signal.
	 *                                 This must be an Opus frame size for the
	 *                                 encoder's sampling rate.
	 *                                 For example, at 48 kHz the permitted values
	 *                                 are 120, 240, 480, 960, 1920, and 2880.
	 *                                 Passing in a duration of less than 10 ms
	 *                                 (480 samples at 48 kHz) will prevent the
	 *                                 encoder from using the LPC or hybrid modes.
	 * @param data [out] <tt>unsigned char*</tt>: Output payload.
	 *                                           This must contain storage for at
	 *                                           least \a max_data_bytes.
	 * @param max_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
	 *                                                 memory for the output
	 *                                                 payload. This may be
	 *                                                 used to impose an upper limit on
	 *                                                 the instant bitrate, but should
	 *                                                 not be used as the only bitrate
	 *                                                 control. Use #OPUS_SET_BITRATE to
	 *                                                 control the bitrate.
	 * @return The length of the encoded packet (in bytes) on success or a
	 *          negative error code (see @ref opus_errorcodes) on failure.
	 */
/*	private static final int opus_multistream_encode(
		final JOpusMSEncoder st,
		final short[] pcm,
		final int frame_size,
		unsigned char *data,
		int max_data_bytes
	)
	{
		return opus_multistream_encode_native( st, opus_copy_channel_in_short,
			pcm, frame_size, data, max_data_bytes, 16, downmix_int, 0, null );
	}

#ifndef DISABLE_FLOAT_API
	int opus_multistream_encode_float(
			final JOpusMSEncoder st,
			final float[] pcm,
			final int frame_size,
			unsigned char *data,
			int max_data_bytes
		)
	{
		return opus_multistream_encode_native( st, opus_copy_channel_in_float,
			pcm, frame_size, data, max_data_bytes, 16, downmix_float, 1, null );
	}
#endif

#else
*/
	/** Encodes a multistream Opus frame from floating point input.
	  * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state.
	  * @param pcm [in] <tt>const float*</tt>: The input signal as interleaved
	  *                                       samples with a normal range of
	  *                                       +/-1.0.
	  *                                       Samples with a range beyond +/-1.0
	  *                                       are supported but will be clipped by
	  *                                       decoders using the integer API and
	  *                                       should only be used if it is known
	  *                                       that the far end supports extended
	  *                                       dynamic range.
	  *                                       This must contain
	  *                                       <code>frame_size*channels</code>
	  *                                       samples.
	  * @param frame_size <tt>int</tt>: Number of samples per channel in the input
	  *                                 signal.
	  *                                 This must be an Opus frame size for the
	  *                                 encoder's sampling rate.
	  *                                 For example, at 48 kHz the permitted values
	  *                                 are 120, 240, 480, 960, 1920, and 2880.
	  *                                 Passing in a duration of less than 10 ms
	  *                                 (480 samples at 48 kHz) will prevent the
	  *                                 encoder from using the LPC or hybrid modes.
	  * @param data [out] <tt>unsigned char*</tt>: Output payload.
	  *                                           This must contain storage for at
	  *                                           least \a max_data_bytes.
	  * @param max_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
	  *                                                 memory for the output
	  *                                                 payload. This may be
	  *                                                 used to impose an upper limit on
	  *                                                 the instant bitrate, but should
	  *                                                 not be used as the only bitrate
	  *                                                 control. Use #OPUS_SET_BITRATE to
	  *                                                 control the bitrate.
	  * @return The length of the encoded packet (in bytes) on success or a
	  *          negative error code (see @ref opus_errorcodes) on failure.
	  */
	public final int opus_multistream_encode_float
	(
		final float[] pcm, final int poffset,
		final int frame_size,
		final byte[] data, final int doffset,
		final int max_data_bytes
		)
	{
		return opus_multistream_encode_native( opus_copy_channel_in_float,
			pcm, poffset, frame_size, data, doffset, max_data_bytes, 24, JOpusEncoder.downmix_float, true, null );
	}

	/** Encodes a multistream Opus frame.
	 * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state.
	 * @param pcm [in] <tt>const opus_int16*</tt>: The input signal as interleaved
	 *                                            samples.
	 *                                            This must contain
	 *                                            <code>frame_size*channels</code>
	 *                                            samples.
	 * @param pcmoffset [in] java an offset for the pcm
	 * @param frame_size <tt>int</tt>: Number of samples per channel in the input
	 *                                 signal.
	 *                                 This must be an Opus frame size for the
	 *                                 encoder's sampling rate.
	 *                                 For example, at 48 kHz the permitted values
	 *                                 are 120, 240, 480, 960, 1920, and 2880.
	 *                                 Passing in a duration of less than 10 ms
	 *                                 (480 samples at 48 kHz) will prevent the
	 *                                 encoder from using the LPC or hybrid modes.
	 * @param data [out] <tt>unsigned char*</tt>: Output payload.
	 *                                           This must contain storage for at
	 *                                           least \a max_data_bytes.
	 * @param max_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
	 *                                                 memory for the output
	 *                                                 payload. This may be
	 *                                                 used to impose an upper limit on
	 *                                                 the instant bitrate, but should
	 *                                                 not be used as the only bitrate
	 *                                                 control. Use #OPUS_SET_BITRATE to
	 *                                                 control the bitrate.
	 * @return The length of the encoded packet (in bytes) on success or a
	 *          negative error code (see @ref opus_errorcodes) on failure.
	 */
	public final int opus_multistream_encode(
		final short[] pcm, final int pcmoffset,// java
		final int frame_size,
		final byte[] data,
		final int max_data_bytes
	)
	{
		return opus_multistream_encode_native( opus_copy_channel_in_short,
			pcm, pcmoffset, frame_size, data, 0, max_data_bytes, 16, JOpusEncoder.downmix_int, false, null );
	}
// #endif

	// java: the same as opus_multistream_enccoder_ctl_va_list
	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	/** Perform a CTL function on a multistream Opus encoder.
	 *
	 * Getters
	 *
	 * Generally the request and subsequent arguments are generated by a
	 * convenience macro.
	 * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state.
	 * @param request This and all remaining parameters should be replaced by one
	 *                of the convenience macros in @ref opus_genericctls,
	 *                @ref opus_encoderctls, or @ref opus_multistream_ctls.
	 * @param arg the placeholder to get a data back
	 * @return status
	 * @see opus_genericctls
	 * @see opus_encoderctls
	 * @see opus_multistream_ctls
	 */
	public final int opus_multistream_encoder_ctl(final int request, final Object[] arg)
	{
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_GET_BITRATE_REQUEST:
			{
				final Object[] rate = new Object[1];
				final JOpusEncoder[] encs = this.mEncoders;// java
				int value = 0;
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					encs[s].opus_encoder_ctl( request, rate );
					value += ((Integer)rate[0]).intValue();
				}
				arg[0] = Integer.valueOf( value );
			}
			break;
		case Jopus_defines.OPUS_GET_LSB_DEPTH_REQUEST:
		case Jopus_defines.OPUS_GET_VBR_REQUEST:
		case Jopus_defines.OPUS_GET_APPLICATION_REQUEST:
		case Jopus_defines.OPUS_GET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_GET_COMPLEXITY_REQUEST:
		case Jopus_defines.OPUS_GET_PACKET_LOSS_PERC_REQUEST:
		case Jopus_defines.OPUS_GET_DTX_REQUEST:
		case Jopus_private.OPUS_GET_VOICE_RATIO_REQUEST:
		case Jopus_defines.OPUS_GET_VBR_CONSTRAINT_REQUEST:
		case Jopus_defines.OPUS_GET_SIGNAL_REQUEST:
		case Jopus_defines.OPUS_GET_LOOKAHEAD_REQUEST:
		case Jopus_defines.OPUS_GET_SAMPLE_RATE_REQUEST:
		case Jopus_defines.OPUS_GET_INBAND_FEC_REQUEST:
		case Jopus_defines.OPUS_GET_FORCE_CHANNELS_REQUEST:
		case Jopus_defines.OPUS_GET_PREDICTION_DISABLED_REQUEST:
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				/* For int32* GET params, just query the first stream */
				ret = this.mEncoders[0].opus_encoder_ctl( request, arg );
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				final Object[] tmp = new Object[1];
				final JOpusEncoder[] encs = this.mEncoders;// java
				long value = 0;
				for( int s = 0, nb_streams = this.layout.nb_streams; s < nb_streams; s++ )
				{
					ret = encs[s].opus_encoder_ctl( request, tmp );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
					value ^= ((Long)tmp[0]).longValue();
				}
				arg[0] = Long.valueOf( value );
			}
			break;
		case OPUS_MULTISTREAM_GET_ENCODER_STATE_REQUEST:
			{
				final int stream_id = ((Integer)arg[0]).intValue();
				if( stream_id < 0 || stream_id >= this.layout.nb_streams ) {
					// ret = Jopus_defines.OPUS_BAD_ARG;// FIXME why go on?
					return Jopus_defines.OPUS_BAD_ARG;// java changed
				}
				if( arg.length < 2 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				arg[1] = this.mEncoders[stream_id];
			}
			break;
		case Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION_REQUEST:
			{
				arg[0] = Integer.valueOf( this.variable_duration );
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param streamId
	 * @param arg
	 * @return status
	 */
	public final int opus_multistream_encoder_ctl(final int request, final int streamId, final Object[] arg)
	{
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case OPUS_MULTISTREAM_GET_ENCODER_STATE_REQUEST:
			{
				if( streamId < 0 || streamId >= this.layout.nb_streams ) {
					// ret = Jopus_defines.OPUS_BAD_ARG;// FIXME why go on?
					return Jopus_defines.OPUS_BAD_ARG;// java changed
				}
				arg[0] = this.mEncoders[ streamId ];
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_multistream_encoder_ctl(final int request, int value)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_BITRATE_REQUEST:
			{
				if( value != Jopus_defines.OPUS_AUTO && value != Jopus_defines.OPUS_BITRATE_MAX )
				{
					if( value <= 0 ) {
						return Jopus_defines.OPUS_BAD_ARG;
					}
					int v = 500 * this.layout.nb_channels;
					value = (v >= value ? v : value);
					v = 300000 * this.layout.nb_channels;
					value = (v <= value ? v : value);
				}
				this.bitrate_bps = value;
			}
			break;
		case Jopus_defines.OPUS_SET_LSB_DEPTH_REQUEST:
		case Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST:
		case Jopus_defines.OPUS_SET_MAX_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_SIGNAL_REQUEST:
		case Jopus_defines.OPUS_SET_APPLICATION_REQUEST:
		case Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST:
		case Jopus_private.OPUS_SET_FORCE_MODE_REQUEST:
		case Jopus_defines.OPUS_SET_FORCE_CHANNELS_REQUEST:
			{
				/* This works for int32 params */
				for( int s = 0; s < this.layout.nb_streams; s++ )
				{
					ret = this.mEncoders[s].opus_encoder_ctl( request, value );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		case Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION_REQUEST:
			{
				this.variable_duration = value;
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_multistream_encoder_ctl(final int request, final boolean value)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_VBR_REQUEST:
		case Jopus_defines.OPUS_SET_VBR_CONSTRAINT_REQUEST:
		case Jopus_defines.OPUS_SET_INBAND_FEC_REQUEST:
		case Jopus_defines.OPUS_SET_DTX_REQUEST:
		case Jopus_defines.OPUS_SET_PREDICTION_DISABLED_REQUEST:
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				for( int s = 0; s < this.layout.nb_streams; s++ )
				{
					ret = this.mEncoders[s].opus_encoder_ctl( request, value );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return status
	 */
	public final int opus_multistream_encoder_ctl(final int request)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
				if( this.mapping_type == MAPPING_TYPE_SURROUND )
				{
					float[] fbuff = ms_get_preemph_mem();
					for( int i = this.layout.nb_channels; i > 0; ) {
						fbuff[--i] = 0;
					}
					fbuff = ms_get_window_mem();
					for( int i = this.layout.nb_channels * 120; i > 0; ) {
						fbuff[--i] = 0;
					}
				}
				for( int s = 0; s < this.layout.nb_streams; s++ )
				{
					ret = this.mEncoders[s].opus_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );
					if( ret != Jopus_defines.OPUS_OK ) {
						break;
					}
				}
			}
			break;
		default:
			// System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}

		return ret;
	}
	/** Frees an <code>OpusMSEncoder</code> allocated by
	 * opus_multistream_encoder_create().
	 * @param st <tt>OpusMSEncoder*</tt>: Multistream encoder state to be freed.
	 */
	/*private static final void opus_multistream_encoder_destroy(OpusMSEncoder *st)
	{// java: don't need. use st = null;
		opus_free( st );
	}*/

}