package silk;

import celt.Jec_enc;

// structs_FLP.h

/** Encoder Super Struct */
public final class Jsilk_encoder extends Jenc_API {
	public final Jsilk_encoder_state_FLP state_Fxx[] = new Jsilk_encoder_state_FLP[ Jdefine.ENCODER_NUM_CHANNELS ];
	public final Jstereo_enc_state sStereo = new Jstereo_enc_state();
	public int nBitsUsedLBRR;
	public int nBitsExceeded;
	public int nChannelsAPI;
	public int nChannelsInternal;
	public int nPrevChannelsInternal;
	public int timeSinceSwitchAllowed_ms;
	public boolean allowBandwidthSwitch;
	public int prev_decode_only_middle;
	//
	public Jsilk_encoder() {
		int i = Jdefine.ENCODER_NUM_CHANNELS;
		do {
			state_Fxx[--i] = new Jsilk_encoder_state_FLP();
		} while( i > 0 );
	}
	/**
	 * java version of c memset 0
	 */
	public final void clear() {
		int i = Jdefine.ENCODER_NUM_CHANNELS;
		do {
			state_Fxx[--i].clear();
		} while( i > 0 );
		sStereo.clear();
		nBitsUsedLBRR = 0;
		nBitsExceeded = 0;
		nChannelsAPI = 0;
		nChannelsInternal = 0;
		nPrevChannelsInternal = 0;
		timeSinceSwitchAllowed_ms = 0;
		allowBandwidthSwitch = false;
		prev_decode_only_middle = 0;
	}
	/**
	 * java version of c memcpy
	 * @param s another Jsilk_encoder object
	 */
	public final void copyFrom(final Jsilk_encoder s) {
		int i = Jdefine.ENCODER_NUM_CHANNELS;
		do {
			i--;
			state_Fxx[i].copyFrom( s.state_Fxx[i]);
		} while( i > 0 );
		sStereo.copyFrom( s.sStereo );
		nBitsUsedLBRR = s.nBitsUsedLBRR;
		nBitsExceeded = s.nBitsExceeded;
		nChannelsAPI = s.nChannelsAPI;
		nChannelsInternal = s.nChannelsInternal;
		nPrevChannelsInternal = s.nPrevChannelsInternal;
		timeSinceSwitchAllowed_ms = s.nPrevChannelsInternal;
		allowBandwidthSwitch = s.allowBandwidthSwitch;
		prev_decode_only_middle = s.prev_decode_only_middle;
	}
	// start check_control_input.c
	/** Check encoder control struct
	 * @param encControl I    Control structure
	 * @return
	 */
	private static final int check_control_input(final Jsilk_EncControlStruct encControl)
	{
		// celt_assert( encControl != NULL );

		if( ( ( encControl.API_sampleRate            !=  8000 ) &&
			( encControl.API_sampleRate            != 12000 ) &&
			( encControl.API_sampleRate            != 16000 ) &&
			( encControl.API_sampleRate            != 24000 ) &&
			( encControl.API_sampleRate            != 32000 ) &&
			( encControl.API_sampleRate            != 44100 ) &&
			( encControl.API_sampleRate            != 48000 ) ) ||
			( ( encControl.desiredInternalSampleRate !=  8000 ) &&
			( encControl.desiredInternalSampleRate != 12000 ) &&
			( encControl.desiredInternalSampleRate != 16000 ) ) ||
			( ( encControl.maxInternalSampleRate     !=  8000 ) &&
			( encControl.maxInternalSampleRate     != 12000 ) &&
			( encControl.maxInternalSampleRate     != 16000 ) ) ||
			( ( encControl.minInternalSampleRate     !=  8000 ) &&
			( encControl.minInternalSampleRate     != 12000 ) &&
			( encControl.minInternalSampleRate     != 16000 ) ) ||
			( encControl.minInternalSampleRate > encControl.desiredInternalSampleRate ) ||
			( encControl.maxInternalSampleRate < encControl.desiredInternalSampleRate ) ||
			( encControl.minInternalSampleRate > encControl.maxInternalSampleRate ) ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_FS_NOT_SUPPORTED;
		}
		if( encControl.payloadSize_ms != 10 &&
			encControl.payloadSize_ms != 20 &&
			encControl.payloadSize_ms != 40 &&
			encControl.payloadSize_ms != 60 ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_PACKET_SIZE_NOT_SUPPORTED;
		}
		if( encControl.packetLossPercentage < 0 || encControl.packetLossPercentage > 100 ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_LOSS_RATE;
		}
		/*if( encControl.useDTX < 0 || encControl.useDTX > 1 ) {// java using boolean
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_DTX_SETTING;
		}*/
		/*if( encControl.useCBR < 0 || encControl.useCBR > 1 ) {// java using boolean
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_CBR_SETTING;
		}*/
		/*if( encControl.useInBandFEC < 0 || encControl.useInBandFEC > 1 ) {// java using boolean
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_INBAND_FEC_SETTING;
		}*/
		if( encControl.nChannelsAPI < 1 || encControl.nChannelsAPI > Jdefine.ENCODER_NUM_CHANNELS ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_NUMBER_OF_CHANNELS_ERROR;
		}
		if( encControl.nChannelsInternal < 1 || encControl.nChannelsInternal > Jdefine.ENCODER_NUM_CHANNELS ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_NUMBER_OF_CHANNELS_ERROR;
		}
		if( encControl.nChannelsInternal > encControl.nChannelsAPI ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_NUMBER_OF_CHANNELS_ERROR;
		}
		if( encControl.complexity < 0 || encControl.complexity > 10 ) {
			// celt_assert( 0 );
			return Jerrors.SILK_ENC_INVALID_COMPLEXITY_SETTING;
		}

		return Jerrors.SILK_NO_ERROR;
	}
	// end check_control_input.c

