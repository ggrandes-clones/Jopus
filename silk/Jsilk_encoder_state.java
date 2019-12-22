package silk;

import celt.Jec_enc;

// structs.h

/** Encoder state */
public final class Jsilk_encoder_state extends Jenc_API {
	/** High pass filter state */
	final int              In_HP_State[] = new int[ 2 ];
	/** State of first smoother */
	public int             variable_HP_smth1_Q15;
	/** State of second smoother */
	int                    variable_HP_smth2_Q15;
	/** Low pass filter state */
	final Jsilk_LP_state   sLP = new Jsilk_LP_state();
	/** Voice activity detector state */
	final Jsilk_VAD_state  sVAD = new Jsilk_VAD_state();
	/** Noise Shape Quantizer State */
	final Jsilk_nsq_state  sNSQ = new Jsilk_nsq_state();
	/** Previously quantized NLSF vector */
	final short            prev_NLSFq_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
	/** Speech activity */
	int                    speech_activity_Q8;
	/** Flag indicating that switching of internal bandwidth is allowed */
	boolean                allow_bandwidth_switch;
	byte                   LBRRprevLastGainIndex;
	byte                   prevSignalType;
	int                    prevLag;
	int                    pitch_LPC_win_length;
	/** Highest possible pitch lag (samples) */
	int                    max_pitch_lag;
	/** API sampling frequency (Hz) */
	int                    API_fs_Hz;
	/** Previous API sampling frequency (Hz) */
	int                    prev_API_fs_Hz;
	/** Maximum internal sampling frequency (Hz) */
	int                    maxInternal_fs_Hz;
	/** Minimum internal sampling frequency (Hz) */
	int                    minInternal_fs_Hz;
	/** Soft request for internal sampling frequency (Hz) */
	int                    desiredInternal_fs_Hz;
	/** Internal sampling frequency (kHz) */
	int                    fs_kHz;
	/** Number of 5 ms subframes in a frame */
	int                    nb_subfr;
	/** Frame length (samples) */
	int                    frame_length;
	/** Subframe length (samples) */
	int                    subfr_length;
	/** Length of LTP memory */
	int                    ltp_mem_length;
	/** Look-ahead for pitch analysis (samples) */
	int                    la_pitch;
	/** Look-ahead for noise shape analysis (samples) */
	int                    la_shape;
	/** Window length for noise shape analysis (samples) */
	int                    shapeWinLength;
	/** Target bitrate (bps) */
	int                    TargetRate_bps;
	/** Number of milliseconds to put in each packet */
	int                    PacketSize_ms;
	/** Packet loss rate measured by farend */
	int                    PacketLoss_perc;
	int                    frameCounter;
	/** Complexity setting */
	int                    Complexity;
	/** Number of states in delayed decision quantization */
	int                    nStatesDelayedDecision;
	/** Flag for using NLSF interpolation */
	boolean                useInterpolatedNLSFs;
	/** Filter order for noise shaping filters */
	int                    shapingLPCOrder;
	/** Filter order for prediction filters */
	int                    predictLPCOrder;
	/** Complexity level for pitch estimator */
	int                    pitchEstimationComplexity;
	/** Whitening filter order for pitch estimator */
	int                    pitchEstimationLPCOrder;
	/** Threshold for pitch estimator */
	int                    pitchEstimationThreshold_Q16;
	/** Cumulative max prediction gain */
	int                    sum_log_gain_Q7;
	/** Number of survivors in NLSF MSVQ */
	int                    NLSF_MSVQ_Survivors;
	/** Flag for deactivating NLSF interpolation, pitch prediction */
	boolean                first_frame_after_reset;
	/** Flag for ensuring codec_control only runs once per packet */
	int                    controlled_since_last_payload;
	/** Warping parameter for warped noise shaping */
	int                    warping_Q16;
	/** Flag to enable constant bitrate */
	boolean                useCBR;
	/** Flag to indicate that only buffers are prefilled, no coding */
	boolean                prefillFlag;
	/** Pointer to iCDF table for low bits of pitch lag index */
	char[]                 pitch_lag_low_bits_iCDF;// java uint8 to char
	/** Pointer to iCDF table for pitch contour index */
	char[]                 pitch_contour_iCDF;// java uint8 to char
	/** Pointer to NLSF codebook */
	Jsilk_NLSF_CB_struct   psNLSF_CB;
	final int              input_quality_bands_Q15[] = new int[ Jdefine.VAD_N_BANDS ];
	int                    input_tilt_Q15;
	/** Quality setting */
	int                    SNR_dB_Q7;

	final byte             VAD_flags[] = new byte[ Jdefine.MAX_FRAMES_PER_PACKET ];
	boolean                LBRR_flag;
	final boolean          LBRR_flags[] = new boolean[ Jdefine.MAX_FRAMES_PER_PACKET ];

	final JSideInfoIndices indices = new JSideInfoIndices();
	final byte             pulses[] = new byte[ Jdefine.MAX_FRAME_LENGTH ];

	// int                          arch;// java don't needed

	/* Input/output buffering */
	/** Buffer containing input signal */
	final short            inputBuf[] = new short[ Jdefine.MAX_FRAME_LENGTH + 2 ];
	int                    inputBufIx;
	int                    nFramesPerPacket;
	/** Number of frames analyzed in current packet */
	int                    nFramesEncoded;

	int                    nChannelsAPI;
	int                    nChannelsInternal;
	int                    channelNb;

	/* Parameters For LTP scaling Control */
	int                    frames_since_onset;

	/* Specifically for entropy coding */
	int                    ec_prevSignalType;
	short                  ec_prevLagIndex;

	final Jsilk_resampler_state_struct resampler_state = new Jsilk_resampler_state_struct();

	/* DTX */
	/** Flag to enable DTX */
	boolean                useDTX;
	/** Flag to signal DTX period */
	boolean                inDTX;
	/** Counts concecutive nonactive frames, used by DTX */
	int                    noSpeechCounter;

