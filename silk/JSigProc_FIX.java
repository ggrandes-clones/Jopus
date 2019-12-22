package silk;

/***********************************************************************
Copyright (c) 2006-2011, Skype Limited. All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
- Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
- Neither the name of Internet Society, IETF or IETF Trust, nor the
names of specific contributors, may be used to endorse or promote
products derived from this software without specific prior written
permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
***********************************************************************/

// SigProc_FIX.h

public final class JSigProc_FIX {

	/*#define silk_MACRO_COUNT */          /* Used to enable WMOPS counting */

	static final int SILK_MAX_ORDER_LPC = 24;            /* max order of the LPC analysis in schur() and k2a() */

/********************************************************************/
/*                                MACROS                            */
/********************************************************************/

	/** Rotate a32 right by 'rot' bits. Negative rot values result in rotating
	   left. Output is 32bit int.
	   Note: contemporary compilers recognize the C expression below and
	   compile it into a 'ror' instruction if available. No need for OPUS_INLINE ASM! */
	/* static final int silk_ROR32( final int a32, int rot )// java: moved to Jmacros
	{
		if( rot == 0 ) {
			return a32;
		}
		if( rot < 0 ) {
			rot = -rot;
			return (int) ((a32 << rot) | (a32 >>> (32 - rot)));
		}
		return (int) ((a32 << (32 - rot)) | (a32 >>> rot));
	}*/

/* Fixed point macros */

/* (a32 * b32) output have to be 32bit int */
// #define silk_MUL(a32, b32)                  ((a32) * (b32))

/* (a32 * b32) output have to be 32bit uint */
//#define silk_MUL_uint(a32, b32)             silk_MUL(a32, b32)

/* a32 + (b32 * c32) output have to be 32bit int */
// #define silk_MLA(a32, b32, c32)             silk_ADD32((a32),((b32) * (c32)))

/* a32 + (b32 * c32) output have to be 32bit uint */
// #define silk_MLA_uint(a32, b32, c32)        silk_MLA(a32, b32, c32)

