package silk;

import celt.Jec_enc;

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

// enc_API.c

abstract class Jenc_API extends Jcodec_API {
	// start energy_FLP
	/**
	 * sum of squares of a silk_float array, with result as double
	 *
	 * @param data
	 * @param doffset java added
	 * @param dataSize
	 * @return
	 */
	static final double silk_energy_FLP(final float[] data, int doffset, int dataSize)
	{
		/* 4x unrolled loop */
		double result = 0.0;
		dataSize += doffset;// java
		final int dataSize3 = dataSize - 3;// java
		while( doffset < dataSize3 ) {
			double v = (double)data[ doffset++ ];// java
			result += v * v;
			v = (double)data[ doffset++ ];// java
			result += v * v;
			v = (double)data[ doffset++ ];// java
			result += v * v;
			v = (double)data[ doffset++ ];// java
			result += v * v;
		}

		/* add any remaining products */
		while( doffset < dataSize ) {
			final double v = (double)data[ doffset++ ];// java
			result += v * v;
		}

		// silk_assert( result >= 0.0 );
		return result;
	}
	// end energy_FLP

	// start inner_product_FLP
	/** inner product of two silk_float arrays, with result as double
	 * @param data1
	 * @param doffset1
	 * @param data2
	 * @param doffset2
	 * @param dataSize
	 * @return
	 */
	static final double silk_inner_product_FLP(final float[] data1, int doffset1, final float[] data2, int doffset2, int dataSize)
	{
		/* 4x unrolled loop */
		double result = 0.0;
		dataSize += doffset1;// java
		final int dataSize3 = dataSize - 3;// java
		while( doffset1 < dataSize3 ) {// java changed
			result += (double)data1[ doffset1++ ] * (double)data2[ doffset2++ ];
			result += (double)data1[ doffset1++ ] * (double)data2[ doffset2++ ];
			result += (double)data1[ doffset1++ ] * (double)data2[ doffset2++ ];
			result += (double)data1[ doffset1++ ] * (double)data2[ doffset2++ ];
		}

		/* add any remaining products */
		while( doffset1 < dataSize ) {// java changed
			result += data1[ doffset1++ ] * (double)data2[ doffset2++ ];
		}

		return result;
	}
	// end inner_product_FLP

	// start LPC_analysis_filter_FLP

	/************************************************/
	/* LPC analysis filter                          */
	/* NB! State is kept internally and the         */
	/* filter always starts with zero state         */
	/* first Order output samples are set to zero   */
	/************************************************/
	/**
	 * 16th order LPC analysis filter, does not write first 16 samples
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 */
	private static final void silk_LPC_analysis_filter16_FLP(final float r_LPC[], final float PredCoef[], final float s[], int soffset, final int length)
	{// java soffset added
		soffset += 16 - 1;// java
		final float c0 = PredCoef[ 0 ];// java
		final float c1 = PredCoef[ 1 ];// java
		final float c2 = PredCoef[ 2 ];// java
		final float c3 = PredCoef[ 3 ];// java
		final float c4 = PredCoef[ 4 ];// java
		final float c5 = PredCoef[ 5 ];// java
		final float c6 = PredCoef[ 6 ];// java
		final float c7 = PredCoef[ 7 ];// java
		final float c8 = PredCoef[ 8 ];// java
		final float c9 = PredCoef[ 9 ];// java
		final float c10 = PredCoef[ 10 ];// java
		final float c11 = PredCoef[ 11 ];// java
		final float c12 = PredCoef[ 12 ];// java
		final float c13 = PredCoef[ 13 ];// java
		final float c14 = PredCoef[ 14 ];// java
		final float c15 = PredCoef[ 15 ];// java
		for( int ix = 16; ix < length; ix++, soffset++ ) {
			// int s_ptr = soffset + ix - 1;// s[ s_ptr ]

			/* short-term prediction */
			final float LPC_pred = s[ soffset ]  * c0  +
								s[ soffset -1 ]  * c1  +
								s[ soffset -2 ]  * c2  +
								s[ soffset -3 ]  * c3  +
								s[ soffset -4 ]  * c4  +
								s[ soffset -5 ]  * c5  +
								s[ soffset -6 ]  * c6  +
								s[ soffset -7 ]  * c7  +
								s[ soffset -8 ]  * c8  +
								s[ soffset -9 ]  * c9  +
								s[ soffset -10 ] * c10 +
								s[ soffset -11 ] * c11 +
								s[ soffset -12 ] * c12 +
								s[ soffset -13 ] * c13 +
								s[ soffset -14 ] * c14 +
								s[ soffset -15 ] * c15;

			/* prediction error */
			r_LPC[ix] = s[ soffset + 1 ] - LPC_pred;
		}
	}

