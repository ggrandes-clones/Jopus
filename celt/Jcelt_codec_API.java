package celt;

/**
 * java: collection of methods are used by the celt decoder and encoder
 */
public abstract class Jcelt_codec_API {
	// start arch.h
	// end arch.h

	/* TF change table. Positive values mean better frequency resolution (longer
	   effective window), whereas negative values mean better time resolution
	   (shorter effective window). The second index is computed as:
	   4*isTransient + 2*tf_select + per_band_flag */
	static final byte tf_select_table[/* 4 */][/* 8 */] = {
		/*isTransient=0     isTransient=1 */
		{0, -1, 0, -1,    0,-1, 0,-1}, /* 2.5 ms */
		{0, -1, 0, -2,    1, 0, 1,-1}, /* 5 ms */
		{0, -2, 0, -3,    2, 0, 1,-1}, /* 10 ms */
		{0, -2, 0, -3,    3, 0, 1,-1}, /* 20 ms */
	};
	// start celt_lpc.c
	/**
	 *
	 * @param lpc out: [0...p-1] LPC coefficients
	 * @param loffset java an offset for the lpc
	 * @param ac in:  [0...p] autocorrelation values
	 * @param p
	 */
	static final void _celt_lpc(final float[] lpc, final int loffset,// java
			final float[] ac, final int p)
	{
		float error = ac[0];
/* #ifdef FIXED_POINT
		opus_val32 lpc[LPC_ORDER];
#else */
		//final float[] lpc = _lpc;
//#endif

		for( int i = loffset, end = p + loffset; i < end; i++ ) {
			lpc[i] = 0;
		}
		if( ac[0] != 0 )
		{
			for( int i = 0; i < p; i++ ) {
				/* Sum up this iteration's reflection coefficient */
				float rr = 0;
				for( int j = 0; j < i; j++ ) {
					rr += lpc[loffset + j] * ac[i - j];
				}
				rr += ac[i + 1];
				final float r = -(rr / error);
				/*  Update LPC coefficients and total error */
				int oi = loffset + i;// java
				lpc[oi--] = r;
				for( int j = 0, end = (i + 1) >> 1; j < end; j++ )
				{
					final int oj = loffset + j;// java
					final float tmp1 = lpc[oj    ];
					final float tmp2 = lpc[oi - j];
					lpc[oj    ] = tmp1 + r * tmp2;
					lpc[oi - j] = tmp2 + r * tmp1;
				}

				error -= r * r * error;
				/* Bail out once we get 30 dB gain */
/* #ifdef FIXED_POINT
				if( error < SHR32( ac[0], 10 ) )
					break;
#else */
				if( error < .001f * ac[0] ) {
					break;
//#endif
				}
			}
		}
/* #ifdef FIXED_POINT
		for( i = 0; i < p; i++ )
			_lpc[i] = ROUND16( lpc[i], 16 );
#endif */
	}

