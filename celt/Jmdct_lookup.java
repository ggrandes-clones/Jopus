package celt;

// mdct.h

public final class Jmdct_lookup {
	int n;
	int maxshift;
	public final Jkiss_fft_state kfft[] = new Jkiss_fft_state[4];
	float[] trig;
	//
	Jmdct_lookup() {
	}
	Jmdct_lookup(final int in, final int imaxshift, final Jkiss_fft_state[] ikfft, final float[] itrig) {
		n = in;
		maxshift = imaxshift;
		kfft[0] = ikfft[0]; kfft[1] = ikfft[1]; kfft[2] = ikfft[2]; kfft[3] = ikfft[3];
		trig = itrig;
	}
	final void copyFrom(final Jmdct_lookup lookup) {
		n = lookup.n;
		maxshift = lookup.maxshift;
		kfft[0] = lookup.kfft[0]; kfft[1] = lookup.kfft[1]; kfft[2] = lookup.kfft[2]; kfft[3] = lookup.kfft[3];
		trig = lookup.trig;
	}

	// start mdct.c
/* #ifdef CUSTOM_MODES

	int clt_mdct_init(final mdct_lookup *l,int N, int maxshift, int arch)
	{
		int i;
		kiss_twiddle_scalar *trig;
		int shift;
		int N2 = N >> 1;
		l.n = N;
		l.maxshift = maxshift;
		for( i = 0; i <= maxshift; i++ )
		{
			if( i == 0 )
				l.kfft[i] = opus_fft_alloc( N >> 2 >> i, 0, 0, arch );
			else
				l.kfft[i] = opus_fft_alloc_twiddles( N >> 2 >> i, 0, 0, l.kfft[0], arch );
#ifndef ENABLE_TI_DSPLIB55
			if( l.kfft[i] == NULL )
				return 0;
#endif
		}
		l.trig = trig = (kiss_twiddle_scalar*)opus_alloc((N - (N2 >> maxshift)) * sizeof(kiss_twiddle_scalar));
		if( l.trig == NULL )
			return 0;
		for( shift = 0; shift <= maxshift; shift++ )
		{
			// We have enough points that sine isn't necessary
#if defined(FIXED_POINT)
#if 1
			for( i = 0; i < N2; i++ )
				trig[i] = TRIG_UPSCALE * celt_cos_norm(DIV32(ADD32(SHL32(EXTEND32(i), 17), N2 + 16384), N));
#else
			for( i = 0; i < N2; i++ )
				trig[i] = (kiss_twiddle_scalar)MAX32(-32767, MIN32(32767, floor(.5 + 32768 * cos(2 * M_PI * (i + .125) / N))));
#endif
#else
			for( i = 0; i < N2; i++ )
				trig[i] = (kiss_twiddle_scalar)cos( 2 * PI * (i + .125) / N );
#endif
			trig += N2;
			N2 >>= 1;
			N >>= 1;
		}
		return 1;
	}

	void clt_mdct_clear(mdct_lookup *l, int arch)
	{
		int i;
		for( i = 0; i <= l.maxshift; i++ )
			opus_fft_free( l.kfft[i], arch );
		opus_free( (kiss_twiddle_scalar*)l.trig );
	}

#endif */ /* CUSTOM_MODES */

