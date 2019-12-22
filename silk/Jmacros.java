package silk;

import celt.Jec_ctx;

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

// macros.h

public final class Jmacros {
	// start macros.h

/* This is an OPUS_INLINE header file for general platform. */

	// java: be careful with exracting inplace! (short)c32 may be very important! especially if using together with silk_SMLAWT!
	/** (a32 * (opus_int32)((opus_int16)(b32))) >> 16 output have to be 32bit int */
	/* public static final int silk_SMULWB(final int a32, final int b32) {
		return (int)((a32 * (long)((short)b32)) >> 16);
		// return (((a32 >> 16) * (int)((short)(b32))) + (((a32 & 0x0000FFFF) * (int)((short)b32)) >> 16));
	}*/

	// java: be careful with exracting inplace! (short)c32 may be very important! especially if using together with silk_SMLAWT!
	/** a32 + (b32 * (opus_int32)((opus_int16)(c32))) >> 16 output have to be 32bit int */
	/* public static final int silk_SMLAWB(final int a32, final int b32, final int c32) {
		return (int)(a32 + ((b32 * (long)((short)c32)) >> 16));
		// return (a32 + (((b32 >> 16) * (int)((short)c32)) + (((b32 & 0x0000FFFF) * (int)((short)c32)) >> 16)));
	}*/

	/** (a32 * (b32 >> 16)) >> 16 */
	/* static final int silk_SMULWT(final int a32, final int b32) {
		return (int)((a32 * (long)(b32 >> 16)) >> 16);
		// return ((a32 >> 16) * (b32 >> 16) + (((a32 & 0x0000FFFF) * (b32 >> 16)) >> 16));
	}*/

	/** a32 + (b32 * (c32 >> 16)) >> 16 */
	/* static final int silk_SMLAWT(final int a32, final int b32, final int c32) {
		return (int)(a32 + ((b32 * (long)(c32 >> 16)) >> 16));
		// return (a32 + ((b32 >> 16) * (c32 >> 16)) + (((b32 & 0x0000FFFF) * (c32 >> 16)) >> 16));
	}*/

	/** (opus_int32)((opus_int16)(a3))) * (opus_int32)((opus_int16)(b32)) output have to be 32bit int */
	/* static final int silk_SMULBB(final int a32, final int b32) {
		return ((int)((short)a32) * (int)((short)b32));
	}*/

	/** a32 + (opus_int32)((opus_int16)(b32)) * (opus_int32)((opus_int16)(c32)) output have to be 32bit int */
	/* static final int silk_SMLABB(final int a32, final int b32, final int c32) {
		return (a32 + ((int)((short)b32)) * (int)((short)c32));
	}*/

	/** (opus_int32)((opus_int16)(a32)) * (b32 >> 16) */
	/* static final int silk_SMULBT(final int a32, final int b32) {
		return ((int)((short)a32) * (b32 >> 16));
	}*/

	/** a32 + (opus_int32)((opus_int16)(b32)) * (c32 >> 16) */
	/* static final int silk_SMLABT(final int a32, final int b32, final int c32) {
		return (a32 + ((int)((short)b32)) * (c32 >> 16));
	}*/

	/** a64 + (b32 * c32) */
	/* static final long silk_SMLAL(final long a64, final int b32, final int c32) {
		return ((a64 + ((long)b32 * (long)c32)));
	}*/

	/** (a32 * b32) >> 16 */
	/* public static final int silk_SMULWW(final int a32, final int b32) {
		return (int)(((long)a32 * b32) >> 16);
		//return silk_MLA(silk_SMULWB( a32, b32), a32, silk_RSHIFT_ROUND( b32, 16 ));
	}*/

	/** a32 + ((b32 * c32) >> 16) */
	/* static final int silk_SMLAWW(final int a32, final int b32, final int c32) {
		return (int)(a32 + (((long)b32 * c32) >> 16));
		// return silk_MLA( silk_SMLAWB( a32, b32, c32 ), b32, silk_RSHIFT_ROUND( c32, 16 ) );
	}*/

	/** add/subtract with output saturated */
	/* static final int silk_ADD_SAT32(final int a, final int b) {
		return (((a + b) & 0x80000000) == 0 ?
				(((a & b) & 0x80000000) != 0 ? Integer.MIN_VALUE : a + b) :
					(((a | b) & 0x80000000) == 0 ? Integer.MAX_VALUE : a + b) );
	}*/

