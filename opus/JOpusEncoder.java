package opus;

import celt.JAnalysisInfo;
import celt.JCELTEncoder;
import celt.JCELTMode;
import celt.JSILKInfo;
import celt.Jcelt;
import celt.Jcelt_codec_API;
import celt.Jec_enc;
import celt.Jfloat_cast;
import silk.Jdefine;
import silk.Jmacros;
import silk.Jsilk_EncControlStruct;
import silk.Jsilk_encoder;
import silk.Jtuning_parameters;

/* Copyright (c) 2010-2011 Xiph.Org Foundation, Skype Limited
   Written by Jean-Marc Valin and Koen Vos */

// opus_encoder.c

/** @defgroup opus_encoder Opus Encoder
 * @{
 *
 * @brief This page describes the process and functions used to encode Opus.
 *
 * Since Opus is a stateful codec, the encoding process starts with creating an encoder
 * state. This can be done with:
 *
 * @code
 * int          error;
 * OpusEncoder *enc;
 * enc = opus_encoder_create(Fs, channels, application, &error);
 * @endcode
 *
 * From this point, @c enc can be used for encoding an audio stream. An encoder state
 * @b must @b not be used for more than one stream at the same time. Similarly, the encoder
 * state @b must @b not be re-initialized for each frame.
 *
 * While opus_encoder_create() allocates memory for the state, it's also possible
 * to initialize pre-allocated memory:
 *
 * @code
 * int          size;
 * int          error;
 * OpusEncoder *enc;
 * size = opus_encoder_get_size(channels);
 * enc = malloc(size);
 * error = opus_encoder_init(enc, Fs, channels, application);
 * @endcode
 *
 * where opus_encoder_get_size() returns the required size for the encoder state. Note that
 * future versions of this code may change the size, so no assuptions should be made about it.
 *
 * The encoder state is always continuous in memory and only a shallow copy is sufficient
 * to copy it (e.g. memcpy())
 *
 * It is possible to change some of the encoder's settings using the opus_encoder_ctl()
 * interface. All these settings already default to the recommended value, so they should
 * only be changed when necessary. The most common settings one may want to change are:
 *
 * @code
 * opus_encoder_ctl(enc, OPUS_SET_BITRATE(bitrate));
 * opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(complexity));
 * opus_encoder_ctl(enc, OPUS_SET_SIGNAL(signal_type));
 * @endcode
 *
 * where
 *
 * @arg bitrate is in bits per second (b/s)
 * @arg complexity is a value from 1 to 10, where 1 is the lowest complexity and 10 is the highest
 * @arg signal_type is either OPUS_AUTO (default), OPUS_SIGNAL_VOICE, or OPUS_SIGNAL_MUSIC
 *
 * See @ref opus_encoderctls and @ref opus_genericctls for a complete list of parameters that can be set or queried. Most parameters can be set or changed at any time during a stream.
 *
 * To encode a frame, opus_encode() or opus_encode_float() must be called with exactly one frame (2.5, 5, 10, 20, 40 or 60 ms) of audio data:
 * @code
 * len = opus_encode(enc, audio_frame, frame_size, packet, max_packet);
 * @endcode
 *
 * where
 * <ul>
 * <li>audio_frame is the audio data in opus_int16 (or float for opus_encode_float())</li>
 * <li>frame_size is the duration of the frame in samples (per channel)</li>
 * <li>packet is the byte array to which the compressed data is written</li>
 * <li>max_packet is the maximum number of bytes that can be written in the packet (4000 bytes is recommended).
 *     Do not use max_packet to control VBR target bitrate, instead use the #OPUS_SET_BITRATE CTL.</li>
 * </ul>
 *
 * opus_encode() and opus_encode_float() return the number of bytes actually written to the packet.
 * The return value <b>can be negative</b>, which indicates that an error has occurred. If the return value
 * is 2 bytes or less, then the packet does not need to be transmitted (DTX).
 *
 * Once the encoder state if no longer needed, it can be destroyed with
 *
 * @code
 * opus_encoder_destroy(enc);
 * @endcode
 *
 * If the encoder was created with opus_encoder_init() rather than opus_encoder_create(),
 * then no action is required aside from potentially freeing the memory that was manually
 * allocated for it (calling free(enc) for the example above)
 *
 */
public final class JOpusEncoder {
	private static final String CLASS_NAME = "JOpusEncoder";

	private static final int MAX_ENCODER_BUFFER = 480;

// #ifndef DISABLE_FLOAT_API
	private static final float PSEUDO_SNR_THRESHOLD = 316.23f;    /* 10^(25/10) */
// #endif

	private static final class JStereoWidthState {
		private float XX, XY, YY;
		private float smoothed_width;
		private float max_follower;
		//
		private final void clear() {
			XX = 0;
			XY = 0;
			YY = 0;
			smoothed_width = 0;
			max_follower = 0;
		}
		private final void copyFrom(final JStereoWidthState s) {
			XX = s.XX;
			XY = s.XY;
			YY = s.YY;
			smoothed_width = s.smoothed_width;
			max_follower = s.max_follower;
		}
	}

//struct OpusEncoder {
	//int          celt_enc_offset;// java changed to JCELTEncoder celt_enc = new JCELTEncoder();
	//int          silk_enc_offset;// java changed to Jsilk_encoder silk_enc = new Jsilk_encoder();
	private Jsilk_encoder silk_enc = null;// new Jsilk_encoder();// java added
	private JCELTEncoder celt_enc = null;// new JCELTEncoder();// java added
	private final Jsilk_EncControlStruct silk_mode = new Jsilk_EncControlStruct();
	private int          application;
	private int          channels;
	private int          delay_compensation;
	private int          force_channels;
	private int          signal_type;
	private int          user_bandwidth;
	private int          max_bandwidth;
	private int          user_forced_mode;
	private int          voice_ratio;
	private int          Fs;
	private boolean      use_vbr;
	private boolean      vbr_constraint;
	private int          variable_duration;
	private int          bitrate_bps;
	private int          user_bitrate_bps;
	private int          lsb_depth;
	private int          encoder_buffer;
	private boolean      lfe;
	// int          arch;// java not using
	private boolean      use_dtx; /* general DTX for both SILK and CELT */
// #ifndef DISABLE_FLOAT_API
	private final JTonalityAnalysisState analysis = new JTonalityAnalysisState();
// #endif

// #define OPUS_ENCODER_RESET_START stream_channels
	private int          stream_channels;
	private short        hybrid_stereo_width_Q14;
	private int          variable_HP_smth2_Q15;
	private float        prev_HB_gain;
	private final float  hp_mem[] = new float[4];
	private int          mode;
	private int          prev_mode;
	private int          prev_channels;
	private int          prev_framesize;
	private int          bandwidth;
	/* Bandwidth determined automatically from the rate (before any other adjustment) */
	private int          auto_bandwidth;
	private boolean      silk_bw_switch;
	/* Sampling rate (at the API level) */
	private boolean      first;
	private float[]      energy_masking;
	private final JStereoWidthState width_mem = new JStereoWidthState();
	private final float  delay_buffer[] = new float[MAX_ENCODER_BUFFER * 2];
// #ifndef DISABLE_FLOAT_API
	private int          detected_bandwidth;
	private int          nb_no_activity_frames;
	private float        peak_signal_energy;
// #endif
	private boolean      nonfinal_frame; /* current frame is not the final in a packet */
	private long         rangeFinal;// uint32
//};
// end of struct
	//
	/**
	 * Default costructor
	 */
	public JOpusEncoder() {
	}
	/**
	 * java. to replace code:
	 * <pre>
	 * inr decsize = JOpusDecoder.opus_decoder_get_size( 1 );
	 * OpusDecoder *dec = (OpusDecoder*)malloc( decsize );
	 * </pre>
	 * @param channels
	 * @throws IllegalArgumentException
	 */
	public JOpusEncoder(final int channels) throws IllegalArgumentException {
		if( channels < 1 || channels > 2 ) {
			throw new IllegalArgumentException();
		}
		silk_enc = new Jsilk_encoder();
		celt_enc = new JCELTEncoder( channels );
	}
	private final void clear(final boolean isFull) {
		if( isFull ) {
			silk_enc = null;
			celt_enc = null;
			silk_mode.clear();
			application = 0;
			channels = 0;
			delay_compensation = 0;
			force_channels = 0;
			signal_type = 0;
			user_bandwidth = 0;
			max_bandwidth = 0;
			user_forced_mode = 0;
			voice_ratio = 0;
			Fs = 0;
			use_vbr = false;
			vbr_constraint = false;
			variable_duration = 0;
			bitrate_bps = 0;
			user_bitrate_bps = 0;
			lsb_depth = 0;
			encoder_buffer = 0;
			lfe = false;
			// arch = 0;// java not using
			use_dtx = false;
// #ifndef DISABLE_FLOAT_API
			analysis.clear( true );
// #endif
		}
		stream_channels = 0;
		hybrid_stereo_width_Q14 = 0;
		variable_HP_smth2_Q15 = 0;
		prev_HB_gain = 0;
		hp_mem[0] = 0; hp_mem[1] = 0; hp_mem[2] = 0; hp_mem[3] = 0;
		mode = 0;
		prev_mode = 0;
		prev_channels = 0;
		prev_framesize = 0;
		bandwidth = 0;
		auto_bandwidth = 0;
		silk_bw_switch = false;
		first = false;
		energy_masking = null;
		width_mem.clear();
		final float[] fbuff = delay_buffer;
		int i = MAX_ENCODER_BUFFER * 2;
		do {
			fbuff[--i] = 0;
		} while( i > 0  );
// #ifndef DISABLE_FLOAT_API
		detected_bandwidth = 0;
		nb_no_activity_frames = 0;
		peak_signal_energy = 0;
// #endif
		nonfinal_frame = false;
		rangeFinal = 0;
	}
	/**
	 * java: memcpy
	 * @param e struct to copy from
	 */
	public final void copyFrom(final JOpusEncoder e) {
		silk_enc.copyFrom( e.silk_enc );
		celt_enc.copyFrom( e.celt_enc );
		silk_mode.copyFrom( e.silk_mode );
		application = e.application;
		channels = e.channels;
		delay_compensation = e.delay_compensation;
		force_channels = e.force_channels;
		signal_type = e.signal_type;
		user_bandwidth = e.user_bandwidth;
		max_bandwidth = e.max_bandwidth;
		user_forced_mode = e.user_forced_mode;
		voice_ratio = e.voice_ratio;
		Fs = e.Fs;
		use_vbr = e.use_vbr;
		vbr_constraint = e.vbr_constraint;
		variable_duration = e.variable_duration;
		bitrate_bps = e.bitrate_bps;
		user_bitrate_bps = e.user_bitrate_bps;
		lsb_depth = e.lsb_depth;
		encoder_buffer = e.encoder_buffer;
		lfe = e.lfe;
		// arch = 0;// java not using
		use_dtx = e.use_dtx;
// #ifndef DISABLE_FLOAT_API
		analysis.copyFrom( e.analysis );
// #endif
		stream_channels = e.stream_channels;
		hybrid_stereo_width_Q14 = e.hybrid_stereo_width_Q14;
		variable_HP_smth2_Q15 = e.variable_HP_smth2_Q15;
		prev_HB_gain = e.prev_HB_gain;
		hp_mem[0] = e.hp_mem[0]; hp_mem[1] = e.hp_mem[1]; hp_mem[2] = e.hp_mem[2]; hp_mem[3] = e.hp_mem[3];
		mode = e.mode;
		prev_mode = e.prev_mode;
		prev_channels = e.prev_channels;
		prev_framesize = e.prev_framesize;
		bandwidth = e.bandwidth;
		auto_bandwidth = e.auto_bandwidth;
		silk_bw_switch = e.silk_bw_switch;
		first = e.first;
		energy_masking = e.energy_masking;
		width_mem.copyFrom( e.width_mem );
		System.arraycopy( e.delay_buffer, 0, delay_buffer, 0, MAX_ENCODER_BUFFER * 2 );
// #ifndef DISABLE_FLOAT_API
		detected_bandwidth = e.detected_bandwidth;
		nb_no_activity_frames = e.nb_no_activity_frames;
		peak_signal_energy = e.peak_signal_energy;
// #endif
		nonfinal_frame = e.nonfinal_frame;
		rangeFinal = e.rangeFinal;
	}

	/* Transition tables for the voice and music. First column is the
	   middle (memoriless) threshold. The second column is the hysteresis
	   (difference with the middle) */
	private static final int mono_voice_bandwidth_thresholds[/* 8 */] = {
			 9000,  700, /* NB<->MB */
			 9000,  700, /* MB<->WB */
			13500, 1000, /* WB<->SWB */
			14000, 2000, /* SWB<->FB */
	};
	private static final int mono_music_bandwidth_thresholds[/* 8 */] = {
			 9000,  700, /* NB<->MB */
			 9000,  700, /* MB<->WB */
			11000, 1000, /* WB<->SWB */
			12000, 2000, /* SWB<->FB */
	};
	private static final int stereo_voice_bandwidth_thresholds[/* 8 */] = {
			 9000,  700, /* NB<->MB */
			 9000,  700, /* MB<->WB */
			13500, 1000, /* WB<->SWB */
			14000, 2000, /* SWB<->FB */
	};
	private static final int stereo_music_bandwidth_thresholds[/* 8 */] = {
			 9000,  700, /* NB<->MB */
			 9000,  700, /* MB<->WB */
			11000, 1000, /* WB<->SWB */
			12000, 2000, /* SWB<->FB */
	};
	/* Threshold bit-rates for switching between mono and stereo */
	private static final int stereo_voice_threshold = 19000;
	private static final int stereo_music_threshold = 17000;

	/** Threshold bit-rate for switching between SILK/hybrid and CELT-only */
	private static final int mode_thresholds[/* 2 */][/* 2 */] = {
			/* voice */ /* music */
			{  64000,      10000}, /* mono */
			{  44000,      10000}, /* stereo */
	};

	private static final int fec_thresholds[] = {
		12000, 1000, /* NB */
		14000, 1000, /* MB */
		16000, 1000, /* WB */
		20000, 1000, /* SWB */
		22000, 1000, /* FB */
	};

	/** Gets the size of an <code>OpusEncoder</code> structure.
	  * @param[in] channels <tt>int</tt>: Number of channels.
	  *                                   This must be 1 or 2.
	  * @returns The size in bytes.
	  */
	/*private static final int opus_encoder_get_size(final int channels)// java not using
	{
		int silkEncSizeBytes, celtEncSizeBytes;
		int ret;
		if( channels < 1 || channels > 2 ) {
			return 0;
		}
		int ret = silk_Get_Encoder_Size( &silkEncSizeBytes );
		if( ret ) {
			return 0;
		}
		silkEncSizeBytes = align( silkEncSizeBytes );
		celtEncSizeBytes = celt_encoder_get_size( channels );
		return align( sizeof( JOpusEncoder ) ) + silkEncSizeBytes + celtEncSizeBytes;
	}*/