	/* Forward MDCT trashes the input array */
// #ifndef OVERRIDE_clt_mdct_forward
	//private static final void clt_mdct_forward_c(final Jmdct_lookup l, final float[] in, final float[] out,
	//		final float[] window, final int overlap, final int shift, int stride)//, final int arch)
	// java renamed
	/** Compute a forward MDCT and scale by 4/N, trashes the input array */
	public final void clt_mdct_forward(
			final float[] in, final int inoffset,// java
			final float[] out, final int outoffset,// java
			final float[] window, final int overlap, final int shift, int stride)//, final int arch)
	{
		final Jkiss_fft_state st = this.kfft[shift];
/* #ifdef FIXED_POINT
		// Allows us to scale with MULT16_32_Q16(), which is faster than
		// MULT16_32_Q15() on ARM.
		int scale_shift = st.scale_shift-1;
#endif */
		// SAVE_STACK;
		// (void)arch;
		final float scale = st.scale;

		int N = this.n;
		final float[] this_trig = this.trig;
		int trig_offset = 0;// .trig[ trig ]// java renamed
		for( int i = 0; i < shift; i++ )
		{
			N >>= 1;
			trig_offset += N;
		}
		final int N2 = N >> 1;
		final int N4 = N >> 2;

		final float[] f = new float[N2];

		/* Consider the input to be composed of four blocks: [a, b, c, d] */
		/* Window, shuffle, fold */
		{
			/* Temp pointers to make it really clear to the compiler what we're doing */
			int xp1 = inoffset + (overlap >> 1);// in[ xp1 ]
			int xp2 = N2 - 1 + xp1;// in[ xp2 ]
			int yp = 0;// f[ yp ]
			int wp1 = (overlap >> 1);// window[ wp1 ]
			int wp2 = wp1 - 1;// window[ wp2 ]
			int end = ((overlap + 3) >> 2) << 1;// java
			while( yp < end )
			{
				/* Real part arranged as -d-cR, Imag part arranged as -b+aR*/
				f[yp++] = (window[wp2] * in[xp1 + N2]) + (window[wp1] * in[xp2]);
				f[yp++] = (window[wp1] * in[xp1])      - (window[wp2] * in[xp2 - N2]);
				xp1 += 2;
				xp2 -= 2;
				wp1 += 2;
				wp2 -= 2;
			}
			wp1 = 0;// window[ wp1 ]
			wp2 = overlap - 1;// window[ wp2 ]
			final int N4_2 = N4 << 1;
			end = N4_2 - end;// java
			while( yp < end )
			{
				/* Real part arranged as a-bR, Imag part arranged as -c-dR */
				f[yp++] = in[xp2];
				f[yp++] = in[xp1];
				xp1 += 2;
				xp2 -= 2;
			}
			while( yp < N4_2 )
			{
				/* Real part arranged as a-bR, Imag part arranged as -c-dR */
				f[yp++] = -(window[wp1] * in[xp1 - N2]) + (window[wp2] * in[xp2]);
				f[yp++] =  (window[wp2] * in[xp1])      + (window[wp1] * in[xp2 + N2]);
				xp1 += 2;
				xp2 -= 2;
				wp1 += 2;
				wp2 -= 2;
			}
		}
		final Jkiss_fft_cpx f2[] = new Jkiss_fft_cpx[N4];
		/* Pre-rotation */
		{
			int yp = 0;// f[ yp ]
			// final float[] t = &trig[0];
			for( int i = 0, t = trig_offset; i < N4; i++, t++ )
			{
				final Jkiss_fft_cpx yc = new Jkiss_fft_cpx();

				final float t0 = this_trig[t];
				final float t1 = this_trig[t + N4];
				final float re = f[yp++];
				final float im = f[yp++];
				final float yr = (re * t0) - (im * t1);
				final float yi = (im * t0) + (re * t1);
				yc.r = yr;
				yc.i = yi;
				yc.r = scale * yc.r;
				yc.i = scale * yc.i;
				f2[ st.bitrev[i] ] = yc;
			}
		}

		/* N/4 complex FFT, does not downscale anymore */
		st.opus_fft_impl( f2 );

		/* Post-rotate */
		{
			/* Temp pointers to make it really clear to the compiler what we're doing */
			// int fp = 0;// f2[ fp ], fp = i
			int yp1 = outoffset;// out[ yp1 ]
			int yp2 = outoffset + stride * (N2 - 1);// out[ yp2 ]
			//const kiss_twiddle_scalar *t = &trig[0];
			stride <<= 1;// java
			/* Temp pointers to make it really clear to the compiler what we're doing */
			for( int i = 0, t = trig_offset; i < N4; i++, t++ )
			{
				final Jkiss_fft_cpx f2_fp = f2[ i /* fp */ ];// java
				final float t1 = this_trig[t + N4];// java
				final float t2 = this_trig[t];// java
				final float yr = (f2_fp.i * t1) - (f2_fp.r * t2);
				final float yi = (f2_fp.r * t1) + (f2_fp.i * t2);
				out[ yp1 ] = yr;
				out[ yp2 ] = yi;
				// fp++;// same as i
				yp1 += stride;
				yp2 -= stride;
			}
		}
		// RESTORE_STACK;
	}
// #endif /* OVERRIDE_clt_mdct_forward */

// #ifndef OVERRIDE_clt_mdct_backward
	/** Compute a backward MDCT (no scaling) and performs weighted overlap-add
    (scales implicitly by 1/2) */
	//private static final void clt_mdct_backward_c(final Jmdct_lookup l, final float[] in, final float[] out,
	//		final float[] window, final int overlap, final int shift, final int stride)//, final int arch)
	// java renamed
	final void clt_mdct_backward(
			final float[] in, final int inoffset,// java
			final float[] out, final int outoffset,// java
			final float[] window, int overlap, final int shift, int stride)
	{
		//(void) arch;
		int N = this.n;
		int trig_offset = 0;// l.trig[trig]// java renamed
		for( int i = 0; i < shift; i++ )
		{
			N >>= 1;
			trig_offset += N;
		}
		final int N2 = N >> 1;
		final int N4 = N >> 2;

		final int overlap2 = overlap >>> 1;// java
		final float[] this_trig = this.trig;// java
		/* Pre-rotate */
		{
			/* Temp pointers to make it really clear to the compiler what we're doing */
			int xp1 = inoffset;// in[xp1]
			int xp2 = inoffset + stride * (N2 - 1);// in[xp2]
			final int yp = outoffset + overlap2;// out[yp]
			//int t = trig;// l.trig[t] &trig[0];
			final short[] bitrev = this.kfft[shift].bitrev;
			stride <<= 1;// java
			for( int i = 0, t = trig_offset; i < N4; i++, t++ )
			{
				final float t1 = this_trig[t];// java
				final float t2 = this_trig[t + N4];// java
				final float yr = (in[xp2] * t1) + (in[xp1] * t2);
				final float yi = (in[xp1] * t1) - (in[xp2] * t2);
				/* We swap real and imag because we use an FFT instead of an IFFT. */
				int rev = yp + (bitrev[i] << 1);// java
				out[rev++] = yi;
				out[rev] = yr;
				/* Storing the pre-rotation directly in the bitrev order. */
				xp1 += stride;
				xp2 -= stride;
			}
		}
		// java: to save cpu time, use special version kiss_fft. another way is making new array: kiss_fft_cpx <-> float dim
		this.kfft[shift].opus_fft_impl( out, outoffset + overlap2 );

		/* Post-rotate and de-shuffle from both ends of the buffer at once to make
		   it in-place. */
		{
			int yp0 = outoffset + overlap2;// out[ yp0 ]
			int yp1 = yp0 + N2 - 2;// out[ yp1 ]
			// final int t = trig;// l.trig[ t ]
			/* Loop to (N4+1)>>1 to handle odd N4. When N4 is odd, the
			  middle pair will be computed twice. */
			// for( int i = 0, ie = (N4 + 1) >> 1; i < ie; i++ )
			for( int ti = trig_offset, ie = ti + ((N4 + 1) >> 1), tn = trig_offset - 1; ti < ie; ti++, tn-- )
			{
				/* We swap real and imag because we're using an FFT instead of an IFFT. */
				float re = out[ yp0 + 1 ];
				float im = out[ yp0 ];
				float t0 = this_trig[ ti ];
				float t1 = this_trig[ ti + N4 ];
				/* We'd scale up by 2 here, but instead it's done when mixing the windows */
				float yr = re * t0 + im * t1;
				float yi = re * t1 - im * t0;
				/* We swap real and imag because we're using an FFT instead of an IFFT. */
				re = out[ yp1 + 1 ];
				im = out[ yp1 ];
				out[ yp0 ] = yr;
				out[ yp1 + 1 ] = yi;

				t0 = this_trig[ tn + N4 ];
				t1 = this_trig[ tn + N2 ];
				/* We'd scale up by 2 here, but instead it's done when mixing the windows */
				yr = re * t0 + im * t1;
				yi = re * t1 - im * t0;
				out[ yp1 ] = yr;
				out[ ++yp0 ] = yi;// yp0 + 1
				yp0++;// yp0++;// yp0 += 2;
				yp1--; yp1--;// yp1 -= 2;
			}
		}

		/* Mirror on both sides for TDAC */
		{
			int xp1 = outoffset + overlap - 1;// out[xp1]
			int yp1 = outoffset;// out[yp1]
			int wp1 = 0;// window[wp1]
			int wp2 = overlap - 1;// window[wp2]

			overlap >>>= 1;// java
			for( int i = 0; i < overlap; i++ )
			{
				final float x1 = out[xp1];
				final float x2 = out[yp1];
				out[yp1++] = (window[wp2] * x2) - (window[wp1] * x1);
				out[xp1--] = (window[wp1] * x2) + (window[wp2] * x1);
				wp1++;
				wp2--;
			}
		}
	}
// #endif /* OVERRIDE_clt_mdct_backward */
	// end mdct.c
}
