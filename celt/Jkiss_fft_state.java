package celt;

/* This code is originally from Mark Borgerding's KISS-FFT but has been
heavily modified to better suit Opus */

// kiss_fft.h

/* The guts header contains all the multiplication and addition macros that are defined for
complex numbers.  It also delares the kf_ internal functions.
*/

public final class Jkiss_fft_state {
	/* java: not uses
	final class Jarch_fft_state {
		int is_supported;
		Object priv;
	}
	*/
	/** e.g. an fft of length 128 has 4 factors as far as kissfft is concerned 4*4*4*2 */
	private static final int MAXFACTORS = 8;

	int nfft;
	float scale;
/* #ifdef FIXED_POINT
	int scale_shift;
#endif */
	int shift;
	final short factors[] = new short[2 * MAXFACTORS];
	short[] bitrev;
	Jkiss_twiddle_cpx[] twiddles;
	// Jarch_fft_state arch_fft;// java not uses
	//
	Jkiss_fft_state(final int infft, final float iscale, final int ishift,
			final short[] ifactors, final short[] ibitrev,
			final Jkiss_twiddle_cpx[] itwiddles)//, final Jarch_fft_state iarch_fft)
	{
		nfft = infft;
		scale = iscale;
		shift = ishift;
		System.arraycopy( ifactors, 0, factors, 0, ifactors.length );
		bitrev = ibitrev;
		twiddles = itwiddles;
		// arch_fft = iarch_fft;
	}

	// start _kiss_fft_guts.h
/*
  Explanation of macros dealing with complex math:

   C_MUL(m,a,b)         : m = a*b
   C_FIXDIV( c , div )  : if a fixed point impl., c /= div. noop otherwise
   C_SUB( res, a,b)     : res = a - b
   C_SUBFROM( res , a)  : res -= a
   C_ADDTO( res , a)    : res += a
 * */
/* #ifdef FIXED_POINT
#include "arch.h"


#define SAMP_MAX 2147483647
#define TWID_MAX 32767
#define TRIG_UPSCALE 1

#define SAMP_MIN -SAMP_MAX


#   define S_MUL(a,b) MULT16_32_Q15(b, a)

#   define C_MUL(m,a,b) \
      do{ (m).r = SUB32_ovflw(S_MUL((a).r,(b).r) , S_MUL((a).i,(b).i)); \
          (m).i = ADD32_ovflw(S_MUL((a).r,(b).i) , S_MUL((a).i,(b).r)); }while(0)

#   define C_MULC(m,a,b) \
      do{ (m).r = ADD32_ovflw(S_MUL((a).r,(b).r) , S_MUL((a).i,(b).i)); \
          (m).i = SUB32_ovflw(S_MUL((a).i,(b).r) , S_MUL((a).r,(b).i)); }while(0)

#   define C_MULBYSCALAR( c, s ) \
      do{ (c).r =  S_MUL( (c).r , s ) ;\
          (c).i =  S_MUL( (c).i , s ) ; }while(0)

#   define DIVSCALAR(x,k) \
        (x) = S_MUL(  x, (TWID_MAX-((k)>>1))/(k)+1 )

#   define C_FIXDIV(c,div) \
        do {    DIVSCALAR( (c).r , div);  \
                DIVSCALAR( (c).i  , div); }while (0)

#define  C_ADD( res, a,b)\
    do {(res).r=ADD32_ovflw((a).r,(b).r);  (res).i=ADD32_ovflw((a).i,(b).i); \
    }while(0)
#define  C_SUB( res, a,b)\
    do {(res).r=SUB32_ovflw((a).r,(b).r);  (res).i=SUB32_ovflw((a).i,(b).i); \
    }while(0)
#define C_ADDTO( res , a)\
    do {(res).r = ADD32_ovflw((res).r, (a).r);  (res).i = ADD32_ovflw((res).i,(a).i);\
    }while(0)

#define C_SUBFROM( res , a)\
    do {(res).r = ADD32((res).r,(a).r);  (res).i = SUB32((res).i,(a).i); \
    }while(0)

#if defined(OPUS_ARM_INLINE_ASM)
#include "arm/kiss_fft_armv4.h"
#endif

#if defined(OPUS_ARM_INLINE_EDSP)
#include "arm/kiss_fft_armv5e.h"
#endif
#if defined(MIPSr1_ASM)
#include "mips/kiss_fft_mipsr1.h"
#endif

#else */ /* not FIXED_POINT*/

// #   define S_MUL(a,b) ( (a)*(b) )
/*
#define C_MUL(m,a,b) \
    do{ (m).r = (a).r*(b).r - (a).i*(b).i;\
        (m).i = (a).r*(b).i + (a).i*(b).r; }while(0)
*/
	/** C_MUL(m,a,b)         : m = a*b */
	/* private static final void C_MUL(final Jkiss_fft_cpx m, final Jkiss_fft_cpx a, final Jkiss_twiddle_cpx b) {// java extracted inplace
		m.r = a.r * b.r - a.i * b.i;
		m.i = a.r * b.i + a.i * b.r;
	}*/

/*
#define C_MULC(m,a,b) \
    do{ (m).r = (a).r*(b).r + (a).i*(b).i;\
        (m).i = (a).i*(b).r - (a).r*(b).i; }while(0)
*/


// #define C_MUL4(m,a,b) C_MUL(m,a,b)

// #   define C_FIXDIV(c,div) /* NOOP */
/*
#   define C_MULBYSCALAR( c, s ) \
    do{ (c).r *= (s);\
        (c).i *= (s); }while(0)
*/
// #endif

