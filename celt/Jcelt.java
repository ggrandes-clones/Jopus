package celt;

/* Copyright (c) 2007-2008 CSIRO
   Copyright (c) 2007-2010 Xiph.Org Foundation
   Copyright (c) 2008 Gregory Maxwell
   Written by Jean-Marc Valin and Gregory Maxwell */

// celt.h
// celt.c

public final class Jcelt {

	// celt.h
	public static final int LEAK_BANDS = 19;

/* Encoder/decoder Requests */

	/* Expose this option again when variable framesize actually works */

	static final int CELT_SET_PREDICTION_REQUEST   = 10002;
	public static final int CELT_SET_PREDICTION = CELT_SET_PREDICTION_REQUEST;
	/** Controls the use of interframe prediction.
	 * 0=Independent frames
	 * 1=Short term interframe prediction allowed
	 * 2=Long term prediction allowed
	 */
	// #define CELT_SET_PREDICTION(x) CELT_SET_PREDICTION_REQUEST, __opus_check_int(x)

	private static final int CELT_SET_INPUT_CLIPPING_REQUEST  = 10004;
	// #define CELT_SET_INPUT_CLIPPING(x) CELT_SET_INPUT_CLIPPING_REQUEST, __opus_check_int(x)
	static final int CELT_SET_INPUT_CLIPPING = CELT_SET_INPUT_CLIPPING_REQUEST;// FIXME CELT_SET_INPUT_CLIPPING never uses

	static final int CELT_GET_AND_CLEAR_ERROR_REQUEST  = 10007;
	// #define CELT_GET_AND_CLEAR_ERROR(x) CELT_GET_AND_CLEAR_ERROR_REQUEST, __opus_check_int_ptr(x)
	static final int CELT_GET_AND_CLEAR_ERROR = CELT_GET_AND_CLEAR_ERROR_REQUEST;// FIXME CELT_GET_AND_CLEAR_ERROR never usees

	static final int CELT_SET_CHANNELS_REQUEST  = 10008;
	// #define CELT_SET_CHANNELS(x) CELT_SET_CHANNELS_REQUEST, __opus_check_int(x)
	public static final int CELT_SET_CHANNELS = CELT_SET_CHANNELS_REQUEST;

	/* Internal */
	static final int CELT_SET_START_BAND_REQUEST  = 10010;
	// #define CELT_SET_START_BAND(x) CELT_SET_START_BAND_REQUEST, __opus_check_int(x)
	public static final int CELT_SET_START_BAND = CELT_SET_START_BAND_REQUEST;

	static final int CELT_SET_END_BAND_REQUEST  = 10012;
	// #define CELT_SET_END_BAND(x) CELT_SET_END_BAND_REQUEST, __opus_check_int(x)
	public static final int CELT_SET_END_BAND = CELT_SET_END_BAND_REQUEST;

	public static final int CELT_GET_MODE_REQUEST    = 10015;
	/** Get the CELTMode used by an encoder or decoder */
	// #define CELT_GET_MODE(x) CELT_GET_MODE_REQUEST, __celt_check_mode_ptr_ptr(x)
	public static final int CELT_GET_MODE = CELT_GET_MODE_REQUEST;

	static final int CELT_SET_SIGNALLING_REQUEST  = 10016;
	// #define CELT_SET_SIGNALLING(x) CELT_SET_SIGNALLING_REQUEST, __opus_check_int(x)
	public static final int CELT_SET_SIGNALLING = CELT_SET_SIGNALLING_REQUEST;

	private static final int CELT_SET_TONALITY_REQUEST    = 10018;
	// #define CELT_SET_TONALITY(x) CELT_SET_TONALITY_REQUEST, __opus_check_int(x)
	static final int CELT_SET_TONALITY = CELT_SET_TONALITY_REQUEST;// FIXME CELT_SET_TONALITY_REQUEST never uses

	private static final int CELT_SET_TONALITY_SLOPE_REQUEST = 10020;
	// #define CELT_SET_TONALITY_SLOPE(x) CELT_SET_TONALITY_SLOPE_REQUEST, __opus_check_int(x)
	static final int CELT_SET_TONALITY_SLOPE = CELT_SET_TONALITY_SLOPE_REQUEST;// FIXME CELT_SET_TONALITY_SLOPE_REQUEST never uses

