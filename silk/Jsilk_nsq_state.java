package silk;

// structs.h

/**
 * Noise shaping quantization state
 */
final class Jsilk_nsq_state extends Jdec_API {
	/** Buffer for quantized output signal */
	private final short xq[] = new short[       2 * Jdefine.MAX_FRAME_LENGTH ];
	private final int sLTP_shp_Q14[] = new int[ 2 * Jdefine.MAX_FRAME_LENGTH ];
	private final int sLPC_Q14[] = new int[ Jdefine.MAX_SUB_FRAME_LENGTH + Jdefine.NSQ_LPC_BUF_LENGTH ];
	private final int sAR2_Q14[] = new int[ Jdefine.MAX_SHAPE_LPC_ORDER ];
	private int sLF_AR_shp_Q14;
	private int sDiff_shp_Q14;
	int lagPrev;
	private int sLTP_buf_idx;
	private int sLTP_shp_buf_idx;
	private int rand_seed;
	int prev_gain_Q16;
	private boolean rewhite_flag;
	//
	final void clear() {
		final short[] sbuff = xq;
		for( int i = 0, end = sbuff.length; i < end; i++ ) {
			sbuff[i] = 0;
		}
		int[] buff = sLTP_shp_Q14;
		for( int i = 0, end = buff.length; i < end; i++ ) {
			buff[i] = 0;
		}
		buff = sLPC_Q14;
		for( int i = 0, end = buff.length; i < end; i++ ) {
			buff[i] = 0;
		}
		buff = sAR2_Q14;
		for( int i = 0, end = buff.length; i < end; i++ ) {
			buff[i] = 0;
		}
		sLF_AR_shp_Q14 = 0;
		sDiff_shp_Q14 = 0;
		lagPrev = 0;
		sLTP_buf_idx = 0;
		sLTP_shp_buf_idx = 0;
		rand_seed = 0;
		prev_gain_Q16 = 0;
		rewhite_flag = false;
	}
	final void copyFrom(final Jsilk_nsq_state state) {
		System.arraycopy( state.xq, 0, xq, 0, 2 * Jdefine.MAX_FRAME_LENGTH );
		System.arraycopy( state.sLTP_shp_Q14, 0, sLTP_shp_Q14, 0, 2 * Jdefine.MAX_FRAME_LENGTH );
		System.arraycopy( state.sLPC_Q14, 0, sLPC_Q14, 0, Jdefine.MAX_SUB_FRAME_LENGTH + Jdefine.NSQ_LPC_BUF_LENGTH );
		System.arraycopy( state.sAR2_Q14, 0, sAR2_Q14, 0, Jdefine.MAX_SHAPE_LPC_ORDER );
		sLF_AR_shp_Q14 = state.sLF_AR_shp_Q14;
		sDiff_shp_Q14 = state.sDiff_shp_Q14;
		lagPrev = state.lagPrev;
		sLTP_buf_idx = state.sLTP_buf_idx;
		sLTP_shp_buf_idx = state.sLTP_shp_buf_idx;
		rand_seed = state.rand_seed;
		prev_gain_Q16 = state.prev_gain_Q16;
		rewhite_flag = state.rewhite_flag;
	}
	//
	// start NSQ.h
	/* java: extracted in place
	private static int silk_noise_shape_quantizer_short_prediction(final int[] buf32, final short[] coef16, int order)
	{
		// silk_assert( order == 10 || order == 16 );

		// Avoids introducing a bias because silk_SMLAWB() always rounds to -inf
		int out = ( order >> 1 );
		out += ( buf32[  0 ] * (long)coef16[ 0 ] ) >> 16;
		out += ( buf32[ -1 ] * (long)coef16[ 1 ] ) >> 16;
		out += ( buf32[ -2 ] * (long)coef16[ 2 ] ) >> 16;
		out += ( buf32[ -3 ] * (long)coef16[ 3 ] ) >> 16;
		out += ( buf32[ -4 ] * (long)coef16[ 4 ] ) >> 16;
		out += ( buf32[ -5 ] * (long)coef16[ 5 ] ) >> 16;
		out += ( buf32[ -6 ] * (long)coef16[ 6 ] ) >> 16;
		out += ( buf32[ -7 ] * (long)coef16[ 7 ] ) >> 16;
		out += ( buf32[ -8 ] * (long)coef16[ 8 ] ) >> 16;
		out += ( buf32[ -9 ] * (long)coef16[ 9 ] ) >> 16;

		if( order == 16 )
		{
			out += ( buf32[ -10 ] * (long)coef16[ 10 ] ) >> 16;
			out += ( buf32[ -11 ] * (long)coef16[ 11 ] ) >> 16;
			out += ( buf32[ -12 ] * (long)coef16[ 12 ] ) >> 16;
			out += ( buf32[ -13 ] * (long)coef16[ 13 ] ) >> 16;
			out += ( buf32[ -14 ] * (long)coef16[ 14 ] ) >> 16;
			out += ( buf32[ -15 ] * (long)coef16[ 15 ] ) >> 16;
		}
		return out;
	} */
	/* java: extracted in place
	private static int silk_NSQ_noise_shape_feedback_loop(final int[] data0, final int doffset0, final int[] data1, final short[] coef, final int order)
	{
		int tmp2 = data0[ doffset0 ];
		int tmp1 = data1[0];
		data1[0] = tmp2;

		int out = order >> 1;
		out += ( tmp2 * (long)coef[0] ) >> 16;

		for( int j = 2; j < order; j += 2 ) {
			tmp2 = data1[j - 1];
			data1[j - 1] = tmp1;
			out += ( tmp1 * (long)coef[j - 1] ) >> 16;
			tmp1 = data1[j + 0];
			data1[j + 0] = tmp2;
			out += ( tmp2 * (long)coef[j] ) >> 16;
		}
		data1[order - 1] = tmp1;
		out += ( tmp1 * (long)coef[order - 1] ) >> 16;
		// Q11 -> Q12
		out <<= 1;
		return out;
	} */
	// end NSQ.h
	// start NSQ.c
	/**
	 * silk_noise_shape_quantizer
	 *
	 * @param NSQ I/O  NSQ state
	 * @param signalType I    Signal type
	 * @param x_sc_Q10 I
	 * @param pulses O
	 * @param poffset I java an offset for the pulses
	 * @param xqo O
	 * @param xoffset I java an offset for the xqo
	 * @param sLTP_Q15 I/O  LTP state
	 * @param a_Q12 I    Short term prediction coefs
	 * @param b_Q14 I    Long term prediction coefs
	 * @param boffset I java an offset for the b_Q14
	 * @param AR_shp_Q13 I    Noise shaping AR coefs
	 * @param aroffset I java an offset for the AR_shp_Q13
	 * @param lag I    Pitch lag
	 * @param HarmShapeFIRPacked_Q14 I
	 * @param Tilt_Q14 I    Spectral tilt
	 * @param LF_shp_Q14 I
	 * @param Gain_Q16 I
	 * @param Lambda_Q10 I
	 * @param offset_Q10 I
	 * @param length I    Input length
	 * @param shapingLPCOrder I    Noise shaping AR filter order
	 * @param predictLPCOrder I    Prediction filter order
	 */
	final void silk_noise_shape_quantizer(final int signalType, final int x_sc_Q10[],
			final byte pulses[], int poffset,// java
			final short xqo[], int xoffset,// java
			final int sLTP_Q15[], final short a_Q12[],
			final short b_Q14[], int boffset,// java
			final short AR_shp_Q13[], final int aroffset,// java
			final int lag, final int HarmShapeFIRPacked_Q14, final int Tilt_Q14, final int LF_shp_Q14, final int Gain_Q16,
			final int Lambda_Q10, final int offset_Q10, final int length, final int shapingLPCOrder, final int predictLPCOrder
			// int  arch // I    Architecture java not used
		)
	{
/* #ifdef silk_short_prediction_create_arch_coef
		opus_int32   a_Q12_arch[MAX_LPC_ORDER];
#endif */

		int shp_lag_ptr    = this.sLTP_shp_buf_idx - lag + Jdefine.HARM_SHAPE_FIR_TAPS / 2;// NSQ.sLTP_shp_Q14[ shp_lag_ptr ]
		final int[] LTP_shp_Q14 = this.sLTP_shp_Q14;// java
		int pred_lag_ptr   = this.sLTP_buf_idx - lag + Jdefine.LTP_ORDER / 2;// sLTP_Q15[ pred_lag_ptr ]
		final int Gain_Q10 = Gain_Q16 >> 6;

		/* Set up short term AR state */
		int psLPC_Q14 = Jdefine.NSQ_LPC_BUF_LENGTH - 1;// NSQ.sLPC_Q14[ psLPC_Q14 ]
		final int[] LPC_Q14 = this.sLPC_Q14;

/* #ifdef silk_short_prediction_create_arch_coef
		silk_short_prediction_create_arch_coef( a_Q12_arch, a_Q12, predictLPCOrder );
#endif */

		// java
		final int[] this_sAR2_Q14 = this.sAR2_Q14;
		final long a_Q12_0 = (long)a_Q12[ 0 ];
		final long a_Q12_1 = (long)a_Q12[ 1 ];
		final long a_Q12_2 = (long)a_Q12[ 2 ];
		final long a_Q12_3 = (long)a_Q12[ 3 ];
		final long a_Q12_4 = (long)a_Q12[ 4 ];
		final long a_Q12_5 = (long)a_Q12[ 5 ];
		final long a_Q12_6 = (long)a_Q12[ 6 ];
		final long a_Q12_7 = (long)a_Q12[ 7 ];
		final long a_Q12_8 = (long)a_Q12[ 8 ];
		final long a_Q12_9 = (long)a_Q12[ 9 ];
		final long a_Q12_10 = (long)a_Q12[ 10 ];
		final long a_Q12_11 = (long)a_Q12[ 11 ];
		final long a_Q12_12 = (long)a_Q12[ 12 ];
		final long a_Q12_13 = (long)a_Q12[ 13 ];
		final long a_Q12_14 = (long)a_Q12[ 14 ];
		final long a_Q12_15 = (long)a_Q12[ 15 ];
		final long b_Q14_0 = (long)b_Q14[ boffset++ ];// + 0
		final long b_Q14_1 = (long)b_Q14[ boffset++ ];
		final long b_Q14_2 = (long)b_Q14[ boffset++ ];
		final long b_Q14_3 = (long)b_Q14[ boffset++ ];
		final long b_Q14_4 = (long)b_Q14[ boffset   ];// + 4
		for( int i = 0; i < length; i++ ) {
			/* Generate dither */
			this.rand_seed = silk_RAND( this.rand_seed );

			/* Short-term prediction */
			// LPC_pred_Q10 = silk_noise_shape_quantizer_short_prediction( psLPC_Q14, a_Q12, a_Q12_arch, predictLPCOrder );//, arch );// java: extracted in place

			// silk_assert( predictLPCOrder == 10 || predictLPCOrder == 16 );
			/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
			int psLPC_Q14_indx = psLPC_Q14;// java
			int LPC_pred_Q10 = ( predictLPCOrder >> 1 );
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_0) >> 16);// + 0
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_1) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_2) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_3) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_4) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_5) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_6) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_7) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx-- ] * a_Q12_8) >> 16);
			LPC_pred_Q10 += (int)((LPC_Q14[ psLPC_Q14_indx   ] * a_Q12_9) >> 16);// - 9
			if( predictLPCOrder == 16 ) {
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_10) >> 16);// - 10
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_11) >> 16);
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_12) >> 16);
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_13) >> 16);
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_14) >> 16);
				LPC_pred_Q10 += (int)((LPC_Q14[ --psLPC_Q14_indx ] * a_Q12_15) >> 16);// - 15
			}
			// java: end silk_noise_shape_quantizer_short_prediction

			/* Long-term prediction */
			int LTP_pred_Q13;
			if( signalType == Jdefine.TYPE_VOICED ) {
				/* Unrolled loop */
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				LTP_pred_Q13 = 2;
				LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_0) >> 16);// + 0
				LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_1) >> 16);
				LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_2) >> 16);
				LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_3) >> 16);
				LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr   ] * b_Q14_4) >> 16);// - 4
				pred_lag_ptr += 1 + 4;// pred_lag_ptr++;
			} else {
				LTP_pred_Q13 = 0;
			}

			/* Noise shape feedback */
			// celt_assert( ( shapingLPCOrder & 1 ) == 0 );   /* check that order is even */
			// n_AR_Q12 = silk_NSQ_noise_shape_feedback_loop( LPC_Q14, psLPC_Q14, this_sAR2_Q14, AR_shp_Q13, shapingLPCOrder );// , arch);// java: extracted in place
			// n_AR_Q12 = silk_NSQ_noise_shape_feedback_loop(          psLPC_Q14, NSQ->sAR2_Q14, AR_shp_Q13, shapingLPCOrder, arch);
			// n_AR_Q12 = silk_NSQ_noise_shape_feedback_loop(&NSQ->sDiff_shp_Q14, NSQ->sAR2_Q14, AR_shp_Q13, shapingLPCOrder, arch);
			int tmp2 = this.sDiff_shp_Q14;
			int tmp1 = this_sAR2_Q14[ 0 ];
			this_sAR2_Q14[ 0 ] = tmp2;
			int n_AR_Q12 = ( shapingLPCOrder >> 1 );
			n_AR_Q12 += (int)((tmp2 * (long)AR_shp_Q13[ aroffset ]) >> 16);
			for( int j = 2, aj = aroffset + 1; j < shapingLPCOrder; j += 2 ) {
				tmp2 = this_sAR2_Q14[ j - 1 ];
				this_sAR2_Q14[ j - 1 ] = tmp1;
				n_AR_Q12 += (int)((tmp1 * (long)AR_shp_Q13[ aj++ ]) >> 16);
				tmp1 = this_sAR2_Q14[ j ];
				this_sAR2_Q14[ j ] = tmp2;
				n_AR_Q12 += (int)((tmp2 * (long)AR_shp_Q13[ aj++ ]) >> 16);
			}
			this_sAR2_Q14[ shapingLPCOrder - 1 ] = tmp1;
			n_AR_Q12 += (int)((tmp1 * (long)AR_shp_Q13[ aroffset + shapingLPCOrder - 1 ]) >> 16);

			n_AR_Q12 <<= 1;                                /* Q11 -> Q12 */
			// java: end silk_NSQ_noise_shape_feedback_loop
			n_AR_Q12 += (int)((this.sLF_AR_shp_Q14 * (long)Tilt_Q14) >> 16);

			int n_LF_Q12 = (int)((LTP_shp_Q14[ this.sLTP_shp_buf_idx - 1 ] * (long)(short)LF_shp_Q14) >> 16);
			n_LF_Q12 += (int)((this.sLF_AR_shp_Q14 * (long)(LF_shp_Q14 >> 16)) >> 16);

			// celt_assert( lag > 0 || signalType != TYPE_VOICED );

			/* Combine prediction and noise shaping signals */
			tmp1 = ( ( LPC_pred_Q10 << 2 ) - n_AR_Q12 );        /* Q12 */
			tmp1 -= n_LF_Q12;                                    /* Q12 */
			if( lag > 0 ) {
				/* Symmetric, packed FIR coefficients */
				int n_LTP_Q13 = (int)((( LTP_shp_Q14[ shp_lag_ptr ] + LTP_shp_Q14[ shp_lag_ptr - 2 ] ) * (long)(short)HarmShapeFIRPacked_Q14) >> 16);
				n_LTP_Q13 += ((LTP_shp_Q14[ shp_lag_ptr - 1 ] * (long)(HarmShapeFIRPacked_Q14 >> 16)) >> 16);
				n_LTP_Q13 <<= 1;
				shp_lag_ptr++;

				tmp2 = ( LTP_pred_Q13 - n_LTP_Q13 );                       /* Q13 */
				tmp1 = tmp2 + (tmp1 << 1);                          /* Q13 */
				tmp1 = JSigProc_FIX.silk_RSHIFT_ROUND( tmp1, 3 );                                /* Q10 */
			} else {
				tmp1 = JSigProc_FIX.silk_RSHIFT_ROUND( tmp1, 2 );                                /* Q10 */
			}

			int r_Q10 = ( x_sc_Q10[ i ] - tmp1 );                              /* residual error Q10 */

			/* Flip sign depending on dither */
			if ( this.rand_seed < 0 ) {
				r_Q10 = -r_Q10;
			}
			r_Q10 = (r_Q10 > (30 << 10) ? (30 << 10) : (r_Q10 < -(31 << 10) ? -(31 << 10) : r_Q10));

			/* Find two quantization level candidates and measure their rate-distortion */
			int q1_Q10 = ( r_Q10 - offset_Q10 );
			int q1_Q0 = ( q1_Q10 >> 10 );
			if( Lambda_Q10 > 2048 ) {
				/* For aggressive RDO, the bias becomes more than one pulse. */
				final int rdo_offset = Lambda_Q10 / 2 - 512;
				if( q1_Q10 > rdo_offset ) {
					q1_Q0 = ((q1_Q10 - rdo_offset) >> 10);
				} else if( q1_Q10 < -rdo_offset ) {
					q1_Q0 = ((q1_Q10 + rdo_offset) >> 10);
				} else if( q1_Q10 < 0 ) {
					q1_Q0 = -1;
				} else {
					q1_Q0 = 0;
				}
			}
			int q2_Q10, rd1_Q20, rd2_Q20;
			if( q1_Q0 > 0 ) {
				q1_Q10  = ( ( q1_Q0 << 10 ) - Jdefine.QUANT_LEVEL_ADJUST_Q10 );
				q1_Q10  += offset_Q10;
				q2_Q10  = ( q1_Q10 + 1024 );
				rd1_Q20 = ( q1_Q10 * Lambda_Q10 );
				rd2_Q20 = ( q2_Q10 * Lambda_Q10 );
			} else if( q1_Q0 == 0 ) {
				q1_Q10  = offset_Q10;
				q2_Q10  = ( q1_Q10 + (1024 - Jdefine.QUANT_LEVEL_ADJUST_Q10) );
				rd1_Q20 = ( q1_Q10 * Lambda_Q10 );
				rd2_Q20 = ( q2_Q10 * Lambda_Q10 );
			} else if( q1_Q0 == -1 ) {
				q2_Q10  = offset_Q10;
				q1_Q10  = ( q2_Q10 - (1024 - Jdefine.QUANT_LEVEL_ADJUST_Q10) );
				rd1_Q20 = ( -q1_Q10 * Lambda_Q10 );
				rd2_Q20 = (  q2_Q10 * Lambda_Q10 );
			} else {            /* Q1_Q0 < -1 */
				q1_Q10  = ( ( q1_Q0 << 10 ) + Jdefine.QUANT_LEVEL_ADJUST_Q10 );
				q1_Q10  += offset_Q10;
				q2_Q10  = ( q1_Q10 + 1024 );
				rd1_Q20 = ( -q1_Q10 * Lambda_Q10 );
				rd2_Q20 = ( -q2_Q10 * Lambda_Q10 );
			}
			int rr_Q10  = ( r_Q10 - q1_Q10 );
			rd1_Q20 += rr_Q10 * rr_Q10;
			rr_Q10  = ( r_Q10 - q2_Q10 );
			rd2_Q20 += rr_Q10 * rr_Q10;

			if( rd2_Q20 < rd1_Q20 ) {
				q1_Q10 = q2_Q10;
			}

			final int pulses_i = JSigProc_FIX.silk_RSHIFT_ROUND( q1_Q10, 10 );// java
			pulses[ poffset++ ] = (byte) pulses_i;// java

			/* Excitation */
			int exc_Q14 = ( q1_Q10 << 4 );
			if ( this.rand_seed < 0 ) {
				exc_Q14 = -exc_Q14;
			}

			/* Add predictions */
			final int LPC_exc_Q14 = exc_Q14 + (LTP_pred_Q13 << 1);
			final int xq_Q14      = LPC_exc_Q14 + (LPC_pred_Q10 << 4);

			/* Scale XQ back to normal level before saving */
			final int v = JSigProc_FIX.silk_RSHIFT_ROUND( (int)(((long)xq_Q14 * Gain_Q10) >> 16), 8 );// java
			xqo[ xoffset++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));

			/* Update states */
			LPC_Q14[ ++psLPC_Q14 ] = xq_Q14;
			this.sDiff_shp_Q14 = xq_Q14 - (x_sc_Q10[ i ] << 4);
			final int LF_AR_shp_Q14 = this.sDiff_shp_Q14 - (n_AR_Q12 << 2);
			this.sLF_AR_shp_Q14 = LF_AR_shp_Q14;

			LTP_shp_Q14[ this.sLTP_shp_buf_idx++ ] = LF_AR_shp_Q14 - (n_LF_Q12 << 2);
			sLTP_Q15[ this.sLTP_buf_idx++ ] = ( LPC_exc_Q14 << 1 );

			/* Make dither dependent on quantized signal */
			this.rand_seed += pulses_i;// java
		}

		/* Update LPC synth buffer */
		System.arraycopy( this.sLPC_Q14, length, this.sLPC_Q14, 0, Jdefine.NSQ_LPC_BUF_LENGTH );
	}
	private static final class JNSQ_sample_struct {
		private int Q_Q10;
		private int RD_Q10;
		private int xq_Q14;
		private int LF_AR_Q14;
		private int Diff_Q14;
		private int sLTP_shp_Q14;
		private int LPC_exc_Q14;
		//
		private final void copyFrom(final JNSQ_sample_struct sample) {
			Q_Q10 = sample.Q_Q10;
			RD_Q10 = sample.RD_Q10;
			xq_Q14 = sample.xq_Q14;
			LF_AR_Q14 = sample.LF_AR_Q14;
			Diff_Q14 = sample.Diff_Q14;
			sLTP_shp_Q14 = sample.sLTP_shp_Q14;
			LPC_exc_Q14 = sample.LPC_exc_Q14;
		}
	}
	/**
	 * Noise shape quantizer for one subframe
	 *
	 * <p>java changed: new value smpl_buf_idx
	 *
	 * @param NSQ I/O  NSQ state
	 * @param psDelDec I/O  Delayed decision states
	 * @param signalType I    Signal type
	 * @param x_Q10 I
	 * @param pulses O
	 * @param poffset I java an offset for the pulses
	 * @param xqo O
	 * @param xqoffset I java an offset for the xqo
	 * @param sLTP_Q15 I/O  LTP filter state
	 * @param delayedGain_Q10 I/O  Gain delay buffer
	 * @param a_Q12 I    Short term prediction coefs
	 * @param b_Q14 I    Long term prediction coefs
	 * @param boffset I java an offset for the b_Q14
	 * @param AR_shp_Q13 I    Noise shaping coefs
	 * @param aroffset I java an offset for the AR_shp_Q13
	 * @param lag I    Pitch lag
	 * @param HarmShapeFIRPacked_Q14 I
	 * @param Tilt_Q14 I    Spectral tilt
	 * @param LF_shp_Q14 I
	 * @param Gain_Q16 I
	 * @param Lambda_Q10 I
	 * @param offset_Q10 I
	 * @param length I    Input length
	 * @param subfr I    Subframe number
	 * @param shapingLPCOrder I    Shaping LPC filter order
	 * @param predictLPCOrder I    Prediction filter order
	 * @param warping_Q16 I
	 * @param nStatesDelayedDecision I/O    Number of states in decision tree
	 * @param smpl_buf_idx I
	 * @param decisionDelay I
	 * @return new value smpl_buf_idx
	 */
	private final int silk_noise_shape_quantizer_del_dec(// final Jsilk_nsq_state NSQ,
			final JNSQ_del_dec_struct psDelDec[], final int signalType,
			final int x_Q10[],
			final byte pulses[], final int poffset,// java
			final short xqo[], final int xqoffset,// java
			final int sLTP_Q15[], final int delayedGain_Q10[],
			final short a_Q12[],
			final short b_Q14[], int boffset,// java
			final short AR_shp_Q13[], final int aroffset,// java
			final int lag, final int HarmShapeFIRPacked_Q14, final int Tilt_Q14, final int LF_shp_Q14, final int Gain_Q16, final int Lambda_Q10, final int offset_Q10, final int length,
			final int subfr, final int shapingLPCOrder, final int predictLPCOrder, final long warping_Q16, final int nStatesDelayedDecision,
			int smpl_buf_idx,// FIXME comment smpl_buf_idx is output also
			final int decisionDelay
			// int arch // I java unused
		)
	{// java: int32 warping_Q16 changed to int64 warping_Q16 to avoid casting inside the loop
/* #ifdef silk_short_prediction_create_arch_coef
		opus_int32   a_Q12_arch[MAX_LPC_ORDER];
#endif */

		// SAVE_STACK;

		// celt_assert( nStatesDelayedDecision > 0 );
		final JNSQ_sample_struct[][] psSampleState = new JNSQ_sample_struct[nStatesDelayedDecision][2];
		for( int i = 0; i < nStatesDelayedDecision; i++ ) {// java
			psSampleState[i][0] = new JNSQ_sample_struct();
			psSampleState[i][1] = new JNSQ_sample_struct();
		}

		int shp_lag_ptr  = this.sLTP_shp_buf_idx - lag + Jdefine.HARM_SHAPE_FIR_TAPS / 2;// NSQ.sLTP_shp_Q14[shp_lag_ptr]
		final int[] LTP_shp_Q14 = this.sLTP_shp_Q14;// java
		int pred_lag_ptr = this.sLTP_buf_idx - lag + Jdefine.LTP_ORDER / 2;// sLTP_Q15[pred_lag_ptr]
		final int Gain_Q10 = ( Gain_Q16 >> 6 );

/* #ifdef silk_short_prediction_create_arch_coef
	    silk_short_prediction_create_arch_coef(a_Q12_arch, a_Q12, predictLPCOrder);
#endif */

		// java
		final long a_Q12_0 = (long)a_Q12[ 0 ];
		final long a_Q12_1 = (long)a_Q12[ 1 ];
		final long a_Q12_2 = (long)a_Q12[ 2 ];
		final long a_Q12_3 = (long)a_Q12[ 3 ];
		final long a_Q12_4 = (long)a_Q12[ 4 ];
		final long a_Q12_5 = (long)a_Q12[ 5 ];
		final long a_Q12_6 = (long)a_Q12[ 6 ];
		final long a_Q12_7 = (long)a_Q12[ 7 ];
		final long a_Q12_8 = (long)a_Q12[ 8 ];
		final long a_Q12_9 = (long)a_Q12[ 9 ];
		final long a_Q12_10 = (long)a_Q12[ 10 ];
		final long a_Q12_11 = (long)a_Q12[ 11 ];
		final long a_Q12_12 = (long)a_Q12[ 12 ];
		final long a_Q12_13 = (long)a_Q12[ 13 ];
		final long a_Q12_14 = (long)a_Q12[ 14 ];
		final long a_Q12_15 = (long)a_Q12[ 15 ];
		final long b_Q14_0 = (long)b_Q14[ boffset++ ];// + 0
		final long b_Q14_1 = (long)b_Q14[ boffset++ ];
		final long b_Q14_2 = (long)b_Q14[ boffset++ ];
		final long b_Q14_3 = (long)b_Q14[ boffset++ ];
		final long b_Q14_4 = (long)b_Q14[ boffset   ];// + 4
		for( int i = 0; i < length; i++ ) {
			/* Perform common calculations used in all states */

			int LTP_pred_Q14;
			/* Long-term prediction */
			if( signalType == Jdefine.TYPE_VOICED ) {
				/* Unrolled loop */
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				LTP_pred_Q14 = 2;
				LTP_pred_Q14 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_0) >> 16);// + 0
				LTP_pred_Q14 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_1) >> 16);
				LTP_pred_Q14 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_2) >> 16);
				LTP_pred_Q14 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * b_Q14_3) >> 16);
				LTP_pred_Q14 += (int)((sLTP_Q15[ pred_lag_ptr   ] * b_Q14_4) >> 16);// - 4
				LTP_pred_Q14 <<= 1;                          /* Q13 . Q14 */
				pred_lag_ptr += 1 + 4;// pred_lag_ptr++;// +1
			} else {
				LTP_pred_Q14 = 0;
			}

			/* Long-term shaping */
			int n_LTP_Q14;
			if( lag > 0 ) {
				/* Symmetric, packed FIR coefficients */
				n_LTP_Q14 = (int)(((LTP_shp_Q14[ shp_lag_ptr + 0 ] + LTP_shp_Q14[ shp_lag_ptr - 2 ]) * (long)(short)HarmShapeFIRPacked_Q14) >> 16);
				n_LTP_Q14 += (int)((LTP_shp_Q14[ shp_lag_ptr - 1 ] * (long)(HarmShapeFIRPacked_Q14 >> 16)) >> 16);
				n_LTP_Q14 = LTP_pred_Q14 - (n_LTP_Q14 << 2);            /* Q12 . Q14 */
				shp_lag_ptr++;
			} else {
				n_LTP_Q14 = 0;
			}

			for( int k = 0; k < nStatesDelayedDecision; k++ ) {
				/* Delayed decision state */
				final JNSQ_del_dec_struct psDD = psDelDec[ k ];

				/* Sample state */
				final JNSQ_sample_struct psSS_0 = psSampleState[ k ][ 0 ];// java
				final JNSQ_sample_struct psSS_1 = psSampleState[ k ][ 1 ];// java

				/* Generate dither */
				psDD.Seed = silk_RAND( psDD.Seed );

				/* Pointer used in short term prediction and shaping */
				final int psLPC_Q14 = Jdefine.NSQ_LPC_BUF_LENGTH - 1 + i;// psDD.sLPC_Q14[ psLPC_Q14 ]
				final int[] LPC_Q14 = psDD.sLPC_Q14;// java
				/* Short-term prediction */
				// LPC_pred_Q14 = silk_noise_shape_quantizer_short_prediction(psLPC_Q14, a_Q12, a_Q12_arch, predictLPCOrder, arch);// java: extracted in place
				// silk_assert( predictLPCOrder == 10 || predictLPCOrder == 16 );
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				int psLPC_indx = psLPC_Q14;// java
				int LPC_pred_Q14 = predictLPCOrder >> 1;
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_0) >> 16);// + 0
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_1) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_2) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_3) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_4) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_5) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_6) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_7) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx-- ] * a_Q12_8) >> 16);
				LPC_pred_Q14 += (int)((LPC_Q14[ psLPC_indx   ] * a_Q12_9) >> 16);// - 9
				if( predictLPCOrder == 16 ) {
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_10) >> 16);
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_11) >> 16);
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_12) >> 16);
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_13) >> 16);
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_14) >> 16);
					LPC_pred_Q14 += (int)((LPC_Q14[ --psLPC_indx ] * a_Q12_15) >> 16);// - 15
				}
				// java: end silk_noise_shape_quantizer_short_prediction
				LPC_pred_Q14 <<= 4;                              /* Q10 . Q14 */

				/* Noise shape feedback */
				// celt_assert( ( shapingLPCOrder & 1 ) == 0 );   /* check that order is even */
				/* Output of lowpass section */
				final int[] psDD_sAR2_Q14 = psDD.sAR2_Q14;// java
				int tmp2 = psDD.Diff_Q14 + (int)((psDD_sAR2_Q14[ 0 ] * warping_Q16) >> 16);
				/* Output of allpass section */
				int tmp1 = psDD_sAR2_Q14[ 0 ] + (int)(((psDD_sAR2_Q14[ 1 ] - tmp2) * warping_Q16) >> 16);
				psDD_sAR2_Q14[ 0 ] = tmp2;
				int n_AR_Q14 = ( shapingLPCOrder >> 1 );
				n_AR_Q14 += (int)((tmp2 * (long)AR_shp_Q13[ aroffset ]) >> 16);
				/* Loop over allpass sections */
				for( int j = 2, aj = aroffset + 2 - 1; j < shapingLPCOrder; j += 2 ) {
					/* Output of allpass section */
					tmp2 = psDD_sAR2_Q14[ j - 1 ] + (int)(((psDD_sAR2_Q14[ j ] - tmp1) * warping_Q16) >> 16);
					psDD_sAR2_Q14[ j - 1 ] = tmp1;
					n_AR_Q14 += (int)((tmp1 * (long)AR_shp_Q13[ aj++ ]) >> 16);
					/* Output of allpass section */
					tmp1 = psDD_sAR2_Q14[ j ] + (int)(((psDD_sAR2_Q14[ j + 1 ] - tmp2) * warping_Q16) >> 16);
					psDD_sAR2_Q14[ j ] = tmp2;
					n_AR_Q14 += (int)((tmp2 * (long)AR_shp_Q13[ aj++ ]) >> 16);
				}
				psDD.sAR2_Q14[ shapingLPCOrder - 1 ] = tmp1;
				n_AR_Q14 += (int)((tmp1 * (long)AR_shp_Q13[ aroffset + shapingLPCOrder - 1 ] ) >> 16);

				n_AR_Q14 <<= 1;                                      /* Q11 . Q12 */
				n_AR_Q14 += (int)((psDD.LF_AR_Q14 * (long)Tilt_Q14) >> 16);              /* Q12 */
				n_AR_Q14 <<= 2;                                      /* Q12 . Q14 */

				int n_LF_Q14 = (int)((psDD.Shape_Q14[ smpl_buf_idx ] * (long)(short)LF_shp_Q14) >> 16);     /* Q12 */
				n_LF_Q14 += (int)((psDD.LF_AR_Q14 * (long)(LF_shp_Q14 >> 16)) >> 16);            /* Q12 */
				n_LF_Q14 <<= 2;                                      /* Q12 . Q14 */

				/* Input minus prediction plus noise feedback                       */
				/* r = x[ i ] - LTP_pred - LPC_pred + n_AR + n_Tilt + n_LF + n_LTP  */
				tmp1 = n_AR_Q14 + n_LF_Q14;                                    /* Q14 */
				tmp2 = n_LTP_Q14 + LPC_pred_Q14;                               /* Q13 */
				tmp1 = tmp2 - tmp1;                                            /* Q13 */
				tmp1 = JSigProc_FIX.silk_RSHIFT_ROUND( tmp1, 4 );                                        /* Q10 */

				int r_Q10 = x_Q10[ i ] - tmp1;                                     /* residual error Q10 */

				/* Flip sign depending on dither */
				if( psDD.Seed < 0 ) {
					r_Q10 = -r_Q10;
				}
				r_Q10 = (r_Q10 > (30 << 10) ? (30 << 10) : (r_Q10 < -(31 << 10) ? -(31 << 10) : r_Q10));

				/* Find two quantization level candidates and measure their rate-distortion */
				int rd1_Q10;
				int rd2_Q10;
				int q2_Q10;
				int q1_Q10 = r_Q10 - offset_Q10;
				int q1_Q0 = q1_Q10 >> 10;
				if( Lambda_Q10 > 2048 ) {
					/* For aggressive RDO, the bias becomes more than one pulse. */
					final int rdo_offset = Lambda_Q10 / 2 - 512;
					if( q1_Q10 > rdo_offset ) {
						q1_Q0 = (q1_Q10 - rdo_offset) >> 10;
					} else if( q1_Q10 < -rdo_offset ) {
						q1_Q0 = (q1_Q10 + rdo_offset) >> 10;
					} else if( q1_Q10 < 0 ) {
						q1_Q0 = -1;
					} else {
						q1_Q0 = 0;
					}
				}
				if( q1_Q0 > 0 ) {
					q1_Q10  = ( q1_Q0 << 10 ) - Jdefine.QUANT_LEVEL_ADJUST_Q10;
					q1_Q10  += offset_Q10;
					q2_Q10  = q1_Q10 + 1024;
					rd1_Q10 = q1_Q10 * Lambda_Q10;
					rd2_Q10 = q2_Q10 * Lambda_Q10;
				} else if( q1_Q0 == 0 ) {
					q1_Q10  = offset_Q10;
					q2_Q10  = q1_Q10 + (1024 - Jdefine.QUANT_LEVEL_ADJUST_Q10);
					rd1_Q10 = q1_Q10 * Lambda_Q10;
					rd2_Q10 = q2_Q10 * Lambda_Q10;
				} else if( q1_Q0 == -1 ) {
					q2_Q10  = offset_Q10;
					q1_Q10  = q2_Q10 - (1024 - Jdefine.QUANT_LEVEL_ADJUST_Q10);
					rd1_Q10 = -q1_Q10 * Lambda_Q10;
					rd2_Q10 =  q2_Q10 * Lambda_Q10;
				} else {            /* q1_Q0 < -1 */
					q1_Q10  = ( q1_Q0 << 10 ) + Jdefine.QUANT_LEVEL_ADJUST_Q10;
					q1_Q10  += offset_Q10;
					q2_Q10  = q1_Q10 + 1024;
					rd1_Q10 = -q1_Q10 * Lambda_Q10;
					rd2_Q10 = -q2_Q10 * Lambda_Q10;
				}
				int rr_Q10  = r_Q10 - q1_Q10;
				rd1_Q10 = ( ( rd1_Q10 + rr_Q10 * rr_Q10 ) >> 10 );
				rr_Q10  = r_Q10 - q2_Q10;
				rd2_Q10 = ( ( rd2_Q10 + rr_Q10 * rr_Q10 ) >> 10 );

				if( rd1_Q10 < rd2_Q10 ) {
					psSS_0.RD_Q10 = psDD.RD_Q10 + rd1_Q10;
					psSS_1.RD_Q10 = psDD.RD_Q10 + rd2_Q10;
					psSS_0.Q_Q10  = q1_Q10;
					psSS_1.Q_Q10  = q2_Q10;
				} else {
					psSS_0.RD_Q10 = psDD.RD_Q10 + rd2_Q10;
					psSS_1.RD_Q10 = psDD.RD_Q10 + rd1_Q10;
					psSS_0.Q_Q10  = q2_Q10;
					psSS_1.Q_Q10  = q1_Q10;
				}

				/* Update states for best quantization */

				/* Quantized excitation */
				int exc_Q14 = psSS_0.Q_Q10 << 4;
				if( psDD.Seed < 0 ) {
					exc_Q14 = -exc_Q14;
				}

				/* Add predictions */
				int LPC_exc_Q14 = exc_Q14 + LTP_pred_Q14;
				int xq_Q14      = LPC_exc_Q14 + LPC_pred_Q14;

				/* Update states */
				psSS_0.Diff_Q14     = xq_Q14 - (x_Q10[ i ] << 4);
				int LF_AR_shp_Q14   = psSS_0.Diff_Q14 - n_AR_Q14;
				psSS_0.sLTP_shp_Q14 = LF_AR_shp_Q14 - n_LF_Q14;
				psSS_0.LF_AR_Q14    = LF_AR_shp_Q14;
				psSS_0.LPC_exc_Q14  = LPC_exc_Q14;
				psSS_0.xq_Q14       = xq_Q14;

				/* Update states for second best quantization */

				/* Quantized excitation */
				exc_Q14 = psSS_1.Q_Q10 << 4;
				if( psDD.Seed < 0 ) {
					exc_Q14 = -exc_Q14;
				}

				/* Add predictions */
				LPC_exc_Q14 = exc_Q14 + LTP_pred_Q14;
				xq_Q14      = LPC_exc_Q14 + LPC_pred_Q14;

				/* Update states */
				psSS_1.Diff_Q14     = xq_Q14 - (x_Q10[ i ] << 4);
	            LF_AR_shp_Q14       = psSS_1.Diff_Q14 - n_AR_Q14;
				psSS_1.sLTP_shp_Q14 = LF_AR_shp_Q14 - n_LF_Q14;
				psSS_1.LF_AR_Q14    = LF_AR_shp_Q14;
				psSS_1.LPC_exc_Q14  = LPC_exc_Q14;
				psSS_1.xq_Q14       = xq_Q14;
			}

			smpl_buf_idx  = ( smpl_buf_idx - 1 ) % Jdefine.DECISION_DELAY;
	        if( smpl_buf_idx < 0 ) {
				smpl_buf_idx += Jdefine.DECISION_DELAY;
			}
	        final int last_smple_idx = ( smpl_buf_idx + decisionDelay ) % Jdefine.DECISION_DELAY;

			/* Find winner */
			int RDmin_Q10 = psSampleState[ 0 ][ 0 ].RD_Q10;
			int Winner_ind = 0;
			for( int k = 1; k < nStatesDelayedDecision; k++ ) {
				if( psSampleState[ k ][ 0 ].RD_Q10 < RDmin_Q10 ) {
					RDmin_Q10  = psSampleState[ k ][ 0 ].RD_Q10;
					Winner_ind = k;
				}
			}

			/* Increase RD values of expired states */
			final int Winner_rand_state = psDelDec[ Winner_ind ].RandState[ last_smple_idx ];
			for( int k = 0; k < nStatesDelayedDecision; k++ ) {
				final JNSQ_sample_struct[] nsq = psSampleState[ k ];// java
				if( psDelDec[ k ].RandState[ last_smple_idx ] != Winner_rand_state ) {
					nsq[ 0 ].RD_Q10 = nsq[ 0 ].RD_Q10 + (Integer.MAX_VALUE >> 4);
					nsq[ 1 ].RD_Q10 = nsq[ 1 ].RD_Q10 + (Integer.MAX_VALUE >> 4);
					// silk_assert( psSampleState[ k ][ 0 ].RD_Q10 >= 0 );
				}
			}

			/* Find worst in first set and best in second set */
			int RDmax_Q10  = psSampleState[ 0 ][ 0 ].RD_Q10;
			RDmin_Q10  = psSampleState[ 0 ][ 1 ].RD_Q10;
			int RDmax_ind = 0;
			int RDmin_ind = 0;
			for( int k = 1; k < nStatesDelayedDecision; k++ ) {
				final JNSQ_sample_struct[] nsq = psSampleState[ k ];// java
				/* find worst in first set */
				if( nsq[ 0 ].RD_Q10 > RDmax_Q10 ) {
					RDmax_Q10  = nsq[ 0 ].RD_Q10;
					RDmax_ind = k;
				}
				/* find best in second set */
				if( nsq[ 1 ].RD_Q10 < RDmin_Q10 ) {
					RDmin_Q10  = nsq[ 1 ].RD_Q10;
					RDmin_ind = k;
				}
			}

			/* Replace a state if best from second set outperforms worst in first set */
			if( RDmin_Q10 < RDmax_Q10 ) {
				// silk_memcpy( ( (opus_int32 *)&psDelDec[ RDmax_ind ] ) + i,
				//		( (opus_int32 *)&psDelDec[ RDmin_ind ] ) + i, sizeof( NSQ_del_dec_struct ) - i * sizeof( opus_int32) );
				psDelDec[ RDmax_ind ].copyFromStart( psDelDec[ RDmin_ind ], i );
				psSampleState[ RDmax_ind ][ 0 ].copyFrom( psSampleState[ RDmin_ind ][ 1 ] );
			}

			/* Write samples from winner to output and long-term filter states */
			JNSQ_del_dec_struct psDD = psDelDec[ Winner_ind ];
			if( subfr > 0 || i >= decisionDelay ) {
				pulses[ poffset + i - decisionDelay ] = (byte)JSigProc_FIX.silk_RSHIFT_ROUND( psDD.Q_Q10[ last_smple_idx ], 10 );
				final int v = JSigProc_FIX.silk_RSHIFT_ROUND(
						((int)(((long)psDD.Xq_Q14[ last_smple_idx ] * delayedGain_Q10[ last_smple_idx ]) >> 16)), 8 );// java
				xqo[ xqoffset + i - decisionDelay ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
				LTP_shp_Q14[ this.sLTP_shp_buf_idx - decisionDelay ] = psDD.Shape_Q14[ last_smple_idx ];
				sLTP_Q15[          this.sLTP_buf_idx     - decisionDelay ] = psDD.Pred_Q15[  last_smple_idx ];
			}
			this.sLTP_shp_buf_idx++;
			this.sLTP_buf_idx++;

			/* Update states */
			for( int k = 0; k < nStatesDelayedDecision; k++ ) {
				psDD                            = psDelDec[ k ];
				final JNSQ_sample_struct psSS   = psSampleState[ k ][ 0 ];
				psDD.LF_AR_Q14                  = psSS.LF_AR_Q14;
				psDD.Diff_Q14                   = psSS.Diff_Q14;
				psDD.sLPC_Q14[ Jdefine.NSQ_LPC_BUF_LENGTH + i ] = psSS.xq_Q14;
				psDD.Xq_Q14[    smpl_buf_idx ]  = psSS.xq_Q14;
				psDD.Q_Q10[     smpl_buf_idx ]  = psSS.Q_Q10;
				psDD.Pred_Q15[  smpl_buf_idx ]  = psSS.LPC_exc_Q14 << 1;
				psDD.Shape_Q14[ smpl_buf_idx ]  = psSS.sLTP_shp_Q14;
				psDD.Seed                      += JSigProc_FIX.silk_RSHIFT_ROUND( psSS.Q_Q10, 10 );
				psDD.RandState[ smpl_buf_idx ]  = psDD.Seed;
				psDD.RD_Q10                     = psSS.RD_Q10;
			}
			delayedGain_Q10[     smpl_buf_idx ] = Gain_Q10;
		}
		/* Update LPC states */
		for( int k = 0; k < nStatesDelayedDecision; k++ ) {
			final JNSQ_del_dec_struct psDD = psDelDec[ k ];
			System.arraycopy( psDD.sLPC_Q14, length, psDD.sLPC_Q14, 0, Jdefine.NSQ_LPC_BUF_LENGTH );
		}
		// RESTORE_STACK;
		return smpl_buf_idx;
	}
	/**
	 *
	 * @param psEncC I    Encoder State
	 * @param NSQ I/O  NSQ state
	 * @param x16 I    input in Q3
	 * @param xoffset I java an offset for the x16
	 * @param x_sc_Q10 O    input scaled with 1/Gain
	 * @param sLTP I    re-whitened LTP state in Q0
	 * @param sLTP_Q15 O    LTP state matching scaled input
	 * @param subfr I    subframe number
	 * @param LTP_scale_Q14 I
	 * @param Gains_Q16 I
	 * @param pitchL I    Pitch lag
	 * @param signal_type I    Signal type
	 */
	private final void silk_nsq_scale_states(final Jsilk_encoder_state psEncC,// final Jsilk_nsq_state NSQ,
			final short x16[], int xoffset,// java
			final int x_sc_Q10[], final short sLTP[], final int sLTP_Q15[],
			final int subfr, final int LTP_scale_Q14, final int Gains_Q16[/* MAX_NB_SUBFR */],
			final int pitchL[/* MAX_NB_SUBFR */], final int signal_type
	)
	{
		final int lag = pitchL[ subfr ];
		int inv_gain_Q31 = Gains_Q16[ subfr ];
		inv_gain_Q31 = inv_gain_Q31 > 1 ? inv_gain_Q31 : 1;
		inv_gain_Q31 = silk_INVERSE32_varQ( inv_gain_Q31, 47 );
		// silk_assert( inv_gain_Q31 != 0 );

		/* Scale input */
		final long inv_gain_Q26 = (long)JSigProc_FIX.silk_RSHIFT_ROUND( inv_gain_Q31, 5 );
		for( int i = 0, ie = psEncC.subfr_length; i < ie; i++ ) {
			x_sc_Q10[ i ] = (int)(((long)x16[ xoffset++ ] * inv_gain_Q26) >> 16);
		}

		/* After rewhitening the LTP state is un-scaled, so scale with inv_gain_Q16 */
		if( this.rewhite_flag ) {
			if( subfr == 0 ) {
				/* Do LTP downscaling */
				inv_gain_Q31 = ( (int)((inv_gain_Q31 * (long)LTP_scale_Q14) >> 16) ) << 2;
			}
			for( int i = this.sLTP_buf_idx - lag - Jdefine.LTP_ORDER / 2, ie = this.sLTP_buf_idx; i < ie; i++ ) {
				// silk_assert( i < MAX_FRAME_LENGTH );
				sLTP_Q15[ i ] = (int)((inv_gain_Q31 * (long)sLTP[ i ]) >> 16);
			}
		}

		/* Adjust for changing gain */
		if( Gains_Q16[ subfr ] != this.prev_gain_Q16 ) {
			final long gain_adj_Q16 =  silk_DIV32_varQ( this.prev_gain_Q16, Gains_Q16[ subfr ], 16 );// java int->long

			int[] buff = this.sLTP_shp_Q14;// java
			/* Scale long-term shaping state */
			for( int i = this.sLTP_shp_buf_idx - psEncC.ltp_mem_length, ie = this.sLTP_shp_buf_idx; i < ie; i++ ) {
				buff[ i ] = ( (int)((gain_adj_Q16 * buff[ i ]) >> 16) );
			}

			/* Scale long-term prediction state */
			if( signal_type == Jdefine.TYPE_VOICED && ! this.rewhite_flag ) {
				for( int i = this.sLTP_buf_idx - lag - Jdefine.LTP_ORDER / 2, ie = this.sLTP_buf_idx; i < ie; i++ ) {
					sLTP_Q15[ i ] = (int)((gain_adj_Q16 * sLTP_Q15[ i ]) >> 16);
				}
			}

			this.sLF_AR_shp_Q14 = (int)((gain_adj_Q16 * this.sLF_AR_shp_Q14) >> 16);
			this.sDiff_shp_Q14 = (int)((gain_adj_Q16 * this.sDiff_shp_Q14) >> 16);

			/* Scale short-term prediction and shaping states */
			buff = this.sLPC_Q14;// java
			for( int i = 0; i < Jdefine.NSQ_LPC_BUF_LENGTH; i++ ) {
				buff[ i ] = (int)((gain_adj_Q16 * buff[ i ]) >> 16);
			}
			buff = this.sAR2_Q14;// java
			for( int i = 0; i < Jdefine.MAX_SHAPE_LPC_ORDER; i++ ) {
				buff[ i ] = (int)((gain_adj_Q16 * buff[ i ]) >> 16);
			}

	        /* Save inverse gain */
			this.prev_gain_Q16 = Gains_Q16[ subfr ];
		}
	}
	// silk_NSQ_c
	/**
	 *
	 * @param psEncC I  Encoder State
	 * @param NSQ I/O  NSQ state
	 * @param psIndices I/O  Quantization Indices
	 * @param x16 I    Input
	 * @param pulses O    Quantized pulse signal
	 * @param PredCoef_Q12 I    Short term prediction coefs
	 * @param LTPCoef_Q14 I    Long term prediction coefs
	 * @param AR_Q13 I Noise shaping coefs
	 * @param HarmShapeGain_Q14 I    Long term shaping coefs
	 * @param Tilt_Q14 I    Spectral tilt
	 * @param LF_shp_Q14 I    Low frequency shaping coefs
	 * @param Gains_Q16 I    Quantization step sizes
	 * @param pitchL I    Pitch lags
	 * @param Lambda_Q10 I    Rate/distortion tradeoff
	 * @param LTP_scale_Q14 I    LTP state scaling
	 */
	final void silk_NSQ/*_c*/(final Jsilk_encoder_state psEncC,// final Jsilk_nsq_state NSQ,
			final JSideInfoIndices psIndices,
			final short x16[], final byte pulses[], final short PredCoef_Q12[/* 2 * MAX_LPC_ORDER */][],
			final short LTPCoef_Q14[/* LTP_ORDER * MAX_NB_SUBFR */], final short AR_Q13[/* MAX_NB_SUBFR * MAX_SHAPE_LPC_ORDER */],
			final int HarmShapeGain_Q14[/* MAX_NB_SUBFR */], final int Tilt_Q14[/* MAX_NB_SUBFR */],
			final int LF_shp_Q14[/* MAX_NB_SUBFR */], final int Gains_Q16[/* MAX_NB_SUBFR */],
			final int pitchL[/* MAX_NB_SUBFR */], final int Lambda_Q10, final int LTP_scale_Q14)
	{// java name changed
		int xoffset = 0;// java x16[ xoffset ]
		int poffset = 0;// java pulses[ poffset ]
		// SAVE_STACK;

		this.rand_seed = psIndices.Seed;

		/* Set unvoiced lag to the previous one, overwrite later for voiced */
		int lag = this.lagPrev;

		// silk_assert( NSQ.prev_gain_Q16 != 0 );

		final int offset_Q10 = (int)Jtables_other.silk_Quantization_Offsets_Q10[ psIndices.signalType >> 1 ][ psIndices.quantOffsetType ];

		final int LSF_interpolation_flag = ( psIndices.NLSFInterpCoef_Q2 == 4 ) ? 0 : 1;

		final int subfr_length = psEncC.subfr_length;// java
		final int[] sLTP_Q15 = new int[psEncC.ltp_mem_length + psEncC.frame_length];
		final short[] sLTP = new short[psEncC.ltp_mem_length + psEncC.frame_length];
		final int[] x_sc_Q10 = new int[subfr_length];
		/* Set up pointers to start of sub frame */
		this.sLTP_shp_buf_idx = psEncC.ltp_mem_length;
		this.sLTP_buf_idx     = psEncC.ltp_mem_length;
		int pxq = psEncC.ltp_mem_length;// NSQ.xq[ pxq ]
		final short[] xqo = this.xq;// java
		int nb_subfr = psEncC.nb_subfr;// java
		for( int k = 0; k < nb_subfr; k++ ) {
			// A_Q12      = &PredCoef_Q12[ (( k >> 1 ) | ( 1 - LSF_interpolation_flag )) * MAX_LPC_ORDER ];// FIXME dirty way to navigate on rows
			final int A_Q12      = (( k >> 1 ) | ( 1 - LSF_interpolation_flag ));// java: don't need * Jdefine.MAX_LPC_ORDER, because this is row navigation
			final int B_Q14      = k * Jdefine.LTP_ORDER;// LTPCoef_Q14[ B_Q14 ]
			final int AR_shp_Q13 = k * Jdefine.MAX_SHAPE_LPC_ORDER;// AR_Q13[ AR_shp_Q13 ]

			/* Noise shape parameters */
			// silk_assert( HarmShapeGain_Q14[ k ] >= 0 );
			int HarmShapeFIRPacked_Q14  =  ( HarmShapeGain_Q14[ k ] >> 2 );
			HarmShapeFIRPacked_Q14     |= (( HarmShapeGain_Q14[ k ] >> 1 ) << 16 );

			this.rewhite_flag = false;
			if( psIndices.signalType == Jdefine.TYPE_VOICED ) {
				/* Voiced */
				lag = pitchL[ k ];

				/* Re-whitening */
				if( ( k & (3 - (LSF_interpolation_flag << 1)) ) == 0 ) {
					/* Rewhiten with new A coefs */
					final int start_idx = psEncC.ltp_mem_length - lag - psEncC.predictLPCOrder - Jdefine.LTP_ORDER / 2;
					// celt_assert( start_idx > 0 );

					silk_LPC_analysis_filter( sLTP, start_idx, this.xq, start_idx + k * subfr_length,
								PredCoef_Q12[A_Q12], psEncC.ltp_mem_length - start_idx, psEncC.predictLPCOrder );//, psEncC.arch );

					this.rewhite_flag = true;
					this.sLTP_buf_idx = psEncC.ltp_mem_length;
				}
			}

			silk_nsq_scale_states( psEncC, x16, xoffset, x_sc_Q10, sLTP, sLTP_Q15, k, LTP_scale_Q14, Gains_Q16, pitchL, psIndices.signalType );

			silk_noise_shape_quantizer( psIndices.signalType, x_sc_Q10, pulses, poffset, xqo, pxq, sLTP_Q15, PredCoef_Q12[A_Q12], LTPCoef_Q14, B_Q14,
					AR_Q13, AR_shp_Q13, lag, HarmShapeFIRPacked_Q14, Tilt_Q14[ k ], LF_shp_Q14[ k ], Gains_Q16[ k ], Lambda_Q10,
					offset_Q10, subfr_length, psEncC.shapingLPCOrder, psEncC.predictLPCOrder );//, psEncC.arch );

			xoffset += subfr_length;
			poffset += subfr_length;
			pxq     += psEncC.subfr_length;
		}

		/* Update lagPrev for next frame */
		this.lagPrev = pitchL[ --nb_subfr ];

		/* Save quantized speech and noise shaping signals */
		System.arraycopy( this.xq, psEncC.frame_length, this.xq, 0, psEncC.ltp_mem_length );
		System.arraycopy( this.sLTP_shp_Q14, psEncC.frame_length, this.sLTP_shp_Q14, 0, psEncC.ltp_mem_length );
		// RESTORE_STACK;
	}
	// end NSQ.c

	// start NSQ_del_dec.c
	private static final class JNSQ_del_dec_struct {
		final int sLPC_Q14[] = new int[ Jdefine.MAX_SUB_FRAME_LENGTH + Jdefine.NSQ_LPC_BUF_LENGTH ];
		final int RandState[] = new int[ Jdefine.DECISION_DELAY ];
		final int Q_Q10[] = new int[     Jdefine.DECISION_DELAY ];
		final int Xq_Q14[] = new int[    Jdefine.DECISION_DELAY ];
		final int Pred_Q15[] = new int[  Jdefine.DECISION_DELAY ];
		final int Shape_Q14[] = new int[ Jdefine.DECISION_DELAY ];
		final int sAR2_Q14[] = new int[ Jdefine.MAX_SHAPE_LPC_ORDER ];
		int LF_AR_Q14;
		private int Diff_Q14;
		int Seed;
		int SeedInit;
		int RD_Q10;
		//
		final void copyFromStart(final JNSQ_del_dec_struct src, final int offset) {
			System.arraycopy( src.sLPC_Q14, offset, sLPC_Q14, offset, Jdefine.MAX_SUB_FRAME_LENGTH + Jdefine.NSQ_LPC_BUF_LENGTH - offset );
			System.arraycopy( src.RandState, 0, RandState, 0, Jdefine.DECISION_DELAY );
			System.arraycopy( src.Q_Q10,     0, Q_Q10,     0, Jdefine.DECISION_DELAY );
			System.arraycopy( src.Xq_Q14,    0, Xq_Q14,    0, Jdefine.DECISION_DELAY );
			System.arraycopy( src.Pred_Q15,  0, Pred_Q15,  0, Jdefine.DECISION_DELAY );
			System.arraycopy( src.Shape_Q14, 0, Shape_Q14, 0, Jdefine.DECISION_DELAY );
			System.arraycopy( src.sAR2_Q14,  0, sAR2_Q14,  0, Jdefine.MAX_SHAPE_LPC_ORDER );
			LF_AR_Q14 = src.LF_AR_Q14;
			Diff_Q14 = src.Diff_Q14;
			Seed = src.Seed;
			SeedInit = src.SeedInit;
			RD_Q10 = src.RD_Q10;
		}
	}

	/**
	 *
	 * @param psEncC I    Encoder State
	 * @param NSQ I/O  NSQ state
	 * @param psDelDec I/O  Delayed decision states
	 * @param x16 I    Input
	 * @param xoffset I java an offset for the x16
	 * @param x_sc_Q10 O    Input scaled with 1/Gain in Q10
	 * @param sLTP I    Re-whitened LTP state in Q0
	 * @param sLTP_Q15 O    LTP state matching scaled input
	 * @param subfr I    Subframe number
	 * @param nStatesDelayedDecision I    Number of del dec states
	 * @param LTP_scale_Q14 I    LTP state scaling
	 * @param Gains_Q16 I
	 * @param pitchL I    Pitch lag
	 * @param signal_type I    Signal type
	 * @param decisionDelay I    Decision delay
	 */
	private final void silk_nsq_del_dec_scale_states(final Jsilk_encoder_state psEncC,// final Jsilk_nsq_state NSQ,
			final JNSQ_del_dec_struct psDelDec[],
			final short x16[], int xoffset,// java
			final int x_sc_Q10[], final short sLTP[], final int sLTP_Q15[],
			final int subfr, final int nStatesDelayedDecision, final int LTP_scale_Q14,
			final int Gains_Q16[/* MAX_NB_SUBFR */], final int pitchL[/* MAX_NB_SUBFR */],
			final int signal_type, final int decisionDelay
		)
	{
		final int lag    = pitchL[ subfr ];
		int inv_gain_Q31 = Gains_Q16[ subfr ];
		inv_gain_Q31 = silk_INVERSE32_varQ( (inv_gain_Q31 >= 1 ? inv_gain_Q31 : 1), 47 );
		// silk_assert( inv_gain_Q31 != 0 );

		/* Scale input */
		final int inv_gain_Q26 = JSigProc_FIX.silk_RSHIFT_ROUND( inv_gain_Q31, 8 );
		for( int i = 0, ie = psEncC.subfr_length; i < ie; i++ ) {
			x_sc_Q10[ i ] = (int)(((long)x16[ xoffset++ ] * inv_gain_Q26) >> 16);
		}

		/* Save inverse gain */
		this.prev_gain_Q16 = Gains_Q16[ subfr ];

		/* After rewhitening the LTP state is un-scaled, so scale with inv_gain_Q16 */
		if( this.rewhite_flag ) {
			if( subfr == 0 ) {
				/* Do LTP downscaling */
				inv_gain_Q31 = ((int)((inv_gain_Q31 * (long)LTP_scale_Q14) >> 16)) << 2;
			}
			for( int i = this.sLTP_buf_idx - lag - Jdefine.LTP_ORDER / 2, ie = this.sLTP_buf_idx; i < ie; i++ ) {
				// silk_assert( i < MAX_FRAME_LENGTH );
				sLTP_Q15[ i ] = (int)((inv_gain_Q31 * (long)sLTP[ i ]) >> 16);
			}
		}

		/* Adjust for changing gain */
		if( Gains_Q16[ subfr ] != this.prev_gain_Q16 ) {
			final long gain_adj_Q16 =  silk_DIV32_varQ( this.prev_gain_Q16, Gains_Q16[ subfr ], 16 );// java int->long

			/* Scale long-term shaping state */
			int[] buf = this.sLTP_shp_Q14;// java
			for( int i = this.sLTP_shp_buf_idx - psEncC.ltp_mem_length, ie = this.sLTP_shp_buf_idx; i < ie; i++ ) {
				buf[ i ] = (int)((gain_adj_Q16 * buf[ i ]) >> 16);
			}

			/* Scale long-term prediction state */
			if( signal_type == Jdefine.TYPE_VOICED && ! this.rewhite_flag ) {
				for( int i = this.sLTP_buf_idx - lag - Jdefine.LTP_ORDER / 2, ie = this.sLTP_buf_idx - decisionDelay; i < ie; i++ ) {
					sLTP_Q15[ i ] = (int)((gain_adj_Q16 * sLTP_Q15[ i ]) >> 16);
				}
			}

			for( int k = 0; k < nStatesDelayedDecision; k++ ) {
				final JNSQ_del_dec_struct psDD = psDelDec[ k ];

				/* Scale scalar states */
				psDD.LF_AR_Q14 = (int)((gain_adj_Q16 * psDD.LF_AR_Q14) >> 16);
				psDD.Diff_Q14 = (int)((gain_adj_Q16 * psDD.Diff_Q14) >> 16);

				/* Scale short-term prediction and shaping states */
				buf = psDD.sLPC_Q14;// java
				for( int i = 0; i < Jdefine.NSQ_LPC_BUF_LENGTH; i++ ) {
					buf[ i ] = (int)((gain_adj_Q16 * buf[ i ]) >> 16);
				}
				buf = psDD.sAR2_Q14;// java
				for( int i = 0; i < Jdefine.MAX_SHAPE_LPC_ORDER; i++ ) {
					buf[ i ] = (int)((gain_adj_Q16 * buf[ i ]) >> 16);
				}
				buf = psDD.Pred_Q15;// java
				final int[] shape = psDD.Shape_Q14;// java
				for( int i = 0; i < Jdefine.DECISION_DELAY; i++ ) {
					buf[  i ] = (int)((gain_adj_Q16 * buf[  i ]) >> 16);
					shape[ i ] = (int)((gain_adj_Q16 * shape[ i ]) >> 16);
				}
			}
		}

        /* Save inverse gain */
        this.prev_gain_Q16 = Gains_Q16[ subfr ];
	}

