package silk;

/** java: collection of methods both for encoder and coder */
abstract class Jcodec_API {
	// start Inlines.h
	/** count leading zeros of opus_int64 */
	/*static OPUS_INLINE opus_int32 silk_CLZ64( final opus_int64 in )
	{
		opus_int32 in_upper;

		in_upper = (opus_int32)silk_RSHIFT64(in, 32);
		if (in_upper == 0) {
			// Search in the lower 32 bits
			return 32 + silk_CLZ32( (opus_int32) in );
		} else {
			// Search in the upper 32 bits
			return silk_CLZ32( in_upper );
		}
	}*/

	/**
	 * get number of leading zeros and fractional part (the bits right after the leading one
	 *
	 *
	 * @param in I  input
	 * @param lz O  number of leading zeros
	 * @param frac_Q7 O  the 7 bits right after the leading one
	 */
	/*private static final void silk_CLZ_FRAC(final int in, final int[] lz, final int[] frac_Q7)
	{
		final int lzeros = Jmacros.silk_CLZ32( in );

		lz[0] = lzeros;
		frac_Q7[0] = JSigProc_FIX.silk_ROR32( in, 24 - lzeros ) & 0x7f;
	}*/

	/** Approximation of square root
	 * Accuracy: < +/- 10%  for output values > 15
	 *           < +/- 2.5% for output values > 120
	 * @param x input value
	 * @return Approximation of square root
	 */
	static final int silk_SQRT_APPROX( final int x )
	{
		if( x <= 0 ) {
			return 0;
		}

		// final int lz, frac_Q7;
		// silk_CLZ_FRAC( x, &lz, &frac_Q7 );// java extracted in place
		final int lz = Jmacros.silk_CLZ32( x );
		final int frac_Q7 = Jmacros.silk_ROR32( x, 24 - lz ) & 0x7f;

		int y = (lz & 1) != 0 ? 32768 : 46214;        /* 46214 = sqrt(2) * 32768 */

		/* get scaling right */
		y >>= (lz >> 1);

		/* increment using fractional part of input */
		y += (int)((y * (long)( 213 * frac_Q7 )) >> 16);

		return y;
	}

	/**
	 * Divide two int32 values and return result as int32 in a given Q-domain
	 *
	 * @param a32 I    numerator (Q0)
	 * @param b32 I    denominator (Q0)
	 * @param Qres I    Q-domain of result (>= 0)
	 * @return O    returns a good approximation of "(a32 << Qres) / b32"
	 */
	static final int silk_DIV32_varQ(final int a32, final int b32, final int Qres)
	{
		//silk_assert( b32 != 0 );
		//silk_assert( Qres >= 0 );

		/* Compute number of bits head room and normalize inputs */
		final int a_headrm = Jmacros.silk_CLZ32( (a32 >= 0 ? a32 : -a32) ) - 1;
		int a32_nrm = (a32 << a_headrm);                       /* Q: a_headrm                  */
		final int b_headrm = Jmacros.silk_CLZ32( (b32 >= 0 ? b32 : -b32) ) - 1;
		final int b32_nrm = (b32 << b_headrm);                              /* Q: b_headrm                  */

		/* Inverse of b32, with 14 bits of precision */
		final int b32_inv = ((Integer.MAX_VALUE >> 2) / (b32_nrm >> 16));   /* Q: 29 + 16 - b_headrm        */

		/* First approximation */
		int result = (int)((a32_nrm * (long)b32_inv) >> 16);               /* Q: 29 + a_headrm - b_headrm  */

		/* Compute residual by subtracting product of denominator and first approximation */
		/* It's OK to overflow because the final value of a32_nrm should always be small */
		a32_nrm -= (((long)b32_nrm * result) >> 32) << 3;  /* Q: a_headrm   */

		/* Refinement */
		result += (int)((a32_nrm * (long)b32_inv) >> 16);           /* Q: 29 + a_headrm - b_headrm  */

		/* Convert to Qres domain */
		int lshift = 29 + a_headrm - b_headrm - Qres;
		if( lshift < 0 ) {
			lshift = -lshift;
			return ((result > (Integer.MAX_VALUE >> lshift) ?
					(Integer.MAX_VALUE >> lshift) : (result < (Integer.MIN_VALUE >> lshift) ? (Integer.MIN_VALUE >> lshift) : result)) << lshift);
		} else {
			if( lshift < 32) {
				return (result >> lshift);
			} else {
				/* Avoid undefined result */
				return 0;
			}
		}
	}

	/**
	 * Invert int32 value and return result as int32 in a given Q-domain
	 *
	 * @param b32 I    denominator (Q0)
	 * @param Qres I    Q-domain of result (> 0)
	 * @return O    returns a good approximation of "(1 << Qres) / b32"
	 */
	static final int silk_INVERSE32_varQ(final int b32, final int Qres )
	{
		//silk_assert( b32 != 0 );
		//silk_assert( Qres > 0 );

		/* Compute number of bits head room and normalize input */
		final int b_headrm = Jmacros.silk_CLZ32( (b32 >= 0 ? b32 : -b32) ) - 1;
		final int b32_nrm = (b32 << b_headrm);                                       /* Q: b_headrm                */

		/* Inverse of b32, with 14 bits of precision */
		final int b32_inv = (Integer.MAX_VALUE >> 2) / (b32_nrm >> 16);   /* Q: 29 + 16 - b_headrm    */

		/* First approximation */
		int result = (b32_inv << 16);                                          /* Q: 61 - b_headrm            */

		/* Compute residual by subtracting product of denominator and first approximation from one */
		final int err_Q32 = ((1 << 29) - ((int)((b32_nrm * (long)b32_inv) >> 16))) << 3;        /* Q32                        */

		/* Refinement */
		result = (int)(result + (((long)err_Q32 * b32_inv) >> 16));                             /* Q: 61 - b_headrm            */

		/* Convert to Qres domain */
		int lshift = 61 - b_headrm - Qres;
		if( lshift <= 0 ) {
			lshift = -lshift;
			return ((result > (Integer.MAX_VALUE >> lshift) ? (Integer.MAX_VALUE >> lshift) : (result < (Integer.MIN_VALUE >> lshift) ? (Integer.MIN_VALUE >> lshift) : result)) << lshift);
		} else {
			if( lshift < 32) {
				return (result >> lshift);
			}else{
				/* Avoid undefined result */
				return 0;
			}
		}
	}
	// end Inlines.h