	static final int CELT_SET_ANALYSIS_REQUEST  = 10022;
	// #define CELT_SET_ANALYSIS(x) CELT_SET_ANALYSIS_REQUEST, __celt_check_analysis_ptr(x)
	public static final int CELT_SET_ANALYSIS = CELT_SET_ANALYSIS_REQUEST;

	public static final int OPUS_SET_LFE_REQUEST   = 10024;// FIXME why int celt.h?
	// #define OPUS_SET_LFE(x) OPUS_SET_LFE_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_LFE = OPUS_SET_LFE_REQUEST;

	public static final int OPUS_SET_ENERGY_MASK_REQUEST = 10026;
	// #define OPUS_SET_ENERGY_MASK(x) OPUS_SET_ENERGY_MASK_REQUEST, __opus_check_val16_ptr(x)
	public static final int OPUS_SET_ENERGY_MASK = OPUS_SET_ENERGY_MASK_REQUEST;

	public static final int CELT_SET_SILK_INFO_REQUEST = 10028;
	// #define CELT_SET_SILK_INFO(x) CELT_SET_SILK_INFO_REQUEST, __celt_check_silkinfo_ptr(x)
	public static final int CELT_SET_SILK_INFO = CELT_SET_SILK_INFO_REQUEST;

	static final char trim_icdf[/* 11 */] = { 126, 124, 119, 109, 87, 41, 19, 9, 4, 2, 0 };// java uint8 to char

	/** Probs: NONE: 21.875%, LIGHT: 6.25%, NORMAL: 65.625%, AGGRESSIVE: 6.25% */
	static final char spread_icdf[/* 4 */] = { 25, 23, 2, 0 };// java uint8 to char

	static final char tapset_icdf[/* 3 */] = { 2, 1, 0 };// java uint8 to char

/* #ifdef CUSTOM_MODES
	static const unsigned char toOpusTable[20] = {
		0xE0, 0xE8, 0xF0, 0xF8,
		0xC0, 0xC8, 0xD0, 0xD8,
		0xA0, 0xA8, 0xB0, 0xB8,
		0x00, 0x00, 0x00, 0x00,
		0x80, 0x88, 0x90, 0x98,
	};

	static const unsigned char fromOpusTable[16] = {
		0x80, 0x88, 0x90, 0x98,
		0x40, 0x48, 0x50, 0x58,
		0x20, 0x28, 0x30, 0x38,
		0x00, 0x08, 0x10, 0x18
	};

	static OPUS_INLINE int toOpus(unsigned char c)
	{
		int ret=0;
		if( c < 0xA0 )
			ret = toOpusTable[c >> 3];
		if( ret == 0 )
			return -1;
		else
			return ret | (c & 0x7);
	}

	static OPUS_INLINE int fromOpus(unsigned char c)
	{
		if( c < 0x80 )
			return -1;
		else
			return fromOpusTable[(c >> 3) - 16] | (c & 0x7);
	}
#endif */ /* CUSTOM_MODES */

	static final int COMBFILTER_MAXPERIOD = 1024;
	static final int COMBFILTER_MINPERIOD = 15;

	// celt.c

//#define CELT_C