	/* private static final void C_MULBYSCALAR(final Jkiss_fft_cpx c, final float s) {// java extracted inplace
		c.r *= s;
		c.i *= s;
	}*/

// #ifndef CHECK_OVERFLOW_OP
// #  define CHECK_OVERFLOW_OP(a,op,b) /* noop */
// #endif

// #ifndef C_ADD
/*
#define  C_ADD( res, a,b)\
    do { \
            CHECK_OVERFLOW_OP((a).r,+,(b).r)\
            CHECK_OVERFLOW_OP((a).i,+,(b).i)\
            (res).r=(a).r+(b).r;  (res).i=(a).i+(b).i; \
    }while(0)
*/
	/* private static final void C_ADD(final Jkiss_fft_cpx res, final Jkiss_fft_cpx a, final Jkiss_fft_cpx b) {// java extracted inplace
		res.r = a.r + b.r; res.i = a.i + b.i;
	}*/

/*
#define  C_SUB( res, a,b)\
    do { \
            CHECK_OVERFLOW_OP((a).r,-,(b).r)\
            CHECK_OVERFLOW_OP((a).i,-,(b).i)\
            (res).r=(a).r-(b).r;  (res).i=(a).i-(b).i; \
    }while(0)
*/
	/* private static final void C_SUB(final Jkiss_fft_cpx res, final Jkiss_fft_cpx a, final Jkiss_fft_cpx b) {// java extracted inplace
		res.r = a.r - b.r; res.i = a.i - b.i;
	}*/
/*
#define C_ADDTO( res , a)\
    do { \
            CHECK_OVERFLOW_OP((res).r,+,(a).r)\
            CHECK_OVERFLOW_OP((res).i,+,(a).i)\
            (res).r += (a).r;  (res).i += (a).i;\
    }while(0)
*/
	/** C_ADDTO( res , a)    : res += a */
	/* private static final void C_ADDTO(final Jkiss_fft_cpx res, final Jkiss_fft_cpx a) {// java extracted inplace
		res.r += a.r; res.i += a.i;
	}*/
/*
#define C_SUBFROM( res , a)\
    do {\
            CHECK_OVERFLOW_OP((res).r,-,(a).r)\
            CHECK_OVERFLOW_OP((res).i,-,(a).i)\
            (res).r -= (a).r;  (res).i -= (a).i; \
    }while(0)
*/

// #endif /* C_ADD defined */

// #ifdef FIXED_POINT
/*#  define KISS_FFT_COS(phase)  TRIG_UPSCALE*floor(MIN(32767,MAX(-32767,.5+32768 * cos (phase))))
#  define KISS_FFT_SIN(phase)  TRIG_UPSCALE*floor(MIN(32767,MAX(-32767,.5+32768 * sin (phase))))*/
/*#  define KISS_FFT_COS(phase)  floor(.5+TWID_MAX*cos (phase))
#  define KISS_FFT_SIN(phase)  floor(.5+TWID_MAX*sin (phase))
#  define HALF_OF(x) ((x)>>1)
#elif defined(USE_SIMD)
#  define KISS_FFT_COS(phase) _mm_set1_ps( cos(phase) )
#  define KISS_FFT_SIN(phase) _mm_set1_ps( sin(phase) )
#  define HALF_OF(x) ((x)*_mm_set1_ps(.5f))
#else
#  define KISS_FFT_COS(phase) (kiss_fft_scalar) cos(phase)
#  define KISS_FFT_SIN(phase) (kiss_fft_scalar) sin(phase)
#  define HALF_OF(x) ((x)*.5f)
#endif */

/*
#define  kf_cexp(x,phase) \
        do{ \
                (x)->r = KISS_FFT_COS(phase);\
                (x)->i = KISS_FFT_SIN(phase);\
        }while(0)
*/

/*
#define  kf_cexp2(x,phase) \
   do{ \
      (x)->r = TRIG_UPSCALE*celt_cos_norm((phase));\
      (x)->i = TRIG_UPSCALE*celt_cos_norm((phase)-32768);\
}while(0)
*/
	// end _kiss_fft_guts.h

	// start kiss_fft.c
	private static final void kf_bfly2(final Jkiss_fft_cpx[] Fout, final int m, int N)
	{
		int foffset = 0;// java
		// (void)m;
/* #ifdef CUSTOM_MODES
		if( m == 1 )
		{
			celt_assert( m == 1 );
			for( i = 0; i < N; i++ )
			{
				kiss_fft_cpx t;
				Fout2 = Fout + 1;
				t = *Fout2;
				C_SUB( *Fout2, *Fout, t );
				C_ADDTO( *Fout, t );
				Fout += 2;
			}
		} else
#endif */
		{
			final float tw = 0.7071067812f;
			/* We know that m == 4 here because the radix-2 is just after a radix-4 */
			// celt_assert( m == 4 );
			final Jkiss_fft_cpx t = new Jkiss_fft_cpx();
			for( N <<= 3; foffset < N; )// ( int i = 0; i < N; i++ )
			{
				int Fout2 = foffset + 4;// Fout[Fout2]
				Jkiss_fft_cpx res2 = Fout[Fout2];
				t.copyFrom( res2 );
				Jkiss_fft_cpx res = Fout[foffset];
				res2.r = res.r - t.r; res2.i = res.i - t.i;// C_SUB( Fout[Fout2], Fout[foffset], t );
				res.r += t.r; res.i += t.i;// C_ADDTO( Fout[foffset], t );

				Fout2++;// java +1
				foffset++;// java +1
				res2 = Fout[Fout2];
				t.r = (res2.r + res2.i) * tw;
				t.i = (res2.i - res2.r) * tw;
				res = Fout[foffset];
				res2.r = res.r - t.r; res2.i = res.i - t.i;// C_SUB( Fout[Fout2], Fout[foffset], t );
				res.r += t.r; res.i += t.i;// C_ADDTO( Fout[foffset], t );

				Fout2++;// java +2
				foffset++;// java +2
				res2 = Fout[Fout2];
				t.r = res2.i;
				t.i = -res2.r;
				res = Fout[foffset];
				res2.r = res.r - t.r; res2.i = res.i - t.i;// C_SUB( Fout[Fout2], Fout[foffset], t );
				res.r += t.r; res.i += t.i;// C_ADDTO( Fout[foffset], t );

				Fout2++;// java +3
				foffset++;// java +3
				res2 = Fout[Fout2];
				t.r = (res2.i - res2.r) * tw;
				t.i = -(res2.i + res2.r) * tw;
				res = Fout[foffset];
				res2.r = res.r - t.r; res2.i = res.i - t.i;// C_SUB( Fout[Fout2], Fout[foffset], t );
				res.r += t.r; res.i += t.i;// C_ADDTO( Fout[foffset], t );
				foffset += 5;// 8;
			}
		}
	}