	// start gain_quant.c
	private static final int OFFSET        = ((Jdefine.MIN_QGAIN_DB * 128) / 6 + 16 * 128);
	private static final int SCALE_Q16     = ((65536 * (Jdefine.N_LEVELS_QGAIN - 1)) / (((Jdefine.MAX_QGAIN_DB - Jdefine.MIN_QGAIN_DB) * 128) / 6));
	private static final int INV_SCALE_Q16 = ((65536 * (((Jdefine.MAX_QGAIN_DB - Jdefine.MIN_QGAIN_DB) * 128) / 6)) / (Jdefine.N_LEVELS_QGAIN - 1));

	/**
	 * Gain scalar quantization with hysteresis, uniform on log scale
	 *
	 * java changed: new value of the prev_ind is returned
	 *
	 * @param ind O    gain indices
	 * @param gain_Q16 I/O  gains (quantized out)
	 * @param prev_ind I/O  last index in previous frame
	 * @param conditional I    first gain is delta coded if 1
	 * @param nb_subfr I    number of subframes
	 * @return prev_ind new value of the last index in previous frame
	 */
	static final byte silk_gains_quant(final byte ind[/* MAX_NB_SUBFR */], final int gain_Q16[/* MAX_NB_SUBFR */],
			int prev_ind, final boolean conditional, final int nb_subfr)
	{// java: prev_int changed to int to avoid casting
		for( int k = 0; k < nb_subfr; k++ ) {
			int v = ind[ k ];// kava
			/* Convert to log scale, scale, floor() */
			v = (int)((SCALE_Q16 * (long)(Jmacros.silk_lin2log( gain_Q16[ k ] ) - OFFSET)) >> 16);
			/* Round towards previous quantized gain (hysteresis) */
			if( v < prev_ind ) {
				v++;
			}
			v = (v > (Jdefine.N_LEVELS_QGAIN - 1) ? (Jdefine.N_LEVELS_QGAIN - 1) : (v < 0 ? 0 : v));

			/* Compute delta indices and limit */
			if( k == 0 && ! conditional ) {
				/* Full index */
				v = JSigProc_FIX.silk_LIMIT( v, (int)prev_ind + Jdefine.MIN_DELTA_GAIN_QUANT, Jdefine.N_LEVELS_QGAIN - 1 );
				prev_ind = v;
			} else {
				/* Delta index */
				v -= prev_ind;

				/* Double the quantization step size for large gain increases, so that the max gain level can be reached */
				final int double_step_size_threshold = 2 * Jdefine.MAX_DELTA_GAIN_QUANT - Jdefine.N_LEVELS_QGAIN + (int)prev_ind;
				if( v > double_step_size_threshold ) {
					v = (double_step_size_threshold + ((v - double_step_size_threshold + 1) >> 1));
				}

				v = (v > Jdefine.MAX_DELTA_GAIN_QUANT ? Jdefine.MAX_DELTA_GAIN_QUANT : (v < Jdefine.MIN_DELTA_GAIN_QUANT ? Jdefine.MIN_DELTA_GAIN_QUANT : v));

				/* Accumulate deltas */
				if( v > double_step_size_threshold ) {
					prev_ind += (v << 1) - double_step_size_threshold;
					prev_ind = ( prev_ind < (Jdefine.N_LEVELS_QGAIN - 1) ? prev_ind : (Jdefine.N_LEVELS_QGAIN - 1) );
				} else {
					prev_ind += v;
				}

				/* Shift to make non-negative */
				v -= Jdefine.MIN_DELTA_GAIN_QUANT;
			}
			ind[ k ] = (byte)v;
			/* Scale and convert to linear scale */
			v = ( (int)((INV_SCALE_Q16 * (long)prev_ind) >> 16) ) + OFFSET;// java
			gain_Q16[ k ] = Jmacros.silk_log2lin( v <= 3967 ? v : 3967 ); /* 3967 = 31 in Q7 */
		}
		return (byte)prev_ind;
	}

	/**
	 * Gains scalar dequantization, uniform on log scale
	 *
	 * java changed: new value of the prev_ind is returned
	 *
	 * @param gain_Q16 O    quantized gains
	 * @param ind I    gain indices
	 * @param prev_ind I/O  last index in previous frame
	 * @param conditional I    first gain is delta coded if 1
	 * @param nb_subfr I    number of subframes
	 * @return prev_ind new value of the last index in previous frame
	 */
	static final byte silk_gains_dequant(final int gain_Q16[/* MAX_NB_SUBFR */], final byte ind[/* MAX_NB_SUBFR */],
			int prev_ind, final boolean conditional, final int nb_subfr)
	{
		for( int k = 0; k < nb_subfr; k++ ) {
			if( k == 0 && ! conditional ) {
				/* Gain index is not allowed to go down more than 16 steps (~21.8 dB) */
				final int v1 = (int)ind[ k ];// java
				final int v2 = (int)prev_ind - 16;// java
				prev_ind = (v1 >= v2 ? v1 : v2);
			} else {
				/* Delta index */
				final int ind_tmp = ind[ k ] + Jdefine.MIN_DELTA_GAIN_QUANT;

				/* Accumulate deltas */
				final int double_step_size_threshold = 2 * Jdefine.MAX_DELTA_GAIN_QUANT - Jdefine.N_LEVELS_QGAIN + prev_ind;
				if( ind_tmp > double_step_size_threshold ) {
					prev_ind += (ind_tmp << 1) - double_step_size_threshold;
				} else {
					prev_ind += ind_tmp;
				}
			}
			prev_ind = (byte)(prev_ind > (Jdefine.N_LEVELS_QGAIN - 1) ? (Jdefine.N_LEVELS_QGAIN - 1) : (prev_ind < 0 ? 0 : prev_ind));

			/* Scale and convert to linear scale */
			final int v = ( (int)((INV_SCALE_Q16 * (long)prev_ind) >> 16) ) + OFFSET;// java
			gain_Q16[ k ] = Jmacros.silk_log2lin( v <= 3967 ? v : 3967 ); /* 3967 = 31 in Q7 */
		}
		return (byte)prev_ind;
	}
	// end gain_quant.c