	// start control_SNR.c
	// TODO java trade off: byte or char
	/* These tables hold SNR values divided by 21 (so they fit in 8 bits)
	   for different target bitrates spaced at 400 bps interval. The first
	   10 values are omitted (0-4 kb/s) because they're all zeros.
	   These tables were obtained by running different SNRs through the
	   encoder and measuring the active bitrate. */
	private static final char silk_TargetRate_NB_21[] = {// [117 - 10] = {
	                                              0, 15, 39, 52, 61, 68,
	     74, 79, 84, 88, 92, 95, 99,102,105,108,111,114,117,119,122,124,
	    126,129,131,133,135,137,139,142,143,145,147,149,151,153,155,157,
	    158,160,162,163,165,167,168,170,171,173,174,176,177,179,180,182,
	    183,185,186,187,189,190,192,193,194,196,197,199,200,201,203,204,
	    205,207,208,209,211,212,213,215,216,217,219,220,221,223,224,225,
	    227,228,230,231,232,234,235,236,238,239,241,242,243,245,246,248,
	    249,250,252,253,255
	};

	private static final char silk_TargetRate_MB_21[] = {// [165 - 10] = {
	                                              0,  0, 28, 43, 52, 59,
	     65, 70, 74, 78, 81, 85, 87, 90, 93, 95, 98,100,102,105,107,109,
	    111,113,115,116,118,120,122,123,125,127,128,130,131,133,134,136,
	    137,138,140,141,143,144,145,147,148,149,151,152,153,154,156,157,
	    158,159,160,162,163,164,165,166,167,168,169,171,172,173,174,175,
	    176,177,178,179,180,181,182,183,184,185,186,187,188,188,189,190,
	    191,192,193,194,195,196,197,198,199,200,201,202,203,203,204,205,
	    206,207,208,209,210,211,212,213,214,214,215,216,217,218,219,220,
	    221,222,223,224,224,225,226,227,228,229,230,231,232,233,234,235,
	    236,236,237,238,239,240,241,242,243,244,245,246,247,248,249,250,
	    251,252,253,254,255
	};

	private static final char silk_TargetRate_WB_21[] = {// [201 - 10] = {
	                                              0,  0,  0,  8, 29, 41,
	     49, 56, 62, 66, 70, 74, 77, 80, 83, 86, 88, 91, 93, 95, 97, 99,
	    101,103,105,107,108,110,112,113,115,116,118,119,121,122,123,125,
	    126,127,129,130,131,132,134,135,136,137,138,140,141,142,143,144,
	    145,146,147,148,149,150,151,152,153,154,156,157,158,159,159,160,
	    161,162,163,164,165,166,167,168,169,170,171,171,172,173,174,175,
	    176,177,177,178,179,180,181,181,182,183,184,185,185,186,187,188,
	    189,189,190,191,192,192,193,194,195,195,196,197,198,198,199,200,
	    200,201,202,203,203,204,205,206,206,207,208,209,209,210,211,211,
	    212,213,214,214,215,216,216,217,218,219,219,220,221,221,222,223,
	    224,224,225,226,226,227,228,229,229,230,231,232,232,233,234,234,
	    235,236,237,237,238,239,240,240,241,242,243,243,244,245,246,246,
	    247,248,249,249,250,251,252,253,255
	};

	/**
	 * Control SNR of residual quantizer
	 * @param psEncC
	 * @param TargetRate_bps
	 * @return
	 */
	private static final int silk_control_SNR(final Jsilk_encoder_state psEncC, int TargetRate_bps)
	{
		int bound;
		final char[] snr_table;

		psEncC.TargetRate_bps = TargetRate_bps;
		if( psEncC.nb_subfr == 2 ) {
			TargetRate_bps -= 2000 + (psEncC.fs_kHz >>> 4);
		}
		if( psEncC.fs_kHz == 8 ) {
			bound = silk_TargetRate_NB_21.length;
			snr_table = silk_TargetRate_NB_21;
		} else if( psEncC.fs_kHz == 12 ) {
			bound = silk_TargetRate_MB_21.length;
			snr_table = silk_TargetRate_MB_21;
		} else {
			bound = silk_TargetRate_WB_21.length;
			snr_table = silk_TargetRate_WB_21;
		}
		int id = (TargetRate_bps + 200) / 400;
		bound--;// java id = silk_min(id - 10, bound - 1);
		id -= 10;
		if( id > bound ) {
			id = bound;
		}
		if( id <= 0 ) {
			psEncC.SNR_dB_Q7 = 0;
		} else {
			psEncC.SNR_dB_Q7 = (int)snr_table[id] * 21;
		}
	    return Jerrors.SILK_NO_ERROR;

	}
	// end control_SNR.c