	private final void kf_bfly4(final Jkiss_fft_cpx[] Fout, final int fstride, final int m, int N, final int mm)
	{// FIXME using size_t
		if( m == 1 )
		{
			final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
			final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
			/* Degenerate case where all the twiddles are 1. */
			int foffset = 0;
			for( N <<= 2; foffset < N; )
			{
				final Jkiss_fft_cpx res = Fout[foffset];
				final Jkiss_fft_cpx res2 = Fout[foffset + 2];
				scratch0.r = res.r - res2.r; scratch0.i = res.i - res2.i;// C_SUB( scratch0, Fout[foffset], Fout[foffset + 2] );
				res.r += res2.r; res.i += res2.i;// C_ADDTO( Fout[foffset], Fout[foffset + 2] );
				final Jkiss_fft_cpx res1 = Fout[foffset + 1];
				final Jkiss_fft_cpx res3 = Fout[foffset + 3];
				scratch1.r = res1.r + res3.r; scratch1.i = res1.i + res3.i;// C_ADD( scratch1, Fout[foffset + 1], Fout[foffset + 3] );
				res2.r = res.r - scratch1.r; res2.i = res.i - scratch1.i;// C_SUB( Fout[foffset + 2], Fout[foffset], scratch1 );
				res.r += scratch1.r; res.i += scratch1.i;// C_ADDTO( Fout[foffset], scratch1 );
				scratch1.r = res1.r - res3.r; scratch1.i = res1.i - res3.i;// C_SUB( scratch1, Fout[foffset + 1], Fout[foffset + 3] );

				res1.r = scratch0.r + scratch1.i;
				res1.i = scratch0.i - scratch1.r;
				res3.r = scratch0.r - scratch1.i;
				res3.i = scratch0.i + scratch1.r;
				foffset += 4;
			}
			return;
		}
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch4 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch5 = new Jkiss_fft_cpx();
		final Jkiss_twiddle_cpx[] twiddles_ptr = this.twiddles;// java
		int tw1, tw2, tw3;
		final int m2 = m << 1;
		final int m3 = m2 + m;// java 3 * m
		final int f2 = fstride << 1;// java
		final int f3 = f2 + fstride;// java
		// final int Fout_beg = foffset;// Fout[Fout_beg]
		int i;
		for( i = 0, N *= mm; i < N; i += mm )
		{
			int foffset = i;
			tw3 = tw2 = tw1 = 0;// st.twiddles[];
			/* m is guaranteed to be a multiple of 4. */
			for( int j = 0; j < m; j++ )
			{
				final Jkiss_fft_cpx res1 = Fout[foffset + m];
				Jkiss_twiddle_cpx t = twiddles_ptr[tw1];
				scratch0.r = res1.r * t.r - res1.i * t.i;
				scratch0.i = res1.r * t.i + res1.i * t.r;// C_MUL( scratch0, Fout[foffset + m], twiddles_ptr[tw1] );
				final Jkiss_fft_cpx res2 = Fout[foffset + m2];
				t = twiddles_ptr[tw2];
				scratch1.r = res2.r * t.r - res2.i * t.i;
				scratch1.i = res2.r * t.i + res2.i * t.r;// C_MUL( scratch1, Fout[foffset + m2], twiddles_ptr[tw2] );
				final Jkiss_fft_cpx res3 = Fout[foffset + m3];
				t = twiddles_ptr[tw3];
				scratch2.r = res3.r * t.r - res3.i * t.i;
				scratch2.i = res3.r * t.i + res3.i * t.r;// C_MUL( scratch2, Fout[foffset + m3], twiddles_ptr[tw3] );

				final Jkiss_fft_cpx res = Fout[foffset];
				scratch5.r = res.r - scratch1.r; scratch5.i = res.i - scratch1.i;// C_SUB( scratch5, Fout[foffset], scratch1 );
				res.r += scratch1.r; res.i += scratch1.i;// C_ADDTO( Fout[foffset], scratch1 );
				scratch3.r = scratch0.r + scratch2.r; scratch3.i = scratch0.i + scratch2.i;// C_ADD( scratch3, scratch0, scratch2 );
				scratch4.r = scratch0.r - scratch2.r; scratch4.i = scratch0.i - scratch2.i;// C_SUB( scratch4, scratch0, scratch2 );
				res2.r = res.r - scratch3.r; res2.i = res.i - scratch3.i;// C_SUB( Fout[foffset + m2], Fout[foffset], scratch3 );
				tw1 += fstride;
				tw2 += f2;// java
				tw3 += f3;// java
				res.r += scratch3.r; res.i += scratch3.i;// C_ADDTO( Fout[foffset], scratch3 );

				res1.r = scratch5.r + scratch4.i;
				res1.i = scratch5.i - scratch4.r;
				res3.r = scratch5.r - scratch4.i;
				res3.i = scratch5.i + scratch4.r;
				++foffset;
			}
		}
	}


// #ifndef RADIX_TWO_ONLY

