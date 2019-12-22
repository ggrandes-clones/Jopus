package silk;

// structs.h

final class Jstereo_enc_state extends Jcodec_API {
    final short pred_prev_Q13[] = new short[ 2 ];
    final short sMid[] = new short[ 2 ];
    final short sSide[] = new short[ 2 ];
    final int   mid_side_amp_Q0[] = new int[ 4 ];
    short       smth_width_Q14;
    short       width_prev_Q14;
    short       silent_side_len;
    final byte  predIx[][][] = new byte[ Jdefine.MAX_FRAMES_PER_PACKET ][ 2 ][ 3 ];
    final byte  mid_only_flags[] = new byte[ Jdefine.MAX_FRAMES_PER_PACKET ];
    //
    final void clear() {
    	pred_prev_Q13[0] = 0; pred_prev_Q13[1] = 0;
    	sMid[0] = 0; sMid[1] = 0;
    	sSide[0] = 0; sSide[1] = 0;
    	mid_side_amp_Q0[0] = 0; mid_side_amp_Q0[1] = 0; mid_side_amp_Q0[2] = 0; mid_side_amp_Q0[3] = 0;
    	smth_width_Q14 = 0;
    	width_prev_Q14 = 0;
    	silent_side_len = 0;
    	//
    	final byte[][][] buff3 = predIx;
    	int i = Jdefine.MAX_FRAMES_PER_PACKET;
    	do {
    		byte[] buf = buff3[--i][0];
    		buf[0] = 0; buf[1] = 0; buf[2] = 0;
    		buf = buff3[i][1];
    		buf[0] = 0; buf[1] = 0; buf[2] = 0;
    	} while( i > 0 );
    	//
    	final byte[] buf = mid_only_flags;
    	i = Jdefine.MAX_FRAMES_PER_PACKET;
    	do {
    		buf[--i] = 0;
    	} while( i > 0 );
    }
    final void copyFrom(final Jstereo_enc_state s) {
    	pred_prev_Q13[0] = s.pred_prev_Q13[0]; pred_prev_Q13[1] = s.pred_prev_Q13[1];
    	sMid[0] = s.sMid[0]; sMid[1] = s.sMid[1];
    	sSide[0] = s.sSide[0]; sSide[1] = s.sSide[1];
    	mid_side_amp_Q0[0] = s.mid_side_amp_Q0[0]; mid_side_amp_Q0[1] = s.mid_side_amp_Q0[1]; mid_side_amp_Q0[2] = s.mid_side_amp_Q0[2]; mid_side_amp_Q0[3] = s.mid_side_amp_Q0[3];
    	smth_width_Q14 = s.smth_width_Q14;
    	width_prev_Q14 = s.width_prev_Q14;
    	silent_side_len = s.silent_side_len;
    	//
    	final byte[][][] buff3 = predIx;
    	final byte[][][] sbuff3 = s.predIx;
    	int i = Jdefine.MAX_FRAMES_PER_PACKET;
    	do {
    		i--;
    		byte[] buf = buff3[i][0];
    		byte[] sbuf = sbuff3[i][0];
    		buf[0] = sbuf[0]; buf[1] = sbuf[1]; buf[2] = sbuf[2];
    		buf = buff3[i][1];
    		sbuf = sbuff3[i][1];
    		buf[0] = sbuf[0]; buf[1] = sbuf[1]; buf[2] = sbuf[2];
    	} while( i > 0 );
    	System.arraycopy( s.mid_only_flags, 0, mid_only_flags, 0, Jdefine.MAX_FRAMES_PER_PACKET );
    }
    //
    // start stereo_quant_pred.c
    /**
	 * Quantize mid/side predictors
	 *
	 * @param pred_Q13 I/O  Predictors (out: quantized)
	 * @param ix O    Quantization indices
	 */
	private static final void silk_stereo_quant_pred(final int pred_Q13[], final byte ix[/* 2 */][/* 3 */])
	{
		int quant_pred_Q13 = 0;

		/* Quantize */
		for( int n = 0; n < 2; n++ ) {
			final byte[] buff = ix[ n ];// java
			/* Brute-force search over quantization levels */
			int err_min_Q13 = Integer.MAX_VALUE;
		loop_i:
			for( int i = 0; i < Jdefine.STEREO_QUANT_TAB_SIZE - 1; i++ ) {
				final int low_Q13 = Jtables_other.silk_stereo_pred_quant_Q13[ i ];
				// final int step_Q13 = Jmacros.silk_SMULWB( (int)Jtables_other.silk_stereo_pred_quant_Q13[ i + 1 ] - low_Q13,
				//									SILK_FIX_CONST( 0.5 / Jdefine.STEREO_QUANT_SUB_STEPS, 16 ) );
				final int step_Q13 = (int)(((long)(int)Jtables_other.silk_stereo_pred_quant_Q13[ i + 1 ] - low_Q13 *
												((long)((0.5 / Jdefine.STEREO_QUANT_SUB_STEPS) * (1 << 16) + .5 ))) >> 16);
				for( int j = 0; j < Jdefine.STEREO_QUANT_SUB_STEPS; j++ ) {
					final int lvl_Q13 = ( low_Q13 + step_Q13 * ((j << 1) + 1) );
					int err_Q13 = pred_Q13[ n ] - lvl_Q13;
					err_Q13 = err_Q13 >= 0 ? err_Q13 : -err_Q13;
					if( err_Q13 < err_min_Q13 ) {
						err_min_Q13 = err_Q13;
						quant_pred_Q13 = lvl_Q13;
						buff[ 0 ] = (byte)i;
						buff[ 1 ] = (byte)j;
					} else {
						/* Error increasing, so we're past the optimum */
						break loop_i;// goto done;
					}
				}
			}
		//done:
			buff[ 2 ]  = (byte)( (int)buff[ 0 ] / 3 );
			buff[ 0 ] -= buff[ 2 ] * 3;
			pred_Q13[ n ] = quant_pred_Q13;
		}

		/* Subtract second from first predictor (helps when actually applying these) */
		pred_Q13[ 0 ] -= pred_Q13[ 1 ];
	}
    // end stereo_quant_pred.c