	/** ((a32 >> 16)  * (b32 >> 16)) output have to be 32bit int */
	/* private static final int silk_SMULTT(final int a32, final int b32) {
		return ((a32 >> 16) * (b32 >> 16));
	}*/

/* a32 + ((a32 >> 16)  * (b32 >> 16)) output have to be 32bit int */
// #define silk_SMLATT(a32, b32, c32)          silk_ADD32((a32),((b32) >> 16) * ((c32) >> 16))

// #define silk_SMLALBB(a64, b16, c16)         silk_ADD64((a64),(opus_int64)((opus_int32)(b16) * (opus_int32)(c16)))

/* (a32 * b32) */
// #define silk_SMULL(a32, b32)                ((opus_int64)(a32) * /*(opus_int64)*/(b32))

/* Adds two signed 32-bit values in a way that can overflow, while not relying on undefined behaviour
   (just standard two's complement implementation-specific behaviour) */
// #define silk_ADD32_ovflw(a, b)              ((opus_int32)((opus_uint32)(a) + (opus_uint32)(b)))
/* Subtractss two signed 32-bit values in a way that can overflow, while not relying on undefined behaviour
   (just standard two's complement implementation-specific behaviour) */
// #define silk_SUB32_ovflw(a, b)              ((opus_int32)((opus_uint32)(a) - (opus_uint32)(b)))

/* Multiply-accumulate macros that allow overflow in the addition (ie, no asserts in debug mode) */
// #define silk_MLA_ovflw(a32, b32, c32)       silk_ADD32_ovflw((a32), (opus_uint32)(b32) * (opus_uint32)(c32))
	/** a32 + b16 * c16 */
	/*static final int silk_SMLABB_ovflw(final int a32, final int b32, final int c32) {
		return (silk_ADD32_ovflw( a32 + (((opus_int32)((opus_int16)b32)) * (opus_int32)((opus_int16)c32))));
	}*/

// #define silk_DIV32_16(a32, b16)             ((opus_int32)((a32) / (b16)))
// #define silk_DIV32(a32, b32)                ((opus_int32)((a32) / (b32)))

/* These macros enables checking for overflow in silk_API_Debug.h*/
// #define silk_ADD16(a, b)                    ((a) + (b))
// #define silk_ADD32(a, b)                    ((a) + (b))
// #define silk_ADD64(a, b)                    ((a) + (b))

// #define silk_SUB16(a, b)                    ((a) - (b))
// #define silk_SUB32(a, b)                    ((a) - (b))
// #define silk_SUB64(a, b)                    ((a) - (b))

// #define silk_SAT8(a)                        ((a) > silk_int8_MAX ? silk_int8_MAX  :       \
//                                            ((a) < silk_int8_MIN ? silk_int8_MIN  : (a)))
	/* private static final int silk_SAT16(final int a) {
		return (a > Short.MAX_VALUE ? Short.MAX_VALUE : (a < Short.MIN_VALUE ? Short.MIN_VALUE : a));
	}*/
// #define silk_SAT32(a)                       ((a) > silk_int32_MAX ? silk_int32_MAX :      \
//                                            ((a) < silk_int32_MIN ? silk_int32_MIN : (a)))

// #define silk_CHECK_FIT8(a)                  (a)
// #define silk_CHECK_FIT16(a)                 (a)
// #define silk_CHECK_FIT32(a)                 (a)

// #define silk_ADD_SAT16(a, b)                (opus_int16)silk_SAT16( silk_ADD32( (opus_int32)(a), (b) ) )
// #define silk_ADD_SAT64(a, b)                ((((a) + (b)) & 0x8000000000000000LL) == 0 ?                            \
//                                             ((((a) & (b)) & 0x8000000000000000LL) != 0 ? silk_int64_MIN : (a)+(b)) : \
//                                             ((((a) | (b)) & 0x8000000000000000LL) == 0 ? silk_int64_MAX : (a)+(b)) )

// #define silk_SUB_SAT16(a, b)                (opus_int16)silk_SAT16( silk_SUB32( (opus_int32)(a), (b) ) )
// #define silk_SUB_SAT64(a, b)                ((((a)-(b)) & 0x8000000000000000LL) == 0 ?                                               \
//                                             (( (a) & ((b)^0x8000000000000000LL) & 0x8000000000000000LL) ? silk_int64_MIN : (a)-(b)) : \
//                                             ((((a)^0x8000000000000000LL) & (b)  & 0x8000000000000000LL) ? silk_int64_MAX : (a)-(b)) )

/* Saturation for positive input values */
// #define silk_POS_SAT32(a)                   ((a) > silk_int32_MAX ? silk_int32_MAX : (a))

/* Add with saturation for positive input values */
// #define silk_ADD_POS_SAT8(a, b)             ((((a)+(b)) & 0x80)                 ? silk_int8_MAX  : ((a)+(b)))
// #define silk_ADD_POS_SAT16(a, b)            ((((a)+(b)) & 0x8000)               ? silk_int16_MAX : ((a)+(b)))
// #define silk_ADD_POS_SAT32(a, b)            (((((opus_uint32)a)+((opus_uint32)b)) & 0x80000000) ? silk_int32_MAX : ((a)+(b)))

// #define silk_LSHIFT8(a, shift)              ((opus_int8)((opus_uint8)(a)<<(shift)))         /* shift >= 0, shift < 8  */
// #define silk_LSHIFT16(a, shift)             ((opus_int16)((opus_uint16)(a)<<(shift)))       /* shift >= 0, shift < 16 */
// #define silk_LSHIFT32(a, shift)             ((opus_int32)((opus_uint32)(a)<<(shift)))       /* shift >= 0, shift < 32 */
// #define silk_LSHIFT64(a, shift)             ((opus_int64)((opus_uint64)(a)<<(shift)))       /* shift >= 0, shift < 64 */
// #define silk_LSHIFT(a, shift)               silk_LSHIFT32(a, shift)                         /* shift >= 0, shift < 32 */

// #define silk_RSHIFT8(a, shift)              ((a)>>(shift))                                  /* shift >= 0, shift < 8  */
// #define silk_RSHIFT16(a, shift)             ((a)>>(shift))                                  /* shift >= 0, shift < 16 */
// #define silk_RSHIFT32(a, shift)             ((a)>>(shift))                                  /* shift >= 0, shift < 32 */
// #define silk_RSHIFT64(a, shift)             ((a)>>(shift))                                  /* shift >= 0, shift < 64 */
// #define silk_RSHIFT(a, shift)               silk_RSHIFT32(a, shift)                         /* shift >= 0, shift < 32 */