	public static final int resampling_factor(final int rate)
	{
		int ret;
		switch( rate )
		{
		case 48000:
			ret = 1;
			break;
		case 24000:
			ret = 2;
			break;
		case 16000:
			ret = 3;
			break;
		case 12000:
			ret = 4;
			break;
		case 8000:
			ret = 6;
			break;
		default:
// #ifndef CUSTOM_MODES
			// celt_assert( 0 );
// #endif
			ret = 0;
			break;
		}
		return ret;
	}

/*#if !defined(OVERRIDE_COMB_FILTER_CONST) || defined(NON_STATIC_COMB_FILTER_CONST_C)
// This version should be faster on ARM
#ifdef OPUS_ARM_ASM
	static final void comb_filter_const_c(opus_val32 *y, opus_val32 *x, int T, int N,
				opus_val16 g10, opus_val16 g11, opus_val16 g12)
	{
		opus_val32 x0, x1, x2, x3, x4;
		int i;
		x4 = SHL32(x[-T - 2], 1);
		x3 = SHL32(x[-T - 1], 1);
		x2 = SHL32(x[-T], 1);
		x1 = SHL32(x[-T + 1], 1);
		for( i = 0; i < N - 4; i += 5)
		{
			opus_val32 t;
			x0 = SHL32( x[i - T + 2], 1 );
			t = MAC16_32_Q16( x[i], g10, x2 );
			t = MAC16_32_Q16( t, g11, ADD32( x1, x3 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x0, x4 ) );
			t = SATURATE(t, SIG_SAT);
			y[i] = t;
			x4 = SHL32( x[i - T + 3], 1 );
			t = MAC16_32_Q16( x[i + 1], g10, x1 );
			t = MAC16_32_Q16( t, g11, ADD32( x0, x2 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x4, x3 ) );
			t = SATURATE(t, SIG_SAT);
			y[i + 1] = t;
			x3 = SHL32( x[i - T + 4], 1 );
			t = MAC16_32_Q16( x[i + 2], g10, x0 );
			t = MAC16_32_Q16( t, g11, ADD32( x4, x1 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x3, x2 ) );
			t = SATURATE(t, SIG_SAT);
			y[i + 2] = t;
			x2 = SHL32( x[i - T + 5], 1 );
			t = MAC16_32_Q16( x[i + 3], g10, x4 );
			t = MAC16_32_Q16( t, g11, ADD32( x3, x0 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x2, x1 ) );
			t = SATURATE(t, SIG_SAT);
			y[i + 3] = t;
			x1 = SHL32( x[i - T + 6], 1 );
			t = MAC16_32_Q16( x[i + 4], g10, x3 );
			t = MAC16_32_Q16( t, g11, ADD32( x2, x4 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x1, x0 ) );
			t = SATURATE(t, SIG_SAT);
			y[i + 4] = t;
		}
#ifdef CUSTOM_MODES
		for( ; i < N; i++ )
		{
			opus_val32 t;
			x0 = SHL32( x[i - T + 2], 1 );
			t = MAC16_32_Q16( x[i], g10, x2 );
			t = MAC16_32_Q16( t, g11, ADD32( x1, x3 ) );
			t = MAC16_32_Q16( t, g12, ADD32( x0, x4 ) );
			t = SATURATE(t, SIG_SAT);
			y[i] = t;
			x4 = x3;
			x3 = x2;
			x2 = x1;
			x1 = x0;
		}
#endif
	}
#else
*/

	static final void comb_filter_const(final float[] y, int yoffset, final float[] x, final int xoffset,
			int T, int N, final float g10, final float g11, final float g12)
	{
		int i = xoffset - T - 2;// java
		float x4 = x[i++];// x[-T - 2];
		float x3 = x[i++];// x[-T - 1];
		float x2 = x[i++];// x[-T];
		float x1 = x[i];// x[-T + 1];
		T -= 2;// java
		for( i = xoffset, N += xoffset; i < N; i++ )
		{
			final float x0 = x[i - T];// i - T + 2
			y[yoffset++] = x[i]
					+ ( g10 * x2 )
					+ ( g11 * ( x1 + x3 ) )
					+ ( g12 * ( x0 + x4 ) );
			// y[i] = SATURATE(y[i], SIG_SAT);// java: just y[i] = y[i]
			x4 = x3;
			x3 = x2;
			x2 = x1;
			x1 = x0;
		}
	}
//#endif
//#endif

//#ifndef OVERRIDE_comb_filter
	private static final float gains[/* 3 */][/* 3 */] = {
			{ 0.3066406250f, 0.2170410156f, 0.1296386719f },
			{ 0.4638671875f, 0.2680664062f, 0.f },
			{ 0.7998046875f, 0.1000976562f, 0.f }};