	/**
	 *
	 * @param x in: [0...n-1] samples x
	 * @param xoffset java offset for the x
	 * @param ac out: [0...lag-1] ac values
	 * @param window
	 * @param overlap
	 * @param lag
	 * @param n
	 * @param arch
	 * @return
	 */
	static final int _celt_autocorr(final float[] x, int xoffset,// java
		final float[] ac, final float[] window,
		final int overlap, final int lag, int n)//, final int arch)
	{
		int fastN = n - lag;
		float[] dim;// java
		final int xptr;// java dim[ xptr ]
		// SAVE_STACK;
		final float[] xx = new float[n];
		// celt_assert( n > 0 );
		// celt_assert( overlap >= 0 );
		if( overlap == 0 )
		{
			dim = x;// xptr = x;
			xptr = xoffset;// java
		} else {
			for( int i = 0, xi = xoffset; i < n; ) {// FIXME why need 2 loops?
				xx[i++] = x[xi++];
			}
			for( int i = 0, k = n - 1, xk = xoffset + k; i < overlap; i++, k-- )
			{
				xx[i] = x[xoffset++] * window[i];
				xx[k] = x[xk--] * window[i];
			}
			dim = xx;// java
			xptr = 0;// xx;
		}
		final int shift = 0;
/* #ifdef FIXED_POINT
		{
			opus_val32 ac0;
			ac0 = 1 + (n << 7);
			if( n & 1 ) ac0 += SHR32( MULT16_16( xptr[0], xptr[0] ), 9 );
			for( i = (n & 1); i < n; i += 2 )
			{
				ac0 += SHR32( MULT16_16( xptr[i], xptr[i] ), 9 );
				ac0 += SHR32( MULT16_16( xptr[i + 1], xptr[i + 1] ), 9 );
			}

			shift = celt_ilog2( ac0 ) - 30 + 10;
			shift = shift / 2;
			if( shift > 0 )
			{
				for( i = 0; i < n; i++ )
					xx[i] = PSHR32( xptr[i], shift );
				xptr = xx;
			} else
				shift = 0;
		}
#endif */
		celt_pitch_xcorr( dim, xptr, dim, xptr, ac, fastN, lag + 1 );//, arch );
		fastN += xptr;// java
		n += xptr;
		for( int k = 0; k <= lag; k++ )
		{
			float d = 0;
			for( int i = k + fastN; i < n; i++ ) {
				d += dim[i] * dim[i - k];
			}
			ac[k] += d;
		}
/* #ifdef FIXED_POINT
		shift = 2 * shift;
		if( shift <= 0 )
			ac[0] += SHL32( (opus_int32 )1, -shift );
		if( ac[0] < 268435456 )
		{
			int shift2 = 29 - EC_ILOG( ac[0] );
			for( i = 0; i <= lag; i++ )
				ac[i] = SHL32( ac[i], shift2 );
			shift -= shift2;
		} else if( ac[0] >= 536870912 )
		{
			int shift2 = 1;
			if( ac[0] >= 1073741824 )
				shift2++;
			for( i = 0; i <= lag; i++ )
				ac[i] = SHR32( ac[i], shift2 );
			shift += shift2;
		}
#endif */

		// RESTORE_STACK;
		return shift;
	}
	// end celt_lpc.c

	// start pitch.h