	/* static final int silk_SUB_SAT32(final int a, final int b) {
		return (((a - b) & 0x80000000) == 0 ?
				((a & (b ^ 0x80000000) & 0x80000000) != 0 ? Integer.MIN_VALUE : a - b) :
					(((a ^ 0x80000000) & b & 0x80000000) != 0 ? Integer.MAX_VALUE : a - b) );
	}*/

/*
	private static final int silk_CLZ16(final short in16)
	{
	    return 32 - Jecintrin.EC_ILOG(in16 << 16 | 0x8000);
	}
*/
	static final int silk_CLZ32(final int in32)
	{
	    return in32 != 0 ? 32 - Jec_ctx.EC_ILOG( in32 ) : 32;
	}

	/* Row based */
	/** @return Matrix_base_adr[ row * N + column ] */
	/* static final long matrix_ptr(final long[] Matrix_base_adr, final int row, final int column, final int N) {
		return (Matrix_base_adr[ row * N + column ]);
	}*/
	//#define matrix_adr(Matrix_base_adr, row, column, N) \
	//      ((Matrix_base_adr) + ((row)*(N)+(column)))

/* Column based */
//#ifndef matrix_c_ptr
//#   define matrix_c_ptr(Matrix_base_adr, row, column, M) \
//    (*((Matrix_base_adr) + ((row)+(M)*(column))))
//#endif
	// end macros.h

	// start log2lin.c
	/**
	 * Approximation of 2^() (very close inverse of silk_lin2log())
	 * Convert input to a linear scale
	 *
	 * @param inLog_Q7 I  input on log scale
	 * @return
	 */
	public static final int silk_log2lin(final int inLog_Q7)
	{
		if( inLog_Q7 < 0 ) {
			return 0;
		}
		if( inLog_Q7 >= 3967 ) {
			return Integer.MAX_VALUE;
		}

		int out = (1 << (inLog_Q7 >> 7));
		final int frac_Q7 = inLog_Q7 & 0x7F;
		if( inLog_Q7 < 2048 ) {
			/* Piece-wise parabolic approximation */
			out += (( out * ( (frac_Q7 + (int)(((long)(frac_Q7 * (128 - frac_Q7)) * -174L) >> 16)) ) ) >> 7);
			return out;
		}// else {
			/* Piece-wise parabolic approximation */
			out += ( out >> 7 ) * ( (frac_Q7 + (int)(((long)(frac_Q7 * (128 - frac_Q7)) * -174L) >> 16)) );
		//}
		return out;
	}
	// end log2lin.c

	// start SigProc_FIX.h
	/** Rotate a32 right by 'rot' bits. Negative rot values result in rotating
	   left. Output is 32bit int.
	   Note: contemporary compilers recognize the C expression below and
	   compile it into a 'ror' instruction if available. No need for OPUS_INLINE ASM! */
	static final int silk_ROR32( final int a32, int rot )
	{
		if( rot == 0 ) {
			return a32;
		}
		if( rot < 0 ) {
			rot = -rot;
			return ((a32 << rot) | (a32 >>> (32 - rot)));
		}
		return ((a32 << (32 - rot)) | (a32 >>> rot));
	}
	// end SigProc_FIX.h

	// start lin2log.c
	/**
	 * Approximation of 128 * log2() (very close inverse of silk_log2lin())
	 * Convert input to a log scale
	 *
	 * @param inLin I  input in linear scale
	 * @return Approximation of 128 * log2()
	 */
	public static final int silk_lin2log(final int inLin)
	{
		// final int lz, frac_Q7;
		// JInlines.silk_CLZ_FRAC( inLin, &lz, &frac_Q7 );
		// java extracted in place
		final int lz = silk_CLZ32( inLin );
		final int frac_Q7 = silk_ROR32( inLin, 24 - lz ) & 0x7f;

		/* Piece-wise parabolic approximation */
		return  (frac_Q7 + (int)(((long)( frac_Q7 * (128 - frac_Q7) ) * 179L) >> 16)) + ((31 - lz) << 7);
	}
	// end lin2log.c
}