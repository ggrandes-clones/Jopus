package silk;

// structs.h

/** Variable cut-off low-pass filter state */
final class Jsilk_LP_state {
	/** Low pass filter state */
	final int In_LP_State[] = new int[ 2 ];
	/** Counter which is mapped to a cut-off frequency */
	int transition_frame_no;
	/** Operating mode, <0: switch down, >0: switch up; 0: do nothing */
	int mode;
	/** If non-zero, holds the last sampling rate before a bandwidth switching reset. */
	int saved_fs_kHz;
	//
	Jsilk_LP_state() {
	}
	Jsilk_LP_state(final Jsilk_LP_state s) {
		copyFrom( s );
	}
	final void clear() {
		In_LP_State[0] = 0; In_LP_State[1] = 0;
		transition_frame_no = 0;
		mode = 0;
	}
	final void copyFrom(final Jsilk_LP_state s) {
		In_LP_State[0] = s.In_LP_State[0]; In_LP_State[1] = s.In_LP_State[1];
		transition_frame_no = s.transition_frame_no;
		mode = s.mode;
		saved_fs_kHz = s.saved_fs_kHz;
	}
	//
	/**
    Elliptic/Cauer filters designed with 0.1 dB passband ripple,
    80 dB minimum stopband attenuation, and
    [0.95 : 0.15 : 0.35] normalized cut off frequencies.
	 */
	// start biquad_alt.c