	/** Initializes a previously allocated encoder state
	  * The memory pointed to by st must be at least the size returned by opus_encoder_get_size().
	  * This is intended for applications which use their own allocator instead of malloc.
	  * @see #opus_encoder_create(int, int, int, int[])
	  * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	  * @param st [in] <tt>OpusEncoder*</tt>: Encoder state
	  * @param Fsr [in] <tt>opus_int32</tt>: Sampling rate of input signal (Hz)
	  *                                     This must be one of 8000, 12000, 16000,
	  *                                     24000, or 48000.
	  * @param nchannels [in] <tt>int</tt>: Number of channels (1 or 2) in input signal
	  * @param appmode [in] <tt>int</tt>: Coding mode (OPUS_APPLICATION_VOIP/OPUS_APPLICATION_AUDIO/OPUS_APPLICATION_RESTRICTED_LOWDELAY)
	  * @return #OPUS_OK Success or @ref opus_errorcodes
	  */
	public final int opus_encoder_init(final int Fsr, final int nchannels, final int appmode)
	{
		if( (Fsr != 48000 && Fsr != 24000 && Fsr != 16000 && Fsr != 12000 && Fsr != 8000) || (nchannels != 1 && nchannels != 2) ||
				(appmode != Jopus_defines.OPUS_APPLICATION_VOIP && appmode != Jopus_defines.OPUS_APPLICATION_AUDIO
				&& appmode != Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY) ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		// OPUS_CLEAR( (char*)st, opus_encoder_get_size( channels ) );
		clear( true );
		/* Create SILK encoder */
		//int ret = silk_Get_Encoder_Size( &silkEncSizeBytes );
		//if( ret ) {
		//	return Jopus_defines.OPUS_BAD_ARG;
		//}
		//final int silkEncSizeBytes = align( silkEncSizeBytes );
		//st.silk_enc_offset = align( sizeof( OpusEncoder ) );
		//st.celt_enc_offset = st.silk_enc_offset + silkEncSizeBytes;
		// final Jsilk_encoder silk_enc = (char*)st + st.silk_enc_offset;// java changed: added silk_enc
		// final JCELTEncoder celt_enc = (JCELTEncoder)((char*)st + st.celt_enc_offset);// java changed: added celt_enc
		this.silk_enc = new Jsilk_encoder();
		this.celt_enc = new JCELTEncoder();

		this.stream_channels = this.channels = nchannels;

		this.Fs = Fsr;

		// st.arch = opus_select_arch();

		final int ret = this.silk_enc.silk_InitEncoder( this.silk_mode );
		if( 0 != ret ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		/* default SILK parameters */
		this.silk_mode.nChannelsAPI              = nchannels;
		this.silk_mode.nChannelsInternal         = nchannels;
		this.silk_mode.API_sampleRate            = this.Fs;
		this.silk_mode.maxInternalSampleRate     = 16000;
		this.silk_mode.minInternalSampleRate     = 8000;
		this.silk_mode.desiredInternalSampleRate = 16000;
		this.silk_mode.payloadSize_ms            = 20;
		this.silk_mode.bitRate                   = 25000;
		this.silk_mode.packetLossPercentage      = 0;
		this.silk_mode.complexity                = 9;
		this.silk_mode.useInBandFEC              = false;
		this.silk_mode.useDTX                    = false;
		this.silk_mode.useCBR                    = false;
		this.silk_mode.reducedDependency         = false;

		/* Create CELT encoder */
		/* Initialize CELT encoder */
		final int err = this.celt_enc.celt_encoder_init( Fsr, nchannels );//, st.arch );
		if( err != Jopus_defines.OPUS_OK ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		this.celt_enc.celt_encoder_ctl( Jcelt.CELT_SET_SIGNALLING, false );
		this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, this.silk_mode.complexity );

		this.use_vbr = true;
		/* Makes constrained VBR the default (safer for real-time use) */
		this.vbr_constraint = true;
		this.user_bitrate_bps = Jopus_defines.OPUS_AUTO;
		this.bitrate_bps = 3000 + Fsr * nchannels;
		this.application = appmode;
		this.signal_type = Jopus_defines.OPUS_AUTO;
		this.user_bandwidth = Jopus_defines.OPUS_AUTO;
		this.max_bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
		this.force_channels = Jopus_defines.OPUS_AUTO;
		this.user_forced_mode = Jopus_defines.OPUS_AUTO;
		this.voice_ratio = -1;
		this.encoder_buffer = this.Fs / 100;
		this.lsb_depth = 24;
		this.variable_duration = Jopus_defines.OPUS_FRAMESIZE_ARG;

		/* Delay compensation of 4 ms (2.5 ms for SILK's extra look-ahead
		   + 1.5 ms for SILK resamplers and stereo prediction) */
		this.delay_compensation = this.Fs / 250;

		this.hybrid_stereo_width_Q14 = 1 << 14;
		this.prev_HB_gain = Jfloat_cast.Q15ONE;
		this.variable_HP_smth2_Q15 = Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ ) << 8;
		this.first = true;
		this.mode = Jopus_private.MODE_HYBRID;
		this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;

// #ifndef DISABLE_FLOAT_API
		this.analysis.tonality_analysis_init( this.Fs );
		this.analysis.application = this.application;
// #endif

		return Jopus_defines.OPUS_OK;
	}

	/**
	 * java: returned type is changed from unsigned char to int
	 * @param mode
	 * @param framerate
	 * @param bandwidth
	 * @param channels
	 * @return
	 */
	private static final int gen_toc(final int mode, int framerate, final int bandwidth, final int channels)
	{
		int period = 0;
		while( framerate < 400 )
		{
			framerate <<= 1;
			period++;
		}
		int toc;
		if( mode == Jopus_private.MODE_SILK_ONLY )
		{
			toc = (bandwidth - Jopus_defines.OPUS_BANDWIDTH_NARROWBAND) << 5;
			toc |= (period - 2) << 3;
		} else if( mode == Jopus_private.MODE_CELT_ONLY )
		{
			int tmp = bandwidth - Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
			if( tmp < 0 ) {
				tmp = 0;
			}
			toc = 0x80;
			toc |= tmp << 5;
			toc |= period << 3;
		} else /* Hybrid */
		{
			toc = 0x60;
			toc |= (bandwidth - Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND) << 4;
			toc |= (period - 2) << 3;
		}
		if( channels == 2 ) {
			toc |= 1 << 2;
		}
		return toc;
	}

// #ifndef FIXED_POINT
	/**
	 *
	 * @param in I:    Input signal
	 * @param inoffset java I: an offset for the in
	 * @param B_Q28 I:    MA coefficients [3]
	 * @param A_Q28 I:    AR coefficients [2]
	 * @param S I/O:  State vector [2]
	 * @param soffset java I: an offset for the S
	 * @param out O:    Output signal
	 * @param outoffset java I: an offset for the out
	 * @param len I:    Signal length (must be even)
	 * @param stride
	 */
	private static final void silk_biquad_float(final float[] in, int inoffset,// java
			final int[] B_Q28, final int[] A_Q28,
			final float[] S, final int soffset,// java
			final float[] out, int outoffset,// java
			int len, final int stride
		)
	{
		/* DIRECT FORM II TRANSPOSED (uses 2 element state vector) */
		// final float A[] = new float[2];// java replaced by vars
		// final float B[] = new float[3];// java replaced by vars

		final float A_0 = ((float)A_Q28[0] * (1.f / ((float)(1 << 28))));
		final float A_1 = ((float)A_Q28[1] * (1.f / ((float)(1 << 28))));
		final float B_0 = ((float)B_Q28[0] * (1.f / ((float)(1 << 28))));
		final float B_1 = ((float)B_Q28[1] * (1.f / ((float)(1 << 28))));
		final float B_2 = ((float)B_Q28[2] * (1.f / ((float)(1 << 28))));

		/* Negate A_Q28 values and split in two parts */

		float S_0 = S[soffset];// java
		float S_1 = S[soffset + 1];// java
		len *= stride;// java
		for( len += inoffset; inoffset < len; inoffset += stride, outoffset += stride ) {
			/* S[ 0 ], S[ 1 ]: Q12 */
			final float inval = in[inoffset];
			final float vout = S_0 + B_0 * inval;

			S_0 = S_1 + B_1 * inval - vout * A_0;
			S_1 = B_2 * inval - vout * A_1 + Jfloat_cast.VERY_SMALL;

			/* Scale back to Q0 and saturate */
			out[outoffset] = vout;
		}
		S[soffset] = S_0;// java
		S[soffset + 1] = S_1;// java
	}
// #endif

	private static final void hp_cutoff(final float[] in, int inoffset,// java
			final int cutoff_Hz,
			final float[] out, int outoffset,// java
			final float[] hp_mem, final int len, final int channels, final int Fs)//, int arch)
	{
		final int B_Q28[] = new int[ 3 ];
		final int A_Q28[] = new int[ 2 ];
		// (void)arch;

		// silk_assert( cutoff_Hz <= silk_int32_MAX / SILK_FIX_CONST( 1.5 * 3.14159 / 1000, 19 ) );
		// FIXME why not PI?
		// Fc_Q19 = (SILK_FIX_CONST( 1.5 * 3.14159 / 1000., 19 ) * cutoff_Hz ) / (Fs / 1000);
		final int Fc_Q19 = (((int)( (1.5 * 3.14159 / 1000.) * (1 << 19) + 0.5 )) * cutoff_Hz ) / (Fs / 1000);
		// silk_assert( Fc_Q19 > 0 && Fc_Q19 < 32768 );
		// r_Q28 = SILK_FIX_CONST( 1.0, 28 ) - SILK_FIX_CONST( 0.92, 9 ) * Fc_Q19;
		final int r_Q28 = ((int)( 1.0 * (1 << 28) + 0.5 )) - ((int)( 0.92 * (1 << 9) + 0.5 )) * Fc_Q19;

		/* b = r * [ 1; -2; 1 ]; */
		/* a = [ 1; -2 * r * ( 1 - 0.5 * Fc^2 ); r^2 ]; */
		B_Q28[ 0 ] = r_Q28;
		B_Q28[ 1 ] = (-r_Q28) << 1;
		B_Q28[ 2 ] = r_Q28;

		/* -r * ( 2 - Fc * Fc ); */
		final long r_Q22 = r_Q28 >> 6;
		// A_Q28[ 0 ] = Jmacros.silk_SMULWW( r_Q22, Jmacros.silk_SMULWW( Fc_Q19, Fc_Q19 ) - SILK_FIX_CONST( 2.0, 22 ) );
		A_Q28[ 0 ] = (int)((r_Q22 * ((((long)Fc_Q19 * Fc_Q19) >> 16) - ((long)(2.0 * (1 << 22) + 0.5)))) >> 16);
		A_Q28[ 1 ] = (int)((r_Q22 * r_Q22) >> 16);

/* #ifdef FIXED_POINT
		if( channels == 1 ) {
			silk_biquad_alt_stride1( in, B_Q28, A_Q28, hp_mem, out, len );
		} else {
			silk_biquad_alt_stride2( in, B_Q28, A_Q28, hp_mem, out, len, arch );
		}
#else */
		silk_biquad_float( in, inoffset, B_Q28, A_Q28, hp_mem, 0, out, outoffset, len, channels );
		if( channels == 2 ) {
			silk_biquad_float( in, ++inoffset, B_Q28, A_Q28, hp_mem, 2, out, ++outoffset, len, channels );
		}
// #endif
	}

/* #ifdef FIXED_POINT
	private static final void dc_reject(final short[] in, final int cutoff_Hz, final short[] out, final int[] hp_mem,
						final int len, final int channels, final int Fs)
	{
		int c, i;
		int shift;

		// Approximates -round( log2(6.3 * cutoff_Hz / Fs) )
		shift = celt_ilog2( Fs / (cutoff_Hz * 4) );
		for( c = 0; c < channels; c++ )
		{
			for( i = 0; i < len; i++ )
			{
				int x, y;
				x = SHL32( EXTEND32( in[channels * i + c]), 14 );
				y = x - hp_mem[2 * c];
				hp_mem[2 * c] = hp_mem[2 * c] + PSHR32( x - hp_mem[2 * c], shift );
				out[channels * i + c] = EXTRACT16( SATURATE( PSHR32( y, 14 ), 32767 ) );
			}
		}
	}

#else */
	private static final void dc_reject(final float[] in, final int inoffset,// java
			final int cutoff_Hz,
			final float[] out, final int outoffset,// java
			final float[] hp_mem, int len, final int channels, final int Fs)
	{
		final float coef = 6.3f * cutoff_Hz / Fs;
		final float coef2 = 1f - coef;
		if( channels == 2 )
		{
			float m0 = hp_mem[0];
			float m2 = hp_mem[2];
			len <<= 1;// java
			for( int i = 0; i < len; )
			{
				final float x0 = in[i++];
				final float x1 = in[i--];
				final float out0 = x0 - m0;
				final float out1 = x1 - m2;
				m0 = coef * x0 + Jfloat_cast.VERY_SMALL + coef2 * m0;
				m2 = coef * x1 + Jfloat_cast.VERY_SMALL + coef2 * m2;
				out[i++] = out0;
				out[i++] = out1;
			}
			hp_mem[0] = m0;
			hp_mem[2] = m2;
			return;
		}// else {
			float m0 = hp_mem[0];
			for( int i = 0; i < len; i++ )
			{
				final float x = in[i];
				final float y = x - m0;
				m0 = coef * x + Jfloat_cast.VERY_SMALL + coef2 * m0;
				out[i] = y;
			}
			hp_mem[0] = m0;
		// }
	}
// #endif

	private static final void stereo_fade(final float[] in, final float[] out, float g1, float g2,
			final int overlap48, int frame_size, final int channels, final float[] window, final int Fs)
	{
		int overlap;
		int inc;
		inc = 48000 / Fs;
		overlap = overlap48 / inc;
		g1 = Jfloat_cast.Q15ONE - g1;
		g2 = Jfloat_cast.Q15ONE - g2;
		int i, ic;
		for( i = 0, ic = 0, overlap *= inc; i < overlap; i += inc, ic += channels )
		{
			float w = window[i];
			w *= w;// java
			final float g = w * g2 + (Jfloat_cast.Q15ONE - w) * g1;
			float diff = .5f * (in[ic] - in[ic + 1]);
			diff *= g;
			out[ic] -= diff;
			out[ic + 1] += diff;
		}
		for( frame_size *= channels; ic < frame_size; ic += channels )
		{
			float diff = .5f * (in[ic] - in[ic + 1]);
			diff *= g2;
			out[ic] -= diff;
			out[ic + 1] += diff;
		}
	}

	private static final void gain_fade(final float[] in, final int inoffset,// java
			final float[] out, final int outoffset,// java
			final float g1, final float g2,
			final int overlap48, int frame_size, final int channels, final float[] window, final int Fs)
	{
		final int inc = 48000 / Fs;
		int overlap = overlap48 / inc;
		if( channels == 1 )
		{
			for( int i = 0; i < overlap; i++ )
			{
				float w = window[i * inc];
				w *= w;
				final float g = (w * g2) + (Jfloat_cast.Q15ONE - w) * g1;
				out[outoffset + i] = g * in[inoffset + i];
			}
		} else {
			for( int i = 0; i < overlap; i++ )
			{
				float w = window[i * inc];
				w *= w;
				final float g = (w * g2) + (Jfloat_cast.Q15ONE - w) * g1;
				final int i2 = i << 1;// java
				int oi = outoffset + i2;// java
				int ii = inoffset + i2;// java
				out[oi++] = g * in[ii++];
				out[oi] = g * in[ii];
			}
		}
		int c = 0;
		overlap *= channels;// java
		frame_size *= channels;// java
		frame_size += inoffset;// java
		do {
			for( int oi = outoffset + overlap, ii = inoffset + overlap; ii < frame_size; oi += channels, ii += channels )
			{
				out[oi] = g2 * in[ii];
			}
			overlap++;// java
			frame_size++;// java
		}
		while( ++c < channels );
	}

	/** Allocates and initializes an encoder state.
	 * There are three coding modes:
	 *
	 * @ref OPUS_APPLICATION_VOIP gives best quality at a given bitrate for voice
	 *    signals. It enhances the  input signal by high-pass filtering and
	 *    emphasizing formants and harmonics. Optionally  it includes in-band
	 *    forward error correction to protect against packet loss. Use this
	 *    mode for typical VoIP applications. Because of the enhancement,
	 *    even at high bitrates the output may sound different from the input.
	 *
	 * @ref OPUS_APPLICATION_AUDIO gives best quality at a given bitrate for most
	 *    non-voice signals like music. Use this mode for music and mixed
	 *    (music/voice) content, broadcast, and applications requiring less
	 *    than 15 ms of coding delay.
	 *
	 * @ref OPUS_APPLICATION_RESTRICTED_LOWDELAY configures low-delay mode that
	 *    disables the speech-optimized mode in exchange for slightly reduced delay.
	 *    This mode can only be set on an newly initialized or freshly reset encoder
	 *    because it changes the codec delay.
	 *
	 * This is useful when the caller knows that the speech-optimized modes will not be needed (use with caution).
	 * @param Fs [in] <tt>opus_int32</tt>: Sampling rate of input signal (Hz)
	 *                                     This must be one of 8000, 12000, 16000,
	 *                                     24000, or 48000.
	 * @param channels [in] <tt>int</tt>: Number of channels (1 or 2) in input signal
	 * @param application [in] <tt>int</tt>: Coding mode (@ref OPUS_APPLICATION_VOIP/@ref OPUS_APPLICATION_AUDIO/@ref OPUS_APPLICATION_RESTRICTED_LOWDELAY)
	 * @param error [out] <tt>int*</tt>: @ref opus_errorcodes
	 * @note Regardless of the sampling rate and number channels selected, the Opus encoder
	 * can switch to a lower audio bandwidth or number of channels if the bitrate
	 * selected is too low. This also means that it is safe to always use 48 kHz stereo input
	 * and let the encoder optimize the encoding.
	 * @return JOpusEncoder object
	 */
	public static final JOpusEncoder opus_encoder_create(final int Fs, final int channels, final int application, final int[] error)
	{
		if( (Fs != 48000 && Fs != 24000 && Fs != 16000 && Fs != 12000 && Fs != 8000) || (channels != 1 && channels != 2) ||
			(application != Jopus_defines.OPUS_APPLICATION_VOIP && application != Jopus_defines.OPUS_APPLICATION_AUDIO
			&& application != Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY) )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_BAD_ARG;
			}
			return null;
		}
		// st = (JOpusEncoder)opus_alloc( opus_encoder_get_size( channels ) );
		JOpusEncoder st = new JOpusEncoder();
		/* if( st == null )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		final int ret = st.opus_encoder_init( Fs, channels, application );
		if( null != error ) {
			error[0] = ret;
		}
		if( ret != Jopus_defines.OPUS_OK )
		{
			// opus_free( st );
			st = null;
		}
		return st;
	}

	private final int user_bitrate_to_bitrate(int frame_size, final int max_data_bytes)
	{
		if( 0 == frame_size ) {
			frame_size = this.Fs / 400;
		}
		if( this.user_bitrate_bps == Jopus_defines.OPUS_AUTO ) {
			return 60 * this.Fs / frame_size + this.Fs * this.channels;
		} else if( this.user_bitrate_bps == Jopus_defines.OPUS_BITRATE_MAX ) {
			return (max_data_bytes << 3) * this.Fs / frame_size;
		}// else {
			return this.user_bitrate_bps;
		// }
	}

/* #ifndef DISABLE_FLOAT_API
#ifdef FIXED_POINT
#define PCM2VAL(x) FLOAT2INT16(x)
#else
#define PCM2VAL(x) SCALEIN(x)// * Jfloat_cast.CELT_SIG_SCALE
#endif */
	private static final class Jdownmix_float extends Idownmix {