	private final void kf_bfly3(final Jkiss_fft_cpx[] Fout, final int fstride, final int m, int N, final int mm)
	{// FIXME using size_t
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();
		new Jkiss_fft_cpx();

		final Jkiss_twiddle_cpx[] twiddles_ptr = this.twiddles;// java
// #ifdef FIXED_POINT
		/*epi3.r = -16384;*/ /* Unused */
//		epi3.i = -28378;
// #else
		// final Jkiss_twiddle_cpx epi3 = new Jkiss_twiddle_cpx( twiddles_ptr[fstride * m] );
		final float epi3_i = twiddles_ptr[fstride * m].i;// java uses only image part
// #endif
		final int m2 = m << 1;
		final int f2 = fstride << 1;// java
		// final int Fout_beg = foffset;// Fout[Fout_beg]
		int i;
		for( i = 0, N *= mm; i < N; i += mm )
		{
			// int foffset = Fout_beg + i;
			int foffset = i;
			int tw1 = 0, tw2 = 0;// st.twiddles;
			/* For non-custom modes, m is guaranteed to be a multiple of 4. */
			int k = m;
			do {
				final Jkiss_fft_cpx res1 = Fout[foffset + m];
				Jkiss_twiddle_cpx t = twiddles_ptr[tw1];
				scratch1.r = res1.r * t.r - res1.i * t.i;
				scratch1.i = res1.r * t.i + res1.i * t.r;// C_MUL( scratch1, Fout[foffset + m], twiddles_ptr[tw1] );
				final Jkiss_fft_cpx res2 = Fout[foffset + m2];
				t = twiddles_ptr[tw2];
				scratch2.r = res2.r * t.r - res2.i * t.i;
				scratch2.i = res2.r * t.i + res2.i * t.r;// C_MUL( scratch2, Fout[foffset + m2], twiddles_ptr[tw2] );

				scratch3.r = scratch1.r + scratch2.r; scratch3.i = scratch1.i + scratch2.i;// C_ADD( scratch3, scratch1, scratch2 );
				scratch0.r = scratch1.r - scratch2.r; scratch0.i = scratch1.i - scratch2.i;// C_SUB( scratch0, scratch1, scratch2 );
				tw1 += fstride;
				tw2 += f2;

				final Jkiss_fft_cpx res = Fout[foffset];
				res1.r = res.r - .5f * scratch3.r;
				res1.i = res.i - .5f * scratch3.i;

				scratch0.r *= epi3_i;// C_MULBYSCALAR( scratch[0], epi3.i );
				scratch0.i *= epi3_i;

				res.r += scratch3.r; res.i += scratch3.i;// C_ADDTO( Fout[foffset], scratch3 );

				res2.r = res1.r + scratch0.i;
				res2.i = res1.i - scratch0.r;

				res1.r -= scratch0.i;
				res1.i += scratch0.r;

				++foffset;
			} while( --k != 0 );
		}
	}


// #ifndef OVERRIDE_kf_bfly5
	private final void kf_bfly5(final Jkiss_fft_cpx[] Fout, final int fstride, final int m, int N, final int mm)
	{// FIXME using size_t
		// int foffset = 0;// java Fout[ foffset ]
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch4 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch5 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch6 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch7 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch8 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch9 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch10 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch11 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch12 = new Jkiss_fft_cpx();

		final int m2 = m << 1;// java
		final int m3 = m2 + m;// java
		final int m4 = m2 << 1;// java
		final int mf = m * fstride;// java
/* #ifdef FIXED_POINT
		ya.r = 10126;
		ya.i = -31164;
		yb.r = -26510;
		yb.i = -19261;
#else */
		final Jkiss_twiddle_cpx[] tw = this.twiddles;
		final Jkiss_twiddle_cpx ya = new Jkiss_twiddle_cpx( tw[mf] );
		final Jkiss_twiddle_cpx yb = new Jkiss_twiddle_cpx( tw[mf << 1] );
// #endif

		// final int Fout_beg = foffset;// Fout[ Fout_beg ]
		int i;
		for( i = 0, N *= mm; i < N; i += mm )
		{
			// foffset = Fout_beg + i * mm;
			int Fout0 = i;// foffset;// Fout[ Fout0 ]
			int Fout1 = Fout0 + m;// Fout[ Fout1 ]
			int Fout2 = Fout0 + m2;// Fout[ Fout2 ]
			int Fout3 = Fout0 + m3;// Fout[ Fout3 ]
			int Fout4 = Fout0 + m4;// Fout[ Fout4 ]

			/* For non-custom modes, m is guaranteed to be a multiple of 4. */
			for( int u = 0; u < mf; u += fstride ) {
				final Jkiss_fft_cpx res0 = Fout[Fout0];
				final Jkiss_fft_cpx res1 = Fout[Fout1];
				final Jkiss_fft_cpx res2 = Fout[Fout2];
				final Jkiss_fft_cpx res3 = Fout[Fout3];
				final Jkiss_fft_cpx res4 = Fout[Fout4];
				scratch0.copyFrom( res0 );

				Jkiss_twiddle_cpx t = tw[u];
				scratch1.r = res1.r * t.r - res1.i * t.i;
				scratch1.i = res1.r * t.i + res1.i * t.r;// C_MUL( scratch1, Fout[ Fout1 ], tw[u]);
				int u2 = u << 1;// java
				t = tw[u2];
				scratch2.r = res2.r * t.r - res2.i * t.i;
				scratch2.i = res2.r * t.i + res2.i * t.r;// C_MUL( scratch2, Fout[ Fout2 ], tw[u2]);
				u2 += u;
				t = tw[u2];
				scratch3.r = res3.r * t.r - res3.i * t.i;
				scratch3.i = res3.r * t.i + res3.i * t.r;// C_MUL( scratch3, Fout[ Fout3 ], tw[u2 + u]);
				u2 += u;
				t = tw[u2];
				scratch4.r = res4.r * t.r - res4.i * t.i;
				scratch4.i = res4.r * t.i + res4.i * t.r;// C_MUL( scratch4, Fout[ Fout4 ], tw[u2]);

				scratch7.r = scratch1.r + scratch4.r; scratch7.i = scratch1.i + scratch4.i;// C_ADD( scratch7, scratch1, scratch4 );
				scratch10.r = scratch1.r - scratch4.r; scratch10.i = scratch1.i - scratch4.i;// C_SUB( scratch10, scratch1, scratch4 );
				scratch8.r = scratch2.r + scratch3.r; scratch8.i = scratch2.i + scratch3.i;// C_ADD( scratch8, scratch2, scratch3 );
				scratch9.r = scratch2.r - scratch3.r; scratch9.i = scratch2.i - scratch3.i;// C_SUB( scratch9, scratch2, scratch3 );

				res0.r += scratch7.r + scratch8.r;
				res0.i += scratch7.i + scratch8.i;

				scratch5.r = scratch0.r + (scratch7.r * ya.r) + (scratch8.r * yb.r);
				scratch5.i = scratch0.i + (scratch7.i * ya.r) + (scratch8.i * yb.r);

				scratch6.r =  (scratch10.i * ya.i) + (scratch9.i * yb.i);
				scratch6.i = -((scratch10.r * ya.i) + (scratch9.r * yb.i));

				res1.r = scratch5.r - scratch6.r; res1.i = scratch5.i - scratch6.i;// C_SUB( Fout[ Fout1 ], scratch5, scratch6 );
				res4.r = scratch5.r + scratch6.r; res4.i = scratch5.i + scratch6.i;// C_ADD( Fout[ Fout4 ], scratch5, scratch6 );

				scratch11.r = scratch0.r + (scratch7.r * yb.r) + (scratch8.r * ya.r);
				scratch11.i = scratch0.i + (scratch7.i * yb.r) + (scratch8.i * ya.r);
				scratch12.r = (scratch9.i * ya.i) - (scratch10.i * yb.i);
				scratch12.i = (scratch10.r * yb.i) - (scratch9.r * ya.i);

				res2.r = scratch11.r + scratch12.r; res2.i = scratch11.i + scratch12.i;// C_ADD( Fout[Fout2], scratch11, scratch12 );
				res3.r = scratch11.r - scratch12.r; res3.i = scratch11.i - scratch12.i;// C_SUB( Fout[Fout3], scratch11, scratch12 );

				++Fout0; ++Fout1; ++Fout2; ++Fout3; ++Fout4;
			}
		}
	}
// #endif /* OVERRIDE_kf_bfly5 */

// #endif


/* #ifdef CUSTOM_MODES

	static void compute_bitrev_table(int Fout, short[] f, final size_t fstride, int in_stride, short[] factors, final Jkiss_fft_state st)
	{
		final int p = *factors++; // the radix
		final int m = *factors++; // stage's fft length/p

		// printf ("fft %d %d %d %d %d %d\n", p*m, m, p, s2, fstride*in_stride, N);
		if( m == 1 )
		{
			int j;
			for( j = 0; j < p; j++ )
			{
				*f = Fout + j;
				f += fstride * in_stride;
			}
		} else {
			int j;
			for( j = 0; j < p; j++ )
			{
				compute_bitrev_table( Fout, f, fstride * p, in_stride, factors, st );
				f += fstride * in_stride;
				Fout += m;
			}
		}
	}
*/
	/**  facbuf is populated by p1,m1,p2,m2, ...
	where
	p[i] * m[i] = m[i-1]
	m0 = n                  */
/*	static int kf_factor(int n,opus_int16 * facbuf)
	{
		int p = 4;
		int i;
		int stages = 0;
		int nbak = n;

		// factor out powers of 4, powers of 2, then any remaining primes
		do {
			while (n % p) {
				switch (p) {
				case 4: p = 2; break;
				case 2: p = 3; break;
				default: p += 2; break;
				}
				if( p > 32000 || (opus_int32)p * (opus_int32)p > n )
					p = n;          // no more factors, skip to end
			}
			n /= p;
#ifdef RADIX_TWO_ONLY
			if( p != 2 && p != 4 )
#else
			if( p > 5 )
#endif
			{
				return 0;
			}
			facbuf[2 * stages] = p;
			if( p == 2 && stages > 1 )
			{
				facbuf[2 * stages] = 4;
				facbuf[2] = 2;
			}
			stages++;
		} while( n > 1 );
		n = nbak;
		// Reverse the order to get the radix 4 at the end, so we can use the
		// fast degenerate case. It turns out that reversing the order also
		// improves the noise behaviour.
		for( i = 0; i < stages / 2; i++ )
		{
			int tmp;
			tmp = facbuf[2 * i];
			facbuf[2 * i] = facbuf[2 * (stages - i - 1)];
			facbuf[2 * (stages - i - 1)] = tmp;
		}
		for( i = 0; i < stages; i++ )
		{
			n /= facbuf[2 * i];
			facbuf[2 * i + 1] = n;
		}
		return 1;
	}

	static void compute_twiddles(kiss_twiddle_cpx *twiddles, int nfft)
	{
		int i;
#ifdef FIXED_POINT
		for( i = 0; i < nfft; ++i ) {
			opus_val32 phase = -i;
			kf_cexp2( twiddles + i, DIV32(SHL32(phase, 17), nfft) );
		}
#else
		for( i = 0; i < nfft; ++i ) {
			const double pi = 3.14159265358979323846264338327;
			double phase = (-2 * pi / nfft) * i;
			kf_cexp( twiddles + i, phase );
		}
#endif
	}

	int opus_fft_alloc_arch_c(kiss_fft_state *st) {
		(void)st;
		return 0;
	}
*/
	/**
	 *
	 * Allocates all necessary storage space for the fft and ifft.
	 * The return value is a contiguous block of memory.  As such,
	 * It can be freed with free().
	 * */
/*	kiss_fft_state *opus_fft_alloc_twiddles(int nfft,void * mem,size_t * lenmem,
			const kiss_fft_state *base, int arch)
	{
		kiss_fft_state *st = NULL;
		size_t memneeded = sizeof(struct kiss_fft_state); // twiddle factors

		if( lenmem == NULL ) {
			st = ( kiss_fft_state*)KISS_FFT_MALLOC( memneeded );
		} else {
			if( mem != NULL && *lenmem >= memneeded )
				st = (kiss_fft_state*)mem;
			*lenmem = memneeded;
		}
		if( st ) {
			opus_int16 *bitrev;
			kiss_twiddle_cpx *twiddles;

			st.nfft = nfft;
#ifdef FIXED_POINT
			st.scale_shift = celt_ilog2( st.nfft );
			if( st.nfft == 1 << st.scale_shift )
				st.scale = Q15ONE;
			else
				st.scale = (1073741824 + st.nfft / 2) / st.nfft >> (15 - st.scale_shift);
#else
			st.scale = 1.f / nfft;
#endif
			if( base != NULL )
			{
				st.twiddles = base.twiddles;
				st.shift = 0;
				while( st.shift < 32 && nfft << st.shift != base.nfft )
					st.shift++;
				if( st.shift >= 32 )
					goto fail;
			} else {
				st.twiddles = twiddles = (kiss_twiddle_cpx*)KISS_FFT_MALLOC( sizeof(kiss_twiddle_cpx)*nfft );
				compute_twiddles( twiddles, nfft );
				st.shift = -1;
			}
			if( ! kf_factor( nfft, st.factors ) )
			{
				goto fail;
			}

			// bitrev
			st.bitrev = bitrev = (opus_int16*)KISS_FFT_MALLOC( sizeof(opus_int16)*nfft );
			if( st.bitrev == NULL )
				goto fail;
			compute_bitrev_table( 0, bitrev, 1, 1, st.factors, st );

			// Initialize architecture specific fft parameters
			if( opus_fft_alloc_arch( st, arch ) )
				goto fail;
		}
		return st;
	fail:
		opus_fft_free( st, arch );
		return NULL;
	}

	kiss_fft_state *opus_fft_alloc(int nfft,void * mem,size_t * lenmem, int arch)
	{
		return opus_fft_alloc_twiddles( nfft, mem, lenmem, NULL, arch );
	}

	void opus_fft_free_arch_c(kiss_fft_state *st) {
		(void)st;
	}

	void opus_fft_free(const kiss_fft_state *cfg, int arch)
	{
		if( cfg )
		{
			opus_fft_free_arch( (kiss_fft_state *)cfg, arch );
			opus_free( (opus_int16*)cfg.bitrev );
			if( cfg.shift < 0 )
				opus_free( (kiss_twiddle_cpx*)cfg.twiddles );
			opus_free( (kiss_fft_state*)cfg );
		}
	}

#endif */ /* CUSTOM_MODES */

