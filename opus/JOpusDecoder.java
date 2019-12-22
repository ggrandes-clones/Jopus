package opus;
/* Copyright ( c) 2010 Xiph.Org Foundation, Skype Limited
Written by Jean-Marc Valin and Koen Vos */

import celt.JCELTDecoder;
import celt.JCELTMode;
import celt.Jcelt;
import celt.Jec_dec;
import celt.Jfloat_cast;
import silk.Jsilk_DecControlStruct;
import silk.Jsilk_decoder;

// opus_decoder.c
//FIXME where is define for maximum channel count?
//FIXME why no method to check if sample rate valid? no method for channel count validation?
/** @defgroup opus_decoder Opus Decoder
  * @{
  *
  * @brief This page describes the process and functions used to decode Opus.
  *
  * The decoding process also starts with creating a decoder
  * state. This can be done with:
  * @code
  * int error;
  * OpusDecoder *dec;
  * dec = opus_decoder_create( Fs, channels, &error );
  * @endcode
  * where
  * @li Fs is the sampling rate and must be 8000, 12000, 16000, 24000, or 48000
  * @li channels is the number of channels ( 1 or 2)
  * @li error will hold the error code in case of failure ( or #OPUS_OK on success)
  * @li the return value is a newly created decoder state to be used for decoding
  *
  * While opus_decoder_create( ) allocates memory for the state, it's also possible
  * to initialize pre-allocated memory:
  * @code
  * int size;
  * int error;
  * OpusDecoder *dec;
  * size = opus_decoder_get_size( channels );
  * dec = malloc( size );
  * error = opus_decoder_init( dec, Fs, channels );
  * @endcode
  * where opus_decoder_get_size( ) returns the required size for the decoder state. Note that
  * future versions of this code may change the size, so no assuptions should be made about it.
  *
  * The decoder state is always continuous in memory and only a shallow copy is sufficient
  * to copy it ( e.g. memcpy( ))
  *
  * To decode a frame, opus_decode( ) or opus_decode_float( ) must be called with a packet of compressed audio data:
  * @code
  * frame_size = opus_decode( dec, packet, len, decoded, max_size, 0 );
  * @endcode
  * where
  *
  * @li packet is the byte array containing the compressed data
  * @li len is the exact number of bytes contained in the packet
  * @li decoded is the decoded audio data in opus_int16 ( or float for opus_decode_float( ))
  * @li max_size is the max duration of the frame in samples ( per channel) that can fit into the decoded_frame array
  *
  * opus_decode( ) and opus_decode_float( ) return the number of samples ( per channel) decoded from the packet.
  * If that value is negative, then an error has occurred. This can occur if the packet is corrupted or if the audio
  * buffer is too small to hold the decoded audio.
  *
  * Opus is a stateful codec with overlapping blocks and as a result Opus
  * packets are not coded independently of each other. Packets must be
  * passed into the decoder serially and in the correct order for a correct
  * decode. Lost packets can be replaced with loss concealment by calling
  * the decoder with a null pointer and zero length for the missing packet.
  *
  * A single codec state may only be accessed from a single thread at
  * a time and any required locking must be performed by the caller. Separate
  * streams must be decoded with separate decoder states and can be decoded
  * in parallel unless the library was compiled with NONTHREADSAFE_PSEUDOSTACK
  * defined.
  *
  *
  */
public final class JOpusDecoder {
	// private static final String CLASS_NAME = "JOpusDecoder";

/* Opus decoder state.
 * This contains the complete state of an Opus decoder.
 * It is position independent and can be freely copied.
 * @see #opus_decoder_create, @see #opus_decoder_init
 */
//struct OpusDecoder {
	//private int celt_dec_offset;// java changed to JCELTDecoder celt_dec = new JCELTDecoder();
	//private int silk_dec_offset;// java changed to Jsilk_decoder silk_dec = new Jsilk_decoder();
	private Jsilk_decoder silk_dec = null;// new Jsilk_decoder();// java added
	private JCELTDecoder celt_dec = null;// new JCELTDecoder();// java added
	private int channels;
	/** Sampling rate ( at the API level) */
	private int Fs;
	private final Jsilk_DecControlStruct DecControl = new Jsilk_DecControlStruct();
	private int decode_gain;
	//private int arch;

	/* Everything beyond this point gets cleared on a reset */
	//#define OPUS_DECODER_RESET_START stream_channels
	private int stream_channels;

	private int bandwidth;
	private int mode;
	private int prev_mode;
	private int frame_size;
	private boolean prev_redundancy;
	private int last_packet_duration;
//if( ! FIXED_POINT ) {
	private final float softclip_mem[] = new float[2];
//}
	private long rangeFinal;// opus_uint32
//}
// end of struct
	//
	/**
	 * default constructor
	 */
	public JOpusDecoder() {
	}
	/**
	 * java. to replace code:
	 * <pre>
	 * int decsize = opus_decoder_get_size( 1 );
	 * OpusDecoder *dec = (OpusDecoder*)malloc( decsize );
	 * </pre>
	 * @param channels
	 * @throws IllegalArgumentException
	 */
	public JOpusDecoder(final int channels) throws IllegalArgumentException {
		if( channels < 1 || channels > 2 ) {
			throw new IllegalArgumentException();
		}
		silk_dec = new Jsilk_decoder();
		celt_dec = new JCELTDecoder( channels );
	}
	/**
	 * java version for c memset
	 * @param isFull
	 */
	public final void clear(final boolean isFull) {
		if( isFull ) {
			silk_dec = null;
			celt_dec = null;
			channels = 0;
			Fs = 0;
			DecControl.clear();
			decode_gain = 0;
		}
		stream_channels = 0;
		bandwidth = 0;
		mode = 0;
		prev_mode = 0;
		frame_size = 0;
		prev_redundancy = false;
		last_packet_duration = 0;
		softclip_mem[0] = 0; softclip_mem[1] = 0;
		rangeFinal = 0;
	}