	/* OPT: This is the kernel you really want to optimize. It gets used a lot
	by the prefilter and by the PLC. */
	// static final void xcorr_kernel_c(final float[] x, final float[] y, float sum[4], int len)
	// java renamed
	static final void xcorr_kernel(final float[] x, int xoffset,// java
			final float[] y, int yoffset,// java
			final float sum[/* 4 */], final int len)
	{
		// celt_assert( len >= 3 );
		float y_3 = 0; /* gcc doesn't realize that y_3 can't be used uninitialized */
		float y_0 = y[yoffset++];
		float y_1 = y[yoffset++];
		float y_2 = y[yoffset++];
		final int end = len - 3;// java
		float sum_0 = sum[0];// java
		float sum_1 = sum[1];// java
		float sum_2 = sum[2];// java
		float sum_3 = sum[3];// java
		int j;
		for( j = 0; j < end; j += 4 )
		{
			float tmp;
			tmp = x[xoffset++];
			y_3 = y[yoffset++];
			sum_0 += tmp * y_0;
			sum_1 += tmp * y_1;
			sum_2 += tmp * y_2;
			sum_3 += tmp * y_3;
			tmp = x[xoffset++];
			y_0 = y[yoffset++];
			sum_0 += tmp * y_1;
			sum_1 += tmp * y_2;
			sum_2 += tmp * y_3;
			sum_3 += tmp * y_0;
			tmp = x[xoffset++];
			y_1 = y[yoffset++];
			sum_0 += tmp * y_2;
			sum_1 += tmp * y_3;
			sum_2 += tmp * y_0;
			sum_3 += tmp * y_1;
			tmp = x[xoffset++];
			y_2 = y[yoffset++];
			sum_0 += tmp * y_3;
			sum_1 += tmp * y_0;
			sum_2 += tmp * y_1;
			sum_3 += tmp * y_2;
		}
		if( j++ < len )
		{
			final float tmp = x[xoffset++];
			y_3 = y[yoffset++];
			sum_0 += tmp * y_0;
			sum_1 += tmp * y_1;
			sum_2 += tmp * y_2;
			sum_3 += tmp * y_3;
		}
		if( j++ < len )
		{
			final float tmp = x[xoffset++];
			y_0 = y[yoffset++];
			sum_0 += tmp * y_1;
			sum_1 += tmp * y_2;
			sum_2 += tmp * y_3;
			sum_3 += tmp * y_0;
		}
		if( j < len )
		{
			final float tmp = x[xoffset++];
			y_1 = y[yoffset++];
			sum_0 += tmp * y_2;
			sum_1 += tmp * y_3;
			sum_2 += tmp * y_0;
			sum_3 += tmp * y_1;
		}
		sum[0] = sum_0;// java
		sum[1] = sum_1;// java
		sum[2] = sum_2;// java
		sum[3] = sum_3;// java
	}

/* #ifndef OVERRIDE_XCORR_KERNEL
#define xcorr_kernel(x, y, sum, len, arch) \
				((void)(arch),xcorr_kernel_c(x, y, sum, len))
#endif */ /* OVERRIDE_XCORR_KERNEL */
	// end pitch.h
	// start pitch.c

/* Pure C implementation. */
/* #ifdef FIXED_POINT
	opus_val32
#else
	void
#endif */
	/**
	 *
	 * @param _x
	 * @param xoffset
	 * @param _y
	 * @param yoffset
	 * @param xcorr
	 * @param len
	 * @param max_pitch
	 */
	public static final void celt_pitch_xcorr(final float[] _x, final int xoffset,// java
			final float[] _y, final int yoffset,// java
			final float[] xcorr, final int len, final int max_pitch)//, final int arch)
	{

/* #if 0
		// This is a simple version of the pitch correlation that should work
		// well on DSPs like Blackfin and TI C5x/C6x
		int i, j;
#ifdef FIXED_POINT
		opus_val32 maxcorr = 1;
#endif
#if !defined(OVERRIDE_PITCH_XCORR)
		(void)arch;
#endif
		for( i = 0; i < max_pitch; i++ )
		{
			opus_val32 sum = 0;
			for( j = 0; j < len; j++ )
				sum = MAC16_16( sum, _x[j], _y[i + j] );
			xcorr[i] = sum;
#ifdef FIXED_POINT
			maxcorr = MAX32( maxcorr, sum );
#endif
		}
#ifdef FIXED_POINT
		return maxcorr;
#endif */

// #else /* Unrolled version of the pitch correlation -- runs faster on x86 and ARM */
		/*The EDSP version requires that max_pitch is at least 1, and that _x is
		32-bit aligned.
		Since it's hard to put asserts in assembly, put them here.*/
/* #ifdef FIXED_POINT
		opus_val32 maxcorr = 1;
#endif */
		// celt_assert( max_pitch > 0 );
		// celt_sig_assert( (((unsigned char *)_x - (unsigned char *)NULL) & 3) == 0 );
		final float sump[/* 4 */] = new float[ 4 ];// { 0, 0, 0, 0 };// java moved up and renamed
		int i = 0;
		for( final int ie = max_pitch - 3; i < ie; )
		{
			sump[0] = 0; sump[1] = 0; sump[2] = 0; sump[3] = 0;// java
			xcorr_kernel( _x, xoffset, _y, yoffset + i, sump, len );// , arch );
			xcorr[i++] = sump[0];
			xcorr[i++] = sump[1];
			xcorr[i++] = sump[2];
			xcorr[i++] = sump[3];
/* #ifdef FIXED_POINT
			sum[0] = MAX32(sum[0], sum[1]);
			sum[2] = MAX32(sum[2], sum[3]);
			sum[0] = MAX32(sum[0], sum[2]);
			maxcorr = MAX32(maxcorr, sum[0]);
#endif */
		}
		/* In case max_pitch isn't a multiple of 4, do non-unrolled version. */
		for( ; i < max_pitch; i++ )
		{
			final float sum = celt_inner_prod( _x, xoffset, _y, yoffset + i, len );//, arch );
			xcorr[i] = sum;
/* #ifdef FIXED_POINT
			maxcorr = MAX32(maxcorr, sum);
#endif */
		}
/* #ifdef FIXED_POINT
		return maxcorr;
#endif */
// #endif
	}
	// static final void dual_inner_prod_c(final float[] x, final float[] y01, final float[] y02, int N, float[] xy1, float[] xy2)
	/**
	 * java changed: return xyret[0] = xy1, xyret[1] = xy2;
	 *
	 * @param x
	 * @param xoffset
	 * @param y01
	 * @param yoffset01
	 * @param y02
	 * @param yoffset02
	 * @param N
	 * @param xyret
	 */
	static final void dual_inner_prod(final float[] x, int xoffset,// java
			final float[] y01, int yoffset01,// java
			final float[] y02, int yoffset02,// java
			int N, final float[] xyret)// final float[] xy1, final float[] xy2)
	{// java renamed
		float xy01 = 0;
		float xy02 = 0;
		for( N += xoffset; xoffset < N; xoffset++ )
		{
			xy01 += x[xoffset] * y01[yoffset01++];
			xy02 += x[xoffset] * y02[yoffset02++];
		}
		xyret[0] = xy01;// xy1[0] = xy01;
		xyret[1] = xy02;// xy2[0] = xy02;
	}

/* #ifndef OVERRIDE_DUAL_INNER_PROD
# define dual_inner_prod(x, y01, y02, N, xy1, xy2, arch) \
			((void)(arch),dual_inner_prod_c(x, y01, y02, N, xy1, xy2))
#endif */

