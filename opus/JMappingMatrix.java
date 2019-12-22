package opus;

import celt.Jfloat_cast;

public final class JMappingMatrix {
	/** number of channels outputted from matrix. */
	public int rows;
	/** number of channels inputted to matrix. */
	public int cols;
	/** in dB. S7.8-format. */
	public int gain;
    /* Matrix cell data goes here using col-wise ordering. */
	short[] mMatrix;

	// TODO java: get mapping_matrix_init to return JMappingMatrix and make the fields final
	public JMappingMatrix() {
		rows = 0;
		cols = 0;
		gain = 0;
		mMatrix = null;
	}
	public JMappingMatrix(final int r, final int c, final int g) {
		rows = r;
		cols = c;
		gain = g;
		mMatrix = null;
	}

	// java extracted inplace #define MATRIX_INDEX(nb_rows, row, col) (nb_rows * col + row)
// java: don't need
//	private static int mapping_matrix_get_size(int rows, int cols)
//	{
//		/* Mapping Matrix must only support up to 255 channels in or out.
//		 * Additionally, the total cell count must be <= 65004 octets in order
//		 * for the matrix to be stored in an OGG header.
//		 */
//		if( rows > 255 || cols > 255 )
//			return 0;
//		final int size = rows * cols * (Short.SIZE / 8);
//		if( size > 65004 )
//			return 0;
//
//		return align(sizeof(MappingMatrix)) + align(size);
//	}

	// private static short[] mapping_matrix_get_data(final JMappingMatrix matrix)
	//{
		/* void* cast avoids clang -Wcast-align warning */
	//	return (opus_int16*)(void*)((char*)matrix + align(sizeof(MappingMatrix)));
	//}

	public final void mapping_matrix_init(// final JMappingMatrix matrix,
			final int rows, final int cols, final int gain, final short[] data)// data.length, final int data_size)
	{
// #if !defined(ENABLE_ASSERTIONS)
//		(void)data_size;
// #endif
		// celt_assert(align(data_size) == align(rows * cols * sizeof(opus_int16)));

		this.rows = rows;
		this.cols = cols;
		this.gain = gain;
		final int size = rows * cols;
		this.mMatrix = new short[ size ];
		//final short[] ptr = this.mMatrix;// java mapping_matrix_get_data( matrix );
		//for( int i = 0; i < size; i++ )
		//{
		//	ptr[i] = data[i];
		//}
		System.arraycopy( data, 0, this.mMatrix, 0, size );
	}

// #ifndef DISABLE_FLOAT_API
	public final void mapping_matrix_multiply_channel_in_float(
			// final JMappingMatrix matrix,
			final float[] input, int inoffset,// java
			final int input_rows,// FIXME is this correct? cols are used while invoking
			final float[] output, int outoffset,// java
			final int output_row,
			final int output_rows,
			final int frame_size)
	{
		/* Matrix data is ordered col-wise. */
		// celt_assert(input_rows <= matrix.cols && output_rows <= matrix.rows);

		final short[] matrix_data = this.mMatrix;// java mapping_matrix_get_data( matrix );
		final int oie = outoffset + frame_size * output_rows;// java
		final int c_rows = this.rows;// java
		final int rce = output_row + input_rows * c_rows;// java

		for( ; outoffset < oie; outoffset += output_rows, inoffset += input_rows )
		{
			float tmp = 0;
			for( int col = inoffset, rc = output_row; rc < rce; col++, rc += c_rows )
			{
				// tmp += matrix_data[MATRIX_INDEX(matrix.rows, output_row, col)] *
				//		input[MATRIX_INDEX(input_rows, col, i)];
				tmp += matrix_data[ rc ] * input[ col ];
			}
// #if defined(FIXED_POINT)
//			output[output_rows * i] = FLOAT2INT16((1/32768.f)*tmp);
// #else
			output[ outoffset ] = (1f / 32768.f) * tmp;
// #endif
		}
	}

	public final void mapping_matrix_multiply_channel_out_float(
			// final JMappingMatrix matrix,
			final float[] input, int inoffset,// java
			final int input_row,
			final int input_rows,
			final float[] output, final int outoffset,// java
			final int output_rows,
			final int frame_size
	)
	{
		/* Matrix data is ordered col-wise. */

		// celt_assert(input_rows <= matrix.cols && output_rows <= matrix.rows);

		final short[] matrix_data = this.mMatrix;// java mapping_matrix_get_data( matrix );
		final int oie = outoffset + frame_size * output_rows;// java
		final int ri = this.rows * input_row;// java
		final int re = ri + output_rows;// java

		for( int oi = outoffset; oi < oie; oi += output_rows, inoffset += input_rows )
		{
// #if defined(FIXED_POINT)
//			input_sample = (1/32768.f)*input[input_rows * i];
// #else
			final float input_sample = input[ inoffset ];
// #endif
			for( int row = ri, or = oi; row < re; row++, or++ )
			{
				// final float tmp = (1f / 32768.f) * matrix_data[MATRIX_INDEX(matrix.rows, row, input_row)] * input_sample;
				// output[MATRIX_INDEX(output_rows, row, i)] += tmp;
				final float tmp = (1f / 32768.f) * matrix_data[ row ] * input_sample;
				output[ or ] += tmp;
			}
		}
	}
// #endif /* DISABLE_FLOAT_API */