	final void opus_fft_impl(final Jkiss_fft_cpx[] fout)
	{
		final short[] f = this.factors;// java
		final int fstride[] = new int[MAXFACTORS];

		/* st.shift can be -1 */
		int sh = this.shift;
		if( sh < 0 ) {
			sh = 0;
		}

		fstride[0] = 1;
		int L = 0;
		int m;
		do {
			int L2 = L << 1;// java
			final int p = f[L2++];
			m = f[L2];
			fstride[L + 1] = fstride[L] * p;
			L++;
		} while( m != 1 );
		m = f[(L << 1) - 1];
		for( int i = L - 1; i >= 0; i-- )
		{
			final int i2 = i << 1;// java
			final int m2 = i != 0 ? f[i2 - 1] : 1;
			switch( f[i2] )
			{
			case 2:
				kf_bfly2( fout, m, fstride[i] );
				break;
			case 4:
				kf_bfly4( fout, fstride[i] << sh, m, fstride[i], m2 );
				break;
// #ifndef RADIX_TWO_ONLY
			case 3:
				kf_bfly3( fout, fstride[i] << sh, m, fstride[i], m2 );
				break;
			case 5:
				kf_bfly5( fout, fstride[i] << sh, m, fstride[i], m2 );
				break;
// #endif
			}
			m = m2;
		}
	}

	/**
	 * opus_fft(cfg,in_out_buf)
	 *
	 * Perform an FFT on a complex input buffer.
	 * for a forward FFT,
	 * fin should be  f[0] , f[1] , ... ,f[nfft-1]
	 * fout will be   F[0] , F[1] , ... ,F[nfft-1]
	 * Note that each element is complex and can be accessed like
	    f[k].r and f[k].i
	 * */
	//private static final void opus_fft_c(final Jkiss_fft_state st, final Jkiss_fft_cpx[] fin, final Jkiss_fft_cpx[] fout)
	public final void opus_fft(final Jkiss_fft_cpx[] fin, final Jkiss_fft_cpx[] fout)
	{// java renamed
/* #ifdef FIXED_POINT
		// Allows us to scale with MULT16_32_Q16(), which is faster than MULT16_32_Q15() on ARM.
		int scale_shift = st.scale_shift - 1;
#endif */
		final float s = this.scale;
		final short[] rev = this.bitrev;// java

		// celt_assert2( fin != fout, "In-place FFT not supported");
		/* Bit-reverse the input */
		for( int i = 0; i < this.nfft; i++ )
		{
			final Jkiss_fft_cpx x = fin[i];
			final Jkiss_fft_cpx res = fout[ rev[i] ];
			res.r = s * x.r;
			res.i = s * x.i;
		}
		opus_fft_impl( fout );
	}

	/* private final void opus_ifft_c(final Jkiss_fft_cpx[] fin, final Jkiss_fft_cpx[] fout)
	{
		// celt_assert2( fin != fout, "In-place FFT not supported");
		final short[] rev = this.bitrev;// java
		// Bit-reverse the input
		for( int i = 0; i < this.nfft; i++ ) {
			fout[ rev[i] ].set( fin[i] );
		}
		for( int i = 0; i < this.nfft; i++ ) {
			fout[i].i = -fout[i].i;
		}
		opus_fft_impl( fout );
		for( int i = 0; i < this.nfft; i++ ) {
			fout[i].i = -fout[i].i;
		}
	}*/
	// end kiss_fft.c

	// start java special version