		@Override
		void downmix(final Object _x, final int xoffset, final float[] y, final int suboffset, int subframe, final int offset, final int c1, final int c2, final int C) {
			final float[] x = (float[])_x;
			subframe += suboffset;// java
			for( int j = suboffset, joc = xoffset + offset * C + c1; j < subframe; j++, joc += C ) {
				y[j] = x[joc] * Jfloat_cast.CELT_SIG_SCALE;
			}
			if( c2 > -1 )
			{
				for( int j = suboffset, joc = xoffset + offset * C + c2; j < subframe; j++, joc += C ) {
					y[j] += x[joc] * Jfloat_cast.CELT_SIG_SCALE;
				}
			} else if( c2 == -2 )
			{
				for( int c = 1; c < C; c++ )
				{
					for( int j = suboffset, joc = xoffset + offset * C + c; j < subframe; j++, joc += C ) {
						y[j] += x[joc] * Jfloat_cast.CELT_SIG_SCALE;
					}
				}
			}
		}
	}
	static final Jdownmix_float downmix_float = new Jdownmix_float();
/*
	private static final void downmix_float(final float[] x, final float[] sub, final int subframe, final int offset, final int c1, final int c2, final int C)
	{
		for( int j = 0, joc = offset * C + c1; j < subframe; j++, joc += C ) {
			sub[j] = x[joc] * Jfloat_cast.CELT_SIG_SCALE;
		}
		if( c2 > -1 )
		{
			for( int j = 0, joc = offset * C + c2; j < subframe; j++, joc += C ) {
				sub[j] += x[joc] * Jfloat_cast.CELT_SIG_SCALE;
			}
		} else if( c2 == -2 )
		{
			for( int c = 1; c < C; c++ )
			{
				for( int j = 0, joc = offset * C + c; j < subframe; j++, joc += C ) {
					sub[j] += x[joc] * Jfloat_cast.CELT_SIG_SCALE;
				}
			}
		}
	}
*/
// #endif

	private static final class Jdownmix_int extends Idownmix {

		@Override
		void downmix(final Object _x, final int xoffset, final float[] y, final int suboffset, int subframe, final int offset, final int c1, final int c2, final int C) {
			final short[] x = (short[])_x;
			subframe += suboffset;// java
			for( int j = suboffset, joc = xoffset + offset * C + c1; j < subframe; j++, joc += C ) {
				y[j] = (float)x[joc];
			}
			if( c2 > -1 )
			{
				for( int j = suboffset, joc = xoffset + offset * C + c2; j < subframe; j++, joc += C ) {
					y[j] += (float)x[joc];
				}
			} else if( c2 == -2 )
			{
				for( int c = 1; c < C; c++ )
				{
					for( int j = suboffset, joc = xoffset + offset * C + c; j < subframe; j++, joc += C ) {
						y[j] += (float)x[joc];
					}
				}
			}
		}
	}
	static final Jdownmix_int downmix_int = new Jdownmix_int();
/*
	private static final void downmix_int(final short[] x, final float[] sub, final int subframe, final int offset, final int c1, final int c2, final int C)
	{
		for( int j = 0, joc = offset * C + c1; j < subframe; j++, joc += C ) {
			sub[j] = (float)x[joc];
		}
		if( c2 > -1 )
		{
			for( int j = 0, joc = offset * C + c2; j < subframe; j++, joc += C ) {
				sub[j] += (float)x[joc];
			}
		} else if( c2 == -2 )
		{
			for( int c = 1; c < C; c++ )
			{
				for( int j = 0, joc = offset * C + c; j < subframe; j++, joc += C ) {
					sub[j] += (float)x[joc];
				}
			}
		}
	}
*/
	static final int frame_size_select(final int frame_size, final int variable_duration, final int Fs)
	{
		int new_size;
		if( frame_size < Fs / 400 ) {
			return -1;
		}
		if( variable_duration == Jopus_defines.OPUS_FRAMESIZE_ARG ) {
			new_size = frame_size;
		} else if( variable_duration >= Jopus_defines.OPUS_FRAMESIZE_2_5_MS && variable_duration <= Jopus_defines.OPUS_FRAMESIZE_120_MS )
		{
			if( variable_duration <= Jopus_defines.OPUS_FRAMESIZE_40_MS ) {
				new_size = (Fs / 400) << (variable_duration - Jopus_defines.OPUS_FRAMESIZE_2_5_MS);
			} else {
				new_size = (variable_duration - Jopus_defines.OPUS_FRAMESIZE_2_5_MS - 2) * Fs / 50;
			}
		} else {
			return -1;
		}
		if( new_size > frame_size ) {
			return -1;
		}
		if( 400 * new_size != Fs     && 200 * new_size !=     Fs && 100 * new_size != Fs     &&
			 50 * new_size != Fs     &&  25 * new_size !=     Fs &&  50 * new_size != 3 * Fs &&
			 50 * new_size != 4 * Fs &&  50 * new_size != 5 * Fs &&  50 * new_size != 6 * Fs) {
			return -1;
		}
		return new_size;
	}

	private static final float compute_stereo_width(final float[] pcm, final int pcmoffset,// java
			int frame_size, final int Fs, final JStereoWidthState mem)
	{
		float xx, xy, yy;
		final int frame_rate = Fs / frame_size;
		final float short_alpha = Jfloat_cast.Q15ONE - 25 * Jfloat_cast.Q15ONE / (50 > frame_rate ? 50 : frame_rate);
		xx = xy = yy = 0;
		/* Unroll by 4. The frame size is always a multiple of 4 *except* for
		 2.5 ms frames at 12 kHz. Since this setting is very rare (and very
		 stupid), we just discard the last two samples. */
		frame_size -= 3;
		frame_size <<= 1;// java
		for( int i2 = 0; i2 < frame_size; /* i2 += 4 * 2 */ )
		{
			float x = pcm[i2++];
			float y = pcm[i2++];
			float pxx = x * x;
			float pxy = x * y;
			float pyy = y * y;
			x = pcm[i2++];
			y = pcm[i2++];
			pxx += x * x;
			pxy += x * y;
			pyy += y * y;
			x = pcm[i2++];
			y = pcm[i2++];
			pxx += x * x;
			pxy += x * y;
			pyy += y * y;
			x = pcm[i2++];
			y = pcm[i2++];
			pxx += x * x;
			pxy += x * y;
			pyy += y * y;

			xx += pxx;
			xy += pxy;
			yy += pyy;
		}
// #ifndef FIXED_POINT
		if( !(xx < 1e9f) || Float.isNaN( xx ) || !(yy < 1e9f) || Float.isNaN( yy ) )
		{
			xy = xx = yy = 0;
		}
// #endif
		mem.XX += short_alpha * (xx - mem.XX);
		mem.XY += short_alpha * (xy - mem.XY);
		mem.YY += short_alpha * (yy - mem.YY);
		mem.XX = 0 > mem.XX ? 0 : mem.XX;
		mem.XY = 0 > mem.XY ? 0 : mem.XY;
		mem.YY = 0 > mem.YY ? 0 : mem.YY;

		if( (mem.XX > mem.YY ? mem.XX : mem.YY) > 8e-4f )
		{
			final float sqrt_xx = (float)Math.sqrt( (double)mem.XX );
			final float sqrt_yy = (float)Math.sqrt( (double)mem.YY );
			final float qrrt_xx = (float)Math.sqrt( (double)sqrt_xx );
			final float qrrt_yy = (float)Math.sqrt( (double)sqrt_yy );
			/* Inter-channel correlation */
			float v = sqrt_xx * sqrt_yy;// java
			mem.XY = mem.XY <= v ? mem.XY : v;
			final float corr = mem.XY / (Jfloat_cast.EPSILON + sqrt_xx * sqrt_yy);
			/* Approximate loudness difference */
			v = qrrt_xx - qrrt_yy;// java
			if( v < 0 ) {
				v = -v;
			}
			final float ldiff = Jfloat_cast.Q15ONE * v / (Jfloat_cast.EPSILON + qrrt_xx + qrrt_yy);
			final float width = (float)Math.sqrt( (double)(1.f - corr * corr) ) * ldiff;
			/* Smoothing over one second */
			mem.smoothed_width += (width - mem.smoothed_width) / frame_rate;
			/* Peak follower */
			v = mem.max_follower - .02f / frame_rate;// java
			mem.max_follower = v > mem.smoothed_width ? v : mem.smoothed_width;
		}
		/*printf("%f %f %f %f %f ", corr/(float)Q15ONE, ldiff/(float)Q15ONE, width/(float)Q15ONE, mem.smoothed_width/(float)Q15ONE, mem.max_follower/(float)Q15ONE);*/
		final float v = 20 * mem.max_follower;// java
		return (Jfloat_cast.Q15ONE < v ? Jfloat_cast.Q15ONE : v);
	}

	/**
	 * java: returns ((boolean status) << 31) | bandwidth;
	 * to get the result:
	 * bandwidth = ret & 0x7FFFFFFF;
	 * status = (ret < 0);
	 *
	 * @param useInBandFEC
	 * @param PacketLoss_perc
	 * @param last_fec
	 * @param mode
	 * @param bandwidth
	 * @param rate
	 * @return
	 */
	private static final int decide_fec(final boolean useInBandFEC, final int PacketLoss_perc, final boolean last_fec, final int mode, int bandwidth, final int rate)
	{
		if( ! useInBandFEC || PacketLoss_perc == 0 || mode == Jopus_private.MODE_CELT_ONLY ) {
			return bandwidth;// java false;
		}
		final int orig_bandwidth = bandwidth;
		for( ; ; )
		{
			/* Compute threshold for using FEC at the current bandwidth setting */
			final int i = (bandwidth - Jopus_defines.OPUS_BANDWIDTH_NARROWBAND) << 1;// java
			int LBRR_rate_thres_bps = fec_thresholds[ i ];
			final int hysteresis = fec_thresholds[ i + 1 ];
			if( last_fec ) {
				LBRR_rate_thres_bps -= hysteresis;
			}
			if( ! last_fec ) {
				LBRR_rate_thres_bps += hysteresis;
			}
			LBRR_rate_thres_bps = (int)(((LBRR_rate_thres_bps * (125 - (PacketLoss_perc <= 25 ? PacketLoss_perc : 25)))
					* (long)(0.01 * (1 << 16) + 0.5)) >> 16);
			/* If loss <= 5%, we look at whether we have enough rate to enable FEC.
 			   If loss > 5%, we decrease the bandwidth until we can enable FEC. */
			if( rate > LBRR_rate_thres_bps ) {
				return 0x80000000 | bandwidth;// java true;
			} else if( PacketLoss_perc <= 5 ) {
				return bandwidth;// java false;
			} else if( bandwidth > Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
				bandwidth--;
			} else {
				break;
			}
		}
		/* Couldn't find any bandwidth to enable FEC, keep original bandwidth. */
		// bandwidth = orig_bandwidth;
		// return false;
		return orig_bandwidth;// java 0 | bandwidth
	}

	private static final int rate_table[][/* 5 */] = {
		/*  |total| |-------- SILK------------|
      				|-- No FEC -| |--- FEC ---|
       				10ms   20ms   10ms   20ms */
		{    0,     0,     0,     0,     0},
		{12000, 10000, 10000, 11000, 11000},
		{16000, 13500, 13500, 15000, 15000},
		{20000, 16000, 16000, 18000, 18000},
		{24000, 18000, 18000, 21000, 21000},
		{32000, 22000, 22000, 28000, 28000},
		{64000, 38000, 38000, 50000, 50000}
	};

	private static final int compute_silk_rate_for_hybrid(int rate, final int bandwidth, final boolean frame20ms, final boolean vbr, final boolean fec, final int channels) {
		int i;
		int silk_rate;
		/* Do the allocation per-channel. */
		rate /= channels;
		final int entry = 1 + (frame20ms ? 1 : 0) + (fec ? 2 : 0);// java frame20ms + (fec << 1);
		final int N = rate_table.length;
		for( i = 1; i < N; i++ )
		{
			if( rate_table[i][0] > rate ) {
				break;
			}
		}
		if( i == N )
		{
			silk_rate = rate_table[i - 1][entry];
			/* For now, just give 50% of the extra bits to SILK. */
			silk_rate += (rate - rate_table[i - 1][0]) / 2;
		} else {
			final int lo = rate_table[i - 1][entry];
			final int hi = rate_table[i][entry];
			final int x0 = rate_table[i - 1][0];
			final int x1 = rate_table[i][0];
			silk_rate = (lo * (x1 - rate) + hi * (rate - x0)) / (x1 - x0);
		}
		if( ! vbr )
		{
			/* Tiny boost to SILK for CBR. We should probably tune this better. */
			silk_rate += 100;
		}
		if( bandwidth == Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND ) {
			silk_rate += 300;
		}
		silk_rate *= channels;
		/* Small adjustment for stereo (calibrated for 32 kb/s, haven't tried other bitrates). */
		if( channels == 2 && rate >= 12000 ) {
			silk_rate -= 1000;
		}
		return silk_rate;
	}