	public final void mapping_matrix_multiply_channel_in_short(
			// final JMappingMatrix matrix,
			final short[] input, int inoffset,// java added
			final int input_rows,// FIXME is this correct? cols are used while invoking
			final float[] output, int outoffset,// java added
			final int output_row,
			final int output_rows,
			final int frame_size)
	{
		/* Matrix data is ordered col-wise. */

		// celt_assert(input_rows <= matrix.cols && output_rows <= matrix.rows);

		final short[] matrix_data = this.mMatrix;// java mapping_matrix_get_data(matrix);
		final int oie = outoffset + frame_size * output_rows;// java
		final int c_rows = this.rows;// java
		final int rce = output_row + input_rows * c_rows;// java

		for( ; outoffset < oie; outoffset += output_rows, inoffset += input_rows )
		{
			float tmp = 0;
			for( int col = inoffset, rc = output_row; rc < rce; col++, rc += c_rows )
			{
// #if defined(FIXED_POINT)
//				tmp += ((opus_int32)matrix_data[MATRIX_INDEX(matrix.rows, output_row, col)] *
//						(opus_int32)input[MATRIX_INDEX(input_rows, col, i)]) >> 8;
// #else
				// tmp += matrix_data[MATRIX_INDEX(matrix.rows, output_row, col)] *
				//			input[MATRIX_INDEX(input_rows, col, i)];
				tmp += matrix_data[ rc ] * input[ col ];
// #endif
			}
// #if defined(FIXED_POINT)
//			output[output_rows * i] = (opus_int16)((tmp + 64) >> 7);
// #else
			output[ outoffset ] = (1f / (32768.f * 32768.f)) * tmp;
// #endif
		}
	}

	public final void mapping_matrix_multiply_channel_out_short(
			// final JMappingMatrix matrix,
			final float[] input, int inoffset,// java added
			final int input_row,
			final int input_rows,
			final short[] output, int outoffset,// java
			final int output_rows,
			final int frame_size)
	{
		/* Matrix data is ordered col-wise. */

		// celt_assert(input_rows <= matrix.cols && output_rows <= matrix.rows);

		final short[] matrix_data = this.mMatrix;// java mapping_matrix_get_data(matrix);
		final int oie = outoffset + frame_size * output_rows;// java
		final int ri = this.rows * input_row;// java
		final int re = ri + output_rows;// java

		for( ; outoffset < oie; outoffset += output_rows, inoffset += input_rows )
		{
// #if defined(FIXED_POINT)
//			int input_sample = (opus_int32)input[input_rows * i];
// #else
			// int input_sample = (int)FLOAT2INT16(input[input_rows * i]);
			float x = input[ inoffset ];
			x *= Jfloat_cast.CELT_SIG_SCALE;
			x = x >= -32768 ? x : -32768;
			x = x <=  32767 ? x :  32767;
			final int input_sample = (int)Math.floor( (double)(.5f + x) );
// #endif
			for( int row = ri, or = outoffset; row < re; row++, or++ )
			{
				// final int tmp = (int)matrix_data[MATRIX_INDEX(matrix.rows, row, input_row)] * input_sample;
				// output[MATRIX_INDEX(output_rows, row, i)] += (tmp + 16384) >> 15;
				final int tmp = (int)matrix_data[ row ] * input_sample;
				output[ or ] += (tmp + 16384) >> 15;
			}
		}
	}

	/* Pre-computed mixing and demixing matrices for 1st to 3rd-order ambisonics.
	 *   foa: first-order ambisonics
	 *   soa: second-order ambisonics
	 *   toa: third-order ambisonics
	 */

	static final JMappingMatrix mapping_matrix_foa_mixing = new JMappingMatrix( 6, 6, 0 );
	static final short mapping_matrix_foa_mixing_data[] = {// [36] = {
	     16384,      0, -16384,  23170,      0,      0,  16384,  23170,
	     16384,      0,      0,      0,  16384,      0, -16384, -23170,
	         0,      0,  16384, -23170,  16384,      0,      0,      0,
	         0,      0,      0,      0,  32767,      0,      0,      0,
	         0,      0,      0,  32767
	};