	/* private static final void C_SUB(final float[] res, int roffset, final Jkiss_fft_cpx a, final Jkiss_fft_cpx b) {// java extracted inplace
		// res.r = a.r - b.r; res.i = a.i - b.i;
		res[roffset] = a.r - b.r; res[++roffset] = a.i - b.i;
	}*/
	/* private static final void C_SUB(final Jkiss_fft_cpx res, final float[] a, int aoffset, final float[] b, int boffset) {// java extracted inplace
		// res.r = a.r - b.r; res.i = a.i - b.i;
		res.r = a[aoffset] - b[boffset]; res.i = a[++aoffset] - b[++boffset];
	}*/
	/* private static final void C_SUB(final float[] res, int roffset, final float[] a, int aoffset, final float br, final float bi) {// java extracted inplace
		// res.r = a.r - b.r; res.i = a.i - b.i;
		res[roffset] = a[aoffset] - br; res[++roffset] = a[++aoffset] - bi;
	}*/
	/* private static final void C_SUB(final Jkiss_fft_cpx res, final float[] a, int aoffset, final Jkiss_fft_cpx b) {// java extracted inplace
		// res.r = a.r - b.r; res.i = a.i - b.i;
		res.r = a[aoffset] - b.r; res.i = a[++aoffset] - b.i;
	}*/
	/* private static final void C_SUB(final float[] res, int roffset, final float[] a, int aoffset, final Jkiss_fft_cpx b) {// java extracted inplace
		// res.r = a.r - b.r; res.i = a.i - b.i;
		res[roffset] = a[aoffset] - b.r; res[++roffset] = a[++aoffset] - b.i;
	}*/
	/* private static final void C_ADD(final float[] res, int roffset, final Jkiss_fft_cpx a, final Jkiss_fft_cpx b) {// java extracted inplace
		// res.r = a.r + b.r; res.i = a.i + b.i;
		res[roffset] = a.r + b.r; res[++roffset] = a.i + b.i;
	}*/
	/* private static final void C_ADD(final Jkiss_fft_cpx res, final float[] a, int aoffset, final float[] b, int boffset) {// java extracted inplace
		// res.r = a.r + b.r; res.i = a.i + b.i;
		res.r = a[aoffset] + b[boffset]; res.i = a[++aoffset] + b[++boffset];
	}*/
	/** C_ADDTO( res , a)    : res += a */
	/* private static final void C_ADDTO(final float[] res, int roffset, final float[] a, int aoffset) {// java extracted inplace
		// res.r += a.r; res.i += a.i;
		res[roffset] += a[aoffset]; res[++roffset] += a[++aoffset];
	}*/
	/* private static final void C_ADDTO(final float[] res, int roffset, final Jkiss_fft_cpx a) {// java extracted inplace
		// res.r += a.r; res.i += a.i;
		res[roffset] += a.r; res[++roffset] += a.i;
	}*/
	/* private static final void C_ADDTO(final float[] res, int roffset, final float ar, final float ai) {// java extracted inplace
		// res.r += a.r; res.i += a.i;
		res[roffset] += ar; res[++roffset] += ai;
	}*/
	/* private static final void C_MUL(final Jkiss_fft_cpx m, final float[] a, int aoffset, final Jkiss_twiddle_cpx b) {// java extracted inplace
		final float ar = a[aoffset];
		final float ai = a[++aoffset];
		// m.r = a.r * b.r - a.i * b.i;
		m.r = ar * b.r - ai * b.i;
		// m.i = a.r * b.i + a.i * b.r;
		m.i = ar * b.i + ai * b.r;
	}*/
	/**
	 * special version.
	 * fout[ r,i, r,i, r,i ... ]
	 *
	 * @param Fout
	 * @param foffset
	 * @param m
	 * @param N
	 */
	private static final void kf_bfly2(final float[] Fout, int foffset, final int m, int N)
	{
		// (void)m;
/* #ifdef CUSTOM_MODES
		if( m == 1 )
		{
			celt_assert( m == 1 );
			for( i = 0; i < N; i++ )
			{
				kiss_fft_cpx t;
				Fout2 = Fout + 1;
				t = *Fout2;
				C_SUB( *Fout2, *Fout, t );
				C_ADDTO( *Fout, t );
				Fout += 2;
			}
		} else
#endif */
		{
			final float tw = 0.7071067812f;
			/* We know that m == 4 here because the radix-2 is just after a radix-4 */
			// celt_assert( m == 4 );
			float tr, ti;
			for( N = foffset + (N << (3 + 1)); foffset < N; )// ( int i = 0; i < N; i++ )
			{
				int Fout2 = foffset + 4 * 2;// Fout[Fout2]
				tr = Fout[Fout2++];
				ti = Fout[Fout2--];
				Fout[Fout2++] = Fout[foffset++] - tr; Fout[Fout2++] = Fout[foffset--] - ti;// C_SUB( Fout, Fout2, Fout, foffset, tr, ti );
				Fout[foffset++] += tr; Fout[foffset++] += ti;// C_ADDTO( Fout, foffset, tr, ti );

				// Fout2++; Fout2++;// java +1
				// foffset++; foffset++;// java +1
				tr = (Fout[Fout2] + Fout[Fout2 + 1]) * tw;
				ti = (Fout[Fout2 + 1] - Fout[Fout2]) * tw;
				Fout[Fout2++] = Fout[foffset++] - tr; Fout[Fout2++] = Fout[foffset--] - ti;// C_SUB( Fout, Fout2, Fout, foffset, tr, ti );
				Fout[foffset++] += tr; Fout[foffset++] += ti;// C_ADDTO( Fout, foffset, tr, ti );

				// Fout2++; Fout2++;// java +2
				// foffset++; foffset++;// java +2
				tr = Fout[++Fout2];
				ti = -Fout[--Fout2];
				Fout[Fout2++] = Fout[foffset++] - tr; Fout[Fout2++] = Fout[foffset--] - ti;// C_SUB( Fout, Fout2, Fout, foffset, tr, ti );
				Fout[foffset++] += tr; Fout[foffset++] += ti;// C_ADDTO( Fout, foffset, tr, ti );

				// Fout2++; Fout2++;// java +3
				// foffset++; foffset++;// java +3
				tr = (Fout[Fout2 + 1] - Fout[Fout2]) * tw;
				ti = -(Fout[Fout2 + 1] + Fout[Fout2]) * tw;
				Fout[Fout2++] = Fout[foffset++] - tr; Fout[Fout2] = Fout[foffset--] - ti;// C_SUB( Fout, Fout2, Fout, foffset, tr, ti );
				Fout[foffset++] += tr; Fout[foffset++] += ti;// C_ADDTO( Fout, foffset, tr, ti );
				foffset += 4 * 2;// 5 * 2;// 8;
			}
		}
	}