	/**
	 * 12th order LPC analysis filter, does not write first 12 samples
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 */
	private static final void silk_LPC_analysis_filter12_FLP(final float r_LPC[], final float PredCoef[], final float s[], int soffset, final int length)
	{// java soffset added
		soffset += 12 - 1;// java
		final float c0 = PredCoef[ 0 ];// java
		final float c1 = PredCoef[ 1 ];// java
		final float c2 = PredCoef[ 2 ];// java
		final float c3 = PredCoef[ 3 ];// java
		final float c4 = PredCoef[ 4 ];// java
		final float c5 = PredCoef[ 5 ];// java
		final float c6 = PredCoef[ 0 ];// java
		final float c7 = PredCoef[ 1 ];// java
		final float c8 = PredCoef[ 2 ];// java
		final float c9 = PredCoef[ 3 ];// java
		final float c10 = PredCoef[ 4 ];// java
		final float c11 = PredCoef[ 5 ];// java
		for( int ix = 12; ix < length; ix++, soffset++ ) {
			// int s_ptr = &s[ix - 1];// s[ s_ptr ]

			/* short-term prediction */
			final float LPC_pred = s[ soffset ]  * c0  +
								s[ soffset -1 ]  * c1  +
								s[ soffset -2 ]  * c2  +
								s[ soffset -3 ]  * c3  +
								s[ soffset -4 ]  * c4  +
								s[ soffset -5 ]  * c5  +
								s[ soffset -6 ]  * c6  +
								s[ soffset -7 ]  * c7  +
								s[ soffset -8 ]  * c8  +
								s[ soffset -9 ]  * c9  +
								s[ soffset -10 ] * c10 +
								s[ soffset -11 ] * c11;

			/* prediction error */
			r_LPC[ix] = s[ soffset + 1 ] - LPC_pred;
		}
	}

	/**
	 * 10th order LPC analysis filter, does not write first 10 samples
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 */
	private static final void silk_LPC_analysis_filter10_FLP(final float r_LPC[], final float PredCoef[], final float s[], int soffset, final int length)
	{// java soffset added
		soffset += 10 - 1;// java
		final float c0 = PredCoef[ 0 ];// java
		final float c1 = PredCoef[ 1 ];// java
		final float c2 = PredCoef[ 2 ];// java
		final float c3 = PredCoef[ 3 ];// java
		final float c4 = PredCoef[ 4 ];// java
		final float c5 = PredCoef[ 5 ];// java
		final float c6 = PredCoef[ 6 ];// java
		final float c7 = PredCoef[ 7 ];// java
		final float c8 = PredCoef[ 8 ];// java
		final float c9 = PredCoef[ 9 ];// java
		for( int ix = 10; ix < length; ix++, soffset++ ) {
			// int s_ptr = &s[ix - 1];// s[ s_ptr ]

			/* short-term prediction */
			final float LPC_pred = s[ soffset ] * c0 +
								s[ soffset -1 ] * c1 +
								s[ soffset -2 ] * c2 +
								s[ soffset -3 ] * c3 +
								s[ soffset -4 ] * c4 +
								s[ soffset -5 ] * c5 +
								s[ soffset -6 ] * c6 +
								s[ soffset -7 ] * c7 +
								s[ soffset -8 ] * c8 +
								s[ soffset -9 ] * c9;

			/* prediction error */
			r_LPC[ix] = s[ soffset + 1 ] - LPC_pred;
		}
	}