	// start bwexpander_32.c
	/**
	 * Chirp (bandwidth expand) LP AR filter
	 *
	 * @param ar I/O  AR filter to be expanded (without leading 1)
	 * @param d I    Length of ar
	 * @param chirp_Q16 I    Chirp factor in Q16
	 */
	static final void silk_bwexpander_32(final int[] ar, int d, int chirp_Q16)
	{
		final int chirp_minus_one_Q16 = chirp_Q16 - 65536;

		d--;// java
	    for( int i = 0; i < d; i++ ) {
	        ar[ i ]    = (int)(((long)chirp_Q16 * ar[ i ]) >> 16);
	        chirp_Q16 += JSigProc_FIX.silk_RSHIFT_ROUND( chirp_Q16 * chirp_minus_one_Q16, 16 );
	    }
	    ar[ d ] = (int)(((long)chirp_Q16 * ar[ d ]) >> 16);
	}
	// end bwexpander_32.c

	// start LPC_inv_pred_gain.c
	private static final int LPC_QA = 24;// java renamed
	// private static final int A_LIMIT = SILK_FIX_CONST( 0.99975, LPC_QA );
	private static final int A_LIMIT = (int)( 0.99975 * (1 << LPC_QA) + .5 );

	private static final int MUL32_FRAC_Q(final int a32, final int b32, final int Q) {
		final long a = ((long)a32 * b32);
		return (int)(Q == 1 ? (a >> 1) + (a & 1) : ((a >> (Q - 1)) + 1) >> 1);
	}

	// LPC_inverse_pred_gain_QA
	/**
	 * Compute inverse of LPC prediction gain, and
	 * test if LPC coefficients are stable (all poles within unit circle)
	 *
	 * @param A_QA I   Prediction coefficients
	 * @param order I   Prediction order
	 * @return O   Returns inverse prediction gain in energy domain, Q30
	 */
	private static final int LPC_inverse_pred_gain_QA(final int A_QA[/* SILK_MAX_ORDER_LPC */], final int order)
	{
		int k;
		int invGain_Q30 = 1 << 30;
		for( k = order - 1; k > 0; k-- ) {
			/* Check for stability */
			if( ( A_QA[ k ] > A_LIMIT ) || ( A_QA[ k ] < -A_LIMIT ) ) {
				return 0;
			}

			/* Set RC equal to negated AR coef */
			final int rc_Q31 = -(A_QA[ k ] << (31 - LPC_QA));

			/* rc_mult1_Q30 range: [ 1 : 2^30 ] */
			final int rc_mult1_Q30 = (1 << 30) - (int)( ((long)rc_Q31 * rc_Q31) >> 32 );
			// silk_assert( rc_mult1_Q30 > ( 1 << 15 ) );                   /* reduce A_LIMIT if fails */
			// silk_assert( rc_mult1_Q30 <= ( 1 << 30 ) );

			/* Update inverse gain */
			/* invGain_Q30 range: [ 0 : 2^30 ] */
			invGain_Q30 = ( (int)(((long)invGain_Q30 * rc_mult1_Q30) >> 32) ) << 2;
			// silk_assert( invGain_Q30 >= 0           );
			// silk_assert( invGain_Q30 <= ( 1 << 30 ) );
			if( invGain_Q30 < (int)((1.0f / Jdefine.MAX_PREDICTION_POWER_GAIN) * (1 << 30) + 0.5) ) {
				return 0;
			}

			/* rc_mult2 range: [ 2^30 : silk_int32_MAX ] */
			final int mult2Q = 32 - Jmacros.silk_CLZ32( (rc_mult1_Q30 >= 0 ? rc_mult1_Q30 : -rc_mult1_Q30) );
			final int rc_mult2 = silk_INVERSE32_varQ( rc_mult1_Q30, mult2Q + 30 );

			/* Update AR coefficient */
			for( int n = 0, ne = (k + 1) >> 1, kn1 = k - 1; n < ne; n++, kn1-- ) {
				final int tmp1 = A_QA[ n ];
				final int tmp2 = A_QA[ kn1 ];
				// java
				int v = MUL32_FRAC_Q( tmp2, rc_Q31, 31 );
				v = (((tmp1 - v) & 0x80000000) == 0 ?
						((tmp1 & (v ^ 0x80000000) & 0x80000000) != 0 ? Integer.MIN_VALUE : tmp1 - v) :
							(((tmp1 ^ 0x80000000) & v & 0x80000000) != 0 ? Integer.MAX_VALUE : tmp1 - v) );
				long tmp64 = (long)v * rc_mult2;
				tmp64 = (mult2Q == 1 ? (tmp64 >> 1) + (tmp64 & 1) : ((tmp64 >> (mult2Q - 1)) + 1) >> 1);
				if( tmp64 > (long)Integer.MAX_VALUE || tmp64 < (long)Integer.MIN_VALUE ) {
					return 0;
				}
				A_QA[ n ] = ( int )tmp64;
				// java
				v = MUL32_FRAC_Q( tmp1, rc_Q31, 31 );
				v = (((tmp2 - v) & 0x80000000) == 0 ?
						((tmp2 & (v ^ 0x80000000) & 0x80000000) != 0 ? Integer.MIN_VALUE : tmp2 - v) :
							(((tmp2 ^ 0x80000000) & v & 0x80000000) != 0 ? Integer.MAX_VALUE : tmp2 - v) );
				tmp64 = (long)v * rc_mult2;
				tmp64 = (mult2Q == 1 ? (tmp64 >> 1) + (tmp64 & 1) : ((tmp64 >> (mult2Q - 1)) + 1) >> 1);
				if( tmp64 > (long)Integer.MAX_VALUE || tmp64 < (long)Integer.MIN_VALUE ) {
					return 0;
				}
				A_QA[ kn1 ] = ( int )tmp64;
			}
		}

		/* Check for stability */
		if( ( A_QA[ k ] > A_LIMIT ) || ( A_QA[ k ] < -A_LIMIT ) ) {
			return 0;
		}

		/* Set RC equal to negated AR coef */
		final int rc_Q31 = -(A_QA[ 0 ] << (31 - LPC_QA));

		/* Range: [ 1 : 2^30 ] */
		final int rc_mult1_Q30 = (1 << 30) - (int)(((long)rc_Q31 * rc_Q31) >> 32);

		/* Update inverse gain */
		/* Range: [ 0 : 2^30 ] */
		invGain_Q30 = ( (int)(((long)invGain_Q30 * rc_mult1_Q30) >> 32) ) << 2;
		// silk_assert( invGain_Q30 >= 0     );
		// silk_assert( invGain_Q30 <= ( 1 << 30 ) );
		if( invGain_Q30 < (int)((1.0f / Jdefine.MAX_PREDICTION_POWER_GAIN) * (1 << 30) + 0.5) ) {
			return 0;
		}

		return invGain_Q30;
	}