	/**
	 * Returns the equivalent bitrate corresponding to 20 ms frames,
	 * complexity 10 VBR operation.
	 * @param bitrate
	 * @param channels
	 * @param frame_rate
	 * @param vbr
	 * @param mode
	 * @param complexity
	 * @param loss
	 * @return equiv_rate
	 */
	private static final int compute_equiv_rate(final int bitrate, final int channels,
			final int frame_rate, final boolean vbr, final int mode, final int complexity, final int loss)
	{
		int equiv = bitrate;
		/* Take into account overhead from smaller frames. */
		if( frame_rate > 50 ) {
			equiv -= (40 * channels + 20) * (frame_rate - 50);
		}
		/* CBR is about a 8% penalty for both SILK and CELT. */
		if( ! vbr ) {
			equiv -= equiv / 12;
		}
		/* Complexity makes about 10% difference (from 0 to 10) in general. */
		equiv = equiv * (90 + complexity) / 100;
		if( mode == Jopus_private.MODE_SILK_ONLY || mode == Jopus_private.MODE_HYBRID )
		{
			/* SILK complexity 0-1 uses the non-delayed-decision NSQ, which costs about 20%. */
			if( complexity < 2 ) {
				equiv = (equiv << 2) / 5;
			}
			equiv -= equiv * loss / (6 * loss + 10);
		} else if( mode == Jopus_private.MODE_CELT_ONLY ) {
			/* CELT complexity 0-4 doesn't have the pitch filter, which costs about 10%. */
			if( complexity < 5 ) {
				equiv = equiv * 9 / 10;
			}
		} else {
			/* Mode not known yet */
			/* Half the SILK loss*/
			equiv -= equiv * loss / (12 * loss + 20);
		}
		return equiv;
	}

// #ifndef DISABLE_FLOAT_API

	private static final boolean is_digital_silence(final float[] pcm, final int pcmoffset,// java
			final int frame_size, final int channels, final int lsb_depth)
	{
/* #ifdef MLP_TRAINING
		return 0;
#endif */
   		final float sample_max = JCELTEncoder.celt_maxabs16( pcm, pcmoffset, frame_size * channels );

/* #ifdef FIXED_POINT
   		silence = (sample_max == 0);
		(void)lsb_depth;
#else */
   		final boolean silence = (sample_max <= (float) 1 / (1 << lsb_depth));
// #endif

		return silence;
	}

/* #ifdef FIXED_POINT
	static opus_val32 compute_frame_energy(const opus_val16 *pcm, int frame_size, int channels, int arch)
	{
		int i;
		opus_val32 sample_max;
		int max_shift;
		int shift;
		opus_val32 energy = 0;
		int len = frame_size*channels;
		(void)arch;
		// Max amplitude in the signal
		sample_max = celt_maxabs16(pcm, len);

		// Compute the right shift required in the MAC to avoid an overflow
		max_shift = celt_ilog2(len);
		shift = IMAX(0, (celt_ilog2(sample_max) << 1) + max_shift - 28);

		// Compute the energy
		for (i=0; i<len; i++)
			energy += SHR32(MULT16_16(pcm[i], pcm[i]), shift);

		// Normalize energy by the frame size and left-shift back to the original position
		energy /= len;
		energy = SHL32(energy, shift);

		return energy;
	}
#else */
	private static final float compute_frame_energy(final float[] pcm, final int pcmoffset, final int frame_size, final int channels)//, int arch)
	{
		final int len = frame_size * channels;
		return Jcelt_codec_API.celt_inner_prod( pcm, pcmoffset, pcm, pcmoffset, len/*, arch*/) / len;
	}
// #endif

	/**
	 * Decides if DTX should be turned on (=1) or off (=0)
	 *
	 * @param activity_probability probability that current frame contains speech/music
	 * @param nb_no_activity_frames number of consecutive frames with no activity
	 * @param peak_signal_energy peak energy of desired signal detected so far
	 * @param pcm input pcm signal
	 * @param pcmoffset java offset for the pcm
	 * @param frame_size frame size
	 * @param channels
	 * @param is_silence only digital silence detected in this frame
	 * @param arch
	 * @return java: returns ((boolean status) << 31) | nb_no_activity_frames;<br>
	 * to get the result:<br>
	 * nb_no_activity_frames = ret & 0x7FFFFFFF;<br>
	 * status = (ret < 0);
	 */
	private static final int decide_dtx_mode(final float activity_probability,
			int nb_no_activity_frames,
			final float peak_signal_energy,
			final float[] pcm, final int pcmoffset,// java
			final int frame_size,
			final int channels,
			boolean is_silence)//, int arch)
	{
		float noise_energy;

		if( ! is_silence )
		{
			if( activity_probability < Jdefine.DTX_ACTIVITY_THRESHOLD )  /* is noise */
			{
				noise_energy = compute_frame_energy( pcm, pcmoffset, frame_size, channels );// , arch);

				/* but is sufficiently quiet */
				is_silence = peak_signal_energy >= (PSEUDO_SNR_THRESHOLD * noise_energy);
			}
		}

		if( is_silence )
		{
			/* The number of consecutive DTX frames should be within the allowed bounds */
			nb_no_activity_frames++;

			if( nb_no_activity_frames > Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX)
			{
				if( nb_no_activity_frames <= (Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX + Jdefine.MAX_CONSECUTIVE_DTX)) {
					/* Valid frame for DTX! */
					return 0x80000000 | nb_no_activity_frames;// java 1;
				} else {
					nb_no_activity_frames = Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX;
				}
			}
		} else {
			nb_no_activity_frames = 0;
		}

		return nb_no_activity_frames;// java 0;
	}

// #endif