	/**
	 * 8th order LPC analysis filter, does not write first 8 samples
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 */
	private static final void silk_LPC_analysis_filter8_FLP(final float r_LPC[], final float PredCoef[], final float s[], int soffset, final int length)
	{// java soffset added
		soffset += 8 - 1;// java
		final float c0 = PredCoef[ 0 ];// java
		final float c1 = PredCoef[ 1 ];// java
		final float c2 = PredCoef[ 2 ];// java
		final float c3 = PredCoef[ 3 ];// java
		final float c4 = PredCoef[ 4 ];// java
		final float c5 = PredCoef[ 5 ];// java
		final float c6 = PredCoef[ 6 ];// java
		final float c7 = PredCoef[ 7 ];// java
		for( int ix = 8; ix < length; ix++, soffset++ ) {
			// int s_ptr = &s[ix - 1];// s[ s_ptr ]

			/* short-term prediction */
			final float LPC_pred = s[ soffset ] * c0 +
								s[ soffset -1 ] * c1 +
								s[ soffset -2 ] * c2 +
								s[ soffset -3 ] * c3 +
								s[ soffset -4 ] * c4 +
								s[ soffset -5 ] * c5 +
								s[ soffset -6 ] * c6 +
								s[ soffset -7 ] * c7;

			/* prediction error */
			r_LPC[ix] = s[ soffset + 1 ] - LPC_pred;
		}
	}

	/**
	 * 6th order LPC analysis filter, does not write first 6 samples
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 */
	private static final void silk_LPC_analysis_filter6_FLP(final float r_LPC[], final float PredCoef[], final float s[], int soffset, final int length)
	{// java soffset added
		soffset += 6 - 1;// java
		final float c0 = PredCoef[ 0 ];// java
		final float c1 = PredCoef[ 1 ];// java
		final float c2 = PredCoef[ 2 ];// java
		final float c3 = PredCoef[ 3 ];// java
		final float c4 = PredCoef[ 4 ];// java
		final float c5 = PredCoef[ 5 ];// java
		for( int ix = 6; ix < length; ix++, soffset++ ) {
			// int s_ptr = &s[ix - 1];// s[ s_ptr ]

			/* short-term prediction */
			final float LPC_pred = s[ soffset ] * c0 +
								s[ soffset -1 ] * c1 +
								s[ soffset -2 ] * c2 +
								s[ soffset -3 ] * c3 +
								s[ soffset -4 ] * c4 +
								s[ soffset -5 ] * c5;

			/* prediction error */
			r_LPC[ix] = s[ soffset + 1 ] - LPC_pred;
		}
	}

	/**
	 * LPC analysis filter
	 * NB! State is kept internally and the
	 * filter always starts with zero state
	 * first Order output samples are set to zero
	 *
	 * @param r_LPC O    LPC residual signal
	 * @param PredCoef I    LPC coefficients
	 * @param s I    Input signal
	 * @param soffset I java an offset for the s
	 * @param length I    Length of input signal
	 * @param Order I    LPC order
	 */
	static final void silk_LPC_analysis_filter_FLP(final float r_LPC[], final float PredCoef[],
			final float s[], final int soffset,// java
			final int length, final int Order)
	{
		// celt_assert( Order <= length );

		switch( Order ) {
		case 6:
			silk_LPC_analysis_filter6_FLP(  r_LPC, PredCoef, s, soffset, length );
			break;

		case 8:
			silk_LPC_analysis_filter8_FLP(  r_LPC, PredCoef, s, soffset, length );
			break;

		case 10:
			silk_LPC_analysis_filter10_FLP( r_LPC, PredCoef, s, soffset, length );
			break;

		case 12:
			silk_LPC_analysis_filter12_FLP( r_LPC, PredCoef, s, soffset, length );
			break;

		case 16:
			silk_LPC_analysis_filter16_FLP( r_LPC, PredCoef, s, soffset, length );
			break;

		default:
			// celt_assert( 0 );
			break;
		}

		/* Set first Order output samples to zero */
		// silk_memset( r_LPC, 0, Order * sizeof( silk_float ) );
		int i = Order;
		do {
			r_LPC[--i] = 0;
		} while( i > 0 );
	}
	// end LPC_analysis_filter_FLP

	// start shell_coder.c
	/**
	 *
	 * @param out O    combined pulses vector [len]
	 * @param in I    input vector       [2 * len]
	 * @param inoffset I java an offset for the in
	 * @param len I    number of OUTPUT samples
	 */
	private static final void combine_pulses(final int[] out, final int[] in, final int inoffset, final int len)
	{// java inoffset is added
		for( int k = 0; k < len; k++ ) {
			int k2 = inoffset + (k << 1);// java
			int v = in[ k2++ ];// java
			v += in[ k2 ];// java
			out[ k ] = v;
		}
	}

