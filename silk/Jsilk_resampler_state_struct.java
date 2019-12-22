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

// resampler_structs.h

final class Jsilk_resampler_state_struct extends Jenc_API {
	// start resampler_private.h
	/* Number of input samples to process in the inner loop */
	private static final int RESAMPLER_MAX_BATCH_SIZE_MS  = 10;
	private static final int RESAMPLER_MAX_FS_KHZ = 48;
	static final int RESAMPLER_MAX_BATCH_SIZE_IN  = ( RESAMPLER_MAX_BATCH_SIZE_MS * RESAMPLER_MAX_FS_KHZ );
	// end resampler_private.h

	// resampler_structs.h
	private static final int SILK_RESAMPLER_MAX_FIR_ORDER = 36;
	private static final int SILK_RESAMPLER_MAX_IIR_ORDER = 6;

	private final int       sIIR[] = new int[ SILK_RESAMPLER_MAX_IIR_ORDER ]; /* this must be the first element of this struct */
	//union{// java using either i32 or i16, so just reserve two different dimensions
		private final int   i32[] = new int[ SILK_RESAMPLER_MAX_FIR_ORDER ];
		private final short i16[] = new short[ SILK_RESAMPLER_MAX_FIR_ORDER ];
	//}                sFIR;
	private final short delayBuf[] = new short[ 48 ];
	private int         resampler_function;
	private int         batchSize;
	private int         invRatio_Q16;
	private int         FIR_Order;
	private int         FIR_Fracs;
	private int         Fs_in_kHz;
	private int         Fs_out_kHz;
	private int         inputDelay;
	private short[]     Coefs;
	//
	final void clear() {
		int[] ibuff = sIIR;
		int i = SILK_RESAMPLER_MAX_IIR_ORDER;
		do {
			ibuff[--i] = 0;
		} while( i > 0 );
		//
		ibuff = i32;
		i = SILK_RESAMPLER_MAX_FIR_ORDER;
		do {
			ibuff[--i] = 0;
		} while( i > 0 );
		//
		short[] sbuff = i16;
		i = SILK_RESAMPLER_MAX_FIR_ORDER;
		do {
			sbuff[--i] = 0;
		} while( i > 0 );
		//
		sbuff = delayBuf;
		i = 48;
		do {
			sbuff[--i] = 0;
		} while( i > 0 );
		//
		resampler_function = 0;
		batchSize = 0;
		invRatio_Q16 = 0;
		FIR_Order = 0;
		FIR_Fracs = 0;
		Fs_in_kHz = 0;
		Fs_out_kHz = 0;
		inputDelay = 0;
		Coefs = null;
	}
	final void copyFrom(final Jsilk_resampler_state_struct state) {
		System.arraycopy( state.sIIR, 0, sIIR, 0, SILK_RESAMPLER_MAX_IIR_ORDER );
		System.arraycopy( state.i32, 0, i32, 0, SILK_RESAMPLER_MAX_FIR_ORDER );
		System.arraycopy( state.i16, 0, i16, 0, SILK_RESAMPLER_MAX_FIR_ORDER );
		System.arraycopy( state.delayBuf, 0, delayBuf, 0, 48 );
		resampler_function = state.resampler_function;
		batchSize = state.batchSize;
		invRatio_Q16 = state.invRatio_Q16;
		FIR_Order = state.FIR_Order;
		FIR_Fracs = state.FIR_Fracs;
		Fs_in_kHz = state.Fs_in_kHz;
		Fs_out_kHz = state.Fs_out_kHz;
		inputDelay = state.inputDelay;
		Coefs = state.Coefs;
	}
	//
	// start resampler_private_up2_HQ.c
	/**
	 * Upsample by a factor 2, high quality
	 * Uses 2nd order allpass filters for the 2x upsampling, followed by a
	 * notch filter just above Nyquist.
	 *
	 * @param S I/O  Resampler state [ 6 ]
	 * @param out O    Output signal [ 2 * len ]
	 * @param in I    Input signal [ len ]
	 * @param len I    Number of input samples
	 */
	private static final void silk_resampler_private_up2_HQ(final int[] S,
			final short[] out, int outoffset,// java
			final short[] in, int inoffset,
			int len)
	{
		// silk_assert( silk_resampler_up2_hq_0[ 0 ] > 0 );
		// silk_assert( silk_resampler_up2_hq_0[ 1 ] > 0 );
		// silk_assert( silk_resampler_up2_hq_0[ 2 ] < 0 );
		// silk_assert( silk_resampler_up2_hq_1[ 0 ] > 0 );
		// silk_assert( silk_resampler_up2_hq_1[ 1 ] > 0 );
		// silk_assert( silk_resampler_up2_hq_1[ 2 ] < 0 );

		/* Internal variables and state are in Q10 format */
		final long rom0_0 = (long)Jresampler_rom.silk_resampler_up2_hq_0[ 0 ];// java
		final long rom0_1 = (long)Jresampler_rom.silk_resampler_up2_hq_0[ 1 ];// java
		final long rom0_2 = (long)Jresampler_rom.silk_resampler_up2_hq_0[ 2 ];// java
		final long rom1_0 = (long)Jresampler_rom.silk_resampler_up2_hq_1[ 0 ];// java
		final long rom1_1 = (long)Jresampler_rom.silk_resampler_up2_hq_1[ 1 ];// java
		final long rom1_2 = (int)Jresampler_rom.silk_resampler_up2_hq_1[ 2 ];// java
		len += inoffset;// java
		while( inoffset < len ) {// java
			/* Convert to Q10 */
			final int in32 = ( (int)in[ inoffset++ ] << 10 );

			/* First all-pass section for even output sample */
			int Y       = ( in32 - S[ 0 ] );
			int X       = (int)((Y * rom0_0) >> 16);
			int out32_1 = ( S[ 0 ] + X );
			S[ 0 ]  = ( in32 + X );

			/* Second all-pass section for even output sample */
			Y       = ( out32_1 - S[ 1 ] );
			X       = (int)((Y * rom0_1) >> 16);
			int out32_2 = ( S[ 1 ] + X );
			S[ 1 ]  = ( out32_1 + X );

			/* Third all-pass section for even output sample */
			Y       = ( out32_2 - S[ 2 ] );
			X       = Y + (int)((Y * rom0_2) >> 16);
			out32_1 = ( S[ 2 ] + X );
			S[ 2 ]  = ( out32_2 + X );

			/* Apply gain in Q15, convert back to int16 and store to output */
			out32_1 = JSigProc_FIX.silk_RSHIFT_ROUND( out32_1, 10 );
			out[ outoffset++ ] = (short)(out32_1 > Short.MAX_VALUE ? Short.MAX_VALUE : (out32_1 < Short.MIN_VALUE ? Short.MIN_VALUE : out32_1));

			/* First all-pass section for odd output sample */
			Y       = ( in32 - S[ 3 ] );
			X       = (int)((Y * rom1_0) >> 16);
			out32_1 = ( S[ 3 ] + X );
			S[ 3 ]  = ( in32 + X );

			/* Second all-pass section for odd output sample */
			Y       = ( out32_1 - S[ 4 ] );
			X       = (int)((Y * rom1_1) >> 16);
			out32_2 = ( S[ 4 ] + X );
			S[ 4 ]  = ( out32_1 + X );

			/* Third all-pass section for odd output sample */
			Y       = ( out32_2 - S[ 5 ] );
			X       = Y + (int)((Y * rom1_2) >> 16);
			out32_1 = ( S[ 5 ] + X );
			S[ 5 ]  = ( out32_2 + X );

			/* Apply gain in Q15, convert back to int16 and store to output */
			out32_1 = JSigProc_FIX.silk_RSHIFT_ROUND( out32_1, 10 );
			out[ outoffset++ ] = (short)(out32_1 > Short.MAX_VALUE ? Short.MAX_VALUE : (out32_1 < Short.MIN_VALUE ? Short.MIN_VALUE : out32_1));
		}
	}