	// start HP_variable_cutoff.c
	/**
	 * High-pass filter with cutoff frequency adaptation based on pitch lag statistics
	 *
	 * @param state_Fxx  I/O  Encoder states
	 */
	private static final void silk_HP_variable_cutoff(final Jsilk_encoder_state_FLP state_Fxx[])
	{
		final Jsilk_encoder_state psEncC1 = state_Fxx[ 0 ].sCmn;

		/* Adaptive cutoff frequency: estimate low end of pitch frequency range */
		if( psEncC1.prevSignalType == Jdefine.TYPE_VOICED ) {
			/* difference, in log domain */
			final int pitch_freq_Hz_Q16 = ((psEncC1.fs_kHz * 100) << 16) / psEncC1.prevLag;
			int pitch_freq_log_Q7 = Jmacros.silk_lin2log( pitch_freq_Hz_Q16 ) - (16 << 7);

			/* adjustment based on quality */
			final int quality_Q15 = psEncC1.input_quality_bands_Q15[ 0 ];
			//pitch_freq_log_Q7 = Jmacros.silk_SMLAWB( pitch_freq_log_Q7, Jmacros.silk_SMULWB( ( -quality_Q15 << 2 ), quality_Q15 ),
			//		pitch_freq_log_Q7 - ( Jmacros.silk_lin2log( SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ, 16 ) ) - ( 16 << 7 ) ) );
			pitch_freq_log_Q7 += (int)((((( -quality_Q15 << 2 ) * (long)quality_Q15) >> 16) *
					(long)(pitch_freq_log_Q7 - (Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ * (1 << 16) ) - ( 16 << 7 )))) >> 16);
			/* delta_freq = pitch_freq_log - psEnc.variable_HP_smth1; */
			int delta_freq_Q7 = pitch_freq_log_Q7 - ( psEncC1.variable_HP_smth1_Q15 >> 8 );
			if( delta_freq_Q7 < 0 ) {
				/* less smoothing for decreasing pitch frequency, to track something close to the minimum */
				delta_freq_Q7 *= 3;
			}

			/* limit delta, to reduce impact of outliers in pitch estimation */
			// delta_freq_Q7 = silk_LIMIT( delta_freq_Q7, -SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_MAX_DELTA_FREQ, 7 ), SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_MAX_DELTA_FREQ, 7 ) );
			final int max_delta = ((int)(Jtuning_parameters.VARIABLE_HP_MAX_DELTA_FREQ * (1 << 7) + .5f));// java
			delta_freq_Q7 = (delta_freq_Q7 > max_delta ? max_delta : (delta_freq_Q7 < -max_delta ? -max_delta : delta_freq_Q7));

			/* update smoother */
			// psEncC1.variable_HP_smth1_Q15 = Jmacros.silk_SMLAWB( psEncC1.variable_HP_smth1_Q15,
			// 		( psEncC1.speech_activity_Q8 * delta_freq_Q7 ), SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_SMTH_COEF1, 16 ) );
			psEncC1.variable_HP_smth1_Q15 += (int)((( psEncC1.speech_activity_Q8 * delta_freq_Q7 ) *
					((long)(Jtuning_parameters.VARIABLE_HP_SMTH_COEF1 * (1 << 16) + .5f))) >> 16);
			/* limit frequency range */
			psEncC1.variable_HP_smth1_Q15 = JSigProc_FIX.silk_LIMIT( psEncC1.variable_HP_smth1_Q15,
					( Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ ) << 8 ),
					( Jmacros.silk_lin2log( Jtuning_parameters.VARIABLE_HP_MAX_CUTOFF_HZ ) << 8 ) );
		}
	}
	// end HP_variable_cutoff.c

	// start stereo_encode_pred.c
	/**
	 * Entropy code the mid/side quantization indices
	 *
	 * @param psRangeEnc I/O  Compressor data structure
	 * @param ix I    Quantization indices
	 */
	private static final void silk_stereo_encode_pred(final Jec_enc psRangeEnc, final byte ix[/* 2 */][/* 3 */])
	{
		/* Entropy coding */
		int n = 5 * ix[ 0 ][ 2 ] + ix[ 1 ][ 2 ];
		// celt_assert( n < 25 );
		psRangeEnc.ec_enc_icdf( n, Jtables_other.silk_stereo_pred_joint_iCDF, 0, 8 );
		for( n = 0; n < 2; n++ ) {
			// celt_assert( ix[ n ][ 0 ] < 3 );
			// celt_assert( ix[ n ][ 1 ] < STEREO_QUANT_SUB_STEPS );
			psRangeEnc.ec_enc_icdf( ix[ n ][ 0 ], Jtables_other.silk_uniform3_iCDF, 0, 8 );
			psRangeEnc.ec_enc_icdf( ix[ n ][ 1 ], Jtables_other.silk_uniform5_iCDF, 0, 8 );
		}
	}

	/**
	 * Entropy code the mid-only flag
	 *
	 * @param psRangeEnc I/O  Compressor data structure
	 * @param mid_only_flag
	 */
	static final void silk_stereo_encode_mid_only(final Jec_enc psRangeEnc, final byte mid_only_flag)
	{
		/* Encode flag that only mid channel is coded */
		psRangeEnc.ec_enc_icdf( mid_only_flag, Jtables_other.silk_stereo_only_code_mid_iCDF, 0, 8 );
	}
	// end stereo_encode_pred.c

	// start enc_API.c
	/****************************************/
	/* Encoder functions                    */
	/****************************************/

	/**
	 *
	 * @param encSizeBytes O    Number of bytes in SILK encoder state
	 * @return O    Returns error code
	 */
	/*private static final int silk_Get_Encoder_Size(final int[] encSizeBytes)
	{
		final int ret = Jerrors.SILK_NO_ERROR;

		encSizeBytes[0] = sizeof( Jsilk_encoder );

		return ret;
	}*/

	/**
	 * Read control structure from encoder
	 *
	 * @param encState I    State
	 * @param encStatus O    Encoder Status
	 * @return O    Returns error code
	 */
	private final int silk_QueryEncoder(final Jsilk_EncControlStruct encStatus)
	{
		final int ret = Jerrors.SILK_NO_ERROR;
		//final Jsilk_encoder psEnc = (Jsilk_encoder)encState;

		final Jsilk_encoder_state_FLP state = this.state_Fxx[ 0 ];// java changed

		encStatus.nChannelsAPI              = this.nChannelsAPI;
		encStatus.nChannelsInternal         = this.nChannelsInternal;
		encStatus.API_sampleRate            = state.sCmn.API_fs_Hz;
		encStatus.maxInternalSampleRate     = state.sCmn.maxInternal_fs_Hz;
		encStatus.minInternalSampleRate     = state.sCmn.minInternal_fs_Hz;
		encStatus.desiredInternalSampleRate = state.sCmn.desiredInternal_fs_Hz;
		encStatus.payloadSize_ms            = state.sCmn.PacketSize_ms;
		encStatus.bitRate                   = state.sCmn.TargetRate_bps;
		encStatus.packetLossPercentage      = state.sCmn.PacketLoss_perc;
		encStatus.complexity                = state.sCmn.Complexity;
		encStatus.useInBandFEC              = state.sCmn.useInBandFEC;
		encStatus.useDTX                    = state.sCmn.useDTX;
		encStatus.useCBR                    = state.sCmn.useCBR;
		encStatus.internalSampleRate        = state.sCmn.fs_kHz * 1000;
		encStatus.allowBandwidthSwitch      = state.sCmn.allow_bandwidth_switch;
		encStatus.inWBmodeWithoutVariableLP = state.sCmn.fs_kHz == 16 && state.sCmn.sLP.mode == 0;

		return ret;
	}
	/**
	 * Init or Reset encoder
	 *
	 * @param encState I/O  State
	 * @param arch I    Run-time architecture
	 * @param encStatus O    Encoder Status
	 * @return O    Returns error code
	 */
	public final int silk_InitEncoder(/*final int arch, */
			final Jsilk_EncControlStruct encStatus)
	{
		int ret = Jerrors.SILK_NO_ERROR;

		//final Jsilk_encoder psEnc = (Jsilk_encoder)encState;

		/* Reset encoder */
		// silk_memset( psEnc, 0, sizeof( silk_encoder ) );
		clear();
		final Jsilk_encoder_state_FLP[] state = this.state_Fxx;
		for( int n = 0; n < Jdefine.ENCODER_NUM_CHANNELS; n++ ) {
			if( 0 != (ret += state[ n ].silk_init_encoder(/* arch )*/)) ) {
				// celt_assert( 0 );
			}
		}

		this.nChannelsAPI = 1;
		this.nChannelsInternal = 1;

		/* Read control structure */
		if( 0 != (ret += silk_QueryEncoder( encStatus )) ) {
			// celt_assert( 0 );
		}

		return ret;
	}
	/**
	 * Encode frame with Silk
	 * Note: if prefillFlag is set, the input must contain 10 ms of audio, irrespective of what
	 * encControl.payloadSize_ms is set to
	 *
	 * @param encState I/O  State
	 * @param encControl I    Control status
	 * @param samplesIn I    Speech sample input vector
	 * @param nSamplesIn I    Number of samples in input vector
	 * @param psRangeEnc I/O  Compressor data structure
	 * @param nBytesOut I/O  Number of bytes in payload (input: Max bytes)
	 * @param prefillFlag I    Flag to indicate prefilling buffers no coding
	 * @param activity I    Decision of Opus voice activity detector
	 * @return O    Returns error code
	 */
	public final int silk_Encode(final Jsilk_EncControlStruct encControl,
			final short[] samplesIn, int nSamplesIn,
			final Jec_enc psRangeEnc, final int[] nBytesOut, final int prefillFlag, final int activity)
	{
		int ret = 0;
		//int   nSamplesFromInput = 0;
		// SAVE_STACK;

		final Jsilk_encoder_state_FLP[] state = this.state_Fxx;// java
		final Jsilk_encoder_state sCmn0 = state[ 0 ].sCmn;// java
		final Jsilk_encoder_state sCmn1 = state[ 1 ].sCmn;// java
		if( encControl.reducedDependency )
		{
			sCmn0.first_frame_after_reset = true;
			sCmn1.first_frame_after_reset = true;
		}
		sCmn0.nFramesEncoded = sCmn1.nFramesEncoded = 0;

		/* Check values in encoder control structure */
		if( ( ret = check_control_input( encControl ) ) != 0 ) {
			// celt_assert( 0 );
			// RESTORE_STACK;
			return ret;
		}

		encControl.switchReady = false;

		if( encControl.nChannelsInternal > this.nChannelsInternal ) {
			/* Mono -> Stereo transition: init state of second channel and stereo state */
			ret += state[ 1 ].silk_init_encoder();//, psEnc.state_Fxx[ 0 ].sCmn.arch );
			// silk_memset( psEnc.sStereo.pred_prev_Q13, 0, sizeof( psEnc.sStereo.pred_prev_Q13 ) );
			short[] sbuff = this.sStereo.pred_prev_Q13;
			for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
				sbuff[i] = 0;
			}
			// silk_memset( psEnc.sStereo.sSide, 0, sizeof( psEnc.sStereo.sSide ) );
			sbuff = this.sStereo.sSide;
			for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
				sbuff[i] = 0;
			}
			this.sStereo.mid_side_amp_Q0[ 0 ] = 0;
			this.sStereo.mid_side_amp_Q0[ 1 ] = 1;
			this.sStereo.mid_side_amp_Q0[ 2 ] = 0;
			this.sStereo.mid_side_amp_Q0[ 3 ] = 1;
			this.sStereo.width_prev_Q14 = 0;
			// this.sStereo.smth_width_Q14 = (short)SILK_FIX_CONST( 1, 14 );
			this.sStereo.smth_width_Q14 = (short)(1 << 14);
			if( this.nChannelsAPI == 2 ) {
				sCmn1.resampler_state.copyFrom( sCmn0.resampler_state );
				System.arraycopy( sCmn0.In_HP_State, 0, sCmn1.In_HP_State, 0, sCmn1.In_HP_State.length );
			}
		}

		final boolean transition = (encControl.payloadSize_ms != sCmn0.PacketSize_ms) || (this.nChannelsInternal != encControl.nChannelsInternal);

		this.nChannelsAPI = encControl.nChannelsAPI;
		this.nChannelsInternal = encControl.nChannelsInternal;

		final int nBlocksOf10ms = (100 * nSamplesIn) / encControl.API_sampleRate;
		final int tot_blocks = ( nBlocksOf10ms > 1 ) ? nBlocksOf10ms >> 1 : 1;
		int curr_block = 0, tmp_payloadSize_ms = 0, tmp_complexity = 0;
		if( prefillFlag != 0 ) {
			/* Only accept input length of 10 ms */
			if( nBlocksOf10ms != 1 ) {
				// celt_assert( 0 );
				// RESTORE_STACK;
				return Jerrors.SILK_ENC_INPUT_INVALID_NO_OF_SAMPLES;
			}
			Jsilk_LP_state save_LP = null;// java = new Jsilk_LP_state();
			if( prefillFlag == 2 ) {
				save_LP = new Jsilk_LP_state( sCmn0.sLP );// java
				/* Save the sampling rate so the bandwidth switching code can keep handling transitions. */
				save_LP.saved_fs_kHz = sCmn0.fs_kHz;// java
			}
			/* Reset Encoder */
			for( int n = 0; n < encControl.nChannelsInternal; n++ ) {
				ret = state[ n ].silk_init_encoder();//, psEnc.state_Fxx[ n ].sCmn.arch );
				if( prefillFlag == 2 ) {
					state[ n ].sCmn.sLP.copyFrom( save_LP );
				}
				// celt_assert( !ret );
			}
			tmp_payloadSize_ms = encControl.payloadSize_ms;
			encControl.payloadSize_ms = 10;
			tmp_complexity = encControl.complexity;
			encControl.complexity = 0;
			for( int n = 0; n < encControl.nChannelsInternal; n++ ) {
				state[ n ].sCmn.controlled_since_last_payload = 0;
				state[ n ].sCmn.prefillFlag = true;
			}
		} else {
			/* Only accept input lengths that are a multiple of 10 ms */
			if( nBlocksOf10ms * encControl.API_sampleRate != 100 * nSamplesIn || nSamplesIn < 0 ) {
				// celt_assert( 0 );
				// RESTORE_STACK;
				return Jerrors.SILK_ENC_INPUT_INVALID_NO_OF_SAMPLES;
			}
			/* Make sure no more than one packet can be produced */
			if( 1000 * nSamplesIn > encControl.payloadSize_ms * encControl.API_sampleRate ) {
				// celt_assert( 0 );
				// RESTORE_STACK;
				return Jerrors.SILK_ENC_INPUT_INVALID_NO_OF_SAMPLES;
			}
		}

		for( int n = 0; n < encControl.nChannelsInternal; n++ ) {
			/* Force the side channel to the same rate as the mid */
			final int force_fs_kHz = (n == 1) ? sCmn0.fs_kHz : 0;
			if( ( ret = state[ n ].silk_control_encoder( encControl, this.allowBandwidthSwitch, n, force_fs_kHz ) ) != 0 ) {
				// silk_assert( 0 );
				// RESTORE_STACK;
				return ret;
			}
			if( state[ n ].sCmn.first_frame_after_reset || transition ) {
				final boolean[] b = state[ n ].sCmn.LBRR_flags;// java
				for( int i = 0, ie = sCmn0.nFramesPerPacket; i < ie; i++ ) {
					b[ i ] = false;
				}
			}
			state[ n ].sCmn.inDTX = state[ n ].sCmn.useDTX;
		}
		// celt_assert( encControl.nChannelsInternal == 1 || psEnc.state_Fxx[ 0 ].sCmn.fs_kHz == psEnc.state_Fxx[ 1 ].sCmn.fs_kHz );

		/* Input buffering/resampling and encoding */
		final int nSamplesToBufferMax = 10 * nBlocksOf10ms * sCmn0.fs_kHz;
		final int nSamplesFromInputMax = ((nSamplesToBufferMax * sCmn0.API_fs_Hz) / (sCmn0.fs_kHz * 1000) );
		final short[] buf = new short[nSamplesFromInputMax];
		final int MStargetRates_bps[] = new int[ 2 ];
		int samplesIn_ptr = 0;// samplesIn[ samplesIn_ptr ]
		while( true ) {
			int nSamplesToBuffer = sCmn0.frame_length - sCmn0.inputBufIx;
			nSamplesToBuffer = nSamplesToBuffer < nSamplesToBufferMax ? nSamplesToBuffer : nSamplesToBufferMax;
			final int nSamplesFromInput = (nSamplesToBuffer * sCmn0.API_fs_Hz) / (sCmn0.fs_kHz * 1000);
			/* Resample and write to buffer */
			if( encControl.nChannelsAPI == 2 && encControl.nChannelsInternal == 2 ) {
				final int id = sCmn0.nFramesEncoded;
				for( int n = 0, n2 = samplesIn_ptr; n < nSamplesFromInput; n++, n2 += 2 ) {
					buf[ n ] = samplesIn[ n2 ];
				}
				/* Making sure to start both resamplers from the same state when switching from mono to stereo */
				if( this.nPrevChannelsInternal == 1 && id == 0 ) {
					sCmn1.resampler_state.copyFrom( sCmn0.resampler_state );
				}

				ret += sCmn0.resampler_state.silk_resampler( sCmn0.inputBuf, sCmn0.inputBufIx + 2, buf, 0, nSamplesFromInput );
				sCmn0.inputBufIx += nSamplesToBuffer;

				nSamplesToBuffer = sCmn1.frame_length - sCmn1.inputBufIx;
				int n = 10 * nBlocksOf10ms * sCmn1.fs_kHz;// java
				nSamplesToBuffer = nSamplesToBuffer < n ? nSamplesToBuffer : n;
				n = 0;
				for( int n2 = samplesIn_ptr + 1; n < nSamplesFromInput; n++, n2 += 2 ) {
					buf[ n ] = samplesIn[ n2 ];
				}
				ret += sCmn1.resampler_state.silk_resampler( sCmn1.inputBuf, sCmn1.inputBufIx + 2, buf, 0, nSamplesFromInput );

				sCmn1.inputBufIx += nSamplesToBuffer;
			} else if( encControl.nChannelsAPI == 2 && encControl.nChannelsInternal == 1 ) {
				/* Combine left and right channels before resampling */
				for( int n = 0, n2 = samplesIn_ptr; n < nSamplesFromInput; n++ ) {
					int sum = (int)samplesIn[ n2++ ];
					sum += (int)samplesIn[ n2++ ];
					buf[ n ] = (short)JSigProc_FIX.silk_RSHIFT_ROUND( sum, 1 );
				}
				ret += sCmn0.resampler_state.silk_resampler( sCmn0.inputBuf, sCmn0.inputBufIx + 2, buf, 0, nSamplesFromInput );
				/* On the first mono frame, average the results for the two resampler states  */
				if( this.nPrevChannelsInternal == 2 && sCmn0.nFramesEncoded == 0 ) {
					ret += sCmn1.resampler_state.silk_resampler( sCmn1.inputBuf, sCmn1.inputBufIx + 2, buf, 0, nSamplesFromInput );
					final short[] in0 = sCmn0.inputBuf;// java
					final short[] in1 = sCmn1.inputBuf;// java
					for( int n = sCmn0.inputBufIx + 2, ne = n + sCmn0.frame_length, n1 = sCmn1.inputBufIx + 2; n < ne; n++, n1++ ) {
						in0[ n ] = (short)((in0[ n ] + in1[ n1 ]) >> 1);
					}
				}
				sCmn0.inputBufIx += nSamplesToBuffer;
			} else {
				// celt_assert( encControl.nChannelsAPI == 1 && encControl.nChannelsInternal == 1 );
				System.arraycopy( samplesIn, samplesIn_ptr, buf, 0, nSamplesFromInput );
				ret += sCmn0.resampler_state.silk_resampler( sCmn0.inputBuf, sCmn0.inputBufIx + 2, buf, 0, nSamplesFromInput );
				sCmn0.inputBufIx += nSamplesToBuffer;
			}

			samplesIn_ptr += nSamplesFromInput * encControl.nChannelsAPI;
			nSamplesIn -= nSamplesFromInput;

			/* Default */
			this.allowBandwidthSwitch = false;

			/* Silk encoder */
			if( sCmn0.inputBufIx >= sCmn0.frame_length ) {
				/* Enough data in input buffer, so encode */
				// celt_assert( psEnc.state_Fxx[ 0 ].sCmn.inputBufIx == psEnc.state_Fxx[ 0 ].sCmn.frame_length );
				// celt_assert( encControl.nChannelsInternal == 1 || psEnc.state_Fxx[ 1 ].sCmn.inputBufIx == psEnc.state_Fxx[ 1 ].sCmn.frame_length );

				/* Deal with LBRR data */
				if( sCmn0.nFramesEncoded == 0 && 0 == prefillFlag ) {
					/* Create space at start of payload for VAD and FEC flags */
					final char iCDF[/* 2 */] = { 0, 0 };// java uint9 to char
					iCDF[ 0 ] = (char)(256 - (256 >> (( sCmn0.nFramesPerPacket + 1 ) * encControl.nChannelsInternal)));
					psRangeEnc.ec_enc_icdf( 0, iCDF, 0, 8 );

					/* Encode any LBRR data from previous packet */
					/* Encode LBRR flags */
					for( int n = 0, ne = encControl.nChannelsInternal; n < ne; n++ ) {
						final Jsilk_encoder_state sCmn_n = state[ n ].sCmn;// java
						int LBRR_symbol = 0;
						for( int i = 0, ie = sCmn_n.nFramesPerPacket; i < ie; i++ ) {
							LBRR_symbol |= (sCmn_n.LBRR_flags[ i ] ? 1 : 0) << i;
						}
						sCmn_n.LBRR_flag = LBRR_symbol > 0;// ? 1 : 0;
						if( 0 != LBRR_symbol && sCmn_n.nFramesPerPacket > 1 ) {
							psRangeEnc.ec_enc_icdf( LBRR_symbol - 1, Jtables_other.silk_LBRR_flags_iCDF_ptr[ sCmn_n.nFramesPerPacket - 2 ], 0, 8 );
						}
					}

					/* Code LBRR indices and excitation signals */
					for( int i = 0, ie = sCmn0.nFramesPerPacket; i < ie; i++ ) {
						for( int n = 0, ne = encControl.nChannelsInternal; n < ne; n++ ) {
							final Jsilk_encoder_state sCmn_n = state[ n ].sCmn;// java
							if( sCmn_n.LBRR_flags[ i ] ) {
								if( encControl.nChannelsInternal == 2 && n == 0 ) {
									silk_stereo_encode_pred( psRangeEnc, this.sStereo.predIx[ i ] );
									/* For LBRR data there's no need to code the mid-only flag if the side-channel LBRR flag is set */
									if( ! sCmn1.LBRR_flags[ i ] ) {
										silk_stereo_encode_mid_only( psRangeEnc, this.sStereo.mid_only_flags[ i ] );
									}
								}
								/* Use conditional coding if previous frame available */
								final int condCoding = (i > 0 && sCmn_n.LBRR_flags[ i - 1 ]) ?
										Jdefine.CODE_CONDITIONALLY : Jdefine.CODE_INDEPENDENTLY;

								sCmn_n.silk_encode_indices( psRangeEnc, i, true, condCoding );
								silk_encode_pulses( psRangeEnc, sCmn_n.indices_LBRR[i].signalType, sCmn_n.indices_LBRR[i].quantOffsetType,
													sCmn_n.pulses_LBRR[ i ], sCmn_n.frame_length );
							}
						}
					}

					/* Reset LBRR flags */
					for( int n = 0, ne = encControl.nChannelsInternal; n < ne; n++ ) {
						// silk_memset( psEnc.state_Fxx[ n ].sCmn.LBRR_flags, 0, sizeof( psEnc.state_Fxx[ n ].sCmn.LBRR_flags ) );
						final boolean[] buff = state[ n ].sCmn.LBRR_flags;
						for( int i = 0, ie = buff.length; i < ie; i++ ) {
							buff[i] = false;
						}
					}

					this.nBitsUsedLBRR = psRangeEnc.ec_tell();
				}

				silk_HP_variable_cutoff( state );

				/* Total target bits for packet */
				int nBits = (encControl.bitRate * encControl.payloadSize_ms) / 1000;
				/* Subtract bits used for LBRR */
				if( 0 == prefillFlag ) {
					nBits -= this.nBitsUsedLBRR;
				}
				/* Divide by number of uncoded frames left in packet */
				nBits /= sCmn0.nFramesPerPacket;
				/* Convert to bits/second */
				int TargetRate_bps;
				if( encControl.payloadSize_ms == 10 ) {
					TargetRate_bps = nBits * 100;
				} else {
					TargetRate_bps = nBits * 50;
				}
				/* Subtract fraction of bits in excess of target in previous frames and packets */
				TargetRate_bps -= (this.nBitsExceeded * 1000) / Jtuning_parameters.BITRESERVOIR_DECAY_TIME_MS;
				if( 0 == prefillFlag && sCmn0.nFramesEncoded > 0 ) {
					/* Compare actual vs target bits so far in this packet */
					final int bitsBalance = psRangeEnc.ec_tell() - this.nBitsUsedLBRR - nBits * sCmn0.nFramesEncoded;
					TargetRate_bps -= (bitsBalance * 1000) / Jtuning_parameters.BITRESERVOIR_DECAY_TIME_MS;
				}
				/* Never exceed input bitrate */
				TargetRate_bps = JSigProc_FIX.silk_LIMIT( TargetRate_bps, encControl.bitRate, 5000 );

				/* Convert Left/Right to Mid/Side */
				if( encControl.nChannelsInternal == 2 ) {
					final Jsilk_encoder_state_FLP state_1 = state[ 1 ];// java
					this.sStereo.silk_stereo_LR_to_MS( sCmn0.inputBuf, 2, state_1.sCmn.inputBuf, 2,
							this.sStereo.predIx[ sCmn0.nFramesEncoded ], this.sStereo.mid_only_flags, sCmn0.nFramesEncoded,
							MStargetRates_bps, TargetRate_bps, sCmn0.speech_activity_Q8, encControl.toMono,
							sCmn0.fs_kHz, sCmn0.frame_length );
					if( this.sStereo.mid_only_flags[ sCmn0.nFramesEncoded ] == 0 ) {
						/* Reset side channel encoder memory for first frame with side coding */
						if( this.prev_decode_only_middle == 1 ) {
							//silk_memset( &psEnc.state_Fxx[ 1 ].sShape,               0, sizeof( psEnc.state_Fxx[ 1 ].sShape ) );
							state_1.sShape.clear();
							// silk_memset( &psEnc.state_Fxx[ 1 ].sCmn.sNSQ,            0, sizeof( psEnc.state_Fxx[ 1 ].sCmn.sNSQ ) );
							state_1.sCmn.sNSQ.clear();
							// silk_memset( psEnc.state_Fxx[ 1 ].sCmn.prev_NLSFq_Q15,   0, sizeof( psEnc.state_Fxx[ 1 ].sCmn.prev_NLSFq_Q15 ) );
							final short[] sbuff = state_1.sCmn.prev_NLSFq_Q15;
							for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
								sbuff[i] = 0;
							}
							// silk_memset( &psEnc.state_Fxx[ 1 ].sCmn.sLP.In_LP_State, 0, sizeof( psEnc.state_Fxx[ 1 ].sCmn.sLP.In_LP_State ) );
							final int[] ibuff = state_1.sCmn.sLP.In_LP_State;
							for( int i = 0, ie = ibuff.length; i < ie; i++ ) {
								ibuff[i] = 0;
							}
							state_1.sCmn.prevLag                 = 100;
							state_1.sCmn.sNSQ.lagPrev            = 100;
							state_1.sShape.LastGainIndex         = 10;
							state_1.sCmn.prevSignalType          = Jdefine.TYPE_NO_VOICE_ACTIVITY;
							state_1.sCmn.sNSQ.prev_gain_Q16      = 65536;
							state_1.sCmn.first_frame_after_reset = true;
						}
						state_1.silk_encode_do_VAD_FLP( activity );
					} else {
						state_1.sCmn.VAD_flags[ sCmn0.nFramesEncoded ] = 0;
					}
					if( 0 == prefillFlag ) {
						silk_stereo_encode_pred( psRangeEnc, this.sStereo.predIx[ sCmn0.nFramesEncoded ] );
						if( state_1.sCmn.VAD_flags[ sCmn0.nFramesEncoded ] == 0 ) {
							silk_stereo_encode_mid_only( psRangeEnc, this.sStereo.mid_only_flags[ sCmn0.nFramesEncoded ] );
						}
					}
				} else {
					/* Buffering */
					final short[] inputBuf = sCmn0.inputBuf;
					final short[] mid = this.sStereo.sMid;
					inputBuf[0] = mid[0];
					inputBuf[1] = mid[1];
					int soffset = sCmn0.frame_length;
					mid[0] = inputBuf[soffset++];
					mid[1] = inputBuf[soffset];
				}
				state[ 0 ].silk_encode_do_VAD_FLP( activity );

				/* Encode */
				for( int n = 0, ne = encControl.nChannelsInternal; n < ne; n++ ) {
					/* Handling rate constraints */
					int maxBits = encControl.maxBits;
					if( tot_blocks == 2 && curr_block == 0 ) {
						maxBits = maxBits * 3 / 5;
					} else if( tot_blocks == 3 ) {
						if( curr_block == 0 ) {
							maxBits = maxBits * 2 / 5;
						} else if( curr_block == 1 ) {
							maxBits = (maxBits * 3) >>> 2;
						}
					}
					boolean useCBR = encControl.useCBR && curr_block == tot_blocks - 1;

					int channelRate_bps;
					if( encControl.nChannelsInternal == 1 ) {
						channelRate_bps = TargetRate_bps;
					} else {
						channelRate_bps = MStargetRates_bps[ n ];
						if( n == 0 && MStargetRates_bps[ 1 ] > 0 ) {
							useCBR = false;
							/* Give mid up to 1/2 of the max bits for that frame */
							maxBits -= encControl.maxBits / ( tot_blocks * 2 );
						}
					}

					final Jsilk_encoder_state n_sCmn = state[ n ].sCmn;// java
					if( channelRate_bps > 0 ) {
						silk_control_SNR( n_sCmn, channelRate_bps );

						/* Use independent coding if no previous frame available */
						int condCoding;
						if( sCmn0.nFramesEncoded - n <= 0 ) {
							condCoding = Jdefine.CODE_INDEPENDENTLY;
						} else if( n > 0 && this.prev_decode_only_middle != 0 ) {
							/* If we skipped a side frame in this packet, we don't
							need LTP scaling; the LTP state is well-defined. */
							condCoding = Jdefine.CODE_INDEPENDENTLY_NO_LTP_SCALING;
						} else {
							condCoding = Jdefine.CODE_CONDITIONALLY;
						}
						if( (ret = state[ n ].silk_encode_frame_FLP( nBytesOut, psRangeEnc, condCoding, maxBits, useCBR )) != 0 ) {
							// silk_assert( 0 );
						}
					}
					n_sCmn.controlled_since_last_payload = 0;
					n_sCmn.inputBufIx = 0;
					n_sCmn.nFramesEncoded++;
				}
				this.prev_decode_only_middle = this.sStereo.mid_only_flags[ sCmn0.nFramesEncoded - 1 ];

				/* Insert VAD and FEC flags at beginning of bitstream */
				if( nBytesOut[ 0 ] > 0 && sCmn0.nFramesEncoded == sCmn0.nFramesPerPacket) {
					int flags = 0;
					for( int n = 0, ne = encControl.nChannelsInternal; n < ne; n++ ) {
						final Jsilk_encoder_state n_sCmn = state[ n ].sCmn;// java
						for( int i = 0, ie = n_sCmn.nFramesPerPacket; i < ie; i++ ) {
							flags <<= 1;
							flags |= n_sCmn.VAD_flags[ i ];
						}
						flags <<= 1;
						if( n_sCmn.LBRR_flag ) {
							flags |= 1;
						}
					}
					if( 0 == prefillFlag ) {
						psRangeEnc.ec_enc_patch_initial_bits( flags, ( sCmn0.nFramesPerPacket + 1 ) * encControl.nChannelsInternal );
					}

					/* Return zero bytes if all channels DTXed */
					if( sCmn0.inDTX && ( encControl.nChannelsInternal == 1 || sCmn1.inDTX ) ) {
						nBytesOut[0] = 0;
					}

					this.nBitsExceeded += nBytesOut[0] << 3;
					this.nBitsExceeded -= ((encControl.bitRate * encControl.payloadSize_ms) / 1000);
					this.nBitsExceeded  = (this.nBitsExceeded > 10000 ? 10000 : (this.nBitsExceeded < 0 ? 0 : this.nBitsExceeded));

					/* Update flag indicating if bandwidth switching is allowed */
					// final int speech_act_thr_for_switch_Q8 = Jmacros.silk_SMLAWB( SILK_FIX_CONST( Jtuning_parameters.SPEECH_ACTIVITY_DTX_THRES, 8 ),
					//		SILK_FIX_CONST( ( 1 - Jtuning_parameters.SPEECH_ACTIVITY_DTX_THRES ) / Jtuning_parameters.MAX_BANDWIDTH_SWITCH_DELAY_MS, 16 + 8 ), this.timeSinceSwitchAllowed_ms );
					final int speech_act_thr_for_switch_Q8 = ((int)(Jtuning_parameters.SPEECH_ACTIVITY_DTX_THRES * (1 << 8) + .5f)) +
							(int)((((long)(((1f - Jtuning_parameters.SPEECH_ACTIVITY_DTX_THRES) / Jtuning_parameters.MAX_BANDWIDTH_SWITCH_DELAY_MS) *
							(1 << (16 + 8)) + .5f)) * (long)this.timeSinceSwitchAllowed_ms) >> 16);
					if( sCmn0.speech_activity_Q8 < speech_act_thr_for_switch_Q8 ) {
						this.allowBandwidthSwitch = true;
						this.timeSinceSwitchAllowed_ms = 0;
					} else {
						this.allowBandwidthSwitch = false;
						this.timeSinceSwitchAllowed_ms += encControl.payloadSize_ms;
					}
				}

				if( nSamplesIn == 0 ) {
					break;
				}
			} else {
				break;
			}
			curr_block++;
		}

		this.nPrevChannelsInternal = encControl.nChannelsInternal;

		encControl.allowBandwidthSwitch = this.allowBandwidthSwitch;
		encControl.inWBmodeWithoutVariableLP = sCmn0.fs_kHz == 16 && sCmn0.sLP.mode == 0;
		encControl.internalSampleRate = sCmn0.fs_kHz * 1000;
		encControl.stereoWidth_Q14 = encControl.toMono ? 0 : this.sStereo.smth_width_Q14;
		if( 0 != prefillFlag ) {
			encControl.payloadSize_ms = tmp_payloadSize_ms;
			encControl.complexity = tmp_complexity;
			for( int n = 0; n < encControl.nChannelsInternal; n++ ) {
				final Jsilk_encoder_state sCmn = state[ n ].sCmn;// java
				sCmn.controlled_since_last_payload = 0;
				sCmn.prefillFlag = false;
			}
		}

		final JSideInfoIndices indices = sCmn0.indices;// java
		encControl.signalType = indices.signalType;
		encControl.offset = Jtables_other.silk_Quantization_Offsets_Q10
							[ indices.signalType >> 1 ]
							[ indices.quantOffsetType ];
		// RESTORE_STACK;
		return ret;
	}
	// end enc_API.c
}