	private final int encode_multiframe_packet(// final OpusEncoder *st,
			final float[] pcm, int pcmoffset,// java
			int nb_frames,
			final int frame_size,
			final byte[] data, final int doffset, // java // unsigned
			final int out_data_bytes,
			final boolean to_celt,
			final int lsbdepth,// java lsb_depth is changed to avoid hiding
			final boolean float_api)
	{
		int ret = 0;
		int repacketize_len;
		// ALLOC_STACK;

		/* Worst cases:
		 * 2 frames: Code 2 with different compressed sizes
		 * >2 frames: Code 3 VBR */
		final int max_header_bytes = nb_frames == 2 ? 3 : (2 + ((nb_frames - 1) << 1));

		if( this.use_vbr || this.user_bitrate_bps == Jopus_defines.OPUS_BITRATE_MAX ) {
			repacketize_len = out_data_bytes;
		} else {
			final int cbr_bytes = 3 * this.bitrate_bps / (3 * 8 * this.Fs / (frame_size * nb_frames));
			repacketize_len = (cbr_bytes <= out_data_bytes ? cbr_bytes : out_data_bytes);
		}
		int bytes_per_frame = 1 + (repacketize_len - max_header_bytes) / nb_frames;// java
		bytes_per_frame = (1276 <= bytes_per_frame ? 1276 : bytes_per_frame);

		final byte[] tmp_data = new byte[ nb_frames * bytes_per_frame ];// unsigned
		final JOpusRepacketizer rp = new JOpusRepacketizer();
		rp.opus_repacketizer_init();

		final int bak_mode = this.user_forced_mode;
		final int bak_bandwidth = this.user_bandwidth;
		final int bak_channels = this.force_channels;

		this.user_forced_mode = this.mode;
		this.user_bandwidth = this.bandwidth;
		this.force_channels = this.stream_channels;

		final boolean bak_to_mono = this.silk_mode.toMono;
		if( bak_to_mono ) {
			this.force_channels = 1;
		} else {
			this.prev_channels = this.stream_channels;
		}

		nb_frames--;// java
		for( int i = 0, tmp_data_offset = 0, fi = this.channels * frame_size;
				i <= nb_frames;
				i++, pcmoffset += fi, tmp_data_offset += bytes_per_frame )
		{
			this.silk_mode.toMono = false;
			this.nonfinal_frame = i < nb_frames;// java i < (nb_frames - 1);

			/* When switching from SILK/Hybrid to CELT, only ask for a switch at the last frame */
			if( to_celt && i == nb_frames ) {// java if( to_celt && i == nb_frames - 1 ) {
				this.user_forced_mode = Jopus_private.MODE_CELT_ONLY;
			}

			final int tmp_len = opus_encode_native( pcm, pcmoffset, frame_size,
					tmp_data, tmp_data_offset, bytes_per_frame, lsbdepth, null, 0, 0, 0, 0, 0,
					null, float_api );

			if( tmp_len < 0 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}

			ret = rp.opus_repacketizer_cat( tmp_data, tmp_data_offset, tmp_len );

			if( ret < 0 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
		}

		ret = rp.opus_repacketizer_out_range_impl( 0, ++nb_frames, data, doffset, repacketize_len, false, ! this.use_vbr );

		if( ret < 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}

		/* Discard configs that were forced locally for the purpose of repacketization */
		this.user_forced_mode = bak_mode;
		this.user_bandwidth = bak_bandwidth;
		this.force_channels = bak_channels;
		this.silk_mode.toMono = bak_to_mono;

		// RESTORE_STACK;
		return ret;
	}

	private static final int compute_redundancy_bytes(final int max_data_bytes, final int bitrate_bps, final int frame_rate, final int channels)
	{
		final int base_bits = (40 * channels + 20);

		/* Equivalent rate for 5 ms frames. */
		int redundancy_rate = bitrate_bps + base_bits * (200 - frame_rate);
		/* For VBR, further increase the bitrate if we can afford it. It's pretty short
	      and we'll avoid artefacts. */
		redundancy_rate = (3 * redundancy_rate) >> 1;
		int redundancy_bytes = redundancy_rate / 1600;

		/* Compute the max rate we can use given CBR or VBR with cap. */
		final int available_bits = (max_data_bytes << 3) - (base_bits << 1);
		final int redundancy_bytes_cap = (available_bits * 240 / (240 + 48000 / frame_rate) + base_bits) >> 3;
		redundancy_bytes = (redundancy_bytes <= redundancy_bytes_cap ? redundancy_bytes : redundancy_bytes_cap);
		/* It we can't get enough bits for redundancy to be worth it, rely on the decoder PLC. */
		if( redundancy_bytes > 4 + (channels << 3)) {
			return (257 <= redundancy_bytes ? 257 : redundancy_bytes);
		}
		// else
			return 0;
		// return redundancy_bytes;
	}

	final int opus_encode_native(
			final float[] pcm, final int pcmoffset,// java
			final int frame_size,
			final byte[] data, int doffset,// java
			final int out_data_bytes, int lsbdepth,
			final Object analysis_pcm, final int aoffset,// java
			final int analysis_size, final int c1, final int c2,
			final int analysis_channels, final Idownmix/*downmix_func*/ downmix, final boolean float_api)
	{
		int ret = 0;
		// ALLOC_STACK;

		int max_data_bytes = 1276 < out_data_bytes ? 1276 : out_data_bytes; /* Max number of bytes we're allowed to use */

		this.rangeFinal = 0;
		if( frame_size <= 0 || max_data_bytes <= 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		/* Cannot encode 100 ms in 1 byte */
		if( max_data_bytes == 1 && this.Fs == (frame_size * 10) )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
		}

		final Jsilk_encoder silkenc = this.silk_enc;
		final JCELTEncoder celtenc = this.celt_enc;
		final int delay_comp = (this.application == Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY) ? 0 : this.delay_compensation;

		lsbdepth = lsbdepth < this.lsb_depth ? lsbdepth : this.lsb_depth;

		final Object[] request = new Object[1];// java helper
		celtenc.celt_encoder_ctl( Jcelt.CELT_GET_MODE, request );
		final JCELTMode celt_mode = (JCELTMode)request[0];// java
// #ifndef DISABLE_FLOAT_API
		final JAnalysisInfo analysis_info = new JAnalysisInfo();
		int analysis_read_pos_bak = -1;
		int analysis_read_subframe_bak = -1;
		boolean is_silence = false;
		analysis_info.valid = false;
/* #ifdef FIXED_POINT
		if( st.silk_mode.complexity >= 10  &&  st.Fs >= 16000 )
#else */
		if( this.silk_mode.complexity >= 7 && this.Fs >= 16000 )
// #endif
		{
			if( is_digital_silence( pcm, pcmoffset, frame_size, this.channels, lsbdepth ) )
			{
				is_silence = true;
			} else {
				analysis_read_pos_bak = this.analysis.read_pos;
				analysis_read_subframe_bak = this.analysis.read_subframe;
				this.analysis.run_analysis( celt_mode, analysis_pcm, aoffset, analysis_size, frame_size,
						c1, c2, analysis_channels, this.Fs,
						lsbdepth, downmix, analysis_info );
			}

			/* Track the peak signal energy */
			if( ! is_silence && analysis_info.activity_probability > Jdefine.DTX_ACTIVITY_THRESHOLD ) {
				final float v1 = (0.999f * this.peak_signal_energy);// java
				final float v2 = compute_frame_energy( pcm, pcmoffset, frame_size, this.channels );// , this.arch);// java
				this.peak_signal_energy = (v1 >= v2 ? v1 : v2);
			}
		}
/* #else
		(void)analysis_pcm;
		(void)analysis_size;
		(void)c1;
		(void)c2;
		(void)analysis_channels;
		(void)downmix;
#endif */

// #ifndef DISABLE_FLOAT_API
		/* Reset voice_ratio if this frame is not silent or if analysis is disabled.
		 * Otherwise, preserve voice_ratio from the last non-silent frame */
		if( ! is_silence ) {
			this.voice_ratio = -1;
		}

		this.detected_bandwidth = 0;
		if( analysis_info.valid )
		{
			if( this.signal_type == Jopus_defines.OPUS_AUTO )
			{
				float prob;
				if( this.prev_mode == 0 ) {
					prob = analysis_info.music_prob;
				} else if( this.prev_mode == Jopus_private.MODE_CELT_ONLY ) {
					prob = analysis_info.music_prob_max;
				} else {
					prob = analysis_info.music_prob_min;
				}
				this.voice_ratio = (int)Math.floor( (double)(.5f + 100.f * (1.f - prob)) );
			}

			final int analysis_bandwidth = analysis_info.bandwidth;
			if( analysis_bandwidth <= 12 ) {
				this.detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
			} else if( analysis_bandwidth <= 14 ) {
				this.detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
			} else if( analysis_bandwidth <= 16 ) {
				this.detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
			} else if( analysis_bandwidth <= 18 ) {
				this.detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
			} else {
				this.detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
			}
		}
// #else
//		this.voice_ratio = -1;
// #endif

		final float stereo_width = (this.channels == 2 && this.force_channels != 1) ?
					compute_stereo_width( pcm, pcmoffset, frame_size, this.Fs, this.width_mem ) : 0;

		final int total_buffer = delay_comp;
		this.bitrate_bps = user_bitrate_to_bitrate( frame_size, max_data_bytes );

		int frame_rate = this.Fs / frame_size;
		if( ! this.use_vbr )
		{
			/* Multiply by 12 to make sure the division is exact. */
			final int frame_rate12 = 12 * this.Fs/frame_size;
			/* We need to make sure that "int" values always fit in 16 bits. */
			int cbrBytes = (((12 * this.bitrate_bps) >>> 3) + (frame_rate12 >> 1)) / frame_rate12;// java
			cbrBytes = ( cbrBytes <= max_data_bytes ? cbrBytes : max_data_bytes );
			this.bitrate_bps = ((cbrBytes * frame_rate12) << 3) / 12;
			/* Make sure we provide at least one byte to avoid failing. */
			max_data_bytes = (1 >= cbrBytes ? 1 : cbrBytes);
		}
		if( max_data_bytes < 3 || this.bitrate_bps < ((3 * frame_rate) << 3)
			|| (frame_rate < 50 && (max_data_bytes * frame_rate < 300 || this.bitrate_bps < 2400)) )
		{
			/*If the space is too low to do something useful, emit 'PLC' frames.*/
			int tocmode = this.mode;
			int bw = this.bandwidth == 0 ? Jopus_defines.OPUS_BANDWIDTH_NARROWBAND : this.bandwidth;
			int packet_code = 0;
			int num_multiframes = 0;

			if( tocmode == 0 ) {
				tocmode = Jopus_private.MODE_SILK_ONLY;
			}
			if( frame_rate > 100 ) {
				tocmode = Jopus_private.MODE_CELT_ONLY;
			}
			/* 40 ms -> 2 x 20 ms if in CELT_ONLY or HYBRID mode */
			if( frame_rate == 25 && tocmode != Jopus_private.MODE_SILK_ONLY )
			{
				frame_rate = 50;
				packet_code = 1;
			}

			/* >= 60 ms frames */
			if( frame_rate <= 16 )
			{
				/* 1 x 60 ms, 2 x 40 ms, 2 x 60 ms */
				if( out_data_bytes == 1 || (tocmode == Jopus_private.MODE_SILK_ONLY && frame_rate != 10) )
				{
					tocmode = Jopus_private.MODE_SILK_ONLY;

					packet_code = (frame_rate <= 12) ? 1 : 0;
					frame_rate = frame_rate == 12 ? 25 : 16;
				}
				else
				{
					num_multiframes = 50 / frame_rate;
					frame_rate = 50;
					packet_code = 3;
				}
			}

			if( tocmode == Jopus_private.MODE_SILK_ONLY && bw > Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
				bw = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
			} else if( tocmode == Jopus_private.MODE_CELT_ONLY && bw == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND) {
				bw = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
			} else if( tocmode == Jopus_private.MODE_HYBRID && bw <= Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND) {
				bw = Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
			}

			data[doffset] = (byte)gen_toc( tocmode, frame_rate, bw, this.stream_channels );
			data[doffset] |= packet_code;

			ret = packet_code <= 1 ? 1 : 2;

			max_data_bytes = (max_data_bytes >= ret ? max_data_bytes : ret);

			if( packet_code == 3 ) {
				data[doffset + 1] = (byte)num_multiframes;
			}

			if( ! this.use_vbr )
			{
				ret = JOpusRepacketizer.opus_packet_pad( data, doffset, ret, max_data_bytes );
				if( ret == Jopus_defines.OPUS_OK ) {
					ret = max_data_bytes;
				} else {
					ret = Jopus_defines.OPUS_INTERNAL_ERROR;
				}
			}
			// RESTORE_STACK;
			return ret;
		}
		final int max_rate = (frame_rate * max_data_bytes) << 3; /* Max bitrate we're allowed to use */

		/* Equivalent 20-ms rate for mode/channel/bandwidth decisions */
		int equiv_rate = compute_equiv_rate( this.bitrate_bps, this.channels, this.Fs / frame_size,
				this.use_vbr, 0, this.silk_mode.complexity, this.silk_mode.packetLossPercentage );

		int voice_est; /* Probability of voice in Q7 */
		if( this.signal_type == Jopus_defines.OPUS_SIGNAL_VOICE ) {
			voice_est = 127;
		} else if( this.signal_type == Jopus_defines.OPUS_SIGNAL_MUSIC ) {
			voice_est = 0;
		} else if( this.voice_ratio >= 0 )
		{
			voice_est = this.voice_ratio * 327 >> 8;
			/* For AUDIO, never be more than 90% confident of having speech */
			if( this.application == Jopus_defines.OPUS_APPLICATION_AUDIO ) {
				voice_est = voice_est < 115 ? voice_est : 115;
			}
		} else if( this.application == Jopus_defines.OPUS_APPLICATION_VOIP) {
			voice_est = 115;
		} else {
			voice_est = 48;
		}

		if( this.force_channels != Jopus_defines.OPUS_AUTO && this.channels == 2 )
		{
			this.stream_channels = this.force_channels;
		} else {
/* #ifdef FUZZING
			// Random mono/stereo decision
			if( st.channels == 2 && (rand() & 0x1F) == 0 )
				st.stream_channels = 3 - st.stream_channels;
#else */
			/* Rate-dependent mono-stereo decision */
			if( this.channels == 2 )
			{
				int stereo_threshold = stereo_music_threshold + ((voice_est * voice_est * (stereo_voice_threshold - stereo_music_threshold)) >> 14);
				if( this.stream_channels == 2 ) {
					stereo_threshold -= 1000;
				} else {
					stereo_threshold += 1000;
				}
				this.stream_channels = (equiv_rate > stereo_threshold) ? 2 : 1;
			} else {
				this.stream_channels = this.channels;
			}
// #endif
		}
		/* Update equivalent rate for channels decision. */
		equiv_rate = compute_equiv_rate( this.bitrate_bps, this.stream_channels, this.Fs / frame_size,
				this.use_vbr, 0, this.silk_mode.complexity, this.silk_mode.packetLossPercentage );

		/* Mode selection depending on application and signal type */
		if( this.application == Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY )
		{
			this.mode = Jopus_private.MODE_CELT_ONLY;
		} else if( this.user_forced_mode == Jopus_defines.OPUS_AUTO )
		{
/* #ifdef FUZZING
			// Random mode switching
			if( (rand() & 0xF) == 0 )
			{
				if( (rand() & 0x1) == 0 )
					st.mode = MODE_CELT_ONLY;
				else
					st.mode = MODE_SILK_ONLY;
			} else {
				if( st.prev_mode == MODE_CELT_ONLY )
					st.mode = MODE_CELT_ONLY;
				else
					st.mode = MODE_SILK_ONLY;
			}
#else */
			/* Interpolate based on stereo width */
			final int mode_voice = (int)((Jfloat_cast.Q15ONE - stereo_width) * mode_thresholds[0][0]
									+ stereo_width * mode_thresholds[1][0] );
			final int mode_music = (int)((Jfloat_cast.Q15ONE - stereo_width) * mode_thresholds[1][1]
									+ stereo_width * mode_thresholds[1][1]);
			/* Interpolate based on speech/music probability */
			int threshold = mode_music + ((voice_est * voice_est * (mode_voice - mode_music)) >> 14);
			/* Bias towards SILK for VoIP because of some useful features */
			if( this.application == Jopus_defines.OPUS_APPLICATION_VOIP ) {
				threshold += 8000;
			}

			/*printf("%f %d\n", stereo_width/(float)Q15ONE, threshold);*/
			/* Hysteresis */
			if( this.prev_mode == Jopus_private.MODE_CELT_ONLY ) {
				threshold -= 4000;
			} else if( this.prev_mode > 0 ) {
				threshold += 4000;
			}

			this.mode = (equiv_rate >= threshold) ? Jopus_private.MODE_CELT_ONLY : Jopus_private.MODE_SILK_ONLY;

			/* When FEC is enabled and there's enough packet loss, use SILK */
			if( this.silk_mode.useInBandFEC && this.silk_mode.packetLossPercentage > (128 - voice_est) >> 4 ) {
				this.mode = Jopus_private.MODE_SILK_ONLY;
			}
			/* When encoding voice and DTX is enabled but the generalized DTX cannot be used,
  			   because of complexity and sampling frequency settings, switch to SILK DTX and
			   set the encoder to SILK mode */
// #ifndef DISABLE_FLOAT_API
			this.silk_mode.useDTX = this.use_dtx && !(analysis_info.valid || is_silence);
/* #else
			this.silk_mode.useDTX = this.use_dtx;
#endif */
			if( this.silk_mode.useDTX && voice_est > 100 ) {
				this.mode = Jopus_private.MODE_SILK_ONLY;
			}
// #endif

			/* If max_data_bytes represents less than 6 kb/s, switch to CELT-only mode */
			if( max_data_bytes < (frame_rate > 50 ? 9000 : 6000) * frame_size / (this.Fs << 3) ) {
				this.mode = Jopus_private.MODE_CELT_ONLY;
			}
		} else {
			this.mode = this.user_forced_mode;
		}

		/* Override the chosen mode to make sure we meet the requested frame size */
		if( this.mode != Jopus_private.MODE_CELT_ONLY && frame_size < this.Fs / 100 ) {
			this.mode = Jopus_private.MODE_CELT_ONLY;
		}
		if( this.lfe ) {
			this.mode = Jopus_private.MODE_CELT_ONLY;
		}

		boolean to_celt = false;
		boolean redundancy = false;
		boolean celt_to_silk = false;
		if( this.prev_mode > 0 &&
			((this.mode != Jopus_private.MODE_CELT_ONLY && this.prev_mode == Jopus_private.MODE_CELT_ONLY) ||
			(this.mode == Jopus_private.MODE_CELT_ONLY && this.prev_mode != Jopus_private.MODE_CELT_ONLY)) )
		{
			redundancy = true;
			celt_to_silk = (this.mode != Jopus_private.MODE_CELT_ONLY);
			if( ! celt_to_silk )
			{
				/* Switch to SILK/hybrid if frame size is 10 ms or more*/
				if( frame_size >= this.Fs / 100 )
				{
					this.mode = this.prev_mode;
					to_celt = true;
				} else {
					redundancy = false;
				}
			}
		}

		/* When encoding multiframes, we can ask for a switch to CELT only in the last frame. This switch
		 * is processed above as the requested mode shouldn't interrupt stereo->mono transition. */
		if (this.stream_channels == 1 && this.prev_channels == 2 && ! this.silk_mode.toMono
				&& this.mode != Jopus_private.MODE_CELT_ONLY && this.prev_mode != Jopus_private.MODE_CELT_ONLY )
		{
			/* Delay stereo->mono transition by two frames so that SILK can do a smooth downmix */
			this.silk_mode.toMono = true;
			this.stream_channels = 2;
		} else {
			this.silk_mode.toMono = false;
		}

		/* Update equivalent rate with mode decision. */
		equiv_rate = compute_equiv_rate(this.bitrate_bps, this.stream_channels, this.Fs/frame_size,
				this.use_vbr, this.mode, this.silk_mode.complexity, this.silk_mode.packetLossPercentage);

		int prefill = 0;
		if( this.mode != Jopus_private.MODE_CELT_ONLY && this.prev_mode == Jopus_private.MODE_CELT_ONLY )
		{
			final Jsilk_EncControlStruct dummy = new Jsilk_EncControlStruct();
			silkenc.silk_InitEncoder( /*, st.arch*/ dummy );
			prefill = 1;
		}

		/* Automatic (rate-dependent) bandwidth selection */
		if( this.mode == Jopus_private.MODE_CELT_ONLY || this.first || this.silk_mode.allowBandwidthSwitch )
		{
			final int[] voice_bandwidth_thresholds, music_bandwidth_thresholds;
			final int bandwidth_thresholds[] = new int[8];
			int band = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;// java renamed

			if( this.channels == 2 && this.force_channels != 1 )
			{
				voice_bandwidth_thresholds = stereo_voice_bandwidth_thresholds;
				music_bandwidth_thresholds = stereo_music_bandwidth_thresholds;
			} else {
				voice_bandwidth_thresholds = mono_voice_bandwidth_thresholds;
				music_bandwidth_thresholds = mono_music_bandwidth_thresholds;
			}
			/* Interpolate bandwidth thresholds depending on voice estimation */
			for( int i = 0; i < 8; i++ )
			{
				bandwidth_thresholds[i] = music_bandwidth_thresholds[i]
						+ ((voice_est * voice_est * (voice_bandwidth_thresholds[i] - music_bandwidth_thresholds[i])) >> 14);
			}
			do {
				int i = (band - Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND) << 1;// java
				int threshold = bandwidth_thresholds[i++];
				final int hysteresis = bandwidth_thresholds[i];
				if( ! this.first )
				{
					if( this.auto_bandwidth >= band ) {
						threshold -= hysteresis;
					} else {
						threshold += hysteresis;
					}
				}
				if( equiv_rate >= threshold ) {
					break;
				}
			} while( --band > Jopus_defines.OPUS_BANDWIDTH_NARROWBAND );
			/* We don't use mediumband anymore, except when explicitly requested or during
	           mode transitions. */
			if( band == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
				band = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
			}
			this.bandwidth = this.auto_bandwidth = band;
			/* Prevents any transition to SWB/FB until the SILK layer has fully
			   switched to WB mode and turned the variable LP filter off */
			if( ! this.first && this.mode != Jopus_private.MODE_CELT_ONLY && ! this.silk_mode.inWBmodeWithoutVariableLP && this.bandwidth > Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
				this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
			}
		}

		if( this.bandwidth > this.max_bandwidth ) {
			this.bandwidth = this.max_bandwidth;
		}

		if( this.user_bandwidth != Jopus_defines.OPUS_AUTO ) {
			this.bandwidth = this.user_bandwidth;
		}

		/* This prevents us from using hybrid at unsafe CBR/max rates */
		if( this.mode != Jopus_private.MODE_CELT_ONLY && max_rate < 15000 )
		{
			this.bandwidth = this.bandwidth < Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ? this.bandwidth : Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
		}

		/* Prevents Opus from wasting bits on frequencies that are above
		   the Nyquist rate of the input signal */
		if( this.Fs <= 24000 && this.bandwidth > Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
		}
		if( this.Fs <= 16000 && this.bandwidth > Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
		}
		if( this.Fs <= 12000 && this.bandwidth > Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
		}
		if( this.Fs <= 8000 && this.bandwidth > Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
		}
// #ifndef DISABLE_FLOAT_API
		/* Use detected bandwidth to reduce the encoded bandwidth. */
		if( 0 != this.detected_bandwidth && this.user_bandwidth == Jopus_defines.OPUS_AUTO )
		{
			int min_detected_bandwidth;
			/* Makes bandwidth detection more conservative just in case the detector
			   gets it wrong when we could have coded a high bandwidth transparently.
			   When operating in SILK/hybrid mode, we don't go below wideband to avoid
			   more complicated switches that require redundancy. */
			if( equiv_rate <= 18000 * this.stream_channels && this.mode == Jopus_private.MODE_CELT_ONLY ) {
				min_detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
			} else if( equiv_rate <= 24000 * this.stream_channels && this.mode == Jopus_private.MODE_CELT_ONLY ) {
				min_detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
			} else if( equiv_rate <= 30000 * this.stream_channels ) {
				min_detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
			} else if( equiv_rate <= 44000 * this.stream_channels ) {
				min_detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
			} else {
				min_detected_bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
			}

			this.detected_bandwidth = this.detected_bandwidth > min_detected_bandwidth ? this.detected_bandwidth : min_detected_bandwidth;
			this.bandwidth = this.bandwidth < this.detected_bandwidth ? this.bandwidth : this.detected_bandwidth;
		}
// #endif
		// java return is changed. return ((status << 31) | this.bandwidth)
		int v = decide_fec( this.silk_mode.useInBandFEC, this.silk_mode.packetLossPercentage,
				this.silk_mode.LBRR_coded, this.mode, this.bandwidth, equiv_rate );
		this.bandwidth = v & 0x7fffffff;
		this.silk_mode.LBRR_coded = (v < 0);
		celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_LSB_DEPTH, lsbdepth );

		/* CELT mode doesn't support mediumband, use wideband instead */
		if( this.mode == Jopus_private.MODE_CELT_ONLY && this.bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
		}
		if( this.lfe ) {
			this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
		}

		int curr_bandwidth = this.bandwidth;

		/* Chooses the appropriate mode for speech
		   *NEVER* switch to/from CELT-only mode here as this will invalidate some assumptions */
		if( this.mode == Jopus_private.MODE_SILK_ONLY && curr_bandwidth > Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
			this.mode = Jopus_private.MODE_HYBRID;
		}
		if( this.mode == Jopus_private.MODE_HYBRID && curr_bandwidth <= Jopus_defines.OPUS_BANDWIDTH_WIDEBAND ) {
			this.mode = Jopus_private.MODE_SILK_ONLY;
		}

		/* Can't support higher than >60 ms frames, and >20 ms when in Hybrid or CELT-only modes */
		if( (frame_size > this.Fs / 50 && (this.mode != Jopus_private.MODE_SILK_ONLY)) || frame_size > 3 * this.Fs / 50 )
		{
			int enc_frame_size;

			if( this.mode == Jopus_private.MODE_SILK_ONLY )
			{
				if( frame_size == (this.Fs << 1) / 25 ) {
					enc_frame_size = this.Fs / 25;
				} else if (frame_size == 3 * this.Fs / 25 ) {
					enc_frame_size = 3 * this.Fs / 50;
				} else {
					enc_frame_size = this.Fs / 50;
				}
			} else {
				enc_frame_size = this.Fs / 50;
			}

			final int nb_frames = frame_size / enc_frame_size;

// #ifndef DISABLE_FLOAT_API
			if( analysis_read_pos_bak != -1 )
			{
				this.analysis.read_pos = analysis_read_pos_bak;
				this.analysis.read_subframe = analysis_read_subframe_bak;
			}
// #endif

			ret = encode_multiframe_packet( pcm, pcmoffset, nb_frames, enc_frame_size, data, doffset,
					out_data_bytes, to_celt, lsbdepth, float_api );

			// RESTORE_STACK;
			return ret;
		}
		/* For the first frame at a new SILK bandwidth */
		if( this.silk_bw_switch )
		{
			redundancy = true;
			celt_to_silk = true;
			this.silk_bw_switch = false;
			/* Do a prefill without reseting the sampling rate control. */
			prefill = 2;
		}

		/* If we decided to go with CELT, make sure redundancy is off, no matter what
	  	   we decided earlier. */
		if( this.mode == Jopus_private.MODE_CELT_ONLY ) {
			redundancy = false;
		}

		int redundancy_bytes = 0; /* Number of bytes to use for redundancy frame */
		if( redundancy )
		{
			redundancy_bytes = compute_redundancy_bytes( max_data_bytes, this.bitrate_bps, frame_rate, this.stream_channels );
			if( redundancy_bytes == 0 ) {
				redundancy = false;
			}
		}

		/* printf("%d %d %d %d\n", st.bitrate_bps, st.stream_channels, st.mode, curr_bandwidth); */
		v = max_data_bytes - redundancy_bytes;// java
		int bytes_target = this.bitrate_bps * frame_size / (this.Fs << 3);
		bytes_target = ( v <= bytes_target ? v : bytes_target ) - 1;

		doffset++;// data += 1;

		final Jec_enc enc = new Jec_enc();
		enc.ec_enc_init( data, doffset, max_data_bytes - 1 );

		final float[] pcm_buf = new float[(total_buffer + frame_size) * this.channels];
		System.arraycopy( this.delay_buffer, (this.encoder_buffer - total_buffer) * this.channels, pcm_buf, 0, total_buffer * this.channels );

		final int hp_freq_smth1 = ( this.mode == Jopus_private.MODE_CELT_ONLY ) ?
					Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ ) << 8
					:
					silkenc.state_Fxx[0].sCmn.variable_HP_smth1_Q15;