	/**
	 *
	 * @param SS I/O  Resampler state (unused)
	 * @param out O    Output signal [ 2 * len ]
	 * @param in I    Input signal [ len ]
	 * @param len I    Number of input samples
	 */
	private final void silk_resampler_private_up2_HQ_wrapper(
			final short[] out, final int outoffset,// java
			final short[] in, final int inoffset,// java
			final int len)
	{
		silk_resampler_private_up2_HQ( this.sIIR, out, outoffset, in, inoffset, len );
	}
	// end resampler_private_up2_HQ.c

	// start resampler_private_IIR_FIR.c
	/**
	 * java changed: return outoffset
	 *
	 * @param out
	 * @param buf
	 * @param max_index_Q16
	 * @param index_increment_Q16
	 * @return outoffset
	 */
	private static final int silk_resampler_private_IIR_FIR_INTERPOL(final short[] out, int outoffset,// java
			final short[] buf,
			final int max_index_Q16, final int index_increment_Q16
			)
	{
		final short[][] table = Jresampler_rom.silk_resampler_frac_FIR_12;// java
		/* Interpolate upsampled signal and store in output array */
		for( int index_Q16 = 0; index_Q16 < max_index_Q16; index_Q16 += index_increment_Q16 ) {
			final int table_index = (((index_Q16 & 0xFFFF) * 12) >> 16);// FIXME why silk_SMULWB?
			int buf_ptr = index_Q16 >> 16;// buf[ buf_ptr ]

			short[] ti = table[ table_index ];// java
			int res_Q15 = (int)buf[ buf_ptr++ ] * (int)ti[ 0 ];
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 1 ];
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 2 ];
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 3 ];
			ti = table[ 11 - table_index ];// java
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 3 ];
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 2 ];
			res_Q15 += (int)buf[ buf_ptr++ ] * (int)ti[ 1 ];
			res_Q15 += (int)buf[ buf_ptr   ] * (int)ti[ 0 ];
			res_Q15 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q15, 15 );// java
			out[ outoffset++ ] = (short)(res_Q15 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q15 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q15));
		}
		return outoffset;
	}
	/*  */
	/**
	 * Upsample using a combination of allpass-based 2x upsampling and FIR interpolation
	 *
	 * @param SS I/O  Resampler state
	 * @param out O    Output signal
	 * @param in I    Input signal
	 * @param inLen I    Number of input samples
	 */
	private final void silk_resampler_private_IIR_FIR(
			final short out[], int outoffset,// java
			final short in[], int inoffset,// java
			int inLen)
	{
		// SAVE_STACK;

		final short[] buf = new short[(this.batchSize << 1) + Jresampler_rom.RESAMPLER_ORDER_FIR_12];

		/* Copy buffered samples to start of buffer */
		System.arraycopy( this.i16, 0, buf, 0, Jresampler_rom.RESAMPLER_ORDER_FIR_12 );

		/* Iterate over blocks of frameSizeIn input samples */
		int nSamplesIn;
		final int index_increment_Q16 = this.invRatio_Q16;
		while( true ) {
			nSamplesIn = ( inLen <= this.batchSize ? inLen : this.batchSize );

			/* Upsample 2x */
			silk_resampler_private_up2_HQ( this.sIIR, buf, Jresampler_rom.RESAMPLER_ORDER_FIR_12, in, inoffset, nSamplesIn );

			final int max_index_Q16 = ( nSamplesIn << (16 + 1) );         /* + 1 because 2x upsampling */
			outoffset = silk_resampler_private_IIR_FIR_INTERPOL( out, outoffset, buf, max_index_Q16, index_increment_Q16 );
			inoffset += nSamplesIn;
			inLen -= nSamplesIn;

			if( inLen > 0 ) {
				/* More iterations to do; copy last part of filtered signal to beginning of buffer */
				System.arraycopy( buf, nSamplesIn << 1, buf, 0, Jresampler_rom.RESAMPLER_ORDER_FIR_12 );
			} else {
				break;
			}
		}

		/* Copy last part of filtered signal to the state for the next call */
		System.arraycopy( buf, nSamplesIn << 1, this.i16, 0, Jresampler_rom.RESAMPLER_ORDER_FIR_12 );
		// RESTORE_STACK;
	}
	// end resampler_private_IIR_FIR.c

	// start resampler_private_down_FIR.c
	/**
	 * java changed: return outoffset
	 *
	 * @param out
	 * @param outoffset
	 * @param buf
	 * @param FIR_Coefs
	 * @param coffset
	 * @param FIR_Order
	 * @param FIR_Fracs
	 * @param max_index_Q16
	 * @param index_increment_Q16
	 * @return outoffset
	 */
	private static final int silk_resampler_private_down_FIR_INTERPOL(final short[] out, int outoffset,// java
			final int[] buf,
			final short[] FIR_Coefs, int coffset,// java
			final int FIR_Order, final int FIR_Fracs,
			final int max_index_Q16, final int index_increment_Q16
			)
	{
		switch( FIR_Order ) {
		case Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR0:
			for( int index_Q16 = 0; index_Q16 < max_index_Q16; index_Q16 += index_increment_Q16 ) {
				/* Integer part gives pointer to buffered input */
				int buf_ptr = ( index_Q16 >> 16 );// buf[ buf_ptr ]

				/* Fractional part gives interpolation coefficients */
				final int interpol_ind = (int)(((index_Q16 & 0xFFFF) * (long)FIR_Fracs) >> 16);

				/* Inner product */
				int interpol_ptr = coffset + (Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR0 / 2) * interpol_ind;// FIR_Coefs[ interpol_ptr ]
				int res_Q6 = (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ]) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr++ ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr   ] * (long)FIR_Coefs[ interpol_ptr   ] ) >> 16);// buf_ptr + 8
				buf_ptr += 17 - 8;
				interpol_ptr = coffset + (Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR0 / 2) * ( FIR_Fracs - 1 - interpol_ind );
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);// buf_ptr + 17
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr-- ] * (long)FIR_Coefs[ interpol_ptr++ ] ) >> 16);
				res_Q6 += (int)((buf[ buf_ptr   ] * (long)FIR_Coefs[ interpol_ptr   ] ) >> 16);// buf_ptr + 9

				/* Scale down, saturate and store in output array */
				res_Q6 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q6, 6 );
				out[outoffset++] = (short)(res_Q6 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q6 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q6));
			}
			break;
		case Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR1: {
			final long c0 = (long)FIR_Coefs[ coffset++ ];// java, + 0
			final long c1 = (long)FIR_Coefs[ coffset++ ];// java
			final long c2 = (long)FIR_Coefs[ coffset++ ];// java
			final long c3 = (long)FIR_Coefs[ coffset++ ];// java
			final long c4 = (long)FIR_Coefs[ coffset++ ];// java
			final long c5 = (long)FIR_Coefs[ coffset++ ];// java
			final long c6 = (long)FIR_Coefs[ coffset++ ];// java
			final long c7 = (long)FIR_Coefs[ coffset++ ];// java
			final long c8 = (long)FIR_Coefs[ coffset++ ];// java
			final long c9 = (long)FIR_Coefs[ coffset++ ];// java
			final long c10 = (long)FIR_Coefs[ coffset++ ];// java
			final long c11 = (long)FIR_Coefs[ coffset ];// java, + 11
			for( int index_Q16 = 0; index_Q16 < max_index_Q16; index_Q16 += index_increment_Q16 ) {
				/* Integer part gives pointer to buffered input */
				int buf_ptr_p = ( index_Q16 >> 16 );// buf[ buf_ptr ]
				int buf_ptr_n = buf_ptr_p + 23;// java

				/* Inner product */
				int res_Q6 = (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c0) >> 16);// + 0, + 23
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c1 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c2 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c3 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c4 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c5 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c6 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c7 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c8 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c9 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c10 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p ] + buf[ buf_ptr_n ] ) * c11 ) >> 16);// + 11, + 12

				/* Scale down, saturate and store in output array */
				res_Q6 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q6, 6 );
				out[outoffset++] = (short)(res_Q6 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q6 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q6));
			}
			break; }
		case Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR2: {
			final long c0 = (long)FIR_Coefs[ coffset++ ];// java. + 0
			final long c1 = (long)FIR_Coefs[ coffset++ ];// java
			final long c2 = (long)FIR_Coefs[ coffset++ ];// java
			final long c3 = (long)FIR_Coefs[ coffset++ ];// java
			final long c4 = (long)FIR_Coefs[ coffset++ ];// java
			final long c5 = (long)FIR_Coefs[ coffset++ ];// java
			final long c6 = (long)FIR_Coefs[ coffset++ ];// java
			final long c7 = (long)FIR_Coefs[ coffset++ ];// java
			final long c8 = (long)FIR_Coefs[ coffset++ ];// java
			final long c9 = (long)FIR_Coefs[ coffset++ ];// java
			final long c10 = (long)FIR_Coefs[ coffset++ ];// java
			final long c11 = (long)FIR_Coefs[ coffset++ ];// java
			final long c12 = (long)FIR_Coefs[ coffset++ ];// java
			final long c13 = (long)FIR_Coefs[ coffset++ ];// java
			final long c14 = (long)FIR_Coefs[ coffset++ ];// java
			final long c15 = (long)FIR_Coefs[ coffset++ ];// java
			final long c16 = (long)FIR_Coefs[ coffset++ ];// java
			final long c17 = (long)FIR_Coefs[ coffset ];// java, + 17
			for( int index_Q16 = 0; index_Q16 < max_index_Q16; index_Q16 += index_increment_Q16 ) {
				/* Integer part gives pointer to buffered input */
				int buf_ptr_p = ( index_Q16 >> 16 );// buf[ buf_ptr ]
				int buf_ptr_n = buf_ptr_p + 35;// java

				/* Inner product */
				int res_Q6 = (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c0) >> 16);// + 0, + 35
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c1 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c2 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c3 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c4 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c5 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c6 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c7 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c8 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c9 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c10 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c11 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c12 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c13 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c14 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c15 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p++ ] + buf[ buf_ptr_n-- ] ) * c16 ) >> 16);
				res_Q6 += (int)((( buf[ buf_ptr_p   ] + buf[ buf_ptr_n   ] ) * c17 ) >> 16);// + 17, + 18

				/* Scale down, saturate and store in output array */
				res_Q6 = JSigProc_FIX.silk_RSHIFT_ROUND( res_Q6, 6 );
				out[outoffset++] = (short)(res_Q6 > Short.MAX_VALUE ? Short.MAX_VALUE : (res_Q6 < Short.MIN_VALUE ? Short.MIN_VALUE : res_Q6));
			}
			break; }
		default:
			// celt_assert( 0 );
		}
		return outoffset;
	}

	/**
	 * Resample with a 2nd order AR filter followed by FIR interpolation
	 *
	 * @param SS I/O  Resampler state
	 * @param out O    Output signal
	 * @param in I    Input signal
	 * @param inLen I    Number of input samples
	 */
	private final void silk_resampler_private_down_FIR(
			final short out[], int outoffset,// java
			final short in[], int inoffset,// java
			int inLen)
	{
		int nSamplesIn;
		int max_index_Q16, index_increment_Q16;
		// SAVE_STACK;

		final int[] buf = new int[this.batchSize + this.FIR_Order];

		/* Copy buffered samples to start of buffer */
		System.arraycopy( this.i32, 0, buf, 0, this.FIR_Order );

		// FIR_Coefs = &S.Coefs[ 2 ];// java offset = 2 using in place
		final short[] coefs = this.Coefs;// java

		/* Iterate over blocks of frameSizeIn input samples */
		index_increment_Q16 = this.invRatio_Q16;
		while( true ) {
			nSamplesIn = ( inLen <= this.batchSize ? inLen : this.batchSize );

			/* Second-order AR filter (output in Q8) */
			silk_resampler_private_AR2( this.sIIR, 0, buf, this.FIR_Order, in, inoffset, this.Coefs, nSamplesIn );

			max_index_Q16 = ( nSamplesIn << 16 );

			/* Interpolate filtered signal */
			outoffset = silk_resampler_private_down_FIR_INTERPOL( out, outoffset, buf, coefs, 2/*FIR_Coefs*/, this.FIR_Order,
																		this.FIR_Fracs, max_index_Q16, index_increment_Q16 );

			inoffset += nSamplesIn;
			inLen -= nSamplesIn;

			if( inLen > 1 ) {
				/* More iterations to do; copy last part of filtered signal to beginning of buffer */
				System.arraycopy( buf, nSamplesIn, buf, 0, this.FIR_Order );
			} else {
				break;
			}
		}

		/* Copy last part of filtered signal to the state for the next call */
		System.arraycopy( buf, nSamplesIn, this.i32, 0, this.FIR_Order );
		//RESTORE_STACK;
	}
	// end resampler_private_down_FIR.c

	// start resampler.c
	/*
	 * Matrix of resampling methods used:
	 *                                 Fs_out (kHz)
	 *                        8      12     16     24     48
	 *
	 *               8        C      UF     U      UF     UF
	 *              12        AF     C      UF     U      UF
	 * Fs_in (kHz)  16        D      AF     C      UF     UF
	 *              24        AF     D      AF     C      U
	 *              48        AF     AF     AF     D      C
	 *
	 * C   . Copy (no resampling)
	 * D   . Allpass-based 2x downsampling
	 * U   . Allpass-based 2x upsampling
	 * UF  . Allpass-based 2x upsampling followed by FIR interpolation
	 * AF  . AR2 filter followed by FIR interpolation
	 */


	/* Tables with delay compensation values to equalize total delay for different modes */
	private static final byte delay_matrix_enc[/* 5 */][/* 3 */] = {
		/* in  \ out  8  12  16 */
		/*  8 */   {  6,  0,  3 },
		/* 12 */   {  0,  7,  3 },
		/* 16 */   {  0,  1, 10 },
		/* 24 */   {  0,  2,  6 },
		/* 48 */   { 18, 10, 12 }
		};

	private static final byte delay_matrix_dec[/* 3 */][/* 5 */] = {
		/* in  \ out  8  12  16  24  48 */
		/*  8 */   {  4,  0,  2,  0,  0 },
		/* 12 */   {  0,  9,  4,  7,  4 },
		/* 16 */   {  0,  3, 12,  7,  7 }
		};

	/** Simple way to make [8000, 12000, 16000, 24000, 48000] to [0, 1, 2, 3, 4] */
	private static final int rateID(final int R) {
		return ( ( ((R >> 12) - (R > 16000 ? 1 : 0)) >> (R > 24000 ? 1 : 0) ) - 1 );
	}

	private static final int USE_silk_resampler_copy                   = 0;
	private static final int USE_silk_resampler_private_up2_HQ_wrapper = 1;
	private static final int USE_silk_resampler_private_IIR_FIR        = 2;
	private static final int USE_silk_resampler_private_down_FIR       = 3;

	/**
	 * Initialize/reset the resampler state for a given pair of input/output sampling rates
	 *
	 * @param S I/O  Resampler state
	 * @param Fs_Hz_in I    Input sampling rate (Hz)
	 * @param Fs_Hz_out I    Output sampling rate (Hz)
	 * @param forEnc I    If 1: encoder; if 0: decoder
	 * @return
	 */
	final int silk_resampler_init(final int Fs_Hz_in, final int Fs_Hz_out, final boolean forEnc)
	{
		int up2x;

		/* Clear state */
		// silk_memset( S, 0, sizeof( silk_resampler_state_struct ) );
		clear();

		/* Input checking */
		if( forEnc ) {
			if( ( Fs_Hz_in != 8000 && Fs_Hz_in != 12000 && Fs_Hz_in != 16000 && Fs_Hz_in != 24000 && Fs_Hz_in != 48000 ) ||
					( Fs_Hz_out != 8000 && Fs_Hz_out != 12000 && Fs_Hz_out != 16000 ) ) {
				// celt_assert( 0 );
				return -1;
			}
			this.inputDelay = delay_matrix_enc[ rateID( Fs_Hz_in ) ][ rateID( Fs_Hz_out ) ];
		} else {
			if( ( Fs_Hz_in != 8000 && Fs_Hz_in != 12000 && Fs_Hz_in != 16000 ) ||
					( Fs_Hz_out != 8000 && Fs_Hz_out != 12000 && Fs_Hz_out != 16000 && Fs_Hz_out != 24000 && Fs_Hz_out != 48000 ) ) {
				// celt_assert( 0 );
				return -1;
			}
			this.inputDelay = delay_matrix_dec[ rateID( Fs_Hz_in ) ][ rateID( Fs_Hz_out ) ];
		}

		this.Fs_in_kHz  = Fs_Hz_in /  1000;
		this.Fs_out_kHz = Fs_Hz_out / 1000;

		/* Number of samples processed per batch */
		this.batchSize = this.Fs_in_kHz * RESAMPLER_MAX_BATCH_SIZE_MS;

		/* Find resampler with the right sampling ratio */
		up2x = 0;
		if( Fs_Hz_out > Fs_Hz_in ) {
			/* Upsample */
			if( Fs_Hz_out == (Fs_Hz_in << 1) ) {                            /* Fs_out : Fs_in = 2 : 1 */
				/* Special case: directly use 2x upsampler */
				this.resampler_function = USE_silk_resampler_private_up2_HQ_wrapper;
			} else {
				/* Default resampler */
				this.resampler_function = USE_silk_resampler_private_IIR_FIR;
				up2x = 1;
			}
		} else if ( Fs_Hz_out < Fs_Hz_in ) {
			/* Downsample */
			this.resampler_function = USE_silk_resampler_private_down_FIR;
			if( (Fs_Hz_out << 2) == (Fs_Hz_in * 3) ) {             /* Fs_out : Fs_in = 3 : 4 */
				this.FIR_Fracs = 3;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR0;
				this.Coefs = Jresampler_rom.silk_Resampler_3_4_COEFS;
			} else if( (Fs_Hz_out * 3) == (Fs_Hz_in << 1) ) {      /* Fs_out : Fs_in = 2 : 3 */
				this.FIR_Fracs = 2;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR0;
				this.Coefs = Jresampler_rom.silk_Resampler_2_3_COEFS;
			} else if( (Fs_Hz_out << 1) == Fs_Hz_in ) {                     /* Fs_out : Fs_in = 1 : 2 */
				this.FIR_Fracs = 1;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR1;
				this.Coefs = Jresampler_rom.silk_Resampler_1_2_COEFS;
			} else if( (Fs_Hz_out * 3) == Fs_Hz_in ) {                     /* Fs_out : Fs_in = 1 : 3 */
				this.FIR_Fracs = 1;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR2;
				this.Coefs = Jresampler_rom.silk_Resampler_1_3_COEFS;
			} else if( (Fs_Hz_out << 2) == Fs_Hz_in ) {                     /* Fs_out : Fs_in = 1 : 4 */
				this.FIR_Fracs = 1;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR2;
				this.Coefs = Jresampler_rom.silk_Resampler_1_4_COEFS;
			} else if( (Fs_Hz_out * 6) == Fs_Hz_in ) {                     /* Fs_out : Fs_in = 1 : 6 */
				this.FIR_Fracs = 1;
				this.FIR_Order = Jresampler_rom.RESAMPLER_DOWN_ORDER_FIR2;
				this.Coefs = Jresampler_rom.silk_Resampler_1_6_COEFS;
			} else {
				/* None available */
				// celt_assert( 0 );
				return -1;
			}
		} else {
			/* Input and output sampling rates are equal: copy */
			this.resampler_function = USE_silk_resampler_copy;
		}

		/* Ratio of input/output samples */
		this.invRatio_Q16 = ( ( Fs_Hz_in << (14 + up2x) ) / Fs_Hz_out ) << 2;
		/* Make sure the ratio is rounded up */
		while( ((int)(((long)this.invRatio_Q16 * Fs_Hz_out) >> 16)) < (Fs_Hz_in << up2x) ) {
			this.invRatio_Q16++;
		}

		return 0;
	}

	/**
	 * Resampler: convert from one sampling rate to another
	 * Input and output sampling rate are at most 48000 Hz
	 *
	 * @param S I/O  Resampler state
	 * @param out O    Output signal
	 * @param in I    Input signal
	 * @param inLen I    Number of input samples
	 * @return
	 */
	final int silk_resampler(
			final short out[], final int outoffset,// java
			final short in[], final int inoffset,// java
			final int inLen
		)
	{
		/* Need at least 1 ms of input data */
		// celt_assert( inLen >= S.Fs_in_kHz );
		/* Delay can't exceed the 1 ms of buffering */
		// celt_assert( S.inputDelay <= S.Fs_in_kHz );

		final int nSamples = this.Fs_in_kHz - this.inputDelay;

		/* Copy to delay buffer */
		System.arraycopy( in, inoffset, this.delayBuf, this.inputDelay, nSamples );

		switch( this.resampler_function ) {
		case USE_silk_resampler_private_up2_HQ_wrapper:
			silk_resampler_private_up2_HQ_wrapper( out, outoffset, this.delayBuf, 0, this.Fs_in_kHz );
			silk_resampler_private_up2_HQ_wrapper( out, outoffset + this.Fs_out_kHz, in, inoffset + nSamples, inLen - this.Fs_in_kHz );
			break;
		case USE_silk_resampler_private_IIR_FIR:
			silk_resampler_private_IIR_FIR( out, outoffset, this.delayBuf, 0, this.Fs_in_kHz );
			silk_resampler_private_IIR_FIR( out, outoffset + this.Fs_out_kHz, in, inoffset + nSamples, inLen - this.Fs_in_kHz );
			break;
		case USE_silk_resampler_private_down_FIR:
			silk_resampler_private_down_FIR( out, outoffset, this.delayBuf, 0, this.Fs_in_kHz );
			silk_resampler_private_down_FIR( out, outoffset + this.Fs_out_kHz, in, inoffset + nSamples, inLen - this.Fs_in_kHz );
			break;
		default:
			System.arraycopy( this.delayBuf, 0, out, outoffset, this.Fs_in_kHz );
			System.arraycopy( in, inoffset + nSamples, out, outoffset + this.Fs_out_kHz, ( inLen - this.Fs_in_kHz ) );
		}

		/* Copy to delay buffer */
		System.arraycopy( in, inoffset + inLen - this.inputDelay, this.delayBuf, 0, this.inputDelay );

		return 0;
	}
	// end resampler.c
}


