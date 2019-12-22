package celt;

// bands.c

final class Jband_ctx extends Jcelt_codec_API {
	private static final class Jsplit_ctx {
		private boolean inv;
		private int imid;
		private int iside;
		private int delta;
		private int itheta;
		private int qalloc;
	}

	boolean encode;
	boolean resynth;
	JCELTMode m;
	int i;
	int intensity;
	int spread;
	int tf_change;
	Jec_ctx ec;
	int remaining_bits;
	float[] bandE;
	int seed;
	// int arch;// java don't need
	int theta_round;
	boolean disable_inv;
	boolean avoid_split_noise;
	//
	Jband_ctx() {
	}
	Jband_ctx(final Jband_ctx band) {
		copyFrom( band );
	}
	final void copyFrom(final Jband_ctx band) {// TODO java: replace by clone ?
		encode = band.encode;
		resynth = band.resynth;
		m = band.m;
		i = band.i;
		intensity = band.intensity;
		spread = band.spread;
		tf_change = band.tf_change;
		ec = band.ec;
		remaining_bits = band.remaining_bits;
		bandE = band.bandE;
		seed = band.seed;
		// arch = band.arch;// java don't need
		theta_round = band.theta_round;
		disable_inv = band.disable_inv;
		avoid_split_noise = band.avoid_split_noise;
	}
	//
	static final int celt_lcg_rand(final int seed)
	{
		return 1664525 * seed + 1013904223;
	}

	/** Multiplies two 16-bit fractional values. Bit-exactness of this macro is important */
	/* private static final int FRAC_MUL16(final int a, final int b) {// java: extracted inplace
		return ((16384 + a * b) >> 15);
	}*/
	/** This is a cos() approximation designed to be bit - exact on any platform. Bit exactness
	   with this approximation is important because it has an impact on the bit allocation */
	private static final int bitexact_cos(int x)
	{
		// final int tmp = (4096 + (x * x)) >> 13;
		//celt_sig_assert( tmp <= 32767 );
		// short x2 = (short)tmp;
		// x2 = (short)((32767 - x2) + FRAC_MUL16( x2, (-7651 + FRAC_MUL16( x2, (8277 + FRAC_MUL16( -626, x2 )) )) ));
		//celt_sig_assert( x2 <= 32766 );
		// return ++x2;
		x *= x;
		x += 4096;
		x >>= 13;
		int v = (8277 + ((16384 - 626 * x) >> 15));
		v = ((16384 + x * v) >> 15) - 7651;
		x = (32767 - x) + ((16384 + x * v) >> 15);
		return ++x;
	}

	private static int bitexact_log2tan( int isin, int icos )
	{
		final int lc = Jec_ctx.EC_ILOG( icos );
		final int ls = Jec_ctx.EC_ILOG( isin );
		icos <<= 15 - lc;
		isin <<= 15 - ls;
		// return (ls - lc) * (1 << 11)
		//		 + FRAC_MUL16( isin, FRAC_MUL16( isin, -2597 ) + 7932 )
		//		 - FRAC_MUL16( icos, FRAC_MUL16( icos, -2597 ) + 7932 );
		return (ls - lc) * (1 << 11)
				 + ((16384 + isin * (((16384 - 2597 * isin) >> 15) + 7932)) >> 15)
				 - ((16384 + icos * (((16384 - 2597 * icos) >> 15) + 7932)) >> 15);
	}

	private static final void stereo_split( final float[] X, int xoffset, final float[] Y, int yoffset, int N )
	{
		for( N += xoffset; xoffset < N; )
		{
			final float l = .70710678f * X[ xoffset ];
			final float r = .70710678f * Y[ yoffset ];
			X[ xoffset++ ] = l + r;
			Y[ yoffset++ ] = r - l;
		}
	}

	private static final void stereo_merge( final float[] X, int xoffset,// java
			final float[] Y, int yoffset,// java
			final float mid, int N )//, int arch )
	{
		//float xp = 0;// FIXME xp = 0 don't need
		//final float side = 0;// FIXME side = 0 don't need
/* #ifdef FIXED_POINT
		int kl, kr;
#endif */
		// final float t;

		final float[] xyret = new float[2];// java to get data back
		/* Compute the norm of X + Y and X - Y as |X|^2 + |Y|^2  + / -  sum( xy ) */
		dual_inner_prod( Y, yoffset, X, xoffset, Y, yoffset, N, xyret );// &xp, &side );//, arch );
		float xp = xyret[0];
		final float side = xyret[1];
		/* Compensating for the mid normalization */
		xp = mid * xp * 2f;// java: added 2f *
		/* mid and side are in Q15, not Q14 like X and Y */
		float mid2 = mid;
		mid2 *= mid2;// java
		mid2 += side;// java
		final float El = mid2 - xp;
		final float Er = mid2 + xp;
		if( Er < 6e-4f || El < 6e-4f )
		{
			System.arraycopy( X, xoffset, Y, yoffset, N );
			return;
		}

/* #ifdef FIXED_POINT
		kl = celt_ilog2( El ) >> 1;
		kr = celt_ilog2( Er ) >> 1;
#endif */
		//t = El;
		final float lgain = 1f / (float)Math.sqrt( El );// 1f / (float)Math.sqrt( t );
		//t = Er;
		final float rgain = 1f / (float)Math.sqrt( Er );// rgain = 1f / (float)Math.sqrt( t );

/* #ifdef FIXED_POINT
		if( kl < 7 )
			kl = 7;
		if( kr < 7 )
			kr = 7;
#endif */

		for( N += xoffset; xoffset < N; xoffset++ )
		{
			/* Apply mid scaling ( side is already scaled ) */
			final float l = mid * X[ xoffset ];
			final float r = Y[ yoffset ];
			X[ xoffset   ] = lgain * (l - r);
			Y[ yoffset++ ] = rgain * (l + r);
		}
	}

	/** Indexing table for converting from natural Hadamard to ordery Hadamard
	   This is essentially a bit - reversed Gray, on top of which we've added
	   an inversion of the order because we want the DC at the end rather than
	   the beginning. The lines are for N = 2, 4, 8, 16 */
	private static final int ordery_table[] = {
		1,  0,
		3,  0,  2,  1,
		7,  0,  4,  3,  6,  1,  5,  2,
		15,  0,  8,  7, 12,  3, 11,  4, 14,  1,  9,  6, 13,  2, 10,  5,
	};

	private static final void deinterleave_hadamard(final float[] X, final int xoffset,// java
			final int N0, final int stride, final boolean hadamard)
	{
		// SAVE_STACK;
		final int N = N0 * stride;
		final float[] tmp = new float[ N ];
		// celt_assert( stride > 0 );
		if( hadamard )
		{
			int ordery = stride - 2;// ordery_table[ ordery ]
			for( int i = 0; i < stride; i++, ordery++ )
			{
				for( int j = xoffset + i, k = ordery_table[ ordery ] * N0, ke = k + N0; k < ke; k++, j += stride ) {
					tmp[ k ] = X[ j ];
				}
			}
		} else {
			for( int i = 0; i < stride; i++ ) {
				for( int j = xoffset + i, k = i * N0, ke = k + N0; k < ke; k++, j += stride ) {
					tmp[ k ] = X[ j ];
				}
			}
		}
		System.arraycopy( tmp, 0, X, xoffset, N );
		// RESTORE_STACK;
	}

	private static final void interleave_hadamard(final float[] X, final int xoffset, final int N0, final int stride, final boolean hadamard)
	{// java xoffset is added
		// SAVE_STACK;
		final int N = N0 * stride;
		final float[] tmp = new float[N];
		if( hadamard )
		{
			final int ordery = stride - 2;
			for( int i = 0; i < stride; i++ ) {
				for( int j = i, k = xoffset + ordery_table[ordery + i] * N0, ke = k + N0; k < ke; k++, j += stride ) {
					tmp[ j ] = X[ k ];
				}
			}
		} else {
			for( int i = 0; i < stride; i++ ) {
				for( int j = i, k = xoffset + i * N0, ke = k + N0; k < ke; k++, j += stride ) {
					tmp[ j ] = X[ k ];
				}
			}
		}
		System.arraycopy( tmp, 0, X, xoffset, N );// OPUS_COPY( X, tmp, N );
		// RESTORE_STACK;
	}

	static final void haar1(final float[] X, int xoffset, int N0, final int stride )
	{
		N0 >>= 1;
		N0 *= stride;// java
		N0 <<= 1;// java
		N0 += xoffset;// java
		final int stride2 = stride << 1;// java
		for( final int ie = xoffset + stride; xoffset < ie; xoffset++ ) {
			for( int k1 = xoffset; k1 < N0; k1 += stride2 )
			{
				final int k2 = k1 + stride;
				final float tmp1 = .70710678f * X[ k1 ];
				final float tmp2 = .70710678f * X[ k2 ];
				X[ k1 ] = tmp1 + tmp2;
				X[ k2 ] = tmp1 - tmp2;
			}
		}
	}
	private static final short exp2_table8[/* 8 */]  =
		{ 16384, 17866, 19483, 21247, 23170, 25267, 27554, 30048 };
	private static final int compute_qn( final int N, final int b, final int offset, final int pulse_cap, final boolean stereo )
	{
		int N2 = (N << 1) - 1;
		if( stereo && N == 2 ) {
			N2--;
		}
		/* The upper limit ensures that in a stereo split with itheta == 16384, we'll
		   always have enough bits left over to code at least one pulse in the
		   side;  otherwise it would collapse, since it doesn't get folded. */
		int qb = (b + N2 * offset) / N2;
		int v = b - pulse_cap - (4 << Jec_ctx.BITRES);// java
		qb = v <= qb ? v : qb;

		v = 8 << Jec_ctx.BITRES;// java
		qb = v <= qb ? v : qb;

		// int qn;
		if( qb < (1 << Jec_ctx.BITRES >> 1) ) {
			// qn = 1;
			return 1;
		}
		// qn = exp2_table8[qb & 0x7] >> (14 - (qb >> Jec_ctx.BITRES));
		// qn = (qn + 1) >> 1 << 1;
		// celt_assert( qn <= 256 );
		// return qn;
		return ((exp2_table8[ qb & 0x7 ] >> (14 - (qb >> Jec_ctx.BITRES))) + 1) >> 1 << 1;
	}

	// start cwrs.c
// #ifdef CUSTOM_MODES