// #endif /* OVERRIDE_silk_noise_shape_quantizer_del_dec */
	// typedef NSQ_sample_struct  NSQ_sample_pair[ 2 ];
	/******************************************/
	/* Noise shape quantizer for one subframe */
	/******************************************/
	// static OPUS_INLINE void silk_noise_shape_quantizer_del_dec
	// silk_NSQ_del_dec_c
	/**
	 *
	 * @param psEncC I/O  Encoder State
	 * @param NSQ I/O  NSQ state
	 * @param psIndices I/O  Quantization Indices
	 * @param x16 I    Input
	 * @param pulses O    Quantized pulse signal
	 * @param PredCoef_Q12 I    Short term prediction coefs
	 * @param LTPCoef_Q14 I    Long term prediction coefs
	 * @param AR_Q13 I Noise shaping coefs
	 * @param HarmShapeGain_Q14 I    Long term shaping coefs
	 * @param Tilt_Q14 I    Spectral tilt
	 * @param LF_shp_Q14 I    Low frequency shaping coefs
	 * @param Gains_Q16 I    Quantization step sizes
	 * @param pitchL I    Pitch lags
	 * @param Lambda_Q10 I    Rate/distortion tradeoff
	 * @param LTP_scale_Q14 I    LTP state scaling
	 */
	final void silk_NSQ_del_dec/*_c*/(final Jsilk_encoder_state psEncC,// final Jsilk_nsq_state NSQ,
			final JSideInfoIndices psIndices,
			final short x16[], final byte pulses[], final short PredCoef_Q12[/* 2 * MAX_LPC_ORDER */][],
			final short LTPCoef_Q14[/* LTP_ORDER * MAX_NB_SUBFR */], final short AR_Q13[/* MAX_NB_SUBFR * MAX_SHAPE_LPC_ORDER */],
			final int HarmShapeGain_Q14[/* MAX_NB_SUBFR */], final int Tilt_Q14[/* MAX_NB_SUBFR */], final int LF_shp_Q14[/* MAX_NB_SUBFR */],
			final int Gains_Q16[/* MAX_NB_SUBFR */], final int pitchL[/* MAX_NB_SUBFR */],
			final int Lambda_Q10, final int LTP_scale_Q14)
	{// java name changed
		int xoffset = 0;// java x16[ xoffset ]
		int poffset = 0;// java pulses[ poffset ]
		// SAVE_STACK;

		/* Set unvoiced lag to the previous one, overwrite later for voiced */
		int lag = this.lagPrev;

		// silk_assert( NSQ.prev_gain_Q16 != 0 );

		/* Initialize delayed decision states */
		final JNSQ_del_dec_struct[] psDelDec = new JNSQ_del_dec_struct[psEncC.nStatesDelayedDecision];
		// java: psDelDec already zeroed
		// silk_memset( psDelDec, 0, psEncC.nStatesDelayedDecision * sizeof( NSQ_del_dec_struct ) );
		for( int k = 0, ke = psEncC.nStatesDelayedDecision; k < ke; k++ ) {
			final JNSQ_del_dec_struct psDD = new JNSQ_del_dec_struct();// java
			psDelDec[ k ] = psDD;// java
			psDD.Seed           = ( k + psIndices.Seed ) & 3;
			psDD.SeedInit       = psDD.Seed;
			psDD.RD_Q10         = 0;
			psDD.LF_AR_Q14      = this.sLF_AR_shp_Q14;
			psDD.Diff_Q14       = this.sDiff_shp_Q14;
			psDD.Shape_Q14[ 0 ] = this.sLTP_shp_Q14[ psEncC.ltp_mem_length - 1 ];
			System.arraycopy( this.sLPC_Q14, 0, psDD.sLPC_Q14, 0, Jdefine.NSQ_LPC_BUF_LENGTH );
			System.arraycopy( this.sAR2_Q14, 0, psDD.sAR2_Q14, 0, this.sAR2_Q14.length );
		}

		final int offset_Q10   = (int)Jtables_other.silk_Quantization_Offsets_Q10[ psIndices.signalType >> 1 ][ psIndices.quantOffsetType ];
		int smpl_buf_idx = 0; /* index of oldest samples */

		int decisionDelay = Jdefine.DECISION_DELAY < psEncC.subfr_length ? Jdefine.DECISION_DELAY : psEncC.subfr_length;

		/* For voiced frames limit the decision delay to lower than the pitch lag */
		if( psIndices.signalType == Jdefine.TYPE_VOICED ) {
			for( int k = 0, ke = psEncC.nb_subfr; k < ke; k++ ) {
				final int v = pitchL[ k ] - Jdefine.LTP_ORDER / 2 - 1;// java
				decisionDelay = ( decisionDelay <= v ? decisionDelay : v );
			}
		} else {
			if( lag > 0 ) {
				final int v = lag - Jdefine.LTP_ORDER / 2 - 1;// java
				decisionDelay = ( decisionDelay <= v ? decisionDelay : v );
			}
		}

		final int LSF_interpolation_flag = (psIndices.NLSFInterpCoef_Q2 == 4 ? 0 : 1);

		final int[] sLTP_Q15 = new int[psEncC.ltp_mem_length + psEncC.frame_length];
		final short[] sLTP = new short[psEncC.ltp_mem_length + psEncC.frame_length];
		final int[] x_sc_Q10 = new int[psEncC.subfr_length];
		final int[] delayedGain_Q10 = new int[ Jdefine.DECISION_DELAY ];
		/* Set up pointers to start of sub frame */
		int pxq              = psEncC.ltp_mem_length;// &NSQ.xq[ psEncC.ltp_mem_length ];
		final short[] xqo = this.xq;// java xq[ pxq ]
		this.sLTP_shp_buf_idx = psEncC.ltp_mem_length;
		int sLTP_shp_buf_idx_delay = this.sLTP_shp_buf_idx - decisionDelay;// java
		this.sLTP_buf_idx     = psEncC.ltp_mem_length;
		int subfr = 0;
		for( int k = 0, ke = psEncC.nb_subfr; k < ke; k++ ) {
			// A_Q12      = &PredCoef_Q12[ ( ( k >> 1 ) | ( 1 - LSF_interpolation_flag ) ) * MAX_LPC_ORDER ];// FIXME dirty way to navigate on rows
			final int A_Q12      = (( k >> 1 ) | ( 1 - LSF_interpolation_flag ));// java: don't need * Jdefine.MAX_LPC_ORDER, because this is row navigation
			final int B_Q14      = k * Jdefine.LTP_ORDER;// LTPCoef_Q14[ B_Q14 ]
			final int AR_shp_Q13 = k * Jdefine.MAX_SHAPE_LPC_ORDER;// AR_Q13[ AR_shp_Q13 ]

			/* Noise shape parameters */
			// silk_assert( HarmShapeGain_Q14[ k ] >= 0 );
			int HarmShapeFIRPacked_Q14 = ( HarmShapeGain_Q14[ k ] >> 2 );
			HarmShapeFIRPacked_Q14 |= ( ( HarmShapeGain_Q14[ k ] >> 1 ) << 16 );

			this.rewhite_flag = false;
			if( psIndices.signalType == Jdefine.TYPE_VOICED ) {
				/* Voiced */
				lag = pitchL[ k ];

				/* Re-whitening */
				if( ( k & (3 - (LSF_interpolation_flag << 1)) ) == 0 ) {
					if( k == 2 ) {
						/* RESET DELAYED DECISIONS */
						/* Find winner */
						int RDmin_Q10 = psDelDec[ 0 ].RD_Q10;
						int Winner_ind = 0;
						int v = psEncC.nStatesDelayedDecision;// java
						for( int i = 1; i < v; i++ ) {
							if( psDelDec[ i ].RD_Q10 < RDmin_Q10 ) {
								RDmin_Q10 = psDelDec[ i ].RD_Q10;
								Winner_ind = i;
							}
						}
						for( int i = 0; i < v; i++ ) {
							if( i != Winner_ind ) {
								psDelDec[ i ].RD_Q10 += ( Integer.MAX_VALUE >> 4 );
								// silk_assert( psDelDec[ i ].RD_Q10 >= 0 );
							}
						}

						/* Copy final part of signals from winner state to output and long-term filter states */
						final JNSQ_del_dec_struct psDD = psDelDec[ Winner_ind ];
						int last_smple_idx = smpl_buf_idx + decisionDelay;
						final long gain_Q16 = (long)Gains_Q16[ 1 ];// java
						for( int pi = poffset - decisionDelay, xqi = pxq - decisionDelay, si = sLTP_shp_buf_idx_delay, ie = si + decisionDelay; si < ie; ) {
							last_smple_idx = ( last_smple_idx - 1 ) % Jdefine.DECISION_DELAY;
							if( last_smple_idx < 0 ) {
								last_smple_idx += Jdefine.DECISION_DELAY;
							}
							pulses[ pi++ ] = (byte)JSigProc_FIX.silk_RSHIFT_ROUND( psDD.Q_Q10[ last_smple_idx ], 10 );
							v = JSigProc_FIX.silk_RSHIFT_ROUND( ((int)((long)psDD.Xq_Q14[ last_smple_idx ] * gain_Q16) >> 16), 14 );// java
							xqo[ xqi++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
							this.sLTP_shp_Q14[ si++ ] = psDD.Shape_Q14[ last_smple_idx ];
						}

						subfr = 0;
					}

					/* Rewhiten with new A coefs */
					final int start_idx = psEncC.ltp_mem_length - lag - psEncC.predictLPCOrder - Jdefine.LTP_ORDER / 2;
					// celt_assert( start_idx > 0 );

					silk_LPC_analysis_filter( sLTP, start_idx, this.xq, start_idx + k * psEncC.subfr_length,
								PredCoef_Q12[A_Q12], psEncC.ltp_mem_length - start_idx, psEncC.predictLPCOrder );//, psEncC.arch );

					this.sLTP_buf_idx = psEncC.ltp_mem_length;
					this.rewhite_flag = true;
				}
			}

			silk_nsq_del_dec_scale_states( psEncC, psDelDec, x16, xoffset, x_sc_Q10, sLTP, sLTP_Q15, k,
					psEncC.nStatesDelayedDecision, LTP_scale_Q14, Gains_Q16, pitchL, psIndices.signalType, decisionDelay );

			smpl_buf_idx = silk_noise_shape_quantizer_del_dec( psDelDec, (int)psIndices.signalType, x_sc_Q10, pulses, poffset, xqo, pxq, sLTP_Q15,
					delayedGain_Q10, PredCoef_Q12[A_Q12], LTPCoef_Q14, B_Q14, AR_Q13, AR_shp_Q13, lag, HarmShapeFIRPacked_Q14, Tilt_Q14[ k ], LF_shp_Q14[ k ],
					Gains_Q16[ k ], Lambda_Q10, offset_Q10, psEncC.subfr_length, subfr++, psEncC.shapingLPCOrder,
					psEncC.predictLPCOrder, psEncC.warping_Q16, psEncC.nStatesDelayedDecision, smpl_buf_idx, decisionDelay );//, psEncC.arch );

			xoffset += psEncC.subfr_length;
			poffset += psEncC.subfr_length;
			pxq     += psEncC.subfr_length;
		}

		/* Find winner */
		int RDmin_Q10 = psDelDec[ 0 ].RD_Q10;
		int Winner_ind = 0;
		for( int k = 1; k < psEncC.nStatesDelayedDecision; k++ ) {
			if( psDelDec[ k ].RD_Q10 < RDmin_Q10 ) {
				RDmin_Q10 = psDelDec[ k ].RD_Q10;
				Winner_ind = k;
			}
		}

		/* Copy final part of signals from winner state to output and long-term filter states */
		final JNSQ_del_dec_struct psDD = psDelDec[ Winner_ind ];
		psIndices.Seed = (byte)psDD.SeedInit;// FIXME casting int to byte
		int last_smple_idx = smpl_buf_idx + decisionDelay;
		final long Gain_Q10 = (long)(Gains_Q16[ psEncC.nb_subfr - 1 ] >> 6);
		poffset -= decisionDelay;// java
		pxq -= decisionDelay;// java
		for( decisionDelay += poffset; poffset < decisionDelay; ) {
			last_smple_idx = ( last_smple_idx - 1 ) % Jdefine.DECISION_DELAY;
	        if( last_smple_idx < 0 ) {
				last_smple_idx += Jdefine.DECISION_DELAY;
			}

			pulses[ poffset++ ] = (byte)JSigProc_FIX.silk_RSHIFT_ROUND( psDD.Q_Q10[ last_smple_idx ], 10 );
			final int v = JSigProc_FIX.silk_RSHIFT_ROUND( ((int)(((long)psDD.Xq_Q14[ last_smple_idx ] * Gain_Q10) >> 16)), 8 );// java
			xqo[ pxq++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			this.sLTP_shp_Q14[ sLTP_shp_buf_idx_delay++ ] = psDD.Shape_Q14[ last_smple_idx ];
		}
		System.arraycopy( psDD.sLPC_Q14, psEncC.subfr_length, this.sLPC_Q14, 0, Jdefine.NSQ_LPC_BUF_LENGTH );
		System.arraycopy( psDD.sAR2_Q14, 0, this.sAR2_Q14, 0, psDD.sAR2_Q14.length );

		/* Update states */
		this.sLF_AR_shp_Q14 = psDD.LF_AR_Q14;
		this.sDiff_shp_Q14  = psDD.Diff_Q14;
		this.lagPrev        = pitchL[ psEncC.nb_subfr - 1 ];

		/* Save quantized speech signal */
		System.arraycopy( this.xq, psEncC.frame_length, this.xq, 0, psEncC.ltp_mem_length );
		System.arraycopy( this.sLTP_shp_Q14, psEncC.frame_length, this.sLTP_shp_Q14, 0, psEncC.ltp_mem_length );
		// RESTORE_STACK;
	}

// #ifndef OVERRIDE_silk_noise_shape_quantizer_del_dec
	// end NSQ_del_dec.c
}