	/* Inband Low Bitrate Redundancy (LBRR) data */
	/** Saves the API setting for query */
	boolean                useInBandFEC;
	/** Depends on useInBandFRC, bitrate and packet loss rate */
	boolean                LBRR_enabled;
	/** Gains increment for coding LBRR frames */
	int                    LBRR_GainIncreases;
	final JSideInfoIndices indices_LBRR[] = new JSideInfoIndices[ Jdefine.MAX_FRAMES_PER_PACKET ];
	final byte             pulses_LBRR[][] = new byte[ Jdefine.MAX_FRAMES_PER_PACKET ][ Jdefine.MAX_FRAME_LENGTH ];
	//
	Jsilk_encoder_state() {
		int i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			indices_LBRR[ --i ] = new JSideInfoIndices();
		} while( i > 0 );
	}
	final void clear() {
		In_HP_State[0] = 0; In_HP_State[1] = 0;
		variable_HP_smth1_Q15 = 0;
		variable_HP_smth2_Q15 = 0;
		sLP.clear();
		sVAD.clear();
		sNSQ.clear();
		short[] sbuff = prev_NLSFq_Q15;
		int i = Jdefine.MAX_LPC_ORDER;
		do {
			sbuff[ --i ] = 0;
		} while( i > 0 );
		speech_activity_Q8 = 0;
		allow_bandwidth_switch = false;
		LBRRprevLastGainIndex = 0;
		prevSignalType = 0;
		prevLag = 0;
		pitch_LPC_win_length = 0;
		max_pitch_lag = 0;
		API_fs_Hz = 0;
		prev_API_fs_Hz = 0;
		maxInternal_fs_Hz = 0;
		minInternal_fs_Hz = 0;
		desiredInternal_fs_Hz = 0;
		fs_kHz = 0;
		nb_subfr = 0;
		frame_length = 0;
		subfr_length = 0;
		ltp_mem_length = 0;
		la_pitch = 0;
		la_shape = 0;
		shapeWinLength = 0;
		TargetRate_bps = 0;
		PacketSize_ms = 0;
		PacketLoss_perc = 0;
		frameCounter = 0;
		Complexity = 0;
		nStatesDelayedDecision = 0;
		useInterpolatedNLSFs = false;
		shapingLPCOrder = 0;
		predictLPCOrder = 0;
		pitchEstimationComplexity = 0;
		pitchEstimationLPCOrder = 0;
		pitchEstimationThreshold_Q16 = 0;
		sum_log_gain_Q7 = 0;
		NLSF_MSVQ_Survivors = 0;
		first_frame_after_reset = false;
		controlled_since_last_payload = 0;
		warping_Q16 = 0;
		useCBR = false;
		prefillFlag = false;
		pitch_lag_low_bits_iCDF = null;
		pitch_contour_iCDF = null;
		psNLSF_CB = null;
		final int[] ibuff = input_quality_bands_Q15;
		i = Jdefine.VAD_N_BANDS;
		do {
			ibuff[ --i ] = 0;
		} while( i > 0 );
		input_tilt_Q15 = 0;
		SNR_dB_Q7 = 0;

		byte[] bbuff = VAD_flags;
		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			bbuff[ --i ] = 0;
		} while( i > 0 );

		LBRR_flag = false;

		final boolean[] boolbuff = LBRR_flags;
		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			boolbuff[ --i ] = false;
		} while( i > 0 );

		indices.clear();
		bbuff = pulses;
		i = Jdefine.MAX_FRAME_LENGTH;
		do {
			bbuff[ --i ] = 0;
		} while( i > 0 );

		// arch = 0;

		sbuff = inputBuf;
		i = Jdefine.MAX_FRAME_LENGTH + 2;
		do {
			sbuff[ --i ] = 0;
		} while( i > 0 );
		inputBufIx = 0;
		nFramesPerPacket = 0;
		nFramesEncoded = 0;

		nChannelsAPI = 0;
		nChannelsInternal = 0;
		channelNb = 0;

		frames_since_onset = 0;

		ec_prevSignalType = 0;
		ec_prevLagIndex = 0;

		resampler_state.clear();

		useDTX = false;
		inDTX = false;
		noSpeechCounter = 0;

		useInBandFEC = false;
		LBRR_enabled = false;
		LBRR_GainIncreases = 0;

		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			indices_LBRR[ --i ].clear();
		} while( i > 0 );

		final byte[][] bbuff2 = pulses_LBRR;
		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			bbuff = bbuff2[ --i ];
			int j = Jdefine.MAX_FRAME_LENGTH;
			do {
				bbuff[ --j ] = 0;
			} while( j > 0 );
		} while( i > 0 );
	}
	final void copyFrom(final Jsilk_encoder_state s) {
		In_HP_State[0] = s.In_HP_State[0]; In_HP_State[1] = s.In_HP_State[1];
		variable_HP_smth1_Q15 = s.variable_HP_smth1_Q15;
		variable_HP_smth2_Q15 = s.variable_HP_smth2_Q15;
		sLP.copyFrom( s.sLP );
		sVAD.copyFrom( s.sVAD );
		sNSQ.copyFrom( s.sNSQ );
		System.arraycopy( s.prev_NLSFq_Q15, 0, prev_NLSFq_Q15, 0, Jdefine.MAX_LPC_ORDER );
		speech_activity_Q8 = s.speech_activity_Q8;
		allow_bandwidth_switch = s.allow_bandwidth_switch;
		LBRRprevLastGainIndex = s.LBRRprevLastGainIndex;
		prevSignalType = s.prevSignalType;
		prevLag = s.prevLag;
		pitch_LPC_win_length = s.pitch_LPC_win_length;
		max_pitch_lag = s.max_pitch_lag;
		API_fs_Hz = s.API_fs_Hz;
		prev_API_fs_Hz = s.prev_API_fs_Hz;
		maxInternal_fs_Hz = s.maxInternal_fs_Hz;
		minInternal_fs_Hz = s.minInternal_fs_Hz;
		desiredInternal_fs_Hz = s.desiredInternal_fs_Hz;
		fs_kHz = s.fs_kHz;
		nb_subfr = s.nb_subfr;
		frame_length = s.frame_length;
		subfr_length = s.subfr_length;
		ltp_mem_length = s.ltp_mem_length;
		la_pitch = s.la_pitch;
		la_shape = s.la_shape;
		shapeWinLength = s.shapeWinLength;
		TargetRate_bps = s.TargetRate_bps;
		PacketSize_ms = s.PacketSize_ms;
		PacketLoss_perc = s.PacketLoss_perc;
		frameCounter = s.frameCounter;
		Complexity = s.Complexity;
		nStatesDelayedDecision = s.nStatesDelayedDecision;
		useInterpolatedNLSFs = s.useInterpolatedNLSFs;
		shapingLPCOrder = s.shapingLPCOrder;
		predictLPCOrder = s.predictLPCOrder;
		pitchEstimationComplexity = s.pitchEstimationComplexity;
		pitchEstimationLPCOrder = s.pitchEstimationLPCOrder;
		pitchEstimationThreshold_Q16 = s.pitchEstimationThreshold_Q16;
		sum_log_gain_Q7 = s.sum_log_gain_Q7;
		NLSF_MSVQ_Survivors = s.NLSF_MSVQ_Survivors;
		first_frame_after_reset = s.first_frame_after_reset;
		controlled_since_last_payload = s.controlled_since_last_payload;
		warping_Q16 = s.warping_Q16;
		useCBR = s.useCBR;
		prefillFlag = s.prefillFlag;
		pitch_lag_low_bits_iCDF = s.pitch_lag_low_bits_iCDF;
		pitch_contour_iCDF = s.pitch_contour_iCDF;
		psNLSF_CB = s.psNLSF_CB;
		System.arraycopy( s.input_quality_bands_Q15, 0, input_quality_bands_Q15, 0, Jdefine.VAD_N_BANDS );
		input_tilt_Q15 = s.input_tilt_Q15;
		SNR_dB_Q7 = s.SNR_dB_Q7;
		System.arraycopy( s.VAD_flags, 0, VAD_flags, 0, Jdefine.MAX_FRAMES_PER_PACKET );
		LBRR_flag = s.LBRR_flag;
		System.arraycopy( s.LBRR_flags, 0, LBRR_flags, 0, Jdefine.MAX_FRAMES_PER_PACKET );

		indices.copyFrom( s.indices );
		System.arraycopy( s.pulses, 0, pulses, 0, Jdefine.MAX_FRAME_LENGTH );

		// arch = s.arch;

		System.arraycopy( s.inputBuf, 0, inputBuf, 0, Jdefine.MAX_FRAME_LENGTH + 2 );
		inputBufIx = s.inputBufIx;
		nFramesPerPacket = s.nFramesPerPacket;
		nFramesEncoded = s.nFramesEncoded;

		nChannelsAPI = s.nChannelsAPI;
		nChannelsInternal = s.nChannelsInternal;
		channelNb = s.channelNb;

		frames_since_onset = s.frames_since_onset;

		ec_prevSignalType = s.ec_prevSignalType;
		ec_prevLagIndex = s.ec_prevLagIndex;

		resampler_state.copyFrom( s.resampler_state );

		useDTX = s.useDTX;
		inDTX = s.inDTX;
		noSpeechCounter = s.noSpeechCounter;

		useInBandFEC = s.useInBandFEC;
		LBRR_enabled = s.LBRR_enabled;
		LBRR_GainIncreases = s.LBRR_GainIncreases;

		int i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			i--;
			indices_LBRR[ i ].copyFrom( s.indices_LBRR[ i ] );
		} while( i > 0 );

		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			i--;
			final byte buf[] = pulses_LBRR[ i ];
			final byte sbuf[] = s.pulses_LBRR[ i ];
			System.arraycopy( sbuf, 0, buf, 0, Jdefine.MAX_FRAME_LENGTH );
		} while( i > 0 );
	}
	//

	// start NLSF_del_dec_quant.c
	/**
	 * Delayed-decision quantizer for NLSF residuals
	 *
	 * @param indices O    Quantization indices [ order ]
	 * @param ioffset I java an offset for the indices
	 * @param x_Q10 I    Input [ order ]
	 * @param w_Q5 I    Weights [ order ]
	 * @param pred_coef_Q8 I    Backward predictor coefs [ order ]
	 * @param ec_ix I    Indices to entropy coding tables [ order ]
	 * @param ec_rates_Q5 I    Rates []
	 * @param quant_step_size_Q16 I    Quantization step size
	 * @param inv_quant_step_size_Q6 I    Inverse quantization step size
	 * @param mu_Q20 I    R/D tradeoff
	 * @param order I    Number of input values
	 * @return O    Returns RD value in Q25
	 */
	private static final int silk_NLSF_del_dec_quant(final byte indices[], final int ioffset,// java
			final short x_Q10[], final short w_Q5[],
			final char pred_coef_Q8[], final short ec_ix[], final char ec_rates_Q5[],
			final int quant_step_size_Q16, final short inv_quant_step_size_Q6, final int mu_Q20, final short order)
	{
		final int ind_sort[] = new int[ Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
		final byte ind[][]   = new byte[ Jdefine.NLSF_QUANT_DEL_DEC_STATES ][ Jdefine.MAX_LPC_ORDER ];
		final short prev_out_Q10[] = new short[ 2 * Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
		final int RD_Q25[]     = new int[       2 * Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
		final int RD_min_Q25[] = new int[       Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
		final int RD_max_Q25[] = new int[       Jdefine.NLSF_QUANT_DEL_DEC_STATES ];

		final int out0_Q10_table[] = new int[2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT];
		final int out1_Q10_table[] = new int[2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT];

		for( int i = -Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT; i <= Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT - 1; i++)
		{
			int out0_Q10 = i << 10;
			int out1_Q10 = out0_Q10 + 1024;
			if( i > 0 ) {
				// out0_Q10 -= SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out0_Q10 -= (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
				// out1_Q10 -= SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out1_Q10 -= (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
			} else if( i == 0 ) {
				// out1_Q10 -= SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out1_Q10 -= (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
			} else if( i == -1 ) {
				// out0_Q10 += SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out0_Q10 += (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
			} else {
				// out0_Q10 += SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out0_Q10 += (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
				// out1_Q10 += SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out1_Q10 += (int)( Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5 );
			}
			out0_Q10_table[ i + Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT ] = ((out0_Q10 * quant_step_size_Q16) >> 16);
			out1_Q10_table[ i + Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT ] = ((out1_Q10 * quant_step_size_Q16) >> 16);
		}

		// silk_assert( (NLSF_QUANT_DEL_DEC_STATES & (NLSF_QUANT_DEL_DEC_STATES-1)) == 0 );     /* must be power of two */

		int nStates = 1;
		RD_Q25[ 0 ] = 0;
		prev_out_Q10[ 0 ] = 0;
		for( int i = order - 1; i >= 0; i-- ) {
			final int rates_Q5 = ec_ix[ i ];// ec_rates_Q5[ rates_Q5 ]
			final int in_Q10 = (int)x_Q10[ i ];
			for( int j = 0; j < nStates; j++ ) {
				final int pred_Q10 = (int)(((int)pred_coef_Q8[ i ] * (int)prev_out_Q10[ j ]) >> 8);
				final int res_Q10  = in_Q10 - pred_Q10;
				int ind_tmp  = (((int)inv_quant_step_size_Q6 * res_Q10) >> 16);
				ind_tmp  = (ind_tmp > (Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT - 1) ? (Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT - 1) :
							(ind_tmp < -Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT ? -Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT : ind_tmp));
				ind[ j ][ i ] = (byte)ind_tmp;

				/* compute outputs for ind_tmp and ind_tmp + 1 */
				int out0_Q10 = out0_Q10_table[ ind_tmp + Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT ];
				int out1_Q10 = out1_Q10_table[ ind_tmp + Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT ];

				out0_Q10  += pred_Q10;
				out1_Q10  += pred_Q10;
				prev_out_Q10[ j           ] = (short)out0_Q10;
				prev_out_Q10[ j + nStates ] = (short)out1_Q10;

				/* compute RD for ind_tmp and ind_tmp + 1 */
				int rate0_Q5, rate1_Q5;
				if( ind_tmp + 1 >= Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
					if( ind_tmp + 1 == Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
						rate0_Q5 = (int)ec_rates_Q5[ rates_Q5 + ind_tmp + Jdefine.NLSF_QUANT_MAX_AMPLITUDE ];
						rate1_Q5 = 280;
					} else {
						// rate0_Q5 = silk_SMLABB( 280 - 43 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE, 43, ind_tmp );
						rate0_Q5 = ( 280 + 43 * (ind_tmp - Jdefine.NLSF_QUANT_MAX_AMPLITUDE) );
						rate1_Q5 = rate0_Q5 + 43;
					}
				} else if( ind_tmp <= -Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
					if( ind_tmp == -Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
						rate0_Q5 = 280;
						rate1_Q5 = (int)ec_rates_Q5[ rates_Q5 + ind_tmp + 1 + Jdefine.NLSF_QUANT_MAX_AMPLITUDE ];
					} else {
						// rate0_Q5 = silk_SMLABB( 280 - 43 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE, -43, ind_tmp );
						rate0_Q5 = ( 280 - 43 * (ind_tmp + Jdefine.NLSF_QUANT_MAX_AMPLITUDE) );
						rate1_Q5 = rate0_Q5 - 43;
					}
				} else {
					rate0_Q5 = (int)ec_rates_Q5[ rates_Q5 + ind_tmp +     Jdefine.NLSF_QUANT_MAX_AMPLITUDE ];
					rate1_Q5 = (int)ec_rates_Q5[ rates_Q5 + ind_tmp + 1 + Jdefine.NLSF_QUANT_MAX_AMPLITUDE ];
				}
				final int RD_tmp_Q25  = RD_Q25[ j ];
				int diff_Q10          = in_Q10 - out0_Q10;
				RD_Q25[ j ]           = ( ( RD_tmp_Q25 + ( diff_Q10 * diff_Q10 ) * (int)w_Q5[ i ] ) + mu_Q20 * rate0_Q5 );
				diff_Q10              = in_Q10 - out1_Q10;
				RD_Q25[ j + nStates ] = ( ( RD_tmp_Q25 + ( diff_Q10 * diff_Q10 ) * (int)w_Q5[ i ] ) + mu_Q20 * rate1_Q5 );
			}

			if( nStates <= Jdefine.NLSF_QUANT_DEL_DEC_STATES / 2 ) {
				/* double number of states and copy */
				for( int j = 0; j < nStates; j++ ) {
					ind[ j + nStates ][ i ] = (byte)((int)ind[ j ][ i ] + 1);
				}
				nStates <<= 1;
				for( int j = nStates; j < Jdefine.NLSF_QUANT_DEL_DEC_STATES; j++ ) {
					ind[ j ][ i ] = ind[ j - nStates ][ i ];
				}
			} else {
				/* sort lower and upper half of RD_Q25, pairwise */
				for( int j = 0; j < Jdefine.NLSF_QUANT_DEL_DEC_STATES; j++ ) {
					if( RD_Q25[ j ] > RD_Q25[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ] ) {
						RD_max_Q25[ j ] = RD_Q25[ j ];
						RD_min_Q25[ j ] = RD_Q25[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
						RD_Q25[ j ]     = RD_min_Q25[ j ];
						RD_Q25[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ] = RD_max_Q25[ j ];
						/* swap prev_out values */
						final int out0_Q10 = (int)prev_out_Q10[ j ];
						prev_out_Q10[ j ] = prev_out_Q10[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
						prev_out_Q10[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ] = (short)out0_Q10;
						ind_sort[ j ] = j + Jdefine.NLSF_QUANT_DEL_DEC_STATES;
					} else {
						RD_min_Q25[ j ] = RD_Q25[ j ];
						RD_max_Q25[ j ] = RD_Q25[ j + Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
						ind_sort[ j ] = j;
					}
				}
				/* compare the highest RD values of the winning half with the lowest one in the losing half, and copy if necessary */
				/* afterwards ind_sort[] will contain the indices of the NLSF_QUANT_DEL_DEC_STATES winning RD values */
				while( true ) {
					int min_max_Q25 = Integer.MAX_VALUE;
					int max_min_Q25 = 0;
					int ind_min_max = 0;
					int ind_max_min = 0;
					for( int j = 0; j < Jdefine.NLSF_QUANT_DEL_DEC_STATES; j++ ) {
						if( min_max_Q25 > RD_max_Q25[ j ] ) {
							min_max_Q25 = RD_max_Q25[ j ];
							ind_min_max = j;
						}
						if( max_min_Q25 < RD_min_Q25[ j ] ) {
							max_min_Q25 = RD_min_Q25[ j ];
							ind_max_min = j;
						}
					}
					if( min_max_Q25 >= max_min_Q25 ) {
						break;
					}
					/* copy ind_min_max to ind_max_min */
					ind_sort[     ind_max_min ] = ind_sort[     ind_min_max ] ^ Jdefine.NLSF_QUANT_DEL_DEC_STATES;
					RD_Q25[       ind_max_min ] = RD_Q25[       ind_min_max + Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
					prev_out_Q10[ ind_max_min ] = prev_out_Q10[ ind_min_max + Jdefine.NLSF_QUANT_DEL_DEC_STATES ];
					RD_min_Q25[   ind_max_min ] = 0;
					RD_max_Q25[   ind_min_max ] = Integer.MAX_VALUE;
					System.arraycopy( ind[ ind_min_max ], 0, ind[ ind_max_min ], 0, Jdefine.MAX_LPC_ORDER );
				}
				/* increment index if it comes from the upper half */
				for( int j = 0; j < Jdefine.NLSF_QUANT_DEL_DEC_STATES; j++ ) {
					ind[ j ][ i ] += ind_sort[ j ] >> Jdefine.NLSF_QUANT_DEL_DEC_STATES_LOG2;
				}
			}
		}

		/* last sample: find winner, copy indices and return RD value */
		int ind_tmp = 0;
		int min_Q25 = Integer.MAX_VALUE;
		for( int j = 0; j < 2 * Jdefine.NLSF_QUANT_DEL_DEC_STATES; j++ ) {
			if( min_Q25 > RD_Q25[ j ] ) {
				min_Q25 = RD_Q25[ j ];
				ind_tmp = j;
			}
		}
		for( int j = 0, jo = ioffset; j < order; j++, jo++ ) {
			indices[ jo ] = ind[ ind_tmp & ( Jdefine.NLSF_QUANT_DEL_DEC_STATES - 1 ) ][ j ];
			// silk_assert( indices[ j ] >= -Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT );
			// silk_assert( indices[ j ] <=  Jdefine.NLSF_QUANT_MAX_AMPLITUDE_EXT );
		}
		indices[ ioffset ] += ind_tmp >> Jdefine.NLSF_QUANT_DEL_DEC_STATES_LOG2;
		// silk_assert( indices[ 0 ] <= NLSF_QUANT_MAX_AMPLITUDE_EXT );
		// silk_assert( min_Q25 >= 0 );
		return min_Q25;
	}
	// end NLSF_del_dec_quant.c

	// start NLSF_VQ.c
	/**
	 * Compute quantization errors for an LPC_order element input vector for a VQ codebook
	 *
	 * @param err_Q24 O    Quantization errors [K]
	 * @param in_Q15 I    Input vectors to be quantized [LPC_order]
	 * @param pCB_Q8 I    Codebook vectors [K*LPC_order]
	 * @param pWght_Q9 I    Codebook weights [K*LPC_order]
	 * @param K I    Number of codebook vectors
	 * @param LPC_order I    Number of LPCs
	 */
	private static final void silk_NLSF_VQ(final int err_Q24[], final short in_Q15[], final char pCB_Q8[],
		    final short pWght_Q9[], final int K, final int LPC_order)
	{
		// celt_assert( ( LPC_order & 1 ) == 0 );

		/* Loop over codebook */
		// int cb_Q8_ptr = 0;// java pCB_Q8[cb_Q8_ptr]; pCB_Q8;
		// int w_Q9_ptr = 0;// java pWght_Q9[w_Q9_ptr]; pWght_Q9;
		int ptr = 0;// java: instead cb_Q8_ptr and w_Q9_ptr
		for( int i = 0; i < K; i++ ) {
			int sum_error_Q24 = 0;
			int pred_Q24 = 0;
			for( int m = LPC_order - 2, ptr_m = ptr + m; m >= 0; m -= 2, ptr_m -= 2 ) {
				/* Compute weighted absolute predictive quantization error for index m + 1 */
				int diff_Q15 = ( (int)in_Q15[ m + 1 ] - ((int)pCB_Q8[ ptr_m + 1 ] << 7) ); /* range: [ -32767 : 32767 ]*/
				int diffw_Q24 = ( diff_Q15 * pWght_Q9[ ptr_m + 1 ] );
				pred_Q24 = diffw_Q24 - (pred_Q24 >> 1);// java
				if( pred_Q24 < 0 ) {
					pred_Q24 = -pred_Q24;// java abs
				}
				sum_error_Q24 += pred_Q24;
				pred_Q24 = diffw_Q24;

				/* Compute weighted absolute predictive quantization error for index m */
				diff_Q15 = in_Q15[ m ] - ((int)pCB_Q8[ ptr_m ] << 7); /* range: [ -32767 : 32767 ]*/
				diffw_Q24 = diff_Q15 * (int)pWght_Q9[ ptr_m ];
				pred_Q24 = diffw_Q24 - (pred_Q24 >> 1);// java
				if( pred_Q24 < 0 ) {
					pred_Q24 = -pred_Q24;// java abs
				}
				sum_error_Q24 += pred_Q24;
				pred_Q24 = diffw_Q24;

				// silk_assert( sum_error_Q24 >= 0 );
			}
			err_Q24[ i ] = sum_error_Q24;
			ptr += LPC_order;
		}
	}
	// end NLSF_VQ.c

	// start sort.c
	/**
	 * Insertion sort (fast for already almost sorted arrays):
	 * Best case:  O(n)   for an already sorted array
	 * Worst case: O(n^2) for an inversely sorted array
	 *
	 * Shell short:    https://en.wikipedia.org/wiki/Shell_sort
	 *
	 * @param a I/O   Unsorted / Sorted vector
	 * @param idx O     Index vector for the sorted elements
	 * @param L I     Vector length
	 * @param K I     Number of correctly sorted positions
	 */
	private static final void silk_insertion_sort_increasing(final int[] a, final int[] idx, final int L, final int K)
	{
		/* Safety checks */
		// celt_assert( K >  0 );
		// celt_assert( L >  0 );
		// celt_assert( L >= K );

		/* Write start indices in index vector */
		for( int i = 0; i < K; i++ ) {
			idx[ i ] = i;
		}

		/* Sort vector elements by value, increasing order */
		for( int i = 1; i < K; i++ ) {
			final int value = a[ i ];
			int j;
			for( j = i - 1; ( j >= 0 ) && ( value < a[ j ] ); j-- ) {
				a[ j + 1 ]   = a[ j ];       /* Shift value */
				idx[ j + 1 ] = idx[ j ];     /* Shift index */
			}
			a[ j + 1 ]   = value;   /* Write value */
			idx[ j + 1 ] = i;       /* Write index */
		}

		/* If less than L values are asked for, check the remaining values, */
		/* but only spend CPU to ensure that the K first values are correct */
		for( int i = K; i < L; i++ ) {
			final int value = a[ i ];
			if( value < a[ K - 1 ] ) {
				int j;
				for( j = K - 2; ( j >= 0 ) && ( value < a[ j ] ); j-- ) {
					a[ j + 1 ]   = a[ j ];       /* Shift value */
					idx[ j + 1 ] = idx[ j ];     /* Shift index */
				}
				a[ j + 1 ]   = value;   /* Write value */
				idx[ j + 1 ] = i;       /* Write index */
			}
		}
	}

/* #ifdef FIXED_POINT
	// This function is only used by the fixed-point build
	void silk_insertion_sort_decreasing_int16(
			opus_int16                  *a,   // I/O   Unsorted / Sorted vector
			opus_int                    *idx, // O     Index vector for the sorted elements
			const opus_int              L,    // I     Vector length
			const opus_int              K     // I     Number of correctly sorted positions
	)
	{
		opus_int i, j;
		opus_int value;

		// Safety checks
		silk_assert( K >  0 );
		silk_assert( L >  0 );
		silk_assert( L >= K );

		// Write start indices in index vector
		for( i = 0; i < K; i++ ) {
			idx[ i ] = i;
		}

		// Sort vector elements by value, decreasing order
		for( i = 1; i < K; i++ ) {
			value = a[ i ];
			for( j = i - 1; ( j >= 0 ) && ( value > a[ j ] ); j-- ) {
				a[ j + 1 ]   = a[ j ];     // Shift value
				idx[ j + 1 ] = idx[ j ];   // Shift index
			}
			a[ j + 1 ]   = value;   // Write value
			idx[ j + 1 ] = i;       // Write index
		}

		// If less than L values are asked for, check the remaining values,
		// but only spend CPU to ensure that the K first values are correct
		for( i = K; i < L; i++ ) {
			value = a[ i ];
			if( value > a[ K - 1 ] ) {
				for( j = K - 2; ( j >= 0 ) && ( value > a[ j ] ); j-- ) {
					a[ j + 1 ]   = a[ j ];     // Shift value
					idx[ j + 1 ] = idx[ j ];   // Shift index
				}
				a[ j + 1 ]   = value;   // Write value
				idx[ j + 1 ] = i;       // Write index
			}
		}
	}
#endif */
	// end sort.s

	// start NLSF_encode.c
	/**
	 * NLSF vector encoder
	 *
	 * @param NLSFIndices I    Codebook path vector [ LPC_ORDER + 1 ]// FIXME this is output
	 * @param pNLSF_Q15 I/O  (Un)quantized NLSF vector [ LPC_ORDER ]
	 * @param psNLSF_CB I    Codebook object
	 * @param pW_Q2 I    NLSF weight vector [ LPC_ORDER ]
	 * @param NLSF_mu_Q20 I    Rate weight for the RD optimization
	 * @param nSurvivors I    Max survivors after first stage
	 * @param signalType I    Signal type: 0/1/2
	 * @return O    Returns RD value in Q25
	 */
	private static final int silk_NLSF_encode(final byte[] NLSFIndices, final short[] pNLSF_Q15,
			final Jsilk_NLSF_CB_struct psNLSF_CB, final short[] pW_Q2,
			final int NLSF_mu_Q20, final int nSurvivors, final int signalType)
	{
		final short res_Q10[] = new short[      Jdefine.MAX_LPC_ORDER ];
		final short NLSF_tmp_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final short W_adj_Q5[] = new short[     Jdefine.MAX_LPC_ORDER ];
		final char  pred_Q8[]  = new char[      Jdefine.MAX_LPC_ORDER ];
		final short ec_ix[]    = new short[     Jdefine.MAX_LPC_ORDER ];
		// SAVE_STACK;

		// celt_assert( signalType >= 0 && signalType <= 2 );
		// silk_assert( NLSF_mu_Q20 <= 32767 && NLSF_mu_Q20 >= 0 );

		/* NLSF stabilization */
		silk_NLSF_stabilize( pNLSF_Q15, psNLSF_CB.deltaMin_Q15, psNLSF_CB.order );

		/* First stage: VQ */
		final int[] err_Q24 = new int[psNLSF_CB.nVectors];
		silk_NLSF_VQ( err_Q24, pNLSF_Q15, psNLSF_CB.CB1_NLSF_Q8, psNLSF_CB.CB1_Wght_Q9, psNLSF_CB.nVectors, psNLSF_CB.order );

		/* Sort the quantization errors */
		final int[] tempIndices1 = new int[nSurvivors];
		silk_insertion_sort_increasing( err_Q24, tempIndices1, psNLSF_CB.nVectors, nSurvivors );

		final int[] RD_Q25 = new int[nSurvivors];
		final byte[] tempIndices2 = new byte[nSurvivors * Jdefine.MAX_LPC_ORDER];

		final char[] iCDF = psNLSF_CB.CB1_iCDF;// java
		final char[] CB1_NLSF_Q8 = psNLSF_CB.CB1_NLSF_Q8;// java
		final short[] CB1_Wght_Q9 = psNLSF_CB.CB1_Wght_Q9;// java
		/* Loop over survivors */
		for( int s = 0; s < nSurvivors; s++ ) {
			final int ind1 = tempIndices1[ s ];

			/* Residual after first stage */
			// pCB_element = &psNLSF_CB.CB1_NLSF_Q8[ ind1 * psNLSF_CB.order ];
			// short[] pCB_Wght_Q9 = &psNLSF_CB.CB1_Wght_Q9[ ind1 * psNLSF_CB.order ];
			for( int i = 0, ie = psNLSF_CB.order, pCB_element = ind1 * ie; i < ie; i++ ) {
				NLSF_tmp_Q15[ i ] = (short)((short)CB1_NLSF_Q8[ pCB_element ] << 7);
				final int W_tmp_Q9 = (int)CB1_Wght_Q9[ pCB_element++ ];
				res_Q10[ i ] = (short)( ( (pNLSF_Q15[ i ] - NLSF_tmp_Q15[ i ]) * W_tmp_Q9 ) >> 14 );
				W_adj_Q5[ i ] = (short)silk_DIV32_varQ( (int)pW_Q2[ i ], ( W_tmp_Q9 * W_tmp_Q9 ), 21 );
			}

			/* Unpack entropy table indices and predictor for current CB1 index */
			silk_NLSF_unpack( ec_ix, pred_Q8, psNLSF_CB, ind1 );

			/* Trellis quantizer */
			RD_Q25[ s ] = silk_NLSF_del_dec_quant( tempIndices2, s * Jdefine.MAX_LPC_ORDER,
					res_Q10, W_adj_Q5, pred_Q8, ec_ix, psNLSF_CB.ec_Rates_Q5, psNLSF_CB.quantStepSize_Q16,
					psNLSF_CB.invQuantStepSize_Q6, NLSF_mu_Q20, psNLSF_CB.order );

			/* Add rate for first stage */
			final int iCDF_ptr = (signalType >> 1) * psNLSF_CB.nVectors + ind1;// java psNLSF_CB.CB1_iCDF[ iCDF_ptr ]
			final int prob_Q8 = ( ind1 == 0 ) ? 256 - iCDF[ iCDF_ptr ] : iCDF[ iCDF_ptr - 1 ] - iCDF[ iCDF_ptr ];

			final int bits_q7 = ( 8 << 7 ) - Jmacros.silk_lin2log( prob_Q8 );
			RD_Q25[ s ] += bits_q7 * (NLSF_mu_Q20 >> 2);
		}

		/* Find the lowest rate-distortion error */
		final int bestIndex[] = new int[1];
		silk_insertion_sort_increasing( RD_Q25, bestIndex, nSurvivors, 1 );

		NLSFIndices[ 0 ] = (byte)tempIndices1[ bestIndex[0] ];
		System.arraycopy( tempIndices2, bestIndex[0] * Jdefine.MAX_LPC_ORDER, NLSFIndices, 1, psNLSF_CB.order );

		/* Decode */
		silk_NLSF_decode( pNLSF_Q15, NLSFIndices, psNLSF_CB );

		// RESTORE_STACK;
		return RD_Q25[ 0 ];
	}
	// end NLSF_encode.c

	// start interpolate.c
	/**
	 * Interpolate two vectors
	 *
	 * @param xi O    interpolated vector
	 * @param x0 I    first vector
	 * @param x1 I    second vector
	 * @param ifact_Q2 I    interp. factor, weight on 2nd vector
	 * @param d I    number of parameters
	 */
	private static final void silk_interpolate(final short xi[/* MAX_LPC_ORDER */],
			final short x0[/* MAX_LPC_ORDER */], final short x1[/* MAX_LPC_ORDER */],
			final int ifact_Q2, final int d)
	{
		// celt_assert( ifact_Q2 >= 0 );
		// celt_assert( ifact_Q2 <= 4 );

		for( int i = 0; i < d; i++ ) {
			final int v = (int)x0[ i ];// java
			xi[ i ] = (short)(v + ((((int)x1[ i ] - v) * ifact_Q2) >> 2));
		}
	}
	// end interpolate.c

	// start process_NLSFs.c
	/**
	 * Limit, stabilize, convert and quantize NLSFs
	 *
	 * @param psEncC I/O  Encoder state
	 * @param PredCoef_Q12 O    Prediction coefficients
	 * @param pNLSF_Q15 I/O  Normalized LSFs (quant out) (0 - (2^15-1))
	 * @param prevNLSFq_Q15 I    Previous Normalized LSFs (0 - (2^15-1))
	 */
	final void silk_process_NLSFs(// final Jsilk_encoder_state psEncC,
			final short PredCoef_Q12[/* 2 */][/* MAX_LPC_ORDER */], final short pNLSF_Q15[/* MAX_LPC_ORDER */],
			final short prevNLSFq_Q15[/* MAX_LPC_ORDER */]
		)
	{
		final short pNLSF0_temp_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final short pNLSFW_QW[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final short pNLSFW0_temp_QW[] = new short[ Jdefine.MAX_LPC_ORDER ];

		// silk_assert( psEncC.speech_activity_Q8 >=   0 );
		// silk_assert( psEncC.speech_activity_Q8 <= SILK_FIX_CONST( 1.0, 8 ) );
		// celt_assert( psEncC.useInterpolatedNLSFs == 1 || psEncC.indices.NLSFInterpCoef_Q2 == ( 1 << 2 ) );

		/***********************/
		/* Calculate mu values */
		/***********************/
		/* NLSF_mu  = 0.003 - 0.0015 * psEnc.speech_activity; */
		// int NLSF_mu_Q20 = Jmacros.silk_SMLAWB( SILK_FIX_CONST( 0.003, 20 ), SILK_FIX_CONST( -0.001, 28 ), this.speech_activity_Q8 );
		int NLSF_mu_Q20 = ((int)(0.003 * (1 << 20) + 0.5)) + (int)((((long)(-0.001 * (1 << 28) + 0.5)) * (long)this.speech_activity_Q8) >> 16);
		if( this.nb_subfr == 2 ) {
			/* Multiply by 1.5 for 10 ms packets */
			NLSF_mu_Q20 += (NLSF_mu_Q20 >> 1);
		}

		// celt_assert( NLSF_mu_Q20 >  0 );
		// silk_assert( NLSF_mu_Q20 <= SILK_FIX_CONST( 0.005, 20 ) );

		/* Calculate NLSF weights */
		silk_NLSF_VQ_weights_laroia( pNLSFW_QW, pNLSF_Q15, this.predictLPCOrder );

		/* Update NLSF weights for interpolated NLSFs */
		final boolean doInterpolate = this.useInterpolatedNLSFs && ( this.indices.NLSFInterpCoef_Q2 < 4 );
		if( doInterpolate ) {
			/* Calculate the interpolated NLSF vector for the first half */
			silk_interpolate( pNLSF0_temp_Q15, prevNLSFq_Q15, pNLSF_Q15, this.indices.NLSFInterpCoef_Q2, this.predictLPCOrder );

			/* Calculate first half NLSF weights for the interpolated NLSFs */
			silk_NLSF_VQ_weights_laroia( pNLSFW0_temp_QW, pNLSF0_temp_Q15, this.predictLPCOrder );

			/* Update NLSF weights with contribution from first half */
			final int i_sqr_Q15 = ( (int)this.indices.NLSFInterpCoef_Q2 * (int)this.indices.NLSFInterpCoef_Q2 ) << 11;
			for( int i = 0, ie = this.predictLPCOrder; i < ie; i++ ) {
	            pNLSFW_QW[ i ] = (short)(( pNLSFW_QW[ i ] >> 1 ) + (((int)pNLSFW0_temp_QW[ i ] * i_sqr_Q15) >> 16));

				// silk_assert( pNLSFW_QW[ i ] >= 1 );
			}
		}

		silk_NLSF_encode( this.indices.NLSFIndices, pNLSF_Q15, this.psNLSF_CB, pNLSFW_QW, NLSF_mu_Q20, this.NLSF_MSVQ_Survivors, this.indices.signalType );

		/* Convert quantized NLSFs back to LPC coefficients */
		silk_NLSF2A( PredCoef_Q12[ 1 ], pNLSF_Q15, this.predictLPCOrder );//, this.arch );

		if( doInterpolate ) {
			/* Calculate the interpolated, quantized LSF vector for the first half */
			silk_interpolate( pNLSF0_temp_Q15, prevNLSFq_Q15, pNLSF_Q15, this.indices.NLSFInterpCoef_Q2, this.predictLPCOrder );

			/* Convert back to LPC coefficients */
			silk_NLSF2A( PredCoef_Q12[ 0 ], pNLSF0_temp_Q15, this.predictLPCOrder );//, this.arch );
			return;
		}// else {
			/* Copy LPC coefficients for first half from second half */
			// celt_assert( psEncC.predictLPCOrder <= MAX_LPC_ORDER );
			System.arraycopy( PredCoef_Q12[ 1 ], 0, PredCoef_Q12[ 0 ], 0, this.predictLPCOrder );
		//}
	}
	// end process_NLSFs.c

	// start A2NLSF.c

	/* Conversion between prediction filter coefficients and NLSFs  */
	/* Requires the order to be an even number                      */
	/* A piecewise linear approximation maps LSF <-> cos(LSF)       */
	/* Therefore the result is not accurate NLSFs, but the two      */
	/* functions are accurate inverses of each other                */
	/* Number of binary divisions, when not in low complexity mode */
	private static final int BIN_DIV_STEPS_A2NLSF_FIX  = 3; /* must be no higher than 16 - log2( LSF_COS_TAB_SZ_FIX ) */
	private static final int MAX_ITERATIONS_A2NLSF_FIX = 16;

	/* Helper function for A2NLSF(..)                    */
	/** Transforms polynomials from cos(n*f) to cos(f)^n
	 * @param p I/O    Polynomial
	 * @param dd I      Polynomial order (= filter order / 2 )
	 */
	private static final void silk_A2NLSF_trans_poly(final int[] p, final int dd)
	{
		for( int k = 2; k <= dd; k++ ) {
			for( int n = dd; n > k; n-- ) {
				p[ n - 2 ] -= p[ n ];
			}
			p[ k - 2 ] -= p[ k ] << 1;
		}
	}
	/* Helper function for A2NLSF(..) */
	/**
	 * Polynomial evaluation
	 *
	 * @param p I    Polynomial, Q16
	 * @param x I    Evaluation point, Q12
	 * @param dd I    Order
	 * @return return the polynomial evaluation, in Q16
	 */
	private static final int silk_A2NLSF_eval_poly(final int[] p, final int x, final int dd)
	{
		long y32 = p[ dd ];                                  /* Q16 */
		final long x_Q16 = x << 4;

		if( 8 == dd )
		{
			y32 = (p[ 7 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 6 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 5 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 4 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 3 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 2 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 1 ] + ((y32 * x_Q16) >> 16));
			y32 = (p[ 0 ] + ((y32 * x_Q16) >> 16));
		}
		else
		{
			for( int n = dd - 1; n >= 0; n-- ) {
				y32 = (p[ n ] + ((y32 * x_Q16) >> 16));    /* Q16 */
			}
		}
		return (int)y32;
	}

	private static final void silk_A2NLSF_init(final int[] a_Q16, final int[] P, final int[] Q, final int dd)
	{
		final int dd1 = dd - 1;// java
		/* Convert filter coefs to even and odd polynomials */
		P[dd] = 1 << 16;
		Q[dd] = 1 << 16;
		for( int k = 0; k < dd; k++ ) {
			final int a_ddk = a_Q16[ dd + k ];// java
			final int a_ddk1 = -a_Q16[ dd1 - k ];// java
			P[ k ] = a_ddk1 - a_ddk;    /* Q16 */
			Q[ k ] = a_ddk1 + a_ddk;    /* Q16 */
		}

		/* Divide out zeros as we have that for even filter orders, */
		/* z =  1 is always a root in Q, and                        */
		/* z = -1 is always a root in P                             */
		for( int k = dd; k > 0; k-- ) {
			final int k1 = k - 1;// java
			P[ k1 ] -= P[ k ];
			Q[ k1 ] += Q[ k ];
		}

		/* Transform polynomials from cos(n*f) to cos(f)^n */
		silk_A2NLSF_trans_poly( P, dd );
		silk_A2NLSF_trans_poly( Q, dd );
	}

	/* Compute Normalized Line Spectral Frequencies (NLSFs) from whitening filter coefficients      */
	/**
	 * If not all roots are found, the a_Q16 coefficients are bandwidth expanded until convergence.
	 * @param NLSF O    Normalized Line Spectral Frequencies in Q15 (0..2^15-1) [d]
	 * @param a_Q16 I/O  Monic whitening filter coefficients in Q16 [d]
	 * @param d I    Filter order (must be even)
	 */
	private static final void silk_A2NLSF(final short[] NLSF, final int[] a_Q16, final int d)
	{
		final int P[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC / 2 + 1 ];
		final int Q[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC / 2 + 1 ];
		final int PQ[][] = new int[ 2 ][];

		/* Store pointers to array */
		PQ[ 0 ] = P;
		PQ[ 1 ] = Q;

		final int dd = d >> 1;

		silk_A2NLSF_init( a_Q16, P, Q, dd );

		/* Find roots, alternating between P and Q */
		int[] p = P;                          /* Pointer to polynomial */

		int xlo = (int)Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ 0 ]; /* Q12*/
		int ylo = silk_A2NLSF_eval_poly( p, xlo, dd );

		int root_ix = 0;
		if( ylo < 0 ) {
			/* Set the first NLSF to zero and move on to the next */
			NLSF[ 0 ] = 0;
			p = Q;                      /* Pointer to polynomial */
			ylo = silk_A2NLSF_eval_poly( p, xlo, dd );
			root_ix = 1;                /* Index of current root */
		}// else {
		//	root_ix = 0;                /* Index of current root */
		//}
		int k = 1;                          /* Loop counter */
		int i = 0;                          /* Counter for bandwidth expansions applied */
		int thr = 0;
		while( true ) {
			/* Evaluate polynomial */
			int xhi = (int)Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ k ]; /* Q12 */
			int yhi = silk_A2NLSF_eval_poly( p, xhi, dd );

			/* Detect zero crossing */
			if( ( ylo <= 0 && yhi >= thr ) || ( ylo >= 0 && yhi <= -thr ) ) {
				if( yhi == 0 ) {
					/* If the root lies exactly at the end of the current       */
					/* interval, look for the next root in the next interval    */
					thr = 1;
				} else {
					thr = 0;
				}
				/* Binary division */
				int ffrac = -256;
				for( int m = 0; m < BIN_DIV_STEPS_A2NLSF_FIX; m++ ) {
					/* Evaluate polynomial */
					final int xmid = JSigProc_FIX.silk_RSHIFT_ROUND( xlo + xhi, 1 );
					final int ymid = silk_A2NLSF_eval_poly( p, xmid, dd );

					/* Detect zero crossing */
					if( ( ylo <= 0 && ymid >= 0 ) || ( ylo >= 0 && ymid <= 0 ) ) {
						/* Reduce frequency */
						xhi = xmid;
						yhi = ymid;
					} else {
						/* Increase frequency */
						xlo = xmid;
						ylo = ymid;
						// ffrac = JSigProc_FIX.silk_ADD_RSHIFT( ffrac, 128, m );
						ffrac = (ffrac + (128 >> m));

					}
				}

				/* Interpolate */
				if( (ylo >= 0 ? ylo : -ylo) < 65536 ) {
					/* Avoid dividing by zero */
					final int den = ylo - yhi;
					final int nom = (ylo << (8 - BIN_DIV_STEPS_A2NLSF_FIX)) + (den >> 1);
					if( den != 0 ) {
						ffrac += nom / den;
					}
				} else {
					/* No risk of dividing by zero because abs(ylo - yhi) >= abs(ylo) >= 65536 */
					ffrac += ylo / ((ylo - yhi) >> (8 - BIN_DIV_STEPS_A2NLSF_FIX));
				}
				final int v = (k << 8) + ffrac;// java
				NLSF[ root_ix ] = v < Short.MAX_VALUE ? (short)v : Short.MAX_VALUE;

				// silk_assert( NLSF[ root_ix ] >= 0 );

				root_ix++;        /* Next root */
				if( root_ix >= d ) {
					/* Found all roots */
					break;
				}
				/* Alternate pointer to polynomial */
				p = PQ[ root_ix & 1 ];

				/* Evaluate polynomial */
				xlo = (int)Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ k - 1 ]; /* Q12*/
				ylo = (1 - ( root_ix & 2 ) ) << 12;
			} else {
				/* Increment loop counter */
				k++;
				xlo = xhi;
				ylo = yhi;
				thr = 0;

				if( k > Jdefine.LSF_COS_TAB_SZ_FIX ) {
					i++;
					if( i > MAX_ITERATIONS_A2NLSF_FIX ) {
						/* Set NLSFs to white spectrum and exit */
						NLSF[ 0 ] = (short)((1 << 15) / (d + 1));
						for( k = 1; k < d; k++ ) {
							NLSF[ k ] = (short)((k - 1) + NLSF[ 0 ]);
						}
						return;
					}

					/* Error: Apply progressively more bandwidth expansion and run again */
					silk_bwexpander_32( a_Q16, d, 65536 - (1 << i) );

					silk_A2NLSF_init( a_Q16, P, Q, dd );
					p = P;                            /* Pointer to polynomial */
					xlo = (int)Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ 0 ]; /* Q12*/
					ylo = silk_A2NLSF_eval_poly( p, xlo, dd );
					if( ylo < 0 ) {
						/* Set the first NLSF to zero and move on to the next */
						NLSF[ 0 ] = 0;
						p = Q;                        /* Pointer to polynomial */
						ylo = silk_A2NLSF_eval_poly( p, xlo, dd );
						root_ix = 1;                  /* Index of current root */
					} else {
						root_ix = 0;                  /* Index of current root */
					}
					k = 1;                            /* Reset loop counter */
				}
			}
		}
	}
	// end A2NLSF.c

	// start wrappers_FLP
	/**
	 * Convert AR filter coefficients to NLSF parameters
	 *
	 * @param NLSF_Q15 O    NLSF vector      [ LPC_order ]
	 * @param pAR I    LPC coefficients [ LPC_order ]
	 * @param LPC_order I    LPC order
	 */
	private static final void silk_A2NLSF_FLP(final short[] NLSF_Q15, final float[] pAR, final int LPC_order)
	{
		final int a_fix_Q16[] = new int[ Jdefine.MAX_LPC_ORDER ];

		for( int i = 0; i < LPC_order; i++ ) {
			a_fix_Q16[ i ] = (int)Math.floor( (double)(.5f + pAR[ i ] * 65536.0f) );// XXX can be using direct casting
		}

		silk_A2NLSF( NLSF_Q15, a_fix_Q16, LPC_order );
	}
	/**
	 * Convert LSF parameters to AR prediction filter coefficients
	 *
	 * @param pAR O    LPC coefficients [ LPC_order ]
	 * @param NLSF_Q15 I    NLSF vector      [ LPC_order ]
	 * @param LPC_order I    LPC order
	 * @param arch I    Run-time architecture
	 */
	private static final void silk_NLSF2A_FLP(final float[] pAR, final short[] NLSF_Q15, final int LPC_order)//, int arch )
	{
		final short a_fix_Q12[] = new short[ Jdefine.MAX_LPC_ORDER ];

		silk_NLSF2A( a_fix_Q12, NLSF_Q15, LPC_order );//, arch );

		for( int i = 0; i < LPC_order; i++ ) {
			pAR[ i ] = (float)a_fix_Q12[ i ] * ( 1.0f / 4096.0f );
		}
	}
	/**
	 * Floating-point NLSF processing wrapper
	 *
	 * @param psEncC I/O  Encoder state
	 * @param PredCoef O    Prediction coefficients
	 * @param NLSF_Q15 I/O  Normalized LSFs (quant out) (0 - (2^15-1))
	 * @param prev_NLSF_Q15 I    Previous Normalized LSFs (0 - (2^15-1))
	 */
	final void silk_process_NLSFs_FLP(// final Jsilk_encoder_state psEncC,
			final float PredCoef[/* 2 */][/* MAX_LPC_ORDER */],
			final short NLSF_Q15[/* MAX_LPC_ORDER */], final short prev_NLSF_Q15[/* MAX_LPC_ORDER */]
		)
	{
		final short PredCoef_Q12[][] = new short[ 2 ][ Jdefine.MAX_LPC_ORDER ];

		silk_process_NLSFs( PredCoef_Q12, NLSF_Q15, prev_NLSF_Q15 );

		for( int j = 0; j < 2; j++ ) {
			final float[] fc = PredCoef[ j ];// java
			final short[] sc = PredCoef_Q12[ j ];// java
			for( int i = 0, ie = this.predictLPCOrder; i < ie; i++ ) {
				fc[ i ] = (float)sc[ i ] * ( 1.0f / 4096.0f );
			}
		}
	}
	// end wrappers_FLP

	// start ana_filt_bank_1.c
	/* Coefficients for 2-band filter bank based on first-order allpass filters */
	private static final short A_fb1_20 = 5394 << 1;
	private static final short A_fb1_21 = -24290; /* (opus_int16)(20623 << 1) */

	/**
	 * Split signal into two decimated bands using first-order allpass filters
	 *
	 * @param in I    Input signal [N]
	 * @param inoffset I java an offset for the in
	 * @param S I/O  State vector [2]
	 * @param outL O    Low band [N/2]
	 * @param outH O    High band [N/2]
	 * @param hoffset I java an offset for the outH
	 * @param N I    Number of input samples
	 */
	private static final void silk_ana_filt_bank_1(final short[] in, int inoffset,// java
			final int[] S, final short[] outL,
			final short[] outH, int hoffset,// java
			final int N)
	{
		final int N2 = N >> 1;

		/* Internal variables and state are in Q10 format */
		for( int k = 0; k < N2; k++ ) {
			/* Convert to Q10 */
			int in32 = (int)in[ inoffset++ ] << 10;// java

			/* All-pass section for even input sample */
			int Y      = in32 - S[ 0 ];
			int X      = Y + (int)((Y * (long)A_fb1_21) >> 16);
			final int out_1  = S[ 0 ] + X;
			S[ 0 ] = in32 + X;

			/* Convert to Q10 */
			in32 = (int)in[ inoffset++ ] << 10;// java

			/* All-pass section for odd input sample, and add to output of previous section */
			Y      = in32 - S[ 1 ];
			X      = (int)((Y * (long)A_fb1_20) >> 16);
			final int out_2  = S[ 1 ] + X;
			S[ 1 ] = in32 + X;

			/* Add/subtract, convert back to int16 and store to output */
			int v = JSigProc_FIX.silk_RSHIFT_ROUND( ( out_2 + out_1 ), 11 );// java
			outL[ k ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			v = JSigProc_FIX.silk_RSHIFT_ROUND( ( out_2 - out_1 ), 11 );// java
			outH[ hoffset++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}
	}
	// end ana_filt_bank_1.c

	// start sigm_Q15.c
	/* Approximate sigmoid function */

	/* fprintf(1, '%d, ', round(1024 * ([1 ./ (1 + exp(-(1:5))), 1] - 1 ./ (1 + exp(-(0:5)))))); */
	private static final int sigm_LUT_slope_Q10[/* 6 */] = {
			237, 153, 73, 30, 12, 7
		};
	/* fprintf(1, '%d, ', round(32767 * 1 ./ (1 + exp(-(0:5))))); */
	private static final int sigm_LUT_pos_Q15[/* 6 */] = {
			16384, 23955, 28861, 31213, 32178, 32548
		};
	/* fprintf(1, '%d, ', round(32767 * 1 ./ (1 + exp((0:5))))); */
	private static final int sigm_LUT_neg_Q15[/* 6 */] = {
			16384, 8812, 3906, 1554, 589, 219
		};

	private static final int silk_sigm_Q15(int in_Q5)
	{
		if( in_Q5 < 0 ) {
			/* Negative input */
			in_Q5 = -in_Q5;
			if( in_Q5 >= 6 * 32 ) {
				return 0;        /* Clip */
			}// else {
				/* Linear interpolation of look up table */
				final int ind = in_Q5 >> 5;
				return( sigm_LUT_neg_Q15[ ind ] - sigm_LUT_slope_Q10[ ind ] * (in_Q5 & 0x1F) );
			//}
		}// else {
			/* Positive input */
			if( in_Q5 >= 6 * 32 ) {
				return 32767;        /* clip */
			}// else {
				/* Linear interpolation of look up table */
				final int ind = in_Q5 >> 5;
				return( sigm_LUT_pos_Q15[ ind ] + sigm_LUT_slope_Q10[ ind ] * (in_Q5 & 0x1F) );
			//}
		//}
	}
	// end sigm_Q15.c

	// start VAD.c
	/** Weighting factors for tilt measure */
	private static final int tiltWeights[/* VAD_N_BANDS */] = { 30000, 6000, -12000, -12000 };

	/**
	 * Get the speech activity level in Q8
	 *
	 * @param psEncC I/O  Encoder state
	 * @param pIn I    PCM input
	 * @param inoffset java start offset to pIn
	 * @return Get the speech activity level in Q8
	 */
	// private static final int silk_VAD_GetSA_Q8_c(final Jsilk_encoder_state psEncC, final short pIn[])
	final int silk_VAD_GetSA_Q8(final short pIn[], final int inoffset)// java
	{// java name is changed
		final int ret = 0;
		final Jsilk_VAD_state psSilk_VAD = this.sVAD;
		// SAVE_STACK;

		/* Safety checks */
		// silk_assert( VAD_N_BANDS == 4 );
		// celt_assert( MAX_FRAME_LENGTH >= psEncC.frame_length );
		// celt_assert( psEncC.frame_length <= 512 );
		// celt_assert( psEncC.frame_length == 8 * silk_RSHIFT( psEncC.frame_length, 3 ) );

		/***********************/
		/* Filter and Decimate */
		/***********************/
		final int decimated_framelength1 = this.frame_length >> 1;
		final int decimated_framelength2 = this.frame_length >> 2;
		int decimated_framelength = this.frame_length >> 3;
		/* Decimate into 4 bands:
		0       L      3L       L              3L                             5L
		-      --       -              --                             --
		8       8       2               4                              4

		[0-1 kHz| temp. |1-2 kHz|    2-4 kHz    |            4-8 kHz           |

		They're arranged to allow the minimal ( frame_length / 4 ) extra
		scratch space during the downsampling process */
		final int X_offset[] = new int[ Jdefine.VAD_N_BANDS ];
		X_offset[ 0 ] = 0;
		X_offset[ 1 ] = decimated_framelength + decimated_framelength2;
		X_offset[ 2 ] = X_offset[ 1 ] + decimated_framelength;
		X_offset[ 3 ] = X_offset[ 2 ] + decimated_framelength2;
		final short[] X = new short[ X_offset[ 3 ] + decimated_framelength1 ];

		/* 0-8 kHz to 0-4 kHz and 4-8 kHz */
		silk_ana_filt_bank_1( pIn, inoffset, psSilk_VAD.AnaState/*[  0 ]*/, X, X, X_offset[ 3 ], this.frame_length );

		/* 0-4 kHz to 0-2 kHz and 2-4 kHz */
		silk_ana_filt_bank_1( X, 0, psSilk_VAD.AnaState1/*[ 0 ]*/, X, X, X_offset[ 2 ], decimated_framelength1 );

		/* 0-2 kHz to 0-1 kHz and 1-2 kHz */
		silk_ana_filt_bank_1( X, 0, psSilk_VAD.AnaState2/*[ 0 ]*/, X, X, X_offset[ 1 ], decimated_framelength2 );

		/*********************************************/
		/* HP filter on lowest band (differentiator) */
		/*********************************************/
		X[ decimated_framelength - 1 ] >>= 1;
		final short HPstateTmp = X[ decimated_framelength - 1 ];
		for( int i = decimated_framelength - 1; i > 0; i-- ) {
			X[ i - 1 ]  = (short)( (int)X[ i - 1 ] >> 1 );
			X[ i ]     -= X[ i - 1 ];
		}
		X[ 0 ] -= psSilk_VAD.HPstate;
		psSilk_VAD.HPstate = HPstateTmp;

		/*************************************/
		/* Calculate the energy in each band */
		/*************************************/
		final int Xnrg[] = new int[ Jdefine.VAD_N_BANDS ];
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			/* Find the decimated framelength in the non-uniformly divided bands */
			decimated_framelength = Jdefine.VAD_N_BANDS - b;// java
			decimated_framelength = decimated_framelength <= (Jdefine.VAD_N_BANDS - 1) ? decimated_framelength : (Jdefine.VAD_N_BANDS - 1);
			decimated_framelength = this.frame_length >> decimated_framelength;

			/* Split length into subframe lengths */
			final int dec_subframe_length = decimated_framelength >> Jdefine.VAD_INTERNAL_SUBFRAMES_LOG2;
			int dec_subframe_offset = 0;

			/* Compute energy per sub-frame */
			/* initialize with summed energy of last subframe */
			Xnrg[ b ] = psSilk_VAD.XnrgSubfr[ b ];
			int sumSquared = 0;// to avoid warning "The local variable sumSquared may not have been initialized"
			for( int s = 0; s < Jdefine.VAD_INTERNAL_SUBFRAMES; s++ ) {
				sumSquared = 0;
				for( int i = 0; i < dec_subframe_length; i++ ) {
					/* The energy will be less than dec_subframe_length * ( silk_int16_MIN / 8 ) ^ 2.            */
					/* Therefore we can accumulate with no risk of overflow (unless dec_subframe_length > 128)  */
					final int x_tmp = ( ((int)X[ X_offset[ b ] + i + dec_subframe_offset ]) >> 3 );
					sumSquared += x_tmp * x_tmp;

					/* Safety check */
					// silk_assert( sumSquared >= 0 );
				}

				/* Add/saturate summed energy of current subframe */
				if( s < Jdefine.VAD_INTERNAL_SUBFRAMES - 1 ) {
					int v = Xnrg[ b ];// java
					v += sumSquared;
					Xnrg[ b ] = (v & 0x80000000) != 0 ? Integer.MAX_VALUE : v;
				} else {
					/* Look-ahead subframe */
					int v = Xnrg[ b ];// java
					v += sumSquared >> 1;
					Xnrg[ b ] = (v & 0x80000000) != 0 ? Integer.MAX_VALUE : v;
				}

				dec_subframe_offset += dec_subframe_length;
			}
			psSilk_VAD.XnrgSubfr[ b ] = sumSquared;
		}

		/********************/
		/* Noise estimation */
		/********************/
		psSilk_VAD.silk_VAD_GetNoiseLevels( Xnrg/*[ 0 ]*/);

		/***********************************************/
		/* Signal-plus-noise to noise ratio estimation */
		/***********************************************/
		final int NrgToNoiseRatio_Q8[] = new int[ Jdefine.VAD_N_BANDS ];
		int sumSquared = 0;
		int input_tilt = 0;
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			final int speech_nrg = Xnrg[ b ] - psSilk_VAD.NL[ b ];
			if( speech_nrg > 0 ) {
				/* Divide, with sufficient resolution */
				if( ( Xnrg[ b ] & 0xFF800000 ) == 0 ) {
					NrgToNoiseRatio_Q8[ b ] = ( Xnrg[ b ] << 8 ) / (psSilk_VAD.NL[ b ] + 1);
				} else {
					NrgToNoiseRatio_Q8[ b ] = Xnrg[ b ] / (( psSilk_VAD.NL[ b ] >> 8 ) + 1);
				}

				/* Convert to log domain */
				int SNR_Q7 = Jmacros.silk_lin2log( NrgToNoiseRatio_Q8[ b ] ) - 8 * 128;

				/* Sum-of-squares */
				sumSquared += SNR_Q7 * SNR_Q7;          /* Q14 */

				/* Tilt measure */
				if( speech_nrg < ( 1 << 20 ) ) {
					/* Scale down SNR value for small subband speech energies */
					SNR_Q7 = (int)((( silk_SQRT_APPROX( speech_nrg ) << 6 ) * (long)SNR_Q7) >> 16);
				}
				input_tilt += (int)((tiltWeights[ b ] * (long)SNR_Q7) >> 16);
			} else {
				NrgToNoiseRatio_Q8[ b ] = 256;
			}
		}

		/* Mean-of-squares */
		sumSquared /= Jdefine.VAD_N_BANDS; /* Q14 */

		/* Root-mean-square approximation, scale to dBs, and write to output pointer */
		final int pSNR_dB_Q7 = /*(int)(short)*/( 3 * silk_SQRT_APPROX( sumSquared ) ); /* Q7 */ // FIXME why casting to int16?

		/*********************************/
		/* Speech Probability Estimation */
		/*********************************/
		int SA_Q15 = silk_sigm_Q15( ((int)(((long)Jdefine.VAD_SNR_FACTOR_Q16 * (long)pSNR_dB_Q7) >> 16)) - Jdefine.VAD_NEGATIVE_OFFSET_Q5 );

		/**************************/
		/* Frequency Tilt Measure */
		/**************************/
		this.input_tilt_Q15 = (silk_sigm_Q15( input_tilt ) - 16384) << 1;

		/**************************************************/
		/* Scale the sigmoid output based on power levels */
		/**************************************************/
		int speech_nrg = 0;
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			/* Accumulate signal-without-noise energies, higher frequency bands have more weight */
			speech_nrg += ( b + 1 ) * ( (Xnrg[ b ] - psSilk_VAD.NL[ b ]) >> 4 );
		}

		if( this.frame_length == 20 * this.fs_kHz ) {
			speech_nrg >>= 1;
		}
		/* Power scaling */
		if( speech_nrg <= 0 ) {
			SA_Q15 >>= 1;
		} else if( speech_nrg < 16384 ) {
			speech_nrg <<= 16;
			/* square-root */
			speech_nrg = silk_SQRT_APPROX( speech_nrg );
			SA_Q15 = (int)(((32768 + speech_nrg) * (long)SA_Q15) >> 16);
		}

		/* Copy the resulting speech activity in Q8 */
		int v = ( SA_Q15 >> 7 );// java
		this.speech_activity_Q8 = ( v <= 255 ? v : 255 );//silk_uint8_MAX );

		/***********************************/
		/* Energy Level and SNR estimation */
		/***********************************/
		/* Smoothing coefficient */
		int smooth_coef_Q16 = ((int)(((long)Jdefine.VAD_SNR_SMOOTH_COEF_Q18 * (long)((int)((SA_Q15 * (long)SA_Q15) >> 16))) >> 16));

		if( this.frame_length == 10 * this.fs_kHz ) {
			smooth_coef_Q16 >>= 1;
		}

		final int[] ratio = psSilk_VAD.NrgRatioSmth_Q8;// java
		final int[] iqb = this.input_quality_bands_Q15;// java
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			/* compute smoothed energy-to-noise ratio per band */
			v = ratio[ b ];// java
			v += (int)(((NrgToNoiseRatio_Q8[ b ] - v) * (long)smooth_coef_Q16) >> 16);
			ratio[ b ] = v;

			/* signal to noise ratio in dB per band */
			final int SNR_Q7 = 3 * ( Jmacros.silk_lin2log( v ) - 8 * 128 );
			/* quality = sigmoid( 0.25 * ( SNR_dB - 16 ) ); */
			iqb[ b ] = silk_sigm_Q15( ( (SNR_Q7 - 16 * 128) >> 4 ) );
		}

		// RESTORE_STACK;
		return( ret );
	}
	// end VAD.c
	// encode_indices.c
	/**
	 * Encode side-information parameters to payload
	 *
	 * @param psEncC I/O  Encoder state
	 * @param psRangeEnc I/O  Compressor data structure
	 * @param FrameIndex I    Frame number
	 * @param encode_LBRR I    Flag indicating LBRR data is being encoded
	 * @param condCoding I    The type of conditional coding to use
	 */
	final void silk_encode_indices(final Jec_enc psRangeEnc,
			final int FrameIndex, final boolean encode_LBRR, final int condCoding )
	{
		final JSideInfoIndices psIndices = encode_LBRR ? this.indices_LBRR[ FrameIndex ] : this.indices;

		/*******************************************/
		/* Encode signal type and quantizer offset */
		/*******************************************/
		final int typeOffset = ((int)psIndices.signalType << 1) + psIndices.quantOffsetType;
		// celt_assert( typeOffset >= 0 && typeOffset < 6 );
		// celt_assert( encode_LBRR == 0 || typeOffset >= 2 );
		if( encode_LBRR || typeOffset >= 2 ) {
			psRangeEnc.ec_enc_icdf( typeOffset - 2, Jtables_other.silk_type_offset_VAD_iCDF, 0, 8 );
		} else {
			psRangeEnc.ec_enc_icdf( typeOffset, Jtables_other.silk_type_offset_no_VAD_iCDF, 0, 8 );
		}

		byte[] tmp_indices = psIndices.GainsIndices;// java
		/****************/
		/* Encode gains */
		/****************/
		/* first subframe */
		if( condCoding == Jdefine.CODE_CONDITIONALLY ) {
			/* conditional coding */
			// silk_assert( psIndices.GainsIndices[ 0 ] >= 0 && psIndices.GainsIndices[ 0 ] < MAX_DELTA_GAIN_QUANT - MIN_DELTA_GAIN_QUANT + 1 );
			psRangeEnc.ec_enc_icdf( (int)tmp_indices[ 0 ], Jtables_gain.silk_delta_gain_iCDF, 0, 8 );
		} else {
			/* independent coding, in two stages: MSB bits followed by 3 LSBs */
			// silk_assert( psIndices.GainsIndices[ 0 ] >= 0 && psIndices.GainsIndices[ 0 ] < N_LEVELS_QGAIN );
			psRangeEnc.ec_enc_icdf( ( (int)tmp_indices[ 0 ] >> 3 ), Jtables_gain.silk_gain_iCDF[ psIndices.signalType ], 0, 8 );
			psRangeEnc.ec_enc_icdf( (int)tmp_indices[ 0 ] & 7, Jtables_other.silk_uniform8_iCDF, 0, 8 );
		}

		/* remaining subframes */
		for( int i = 1, ie = this.nb_subfr; i < ie; i++ ) {
			// silk_assert( psIndices.GainsIndices[ i ] >= 0 && psIndices.GainsIndices[ i ] < MAX_DELTA_GAIN_QUANT - MIN_DELTA_GAIN_QUANT + 1 );
			psRangeEnc.ec_enc_icdf( tmp_indices[ i ], Jtables_gain.silk_delta_gain_iCDF, 0, 8 );
		}

		/****************/
		/* Encode NLSFs */
		/****************/
		final short ec_ix[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final char pred_Q8[] = new char[ Jdefine.MAX_LPC_ORDER ];// java uint8 to char
		tmp_indices = psIndices.NLSFIndices;// java
		psRangeEnc.ec_enc_icdf( (int)tmp_indices[ 0 ], this.psNLSF_CB.CB1_iCDF, ((int)psIndices.signalType >> 1) * (int)this.psNLSF_CB.nVectors, 8 );
		silk_NLSF_unpack( ec_ix, pred_Q8, this.psNLSF_CB, (int)tmp_indices[ 0 ] );
		// celt_assert( psEncC.psNLSF_CB.order == psEncC.predictLPCOrder );
		for( int i = 0, ie = this.psNLSF_CB.order; i < ie; i++ ) {
			if( (int)tmp_indices[ i + 1 ] >= Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
				psRangeEnc.ec_enc_icdf( 2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE, this.psNLSF_CB.ec_iCDF, (int)ec_ix[ i ], 8 );
				psRangeEnc.ec_enc_icdf( tmp_indices[ i+1 ] - Jdefine.NLSF_QUANT_MAX_AMPLITUDE, Jtables_other.silk_NLSF_EXT_iCDF, 0, 8 );
			} else if( (int)tmp_indices[ i + 1 ] <= -Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
				psRangeEnc.ec_enc_icdf( 0, this.psNLSF_CB.ec_iCDF, (int)ec_ix[ i ], 8 );
				psRangeEnc.ec_enc_icdf( -(int)tmp_indices[ i + 1 ] - Jdefine.NLSF_QUANT_MAX_AMPLITUDE, Jtables_other.silk_NLSF_EXT_iCDF, 0, 8 );
			} else {
				psRangeEnc.ec_enc_icdf( (int)tmp_indices[ i + 1 ] + Jdefine.NLSF_QUANT_MAX_AMPLITUDE, this.psNLSF_CB.ec_iCDF, (int)ec_ix[ i ], 8 );
			}
		}

		/* Encode NLSF interpolation factor */
		if( this.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
			// silk_assert( psIndices.NLSFInterpCoef_Q2 >= 0 && psIndices.NLSFInterpCoef_Q2 < 5 );
			psRangeEnc.ec_enc_icdf( psIndices.NLSFInterpCoef_Q2, Jtables_other.silk_NLSF_interpolation_factor_iCDF, 0, 8 );
		}

		if( psIndices.signalType == Jdefine.TYPE_VOICED )
		{
			/*********************/
			/* Encode pitch lags */
			/*********************/
			/* lag index */
			boolean encode_absolute_lagIndex = true;
			if( condCoding == Jdefine.CODE_CONDITIONALLY && this.ec_prevSignalType == Jdefine.TYPE_VOICED ) {
				/* Delta Encoding */
				int delta_lagIndex = psIndices.lagIndex - this.ec_prevLagIndex;
				if( delta_lagIndex < -8 || delta_lagIndex > 11 ) {
					delta_lagIndex = 0;
				} else {
					delta_lagIndex = delta_lagIndex + 9;
					encode_absolute_lagIndex = false; /* Only use delta */
				}
				// silk_assert( delta_lagIndex >= 0 && delta_lagIndex < 21 );
				psRangeEnc.ec_enc_icdf( delta_lagIndex, Jtables_pitch_lag.silk_pitch_delta_iCDF, 0, 8 );
			}
			if( encode_absolute_lagIndex ) {
				/* Absolute encoding */
				final int pitch_high_bits = psIndices.lagIndex / ( this.fs_kHz >> 1 );
				final int pitch_low_bits = psIndices.lagIndex - ( pitch_high_bits * ( this.fs_kHz >> 1 ) );
				// silk_assert( pitch_low_bits < psEncC.fs_kHz / 2 );
				// silk_assert( pitch_high_bits < 32 );
				psRangeEnc.ec_enc_icdf( pitch_high_bits, Jtables_pitch_lag.silk_pitch_lag_iCDF, 0, 8 );
				psRangeEnc.ec_enc_icdf( pitch_low_bits, this.pitch_lag_low_bits_iCDF, 0, 8 );
			}
			this.ec_prevLagIndex = psIndices.lagIndex;

			/* Countour index */
			// silk_assert(   psIndices.contourIndex  >= 0 );
			/* silk_assert( ( psIndices.contourIndex < 34 && psEncC.fs_kHz  > 8 && psEncC.nb_subfr == 4 ) ||
				( psIndices.contourIndex < 11 && psEncC.fs_kHz == 8 && psEncC.nb_subfr == 4 ) ||
				( psIndices.contourIndex < 12 && psEncC.fs_kHz  > 8 && psEncC.nb_subfr == 2 ) ||
				( psIndices.contourIndex <  3 && psEncC.fs_kHz == 8 && psEncC.nb_subfr == 2 ) ); */
			psRangeEnc.ec_enc_icdf( psIndices.contourIndex, this.pitch_contour_iCDF, 0, 8 );

			/********************/
			/* Encode LTP gains */
			/********************/
			/* PERIndex value */
			// silk_assert( psIndices.PERIndex >= 0 && psIndices.PERIndex < 3 );
			psRangeEnc.ec_enc_icdf( psIndices.PERIndex, Jtables_LTP.silk_LTP_per_index_iCDF, 0, 8 );

			/* Codebook Indices */
			for( int k = 0, ke = this.nb_subfr; k < ke; k++ ) {
				// silk_assert( psIndices.LTPIndex[ k ] >= 0 && psIndices.LTPIndex[ k ] < ( 8 << psIndices.PERIndex ) );
				psRangeEnc.ec_enc_icdf( psIndices.LTPIndex[ k ], Jtables_LTP.silk_LTP_gain_iCDF_ptrs[ psIndices.PERIndex ], 0, 8 );
			}

			/**********************/
			/* Encode LTP scaling */
			/**********************/
			if( condCoding == Jdefine.CODE_INDEPENDENTLY ) {
				// silk_assert( psIndices.LTP_scaleIndex >= 0 && psIndices.LTP_scaleIndex < 3 );
				psRangeEnc.ec_enc_icdf( psIndices.LTP_scaleIndex, Jtables_other.silk_LTPscale_iCDF, 0, 8 );
			}
			// silk_assert( !condCoding || psIndices.LTP_scaleIndex == 0 );
		}

		this.ec_prevSignalType = psIndices.signalType;

		/***************/
		/* Encode seed */
		/***************/
		// silk_assert( psIndices.Seed >= 0 && psIndices.Seed < 4 );
		psRangeEnc.ec_enc_icdf( psIndices.Seed, Jtables_other.silk_uniform4_iCDF, 0, 8 );
	}
	// end encode_indices.c
	// start control_audio_bandwidth.c
	/**
	 * Control internal sampling rate
	 *
	 * @param psEncC I/O  Pointer to Silk encoder state
	 * @param encControl I    Control structure
	 * @return
	 */
	final int silk_control_audio_bandwidth(final Jsilk_EncControlStruct encControl)
	{
		int orig_kHz = this.fs_kHz;
		/* Handle a bandwidth-switching reset where we need to be aware what the last sampling rate was. */
		if( orig_kHz == 0 ) {
			orig_kHz = this.sLP.saved_fs_kHz;
		}
		int sfs_kHz = orig_kHz;// java renamed to avoid hiding the field
		int fs_Hz = sfs_kHz * 1000;
		if( fs_Hz == 0 ) {
			/* Encoder has just been initialized */
			fs_Hz  = this.desiredInternal_fs_Hz < this.API_fs_Hz ? this.desiredInternal_fs_Hz : this.API_fs_Hz;
			sfs_kHz = fs_Hz / 1000;
		} else if( fs_Hz > this.API_fs_Hz || fs_Hz > this.maxInternal_fs_Hz || fs_Hz < this.minInternal_fs_Hz ) {
			/* Make sure internal rate is not higher than external rate or maximum allowed, or lower than minimum allowed */
			fs_Hz  = this.API_fs_Hz;
			fs_Hz  = fs_Hz < this.maxInternal_fs_Hz ? fs_Hz : this.maxInternal_fs_Hz;
			fs_Hz  = fs_Hz > this.minInternal_fs_Hz ? fs_Hz : this.minInternal_fs_Hz;
			sfs_kHz = fs_Hz / 1000;
		} else {
			/* State machine for the internal sampling rate switching */
			if( this.sLP.transition_frame_no >= Jdefine.TRANSITION_FRAMES ) {
				/* Stop transition phase */
				this.sLP.mode = 0;
			}
			if( this.allow_bandwidth_switch || encControl.opusCanSwitch ) {
				/* Check if we should switch down */
				if( orig_kHz * 1000 > this.desiredInternal_fs_Hz )
				{
					/* Switch down */
					if( this.sLP.mode == 0 ) {
						/* New transition */
						this.sLP.transition_frame_no = Jdefine.TRANSITION_FRAMES;

						/* Reset transition filter state */
						for( int i = 0, end = this.sLP.In_LP_State.length; i < end; i++ ) {
							this.sLP.In_LP_State[i] = 0;
						}
					}
					if( encControl.opusCanSwitch ) {
						/* Stop transition phase */
						this.sLP.mode = 0;

						/* Switch to a lower sample frequency */
						sfs_kHz = orig_kHz == 16 ? 12 : 8;
					} else {
						if( this.sLP.transition_frame_no <= 0 ) {
							encControl.switchReady = true;
							/* Make room for redundancy */
							encControl.maxBits -= encControl.maxBits * 5 / ( encControl.payloadSize_ms + 5 );
						} else {
							/* Direction: down (at double speed) */
							this.sLP.mode = -2;
						}
					}
				}
				else
					/* Check if we should switch up */
					if( orig_kHz * 1000 < this.desiredInternal_fs_Hz )
					{
						/* Switch up */
						if( encControl.opusCanSwitch ) {
							/* Switch to a higher sample frequency */
							sfs_kHz = orig_kHz == 8 ? 12 : 16;

							/* New transition */
							this.sLP.transition_frame_no = 0;

							/* Reset transition filter state */
							for( int i = 0, end = this.sLP.In_LP_State.length; i < end; i++ ) {
								this.sLP.In_LP_State[i] = 0;
							}

							/* Direction: up */
							this.sLP.mode = 1;
						} else {
							if( this.sLP.mode == 0 ) {
								encControl.switchReady = true;
								/* Make room for redundancy */
								encControl.maxBits -= encControl.maxBits * 5 / ( encControl.payloadSize_ms + 5 );
							} else {
								/* Direction: up */
								this.sLP.mode = 1;
							}
						}
					} else {
						if( this.sLP.mode < 0 ) {
							this.sLP.mode = 1;
					}
				}
			}
		}
		return sfs_kHz;
	}
	// end control_audio_bandwidth.c

	// start control_codec.c
	/**
	 *
	 * @param psEncC I/O
	 * @param complexity I
	 * @return
	 */
	final int silk_setup_complexity(final int complexity)
	{
		final int ret = 0;

		/* Set encoding complexity */
		// celt_assert( Complexity >= 0 && Complexity <= 10 );
		if( complexity < 1 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MIN_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.8 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 6;
			this.shapingLPCOrder                 = 12;
			this.la_shape                        = 3 * this.fs_kHz;
			this.nStatesDelayedDecision          = 1;
			this.useInterpolatedNLSFs            = false;
			this.NLSF_MSVQ_Survivors             = 2;
			this.warping_Q16                     = 0;
		} else if( complexity < 2 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MID_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.76 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 8;
			this.shapingLPCOrder                 = 14;
			this.la_shape                        = 5 * this.fs_kHz;
			this.nStatesDelayedDecision          = 1;
			this.useInterpolatedNLSFs            = false;
			this.NLSF_MSVQ_Survivors             = 3;
			this.warping_Q16                     = 0;
		} else if( complexity < 3 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MIN_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.8 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 6;
			this.shapingLPCOrder                 = 12;
			this.la_shape                        = 3 * this.fs_kHz;
			this.nStatesDelayedDecision          = 2;
			this.useInterpolatedNLSFs            = false;
			this.NLSF_MSVQ_Survivors             = 2;
			this.warping_Q16                     = 0;
		} else if( complexity < 4 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MID_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.76 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 8;
			this.shapingLPCOrder                 = 14;
			this.la_shape                        = 5 * this.fs_kHz;
			this.nStatesDelayedDecision          = 2;
			this.useInterpolatedNLSFs            = false;
			this.NLSF_MSVQ_Survivors             = 4;
			this.warping_Q16                     = 0;
		} else if( Complexity < 6 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MID_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.74 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 10;
			this.shapingLPCOrder                 = 16;
			this.la_shape                        = 5 * this.fs_kHz;
			this.nStatesDelayedDecision          = 2;
			this.useInterpolatedNLSFs            = true;
			this.NLSF_MSVQ_Survivors             = 6;
			this.warping_Q16                     = this.fs_kHz * ((int)( Jtuning_parameters.WARPING_MULTIPLIER * (1 << 16) + 0.5));
		} else if( Complexity < 8 ) {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MID_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.72 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 12;
			this.shapingLPCOrder                 = 20;
			this.la_shape                        = 5 * this.fs_kHz;
			this.nStatesDelayedDecision          = 3;
			this.useInterpolatedNLSFs            = true;
			this.NLSF_MSVQ_Survivors             = 8;
			this.warping_Q16                     = this.fs_kHz * ((int)( Jtuning_parameters.WARPING_MULTIPLIER * (1 << 16) + 0.5));
		} else {
			this.pitchEstimationComplexity       = Jpitch_est_defines.SILK_PE_MAX_COMPLEX;
			this.pitchEstimationThreshold_Q16    = (int)( 0.7 * (1 << 16) + 0.5 );
			this.pitchEstimationLPCOrder         = 16;
			this.shapingLPCOrder                 = 24;
			this.la_shape                        = 5 * this.fs_kHz;
			this.nStatesDelayedDecision          = Jdefine.MAX_DEL_DEC_STATES;
			this.useInterpolatedNLSFs            = true;
			this.NLSF_MSVQ_Survivors             = 16;
			this.warping_Q16                     = this.fs_kHz * ((int)( Jtuning_parameters.WARPING_MULTIPLIER * (1 << 16) + 0.5));
		}

		/* Do not allow higher pitch estimation LPC order than predict LPC order */
		this.pitchEstimationLPCOrder = this.pitchEstimationLPCOrder < this.predictLPCOrder ? this.pitchEstimationLPCOrder : this.predictLPCOrder;
		this.shapeWinLength          = Jdefine.SUB_FRAME_LENGTH_MS * this.fs_kHz + (this.la_shape << 1);
		this.Complexity              = complexity;

		// celt_assert( psEncC.pitchEstimationLPCOrder <= MAX_FIND_PITCH_LPC_ORDER );
		// celt_assert( psEncC.shapingLPCOrder         <= MAX_SHAPE_LPC_ORDER      );
		// celt_assert( psEncC.nStatesDelayedDecision  <= MAX_DEL_DEC_STATES       );
		// celt_assert( psEncC.warping_Q16             <= 32767                    );
		// celt_assert( psEncC.la_shape                <= LA_SHAPE_MAX             );
		// celt_assert( psEncC.shapeWinLength          <= SHAPE_LPC_WIN_MAX        );

		return ret;
	}

	/**
	 *
	 * @param psEncC I/O
	 * @param encControl I
	 * @return
	 */
	final int silk_setup_LBRR(final Jsilk_EncControlStruct encControl)
	{
		final int ret = Jerrors.SILK_NO_ERROR;

		final boolean LBRR_in_previous_packet = this.LBRR_enabled;
		this.LBRR_enabled = encControl.LBRR_coded;
		if( this.LBRR_enabled ) {
			/* Set gain increase for coding LBRR excitation */
			if( ! LBRR_in_previous_packet ) {
				/* Previous packet did not have LBRR, and was therefore coded at a higher bitrate */
				this.LBRR_GainIncreases = 7;
			} else {
				// final int v = 7 - Jmacros.silk_SMULWB( this.PacketLoss_perc, SILK_FIX_CONST( 0.4, 16 ) );// java
				final int v = 7 - ((int)((this.PacketLoss_perc * ((long)(0.4 * (1 << 16) + 0.5))) >> 16));// java
				this.LBRR_GainIncreases = v >= 2 ? v : 2;
			}
		}

		return ret;
	}
	// end control_codec.c

	// start burg_modified_FLP
	// private static final int MAX_FRAME_SIZE = 384; /* subfr_length * nb_subfr = ( 0.005 * 16000 + 16 ) * 4 = 384 */

	/**
	 * Compute reflection coefficients from input signal
	 *
	 * @param A O    prediction coefficients (length order)
	 * @param x I    input signal, length: nb_subfr*(D+L_sub)
	 * @param xoffset I java an offset for the x
	 * @param minInvGain I    minimum inverse prediction gain
	 * @param subfr_length I    input signal subframe length (incl. D preceding samples)
	 * @param nb_subfr I    number of subframes stacked in x
	 * @param D I    order
	 * @return O    returns residual energy
	 */
	private static final float silk_burg_modified_FLP(final float A[],
			final float x[], final int xoffset,// java
			final float minInvGain, final int subfr_length, final int nb_subfr, final int D
		)
	{
		final double C_last_row[] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];
		final double CAf[] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC + 1 ];
		final double CAb[] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC + 1 ];
		final double Af[] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];

		// celt_assert( subfr_length * nb_subfr <= MAX_FRAME_SIZE );

		final int data_size = nb_subfr * subfr_length;// java
		/* Compute autocorrelations, added over subframes */
		double C0 = silk_energy_FLP( x, xoffset, data_size );
		final double C_first_row[] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];// java already zeroed
		// silk_memset( C_first_row, 0, SILK_MAX_ORDER_LPC * sizeof( double ) );
		final int xoffset_data_size = data_size + xoffset;// java
		for( int x_ptr = xoffset; x_ptr < xoffset_data_size; x_ptr += subfr_length ) {// java
			//final int x_ptr = s * subfr_length;// x[ x_ptr ]
			for( int n = 1; n <= D; n++ ) {
				C_first_row[ n - 1 ] += silk_inner_product_FLP( x, x_ptr, x, x_ptr + n, subfr_length - n );
			}
		}
		System.arraycopy( C_first_row, 0, C_last_row, 0, JSigProc_FIX.SILK_MAX_ORDER_LPC );

		/* Initialize */
		CAb[ 0 ] = CAf[ 0 ] = C0 + Jtuning_parameters.FIND_LPC_COND_FAC * C0 + 1e-9f;
		double invGain = 1.0;
		boolean reached_max_gain = false;
		for( int n = 0; n < D; n++ ) {
			/* Update first row of correlation matrix (without first element) */
			/* Update last row of correlation matrix (without last element, stored in reversed order) */
			/* Update C * Af */
			/* Update C * flipud(Af) (stored in reversed order) */
			for( int x_ptr_n = n + xoffset, xe = n + xoffset_data_size, x_ptr_s = xoffset + subfr_length - n;
					x_ptr_n < xe;
					x_ptr_n += subfr_length, x_ptr_s += subfr_length ) {
				// int x_ptr = s * subfr_length;// x[ x_ptr ]
				double tmp1 = x[ x_ptr_n ];
				double tmp2 = x[ x_ptr_s - 1 ];
				for( int k = 0; k < n; k++ ) {
					final float xnk1 = x[ x_ptr_n - k - 1 ];// java
					final float xsk = x[ x_ptr_s + k ];// java
					C_first_row[ k ] -= x[ x_ptr_n ] * xnk1;
					C_last_row[ k ]  -= x[ x_ptr_s - 1 ] * xsk;
					final double Atmp = Af[ k ];
					tmp1 += xnk1 * Atmp;
					tmp2 += xsk * Atmp;
				}
				for( int k = 0; k <= n; k++ ) {
					CAf[ k ] -= tmp1 * x[ x_ptr_n - k ];
					CAb[ k ] -= tmp2 * x[ x_ptr_s + k - 1 ];
				}
			}
			double tmp1 = C_first_row[ n ];
			double tmp2 = C_last_row[ n ];
			for( int k = 0, nk1 = n - 1; k < n; k++, nk1-- ) {
				final double Atmp = Af[ k ];
				tmp1 += C_last_row[  nk1 ] * Atmp;
				tmp2 += C_first_row[ nk1 ] * Atmp;
			}
			CAf[ n + 1 ] = tmp1;
			CAb[ n + 1 ] = tmp2;

			/* Calculate nominator and denominator for the next order reflection (parcor) coefficient */
			double num = CAb[ n + 1 ];
			double nrg_b = CAb[ 0 ];
			double nrg_f = CAf[ 0 ];
			for( int k = 0; k < n; k++ ) {
				final double Atmp = Af[ k ];
				num   += CAb[ n - k ] * Atmp;
				nrg_b += CAb[ k + 1 ] * Atmp;
				nrg_f += CAf[ k + 1 ] * Atmp;
			}
			// silk_assert( nrg_f > 0.0 );
			// silk_assert( nrg_b > 0.0 );

			/* Calculate the next order reflection (parcor) coefficient */
			double rc = -2.0 * num / ( nrg_f + nrg_b );
			// silk_assert( rc > -1.0 && rc < 1.0 );

			/* Update inverse prediction gain */
			tmp1 = invGain * ( 1.0 - rc * rc );
			if( tmp1 <= minInvGain ) {
				/* Max prediction gain exceeded; set reflection coefficient such that max prediction gain is exactly hit */
				rc = Math.sqrt( 1.0 - (double)minInvGain / invGain );
				if( num > 0 ) {
					/* Ensure adjusted reflection coefficients has the original sign */
					rc = -rc;
				}
				invGain = minInvGain;
				reached_max_gain = true;
			} else {
				invGain = tmp1;
			}

			/* Update the AR coefficients */
			for( int k = 0, ke = (n + 1) >> 1, nk1 = n - 1; k < ke; k++, nk1-- ) {
				tmp1 = Af[ k ];
				tmp2 = Af[ nk1 ];
				Af[ k ]   = tmp1 + rc * tmp2;
				Af[ nk1 ] = tmp2 + rc * tmp1;
			}
			Af[ n ] = rc;

			if( reached_max_gain ) {
				/* Reached max prediction gain; set remaining coefficients to zero and exit loop */
				for( int k = n + 1; k < D; k++ ) {
					Af[ k ] = 0.0;
				}
				break;
			}

			/* Update C * Af and C * Ab */
			for( int k = 0, ke = n + 1, nk1 = ke; k <= ke; k++, nk1-- ) {
				tmp1 = CAf[ k ];
				CAf[ k ]    += rc * CAb[ nk1 ];
				CAb[ nk1  ] += rc * tmp1;
			}
		}

		double nrg_f;
		if( reached_max_gain ) {
			/* Convert to silk_float */
			for( int k = 0; k < D; k++ ) {
				A[ k ] = (float)( -Af[ k ] );
			}
			/* Subtract energy of preceding samples from C0 */
			for( int s = xoffset; s < xoffset_data_size; s += subfr_length ) {// java changed
				C0 -= silk_energy_FLP( x, s, D );
			}
			/* Approximate residual energy */
			nrg_f = C0 * invGain;
		} else {
			/* Compute residual energy and store coefficients as silk_float */
			nrg_f = CAf[ 0 ];
			double tmp1 = 1.0;
			for( int k = 0; k < D; k++ ) {
				final double Atmp = Af[ k ];
				nrg_f += CAf[ k + 1 ] * Atmp;
				tmp1  += Atmp * Atmp;
				A[ k ] = (float)(-Atmp);
			}
			nrg_f -= Jtuning_parameters.FIND_LPC_COND_FAC * C0 * tmp1;
		}

		/* Return residual energy */
		return (float)nrg_f;
	}
	// end burg_modified_FLP

	// start find_LPC_FLP
	/**
	 * LPC analysis
	 *
	 * @param psEncC I/O  Encoder state
	 * @param NLSF_Q15 O    NLSFs
	 * @param x I    Input signal
	 * @param minInvGain I    Inverse of max prediction gain
	 */
	final void silk_find_LPC_FLP(final short[] NLSF_Q15, final float x[], final float minInvGain)
	{
		final int subframe_length = this.subfr_length + this.predictLPCOrder;

		/* Default: No interpolation */
		this.indices.NLSFInterpCoef_Q2 = 4;

		/* Burg AR analysis for the full frame */
		final float a[] = new float[ Jdefine.MAX_LPC_ORDER ];
		float res_nrg = silk_burg_modified_FLP( a, x, 0, minInvGain, subframe_length, this.nb_subfr, this.predictLPCOrder );

		if( this.useInterpolatedNLSFs && ! this.first_frame_after_reset && this.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
			/* Optimal solution for last 10 ms; subtract residual energy here, as that's easier than        */
			/* adding it to the residual energy of the first 10 ms in each iteration of the search below    */
			final float a_tmp[] = new float[ Jdefine.MAX_LPC_ORDER ];
			res_nrg -= silk_burg_modified_FLP( a_tmp, x, ( Jdefine.MAX_NB_SUBFR / 2 ) * subframe_length, minInvGain,
																	subframe_length, Jdefine.MAX_NB_SUBFR / 2, this.predictLPCOrder );

			/* Convert to NLSFs */
			silk_A2NLSF_FLP( NLSF_Q15, a_tmp, this.predictLPCOrder );

			/* Search over interpolation indices to find the one with lowest residual energy */
			final short NLSF0_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
			final float LPC_res[] = new float[ Jdefine.MAX_FRAME_LENGTH + Jdefine.MAX_NB_SUBFR * Jdefine.MAX_LPC_ORDER ];
			float res_nrg_2nd = Float.MAX_VALUE;
			for( int k = 3; k >= 0; k-- ) {
				/* Interpolate NLSFs for first half */
				silk_interpolate( NLSF0_Q15, this.prev_NLSFq_Q15, NLSF_Q15, k, this.predictLPCOrder );

				/* Convert to LPC for residual energy evaluation */
				silk_NLSF2A_FLP( a_tmp, NLSF0_Q15, this.predictLPCOrder );//, this.arch );

				/* Calculate residual energy with LSF interpolation */
				silk_LPC_analysis_filter_FLP( LPC_res, a_tmp, x, 0, subframe_length << 1, this.predictLPCOrder );
				final float res_nrg_interp = (float)(
						silk_energy_FLP( LPC_res, this.predictLPCOrder,                subframe_length - this.predictLPCOrder ) +
						silk_energy_FLP( LPC_res, this.predictLPCOrder + subframe_length, subframe_length - this.predictLPCOrder ) );

				/* Determine whether current interpolated NLSFs are best so far */
				if( res_nrg_interp < res_nrg ) {
					/* Interpolation has lower residual energy */
					res_nrg = res_nrg_interp;
					this.indices.NLSFInterpCoef_Q2 = (byte)k;
				} else if( res_nrg_interp > res_nrg_2nd ) {
					/* No reason to continue iterating - residual energies will continue to climb */
					break;
				}
				res_nrg_2nd = res_nrg_interp;
			}
		}

		if( this.indices.NLSFInterpCoef_Q2 == 4 ) {
			/* NLSF interpolation is currently inactive, calculate NLSFs from full frame AR coefficients */
			silk_A2NLSF_FLP( NLSF_Q15, a, this.predictLPCOrder );
		}

		// celt_assert( psEncC.indices.NLSFInterpCoef_Q2 == 4 ||
		//		( psEncC.useInterpolatedNLSFs && !psEncC.first_frame_after_reset && psEncC.nb_subfr == MAX_NB_SUBFR ) );
	}
	// end find_LPC_FLP
}