	/** Guaranteed to return a conservatively large estimate of the binary logarithm
	   with frac bits of fractional precision.
	  Tested for all possible 32 - bit inputs with frac = 4, where the maximum
	   overestimation is 0.06254243 bits. */
/*	int log2_frac( opus_uint32 val, int frac )
	{
		int l = EC_ILOG( val );
		if( val & (val - 1) ) {
			// This is ( val >> l - 16 ), but guaranteed to round up, even if adding a bias
			// before the shift would cause overflow ( e.g., for 0xFFFFxxxx ).
			// Doesn't work for val = 0, but that case fails the test above.
			if( l > 16 ) val = ((val - 1) >> (l - 16)) + 1;
			else val <<= 16 - l;
			l = (l - 1) << frac;
			// Note that we always need one iteration, since the rounding up above means
			// that we might need to adjust the integer part of the logarithm.
			do{
				int b;
				b = (int)(val >> 16);
				l += b << frac;
				val = (val + b) >> b;
				val = (val * val + 0x7FFF) >> 15;
			}
			while( frac-- > 0 );
			// If val is not exactly 0x8000, then we have to round up the remainder.
			return l + (val > 0x8000);
		}
		// Exact powers of two require no rounding.
		else return (l - 1) << frac;
	} */
// #endif

/*Although derived separately, the pulse vector coding scheme is equivalent to
   a Pyramid Vector Quantizer \cite{Fis86}.
  Some additional notes about an early version appear at
   https://people.xiph.org/~tterribe/notes/cwrs.html, but the codebook ordering
   and the definitions of some terms have evolved since that was written.

  The conversion from a pulse vector to an integer index ( encoding ) and back
   ( decoding ) is governed by two related functions, V( N,K ) and U( N,K ).

  V( N,K )  =  the number of combinations, with replacement, of N items, taken K
   at a time, when a sign bit is added to each item taken at least once ( i.e.,
   the number of N - dimensional unit pulse vectors with K pulses ).
  One way to compute this is via
    V( N,K )  =  K > 0 ? sum( k = 1...K,2**k*choose( N,k )*choose( K - 1,k - 1 ) ) : 1,
   where choose(  ) is the binomial function.
  A table of values for N<10 and K<10 looks like:
  V[10][10]  =  {
    {1,  0,   0,    0,    0,     0,     0,      0,      0,       0},
    {1,  2,   2,    2,    2,     2,     2,      2,      2,       2},
    {1,  4,   8,   12,   16,    20,    24,     28,     32,      36},
    {1,  6,  18,   38,   66,   102,   146,    198,    258,     326},
    {1,  8,  32,   88,  192,   360,   608,    952,   1408,    1992},
    {1, 10,  50,  170,  450,  1002,  1970,   3530,   5890,    9290},
    {1, 12,  72,  292,  912,  2364,  5336,  10836,  20256,   35436},
    {1, 14,  98,  462, 1666,  4942, 12642,  28814,  59906,  115598},
    {1, 16, 128,  688, 2816,  9424, 27008,  68464, 157184,  332688},
    {1, 18, 162,  978, 4482, 16722, 53154, 148626, 374274,  864146}
  };

  U( N,K )  =  the number of such combinations wherein N - 1 objects are taken at
   most K - 1 at a time.
  This is given by
    U( N,K )  =  sum( k = 0...K - 1,V( N - 1,k ) )
            =  K > 0 ? ( V( N - 1,K - 1 )  +  V( N,K - 1 ) )/2 : 0.
  The latter expression also makes clear that U( N,K ) is half the number of such
   combinations wherein the first object is taken at least once.
  Although it may not be clear from either of these definitions, U( N,K ) is the
   natural function to work with when enumerating the pulse vector codebooks,
   not V( N,K ).
  U( N,K ) is not well - defined for N = 0, but with the extension
    U( 0,K )  =  K > 0 ? 0 : 1,
   the function becomes symmetric: U( N,K )  =  U( K,N ), with a similar table:
  U[10][10]  =  {
    {1, 0,  0,   0,    0,    0,     0,     0,      0,      0},
    {0, 1,  1,   1,    1,    1,     1,     1,      1,      1},
    {0, 1,  3,   5,    7,    9,    11,    13,     15,     17},
    {0, 1,  5,  13,   25,   41,    61,    85,    113,    145},
    {0, 1,  7,  25,   63,  129,   231,   377,    575,    833},
    {0, 1,  9,  41,  129,  321,   681,  1289,   2241,   3649},
    {0, 1, 11,  61,  231,  681,  1683,  3653,   7183,  13073},
    {0, 1, 13,  85,  377, 1289,  3653,  8989,  19825,  40081},
    {0, 1, 15, 113,  575, 2241,  7183, 19825,  48639, 108545},
    {0, 1, 17, 145,  833, 3649, 13073, 40081, 108545, 265729}
  };

  With this extension, V( N,K ) may be written in terms of U( N,K ):
    V( N,K )  =  U( N,K )  +  U( N,K + 1 )
   for all N >= 0, K >= 0.
  Thus U( N,K + 1 ) represents the number of combinations where the first element
   is positive or zero, and U( N,K ) represents the number of combinations where
   it is negative.
  With a large enough table of U( N,K ) values, we could write O( N ) encoding
   and O( min( N*log( K ),N + K ) ) decoding routines, but such a table would be
   prohibitively large for small embedded devices ( K may be as large as 32767
   for small N, and N may be as large as 200 ).

  Both functions obey the same recurrence relation:
    V( N,K )  =  V( N - 1,K )  +  V( N,K - 1 )  +  V( N - 1,K - 1 ),
    U( N,K )  =  U( N - 1,K )  +  U( N,K - 1 )  +  U( N - 1,K - 1 ),
   for all N > 0, K > 0, with different initial conditions at N = 0 or K = 0.
  This allows us to construct a row of one of the tables above given the
   previous row or the next row.
  Thus we can derive O( NK ) encoding and decoding routines with O( K ) memory
   using only addition and subtraction.

  When encoding, we build up from the U( 2,K ) row and work our way forwards.
  When decoding, we need to start at the U( N,K ) row and work our way backwards,
   which requires a means of computing U( N,K ).
  U( N,K ) may be computed from two previous values with the same N:
    U( N,K )  =  ( ( 2*N - 1 )*U( N,K - 1 )  -  U( N,K - 2 ) )/( K - 1 )  +  U( N,K - 2 )
   for all N > 1, and since U( N,K ) is symmetric, a similar relation holds for two
   previous values with the same K:
    U( N,K > 1 )  =  ( ( 2*K - 1 )*U( N - 1,K )  -  U( N - 2,K ) )/( N - 1 )  +  U( N - 2,K )
   for all K > 1.
  This allows us to construct an arbitrary row of the U( N,K ) table by starting
   with the first two values, which are constants.
  This saves roughly 2/3 the work in our O( NK ) decoding routine, but costs O( K )
   multiplications.
  Similar relations can be derived for V( N,K ), but are not used here.

  For N > 0 and K > 0, U( N,K ) and V( N,K ) take on the form of an ( N - 1 ) - degree
   polynomial for fixed N.
  The first few are
    U( 1,K )  =  1,
    U( 2,K )  =  2*K - 1,
    U( 3,K )  =  ( 2*K - 2 )*K + 1,
    U( 4,K )  =  ( ( ( 4*K - 6 )*K + 8 )*K - 3 )/3,
    U( 5,K )  =  ( ( ( ( 2*K - 4 )*K + 10 )*K - 8 )*K + 3 )/3,
   and
    V( 1,K )  =  2,
    V( 2,K )  =  4*K,
    V( 3,K )  =  4*K*K + 2,
    V( 4,K )  =  8*( K*K + 2 )*K/3,
    V( 5,K )  =  ( ( 4*K*K + 20 )*K*K + 6 )/3,
   for all K > 0.
  This allows us to derive O( N ) encoding and O( N*log( K ) ) decoding routines for
   small N ( and indeed decoding is also O( N ) for N<3 ).

  @ARTICLE{Fis86,
    author = "Thomas R. Fischer",
    title = "A Pyramid Vector Quantizer",
    journal = "IEEE Transactions on Information Theory",
    volume = "IT - 32",
    number = 4,
    pages = "568--583",
    month = Jul,
    year = 1986
  }*/

// #if !defined( SMALL_FOOTPRINT )

/*U( N,K )  =  U( K,N ) : =  N > 0?K > 0?U( N - 1,K ) + U( N,K - 1 ) + U( N - 1,K - 1 ):0:K > 0?1:0*/
//# define CELT_PVQ_U( _n,_k ) ( CELT_PVQ_U_ROW[IMIN( _n,_k )][IMAX( _n,_k )] )
	/* private static final long CELT_PVQ_U(final int n, final int k ) {// java: extracted inplace
		//return CELT_PVQ_U_ROW[ n < k ? n : k ][ n > k ? n : k];
		return CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[n <= k ? n : k] + (n >= k ? n : k) ];
	} */
/*V( N,K ) : =  U( N,K ) + U( N,K + 1 )  =  the number of PVQ codewords for a band of size N
   with K pulses allocated to it.*/
//# define CELT_PVQ_V( _n,_k ) ( CELT_PVQ_U( _n,_k ) + CELT_PVQ_U( _n,( _k ) + 1 ) )
	/* private static final long CELT_PVQ_V(final int n, int k) {// java: extracted inplace
		// return (CELT_PVQ_U( n, k ) + CELT_PVQ_U( n, k + 1 ));
		long v = CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[n <= k ? n : k] + (n >= k ? n : k) ];
		k++;
		v += CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[n <= k ? n : k] + (n >= k ? n : k) ];
		return v;
	}*/
/*For each V( N,K ) supported, we will access element U( min( N,K + 1 ),max( N,K + 1 ) ).
  Thus, the number of entries in row I is the larger of the maximum number of
   pulses we will ever allocate for a given N = I ( K = 128, or however many fit in
   32 bits, whichever is smaller ), plus one, and the maximum N for which
   K = I - 1 pulses fit in 32 bits.
  The largest band size in an Opus Custom mode is 208.
  Otherwise, we can limit things to the set of N which can be achieved by
   splitting a band from a standard Opus mode: 176, 144, 96, 88, 72, 64, 48,
   44, 36, 32, 24, 22, 18, 16, 8, 4, 2 ).*/
/* #if defined( CUSTOM_MODES )
	static const opus_uint32 CELT_PVQ_U_DATA[1488] = {
#else */
	private static final long CELT_PVQ_U_DATA[/* 1272 */] = {// uint32
// #endif
		/*N = 0, K = 0...176:*/
		1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, */
// #endif
		/*N = 1, K = 1...176:*/
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, */
// #endif
		/*N = 2, K = 2...176:*/
		3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41,
		43, 45, 47, 49, 51, 53, 55, 57, 59, 61, 63, 65, 67, 69, 71, 73, 75, 77, 79,
		81, 83, 85, 87, 89, 91, 93, 95, 97, 99, 101, 103, 105, 107, 109, 111, 113,
		115, 117, 119, 121, 123, 125, 127, 129, 131, 133, 135, 137, 139, 141, 143,
		145, 147, 149, 151, 153, 155, 157, 159, 161, 163, 165, 167, 169, 171, 173,
		175, 177, 179, 181, 183, 185, 187, 189, 191, 193, 195, 197, 199, 201, 203,
		205, 207, 209, 211, 213, 215, 217, 219, 221, 223, 225, 227, 229, 231, 233,
		235, 237, 239, 241, 243, 245, 247, 249, 251, 253, 255, 257, 259, 261, 263,
		265, 267, 269, 271, 273, 275, 277, 279, 281, 283, 285, 287, 289, 291, 293,
		295, 297, 299, 301, 303, 305, 307, 309, 311, 313, 315, 317, 319, 321, 323,
		325, 327, 329, 331, 333, 335, 337, 339, 341, 343, 345, 347, 349, 351,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 353, 355, 357, 359, 361, 363, 365, 367, 369, 371, 373, 375, 377, 379, 381,
		383, 385, 387, 389, 391, 393, 395, 397, 399, 401, 403, 405, 407, 409, 411,
		413, 415, */
// #endif
		/*N = 3, K = 3...176:*/
		13, 25, 41, 61, 85, 113, 145, 181, 221, 265, 313, 365, 421, 481, 545, 613,
		685, 761, 841, 925, 1013, 1105, 1201, 1301, 1405, 1513, 1625, 1741, 1861,
		1985, 2113, 2245, 2381, 2521, 2665, 2813, 2965, 3121, 3281, 3445, 3613, 3785,
		3961, 4141, 4325, 4513, 4705, 4901, 5101, 5305, 5513, 5725, 5941, 6161, 6385,
		6613, 6845, 7081, 7321, 7565, 7813, 8065, 8321, 8581, 8845, 9113, 9385, 9661,
		9941, 10225, 10513, 10805, 11101, 11401, 11705, 12013, 12325, 12641, 12961,
		13285, 13613, 13945, 14281, 14621, 14965, 15313, 15665, 16021, 16381, 16745,
		17113, 17485, 17861, 18241, 18625, 19013, 19405, 19801, 20201, 20605, 21013,
		21425, 21841, 22261, 22685, 23113, 23545, 23981, 24421, 24865, 25313, 25765,
		26221, 26681, 27145, 27613, 28085, 28561, 29041, 29525, 30013, 30505, 31001,
		31501, 32005, 32513, 33025, 33541, 34061, 34585, 35113, 35645, 36181, 36721,
		37265, 37813, 38365, 38921, 39481, 40045, 40613, 41185, 41761, 42341, 42925,
		43513, 44105, 44701, 45301, 45905, 46513, 47125, 47741, 48361, 48985, 49613,
		50245, 50881, 51521, 52165, 52813, 53465, 54121, 54781, 55445, 56113, 56785,
		57461, 58141, 58825, 59513, 60205, 60901, 61601,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 62305, 63013, 63725, 64441, 65161, 65885, 66613, 67345, 68081, 68821, 69565,
		70313, 71065, 71821, 72581, 73345, 74113, 74885, 75661, 76441, 77225, 78013,
		78805, 79601, 80401, 81205, 82013, 82825, 83641, 84461, 85285, 86113, */
// #endif
		/*N = 4, K = 4...176:*/
		63, 129, 231, 377, 575, 833, 1159, 1561, 2047, 2625, 3303, 4089, 4991, 6017,
		7175, 8473, 9919, 11521, 13287, 15225, 17343, 19649, 22151, 24857, 27775,
		30913, 34279, 37881, 41727, 45825, 50183, 54809, 59711, 64897, 70375, 76153,
		82239, 88641, 95367, 102425, 109823, 117569, 125671, 134137, 142975, 152193,
		161799, 171801, 182207, 193025, 204263, 215929, 228031, 240577, 253575,
		267033, 280959, 295361, 310247, 325625, 341503, 357889, 374791, 392217,
		410175, 428673, 447719, 467321, 487487, 508225, 529543, 551449, 573951,
		597057, 620775, 645113, 670079, 695681, 721927, 748825, 776383, 804609,
		833511, 863097, 893375, 924353, 956039, 988441, 1021567, 1055425, 1090023,
		1125369, 1161471, 1198337, 1235975, 1274393, 1313599, 1353601, 1394407,
		1436025, 1478463, 1521729, 1565831, 1610777, 1656575, 1703233, 1750759,
		1799161, 1848447, 1898625, 1949703, 2001689, 2054591, 2108417, 2163175,
		2218873, 2275519, 2333121, 2391687, 2451225, 2511743, 2573249, 2635751,
		2699257, 2763775, 2829313, 2895879, 2963481, 3032127, 3101825, 3172583,
		3244409, 3317311, 3391297, 3466375, 3542553, 3619839, 3698241, 3777767,
		3858425, 3940223, 4023169, 4107271, 4192537, 4278975, 4366593, 4455399,
		4545401, 4636607, 4729025, 4822663, 4917529, 5013631, 5110977, 5209575,
		5309433, 5410559, 5512961, 5616647, 5721625, 5827903, 5935489, 6044391,
		6154617, 6266175, 6379073, 6493319, 6608921, 6725887, 6844225, 6963943,
		7085049, 7207551,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 7331457, 7456775, 7583513, 7711679, 7841281, 7972327, 8104825, 8238783,
		8374209, 8511111, 8649497, 8789375, 8930753, 9073639, 9218041, 9363967,
		9511425, 9660423, 9810969, 9963071, 10116737, 10271975, 10428793, 10587199,
		10747201, 10908807, 11072025, 11236863, 11403329, 11571431, 11741177,
		11912575, */
// #endif
		/*N = 5, K = 5...176:*/
		321, 681, 1289, 2241, 3649, 5641, 8361, 11969, 16641, 22569, 29961, 39041,
		50049, 63241, 78889, 97281, 118721, 143529, 172041, 204609, 241601, 283401,
		330409, 383041, 441729, 506921, 579081, 658689, 746241, 842249, 947241,
		1061761, 1186369, 1321641, 1468169, 1626561, 1797441, 1981449, 2179241,
		2391489, 2618881, 2862121, 3121929, 3399041, 3694209, 4008201, 4341801,
		4695809, 5071041, 5468329, 5888521, 6332481, 6801089, 7295241, 7815849,
		8363841, 8940161, 9545769, 10181641, 10848769, 11548161, 12280841, 13047849,
		13850241, 14689089, 15565481, 16480521, 17435329, 18431041, 19468809,
		20549801, 21675201, 22846209, 24064041, 25329929, 26645121, 28010881,
		29428489, 30899241, 32424449, 34005441, 35643561, 37340169, 39096641,
		40914369, 42794761, 44739241, 46749249, 48826241, 50971689, 53187081,
		55473921, 57833729, 60268041, 62778409, 65366401, 68033601, 70781609,
		73612041, 76526529, 79526721, 82614281, 85790889, 89058241, 92418049,
		95872041, 99421961, 103069569, 106816641, 110664969, 114616361, 118672641,
		122835649, 127107241, 131489289, 135983681, 140592321, 145317129, 150160041,
		155123009, 160208001, 165417001, 170752009, 176215041, 181808129, 187533321,
		193392681, 199388289, 205522241, 211796649, 218213641, 224775361, 231483969,
		238341641, 245350569, 252512961, 259831041, 267307049, 274943241, 282741889,
		290705281, 298835721, 307135529, 315607041, 324252609, 333074601, 342075401,
		351257409, 360623041, 370174729, 379914921, 389846081, 399970689, 410291241,
		420810249, 431530241, 442453761, 453583369, 464921641, 476471169, 488234561,
		500214441, 512413449, 524834241, 537479489, 550351881, 563454121, 576788929,
		590359041, 604167209, 618216201, 632508801,
// #if defined( CUSTOM_MODES )
		/*...208:*/
		/* 647047809, 661836041, 676876329, 692171521, 707724481, 723538089, 739615241,
		755958849, 772571841, 789457161, 806617769, 824056641, 841776769, 859781161,
		878072841, 896654849, 915530241, 934702089, 954173481, 973947521, 994027329,
		1014416041, 1035116809, 1056132801, 1077467201, 1099123209, 1121104041,
		1143412929, 1166053121, 1189027881, 1212340489, 1235994241, */
// #endif
		/*N = 6, K = 6...96:*/
		1683, 3653, 7183, 13073, 22363, 36365, 56695, 85305, 124515, 177045, 246047,
		335137, 448427, 590557, 766727, 982729, 1244979, 1560549, 1937199, 2383409,
		2908411, 3522221, 4235671, 5060441, 6009091, 7095093, 8332863, 9737793,
		11326283, 13115773, 15124775, 17372905, 19880915, 22670725, 25765455,
		29189457, 32968347, 37129037, 41699767, 46710137, 52191139, 58175189,
		64696159, 71789409, 79491819, 87841821, 96879431, 106646281, 117185651,
		128542501, 140763503, 153897073, 167993403, 183104493, 199284183, 216588185,
		235074115, 254801525, 275831935, 298228865, 322057867, 347386557, 374284647,
		402823977, 433078547, 465124549, 499040399, 534906769, 572806619, 612825229,
		655050231, 699571641, 746481891, 795875861, 847850911, 902506913, 959946283,
		1020274013, 1083597703, 1150027593, 1219676595, 1292660325, 1369097135,
		1449108145, 1532817275, 1620351277, 1711839767, 1807415257, 1907213187,
		2011371957, 2120032959,
// #if defined( CUSTOM_MODES )
		/*...109:*/
		/* 2233340609U, 2351442379U, 2474488829U, 2602633639U, 2736033641U, 2874848851U,
		3019242501U, 3169381071U, 3325434321U, 3487575323U, 3655980493U, 3830829623U,
		4012305913U, */
// #endif
		/*N = 7, K = 7...54*/
		8989, 19825, 40081, 75517, 134245, 227305, 369305, 579125, 880685, 1303777,
		1884961, 2668525, 3707509, 5064793, 6814249, 9041957, 11847485, 15345233,
		19665841, 24957661, 31388293, 39146185, 48442297, 59511829, 72616013,
		88043969, 106114625, 127178701, 151620757, 179861305, 212358985, 249612805,
		292164445, 340600625, 395555537, 457713341, 527810725, 606639529, 695049433,
		793950709, 904317037, 1027188385, 1163673953, 1314955181, 1482288821,
		1667010073, 1870535785, 2094367717,
// #if defined( CUSTOM_MODES )
		/*...60:*/
		// 2340095869U, 2609401873U, 2904062449U, 3225952925U, 3577050821U, 3959439497U,
// #endif
		/*N = 8, K = 8...37*/
		48639, 108545, 224143, 433905, 795455, 1392065, 2340495, 3800305, 5984767,
		9173505, 13726991, 20103025, 28875327, 40754369, 56610575, 77500017,
		104692735, 139703809, 184327311, 240673265, 311207743, 398796225, 506750351,
		638878193, 799538175, 993696769, 1226990095, 1505789553, 1837271615,
		2229491905L,
// #if defined( CUSTOM_MODES )
		/*...40:*/
		// 2691463695U, 3233240945U, 3866006015U,
// #endif
		/*N = 9, K = 9...28:*/
		265729, 598417, 1256465, 2485825, 4673345, 8405905, 14546705, 24331777,
		39490049, 62390545, 96220561, 145198913, 214828609, 312193553, 446304145,
		628496897, 872893441, 1196924561, 1621925137, 2173806145L,
// #if defined( CUSTOM_MODES )
		/*...29:*/
		// 2883810113U,
// #endif
		/*N = 10, K = 10...24:*/
		1462563, 3317445, 7059735, 14218905, 27298155, 50250765, 89129247, 152951073,
		254831667, 413442773, 654862247, 1014889769, 1541911931, 2300409629L,
		3375210671L,
		/*N = 11, K = 11...19:*/
		8097453, 18474633, 39753273, 81270333, 158819253, 298199265, 540279585,
		948062325, 1616336765,
// #if defined( CUSTOM_MODES )
		/*...20:*/
		// 2684641785U,
// #endif
		/*N = 12, K = 12...18:*/
		45046719, 103274625, 224298231, 464387817, 921406335, 1759885185,
		3248227095L,
		/*N = 13, K = 13...16:*/
		251595969, 579168825, 1267854873, 2653649025L,
		/*N = 14, K = 14:*/
		1409933619
	};

/* #if defined( CUSTOM_MODES )
	static const opus_uint32 *const CELT_PVQ_U_ROW[15] = {
		CELT_PVQ_U_DATA +    0,CELT_PVQ_U_DATA +  208,CELT_PVQ_U_DATA +  415,
		CELT_PVQ_U_DATA +  621,CELT_PVQ_U_DATA +  826,CELT_PVQ_U_DATA + 1030,
		CELT_PVQ_U_DATA + 1233,CELT_PVQ_U_DATA + 1336,CELT_PVQ_U_DATA + 1389,
		CELT_PVQ_U_DATA + 1421,CELT_PVQ_U_DATA + 1441,CELT_PVQ_U_DATA + 1455,
		CELT_PVQ_U_DATA + 1464,CELT_PVQ_U_DATA + 1470,CELT_PVQ_U_DATA + 1473
	};
#else */
	/**
	 * java changed, using: CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ index ] ]
	 */
	private static final int CELT_PVQ_U_ROW[/* 15 */] = {
		/*CELT_PVQ_U_DATA + */   0, /*CELT_PVQ_U_DATA + */ 176, /*CELT_PVQ_U_DATA + */ 351,
		/*CELT_PVQ_U_DATA + */ 525, /*CELT_PVQ_U_DATA + */ 698, /*CELT_PVQ_U_DATA + */ 870,
		/*CELT_PVQ_U_DATA + */1041, /*CELT_PVQ_U_DATA + */1131, /*CELT_PVQ_U_DATA + */1178,
		/*CELT_PVQ_U_DATA + */1207, /*CELT_PVQ_U_DATA + */1226, /*CELT_PVQ_U_DATA + */1240,
		/*CELT_PVQ_U_DATA + */1248, /*CELT_PVQ_U_DATA + */1254, /*CELT_PVQ_U_DATA + */1257
	};
// #endif

/* #if defined( CUSTOM_MODES )
	void get_required_bits( opus_int16 *_bits,int _n,int _maxk,int _frac ) {
		int k;
		// _maxk == 0 => there's nothing to do.
		celt_assert( _maxk > 0 );
		_bits[0] = 0;
		for( k = 1; k <= _maxk; k++ )_bits[k] = log2_frac( CELT_PVQ_V( _n,k ),_frac );
	}
#endif */