	/*We make sure a C version is always available for cases where the overhead of
	vectorization and passing around an arch flag aren't worth it.*/
	/* static final float celt_inner_prod_c(final float[] x, int xoffset, final float[] y, int yoffset, int N)
	{// java xoffset and yoffset are added
		float xy = 0;
		for( N += xoffset; xoffset < N; xoffset++ ) {
			xy += x[xoffset] * y[yoffset++];
		}
		return xy;
	} */
	/**
	 *
	 * @param x
	 * @param xoffset
	 * @param y
	 * @param yoffset
	 * @param N
	 * @return corr
	 */
	public static final float celt_inner_prod(final float[] x, int xoffset, final float[] y, int yoffset, int N)// java copied
	{// java xoffset and yoffset is added
		float xy = 0;
		for( N += xoffset; xoffset < N; ) {
			xy += x[xoffset++] * y[yoffset++];
		}
		return xy;
	}

/* #if !defined(OVERRIDE_CELT_INNER_PROD)
# define celt_inner_prod(x, y, N, arch) \
			((void)(arch),celt_inner_prod_c(x, y, N))
#endif */

	private static final void celt_fir5(final float[] x, final float[] num, final int N)
	{
		final float num0 = num[0];
		final float num1 = num[1];
		final float num2 = num[2];
		final float num3 = num[3];
		final float num4 = num[4];
		float mem0 = 0;
		float mem1 = 0;
		float mem2 = 0;
		float mem3 = 0;
		float mem4 = 0;
		for( int i = 0; i < N; i++ )
		{
			float sum = x[i];
			sum += num0 * mem0;
			sum += num1 * mem1;
			sum += num2 * mem2;
			sum += num3 * mem3;
			sum += num4 * mem4;
			mem4 = mem3;
			mem3 = mem2;
			mem2 = mem1;
			mem1 = mem0;
			mem0 = x[i];
			x[i] = sum;
		}
	}