	// silk_LPC_inverse_pred_gain_c
	/**
	 * For input in Q12 domain
	 *
	 * @param A_Q12 I   Prediction coefficients, Q12 [order]
	 * @param order I   Prediction order
	 * @return O   Returns inverse prediction gain in energy domain, Q30
	 */
	static final int silk_LPC_inverse_pred_gain(final short[] A_Q12, final int order)
	{
		final int Atmp_QA[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];
		int DC_resp = 0;

		/* Increase Q domain of the AR coefficients */
		for( int k = 0; k < order; k++ ) {
			final int v = (int)A_Q12[ k ];// java
			DC_resp += v;
			Atmp_QA[ k ] = v << (LPC_QA - 12);
		}
		/* If the DC is unstable, we don't even need to do the full calculations */
		if( DC_resp >= 4096 ) {
			return 0;
		}
		return LPC_inverse_pred_gain_QA( Atmp_QA, order );
	}

// #ifdef FIXED_POINT

	/*
	 * For input in Q24 domain
	 *
	 * @param A_Q24 I    Prediction coefficients [order]
	 * @param order I    Prediction order
	 * @return O    Returns inverse prediction gain in energy domain, Q30
	 */
	/* private static final int silk_LPC_inverse_pred_gain_Q24(final int[] A_Q24, final int order)
	{
		int   k;
		final int Atmp_QA[][] = new int[ 2 ][ JSigProc_FIX.SILK_MAX_ORDER_LPC ];
		int[] Anew_QA;

		Anew_QA = Atmp_QA[ order & 1 ];

		// Increase Q domain of the AR coefficients
		for( k = 0; k < order; k++ ) {
			Anew_QA[ k ] = silk_RSHIFT32( A_Q24[ k ], 24 - QA );
		}

		return LPC_inverse_pred_gain_QA( Atmp_QA, order );
	} */
// #endif
	// end LPC_inv_pred_gain.c

	// start NLSF2A.c

	/*
	 * conversion between prediction filter coefficients and LSFs
	 * order should be even
	 * a piecewise linear approximation maps LSF <-> cos(LSF)
	 * therefore the result is not accurate LSFs, but the two
	 * functions are accurate inverses of each other
	 */
	private static final int QA = 16;

	/**
	 * helper function for NLSF2A(..)
	 *
	 * @param out O    intermediate polynomial, QA [dd+1]
	 * @param cLSF I    vector of interleaved 2*cos(LSFs), QA [d]
	 * @param coffset I java an offset for the cLSF
	 * @param dd I    polynomial order (= 1/2 * filter order)
	 */
	private static final void silk_NLSF2A_find_poly(final int[] out, final int[] cLSF, int coffset, final int dd)
	{
		out[0] = ( 1 << QA );
		out[1] = -cLSF[coffset];
		int k;
		for( k = 1, coffset += 2; k < dd; k++, coffset += 2 ) {
			final int ftmp = cLSF[ coffset ];            /* QA*/
			long a = ((long)ftmp * out[k]);
			out[k + 1] = ( out[k - 1] << 1 ) - (int)(((a >> (QA - 1)) + 1) >> 1);
			for( int n = k; n > 1; n-- ) {
				a = ((long)ftmp * out[n - 1]);
				out[n] += out[n - 2] - (int)(((a >> (QA - 1)) + 1) >> 1);
			}
			out[1] -= ftmp;
		}
	}

