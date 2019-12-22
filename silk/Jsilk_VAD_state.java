package silk;

// structs.h

/** VAD state */
final class Jsilk_VAD_state {
	/** Analysis filterbank state: 0-8 kHz */
	final int AnaState[] = new int[ 2 ];
	/** Analysis filterbank state: 0-4 kHz */
	final int AnaState1[] = new int[ 2 ];
	/** Analysis filterbank state: 0-2 kHz */
	final int AnaState2[] = new int[ 2 ];
	/** Subframe energies */
	final int XnrgSubfr[] = new int[ Jdefine.VAD_N_BANDS ];
	/** Smoothed energy level in each band */
	final int NrgRatioSmth_Q8[] = new int[ Jdefine.VAD_N_BANDS ];
	/** State of differentiator in the lowest band */
	short     HPstate;
	/** Noise energy level in each band */
	final int NL[] = new int[ Jdefine.VAD_N_BANDS ];
	/** Inverse noise energy level in each band */
	private final int inv_NL[] = new int[ Jdefine.VAD_N_BANDS ];
	/** Noise level estimator bias/offset */
	private final int NoiseLevelBias[] = new int[ Jdefine.VAD_N_BANDS ];
	/** Frame counter used in the initial phase */
	private int       counter;
	//
	final void clear() {
		AnaState[0] = 0; AnaState[1] = 0;
		AnaState1[0] = 0; AnaState1[1] = 0;
		AnaState2[0] = 0; AnaState2[1] = 0;
		int i = Jdefine.VAD_N_BANDS;
		do {
			XnrgSubfr[--i] = 0;
			NrgRatioSmth_Q8[i] = 0;
			NL[i] = 0;
			inv_NL[i] = 0;
			NoiseLevelBias[i] = 0;
		} while( i > 0 );
		HPstate = 0;
		counter = 0;
	}
	final void copyFrom(final Jsilk_VAD_state s) {
		AnaState[0] = s.AnaState[0]; AnaState[1] = s.AnaState[1];
		AnaState1[0] = s.AnaState1[0]; AnaState1[1] = s.AnaState1[1];
		AnaState2[0] = s.AnaState2[1]; AnaState2[1] = s.AnaState2[1];
		System.arraycopy( s.XnrgSubfr, 0, XnrgSubfr, 0, Jdefine.VAD_N_BANDS );
		System.arraycopy( s.NrgRatioSmth_Q8, 0, NrgRatioSmth_Q8, 0, Jdefine.VAD_N_BANDS );
		HPstate = s.HPstate;
		System.arraycopy( s.NL, 0, NL, 0, Jdefine.VAD_N_BANDS );
		System.arraycopy( s.inv_NL, 0, inv_NL, 0, Jdefine.VAD_N_BANDS );
		System.arraycopy( s.NoiseLevelBias, 0, NoiseLevelBias, 0, Jdefine.VAD_N_BANDS );
		counter = s.counter;
	}
	//
	// start VAD.c
	/**
	 * Initialization of the Silk VAD
	 *
	 * @param psSilk_VAD I/O  Pointer to Silk VAD state
	 * @return O    Return value, 0 if success
	 */
	final int silk_VAD_Init()
	{
		final int ret = 0;

		/* reset state memory */
		// silk_memset( psSilk_VAD, 0, sizeof( silk_VAD_state ) );
		clear();

		/* init noise levels */
		/* Initialize array with approx pink noise levels (psd proportional to inverse of frequency) */
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			final int v = Jdefine.VAD_NOISE_LEVELS_BIAS / (b + 1);// java
			this.NoiseLevelBias[ b ] = v > 1 ? v : 1;
		}

		/* Initialize state */
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			this.NL[ b ]     = 100 * this.NoiseLevelBias[ b ];
			this.inv_NL[ b ] = Integer.MAX_VALUE / this.NL[ b ];
		}
		this.counter = 15;

		/* init smoothed energy-to-noise ratio*/
		for( int b = 0; b < Jdefine.VAD_N_BANDS; b++ ) {
			this.NrgRatioSmth_Q8[ b ] = 100 * 256;       /* 100 * 256 -. 20 dB SNR */
		}

		return( ret );
	}
	// end start VAD.c

	// start VAD.c
	/**
	 * Noise level estimation
	 *
	 * @param pX I    subband energies
	 * @param psSilk_VAD I/O  Pointer to Silk VAD state
	 */
	final void silk_VAD_GetNoiseLevels(final int pX[/* VAD_N_BANDS */])
	{
		/* Initially faster smoothing */
		final int min_coef;
		if( this.counter < 1000 ) { /* 1000 = 20 sec */
			min_coef = (int)Short.MAX_VALUE / (( this.counter >> 4 ) + 1);
			/* Increment frame counter */
			this.counter++;
		} else {
			min_coef = 0;
		}

		for( int k = 0; k < Jdefine.VAD_N_BANDS; k++ ) {
			/* Get old noise level estimate for current band */
			int nl = this.NL[ k ];
			// silk_assert( nl >= 0 );

			/* Add bias */
			int nrg = pX[ k ] + this.NoiseLevelBias[ k ];// java
			nrg = (nrg & 0x80000000) != 0 ? Integer.MAX_VALUE : nrg;
			// silk_assert( nrg > 0 );

			/* Invert energies */
			final int inv_nrg = Integer.MAX_VALUE / nrg;
			// silk_assert( inv_nrg >= 0 );

			/* Less update when subband energy is high */
			int coef;
			if( nrg > ( nl << 3 ) ) {
				coef = Jdefine.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16 >> 3;
			} else if( nrg < nl ) {
				coef = Jdefine.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16;
			} else {
				coef = (int)((((int)(((long)inv_nrg * nl) >> 16)) * (long)(Jdefine.VAD_NOISE_LEVEL_SMOOTH_COEF_Q16 << 1)) >> 16);
			}

			/* Initially faster smoothing */
			coef = ( coef >= min_coef ? coef : min_coef );

			/* Smooth inverse energies */
			this.inv_NL[ k ] += (int)(((long)(inv_nrg - this.inv_NL[ k ]) * (long)coef) >> 16);
			// silk_assert( psSilk_VAD.inv_NL[ k ] >= 0 );

			/* Compute noise level by inverting again */
			nl = ( Integer.MAX_VALUE / this.inv_NL[ k ] );
			// silk_assert( nl >= 0 );

			/* Limit noise levels (guarantee 7 bits of head room) */
			nl = ( nl < 0x00FFFFFF ? nl : 0x00FFFFFF );

			/* Store as part of state */
			this.NL[ k ] = nl;
		}
	}
	// end VAD.c
}