	/** saturates before shifting */
	/* static final int silk_LSHIFT_SAT32(final int a, final int shift) {// java extracted in place
		return ((a > (Integer.MAX_VALUE >> shift) ? (Integer.MAX_VALUE >> shift) : (a < (Integer.MIN_VALUE >> shift) ? (Integer.MIN_VALUE >> shift) : a)) << shift);
	} */

// #define silk_LSHIFT_ovflw(a, shift)         ((opus_int32)((opus_uint32)(a) << (shift)))     /* shift >= 0, allowed to overflow */
// #define silk_LSHIFT_uint(a, shift)          ((a) << (shift))                                /* shift >= 0 */
// #define silk_RSHIFT_uint(a, shift)          ((a) >> (shift))                                /* shift >= 0 */

// #define silk_ADD_LSHIFT(a, b, shift)        ((a) + silk_LSHIFT((b), (shift)))               /* shift >= 0 */
// #define silk_ADD_LSHIFT32(a, b, shift)      silk_ADD32((a), silk_LSHIFT32((b), (shift)))    /* shift >= 0 */
// #define silk_ADD_LSHIFT_uint(a, b, shift)   ((a) + silk_LSHIFT_uint((b), (shift)))          /* shift >= 0 */
	/** shift >= 0 */
	/* static final int silk_ADD_RSHIFT(final int a, final int b, final int shift) {
		return (a + (b >> shift));// (a + silk_RSHIFT( b, shift ));
	}*/
// #define silk_ADD_RSHIFT32(a, b, shift)      silk_ADD32((a), silk_RSHIFT32((b), (shift)))    /* shift >= 0 */
// #define silk_ADD_RSHIFT_uint(a, b, shift)   ((a) + silk_RSHIFT_uint((b), (shift)))          /* shift >= 0 */
// #define silk_SUB_LSHIFT32(a, b, shift)      silk_SUB32((a), silk_LSHIFT32((b), (shift)))    /* shift >= 0 */
// #define silk_SUB_RSHIFT32(a, b, shift)      silk_SUB32((a), silk_RSHIFT32((b), (shift)))    /* shift >= 0 */

	/** Requires that shift > 0 */
	static final int silk_RSHIFT_ROUND(final int a, final int shift) {
		return (shift == 1 ? (a >> 1) + (a & 1) : ((a >> (shift - 1)) + 1) >> 1);
	}
	/* static final long silk_RSHIFT_ROUND64(final long a, final int shift) {// extracted in place
		return (shift == 1 ? (a >> 1) + (a & 1) : ((a >> (shift - 1)) + 1) >> 1);
	} */

/* Number of rightshift required to fit the multiplication */
// #define silk_NSHIFT_MUL_32_32(a, b)         ( -(31- (32-silk_CLZ32(silk_abs(a)) + (32-silk_CLZ32(silk_abs(b))))) )
// #define silk_NSHIFT_MUL_16_16(a, b)         ( -(15- (16-silk_CLZ16(silk_abs(a)) + (16-silk_CLZ16(silk_abs(b))))) )


// #define silk_min(a, b)                      (((a) < (b)) ? (a) : (b))
// #define silk_max(a, b)                      (((a) > (b)) ? (a) : (b))

