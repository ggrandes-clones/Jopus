package silk;

import celt.Jec_dec;

abstract class Jdec_API extends Jcodec_API {
	// start SigProc_FIX.h
	/** PSEUDO-RANDOM GENERATOR
	 * Make sure to store the result as the seed for the next call (also in between
	 * frames), otherwise result won't be random at all. When only using some of the
	 * bits, take the most significant bits by right-shifting.*/
	private static final int RAND_MULTIPLIER = 196314165;
	private static final int RAND_INCREMENT  = 907633515;
	static final int silk_RAND(final int seed) {
		return RAND_INCREMENT + (seed * RAND_MULTIPLIER);
	}
	// end SigProc_FIX.h

	// start shell_coder.c
	/**
	 *
	 * @param p_child1 O    pulse amplitude of first child subframe
	 * @param p_child2 O    pulse amplitude of second child subframe
	 * @param psRangeDec I/O  Compressor data structure
	 * @param p I    pulse amplitude of current subframe
	 * @param shell_table I    table of shell cdfs
	 */
	private static final void decode_split(final short[] p_child1, final int offset1,// java
			final short[] p_child2, final int offset2,// java
			final Jec_dec psRangeDec, final int p, final char[] shell_table)
	{
		if( p > 0 ) {
			final int v = psRangeDec.ec_dec_icdf( shell_table, Jtables_pulses_per_block.silk_shell_code_table_offsets[ p ], 8 );
			p_child1[ offset1 ] = (short)v;// FIXME int to int16
			p_child2[ offset2 ] = (short)(p - v);
		} else {
			p_child1[ offset1 ] = 0;
			p_child2[ offset2 ] = 0;
		}
	}
	/**
	 * Shell decoder, operates on one shell code frame of 16 pulses
	 *
	 * @param pulses0 O    data: nonnegative pulse amplitudes
	 * @param psRangeDec I/O  Compressor data structure
	 * @param pulses4 I    number of pulses per pulse-subframe
	 */
	private static final void silk_shell_decoder(final short[] pulses0, final int poffset, final Jec_dec psRangeDec, final int pulses4)
	{// java poffset is added
		final short pulses3[] = new short[ 2 ];
		final short pulses2[] = new short[ 4 ];
		final short pulses1[] = new short[ 8 ];

		/* this function operates on one shell code frame of 16 pulses */
		// silk_assert( SHELL_CODEC_FRAME_LENGTH == 16 );

		decode_split( pulses3,  0, pulses3,  1, psRangeDec, pulses4,      Jtables_pulses_per_block.silk_shell_code_table3 );

		decode_split( pulses2,  0, pulses2,  1, psRangeDec, pulses3[ 0 ], Jtables_pulses_per_block.silk_shell_code_table2 );

		decode_split( pulses1,  0, pulses1,  1, psRangeDec, pulses2[ 0 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		decode_split( pulses0, poffset + 0, pulses0, poffset + 1, psRangeDec, pulses1[ 0 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		decode_split( pulses0, poffset + 2, pulses0, poffset + 3, psRangeDec, pulses1[ 1 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		decode_split( pulses1,  2, pulses1,  3, psRangeDec, pulses2[ 1 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		decode_split( pulses0, poffset + 4, pulses0, poffset + 5, psRangeDec, pulses1[ 2 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		decode_split( pulses0, poffset + 6, pulses0, poffset + 7, psRangeDec, pulses1[ 3 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		decode_split( pulses2,  2, pulses2,  3, psRangeDec, pulses3[ 1 ], Jtables_pulses_per_block.silk_shell_code_table2 );

		decode_split( pulses1,  4, pulses1,  5, psRangeDec, pulses2[ 2 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		decode_split( pulses0, poffset + 8, pulses0, poffset + 9, psRangeDec, pulses1[ 4 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		decode_split( pulses0, poffset +10, pulses0, poffset +11, psRangeDec, pulses1[ 5 ], Jtables_pulses_per_block.silk_shell_code_table0 );

		decode_split( pulses1,  6, pulses1,  7, psRangeDec, pulses2[ 3 ], Jtables_pulses_per_block.silk_shell_code_table1 );
		decode_split( pulses0, poffset +12, pulses0, poffset +13, psRangeDec, pulses1[ 6 ], Jtables_pulses_per_block.silk_shell_code_table0 );
		decode_split( pulses0, poffset +14, pulses0, poffset +15, psRangeDec, pulses1[ 7 ], Jtables_pulses_per_block.silk_shell_code_table0 );
	}
	// end shell_coder.c

	//
	/*#define silk_dec_map(a)                ((a) > 0 ? 1 : -1)*/
	/* shifting avoids if-statement */
	/* private static final int silk_dec_map(final int a) {
		return ( ( a << 1 ) - 1 );// ( silk_LSHIFT( (a),  1 ) - 1 )
	}*/
	/**
	 * Decodes signs of excitation
	 *
	 * @param psRangeDec I/O  Compressor data structure
	 * @param pulses I/O  pulse signal
	 * @param length I    length of input
	 * @param signalType I    Signal type
	 * @param quantOffsetType I    Quantization offset type
	 * @param sum_pulses I    Sum of absolute pulses per block
	 */
	private static final void silk_decode_signs(final Jec_dec psRangeDec, final short pulses[], int length,
			final int signalType, final int quantOffsetType, final int sum_pulses[/* MAX_NB_SHELL_BLOCKS */] )
	{
		final char icdf[] = new char[ 2 ];// java uint8 to char

		// icdf[ 1 ] = 0;// java already zeroed
		int q_ptr = 0;// pulses[ q_ptr ]
		final int icdf_ptr = 7 * (quantOffsetType + (signalType << 1));
		final char[] silk_sign_iCDF = Jtables_pulses_per_block.silk_sign_iCDF;// silk_sign_iCDF[ icdf_ptr ]
		length = (length + Jdefine.SHELL_CODEC_FRAME_LENGTH / 2) >> Jdefine.LOG2_SHELL_CODEC_FRAME_LENGTH;
		for( int i = 0; i < length; i++ ) {
			int p = sum_pulses[ i ];
			if( p > 0 ) {
				p &= 0x1F;
				icdf[ 0 ] = silk_sign_iCDF[icdf_ptr + (p <= 6 ? p : 6)];
				for( int j = 0; j < Jdefine.SHELL_CODEC_FRAME_LENGTH; j++ ) {
					if( pulses[ q_ptr + j ] > 0 ) {
						/* attach sign */
/* #if 0
						// conditional implementation
						if( ec_dec_icdf( psRangeDec, icdf, 8 ) == 0 ) {
							q_ptr[ j ] = -q_ptr[ j ];
						}
#else */
						/* implementation with shift, subtraction, multiplication */
						// pulses[ q_ptr + j ] *= silk_dec_map( psRangeDec.ec_dec_icdf( icdf, 0, 8 ) );
						pulses[ q_ptr + j ] *= (psRangeDec.ec_dec_icdf( icdf, 0, 8 ) << 1) - 1;
//#endif
					}
				}
			}
			q_ptr += Jdefine.SHELL_CODEC_FRAME_LENGTH;
		}
	}
	//

	// start decode_pulses.c
	/**
	 * Decode quantization indices of excitation
	 *
	 * @param psRangeDec I/O  Compressor data structure
	 * @param pulses O    Excitation signal
	 * @param signalType I    Sigtype
	 * @param quantOffsetType I    quantOffsetType
	 * @param frame_length I    Frame length
	 */
	static final void silk_decode_pulses(final Jec_dec psRangeDec, final short pulses[],
			final int signalType, final int quantOffsetType, final int frame_length)
	{
		/*********************/
		/* Decode rate level */
		/*********************/
		final int RateLevelIndex = psRangeDec.ec_dec_icdf( Jtables_pulses_per_block.silk_rate_levels_iCDF[ signalType >> 1], 0, 8 );

		/* Calculate number of shell blocks */
		// silk_assert( 1 << Jdefine.LOG2_SHELL_CODEC_FRAME_LENGTH == Jdefine.SHELL_CODEC_FRAME_LENGTH );
		int iter = frame_length >> Jdefine.LOG2_SHELL_CODEC_FRAME_LENGTH;
		if( iter * Jdefine.SHELL_CODEC_FRAME_LENGTH < frame_length ) {
			// celt_assert( frame_length == 12 * 10 ); /* Make sure only happens for 10 ms @ 12 kHz */
			iter++;
		}

		/***************************************************/
		/* Sum-Weighted-Pulses Decoding                    */
		/***************************************************/
		final int sum_pulses[] = new int[ Jdefine.MAX_NB_SHELL_BLOCKS ];
		final int nLshifts[] = new int[ Jdefine.MAX_NB_SHELL_BLOCKS ];
		final char[] cdf_ptr = Jtables_pulses_per_block.silk_pulses_per_block_iCDF[ RateLevelIndex ];
		for( int i = 0; i < iter; i++ ) {
			nLshifts[ i ] = 0;
			sum_pulses[ i ] = psRangeDec.ec_dec_icdf( cdf_ptr, 0, 8 );

			/* LSB indication */
			while( sum_pulses[ i ] == Jdefine.SILK_MAX_PULSES + 1 ) {
				nLshifts[ i ]++;
				/* When we've already got 10 LSBs, we shift the table to not allow (SILK_MAX_PULSES + 1) */
				sum_pulses[ i ] = psRangeDec.ec_dec_icdf(
						Jtables_pulses_per_block.silk_pulses_per_block_iCDF[ Jdefine.N_RATE_LEVELS - 1], ( nLshifts[ i ] == 10 ? 1 : 0 ), 8 );
			}
		}

		/***************************************************/
		/* Shell decoding                                  */
		/***************************************************/
		for( int i = 0; i < iter; i++ ) {
			if( sum_pulses[ i ] > 0 ) {
				silk_shell_decoder( pulses, i * Jdefine.SHELL_CODEC_FRAME_LENGTH, psRangeDec, sum_pulses[ i ] );
			} else {
				// silk_memset( &pulses[ silk_SMULBB( i, Jdefine.SHELL_CODEC_FRAME_LENGTH ) ], 0, SHELL_CODEC_FRAME_LENGTH * sizeof( pulses[0] ) );
				for( int k = i * Jdefine.SHELL_CODEC_FRAME_LENGTH, ke = k + Jdefine.SHELL_CODEC_FRAME_LENGTH; k < ke; k++ ) {
					pulses[k] = 0;
				}
			}
		}

		/***************************************************/
		/* LSB Decoding                                    */
		/***************************************************/
		for( int i = 0; i < iter; i++ ) {
			if( nLshifts[ i ] > 0 ) {
				final int nLS = nLshifts[ i ];
				int pulses_ptr = i * Jdefine.SHELL_CODEC_FRAME_LENGTH;// pulses[ pulses_ptr ]
				for( final int ke = Jdefine.SHELL_CODEC_FRAME_LENGTH + pulses_ptr; pulses_ptr < ke; pulses_ptr++ ) {// java
					int abs_q = (int)pulses[ pulses_ptr ];
					for( int j = 0; j < nLS; j++ ) {
						abs_q = abs_q << 1;
						abs_q += psRangeDec.ec_dec_icdf( Jtables_other.silk_lsb_iCDF, 0, 8 );
					}
					pulses[ pulses_ptr ] = (short)abs_q;
				}
				/* Mark the number of pulses non-zero for sign decoding. */
				sum_pulses[ i ] |= nLS << 5;
			}
		}

		/****************************************/
		/* Decode and add signs to pulse signal */
		/****************************************/
		silk_decode_signs( psRangeDec, pulses, frame_length, signalType, quantOffsetType, sum_pulses );
	}
	// end decode_pulses.c

	// start LPC_analysis_filter.c
	/**
	 * LPC analysis filter
	 * NB! State is kept internally and the
	 * filter always starts with zero state
	 * first d output samples are set to zero
	 *
	 * @param out O    Output signal
	 * @param in I    Input signal
	 * @param B I    MA prediction coefficients, Q12 [order]
	 * @param len I    Signal length
	 * @param d I    Filter order
	 * @param arch I    Run-time architecture
	 */
	static final void silk_LPC_analysis_filter(final short[] out, final int outoffset,// java
			final short[] in, final int inoffset,// java
			final short[] B, final int len, final int d)//, final int arch)
	{
/* #if defined(FIXED_POINT) && USE_CELT_FIR
		opus_int16 num[SILK_MAX_ORDER_LPC];
#else */
// #endif

		// celt_assert( d >= 6 );
		// celt_assert( (d & 1) == 0 );
		// celt_assert( d <= len );

/* #if defined(FIXED_POINT) && USE_CELT_FIR
		celt_assert( d <= SILK_MAX_ORDER_LPC );
		for ( j = 0; j < d; j++ ) {
			num[ j ] = -B[ j ];
		}
		celt_fir( in + d, num, out + d, len - d, d, arch );
		for ( j = 0; j < d; j++ ) {
			out[ j ] = 0;
		}
#else */
		// (void)arch;
		final int B0 = (int)B[ 0 ];
		final int B1 = (int)B[ 1 ];
		final int B2 = (int)B[ 2 ];
		final int B3 = (int)B[ 3 ];
		final int B4 = (int)B[ 4 ];
		final int B5 = (int)B[ 5 ];
		for( int ix = d, oi = outoffset + ix; ix < len; ix++ ) {
			final int in_ptr = inoffset + ix - 1;// in[ in_ptr ]

			int out32_Q12 = (int)in[ in_ptr ] * B0;
			/* Allowing wrap around so that two wraps can cancel each other. The rare
			cases where the result wraps around can only be triggered by invalid streams*/
			out32_Q12 += ((int)in[ in_ptr - 1 ] * B1);
			out32_Q12 += ((int)in[ in_ptr - 2 ] * B2);
			out32_Q12 += ((int)in[ in_ptr - 3 ] * B3);
			out32_Q12 += ((int)in[ in_ptr - 4 ] * B4);
			out32_Q12 += ((int)in[ in_ptr - 5 ] * B5);
			for( int j = 6, ji = in_ptr - 6; j < d; /* j += 2*/ ) {
				out32_Q12 += ((int)in[ ji--         ] * (int)B[ j++       ]);
				out32_Q12 += ((int)in[ ji--/* - 1*/ ] * (int)B[ j++/* + 1*/ ]);
			}

			/* Subtract prediction */
			out32_Q12 = ((((int)in[ in_ptr + 1 ]) << 12) - out32_Q12);

			/* Scale to Q0 */
			final int out32 = JSigProc_FIX.silk_RSHIFT_ROUND( out32_Q12, 12 );

			/* Saturate output */
			out[ oi++ ] = (short)(out32 > Short.MAX_VALUE ? Short.MAX_VALUE : (out32 < Short.MIN_VALUE ? Short.MIN_VALUE : out32));
		}

		/* Set first d output samples to zero */
		// silk_memset( out, 0, d * sizeof( opus_int16 ) );
		for( int i = outoffset, ie = outoffset + d; i < ie; i++ ) {
			out[i] = 0;
		}
// #endif
	}
	// end LPC_analysis_filter.c
}