	/**
	 * Convert int32 coefficients to int16 coefs and make sure there's no wrap-around
	 *
	 * @param a_QOUT O    Output signal
	 * @param a_QIN I/O  Input signal
	 * @param QOUT I    Input Q domain
	 * @param QIN I    Input Q domain
	 * @param d I    Filter order
	 */
	private static final void silk_LPC_fit(final short[] a_QOUT, final int[] a_QIN, final int QOUT, int QIN, final int d)
	{
		QIN -= QOUT;// java
		int i, idx = 0;

		/* Limit the maximum absolute value of the prediction coefficients, so that they'll fit in int16 */
		for( i = 0; i < 10; i++ ) {
			/* Find maximum absolute value and its index */
			int maxabs = 0;
			for( int k = 0; k < d; k++ ) {
				int absval = a_QIN[ k ];
				if( absval < 0 ) {
					absval = -absval;// java
				}
				if( absval > maxabs ) {
					maxabs = absval;
					idx    = k;
				}
			}
			maxabs = JSigProc_FIX.silk_RSHIFT_ROUND( maxabs, QIN );

			if( maxabs > Short.MAX_VALUE ) {
				/* Reduce magnitude of prediction coefficients */
				maxabs = ( maxabs < 163838 ? maxabs : 163838 );  /* ( silk_int32_MAX >> 14 ) + silk_int16_MAX = 163838 */
				final int chirp_Q16 = (int)(0.999 * (1 << 16) + 0.5) - ( ((maxabs - Short.MAX_VALUE) << 14) / ((maxabs * (idx + 1)) >> 2) );
				Jcodec_API.silk_bwexpander_32( a_QIN, d, chirp_Q16 );
			} else {
				break;
			}
		}

		if( i == 10 ) {
			/* Reached the last iteration, clip the coefficients */
			for( int k = 0; k < d; k++ ) {
				int v = JSigProc_FIX.silk_RSHIFT_ROUND( a_QIN[ k ], QIN );
				v = (v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
				a_QOUT[ k ] = (short)v;
				a_QIN[ k ] = ( v << (QIN) );
			}
			return;// java
		}// else {
			for( int k = 0; k < d; k++ ) {
				a_QOUT[ k ] = (short)JSigProc_FIX.silk_RSHIFT_ROUND( a_QIN[ k ], QIN );
			}
		// }
	}
	/* This ordering was found to maximize quality. It improves numerical accuracy of
	silk_NLSF2A_find_poly() compared to "standard" ordering. */
	private static final int ordering16[/* 16 */] = {// java changes: uint8 to int32, to avoid casting during runtime
			0, 15, 8, 7, 4, 11, 12, 3, 2, 13, 10, 5, 6, 9, 14, 1
	};
	private static final int ordering10[/* 10 */] = {// java changes: uint8 to int32, to avoid casting during runtime
			0, 9, 6, 3, 4, 5, 8, 1, 2, 7
	};
	/**
	 * compute whitening filter coefficients from normalized line spectral frequencies
	 *
	 * @param a_Q12 O    monic whitening filter coefficients in Q12,  [ d ]
	 * @param NLSF I    normalized line spectral frequencies in Q15, [ d ]
	 * @param d I    filter order (should be even)
	 * @param arch I    Run-time architecture
	 */
	static final void silk_NLSF2A(final short[] a_Q12, final short[] NLSF, final int d)// int arch)
	{
		final int cos_LSF_QA[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];
		final int P[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC / 2 + 1 ];
		final int Q[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC / 2 + 1 ];
		final int a32_QA1[] = new int[ JSigProc_FIX.SILK_MAX_ORDER_LPC ];

		// silk_assert( LSF_COS_TAB_SZ_FIX == 128 );
		// celt_assert( d==10 || d==16 );

		/* convert LSFs to 2*cos(LSF), using piecewise linear curve from table */
		final int[] ordering = d == 16 ? ordering16 : ordering10;
		for( int k = 0; k < d; k++ ) {
			// silk_assert(NLSF[k] >= 0 );

			/* f_int on a scale 0-127 (rounded down) */
			final int f_int = ( NLSF[k] >> (15 - 7) );

			/* f_frac, range: 0..255 */
			final int f_frac = NLSF[k] - ( f_int << (15 - 7) );

			// silk_assert( f_int >= 0 );
			// silk_assert( f_int < LSF_COS_TAB_SZ_FIX );

			/* Read start and end value from table */
			final int cos_val = Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ f_int ];                /* Q12 */
			final int delta   = Jtable_LSF_cos.silk_LSFCosTab_FIX_Q12[ f_int + 1 ] - cos_val;  /* Q12, with a range of 0..200 */

			/* Linear interpolation */
			cos_LSF_QA[ordering[k]] = JSigProc_FIX.silk_RSHIFT_ROUND( ( cos_val << 8 ) + ( delta * f_frac ), 20 - QA ); /* QA */
		}

		final int dd = ( d >> 1 );

		/* generate even and odd polynomials using convolution */
		silk_NLSF2A_find_poly( P, cos_LSF_QA, 0, dd );
		silk_NLSF2A_find_poly( Q, cos_LSF_QA, 1, dd );

		/* convert even and odd polynomials to opus_int32 Q12 filter coefs */
		int i = d - 1;
		for( int k = 0; k < dd; k++, i-- ) {
			final int Ptmp = P[ k + 1 ] + P[ k ];
			final int Qtmp = Q[ k + 1 ] - Q[ k ];

			/* the Ptmp and Qtmp values at this stage need to fit in int32 */
			a32_QA1[ k ] = -Qtmp - Ptmp;        /* QA+1 */
			a32_QA1[ i ] =  Qtmp - Ptmp;        /* QA+1 */
		}

		/* Convert int32 coefficients to Q12 int16 coefs */
		silk_LPC_fit( a_Q12, a32_QA1, 12, QA + 1, d );

		for( i = 0; silk_LPC_inverse_pred_gain( a_Q12, d )/*, arch )*/ == 0 && i < Jdefine.MAX_LPC_STABILIZE_ITERATIONS; i++ ) {
			/* Prediction coefficients are (too close to) unstable; apply bandwidth expansion   */
			/* on the unscaled coefficients, convert to Q12 and measure again                   */
			silk_bwexpander_32( a32_QA1, d, 65536 - ( 2 << i ) );
			for( int k = 0; k < d; k++ ) {
				a_Q12[ k ] = (short)JSigProc_FIX.silk_RSHIFT_ROUND( a32_QA1[ k ], QA + 1 - 12 );            /* QA+1 -> Q12 */
			}
		}
	}
	// end NLSF2A.c

	// start sort.c
	/**
	 *
	 * @param a I/O   Unsorted / Sorted vector
	 * @param L I     Vector length
	 */
	private static final void silk_insertion_sort_increasing_all_values_int16(final short[] a, final int L)
	{
		/* Safety checks */
		// celt_assert( L >  0 );

		/* Sort vector elements by value, increasing order */
		for( int i = 1; i < L; i++ ) {
			final short value = a[ i ];
			int j;
			for( j = i - 1; ( j >= 0 ) && ( value < a[ j ] ); j-- ) {
				a[ j + 1 ] = a[ j ]; /* Shift value */
			}
			a[ j + 1 ] = value; /* Write value */
		}
	}
	// end sort.c

