package celt;

import opus.Jopus_defines;

/** Encoder state
@brief Encoder state
*/
class JOpusCustomEncoder extends Jcelt_codec_API {
	private static final String CLASS_NAME = "JOpusCustomEncoder";
	// struct OpusCustomEncoder {
	/** Mode used by the encoder */
	JOpusCustomMode mode;
	int channels;
	int stream_channels;

	boolean force_intra;
	boolean clip;
	boolean disable_pf;
	int complexity;
	int upsample;
	int start, end;

	int bitrate;
	boolean vbr;
	boolean signalling;
	/** If zero, VBR can do whatever it likes with the rate */
	boolean constrained_vbr;
	int loss_rate;
	int lsb_depth;
	boolean lfe;
	boolean disable_inv;
	// int arch;

	/* Everything beyond this point gets cleared on a reset */
	// #define ENCODER_RESET_START rng

	int rng;
	int spread_decision;
	float delayedIntra;
	int tonal_average;
	int lastCodedBands;
	int hf_average;
	int tapset_decision;

	int prefilter_period;
	float prefilter_gain;
	int prefilter_tapset;
/* #ifdef RESYNTH
	int prefilter_period_old;
	opus_val16 prefilter_gain_old;
	int prefilter_tapset_old;
#endif */
	int consec_transient;
	final JAnalysisInfo analysis = new JAnalysisInfo();
	final JSILKInfo silk_info = new JSILKInfo();

	final float preemph_memE[] = new float[2];
	final float preemph_memD[] = new float[2];

	/* VBR - related parameters */
	int vbr_reservoir;
	int vbr_drift;
	int vbr_offset;
	int vbr_count;
	float overlap_max;
	float stereo_saving;
	int intensity;
	float[] energy_mask;
	float spec_avg;

// #ifdef RESYNTH
	/*  + MAX_PERIOD/2 to make space for overlap */
//	celt_sig syn_mem[2][2*MAX_PERIOD + MAX_PERIOD/2];
// #endif