	/**
	 * java: memcpy to test
	 * @param decoder source
	 */
	public final void copyFrom(final JOpusDecoder decoder) {
		//celt_dec_offset = decoder.celt_dec_offset;
		//silk_dec_offset = decoder.silk_dec_offset;
		silk_dec.copyFrom( decoder.silk_dec );
		celt_dec.copyFrom( decoder.celt_dec );
		channels = decoder.channels;
		Fs = decoder.Fs;
		DecControl.copyFrom( decoder.DecControl );
		decode_gain = decoder.decode_gain;
		//arch = decoder.arch;
		stream_channels = decoder.stream_channels;
		bandwidth = decoder.bandwidth;
		mode = decoder.mode;
		prev_mode = decoder.prev_mode;
		frame_size = decoder.frame_size;
		prev_redundancy = decoder.prev_redundancy;
		last_packet_duration = decoder.last_packet_duration;
		System.arraycopy( decoder.softclip_mem, 0, softclip_mem, 0, softclip_mem.length );
		rangeFinal = decoder.rangeFinal;
	}

/* #if defined(ENABLE_HARDENING) || defined(ENABLE_ASSERTIONS)
	private void validate_opus_decoder(OpusDecoder *st)
	{
		celt_assert(st->channels == 1 || st->channels == 2);
		celt_assert(st->Fs == 48000 || st->Fs == 24000 || st->Fs == 16000 || st->Fs == 12000 || st->Fs == 8000);
		celt_assert(st->DecControl.API_sampleRate == st->Fs);
		celt_assert(st->DecControl.internalSampleRate == 0 || st->DecControl.internalSampleRate == 16000 || st->DecControl.internalSampleRate == 12000 || st->DecControl.internalSampleRate == 8000);
		celt_assert(st->DecControl.nChannelsAPI == st->channels);
		celt_assert(st->DecControl.nChannelsInternal == 0 || st->DecControl.nChannelsInternal == 1 || st->DecControl.nChannelsInternal == 2);
		celt_assert(st->DecControl.payloadSize_ms == 0 || st->DecControl.payloadSize_ms == 10 || st->DecControl.payloadSize_ms == 20 || st->DecControl.payloadSize_ms == 40 || st->DecControl.payloadSize_ms == 60);
#ifdef OPUS_ARCHMASK
		celt_assert(st->arch >= 0);
		celt_assert(st->arch <= OPUS_ARCHMASK);
#endif
		celt_assert(st->stream_channels == 1 || st->stream_channels == 2);
	}
#define VALIDATE_OPUS_DECODER(st) validate_opus_decoder(st)
#else
#define VALIDATE_OPUS_DECODER(st)
#endif */

	/** Gets the size of an <code>OpusDecoder</code> structure.
	  * @param [in] channels <tt>int</tt>: Number of channels.
	  *                                    This must be 1 or 2.
	  * @returns The size in bytes.
	  */
	/*public static final int opus_decoder_get_size(final int channels)
	{
		int silkDecSizeBytes, celtDecSizeBytes;
		int ret;
		if( channels < 1 || channels > 2 ) {
			return 0;
		}
		ret = silk_Get_Decoder_Size( &silkDecSizeBytes );
		if( ret ) {
			return 0;
		}
		silkDecSizeBytes = align( silkDecSizeBytes );
		celtDecSizeBytes = celt_decoder_get_size( channels );
		return align( sizeof( OpusDecoder ) ) + silkDecSizeBytes + celtDecSizeBytes;
	}*/