	// NLSF_stabilize.c
	/*
	 * NLSF stabilizer:
	 *
	 * - Moves NLSFs further apart if they are too close
	 * - Moves NLSFs away from borders if they are too close
	 * - High effort to achieve a modification with minimum
	 *     Euclidean distance to input vector
	 * - Output are sorted NLSF coefficients
	 */
	/* Constant Definitions */
	private static final int MAX_LOOPS = 20;

	/**
	 * NLSF stabilizer, for a single input data vector
	 *
	 * @param NLSF_Q15 I/O   Unstable/stabilized normalized LSF vector in Q15 [L]
	 * @param NDeltaMin_Q15 I     Min distance vector, NDeltaMin_Q15[L] must be >= 1 [L+1]
	 * @param L I     Number of NLSF parameters in the input vector
	 */
	static final void silk_NLSF_stabilize(final short[] NLSF_Q15, final short[] NDeltaMin_Q15, final int L)
	{
		/* This is necessary to ensure an output within range of a opus_int16 */
		// silk_assert( NDeltaMin_Q15[L] >= 1 );

		int loops;
		for( loops = 0; loops < MAX_LOOPS; loops++ ) {
			/**************************/
			/* Find smallest distance */
			/**************************/
			/* First element */
			int min_diff_Q15 = NLSF_Q15[0] - NDeltaMin_Q15[0];
			int I = 0;
			/* Middle elements */
			for( int i = 1; i <= L-1; i++ ) {
				final int diff_Q15 = NLSF_Q15[i] - ( NLSF_Q15[i-1] + NDeltaMin_Q15[i] );
				if( diff_Q15 < min_diff_Q15 ) {
					min_diff_Q15 = diff_Q15;
					I = i;
				}
			}
			/* Last element */
			final int diff_Q15 = ( 1 << 15 ) - ( NLSF_Q15[L-1] + NDeltaMin_Q15[L] );
			if( diff_Q15 < min_diff_Q15 ) {
				min_diff_Q15 = diff_Q15;
				I = L;
			}

			/***************************************************/
			/* Now check if the smallest distance non-negative */
			/***************************************************/
			if( min_diff_Q15 >= 0 ) {
				return;
			}

			if( I == 0 ) {
				/* Move away from lower limit */
				NLSF_Q15[0] = NDeltaMin_Q15[0];

			} else if( I == L) {
				/* Move away from higher limit */
				NLSF_Q15[L - 1] = (short)(( 1 << 15 ) - NDeltaMin_Q15[L]);

			} else {
				/* Find the lower extreme for the location of the current center frequency */
				int min_center_Q15 = 0;
				for( int k = 0; k < I; k++ ) {
					min_center_Q15 += NDeltaMin_Q15[k];
				}
				min_center_Q15 += (int)NDeltaMin_Q15[I] >> 1;

				/* Find the upper extreme for the location of the current center frequency */
				int max_center_Q15 = 1 << 15;
				for( int k = L; k > I; k-- ) {
					max_center_Q15 -= NDeltaMin_Q15[k];
				}
				max_center_Q15 -= (int)NDeltaMin_Q15[I] >> 1;

				/* Move apart, sorted by value, keeping the same center frequency */
				final int center_freq_Q15 = JSigProc_FIX.silk_LIMIT( JSigProc_FIX.silk_RSHIFT_ROUND( (int)NLSF_Q15[I - 1] + (int)NLSF_Q15[I], 1 ),
										min_center_Q15, max_center_Q15 );
				NLSF_Q15[I - 1] = (short)(center_freq_Q15 - ( (int)NDeltaMin_Q15[I] >> 1 ));
				NLSF_Q15[I] = (short)((int)NLSF_Q15[I - 1] + (int)NDeltaMin_Q15[I]);
			}
		}

		/* Safe and simple fall back method, which is less ideal than the above */
		if( loops == MAX_LOOPS )
		{
			/* Insertion sort (fast for already almost sorted arrays):   */
			/* Best case:  O(n)   for an already sorted array            */
			/* Worst case: O(n^2) for an inversely sorted array          */
			silk_insertion_sort_increasing_all_values_int16( NLSF_Q15/*[0]*/, L );

			/* First NLSF should be no less than NDeltaMin[0] */
			int v1 = (int)NLSF_Q15[0];// java
			int v2 = (int)NDeltaMin_Q15[0];// java
			NLSF_Q15[0] = (short)(v1 >= v2 ? v1 : v2);

			/* Keep delta_min distance between the NLSFs */
			for( int i = 1; i < L; i++ ) {
				v1 = (int)NLSF_Q15[i];
				v2 = (int)NLSF_Q15[i - 1] + (int)NDeltaMin_Q15[i];
				v2 = (v2 > Short.MAX_VALUE ? Short.MAX_VALUE : (v2 < Short.MIN_VALUE ? Short.MIN_VALUE : v2));
				NLSF_Q15[i] = (short)( v1 >= v2 ? v1 : v2 );
			}

			/* Last NLSF should be no higher than 1 - NDeltaMin[L] */
			v1 = (int)NLSF_Q15[L - 1];
			v2 = (int)(1 << 15) - NDeltaMin_Q15[L];
			NLSF_Q15[L - 1] = (short)( v1 <= v2 ? v1 : v2 );

			/* Keep NDeltaMin distance between the NLSFs */
			for( int i = L - 2; i >= 0; i-- ) {
				v1 = NLSF_Q15[i];
				final int i1 = i + 1;
				v2 = (int)NLSF_Q15[i1] - (int)NDeltaMin_Q15[i1];
				NLSF_Q15[i] = (short)(v1 <= v2 ? v1 : v2);
			}
		}
	}
	// end NLSF_stabilize.c