	/**
	 *
	 * @param psRangeEnc I/O  compressor data structure
	 * @param p_child1 I    pulse amplitude of first child subframe
	 * @param p I    pulse amplitude of current subframe
	 * @param shell_table I    table of shell cdfs
	 */
	private static final void encode_split(final Jec_enc psRangeEnc,
			final int p_child1, final int p, final char[] shell_table
		)
	{
		if( p > 0 ) {
			psRangeEnc.ec_enc_icdf( p_child1, shell_table, Jtables_pulses_per_block.silk_shell_code_table_offsets[ p ], 8 );
		}
	}
	/**
	 * Shell encoder, operates on one shell code frame of 16 pulses
	 *
	 * @param psRangeEnc I/O  compressor data structure
	 * @param pulses0 I    data: nonnegative pulse amplitudes
	 * @param poffset I java an offset for the pulses0
	 */
	private static final void silk_shell_encoder(final Jec_enc psRangeEnc, final int[] pulses0, final int poffset)// java
	{
		final int pulses1[] = new int[ 8 ];
		final int pulses2[] = new int[ 4 ];
		final int pulses3[] = new int[ 2 ];
		final int pulses4[] = new int[ 1 ];

		/* this function operates on one shell code frame of 16 pulses */
		// silk_assert( SHELL_CODEC_FRAME_LENGTH == 16 );

		/* tree representation per pulse-subframe */
		combine_pulses( pulses1, pulses0, poffset, 8 );
		combine_pulses( pulses2, pulses1, 0, 4 );
		combine_pulses( pulses3, pulses2, 0, 2 );
		combine_pulses( pulses4, pulses3, 0, 1 );

		encode_split( psRangeEnc, pulses3[  0 ], pulses4[ 0 ], Jtables_pulses_per_block.silk_shell_code_table3 );

		encode_split( psRangeEnc, pulses2[  0 ], pulses3[ 0 ], Jtables_pulses_per_block.silk_shell_code_table2 );

		encode_split( psRangeEnc, pulses1[  0 ], pulses2[ 0 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		encode_split( psRangeEnc, pulses0[ poffset ], pulses1[ 0 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		encode_split( psRangeEnc, pulses0[ poffset + 2 ], pulses1[ 1 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		encode_split( psRangeEnc, pulses1[  2 ], pulses2[ 1 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		encode_split( psRangeEnc, pulses0[ poffset +  4 ], pulses1[ 2 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		encode_split( psRangeEnc, pulses0[ poffset +  6 ], pulses1[ 3 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		encode_split( psRangeEnc, pulses2[  2 ], pulses3[ 1 ], Jtables_pulses_per_block.silk_shell_code_table2 );

		encode_split( psRangeEnc, pulses1[  4 ], pulses2[ 2 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		encode_split( psRangeEnc, pulses0[ poffset +  8 ], pulses1[ 4 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		encode_split( psRangeEnc, pulses0[ poffset + 10 ], pulses1[ 5 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		encode_split( psRangeEnc, pulses1[  6 ], pulses2[ 3 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		encode_split( psRangeEnc, pulses0[ poffset + 12 ], pulses1[ 6 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		encode_split( psRangeEnc, pulses0[ poffset + 14 ], pulses1[ 7 ], Jtables_pulses_per_block.silk_shell_code_table0 );
	}
	// end shell_coder.c

	// start code_signs.c
	/*#define silk_enc_map(a)                ((a) > 0 ? 1 : 0)*/
	/* shifting avoids if-statement */
	/* private static final int silk_enc_map(final int a) {
		return ( (a >> 15) + 1 );// ( silk_RSHIFT( (a), 15 ) + 1 )
	}*/
	/**
	 * Encodes signs of excitation
	 *
	 * @param psRangeEnc I/O  Compressor data structure
	 * @param pulses I    pulse signal
	 * @param length I    length of input
	 * @param signalType I    Signal type
	 * @param quantOffsetType I    Quantization offset type
	 * @param sum_pulses I    Sum of absolute pulses per block
	 */
	private static final void silk_encode_signs(final Jec_enc psRangeEnc,
			final byte pulses[], int length, final int signalType, final int quantOffsetType, final int sum_pulses[/* MAX_NB_SHELL_BLOCKS */])
	{
		final char icdf[] = new char[ 2 ];// java uint8 to char

		// icdf[ 1 ] = 0;// java already zeroed
		int q_ptr = 0;// pulses[ q_ptr ]
		final int icdf_ptr = 7 * (quantOffsetType + (signalType << 1));
		final char[] sign_iCDF = Jtables_pulses_per_block.silk_sign_iCDF;// sign_iCDF[ icdf_ptr ]
		length = ( length + Jdefine.SHELL_CODEC_FRAME_LENGTH / 2 ) >> Jdefine.LOG2_SHELL_CODEC_FRAME_LENGTH;
		for( int i = 0; i < length; i++ ) {
			int p = sum_pulses[ i ];
			if( p > 0 ) {
				p &= 0x1F;
				icdf[ 0 ] = sign_iCDF[ icdf_ptr + (p <= 6 ? p : 6) ];
				for( int j = 0; j < Jdefine.SHELL_CODEC_FRAME_LENGTH; j++ ) {
					if( pulses[ q_ptr + j ] != 0 ) {
						// psRangeEnc.ec_enc_icdf( silk_enc_map( pulses[ q_ptr + j ]), icdf, 0, 8 );
						psRangeEnc.ec_enc_icdf( (( pulses[ q_ptr + j ]) >> 15) + 1, icdf, 0, 8 );
					}
				}
			}
			q_ptr += Jdefine.SHELL_CODEC_FRAME_LENGTH;
		}
	}
	// end code_signs.c

	// start encode_pulses.c
	/**
	 * Encode quantization indices of excitation
	 *
	 * @param pulses_comb O
	 * @param coffset I java an offset for the pulses
	 * @param pulses_in I
	 * @param inoffset I java an offset for the pulses_in
	 * @param max_pulses I    max value for sum of pulses
	 * @param len I    number of output values
	 * @return return ok
	 */
	private static final boolean combine_and_check(final int[] pulses_comb, int coffset,// java
			final int[] pulses_in, final int inoffset,// java
			final int max_pulses, int len)
	{
		len += coffset;// java
		for( int k2 = inoffset; coffset < len; ) {
			int sum = pulses_in[ k2++ ];
			sum += pulses_in[ k2++ ];
			if( sum > max_pulses ) {
				return true;
			}
			pulses_comb[ coffset++ ] = sum;
		}

		return false;
	}

	/**
	 * Encode quantization indices of excitation
	 *
	 * @param psRangeEnc I/O  compressor data structure
	 * @param signalType I    Signal type
	 * @param quantOffsetType I    quantOffsetType
	 * @param pulses I    quantization indices
	 * @param frame_length I    Frame length
	 */
	static final void silk_encode_pulses(final Jec_enc psRangeEnc, final int signalType, final int quantOffsetType, final byte pulses[], final int frame_length)
	{
		// SAVE_STACK;

		//silk_memset( pulses_comb, 0, 8 * sizeof( opus_int ) ); /* Fixing Valgrind reported problem*/

		/****************************/
		/* Prepare for shell coding */
		/****************************/
		/* Calculate number of shell blocks */
		// silk_assert( 1 << LOG2_SHELL_CODEC_FRAME_LENGTH == SHELL_CODEC_FRAME_LENGTH );
		int iter = frame_length >> Jdefine.LOG2_SHELL_CODEC_FRAME_LENGTH;
		if( iter * Jdefine.SHELL_CODEC_FRAME_LENGTH < frame_length ) {
			// celt_assert( frame_length == 12 * 10 ); /* Make sure only happens for 10 ms @ 12 kHz */
			iter++;
			// silk_memset( &pulses[ frame_length ], 0, Jdefine.SHELL_CODEC_FRAME_LENGTH * sizeof(opus_int8));
			for( int i = frame_length, ie = i + Jdefine.SHELL_CODEC_FRAME_LENGTH; i < ie; i++ ) {
				pulses[i] = 0;
			}
		}

		/* Take the absolute value of the pulses */
		final int[] abs_pulses = new int[iter * Jdefine.SHELL_CODEC_FRAME_LENGTH];
		// silk_assert( !( SHELL_CODEC_FRAME_LENGTH & 3 ) );
		for( int i = 0, ie = iter * Jdefine.SHELL_CODEC_FRAME_LENGTH; i < ie; /* i+= 4*/ ) {
			int v = (int)pulses[ i ];// java
			if( v < 0 ) {
				v = -v;
			}
			abs_pulses[i++] = v;
			v = (int)pulses[ i ];// java
			if( v < 0 ) {
				v = -v;
			}
			abs_pulses[i++] = v;
			v = (int)pulses[ i ];// java
			if( v < 0 ) {
				v = -v;
			}
			abs_pulses[i++] = v;
			v = (int)pulses[ i ];// java
			if( v < 0 ) {
				v = -v;
			}
			abs_pulses[i++] = v;
		}

		final int pulses_comb[] = new int[ 8 ];// java already zeroed
		/* Calc sum pulses per shell code frame */
		final int[] sum_pulses = new int[iter];
		final int[] nRshifts = new int[iter];
		int abs_pulses_ptr = 0;// abs_pulses[ abs_pulses_ptr ]
		for( int i = 0; i < iter; i++ ) {
			nRshifts[ i ] = 0;

			while( true ) {
				/* 1+1 -> 2 */
				boolean scale_down = combine_and_check( pulses_comb, 0, abs_pulses, abs_pulses_ptr, Jtables_pulses_per_block.silk_max_pulses_table[ 0 ], 8 );
				/* 2+2 -> 4 */
				scale_down |= combine_and_check( pulses_comb, 0, pulses_comb, 0, Jtables_pulses_per_block.silk_max_pulses_table[ 1 ], 4 );
				/* 4+4 -> 8 */
				scale_down |= combine_and_check( pulses_comb, 0, pulses_comb, 0, Jtables_pulses_per_block.silk_max_pulses_table[ 2 ], 2 );
				/* 8+8 -> 16 */
				scale_down |= combine_and_check( sum_pulses, i, pulses_comb, 0, Jtables_pulses_per_block.silk_max_pulses_table[ 3 ], 1 );

				if( scale_down ) {
					/* We need to downscale the quantization signal */
					nRshifts[ i ]++;
					for( int k = abs_pulses_ptr, ke = k + Jdefine.SHELL_CODEC_FRAME_LENGTH; k < ke; k++ ) {
						abs_pulses[ k ] >>= 1;
					}
				} else {
					/* Jump out of while(1) loop and go to next shell coding frame */
					break;
				}
			}
			abs_pulses_ptr += Jdefine.SHELL_CODEC_FRAME_LENGTH;
		}

		/**************/
		/* Rate level */
		/**************/
		/* find rate level that leads to fewest bits for coding of pulses per block info */
		int RateLevelIndex = 0;
		int minSumBits_Q5 = Integer.MAX_VALUE;
		final char[] levels = Jtables_pulses_per_block.silk_rate_levels_BITS_Q5[ signalType >> 1 ];// java
		for( int k = 0; k < Jdefine.N_RATE_LEVELS - 1; k++ ) {
			final char[] nBits_ptr = Jtables_pulses_per_block.silk_pulses_per_block_BITS_Q5[ k ];
			int sumBits_Q5 = levels[ k ];
			for( int i = 0; i < iter; i++ ) {
				if( nRshifts[ i ] > 0 ) {
					sumBits_Q5 += nBits_ptr[ Jdefine.SILK_MAX_PULSES + 1 ];
				} else {
					sumBits_Q5 += nBits_ptr[ sum_pulses[ i ] ];
				}
			}
			if( sumBits_Q5 < minSumBits_Q5 ) {
				minSumBits_Q5 = sumBits_Q5;
				RateLevelIndex = k;
			}
		}
		psRangeEnc.ec_enc_icdf( RateLevelIndex, Jtables_pulses_per_block.silk_rate_levels_iCDF[ signalType >> 1 ], 0, 8 );

		/***************************************************/
		/* Sum-Weighted-Pulses Encoding                    */
		/***************************************************/
		final char[] cdf_ptr = Jtables_pulses_per_block.silk_pulses_per_block_iCDF[ RateLevelIndex ];
		for( int i = 0; i < iter; i++ ) {
			if( nRshifts[ i ] == 0 ) {
				psRangeEnc.ec_enc_icdf( sum_pulses[ i ], cdf_ptr, 0, 8 );
			} else {
				psRangeEnc.ec_enc_icdf( Jdefine.SILK_MAX_PULSES + 1, cdf_ptr, 0, 8 );
				for( int k = 0; k < nRshifts[ i ] - 1; k++ ) {
					psRangeEnc.ec_enc_icdf( Jdefine.SILK_MAX_PULSES + 1, Jtables_pulses_per_block.silk_pulses_per_block_iCDF[ Jdefine.N_RATE_LEVELS - 1 ], 0, 8 );
				}
				psRangeEnc.ec_enc_icdf( sum_pulses[ i ], Jtables_pulses_per_block.silk_pulses_per_block_iCDF[ Jdefine.N_RATE_LEVELS - 1 ], 0, 8 );
			}
		}

		/******************/
		/* Shell Encoding */
		/******************/
		for( int i = 0; i < iter; i++ ) {
			if( sum_pulses[ i ] > 0 ) {
				silk_shell_encoder( psRangeEnc, abs_pulses, i * Jdefine.SHELL_CODEC_FRAME_LENGTH );
			}
		}

		/****************/
		/* LSB Encoding */
		/****************/
		for( int i = 0; i < iter; i++ ) {
			if( nRshifts[ i ] > 0 ) {
				// final int pulses_ptr = i * Jdefine.SHELL_CODEC_FRAME_LENGTH;// pulses[ pulses_ptr ]
				final int nLS = nRshifts[ i ] - 1;
				for( int k = i * Jdefine.SHELL_CODEC_FRAME_LENGTH, ke = k + Jdefine.SHELL_CODEC_FRAME_LENGTH; k < ke; k++ ) {
					//final int abs_q = Math.abs( (int)pulses[ k ] );// FIXME why casting to int8?
					int abs_q = (int)pulses[ k ];
					if( abs_q < 0 ) {
						abs_q = -abs_q;
					}
					for( int j = nLS; j > 0; j-- ) {
						final int bit = (abs_q >> j) & 1;
						psRangeEnc.ec_enc_icdf( bit, Jtables_other.silk_lsb_iCDF, 0, 8 );
					}
					final int bit = abs_q & 1;
					psRangeEnc.ec_enc_icdf( bit, Jtables_other.silk_lsb_iCDF, 0, 8 );
				}
			}
		}

		/****************/
		/* Encode signs */
		/****************/
		silk_encode_signs( psRangeEnc, pulses, frame_length, signalType, quantOffsetType, sum_pulses );
		// RESTORE_STACK;
	}
	// end encode_pulses.c

	// start resampler_private_AR2.c
	/**
	 * Second order AR filter with single delay elements
	 *
	 * @param S I/O  State vector [ 2 ]
	 * @param soffset I java an offset for the S
	 * @param out_Q8 O    Output signal
	 * @param outoffset I java an offset for the out_Q8
	 * @param in I    Input signal
	 * @param inoffset I java an offset for the in
	 * @param A_Q14 I    AR coefficients, Q14
	 * @param len I    Signal length
	 */
	static final void silk_resampler_private_AR2(final int S[], final int soffset,// java
			final int out_Q8[], int outoffset,// java
			final short in[], int inoffset,// java
			final short A_Q14[], int len)
	{
		int s0 = S[ soffset ];// java
		int s1 = S[ soffset + 1 ];// java
		final long A_Q14_0 = (long)A_Q14[ 0 ];// java
		final long A_Q14_1 = (long)A_Q14[ 1 ];// java
		for( len += inoffset; inoffset < len; ) {
			int out32 = ( s0 + ((int)in[ inoffset++ ] << 8) );
			out_Q8[ outoffset++ ] = out32;
			out32 <<= 2;
			s0 = s1 + (int)((out32 * A_Q14_0) >> 16);
			s1 = (int)((out32 * A_Q14_1) >> 16);
		}
		S[ soffset ] = s0;// java
		S[ soffset + 1 ] = s1;// java

	}
	// end resampler_private_AR2.c
}