	private final void kf_bfly4(final float[] Fout, int foffset, final int fstride, final int m, int N, int mm)
	{
		if( m == 1 )
		{
			final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
			final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
			/* Degenerate case where all the twiddles are 1. */
			for( N = foffset + (N << (2 + 1)); foffset < N; )
			{
				int off1 = foffset + 1 * 2;
				int off2 = foffset + 2 * 2;
				int off3 = foffset + 3 * 2;
				scratch0.r = Fout[foffset++] - Fout[off2++]; scratch0.i = Fout[foffset--] - Fout[off2--];// C_SUB( scratch0, Fout, foffset, Fout, off2 );
				Fout[foffset++] += Fout[off2++]; Fout[foffset--] += Fout[off2--];// C_ADDTO( Fout, foffset, Fout, off2 );
				scratch1.r = Fout[off1++] + Fout[off3++]; scratch1.i = Fout[off1--] + Fout[off3--];// C_ADD( scratch1, Fout, off1, Fout, off3 );
				Fout[off2++] = Fout[foffset++] - scratch1.r; Fout[off2] = Fout[foffset--] - scratch1.i;// C_SUB( Fout, off2, Fout, foffset, scratch1 );
				Fout[foffset++] += scratch1.r; Fout[foffset++] += scratch1.i;// C_ADDTO( Fout, foffset, scratch1 );
				scratch1.r = Fout[off1++] - Fout[off3++]; scratch1.i = Fout[off1] - Fout[off3];// C_SUB( scratch1, Fout, off1, Fout, off3 );

				Fout[off1--] = scratch0.i - scratch1.r;
				Fout[off1] = scratch0.r + scratch1.i;
				Fout[off3--] = scratch0.i + scratch1.r;
				Fout[off3] = scratch0.r - scratch1.i;
				foffset += 3 * 2;// 4 - 1
			}
			return;
		}
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch4 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch5 = new Jkiss_fft_cpx();
		final Jkiss_twiddle_cpx[] twiddles_ptr = this.twiddles;// java
		int tw1, tw2, tw3;
		final int m_2 = m << 1;// java
		final int m2_2 = m_2 << 1;
		final int m3_2 = m2_2 + m_2;// java 3 * m
		final int f2 = fstride << 1;// java
		final int f3 = f2 + fstride;// java
		mm <<= 1;// java
		// final int Fout_beg = foffset;// Fout[Fout_beg]
		int i;
		for( i = foffset, N = foffset + N * mm; i < N; i += mm )
		{
			foffset = i;
			tw3 = tw2 = tw1 = 0;// st.twiddles[];
			/* m is guaranteed to be a multiple of 4. */
			for( int j = 0; j < m; j++ )
			{
				int off1 = foffset + m_2;
				int off2 = foffset + m2_2;
				int off3 = foffset + m3_2;
				Jkiss_twiddle_cpx t = twiddles_ptr[tw1];
				float ar = Fout[off1++];
				float ai = Fout[off1];
				scratch0.r = ar * t.r - ai * t.i;
				scratch0.i = ar * t.i + ai * t.r;// C_MUL( scratch0, Fout, off1, twiddles_ptr[tw1] );
				t = twiddles_ptr[tw2];
				ar = Fout[off2++];
				ai = Fout[off2--];
				scratch1.r = ar * t.r - ai * t.i;
				scratch1.i = ar * t.i + ai * t.r;// C_MUL( scratch1, Fout, off2, twiddles_ptr[tw2] );
				t = twiddles_ptr[tw3];
				ar = Fout[off3++];
				ai = Fout[off3];
				scratch2.r = ar * t.r - ai * t.i;
				scratch2.i = ar * t.i + ai * t.r;// C_MUL( scratch2, Fout, off3, twiddles_ptr[tw3] );

				scratch5.r = Fout[foffset++] - scratch1.r; scratch5.i = Fout[foffset--] - scratch1.i;// C_SUB( scratch5, Fout, foffset, scratch1 );
				Fout[foffset++] += scratch1.r; Fout[foffset--] += scratch1.i;// C_ADDTO( Fout, foffset, scratch1 );
				scratch3.r = scratch0.r + scratch2.r; scratch3.i = scratch0.i + scratch2.i;// C_ADD( scratch3, scratch0, scratch2 );
				scratch4.r = scratch0.r - scratch2.r; scratch4.i = scratch0.i - scratch2.i;// C_SUB( scratch4, scratch0, scratch2 );
				Fout[off2++] = Fout[foffset++] - scratch3.r; Fout[off2] = Fout[foffset--] - scratch3.i;// C_SUB( Fout, off2, Fout, foffset, scratch3 );
				tw1 += fstride;
				tw2 += f2;// java
				tw3 += f3;// java
				Fout[foffset++] += scratch3.r; Fout[foffset++] += scratch3.i;// C_ADDTO( Fout, foffset, scratch3 );

				Fout[off1--] = scratch5.i - scratch4.r;
				Fout[off1] = scratch5.r + scratch4.i;
				Fout[off3--] = scratch5.i + scratch4.r;
				Fout[off3] = scratch5.r - scratch4.i;
				// ++foffset; ++foffset;
			}
		}
	}


// #ifndef RADIX_TWO_ONLY