	private static final int icwrs(final int _n, final int[] _y ) {// return uint32
		// celt_assert( _n >= 2 );
		int j = _n - 1;
		int i = 0;
		int k = _y[ j ];
		if( k < 0 ) {
			k = -k;
			i++;
		}
		do {
			j--;
			final int n = _n - j;// java
			i += (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ n <= k ? n : k ] + (n >= k ? n : k) ];// CELT_PVQ_U( _n - j, k );
			/* k += Math.abs( _y[j] );
			if( _y[j] < 0 ) {
				final int k1 = k + 1;
				i += (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ n <= k1 ? n : k1 ] + (n >= k1 ? n : k1) ];// CELT_PVQ_U( _n - j, k + 1 );
			}*/
			final int v = _y[j];// java
			if( v < 0 ) {
				k -= v;
				final int k1 = k + 1;
				i += (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ n <= k1 ? n : k1 ] + (n >= k1 ? n : k1) ];// CELT_PVQ_U( _n - j, k + 1 );
			} else {
				k += v;
			}
		} while( j > 0 );
		return i;
	}

	private static final void encode_pulses(final int[] _y, final int _n, final int _k, final Jec_enc _enc) {
		// celt_assert( _k > 0 );
		// _enc.ec_enc_uint( (int)icwrs( _n, _y ), (int)CELT_PVQ_V( _n, _k ) );
		int v = (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ _n <= _k ? _n : _k ] + (_n >= _k ? _n : _k) ];
		final int k1 = _k + 1;
		v += (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ _n <= k1 ? _n : k1 ] + (_n >= k1 ? _n : k1) ];
		_enc.ec_enc_uint( icwrs( _n, _y ), v );
	}

	private static final float cwrsi(int _n, int _k, final int _ii, final int[] _y) {
		long _i = (long)_ii & 0xffffffffL;// java
		int yoffset = 0;// java _y[yoffset]
		float yy = 0;
		// celt_assert( _k > 0 );
		// celt_assert( _n > 1 );
		while( _n > 2 ) {
			/*Lots of pulses case:*/
			if( _k >= _n ) {
				final int row = CELT_PVQ_U_ROW[_n];// CELT_PVQ_U_DATA[ row ]
				/*Are the pulses in this dimension negative?*/
				long p = CELT_PVQ_U_DATA[row + _k + 1];
				final long s = (_i >= p ? -1L : 0);// FIXME why int s?
				_i -= p & s;
				/*Count how many pulses were placed in this dimension.*/
				final int k0 = _k;
				final long q = CELT_PVQ_U_DATA[row + _n];
				if( q > _i ) {
					// celt_sig_assert( p > q );
					_k = _n;
					do {
						p = CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[--_k] + _n ];// CELT_PVQ_U_ROW[--_k][_n];
					} while( p > _i );
				} else {
					for( p = CELT_PVQ_U_DATA[row + _k]; p > _i; p = CELT_PVQ_U_DATA[row + _k] ) {
						_k--;
					}
				}
				_i -= p;
				final int val = (int)((k0 - _k + s) ^ s);// FIXME why using short?
				_y[yoffset++] = val;// int <- short
				final float v = (float)val;// float <- short
				yy += v * v;
			}
			/*Lots of dimensions case:*/
			else {
				/*Are there any pulses in this dimension at all?*/
				long p = CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[_k] + _n ];
				final long q = CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[_k + 1] + _n ];
				if( p <= _i && _i < q ) {
					_i -= p;
					_y[yoffset++] = 0;
				}
				else {
					/*Are the pulses in this dimension negative?*/
					final long s = ( _i >= q ? -1L : 0);
					_i -= q & s;
					/*Count how many pulses were placed in this dimension.*/
					final int k0 = _k;
					do {
						p = CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[--_k] + _n ];
					} while( p > _i );
					_i -= p;
					final int val = (int)((k0 - _k + s) ^ s);// FIXME why using short?
					_y[yoffset++] = val;// int <- short
					final float v = (float)val;// float <- short
					yy += v * v;
				}
			}
			_n--;
		}
		/*_n == 2*/
		final long p = (long)((_k << 1) + 1);
		int s = (_i >= p ? -1 : 0);
		_i -= p & (long)s;
		final int k0 = _k;
		_k = (int)(( _i + 1 ) >>> 1);
		if( 0 != _k ) {
			_i -= (_k << 1) - 1;
		}
		int val = (( k0 - _k + s ) ^ s);// FIXME why using short?
		_y[yoffset++] = val;// int <- short
		float v = (float)val;// float <- short
		yy += v * v;
		/*_n == 1*/
		s = (int)(-_i);//-(int)_i;
		val = (( _k + s ) ^ s);// FIXME why using short?
		_y[yoffset] = val;// int <- short
		v = (float)val;// float <- short
		yy += v * v;
		return yy;
	}

	private static final float decode_pulses(final int[] _y, final int _n, final int _k, final Jec_dec _dec) {
		// return cwrsi( _n, _k, _dec.ec_dec_uint( (int)CELT_PVQ_V( _n, _k ) ), _y );
		int v = (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ _n <= _k ? _n : _k ] + (_n >= _k ? _n : _k) ];
		final int k1 = _k + 1;
		v += (int)CELT_PVQ_U_DATA[ CELT_PVQ_U_ROW[ _n <= k1 ? _n : k1 ] + (_n >= k1 ? _n : k1) ];
		return cwrsi( _n, _k, _dec.ec_dec_uint( v ), _y );
	}