		// this.variable_HP_smth2_Q15 = Jmacros.silk_SMLAWB( this.variable_HP_smth2_Q15,
		//		hp_freq_smth1 - this.variable_HP_smth2_Q15, SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_SMTH_COEF2, 16 ) );
		this.variable_HP_smth2_Q15 += (int)(((hp_freq_smth1 - this.variable_HP_smth2_Q15) *
							(long)(Jtuning_parameters.VARIABLE_HP_SMTH_COEF2 * (1 << 16 ) + 0.5f)) >> 16);
		/* convert from log scale to Hertz */
		final int cutoff_Hz = Jmacros.silk_log2lin( this.variable_HP_smth2_Q15 >> 8 );

		if( this.application == Jopus_defines.OPUS_APPLICATION_VOIP )
		{
			hp_cutoff( pcm, pcmoffset, cutoff_Hz, pcm_buf, total_buffer * this.channels, this.hp_mem, frame_size, this.channels, this.Fs );//, this.arch );
		} else {
			dc_reject( pcm, pcmoffset, 3, pcm_buf, total_buffer * this.channels, this.hp_mem, frame_size, this.channels, this.Fs );
		}
// #ifndef FIXED_POINT
		if( float_api )
		{
			final float sum = Jcelt_codec_API.celt_inner_prod( pcm_buf, total_buffer * this.channels, pcm_buf, total_buffer * this.channels, frame_size * this.channels );//, st.arch );
			/* This should filter out both NaNs and ridiculous signals that could
			   cause NaNs further down. */
			if( !(sum < 1e9f) || Float.isNaN( sum ) )
			{
				for( int i = total_buffer * this.channels, ie = i + frame_size * this.channels; i < ie; i++ ) {
					pcm_buf[i] = 0;
				}
				this.hp_mem[0] = this.hp_mem[1] = this.hp_mem[2] = this.hp_mem[3] = 0;
			}
		}
// #endif

		/* SILK processing */
		float HB_gain = Jfloat_cast.Q15ONE;
		if( this.mode != Jopus_private.MODE_CELT_ONLY )
		{
/* #ifdef FIXED_POINT
			const opus_int16 *pcm_silk;
#else */
			final short[] pcm_silk = new short[this.channels * frame_size];
// #endif

			int activity = Jdefine.VAD_NO_DECISION;
// #ifndef DISABLE_FLOAT_API
			if( analysis_info.valid ) {
				/* Inform SILK about the Opus VAD decision */
				activity = ( analysis_info.activity_probability >= Jdefine.DTX_ACTIVITY_THRESHOLD ) ? 1 : 0;
			}
// #endif

			/* Distribute bits between SILK and CELT */
			final int total_bitRate = (bytes_target * frame_rate) << 3;
			if( this.mode == Jopus_private.MODE_HYBRID ) {
				/* Base rate for SILK */
				this.silk_mode.bitRate = compute_silk_rate_for_hybrid( total_bitRate,
						curr_bandwidth, this.Fs == 50 * frame_size, this.use_vbr, this.silk_mode.LBRR_coded, this.stream_channels );

				if( null == this.energy_masking )
				{
					/* Increasingly attenuate high band when it gets allocated fewer bits */
					final int celt_rate = total_bitRate - this.silk_mode.bitRate;
					HB_gain = Jfloat_cast.Q15ONE - ((float)Math.exp( -0.6931471805599453094 * (celt_rate * (1.f / 1024)) ));
				}
			} else {
				/* SILK gets all bits */
				this.silk_mode.bitRate = total_bitRate;
			}

			/* Surround masking for SILK */
			if( null != this.energy_masking && this.use_vbr && ! this.lfe )
			{
				float mask_sum = 0;
				int end = 17;
				float srate = 16000f;// FIXME why int16?
				if( this.bandwidth == Jopus_defines.OPUS_BANDWIDTH_NARROWBAND )
				{
					end = 13;
					srate = 8000f;
				} else if( this.bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND )
				{
					end = 15;
					srate = 12000f;
				}
				for( int c = 0; c < this.channels; c++ )
				{
					for( int i = 0; i < end; i++ )
					{
						//float mask = MAX16( MIN16( st.energy_masking[21 * c + i], .5f), -2.0f );
						float mask = this.energy_masking[21 * c + i];
						mask = mask <= .5f ? mask : .5f;
						mask = mask >= -2.0f ? mask : -2.0f;
						if( mask > 0 ) {
							mask *= .5f;
						}
						mask_sum += mask;
					}
				}
				/* Conservative rate reduction, we cut the masking in half */
				float masking_depth = mask_sum / end * this.channels;
				masking_depth += .2f;
				int rate_offset = (int)(srate * masking_depth);
				v = -2 * this.silk_mode.bitRate / 3;// java
				rate_offset = rate_offset >= v ? rate_offset : v;
				/* Split the rate change between the SILK and CELT part for hybrid. */
				if( this.bandwidth == Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND || this.bandwidth == Jopus_defines.OPUS_BANDWIDTH_FULLBAND ) {
					this.silk_mode.bitRate += 3 * rate_offset / 5;
				} else {
					this.silk_mode.bitRate += rate_offset;
				}
			}

			this.silk_mode.payloadSize_ms = 1000 * frame_size / this.Fs;
			this.silk_mode.nChannelsAPI = this.channels;
			this.silk_mode.nChannelsInternal = this.stream_channels;
			if( curr_bandwidth == Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
				this.silk_mode.desiredInternalSampleRate = 8000;
			} else if( curr_bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
				this.silk_mode.desiredInternalSampleRate = 12000;
			} else {
				// celt_assert( st.mode == MODE_HYBRID || curr_bandwidth == Jopus_defines.OPUS_BANDWIDTH_WIDEBAND );
				this.silk_mode.desiredInternalSampleRate = 16000;
			}
			if( this.mode == Jopus_private.MODE_HYBRID ) {
				/* Don't allow bandwidth reduction at lowest bitrates in hybrid mode */
				this.silk_mode.minInternalSampleRate = 16000;
			} else {
				this.silk_mode.minInternalSampleRate = 8000;
			}

			this.silk_mode.maxInternalSampleRate = 16000;
			if( this.mode == Jopus_private.MODE_SILK_ONLY )
			{
				int effective_max_rate = max_rate;
				if( frame_rate > 50 ) {
					effective_max_rate = (effective_max_rate << 1) / 3;
				}
				if( effective_max_rate < 8000 )
				{
					this.silk_mode.maxInternalSampleRate = 12000;
					this.silk_mode.desiredInternalSampleRate = 12000 < this.silk_mode.desiredInternalSampleRate ? 12000 : this.silk_mode.desiredInternalSampleRate;
				}
				if( effective_max_rate < 7000 )
				{
					this.silk_mode.maxInternalSampleRate = 8000;
					this.silk_mode.desiredInternalSampleRate = 8000 < this.silk_mode.desiredInternalSampleRate ? 8000 : this.silk_mode.desiredInternalSampleRate;
				}
			}

			this.silk_mode.useCBR = ! this.use_vbr;

			/* Call SILK encoder for the low band */

			/* Max bits for SILK, counting ToC, redundancy bytes, and optionally redundancy. */
			this.silk_mode.maxBits = (max_data_bytes - 1) << 3;
			if( redundancy && redundancy_bytes >= 2 )
			{
				/* Counting 1 bit for redundancy position and 20 bits for flag+size (only for hybrid). */
				this.silk_mode.maxBits -= (redundancy_bytes << 3) + 1;
				if( this.mode == Jopus_private.MODE_HYBRID ) {
					this.silk_mode.maxBits -= 20;
				}
			}
			if( this.silk_mode.useCBR )
			{
				if( this.mode == Jopus_private.MODE_HYBRID )
				{
					v = this.silk_mode.bitRate * frame_size / this.Fs;// java
					this.silk_mode.maxBits = (this.silk_mode.maxBits <= v ? this.silk_mode.maxBits : v);
				}
			} else {
				/* Constrained VBR. */
				if( this.mode == Jopus_private.MODE_HYBRID )
				{
					/* Compute SILK bitrate corresponding to the max total bits available */
					final int maxBitRate = compute_silk_rate_for_hybrid( this.silk_mode.maxBits * this.Fs / frame_size,
							curr_bandwidth, this.Fs == 50 * frame_size, this.use_vbr, this.silk_mode.LBRR_coded, this.stream_channels );
					this.silk_mode.maxBits = maxBitRate * frame_size / this.Fs;
				}
			}

			final int[] data_holder = new int[1];// java helper
			if( 0 != prefill )
			{
				final float[] d_buff = this.delay_buffer;// java
				/* Use a smooth onset for the SILK prefill to avoid the encoder trying to encode
				   a discontinuity. The exact location is what we need to avoid leaving any "gap"
				   in the audio when mixing with the redundant CELT frame. Here we can afford to
				   overwrite st.delay_buffer because the only thing that uses it before it gets
				   rewritten is tmp_prefill[] and even then only the part after the ramp really
				   gets used (rather than sent to the encoder and discarded) */
				final int prefill_offset = this.channels * (this.encoder_buffer - this.delay_compensation - this.Fs / 400);
				gain_fade( d_buff, prefill_offset, d_buff, prefill_offset,
						0, Jfloat_cast.Q15ONE, celt_mode.overlap, this.Fs / 400, this.channels, celt_mode.window, this.Fs );
				for( int i = 0; i < prefill_offset; i++ ) {
					d_buff[i] = 0;
				}
/* #ifdef FIXED_POINT
				pcm_silk = st.delay_buffer;
#else */
				for( int i = 0, ie = this.encoder_buffer * this.channels; i < ie; i++ ) {
					// pcm_silk[i] = Jfloat_cast.FLOAT2INT16( this.delay_buffer[i] );
					float x = d_buff[i];
					x *= Jfloat_cast.CELT_SIG_SCALE;
					x = x >= -32768 ? x : -32768;
					x = x <=  32767 ? x :  32767;
					pcm_silk[i] = (short)Math.floor( .5 + (double)x );
				}
// #endif
				data_holder[0] = 0;// final int zero = 0;
				silkenc.silk_Encode( this.silk_mode, pcm_silk, this.encoder_buffer, null, data_holder/* &zero*/, prefill, activity );
				/* Prevent a second switch in the real encode call. */
				this.silk_mode.opusCanSwitch = false;
			}

/* #ifdef FIXED_POINT
			pcm_silk = pcm_buf + total_buffer * st.channels;
#else */
			for( int i = 0, ie = frame_size * this.channels, bi = total_buffer * this.channels; i < ie; i++, bi++ ) {
				// pcm_silk[i] = Jfloat_cast.FLOAT2INT16( pcm_buf[bi] );
				float x = pcm_buf[bi];
				x *= Jfloat_cast.CELT_SIG_SCALE;
				x = x >= -32768 ? x : -32768;
				x = x <=  32767 ? x :  32767;
				pcm_silk[i] = (short)Math.floor( .5 + (double)x );
			}
// #endif
			ret = silkenc.silk_Encode( this.silk_mode, pcm_silk, frame_size, enc, data_holder/*&nBytes*/, 0, activity );// FIXME nBytes is not initialized
			final int nBytes = data_holder[0];// java
			if( 0 != ret ) {
				/*fprintf (stderr, "SILK encode error: %d\n", ret);*/
				/* Handle error */
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}

			/* Extract SILK internal bandwidth for signaling in first byte */
			if( this.mode == Jopus_private.MODE_SILK_ONLY ) {
				if( this.silk_mode.internalSampleRate == 8000 ) {
					curr_bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
				} else if( this.silk_mode.internalSampleRate == 12000 ) {
					curr_bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
				} else if( this.silk_mode.internalSampleRate == 16000 ) {
					curr_bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
				}
			} else {
				// celt_assert( st.silk_mode.internalSampleRate == 16000 );
			}

			this.silk_mode.opusCanSwitch = this.silk_mode.switchReady && ! this.nonfinal_frame;

			if( nBytes == 0 )
			{
				this.rangeFinal = 0;
				data[doffset - 1] = (byte)gen_toc( this.mode, this.Fs / frame_size, curr_bandwidth, this.stream_channels );
				// RESTORE_STACK;
				return 1;
			}

			/* FIXME: How do we allocate the redundancy for CBR? */
			if( this.silk_mode.opusCanSwitch )
			{
				redundancy_bytes = compute_redundancy_bytes( max_data_bytes, this.bitrate_bps, frame_rate, this.stream_channels );
				redundancy = (redundancy_bytes != 0);
				celt_to_silk = false;
				this.silk_bw_switch = true;
			}
		}