	static final void pitch_downsample(final float xd[], final int[] xoffset,// java
			final float[] x_lp, final int len, final int C)//, final int arch)
	{
/* #ifdef FIXED_POINT
		int shift;
		opus_val32 maxabs = celt_maxabs32( x[0], len );
		if( C == 2 )
		{
			opus_val32 maxabs_1 = celt_maxabs32( x[1], len );
			maxabs = MAX32( maxabs, maxabs_1 );
		}
		if( maxabs < 1 )
			maxabs = 1;
		shift = celt_ilog2( maxabs ) - 10;
		if( shift < 0 )
			shift=0;
		if( C == 2 )
			shift++;
#endif */
		int xo = xoffset[0];// java
		for( int i = 1, end = len >> 1; i < end; i++ ) {
			// final int i2 = i << 1;// java
			// x_lp[i] = .5f * (.5f * (x[0][i2 - 1] + x[0][i2 + 1]) + x[0][i2]);
			final int i2 = (i << 1) + xo;// java
			x_lp[i] = .5f * (.5f * (xd[i2 - 1] + xd[i2 + 1]) + xd[i2]);
		}
		// x_lp[0] = .5f * ( .5f * x[0][1] + x[0][0] );
		x_lp[0] = .5f * ( .5f * xd[xo + 1] + xd[xo] );
		if( C == 2 )
		{
			xo = xoffset[1];
			for( int i = 1, end = len >> 1; i < end; i++ ) {
				// final int i2 = i << 1;// java
				// x_lp[i] += .5f * ( .5f * (x[1][i2 - 1] + x[1][i2 + 1]) + x[1][i2] );
				final int i2 = (i << 1) + xo;// java
				x_lp[i] += .5f * (.5f * (xd[i2 - 1] + xd[i2 + 1]) + xd[i2] );
			}
			// x_lp[0] += .5f * (.5f * x[1][1] + x[1][0] );
			x_lp[0] += .5f * (.5f * xd[xo + 1] + xd[xo] );
		}

		final float ac[] = new float[5];
		_celt_autocorr( x_lp, 0, ac, null, 0, 4, len >> 1 );// , arch );

		/* Noise floor -40 dB */
/* #ifdef FIXED_POINT
		ac[0] += SHR32(ac[0], 13);
#else */
		ac[0] *= 1.0001f;
// #endif
		/* Lag windowing */
		for( int i = 1; i <= 4; i++ )
		{
			/*ac[i] *= exp(-.5*(2*M_PI*.002*i)*(2*M_PI*.002*i));*/
/* #ifdef FIXED_POINT
			ac[i] -= MULT16_32_Q15( 2 * i * i, ac[i] );
#else */
			ac[i] -= ac[i] * (.008f * i) * (.008f * i);
// #endif
		}

		final float lpc[] = new float[5];//[4];// java: using single array for both operations
		_celt_lpc( lpc, 0, ac, 4 );
		float tmp = Jfloat_cast.Q15ONE;
		for( int i = 0; i < 4; i++ )
		{
			tmp = .9f * tmp;
			lpc[i] = lpc[i] * tmp;
		}
		/* Add a zero */
		final float c1 = .8f;
		/* final float lpc2[] = new float[5];// FIXME why need two arrays, lpc and lpc2?
		lpc2[0] = lpc[0] + .8f;
		lpc2[1] = lpc[1] + c1 * lpc[0];
		lpc2[2] = lpc[2] + c1 * lpc[1];
		lpc2[3] = lpc[3] + c1 * lpc[2];
		lpc2[4] = c1 * lpc[3];*/
		lpc[4] = c1 * lpc[3];
		lpc[3] = lpc[3] + c1 * lpc[2];
		lpc[2] = lpc[2] + c1 * lpc[1];
		lpc[1] = lpc[1] + c1 * lpc[0];
		lpc[0] = lpc[0] + .8f;// FIXME why not c1?
		// celt_fir5( x_lp, lpc2, x_lp, len >> 1, mem );
		celt_fir5( x_lp, lpc, len >> 1 );
	}