// #else /* SMALL_FOOTPRINT */

	/** Computes the next row/column of any recurrence that obeys the relation
	   u[i][j] = u[i - 1][j] + u[i][j - 1] + u[i - 1][j - 1].
	  _ui0 is the base case for the new row/column.*/
/*	static OPUS_INLINE void unext(opus_uint32 *_ui, unsigned _len, opus_uint32 _ui0) {
		opus_uint32 ui1;
		unsigned      j;
		// This do - while will overrun the array if we don't have storage for at least
		// 2 values.
		j = 1;
		do {
			ui1 = UADD32( UADD32( _ui[j], _ui[j - 1] ), _ui0 );
			_ui[j - 1] = _ui0;
			_ui0 = ui1;
		} while ( ++j < _len );
		_ui[j - 1] = _ui0;
	} */

	/** Computes the previous row/column of any recurrence that obeys the relation
	    u[i - 1][j] = u[i][j] - u[i][j - 1] - u[i - 1][j - 1].
	    _ui0 is the base case for the new row/column.*/
/*	static OPUS_INLINE void uprev(opus_uint32 *_ui, unsigned _n, opus_uint32 _ui0) {
		opus_uint32 ui1;
		unsigned      j;
		// This do - while will overrun the array if we don't have storage for at least
		// 2 values.
		j = 1;
		do {
			ui1 = USUB32( USUB32( _ui[j],_ui[j - 1] ),_ui0 );
			_ui[j - 1] = _ui0;
			_ui0 = ui1;
		} while ( ++j < _n );
		_ui[j - 1] = _ui0;
	} */

	/** Compute V( _n,_k ), as well as U( _n,0..._k + 1 ).
	    _u: On exit, _u[i] contains U( _n,i ) for i in [0..._k + 1].*/
/*	static opus_uint32 ncwrs_urow(unsigned _n, final unsigned _k, opus_uint32 *_u) {
		opus_uint32 um2;
		unsigned      len;
		unsigned      k;
		len = _k + 2;
		// We require storage at least 3 values ( e.g., _k > 0 ).
		celt_assert( len >= 3 );
		_u[0] = 0;
		_u[1] = um2 = 1;
		// If _n == 0, _u[0] should be 1 and the rest should be 0.
		// If _n == 1, _u[i] should be 1 for i > 1.
		celt_assert( _n >= 2 );
		// If _k == 0, the following do - while loop will overflow the buffer.
		celt_assert( _k > 0 );
		k = 2;
		do _u[k] = ( k << 1 ) - 1;
		while( ++k < len );
		for( k = 2; k < _n; k++ ) unext( _u + 1, _k + 1, 1 );
		return _u[_k] + _u[_k + 1];
	} */

	/** Returns the _i'th combination of _k elements chosen from a set of size _n
	   with associated sign bits.
	   _y: Returns the vector of pulses.
	   _u: Must contain entries [0..._k + 1] of row _n of U(  ) on input.
	   Its contents will be destructively modified.*/
/*	static opus_val32 cwrsi( final int _n,final int _k,final opus_uint32 _i,int *_y,opus_uint32 *_u ) {
		int j;
		opus_int16 val;
		opus_val32 yy = 0;
		celt_assert( _n > 0 );
		j = 0;
		do{
			opus_uint32 p;
			int           s;
			int           yj;
			p = _u[_k + 1];
			s =  - ( _i >= p );
			_i -= p & s;
			yj = _k;
			p = _u[_k];
			while( p > _i ) p = _u[--_k];
			_i -= p;
			yj -= _k;
			val = ( yj + s ) ^ s;
			_y[j] = val;
			yy = MAC16_16( yy, val, val );
			uprev( _u, _k + 2, 0 );
		}
		while( ++j < _n );
		return yy;
	} */

	/** Returns the index of the given combination of K elements chosen from a set
	    of size 1 with associated sign bits.
	    _y: The vector of pulses, whose sum of absolute values is K.
	    _k: Returns K.*/
/*	static OPUS_INLINE opus_uint32 icwrs1( const int *_y,int *_k ) {
		*_k = abs( _y[0] );
		return _y[0] < 0;
	} */

	/** Returns the index of the given combination of K elements chosen from a set
	    of size _n with associated sign bits.
	    _y:  The vector of pulses, whose sum of absolute values must be _k.
	    _nc: Returns V( _n,_k ).*/
/*	static OPUS_INLINE opus_uint32 icwrs( int _n, int _k, opus_uint32 *_nc, const int *_y,
			opus_uint32 *_u ) {
		opus_uint32 i;
		int         j;
		int         k;
		// We can't unroll the first two iterations of the loop unless _n >= 2.
		celt_assert( _n >= 2 );
		_u[0] = 0;
		for( k = 1; k <= _k + 1; k++ ) _u[k] = ( k << 1 ) - 1;
		i = icwrs1( _y + _n - 1, &k );
		j = _n - 2;
		i += _u[k];
		k += abs( _y[j] );
		if( _y[j] < 0 ) i += _u[k + 1];
		while( j-- > 0 ) {
			unext( _u, _k + 2, 0 );
			i += _u[k];
			k += abs( _y[j] );
			if( _y[j] < 0 )i += _u[k + 1];
		}
		*_nc = _u[k] + _u[k + 1];
		return i;
	} */

