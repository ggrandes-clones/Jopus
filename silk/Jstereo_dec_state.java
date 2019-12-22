package silk;

// structs.h

final class Jstereo_dec_state {
	final short pred_prev_Q13[] = new short[ 2 ];
	final short sMid[] = new short[ 2 ];
	final short sSide[] = new short[ 2 ];
	//
	final void clear() {
		pred_prev_Q13[0] = 0; pred_prev_Q13[1] = 0;
		sMid[0] = 0; sMid[1] = 0;
		sSide[0] = 0; sSide[1] = 0;
	}
	final void copyFrom(final Jstereo_dec_state s) {
		pred_prev_Q13[0] = s.pred_prev_Q13[0]; pred_prev_Q13[1] = s.pred_prev_Q13[1];
		sMid[0] = s.sMid[0]; sMid[1] = s.sMid[1];
		sSide[0] = s.sSide[0]; sSide[1] = s.sSide[1];
	}
	//
	// start stereo_MS_to_LR.c
	/**
	 * Convert adaptive Mid/Side representation to Left/Right stereo signal
	 *
	 * @param state I/O  State
	 * @param x1 I/O  Left input signal, becomes mid signal
	 * @param x2 I/O  Right input signal, becomes side signal
	 * @param pred_Q13 I    Predictors
	 * @param fs_kHz I    Samples rate (kHz)
	 * @param frame_length I    Number of samples
	 */
	final void silk_stereo_MS_to_LR(
			final short x1[], int xoffset1,// java
			final short x2[], int xoffset2,// java
			final int pred_Q13[], final int fs_kHz, final int frame_length
		)
	{
		/* Buffering */
		x1[xoffset1] = this.sMid[0]; x1[xoffset1 + 1] = this.sMid[1];
		x2[xoffset2] = this.sSide[0]; x2[xoffset2 + 1] = this.sSide[1];
		int v = xoffset1 + frame_length;// java
		this.sMid[0] = x1[ v++ ]; this.sMid[1] = x1[ v ];
		v = xoffset2 + frame_length;// java
		this.sSide[0] = x2[ v++ ]; this.sSide[1] = x2[ v ];

		/* Interpolate predictors and add prediction to side channel */
		int pred0_Q13  = (int)this.pred_prev_Q13[ 0 ];
		int pred1_Q13  = (int)this.pred_prev_Q13[ 1 ];
		final int denom_Q16  = (1 << 16) / (Jdefine.STEREO_INTERP_LEN_MS * fs_kHz);
		final int delta0_Q13 = JSigProc_FIX.silk_RSHIFT_ROUND( ( (pred_Q13[ 0 ] - (int)this.pred_prev_Q13[ 0 ]) * denom_Q16 ), 16 );
		final int delta1_Q13 = JSigProc_FIX.silk_RSHIFT_ROUND( ( (pred_Q13[ 1 ] - (int)this.pred_prev_Q13[ 1 ]) * denom_Q16 ), 16 );
		for( int n = xoffset1, ne = xoffset1 + Jdefine.STEREO_INTERP_LEN_MS * fs_kHz, n2 = xoffset2 + 1; n < ne; n++, n2++ ) {
			pred0_Q13 += delta0_Q13;
			pred1_Q13 += delta1_Q13;
			v = (int)x1[ n + 1 ];// java
			int sum = ( ( ((int)x1[ n ] + (int)x1[ n + 2 ]) + (v << 1) ) << 9 );       /* Q11 */
			sum = ( (int)x2[ n2 ] << 8 ) + (int)((sum * (long)pred0_Q13) >> 16);         /* Q8  */
			sum += (int)((( v << 11 ) * (long)pred1_Q13) >> 16);        /* Q8  */
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( sum, 8 );
			x2[ n2 ] = (short)(sum > Short.MAX_VALUE ? Short.MAX_VALUE : (sum < Short.MIN_VALUE ? Short.MIN_VALUE : sum));
		}
		pred0_Q13 = pred_Q13[ 0 ];
		pred1_Q13 = pred_Q13[ 1 ];
		v = Jdefine.STEREO_INTERP_LEN_MS * fs_kHz;// java
		for( int n = xoffset1 + v, ne = xoffset1 + frame_length, n2 = xoffset2 + v + 1; n < ne; n++, n2++ ) {
			v = (int)x1[ n + 1 ];// java
			int sum = ( ( ((int)x1[ n ] + (int)x1[ n + 2 ]) + (v << 1) ) << 9 );       /* Q11 */
			sum = ( (int)x2[ n2 ] << 8 ) + (int)((sum * (long)pred0_Q13) >> 16);         /* Q8  */
			sum += (int)((( v << 11 ) * (long)pred1_Q13) >> 16);        /* Q8  */
			sum = JSigProc_FIX.silk_RSHIFT_ROUND( sum, 8 );
			x2[ n2 ] = (short)(sum > Short.MAX_VALUE ? Short.MAX_VALUE : (sum < Short.MIN_VALUE ? Short.MIN_VALUE : sum));
		}
		this.pred_prev_Q13[ 0 ] = (short)pred_Q13[ 0 ];
		this.pred_prev_Q13[ 1 ] = (short)pred_Q13[ 1 ];

		/* Convert to left/right signals */
		xoffset1++;
		xoffset2++;
		for( final int ne = xoffset1 + frame_length; xoffset1 <= ne; xoffset1++, xoffset2++ ) {// java changed to +1
			int diff = (int)x1[ xoffset1 ];// java
			v = (int)x2[ xoffset2 ];// java
			final int sum  = diff + v;
			diff -= v;
			x1[ xoffset1 ] = (short)(sum > Short.MAX_VALUE ? Short.MAX_VALUE : (sum < Short.MIN_VALUE ? Short.MIN_VALUE : sum));
			x2[ xoffset2 ] = (short)(diff > Short.MAX_VALUE ? Short.MAX_VALUE : (diff < Short.MIN_VALUE ? Short.MIN_VALUE : diff));
		}
	}
	// end stereo_MS_to_LR.c
}