	/** Initializes a previously allocated decoder state.
	 * The state must be at least the size returned by opus_decoder_get_size( ).
	 * This is intended for applications which use their own allocator instead of malloc. @see opus_decoder_create,opus_decoder_get_size
	 * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	 * @param st [in] <tt>OpusDecoder*</tt>: Decoder state.
	 * @param Fsr [in] <tt>opus_int32</tt>: Sampling rate to decode to ( Hz).
	 * This must be one of 8000, 12000, 16000,
	 * 24000, or 48000.
	 * @param nchannels [in] <tt>int</tt>: Number of channels ( 1 or 2) to decode
	 * @return #OPUS_OK Success or @ref opus_errorcodes
	 */
	public final int opus_decoder_init(final int Fsr, final int nchannels)
	{
		//int[] silkDecSizeBytes;

		if( (Fsr != 48000 && Fsr != 24000 && Fsr != 16000 && Fsr != 12000 && Fsr != 8000)
				|| (nchannels != 1 && nchannels != 2) ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		//OPUS_CLEAR( ( char*)st, opus_decoder_get_size( channels ) );
		/* Initialize SILK decoder */
		//ret = Jdec_API.silk_Get_Decoder_Size( &silkDecSizeBytes );
		//if( 0 != ret ) {
		//	return Jopus_defines.OPUS_INTERNAL_ERROR;
		//}

		//silkDecSizeBytes = align( silkDecSizeBytes );
		//st.silk_dec_offset = align( sizeof( OpusDecoder) );
		//st.celt_dec_offset = st.silk_dec_offset + silkDecSizeBytes;
		//silk_dec = (char*)st + st.silk_dec_offset;
		//celt_dec = (JCELTDecoder)( (char*)st + st.celt_dec_offset );
		this.silk_dec = new Jsilk_decoder();// java added
		this.celt_dec = new JCELTDecoder();// java added
		this.stream_channels = this.channels = nchannels;

		this.Fs = Fsr;
		this.DecControl.API_sampleRate = this.Fs;
		this.DecControl.nChannelsAPI = this.channels;

		/* Reset decoder */
		int ret = this.silk_dec.silk_InitDecoder();
		if( 0 != ret ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		/* Initialize CELT decoder */
		ret = this.celt_dec.celt_decoder_init( Fsr, nchannels );
		if( ret != Jopus_defines.OPUS_OK ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		this.celt_dec.celt_decoder_ctl( Jcelt.CELT_SET_SIGNALLING, false );

		this.prev_mode = 0;
		this.frame_size = Fsr / 400;
		//st.arch = opus_select_arch();
		return Jopus_defines.OPUS_OK;
	}

	/** Allocates and initializes a decoder state.
	 * @param Fs [in] <tt>opus_int32</tt>: Sample rate to decode at ( Hz).
	 * This must be one of 8000, 12000, 16000,
	 * 24000, or 48000.
	 * @param channels [in] <tt>int</tt>: Number of channels ( 1 or 2) to decode
	 * @param error [out] <tt>int*</tt>: #OPUS_OK Success or @ref opus_errorcodes
	 * @return a JOpusDecoder object
	 *
	 * Internally Opus stores data at 48000 Hz, so that should be the default
	 * value for Fs. However, the decoder can efficiently decode to buffers
	 * at 8, 12, 16, and 24 kHz so if for some reason the caller cannot use
	 * data at the full sample rate, or knows the compressed data doesn't
	 * use the full frequency range, it can request decoding at a reduced
	 * rate. Likewise, the decoder is capable of filling in either mono or
	 * interleaved stereo pcm buffers, at the caller's request.
	 */
	public static final JOpusDecoder opus_decoder_create(final int Fs, final int channels, final int[] error)
	{
		if( (Fs != 48000 && Fs != 24000 && Fs != 16000 && Fs != 12000 && Fs != 8000)
				|| (channels != 1 && channels != 2) )
		{
			if( error != null ) {
				error[0] = Jopus_defines.OPUS_BAD_ARG;
			}
			return null;
		}
		JOpusDecoder st = new JOpusDecoder();
		/* if( st == null )
		{
			if( error != null ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		final int ret = st.opus_decoder_init( Fs, channels );
		if( error != null ) {
			error[0] = ret;
		}
		if( ret != Jopus_defines.OPUS_OK )
		{
			st = null;
		}
		return st;
	}

	private static final void smooth_fade(final float[] in1, final int inoffset1,// java
			final float[] in2, final int inoffset2,// java
			final float[] out, final int outoffset,// java
			int overlap, final int channels,
			final float[] window, final int Fs)
	{
		final int inc = 48000 / Fs;
		overlap *= inc;
		for( int c = 0; c < channels; c++ )
		{
			for( int i = 0, ic = c; i < overlap; i += inc, ic += channels )
			{
				float w = window[i];// java
				w *= w;
				out[outoffset + ic] = w * in2[inoffset2 + ic] + (Jfloat_cast.Q15ONE - w) * in1[inoffset1 + ic];
			}
		}
	}

	private static final int opus_packet_get_mode(final byte[] data, final int doffset)
	{// java doffset is added
		final int d = (int)data[doffset];// java
		if( 0 != (d & 0x80) )
		{
			return Jopus_private.MODE_CELT_ONLY;
		}
		if( (d & 0x60) == 0x60 )
		{
			return Jopus_private.MODE_HYBRID;
		}
		return Jopus_private.MODE_SILK_ONLY;
	}

	private final int opus_decode_frame(
								byte[] data, final int doffset,// java
								int len,
								final float[] pcm, int pcmoffset,// java
								int frameSize, final int decode_fec)
	{
		// ALLOC_STACK;

		final int F20 = this.Fs / 50;
		final int F10 = F20 >> 1;
		final int F5 = F10 >> 1;
		final int F2_5 = F5 >> 1;
		if( frameSize < F2_5 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
		}
		/* Limit frame_size to avoid excessive stack allocations. */
		int v = this.Fs / 25 * 3;// java
		frameSize = frameSize < v ? frameSize : v;
		/* Payloads of 1 ( 2 including ToC) or 0 trigger the PLC/DTX */
		if( len <= 1 )
		{
			data = null;
			/* In that case, don't conceal more than what the ToC says */
			frameSize = frameSize < this.frame_size ? frameSize : this.frame_size;
		}
		final int nchannels = this.channels;// java
		final Jec_dec dec = new Jec_dec();
		int audiosize;
		int dec_mode;// renamed
		int dec_bandwidth;// renamed
		if( data != null )
		{
			audiosize = this.frame_size;
			dec_mode = this.mode;
			dec_bandwidth = this.bandwidth;
			dec.ec_dec_init( data, doffset, len );
		} else {
			audiosize = frameSize;
			dec_mode = this.prev_mode;
			dec_bandwidth = 0;

			if( dec_mode == 0 )
			{
				/* If we haven't got any packet yet, all we can do is return zeros */
				for( int i = pcmoffset, end = pcmoffset + audiosize * nchannels; i < end; i++ ) {
					pcm[i] = 0;
				}
				// RESTORE_STACK;
				return audiosize;
			}

			/* Avoids trying to run the PLC on sizes other than 2.5 ( CELT), 5 ( CELT),
			   10, or 20 ( e.g. 12.5 or 30 ms). */
			if( audiosize > F20 )
			{
				do {
					final int ret = opus_decode_frame( null, 0, 0, pcm, pcmoffset, audiosize < F20 ? audiosize : F20, 0 );
					if( ret < 0 )
					{
						// RESTORE_STACK;
						return ret;
					}
					pcmoffset += ret * nchannels;
					audiosize -= ret;
				} while( audiosize > 0 );
				// RESTORE_STACK;
				return frameSize;
			} else if( audiosize < F20 )
			{
				if( audiosize > F10 ) {
					audiosize = F10;
				} else if( dec_mode != Jopus_private.MODE_SILK_ONLY && audiosize > F5 && audiosize < F10 ) {
					audiosize = F5;
				}
			}
		}

		/* In fixed-point, we can tell CELT to do the accumulation on top of the
		   SILK PCM buffer. This saves some stack space. */
/* #ifdef FIXED_POINT
		celt_accum = (mode != MODE_CELT_ONLY) && (frame_size >= F10);
#else */
		final boolean celt_accum = false;
// #endif

		boolean transition = false;
		int pcm_transition_silk_size = 0;
		int pcm_transition_celt_size = 0;
		if( data != null && this.prev_mode > 0 && (
				(dec_mode == Jopus_private.MODE_CELT_ONLY && this.prev_mode != Jopus_private.MODE_CELT_ONLY && ! this.prev_redundancy)
				|| (dec_mode != Jopus_private.MODE_CELT_ONLY && this.prev_mode == Jopus_private.MODE_CELT_ONLY) )
			)
		{
			transition = true;
			/* Decide where to allocate the stack memory for pcm_transition */
			if( dec_mode == Jopus_private.MODE_CELT_ONLY ) {
				pcm_transition_celt_size = F5 * nchannels;
			} else {
				pcm_transition_silk_size = F5 * nchannels;
			}
		}
		final float[] pcm_transition_celt = new float[pcm_transition_celt_size];
		float[] pcm_transition = pcm_transition_celt;// null;// java to supress warning "Potential null pointer access"
		if( transition && dec_mode == Jopus_private.MODE_CELT_ONLY )
		{
			pcm_transition = pcm_transition_celt;
			opus_decode_frame( null, 0, 0, pcm_transition, 0, F5 < audiosize ? F5 : audiosize, 0 );
		}
		if( audiosize > frameSize )
		{
			/*fprintf( stderr, "PCM buffer too small: %d vs %d ( mode = %d)\n", audiosize, frame_size, mode );*/
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		} else {
			frameSize = audiosize;
		}

		/* Don't allocate any memory when in CELT-only mode */
		final int pcm_silk_size = (dec_mode != Jopus_private.MODE_CELT_ONLY && ! celt_accum) ? ( F10 > frameSize ? F10 : frameSize ) * nchannels : 0;
		final short[] pcm_silk = new short[pcm_silk_size];

		final Jsilk_decoder silk_decoder = this.silk_dec;// (char*)st + st.silk_dec_offset;
		/* SILK processing */
		if( dec_mode != Jopus_private.MODE_CELT_ONLY )
		{
/* #ifdef FIXED_POINT
			if( celt_accum )
				pcm_ptr = pcm;
			else
#endif */
			int pcm_ptr = 0;// pcm_silk[ pcm_ptr ]

			if( this.prev_mode == Jopus_private.MODE_CELT_ONLY ) {
				silk_decoder.silk_InitDecoder();
			}

			/* The SILK PLC cannot produce frames of less than 10 ms */
			v = 1000 * audiosize / this.Fs;// java
			this.DecControl.payloadSize_ms = 10 >= v ? 10 : v;

			if( data != null )
			{
				this.DecControl.nChannelsInternal = this.stream_channels;
				if( dec_mode == Jopus_private.MODE_SILK_ONLY ) {
					if( dec_bandwidth == Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
						this.DecControl.internalSampleRate = 8000;
					} else if( dec_bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
						this.DecControl.internalSampleRate = 12000;
					} else if( dec_bandwidth == Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
						this.DecControl.internalSampleRate = 16000;
					} else {
						this.DecControl.internalSampleRate = 16000;
						// celt_assert( 0 );
					}
				} else {
					/* Hybrid mode */
					this.DecControl.internalSampleRate = 16000;
				}
			}

			final int lost_flag = data == null ? 1 : (decode_fec << 1);
			int decoded_samples = 0;
			final int[] silk_frame_size_ptr = new int[1];// java TODO return the value
			do {
				/* Call SILK decoder */
				final boolean first_frame = decoded_samples == 0;
				final int silk_ret = silk_decoder.silk_Decode( this.DecControl,
							lost_flag, first_frame, dec, pcm_silk, pcm_ptr, silk_frame_size_ptr );//, st.arch );
				int silk_frame_size = silk_frame_size_ptr[0];// java
				if( 0 != silk_ret ) {
					if( 0 != lost_flag ) {
						/* PLC failure should not be fatal */
						silk_frame_size = frameSize;
						for( int i = pcm_ptr, end = pcm_ptr + frameSize * nchannels; i < end; i++ ) {
							pcm_silk[i] = 0;
						}
					} else {
						// RESTORE_STACK;
						return Jopus_defines.OPUS_INTERNAL_ERROR;
					}
				}
				pcm_ptr += silk_frame_size * nchannels;
				decoded_samples += silk_frame_size;
			} while( decoded_samples < frameSize );
		}

		boolean celt_to_silk = false;
		boolean redundancy = false;
		int redundancy_bytes = 0;
		int start_band = 0;
		if( 0 == decode_fec && dec_mode != Jopus_private.MODE_CELT_ONLY && data != null
				&& dec.ec_tell() + 17 + (this.mode == Jopus_private.MODE_HYBRID ? 20 : 0) <= (len << 3) )
		{
			/* Check if we have a redundant 0-8 kHz band */
			redundancy = dec_mode == Jopus_private.MODE_HYBRID ?
					dec.ec_dec_bit_logp( 12 )
					:
					true;
			if( redundancy )
			{
				celt_to_silk = dec.ec_dec_bit_logp( 1 );
				/* redundancy_bytes will be at least two, in the non-hybrid
				   case due to the ec_tell( ) check above */
				redundancy_bytes = dec_mode == Jopus_private.MODE_HYBRID ?
							dec.ec_dec_uint( 256 ) + 2 :
							len - ((dec.ec_tell() + 7) >> 3);
				len -= redundancy_bytes;
				/* This is a sanity check. It should never happen for a valid
				   packet, so the exact behaviour is not normative. */
				if( (len << 3) < dec.ec_tell() )
				{
					len = 0;
					redundancy_bytes = 0;
					redundancy = false;
				}
				/* Shrink decoder because of raw bits */
				dec.storage -= redundancy_bytes;
			}
		}
		if( dec_mode != Jopus_private.MODE_CELT_ONLY ) {
			start_band = 17;
		}

		final JCELTDecoder celt_decoder = this.celt_dec;// (JCELTDecoder)(( char*)st + st.celt_dec_offset);
		if( redundancy )
		{
			transition = false;
			pcm_transition_silk_size = 0;// ALLOC_NONE;
		}

		final float pcm_transition_silk[] = new float[ pcm_transition_silk_size ];

		if( transition && mode != Jopus_private.MODE_CELT_ONLY )
		{
			pcm_transition = pcm_transition_silk;
			opus_decode_frame( null, 0, 0, pcm_transition, 0, (F5 <= audiosize ? F5 : audiosize), 0 );
		}

		if( 0 != dec_bandwidth )
		{
			int endband = 21;

			switch( dec_bandwidth )
			{
			case Jopus_defines.OPUS_BANDWIDTH_NARROWBAND:
				endband = 13;
				break;
			case Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND:
			case Jopus_defines.OPUS_BANDWIDTH_WIDEBAND:
				endband = 17;
				break;
			case Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND:
				endband = 19;
				break;
			case Jopus_defines.OPUS_BANDWIDTH_FULLBAND:
				endband = 21;
				break;
			//default:
				// celt_assert(0);
			//	break;
			}
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_END_BAND, endband ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
		}
		if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_CHANNELS, this.stream_channels ) ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		/* Only allocation memory for redundancy if/when needed */
		final int redundant_audio_size = redundancy ? F5 * nchannels : 0;
		final float[] redundant_audio = new float[redundant_audio_size];

		final Object[] request = new Object[1];// java helper for calling decoder ctl
		/* 5 ms redundant frame for CELT.SILK*/
		long redundant_rng = 0;
		if( redundancy && celt_to_silk )
		{
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_START_BAND, 0 ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			celt_decoder.celt_decode_with_ec( data, doffset + len, redundancy_bytes, redundant_audio, 0, F5, null, false );
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			redundant_rng = ((Long)request[0]).longValue();// java
		}

		/* MUST be after PLC */
		celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_START_BAND, start_band );

		int celt_ret = 0;
		if( dec_mode != Jopus_private.MODE_SILK_ONLY )
		{
			final int celt_frame_size = F20 < frameSize ? F20 : frameSize;
			/* Make sure to discard any previous CELT state */
			if( dec_mode != this.prev_mode && this.prev_mode > 0 && ! this.prev_redundancy ) {
				if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) ) {
					return Jopus_defines.OPUS_INTERNAL_ERROR;
				}
			}
			/* Decode CELT */
			celt_ret = celt_decoder.celt_decode_with_ec( decode_fec != 0 ? null : data, doffset,
										len, pcm, pcmoffset, celt_frame_size, dec, celt_accum );
		} else {
			if( ! celt_accum )
			{
				for( int i = pcmoffset, end = pcmoffset + frameSize * nchannels; i < end; i++ ) {
					pcm[i] = 0;
				}
			}
			/* For hybrid . SILK transitions, we let the CELT MDCT
			   do a fade-out by decoding a silence frame */
			if( this.prev_mode == Jopus_private.MODE_HYBRID && !( redundancy && celt_to_silk && this.prev_redundancy) )
			{
				if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_START_BAND, 0 ) ) {
					return Jopus_defines.OPUS_INTERNAL_ERROR;
				}
				final byte silence[/* 2 */] = { -1, -1 };//{ 0xFF, 0xFF };
				celt_decoder.celt_decode_with_ec( silence, 0, 2, pcm, pcmoffset, F2_5, null, celt_accum );
			}
		}

		if( dec_mode != Jopus_private.MODE_CELT_ONLY && ! celt_accum )
		{
/* #ifdef FIXED_POINT
			for( i = 0; i < frame_size * st.channels; i++ )
				pcm[i] = SAT16( ADD32( pcm[i], pcm_silk[i] ) );
#else */
			for( int i = 0, end = frameSize * nchannels, pcm_i = pcmoffset; i < end; i++ ) {
				pcm[pcm_i++] += (1.f / 32768.f) * pcm_silk[i];
			}
// #endif
		}

		float[] window;
		{
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl(Jcelt.CELT_GET_MODE, request ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			window = ((JCELTMode)request[0]).window;
		}

		/* 5 ms redundant frame for SILK.CELT */
		if( redundancy && ! celt_to_silk )
		{
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jcelt.CELT_SET_START_BAND, 0 ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}

			celt_decoder.celt_decode_with_ec( data, doffset + len, redundancy_bytes, redundant_audio, 0, F5, null, false );
			if( Jopus_defines.OPUS_OK != celt_decoder.celt_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) ) {
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			redundant_rng = ((Long)request[0]).longValue();
			v = pcmoffset + nchannels * (frameSize - F2_5);// java
			smooth_fade( pcm, v, redundant_audio, nchannels * F2_5, pcm, v, F2_5, nchannels, window, this.Fs );
		}
		if( redundancy && celt_to_silk )
		{
			final int fe = F2_5 * nchannels;// java
			for( int c = 0; c < nchannels; c++ )
			{
				for( int i = 0; i < fe; i += nchannels ) {
					v = i + c;// java
					pcm[pcmoffset + v] = redundant_audio[v];
				}
			}
			final int c = nchannels * F2_5;// java
			v = pcmoffset + c;// java
			smooth_fade( redundant_audio, c, pcm, v, pcm, v, F2_5, nchannels, window, this.Fs );
		}
		if( transition )
		{
			if( audiosize >= F5 )
			{
				for( int i = 0, end = nchannels * F2_5, pcm_i = pcmoffset; i < end; i++ ) {
					pcm[pcm_i++] = pcm_transition[i];// FIXME potential null access
				}
				final int c = nchannels * F2_5;// java
				v = pcmoffset + nchannels * F2_5;// java
				smooth_fade( pcm_transition, c, pcm, v, pcm, v, F2_5, nchannels, window, this.Fs );
			} else {
				/* Not enough time to do a clean transition, but we do it anyway
				   This will not preserve amplitude perfectly and may introduce
				   a bit of temporal aliasing, but it shouldn't be too bad and
				   that's pretty much the best we can do. In any case, generating this
				   transition it pretty silly in the first place */
				smooth_fade( pcm_transition, 0, pcm, pcmoffset, pcm, pcmoffset, F2_5, nchannels, window, this.Fs );
			}
		}

		if( 0 != this.decode_gain )
		{
			final float gain = (float)Math.exp( 0.6931471805599453094 * (6.48814081e-4 * (double)this.decode_gain) );
			for( int i = pcmoffset, end = pcmoffset + frameSize * nchannels; i < end; i++ )
			{
				pcm[i] *= gain;
			}
		}

		this.rangeFinal = len <= 1 ?
							0
							:
							dec.rng ^ redundant_rng;

		this.prev_mode = dec_mode;
		this.prev_redundancy = redundancy && ! celt_to_silk;

		/* if( celt_ret >= 0 )
		{
			if( OPUS_CHECK_ARRAY( pcm, audiosize * st.channels ) ) {
				OPUS_PRINT_INT( audiosize );
			}
		} */

		// RESTORE_STACK;
		return celt_ret < 0 ? celt_ret : audiosize;

	}

	/**
	 * java changed: packet_offset replaced by Jopus_packet_data_aux
	 *
	 * @param data
	 * @param doffset
	 * @param len
	 * @param pcm
	 * @param frameSize
	 * @param decode_fec
	 * @param self_delimited
	 * @param aux
	 * @param soft_clip
	 * @return
	 */
	int opus_decode_native(
			final byte[] data, int doffset,// java
			final int len, final float[] pcm, final int frameSize, final boolean decode_fec,
			final boolean self_delimited,
			// final int[] packet_offset,
			Jopus_packet_data_aux aux,// java
			final boolean soft_clip)
	{
		// VALIDATE_OPUS_DECODER(st);
		/* if( decode_fec < 0 || decode_fec > 1 ) {// java changed to boolean
			return Jopus_defines.OPUS_BAD_ARG;
		}*/
		/* For FEC/PLC, frame_size has to be to have a multiple of 2.5 ms */
		if( (decode_fec || len == 0 || data == null) && frameSize % (this.Fs / 400) != 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( len == 0 || data == null )
		{
			int pcm_count = 0;
			do {
				final int ret = opus_decode_frame( null, 0, 0, pcm, pcm_count * this.channels, frameSize - pcm_count, 0 );
				if( ret < 0 ) {
					return ret;
				}
				pcm_count += ret;
			} while( pcm_count < frameSize );
			// celt_assert( pcm_count == frame_size );
			/* if( OPUS_CHECK_ARRAY( pcm, pcm_count * st.channels ) ) {
				OPUS_PRINT_INT( pcm_count );
			} */
			this.last_packet_duration = pcm_count;
			return pcm_count;
		} else if( len < 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final int packet_mode = opus_packet_get_mode( data, doffset );
		final int packet_bandwidth = opus_packet_get_bandwidth( data, doffset );
		final int packet_frame_size = Jopus.opus_packet_get_samples_per_frame( data, doffset, this.Fs );
		final int packet_stream_channels = opus_packet_get_nb_channels( data, doffset );

		/* 48 x 2.5 ms = 120 ms */
		final short size[] = new short[48];
		// unsigned final char toc;
		// final int offset;// java aux.payload_offset
		if( aux == null ) {// java aux can be null
			aux = new Jopus_packet_data_aux();
		}
		final int count = Jopus.opus_packet_parse_impl( data, doffset, len, self_delimited,
							// &toc,
							null, 0, size, 0,
							// &offset, packet_offset );
							aux );// java
		if( count < 0 ) {
			return count;
		}

		doffset += aux.mPayloadOffset;// data[doffset]

		if( decode_fec )
		{
			int duration_copy;
			int ret;
			/* If no FEC can be present, run the PLC ( recursive call) */
			if( frameSize < packet_frame_size || packet_mode == Jopus_private.MODE_CELT_ONLY || this.mode == Jopus_private.MODE_CELT_ONLY ) {
				return opus_decode_native( null, 0, 0, pcm, frameSize, false, false, null, soft_clip );
			}
			/* Otherwise, run the PLC on everything except the size for which we might have FEC */
			duration_copy = this.last_packet_duration;
			if( frameSize-packet_frame_size != 0 )
			{
				ret = opus_decode_native( null, 0, 0, pcm, frameSize - packet_frame_size, false, false, null, soft_clip );
				if( ret < 0 )
				{
					this.last_packet_duration = duration_copy;
					return ret;
				}
				// celt_assert( ret == frame_size - packet_frame_size );
			}
			/* Complete with FEC */
			this.mode = packet_mode;
			this.bandwidth = packet_bandwidth;
			this.frame_size = packet_frame_size;
			this.stream_channels = packet_stream_channels;
			ret = opus_decode_frame( data, doffset, (int)size[0], pcm, this.channels * (frameSize - packet_frame_size),
									packet_frame_size, 1 );
			if( ret < 0 ) {
				return ret;
			} else {
				/* if( OPUS_CHECK_ARRAY( pcm, frame_size * st.channels ) ) {
					OPUS_PRINT_INT( frame_size );
				} */
				this.last_packet_duration = frameSize;
				return frameSize;
			}
		}

		if( count * packet_frame_size > frameSize ) {
			return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
		}

		/* Update the state as the last step to avoid updating it on an invalid packet */
		this.mode = packet_mode;
		this.bandwidth = packet_bandwidth;
		this.frame_size = packet_frame_size;
		this.stream_channels = packet_stream_channels;

		int nb_samples = 0;
		for( int i = 0; i < count; i++ )
		{
			final int ret = opus_decode_frame( data, doffset, (int)size[i], pcm, nb_samples * this.channels, frameSize - nb_samples, 0 );
			if( ret < 0 ) {
				return ret;
			}
			// celt_assert( ret == packet_frame_size );
			doffset += (int)size[i];
			nb_samples += ret;
		}
		this.last_packet_duration = nb_samples;
		/* if( OPUS_CHECK_ARRAY( pcm, nb_samples * st.channels ) ) {
			OPUS_PRINT_INT( nb_samples );
		} */
// #ifndef FIXED_POINT
		if( soft_clip ) {
			Jopus.opus_pcm_soft_clip( pcm, 0, nb_samples, this.channels, this.softclip_mem );
		} else {
			this.softclip_mem[0] = this.softclip_mem[1] = 0;
		}
// #endif
		return nb_samples;
	}

//#ifdef FIXED_POINT

	/** Decode an Opus packet.
	 * @param [in] st <tt>OpusDecoder*</tt>: Decoder state
	 * @param [in] data <tt>char*</tt>: Input payload. Use a NULL pointer to indicate packet loss
	 * @param [in] len <tt>opus_int32</tt>: Number of bytes in payload*
	 * @param [out] pcm <tt>opus_int16*</tt>: Output signal ( interleaved if 2 channels). length
	 *  is frame_size*channels*sizeof( opus_int16)
	 * @param [in] frame_size Number of samples per channel of available space in \a pcm.
	 *  If this is less than the maximum packet duration ( 120ms; 5760 for 48kHz), this function will
	 *  not be capable of decoding some packets. In the case of PLC ( data==NULL) or FEC ( decode_fec=1),
	 *  then frame_size needs to be exactly the duration of audio that is missing, otherwise the
	 *  decoder will not be in the optimal state to decode the next incoming packet. For the PLC and
	 *  FEC cases, frame_size <b>must</b> be a multiple of 2.5 ms.
	 * @param [in] decode_fec <tt>int</tt>: Flag ( 0 or 1) to request that any in-band forward error correction data be
	 *  decoded. If no such data is available, the frame is decoded as if it were lost.
	 * @returns Number of decoded samples or @ref opus_errorcodes
	 */
/*	private static final int opus_decode(final JOpusDecoder st, const unsigned char *data,
					int len, short[] pcm, int frame_size, int decode_fec)
	{
		if( frame_size <= 0 )
			return OPUS_BAD_ARG;
		return opus_decode_native( st, data, len, pcm, frame_size, decode_fec, 0, null, 0 );
	}
*/
//#ifndef DISABLE_FLOAT_API
	/** Decode an Opus packet with floating point output.
	 * @param st [in] <tt>OpusDecoder*</tt>: Decoder state
	 * @param data [in] <tt>char*</tt>: Input payload. Use a NULL pointer to indicate packet loss
	 * @param len [in] <tt>opus_int32</tt>: Number of bytes in payload
	 * @param pcm [out] <tt>float*</tt>: Output signal ( interleaved if 2 channels). length
	 *  is frame_size*channels*sizeof( float)
	 * @param frame_size [in] Number of samples per channel of available space in \a pcm.
	 *  If this is less than the maximum packet duration ( 120ms; 5760 for 48kHz), this function will
	 *  not be capable of decoding some packets. In the case of PLC ( data==NULL) or FEC ( decode_fec=1),
	 *  then frame_size needs to be exactly the duration of audio that is missing, otherwise the
	 *  decoder will not be in the optimal state to decode the next incoming packet. For the PLC and
	 *  FEC cases, frame_size <b>must</b> be a multiple of 2.5 ms.
	 * @param decode_fec [in] <tt>int</tt>: Flag ( 0 or 1) to request that any in-band forward error correction data be
	 *  decoded. If no such data is available the frame is decoded as if it were lost.
	 * @returns Number of decoded samples or @ref opus_errorcodes
	 */
/*	private final int opus_decode_float(final JOpusDecoder st, final byte[] data,
								int len, float[] pcm, int frame_size, boolean decode_fec)
	{
		VARDECL( opus_int16, out );
		int ret, i;
		int nb_samples;
		// ALLOC_STACK;

		if( frame_size <= 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( data != null && len > 0 && ! decode_fec )
		{
			nb_samples = opus_decoder_get_nb_samples( data, len );
			if( nb_samples > 0 )
				frame_size = IMIN( frame_size, nb_samples );
			else
				return Jopus_defines.OPUS_INVALID_PACKET;
		}
		// celt_assert(st->channels == 1 || st->channels == 2);
		ALLOC( out, frame_size * st.channels, opus_int16 );

		ret = opus_decode_native( data, len, out, frame_size, decode_fec, 0, null, 0 );
		if( ret > 0 )
		{
			for( i = 0; i < ret * st.channels; i++ )
				pcm[i] = (1.f / 32768.f) * (out[i]);
		}
		// RESTORE_STACK;
		return ret;
	} */
//#endif

//#else
	/** Decode an Opus packet.
	  * @param st [in] <tt>OpusDecoder*</tt>: Decoder state
	  * @param data [in] <tt>char*</tt>: Input payload. Use a NULL pointer to indicate packet loss
	  * @param len [in] <tt>opus_int32</tt>: Number of bytes in payload*
	  * @param pcm [out] <tt>opus_int16*</tt>: Output signal (interleaved if 2 channels). length
	  *  is frame_size*channels*sizeof(opus_int16)
	  * @param pcm_offset [in] start offset for pcm output
	  * @param frameSize [in] Number of samples per channel of available space in \a pcm.
	  *  If this is less than the maximum packet duration (120ms; 5760 for 48kHz), this function will
	  *  not be capable of decoding some packets. In the case of PLC (data==NULL) or FEC (decode_fec=1),
	  *  then frame_size needs to be exactly the duration of audio that is missing, otherwise the
	  *  decoder will not be in the optimal state to decode the next incoming packet. For the PLC and
	  *  FEC cases, frame_size <b>must</b> be a multiple of 2.5 ms.
	  * @param decode_fec [in] <tt>int</tt>: Flag (0 or 1) to request that any in-band forward error correction data be
	  *  decoded. If no such data is available, the frame is decoded as if it were lost.
	  * @return Number of decoded samples or @ref opus_errorcodes
	  */
	public final int opus_decode(final byte[] data, final int len,
			final short[] pcm, int pcm_offset, int frameSize, final boolean decode_fec)
	{
		//ALLOC_STACK;

		if( frameSize <= 0 )
		{
			//RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		if( data != null && len > 0 && ! decode_fec )
		{
			final int nb_samples = opus_decoder_get_nb_samples( data, len );
			if( nb_samples <= 0 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			frameSize = frameSize < nb_samples ? frameSize : nb_samples;
		}
		// celt_assert(st->channels == 1 || st->channels == 2);
		final float[] out = new float[frameSize * this.channels];

		final int ret = opus_decode_native( data, 0, len, out, frameSize, decode_fec, false, null, true );
		if( ret > 0 )
		{
			for( int i = 0, end = ret * this.channels; i < end; i++ ) {
				// pcm[pcm_offset++] = Jfloat_cast.FLOAT2INT16( out[i] );
				float x = out[i];
				x *= Jfloat_cast.CELT_SIG_SCALE;
				x = x >= -32768 ? x : -32768;
				x = x <=  32767 ? x :  32767;
				pcm[pcm_offset++] = (short)Math.floor( .5 + (double)x );
			}
		}
		//RESTORE_STACK;
		return ret;
	}

	/** Decode an Opus packet with floating point output.
	  * @param st [in] <tt>OpusDecoder*</tt>: Decoder state
	  * @param data [in] <tt>char*</tt>: Input payload. Use a NULL pointer to indicate packet loss
	  * @param len [in] <tt>opus_int32</tt>: Number of bytes in payload
	  * @param pcm [out] <tt>float*</tt>: Output signal (interleaved if 2 channels). length
	  *  is frame_size*channels*sizeof(float)
	  * @param frameSize [in] Number of samples per channel of available space in \a pcm.
	  *  If this is less than the maximum packet duration (120ms; 5760 for 48kHz), this function will
	  *  not be capable of decoding some packets. In the case of PLC (data==NULL) or FEC (decode_fec=1),
	  *  then frame_size needs to be exactly the duration of audio that is missing, otherwise the
	  *  decoder will not be in the optimal state to decode the next incoming packet. For the PLC and
	  *  FEC cases, frame_size <b>must</b> be a multiple of 2.5 ms.
	  * @param decode_fec [in] <tt>int</tt>: Flag (0 or 1) to request that any in-band forward error correction data be
	  *  decoded. If no such data is available the frame is decoded as if it were lost.
	  * @return Number of decoded samples or @ref opus_errorcodes
	  */
	public final int opus_decode_float(final byte[] data,
						final int len, final float[] pcm, final int frameSize, final boolean decode_fec)
	{
		if( frameSize <= 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		return opus_decode_native( data, 0, len, pcm, frameSize, decode_fec, false, null, false );
	}

//#endif

	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	/** Perform a CTL function on an Opus decoder.
	 *
	 * java: getters
	 *
	 * Generally the request and subsequent arguments are generated
	 * by a convenience macro.
	 * @param st <tt>OpusDecoder*</tt>: Decoder state.
	 * @param request This and all remaining parameters should be replaced by one
	 * of the convenience macros in @ref opus_genericctls or @ref opus_decoderctls.
	 * @param arg the placeholder to get a data back
	 * @return status
	 * @see opus_genericctls
	 * @see opus_decoderctls
	 */
	public final int opus_decoder_ctl(final int request, final Object[] arg)
	{// getters
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_GET_BANDWIDTH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.bandwidth );
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				arg[0] = Long.valueOf( this.rangeFinal );
			}
			break;
		case Jopus_defines.OPUS_GET_SAMPLE_RATE_REQUEST:
			{
				arg[0] = Integer.valueOf( this.Fs );
			}
			break;
		case Jopus_defines.OPUS_GET_PITCH_REQUEST:
			{
				if( this.prev_mode == Jopus_private.MODE_CELT_ONLY ) {
					ret = this.celt_dec.celt_decoder_ctl( Jopus_defines.OPUS_GET_PITCH_REQUEST, arg );
				} else {
					arg[0] = Integer.valueOf( this.DecControl.prevPitchLag );
				}
			}
			break;
		case Jopus_defines.OPUS_GET_GAIN_REQUEST:
			{
				arg[0] = Integer.valueOf( this.decode_gain );
			}
			break;
		case Jopus_defines.OPUS_GET_LAST_PACKET_DURATION_REQUEST:
			{
				arg[0] = Integer.valueOf( this.last_packet_duration );
			}
			break;
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				ret = this.celt_dec.celt_decoder_ctl( Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED, arg );
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
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_decoder_ctl(final int request, final int value)
	{

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_GAIN_REQUEST:
			{
				if( value < -32768 || value > 32767 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.decode_gain = value;
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
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_decoder_ctl(final int request, final boolean value)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				ret = this.celt_dec.celt_decoder_ctl( Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED, value );
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
	public final int opus_decoder_ctl(final int request) {
		int ret = Jopus_defines.OPUS_OK;
		switch( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
				//OPUS_CLEAR( (char*)&st.OPUS_DECODER_RESET_START,
				//		sizeof( OpusDecoder ) -
				//		((char*)&st.OPUS_DECODER_RESET_START - (char*)st) );
				clear( false );
				// final Jsilk_decoder silk_decoder = this.silk_dec;// (char*)st + st.silk_dec_offset;
				// final JCELTDecoder celt_decoder = this.celt_dec;// (JCELTDecoder)((char*)st + st.celt_dec_offset);
				this.celt_dec.celt_decoder_ctl( Jopus_defines.OPUS_RESET_STATE );
				this.silk_dec.silk_InitDecoder();
				this.stream_channels = this.channels;
				this.frame_size = this.Fs / 400;
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

	/** Frees an <code>OpusDecoder</code> allocated by opus_decoder_create( ).
	 * @param[in] st <tt>OpusDecoder*</tt>: State to be freed.
	 */
	/*private static final void opus_decoder_destroy(final JOpusDecoder st)
	{
		opus_free( st );
	}*/

	/** Gets the bandwidth of an Opus packet.
	  * @param data [in] <tt>char*</tt>: Opus packet
	  * @param doffset java: data offset
	  * @return band
	  * @retval OPUS_BANDWIDTH_NARROWBAND Narrowband (4kHz bandpass)
	  * @retval OPUS_BANDWIDTH_MEDIUMBAND Mediumband (6kHz bandpass)
	  * @retval OPUS_BANDWIDTH_WIDEBAND Wideband (8kHz bandpass)
	  * @retval OPUS_BANDWIDTH_SUPERWIDEBAND Superwideband (12kHz bandpass)
	  * @retval OPUS_BANDWIDTH_FULLBAND Fullband (20kHz bandpass)
	  * @retval OPUS_INVALID_PACKET The compressed data passed is corrupted or of an unsupported type
	  */
	public static final int opus_packet_get_bandwidth(final byte[] data, final int doffset)
	{// java doffset is added
		final int d = (int)data[doffset];
		if( 0 != (d & 0x80) )
		{
			int bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND + ((d >> 5) & 0x3);
			if( bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
				bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
			}
			return bandwidth;
		}
		if( (d & 0x60) == 0x60 )
		{
			return (d & 0x10) != 0 ? Jopus_defines.OPUS_BANDWIDTH_FULLBAND : Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
		}
		return Jopus_defines.OPUS_BANDWIDTH_NARROWBAND + ((d >> 5) & 0x3);
	}

	/** Gets the number of channels from an Opus packet.
	  * @param data [in] <tt>char*</tt>: Opus packet
	  * @param doffset java: data offset
	  * @return Number of channels
	  * @retval OPUS_INVALID_PACKET The compressed data passed is corrupted or of an unsupported type
	  */
	public static final int opus_packet_get_nb_channels(final byte[] data, final int doffset)
	{// java doffset is added
		return ((int)data[doffset] & 0x4) != 0 ? 2 : 1;
	}

	/** Gets the number of frames in an Opus packet.
	  * @param packet [in] <tt>char*</tt>: Opus packet
	  * @param poffset java: packet offset
	  * @param len [in] <tt>opus_int32</tt>: Length of packet
	  * @return Number of frames
	  * @retval OPUS_BAD_ARG Insufficient data was passed to the function
	  * @retval OPUS_INVALID_PACKET The compressed data passed is corrupted or of an unsupported type
	  */
	public static final int opus_packet_get_nb_frames(final byte packet[], int poffset,// java
			final int len)
	{
		int count;
		if( len < 1 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		count = packet[poffset] & 0x3;
		if( count == 0 ) {
			return 1;
		} else if( count != 3 ) {
			return 2;
		} else if( len < 2 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}// else {
			return packet[++poffset] & 0x3F;
		//}
	}

	/** Gets the number of samples of an Opus packet.
	  * @param packet [in] <tt>char*</tt>: Opus packet
	  * @param poffset java: packet offset
	  * @param len [in] <tt>opus_int32</tt>: Length of packet
	  * @param Fs [in] <tt>opus_int32</tt>: Sampling rate in Hz.
	  *                                     This must be a multiple of 400, or
	  *                                     inaccurate results will be returned.
	  * @return Number of samples
	  * @retval OPUS_BAD_ARG Insufficient data was passed to the function
	  * @retval OPUS_INVALID_PACKET The compressed data passed is corrupted or of an unsupported type
	  */
	public static final int opus_packet_get_nb_samples(final byte packet[], final int poffset,// java
			final int len, final int Fs)
	{
		final int count = opus_packet_get_nb_frames( packet, poffset, len );

		if( count < 0 ) {
			return count;
		}

		final int samples = count * Jopus.opus_packet_get_samples_per_frame( packet, poffset, Fs );
		/* Can't have more than 120 ms */
		if( samples * 25 > Fs * 3 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}// else {
			return samples;
		//}
	}

	/** Gets the number of samples of an Opus packet.
	  * @param dec [in] <tt>OpusDecoder*</tt>: Decoder state
	  * @param packet [in]<tt>char*</tt>: Opus packet
	  * @param len [in] <tt>opus_int32</tt>: Length of packet
	  * @return Number of samples
	  * @retval OPUS_BAD_ARG Insufficient data was passed to the function
	  * @retval OPUS_INVALID_PACKET The compressed data passed is corrupted or of an unsupported type
	  */
	public final int opus_decoder_get_nb_samples(final byte packet[], final int len)
	{
		return opus_packet_get_nb_samples( packet, 0, len, this.Fs );
	}
}