	static final void comb_filter(final float[] y, final int yoffset,// java
			final float[] x, final int xoffset,// java
			int T0, int T1, final int N,
			final float g0, final float g1, final int tapset0, final int tapset1,
			final float[] window, int overlap)// int arch
	{
		/* printf ("%d %d %f %f\n", T0, T1, g0, g1); */

		if( g0 == 0 && g1 == 0 )
		{
			/* OPT: Happens to work without the OPUS_MOVE(), but only because the current encoder already copies x to y */
			//if( x != y ) {
			//	OPUS_MOVE( y, x, N );
			//}
			if( !(x == y && xoffset == yoffset) ) {
				System.arraycopy( x, xoffset, y, yoffset, N );
			}
			return;
		}
		/* When the gain is zero, T0 and/or T1 is set to zero. We need
		  to have then be at least 2 to avoid processing garbage data. */
		T0 = (T0 > COMBFILTER_MINPERIOD ? T0 : COMBFILTER_MINPERIOD);
		T1 = (T1 > COMBFILTER_MINPERIOD ? T1 : COMBFILTER_MINPERIOD);
		final float g00 = g0 * gains[tapset0][0];
		final float g01 = g0 * gains[tapset0][1];
		final float g02 = g0 * gains[tapset0][2];
		final float g10 = g1 * gains[tapset1][0];
		final float g11 = g1 * gains[tapset1][1];
		final float g12 = g1 * gains[tapset1][2];
		int xi = xoffset - T1;// java
		float x1 = x[xi + 1];
		float x2 = x[xi    ];
		float x3 = x[xi - 1];
		float x4 = x[xi - 2];
		/* If the filter didn't change, we don't need the overlap */
		if( g0 == g1 && T0 == T1 && tapset0 == tapset1 ) {
			overlap = 0;
		}
		final int t1_2 = 2 - T1;// java
		int i, yi;
		for( i = 0, xi = xoffset, yi = yoffset; i < overlap; i++, xi++, yi++ )
		{
			final float x0 = x[xi + t1_2];
			float f = window[i];
			f *= f;
			final int xi0 = xi - T0;// java
			y[yi] = x[xi]
					+ (((Jfloat_cast.Q15ONE - f) * g00) * x[xi0])
					+ (((Jfloat_cast.Q15ONE - f) * g01) * (x[xi0 + 1] + x[xi0 - 1]))
					+ (((Jfloat_cast.Q15ONE - f) * g02) * (x[xi0 + 2] + x[xi0 - 2]))
					+ ((f * g10) * x2)
					+ ((f * g11) * (x1 + x3))
					+ ((f * g12) * (x0 + x4));
			// y[i] = SATURATE(y[i], SIG_SAT);// java: just y[i] = y[i]
			x4 = x3;
			x3 = x2;
			x2 = x1;
			x1 = x0;

		}
		if( g1 == 0 )
		{
			/* OPT: Happens to work without the OPUS_MOVE(), but only because the current encoder already copies x to y */
			//if( x != y ) {
			//	OPUS_MOVE( y + overlap, x + overlap, N - overlap );
			//}
			if( !(x == y && xoffset == yoffset) ) {
				System.arraycopy( x, xoffset + overlap, y, yoffset + overlap, N - overlap );
			}
			return;
		}

		/* Compute the part with the constant filter. */
		// comb_filter_const( y + i, x + i, T1, N - i, g10, g11, g12, arch );
		comb_filter_const( y, yoffset + i, x, xoffset + i, T1, N - i, g10, g11, g12 );// FIXME why using i, not overlap?
	}
//#endif /* OVERRIDE_comb_filter */

	private static final String error_strings[/* 8 */] = {
			"success",
			"invalid argument",
			"buffer too small",
			"internal error",
			"corrupted stream",
			"request not implemented",
			"invalid state",
			"memory allocation failed"
		};

	/** Converts an opus error code into a human readable string.
	  *
	  * @param error [in] <tt>int</tt>: Error number
	  * @return Error string
	  */
	public static final String opus_strerror(final int error)// FIXME why in celt.c?
	{
		if( error > 0 || error < -7 ) {
			return "unknown error";
		}// else {
			return error_strings[-error];
		//}
	}

	private static final String PACKAGE_VERSION = "1.3";

	/** Gets the libopus version string.
	  *
	  * Applications may look for the substring "-fixed" in the version string to
	  * determine whether they have a fixed-point or floating-point build at
	  * runtime.
	  *
	  * @return Version string
	  */
	public static final String opus_get_version_string()// FIXME why in celt.c?
	{
		return "libopus " + PACKAGE_VERSION;// +
		/* Applications may rely on the presence of this substring in the version
		  string to determine if they have a fixed-point or floating-point build
		  at runtime. */
		/*	( FIXED_POINT ?
			"-fixed" : "" ) +
			( FUZZING ?
			"-fuzzing" : "" ); */
	}
}