	private final void kf_bfly3(final float[] Fout, int foffset, final int fstride, final int m, int N, int mm)
	{
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();

		final Jkiss_twiddle_cpx[] twiddles_ptr = this.twiddles;// java
/* #ifdef FIXED_POINT
		epi3.r = -16384;
		epi3.i = -28378;
#else */
		// final Jkiss_twiddle_cpx epi3 = new Jkiss_twiddle_cpx( twiddles_ptr[fstride * m] );
		final float epi3_i = twiddles_ptr[fstride * m].i;// java uses only image part
// #endif
		final int m_2 = m << 1;// java
		final int m2_2 = m_2 << 1;
		final int f2 = fstride << 1;// java
		mm <<= 1;// java
		// final int Fout_beg = foffset;// Fout[Fout_beg]
		int i;
		for( i = foffset, N = foffset + N * mm; i < N; i += mm )
		{
			foffset = i;
			int tw1 = 0, tw2 = 0;// st.twiddles;
			/* For non-custom modes, m is guaranteed to be a multiple of 4. */
			int k = m;
			do {
				int off1 = foffset + m_2;
				int off2 = foffset + m2_2;
				Jkiss_twiddle_cpx t = twiddles_ptr[tw1];
				float ar = Fout[off1++];
				float ai = Fout[off1--];
				scratch1.r = ar * t.r - ai * t.i;
				scratch1.i = ar * t.i + ai * t.r;// C_MUL( scratch1, Fout, off1, twiddles_ptr[tw1] );
				t = twiddles_ptr[tw2];
				ar = Fout[off2++];
				ai = Fout[off2];
				scratch2.r = ar * t.r - ai * t.i;
				scratch2.i = ar * t.i + ai * t.r;// C_MUL( scratch2, Fout, off2, twiddles_ptr[tw2] );

				scratch3.r = scratch1.r + scratch2.r; scratch3.i = scratch1.i + scratch2.i;// C_ADD( scratch3, scratch1, scratch2 );
				scratch0.r = scratch1.r - scratch2.r; scratch0.i = scratch1.i - scratch2.i;// C_SUB( scratch0, scratch1, scratch2 );
				tw1 += fstride;
				tw2 += f2;

				Fout[off1++] = Fout[foffset] - .5f * scratch3.r;
				Fout[off1] = Fout[foffset + 1] - .5f * scratch3.i;

				scratch0.r *= epi3_i;// C_MULBYSCALAR( scratch[0], epi3.i );
				scratch0.i *= epi3_i;

				Fout[foffset++] += scratch3.r; Fout[foffset++] += scratch3.i;// C_ADDTO( Fout, foffset, scratch3 );

				Fout[off2--] = Fout[off1--] - scratch0.r;
				Fout[off2] = Fout[off1] + scratch0.i;

				Fout[off1++] -= scratch0.i;
				Fout[off1] += scratch0.r;

				// ++foffset; ++foffset;
			} while( --k != 0 );
		}
	}

// #ifndef OVERRIDE_kf_bfly5
	private final void kf_bfly5(final float[] Fout, final int foffset, final int fstride, int m, int N, int mm)
	{
		// final int foffset = 0;// java Fout[ foffset ]
		final Jkiss_fft_cpx scratch0 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch1 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch2 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch3 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch4 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch5 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch6 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch7 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch8 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch9 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch10 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch11 = new Jkiss_fft_cpx();
		final Jkiss_fft_cpx scratch12 = new Jkiss_fft_cpx();

		final int mf = m * fstride;// java
/* #ifdef FIXED_POINT
		ya.r = 10126;
		ya.i = -31164;
		yb.r = -26510;
		yb.i = -19261;
#else */
		final Jkiss_twiddle_cpx[] tw = this.twiddles;
		final Jkiss_twiddle_cpx ya = new Jkiss_twiddle_cpx( tw[mf] );
		final Jkiss_twiddle_cpx yb = new Jkiss_twiddle_cpx( tw[mf << 1] );// fstride * m * 2
// #endif

		m <<= 1;// java
		final int m2_2 = m << 1;// java
		final int m3_2 = m2_2 + m;// java
		final int m4_2 = m2_2 << 1;// java
		mm <<= 1;// java
		// final int Fout_beg = foffset;// Fout[ Fout_beg ]
		int i;
		for( i = foffset, N = foffset + N * mm; i < N; i += mm )
		{
			// foffset = Fout_beg + i;
			int Fout0 = i;// foffset;// Fout[ Fout0 ]
			int Fout1 = Fout0 + m;// Fout[ Fout1 ]
			int Fout2 = Fout0 + m2_2;// Fout[ Fout2 ]
			int Fout3 = Fout0 + m3_2;// Fout[ Fout3 ]
			int Fout4 = Fout0 + m4_2;// Fout[ Fout4 ]

			/* For non-custom modes, m is guaranteed to be a multiple of 4. */
			for( int u = 0; u < mf; u += fstride ) {
				scratch0.set( Fout[Fout0], Fout[Fout0 + 1] );

				Jkiss_twiddle_cpx t = tw[u];
				float ar = Fout[Fout1++];
				float ai = Fout[Fout1--];
				scratch1.r = ar * t.r - ai * t.i;
				scratch1.i = ar * t.i + ai * t.r;// C_MUL( scratch1, Fout, Fout1, tw[u] );
				int u2 = u << 1;// java
				t = tw[u2];
				ar = Fout[Fout2++];
				ai = Fout[Fout2--];
				scratch2.r = ar * t.r - ai * t.i;
				scratch2.i = ar * t.i + ai * t.r;// C_MUL( scratch2, Fout, Fout2, tw[u2] );
				t = tw[u2 + u];
				ar = Fout[Fout3++];
				ai = Fout[Fout3--];
				scratch3.r = ar * t.r - ai * t.i;
				scratch3.i = ar * t.i + ai * t.r;// C_MUL( scratch3, Fout, Fout3, tw[u2 + u] );
				u2 += u2;
				t = tw[u2];
				ar = Fout[Fout4++];
				ai = Fout[Fout4--];
				scratch4.r = ar * t.r - ai * t.i;
				scratch4.i = ar * t.i + ai * t.r;// C_MUL( scratch4, Fout, Fout4, tw[u2]);

				scratch7.r = scratch1.r + scratch4.r; scratch7.i = scratch1.i + scratch4.i;// C_ADD( scratch7, scratch1, scratch4 );
				scratch10.r = scratch1.r - scratch4.r; scratch10.i = scratch1.i - scratch4.i;// C_SUB( scratch10, scratch1, scratch4 );
				scratch8.r = scratch2.r + scratch3.r; scratch8.i = scratch2.i + scratch3.i;// C_ADD( scratch8, scratch2, scratch3 );
				scratch9.r = scratch2.r - scratch3.r; scratch9.i = scratch2.i - scratch3.i;// C_SUB( scratch9, scratch2, scratch3 );

				Fout[ Fout0++ ] += scratch7.r + scratch8.r;
				Fout[ Fout0++ ] += scratch7.i + scratch8.i;

				scratch5.r = scratch0.r + (scratch7.r * ya.r) + (scratch8.r * yb.r);
				scratch5.i = scratch0.i + (scratch7.i * ya.r) + (scratch8.i * yb.r);

				scratch6.r =  (scratch10.i * ya.i) + (scratch9.i * yb.i);
				scratch6.i = -((scratch10.r * ya.i) + (scratch9.r * yb.i));

				Fout[Fout1++] = scratch5.r - scratch6.r; Fout[Fout1++] = scratch5.i - scratch6.i;// C_SUB( Fout, Fout1, scratch5, scratch6);
				Fout[Fout4++] = scratch5.r + scratch6.r; Fout[Fout4++] = scratch5.i + scratch6.i;// C_ADD( Fout, Fout4, scratch5, scratch6);

				scratch11.r = scratch0.r + (scratch7.r * yb.r) + (scratch8.r * ya.r);
				scratch11.i = scratch0.i + (scratch7.i * yb.r) + (scratch8.i * ya.r);
				scratch12.r = (scratch9.i * ya.i) - (scratch10.i * yb.i);
				scratch12.i = (scratch10.r * yb.i) - (scratch9.r * ya.i);

				Fout[Fout2++] = scratch11.r + scratch12.r; Fout[Fout2++] = scratch11.i + scratch12.i;// C_ADD( Fout, Fout2, scratch11, scratch12 );
				Fout[Fout3++] = scratch11.r - scratch12.r; Fout[Fout3++] = scratch11.i - scratch12.i;// C_SUB( Fout, Fout3, scratch11, scratch12 );

				//++Fout0; ++Fout0; ++Fout1; ++Fout1; ++Fout2; ++Fout2; ++Fout3; ++Fout3;// ++Fout4; ++Fout4;
			}
		}
	}
// #endif /* OVERRIDE_kf_bfly5 */

	/**
	 * special version.
	 * fout[ r,i, r,i, r,i ... ]
	 *
	 * @param st
	 * @param fout
	 * @param foffset
	 */
	final void opus_fft_impl(final float[] fout, final int foffset)
	{
		final short[] f = this.factors;// java
		final int fstride[] = new int[MAXFACTORS];

		/* st.shift can be -1 */
		int sh = this.shift;
		if( sh < 0 ) {
			sh = 0;
		}
		fstride[0] = 1;
		int L = 0;
		int m;
		do {
			int L2 = L << 1;// java
			final int p = f[L2++];
			m = f[L2];
			fstride[L + 1] = fstride[L] * p;
			L++;
		} while( m != 1 );
		m = f[(L << 1) - 1];
		for( int i = L - 1; i >= 0; i-- )
		{
			final int i2 = i << 1;// java
			final int m2 = i != 0 ? f[i2 - 1] : 1;
			switch( f[i2] )
			{
			case 2:
				kf_bfly2( fout, foffset, m, fstride[i] );
				break;
			case 4:
				kf_bfly4( fout, foffset, fstride[i] << sh, m, fstride[i], m2 );
				break;
// #ifndef RADIX_TWO_ONLY
			case 3:
				kf_bfly3( fout, foffset, fstride[i] << sh, m, fstride[i], m2 );
				break;
			case 5:
				kf_bfly5( fout, foffset, fstride[i] << sh, m, fstride[i], m2 );
				break;
// #endif
			}
			m = m2;
		}
	}
}