	static final JMappingMatrix mapping_matrix_soa_mixing = new JMappingMatrix( 11, 11, 0 );
	static final short mapping_matrix_soa_mixing_data[] = {// [121] = {
	     10923,   7723,  13377, -13377,  11585,   9459,   7723, -16384,
	     -6689,      0,      0,  10923,   7723,  13377,  13377, -11585,
	      9459,   7723,  16384,  -6689,      0,      0,  10923, -15447,
	     13377,      0,      0, -18919,   7723,      0,  13377,      0,
	         0,  10923,   7723, -13377, -13377,  11585,  -9459,   7723,
	     16384,  -6689,      0,      0,  10923,  -7723,      0,  13377,
	    -16384,      0, -15447,      0,   9459,      0,      0,  10923,
	     -7723,      0, -13377,  16384,      0, -15447,      0,   9459,
	         0,      0,  10923,  15447,      0,      0,      0,      0,
	    -15447,      0, -18919,      0,      0,  10923,   7723, -13377,
	     13377, -11585,  -9459,   7723, -16384,  -6689,      0,      0,
	     10923, -15447, -13377,      0,      0,  18919,   7723,      0,
	     13377,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,  32767,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	     32767
	};

	static final JMappingMatrix mapping_matrix_toa_mixing = new JMappingMatrix( 18, 18, 0 );
	static final short mapping_matrix_toa_mixing_data[] = {// [324] = {
	      8208,      0,   -881,  14369,      0,      0,  -8192,  -4163,
	     13218,      0,      0,      0,  11095,  -8836,  -6218,  14833,
	         0,      0,   8208, -10161,    881,  10161, -13218,  -2944,
	     -8192,   2944,      0, -10488,  -6218,   6248, -11095,  -6248,
	         0, -10488,      0,      0,   8208,  10161,    881, -10161,
	    -13218,   2944,  -8192,  -2944,      0,  10488,  -6218,  -6248,
	    -11095,   6248,      0,  10488,      0,      0,   8176,   5566,
	    -11552,   5566,   9681, -11205,   8192, -11205,      0,   4920,
	    -15158,   9756,  -3334,   9756,      0,  -4920,      0,      0,
	      8176,   7871,  11552,      0,      0,  15846,   8192,      0,
	     -9681,  -6958,      0,  13797,   3334,      0, -15158,      0,
	         0,      0,   8176,      0,  11552,   7871,      0,      0,
	      8192,  15846,   9681,      0,      0,      0,   3334,  13797,
	     15158,   6958,      0,      0,   8176,   5566, -11552,  -5566,
	     -9681, -11205,   8192,  11205,      0,   4920,  15158,   9756,
	     -3334,  -9756,      0,   4920,      0,      0,   8208,  14369,
	      -881,      0,      0,  -4163,  -8192,      0, -13218, -14833,
	         0,  -8836,  11095,      0,   6218,      0,      0,      0,
	      8208,  10161,    881,  10161,  13218,   2944,  -8192,   2944,
	         0,  10488,   6218,  -6248, -11095,  -6248,      0, -10488,
	         0,      0,   8208, -14369,   -881,      0,      0,   4163,
	     -8192,      0, -13218,  14833,      0,   8836,  11095,      0,
	      6218,      0,      0,      0,   8208,      0,   -881, -14369,
	         0,      0,  -8192,   4163,  13218,      0,      0,      0,
	     11095,   8836,  -6218, -14833,      0,      0,   8176,  -5566,
	    -11552,   5566,  -9681,  11205,   8192, -11205,      0,  -4920,
	     15158,  -9756,  -3334,   9756,      0,  -4920,      0,      0,
	      8176,      0,  11552,  -7871,      0,      0,   8192, -15846,
	      9681,      0,      0,      0,   3334, -13797,  15158,  -6958,
	         0,      0,   8176,  -7871,  11552,      0,      0, -15846,
	      8192,      0,  -9681,   6958,      0, -13797,   3334,      0,
	    -15158,      0,      0,      0,   8176,  -5566, -11552,  -5566,
	      9681,  11205,   8192,  11205,      0,  -4920, -15158,  -9756,
	     -3334,  -9756,      0,   4920,      0,      0,   8208, -10161,
	       881, -10161,  13218,  -2944,  -8192,  -2944,      0, -10488,
	      6218,   6248, -11095,   6248,      0,  10488,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	     32767,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,  32767
	};

	static final JMappingMatrix mapping_matrix_foa_demixing = new JMappingMatrix( 6, 6, 0 );
	static short mapping_matrix_foa_demixing_data[] = {// [36] = {
	     16384,  16384,  16384,  16384,      0,      0,      0,  23170,
	         0, -23170,      0,      0, -16384,  16384, -16384,  16384,
	         0,      0,  23170,      0, -23170,      0,      0,      0,
	         0,      0,      0,      0,  32767,      0,      0,      0,
	         0,      0,      0,  32767
	};