/* #ifdef CUSTOM_MODES
	void get_required_bits( opus_int16 *_bits,int _n,int _maxk,int _frac ) {
		int k;
		// _maxk == 0 => there's nothing to do.
		celt_assert( _maxk > 0 );
		_bits[0] = 0;
		if( _n == 1 )
		{
			for( k = 1; k <= _maxk; k++ )
				_bits[k]  =  1 << _frac;
		}
		else {
			VARDECL( opus_uint32, u );
			SAVE_STACK;
			ALLOC( u, _maxk + 2U, opus_uint32 );
			ncwrs_urow( _n,_maxk,u );
			for( k = 1; k <= _maxk; k++ )
				_bits[k] = log2_frac( u[k] + u[k + 1], _frac );
			RESTORE_STACK;
		}
	}
#endif */ /* CUSTOM_MODES */

/*	void encode_pulses( const int *_y, int _n, int _k, ec_enc *_enc ) {
		opus_uint32 i;
		VARDECL( opus_uint32, u );
		opus_uint32 nc;
		SAVE_STACK;
		celt_assert( _k > 0 );
		ALLOC( u, _k + 2U, opus_uint32 );
		i = icwrs( _n, _k, &nc, _y, u );
		ec_enc_uint( _enc, i, nc );
		RESTORE_STACK;
	} */

/*	opus_val32 decode_pulses( int *_y, int _n, int _k, ec_dec *_dec ) {
		VARDECL( opus_uint32,u );
		int ret;
		SAVE_STACK;
		celt_assert( _k > 0 );
		ALLOC( u, _k + 2U, opus_uint32 );
		ret = cwrsi( _n, _k, ec_dec_uint( _dec, ncwrs_urow( _n, _k, u ) ), _y, u );
		RESTORE_STACK;
		return ret;
	}

#endif */ /* SMALL_FOOTPRINT */
	// end cwrs.c
	// start vq.c
// #ifndef OVERRIDE_vq_exp_rotation1
	private static final void exp_rotation1(final float[] X, final int xoffset,// java
			final int len, final int stride, final float c, final float s)
	{
		final float ms = -s;
		for( int Xptr = xoffset, end = Xptr + len - stride; Xptr < end; )
		{
			final float x1 = X[Xptr];
			final int k = Xptr + stride;// java
			final float x2 = X[k];
			X[k     ] = (c * x2) +  s * x1;
			X[Xptr++] = (c * x1) + ms * x2;
		}
		for( int Xptr = xoffset + (len - (stride << 1) - 1); Xptr >= xoffset; )
		{
			final float x1 = X[Xptr];
			final int xs = Xptr + stride;// java
			final float x2 = X[xs];
			X[xs] = (c * x2) +  s * x1;
			X[Xptr--] = (c * x1) + ms * x2;
		}
	}
// #endif /* OVERRIDE_vq_exp_rotation1 */

	private static final float Q15_ONE = 1.f;
	private static final int SPREAD_FACTOR[/* 3 */] = { 15, 10, 5 };

	private static final void exp_rotation(final float[] X, final int xoffset,// java
			int len, final int dir, final int stride, final int K, final int spread)
	{
		if( (K << 1) >= len || spread == JCELTMode.SPREAD_NONE ) {
			return;
		}
		final int factor = SPREAD_FACTOR[spread - 1];

		final float gain = (float)(Q15_ONE * len) / (float)(len + factor * K);
		final float theta = .5f * gain * gain;

		final float c = (float)Math.cos( .5 * Math.PI * (double)theta );
		final float s = (float)Math.cos( .5 * Math.PI * (double)(Jfloat_cast.Q15ONE - theta) ); /*  sin(theta) */

		int stride2 = 0;
		if( len >= (stride << 3) )
		{
			stride2 = 1;
			/* This is just a simple (equivalent) way of computing sqrt(len/stride) with rounding.
			It's basically incrementing long as (stride2+0.5)^2 < len/stride. */
			while( (stride2 * stride2 + stride2) * stride + (stride >> 2) < len ) {
				stride2++;
			}
		}
		/*NOTE: As a minor optimization, we could be passing around log2(B), not B, for both this and for
		extract_collapse_mask().*/
		len /= stride;
		for( int i = xoffset, ie = xoffset + len * stride; i < ie; i += len )
		{
			if( dir < 0 )
			{
				if( 0 != stride2 ) {
					exp_rotation1( X, i, len, stride2, s, c );
				}
				exp_rotation1( X, i, len, 1, c, s );
			} else {
				exp_rotation1( X, i, len, 1, c, -s );
				if( 0 != stride2 ) {
					exp_rotation1( X, i, len, stride2, s, -c );
				}
			}
		}
	}

	/** Takes the pitch vector and the decoded residual vector, computes the gain
	that will give ||p+g*y||=1 and mixes the residual with the pitch. */
	private static final void normalise_residual(final int[] iy,
			final float[] X, int xoffset,// java
			final int N, final float Ryy, float gain)
	{
/* #ifdef FIXED_POINT
		int k;
#endif */

/* #ifdef FIXED_POINT
		k = celt_ilog2( Ryy ) >> 1;
#endif */
		// final double t = (double)Ryy;
		// final float g = 1f / (float)Math.sqrt( t ) * gain;
		gain = 1f / (float)Math.sqrt( (double)Ryy ) * gain;

		int i = 0;
		do {
			X[xoffset++] = gain * (float)iy[i];// g * (float)iy[i];
		} while( ++i < N );
	}

	private static final int extract_collapse_mask(final int[] iy, final int N, final int B)
	{
		if( B <= 1 ) {
			return 1;
		}
		/*NOTE: As a minor optimization, we could be passing around log2(B), not B, for both this and for
		exp_rotation().*/
		final int N0 = N / B;
		int collapse_mask = 0;
		int i = 0;
		do {
			int tmp = 0;
			int j = i * N0;
			final int end = j + N0;
			do {
				tmp |= iy[ j ];
			} while( ++j < end );
			if( tmp != 0 ) {
				collapse_mask |= 1 << i;
			}
		} while( ++i < B );
		return collapse_mask;
	}

	// op_pvq_search_c
	private static final float op_pvq_search(final float[] X, final int xoffset,// java
			final int[] iy, final int K, final int N)//, int arch )
	{
		// SAVE_STACK;

		final float[] y = new float[N];
		final int[] signx = new int[N];

		/* Get rid of the sign */
		float sum = 0;
		int j = 0;
		int i = xoffset;// java
		do {
			float v = X[i];// java
			signx[j] = v < 0 ? 1 : 0;
			/* OPT: Make sure the compiler doesn't use a branch on ABS16(). */
			if( v < 0 ) {// java
				v = -v;
			}
			X[i] = v;
			iy[j] = 0;// java don't need, already zeroed
			// y[j] = 0;// java don't need, already zeroed
			i++;// java
		} while( ++j < N );

		float xy = 0, yy = 0;

		int pulsesLeft = K;

		/* Do a pre-search by projecting on the pyramid */
		if( K > (N >> 1) )
		{
			j = xoffset;
			i = xoffset + N;// java
			do {
				sum += X[j];
			}  while( ++j < i );

			/* If X is too small, just replace it with a pulse at 0 */
/* #ifdef FIXED_POINT
			if( sum <= K )
#else */
			/* Prevents infinities and NaNs from causing too many pulses
			to be allocated. 64 is an approximation of infinity here. */
			if( !(sum > Jfloat_cast.EPSILON && sum < 64) )
// #endif
			{
				X[xoffset] = 1.f;
				j = xoffset + 1;
				do {
					X[j] = 0;
				} while( ++j < i );
				sum = 1.f;
			}
/* #ifdef FIXED_POINT
			rcp = EXTRACT16(MULT16_32_Q16(K, celt_rcp(sum)));
#else */
			/* Using K+e with e < 1 guarantees we cannot get more than K pulses. */
			final float rcp = (K + 0.8f) * (1f / sum);
// #endif
			j = 0;
			i = xoffset;// java
			do {
/* #ifdef FIXED_POINT
				// It's really important to round *towards zero* here
				iy[j] = MULT16_16_Q15(X[j],rcp);
#else */
				iy[j] = (int)Math.floor( (double)(rcp * X[i]) );
// #endif
				float v = (float)iy[j];// java
				// y[j] = (float)iy[j];
				yy += v * v;
				xy += X[i] * v;
				v *= 2f;
				y[j] = v;
				pulsesLeft -= iy[j];
				i++;// java
			}  while( ++j < N );
		}
		// celt_sig_assert(pulsesLeft<=N+3);

		/* This should never happen, but just in case it does (e.g. on silence)
		we fill the first bin with pulses. */
/* #ifdef FIXED_POINT_DEBUG
		celt_sig_assert(pulsesLeft<=N+3);
#endif */
		if( pulsesLeft > N + 3 )
		{
			final float tmp = (float)pulsesLeft;
			yy += tmp * tmp;
			yy += tmp * y[0];
			iy[0] += pulsesLeft;
			pulsesLeft = 0;
		}

		for( i = 0; i < pulsesLeft; i++ )
		{
/* #ifdef FIXED_POINT
			int rshift;
#endif */
/* #ifdef FIXED_POINT
			rshift = 1 + celt_ilog2( K - pulsesLeft + i + 1 );
#endif */
			int best_id = 0;
			/* The squared magnitude term gets added anyway, so we might as well
			add it outside the loop */
			yy += 1f;
			/* Calculations for position 0 are out of the loop, in part to reduce
			  mispredicted branches (since the if condition is usually false)
			  in the loop. */
			/* Temporary sums of the new pulse(s) */
			float Rxy = xy + X[xoffset];
			/* We're multiplying y[j] by two so we don't have to do it here */
			float Ryy = yy + y[0];

			/* Approximate score: we maximise Rxy/sqrt(Ryy) (we're guaranteed that
			  Rxy is positive because the sign is pre-computed) */
			Rxy *= Rxy;
			float best_den = Ryy;
			float best_num = Rxy;
			j = 1;
			int n = xoffset;// java
			do {
				/* Temporary sums of the new pulse(s) */
				Rxy = xy + X[++n];// java: j starts from 1
				/* We're multiplying y[j] by two so we don't have to do it here */
				Ryy = yy + y[j];

				/* Approximate score: we maximise Rxy/sqrt(Ryy) (we're guaranteed that
				Rxy is positive because the sign is pre-computed) */
				Rxy = Rxy * Rxy;
				/* The idea is to check for num/den >= best_num/best_den, but that way
				we can do it without any division */
				/* OPT: It's not clear whether a cmov is faster than a branch here
				  since the condition is more often false than true and using
				  a cmov introduces data dependencies across iterations. The optimal
				  choice may be architecture-dependent. */
				if( (best_den * Rxy) > (Ryy * best_num) )
				{
					best_den = Ryy;
					best_num = Rxy;
					best_id = j;
				}
			} while( ++j < N );

			/* Updating the sums of the new pulse(s) */
			xy += X[xoffset + best_id];
			/* We're multiplying y[j] by two so we don't have to do it here */
			yy += y[best_id];

			/* Only now that we've made the final choice, update y/iy */
			/* Multiplying y[j] by 2 so we don't have to do it everywhere else */
			y[best_id] += 2f;
			iy[best_id]++;
		}

		/* Put the original sign back */
		j = 0;
		do {
			/*iy[j] = signx[j] ? -iy[j] : iy[j];*/
			/* OPT: The is more likely to be compiled without a branch than the code above
			  but has the same performance otherwise. */
			final int s = signx[j];// java
			iy[j] = (iy[j] ^ -s) + s;
		} while( ++j < N );
		// RESTORE_STACK;
		return yy;
	}

	/** Algebraic pulse-vector quantiser. The signal x is replaced by the sum of
	 * the pitch and a combination of pulses such that its norm is still equal
	 * to 1. This is the function that will typically require the most CPU.
	 * @param X Residual signal to quantise/encode (returns quantised version)
	 * @param N Number of samples to encode
	 * @param K Number of pulses to use
	 * @param enc Entropy encoder state
	 * @return A mask indicating which blocks in the band received pulses
	 */
	private static final int alg_quant(final float[] X, final int xoffset,// java
			final int N, final int K, final int spread, final int B, final Jec_enc enc,
			final float gain, final boolean resynth )// , int arch)
	{
		// SAVE_STACK;

		// celt_assert2(K>0, "alg_quant() needs at least one pulse");
		// celt_assert2(N>1, "alg_quant() needs at least two dimensions");

		/* Covers vectorization by up to 4. */
		final int[] iy = new int[ N + 3 ];

		exp_rotation( X, xoffset, N, 1, B, K, spread );

		final float yy = op_pvq_search( X, xoffset, iy, K, N );//, arch );

		encode_pulses( iy, N, K, enc );

		if( resynth )
		{
			normalise_residual( iy, X, xoffset, N, yy, gain );
			exp_rotation( X, xoffset, N, -1, B, K, spread );
		}

		final int collapse_mask = extract_collapse_mask(iy, N, B);
		// RESTORE_STACK;
		return collapse_mask;
	}
	/** Decode pulse vector and combine the result with the pitch vector to produce
	the final normalised signal in the current band. */
	/** Algebraic pulse decoder
	 * @param X Decoded normalised spectrum (returned)
	 * @param N Number of samples to decode
	 * @param K Number of pulses to use
	 * @param dec Entropy decoder state
	 * @ret A mask indicating which blocks in the band received pulses
	 */
	private static final int alg_unquant(final float[] X, final int xoffset,// java
			final int N, final int K, final int spread, final int B, final Jec_dec dec, final float gain)
	{
		// SAVE_STACK;

		// celt_assert2( K > 0, "alg_unquant() needs at least one pulse");
		// celt_assert2( N > 1, "alg_unquant() needs at least two dimensions");
		final int[] iy = new int[N];
		final float Ryy = decode_pulses( iy, N, K, dec );
		normalise_residual( iy, X, xoffset, N, Ryy, gain );
		exp_rotation( X, xoffset, N, -1, B, K, spread );
		final int collapse_mask = extract_collapse_mask( iy, N, B );
		// RESTORE_STACK;
		return collapse_mask;
	}