	float in_mem[] = null;/* Size = channels*mode.overlap */
	float prefilter_mem[] = null;/* celt_sig prefilter_mem[],  Size = channels*COMBFILTER_MAXPERIOD */
	float oldBandE[] = null;/* opus_val16 oldBandE[],     Size = channels*mode.nbEBands */
	float oldLogE[] = null;/* opus_val16 oldLogE[],      Size = channels*mode.nbEBands */
	float oldLogE2[] = null;/* opus_val16 oldLogE2[],     Size = channels*mode.nbEBands */
	float energyError[] = null;/* opus_val16 energyError[],  Size = channels*mode->nbEBands */
	// };
// end of struct
	//
	final void clear(final boolean isFull) {
		if( isFull ) {
			mode = null;
			channels = 0;
			stream_channels = 0;

			force_intra = false;
			clip = false;
			disable_pf = false;
			complexity = 0;
			upsample = 0;
			start = 0;
			end = 0;

			bitrate = 0;
			vbr = false;
			signalling = false;
			constrained_vbr = false;
			loss_rate = 0;
			lsb_depth = 0;
			lfe = false;
			disable_inv = false;
			// arch = 0;
			in_mem = null;// java: c uses dirty way to save this mem unclear
			prefilter_mem = null;
			oldBandE = null;
			oldLogE = null;
			oldLogE2 = null;
			energyError = null;
		}
		rng = 0;
		spread_decision = 0;
		delayedIntra = 0;
		tonal_average = 0;
		lastCodedBands = 0;
		hf_average = 0;
		tapset_decision = 0;

		prefilter_period = 0;
		prefilter_gain = 0;
		prefilter_tapset = 0;
/* #ifdef RESYNTH
		prefilter_period_old = 0;
		prefilter_gain_old = 0;
		prefilter_tapset_old = 0;
#endif */
		consec_transient = 0;
		analysis.clear();
		silk_info.clear();

		preemph_memE[0] = 0; preemph_memE[1] = 0;
		preemph_memD[1] = 0; preemph_memD[1] = 0;

		vbr_reservoir = 0;
		vbr_drift = 0;
		vbr_offset = 0;
		vbr_count = 0;
		overlap_max = 0;
		stereo_saving = 0;
		intensity = 0;
		energy_mask = null;
		spec_avg = 0;
		//
		if( in_mem != null ) {
			int i = in_mem.length;
			do {
				in_mem[ --i ] = 0f;
			} while( i > 0 );
		}
		if( prefilter_mem != null ) {
			int i = prefilter_mem.length;
			do {
				prefilter_mem[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldBandE != null ) {
			int i = oldBandE.length;
			do {
				oldBandE[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldLogE != null ) {
			int i = oldLogE.length;
			do {
				oldLogE[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldLogE2 != null ) {
			int i = oldLogE2.length;
			do {
				oldLogE2[ --i ] = 0f;
			} while( i > 0 );
		}
		if( energyError != null ) {
			int i = energyError.length;
			do {
				energyError[ --i ] = 0f;
			} while( i > 0 );
		}
	}
	public final void copyFrom(final JOpusCustomEncoder e) {
		mode = e.mode;
		channels = e.channels;
		stream_channels = e.stream_channels;

		force_intra = e.force_intra;
		clip = e.clip;
		disable_pf = e.disable_pf;
		complexity = e.complexity;
		upsample = e.upsample;
		start = e.start;
		end = e.end;

		bitrate = e.bitrate;
		vbr = e.vbr;
		signalling = e.signalling;
		constrained_vbr = e.constrained_vbr;
		loss_rate = e.loss_rate;
		lsb_depth = e.lsb_depth;
		lfe = e.lfe;
		disable_inv = e.disable_inv;
		// arch = e.arch;

		System.arraycopy( e.in_mem, 0, in_mem, 0, in_mem.length );
		System.arraycopy( e.prefilter_mem, 0, prefilter_mem, 0, prefilter_mem.length );
		System.arraycopy( e.oldBandE, 0, oldBandE, 0, oldBandE.length );
		System.arraycopy( e.oldLogE, 0, oldLogE, 0, oldLogE.length );
		System.arraycopy( e.oldLogE2, 0, oldLogE2, 0, oldLogE2.length );
		System.arraycopy( e.energyError, 0, energyError, 0, energyError.length );

		rng = e.rng;
		spread_decision = e.spread_decision;
		delayedIntra = e.delayedIntra;
		tonal_average = e.tonal_average;
		lastCodedBands = e.lastCodedBands;
		hf_average = e.hf_average;
		tapset_decision = e.tapset_decision;

		prefilter_period = e.prefilter_period;
		prefilter_gain = e.prefilter_gain;
		prefilter_tapset = e.prefilter_tapset;
/* #ifdef RESYNTH
		prefilter_period_old = 0;
		prefilter_gain_old = 0;
		prefilter_tapset_old = 0;
#endif */
		consec_transient = e.consec_transient;
		analysis.copyFrom( e.analysis );
		silk_info.copyFrom( e.silk_info );

		preemph_memE[0] = e.preemph_memE[0]; preemph_memE[1] = e.preemph_memE[1];
		preemph_memD[1] = e.preemph_memD[0]; preemph_memD[1] = e.preemph_memD[1];

		vbr_reservoir = e.vbr_reservoir;
		vbr_drift = e.vbr_drift;
		vbr_offset = e.vbr_offset;
		vbr_count = e.vbr_count;
		overlap_max = e.overlap_max;
		stereo_saving = e.stereo_saving;
		intensity = e.intensity;
		energy_mask = e.energy_mask;
		spec_avg = e.spec_avg;
	}
	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	// TODO may be better way is throwing exception, because return state don't checked
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return
	 */
	final int opus_custom_encoder_ctl(final int request, final Object[] arg)
	{
		if( arg == null || arg.length == 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		switch ( request )
		{
		case Jopus_defines.OPUS_GET_LSB_DEPTH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.lsb_depth );
			}
			break;
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.disable_inv );
			}
			break;
		case Jcelt.CELT_GET_MODE_REQUEST:
			{
				if( arg == null || arg.length == 0 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				arg[0] = this.mode;
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				if( arg == null || arg.length == 0 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				arg[0] = Long.valueOf( this.rng );
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return
	 */
	final int opus_custom_encoder_ctl(final int request, final int value)
	{
		switch( request )
		{
		case Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST:
			{
				if( value < 0 || value > 10 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.complexity = value;
			}
			break;
		case Jcelt.CELT_SET_START_BAND_REQUEST:
			{
				if( value < 0 || value >= this.mode.nbEBands ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.start = value;
			}
			break;
		case Jcelt.CELT_SET_END_BAND_REQUEST:
			{
				if( value < 1 || value > this.mode.nbEBands ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.end = value;
			}
			break;
		case Jcelt.CELT_SET_PREDICTION_REQUEST:
			{
				if( value < 0 || value > 2 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.disable_pf = value <= 1;
				this.force_intra = value == 0;
			}
			break;
		case Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST:
			{
				if( value < 0 || value > 100 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.loss_rate = value;
			}
			break;
		case Jopus_defines.OPUS_SET_BITRATE_REQUEST:
			{
				if( value <= 500 && value != Jopus_defines.OPUS_BITRATE_MAX ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				final int v = 260000 * this.channels;
				this.bitrate = value <= v ? value : v;
			}
			break;
		case Jcelt.CELT_SET_CHANNELS_REQUEST:
			{
				if( value < 1 || value > 2 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.stream_channels = value;
			}
			break;
		case Jopus_defines.OPUS_SET_LSB_DEPTH_REQUEST:
			{
				if( value < 8 || value > 24 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.lsb_depth = value;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return
	 */
	final int opus_custom_encoder_ctl(final int request, final boolean value)
	{
		switch ( request )
		{
		case Jopus_defines.OPUS_SET_VBR_CONSTRAINT_REQUEST:
			{
				this.constrained_vbr = value;
			}
			break;
		case Jopus_defines.OPUS_SET_VBR_REQUEST:
			{
				this.vbr = value;
			}
			break;
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				this.disable_inv = value;
			}
		break;
/* #ifdef CUSTOM_MODES
		case Jopus_defines.CELT_SET_INPUT_CLIPPING_REQUEST:
			{
				this.clip = value;
			}
			break;
#endif */
		case Jcelt.CELT_SET_SIGNALLING_REQUEST:
			{
				this.signalling = value;
			}
			break;
		case Jcelt.OPUS_SET_LFE_REQUEST:
			{
				this.lfe = value;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * Setters for objects
	 *
	 * @param st
	 * @param request
	 * @param arg an object to copy data from
	 * @return
	 */
	final int opus_custom_encoder_ctl(final int request, final Object arg)
	{
		switch ( request )
		{
		case Jcelt.CELT_SET_ANALYSIS_REQUEST:
			{
				final JAnalysisInfo info = (JAnalysisInfo)arg;
				if( info != null ) {
					if( !(arg instanceof JAnalysisInfo) ) {
						return Jopus_defines.OPUS_BAD_ARG;
					}
					this.analysis.copyFrom( info );
				}
			}
			break;
		case Jcelt.CELT_SET_SILK_INFO_REQUEST:
			{
				final JSILKInfo info = (JSILKInfo)arg;
				if( info != null ) {
					if( !(arg instanceof JSILKInfo) ) {
						return Jopus_defines.OPUS_BAD_ARG;
					}
					this.silk_info.copyFrom( info );
				}
			}
			break;
		case Jcelt.OPUS_SET_ENERGY_MASK_REQUEST:
			{
				if( !(arg instanceof float[]) ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.energy_mask = (float[])arg;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return
	 */
	final int opus_custom_encoder_ctl(final int request)
	{
		switch ( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
				//OPUS_CLEAR( (char*)&st.ENCODER_RESET_START,
				//			opus_custom_encoder_get_size( st.mode, st.channels ) -
				//			((char*)&st.ENCODER_RESET_START - (char*)st ) );
				clear( false );
				final float[] old_LogE = this.oldLogE;
				final float[] old_LogE2 = this.oldLogE2;
				for( int i = 0, ie = this.channels * this.mode.nbEBands; i < ie; i++ ) {
					old_LogE[ i ] = old_LogE2[i] = -28.f;
				}
				this.vbr_offset = 0;
				this.delayedIntra = 1;
				this.spread_decision = JCELTMode.SPREAD_NORMAL;
				this.tonal_average = 256;
				this.hf_average = 0;
				this.tapset_decision = 0;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
}
