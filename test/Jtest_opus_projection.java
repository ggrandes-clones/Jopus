package test;

import celt.Jfloat_cast;
import opus.JMappingMatrix;
import opus.JOpusProjectionDecoder;
import opus.JOpusProjectionEncoder;
import opus.Jms_encoder_data_aux;
import opus.Jopus_defines;
import opus.Jopus_projection;

/* Copyright (c) 2017 Google Inc.
   Written by Andrew Allen */
/*
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

final class Jtest_opus_projection {
	private static final String CLASS_NAME = "Jtest_opus_projection";

	private static final int BUFFER_SIZE = 960;
	private static final int MAX_DATA_BYTES = 32768;
	private static final int MAX_FRAME_SAMPLES = 5760;
	private static final short ERROR_TOLERANCE = 1;

	// private static final int SIMPLE_MATRIX_SIZE = 12;
	private static final int SIMPLE_MATRIX_FRAME_SIZE = 10;
	private static final int SIMPLE_MATRIX_INPUT_SIZE = 30;
	private static final int SIMPLE_MATRIX_OUTPUT_SIZE = 40;

	private static final boolean assert_is_equal(final float[] a, final short[] b, final int size, final short tolerance)
	{
		int i;
		for( i = 0; i < size; i++)
		{
// #ifdef FIXED_POINT
//			opus_int16 val = a[i];
// #else
			//short val = FLOAT2INT16( a[i] );
			float x = a[i];
			x *= Jfloat_cast.CELT_SIG_SCALE;
			x = x >= -32768f ? x : -32768f;
			x = x <=  32767f ? x :  32767f;
			final short val = (short)Math.floor( .5 + (double)x );
// #endif
			if( Math.abs( val - b[i] ) > tolerance ) {
				return true;
			}
		}
		return false;
	}

	private static final boolean assert_is_equal_short(final short[] a, final short[] b, final int size, final short tolerance)
	{
		for( int i = 0; i < size; i++ ) {
			if( Math.abs( a[i] - b[i] ) > tolerance ) {
				return true;
			}
		}
		return false;
	}

	private static final void test_simple_matrix()
	{
		final String str_method_name = "test_simple_matrix";
		final JMappingMatrix simple_matrix_params = new JMappingMatrix(4, 3, 0);
		final short simple_matrix_data[/* SIMPLE_MATRIX_SIZE */] = {0, 32767, 0, 0, 32767, 0, 0, 0, 0, 0, 0, 32767};
		final short input_int16[/* SIMPLE_MATRIX_INPUT_SIZE */] = {
				32767, 0, -32768, 29491, -3277, -29491, 26214, -6554, -26214, 22938, -9830,
				-22938, 19661, -13107, -19661, 16384, -16384, -16384, 13107, -19661, -13107,
				9830, -22938, -9830, 6554, -26214, -6554, 3277, -29491, -3277};
		final short expected_output_int16[/* SIMPLE_MATRIX_OUTPUT_SIZE */] = {
				0, 32767, 0, -32768, -3277, 29491, 0, -29491, -6554, 26214, 0, -26214,
				-9830, 22938, 0, -22938, -13107, 19661, 0, -19661, -16384, 16384, 0, -16384,
				-19661, 13107, 0, -13107, -22938, 9830, 0, -9830, -26214, 6554, 0, -6554,
				-29491, 3277, 0, -3277};

		/* Allocate input/output buffers. */
		final float[] input_val16 = new float[ SIMPLE_MATRIX_INPUT_SIZE ];
		final short[] output_int16 = new short[ SIMPLE_MATRIX_OUTPUT_SIZE ];
		final float[] output_val16 = new float[ SIMPLE_MATRIX_OUTPUT_SIZE ];

		/* Initialize matrix */
		/* int simple_matrix_size = mapping_matrix_get_size( simple_matrix_params.rows, simple_matrix_params.cols );
		if( 0 == simple_matrix_size ) {
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		} */

		final JMappingMatrix simple_matrix = new JMappingMatrix();// (MappingMatrix *)opus_alloc(simple_matrix_size);
		simple_matrix.mapping_matrix_init( simple_matrix_params.rows,
				simple_matrix_params.cols, simple_matrix_params.gain, simple_matrix_data );//, simple_matrix_data.length );

		/* Copy inputs. */
		for( int i = 0; i < SIMPLE_MATRIX_INPUT_SIZE; i++ )
		{
// #ifdef FIXED_POINT
//			input_val16[i] = input_int16[i];
//#else
			input_val16[i] = (1f / 32768.f) * input_int16[i];
//#endif
		}

		/* _in_short */
		for( int i = 0; i < SIMPLE_MATRIX_OUTPUT_SIZE; i++ ) {
			output_val16[i] = 0;
		}
		for( int i = 0; i < simple_matrix.rows; i++ )
		{
			simple_matrix.mapping_matrix_multiply_channel_in_short(
					input_int16, 0, simple_matrix.cols, output_val16, i, i,
					simple_matrix.rows, SIMPLE_MATRIX_FRAME_SIZE );
		}
		boolean ret = assert_is_equal( output_val16, expected_output_int16, SIMPLE_MATRIX_OUTPUT_SIZE, ERROR_TOLERANCE );
		if( ret ) {
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		/* _out_short */
		for( int i = 0; i < SIMPLE_MATRIX_OUTPUT_SIZE; i++ ) {
			output_int16[i] = 0;
		}
		for( int i = 0; i < simple_matrix.cols; i++ )
		{
			simple_matrix.mapping_matrix_multiply_channel_out_short(
					input_val16, i, i, simple_matrix.cols, output_int16, 0,
					simple_matrix.rows, SIMPLE_MATRIX_FRAME_SIZE );
		}
		ret = assert_is_equal_short( output_int16, expected_output_int16, SIMPLE_MATRIX_OUTPUT_SIZE, ERROR_TOLERANCE );
		if( ret ) {
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

// #if !defined(DISABLE_FLOAT_API) && !defined(FIXED_POINT)
		/* _in_float */
		for( int i = 0; i < SIMPLE_MATRIX_OUTPUT_SIZE; i++ ) {
			output_val16[i] = 0;
		}
		for( int i = 0; i < simple_matrix.rows; i++ )
		{
			simple_matrix.mapping_matrix_multiply_channel_in_float(
					input_val16, 0, simple_matrix.cols, output_val16, i, i,
					simple_matrix.rows, SIMPLE_MATRIX_FRAME_SIZE);
		}
		ret = assert_is_equal( output_val16, expected_output_int16, SIMPLE_MATRIX_OUTPUT_SIZE, ERROR_TOLERANCE );
		if( ret ) {
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		/* _out_float */
		for( int i = 0; i < SIMPLE_MATRIX_OUTPUT_SIZE; i++ ) {
			output_val16[i] = 0;
		}
		for( int i = 0; i < simple_matrix.cols; i++ )
		{
			simple_matrix.mapping_matrix_multiply_channel_out_float(
					input_val16, i, i, simple_matrix.cols, output_val16, 0,
					simple_matrix.rows, SIMPLE_MATRIX_FRAME_SIZE );
		}
		ret = assert_is_equal( output_val16, expected_output_int16, SIMPLE_MATRIX_OUTPUT_SIZE, ERROR_TOLERANCE );
		if( ret ) {
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}
// #endif

		// opus_free(input_val16);
		// opus_free(output_int16);
		// opus_free(output_val16);
		// opus_free(simple_matrix);
	}

	@SuppressWarnings("boxing")
	private static final void test_creation_arguments(final int channels, final int mapping_family)
	{
		final String str_method_name = "test_creation_arguments";
		final int streams[] = new int[1];
		final int coupled_streams[] = new int[1];
		final int dec_error[] = new int[] {Jopus_defines.OPUS_INTERNAL_ERROR};// java FIXME dec_error is not initiated
		final int enc_error[] = new int[1];// java

		final int Fs = 48000;
		final int application = Jopus_defines.OPUS_APPLICATION_AUDIO;

		final int order_plus_one = (int)Math.floor( Math.sqrt( (float)channels ) );
		final int nondiegetic_channels = channels - order_plus_one * order_plus_one;

		final Jms_encoder_data_aux data = new Jms_encoder_data_aux();// java aux

		JOpusProjectionEncoder st_enc = JOpusProjectionEncoder.opus_projection_ambisonics_encoder_create( Fs, channels, mapping_family,
				data,// java streams, coupled_streams,
				application, enc_error );
		if( st_enc != null )
		{
			final Object[] request = new Object[1];// java helper

			int ret = st_enc.opus_projection_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST, request );
			final int matrix_size = ((Integer)request[ 0 ]).intValue();// java
			if( ret != Jopus_defines.OPUS_OK || 0 == matrix_size ) {
				Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
			}

			char[] matrix = new char[ matrix_size ];
			// request[ 0 ] = matrix;
			ret = st_enc.opus_projection_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST, matrix, 0, matrix_size );

			st_enc = null;// opus_projection_encoder_destroy( st_enc );

			JOpusProjectionDecoder st_dec = JOpusProjectionDecoder.opus_projection_decoder_create( Fs, channels,
					data.streams, data.coupled_streams, matrix, dec_error );// , matrix_size, &dec_error );
			if( st_dec != null )
			{
				st_dec = null;// opus_projection_decoder_destroy( st_dec );
			}
			matrix = null;
		}

		final boolean is_channels_valid = (order_plus_one >= 2 && order_plus_one <= 4) &&
				(nondiegetic_channels == 0 || nondiegetic_channels == 2);
		final boolean is_projection_valid = (enc_error[0] == Jopus_defines.OPUS_OK && dec_error[0] == Jopus_defines.OPUS_OK);
		if( is_channels_valid ^ is_projection_valid )
		{
			System.err.printf("Channels: %d, Family: %d\n", channels, mapping_family );
			System.err.printf("Order+1: %d, Non-diegetic Channels: %d\n",
					order_plus_one, nondiegetic_channels );
			System.err.printf("Streams: %d, Coupled Streams: %d\n",
					streams, coupled_streams );
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}
	}

	private static final void generate_music(final short[] buf, final int len, final int channels)
	{
		final int[] a = new int[ channels ];
		final int[] b = new int[ channels ];
		final int[] c = new int[ channels ];
		final int[] d = new int[ channels ];
		// memset(a, 0, sizeof(opus_int32) * channels);
		// memset(b, 0, sizeof(opus_int32) * channels);
		// memset(c, 0, sizeof(opus_int32) * channels);
		// memset(d, 0, sizeof(opus_int32) * channels);
		int j = 0;

		for( int i = 0; i < len; i++ )
		{
			for( int k = 0; k < channels; k++ )
			{
				int v = (((j * ((j >> 12) ^ ((j >> 10 | j >> 12) & 26 & j >> 7))) & 128) + 128) << 15;
				final int r = Jtest_opus_common.fast_rand(); v += r & 65535; v -= r >>> 16;
				b[k] = v - a[k] + ((b[k] * 61 + 32) >> 6); a[k] = v;
				c[k] = (30 * (c[k] + b[k] + d[k]) + 32) >> 6; d[k] = b[k];
				v = (c[k] + 128) >> 8;
				buf[i * channels + k] = (short)(v > 32767 ? 32767 : (v < -32768 ? -32768 : v));
				if( i % 6 == 0 ) {
					j++;
				}
			}
		}

		//free(a);
		//free(b);
		//free(c);
		//free(d);
	}

	@SuppressWarnings("boxing")
	private static final void test_encode_decode(final int bitrate, final int channels, final int mapping_family)
	{
		final String str_method_name = "test_encode_decode";
		final int Fs = 48000;
		final int application = Jopus_defines.OPUS_APPLICATION_AUDIO;

		// final int streams;
		// final int coupled;
		final Jms_encoder_data_aux stream_data = new Jms_encoder_data_aux();// java aux
		final int error[] = new int[1];// java

		short buffer_in[] = new short[ BUFFER_SIZE * channels ];
		short buffer_out[] = new short[ BUFFER_SIZE * channels ];

		final JOpusProjectionEncoder st_enc = JOpusProjectionEncoder.opus_projection_ambisonics_encoder_create( Fs, channels,
				mapping_family,
				stream_data,// java &streams, &coupled,
				application, error );
		if( error[0] != Jopus_defines.OPUS_OK ) {
			System.err.printf("Couldn\'t create encoder with %d channels and mapping family %d.\n",
					channels, mapping_family );
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		error[0] = st_enc.opus_projection_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate * 1000 * (stream_data.streams + stream_data.coupled_streams) );
		if( error[0] != Jopus_defines.OPUS_OK )
		{
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		final Object[] request = new Object[1];// java helper
		error[0] = st_enc.opus_projection_encoder_ctl(
				Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST, request );
		final int matrix_size = ((Integer)request[0]).intValue();
		if( error[0] != Jopus_defines.OPUS_OK || 0 == matrix_size )
		{
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		char[] matrix = new char[ matrix_size ];
		// request[ 0 ] = matrix;// java
		error[0] = st_enc.opus_projection_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST, matrix, 0, matrix_size );

		final JOpusProjectionDecoder st_dec = JOpusProjectionDecoder.opus_projection_decoder_create( Fs, channels, stream_data.streams, stream_data.coupled_streams,
				matrix,/* matrix_size,*/ error );
		matrix = null;

		if( error[0] != Jopus_defines.OPUS_OK ) {
			System.err.printf("Couldn\'t create decoder with %d channels, %d streams and %d coupled streams.\n", channels, stream_data.streams, stream_data.coupled_streams );
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		generate_music( buffer_in, BUFFER_SIZE, channels );

		final byte data[] = new byte[MAX_DATA_BYTES];
		final int len = st_enc.opus_projection_encode( buffer_in, BUFFER_SIZE, data, MAX_DATA_BYTES );
		if( len < 0 || len > MAX_DATA_BYTES ) {
			System.err.printf("opus_encode() returned %d\n", len);
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		final int out_samples = st_dec.opus_projection_decode( data, len, buffer_out, MAX_FRAME_SAMPLES, false );
		if( out_samples != BUFFER_SIZE ) {
			System.err.printf("opus_decode() returned %d\n", out_samples);
			buffer_in = null;
			buffer_out = null;
			Jtest_opus_common.test_failed( CLASS_NAME, str_method_name );
		}

		buffer_in = null;
		buffer_out = null;
		return;
	}

	public static final void main(final String[] _argv)
	{
		/* Test simple matrix multiplication routines. */
		test_simple_matrix();

		/* Test full range of channels in creation arguments. */
		for( int i = 0; i < 255; i++ ) {
			test_creation_arguments( i, 3 );
		}

		/* Test encode/decode pipeline. */
		test_encode_decode( 64 * 18, 18, 3 );

		System.err.printf("All projection tests passed.\n");
		return;
	}

}