	/** Macro to convert floating-point constants to fixed-point */
	/* public static final int SILK_FIX_CONST(final double C, final int Q) {
		return (int)(C * (1L << Q) + 0.5);
	}*/

	/* silk_min() versions with typecast in the function call */
	/*static OPUS_INLINE opus_int silk_min_int(final opus_int a, final opus_int b)
	{
	    return (((a) < (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int16 silk_min_16(final opus_int16 a, final opus_int16 b)
	{
	    return (((a) < (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int32 silk_min_32(final opus_int32 a, final opus_int32 b)
	{
	    return (((a) < (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int64 silk_min_64(final opus_int64 a, final opus_int64 b)
	{
	    return (((a) < (b)) ? (a) : (b));
	}*/

	/* silk_min() versions with typecast in the function call */
	/*static OPUS_INLINE opus_int silk_max_int(final opus_int a, final opus_int b)
	{
	    return (((a) > (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int16 silk_max_16(final opus_int16 a, final opus_int16 b)
	{
	    return (((a) > (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int32 silk_max_32(final opus_int32 a, final opus_int32 b)
	{
	    return (((a) > (b)) ? (a) : (b));
	}
	static OPUS_INLINE opus_int64 silk_max_64(final opus_int64 a, final opus_int64 b)
	{
	    return (((a) > (b)) ? (a) : (b));
	}*/

	static final int silk_LIMIT(final int a, final int limit1, final int limit2) {// java: partially extracted in place
		return (limit1 > limit2 ?
				(a > limit1 ? limit1 : (a < limit2 ? limit2 : a)) :
				(a > limit2 ? limit2 : (a < limit1 ? limit1 : a)));
	}
	/* public static final float silk_LIMIT(final float a, final float limit1, final float limit2) {
		return (limit1 > limit2 ?
				(a > limit1 ? limit1 : (a < limit2 ? limit2 : a)) :
				(a > limit2 ? limit2 : (a < limit1 ? limit1 : a)));
	}*/

// #define silk_LIMIT_int                      silk_LIMIT
// #define silk_LIMIT_16                       silk_LIMIT
// #define silk_LIMIT_32                       silk_LIMIT

// #define silk_abs(a)                         (((a) >  0)  ? (a) : -(a))            /* Be careful, silk_abs returns wrong when input equals to silk_intXX_MIN */
// #define silk_abs_int(a)                     (((a) ^ ((a) >> (8 * sizeof(a) - 1))) - ((a) >> (8 * sizeof(a) - 1)))
// #define silk_abs_int32(a)                   (((a) ^ ((a) >> 31)) - ((a) >> 31))
// #define silk_abs_int64(a)                   (((a) >  0)  ? (a) : -(a))

// #define silk_sign(a)                        ((a) > 0 ? 1 : ( (a) < 0 ? -1 : 0 ))

	/** PSEUDO-RANDOM GENERATOR
	 * Make sure to store the result as the seed for the next call (also in between
	 * frames), otherwise result won't be random at all. When only using some of the
	 * bits, take the most significant bits by right-shifting.*/
	/* private static final int RAND_MULTIPLIER = 196314165;
	private static final int RAND_INCREMENT  = 907633515;
	static final int silk_RAND(final int seed) {
		return RAND_INCREMENT + (seed * RAND_MULTIPLIER);
	} */

/*  Add some multiplication functions that can be easily mapped to ARM. */

/*    silk_SMMUL: Signed top word multiply.
          ARMv6        2 instruction cycles.
          ARMv3M+      3 instruction cycles. use SMULL and ignore LSB registers.(except xM)*/
/*#define silk_SMMUL(a32, b32)                (opus_int32)silk_RSHIFT(silk_SMLAL(silk_SMULWB((a32), (b32)), (a32), silk_RSHIFT_ROUND((b32), 16)), 16)*/
/* the following seems faster on x86 */
// #define silk_SMMUL(a32, b32)                (opus_int32)silk_RSHIFT64(silk_SMULL((a32), (b32)), 32)

}