	// start inner_prod_aligned.c
	/**
	 *
	 * @param inVec1 I input vector 1
	 * @param inVec2 I input vector 2
	 * @param scale I number of bits to shift
	 * @param len I vector lengths
	 * @return
	 */
	private static final int silk_inner_prod_aligned_scale(final short[] inVec1, final short[] inVec2, final int scale, final int len)
	{
		int sum = 0;
		for( int i = 0; i < len; i++ ) {
			sum += ((int)inVec1[ i ] * (int)inVec2[ i ]) >> scale;
		}
		return sum;
	}
	// end inner_prod_aligned.c

	// start stereo_find_predictor.c
	/**
	 * Find least-squares prediction gain for one signal based on another and quantize it
	 *
	 * java changed: return int64, predictor in Q13 | (ratio_Q14 << 32)
	 *
	 * @param ratio_Q14 O    Ratio of residual and mid energies
	 * @param x I    Basis signal
	 * @param y I    Target signal
	 * @param mid_res_amp_Q0 I/O  Smoothed mid, residual norms
	 * @param length I    Number of samples
	 * @param smooth_coef_Q16 I    Smoothing coefficient
	 * @return O    Returns predictor in Q13 | (ratio_Q14 << 32)
	 */
	private static final long silk_stereo_find_predictor(/*final int[] ratio_Q14, */final short x[], final short y[],
			final int mid_res_amp_Q0[], final int moffset,// java
			final int length, int smooth_coef_Q16)
	{
		/* Find  predictor */
		// final int nrgx, nrgy, scale1, scale2;
		long ret = silk_sum_sqr_shift( /*&nrgx, &scale1,*/ x, 0, length );// java
		int nrgx = (int)(ret >>> 32);// java
		final int scale1 = (int)ret;// java
		ret = silk_sum_sqr_shift( /*&nrgy, &scale2,*/ y, 0, length );
		int nrgy = (int)(ret >>> 32);// java
		final int scale2 = (int)ret;// java
		int scale = scale1 > scale2 ? scale1 : scale2;
		scale = scale + ( scale & 1 );          /* make even */
		nrgy >>= scale - scale2;
		nrgx >>= scale - scale1;
		nrgx = nrgx > 1 ? nrgx : 1;
		final int corr = silk_inner_prod_aligned_scale( x, y, scale, length );
		int pred_Q13 = silk_DIV32_varQ( corr, nrgx, 13 );
		pred_Q13 = (pred_Q13 > (1 << 14) ? (1 << 14) : (pred_Q13 < -(1 << 14) ? -(1 << 14) : pred_Q13));
		final int pred2_Q10 = (int)((pred_Q13 * (long)pred_Q13) >> 16);

		/* Faster update for signals with large prediction parameters */
		int v = pred2_Q10 >= 0 ? pred2_Q10 : -pred2_Q10;// java
		smooth_coef_Q16 = smooth_coef_Q16 >= v ? smooth_coef_Q16 : v;

		/* Smoothed mid and residual norms */
		// silk_assert( smooth_coef_Q16 < 32768 );
		scale >>= 1;
		v = mid_res_amp_Q0[moffset];// java
		mid_res_amp_Q0[ moffset ] = v + (int)(((( silk_SQRT_APPROX( nrgx ) << scale ) - v) * (long)smooth_coef_Q16) >> 16);
		/* Residual energy = nrgy - 2 * pred * corr + pred^2 * nrgx */
		nrgy -= ( (int)((corr * (long)pred_Q13) >> 16) ) << (3 + 1);
		nrgy += ( (int)((nrgx * (long)pred2_Q10) >> 16) ) << 6;
		v = mid_res_amp_Q0[moffset + 1];// java
		mid_res_amp_Q0[ moffset + 1 ] = v + (int)(((( silk_SQRT_APPROX( nrgy ) << scale ) - v) * (long)smooth_coef_Q16) >> 16);

		/* Ratio of smoothed residual and mid norms */
		v = mid_res_amp_Q0[ moffset ];// java
		v = silk_DIV32_varQ( mid_res_amp_Q0[ moffset + 1 ], (v > 1 ? v : 1), 14 );
		// ratio_Q14[0] = JSigProc_FIX.silk_LIMIT( v, 0, 32767 );// java changed

		return ((long)pred_Q13 & 0xffffffffL ) | ((long)(v > 32767 ? 32767 : (v < 0 ? 0 : v)) << 32);
	}
	// end stereo_find_predictor.c
    // start stereo_LR_to_MS.c
    /**
	 * Convert Left/Right stereo signal to adaptive Mid/Side representation
	 *
	 * @param state I/O  State
	 * @param x1 I/O  Left input signal, becomes mid signal
	 * @param x2 I/O  Right input signal, becomes side signal
	 * @param ix O    Quantization indices
	 * @param mid_only_flag O    Flag: only mid signal coded
	 * @param mid_side_rates_bps O    Bitrates for mid and side signals
	 * @param total_rate_bps I    Total bitrate
	 * @param prev_speech_act_Q8 I    Speech activity level in previous frame
	 * @param toMono I    Last frame before a stereo.mono transition
	 * @param fs_kHz I    Sample rate (kHz)
	 * @param frame_length I    Number of samples
	 */
	final void silk_stereo_LR_to_MS(
			final short x1[], final int xoffset1,// java
			final short x2[], int xoffset2,// java
			final byte ix[/* 2 */][/* 3 */],
			final byte[] mid_only_flag, final int foffset,// java
			final int mid_side_rates_bps[],
			int total_rate_bps, final int prev_speech_act_Q8, final boolean toMono, final int fs_kHz, final int frame_length
		)
	{
		final int mid = xoffset1 - 2;// &x1[ -2 ];// x1[ mid ]
		// SAVE_STACK;

		final short[] side = new short[frame_length + 2];
		/* Convert to basic mid/side signals */
		int sum, diff;
		for( int n = 0, ne = frame_length + 2, mn = mid; n < ne; n++, mn++ ) {
			int v = n - 2;// java
			diff = (int)x1[ xoffset1 + v ];// java
			v = (int)x2[ xoffset2 + v ];// java
			sum  = v + diff;
			diff -= v;
			x1[ mn ] = (short)JSigProc_FIX.silk_RSHIFT_ROUND( sum, 1 );
			v = JSigProc_FIX.silk_RSHIFT_ROUND( diff, 1 );// java
			side[ n ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}

		/* Buffering */
		x1[ mid ] = this.sMid[0]; x1[ mid + 1 ] = this.sMid[1];
		side[0] = this.sSide[0]; side[1] = this.sSide[1];
		this.sMid[0] = x1[ mid + frame_length ]; this.sMid[1] = x1[ mid + frame_length + 1 ];
		this.sSide[0] = side[ frame_length ]; this.sSide[1] = side[ frame_length + 1 ];

		/* LP and HP filter mid signal */
		final short[] LP_mid = new short[frame_length];
		final short[] HP_mid = new short[frame_length];
		for( int n = 0, mn = mid; n < frame_length; n++, mn++ ) {
			final int v = (int)x1[ mn + 1 ];// java
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( ( ((int)x1[ mn ] + (int)x1[ mn + 2 ]) + (v << 1) ), 2 );
			LP_mid[ n ] = (short)sum;
			HP_mid[ n ] = (short)(v - sum);
		}

		/* LP and HP filter side signal */
		final short[] LP_side = new short[frame_length];
		final short[] HP_side = new short[frame_length];
		for( int n = 0; n < frame_length; n++ ) {
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( ( ((int)side[ n ] + (int)side[ n + 2 ]) + (side[ n + 1 ] << 1) ), 2 );
			LP_side[ n ] = (short)sum;
			HP_side[ n ] = (short)((int)side[ n + 1 ] - sum);
		}

		/* Find energies and predictors */
		final boolean is10msFrame = frame_length == 10 * fs_kHz;
		// int smooth_coef_Q16 = is10msFrame ?
		//			SILK_FIX_CONST( Jdefine.STEREO_RATIO_SMOOTH_COEF / 2, 16 ) :
		//			SILK_FIX_CONST( Jdefine.STEREO_RATIO_SMOOTH_COEF,     16 );
		int smooth_coef_Q16 = is10msFrame ?
					((int)( (Jdefine.STEREO_RATIO_SMOOTH_COEF / 2) * (1 << 16) + .5 )) :
					((int)( Jdefine.STEREO_RATIO_SMOOTH_COEF * (1 << 16) + .5 ));
		smooth_coef_Q16 = (int)(((long)(prev_speech_act_Q8 * prev_speech_act_Q8) * (long)smooth_coef_Q16) >> 16);

		final int LP_ratio_Q14, HP_ratio_Q14;
		final int pred_Q13[] = new int[ 2 ];
		long rv = silk_stereo_find_predictor( /*&LP_ratio_Q14, */LP_mid, LP_side, this.mid_side_amp_Q0, 0, frame_length, smooth_coef_Q16 );
		pred_Q13[ 0 ] = (int)rv;// java
		LP_ratio_Q14 = (int)(rv >> 32);// java
		rv = silk_stereo_find_predictor( /*&HP_ratio_Q14, */HP_mid, HP_side, this.mid_side_amp_Q0, 2, frame_length, smooth_coef_Q16 );
		pred_Q13[ 1 ] = (int)rv;// java
		HP_ratio_Q14 = (int)(rv >> 32);// java
		/* Ratio of the norms of residual and mid signals */
		int frac_Q16 = ( HP_ratio_Q14 + LP_ratio_Q14 * 3 );
		// frac_Q16 = frac_Q16 < SILK_FIX_CONST( 1, 16 ) ? frac_Q16 : SILK_FIX_CONST( 1, 16 );
		frac_Q16 = frac_Q16 <= (1 << 16) ? frac_Q16 : (1 << 16);

		/* Determine bitrate distribution between mid and side, and possibly reduce stereo width */
		total_rate_bps -= is10msFrame ? 1200 : 600;      /* Subtract approximate bitrate for coding stereo parameters */
		if( total_rate_bps < 1 ) {
			total_rate_bps = 1;
		}
		final int min_mid_rate_bps = ( 2000 + fs_kHz * 600 );
		// silk_assert( min_mid_rate_bps < 32767 );
		/* Default bitrate distribution: 8 parts for Mid and (5+3*frac) parts for Side. so: mid_rate = ( 8 / ( 13 + 3 * frac ) ) * total_ rate */
		final int frac_3_Q16 = 3 * frac_Q16;
		// mid_side_rates_bps[ 0 ] = silk_DIV32_varQ( total_rate_bps, SILK_FIX_CONST( 8 + 5, 16 ) + frac_3_Q16, 16 + 3 );
		mid_side_rates_bps[ 0 ] = silk_DIV32_varQ( total_rate_bps, ( (8 + 5) << 16 ) + frac_3_Q16, 16 + 3 );
		/* If Mid bitrate below minimum, reduce stereo width */
		int width_Q14;
		if( mid_side_rates_bps[ 0 ] < min_mid_rate_bps ) {
			mid_side_rates_bps[ 0 ] = min_mid_rate_bps;
			mid_side_rates_bps[ 1 ] = total_rate_bps - mid_side_rates_bps[ 0 ];
			/* width = 4 * ( 2 * side_rate - min_rate ) / ( ( 1 + 3 * frac ) * min_rate ) */
			// width_Q14 = silk_DIV32_varQ( ( mid_side_rates_bps[ 1 ] << 1 ) - min_mid_rate_bps,
			// 				Jmacros.silk_SMULWB( SILK_FIX_CONST( 1, 16 ) + frac_3_Q16, min_mid_rate_bps ), 14 + 2 );
			width_Q14 = silk_DIV32_varQ( ( mid_side_rates_bps[ 1 ] << 1 ) - min_mid_rate_bps,
							( (int)(((long)(( 1 << 16 ) + frac_3_Q16) * (long)min_mid_rate_bps) >> 16) ), 14 + 2 );
			// width_Q14 = JSigProc_FIX.silk_LIMIT( width_Q14, 0, SILK_FIX_CONST( 1, 14 ) );
			width_Q14 = (width_Q14 > ( 1 << 14 ) ? ( 1 << 14 ) : (width_Q14 < 0 ? 0 : width_Q14));
		} else {
			mid_side_rates_bps[ 1 ] = total_rate_bps - mid_side_rates_bps[ 0 ];
			// width_Q14 = JSigProc_FIX.SILK_FIX_CONST( 1, 14 );
			width_Q14 = ( 1 << 14 );
		}

		/* Smoother */
		this.smth_width_Q14 += (int)(((width_Q14 - this.smth_width_Q14) * (long)smooth_coef_Q16) >> 16);

		/* At very low bitrates or for inputs that are nearly amplitude panned, switch to panned-mono coding */
		mid_only_flag[foffset] = 0;
		if( toMono ) {
			/* Last frame before stereo.mono transition; collapse stereo width */
			width_Q14 = 0;
			pred_Q13[ 0 ] = 0;
			pred_Q13[ 1 ] = 0;
			silk_stereo_quant_pred( pred_Q13, ix );
		} else if( this.width_prev_Q14 == 0 &&
				// ( 8 * total_rate_bps < 13 * min_mid_rate_bps || Jmacros.silk_SMULWB( frac_Q16, this.smth_width_Q14 ) < SILK_FIX_CONST( 0.05, 14 ) ) )
				( (total_rate_bps << 3) < 13 * min_mid_rate_bps || ( (int)((frac_Q16 * (long)this.smth_width_Q14) >> 16) ) < ((int)(0.05 * (1 << 14) + .5)) ) )
		{
			/* Code as panned-mono; previous frame already had zero width */
			/* Scale down and quantize predictors */
			pred_Q13[ 0 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 0 ] ) >> 14 );
			pred_Q13[ 1 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 1 ] ) >> 14 );
			silk_stereo_quant_pred( pred_Q13, ix );
			/* Collapse stereo width */
			width_Q14 = 0;
			pred_Q13[ 0 ] = 0;
			pred_Q13[ 1 ] = 0;
			mid_side_rates_bps[ 0 ] = total_rate_bps;
			mid_side_rates_bps[ 1 ] = 0;
			mid_only_flag[foffset] = 1;
		} else if( this.width_prev_Q14 != 0 &&
				// ( 8 * total_rate_bps < 11 * min_mid_rate_bps || Jmacros.silk_SMULWB( frac_Q16, this.smth_width_Q14 ) < SILK_FIX_CONST( 0.02, 14 ) ) )
				( (total_rate_bps << 3) < 11 * min_mid_rate_bps || ( (int)((frac_Q16 * (long)this.smth_width_Q14) >> 16) ) < ((int)(0.02 * (1 << 14) + .5)) ) )
		{
			/* Transition to zero-width stereo */
			/* Scale down and quantize predictors */
			pred_Q13[ 0 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 0 ] ) >> 14 );
			pred_Q13[ 1 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 1 ] ) >> 14 );
			silk_stereo_quant_pred( pred_Q13, ix );
			/* Collapse stereo width */
			width_Q14 = 0;
			pred_Q13[ 0 ] = 0;
			pred_Q13[ 1 ] = 0;
		// } else if( this.smth_width_Q14 > SILK_FIX_CONST( 0.95, 14 ) ) {
		} else if( this.smth_width_Q14 > ((int)(0.95 * (1 << 14) + .5)) ) {
			/* Full-width stereo coding */
			silk_stereo_quant_pred( pred_Q13, ix );
			// width_Q14 = JSigProc_FIX.SILK_FIX_CONST( 1, 14 );
			width_Q14 = ( 1 << 14 );
		} else {
			/* Reduced-width stereo coding; scale down and quantize predictors */
			pred_Q13[ 0 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 0 ] ) >> 14 );
			pred_Q13[ 1 ] = ( ( (int)this.smth_width_Q14 * pred_Q13[ 1 ] ) >> 14 );
			silk_stereo_quant_pred( pred_Q13, ix );
			width_Q14 = this.smth_width_Q14;
		}

		/* Make sure to keep on encoding until the tapered output has been transmitted */
		if( mid_only_flag[foffset] == 1 ) {
			this.silent_side_len += frame_length - Jdefine.STEREO_INTERP_LEN_MS * fs_kHz;
			if( this.silent_side_len < Jdefine.LA_SHAPE_MS * fs_kHz ) {
				mid_only_flag[foffset] = 0;
			} else {
				/* Limit to avoid wrapping around */
				this.silent_side_len = 10000;
			}
		} else {
			this.silent_side_len = 0;
		}

		if( mid_only_flag[foffset] == 0 && mid_side_rates_bps[ 1 ] < 1 ) {
			mid_side_rates_bps[ 1 ] = 1;
			total_rate_bps--;// java total_rate_bps - mid_side_rates_bps[ 1 ]
			mid_side_rates_bps[ 0 ] = 1 > total_rate_bps ? 1 : total_rate_bps;
		}

		/* Interpolate predictors and subtract prediction from side channel */
		int pred0_Q13  = -this.pred_prev_Q13[ 0 ];
		int pred1_Q13  = -this.pred_prev_Q13[ 1 ];
		int w_Q24      =  this.width_prev_Q14 << 10;
		final int denom_Q16  = (1 << 16) / (Jdefine.STEREO_INTERP_LEN_MS * fs_kHz);
		final int delta0_Q13 = -JSigProc_FIX.silk_RSHIFT_ROUND( ( (pred_Q13[ 0 ] - (int)this.pred_prev_Q13[ 0 ]) * denom_Q16 ), 16 );
		final int delta1_Q13 = -JSigProc_FIX.silk_RSHIFT_ROUND( ( (pred_Q13[ 1 ] - (int)this.pred_prev_Q13[ 1 ]) * denom_Q16 ), 16 );
		final int deltaw_Q24 =  (((int)(((long)(width_Q14 - this.width_prev_Q14) * (long)denom_Q16) >> 16)) << 10);
		xoffset2--;// java
		for( int n = 0, ne = Jdefine.STEREO_INTERP_LEN_MS * fs_kHz, mn = mid, xn2 = xoffset2; n < ne; n++, mn++ ) {
			pred0_Q13 += delta0_Q13;
			pred1_Q13 += delta1_Q13;
			w_Q24   += deltaw_Q24;
			final int v = (int)x1[ mn + 1 ];// java
			sum = ( ( ((int)x1[ mn ] + (int)x1[ mn + 2 ]) + (v << 1) ) << 9 );    /* Q11 */
			sum = ( (int)((w_Q24 * (long)side[ n + 1 ]) >> 16) ) + (int)((sum * (long)pred0_Q13) >> 16);               /* Q8  */
			sum += (int)((( v << 11 ) * (long)pred1_Q13) >> 16);       /* Q8  */
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( sum, 8 );
			x2[ xn2++ ] = (short)(sum > Short.MAX_VALUE ? Short.MAX_VALUE : (sum < Short.MIN_VALUE ? Short.MIN_VALUE : sum));
		}

		pred0_Q13 = -pred_Q13[ 0 ];
		pred1_Q13 = -pred_Q13[ 1 ];
		w_Q24     =  ( width_Q14 << 10 );
		for( int n = Jdefine.STEREO_INTERP_LEN_MS * fs_kHz, mn = mid + n, xn2 = xoffset2 + n; n < frame_length; n++, mn++ ) {
			final int v = (int)x1[ mn + 1 ];// java
			sum = ( ( ((int)x1[ mn ] + (int)x1[ mn + 2 ]) + (v << 1) ) << 9 );    /* Q11 */
			sum = ( (int)((w_Q24 * (long)side[ n + 1 ]) >> 16) ) + (int)((sum * (long)pred0_Q13) >> 16);               /* Q8  */
			sum += (int)((( v << 11 ) * (long)pred1_Q13) >> 16);       /* Q8  */
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( sum, 8 );
			x2[ xn2++ ] = (short)(sum > Short.MAX_VALUE ? Short.MAX_VALUE : (sum < Short.MIN_VALUE ? Short.MIN_VALUE : sum));
		}
		this.pred_prev_Q13[ 0 ] = (short)pred_Q13[ 0 ];
		this.pred_prev_Q13[ 1 ] = (short)pred_Q13[ 1 ];
		this.width_prev_Q14     = (short)width_Q14;
		// RESTORE_STACK;
	}
    // end stereo_LR_to_MS.c
}