	// NLSF_unpack.c
	/**
	 * Unpack predictor values and indices for entropy coding tables
	 *
	 * @param ec_ix O    Indices to entropy tables [ LPC_ORDER ]
	 * @param pred_Q8 O    LSF predictor [ LPC_ORDER ]
	 * @param psNLSF_CB I    Codebook object
	 * @param CB1_index I    Index of vector in first LSF codebook
	 */
	static final void silk_NLSF_unpack(final short ec_ix[], final char pred_Q8[], final Jsilk_NLSF_CB_struct psNLSF_CB, final int CB1_index)
	{
		final char[] ec_sel = psNLSF_CB.ec_sel;// java
		int ec_sel_ptr = (CB1_index * psNLSF_CB.order) >> 1;// psNLSF_CB.ec_sel[ ec_sel_ptr ]
		for( int i = 0; i < psNLSF_CB.order; i += 2 ) {
			final int entry = (int)ec_sel[ ec_sel_ptr++ ];
			ec_ix  [ i     ] = (short)( (( entry >>> 1 ) & 7) * (2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE + 1) );
			pred_Q8[ i     ] = psNLSF_CB.pred_Q8[ i + ( entry & 1 ) * ( psNLSF_CB.order - 1 ) ];
			ec_ix  [ i + 1 ] = (short)( (( entry >>> 5 ) & 7) * (2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE + 1) );
			pred_Q8[ i + 1 ] = psNLSF_CB.pred_Q8[ i + ( ( entry >>> 4 ) & 1 ) * ( psNLSF_CB.order - 1 ) + 1 ];
		}
	}
	// end NLSF_unpack.c

	// NLSF_VQ_weights_laroia.c
	/*
	R. Laroia, N. Phamdo and N. Farvardin, "Robust and Efficient Quantization of Speech LSP
	Parameters Using Structured Vector Quantization", Proc. IEEE Int. Conf. Acoust., Speech,
	Signal Processing, pp. 641-644, 1991.
	*/
	/* Laroia low complexity NLSF weights */
	/**
	 *
	 * @param pNLSFW_Q_OUT O     Pointer to input vector weights [D]
	 * @param pNLSF_Q15 I     Pointer to input vector         [D]
	 * @param D I     Input vector dimension (even)
	 */
	static final void silk_NLSF_VQ_weights_laroia(final short[] pNLSFW_Q_OUT, final short[] pNLSF_Q15, final int D)
	{
		// celt_assert( D > 0 );
		// celt_assert( ( D & 1 ) == 0 );

		/* First value */
		int tmp1_int = pNLSF_Q15[ 0 ];
		tmp1_int = ( tmp1_int >= 1 ? tmp1_int : 1 );
		tmp1_int = ( (1 << ( 15 + Jdefine.NLSF_W_Q )) / tmp1_int );
		int tmp2_int = pNLSF_Q15[ 1 ] - pNLSF_Q15[ 0 ];
		tmp2_int = ( tmp2_int >= 1 ? tmp2_int : 1 );
		tmp2_int = ( (1 << ( 15 + Jdefine.NLSF_W_Q )) / tmp2_int );
		tmp1_int += tmp2_int;// java
		pNLSFW_Q_OUT[ 0 ] = (short)( tmp1_int < Short.MAX_VALUE ? tmp1_int : Short.MAX_VALUE );
		// silk_assert( pNLSFW_Q_OUT[ 0 ] > 0 );

		/* Main loop */
		for( int k = 1; k < D - 1; k += 2 ) {
			tmp1_int = pNLSF_Q15[ k + 1 ] - pNLSF_Q15[ k ];
			tmp1_int = ( tmp1_int > 1 ? tmp1_int : 1 );
			tmp1_int = ( (1 << ( 15 + Jdefine.NLSF_W_Q )) / tmp1_int );
			int v = tmp1_int + tmp2_int;// java
			pNLSFW_Q_OUT[ k ] = (short)( v < Short.MAX_VALUE ? v : Short.MAX_VALUE );
			// silk_assert( pNLSFW_Q_OUT[ k ] > 0 );

			tmp2_int = pNLSF_Q15[ k + 2 ] - pNLSF_Q15[ k + 1 ];
			tmp2_int = ( tmp2_int >= 1 ? tmp2_int : 1 );
			tmp2_int = ( (1 << ( 15 + Jdefine.NLSF_W_Q )) / tmp2_int );
			v = tmp1_int + tmp2_int;// java
			pNLSFW_Q_OUT[ k + 1 ] = (short)( v < Short.MAX_VALUE ? v : Short.MAX_VALUE );
			// silk_assert( pNLSFW_Q_OUT[ k + 1 ] > 0 );
		}

		/* Last value */
		tmp1_int = ( 1 << 15 ) - pNLSF_Q15[ D - 1 ];
		tmp1_int = ( tmp1_int >= 1 ? tmp1_int : 1 );
		tmp1_int = ( (1 << ( 15 + Jdefine.NLSF_W_Q )) / tmp1_int );
		tmp1_int += tmp2_int;// java
		pNLSFW_Q_OUT[ D - 1 ] = (short)( tmp1_int < Short.MAX_VALUE ? tmp1_int : Short.MAX_VALUE );
		// silk_assert( pNLSFW_Q_OUT[ D - 1 ] > 0 );
	}
	// end NLSF_VQ_weights_laroia.c

	// start NLSF_decode.c
	/**
	 * Predictive dequantizer for NLSF residuals
	 * Returns RD value in Q30
	 *
	 * @param x_Q10 O    Output [ order ]
	 * @param indices I    Quantization indices [ order ]
	 * @param ioffset I java an offset for the x_Q10
	 * @param pred_coef_Q8 I    Backward predictor coefs [ order ]
	 * @param quant_step_size_Q16 I    Quantization step size
	 * @param order  I    Number of input values
	 */
	private static final void silk_NLSF_residual_dequant(final short x_Q10[],
			final byte indices[], int ioffset,// java
			final char pred_coef_Q8[],
			final long quant_step_size_Q16, final short order )
	{// java: int32 quant_step_size_Q16 changed to int64 quant_step_size_Q16 to avoid casting inside the loop
		int out_Q10 = 0;
		int i = order - 1;
		ioffset += i;// java
		for( ; i >= 0; i-- ) {
			final int pred_Q10 = ( out_Q10 * (int)pred_coef_Q8[ i ] ) >> 8;
			out_Q10 = indices[ ioffset-- ] << 10;
			if( out_Q10 > 0 ) {
				// out_Q10 -= SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out_Q10 -= (int)(Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5);
			} else if( out_Q10 < 0 ) {
				// out_Q10 += SILK_FIX_CONST( Jdefine.NLSF_QUANT_LEVEL_ADJ, 10 );
				out_Q10 += (int)(Jdefine.NLSF_QUANT_LEVEL_ADJ * (1 << 10) + .5);
			}
			out_Q10  = pred_Q10 + (int)((out_Q10 * quant_step_size_Q16) >> 16);
			x_Q10[ i ] = (short)out_Q10;
		}
	}