	static final JMappingMatrix mapping_matrix_soa_demixing = new JMappingMatrix( 11, 11, 3050 );
	static final short mapping_matrix_soa_demixing_data[] = {// [121] = {
	      2771,   2771,   2771,   2771,   2771,   2771,   2771,   2771,
	      2771,      0,      0,  10033,  10033, -20066,  10033,  14189,
	     14189, -28378,  10033, -20066,      0,      0,   3393,   3393,
	      3393,  -3393,      0,      0,      0,  -3393,  -3393,      0,
	         0, -17378,  17378,      0, -17378, -24576,  24576,      0,
	     17378,      0,      0,      0, -14189,  14189,      0, -14189,
	    -28378,  28378,      0,  14189,      0,      0,      0,   2399,
	      2399,  -4799,  -2399,      0,      0,      0,  -2399,   4799,
	         0,      0,   1959,   1959,   1959,   1959,  -3918,  -3918,
	     -3918,   1959,   1959,      0,      0,  -4156,   4156,      0,
	      4156,      0,      0,      0,  -4156,      0,      0,      0,
	      8192,   8192, -16384,   8192,  16384,  16384, -32768,   8192,
	    -16384,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,   8312,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	      8312
	};

	static final JMappingMatrix mapping_matrix_toa_demixing = new JMappingMatrix( 18, 18, 0 );
	static final short mapping_matrix_toa_demixing_data[] = {// [324] = {
	      8192,   8192,   8192,   8192,   8192,   8192,   8192,   8192,
	      8192,   8192,   8192,   8192,   8192,   8192,   8192,   8192,
	         0,      0,      0,  -9779,   9779,   6263,   8857,      0,
	      6263,  13829,   9779, -13829,      0,  -6263,      0,  -8857,
	     -6263,  -9779,      0,      0,  -3413,   3413,   3413, -11359,
	     11359,  11359, -11359,  -3413,   3413,  -3413,  -3413, -11359,
	     11359,  11359, -11359,   3413,      0,      0,  13829,   9779,
	     -9779,   6263,      0,   8857,  -6263,      0,   9779,      0,
	    -13829,   6263,  -8857,      0,  -6263,  -9779,      0,      0,
	         0, -15617, -15617,   6406,      0,      0,  -6406,      0,
	     15617,      0,      0,  -6406,      0,      0,   6406,  15617,
	         0,      0,      0,  -5003,   5003, -10664,  15081,      0,
	    -10664,  -7075,   5003,   7075,      0,  10664,      0, -15081,
	     10664,  -5003,      0,      0,  -8176,  -8176,  -8176,   8208,
	      8208,   8208,   8208,  -8176,  -8176,  -8176,  -8176,   8208,
	      8208,   8208,   8208,  -8176,      0,      0,  -7075,   5003,
	     -5003, -10664,      0,  15081,  10664,      0,   5003,      0,
	      7075, -10664, -15081,      0,  10664,  -5003,      0,      0,
	     15617,      0,      0,      0,  -6406,   6406,      0, -15617,
	         0, -15617,  15617,      0,   6406,  -6406,      0,      0,
	         0,      0,      0, -11393,  11393,   2993,  -4233,      0,
	      2993, -16112,  11393,  16112,      0,  -2993,      0,   4233,
	     -2993, -11393,      0,      0,      0,  -9974,  -9974, -13617,
	         0,      0,  13617,      0,   9974,      0,      0,  13617,
	         0,      0, -13617,   9974,      0,      0,      0,   5579,
	     -5579,  10185,  14403,      0,  10185,  -7890,  -5579,   7890,
	         0, -10185,      0, -14403, -10185,   5579,      0,      0,
	     11826, -11826, -11826,   -901,    901,    901,   -901,  11826,
	    -11826,  11826,  11826,   -901,    901,    901,   -901, -11826,
	         0,      0,  -7890,  -5579,   5579,  10185,      0,  14403,
	    -10185,      0,  -5579,      0,   7890,  10185, -14403,      0,
	    -10185,   5579,      0,      0,  -9974,      0,      0,      0,
	    -13617,  13617,      0,   9974,      0,   9974,  -9974,      0,
	     13617, -13617,      0,      0,      0,      0,  16112, -11393,
	     11393,  -2993,      0,   4233,   2993,      0, -11393,      0,
	    -16112,  -2993,  -4233,      0,   2993,  11393,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	     32767,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,      0,      0,      0,      0,      0,
	         0,      0,      0,  32767
	};
}
