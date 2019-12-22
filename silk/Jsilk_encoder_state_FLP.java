package silk;

import celt.Jcelt_codec_API;
import celt.Jec_enc;

// structs_FLP.h

/** Encoder state FLP */
public class Jsilk_encoder_state_FLP extends Jenc_API {
	/** Encoder control FLP  */
	private static final class Jsilk_encoder_control_FLP {

		/* Prediction and coding parameters */
		private final float Gains[] = new float[ Jdefine.MAX_NB_SUBFR ];
		private final float PredCoef[][] = new float[ 2 ][ Jdefine.MAX_LPC_ORDER ];     /* holds interpolated and final coefficients */
		private final float LTPCoef[] = new float[Jdefine.LTP_ORDER * Jdefine.MAX_NB_SUBFR];
		@SuppressWarnings("unused")
		private float       LTP_scale;// FIXME LTP_scale never uses
		private final int   pitchL[] = new int[ Jdefine.MAX_NB_SUBFR ];

		/* Noise shaping parameters */
		private final float AR[]            = new float[ Jdefine.MAX_NB_SUBFR * Jdefine.MAX_SHAPE_LPC_ORDER ];
		private final float LF_MA_shp[]     = new float[ Jdefine.MAX_NB_SUBFR ];
		private final float LF_AR_shp[]     = new float[ Jdefine.MAX_NB_SUBFR ];
		private final float Tilt[]          = new float[ Jdefine.MAX_NB_SUBFR ];
		private final float HarmShapeGain[] = new float[ Jdefine.MAX_NB_SUBFR ];
		private float       Lambda;
		private float       input_quality;
		private float       coding_quality;

		/* Measures */
		private float       predGain;
		private float       LTPredCodGain;
		private final float ResNrg[] = new float[ Jdefine.MAX_NB_SUBFR ];             /* Residual energy per subframe */

		/* Parameters for CBR mode */
		private final int GainsUnq_Q16[] = new int[ Jdefine.MAX_NB_SUBFR ];
		private byte      lastGainIndexPrev;
	}
	/** Auxilary structure to exchange data on java */
	private static final class Jpitch_data_struct_aux {
		private short mLagIndex;
		private byte mContourIndex;
		private float mLTPCorr;
		//
		private Jpitch_data_struct_aux(/*final short lag, final byte contour, */final float corr) {
			mLagIndex = 0;// lagIndex = lag;
			mContourIndex = 0;// contourIndex = contour;
			mLTPCorr = corr;
		}
	}
	/** Auxilary structure to exchange data on java */
	private static final class Jgains_data_struct_aux {
		private byte mPeriodicityIndex;
		private int mSumLogGain;
		private float mPred_gain_dB;
		// extra fields to use in silk_VQ_WMat_EC
		/** res_nrg_Q15_subfr, O    residual energy  */
		private int res_nrg_Q15_subfr;
		/** rate_dist_Q7_subfr, O    best weighted quantization error + mu * rate  */
		private int rate_dist_Q7_subfr;
		/** gain_Q7 */
		private int gain_Q7;
		//
		private Jgains_data_struct_aux(final byte index, final int gain, final float pred_gain_dB) {
			mPeriodicityIndex = index;
			mSumLogGain = gain;
			mPred_gain_dB = pred_gain_dB;
		}
	}
	/** Common struct, shared with fixed-point code */
	public final Jsilk_encoder_state       sCmn = new Jsilk_encoder_state();
	/** Noise shaping state */
	final Jsilk_shape_state_FLP     sShape = new Jsilk_shape_state_FLP();

	/** Buffer for find pitch and noise shape analysis */
	final float x_buf[] = new float[ 2 * Jdefine.MAX_FRAME_LENGTH + Jdefine.LA_SHAPE_MAX ];
	/** Normalized correlation from pitch lag estimator */
	float       LTPCorr;
	//
	final void clear() {
		sCmn.clear();
		sShape.clear();
		final float[] fb = x_buf;
		for( int i = 0, end = fb.length; i < end; i++ ) {
			x_buf[i] = 0;
		}
		LTPCorr = 0;
	}
	final void copyFrom(final Jsilk_encoder_state_FLP s) {
		sCmn.copyFrom( s.sCmn );
		sShape.copyFrom( s.sShape );
		System.arraycopy( s.x_buf, 0, x_buf, 0, 2 * Jdefine.MAX_FRAME_LENGTH + Jdefine.LA_SHAPE_MAX );
		LTPCorr = s.LTPCorr;
	}

	// start init_encoder.c
	/**
	 * Initialize Silk Encoder state
	 *
	 * @param psEnc I/O  Pointer to Silk FIX encoder state
	 * @param arch I    Run-time architecture
	 * @return
	 */
	final int silk_init_encoder()//( Jsilk_encoder_state_FLP psEnc, final int arch)
	{
		int ret = 0;

		/* Clear the entire encoder state */
		// silk_memset( psEnc, 0, sizeof( silk_encoder_state_Fxx ) );
		clear();

		// psEnc.sCmn.arch = arch;
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java

		// this_sCmn.variable_HP_smth1_Q15 = (Jmacros.silk_lin2log( SILK_FIX_CONST( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ, 16 ) ) - ( 16 << 7 )) << 8;
		this_sCmn.variable_HP_smth1_Q15 = (Jmacros.silk_lin2log( ((int)( Jtuning_parameters.VARIABLE_HP_MIN_CUTOFF_HZ * (1 << 16) + 0.5)) ) - ( 16 << 7 )) << 8;
		this_sCmn.variable_HP_smth2_Q15 = this_sCmn.variable_HP_smth1_Q15;

		/* Used to deactivate LSF interpolation, pitch prediction */
		this_sCmn.first_frame_after_reset = true;

		/* Initialize Silk VAD */
		ret += this_sCmn.sVAD.silk_VAD_Init();

		return  ret;
	}
	// end init_encoder.c

	// start process_gains_FLP
	/**
	 * Processing of gains
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param condCoding I    The type of conditional coding to use
	 */
	private final void silk_process_gains_FLP(final Jsilk_encoder_control_FLP psEncCtrl, final int condCoding)
	{
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		final int nsubfr = this_sCmn.nb_subfr;// java
		final float[] gains = psEncCtrl.Gains;// java
		/* Gain reduction when LTP coding gain is high */
		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			final float s = 1.0f - 0.5f * (float)(1.0 / (1.0 + Math.exp( (double)-(0.25f * (psEncCtrl.LTPredCodGain - 12.0f)))));
			for( int k = 0; k < nsubfr; k++ ) {
				gains[ k ] *= s;
			}
		}

		/* Limit the quantized signal */
		final float InvMaxSqrVal = (float)( Math.pow( 2.0, (double)(0.33f * (21.0f - (float)this_sCmn.SNR_dB_Q7 * (1f / 128.0f)) ) ) / (float)this_sCmn.subfr_length );

		for( int k = 0; k < nsubfr; k++ ) {
			/* Soft limit on ratio residual energy and squared gains */
			float gain = gains[ k ];
			gain = (float)Math.sqrt( (double)(gain * gain + psEncCtrl.ResNrg[ k ] * InvMaxSqrVal) );
			gains[ k ] = gain <= 32767.0f ? gain : 32767.0f;
		}

		/* Prepare gains for noise shaping quantization */
		final int pGains_Q16[] = new int[ Jdefine.MAX_NB_SUBFR ];
		for( int k = 0; k < nsubfr; k++ ) {
			pGains_Q16[ k ] = (int)(gains[ k ] * 65536.0f);
		}

		/* Save unquantized gains and gain Index */
		System.arraycopy( pGains_Q16, 0, psEncCtrl.GainsUnq_Q16, 0, nsubfr );

		final Jsilk_shape_state_FLP psShapeSt = this.sShape;
		psEncCtrl.lastGainIndexPrev = psShapeSt.LastGainIndex;

		/* Quantize gains */
		psShapeSt.LastGainIndex = silk_gains_quant( this_sCmn.indices.GainsIndices, pGains_Q16,
							psShapeSt.LastGainIndex, condCoding == Jdefine.CODE_CONDITIONALLY, nsubfr );// java changed

		/* Overwrite unquantized gains with quantized gains and convert back to Q0 from Q16 */
		for( int k = 0; k < nsubfr; k++ ) {
			gains[ k ] = (float)pGains_Q16[ k ] / 65536.0f;
		}