	/**
	 * NLSF vector decoder
	 *
	 * @param pNLSF_Q15 O    Quantized NLSF vector [ LPC_ORDER ]
	 * @param NLSFIndices I    Codebook path vector [ LPC_ORDER + 1 ]
	 * @param psNLSF_CB I    Codebook object
	 */
	static final void silk_NLSF_decode(final short[] pNLSF_Q15, final byte[] NLSFIndices, final Jsilk_NLSF_CB_struct psNLSF_CB)
	{
		final short ec_ix[] = new short[    Jdefine.MAX_LPC_ORDER ];
		final short res_Q10[] = new short[  Jdefine.MAX_LPC_ORDER ];

		final char pred_Q8[] = new char[ Jdefine.MAX_LPC_ORDER ];// java uint8 to char
		/* Unpack entropy table indices and predictor for current CB1 index */
		silk_NLSF_unpack( ec_ix, pred_Q8, psNLSF_CB, NLSFIndices[ 0 ] );

		/* Predictive residual dequantizer */
		silk_NLSF_residual_dequant( res_Q10, NLSFIndices, 1, pred_Q8, psNLSF_CB.quantStepSize_Q16, psNLSF_CB.order );

		/* Apply inverse square-rooted weights to first stage and add to output */
		// final char[] pCB_element = &psNLSF_CB.CB1_NLSF_Q8[ NLSFIndices[ 0 ] * psNLSF_CB.order ];
		// final short[] pCB_Wght_Q9 = &psNLSF_CB.CB1_Wght_Q9[ NLSFIndices[ 0 ] * psNLSF_CB.order ];
		final char[] pCB_element = psNLSF_CB.CB1_NLSF_Q8;// java
		final short[] pCB_Wght_Q9 = psNLSF_CB.CB1_Wght_Q9;// java
		int indx = NLSFIndices[ 0 ] * psNLSF_CB.order;// java
		for( int i = 0, ie = psNLSF_CB.order; i < ie; i++, indx++ ) {
			final int NLSF_Q15_tmp = (( (int)res_Q10[ i ] << 14 ) / pCB_Wght_Q9[ indx ]) + ((int)pCB_element[ indx ] << 7);
			pNLSF_Q15[ i ] = (short)(NLSF_Q15_tmp > 32767 ? 32767 : (NLSF_Q15_tmp < 0 ? 0 : NLSF_Q15_tmp));
		}

		/* NLSF stabilization */
		silk_NLSF_stabilize( pNLSF_Q15, psNLSF_CB.deltaMin_Q15, psNLSF_CB.order );
	}
	// end NLSF_decode.c

	// start sum_sqr_shift.c
	/**
	 * Compute number of bits to right shift the sum of squares of a vector
	 * of int16s to make it fit in an int32
	 *
	 * java changed: return long (energy << 32 | shift)
	 *
	 * @param energy O   Energy of x, after shifting to the right
	 * @param shift O   Number of bits right shift applied to energy
	 * @param x I   Input vector
	 * @param xoffset I java an offset for the x
	 * @param len I   Length of input vector
	 * @return (energy << 32 | shift)
	 */
	static final long silk_sum_sqr_shift(/*final int[] energy, final int[] shift, */final short[] x, final int xoffset, int len)
	{
		/* Do a first run with the maximum shift we could have. */
		int shft = 31 - Jmacros.silk_CLZ32( len );
		/* Let's be conservative with rounding and start with nrg=len. */
		int nrg  = len;
		len += xoffset;// java
		final int len1 = len - 1;// java
		int i;
		for( i = xoffset; i < len1; /* i += 2 */ ) {// java changed
			int nrg_tmp = (int)x[ i++ ];// java uint32->int32, use >>>
			nrg_tmp *= nrg_tmp;
			int v = (int)x[ i++ ];// java
			v *= v;
			nrg_tmp += v;
			nrg += nrg_tmp >>> shft;
		}
		if( i < len ) {
			/* One sample left to process */
			int nrg_tmp = (int)x[ i ];
			nrg_tmp *= nrg_tmp;
			nrg += nrg_tmp >>> shft;
		}
		// silk_assert( nrg >= 0 );
		/* Make sure the result will fit in a 32-bit signed integer with two bits
		   of headroom. */
		shft = shft + 3 - Jmacros.silk_CLZ32( nrg );
		shft = ( 0 > shft ? 0 : shft );
		nrg = 0;
		for( i = xoffset; i < len1; /* i += 2 */ ) {// java changed
			int nrg_tmp = (int)x[ i++ ];// java uint32->int32, use >>>
			nrg_tmp *= nrg_tmp;
			int v = (int)x[ i++ ];// java
			v *= v;
			nrg_tmp += v;
			nrg += nrg_tmp >>> shft;
		}
		if( i < len ) {
			/* One sample left to process */
			int nrg_tmp = (int)x[ i ];// java uint32->int32, use >>>
			nrg_tmp *= nrg_tmp;
			nrg += nrg_tmp >>> shft;
		}
		// silk_assert( nrg >= 0 );

		/* Output arguments */
		// shift[0]  = shft;
		// energy[0] = nrg;
		return ((long)nrg << 32) | ((long)shft & 0xffffffffL);// java
	}
	// end sum_sqr_shift.c
}