	private static final void find_best_pitch(final float[] xcorr, final float[] y, final int len,
			final int max_pitch, final int[] best_pitch
/* #ifdef FIXED_POINT
			, int yshift, opus_val32 maxcorr
#endif */
			)
	{
		// final float best_num[] = { -1, -1 };
		// final float best_den[] = { 0, 0 };
/* #ifdef FIXED_POINT
		int xshift;

		xshift = celt_ilog2( maxcorr ) - 14;
#endif */

		// best_num[0] = -1;
		// best_num[1] = -1;
		// best_den[0] = 0;
		// best_den[1] = 0;
		float best_num_0 = -1;// java changed
		float best_num_1 = -1;
		float best_den_0 = 0;
		float best_den_1 = 0;
		best_pitch[0] = 0;
		best_pitch[1] = 1;
		float Syy = 1f;
		for( int j = 0; j < len; j++ ) {
			final float v = y[j];// java
			Syy += v * v;
		}
		for( int i = 0; i < max_pitch; i++ )
		{
			if( xcorr[i] > 0 )
			{
				float xcorr16 = xcorr[i];
// #ifndef FIXED_POINT
				// Considering the range of xcorr16, this should avoid both underflows
				// and overflows (inf) when squaring xcorr16
				xcorr16 *= 1e-12f;
// #endif
				final float num = xcorr16 * xcorr16;
				if( num * best_den_1 > best_num_1 * Syy )
				{
					if( num * best_den_0 > best_num_0 * Syy )
					{
						best_num_1 = best_num_0;
						best_den_1 = best_den_0;
						best_pitch[1] = best_pitch[0];
						best_num_0 = num;
						best_den_0 = Syy;
						best_pitch[0] = i;
					} else {
						best_num_1 = num;
						best_den_1 = Syy;
						best_pitch[1] = i;
					}
				}
			}
			final float v1 = y[i + len];// java
			final float v2 = y[i];// java
			Syy += v1 * v1 -  v2 * v2;
			Syy = 1 >= Syy ? 1 : Syy;
		}
	}
	/**
	 * java changed: pitch moved from parameters to return
	 *
	 * @param x_lp
	 * @param xoffset
	 * @param y
	 * @param len
	 * @param max_pitch
	 * @return pitch
	 */
	static final int pitch_search(final float[] x_lp, final int xoffset,// java
			final float[] y, final int len, int max_pitch)//, final int[] pitch, final int arch)
	{
/* #ifdef FIXED_POINT
		opus_val32 maxcorr;
		opus_val32 xmax, ymax;
		int shift = 0;
#endif */

		// SAVE_STACK;

		// celt_assert( len > 0 );
		// celt_assert( max_pitch > 0 );
		final int lag = len + max_pitch;

		/* Downsample by 2 again */
		int i = len >> 2;// java
		final float[] x_lp4 = new float[i];
		for( int j = 0, k = xoffset; j < i; j++, k += 2 ) {
			x_lp4[j] = x_lp[ k ];
		}
		i = lag >> 2;// java
		final float[] y_lp4 = new float[i];
		for( int j = 0; j < i; j++ ) {
			y_lp4[j] = y[j << 1];
		}

/* #ifdef FIXED_POINT
		xmax = celt_maxabs16( x_lp4, len >> 2 );
		ymax = celt_maxabs16( y_lp4, lag >> 2 );
		shift = celt_ilog2( MAX32(1, MAX32(xmax, ymax)) ) - 11;
		if( shift > 0 )
		{
			for( j = 0; j < len >> 2; j++ )
				x_lp4[j] = SHR16(x_lp4[j], shift);
			for( j = 0; j < lag >> 2; j++ )
				y_lp4[j] = SHR16(y_lp4[j], shift);
			// Use double the shift for a MAC
			shift *= 2;
		} else {
			shift = 0;
		}
#endif */

		/* Coarse search with 4x decimation */

/* #ifdef FIXED_POINT
		maxcorr =
#endif */
		max_pitch >>= 1;// java
		final float[] xcorr = new float[ max_pitch ];
		celt_pitch_xcorr( x_lp4, 0, y_lp4, 0, xcorr, len >> 2, max_pitch >> 1 );// , arch );

		final int best_pitch[/* 2 */] = { 0, 0 };
		find_best_pitch( xcorr, y_lp4, len >> 2, max_pitch >> 1, best_pitch
/* #ifdef FIXED_POINT
						, 0, maxcorr
#endif */
					);

		/* Finer search with 2x decimation */
/* #ifdef FIXED_POINT
		maxcorr = 1;
#endif */
		for( i = 0; i < max_pitch; i++ )
		{
			xcorr[i] = 0;
			if( Math.abs( i - (best_pitch[0] << 1) ) > 2 && Math.abs( i - (best_pitch[1] << 1) ) > 2 ) {
				continue;
			}
/* #ifdef FIXED_POINT
			sum = 0;
			for( j = 0; j < len >> 1; j++ )
				sum += SHR32(MULT16_16(x_lp[j], y[i+j]), shift);
#else */
			final float sum = celt_inner_prod( x_lp, xoffset, y, i, len >> 1 );// arch);
// #endif
			xcorr[i] = -1 > sum ? -1 : sum;
/* #ifdef FIXED_POINT
			maxcorr = MAX32( maxcorr, sum );
#endif */
		}
		find_best_pitch( xcorr, y, len >> 1, max_pitch, best_pitch
/* #ifdef FIXED_POINT
						, shift + 1, maxcorr
#endif */
				);

		/* Refine by pseudo-interpolation */
		int offset;
		if( best_pitch[0] > 0 && best_pitch[0] < max_pitch - 1 )
		{
			final float a = xcorr[best_pitch[0] - 1];
			final float b = xcorr[best_pitch[0]];
			final float c = xcorr[best_pitch[0] + 1];
			if( (c - a) > .7f * (b - a) ) {
				offset = 1;
			} else if( (a - c) > .7f * (b - c) ) {
				offset = -1;
			} else {
				offset = 0;
			}
		} else {
			offset = 0;
		}
		// pitch[0] = (best_pitch[0] << 1) - offset;
		return (best_pitch[0] << 1) - offset;

		// RESTORE_STACK;
	}
	// end pitch.c
}