		/* Set quantizer offset for voiced signals. Larger offset when LTP coding gain is low or tilt is high (ie low-pass) */
		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			if( psEncCtrl.LTPredCodGain + this_sCmn.input_tilt_Q15 * (1.0f / 32768.0f) > 1.0f ) {
				this_sCmn.indices.quantOffsetType = 0;
			} else {
				this_sCmn.indices.quantOffsetType = 1;
			}
		}

		/* Quantizer boundary adjustment */
		final float quant_offset = (float)Jtables_other.silk_Quantization_Offsets_Q10[ this_sCmn.indices.signalType >> 1 ][ this_sCmn.indices.quantOffsetType ] / 1024.0f;
		psEncCtrl.Lambda = Jtuning_parameters.LAMBDA_OFFSET
				+ Jtuning_parameters.LAMBDA_DELAYED_DECISIONS * this_sCmn.nStatesDelayedDecision
				+ Jtuning_parameters.LAMBDA_SPEECH_ACT        * this_sCmn.speech_activity_Q8 * (1.0f / 256.0f)
				+ Jtuning_parameters.LAMBDA_INPUT_QUALITY     * psEncCtrl.input_quality
				+ Jtuning_parameters.LAMBDA_CODING_QUALITY    * psEncCtrl.coding_quality
				+ Jtuning_parameters.LAMBDA_QUANT_OFFSET      * quant_offset;

		// silk_assert( psEncCtrl.Lambda > 0.0f );
		// silk_assert( psEncCtrl.Lambda < 2.0f );
	}
	// end process_gains_FLP

	// start LPC_inv_pred_gain_FLP
	// FIXME silk_LPC_inverse_pred_gain_FLP never used
	/**
	 * compute inverse of LPC prediction gain, and
	 * test if LPC coefficients are stable (all poles within unit circle)
	 * this code is based on silk_a2k_FLP()
	 *
	 * @param A I    prediction coefficients [order]
	 * @param aoffset java added
	 * @param order I    prediction order
	 * @return O    return inverse prediction gain, energy domain
	 */
	/* private static final float silk_LPC_inverse_pred_gain_FLP(final float[] A, final int aoffset, final int order)
	{// java aoffset is added
		// double     invGain, rc, rc_mult1, rc_mult2, tmp1, tmp2;// FIXME why using double?
		final float Atmp[] = new float[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];

		System.arraycopy( A, aoffset, Atmp, 0, order );

		float invGain = 1.0f;
		for( int k = order - 1; k > 0; k-- ) {
			final float rc = -Atmp[ k ];
			final float rc_mult1 = 1.0f - rc * rc;
			invGain *= rc_mult1;
			if( invGain * Jdefine.MAX_PREDICTION_POWER_GAIN < 1.0f ) {
				return 0.0f;
			}
			final float rc_mult2 = 1.0f / rc_mult1;
			for( int n = 0, ne = (k + 1) >> 1, i = k - 1; n < ne; n++, i-- ) {
				final float tmp1 = Atmp[ n ];
				final float tmp2 = Atmp[ i ];
				Atmp[ n ] = (float)( ( tmp1 - tmp2 * rc ) * rc_mult2 );
				Atmp[ i ] = (float)( ( tmp2 - tmp1 * rc ) * rc_mult2 );
			}
		}
		final float rc = -Atmp[ 0 ];
		final float rc_mult1 = 1.0f - rc * rc;
		invGain *= rc_mult1;
		if( invGain * Jdefine.MAX_PREDICTION_POWER_GAIN < 1.0f ) {
		    return 0.0f;
		}
		return invGain;
	} */
	// end LPC_inv_pred_gain_FLP

	// start VQ_WMat_EC.c
	// silk_VQ_WMat_EC_c
	/**
	 * Entropy constrained matrix-weighted VQ, hard-coded to 5-element vectors, for a single input data vector
	 *
	 * java changed: name is changed
	 * java changed: return long type. rate_dist_Q14 << 32 | gain_Q7
	 *
	 * @param ind O    index of best codebook vector
	 * @param indoffset I java an offset for the ind
	 * @param res_nrg_Q15 O    best residual energy
	 * @param rate_dist_Q8 O    best total bitrate
	 * @param gain_Q7 O    sum of absolute LTP coefficients
	 * @param aux O java replacement for the res_nrg_Q15, the rate_dist_Q8 and the gain_Q7
	 * @param XX_Q17 I    correlation matrix
	 * @param XX_Q17_offset I java an offset for the XX_Q17
	 * @param xX_Q17 I    correlation vector
	 * @param xX_Q17_offset I java an offset for the xX_Q17
	 * @param cb_Q7 I    codebook
	 * @param cb_gain_Q7 I    codebook effective gain
	 * @param cl_Q5 I    code length for each codebook vector
	 * @param subfr_len I    number of samples per subframe
	 * @param max_gain_Q7 I    maximum sum of absolute LTP coefficients
	 * @param L I    number of vectors in codebook
	 */
	private static final void silk_VQ_WMat_EC(final byte[] ind, final int indoffset,// java
			// final int[] res_nrg_Q15, final int[] rate_dist_Q8, final int[] gain_Q7,
			final Jgains_data_struct_aux aux,// java
			final int[] XX_Q17, final int XX_Q17_offset,// java
			final int[] xX_Q17, final int xX_Q17_offset,// java
			final byte[][] cb_Q7, final char[] cb_gain_Q7, final char[] cl_Q5,
			final int subfr_len, final int max_gain_Q7, final int L)
	{
		// final int neg_xX_Q24[] = new int[ 5 ];// java replaced by vars

		/* Negate and convert to new Q domain */
		final int neg_xX_Q24_0 = -( xX_Q17[ xX_Q17_offset + 0 ] << 7 );
		final int neg_xX_Q24_1 = -( xX_Q17[ xX_Q17_offset + 1 ] << 7 );
		final int neg_xX_Q24_2 = -( xX_Q17[ xX_Q17_offset + 2 ] << 7 );
		final int neg_xX_Q24_3 = -( xX_Q17[ xX_Q17_offset + 3 ] << 7 );
		final int neg_xX_Q24_4 = -( xX_Q17[ xX_Q17_offset + 4 ] << 7 );
		// java optimization
		final int XX_Q17_0 = XX_Q17[ XX_Q17_offset +  0 ];
		final int XX_Q17_1 = XX_Q17[ XX_Q17_offset +  1 ];
		final int XX_Q17_2 = XX_Q17[ XX_Q17_offset +  2 ];
		final int XX_Q17_3 = XX_Q17[ XX_Q17_offset +  3 ];
		final int XX_Q17_4 = XX_Q17[ XX_Q17_offset +  4 ];
		final int XX_Q17_6 = XX_Q17[ XX_Q17_offset +  6 ];
		final int XX_Q17_7 = XX_Q17[ XX_Q17_offset +  7 ];
		final int XX_Q17_8 = XX_Q17[ XX_Q17_offset +  8 ];
		final int XX_Q17_9 = XX_Q17[ XX_Q17_offset +  9 ];
		final int XX_Q17_12 = XX_Q17[ XX_Q17_offset + 12 ];
		final int XX_Q17_13 = XX_Q17[ XX_Q17_offset + 13 ];
		final int XX_Q17_14 = XX_Q17[ XX_Q17_offset + 14 ];
		final int XX_Q17_18 = XX_Q17[ XX_Q17_offset + 18 ];
		final int XX_Q17_19 = XX_Q17[ XX_Q17_offset + 19 ];
		final int XX_Q17_24 = XX_Q17[ XX_Q17_offset + 24 ];

		/* Loop over codebook */
		aux.rate_dist_Q7_subfr = Integer.MAX_VALUE;// FIXME why Q8? rate_dist_Q8
		aux.res_nrg_Q15_subfr = Integer.MAX_VALUE;// res_nrg_Q15
		// byte[] cb_row_Q7 = cb_Q7[0];// java changed to index
		/* In things go really bad, at least *ind is set to something safe. */
		ind[0] = 0;
		for( int k = 0; k < L; k++ ) {
			final byte[] cb_row_Q7 = cb_Q7[ k ];// java
			final int gain_tmp_Q7 = cb_gain_Q7[k];

			/* Weighted rate */
			/* Quantization error: 1 - 2 * xX * cb + cb' * XX * cb */
			int sum1_Q15 = (int)(1.001 * (1 << 15) + 0.5);

			/* Penalty for too large gain */
			int penalty = gain_tmp_Q7 - max_gain_Q7;// java
			penalty = ( penalty > 0 ? penalty : 0 );// java max
			penalty = ( penalty << 11 );

			/* first row of XX_Q17 */
			int sum2_Q24 = ( neg_xX_Q24_0 + XX_Q17_1 * cb_row_Q7[ 1 ] );
			sum2_Q24 += XX_Q17_2 * cb_row_Q7[ 2 ];
			sum2_Q24 += XX_Q17_3 * cb_row_Q7[ 3 ];
			sum2_Q24 += XX_Q17_4 * cb_row_Q7[ 4 ];
			sum2_Q24 <<= 1;
			sum2_Q24 += XX_Q17_0 * cb_row_Q7[ 0 ];
			sum1_Q15 += ((sum2_Q24 * (long)cb_row_Q7[ 0 ]) >> 16);

			/* second row of XX_Q17 */
			sum2_Q24 = ( neg_xX_Q24_1 + XX_Q17_7 * cb_row_Q7[ 2 ] );
			sum2_Q24 += XX_Q17_8 * cb_row_Q7[ 3 ];
			sum2_Q24 += XX_Q17_9 * cb_row_Q7[ 4 ];
			sum2_Q24 <<= 1;
			sum2_Q24 += XX_Q17_6 * cb_row_Q7[ 1 ];
			sum1_Q15 += ((sum2_Q24 * (long)cb_row_Q7[ 1 ]) >> 16);

			/* third row of XX_Q17 */
			sum2_Q24 = ( neg_xX_Q24_2 + XX_Q17_13 * cb_row_Q7[ 3 ] );
			sum2_Q24 += XX_Q17_14 * cb_row_Q7[ 4 ];
			sum2_Q24 <<= 1;
			sum2_Q24 += XX_Q17_12 * cb_row_Q7[ 2 ];
			sum1_Q15 += ((sum2_Q24 * (long)((short)cb_row_Q7[ 2 ])) >> 16);

			/* fourth row of XX_Q17 */
			sum2_Q24 = ( neg_xX_Q24_3 + XX_Q17_19 * cb_row_Q7[ 4 ] );
			sum2_Q24 <<= 1;
			sum2_Q24 += XX_Q17_18 * cb_row_Q7[ 3 ];
			sum1_Q15 += ((sum2_Q24 * (long)((short)cb_row_Q7[ 3 ])) >> 16);

			/* last row of XX_Q17 */
			sum2_Q24 = ( neg_xX_Q24_4 << 1 );
			sum2_Q24 += XX_Q17_24 * cb_row_Q7[ 4 ];
			sum1_Q15 += ((sum2_Q24 * (long)((short)cb_row_Q7[ 4 ])) >> 16);

			/* find best */
			if( sum1_Q15 >= 0 ) {
				/* Translate residual energy to bits using high-rate assumption (6 dB ==> 1 bit/sample) */
				final int bits_res_Q8 = ( subfr_len * Jmacros.silk_lin2log( sum1_Q15 + penalty) - (15 << 7) );
				/* In the following line we reduce the codelength component by half ("-1"); seems to slghtly improve quality */
				final int bits_tot_Q8 = ( bits_res_Q8 + (cl_Q5[ k ] << (3 - 1)) );
				if( bits_tot_Q8 <= aux.rate_dist_Q7_subfr ) {// java rate_dist_Q8[0] ) {
					aux.rate_dist_Q7_subfr = bits_tot_Q8;// java rate_dist_Q8[0] = bits_tot_Q8;
					aux.res_nrg_Q15_subfr = sum1_Q15 + penalty;// java res_nrg_Q15[0] = sum1_Q15 + penalty;
					ind[0] = (byte)k;
					aux.gain_Q7 = gain_tmp_Q7;// java gain_Q7[0] = gain_tmp_Q7;
				}
			}

			/* Go to next cbk vector */
			// cb_row_Q7 += Jdefine.LTP_ORDER;// java changed
		}
	}

	// end VQ_WMat_EC.c

	// start quant_LTP_gains.c
	/**
	 * java changed: aux to get and to return periodicity_index, sum_log_gain_Q7
	 *
	 * @param B_Q14 O  Quantized LTP gains
	 * @param cbk_index O    Codebook Index
	 * @param periodicity_index O    Periodicity Index
	 * @param sum_log_gain_Q7 I/O  Cumulative max prediction gain
	 * @param pred_gain_dB_Q7 O    LTP prediction gain
	 * @param aux I/O java replacement for the periodicity_index, the sum_log_gain_Q7 and the pred_gain_dB_Q7
	 * @param XX_Q17 I    Correlation matrix in Q18
	 * @param xX_Q17 I    Correlation vector in Q18
	 * @param subfr_len I    Number of samples per subframe
	 * @param nb_subfr I    Number of subframes
	 * @param arch I    Run-time architecture
	 */
	private static final void silk_quant_LTP_gains(final short B_Q14[/* MAX_NB_SUBFR * LTP_ORDER */], final byte cbk_index[/* MAX_NB_SUBFR */],
			// final byte[] periodicity_index, final int[] sum_log_gain_Q7, int[] pred_gain_dB_Q7,
			final Jgains_data_struct_aux aux,// java
			final int XX_Q17[/* MAX_NB_SUBFR*LTP_ORDER*LTP_ORDER */],
			final int xX_Q17[/* MAX_NB_SUBFR*LTP_ORDER */],
			final int subfr_len, final int nb_subfr)//, final int arch)
	{
		final byte temp_idx[] = new byte[ Jdefine.MAX_NB_SUBFR ];
		int res_nrg_Q15 = 0;// java = 0 to fix incorrect warning

		/***************************************************/
		/* iterate over different codebooks with different */
		/* rates/distortions, and choose best */
		/***************************************************/
		int min_rate_dist_Q7 = Integer.MAX_VALUE;
		int best_sum_log_gain_Q7 = 0;
		for( int k = 0; k < 3; k++ ) {
			/* Safety margin for pitch gain control, to take into account factors
			such as state rescaling/rewhitening. */
			// final int gain_safety = SILK_FIX_CONST( 0.4, 7 );
			final int gain_safety = (int)(0.4 * (1 << 7) + .5);

			final char[] cl_ptr_Q5     = Jtables_LTP.silk_LTP_gain_BITS_Q5_ptrs[ k ];
			final byte[][] cbk_ptr_Q7  = Jtables_LTP.silk_LTP_vq_ptrs_Q7[        k ];
			final char[] cbk_gain_ptr_Q7 = Jtables_LTP.silk_LTP_vq_gain_ptrs_Q7[ k ];// java uint8 to char
			final int cbk_size         = Jtables_LTP.silk_LTP_vq_sizes[          k ];

			/* Set up pointer to first subframe */
			int XX_Q17_ptr = 0;// XX_Q17[ XX_Q17_ptr ]
			int xX_Q17_ptr = 0;// xX_Q17[ xX_Q17_ptr ]

			res_nrg_Q15 = 0;
			int rate_dist_Q7 = 0;
			int sum_log_gain_tmp_Q7 = aux.mSumLogGain;// sum_log_gain_Q7[0];
			for( int j = 0; j < nb_subfr; j++ ) {
				// final int max_gain_Q7 = Jmacros.silk_log2lin( ( SILK_FIX_CONST( Jtuning_parameters.MAX_SUM_LOG_GAIN_DB / 6.0, 7 ) - sum_log_gain_tmp_Q7 )
				//		+ SILK_FIX_CONST( 7, 7 ) ) - gain_safety;
				final int max_gain_Q7 = Jmacros.silk_log2lin( ( ((int)( (Jtuning_parameters.MAX_SUM_LOG_GAIN_DB / 6.0) * (1 << 7) + 0.5)) - sum_log_gain_tmp_Q7 )
						+ ( 7 << 7 ) ) - gain_safety;
				// final int rate_dist_Q14_subfr, gain_Q7;// java changed to rate_gain
				silk_VQ_WMat_EC(
						temp_idx, j,         /* O    index of best codebook vector                           */
						// &res_nrg_Q15_subfr,     /* O    residual energy                                         */
						// &rate_dist_Q7_subfr,    /* O    best weighted quantization error + mu * rate            */
						// &gain_Q7,               /* O    sum of absolute LTP coefficients                        */
						aux,// java
						XX_Q17, XX_Q17_ptr,       /* I    correlation matrix                                      */
						xX_Q17, xX_Q17_ptr,      /* I    correlation vector                                      */
						cbk_ptr_Q7,             /* I    codebook                                                */
						cbk_gain_ptr_Q7,        /* I    codebook effective gains                                */
						cl_ptr_Q5,              /* I    code length for each codebook vector                    */
						subfr_len,              /* I    number of samples per subframe                          */
						max_gain_Q7,            /* I    maximum sum of absolute LTP coefficients                */
						cbk_size               /* I    number of vectors in codebook                           */
						// arch                    /* I    Run-time architecture                                   */
					);
				// final int res_nrg_Q15_subfr = aux.res_nrg_Q15_subfr;// java
				// final int rate_dist_Q7_subfr = aux.rate_dist_Q7_subfr;// java
				// final int gain_Q7 = aux.gain_Q7;// java FIXME gain_Q7 has no initial value neither here nor in silk_VQ_WMat_EC

				res_nrg_Q15 += aux.res_nrg_Q15_subfr;// java silk_ADD_POS_SAT32
				res_nrg_Q15 = (res_nrg_Q15 & 0x80000000) != 0 ? Integer.MAX_VALUE : res_nrg_Q15;
				rate_dist_Q7 += aux.rate_dist_Q7_subfr;// java silk_ADD_POS_SAT32
				rate_dist_Q7 = (rate_dist_Q7 & 0x80000000) != 0 ? Integer.MAX_VALUE : rate_dist_Q7;
				// sum_log_gain_tmp_Q7 = sum_log_gain_tmp_Q7 + Jmacros.silk_lin2log( gain_safety + gain_Q7 ) - SILK_FIX_CONST( 7, 7 );
				sum_log_gain_tmp_Q7 += Jmacros.silk_lin2log( gain_safety + aux.gain_Q7 ) - (int)(7 * (1 << 7) + 0.5);
				sum_log_gain_tmp_Q7 = (0 >= sum_log_gain_tmp_Q7 ? 0 : sum_log_gain_tmp_Q7);

				XX_Q17_ptr += Jdefine.LTP_ORDER * Jdefine.LTP_ORDER;
				xX_Q17_ptr += Jdefine.LTP_ORDER;
			}

			if( rate_dist_Q7 < min_rate_dist_Q7 ) {
				min_rate_dist_Q7 = rate_dist_Q7;
				aux.mPeriodicityIndex = (byte)k;// periodicity_index[0] = (byte)k;
				System.arraycopy( temp_idx, 0, cbk_index, 0, nb_subfr );
				best_sum_log_gain_Q7 = sum_log_gain_tmp_Q7;
			}
		}

		final byte[][] cbk_ptr_Q7 = Jtables_LTP.silk_LTP_vq_ptrs_Q7[ aux.mPeriodicityIndex ];// Jtables_LTP.silk_LTP_vq_ptrs_Q7[ periodicity_index[0] ];
		for( int j = 0; j < nb_subfr; j++ ) {
			final byte[] ptr = cbk_ptr_Q7[ cbk_index[ j ] ];// java
			for( int k = 0, jok = j * Jdefine.LTP_ORDER; k < Jdefine.LTP_ORDER; k++, jok++ ) {
				// B_Q14[ jok ] = (short)((int)cbk_ptr_Q7[ cbk_index[ j ] * Jdefine.LTP_ORDER + k ] << 7);// FIXME dirty way...
				B_Q14[ jok ] = (short)((int)ptr[ k ] << 7);
			}
		}

		if( nb_subfr == 2 ) {
			res_nrg_Q15 >>= 1;
		} else {
			res_nrg_Q15 >>= 2;
		}

		aux.mSumLogGain = best_sum_log_gain_Q7;// sum_log_gain_Q7[0] = best_sum_log_gain_Q7;
		//*pred_gain_dB_Q7 = (int)silk_SMULBB( -3, Jmacros.silk_lin2log( res_nrg_Q15 ) - ( 15 << 7 ) );
		aux.mPred_gain_dB = ( -3 * (Jmacros.silk_lin2log( res_nrg_Q15 ) - ( 15 << 7 )) );
	}
	// end quant_LTP_gains.c

	// start wrappers_FLP
	/* Wrappers. Calls flp / fix code */
	/**
	 * Floating-point Silk NSQ wrapper
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param psIndices I/O  Quantization indices
	 * @param psNSQ I/O  Noise Shaping Quantization state
	 * @param pulses O    Quantized pulse signal
	 * @param x I    Prefiltered input signal
	 * @param xoffset I java an offset for the x
	 */
	private final void silk_NSQ_wrapper_FLP(final Jsilk_encoder_control_FLP psEncCtrl,
			final JSideInfoIndices psIndices, final Jsilk_nsq_state psNSQ, final byte pulses[],
			final float x[], int xoffset)
	{
		final short x16[] = new short[ Jdefine.MAX_FRAME_LENGTH ];
		final int Gains_Q16[] = new int[ Jdefine.MAX_NB_SUBFR ];
		final short PredCoef_Q12[][] = new short[ 2 ][ Jdefine.MAX_LPC_ORDER ];
		final short LTPCoef_Q14[] = new short[ Jdefine.LTP_ORDER * Jdefine.MAX_NB_SUBFR ];

		/* Noise shaping parameters */
		final short AR_Q13[] = new short[ Jdefine.MAX_NB_SUBFR * Jdefine.MAX_SHAPE_LPC_ORDER ];
		final int LF_shp_Q14[] = new int[ Jdefine.MAX_NB_SUBFR ]; /* Packs two int16 coefficients per int32 value */
		final int Tilt_Q14[] = new int[ Jdefine.MAX_NB_SUBFR ];
		final int HarmShapeGain_Q14[] = new int[ Jdefine.MAX_NB_SUBFR ];

		/* Convert control struct to fix control struct */
		/* Noise shape parameters */
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		final int nb_subfr = this_sCmn.nb_subfr;// java
		float dim1[] = psEncCtrl.AR;// java
		for( int i = 0, ie = nb_subfr * Jdefine.MAX_SHAPE_LPC_ORDER; i < ie; i += Jdefine.MAX_SHAPE_LPC_ORDER ) {
			for( int j = i, je = j + this_sCmn.shapingLPCOrder; j < je; j++ ) {
				AR_Q13[ j ] = (short)Math.floor( (double)(.5f + dim1[ j ] * 8192.0f) );// XXX here and later can use direct casting
			}
		}

		for( int i = 0; i < nb_subfr; i++ ) {
			LF_shp_Q14[ i ] = ( ((int)Math.floor( (double)(.5f + psEncCtrl.LF_AR_shp[ i ]     * 16384.0f) )) << 16 ) |// can be using direct casting
								((int)Math.floor( (double)(.5f + psEncCtrl.LF_MA_shp[ i ]     * 16384.0f) ) & 0xffff);// can be using direct casting
			Tilt_Q14[ i ]   =        (int)Math.floor( (double)(.5f + psEncCtrl.Tilt[ i ]          * 16384.0f) );// can be using direct casting
			HarmShapeGain_Q14[ i ] = (int)Math.floor( (double)(.5f + psEncCtrl.HarmShapeGain[ i ] * 16384.0f) );// can be using direct casting
		}
		final int Lambda_Q10 = (int)Math.floor( (double)(.5f + psEncCtrl.Lambda * 1024.0f) );

		/* prediction and coding parameters */
		dim1 = psEncCtrl.LTPCoef;// java
		for( int i = 0, ie = nb_subfr * Jdefine.LTP_ORDER; i < ie; i++ ) {
			LTPCoef_Q14[ i ] = (short)Math.floor( (double)(.5f + dim1[ i ] * 16384.0f) );// can be using direct casting
		}

		for( int j = 0, ie = this_sCmn.predictLPCOrder; j < 2; j++ ) {
			dim1 = psEncCtrl.PredCoef[ j ];// java
			final short[] p_j = PredCoef_Q12[ j ];// java
			for( int i = 0; i < ie; i++ ) {
				p_j[ i ] = (short)Math.floor( (double)(.5f + dim1[ i ] * 4096.0f) );// can be using direct casting
			}
		}
		dim1 = psEncCtrl.Gains;// java
		for( int i = 0; i < nb_subfr; i++ ) {
			Gains_Q16[ i ] = (int)Math.floor( (double)(.5f + dim1[ i ] * 65536.0f) );// can be using direct casting
			// silk_assert( Gains_Q16[ i ] > 0 );
		}

		final int LTP_scale_Q14 = ( psIndices.signalType == Jdefine.TYPE_VOICED ) ?
				(int)Jtables_other.silk_LTPScales_table_Q14[ psIndices.LTP_scaleIndex ]
				:
				0;

		/* Convert input to fix */
		for( int i = 0, ie = this_sCmn.frame_length; i < ie; i++ ) {
			x16[ i ] = (short)Math.floor( (double)(.5f + 8.0f * x[ xoffset++ ]) );// can use direct casting
		}

		/* Call NSQ */
		if( this_sCmn.nStatesDelayedDecision > 1 || this_sCmn.warping_Q16 > 0 ) {
			psNSQ.silk_NSQ_del_dec( this_sCmn, psIndices, x16, pulses, PredCoef_Q12/*[ 0 ]*/, LTPCoef_Q14,
					AR_Q13, HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, psEncCtrl.pitchL, Lambda_Q10, LTP_scale_Q14 );//, psEnc.sCmn.arch );
		} else {
			psNSQ.silk_NSQ( this_sCmn, psIndices, x16, pulses, PredCoef_Q12/*[ 0 ]*/, LTPCoef_Q14,
					AR_Q13, HarmShapeGain_Q14, Tilt_Q14, LF_shp_Q14, Gains_Q16, psEncCtrl.pitchL, Lambda_Q10, LTP_scale_Q14 );//, psEnc.sCmn.arch );
		}
	}

	/**
	 * Floating-point Silk LTP quantiation wrapper
	 *
	 * java changed: struct aux to get and to return periodicity_index, sum_log_gain_Q7
	 *
	 * @param B O  Quantized LTP gains
	 * @param cbk_index O    Codebook index
	 * @param periodicity_index O    Periodicity index
	 * @param sum_log_gain_Q7 I/O  Cumulative max prediction gain
	 * @param pred_gain_dB O    LTP prediction gain
	 * @param aux I/O java replacement for the periodicity_index, the sum_log_gain_Q7 and the pred_gain_dB
	 * @param XX I    Correlation matrix
	 * @param xX I    Correlation vector
	 * @param subfr_len I    Number of samples per subframe
	 * @param nb_subfr I    Number of subframes
	 * @param arch I    Run-time architecture
	 */
	private static final void silk_quant_LTP_gains_FLP(final float B[/* MAX_NB_SUBFR * LTP_ORDER */], final byte cbk_index[/* MAX_NB_SUBFR */],
			// final byte[] periodicity_index, final int[] sum_log_gain_Q7, final float[] pred_gain_dB,
			final Jgains_data_struct_aux aux,// java
			final float XX[/* MAX_NB_SUBFR * LTP_ORDER * LTP_ORDER */],
		    final float xX[/* MAX_NB_SUBFR * LTP_ORDER */],
		    final int subfr_len,
			final int nb_subfr)//, final int arch)
	{
		final short B_Q14[] = new short[ Jdefine.MAX_NB_SUBFR * Jdefine.LTP_ORDER ];
		final int XX_Q17[] = new int[ Jdefine.MAX_NB_SUBFR * Jdefine.LTP_ORDER * Jdefine.LTP_ORDER ];
		final int xX_Q17[] = new int[ Jdefine.MAX_NB_SUBFR * Jdefine.LTP_ORDER ];

		for( int i = 0; i < nb_subfr * Jdefine.LTP_ORDER * Jdefine.LTP_ORDER; i++ ) {
			XX_Q17[ i ] = (int)(Math.floor( (double)(.5f + ( XX[ i ] * 131072.0f )) ));// can use direct casting
		}

		silk_quant_LTP_gains( B_Q14, cbk_index,
				// periodicity_index, sum_log_gain_Q7, &pred_gain_dB_Q7,// FIXME why need pred_gain_dB_Q7?
				aux,// java
				XX_Q17, xX_Q17, subfr_len, nb_subfr );//, arch );

		for( int i = 0; i < nb_subfr * Jdefine.LTP_ORDER; i++ ) {
			B[ i ] = (float)B_Q14[ i ] * ( 1.0f / 16384.0f );
		}

		// pred_gain_dB[0] = (float)pred_gain_dB_Q7 * ( 1.0f / 128.0f );
		aux.mPred_gain_dB *= ( 1.0f / 128.0f );// java
	}
	// end wrappers_FLP

	// start corrMatrix_FLP
	/**
	 * Calculates correlation vector X'*t
	 *
	 * @param x I    x vector [L+order-1] used to create X
	 * @param xoffset I java an offset for the x
	 * @param t I    Target vector [L]
	 * @param toffset I java an offset for the t
	 * @param L I    Length of vecors
	 * @param Order I    Max lag for correlation
	 * @param Xt O    X'*t correlation vector [order]
	 * @param Xtoffset I java an offset for the Xt
	 */
	private static final void silk_corrVector_FLP(final float[] x, final int xoffset,// java
			final float[] t, final int toffset,// java
			final int L, int Order,
			final float[] Xt, int Xtoffset)
	{
		int ptr1 = xoffset + Order - 1;// Points to first sample of column 0 of X: X[:,0]. java x[ ptr1 ]
		Order += Xtoffset;// java
		// for( int lag = 0; lag < Order; lag++ ) {
		while( Xtoffset < Order ) {// java
			/* Calculate X[:,lag]'*t */
			Xt[ Xtoffset++ ] = (float)silk_inner_product_FLP( x, ptr1, t, toffset, L );
			ptr1--; /* Next column of X */
		}
	}
	/**
	 * Calculates correlation matrix X'*X
	 *
	 * @param x I    x vector [ L+order-1 ] used to create X
	 * @param xoffset I java an offset for the x
	 * @param L I    Length of vectors
	 * @param Order I    Max lag for correlation
	 * @param XX O    X'*X correlation matrix [order x order]
	 * @param Xoffset I java an offset for the XX
	 */
	private static final void silk_corrMatrix_FLP(final float[] x, final int xoffset, final int L, final int Order, final float[] XX, final int Xoffset)
	{// java xoffset, Xoffset are added
		final int ptr1 = xoffset + Order - 1;// First sample of column 0 of X. java x[ ptr1 ]
		// java double changed to float to avoid extra casting
		float energy = (float)silk_energy_FLP( x, ptr1, L );  /* X[:,0]'*X[:,0] */
		XX[Xoffset] = energy;
		for( int j = 1; j < Order; j++ ) {
			/* Calculate X[:,j]'*X[:,j] */
			final int pj = ptr1 - j;// java
			float v1 = x[ pj ];// java
			v1 *= v1;
			float v2 = x[ pj + L ];// java
			v2 *= v2;
			//energy += x[ ptr1 -j ] * x[ ptr1 -j ] - x[ ptr1 + L - j ] * x[ ptr1 + L - j ];
			energy += (v1 - v2);
			XX[ Xoffset + j * Order + j ] = energy;
		}

		int ptr2 = xoffset + Order - 2;// First sample of column 1 of X. java x[ ptr1 ]
		for( int lag = 1; lag < Order; lag++ ) {
			/* Calculate X[:,0]'*X[:,lag] */
			energy = (float)silk_inner_product_FLP( x, ptr1, x, ptr2, L );
			final int lag_order = lag * Order;// java
			XX[ Xoffset + lag_order ] = energy;
			XX[ Xoffset + lag ] = energy;
			/* Calculate X[:,j]'*X[:,j + lag] */
			for( int j = 1, je = ( Order - lag ); j < je; j++ ) {
				final int pj1 = ptr1 - j;// java
				final int pj2 = ptr2 - j;// java
				energy += x[ pj1 ] * x[ pj2 ] - x[ pj1 + L ] * x[ pj2 + L ];
				final int xjo = Xoffset + j * Order + j;// java
				XX[ xjo + lag_order ] = energy;
				XX[ xjo + lag ] = energy;
			}
			ptr2--; /* Next column of X */
		}
	}
	// end corrMatrix_FLP

	// start regularize_correlations_FLP
	// FIXME silk_regularize_correlations_FLP never used
	/*
	 * Add noise to matrix diagonal
	 *
	 * @param XX I/O  Correlation matrices
	 * @param Xoffset I java an offset for the XX
	 * @param xx I/O  Correlation values
	 * @param xoffset I java an offset for the xx
	 * @param noise I    Noise energy to add
	 * @param D I    Dimension of XX
	 */
	/* private static final void silk_regularize_correlations_FLP(final float[] XX, int Xoffset, final float[] xx, final int xoffset, final float noise, int D)
	{// java xoffset, Xoffset are added
		// for( int i = 0; i < D; i++ ) {
		//	XX[ Xoffset + i * D + i ] += noise;
		// }
		final int d1 = D + 1;
		D *= d1;
		D += Xoffset;
		for( ; Xoffset < D; Xoffset += d1 ) {
			XX[ Xoffset ] += noise;
		}
		xx[ xoffset ] += noise;
	} */
	// end regularize_correlations_FLP

	// start residual_energy_FLP
	// FIXME silk_residual_energy_covar_FLP is never used
	// private static final int MAX_ITERATIONS_RESIDUAL_NRG = 10;
	// private static final float REGULARIZATION_FACTOR     = 1e-8f;

	/* Residual energy: nrg = wxx - 2 * wXx * c + c' * wXX * c */
	/**
	 *
	 * @param c I    Filter coefficients
	 * @param coffset I java an offset for the c
	 * @param wXX I/O  Weighted correlation matrix, reg. out
	 * @param Xoffset I java an offset for the wXX
	 * @param wXx I    Weighted correlation vector
	 * @param wxx I    Weighted correlation value
	 * @param D I    Dimension
	 * @return O    Weighted residual energy
	 */
	/* private static final float silk_residual_energy_covar_FLP(final float [] c, final int coffset,
			final float[] wXX, final int Xoffset,
			final float[] wXx, final float wxx, final int D)
	{
		float nrg = 0.0f;

		// Safety checks
		// celt_assert( D >= 0 );

		float regularization = REGULARIZATION_FACTOR * ( wXX[ Xoffset ] + wXX[ Xoffset + D * D - 1 ] );
		final int d1 = D + 1;// java
		final int xd = D * d1 + Xoffset;// java
		int k;
		for( k = 0; k < MAX_ITERATIONS_RESIDUAL_NRG; k++ ) {
			nrg = wxx;

			float tmp = 0.0f;
			for( int i = 0; i < D; i++ ) {
				tmp += wXx[ i ] * c[ coffset + i ];
			}
			nrg -= 2.0f * tmp;

			// compute c' * wXX * c, assuming wXX is symmetric
			for( int i = 0, xi = Xoffset; i < D; i++, xi++ ) {
				tmp = 0.0f;
				for( int j = i + 1; j < D; j++ ) {
					tmp += wXX[ xi + j * D ] * c[ coffset + j ];
				}
				final float v = c[ coffset + i ];// java
				nrg += v * ( 2.0f * tmp + wXX[ xi + i * D ] * v );
			}
			if( nrg > 0 ) {
				break;
			} else {
				// Add white noise
				// for( int i = 0; i < D; i++ ) {
				//    wXX[ Xoffset + i + i * D ] +=  regularization;
				// }
				for( int i = Xoffset; i < xd; i += d1 ) {
					wXX[ i ] += regularization;
				}
				// Increase noise for next run
				regularization *= 2.0f;
			}
		}
		if( k == MAX_ITERATIONS_RESIDUAL_NRG ) {
			// silk_assert( nrg == 0 );
			nrg = 1.0f;
		}

		return nrg;
	} */

	/**
	 * Calculates residual energies of input subframes where all subframes have LPC_order
	 * of preceding samples
	 *
	 * @param nrgs O    Residual energy per subframe
	 * @param x I    Input signal
	 * @param a I    AR coefs for each frame half
	 * @param gains I    Quantization gains
	 * @param subfr_length I    Subframe length
	 * @param nb_subfr I    number of subframes
	 * @param LPC_order I    LPC order
	 */
	private static final void silk_residual_energy_FLP(final float nrgs[/* MAX_NB_SUBFR */], final float x[],
			final float a[/* 2 */][/* MAX_LPC_ORDER */], final float gains[],
			final int subfr_length, final int nb_subfr, final int LPC_order
		)
	{
		final float LPC_res[] = new float[ ( Jdefine.MAX_FRAME_LENGTH + Jdefine.MAX_NB_SUBFR * Jdefine.MAX_LPC_ORDER ) / 2 ];

		final int LPC_res_ptr = LPC_order;// LPC_res[ LPC_res_ptr ]
		final int shift = LPC_order + subfr_length;
		final int shift2 = shift << 1;// java

		/* Filter input to create the LPC residual for each frame half, and measure subframe energies */
		silk_LPC_analysis_filter_FLP( LPC_res, a[ 0 ], x, 0 * shift, shift2, LPC_order );
		nrgs[ 0 ] = (float)( gains[ 0 ] * gains[ 0 ] * silk_energy_FLP( LPC_res, LPC_res_ptr /* + 0 * shift */, subfr_length ) );
		nrgs[ 1 ] = (float)( gains[ 1 ] * gains[ 1 ] * silk_energy_FLP( LPC_res, LPC_res_ptr + /* 1 * */shift, subfr_length ) );

		if( nb_subfr == Jdefine.MAX_NB_SUBFR ) {
			silk_LPC_analysis_filter_FLP( LPC_res, a[ 1 ], x, shift2, shift2, LPC_order );
			nrgs[ 2 ] = (float)( gains[ 2 ] * gains[ 2 ] * silk_energy_FLP( LPC_res, LPC_res_ptr /* + 0 * shift */, subfr_length ) );
			nrgs[ 3 ] = (float)( gains[ 3 ] * gains[ 3 ] * silk_energy_FLP( LPC_res, LPC_res_ptr + /* 1 * */ shift, subfr_length ) );
		}
	}
	// end residual_energy_FLP

	// start scale_vector_FLP
	/**
	 * multiply a vector by a constant
	 * @param data1
	 * @param doffset
	 * @param gain
	 * @param dataSize
	 */
	private static final void silk_scale_vector_FLP(final float[] data1, int doffset, final float gain, int dataSize)
	{// java doffset is added
		/* 4x unrolled loop */
		int dataSize4 = dataSize & 0xFFFC;
		dataSize4 += doffset;// java
		dataSize += doffset;// java
		while( doffset < dataSize4 ) {// java is changed
			data1[ doffset++ ] *= gain;
			data1[ doffset++ ] *= gain;
			data1[ doffset++ ] *= gain;
			data1[ doffset++ ] *= gain;
		}

		/* any remaining elements */
		while( doffset < dataSize ) {// java is changed
			data1[ doffset++ ] *= gain;
		}
	}
	// end scale_vector_FLP

	// start LTP_analysis_filter_FLP
	/**
	 *
	 * @param LTP_res O    LTP res MAX_NB_SUBFR*(pre_lgth+subfr_lngth)
	 * @param x I    Input signal, with preceding samples
	 * @param xoffset I java an offset for the x
	 * @param B I    LTP coefficients for each subframe
	 * @param pitchL I    Pitch lags
	 * @param invGains I    Inverse quantization gains
	 * @param subfr_length I    Length of each subframe
	 * @param nb_subfr I    number of subframes
	 * @param pre_length I    Preceding samples for each subframe
	 */
	private static final void silk_LTP_analysis_filter_FLP(final float[] LTP_res,
			final float[] x, final int xoffset,// java
			final float B[/* LTP_ORDER * MAX_NB_SUBFR */],
			final int pitchL[/*   MAX_NB_SUBFR */], final float invGains[/* MAX_NB_SUBFR */],
			final int subfr_length, final int nb_subfr, final int pre_length
		)
	{
		final float Btmp[] = new float[ Jdefine.LTP_ORDER ];

		final int sum_length = subfr_length + pre_length;// java
		int x_ptr = xoffset;// x[ x_ptr ]
		int LTP_res_ptr = 0;// LTP_res[ LTP_res_ptr ]
		for( int  k = 0; k < nb_subfr; k++ ) {
			int x_lag_ptr = x_ptr - pitchL[ k ];// x[ x_lag_ptr ]
			final float inv_gain = invGains[ k ];
			for( int i = 0; i < Jdefine.LTP_ORDER; i++ ) {
				Btmp[ i ] = B[ k * Jdefine.LTP_ORDER + i ];
			}

			/* LTP analysis FIR filter */
			for( int i = 0; i < sum_length; i++ ) {
				// LTP_res[ LTP_res_ptr + i ] = x[ x_ptr + i ];
				float res = x[ x_ptr + i ];// java
				/* Subtract long-term prediction */
				for( int j = 0, pj = x_lag_ptr + Jdefine.LTP_ORDER / 2; j < Jdefine.LTP_ORDER; j++, pj-- ) {
					// LTP_res[ LTP_res_ptr + i ] -= Btmp[ j ] * x[ x_lag_ptr + Jdefine.LTP_ORDER / 2 - j ];
					res -= Btmp[ j ] * x[ pj ];// java
				}
				// LTP_res[ LTP_res_ptr + i ] *= inv_gain;
				res *= inv_gain;// java
				LTP_res[ LTP_res_ptr + i ] = res;// java
				x_lag_ptr++;
			}

			/* Update pointers */
			LTP_res_ptr += sum_length;
			x_ptr       += subfr_length;
		}
	}
	// end LTP_analysis_filter_FLP

	// start LTP_scale_ctrl_FLP
	/**
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param condCoding I    The type of conditional coding to use
	 */
	private final void silk_LTP_scale_ctrl_FLP(final Jsilk_encoder_control_FLP psEncCtrl, final int condCoding)
	{
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		if( condCoding == Jdefine.CODE_INDEPENDENTLY ) {
			/* Only scale if first frame in packet */
			final int round_loss = this_sCmn.PacketLoss_perc + this_sCmn.nFramesPerPacket;
			//this.sCmn.indices.LTP_scaleIndex = (byte)JSigProc_FIX.silk_LIMIT( round_loss * psEncCtrl.LTPredCodGain * 0.1f, 0.0f, 2.0f );
			float a = (float)round_loss * psEncCtrl.LTPredCodGain * 0.1f;
			a = (a > 2.0f ? 2.0f : (a < 0.0f ? 0.0f : a));
			this_sCmn.indices.LTP_scaleIndex = (byte)a;
		} else {
		/* Default is minimum scaling */
			this_sCmn.indices.LTP_scaleIndex = 0;
		}

		psEncCtrl.LTP_scale = (float)Jtables_other.silk_LTPScales_table_Q14[ this_sCmn.indices.LTP_scaleIndex ] / 16384.0f;
	}
	// end LTP_scale_ctrl_FLP

	// start scale_copy_vector_FLP
	/**
	 * copy and multiply a vector by a constant
	 *
	 * @param data_out
	 * @param outoffset
	 * @param data_in
	 * @param inoffset
	 * @param gain
	 * @param dataSize
	 */
	private static final void silk_scale_copy_vector_FLP(final float[] data_out, int outoffset,// java
			final float[] data_in, int inoffset,// java
			final float gain, int dataSize)
	{
		/* 4x unrolled loop */
		int dataSize4 = dataSize & 0xFFFC;
		dataSize4 += outoffset;// java
		dataSize += outoffset;// java
		while( outoffset < dataSize4 ) {// java changed
			data_out[ outoffset++ ] = gain * data_in[ inoffset++ ];
			data_out[ outoffset++ ] = gain * data_in[ inoffset++ ];
			data_out[ outoffset++ ] = gain * data_in[ inoffset++ ];
			data_out[ outoffset++ ] = gain * data_in[ inoffset++ ];
		}

		/* any remaining elements */
		while( outoffset < dataSize ) {// java changed
			data_out[ outoffset++ ] = gain * data_in[ inoffset++ ];
		}
	}
	// end scale_copy_vector_FLP


	// start sort_FLP
	/**
	 * Insertion sort (fast for already almost sorted arrays):
	 * Best case:  O(n)   for an already sorted array
	 * Worst case: O(n^2) for an inversely sorted array
	 *
	 * @param a I/O  Unsorted / Sorted vector
	 * @param aoffset I java an offset for the a
	 * @param idx O    Index vector for the sorted elements
	 * @param L I    Vector length
	 * @param K I    Number of correctly sorted positions
	 */
	private static final void silk_insertion_sort_decreasing_FLP(final float[] a, final int aoffset, final int[] idx, final int L, final int K)
	{// java aoffset is added
		/* Safety checks */
		// celt_assert( K >  0 );
		// celt_assert( L >  0 );
		// celt_assert( L >= K );

		/* Write start indices in index vector */
		for( int i = 0; i < K; i++ ) {
			idx[ i ] = i;
		}

		/* Sort vector elements by value, decreasing order */
		for( int i = 1; i < K; i++ ) {
			final float value = a[ aoffset + i ];
			int j;
			for( j = i - 1; ( j >= 0 ) && ( value > a[ aoffset + j ] ); j-- ) {
				final int j1 = j + 1;// java
				a[ aoffset + j1 ] = a[ aoffset + j ];      /* Shift value */
				idx[ j1 ] = idx[ j ];    /* Shift index */
			}
			final int j1 = j + 1;// java
			a[ aoffset + j1 ] = value;   /* Write value */
			idx[ j1 ] = i;       /* Write index */
		}

		/* If less than L values are asked check the remaining values,      */
		/* but only spend CPU to ensure that the K first values are correct */
		final int ak1 = aoffset + K - 1;// java
		for( int i = K; i < L; i++ ) {
			final float value = a[ aoffset + i ];
			if( value > a[ ak1 ] ) {
				int j;
				for( j = K - 2; ( j >= 0 ) && ( value > a[ aoffset + j ] ); j-- ) {
					final int j1 = j + 1;// java
					a[ aoffset + j1 ] = a[ aoffset + j ];      /* Shift value */
					idx[ j1 ] = idx[ j ];    /* Shift index */
				}
				final int j1 = j + 1;// java
				a[ aoffset + j1 ] = value;   /* Write value */
				idx[ j1 ] = i;       /* Write index */
			}
		}
	}
	// end sort_FLP

	// start resampler_down2.c
	/**
	 * Downsample by a factor 2
	 *
	 * @param S I/O  State vector [ 2 ]
	 * @param out O    Output signal [ floor(len/2) ]
	 * @param in I    Input signal [ len ]
	 * @param inLen I    Number of input samples
	 */
	static final void silk_resampler_down2(final int[] S, final short[] out, final short[] in, final int inLen)
	{
		final int len2 = ( inLen >> 1 );

		// celt_assert( silk_resampler_down2_0 > 0 );
		// celt_assert( silk_resampler_down2_1 < 0 );

		/* Internal variables and state are in Q10 format */
		for( int k = 0; k < len2; k++ ) {
			int k2 = k << 1;// java
			/* Convert to Q10 */
			int in32 = ( (int)in[ k2 ] << 10 );

			/* All-pass section for even input sample */
			int Y      = ( in32 - S[ 0 ] );
			int X      = Y + (int)((Y * (long)Jresampler_rom.silk_resampler_down2_1) >> 16);
			int out32  = ( S[ 0 ] + X );
			S[ 0 ] = ( in32 + X );

			/* Convert to Q10 */
			in32 = ( (int)in[ ++k2 ] << 10 );

			/* All-pass section for odd input sample, and add to output of previous section */
			Y      = ( in32 - S[ 1 ] );
			X      = (int)((Y * (long)Jresampler_rom.silk_resampler_down2_0) >> 16);
			out32  = ( out32 + S[ 1 ] );
			out32  = ( out32 + X );
			S[ 1 ] = ( in32 + X );

			/* Add, convert back to int16 and store to output */
			out32 = JSigProc_FIX.silk_RSHIFT_ROUND( out32, 11 );// java
			out[ k ] = (short)(out32 > Short.MAX_VALUE ? Short.MAX_VALUE : (out32 < Short.MIN_VALUE ? Short.MIN_VALUE : out32));
		}
	}
	// end resampler_down2.c

	// start resampler_down2_3.c
	private static final int ORDER_FIR = 4;

	/**
	 * Downsample by a factor 2/3, low quality
	 *
	 * @param S I/O  State vector [ 6 ]
	 * @param out O    Output signal [ floor(2*inLen/3) ]
	 * @param in I    Input signal [ inLen ]
	 * @param inLen I    Number of input samples
	 */
	private static final void silk_resampler_down2_3(final int[] S, final short[] out, final short[] in, int inLen)
	{
		int outoffset = 0;// java out[ outoffset ]
		int inoffset = 0;// java out[ inoffset ]
		// SAVE_STACK;

		final int[] buf = new int[Jsilk_resampler_state_struct.RESAMPLER_MAX_BATCH_SIZE_IN + ORDER_FIR];

		/* Copy buffered samples to start of buffer */
		System.arraycopy( S, 0, buf, 0, ORDER_FIR );

		/* Iterate over blocks of frameSizeIn input samples */
		int nSamplesIn;
		while( true ) {
			nSamplesIn = inLen < Jsilk_resampler_state_struct.RESAMPLER_MAX_BATCH_SIZE_IN ? inLen : Jsilk_resampler_state_struct.RESAMPLER_MAX_BATCH_SIZE_IN ;

			/* Second-order AR filter (output in Q8) */
			silk_resampler_private_AR2( S, ORDER_FIR, buf, ORDER_FIR, in, inoffset, Jresampler_rom.silk_Resampler_2_3_COEFS_LQ, nSamplesIn );

			/* Interpolate filtered signal */
			int buf_ptr = 0;// buf[ buf_ptr ]
			int counter = nSamplesIn;
			while( counter > 2 ) {
				/* Inner product */
				int res_Q6 = (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 2 ]) >> 16);// + 0
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 3 ]) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 5 ]) >> 16);
				res_Q6 += (int)((buf[ buf_ptr   ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 4 ]) >> 16);// + 3

				/* Scale down, saturate and store in output array */
				res_Q6 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q6, 6 );// java
				out[outoffset++] = (short)(res_Q6 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q6 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q6));

				buf_ptr -= 3 - 1;
				res_Q6 = ( (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 4 ]) >> 16) );// + 1
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 5 ]) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 3 ]) >> 16);
				res_Q6 += (int)((buf[ buf_ptr   ] * (long)Jresampler_rom.silk_Resampler_2_3_COEFS_LQ[ 2 ]) >> 16);// + 4

				/* Scale down, saturate and store in output array */
				res_Q6 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q6, 6 );// java
				out[outoffset++] = (short)(res_Q6 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q6 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q6));

				buf_ptr--;// buf_ptr += 3;
				counter -= 3;
			}

			inoffset += nSamplesIn;
			inLen -= nSamplesIn;

			if( inLen > 0 ) {
				/* More iterations to do; copy last part of filtered signal to beginning of buffer */
				System.arraycopy( buf, nSamplesIn, buf, 0, ORDER_FIR );
			} else {
				break;
			}
		}

		/* Copy last part of filtered signal to the state for the next call */
		System.arraycopy( buf, nSamplesIn, S, 0, ORDER_FIR );
		// RESTORE_STACK;
	}
	// end resampler_down2_3.c

	// start pitch_analysis_core_FLP
	private static final int SCRATCH_SIZE = 22;

	/***********************************************************************
	 * Calculates the correlations used in stage 3 search. In order to cover
	 * the whole lag codebook for all the searched offset lags (lag +- 2),
	 * the following correlations are needed in each sub frame:
	 *
	 * sf1: lag range [-8,...,7] total 16 correlations
	 * sf2: lag range [-4,...,4] total 9 correlations
	 * sf3: lag range [-3,....4] total 8 correltions
	 * sf4: lag range [-6,....8] total 15 correlations
	 *
	 * In total 48 correlations. The direct implementation computed in worst
	 * case 4*12*5 = 240 correlations, but more likely around 120.
	 ***********************************************************************
	 *
	 * @param cross_corr_st3 O 3 DIM correlation array
	 * @param frame I vector to correlate
	 * @param start_lag I start lag
	 * @param sf_length I sub frame length
	 * @param nb_subfr I number of subframes
	 * @param complexity I Complexity setting
	 * @param arch I Run-time architecture
	 */
	private static final void silk_P_Ana_calc_corr_st3(final float cross_corr_st3[/* PE_MAX_NB_SUBFR */][/* PE_NB_CBKS_STAGE3_MAX */][/* PE_NB_STAGE3_LAGS */],
			final float frame[], final int start_lag, final int sf_length, final int nb_subfr, final int complexity)//, final int arch)
	{
		// celt_assert( complexity >= SILK_PE_MIN_COMPLEX );
		// celt_assert( complexity <= SILK_PE_MAX_COMPLEX );

		int nb_cbk_search;// , cbk_size;// java don't need
		byte[][] Lag_range_ptr;
		final byte[][] Lag_CB_ptr;
		if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
			// Lag_range_ptr = &silk_Lag_range_stage3[ complexity ][ 0 ][ 0 ];
			Lag_range_ptr     = Jpitch_est_tables.silk_Lag_range_stage3[ complexity ];// [ 0 ][ 0 ];
			// Lag_CB_ptr    = &silk_CB_lags_stage3[ 0 ][ 0 ];
			Lag_CB_ptr       = Jpitch_est_tables.silk_CB_lags_stage3;// [ 0 ];
			nb_cbk_search = (int)Jpitch_est_tables.silk_nb_cbk_searchs_stage3[ complexity ];
			// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX;
		} else {
			// celt_assert( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR >> 1);
			// Lag_range_ptr = &silk_Lag_range_stage3_10_ms[ 0 ][ 0 ];
			Lag_range_ptr = Jpitch_est_tables.silk_Lag_range_stage3_10_ms;// [ 0 ][ 0 ];
			// Lag_CB_ptr    = &silk_CB_lags_stage3_10_ms[ 0 ][ 0 ];
			Lag_CB_ptr       = Jpitch_est_tables.silk_CB_lags_stage3_10_ms;// [ 0 ];
			nb_cbk_search = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
			// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
		}

		final float scratch_mem[] = new float[ SCRATCH_SIZE ];
		final float xcorr[] = new float[ SCRATCH_SIZE ];
		int target_ptr = sf_length << 2; // Pointer to middle of frame. frame[ target_ptr ]
		for( int k = 0; k < nb_subfr; k++ ) {
			int lag_counter = 0;

			/* Calculate the correlations for each subframe */
			// lag_low  = matrix_ptr( Lag_range_ptr, k, 0, 2 );// FIXME dirty way for navigation on 2-dim array
			// lag_high = matrix_ptr( Lag_range_ptr, k, 1, 2 );
			byte[] ptr = Lag_range_ptr[ k ];// java
			final int lag_low  = (int)ptr[ 0 ];
			final int lag_high = (int)ptr[ 1 ];
			// silk_assert(lag_high-lag_low+1 <= SCRATCH_SIZE);
			Jcelt_codec_API.celt_pitch_xcorr( frame, target_ptr, frame, target_ptr - start_lag - lag_high, xcorr, sf_length, lag_high - lag_low + 1 );//, arch );
			for( int j = lag_low; j <= lag_high; j++ ) {
				// silk_assert( lag_counter < SCRATCH_SIZE );
				scratch_mem[ lag_counter ] = xcorr[ lag_high - j ];
				lag_counter++;
			}

			ptr = Lag_CB_ptr[ k ];// java
			// final int delta = (int)ptr[ 0 ];// java: changed to lag_low FIXME why need 2 different values?
			final float[][] c1 = cross_corr_st3[ k ];// java
			for( int i = 0; i < nb_cbk_search; i++ ) {
				/* Fill out the 3 dim array that stores the correlations for */
				/* each code_book vector for each start lag */
				// idx = matrix_ptr( Lag_CB_ptr, k, i, cbk_size ) - delta;// FIXME dirty way for navigation on 2-dim array
				final int idx = (int)ptr[ i ] - lag_low;// delta;// java changed
				final float[] c2 = c1[ i ];// java
				for( int j = 0; j < Jpitch_est_defines.PE_NB_STAGE3_LAGS; j++ ) {
					// silk_assert( idx + j < SCRATCH_SIZE );
					// silk_assert( idx + j < lag_counter );
					c2[ j ] = scratch_mem[ idx + j ];
				}
			}
			target_ptr += sf_length;
		}
	}

	/**
	 * Calculate the energies for first two subframes. The energies are
	 * calculated recursively.
	 *
	 * @param energies_st3 O 3 DIM correlation array
	 * @param frame I vector to correlate
	 * @param start_lag I start lag
	 * @param sf_length I sub frame length
	 * @param nb_subfr I number of subframes
	 * @param complexity I Complexity setting
	 */
	private static final void silk_P_Ana_calc_energy_st3(final float energies_st3[/* PE_MAX_NB_SUBFR */][/* PE_NB_CBKS_STAGE3_MAX */][/* PE_NB_STAGE3_LAGS */],
			final float frame[], final int start_lag, final int sf_length, final int nb_subfr, final int complexity
		)
	{
		// celt_assert( complexity >= SILK_PE_MIN_COMPLEX );
		// celt_assert( complexity <= SILK_PE_MAX_COMPLEX );

		int nb_cbk_search;// , cbk_size;// java don't need
		byte[][] Lag_range_ptr;// FIXME using dirty way for navigation on 2-dim array
		byte[][] Lag_CB_ptr;// FIXME using dirty way for navigation on 2-dim array
		if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
			// Lag_range_ptr = &silk_Lag_range_stage3[ complexity ][ 0 ][ 0 ];
			Lag_range_ptr     = Jpitch_est_tables.silk_Lag_range_stage3[ complexity ];// [ 0 ];
			// Lag_CB_ptr    = &silk_CB_lags_stage3[ 0 ][ 0 ];
			Lag_CB_ptr       = Jpitch_est_tables.silk_CB_lags_stage3;// [ 0 ];
			nb_cbk_search = Jpitch_est_tables.silk_nb_cbk_searchs_stage3[ complexity ];
			// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX;
		} else {
			// celt_assert( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR >> 1);
			// Lag_range_ptr = &silk_Lag_range_stage3_10_ms[ 0 ][ 0 ];
			Lag_range_ptr     = Jpitch_est_tables.silk_Lag_range_stage3_10_ms;// [ 0 ];
			// Lag_CB_ptr    = &silk_CB_lags_stage3_10_ms[ 0 ][ 0 ];
			Lag_CB_ptr       = Jpitch_est_tables.silk_CB_lags_stage3_10_ms;// [ 0 ];
			nb_cbk_search = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
			// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
		}

		final float scratch_mem[] = new float[ SCRATCH_SIZE ];
		int target_ptr = sf_length << 2;// frame[ target_ptr ]
		for( int k = 0; k < nb_subfr; k++ ) {
			int lag_counter = 0;

			byte[] ptr = Lag_range_ptr[ k ];// java
			final int delta = (int)ptr[ 0 ];// java moved
			/* Calculate the energy for first lag */
			final int basis_ptr = target_ptr - ( start_lag + delta );// (int)Lag_range_ptr[ k ][ 0 ] );// frame[ basis_ptr ]
			double energy = silk_energy_FLP( frame, basis_ptr, sf_length ) + 1e-3;
			// silk_assert( energy >= 0.0 );
			scratch_mem[lag_counter] = (float)energy;
			lag_counter++;

			final int lag_diff = (int)ptr[ 1 ] - delta + 1;// (int)Lag_range_ptr[ k ][ 0 ] + 1;
			for( int i = 1; i < lag_diff; i++ ) {
				/* remove part outside new window */
				double v = (double)frame[ basis_ptr + sf_length - i];// java
				v *= v;
				energy -= v;
				// silk_assert( energy >= 0.0 );

				/* add part that comes into window */
				v = (double)frame[ basis_ptr -i ];// java
				v *= v;
				energy += v;
				// silk_assert( energy >= 0.0 );
				// silk_assert( lag_counter < Jpitch_est_defines.SCRATCH_SIZE );
				scratch_mem[lag_counter] = (float)energy;
				lag_counter++;
			}

			final float[][] e1 = energies_st3[ k ];// java
			ptr = Lag_CB_ptr[ k ];// java
			// final int delta = (int)Lag_range_ptr[ k ][ 0 ];// java moved up
			for( int i = 0; i < nb_cbk_search; i++ ) {
				/* Fill out the 3 dim array that stores the correlations for    */
				/* each code_book vector for each start lag                     */
				final int idx = (int)ptr[ i ] - delta;
				final float[] e2 = e1[ i ];// java
				for( int j = 0; j < Jpitch_est_defines.PE_NB_STAGE3_LAGS; j++ ) {
					// silk_assert( idx + j < Jpitch_est_defines.SCRATCH_SIZE );
					// silk_assert( idx + j < lag_counter );
					e2[ j ] = scratch_mem[ idx + j ];
					// silk_assert( energies_st3[ k ][ i ][ j ] >= 0.0f );
				}
			}
			target_ptr += sf_length;
		}
	}

	/**
	 * CORE PITCH ANALYSIS FUNCTION
	 *
	 * java changed: lagIndex, contourIndex, LTPCorr is changed to aux
	 *
	 * @param frame I    Signal of length PE_FRAME_LENGTH_MS*Fs_kHz
	 * @param pitch_out O    Pitch lag values [nb_subfr]
	 * @param lagIndex O    Lag Index
	 * @param contourIndex O    Pitch contour Index
	 * @param LTPCorr I/O  Normalized correlation; input: value from previous frame
	 * @param aux I/O java replacement for the lagIndex, the contourIndex and the LTPCorr
	 * @param prevLag I    Last lag of previous frame; set to zero is unvoiced
	 * @param search_thres1 I    First stage threshold for lag candidates 0 - 1
	 * @param search_thres2 I    Final threshold for lag candidates 0 - 1
	 * @param Fs_kHz I    sample frequency (kHz)
	 * @param complexity I    Complexity setting, 0-2, where 2 is highest
	 * @param nb_subfr I    Number of 5 ms subframes
	 * @param arch I    Run-time architecture
	 * @return O    Voicing estimate: 0 voiced, 1 unvoiced
	 */
	private static final int silk_pitch_analysis_core_FLP(final float[] frame, final int[] pitch_out,
			// final short[] lagIndex, final byte[] contourIndex, final float[] LTPCorr,// java
			final Jpitch_data_struct_aux aux,// java to return data
			int prevLag, final float search_thres1, final float search_thres2, final int Fs_kHz, final int complexity, final int nb_subfr)//, final int arch)
	{
		/* Check for valid sampling frequency */
		// celt_assert( Fs_kHz == 8 || Fs_kHz == 12 || Fs_kHz == 16 );

		/* Check for valid complexity setting */
		// celt_assert( complexity >= SILK_PE_MIN_COMPLEX );
		// celt_assert( complexity <= SILK_PE_MAX_COMPLEX );

		// silk_assert( search_thres1 >= 0.0f && search_thres1 <= 1.0f );
		// silk_assert( search_thres2 >= 0.0f && search_thres2 <= 1.0f );

		/* Set up frame lengths max / min lag for the sampling frequency */
		final int frame_length      = ( Jpitch_est_defines.PE_LTP_MEM_LENGTH_MS + nb_subfr * Jpitch_est_defines.PE_SUBFR_LENGTH_MS ) * Fs_kHz;
		final int frame_length_4kHz = ( Jpitch_est_defines.PE_LTP_MEM_LENGTH_MS + nb_subfr * Jpitch_est_defines.PE_SUBFR_LENGTH_MS ) * 4;
		final int frame_length_8kHz = ( Jpitch_est_defines.PE_LTP_MEM_LENGTH_MS + nb_subfr * Jpitch_est_defines.PE_SUBFR_LENGTH_MS ) * 8;
		final int sf_length         = Jpitch_est_defines.PE_SUBFR_LENGTH_MS * Fs_kHz;
		final int sf_length_4kHz    = Jpitch_est_defines.PE_SUBFR_LENGTH_MS * 4;
		final int sf_length_8kHz    = Jpitch_est_defines.PE_SUBFR_LENGTH_MS * 8;
		final int min_lag           = Jpitch_est_defines.PE_MIN_LAG_MS * Fs_kHz;
		final int min_lag_4kHz      = Jpitch_est_defines.PE_MIN_LAG_MS * 4;
		final int min_lag_8kHz      = Jpitch_est_defines.PE_MIN_LAG_MS * 8;
		final int max_lag           = Jpitch_est_defines.PE_MAX_LAG_MS * Fs_kHz - 1;
		final int max_lag_4kHz      = Jpitch_est_defines.PE_MAX_LAG_MS * 4;
		final int max_lag_8kHz      = Jpitch_est_defines.PE_MAX_LAG_MS * 8 - 1;

		/* Resample from input sampled at Fs_kHz to 8 kHz */
		final float frame_8kHz[] = new float[  Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS * 8 ];
		final short frame_8_FIX[] = new short[ Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS * 8 ];
		final int filt_state[] = new int[ 6 ];
		if( Fs_kHz == 16 ) {
			/* Resample to 16 -> 8 khz */
			final short frame_16_FIX[] = new short[ 16 * Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS ];
			// silk_float2short_array( frame_16_FIX, frame, frame_length );
			for( int k = frame_length - 1; k >= 0; k-- ) {
				final int v = ( (int)Math.floor( .5 + (double)frame[k] ) );// java XXX can be using direct casting
				frame_16_FIX[k] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		    }
			// silk_memset( filt_state, 0, 2 * sizeof( opus_int32 ) );// java already zeroed
			silk_resampler_down2( filt_state, frame_8_FIX, frame_16_FIX, frame_length );
			// silk_short2float_array( frame_8kHz, frame_8_FIX, frame_length_8kHz );
			for( int k = frame_length_8kHz - 1; k >= 0; k-- ) {
				frame_8kHz[k] = (float)frame_8_FIX[k];
		    }
		} else if( Fs_kHz == 12 ) {
			/* Resample to 12 -> 8 khz */
			final short frame_12_FIX[] = new short[ 12 * Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS ];
			// silk_float2short_array( frame_12_FIX, frame, frame_length );
			for( int k = frame_length - 1; k >= 0; k-- ) {
				final int v = (int)Math.floor( .5 + (double)frame[k] );// can be using direct casting
				frame_12_FIX[k] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		    }
			// silk_memset( filt_state, 0, 6 * sizeof( opus_int32 ) );// java already zeroed
			silk_resampler_down2_3( filt_state, frame_8_FIX, frame_12_FIX, frame_length );
			// silk_short2float_array( frame_8kHz, frame_8_FIX, frame_length_8kHz );
			for( int k = frame_length_8kHz - 1; k >= 0; k-- ) {
				frame_8kHz[k] = (float)frame_8_FIX[k];
		    }
		} else {
			// celt_assert( Fs_kHz == 8 );
			// silk_float2short_array( frame_8_FIX, frame, frame_length_8kHz );
			for( int k = frame_length_8kHz - 1; k >= 0; k-- ) {
				final int v = ( (int)Math.floor( .5 + (double)frame[k] ) );// java XXX can be replaced by casting to int
				frame_8_FIX[k] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		    }
		}

		/* Decimate again to 4 kHz */
		final float frame_4kHz[] = new float[  Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS * 4 ];
		final short frame_4_FIX[] = new short[ Jpitch_est_defines.PE_MAX_FRAME_LENGTH_MS * 4 ];
		// silk_memset( filt_state, 0, 2 * sizeof( opus_int32 ) );
		filt_state[0] = 0; filt_state[1] = 0;
		silk_resampler_down2( filt_state, frame_4_FIX, frame_8_FIX, frame_length_8kHz );
		// silk_short2float_array( frame_4kHz, frame_4_FIX, frame_length_4kHz );
		for( int k = frame_length_4kHz - 1; k >= 0; k-- ) {
			frame_4kHz[k] = (float)frame_4_FIX[k];
	    }

		/* Low-pass filter */
		for( int i = frame_length_4kHz - 1; i > 0; i-- ) {
			final float v = frame_4kHz[ i ] + frame_4kHz[ i - 1 ];
	        frame_4kHz[ i ] = (v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}

		/******************************************************************************
		* FIRST STAGE, operating in 4 khz
		******************************************************************************/
		final float C[][] = new float[ Jpitch_est_defines.PE_MAX_NB_SUBFR ][ (Jpitch_est_defines.PE_MAX_LAG >> 1) + 5 ];// java already zeroed
		// silk_memset( C, 0, sizeof(silk_float) * nb_subfr * ((PE_MAX_LAG >> 1) + 5) );
		float[] cbuff = C[0];// java

		final float xcorr[] = new float[ Jpitch_est_defines.PE_MAX_LAG_MS * 4 - Jpitch_est_defines.PE_MIN_LAG_MS * 4 + 1 ];
		int target_ptr = sf_length_4kHz << 2;// frame_4kHz[ target_ptr ]
		for( int k = 0, ke = nb_subfr >> 1; k < ke; k++ ) {
			/* Check that we are within range of the array */
			// celt_assert( target_ptr >= frame_4kHz );
			// celt_assert( target_ptr + sf_length_8kHz <= frame_4kHz + frame_length_4kHz );

			int basis_ptr = target_ptr - min_lag_4kHz;// frame_4kHz[ basis_ptr ]

			/* Check that we are within range of the array */
			// celt_assert( basis_ptr >= frame_4kHz );
			// celt_assert( basis_ptr + sf_length_8kHz <= frame_4kHz + frame_length_4kHz );

			Jcelt_codec_API.celt_pitch_xcorr( frame_4kHz, target_ptr, frame_4kHz, target_ptr - max_lag_4kHz, xcorr, sf_length_8kHz, max_lag_4kHz - min_lag_4kHz + 1 );//, arch );

			/* Calculate first vector products before loop */
			double cross_corr = (double)xcorr[ max_lag_4kHz - min_lag_4kHz ];
			double normalizer = silk_energy_FLP( frame_4kHz, target_ptr, sf_length_8kHz ) +
								silk_energy_FLP( frame_4kHz, basis_ptr, sf_length_8kHz ) + sf_length_8kHz * 4000.0f;

			cbuff[ min_lag_4kHz ] += (float)( 2. * cross_corr / normalizer );

			/* From now on normalizer is computed recursively */
			for( int d = min_lag_4kHz + 1; d <= max_lag_4kHz; d++ ) {
				basis_ptr--;

				/* Check that we are within range of the array */
				// silk_assert( basis_ptr >= frame_4kHz );
				// silk_assert( basis_ptr + sf_length_8kHz <= frame_4kHz + frame_length_4kHz );

				cross_corr = xcorr[ max_lag_4kHz - d ];

				/* Add contribution of new sample and remove contribution from oldest sample */
				double v1 = (double)frame_4kHz[ basis_ptr ];// java
				v1 *= v1;
				double v2 = (double)frame_4kHz[ basis_ptr + sf_length_8kHz ];// java
				v2 *= v2;
				normalizer += v1 - v2;
				cbuff[ d ] += (float)( 2. * cross_corr / normalizer );
			}
			/* Update target pointer */
			target_ptr += sf_length_8kHz;
		}

		/* Apply short-lag bias */
		for( int i = max_lag_4kHz; i >= min_lag_4kHz; i-- ) {
			final float v = cbuff[ i ];// java
			cbuff[ i ] = v - v * (float)i / 4096.0f;
		}

		/* Sort */
		int length_d_srch = 4 + (complexity << 1);
		// celt_assert( 3 * length_d_srch <= PE_D_SRCH_LENGTH );
		final int d_srch[] = new int[ Jpitch_est_defines.PE_D_SRCH_LENGTH ];
		silk_insertion_sort_decreasing_FLP( cbuff, min_lag_4kHz, d_srch, max_lag_4kHz - min_lag_4kHz + 1, length_d_srch );

		/* Escape if correlation is very low already here */
		final float Cmax = cbuff[ min_lag_4kHz ];
		if( Cmax < 0.2f ) {
			//silk_memset( pitch_out, 0, nb_subfr * sizeof( opus_int ) );
			for( int i = 0; i < nb_subfr; i++ ) {
				pitch_out[i] = 0;
			}
			aux.mLTPCorr      = 0.0f;// LTPCorr[0]      = 0.0f;
			aux.mLagIndex     = 0;// lagIndex[0]     = 0;
			aux.mContourIndex = 0;// contourIndex[0] = 0;
			return 1;
		}

		final float threshold = search_thres1 * Cmax;
		for( int i = 0; i < length_d_srch; i++ ) {
			/* Convert to 8 kHz indices for the sorted correlation that exceeds the threshold */
			if( cbuff[ min_lag_4kHz + i ] > threshold ) {
				d_srch[ i ] = (d_srch[ i ] + min_lag_4kHz) << 1;
			} else {
				length_d_srch = i;
				break;
			}
		}
		// celt_assert( length_d_srch > 0 );

		final short d_comp[] = new short[ (Jpitch_est_defines.PE_MAX_LAG >> 1) + 5 ];// java already zeroed
		//for( int i = min_lag_8kHz - 5; i < max_lag_8kHz + 5; i++ ) {
		//	d_comp[ i ] = 0;
		//}
		for( int i = 0; i < length_d_srch; i++ ) {
			d_comp[ d_srch[ i ] ] = 1;
		}

		/* Convolution */
		for( int i = max_lag_8kHz + 3; i >= min_lag_8kHz; i-- ) {
			d_comp[ i ] += d_comp[ i - 1 ] + d_comp[ i - 2 ];
		}

		length_d_srch = 0;
		for( int i = min_lag_8kHz; i < max_lag_8kHz + 1; i++ ) {
			if( d_comp[ i + 1 ] > 0 ) {
				d_srch[ length_d_srch ] = i;
				length_d_srch++;
			}
		}

		/* Convolution */
		for( int i = max_lag_8kHz + 3; i >= min_lag_8kHz; i-- ) {
			d_comp[ i ] += d_comp[ i - 1 ] + d_comp[ i - 2 ] + d_comp[ i - 3 ];
		}

		int length_d_comp = 0;
		for( int i = min_lag_8kHz; i < max_lag_8kHz + 4; i++ ) {
			if( d_comp[ i ] > 0 ) {
				d_comp[ length_d_comp ] = (short)( i - 2 );
				length_d_comp++;
			}
		}

		/**********************************************************************************
		** SECOND STAGE, operating at 8 kHz, on lag sections with high correlation
		*************************************************************************************/
		/*********************************************************************************
		* Find energy of each subframe projected onto its history, for a range of delays
		*********************************************************************************/
		// silk_memset( C, 0, Jpitch_est_defines.PE_MAX_NB_SUBFR * ((PE_MAX_LAG >> 1) + 5) * sizeof(silk_float));
		for( int k = 0; k < Jpitch_est_defines.PE_MAX_NB_SUBFR; k++ ) {
			final float[] cr = C[k];
			int i = 0;
			do {
				cr[i] = 0;
			} while( ++i < (Jpitch_est_defines.PE_MAX_LAG >> 1) + 5 );
		}

		target_ptr = Jpitch_est_defines.PE_LTP_MEM_LENGTH_MS * 8;// dim[ target_ptr ], frame[ target_ptr ] or frame_8kHz[ target_ptr ]
		float[] dim;// java
		if( Fs_kHz == 8 ) {
			dim = frame;// java
		} else {
			dim = frame_8kHz;// java
		}
		for( int k = 0; k < nb_subfr; k++ ) {
			cbuff = C[ k ];// java
			final double energy_tmp = silk_energy_FLP( dim, target_ptr, sf_length_8kHz ) + 1.0;
			for( int j = 0; j < length_d_comp; j++ ) {
				final int d = (int)d_comp[ j ];
				final int basis_ptr = target_ptr - d;// dim[ basis_ptr ]
				final double cross_corr = silk_inner_product_FLP( dim, basis_ptr, dim, target_ptr, sf_length_8kHz );
				if( cross_corr > 0.0 ) {// FIXME using float
					final double energy = silk_energy_FLP( dim, basis_ptr, sf_length_8kHz );
					cbuff[ d ] = (float)( 2. * cross_corr / ( energy + energy_tmp ) );
				} else {
					cbuff[ d ] = 0.0f;
				}
			}
			target_ptr += sf_length_8kHz;
		}

		/* search over lag range and lags codebook */
		/* scale factor for lag codebook, as a function of center lag */

		float CCmax   = 0.0f; /* This value doesn't matter */
		float CCmax_b = -1000.0f;

		int CBimax = 0; /* To avoid returning undefined lag values */
		int lag = -1;   /* To check if lag with strong enough correlation has been found */

		float prevLag_log2;
		if( prevLag > 0 ) {
			if( Fs_kHz == 12 ) {
				prevLag = ( prevLag << 1 ) / 3;
			} else if( Fs_kHz == 16 ) {
				prevLag = ( prevLag >> 1 );
			}
			prevLag_log2 = (float)( 3.32192809488736 * Math.log10( (double)prevLag ) );
		} else {
			prevLag_log2 = 0;
		}

		/* Set up stage 2 codebook based on number of subframes */
		// int cbk_size;
		int nb_cbk_search;
		byte[][] Lag_CB_ptr;
		if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
			// cbk_size = Jpitch_est_defines.PE_NB_CBKS_STAGE2_EXT;// java don't need
			Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2;// [ 0 ][ 0 ];
			if( Fs_kHz == 8 && complexity > Jpitch_est_defines.SILK_PE_MIN_COMPLEX ) {
				/* If input is 8 khz use a larger codebook here because it is last stage */
				nb_cbk_search = Jpitch_est_defines.PE_NB_CBKS_STAGE2_EXT;
			} else {
				nb_cbk_search = Jpitch_est_defines.PE_NB_CBKS_STAGE2;
			}
		} else {
			// cbk_size       = Jpitch_est_defines.PE_NB_CBKS_STAGE2_10MS;// java don't need
			Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2_10_ms;// [ 0 ][ 0 ];
			nb_cbk_search  = Jpitch_est_defines.PE_NB_CBKS_STAGE2_10MS;
		}

		final float CC[] = new float[ Jpitch_est_defines.PE_NB_CBKS_STAGE2_EXT ];
		for( int k = 0; k < length_d_srch; k++ ) {
			final int d = d_srch[ k ];
			for( int j = 0; j < nb_cbk_search; j++ ) {
				float ccj = 0.0f;
				for( int i = 0; i < nb_subfr; i++ ) {
					/* Try all codebooks */
					// CC[ j ] += C[ i ][ d + matrix_ptr( Lag_CB_ptr, i, j, cbk_size )];// FIXME dirty way to navigate on rows
					ccj += C[ i ][ d + Lag_CB_ptr[ i ][ j ] ];
				}
				CC[j] = ccj;// java
			}
			/* Find best codebook */
			float CCmax_new  = -1000.0f;
			int CBimax_new = 0;
			for( int i = 0; i < nb_cbk_search; i++ ) {
				if( CC[ i ] > CCmax_new ) {
					CCmax_new = CC[ i ];
					CBimax_new = i;
				}
			}

			/* Bias towards shorter lags */
			final float lag_log2 = (float)( 3.32192809488736 * Math.log10( (double)d ) );
			float CCmax_new_b = CCmax_new - Jpitch_est_defines.PE_SHORTLAG_BIAS * (float)nb_subfr * lag_log2;

			/* Bias towards previous lag */
			if( prevLag > 0 ) {
				float delta_lag_log2_sqr = lag_log2 - prevLag_log2;
				delta_lag_log2_sqr *= delta_lag_log2_sqr;
				CCmax_new_b -= Jpitch_est_defines.PE_PREVLAG_BIAS * (float)nb_subfr * (aux.mLTPCorr) * delta_lag_log2_sqr / ( delta_lag_log2_sqr + 0.5f );
			}

			if( CCmax_new_b > CCmax_b &&                /* Find maximum biased correlation                  */
					CCmax_new > (float)nb_subfr * search_thres2    /* Correlation needs to be high enough to be voiced */
			) {
				CCmax_b = CCmax_new_b;
				CCmax   = CCmax_new;
				lag     = d;
				CBimax  = CBimax_new;
			}
		}

		if( lag == -1 ) {
			/* No suitable candidate found */
			// silk_memset( pitch_out, 0, Jpitch_est_defines.PE_MAX_NB_SUBFR * sizeof(opus_int) );
			for( int i = 0; i < Jpitch_est_defines.PE_MAX_NB_SUBFR; i++ ) {
				pitch_out[i] = 0;
			}
			aux.mLTPCorr      = 0.0f;// LTPCorr[0]      = 0.0f;
			aux.mLagIndex     = 0;// lagIndex[0]     = 0;
			aux.mContourIndex = 0;// contourIndex[0] = 0;
			return 1;
		}

		/* Output normalized correlation */
		aux.mLTPCorr = CCmax / (float)nb_subfr;
		// silk_assert( *LTPCorr >= 0.0f );

		if( Fs_kHz > 8 ) {
			/* Search in original signal */

			/* Compensate for decimation */
			// silk_assert( lag == silk_SAT16( lag ) );
			if( Fs_kHz == 12 ) {
				lag = JSigProc_FIX.silk_RSHIFT_ROUND( ( lag * 3 ), 1 );
			} else { /* Fs_kHz == 16 */
				lag <<= 1;
			}

			lag = (lag > max_lag ? max_lag : (lag < min_lag ? min_lag : lag));
			int start_lag = lag - 2;
			start_lag = ( start_lag >= min_lag ? start_lag : min_lag );
			int end_lag   = lag + 2;
			end_lag   = ( end_lag <= max_lag ? end_lag : max_lag );
			int lag_new   = lag;                                    /* to avoid undefined lag */
			CBimax    = 0;                                      /* to avoid undefined lag */

			CCmax = -1000.0f;

			/* Calculate the correlations and energies needed in stage 3 */
			final float energies_st3[][][] =
					new float[ Jpitch_est_defines.PE_MAX_NB_SUBFR ][ Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX ][ Jpitch_est_defines.PE_NB_STAGE3_LAGS ];
			final float cross_corr_st3[][][] =
					new float[ Jpitch_est_defines.PE_MAX_NB_SUBFR ][ Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX ][ Jpitch_est_defines.PE_NB_STAGE3_LAGS ];
			silk_P_Ana_calc_corr_st3( cross_corr_st3, frame, start_lag, sf_length, nb_subfr, complexity );//, arch );
			silk_P_Ana_calc_energy_st3( energies_st3, frame, start_lag, sf_length, nb_subfr, complexity );

			int lag_counter = 0;
			// silk_assert( lag == silk_SAT16( lag ) );
			final float contour_bias = Jpitch_est_defines.PE_FLATCONTOUR_BIAS / (float)lag;

			/* Set up cbk parameters according to complexity setting and frame length */
			if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
				nb_cbk_search = (int)Jpitch_est_tables.silk_nb_cbk_searchs_stage3[ complexity ];
				// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX;// java don't need
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3;// [ 0 ][ 0 ];
			} else {
				nb_cbk_search = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
				// cbk_size      = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;// java don't need
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3_10_ms;// [ 0 ][ 0 ];
			}

			target_ptr = Jpitch_est_defines.PE_LTP_MEM_LENGTH_MS * Fs_kHz;// frame[ target_ptr ]
			final double energy_tmp = silk_energy_FLP( frame, target_ptr, nb_subfr * sf_length ) + 1.0;
			for( int d = start_lag; d <= end_lag; d++ ) {
				for( int j = 0; j < nb_cbk_search; j++ ) {
					double cross_corr = 0.0;
					double energy = energy_tmp;
					for( int k = 0; k < nb_subfr; k++ ) {
						cross_corr += cross_corr_st3[ k ][ j ][ lag_counter ];
						energy     +=   energies_st3[ k ][ j ][ lag_counter ];
					}
					float CCmax_new;
					if( cross_corr > 0.0 ) {
						CCmax_new = (float)( 2. * cross_corr / energy );
						/* Reduce depending on flatness of contour */
						CCmax_new *= 1.0f - contour_bias * j;
					} else {
						CCmax_new = 0.0f;
					}

					if( CCmax_new > CCmax && ( d + (int)Jpitch_est_tables.silk_CB_lags_stage3[ 0 ][ j ] ) <= max_lag ) {
						CCmax   = CCmax_new;
						lag_new = d;
						CBimax  = j;
					}
				}
				lag_counter++;
			}

			for( int k = 0; k < nb_subfr; k++ ) {
				// pitch_out[ k ] = lag_new + matrix_ptr( Lag_CB_ptr, k, CBimax, cbk_size );// FIXME dirty way to navigate on rows
				final int v = lag_new + (int)Lag_CB_ptr[ k ][ CBimax ];// java
				pitch_out[ k ] = JSigProc_FIX.silk_LIMIT( v, min_lag, Jpitch_est_defines.PE_MAX_LAG_MS * Fs_kHz );
			}
			aux.mLagIndex = (short)( lag_new - min_lag );// lagIndex[0] = (short)( lag_new - min_lag );
			aux.mContourIndex = (byte)CBimax;// contourIndex[0] = (byte)CBimax;
		} else {        /* Fs_kHz == 8 */
			/* Save Lags */
			for( int k = 0; k < nb_subfr; k++ ) {
				// pitch_out[ k ] = lag + matrix_ptr( Lag_CB_ptr, k, CBimax, cbk_size );// FIXME dirty way to navigate on rows
				final int v = lag + (int)Lag_CB_ptr[ k ][ CBimax ];// java
				pitch_out[ k ] = JSigProc_FIX.silk_LIMIT( v, min_lag_8kHz, Jpitch_est_defines.PE_MAX_LAG_MS * 8 );
			}
			aux.mLagIndex = (short)( lag - min_lag_8kHz );// lagIndex[0] = (short)( lag - min_lag_8kHz );
			aux.mContourIndex = (byte)CBimax;// contourIndex[0] = (byte)CBimax;
		}
		// celt_assert( *lagIndex >= 0 );
		/* return as voiced */
		return 0;
	}
	// end pitch_analysis_core_FLP

	// start bwexpander_FLP
	/**
	 * Chirp (bw expand) LP AR filter
	 *
	 * @param ar I/O  AR filter to be expanded (without leading 1)
	 * @param aoffset I java an offset for the ar
	 * @param d I    length of ar
	 * @param chirp I    chirp factor (typically in range (0..1) )
	 */
	private static final void silk_bwexpander_FLP(final float[] ar, int aoffset, int d, final float chirp)
	{// java aoffset is added
		float cfac = chirp;
		d--;// java
		d += aoffset;// java
		while( aoffset < d ) {// java changed
			ar[ aoffset++ ] *= cfac;
			cfac *= chirp;
		}
		ar[ d ] *= cfac;
	}
	// end bwexpander_FLP

	// start autocorrelation_FLP
	/**
	 * compute autocorrelation
	 *
	 * @param results O    result (length correlationCount)
	 * @param inputData I    input data to correlate
	 * @param inputDataSize I    length of input
	 * @param correlationCount I    number of correlation taps to compute
	 */
	private static final void silk_autocorrelation_FLP(final float[] results, final float[] inputData,
			final int inputDataSize, int correlationCount
		)
	{
		if( correlationCount > inputDataSize ) {
			correlationCount = inputDataSize;
		}

		for( int i = 0; i < correlationCount; i++ ) {
			results[ i ] =  (float)silk_inner_product_FLP( inputData, 0, inputData, i, inputDataSize - i );
		}
	}
	// end autocorrelation_FLP

	// start apply_sine_window_FLP
	/**
	 * Apply sine window to signal vector
	 * Window types:
	 *  1 -> sine window from 0 to pi/2
	 *  2 -> sine window from pi/2 to pi
	 *
	 * @param px_win O    Pointer to windowed signal
	 * @param woffset I java an offset for the px_win
	 * @param px I    Pointer to input signal
	 * @param xoffset I java an offset for the px
	 * @param win_type I    Selects a window type
	 * @param length I    Window length, multiple of 4
	 */
	private static final void silk_apply_sine_window_FLP(final float px_win[], int woffset, final float px[], int xoffset, final int win_type, int length)
	{// java woffset, xoffset are added
		// celt_assert( win_type == 1 || win_type == 2 );

		/* Length must be multiple of 4 */
		// celt_assert( ( length & 3 ) == 0 );

		final float freq = ((float)Math.PI) / (float)( length + 1 );

		/* Approximation of 2 * cos(f) */
		final float c = 2.0f - freq * freq;

		/* Initialize state */
		float S0, S1;
		if( win_type < 2 ) {
			/* Start from 0 */
			S0 = 0.0f;
			/* Approximation of sin(f) */
			S1 = freq;
		} else {
			/* Start from 1 */
			S0 = 1.0f;
			/* Approximation of cos(f) */
			S1 = 0.5f * c;
		}

		/* Uses the recursive equation:   sin(n*f) = 2 * cos(f) * sin((n-1)*f) - sin((n-2)*f)   */
		/* 4 samples at a time */
		length += woffset;// java
		while( woffset < length ) {// java changed
			px_win[ woffset++ ] = px[ xoffset++ ] * 0.5f * ( S0 + S1 );
			px_win[ woffset++ ] = px[ xoffset++ ] * S1;
			S0 = c * S1 - S0;
			px_win[ woffset++ ] = px[ xoffset++ ] * 0.5f * ( S1 + S0 );
			px_win[ woffset++ ] = px[ xoffset++ ] * S0;
			S1 = c * S0 - S1;
		}
	}
	// end apply_sine_window_FLP

	// start warped_autocorrelation_FLP
	/**
	 * Autocorrelations for a warped frequency axis
	 *
	 * @param corr O    Result [order + 1]
	 * @param input I    Input data to correlate
	 * @param warping I    Warping coefficient
	 * @param length I    Length of input
	 * @param order I    Correlation order (even)
	 */
	private static final void silk_warped_autocorrelation_FLP(final float[] corr, final float[] input,
			final float warping, final int length, final int order
		)
	{// FIXME why using doubles?
		// FIXME incorrect initialization of the arrays because MAX_SHAPE_LPC_ORDER = 24 (was 16)
		// final double state[/* MAX_SHAPE_LPC_ORDER + 1 */] = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
		// final double C[/*     MAX_SHAPE_LPC_ORDER + 1 */] = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
		final double state[] = new double[ Jdefine.MAX_SHAPE_LPC_ORDER + 1 ];// = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
		final double C[] = new double[     Jdefine.MAX_SHAPE_LPC_ORDER + 1 ];// = { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
		/* Order must be even */
		// celt_assert( ( order & 1 ) == 0 );

		/* Loop over samples */
		for( int n = 0; n < length; n++ ) {
			double tmp1 = input[ n ];
			/* Loop over allpass sections */
			for( int i = 0; i < order; i += 2 ) {
				final int i1 = i + 1;// java
				/* Output of allpass section */
				final double tmp2 = state[ i ] + warping * ( state[ i1 ] - tmp1 );
				state[ i ] = tmp1;
				C[ i ] += state[ 0 ] * tmp1;
				/* Output of allpass section */
				tmp1 = state[ i1 ] + warping * ( state[ i + 2 ] - tmp2 );
				state[ i1 ] = tmp2;
				C[ i1 ] += state[ 0 ] * tmp2;
			}
			state[ order ] = tmp1;
			C[ order ] += state[ 0 ] * tmp1;
		}

		/* Copy correlations in float output format */
		for( int i = 0; i < order + 1; i++ ) {
			corr[ i ] = (float)C[ i ];
		}
	}
	// end warped_autocorrelation_FLP

	// start noise_shape_analysis_FLP
	/** Compute gain to make warped filter coefficients have a zero mean log frequency response on a
	 * non-warped frequency scale. (So that it can be implemented with a minimum-phase monic filter.)
	 * Note: A monic filter is one with the first coefficient equal to 1.0. In Silk we omit the first
	 * coefficient in an array of coefficients, for monic filters.
	 */

	/**
	 * Compute gain to make warped filter coefficients have a zero mean log frequency response on a
	 * non-warped frequency scale. (So that it can be implemented with a minimum-phase monic filter.)
	 * Note: A monic filter is one with the first coefficient equal to 1.0. In Silk we omit the first
	 * coefficient in an array of coefficients, for monic filters.
	 *
	 * @param coefs
	 * @param coffset
	 * @param lambda
	 * @param order
	 * @return
	 */
	private static final float warped_gain(final float[] coefs, final int coffset, float lambda, final int order) {// java coffset is added
		lambda = -lambda;
		int i = coffset + order - 1;// java
		float gain = coefs[ i-- ];// i = coffset + order - 2
		for( ; i >= coffset; i-- ) {
			gain = lambda * gain + coefs[ i ];
		}
		return (float)( 1.0f / ( 1.0f - lambda * gain ) );
	}

	/**
	 * Convert warped filter coefficients to monic pseudo-warped coefficients and limit maximum
	 * amplitude of monic warped coefficients by using bandwidth expansion on the true coefficients
	 *
	 * @param coefs
	 * @param soffset
	 * @param lambda
	 * @param limit
	 * @param order
	 */
	private static final void warped_true2monic_coefs(final float[] coefs, final int soffset,// java
			final float lambda, final float limit, final int order
		)
	{
		int ind = soffset;// java 0;

		final int soffset_order = soffset + order;// java
		/* Convert to monic coefficients */
		for( int si = soffset_order - 1; si > soffset; si-- ) {
			coefs[ si - 1 ] -= lambda * coefs[ si ];
		}
		float gain = ( 1.0f - lambda * lambda ) / ( 1.0f + lambda * coefs[ soffset + 0 ] );
		for( int si = soffset; si < soffset_order; si++ ) {
			coefs[ si ] *= gain;
		}

		/* Limit */
		for( int iter = 0; iter < 10; iter++ ) {
			/* Find maximum absolute value */
			float maxabs = -1.0f;
			for( int si = soffset; si < soffset_order; si++ ) {
				float tmp = coefs[ si ];// java
				if( tmp < 0 ) {// java
					tmp = -tmp;// java
				}
				if( tmp > maxabs ) {
					maxabs = tmp;
					ind = si - soffset;// i; java changed
				}
			}
			if( maxabs <= limit ) {
				/* Coefficients are within range - done */
				return;
			}

			/* Convert back to true warped coefficients */
			for( int si = soffset; si < soffset_order; si++ ) {
				coefs[ si - 1 ] += lambda * coefs[ si ];
			}
			gain = 1.0f / gain;
			for( int si = soffset; si < soffset_order; si++ ) {
				coefs[ si ] *= gain;
			}

			/* Apply bandwidth expansion */
			final float chirp = 0.99f - ( 0.8f + 0.1f * iter ) * ( maxabs - limit ) / ( maxabs * ( ind + 1 ) );
			silk_bwexpander_FLP( coefs, soffset, order, chirp );

			/* Convert to monic warped coefficients */
			for( int si = soffset_order - 1; si > soffset; si-- ) {
				coefs[ si - 1 ] -= lambda * coefs[ si ];
			}
			gain = ( 1.0f - lambda * lambda ) / ( 1.0f + lambda * coefs[ soffset + 0 ] );
			for( int si = soffset; si < soffset_order; si++ ) {
				coefs[ si ] *= gain;
			}
		}
		// silk_assert( 0 );
	}

	private static final void limit_coefs(final float[] coefs, final int coefs_offset, final float limit, final int order) {
		final int ie = order + coefs_offset;// java
		int ind = coefs_offset;// java 0;
		for( int iter = 0; iter < 10; iter++ ) {
			/* Find maximum absolute value */
			float maxabs = -1.0f;
			for( int i = coefs_offset; i < ie; i++ ) {
				float tmp = coefs[ i ];
				if( tmp < 0 ) {
					tmp = -tmp;// java abs
				}
				if( tmp > maxabs ) {
					maxabs = tmp;
					ind = i;
				}
			}
			if( maxabs <= limit ) {
				/* Coefficients are within range - done */
				return;
			}

			/* Apply bandwidth expansion */
			final float chirp = 0.99f - ( 0.8f + 0.1f * iter ) * ( maxabs - limit ) / ( maxabs * ( ind - coefs_offset + 1 ) );// java added - coefs_offset
			silk_bwexpander_FLP( coefs, coefs_offset, order, chirp );
		}
		// silk_assert( 0 );
	}
	/**
	 * Compute noise shaping coefficients and initial gain values
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param pitch_res I    LPC residual from pitch analysis
	 * @param roffset I java an offset for the pitch_res
	 * @param x I    Input signal [frame_length + la_shape]
	 * @param xoffset I java an offset for the x
	 */
	private final void silk_noise_shape_analysis_FLP(final Jsilk_encoder_control_FLP psEncCtrl,
			final float[] pitch_res, final int roffset,
			final float[] x, final int xoffset
		)
	{
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		/* Point to start of first LPC analysis block */
		int x_ptr = xoffset - this_sCmn.la_shape;// x[ x_ptr ]

		/****************/
		/* GAIN CONTROL */
		/****************/
		float SNR_adj_dB = this_sCmn.SNR_dB_Q7 * ( 1 / 128.0f );

		/* Input quality is the average of the quality in the lowest two VAD bands */
		psEncCtrl.input_quality = 0.5f * ( this_sCmn.input_quality_bands_Q15[ 0 ] + this_sCmn.input_quality_bands_Q15[ 1 ] ) * ( 1.0f / 32768.0f );

		/* Coding quality level, between 0.0 and 1.0 */
		psEncCtrl.coding_quality = (float)(1.0 / (1.0 + Math.exp( -(double)(0.25f * ( SNR_adj_dB - 20.0f )) )));

		if( ! this_sCmn.useCBR ) {
			/* Reduce coding SNR during low speech activity */
			final float b = 1.0f - this_sCmn.speech_activity_Q8 * ( 1.0f /  256.0f );
			SNR_adj_dB -= Jtuning_parameters.BG_SNR_DECR_dB * psEncCtrl.coding_quality * ( 0.5f + 0.5f * psEncCtrl.input_quality ) * b * b;
		}

		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			/* Reduce gains for periodic signals */
			SNR_adj_dB += Jtuning_parameters.HARM_SNR_INCR_dB * this.LTPCorr;
		} else {
			/* For unvoiced signals and low-quality input, adjust the quality slower than SNR_dB setting */
			SNR_adj_dB += ( -0.4f * this_sCmn.SNR_dB_Q7 * ( 1 / 128.0f ) + 6.0f ) * ( 1.0f - psEncCtrl.input_quality );
		}

		/*************************/
		/* SPARSENESS PROCESSING */
		/*************************/
		/* Set quantizer offset */
		final int nb_subfr = this_sCmn.nb_subfr;// java
		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			/* Initially set to 0; may be overruled in process_gains(..) */
			this_sCmn.indices.quantOffsetType = 0;
		} else {
			/* Sparseness measure, based on relative fluctuations of energy per 2 milliseconds */
			final int nSamples = this_sCmn.fs_kHz << 1;
			float energy_variation = 0.0f;
			float log_energy_prev  = 0.0f;
			int pitch_res_ptr = roffset;// pitch_res[ pitch_res_ptr ]
			final int nSegs = ( Jdefine.SUB_FRAME_LENGTH_MS * nb_subfr ) >>> 1;
			for( int k = 0; k < nSegs; k++ ) {
				final float nrg = (float)nSamples + (float)silk_energy_FLP( pitch_res, pitch_res_ptr, nSamples );
				final float log_energy = (float)(3.32192809488736 * Math.log10( (double)nrg ));
				if( k > 0 ) {
					float v = log_energy - log_energy_prev;// java
					if( v < 0 ) {
						v = -v;
					}// java
					energy_variation += v;
				}
				log_energy_prev = log_energy;
				pitch_res_ptr += nSamples;
			}

			/* Set quantization offset depending on sparseness measure */
			if( energy_variation > Jtuning_parameters.ENERGY_VARIATION_THRESHOLD_QNT_OFFSET * (nSegs - 1) ) {
				this_sCmn.indices.quantOffsetType = 0;
			} else {
				this_sCmn.indices.quantOffsetType = 1;
			}
		}

		/*******************************/
		/* Control bandwidth expansion */
		/*******************************/
		/* More BWE for signals with high prediction gain */
		float strength = Jtuning_parameters.FIND_PITCH_WHITE_NOISE_FRACTION * psEncCtrl.predGain;           /* between 0.0 and 1.0 */
		final float BWExp = Jtuning_parameters.BANDWIDTH_EXPANSION / ( 1.0f + strength * strength );

		final float warping = (float)this_sCmn.warping_Q16 / 65536.0f + 0.01f * psEncCtrl.coding_quality;

		/********************************************/
		/* Compute noise shaping AR coefs and gains */
		/********************************************/
		final float x_windowed[] = new float[ Jdefine.SHAPE_LPC_WIN_MAX ];
		final float auto_corr[] = new float[ Jdefine.MAX_SHAPE_LPC_ORDER + 1 ];
		final float rc[] = new float[ Jdefine.MAX_SHAPE_LPC_ORDER + 1 ];
		final int flat_part = this_sCmn.fs_kHz * 3;// java
		for( int k = 0; k < nb_subfr; k++ ) {
			/* Apply window: sine slope followed by flat part followed by cosine slope */
			// final int flat_part = this_sCmn.fs_kHz * 3;// java moved up
			final int slope_part = ( this_sCmn.shapeWinLength - flat_part ) >> 1;

			silk_apply_sine_window_FLP( x_windowed, 0, x, x_ptr, 1, slope_part );
			int shift = slope_part;
			System.arraycopy( x, x_ptr + shift, x_windowed, shift, flat_part );
			shift += flat_part;
			silk_apply_sine_window_FLP( x_windowed, shift, x, x_ptr + shift, 2, slope_part );

			/* Update pointer: next LPC analysis block */
			x_ptr += this_sCmn.subfr_length;

			if( this_sCmn.warping_Q16 > 0 ) {
				/* Calculate warped auto correlation */
				silk_warped_autocorrelation_FLP( auto_corr, x_windowed, warping,
						this_sCmn.shapeWinLength, this_sCmn.shapingLPCOrder );
			} else {
				/* Calculate regular auto correlation */
				silk_autocorrelation_FLP( auto_corr, x_windowed, this_sCmn.shapeWinLength, this_sCmn.shapingLPCOrder + 1 );
			}

			/* Add white noise, as a fraction of energy */
			auto_corr[ 0 ] += auto_corr[ 0 ] * Jtuning_parameters.SHAPE_WHITE_NOISE_FRACTION + 1.0f;

			/* Convert correlations to prediction coefficients, and compute residual energy */
			final int ko = k * Jdefine.MAX_SHAPE_LPC_ORDER;// java
			final float nrg = silk_schur_FLP( rc, auto_corr, this_sCmn.shapingLPCOrder );
			silk_k2a_FLP( psEncCtrl.AR, ko, rc, this_sCmn.shapingLPCOrder );
			psEncCtrl.Gains[ k ] = (float)Math.sqrt( (double)nrg );

			if( this_sCmn.warping_Q16 > 0 ) {
				/* Adjust gain for warping */
				psEncCtrl.Gains[ k ] *= warped_gain( psEncCtrl.AR, ko, warping, this_sCmn.shapingLPCOrder );
			}

			/* Bandwidth expansion for synthesis filter shaping */
			silk_bwexpander_FLP( psEncCtrl.AR, ko, this_sCmn.shapingLPCOrder, BWExp );

			if( this_sCmn.warping_Q16 > 0 ) {
				/* Convert to monic warped prediction coefficients and limit absolute values */
				warped_true2monic_coefs( psEncCtrl.AR, ko, warping, 3.999f, this_sCmn.shapingLPCOrder );
			} else {
				/* Limit absolute values */
				limit_coefs( psEncCtrl.AR, ko, 3.999f, this_sCmn.shapingLPCOrder );
			}
		}

		/*****************/
		/* Gain tweaking */
		/*****************/
		/* Increase gains during low speech activity */
		final float gain_mult = (float)Math.pow( 2.0, -0.16 * (double)SNR_adj_dB );
		final float gain_add  = (float)Math.pow( 2.0,  0.16 * (double)Jdefine.MIN_QGAIN_DB );
		for( int k = 0; k < nb_subfr; k++ ) {
			float v = psEncCtrl.Gains[ k ];
			v *= gain_mult;
			v += gain_add;
			psEncCtrl.Gains[ k ] = v;
		}

		/************************************************/
		/* Control low-frequency shaping and noise tilt */
		/************************************************/
		/* Less low frequency shaping for noisy inputs */
		strength = Jtuning_parameters.LOW_FREQ_SHAPING * ( 1.0f + Jtuning_parameters.LOW_QUALITY_LOW_FREQ_SHAPING_DECR * ( this_sCmn.input_quality_bands_Q15[ 0 ] * ( 1.0f / 32768.0f ) - 1.0f ) );
		strength *= this_sCmn.speech_activity_Q8 * ( 1.0f /  256.0f );
		float Tilt;
		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			/* Reduce low frequencies quantization noise for periodic signals, depending on pitch lag */
			/*f = 400; freqz([1, -0.98 + 2e-4 * f], [1, -0.97 + 7e-4 * f], 2^12, Fs); axis([0, 1000, -10, 1])*/
			for( int k = 0; k < nb_subfr; k++ ) {
				final float b = 0.2f / this_sCmn.fs_kHz + 3.0f / psEncCtrl.pitchL[ k ];
				psEncCtrl.LF_MA_shp[ k ] = -1.0f + b;
				psEncCtrl.LF_AR_shp[ k ] =  1.0f - b - b * strength;
			}
			Tilt = - Jtuning_parameters.HP_NOISE_COEF -
					(1 - Jtuning_parameters.HP_NOISE_COEF) * Jtuning_parameters.HARM_HP_NOISE_COEF * this_sCmn.speech_activity_Q8 * ( 1.0f /  256.0f );
		} else {
			final float b = 1.3f / this_sCmn.fs_kHz;
			final float ma = -1.0f + b;
			final float ar =  1.0f - b - b * strength * 0.6f;
			psEncCtrl.LF_MA_shp[ 0 ] = ma;
			psEncCtrl.LF_AR_shp[ 0 ] = ar;
			for( int k = 1; k < nb_subfr; k++ ) {
				psEncCtrl.LF_MA_shp[ k ] = ma;
				psEncCtrl.LF_AR_shp[ k ] = ar;
			}
			Tilt = -Jtuning_parameters.HP_NOISE_COEF;
		}

		/****************************/
		/* HARMONIC SHAPING CONTROL */
		/****************************/
		float HarmShapeGain;
		if( Jdefine.USE_HARM_SHAPING && this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			/* Harmonic noise shaping */
			HarmShapeGain = Jtuning_parameters.HARMONIC_SHAPING;

			/* More harmonic noise shaping for high bitrates or noisy input */
			HarmShapeGain += Jtuning_parameters.HIGH_RATE_OR_LOW_QUALITY_HARMONIC_SHAPING *
					( 1.0f - ( 1.0f - psEncCtrl.coding_quality ) * psEncCtrl.input_quality );

			/* Less harmonic noise shaping for less periodic signals */
			HarmShapeGain *= (float)Math.sqrt( (double)this.LTPCorr );
		} else {
			HarmShapeGain = 0.0f;
		}

		/*************************/
		/* Smooth over subframes */
		/*************************/
		final Jsilk_shape_state_FLP psShapeSt = this.sShape;
		for( int k = 0, n = nb_subfr; k < n; k++ ) {
			psShapeSt.HarmShapeGain_smth += Jtuning_parameters.SUBFR_SMTH_COEF * ( HarmShapeGain - psShapeSt.HarmShapeGain_smth );
			psEncCtrl.HarmShapeGain[ k ]  = psShapeSt.HarmShapeGain_smth;
			psShapeSt.Tilt_smth          += Jtuning_parameters.SUBFR_SMTH_COEF * ( Tilt - psShapeSt.Tilt_smth );
			psEncCtrl.Tilt[ k ]           = psShapeSt.Tilt_smth;
		}
	}
	// end noise_shape_analysis_FLP

	// start find_LTP_FLP
	/**
	 *
	 * @param XX O    Weight for LTP quantization
	 * @param xX O    Weight for LTP quantization
	 * @param r_ptr I    LPC residual
	 * @param roffset I java an offset for the res_pitch
	 * @param lag I    LTP lags
	 * @param subfr_length I    Subframe length
	 * @param nb_subfr I    Number of subframes
	 */
	private static final void silk_find_LTP_FLP(
			final float XX[/* MAX_NB_SUBFR * LTP_ORDER * LTP_ORDER */],
			final float xX[/* MAX_NB_SUBFR * LTP_ORDER */],
			final float r_ptr[], final int roffset,// java
			final int lag[/* MAX_NB_SUBFR */],
			final int subfr_length,
			final int nb_subfr)
	{
		int xX_ptr = 0;// java xX[ xX_ptr ];
		int XX_ptr = 0;// java XX[ XX_ptr ];
		int r_ptr_offet = roffset - (Jdefine.LTP_ORDER / 2);// java
		for( int k = 0; k < nb_subfr; k++ ) {
			final int lag_ptr = r_ptr_offet - lag[ k ];// java r_ptr - ( lag[ k ] + Jdefine.LTP_ORDER / 2 );
			silk_corrMatrix_FLP( r_ptr, lag_ptr, subfr_length, Jdefine.LTP_ORDER, XX, XX_ptr );
			silk_corrVector_FLP( r_ptr, lag_ptr, r_ptr, r_ptr_offet, subfr_length, Jdefine.LTP_ORDER, xX, xX_ptr );
			final float xx = ( float )silk_energy_FLP( r_ptr, r_ptr_offet, subfr_length + Jdefine.LTP_ORDER );
			float temp = Jtuning_parameters.LTP_CORR_INV_MAX * 0.5f * ( XX[ XX_ptr ] + XX[ XX_ptr + 24 ] ) + 1.0f;// java
			temp = ( xx > temp ? xx : temp );
			temp = 1.0f / temp;
			silk_scale_vector_FLP( XX, XX_ptr, temp, Jdefine.LTP_ORDER * Jdefine.LTP_ORDER );
			silk_scale_vector_FLP( xX, xX_ptr, temp, Jdefine.LTP_ORDER );

			r_ptr_offet += subfr_length;// java r_ptr  += subfr_length;
			XX_ptr += Jdefine.LTP_ORDER * Jdefine.LTP_ORDER;
			xX_ptr += Jdefine.LTP_ORDER;
		}
	}
	// end find_LTP_FLP

	// start find_pred_coefs_FLP
	/**
	 * Find LPC and LTP coefficients
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param res_pitch I    Residual from pitch analysis
	 * @param roffset I java an offset for the res_pitch
	 * @param x I    Speech signal
	 * @param xoffset I java an offset for the x
	 * @param condCoding I    The type of conditional coding to use
	 */
	private final void silk_find_pred_coefs_FLP(final Jsilk_encoder_control_FLP psEncCtrl,
			final float res_pitch[], final int roffset,
			final float x[], final int xoffset,
			final int condCoding
		)
	{
		final float XXLTP[] = new float[ Jdefine.MAX_NB_SUBFR * Jdefine.LTP_ORDER * Jdefine.LTP_ORDER ];
	    final float xXLTP[] = new float[ Jdefine.MAX_NB_SUBFR * Jdefine.LTP_ORDER ];
		final float invGains[] = new float[ Jdefine.MAX_NB_SUBFR ];
		final short NLSF_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final float LPC_in_pre[] = new float[ Jdefine.MAX_NB_SUBFR * Jdefine.MAX_LPC_ORDER + Jdefine.MAX_FRAME_LENGTH ];

		final Jsilk_encoder_state this_sCmn = this.sCmn;// java

		/* Weighting for weighted least squares */
		for( int i = 0, ie = this_sCmn.nb_subfr; i < ie; i++ ) {
			// silk_assert( psEncCtrl.Gains[ i ] > 0.0f );
			invGains[ i ] = 1.0f / psEncCtrl.Gains[ i ];
		}

		if( this_sCmn.indices.signalType == Jdefine.TYPE_VOICED ) {
			/**********/
			/* VOICED */
			/**********/
			// celt_assert( psEnc.sCmn.ltp_mem_length - psEnc.sCmn.predictLPCOrder >= psEncCtrl.pitchL[ 0 ] + Jdefine.LTP_ORDER / 2 );

			/* LTP analysis */
			silk_find_LTP_FLP( XXLTP, xXLTP, res_pitch, roffset, psEncCtrl.pitchL, this_sCmn.subfr_length, this_sCmn.nb_subfr );

			/* Quantize LTP gain parameters */
			final Jgains_data_struct_aux aux = new Jgains_data_struct_aux( this_sCmn.indices.PERIndex, this_sCmn.sum_log_gain_Q7, psEncCtrl.LTPredCodGain );// java
			silk_quant_LTP_gains_FLP( psEncCtrl.LTPCoef, this_sCmn.indices.LTPIndex,
					// &this_sCmn.indices.PERIndex, &this_sCmn.sum_log_gain_Q7, &psEncCtrl.LTPredCodGain,
					aux,
					XXLTP, xXLTP, this_sCmn.subfr_length, this_sCmn.nb_subfr );//, this_sCmn.arch );
			this_sCmn.indices.PERIndex = aux.mPeriodicityIndex;// java
			this_sCmn.sum_log_gain_Q7 = aux.mSumLogGain;// java
			psEncCtrl.LTPredCodGain = aux.mPred_gain_dB;

			/* Control LTP scaling */
			silk_LTP_scale_ctrl_FLP( psEncCtrl, condCoding );

			/* Create LTP residual */
			silk_LTP_analysis_filter_FLP( LPC_in_pre, x, xoffset - this_sCmn.predictLPCOrder, psEncCtrl.LTPCoef,
									psEncCtrl.pitchL, invGains, this_sCmn.subfr_length, this_sCmn.nb_subfr, this_sCmn.predictLPCOrder );
		} else {
			/************/
			/* UNVOICED */
			/************/
			/* Create signal with prepended subframes, scaled by inverse gains */
			int x_ptr     = xoffset - this_sCmn.predictLPCOrder;// x[ x_ptr ]
			int x_pre_ptr = 0;// LPC_in_pre[ x_pre_ptr ]
			for( int i = 0, ie = this_sCmn.nb_subfr; i < ie; i++ ) {
				silk_scale_copy_vector_FLP( LPC_in_pre, x_pre_ptr, x, x_ptr, invGains[ i ],
											this_sCmn.subfr_length + this_sCmn.predictLPCOrder );
				x_pre_ptr += this_sCmn.subfr_length + this_sCmn.predictLPCOrder;
				x_ptr     += this_sCmn.subfr_length;
			}
			// silk_memset( psEncCtrl.LTPCoef, 0, psEnc.sCmn.nb_subfr * Jdefine.LTP_ORDER * sizeof( silk_float ) );
			final float[] fbuff = psEncCtrl.LTPCoef;// java
			for( int i = 0, ie = this_sCmn.nb_subfr * Jdefine.LTP_ORDER; i < ie; i++ ) {
				fbuff[i] = 0;
			}
			psEncCtrl.LTPredCodGain = 0.0f;
			this_sCmn.sum_log_gain_Q7 = 0;
		}

		/* Limit on total predictive coding gain */
		float minInvGain;
		if( this_sCmn.first_frame_after_reset ) {
			minInvGain = 1.0f / Jdefine.MAX_PREDICTION_POWER_GAIN_AFTER_RESET;
		} else {
			minInvGain = (float)Math.pow( 2., (double)(psEncCtrl.LTPredCodGain / 3f) ) / Jdefine.MAX_PREDICTION_POWER_GAIN;
			minInvGain /= 0.25f + 0.75f * psEncCtrl.coding_quality;
		}

		/* LPC_in_pre contains the LTP-filtered input for voiced, and the unfiltered input for unvoiced */
		this_sCmn.silk_find_LPC_FLP( NLSF_Q15, LPC_in_pre, minInvGain );

		/* Quantize LSFs */
		this_sCmn.silk_process_NLSFs_FLP( psEncCtrl.PredCoef, NLSF_Q15, this_sCmn.prev_NLSFq_Q15 );

		/* Calculate residual energy using quantized LPC coefficients */
		silk_residual_energy_FLP( psEncCtrl.ResNrg, LPC_in_pre, psEncCtrl.PredCoef, psEncCtrl.Gains,
											this_sCmn.subfr_length, this_sCmn.nb_subfr, this_sCmn.predictLPCOrder );

		/* Copy to prediction struct for use in next frame for interpolation */
		System.arraycopy( NLSF_Q15, 0, this_sCmn.prev_NLSFq_Q15, 0, this_sCmn.prev_NLSFq_Q15.length );
	}
	// end find_pred_coefs_FLP

	// start k2a_FLP
	/**
	 * step up function, converts reflection coefficients to prediction coefficients
	 *
	 * @param A O     prediction coefficients [order]
	 * @param aoffset I java an offset for the A
	 * @param rc I     reflection coefficients [order]
	 * @param order I     prediction order
	 */
	private static final void silk_k2a_FLP(final float[] A, final int aoffset, final float[] rc, final int order)
	{
		for( int k = 0; k < order; k++ ) {
			final float rck = rc[ k ];
			for( int n = aoffset, ne = aoffset + ((k + 1) >> 1), i = aoffset + k - 1; n < ne; n++, i-- ) {
				final float tmp1 = A[ n ];
				final float tmp2 = A[ i ];
				A[ n ] = tmp1 + tmp2 * rck;
				A[ i ] = tmp2 + tmp1 * rck;
			}
			A[ aoffset + k ] = -rck;
		}
	}
	// end k2a_FLP

	// start schur_FLP
	/**
	 *
	 * @param refl_coef O    reflection coefficients (length order)
	 * @param auto_corr I    autocorrelation sequence (length order+1)
	 * @param order I    order
	 * @return O    returns residual energy
	 */
	private static final float silk_schur_FLP(final float refl_coef[], final float auto_corr[], final int order)
	{
		final double C[][] = new double[ JSigProc_FIX.SILK_MAX_ORDER_LPC + 1 ][ 2 ];

		// celt_assert( order >= 0 && order <= SILK_MAX_ORDER_LPC );

		/* Copy correlations */
		int k = 0;
		do {
			C[ k ][ 0 ] = C[ k ][ 1 ] = auto_corr[ k ];
		} while( ++k <= order );

		final double c0[] = C[ 0 ];// java
		for( k = 0; k < order; k++ ) {
			int k1 = k + 1;// java
			/* Get reflection coefficient */
			final double rc_tmp = -C[ k1 ][ 0 ] / ( c0[ 1 ] > 1e-9f ? c0[ 1 ] : 1e-9f );

			/* Save the output */
			refl_coef[ k ] = (float)rc_tmp;

			/* Update correlations */
			for( int n = 0, end = order - k; n < end; n++, k1++ ) {
				final double Ctmp1 = C[ k1 ][ 0 ];
				final double Ctmp2 = C[ n ][ 1 ];
				C[ k1 ][ 0 ] = Ctmp1 + Ctmp2 * rc_tmp;
				C[ n ][ 1 ]  = Ctmp2 + Ctmp1 * rc_tmp;
			}
		}

		/* Return residual energy */
		return (float)c0[ 1 ];// TODO may be use float?
	}
	// end schur_FLP

	// start find_pitch_lags_FLP
	/**
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param res O    Residual
	 * @param x I    Speech signal
	 * @param xoffset I java an offset for the x
	 * @param arch I    Run-time architecture
	 */
	private final void silk_find_pitch_lags_FLP(final Jsilk_encoder_control_FLP psEncCtrl,
			final float res[], final float x[], final int xoffset)//, final int arch)
	{
		final float auto_corr[] = new float[ Jdefine.MAX_FIND_PITCH_LPC_ORDER + 1 ];
		final float A[] = new float[         Jdefine.MAX_FIND_PITCH_LPC_ORDER ];
		final float refl_coef[] = new float[ Jdefine.MAX_FIND_PITCH_LPC_ORDER ];
		final float Wsig[] = new float[      Jdefine.FIND_PITCH_LPC_WIN_MAX ];
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		/******************************************/
		/* Set up buffer lengths etc based on Fs  */
		/******************************************/
		final int buf_len = this_sCmn.la_pitch + this_sCmn.frame_length + this_sCmn.ltp_mem_length;

		/* Safety check */
		// celt_assert( buf_len >= psEnc.sCmn.pitch_LPC_win_length );

		final int x_buff = xoffset - this_sCmn.ltp_mem_length;// x[ x_buf ]

		/******************************************/
		/* Estimate LPC AR coeficients            */
		/******************************************/

		/* Calculate windowed signal */

		/* First LA_LTP samples */
		int x_buf_ptr = x_buff + buf_len - this_sCmn.pitch_LPC_win_length;// x[ x_buf_ptr ]
		int Wsig_ptr  = 0;// Wsig[ Wsig_ptr ]
		silk_apply_sine_window_FLP( Wsig, Wsig_ptr, x, x_buf_ptr, 1, this_sCmn.la_pitch );

		/* Middle non-windowed samples */
		Wsig_ptr  += this_sCmn.la_pitch;
		x_buf_ptr += this_sCmn.la_pitch;
		System.arraycopy( x, x_buf_ptr, Wsig, Wsig_ptr, this_sCmn.pitch_LPC_win_length - ( this_sCmn.la_pitch << 1 ) );

		/* Last LA_LTP samples */
		Wsig_ptr  += this_sCmn.pitch_LPC_win_length - ( this_sCmn.la_pitch << 1 );
		x_buf_ptr += this_sCmn.pitch_LPC_win_length - ( this_sCmn.la_pitch << 1 );
		silk_apply_sine_window_FLP( Wsig, Wsig_ptr, x, x_buf_ptr, 2, this_sCmn.la_pitch );

		/* Calculate autocorrelation sequence */
		silk_autocorrelation_FLP( auto_corr, Wsig, this_sCmn.pitch_LPC_win_length, this_sCmn.pitchEstimationLPCOrder + 1 );

		/* Add white noise, as a fraction of the energy */
		float v = auto_corr[ 0 ];// java
		v += v * Jtuning_parameters.FIND_PITCH_WHITE_NOISE_FRACTION + 1;
		auto_corr[ 0 ] = v;

		/* Calculate the reflection coefficients using Schur */
		final float res_nrg = silk_schur_FLP( refl_coef, auto_corr, this_sCmn.pitchEstimationLPCOrder );

		/* Prediction gain */
		psEncCtrl.predGain = v / ( res_nrg >= 1.0f ? res_nrg : 1.0f );

		/* Convert reflection coefficients to prediction coefficients */
		silk_k2a_FLP( A, 0, refl_coef, this_sCmn.pitchEstimationLPCOrder );

		/* Bandwidth expansion */
		silk_bwexpander_FLP( A, 0, this_sCmn.pitchEstimationLPCOrder, Jtuning_parameters.FIND_PITCH_BANDWIDTH_EXPANSION );

		/*****************************************/
		/* LPC analysis filtering                */
		/*****************************************/
		silk_LPC_analysis_filter_FLP( res, A, x, x_buff, buf_len, this_sCmn.pitchEstimationLPCOrder );

		if( this_sCmn.indices.signalType != Jdefine.TYPE_NO_VOICE_ACTIVITY && ! this_sCmn.first_frame_after_reset ) {
			/* Threshold for pitch estimator */
			float thrhld  = 0.6f;
			thrhld -= 0.004f * this_sCmn.pitchEstimationLPCOrder;
			thrhld -= 0.1f   * this_sCmn.speech_activity_Q8 * ( 1.0f / 256.0f );
			thrhld -= 0.15f  * (this_sCmn.prevSignalType >> 1);
			thrhld -= 0.1f   * this_sCmn.input_tilt_Q15 * ( 1.0f / 32768.0f );

			/*****************************************/
			/* Call Pitch estimator                  */
			/*****************************************/
			final Jpitch_data_struct_aux aux = new Jpitch_data_struct_aux( /*psEnc.sCmn.indices.lagIndex, psEnc.sCmn.indices.contourIndex,*/ this.LTPCorr );// java
			if( silk_pitch_analysis_core_FLP( res, psEncCtrl.pitchL,
					// this..sCmn.indices.lagIndex, &psEnc.sCmn.indices.contourIndex, &psEnc.LTPCorr,
					aux,// java to return data
					this_sCmn.prevLag,
					this_sCmn.pitchEstimationThreshold_Q16 / 65536.0f,
					thrhld, this_sCmn.fs_kHz, this_sCmn.pitchEstimationComplexity, this_sCmn.nb_subfr/*, arch*/ ) == 0 )
			{
				this_sCmn.indices.signalType = Jdefine.TYPE_VOICED;
			} else {
				this_sCmn.indices.signalType = Jdefine.TYPE_UNVOICED;
			}
			this_sCmn.indices.lagIndex = aux.mLagIndex;// java
			this_sCmn.indices.contourIndex = aux.mContourIndex;// java
			this.LTPCorr = aux.mLTPCorr;// java
		} else {
			// silk_memset( psEncCtrl.pitchL, 0, sizeof( psEncCtrl.pitchL ) );
			final int[] buff = psEncCtrl.pitchL;// java
			for( int i = 0, ie = buff.length; i < ie; i++ ) {
				buff[i] = 0;
			}
			this_sCmn.indices.lagIndex = 0;
			this_sCmn.indices.contourIndex = 0;
			this.LTPCorr = 0;
		}
	}
	// end find_pitch_lags_FLP

	// start gain_quant.c
	/**
	 * Compute unique identifier of gain indices vector
	 *
	 * @param ind I    gain indices
	 * @param nb_subfr I    number of subframes
	 * @return O    returns unique identifier of gains
	 */
	private static final int silk_gains_ID(final byte ind[/* MAX_NB_SUBFR */], final int nb_subfr)
	{
		int gainsID = 0;
		for( int k = 0; k < nb_subfr; k++ ) {
			gainsID = (int)ind[ k ] + (gainsID << 8);
		}

		return gainsID;
	}
	// end gain_quant.c

	// start encode_frame_FLP

	/* Low Bitrate Redundancy (LBRR) encoding. Reuse all parameters but encode with lower bitrate */

	/**
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param activity I    Decision of Opus voice activity detector
	 */
	final void silk_encode_do_VAD_FLP(final int activity)
	{
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java

		final int activity_threshold = (int)(Jtuning_parameters.SPEECH_ACTIVITY_DTX_THRES * (1 << 8) + .5f);
		/****************************/
		/* Voice Activity Detection */
		/****************************/
		this_sCmn.silk_VAD_GetSA_Q8( this_sCmn.inputBuf, 1 );//, psEnc.sCmn.arch );
	    /* If Opus VAD is inactive and Silk VAD is active: lower Silk VAD to just under the threshold */
		if( activity == Jdefine.VAD_NO_ACTIVITY && this_sCmn.speech_activity_Q8 >= activity_threshold ) {
			this_sCmn.speech_activity_Q8 = activity_threshold - 1;
		}

		/**************************************************/
		/* Convert speech activity into VAD and DTX flags */
		/**************************************************/
		if( this_sCmn.speech_activity_Q8 < activity_threshold ) {
				this_sCmn.indices.signalType = Jdefine.TYPE_NO_VOICE_ACTIVITY;
				this_sCmn.noSpeechCounter++;
			if( this_sCmn.noSpeechCounter <= Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX ) {
				this_sCmn.inDTX = false;
			} else if( this_sCmn.noSpeechCounter > Jdefine.MAX_CONSECUTIVE_DTX + Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX ) {
				this_sCmn.noSpeechCounter = Jdefine.NB_SPEECH_FRAMES_BEFORE_DTX;
				this_sCmn.inDTX           = false;
			}
			this_sCmn.VAD_flags[ this_sCmn.nFramesEncoded ] = 0;
		} else {
			this_sCmn.noSpeechCounter    = 0;
			this_sCmn.inDTX              = false;
			this_sCmn.indices.signalType = Jdefine.TYPE_UNVOICED;
			this_sCmn.VAD_flags[ this_sCmn.nFramesEncoded ] = 1;
		}
	}

	/**
	 * Low-Bitrate Redundancy (LBRR) encoding. Reuse all parameters but encode excitation at lower bitrate
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param psEncCtrl I/O  Encoder control FLP
	 * @param xfw I    Input signal
	 * @param xoffset I java an offset for the xfw
	 * @param condCoding I    The type of conditional coding used so far for this frame
	 */
	private final void silk_LBRR_encode_FLP(final Jsilk_encoder_control_FLP psEncCtrl, final float xfw[], final int xoffset, final int condCoding)
	{
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		/*******************************************/
		/* Control use of inband LBRR              */
		/*******************************************/
		// if( this_sCmn.LBRR_enabled && this_sCmn.speech_activity_Q8 > SILK_FIX_CONST( Jtuning_parameters.LBRR_SPEECH_ACTIVITY_THRES, 8 ) ) {
		if( this_sCmn.LBRR_enabled && this_sCmn.speech_activity_Q8 > ((int)(Jtuning_parameters.LBRR_SPEECH_ACTIVITY_THRES * (1 << 8) + .5f)) ) {
			this_sCmn.LBRR_flags[ this_sCmn.nFramesEncoded ] = true;

			/* Copy noise shaping quantizer state and quantization indices from regular encoding */
			final Jsilk_nsq_state sNSQ_LBRR = new Jsilk_nsq_state();
			sNSQ_LBRR.copyFrom( this_sCmn.sNSQ );
			final JSideInfoIndices psIndices_LBRR = this_sCmn.indices_LBRR[ this_sCmn.nFramesEncoded ];
			psIndices_LBRR.copyFrom( this_sCmn.indices );

			/* Save original gains */
			final float TempGains[] = new float[ Jdefine.MAX_NB_SUBFR ];
			System.arraycopy( psEncCtrl.Gains, 0, TempGains, 0, this_sCmn.nb_subfr );

			if( this_sCmn.nFramesEncoded == 0 || ! this_sCmn.LBRR_flags[ this_sCmn.nFramesEncoded - 1 ] ) {
				/* First frame in packet or previous frame not LBRR coded */
				this_sCmn.LBRRprevLastGainIndex = this.sShape.LastGainIndex;

				/* Increase Gains to get target LBRR rate */
				psIndices_LBRR.GainsIndices[ 0 ] += this_sCmn.LBRR_GainIncreases;
				final int v = (int)psIndices_LBRR.GainsIndices[ 0 ];// java
				psIndices_LBRR.GainsIndices[ 0 ] = (byte)( v < (Jdefine.N_LEVELS_QGAIN - 1) ? v : (Jdefine.N_LEVELS_QGAIN - 1) );
			}

			/* Decode to get gains in sync with decoder */
			final int Gains_Q16[] = new int[ Jdefine.MAX_NB_SUBFR ];
			this_sCmn.LBRRprevLastGainIndex = silk_gains_dequant( Gains_Q16, psIndices_LBRR.GainsIndices,
								this_sCmn.LBRRprevLastGainIndex, condCoding == Jdefine.CODE_CONDITIONALLY, this_sCmn.nb_subfr );// java changed

			/* Overwrite unquantized gains with quantized gains and convert back to Q0 from Q16 */
			final float[] gains = psEncCtrl.Gains;// java
			for( int k = 0, n = this_sCmn.nb_subfr; k < n; k++ ) {
				gains[ k ] = Gains_Q16[ k ] * ( 1.0f / 65536.0f );
			}

			/*****************************************/
			/* Noise shaping quantization            */
			/*****************************************/
			silk_NSQ_wrapper_FLP( psEncCtrl, psIndices_LBRR, sNSQ_LBRR,
									this_sCmn.pulses_LBRR[ this_sCmn.nFramesEncoded ], xfw, xoffset );

			/* Restore original gains */
			System.arraycopy( TempGains, 0, psEncCtrl.Gains, 0, this_sCmn.nb_subfr );
		}
	}
	/**
	 * Encode frame
	 *
	 * @param psEnc I/O  Encoder state FLP
	 * @param pnBytesOut O    Number of payload bytes
	 * @param psRangeEnc I/O  compressor data structure
	 * @param condCoding I    The type of conditional coding to use
	 * @param maxBits I    If > 0: maximum number of output bits
	 * @param useCBR I    Flag to force constant-bitrate operation
	 * @return
	 */
	final int silk_encode_frame_FLP(final int[] pnBytesOut, final Jec_enc psRangeEnc,
			final int condCoding, final int maxBits, final boolean useCBR
		)
	{
		final int ret = 0;

		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		final int nb_subfr = this_sCmn.nb_subfr;// java
		this_sCmn.indices.Seed = (byte)(this_sCmn.frameCounter++ & 3);

		/**************************************************************/
		/* Set up Input Pointers, and insert frame in input buffer    */
		/**************************************************************/
		/* pointers aligned with start of frame to encode */
		final int x_frame    = this_sCmn.ltp_mem_length;// start of frame to encode. java psEnc.x_buf[ x_frame ]
		final float[] x_buff = this.x_buf;// java
		final float res_pitch[] = new float[ 2 * Jdefine.MAX_FRAME_LENGTH + Jdefine.LA_PITCH_MAX ];
		final int res_pitch_frame = this_sCmn.ltp_mem_length;// start of pitch LPC residual frame. java res_pitch[ res_pitch_frame ]

		/***************************************/
		/* Ensure smooth bandwidth transitions */
		/***************************************/
		final short[] inputBuf = this_sCmn.inputBuf;// java
		this_sCmn.sLP.silk_LP_variable_cutoff( inputBuf, 1, this_sCmn.frame_length );

		/*******************************************/
		/* Copy new frame to front of input buffer */
		/*******************************************/
		// silk_short2float_array( x_frame + Jdefine.LA_SHAPE_MS * psEnc.sCmn.fs_kHz, psEnc.sCmn.inputBuf + 1, psEnc.sCmn.frame_length );
		for( int i = this_sCmn.frame_length, xi = x_frame + Jdefine.LA_SHAPE_MS * this_sCmn.fs_kHz + this_sCmn.frame_length - 1; i > 0; i-- ) {
			x_buff[ xi-- ] = (float)inputBuf[ i ];
		}

		/* Add tiny signal to avoid high CPU load from denormalized floating point numbers */
		for( int i = 0, xi = x_frame + Jdefine.LA_SHAPE_MS * this_sCmn.fs_kHz, f8 = this_sCmn.frame_length >> 3; i < 8; i++, xi += f8 ) {
			x_buff[ xi ] += (1 - (i & 2)) * 1e-6f;
		}

		final Jsilk_encoder_control_FLP sEncCtrl = new Jsilk_encoder_control_FLP();
		int nBits_lower, nBits_upper, gainMult_lower, gainMult_upper;
		/* This is totally unnecessary but many compilers (including gcc) are too dumb to realise it */
		nBits_lower = nBits_upper = gainMult_lower = gainMult_upper = 0;
		byte LastGainIndex_copy2 = 0;// java
		if( ! this_sCmn.prefillFlag ) {
			/*****************************************/
			/* Find pitch lags, initial LPC analysis */
			/*****************************************/
			silk_find_pitch_lags_FLP( sEncCtrl, res_pitch, x_buff, x_frame );//, psEnc.sCmn.arch );

			/************************/
			/* Noise shape analysis */
			/************************/
			silk_noise_shape_analysis_FLP( sEncCtrl, res_pitch, res_pitch_frame, x_buff, x_frame );

			/***************************************************/
			/* Find linear prediction coefficients (LPC + LTP) */
			/***************************************************/
			silk_find_pred_coefs_FLP( sEncCtrl, res_pitch, res_pitch_frame, x_buff, x_frame, condCoding );

			/****************************************/
			/* Process gains                        */
			/****************************************/
			silk_process_gains_FLP( sEncCtrl, condCoding );

			/****************************************/
			/* Low Bitrate Redundant Encoding       */
			/****************************************/
			silk_LBRR_encode_FLP( sEncCtrl, x_buff, x_frame, condCoding );

			/* Loop over quantizer and entroy coding to control bitrate */
			final int maxIter = 6;
			// int gainMult_Q8 = SILK_FIX_CONST( 1, 8 );// FIXME may be better to use int?
			int gainMult_Q8 = ( 1 << 8 );// FIXME may be better to use int?
			boolean found_lower = false;
			boolean found_upper = false;
			int gainsID = silk_gains_ID( this_sCmn.indices.GainsIndices, nb_subfr );
			int gainsID_lower = -1;
			int gainsID_upper = -1;
			/* Copy part of the input state */
			final Jec_enc sRangeEnc_copy = new Jec_enc();
			sRangeEnc_copy.copyFrom( psRangeEnc );
			final Jsilk_nsq_state sNSQ_copy = new Jsilk_nsq_state();
			sNSQ_copy.copyFrom( this_sCmn.sNSQ );
			final byte seed_copy = this_sCmn.indices.Seed;// FIXME why int seed_copy?
			final short ec_prevLagIndex_copy = this_sCmn.ec_prevLagIndex;
			final int ec_prevSignalType_copy = this_sCmn.ec_prevSignalType;
			final Jec_enc sRangeEnc_copy2 = new Jec_enc();
			final Jsilk_nsq_state sNSQ_copy2 = new Jsilk_nsq_state();
			final int[] pGains_Q16 = new int[ Jdefine.MAX_NB_SUBFR ];
			final byte ec_buf_copy[] = new byte[ 1275 ];
			final int[] GainsUnq_Q16 = sEncCtrl.GainsUnq_Q16;// java
			final float[] sEncCtrl_Gains = sEncCtrl.Gains;// java
			final boolean gain_lock[] = new boolean[ Jdefine.MAX_NB_SUBFR ];// java already zeroed = {0};
			final short best_gain_mult[] = new short[ Jdefine.MAX_NB_SUBFR ];
			final int best_sum[] = new int[ Jdefine.MAX_NB_SUBFR ];// FIXME used, but is not initialized!
			for( int iter = 0; ; iter++ ) {
				int nBits;
				if( gainsID == gainsID_lower ) {
					nBits = nBits_lower;
				} else if( gainsID == gainsID_upper ) {
					nBits = nBits_upper;
				} else {
					/* Restore part of the input state */
					if( iter > 0 ) {
						psRangeEnc.copyFrom( sRangeEnc_copy );
						this_sCmn.sNSQ.copyFrom( sNSQ_copy );
						this_sCmn.indices.Seed = seed_copy;
						this_sCmn.ec_prevLagIndex = ec_prevLagIndex_copy;
						this_sCmn.ec_prevSignalType = ec_prevSignalType_copy;
					}

					/*****************************************/
					/* Noise shaping quantization            */
					/*****************************************/
					silk_NSQ_wrapper_FLP( sEncCtrl, this_sCmn.indices, this_sCmn.sNSQ, this_sCmn.pulses, x_buff, x_frame );
	                if( iter == maxIter && ! found_lower ) {
	                    sRangeEnc_copy2.copyFrom( psRangeEnc );
	                }

					/****************************************/
					/* Encode Parameters                    */
					/****************************************/
					this_sCmn.silk_encode_indices( psRangeEnc, this_sCmn.nFramesEncoded, false, condCoding );

					/****************************************/
					/* Encode Excitation Signal             */
					/****************************************/
					silk_encode_pulses( psRangeEnc, this_sCmn.indices.signalType, this_sCmn.indices.quantOffsetType,
														this_sCmn.pulses, this_sCmn.frame_length );

					nBits = psRangeEnc.ec_tell();

					/* If we still bust after the last iteration, do some damage control. */
					if( iter == maxIter && ! found_lower && nBits > maxBits ) {
						psRangeEnc.copyFrom( sRangeEnc_copy2 );

						/* Keep gains the same as the last frame. */
						this.sShape.LastGainIndex = sEncCtrl.lastGainIndexPrev;
						for( int i = 0; i < nb_subfr; i++ ) {
							this_sCmn.indices.GainsIndices[ i ] = 4;
						}
						if( condCoding != Jdefine.CODE_CONDITIONALLY ) {
							this_sCmn.indices.GainsIndices[ 0 ] = sEncCtrl.lastGainIndexPrev;
						}
						this_sCmn.ec_prevLagIndex = ec_prevLagIndex_copy;
						this_sCmn.ec_prevSignalType = ec_prevSignalType_copy;
						/* Clear all pulses. */
						final byte[] pulses = this_sCmn.pulses;// java
						for( int i = 0, ie = this_sCmn.frame_length; i < ie; i++ ) {
							pulses[ i ] = 0;
						}

						this_sCmn.silk_encode_indices( psRangeEnc, this_sCmn.nFramesEncoded, false, condCoding );

						silk_encode_pulses( psRangeEnc, this_sCmn.indices.signalType, this_sCmn.indices.quantOffsetType,
								this_sCmn.pulses, this_sCmn.frame_length );

						nBits = psRangeEnc.ec_tell();
					}

					if( ! useCBR && iter == 0 && nBits <= maxBits ) {
						break;
					}
				}

				if( iter == maxIter ) {
					if( found_lower && ( gainsID == gainsID_lower || nBits > maxBits ) ) {
						/* Restore output state from earlier iteration that did meet the bitrate budget */
						psRangeEnc.copyFrom( sRangeEnc_copy2 );
						// celt_assert( sRangeEnc_copy2.offs <= 1275 );
						System.arraycopy( ec_buf_copy, 0, psRangeEnc.buf, psRangeEnc.buf_start, sRangeEnc_copy2.offs );
						this_sCmn.sNSQ.copyFrom( sNSQ_copy2 );
						this.sShape.LastGainIndex = LastGainIndex_copy2;
					}
					break;
				}

				if( nBits > maxBits ) {
					if( ! found_lower && iter >= 2 ) {
						/* Adjust the quantizer's rate/distortion tradeoff and discard previous "upper" results */
						final float v = sEncCtrl.Lambda * 1.5f;// java
						sEncCtrl.Lambda = (v > 1.5f ? v : 1.5f);
						/* Reducing dithering can help us hit the target. */
						this_sCmn.indices.quantOffsetType = 0;
						found_upper = false;
						gainsID_upper = -1;
					} else {
						found_upper = true;
						nBits_upper = nBits;
						gainMult_upper = gainMult_Q8;
						gainsID_upper = gainsID;
					}
				} else if( nBits < maxBits - 5 ) {
					found_lower = true;
					nBits_lower = nBits;
					gainMult_lower = gainMult_Q8;
					if( gainsID != gainsID_lower ) {
						gainsID_lower = gainsID;
						/* Copy part of the output state */
						sRangeEnc_copy2.copyFrom( psRangeEnc );
						// celt_assert( psRangeEnc.offs <= 1275 );
						System.arraycopy( psRangeEnc.buf, psRangeEnc.buf_start, ec_buf_copy, 0, psRangeEnc.offs );
						sNSQ_copy2.copyFrom( this_sCmn.sNSQ );
						LastGainIndex_copy2 = this.sShape.LastGainIndex;
					}
				} else {
					/* Within 5 bits of budget: close enough */
					break;
				}

				if ( ! found_lower && nBits > maxBits ) {
					final byte[] pulses = this_sCmn.pulses;// java
					for( int i = 0; i < nb_subfr; i++ ) {
						int sum = 0;
						for( int j = i * this_sCmn.subfr_length, je = (i + 1) * this_sCmn.subfr_length; j < je; j++ ) {
							int v = (int)pulses[j];// java abs
							if( v < 0 ) {
								v = -v;
							}
							sum += v;
						}
						if( iter == 0 || (sum < best_sum[i] && ! gain_lock[i]) ) {
							best_sum[i] = sum;
							best_gain_mult[i] = (short)gainMult_Q8;
						} else {
							gain_lock[i] = true;
						}
					}
				}
				if( ! ( found_lower & found_upper ) ) {
					/* Adjust gain according to high-rate rate/distortion curve */
					if( nBits > maxBits ) {
						if( gainMult_Q8 < 16384 ) {
							gainMult_Q8 <<= 1;
						} else {
							gainMult_Q8 = 32767;
						}
					} else {
						final int gain_factor_Q16 = Jmacros.silk_log2lin( ((nBits - maxBits) << 7) / this_sCmn.frame_length + ( 16 << 7 ) );
						gainMult_Q8 = (short)((gain_factor_Q16 * (long)gainMult_Q8) >> 16);
					}
				} else {
					/* Adjust gain by interpolating */
					gainMult_Q8 = (short)(gainMult_lower + ( ( gainMult_upper - gainMult_lower ) * ( maxBits - nBits_lower ) ) / ( nBits_upper - nBits_lower ));
					/* New gain multplier must be between 25% and 75% of old range (note that gainMult_upper < gainMult_lower) */
					if( gainMult_Q8 > ( gainMult_lower + ((gainMult_upper - gainMult_lower) >> 2) ) ) {
						gainMult_Q8 = (short)( gainMult_lower + ((gainMult_upper - gainMult_lower) >> 2) );
					} else
						if( gainMult_Q8 < ( gainMult_upper - ((gainMult_upper - gainMult_lower) >> 2) ) ) {
							gainMult_Q8 = (short)( gainMult_upper - ((gainMult_upper - gainMult_lower) >> 2) );
						}
				}

				for( int i = 0; i < nb_subfr; i++ ) {
					final int tmp = gain_lock[i] ? best_gain_mult[i] : gainMult_Q8;
					final int a = ( (int)((GainsUnq_Q16[ i ] * (long)tmp) >> 16) );
					pGains_Q16[ i ] = ((a > (Integer.MAX_VALUE >> 8) ?
							(Integer.MAX_VALUE >> 8) : (a < (Integer.MIN_VALUE >> 8) ?
									(Integer.MIN_VALUE >> 8) : a)) << 8);
				}

				/* Quantize gains */
				this.sShape.LastGainIndex = sEncCtrl.lastGainIndexPrev;
				this.sShape.LastGainIndex = silk_gains_quant( this_sCmn.indices.GainsIndices, pGains_Q16,
								this.sShape.LastGainIndex, condCoding == Jdefine.CODE_CONDITIONALLY, nb_subfr );// java changed

				/* Unique identifier of gains vector */
				gainsID = silk_gains_ID( this_sCmn.indices.GainsIndices, nb_subfr );

				/* Overwrite unquantized gains with quantized gains and convert back to Q0 from Q16 */
				for( int i = 0; i < nb_subfr; i++ ) {
					sEncCtrl_Gains[ i ] = pGains_Q16[ i ] / 65536.0f;
				}
			}
		}

		/* Update input buffer */
		System.arraycopy( this.x_buf, this_sCmn.frame_length, this.x_buf, 0, ( this_sCmn.ltp_mem_length + Jdefine.LA_SHAPE_MS * this_sCmn.fs_kHz ) );

		/* Exit without entropy coding */
		if( this_sCmn.prefillFlag ) {
			/* No payload */
			pnBytesOut[0] = 0;
			return ret;
		}

		/* Parameters needed for next frame */
		this_sCmn.prevLag        = sEncCtrl.pitchL[ nb_subfr - 1 ];// FIXME what happens with sEncCtrl if psEnc.sCmn.prefillFlag = true?
		this_sCmn.prevSignalType = this_sCmn.indices.signalType;

		/****************************************/
		/* Finalize payload                     */
		/****************************************/
		this_sCmn.first_frame_after_reset = false;
		/* Payload size */
		pnBytesOut[0] = ( (psRangeEnc.ec_tell() + 7) >> 3 );

		return ret;
	}
	// end encode_frame_FLP

	// start control_codec.c
	/**
	 * Control encoder
	 *
	 * @param psEnc I/O  Pointer to Silk encoder state
	 * @param encControl I    Control structure
	 * @param allow_bw_switch I    Flag to allow switching audio bandwidth
	 * @param channelNb I    Channel number
	 * @param force_fs_kHz
	 * @return
	 */
	final int silk_control_encoder(
			final Jsilk_EncControlStruct encControl,
			final boolean allow_bw_switch, final int channelNb, final int force_fs_kHz)
	{
		int ret = 0;
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java

		this_sCmn.useDTX                 = encControl.useDTX;
		this_sCmn.useCBR                 = encControl.useCBR;
		this_sCmn.API_fs_Hz              = encControl.API_sampleRate;
		this_sCmn.maxInternal_fs_Hz      = encControl.maxInternalSampleRate;
		this_sCmn.minInternal_fs_Hz      = encControl.minInternalSampleRate;
		this_sCmn.desiredInternal_fs_Hz  = encControl.desiredInternalSampleRate;
		this_sCmn.useInBandFEC           = encControl.useInBandFEC;
		this_sCmn.nChannelsAPI           = encControl.nChannelsAPI;
		this_sCmn.nChannelsInternal      = encControl.nChannelsInternal;
		this_sCmn.allow_bandwidth_switch = allow_bw_switch;
		this_sCmn.channelNb              = channelNb;

		if( this_sCmn.controlled_since_last_payload != 0 && ! this_sCmn.prefillFlag ) {
			if( this_sCmn.API_fs_Hz != this_sCmn.prev_API_fs_Hz && this_sCmn.fs_kHz > 0 ) {
				/* Change in API sampling rate in the middle of encoding a packet */
				ret += silk_setup_resamplers( this_sCmn.fs_kHz );
			}
			return ret;
		}

		/* Beyond this point we know that there are no previously coded frames in the payload buffer */

		/********************************************/
		/* Determine internal sampling rate         */
		/********************************************/
		int fs_kHz = this_sCmn.silk_control_audio_bandwidth( encControl );
		if( 0 != force_fs_kHz ) {
			fs_kHz = force_fs_kHz;
		}
		/********************************************/
		/* Prepare resampler and buffered data      */
		/********************************************/
		ret += silk_setup_resamplers( fs_kHz );

		/********************************************/
		/* Set internal sampling frequency          */
		/********************************************/
		ret += silk_setup_fs( fs_kHz, encControl.payloadSize_ms );

		/********************************************/
		/* Set encoding complexity                  */
		/********************************************/
		ret += this_sCmn.silk_setup_complexity( encControl.complexity  );

		/********************************************/
		/* Set packet loss rate measured by farend  */
		/********************************************/
		this_sCmn.PacketLoss_perc = encControl.packetLossPercentage;

		/********************************************/
		/* Set LBRR usage                           */
		/********************************************/
		ret += this_sCmn.silk_setup_LBRR( encControl );

		this_sCmn.controlled_since_last_payload = 1;

		return ret;
	}

	/**
	 *
	 * @param psEnc I/O
	 * @param fs_kHz I
	 * @return
	 */
	private final int silk_setup_resamplers(final int fs_kHz)
	{
		int ret = Jerrors.SILK_NO_ERROR;
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java
		// SAVE_STACK;

		if( this_sCmn.fs_kHz != fs_kHz || this_sCmn.prev_API_fs_Hz != this_sCmn.API_fs_Hz )
		{
			if( this_sCmn.fs_kHz == 0 ) {
				/* Initialize the resampler for enc_API.c preparing resampling from API_fs_Hz to fs_kHz */
				ret += this_sCmn.resampler_state.silk_resampler_init( this_sCmn.API_fs_Hz, fs_kHz * 1000, true );
			} else {
/* #ifdef FIXED_POINT
				opus_int16 *x_bufFIX = psEnc.x_buf;
#else */
				//VARDECL( opus_int16, x_bufFIX );
				//int new_buf_samples;
// #endif
				final int buf_length_ms = (this_sCmn.nb_subfr * 10) + Jdefine.LA_SHAPE_MS;// FIXME why not * 10?
				final int old_buf_samples = buf_length_ms * this_sCmn.fs_kHz;

// #ifndef FIXED_POINT
				final int new_buf_samples = buf_length_ms * fs_kHz;
				final short[] x_bufFIX = new short[ old_buf_samples > new_buf_samples ? old_buf_samples : new_buf_samples ];
				// silk_float2short_array( x_bufFIX, psEnc.x_buf, old_buf_samples );
				final float[] buf = this.x_buf;// java
				for( int k = old_buf_samples - 1; k >= 0; k-- ) {
					final int v = (int)buf[k];
					x_bufFIX[k] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
				}
// #endif

				/* Initialize resampler for temporary resampling of x_buf data to API_fs_Hz */
				final Jsilk_resampler_state_struct temp_resampler_state = new Jsilk_resampler_state_struct();
				ret += temp_resampler_state.silk_resampler_init( this_sCmn.fs_kHz * 1000, this_sCmn.API_fs_Hz, false );

				/* Calculate number of samples to temporarily upsample */
				final int api_buf_samples = buf_length_ms * (this_sCmn.API_fs_Hz / 1000);

				/* Temporary resampling of x_buf data to API_fs_Hz */
				final short[] x_buf_API_fs_Hz = new short[api_buf_samples];
				ret += temp_resampler_state.silk_resampler( x_buf_API_fs_Hz, 0, x_bufFIX, 0, old_buf_samples );

				/* Initialize the resampler for enc_API.c preparing resampling from API_fs_Hz to fs_kHz */
				ret += this_sCmn.resampler_state.silk_resampler_init( this_sCmn.API_fs_Hz, fs_kHz * 1000, true );

				/* Correct resampler state by resampling buffered data from API_fs_Hz to fs_kHz */
				ret += this_sCmn.resampler_state.silk_resampler( x_bufFIX, 0, x_buf_API_fs_Hz, 0, api_buf_samples );

// #ifndef FIXED_POINT
				// silk_short2float_array( psEnc.x_buf, x_bufFIX, new_buf_samples);
				//final float[] buf = psEnc.x_buf;// java
				for( int k = new_buf_samples - 1; k >= 0; k-- ) {
					buf[k] = (float)x_bufFIX[k];
				}
// #endif
			}
		}

		this_sCmn.prev_API_fs_Hz = this_sCmn.API_fs_Hz;

		// RESTORE_STACK;
		return ret;
	}

	/**
	 *
	 * @param psEnc I/O
	 * @param fs_kHz I
	 * @param PacketSize_ms I
	 * @return
	 */
	private final int silk_setup_fs(final int fs_kHz, final int PacketSize_ms)
	{
		int ret = Jerrors.SILK_NO_ERROR;
		final Jsilk_encoder_state this_sCmn = this.sCmn;// java

		/* Set packet size */
		if( PacketSize_ms != this_sCmn.PacketSize_ms ) {
			if( ( PacketSize_ms !=  10 ) &&
					( PacketSize_ms !=  20 ) &&
					( PacketSize_ms !=  40 ) &&
					( PacketSize_ms !=  60 ) ) {
				ret = Jerrors.SILK_ENC_PACKET_SIZE_NOT_SUPPORTED;
			}
			if( PacketSize_ms <= 10 ) {
				this_sCmn.nFramesPerPacket = 1;
				this_sCmn.nb_subfr = PacketSize_ms == 10 ? 2 : 1;
				this_sCmn.frame_length = PacketSize_ms * fs_kHz;
				this_sCmn.pitch_LPC_win_length = Jdefine.FIND_PITCH_LPC_WIN_MS_2_SF * fs_kHz;
				if( this_sCmn.fs_kHz == 8 ) {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_NB_iCDF;
				} else {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_iCDF;
				}
			} else {
				this_sCmn.nFramesPerPacket = PacketSize_ms / Jdefine.MAX_FRAME_LENGTH_MS;
				this_sCmn.nb_subfr = Jdefine.MAX_NB_SUBFR;
				this_sCmn.frame_length = 20 * fs_kHz;
				this_sCmn.pitch_LPC_win_length = Jdefine.FIND_PITCH_LPC_WIN_MS * fs_kHz;
				if( this_sCmn.fs_kHz == 8 ) {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_NB_iCDF;
				} else {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_iCDF;
				}
			}
			this_sCmn.PacketSize_ms  = PacketSize_ms;
			this_sCmn.TargetRate_bps = 0;         /* trigger new SNR computation */
		}

		/* Set internal sampling frequency */
		// celt_assert( fs_kHz == 8 || fs_kHz == 12 || fs_kHz == 16 );
		// celt_assert( psEnc.sCmn.nb_subfr == 2 || psEnc.sCmn.nb_subfr == 4 );
		if( this_sCmn.fs_kHz != fs_kHz ) {
			/* reset part of the state */
			//silk_memset( &psEnc.sShape,               0, sizeof( psEnc.sShape ) );
			this.sShape.clear();
			//silk_memset( &psEnc.sCmn.sNSQ,            0, sizeof( psEnc.sCmn.sNSQ ) );
			this_sCmn.sNSQ.clear();
			//silk_memset( psEnc.sCmn.prev_NLSFq_Q15,   0, sizeof( psEnc.sCmn.prev_NLSFq_Q15 ) );
			final short[] sbuff = this_sCmn.prev_NLSFq_Q15;
			for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
				sbuff[i] = 0;
			}
			//silk_memset( &psEnc.sCmn.sLP.In_LP_State, 0, sizeof( psEnc.sCmn.sLP.In_LP_State ) );
			final int[] ibuff = this_sCmn.sLP.In_LP_State;
			for( int i = 0, ie = ibuff.length; i < ie; i++ ) {
				ibuff[i] = 0;
			}
			this_sCmn.inputBufIx                  = 0;
			this_sCmn.nFramesEncoded              = 0;
			this_sCmn.TargetRate_bps              = 0;     /* trigger new SNR computation */

			/* Initialize non-zero parameters */
			this_sCmn.prevLag                     = 100;
			this_sCmn.first_frame_after_reset     = true;
			this.sShape.LastGainIndex             = 10;
			this_sCmn.sNSQ.lagPrev                = 100;
			this_sCmn.sNSQ.prev_gain_Q16          = 65536;
			this_sCmn.prevSignalType              = Jdefine.TYPE_NO_VOICE_ACTIVITY;

			this_sCmn.fs_kHz = fs_kHz;
			if( this_sCmn.fs_kHz == 8 ) {
				if( this_sCmn.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_NB_iCDF;
				} else {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_NB_iCDF;
				}
			} else {
				if( this_sCmn.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_iCDF;
				} else {
					this_sCmn.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_iCDF;
				}
			}
			if( this_sCmn.fs_kHz == 8 || this_sCmn.fs_kHz == 12 ) {
				this_sCmn.predictLPCOrder = Jdefine.MIN_LPC_ORDER;
				this_sCmn.psNLSF_CB  = Jtables_NLSF_CB_NB_MB.silk_NLSF_CB_NB_MB;
			} else {
				this_sCmn.predictLPCOrder = Jdefine.MAX_LPC_ORDER;
				this_sCmn.psNLSF_CB  = Jtables_NLSF_CB_WB.silk_NLSF_CB_WB;
			}
			this_sCmn.subfr_length   = Jdefine.SUB_FRAME_LENGTH_MS * fs_kHz;
			this_sCmn.frame_length   = this_sCmn.subfr_length * this_sCmn.nb_subfr;
			this_sCmn.ltp_mem_length = Jdefine.LTP_MEM_LENGTH_MS * fs_kHz;
			this_sCmn.la_pitch       = Jdefine.LA_PITCH_MS * fs_kHz;
			this_sCmn.max_pitch_lag  = 18 * fs_kHz;
			if( this_sCmn.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
				this_sCmn.pitch_LPC_win_length = Jdefine.FIND_PITCH_LPC_WIN_MS * fs_kHz;
			} else {
				this_sCmn.pitch_LPC_win_length = Jdefine.FIND_PITCH_LPC_WIN_MS_2_SF * fs_kHz;
			}
			if( this_sCmn.fs_kHz == 16 ) {
				this_sCmn.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform8_iCDF;
			} else if( this_sCmn.fs_kHz == 12 ) {
				this_sCmn.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform6_iCDF;
			} else {
				this_sCmn.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform4_iCDF;
			}
		}

		/* Check that settings are valid */
		// celt_assert( ( psEnc.sCmn.subfr_length * psEnc.sCmn.nb_subfr ) == psEnc.sCmn.frame_length );

		return ret;
	}

	// end
}