		/* CELT processing */
		{
			int endband = 21;

			switch( curr_bandwidth )
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
			}
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_END_BAND, endband );
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_CHANNELS, this.stream_channels );
		}
		celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, Jopus_defines.OPUS_BITRATE_MAX );
		int nb_compr_bytes;
		if( this.mode != Jopus_private.MODE_SILK_ONLY )
		{
			int celt_pred = 2;
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR, false );
			/* We may still decide to disable prediction later */
			if( this.silk_mode.reducedDependency ) {
				celt_pred = 0;
			}
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_PREDICTION, celt_pred );

			if( this.mode == Jopus_private.MODE_HYBRID )
			{
				if( this.use_vbr ) {
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, this.bitrate_bps - this.silk_mode.bitRate );
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, false );
				}
			} else {
				if( this.use_vbr )
				{
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR, true );
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, this.vbr_constraint );
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, this.bitrate_bps );
				}
			}
		}

		final float[] tmp_prefill = new float[this.channels * this.Fs / 400];
		if( this.mode != Jopus_private.MODE_SILK_ONLY  &&  this.mode != this.prev_mode && this.prev_mode > 0 )
		{
			System.arraycopy( this.delay_buffer, (this.encoder_buffer - total_buffer - this.Fs / 400) * this.channels, tmp_prefill, 0, this.channels * this.Fs / 400 );
		}

		if( this.channels * (this.encoder_buffer - (frame_size + total_buffer)) > 0 )
		{
			System.arraycopy( this.delay_buffer, this.channels * frame_size, this.delay_buffer, 0, this.channels * (this.encoder_buffer - frame_size - total_buffer) );
			System.arraycopy( pcm_buf, 0, this.delay_buffer, this.channels * (this.encoder_buffer - frame_size - total_buffer), (frame_size + total_buffer) * this.channels );
		} else {
			System.arraycopy( pcm_buf, (frame_size + total_buffer - this.encoder_buffer) * this.channels, this.delay_buffer, 0, this.encoder_buffer * this.channels );
		}
		/* gain_fade() and stereo_fade() need to be after the buffer copying
		   because we don't want any of this to affect the SILK part */
		if( this.prev_HB_gain < Jfloat_cast.Q15ONE || HB_gain < Jfloat_cast.Q15ONE ) {
			gain_fade( pcm_buf, 0, pcm_buf, 0,
					this.prev_HB_gain, HB_gain, celt_mode.overlap, frame_size, this.channels, celt_mode.window, this.Fs );
		}
		this.prev_HB_gain = HB_gain;
		if( this.mode != Jopus_private.MODE_HYBRID || this.stream_channels == 1 ) {
			if( equiv_rate > 32000 ) {
				this.silk_mode.stereoWidth_Q14 = 16384;
			} else if( equiv_rate < 16000 ) {
				this.silk_mode.stereoWidth_Q14 = 0;
			} else {
				this.silk_mode.stereoWidth_Q14 = 16384 - 2048 * (32000 - equiv_rate) / (equiv_rate - 14000);
			}
		}
		if( null == this.energy_masking && this.channels == 2 ) {
			/* Apply stereo width reduction (at low bitrates) */
			if( this.hybrid_stereo_width_Q14 < (1 << 14) || this.silk_mode.stereoWidth_Q14 < (1 << 14) ) {
				float g1 = (float)this.hybrid_stereo_width_Q14;
				float g2 = (float)this.silk_mode.stereoWidth_Q14;
/* #ifdef FIXED_POINT
				g1 = g1 == 16384 ? Q15ONE : SHL16( g1, 1 );
				g2 = g2 == 16384 ? Q15ONE : SHL16( g2, 1 );
#else */
				g1 *= (1.f / 16384f);
				g2 *= (1.f / 16384f);
// #endif
				stereo_fade( pcm_buf, pcm_buf, g1, g2, celt_mode.overlap,
							frame_size, this.channels, celt_mode.window, this.Fs );
				this.hybrid_stereo_width_Q14 = (short)this.silk_mode.stereoWidth_Q14;
			}
		}

		if( this.mode != Jopus_private.MODE_CELT_ONLY && enc.ec_tell() + 17 + (this.mode == Jopus_private.MODE_HYBRID ? 20 : 0) <= ((max_data_bytes - 1) << 3) )
		{
			/* For SILK mode, the redundancy is inferred from the length */
			if( this.mode == Jopus_private.MODE_HYBRID ) {
				enc.ec_enc_bit_logp( redundancy, 12 );
			}
			if( redundancy )
			{
				int max_redundancy;
				enc.ec_enc_bit_logp( celt_to_silk, 1 );
				if( this.mode == Jopus_private.MODE_HYBRID ) {
					/* Reserve the 8 bits needed for the redundancy length,
					   and at least a few bits for CELT if possible */
					max_redundancy = (max_data_bytes - 1) - ((enc.ec_tell() + 8 + 3 + 7) >> 3);
				} else {
					max_redundancy = (max_data_bytes - 1) - ((enc.ec_tell() + 7) >> 3);
				}
				/* Target the same bit-rate for redundancy as for the rest,
				   up to a max of 257 bytes */
				redundancy_bytes = max_redundancy <= redundancy_bytes ? max_redundancy : redundancy_bytes;
				redundancy_bytes = 2 >= redundancy_bytes ? 2 : redundancy_bytes;
				redundancy_bytes = 257 <= redundancy_bytes ? 257 : redundancy_bytes;
				if( this.mode == Jopus_private.MODE_HYBRID ) {
					enc.ec_enc_uint( redundancy_bytes - 2, 256 );
				}
			}
		} else {
			redundancy = false;
		}

		if( ! redundancy )
		{
			this.silk_bw_switch = false;
			redundancy_bytes = 0;
		}

		int start_band = 0;
		if( this.mode != Jopus_private.MODE_CELT_ONLY ) {
			start_band = 17;
		}

		if( this.mode == Jopus_private.MODE_SILK_ONLY )
		{
			ret = (enc.ec_tell() + 7) >> 3;
			enc.ec_enc_done();
			nb_compr_bytes = ret;
		} else {
			nb_compr_bytes = (max_data_bytes - 1) - redundancy_bytes;
			enc.ec_enc_shrink( nb_compr_bytes );
		}

// #ifndef DISABLE_FLOAT_API
		if( redundancy || this.mode != Jopus_private.MODE_SILK_ONLY ) {
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_ANALYSIS, analysis_info );
		}
// #endif
		if( this.mode == Jopus_private.MODE_HYBRID ) {
			final JSILKInfo info = new JSILKInfo( this.silk_mode.signalType, this.silk_mode.offset );
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_SILK_INFO, info );
		}

		long redundant_rng = 0;
		/* 5 ms redundant frame for CELT.SILK */
		if( redundancy && celt_to_silk )
		{
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_START_BAND, 0 );
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR, false );
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, Jopus_defines.OPUS_BITRATE_MAX );
			final int err = celtenc.celt_encode_with_ec( pcm_buf, 0, this.Fs / 200, data, doffset + nb_compr_bytes, redundancy_bytes, null );
			if( err < 0 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			redundant_rng = ((Long)request[0]).longValue();// java
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );
		}

		celtenc.celt_encoder_ctl( Jcelt.CELT_SET_START_BAND, start_band );

		if( this.mode != Jopus_private.MODE_SILK_ONLY )
		{
			if( this.mode != this.prev_mode && this.prev_mode > 0 )
			{
				celtenc.celt_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );

				/* Prefilling */
				final byte dummy[] = new byte[2];
				celtenc.celt_encode_with_ec( tmp_prefill, 0, this.Fs / 400, dummy, 0, 2, null );
				celtenc.celt_encoder_ctl( Jcelt.CELT_SET_PREDICTION, 0 );
			}
			/* If false, we already busted the budget and we'll end up with a "PLC frame" */
			if( enc.ec_tell() <= (nb_compr_bytes << 3) )
			{
				/* Set the bitrate again if it was overridden in the redundancy code above*/
				if( redundancy && celt_to_silk && this.mode == Jopus_private.MODE_HYBRID && this.use_vbr ) {
					celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, this.bitrate_bps - this.silk_mode.bitRate );
				}
				celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR, this.use_vbr );
				ret = celtenc.celt_encode_with_ec( pcm_buf, 0, frame_size, null, 0, nb_compr_bytes, enc );
				if( ret < 0 )
				{
					// RESTORE_STACK;
					return Jopus_defines.OPUS_INTERNAL_ERROR;
				}
				/* Put CELT->SILK redundancy data in the right place. */
				if( redundancy && celt_to_silk && this.mode == Jopus_private.MODE_HYBRID && this.use_vbr )
				{
					System.arraycopy( data, doffset + nb_compr_bytes, data, doffset + ret, redundancy_bytes );
					nb_compr_bytes = nb_compr_bytes+redundancy_bytes;
				}
			}
		}

		/* 5 ms redundant frame for SILK.CELT */
		if( redundancy  && ! celt_to_silk )
		{
			final int N2 = this.Fs / 200;
			final int N4 = N2 >> 1;// this.Fs / 400;

			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_START_BAND, 0 );
			celtenc.celt_encoder_ctl( Jcelt.CELT_SET_PREDICTION, 0 );
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_VBR, false );
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, Jopus_defines.OPUS_BITRATE_MAX );

			if( this.mode == Jopus_private.MODE_HYBRID )
			{
				/* Shrink packet to what the encoder actually used. */
				nb_compr_bytes = ret;
				enc.ec_enc_shrink( nb_compr_bytes );
			}
			/* NOTE: We could speed this up slightly (at the expense of code size) by just adding a function that prefills the buffer */
			final byte dummy[] = new byte[2];
			celtenc.celt_encode_with_ec( pcm_buf, this.channels * (frame_size - N2 - N4), N4, dummy, 0, 2, null );

			final int err = celtenc.celt_encode_with_ec( pcm_buf, this.channels * (frame_size - N2), N2, data, doffset + nb_compr_bytes, redundancy_bytes, null );
			if( err < 0 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			celtenc.celt_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			redundant_rng = ((Long)request[0]).longValue();// java
		}

		/* Signalling the mode in the first byte */
		doffset--;// data--;
		data[doffset] = (byte)gen_toc( this.mode, this.Fs / frame_size, curr_bandwidth, this.stream_channels );

		this.rangeFinal = enc.rng ^ redundant_rng;

		if( to_celt ) {
			this.prev_mode = Jopus_private.MODE_CELT_ONLY;
		} else {
			this.prev_mode = this.mode;
		}
		this.prev_channels = this.stream_channels;
		this.prev_framesize = frame_size;

		this.first = false;

		/* DTX decision */
// #ifndef DISABLE_FLOAT_API
		if( this.use_dtx && (analysis_info.valid || is_silence) )
		{
			// java: returns status | nb_no_activity_frames
			v = decide_dtx_mode( analysis_info.activity_probability, this.nb_no_activity_frames,
					this.peak_signal_energy, pcm, pcmoffset, frame_size, this.channels, is_silence/*, this.arch*/);
			this.nb_no_activity_frames = v & 0x7fffffff;// java
			if( v < 0 )
			{
				this.rangeFinal = 0;
				data[doffset] = (byte)gen_toc( this.mode, this.Fs / frame_size, curr_bandwidth, this.stream_channels );
				// RESTORE_STACK;
				return 1;
			}
		}
// #endif

		/* In the unlikely case that the SILK encoder busted its target, tell
		   the decoder to call the PLC */
		if( enc.ec_tell() > ((max_data_bytes - 1) << 3) )
		{
			if( max_data_bytes < 2 )
			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_BUFFER_TOO_SMALL;
			}
			data[doffset + 1] = 0;
			ret = 1;
			this.rangeFinal = 0;
		} else if( this.mode == Jopus_private.MODE_SILK_ONLY && ! redundancy )
		{
			/*When in LPC only mode it's perfectly
			  reasonable to strip off trailing zero bytes as
			  the required range decoder behavior is to
			  fill these in. This can't be done when the MDCT
			  modes are used because the decoder needs to know
			  the actual length for allocation purposes.*/
			while( ret > 2 && data[doffset + ret] == 0 ) {
				ret--;
			}
		}
		/* Count ToC and redundancy */
		ret += 1 + redundancy_bytes;
		if( ! this.use_vbr )
		{
			if( JOpusRepacketizer.opus_packet_pad( data, doffset, ret, max_data_bytes ) != Jopus_defines.OPUS_OK )

			{
				// RESTORE_STACK;
				return Jopus_defines.OPUS_INTERNAL_ERROR;
			}
			ret = max_data_bytes;
		}
		// RESTORE_STACK;
		return ret;
	}

// #ifdef FIXED_POINT

// #ifndef DISABLE_FLOAT_API
	/** Encodes an Opus frame from floating point input.
	 * @param [in] st <tt>OpusEncoder*</tt>: Encoder state
	 * @param [in] pcm <tt>float*</tt>: Input in float format (interleaved if 2 channels), with a normal range of +/-1.0.
	 *          Samples with a range beyond +/-1.0 are supported but will
	 *          be clipped by decoders using the integer API and should
	 *          only be used if it is known that the far end supports
	 *          extended dynamic range.
	 *          length is frame_size*channels*sizeof(float)
	 * @param [in] frame_size <tt>int</tt>: Number of samples per channel in the
	 *                                      input signal.
	 *                                      This must be an Opus frame size for
	 *                                      the encoder's sampling rate.
	 *                                      For example, at 48 kHz the permitted
	 *                                      values are 120, 240, 480, 960, 1920,
	 *                                      and 2880.
	 *                                      Passing in a duration of less than
	 *                                      10 ms (480 samples at 48 kHz) will
	 *                                      prevent the encoder from using the LPC
	 *                                      or hybrid modes.
	 * @param [out] data <tt>unsigned char*</tt>: Output payload.
	 *                                            This must contain storage for at
	 *                                            least \a max_data_bytes.
	 * @param [in] max_data_bytes <tt>opus_int32</tt>: Size of the allocated
	 *                                                 memory for the output
	 *                                                 payload. This may be
	 *                                                 used to impose an upper limit on
	 *                                                 the instant bitrate, but should
	 *                                                 not be used as the only bitrate
	 *                                                 control. Use #OPUS_SET_BITRATE to
	 *                                                 control the bitrate.
	 * @returns The length of the encoded packet (in bytes) on success or a
	 *          negative error code (see @ref opus_errorcodes) on failure.
 */
/*	private static final int opus_encode_float(final JOpusEncoder st, final float[] pcm, final int analysis_frame_size,
		unsigned char *data, int max_data_bytes)
	{
		int i, ret;
		int frame_size;
		VARDECL( opus_int16, in );
		ALLOC_STACK;

		frame_size = frame_size_select(analysis_frame_size, st.variable_duration, st.Fs);
		if (frame_size <= 0)
		{
			RESTORE_STACK;
			return OPUS_BAD_ARG;
		}

		ALLOC( in, frame_size * st.channels, short );

		for( i = 0; i < frame_size * st.channels; i++ )
			in[i] = FLOAT2INT16( pcm[i] );
		ret = opus_encode_native( st, in, frame_size, data, max_data_bytes, 16,
						pcm, analysis_frame_size, 0, -2, st.channels, downmix_float, 1 );
		RESTORE_STACK;
		return ret;
	}
#endif */

	/** Encodes an Opus frame.
	 * @param [in] st <tt>OpusEncoder*</tt>: Encoder state
	 * @param [in] pcm <tt>opus_int16*</tt>: Input signal (interleaved if 2 channels). length is frame_size*channels*sizeof(opus_int16)
	 * @param [in] frame_size <tt>int</tt>: Number of samples per channel in the
	 *                                      input signal.
	 *                                      This must be an Opus frame size for
	 *                                      the encoder's sampling rate.
	 *                                      For example, at 48 kHz the permitted
	 *                                      values are 120, 240, 480, 960, 1920,
	 *                                      and 2880.
	 *                                      Passing in a duration of less than
	 *                                      10 ms (480 samples at 48 kHz) will
	 *                                      prevent the encoder from using the LPC
	 *                                      or hybrid modes.
	 * @param [out] data <tt>unsigned char*</tt>: Output payload.
	 *                                            This must contain storage for at
	 *                                            least \a max_data_bytes.
	 * @param [in] max_data_bytes <tt>opus_int32</tt>: Size of the allocated
	 *                                                 memory for the output
	 *                                                 payload. This may be
	 *                                                 used to impose an upper limit on
	 *                                                 the instant bitrate, but should
	 *                                                 not be used as the only bitrate
	 *                                                 control. Use #OPUS_SET_BITRATE to
	 *                                                 control the bitrate.
	 * @returns The length of the encoded packet (in bytes) on success or a
	 *          negative error code (see @ref opus_errorcodes) on failure.
	 */