// #ifndef OVERRIDE_renormalise_vector
	static final void renormalise_vector(final float[] X, int xoffset, int N, float gain)//, final int arch)
	{// java xoffset is added
/* #ifdef FIXED_POINT
		int k;
#endif */
		final float E = Jfloat_cast.EPSILON + celt_inner_prod( X, xoffset, X, xoffset, N );//, arch );
/* #ifdef FIXED_POINT
		k = celt_ilog2(E)>>1;
#endif */
		// float t = E;
		gain = (1.f / (float)Math.sqrt( (double)E )) * gain;

		// int xptr = 0;
		for( N += xoffset; xoffset < N; xoffset++ )
		{
			X[xoffset] *= gain;
		}
		/*return celt_sqrt(E);*/
	}
// #endif /* OVERRIDE_renormalise_vector */

	// start mathops.h
	/* CELT doesn't need it for fixed-point, by analysis.c does. */
// #if !defined(FIXED_POINT) || defined(ANALYSIS_C)
private static final float cA = 0.43157974f;
private static final float cB = 0.67848403f;
private static final float cC = 0.08595542f;
private static final float cE = ((float)Math.PI / 2.f);
/*	private static final float fast_atan2f(final float y, final float x) {// java: extracted in place
		final float x2 = x * x;
		final float y2 = y * y;
		// For very small values, we don't care about the answer, so
		  we can just return 0.
		if( x2 + y2 < 1e-18f )
		{
			return 0;
		}
		if( x2 < y2 ) {
			final float den = (y2 + cB * x2) * (y2 + cC * x2);
			return -x * y * (y2 + cA * x2) / den + (y < 0 ? -cE : cE);
		}// else {
			final float den = (x2 + cB * y2) * (x2 + cC * y2);
			return x * y *(x2 + cA * y2) / den + (y < 0 ? -cE : cE) - (x * y < 0 ? -cE : cE);
		// }
	}
#undef cA
#undef cB
#undef cC
#undef cD
#endif */
	// end mathops.h

	private static final int stereo_itheta(final float[] X, int xoffset, final float[] Y, int yoffset, final boolean stereo, final int N)//, final int arch)
	{
		float Emid, Eside;

		Emid = Eside = Jfloat_cast.EPSILON;
		if( stereo )
		{
			for( final int i = yoffset + N; yoffset < i; )
			{
				final float m = X[xoffset] + Y[yoffset];
				final float s = X[xoffset++] - Y[yoffset++];
				Emid += m * m;
				Eside += s * s;
			}
		} else {
			Emid += celt_inner_prod( X, xoffset, X, xoffset, N );//, arch );
			Eside += celt_inner_prod( Y, yoffset, Y, yoffset, N );//, arch );
		}
		final float mid = (float)Math.sqrt( (double)Emid );
		final float side = (float)Math.sqrt( (double)Eside );
// #ifdef FIXED_POINT
		/* 0.63662 = 2/pi */
// 		itheta = MULT16_16_Q15( QCONST16(0.63662f, 15), celt_atan2p(side, mid) );
// #else
		// java: fast_atan2f extracted in place
		// final int itheta = (int)Math.floor( .5f + 16384f * 0.63662f * fast_atan2f(side, mid));
		Eside = mid * mid;
		Emid = side * side;// java: fast_atan2f returns Emid
		/* For very small values, we don't care about the answer, so
		  we can just return 0. */
		if( Eside + Emid < 1e-18f ) {
			Emid = 0f;
		} else if( Eside < Emid ) {
			final float den = (Emid + cB * Eside) * (Emid + cC * Eside);
			Emid = -side * mid * (Emid + cA * Eside) / den + (mid < 0 ? -cE : cE);
		} else {
			final float den = (Eside + cB * Emid) * (Eside + cC * Emid);
			Emid = side * mid *(Eside + cA * Emid) / den + (mid < 0 ? -cE : cE) - (side * mid < 0 ? -cE : cE);
		}
		final int itheta = (int)Math.floor( .5f + 16384f * 0.63662f * Emid );
// #endif

		return itheta;
	}
	// end vq.c

	// start band.c
	private static final void intensity_stereo(final int bands,// java replace JCELTMode m to nbEBands. FIXME why need CELTMode?
			final float[] X, int xoffset,// java
			final float[] Y, int yoffset,// java
			final float[] bandE, final int bandID, int N)
	{
		final int i = bandID;
/* #ifdef FIXED_POINT
		int shift = celt_zlog2( MAX32( bandE[i], bandE[i + m.nbEBands] ) ) - 13;
#endif */
		final float left = bandE[i];
		final float right = bandE[i + bands];
		final float norm = Jfloat_cast.EPSILON + (float)Math.sqrt( (double)(Jfloat_cast.EPSILON + left * left + right * right) );
		final float a1 = left / norm;
		final float a2 = right / norm;
		for( N += xoffset; xoffset < N; xoffset++ )
		{
			final float l = X[xoffset];
			final float r = Y[yoffset++];
			X[xoffset] = (a1 * l) + (a2 * r);
			/* Side is not encoded, no need to calculate */
		}
	}
	// end band.c

	/**
	 * java changed: return value is ((b << 32) | fill)
	 *
	 * @param ctx
	 * @param sctx
	 * @param X
	 * @param xoffset
	 * @param Y
	 * @param yoffset
	 * @param N
	 * @param b
	 * @param B
	 * @param B0
	 * @param LM
	 * @param stereo
	 * @param fill
	 * @return
	 */
	private final long compute_theta(final Jsplit_ctx sctx,
				final float[] X, final int xoffset,// java
				final float[] Y, final int yoffset,// java
				final int N,
				int b,// java changed: the value will be returned
				final int B, final int B0, final int LM, final boolean stereo,
				int fill// java changed: the value will be returned
		)
	{
		final boolean is_encode = this.encode;// java renamed
		final JCELTMode mode = this.m;
		final int curr_i = this.i;
		final int curr_intensity = this.intensity;
		final Jec_ctx entropy = this.ec;
		final float[] bandE_ptr = this.bandE;

		/* Decide on the resolution to give to the split parameter theta */
		final int pulse_cap = mode.logN[curr_i] + LM * (1 << Jec_ctx.BITRES);
		final int offset = (pulse_cap >> 1) - (stereo && N == 2 ? JCELTMode.QTHETA_OFFSET_TWOPHASE : JCELTMode.QTHETA_OFFSET);
		int qn = compute_qn( N, b, offset, pulse_cap, stereo );
		if( stereo && curr_i >= curr_intensity ) {
			qn = 1;
		}
		int itheta = 0;
		if( is_encode )
		{
			/* theta is the atan() of the ratio between the (normalized)
			   side and mid. With just that parameter, we can re - scale both
			   mid and side because we know that 1) they have unit norm and
			   2) they are orthogonal. */
			itheta = stereo_itheta( X, xoffset, Y, yoffset, stereo, N );//, ctx.arch );
		}
		final int tell = entropy.ec_tell_frac();
		boolean inv = false;
		int delta, imid, iside;
		if( qn != 1 )
		{
			if( is_encode ) {
				if( ! stereo || this.theta_round == 0 )
				{
					itheta = (itheta * qn + 8192) >> 14;
					if( ! stereo && this.avoid_split_noise && itheta > 0 && itheta < qn )
					{
						/* Check if the selected value of theta will cause the bit allocation
  						  to inject noise on one side. If so, make sure the energy of that side
						  is zero. */
						final int unquantized = ( itheta * 16384 / qn );
						imid = bitexact_cos( unquantized );
						iside = bitexact_cos( 16384 - unquantized );
						delta = ((16384 + ((N - 1) << 7) * bitexact_log2tan( iside, imid )) >> 15);
						if( delta > b ) {
							itheta = qn;
						} else if( delta < -b ) {
							itheta = 0;
						}
					}
				} else {
					/* Bias quantization towards itheta=0 and itheta=16384. */
					int bias = itheta > 8192 ? 32767 / qn : -32767 / qn;
					bias = (itheta * qn + bias) >> 14;// java
					bias = (0 > bias ? 0 : bias);// java
					int down = qn - 1;
					down = (down < bias ? down : bias);
					if( this.theta_round < 0 ) {
						itheta = down;
					} else {
						itheta = down + 1;
					}
				}
			}
			/* Entropy coding of the angle. We use a uniform pdf for the
			   time split, a step for stereo, and a triangular one for the rest. */
			if( stereo && N > 2 )
			{
				final int p0 = 3;
				int x = itheta;
				final int x0 = qn >> 1;
				final int ft = p0 * (x0 + 1) + x0;
				/* Use a probability of p0 up to itheta = 8192 and then use 1 after */
				if( is_encode )
				{
					((Jec_enc)entropy).ec_encode( x <= x0 ? p0 * x : (x - 1 - x0) + (x0 + 1) * p0, x <= x0 ? p0 * (x + 1) : (x - x0) + (x0 + 1) * p0, ft );
				} else {
					final int fs = ((Jec_dec)entropy).ec_decode( ft );
					if( fs < (x0 + 1) * p0 ) {
						x = fs / p0;
					} else {
						x = x0 + 1 + (fs - (x0 + 1) * p0);
					}
					((Jec_dec)entropy).ec_dec_update( x <= x0 ? p0 * x : (x - 1 - x0) + (x0 + 1) * p0, x <= x0 ? p0 * (x + 1) : (x - x0) + (x0 + 1) * p0, ft );
					itheta = x;
				}
			} else if( B0 > 1 || stereo ) {
				/* Uniform pdf */
				if( is_encode ) {
					((Jec_enc)entropy).ec_enc_uint( itheta, qn + 1 );
				} else {
					itheta = ((Jec_dec)entropy).ec_dec_uint( qn + 1 );
				}
			} else {
				int fs = 1;
				final int ft = ((qn >> 1) + 1) * ((qn >> 1) + 1);
				if( is_encode )
				{
					fs = itheta <= (qn >> 1) ? itheta + 1 : qn + 1 - itheta;
					final int fl = itheta <= (qn >> 1) ? itheta * (itheta + 1) >> 1 :
						ft - ((qn + 1 - itheta) * (qn + 2 - itheta) >> 1);

					((Jec_enc)entropy).ec_encode( fl, fl + fs, ft );
				} else {
					/* Triangular pdf */
					int fl = 0;
					final int fm = ((Jec_dec)entropy).ec_decode( ft );

					if( fm < ((qn >> 1) * ((qn >> 1) + 1) >> 1) )
					{
						itheta = (Jmath_ops.isqrt32( (fm << 3) + 1 ) - 1) >> 1;
						fs = itheta + 1;
						fl = itheta * (itheta + 1) >> 1;
					}
					else
					{// FIXME signed - unsigned.
						itheta = (2 * (qn + 1) - Jmath_ops.isqrt32( ((ft - fm - 1) << 3) + 1 )) >> 1;
						fs = qn + 1 - itheta;
						fl = ft - ((qn + 1 - itheta) * (qn + 2 - itheta) >> 1);
					}

					((Jec_dec)entropy).ec_dec_update( fl, fl + fs, ft );
				}
			}
			// celt_assert( itheta >= 0 );
			itheta = (itheta << 14) / qn;
			if( is_encode && stereo )
			{
				if( itheta == 0 ) {
					intensity_stereo( mode.nbEBands /*mode*/, X, xoffset, Y, yoffset, bandE_ptr, curr_i, N );// java changed
				} else {
					stereo_split( X, xoffset, Y, yoffset, N );
				}
			}
			/* NOTE: Renormalising X and Y *may* help fixed - point a bit at very high rate.
			   Let's do that at higher complexity */
		} else if( stereo ) {
			if( is_encode )
			{
				inv = itheta > 8192 && ! this.disable_inv;
				if( inv )
				{
					for( int j = yoffset, je = j + N; j < je; j++ ) {
						Y[j] = -Y[j];
					}
				}
				intensity_stereo( mode.nbEBands /*mode*/, X, xoffset, Y, yoffset, bandE_ptr, curr_i, N );// java changed
			}
			if( b > 2 << Jec_ctx.BITRES && this.remaining_bits > 2 << Jec_ctx.BITRES )
			{
				if( is_encode ) {
					((Jec_enc)entropy).ec_enc_bit_logp( inv, 2 );
				} else {
					inv = ((Jec_dec)entropy).ec_dec_bit_logp( 2 );
				}
			} else {
				inv = false;
			}
			/* inv flag override to avoid problems with downmixing. */
			if( this.disable_inv ) {
				inv = false;
			}
			itheta = 0;
		}
		final int qalloc = entropy.ec_tell_frac() - tell;
		b -= qalloc;

		if( itheta == 0 )
		{
			imid = 32767;
			iside = 0;
			fill &= (1 << B) - 1;
			delta = -16384;
		} else if( itheta == 16384 )
		{
			imid = 0;
			iside = 32767;
			fill &= ((1 << B) - 1) << B;
			delta = 16384;
		} else {
			imid = bitexact_cos( itheta );
			iside = bitexact_cos( 16384 - itheta );
			/* This is the mid vs side allocation that minimizes squared error
			   in that band. */
			delta = (16384 + ((N - 1) << 7) * bitexact_log2tan( iside, imid )) >> 15;
		}

		sctx.inv = inv;
		sctx.imid = imid;
		sctx.iside = iside;
		sctx.delta = delta;
		sctx.itheta = itheta;
		sctx.qalloc = qalloc;
		//
		return ((long)b << 32) | ((long)fill & 0xffffffffL);
	}

	private static final float NORM_SCALING = 1.f;

	private final int quant_band_n1(
			final float[] X, final int xoffset,// java
			final float[] Y, final int yoffset,// java
			// int b,// FIXME why need b?
			final float[] lowband_out, final int outoffset// java
		)
	{
		float[] x = X;
		int offset = xoffset;// java

		final boolean is_encode = this.encode;// java renamed
		final Jec_ctx entropy = this.ec;

		final int stereo = (Y != null ? 2 : 1);// java
		int c = 0;
		do {
			int sign = 0;
			if( this.remaining_bits >= 1 << Jec_ctx.BITRES )
			{
				if( is_encode )
				{
					if( x[offset] < 0 ) {
						sign = 1;
					}
					((Jec_enc)entropy).ec_enc_bits( sign, 1 );
				} else {
					sign = ((Jec_dec)entropy).ec_dec_bits( 1 );
				}
				this.remaining_bits -= 1 << Jec_ctx.BITRES;
				// b -= 1 << Jec_ctx.BITRES;// why need b?
			}
			if( this.resynth ) {
				x[offset] = sign != 0 ? -NORM_SCALING : NORM_SCALING;
			}
			x = Y;
			offset = yoffset;
		} while ( ++c < stereo );// java
		if( null != lowband_out ) {
			lowband_out[outoffset] = X[xoffset];
		}
		return 1;
	}

	// rate.h
	/* private static final int get_pulses(final int i)// java extracted inplace
	{
		return i < 8 ? i : (8 + (i & 7)) << ((i >> 3) - 1);
	}*/
	// rate.h
	/** This function is responsible for encoding and decoding a mono partition.
	   It can split the band in two and transmit the energy difference with
	   the two half - bands. It can be called recursively so bands can end up being
	   split in 8 parts. */
	private int quant_partition(
			final float[] X, final int xoffset,// java
			int N, int b, int B,
			final float[] lowband, final int lboffset,// java
			int LM, final float gain, int fill)
	{
		// int imid = 0, iside = 0;// FIXME why need imid, iside?
		final int B0 = B;
		// float mid = 0, side = 0;// FIXME why need settings?
		int cm = 0;// java unsigned
		final boolean is_encode = this.encode;// java renamed
		final JCELTMode mode = this.m;
		final int curr_i = this.i;
		final int curr_spread = this.spread;
		final Jec_ctx entropy = this.ec;

		/* If we need 1.5 more bit than we can produce, split the band in two. */
		final int cache = mode.cache.index[ (LM + 1) * mode.nbEBands + curr_i ];// m.cache.bits[ cache ]
		final char[] bits = mode.cache.bits;// java
		if( LM != -1 && b > bits[ cache + bits[ cache ] ] + 12 && N > 2 )
		{
			final Jsplit_ctx sctx = new Jsplit_ctx();
			int next_lowband2 = -1;// lowband[next_lowband2]

			N >>= 1;
			final int Y = xoffset + N;// X[Y]
			LM -= 1;
			if( B == 1 ) {
				fill = (fill & 1) | (fill << 1);
			}
			B = (B + 1) >> 1;

			final long b_fill = compute_theta( sctx, X, xoffset, X, Y, N, b, B, B0, LM, false, fill );
			b = (int)(b_fill >>> 32);// java
			fill = (int)b_fill;// java
			// final int imid = sctx.imid;// FIXME why need imid?
			// final int iside = sctx.iside;// FIXME why need iside?
			int delta = sctx.delta;
			final int itheta = sctx.itheta;
			final int qalloc = sctx.qalloc;
/* #ifdef FIXED_POINT
			mid = imid;
			side = iside;
#else */
			final float mid = (1.f / 32768f) * (float)sctx.imid;// imid;
			final float side = (1.f / 32768f) * (float)sctx.iside;// iside;
// #endif

			/* Give more bits to low - energy MDCTs than they would otherwise deserve */
			if( B0 > 1 && (itheta & 0x3fff) != 0 )
			{
				if( itheta > 8192 ) {
					delta -= delta >> (4 - LM);
				} else {
					delta += (N << Jec_ctx.BITRES >> (5 - LM));// java
					delta = 0 <= delta ? 0 : delta;
				}
			}
			// int mbits = IMAX( 0, IMIN( b, (b - delta ) / 2 ) );
			int mbits = (b - delta ) / 2;
			mbits = b <= mbits ? b : mbits;
			mbits = 0 >= mbits ? 0 : mbits;
			int sbits = b - mbits;
			this.remaining_bits -= qalloc;

			if( null != lowband ) {
				next_lowband2 = lboffset + N;
			}  /* > 32 - bit split case */

			int rebalance = this.remaining_bits;
			if( mbits >= sbits )
			{
				cm = quant_partition( X, xoffset, N, mbits, B, lowband, lboffset, LM, gain * mid, fill );
				rebalance = mbits - (rebalance - this.remaining_bits);
				if( rebalance > 3 << Jec_ctx.BITRES && itheta != 0 ) {
					sbits += rebalance - (3 << Jec_ctx.BITRES);
				}
				cm |= quant_partition( X, Y, N, sbits, B, lowband, next_lowband2, LM, gain * side, fill >> B ) << (B0 >> 1);
			} else {
				cm = quant_partition( X, Y, N, sbits, B, lowband, next_lowband2, LM, gain * side, fill >> B ) << (B0 >> 1);
				rebalance = sbits - (rebalance - this.remaining_bits);
				if( rebalance > 3 << Jec_ctx.BITRES && itheta != 16384 ) {
					mbits += rebalance - (3 << Jec_ctx.BITRES);
				}
				cm |= quant_partition( X, xoffset, N, mbits, B, lowband, lboffset, LM, gain * mid, fill );
			}
		} else {
			/* This is the basic no-split case */
			int q = mode.bits2pulses( curr_i, LM, b );
			int curr_bits = mode.pulses2bits( curr_i, LM, q );
			this.remaining_bits -= curr_bits;

			/* Ensures we can never bust the budget */
			while( this.remaining_bits < 0 && q > 0 )
			{
				this.remaining_bits += curr_bits;
				q--;
				curr_bits = mode.pulses2bits( curr_i, LM, q );
				this.remaining_bits -= curr_bits;
			}

			if( q != 0 )
			{
				final int K = q < 8 ? q : (8 + (q & 7)) << ((q >> 3) - 1);// get_pulses( q );

				/* Finally do the actual quantization */
				if( is_encode )
				{
					cm = alg_quant( X, xoffset, N, K, curr_spread, B, (Jec_enc)entropy, gain, this.resynth );// this.arch );
				} else {
					cm = alg_unquant( X, xoffset, N, K, curr_spread, B, (Jec_dec)entropy, gain );
				}
			} else {
				/* If there's no pulse, fill the band anyway */
				if( this.resynth )
				{
					/* B can be as large as 16, so this shift might overflow an int on a
					   16 - bit platform;  use a long to get defined behavior.*/
					final int cm_mask = (1 << B) - 1;
					fill &= cm_mask;// FIXME signed/unsigned
					if( 0 == fill )
					{
						// OPUS_CLEAR( X, N );
						for( int j = xoffset, je = xoffset + N; j < je; j++ ) {
							X[j] = 0;
						}
					} else {
						if( lowband == null )
						{
							/* Noise */
							for( int j = xoffset, je = xoffset + N; j < je; j++ )
							{
								this.seed = celt_lcg_rand( this.seed );
								X[j] = (float)(this.seed >> 20);
							}
							cm = cm_mask;
						} else {
							/* Folded spectrum */
							for( int j = xoffset, je = xoffset + N, jo = lboffset; j < je; j++, jo++ )
							{
								this.seed = celt_lcg_rand( this.seed );
								/* About 48 dB below the "normal" folding level */
								float tmp = 1.0f / 256f;
								tmp = ((this.seed & 0x8000) != 0) ? tmp : -tmp;
								X[j] = lowband[jo] + tmp;
							}
							cm = fill;// FIXME signed/unsigned
						}
						renormalise_vector( X, xoffset, N, gain );//, ctx.arch );
					}
				}
			}
		}

		return cm;
	}

	private static final int bit_interleave_table1[/* 16 */] = {
			0, 1, 1, 1, 2, 3, 3, 3, 2, 3, 3, 3, 2, 3, 3, 3
		};
	private static final int bit_deinterleave_table2[/* 16 */] = {
			0x00, 0x03, 0x0C, 0x0F, 0x30, 0x33, 0x3C, 0x3F,
			0xC0, 0xC3, 0xCC, 0xCF, 0xF0, 0xF3, 0xFC, 0xFF
		};
	/** This function is responsible for encoding and decoding a band for the mono case. */
	final int quant_band(
			final float[] X, int xoffset,// java
			final int N, final int b, int B,
			float[] lowband, int lboffset,// java
			final int LM,
			final float[] lowband_out, int outoffset,// java
			final float gain,
			final float[] lowband_scratch, final int soffset,// java
			int fill )
	{
		int N0 = N;
		int N_B = N;
		int B0 = B;
		int time_divide = 0;
		int recombine = 0;
		int cm = 0;
		final boolean is_encode = this.encode;// java renamed
		int curr_tf_change = this.tf_change;

		final boolean longBlocks = B0 == 1;

		N_B /= B;// FIXME why celt_udiv ?

		/* Special case for one sample */
		if( N == 1 )
		{
			return quant_band_n1( X, xoffset, null, 0,/* b,*/ lowband_out, outoffset );
		}

		if( curr_tf_change > 0 ) {
			recombine = curr_tf_change;
		/* Band recombining to increase frequency resolution */
		}

		if( null != lowband_scratch && null != lowband && (0 != recombine || ((N_B & 1) == 0 && curr_tf_change < 0) || B0 > 1) )
		{
			System.arraycopy( lowband, lboffset, lowband_scratch, soffset, N );
			lowband = lowband_scratch;
			lboffset = soffset;
		}

		for( int k = 0; k < recombine; k++ )
		{
			if( is_encode ) {
				haar1( X, xoffset, N >> k, 1 << k );
			}
			if( null != lowband ) {
				haar1( lowband, lboffset, N >> k, 1 << k );
			}
			fill = bit_interleave_table1[fill & 0xF] | bit_interleave_table1[fill >> 4] << 2;
		}
		B >>= recombine;
		N_B <<= recombine;

		/* Increasing the time resolution */
		while( (N_B & 1) == 0 && curr_tf_change < 0 )
		{
			if( is_encode ) {
				haar1( X, xoffset, N_B, B );
			}
			if( null != lowband ) {
				haar1( lowband, lboffset, N_B, B );
			}
			fill |= fill << B;
			B <<= 1;
			N_B >>= 1;
			time_divide++;
			curr_tf_change++;
		}
		B0 = B;
		final int N_B0 = N_B;

		/* Reorganize the samples in time order instead of frequency order */
		if( B0 > 1 )
		{
			if( is_encode ) {
				deinterleave_hadamard( X, xoffset, N_B >> recombine, B0 << recombine, longBlocks );
			}
			if( null != lowband ) {
				deinterleave_hadamard( lowband, lboffset, N_B >> recombine, B0 << recombine, longBlocks );
			}
		}

		cm = quant_partition( X, xoffset, N, b, B, lowband, lboffset, LM, gain, fill );

		/* This code is used by the decoder and by the resynthesis - enabled encoder */
		if( this.resynth )
		{
			/* Undo the sample reorganization going from time order to frequency order */
			if( B0 > 1 ) {
				interleave_hadamard( X, xoffset, N_B >> recombine, B0 << recombine, longBlocks );
			}

			/* Undo time - freq changes that we did earlier */
			N_B = N_B0;
			B = B0;
			for( int k = 0; k < time_divide; k++ )
			{
				B >>= 1;
				N_B <<=  1;
				cm |= cm >>> B;
				haar1( X, xoffset, N_B, B );
			}

			for( int k = 0; k < recombine; k++ )
			{
				cm = bit_deinterleave_table2[cm];
				haar1( X, xoffset, N0 >> k, 1 << k );
			}
			B <<= recombine;

			/* Scale output for later folding */
			if( null != lowband_out )
			{
				final float n = (float)Math.sqrt( (double)N0 );
				for( N0 += xoffset; xoffset < N0; ) {
					lowband_out[outoffset++] = n * X[xoffset++];
				}
			}
			cm &= (1 << B) - 1;
		}
		return cm;
	}


	/** This function is responsible for encoding and decoding a band for the stereo case. */
	final int quant_band_stereo(
			final float[] X, final int xoffset,// java
			final float[] Y, int yoffset,// java
			final int N, int b, final int B,
			final float[] lowband, final int lboffset,// java
			final int LM,
			final float[] lowband_out, final int outoffset,// java
			final float[] lowband_scratch, final int soffset,// java
			int fill)
	{
		// int imid = 0, iside = 0;// FIXME why need mid, iside?
		int cm = 0;
		final Jsplit_ctx sctx = new Jsplit_ctx();

		final boolean is_encode = this.encode;// java renamed
		final Jec_ctx entropy = this.ec;

		/* Special case for one sample */
		if( N == 1 )
		{
			return quant_band_n1( X, xoffset, Y, yoffset,/* b,*/ lowband_out, outoffset );
		}

		final int orig_fill = fill;

		final long b_fill = compute_theta( sctx, X, xoffset, Y, yoffset, N, b, B, B, LM, true, fill );
		b = (int)(b_fill >>> 32);// java
		fill = (int)b_fill;// java
		final boolean inv = sctx.inv;
		// imid = sctx.imid;
		// iside = sctx.iside;
		final int delta = sctx.delta;
		final int itheta = sctx.itheta;
		final int qalloc = sctx.qalloc;
/* #ifdef FIXED_POINT
		mid = imid;
		side = iside;
#else */
		final float mid = (1.f / 32768f) * sctx.imid;// imid;
		final float side = (1.f / 32768f) * sctx.iside;// iside;
// #endif

		/* This is a special case for N = 2 that only works for stereo and takes
		   advantage of the fact that mid and side are orthogonal to encode
		   the side with just one bit. */
		if( N == 2 )
		{
			int sign = 0;
			int mbits = b;
			int sbits = 0;
			/* Only need one bit for the side. */
			if( itheta != 0 && itheta != 16384 ) {
				sbits = 1 << Jec_ctx.BITRES;
			}
			mbits -= sbits;
			// final boolean c = itheta > 8192;
			this.remaining_bits -= qalloc + sbits;

			final float[] x2, y2;
			final int x2offset, y2offset;// java x2offset using together with x2, y2offset using together with y2
			if( itheta > 8192 ) {// if( c )
				x2 = Y;
				x2offset = yoffset;
				y2 = X;
				y2offset = xoffset;
			} else {
				x2 = X;
				x2offset = xoffset;
				y2 = Y;
				y2offset = yoffset;
			}
			if( 0 != sbits )
			{
				if( is_encode )
				{
					/* Here we only need to encode a sign for the side. */
					sign = (x2[x2offset] * y2[y2offset + 1] - x2[x2offset + 1] * y2[y2offset]) < 0 ? 1 : 0;
					((Jec_enc)entropy).ec_enc_bits( sign, 1 );
				} else {
					sign = ((Jec_dec)entropy).ec_dec_bits( 1 );
				}
			}
			sign = 1 - (sign << 1);
			/* We use orig_fill here because we want to fold the side, but if
			   itheta == 16384, we'll have cleared the low bits of fill. */
			cm = quant_band( x2, x2offset, N, mbits, B, lowband, lboffset,
					LM, lowband_out, outoffset, Jfloat_cast.Q15ONE, lowband_scratch, soffset, orig_fill );
			/* We don't split N = 2 bands, so cm is either 1 or 0 ( for a fold - collapse ),
			   and there's no need to worry about mixing with the other channel. */
			y2[y2offset] = -sign * x2[x2offset + 1];
			y2[y2offset + 1] = sign * x2[x2offset];
			if( this.resynth )
			{
				X[xoffset] = ( mid * X[xoffset] );
				X[xoffset + 1] = ( mid * X[xoffset + 1] );
				Y[yoffset] = ( side * Y[yoffset] );
				Y[yoffset + 1] = ( side * Y[yoffset + 1] );
				float tmp = X[xoffset];
				X[xoffset] = ( tmp - Y[yoffset] );
				Y[yoffset] = ( tmp + Y[yoffset] );
				tmp = X[xoffset + 1];
				X[xoffset + 1] = ( tmp - Y[yoffset + 1] );
				Y[yoffset + 1] = ( tmp + Y[yoffset + 1] );
			}
		} else {
			/* "Normal" split code */
			int mbits = (b - delta) / 2;// java
			mbits = b <= mbits ? b : mbits;
			mbits = 0 >= mbits ? 0 : mbits;
			int sbits = b - mbits;
			this.remaining_bits -= qalloc;

			int rebalance = this.remaining_bits;
			if( mbits >= sbits )
			{
				/* In stereo mode, we do not apply a scaling to the mid because we need the normalized
				   mid for folding later. */
				cm = quant_band( X, xoffset, N, mbits, B, lowband, lboffset, LM, lowband_out, outoffset, Jfloat_cast.Q15ONE, lowband_scratch, soffset, fill );
				rebalance = mbits - (rebalance - this.remaining_bits);
				if( rebalance > 3 << Jec_ctx.BITRES && itheta != 0 ) {
					sbits += rebalance - (3 << Jec_ctx.BITRES);
				}

				/* For a stereo split, the high bits of fill are always zero, so no
				folding will be done to the side. */
				cm |= quant_band( Y, yoffset, N, sbits, B,
							null, 0, LM, null, 0,
							side, null, 0, fill >> B );
			} else {
				/* For a stereo split, the high bits of fill are always zero, so no
				   folding will be done to the side. */
				cm = quant_band( Y, yoffset, N, sbits, B,
							null, 0, LM, null, 0,
							side, null, 0, fill >> B );
				rebalance = sbits - (rebalance - this.remaining_bits);
				if( rebalance > 3 << Jec_ctx.BITRES && itheta != 16384 ) {
					mbits += rebalance - (3 << Jec_ctx.BITRES);
				}
				/* In stereo mode, we do not apply a scaling to the mid because we need the normalized
				   mid for folding later. */
				cm |= quant_band( X, xoffset, N, mbits, B,
							lowband, lboffset, LM, lowband_out, outoffset,
							Jfloat_cast.Q15ONE, lowband_scratch, soffset, fill );
			}
		}


		/* This code is used by the decoder and by the resynthesis - enabled encoder */
		if( this.resynth )
		{
			if( N != 2 ) {
				stereo_merge( X, xoffset, Y, yoffset, mid, N );//, ctx.arch );
			}
			if( inv )
			{
				for( final int jn = yoffset + N; yoffset < jn; yoffset++ ) {
					Y[yoffset] = -Y[yoffset];
				}
			}
		}
		return cm;
	}
}