	/*                                               *
	 * silk_biquad_alt.c                             *
	 *                                               *
	 * Second order ARMA filter                      *
	 * Can handle slowly varying filter coefficients *
	 *                                               */
	/**
	 * Second order ARMA filter, alternative implementation
	 *
	 * @param in I     input signal
	 * @param inoffset I java an offset for the in
	 * @param B_Q28 I     MA coefficients [3]
	 * @param A_Q28 I     AR coefficients [2]
	 * @param S I/O   State vector [2]
	 * @param out O     output signal
	 * @param outoffset I java an offset for the out
	 * @param len I     signal length (must be even)
	 * @param stride I     Operate on interleaved signal if > 1
	 */
	private static final void silk_biquad_alt_stride1(final short[] in, int inoffset,// java
			final int[] B_Q28, final int[] A_Q28, final int[] S,
			final short[] out, int outoffset,// java
			int len)
	{
		/* DIRECT FORM II TRANSPOSED (uses 2 element state vector) */

		/* Negate A_Q28 values and split in two parts */
		final long A0_L_Q28 = (long)(( -A_Q28[ 0 ] ) & 0x00003FFF);        /* lower part */
		final long A0_U_Q28 = (long)(-A_Q28[ 0 ] >> 14);      /* upper part */
		final long A1_L_Q28 = (long)(( -A_Q28[ 1 ] ) & 0x00003FFF);        /* lower part */
		final long A1_U_Q28 = (long)(-A_Q28[ 1 ] >> 14);      /* upper part */

		// java vars
		final long b0 = (long)B_Q28[ 0 ];
		final long b1 = (long)B_Q28[ 1 ];
		final long b2 = (long)B_Q28[ 2 ];
		len += inoffset;// java
		for( ; inoffset < len; inoffset++, outoffset++ ) {
			/* S[ 0 ], S[ 1 ]: Q12 */
			final long inval = (long)in[ inoffset ];
			final int out32_Q14 = ( S[ 0 ] + (int)((b0 * inval) >> 16) ) << 2;

			int v = S[ 1 ] + JSigProc_FIX.silk_RSHIFT_ROUND( ( (int)((out32_Q14 * A0_L_Q28) >> 16) ), 14 );
			v += (int)(( out32_Q14 * A0_U_Q28 ) >> 16);
			v += (int)(( b1 * inval ) >> 16);
			S[ 0 ] = v;

			v = JSigProc_FIX.silk_RSHIFT_ROUND( ( (int)((out32_Q14 * A1_L_Q28) >> 16) ), 14 );
			v += (int)(( out32_Q14 * A1_U_Q28 ) >> 16);
			v += (int)(( b2 * inval ) >> 16);
			S[ 1 ] = v;

			/* Scale back to Q0 and saturate */
			v = ((out32_Q14 + (1 << 14) - 1) >> 14);// java
			out[ outoffset ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}
	}
	// java: silk_biquad_alt_stride2_c is used only in the fixed point version
	/**
	 *
	 * @param in I     input signal
	 * @param B_Q28 I     MA coefficients [3]
	 * @param A_Q28 I     AR coefficients [2]
	 * @param S I/O   State vector [4]
	 * @param out O     output signal
	 * @param len I     signal length (must be even)
	 */
	/* private static final void silk_biquad_alt_stride2_c(final short[] in, final int[] B_Q28, final int[] A_Q28, final int[] S, final short[] out, int len)
	{
		// DIRECT FORM II TRANSPOSED (uses 2 element state vector)
		// java replaced by variables. final opus_int32 out32_Q14[ 2 ];
		// int out32_Q14_0, out32_Q14_1;

		/ Negate A_Q28 values and split in two parts
		final long A0_L_Q28 = (long)(( -A_Q28[ 0 ] ) & 0x00003FFF);// lower part. java changed to long
		final long A0_U_Q28 = (long)((short)( -A_Q28[ 0 ] >> 14 ));// upper part. java changed to long
		final long A1_L_Q28 = (long)(( -A_Q28[ 1 ] ) & 0x00003FFF);// lower part. java changed to long
		final long A1_U_Q28 = (long)((short)( -A_Q28[ 1 ] >> 14 ));// upper part. java changed to long

		len <<= 1;// java
		for( int k = 0; k < len; k += 2 ) {
			// S[ 0 ], S[ 1 ], S[ 2 ], S[ 3 ]: Q12
			final int out32_Q14_0 = (int)(S[ 0 ] + ((B_Q28[ 0 ] * (long)(in[ k + 0 ])) >> 16)) << 2;
			final int out32_Q14_1 = (int)(S[ 2 ] + ((B_Q28[ 0 ] * (long)(in[ k + 1 ])) >> 16)) << 2;

			S[ 0 ] = S[ 1 ] + JSigProc_FIX.silk_RSHIFT_ROUND( (int)((out32_Q14_0 * A0_L_Q28) >> 16), 14 );
			S[ 2 ] = S[ 3 ] + JSigProc_FIX.silk_RSHIFT_ROUND( (int)((out32_Q14_1 * A0_L_Q28) >> 16), 14 );
			S[ 0 ] = (int)(S[ 0 ] + ((out32_Q14_0 * A0_U_Q28) >> 16));
			S[ 2 ] = (int)(S[ 2 ] + ((out32_Q14_1 * A0_U_Q28) >> 16));

			S[ 0 ] = (int)(S[ 0 ] + ((B_Q28[ 1 ] * (long)(in[ k + 0 ])) >> 16));
			S[ 2 ] = (int)(S[ 2 ] + ((B_Q28[ 1 ] * (long)(in[ k + 1 ])) >> 16));

			S[ 1 ] = JSigProc_FIX.silk_RSHIFT_ROUND( (int)((out32_Q14_0 * A1_L_Q28) >> 16), 14 );
			S[ 3 ] = JSigProc_FIX.silk_RSHIFT_ROUND( (int)((out32_Q14_1 * A1_L_Q28) >> 16), 14 );
			S[ 1 ] = (int)(S[ 1 ] + ((out32_Q14_0 * A1_U_Q28) >> 16));
			S[ 3 ] = (int)(S[ 3 ] + ((out32_Q14_1 * A1_U_Q28) >> 16));
			S[ 1 ] = (int)(S[ 1 ] + ((B_Q28[ 2 ] * (long)(in[ k + 0 ])) >> 16));
			S[ 3 ] = (int)(S[ 3 ] + ((B_Q28[ 2 ] * (long)(in[ k + 1 ])) >> 16));

			// Scale back to Q0 and saturate
			int v = ( (out32_Q14_0 + (1<<14) - 1) >> 14 );// java
			out[ k + 0 ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			v = ( (out32_Q14_1 + (1<<14) - 1) >> 14 );// java
			out[ k + 1 ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}
	} */
	// end biquad_alt.c
	// enc
	// start LP_variable_cutoff.c
	/**
	 * Helper function, interpolates the filter taps
	 *
	 * @param B_Q28
	 * @param A_Q28
	 * @param ind
	 * @param fac_Q16
	 */
	private static final void silk_LP_interpolate_filter_taps(final int B_Q28[/* TRANSITION_NB */], final int A_Q28[/* TRANSITION_NA */],
			final int ind, final int fac_Q16)
	{
		if( ind < Jdefine.TRANSITION_INT_NUM - 1 ) {
			if( fac_Q16 > 0 ) {
				if( fac_Q16 < 32768 ) { /* fac_Q16 is in range of a 16-bit int */
					final long fac = (long)fac_Q16;// java
					int[] tr_ind = Jtables_other.silk_Transition_LP_B_Q28[ ind ];// java
					int[] tr_ind1 = Jtables_other.silk_Transition_LP_B_Q28[ ind + 1 ];// java
					/* Piece-wise linear interpolation of B and A */
					for( int nb = 0; nb < Jdefine.TRANSITION_NB; nb++ ) {
						B_Q28[ nb ] = tr_ind[ nb ] + (int)(((tr_ind1[ nb ] - tr_ind[ nb ]) * fac) >> 16 );
					}
					tr_ind = Jtables_other.silk_Transition_LP_A_Q28[ ind ];// java
					tr_ind1 = Jtables_other.silk_Transition_LP_A_Q28[ ind + 1 ];// java
					for( int na = 0; na < Jdefine.TRANSITION_NA; na++ ) {
						A_Q28[ na ] = tr_ind[ na ] + (int)(((tr_ind1[ na ] - tr_ind[ na ]) * fac) >> 16 );
					}
				} else { /* ( fac_Q16 - ( 1 << 16 ) ) is in range of a 16-bit int */
					// silk_assert( fac_Q16 - ( 1 << 16 ) == silk_SAT16( fac_Q16 - ( 1 << 16 ) ) );
					final long fac = (long)(fac_Q16 - (1 << 16));// java
					int[] tr_ind = Jtables_other.silk_Transition_LP_B_Q28[ ind ];// java
					int[] tr_ind1 = Jtables_other.silk_Transition_LP_B_Q28[ ind + 1 ];// java
					/* Piece-wise linear interpolation of B and A */
					for( int nb = 0; nb < Jdefine.TRANSITION_NB; nb++ ) {
						B_Q28[ nb ] = tr_ind1[ nb ] + (int)(((tr_ind1[ nb ] - tr_ind[ nb ]) * fac) >> 16 );
					}
					tr_ind = Jtables_other.silk_Transition_LP_A_Q28[ ind ];// java
					tr_ind1 = Jtables_other.silk_Transition_LP_A_Q28[ ind + 1 ];// java
					for( int na = 0; na < Jdefine.TRANSITION_NA; na++ ) {
						A_Q28[ na ] = tr_ind1[ na ] + (int)(((tr_ind1[ na ] - tr_ind[ na ]) * fac) >> 16 );
					}
				}
			} else {
				System.arraycopy( Jtables_other.silk_Transition_LP_B_Q28[ ind ], 0, B_Q28, 0, Jdefine.TRANSITION_NB );
				System.arraycopy( Jtables_other.silk_Transition_LP_A_Q28[ ind ], 0, A_Q28, 0, Jdefine.TRANSITION_NA );
			}
		} else {
			System.arraycopy( Jtables_other.silk_Transition_LP_B_Q28[ Jdefine.TRANSITION_INT_NUM - 1 ], 0, B_Q28, 0, Jdefine.TRANSITION_NB );
			System.arraycopy( Jtables_other.silk_Transition_LP_A_Q28[ Jdefine.TRANSITION_INT_NUM - 1 ], 0, A_Q28, 0, Jdefine.TRANSITION_NA );
		}
	}

	/**
	 * Low-pass filter with variable cutoff frequency based on
	 * piece-wise linear interpolation between elliptic filters
	 * Start by setting psEncC.mode <> 0;
	 * Deactivate by setting psEncC.mode = 0;
	 *
	 * @param psLP I/O  LP filter state
	 * @param frame I/O  Low-pass filtered output signal
	 * @param foffset I java an offset for the frame
	 * @param frame_length I    Frame length
	 */
	final void silk_LP_variable_cutoff(final short[] frame, final int foffset, final int frame_length)
	{
		// silk_assert( psLP.transition_frame_no >= 0 && psLP.transition_frame_no <= Jdefine.TRANSITION_FRAMES );

		/* Run filter if needed */
		if( this.mode != 0 ) {
			/* Calculate index and interpolation factor for interpolation */
// #if( TRANSITION_INT_STEPS == 64 )
			int fac_Q16 = ( (Jdefine.TRANSITION_FRAMES - this.transition_frame_no) << (16 - 6) );
/* #else
			fac_Q16 = silk_DIV32_16( silk_LSHIFT( TRANSITION_FRAMES - psLP.transition_frame_no, 16 ), TRANSITION_FRAMES );
#endif */
			int ind = ( fac_Q16 >> 16 );
			fac_Q16 -= ( ind << 16 );

			// silk_assert( ind >= 0 );
			// silk_assert( ind < Jdefine.TRANSITION_INT_NUM );

			final int B_Q28[] = new int[ Jdefine.TRANSITION_NB ];
			final int A_Q28[] = new int[ Jdefine.TRANSITION_NA ];

			/* Interpolate filter coefficients */
			silk_LP_interpolate_filter_taps( B_Q28, A_Q28, ind, fac_Q16 );

			/* Update transition frame number for next frame */
			ind = this.transition_frame_no + this.mode;// java
			this.transition_frame_no = (ind > Jdefine.TRANSITION_FRAMES ? Jdefine.TRANSITION_FRAMES : (ind < 0 ? 0 : ind));

			/* ARMA low-pass filtering */
			// silk_assert( TRANSITION_NB == 3 && TRANSITION_NA == 2 );
			silk_biquad_alt_stride1( frame, foffset, B_Q28, A_Q28, this.In_LP_State, frame, foffset, frame_length );
		}
	}
	// end LP_variable_cutoff.c
}