/*	private static final opus_int32 opus_encode(final JOpusEncoder st, final short[] pcm, final int analysis_frame_size,
			unsigned char *data, int out_data_bytes)
	{
		int frame_size;
		frame_size = frame_size_select( analysis_frame_size, st.variable_duration, st.Fs );
		return opus_encode_native( st, pcm, frame_size, data, out_data_bytes, 16,
				pcm, analysis_frame_size, 0, -2, st.channels, downmix_int, 0 );
	}

#else */
	/** Encodes an Opus frame.
	  * @param st [in] <tt>OpusEncoder*</tt>: Encoder state
	  * @param pcm [in] <tt>opus_int16*</tt>: Input signal (interleaved if 2 channels). length is frame_size*channels*sizeof(opus_int16)
	  * @param pcmoffset [in] java offset for the pcm
	  * @param analysis_frame_size [in] <tt>int</tt>: Number of samples per channel in the
	  *                                      input signal.
	  *                                      This must be an Opus frame size for
	  *                                      the encoder's sampling rate.
	  *                                      For example, at 48 kHz the permitted
	  *                                      values are 120, 240, 480, 960, 1920,
	  *                                      and 2880.
	  *                                      Passing in a duration of less than
	  *                                      10 ms (480 samples at 48 kHz) will
	  *                                      prevent the encoder from using the LPC
	  *                                      or hybrid modes.
	  * @param data [out] <tt>unsigned char*</tt>: Output payload.
	  *                                            This must contain storage for at
	  *                                            least \a max_data_bytes.
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
	public final int opus_encode(final short[] pcm, final int pcmoffset,// java
				final int analysis_frame_size,
				final byte[] data, final int max_data_bytes)
	{
		// ALLOC_STACK;

		final int frame_size = frame_size_select( analysis_frame_size, this.variable_duration, this.Fs );
		if( frame_size <= 0 )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final float[] in = new float[frame_size * this.channels];

		for( int i = 0, ie = frame_size * this.channels, j = pcmoffset; i < ie; i++ ) {
			in[i] = (1.0f / 32768f) * pcm[j++];
		}
		final int ret = opus_encode_native( in, 0, frame_size, data, 0, max_data_bytes, 16,
					pcm, pcmoffset, analysis_frame_size, 0, -2, this.channels, downmix_int, false );
		// RESTORE_STACK;
		return ret;
	}

	/** Encodes an Opus frame from floating point input.
	 * @param st [in] <tt>OpusEncoder*</tt>: Encoder state
	 * @param pcm [in] <tt>float*</tt>: Input in float format (interleaved if 2 channels), with a normal range of +/-1.0.
	 *          Samples with a range beyond +/-1.0 are supported but will
	 *          be clipped by decoders using the integer API and should
	 *          only be used if it is known that the far end supports
	 *          extended dynamic range.
	 *          length is frame_size*channels*sizeof(float)
	 * @param analysis_frame_size [in] <tt>int</tt>: Number of samples per channel in the
	 *                                      input signal.
	 *                                      This must be an Opus frame size for
	 *                                      the encoder's sampling rate.
	 *                                      For example, at 48 kHz the permitted
	 *                                      values are 120, 240, 480, 960, 1920,
	 *                                      and 2880.
	 *                                      Passing in a duration of less than
	 *                                      10 ms (480 samples at 48 kHz) will
	 *                                      prevent the encoder from using the LPC
	 *                                      or hybrid modes.
	 * @param data [out] <tt>unsigned char*</tt>: Output payload.
	 *                                            This must contain storage for at
	 *                                            least \a max_data_bytes.
	 * @param out_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
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
	public final int opus_encode_float(final float[] pcm, final int analysis_frame_size,
								final byte[] data, final int out_data_bytes)
	{
		final int frame_size = frame_size_select( analysis_frame_size, this.variable_duration, this.Fs );
		return opus_encode_native( pcm, 0, frame_size, data, 0, out_data_bytes, 24,
					pcm, 0, analysis_frame_size, 0, -2, this.channels, downmix_float, true );
	}
// #endif

	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	/** Perform a CTL function on an Opus encoder.
	 *
	 * java: getters
	 *
	 * Generally the request and subsequent arguments are generated
	 * by a convenience macro.
	 * @param st <tt>OpusEncoder*</tt>: Encoder state.
	 * @param request This and all remaining parameters should be replaced by one
	 *                of the convenience macros in @ref opus_genericctls or
	 *                @ref opus_encoderctls.
	 * @param arg the array to get returned object(s)
	 * @return status
	 * @see opus_genericctls
	 * @see opus_encoderctls
	 */
	public final int opus_encoder_ctl(final int request, final Object[] arg)
	{// getters
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_GET_APPLICATION_REQUEST:
			{
				arg[0] = Integer.valueOf( this.application );
			}
			break;
		case Jopus_defines.OPUS_GET_BITRATE_REQUEST:
			{
				arg[0] = Integer.valueOf( user_bitrate_to_bitrate( this.prev_framesize, 1276 ) );
			}
			break;
		case Jopus_defines.OPUS_GET_FORCE_CHANNELS_REQUEST:
			{
				arg[0] = Integer.valueOf( this.force_channels );
			}
			break;
		case Jopus_defines.OPUS_GET_MAX_BANDWIDTH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.max_bandwidth );
			}
			break;
		case Jopus_defines.OPUS_GET_BANDWIDTH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.bandwidth );
			}
			break;
		case Jopus_defines.OPUS_GET_DTX_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.use_dtx );
			}
			break;
		case Jopus_defines.OPUS_GET_COMPLEXITY_REQUEST:
			{
				arg[0] = Integer.valueOf( this.silk_mode.complexity );
			}
			break;
		case Jopus_defines.OPUS_GET_INBAND_FEC_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.silk_mode.useInBandFEC );
			}
			break;
		case Jopus_defines.OPUS_GET_PACKET_LOSS_PERC_REQUEST:
			{
				arg[0] = Integer.valueOf( this.silk_mode.packetLossPercentage );
			}
			break;
		case Jopus_defines.OPUS_GET_VBR_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.use_vbr );
			}
			break;
		case Jopus_private.OPUS_GET_VOICE_RATIO_REQUEST:
			{
				arg[0] = Integer.valueOf( this.voice_ratio );
			}
			break;
		case Jopus_defines.OPUS_GET_VBR_CONSTRAINT_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.vbr_constraint );
			}
			break;
		case Jopus_defines.OPUS_GET_SIGNAL_REQUEST:
			{
				arg[0] = Integer.valueOf( this.signal_type );
			}
			break;
		case Jopus_defines.OPUS_GET_LOOKAHEAD_REQUEST:
			{
				int v = this.Fs / 400;
				if( this.application != Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY ) {
					v += this.delay_compensation;
				}
				arg[0] = Integer.valueOf( v );
			}
			break;
		case Jopus_defines.OPUS_GET_SAMPLE_RATE_REQUEST:
			{
				arg[0] = Integer.valueOf( this.Fs );
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				arg[0] = Long.valueOf( this.rangeFinal );
			}
			break;
		case Jopus_defines.OPUS_GET_LSB_DEPTH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.lsb_depth );
			}
			break;
		case Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION_REQUEST:
			{
				arg[0] = Integer.valueOf( this.variable_duration );
			}
			break;
		case Jopus_defines.OPUS_GET_PREDICTION_DISABLED_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.silk_mode.reducedDependency );
			}
			break;
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED, arg );
			}
			break;

		case Jcelt.CELT_GET_MODE_REQUEST:
			{
				ret = this.celt_enc.celt_encoder_ctl( Jcelt.CELT_GET_MODE_REQUEST, arg );
			}
			break;
		default:
			/* fprintf(stderr, "unknown opus_encoder_ctl() request: %d", request);*/
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}
	// java added overload variant, setters
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_encoder_ctl(final int request, int value)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_APPLICATION_REQUEST:
			{
				if( (value != Jopus_defines.OPUS_APPLICATION_VOIP && value != Jopus_defines.OPUS_APPLICATION_AUDIO
					&&  value != Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY )
						|| (! this.first && this.application != value) )
				{
					ret = Jopus_defines.OPUS_BAD_ARG;
					break;
				}
				this.application = value;
// #ifndef DISABLE_FLOAT_API
				this.analysis.application = value;
// #endif
			}
			break;
		case Jopus_defines.OPUS_SET_BITRATE_REQUEST:
			{
				if( value != Jopus_defines.OPUS_AUTO && value != Jopus_defines.OPUS_BITRATE_MAX )
				{
					if( value <= 0 ) {
						return Jopus_defines.OPUS_BAD_ARG;
					} else if( value <= 500 ) {
						value = 500;
					} else if( value > (int)300000 * this.channels ) {
						value = (int)300000 * this.channels;
					}
				}
				this.user_bitrate_bps = value;
			}
			break;
		case Jopus_defines.OPUS_SET_FORCE_CHANNELS_REQUEST:
			{
				if( (value < 1 || value > this.channels) && value != Jopus_defines.OPUS_AUTO )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.force_channels = value;
			}
			break;
		case Jopus_defines.OPUS_SET_MAX_BANDWIDTH_REQUEST:
			{
				if( value < Jopus_defines.OPUS_BANDWIDTH_NARROWBAND || value > Jopus_defines.OPUS_BANDWIDTH_FULLBAND )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.max_bandwidth = value;
				if( this.max_bandwidth == Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
					this.silk_mode.maxInternalSampleRate = 8000;
				} else if( this.max_bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
					this.silk_mode.maxInternalSampleRate = 12000;
				} else {
					this.silk_mode.maxInternalSampleRate = 16000;
				}
			}
			break;
		case Jopus_defines.OPUS_SET_BANDWIDTH_REQUEST:
			{
				if( (value < Jopus_defines.OPUS_BANDWIDTH_NARROWBAND || value > Jopus_defines.OPUS_BANDWIDTH_FULLBAND) && value != Jopus_defines.OPUS_AUTO )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.user_bandwidth = value;
				if( this.user_bandwidth == Jopus_defines.OPUS_BANDWIDTH_NARROWBAND ) {
					this.silk_mode.maxInternalSampleRate = 8000;
				} else if( this.user_bandwidth == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
					this.silk_mode.maxInternalSampleRate = 12000;
				} else {
					this.silk_mode.maxInternalSampleRate = 16000;
				}
			}
			break;
		case Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST:
			{
				if( value < 0 || value > 10 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.silk_mode.complexity = value;
				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST, value );
			}
			break;
		case Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST:
			{
				if( value < 0 || value > 100 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.silk_mode.packetLossPercentage = value;
				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST, value );
			}
			break;
		case Jopus_private.OPUS_SET_VOICE_RATIO_REQUEST:
			{
				if( value < -1 || value > 100 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.voice_ratio = value;
			}
			break;
		case Jopus_defines.OPUS_SET_SIGNAL_REQUEST:
			{
				if( value != Jopus_defines.OPUS_AUTO && value != Jopus_defines.OPUS_SIGNAL_VOICE && value != Jopus_defines.OPUS_SIGNAL_MUSIC )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.signal_type = value;
			}
			break;
		case Jopus_defines.OPUS_SET_LSB_DEPTH_REQUEST:
			{
				if( value < 8 || value > 24 )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.lsb_depth = value;
			}
			break;
		case Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION_REQUEST:
			{
				if( value != Jopus_defines.OPUS_FRAMESIZE_ARG && value != Jopus_defines.OPUS_FRAMESIZE_2_5_MS &&
					value != Jopus_defines.OPUS_FRAMESIZE_5_MS  && value != Jopus_defines.OPUS_FRAMESIZE_10_MS &&
					value != Jopus_defines.OPUS_FRAMESIZE_20_MS && value != Jopus_defines.OPUS_FRAMESIZE_40_MS &&
					value != Jopus_defines.OPUS_FRAMESIZE_60_MS && value != Jopus_defines.OPUS_FRAMESIZE_80_MS &&
					value != Jopus_defines.OPUS_FRAMESIZE_100_MS && value != Jopus_defines.OPUS_FRAMESIZE_120_MS )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.variable_duration = value;
				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION_REQUEST, value );
			}
			break;
		case Jopus_private.OPUS_SET_FORCE_MODE_REQUEST:
			{
				if( (value < Jopus_private.MODE_SILK_ONLY || value > Jopus_private.MODE_CELT_ONLY) && value != Jopus_defines.OPUS_AUTO )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.user_forced_mode = value;
			}
			break;
		default:
			/* fprintf(stderr, "unknown opus_encoder_ctl() request: %d", request);*/
			System.err.println( CLASS_NAME + " unknown request: " + request );
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
	public final int opus_encoder_ctl(final int request, final boolean value)
	{
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_SET_DTX_REQUEST:
			{
				this.use_dtx = value;
			}
			break;
		case Jopus_defines.OPUS_SET_INBAND_FEC_REQUEST:
			{
				this.silk_mode.useInBandFEC = value;
			}
			break;
		case Jopus_defines.OPUS_SET_VBR_REQUEST:
			{
				this.use_vbr = value;
				this.silk_mode.useCBR = ! value;
			}
			break;
		case Jopus_defines.OPUS_SET_VBR_CONSTRAINT_REQUEST:
			{
				this.vbr_constraint = value;
			}
			break;
		case Jopus_defines.OPUS_SET_PREDICTION_DISABLED_REQUEST:
			{
				this.silk_mode.reducedDependency = value;
			}
			break;
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED, value );
			}
			break;

		case Jcelt.OPUS_SET_LFE_REQUEST:
			{
				this.lfe = value;
				ret = this.celt_enc.celt_encoder_ctl( Jcelt.OPUS_SET_LFE_REQUEST, value );
			}
			break;
		default:
			/* fprintf(stderr, "unknown opus_encoder_ctl() request: %d", request);*/
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}
	/**
	 * Setters for objects
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int opus_encoder_ctl(final int request, final Object arg)
	{
		if( arg == null )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}

		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jcelt.OPUS_SET_ENERGY_MASK_REQUEST:
			{
				if( ! (arg instanceof float[]) )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.energy_masking = (float[])arg;
				ret = this.celt_enc.celt_encoder_ctl( Jcelt.OPUS_SET_ENERGY_MASK_REQUEST, arg );
			}
			break;
		default:
			/* fprintf(stderr, "unknown opus_encoder_ctl() request: %d", request);*/
			System.err.println( CLASS_NAME + " unknown request: " + request );
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
	public final int opus_encoder_ctl(final int request) {
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
// #ifndef DISABLE_FLOAT_API
				this.analysis.tonality_analysis_reset();
// #endif

				//start = (char*)&st.OPUS_ENCODER_RESET_START;
				//OPUS_CLEAR( start, sizeof(JOpusEncoder) - (start - (char*)st) );
				clear( false );

				this.celt_enc.celt_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );
				this.silk_enc.silk_InitEncoder( /*st.arch,*/ new Jsilk_EncControlStruct() );
				this.stream_channels = this.channels;
				this.hybrid_stereo_width_Q14 = 1 << 14;
				this.prev_HB_gain = Jfloat_cast.Q15ONE;
				this.first = true;
				this.mode = Jopus_private.MODE_HYBRID;
				this.bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
				this.variable_HP_smth2_Q15 = Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ ) << 8;
			}
			break;
		default:
			/* fprintf(stderr, "unknown opus_encoder_ctl() request: %d", request);*/
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		return ret;
	}

	/** Frees an <code>OpusEncoder</code> allocated by opus_encoder_create().
	 *
	 * java: use st = null
	 *
	 * @param[in] st <tt>OpusEncoder*</tt>: State to be freed.
	 */
	/*private static final void opus_encoder_destroy(final JOpusEncoder st)
	{
		opus_free( st );
	}*/

}
