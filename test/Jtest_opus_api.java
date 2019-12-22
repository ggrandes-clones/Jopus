package test;

import celt.Jcelt;
import opus.JOpusDecoder;
import opus.JOpusEncoder;
import opus.JOpusMSDecoder;
import opus.JOpusRepacketizer;
import opus.Jopus;
import opus.Jopus_defines;
import opus.Jopus_packet_data_aux;

/* Copyright ( c ) 2011-2013 Xiph.Org Foundation
   Written by Gregory Maxwell */
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
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES ( INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR
   PROFITS;  OR BUSINESS INTERRUPTION ) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT ( INCLUDING
   NEGLIGENCE OR OTHERWISE ) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* This tests the API presented by the libopus system.
   It does not attempt to extensively exercise the codec internals.
   The strategy here is to simply the API interface invariants:
   That sane options are accepted, insane options are rejected,
   and that nothing blows up. In particular we don't actually test
   that settings are heeded by the codec ( though we do check that
   get after set returns a sane value when it should ). Other
   tests check the actual codec behavior.
   In cases where its reasonable to do so we test exhaustively,
   but its not reasonable to do so in all cases.
   Although these tests are simple they found several library bugs
   when they were initially developed. */

/* These tests are more sensitive if compiled with -DVALGRIND and
   run inside valgrind. Malloc failure testing requires glibc. */

// test_opus_api.c

final class Jtest_opus_api {
	private static final String CLASS_NAME = "Jtest_opus_api";

	private static final int opus_rates[/* 5 */] = { 48000, 24000, 16000, 12000, 8000 };

	private static final int test_dec_api()
	{
		final String method_name = "test_dec_api";
		JOpusDecoder dec;
		int i, j;
		final byte packet[] = new byte[1276];
// #ifndef DISABLE_FLOAT_API
		final float fbuf[] = new float[960 * 2];
// #endif
		final short sbuf[] = new short[960 * 2];
		final int[] err = new int[1];
		final Object[] request = new Object[1];// java helper
		final Object[] nullvalue = null;// java change: int[] to Object[]

		int cfgs = 0;
		/*First test invalid configurations which should fail*/
		System.out.printf( "\n  Decoder basic API tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );
		for( int c = 0; c < 4; c++ )// java: opus_decoder_get_size not implemented
		{
			/*
			i = opus_decoder_get_size( c );
			if( ( (c == 1 || c == 2) && (i <= 2048 || i > 1 << 16) ) || ( (c != 1 && c != 2) && i != 0 ) ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			System.out.printf( "    opus_decoder_get_size( %d ) = %d ...............%s OK.\n", c, i, i > 0 ? "": "...." );
			*/
			cfgs++;
		}

		/*Test with unsupported sample rates*/
		for( int c = 0; c < 4; c++ )
		{
			for( i = -7; i <= 96000; i++ )
			{
				int fs;
				if( (i == 8000 || i == 12000 || i == 16000 || i == 24000 || i == 48000) && (c == 1 || c == 2) ) {
					continue;
				}
				switch( i )
				{
				case( -5 ): fs = -8000; break;
				case( -6 ): fs = Integer.MAX_VALUE; break;
				case( -7 ): fs = Integer.MIN_VALUE; break;
				default: fs = i;
				}
				err[0] = Jopus_defines.OPUS_OK;
				// VG_UNDEF( &err,sizeof( err ) );
				dec = JOpusDecoder.opus_decoder_create( fs, c, err );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG || dec != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				dec = JOpusDecoder.opus_decoder_create( fs, c, null );
				if( dec != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				dec = new JOpusDecoder( 2 );// ( opus_decoder_get_size( 2 ) );
				/* if( dec == null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}*/
				err[0] = dec.opus_decoder_init( fs, c );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				dec = null;
			}
		}

		// VG_UNDEF( &err,sizeof( err ) );
		dec = JOpusDecoder.opus_decoder_create( 48000, 2, err );
		if( err[0] != Jopus_defines.OPUS_OK || dec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		// VG_CHECK( dec,opus_decoder_get_size( 2 ) );
		cfgs++;

		System.out.printf( "    opus_decoder_create() ........................ OK.\n" );
		System.out.printf( "    opus_decoder_init() .......................... OK.\n" );

		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_UNDEF( &dec_final_range,sizeof( dec_final_range ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
		((Long)request[0]).longValue();
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		//VG_CHECK( &dec_final_range,sizeof( dec_final_range ) );
		System.out.printf( "    OPUS_GET_FINAL_RANGE ......................... OK.\n" );
		cfgs++;

		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_UNIMPLEMENTED );
		if( err[0] != Jopus_defines.OPUS_UNIMPLEMENTED ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_UNIMPLEMENTED ........................... OK.\n" );
		cfgs++;

		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_BANDWIDTH, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_BANDWIDTH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] !=  Jopus_defines.OPUS_OK || i != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_GET_BANDWIDTH ........................... OK.\n" );
		cfgs++;

		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i != 48000 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_GET_SAMPLE_RATE ......................... OK.\n" );
		cfgs++;

		/*GET_PITCH has different execution paths depending on the previously decoded frame.*/
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_PITCH, nullvalue );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_PITCH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i > 0 || i < -1 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( packet,sizeof( packet ) );
		packet[0] = (byte)(63 << 2); packet[1] = packet[2] = 0;
		if( dec.opus_decode( packet, 3, sbuf, 0, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_PITCH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i > 0 || i < -1 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] = 1;
		if( dec.opus_decode( packet, 1, sbuf, 0, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_PITCH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i > 0 || i < -1 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_PITCH ............................... OK.\n" );

		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_LAST_PACKET_DURATION ................ OK.\n" );

		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_GAIN, request );
		i = ((Integer)request[0]).intValue();// java
		// VG_CHECK( &i,sizeof( i ) );
		if( err[0] != Jopus_defines.OPUS_OK || i != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_GAIN, nullvalue );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_SET_GAIN, -32769 );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_SET_GAIN, 32768 );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_SET_GAIN, -15 );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_GAIN, request );
		i = ((Integer)request[0]).intValue();// java
		// VG_CHECK( &i,sizeof( i ) );
		if( err[0] != Jopus_defines.OPUS_OK || i != -15 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_SET_GAIN ................................ OK.\n" );
		System.out.printf( "    OPUS_GET_GAIN ................................ OK.\n" );

		/*Reset the decoder*/// TODO java: copy test not realized
		// dec2 = malloc( opus_decoder_get_size( 2 ) );
		// memcpy( dec2, dec, opus_decoder_get_size( 2 ) );
		if( dec.opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// if( memcmp( dec2, dec, opus_decoder_get_size( 2 ) ) == 0 ) test_failed();
		// free( dec2 );
		System.out.printf( "    OPUS_RESET_STATE ............................. OK.\n" );
		cfgs++;

		// VG_UNDEF( packet,sizeof( packet ) );
		packet[0] = 0;
		if( dec.opus_decoder_get_nb_samples( packet, 1 ) != 480 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 1, 48000 ) != 480 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 1, 96000 ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 1, 32000 ) != 320 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 1, 8000 ) != 80 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		packet[0] = 3;
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 1, 24000 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		packet[0] = (byte)((63 << 2) | 3);
		packet[1] = 63;
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 0, 24000 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( JOpusDecoder.opus_packet_get_nb_samples( packet, 0, 2, 48000 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( dec.opus_decoder_get_nb_samples( packet, 2 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    opus_{packet,decoder}_get_nb_samples() ....... OK.\n" );
		cfgs += 9;

		if( Jopus_defines.OPUS_BAD_ARG != JOpusDecoder.opus_packet_get_nb_frames( packet, 0, 0 ) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		for( i = 0; i < 256; i++ ) {
			final int l1res[/* 4 */] = { 1, 2, 2, Jopus_defines.OPUS_INVALID_PACKET };
			packet[0] = (byte)i;
			if( l1res[ packet[0] & 3 ] != JOpusDecoder.opus_packet_get_nb_frames( packet, 0, 1 ) ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
			for( j = 0; j < 256; j++ ) {
				packet[1] = (byte)j;
				if( ( (packet[0] & 3) != 3 ? l1res[ packet[0] & 3 ] : packet[1] & 63) != JOpusDecoder.opus_packet_get_nb_frames( packet, 0, 2 ) ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
			}
		}
		System.out.printf( "    opus_packet_get_nb_frames() .................. OK.\n" );

		for( i = 0; i < 256; i++ ) {
			packet[0] = (byte)i;
			int bw = ((int)packet[0] & 0xff) >>> 4;
			bw = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND + ( ( (((bw & 7) * 9) & (63 - (bw & 8))) + 2 + 12 * ((bw & 8) != 0 ? 1 : 0) ) >> 4 );
			if( bw != JOpusDecoder.opus_packet_get_bandwidth( packet, 0 ) ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
		}
		System.out.printf( "    opus_packet_get_bandwidth() .................. OK.\n" );

		for( i = 0; i < 256; i++ ) {
			packet[0] = (byte)i;
			int fp3s = ((int)packet[0] & 0xff) >> 3;
			fp3s = ((((3 - (fp3s & 3)) * 13 & 119) + 9 ) >> 2) * ((fp3s > 13 ? 1 : 0) * (3 - ((fp3s & 3) == 3 ? 1 : 0)) + 1) * 25;
			for( int rate = 0; rate < 5; rate++ ) {
				if( ( opus_rates[rate] * 3 / fp3s ) != Jopus.opus_packet_get_samples_per_frame( packet, 0, opus_rates[rate] ) ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
			}
		}
		System.out.printf( "    opus_packet_get_samples_per_frame() .......... OK.\n" );

		packet[0] = (byte)((63 << 2) + 3);
		packet[1] = 49;
		for( j = 2; j < 51; j++ ) {
			packet[j] = 0;
		}
		// VG_UNDEF( sbuf,sizeof( sbuf ) );
		if( dec.opus_decode( packet, 51, sbuf, 0, 960, false ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] = (byte)(63 << 2);
		packet[1] = packet[2] = 0;
		if( dec.opus_decode( packet, -1, sbuf, 0, 960, false ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_decode( packet, 3, sbuf, 0, 60, false ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_decode( packet, 3, sbuf, 0, 480, false ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_decode( packet, 3, sbuf, 0, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    opus_decode() ................................ OK.\n" );
// ifndef DISABLE_FLOAT_API
		// VG_UNDEF( fbuf,sizeof( fbuf ) );
		if( dec.opus_decode_float( packet, 3, fbuf, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    opus_decode_float() .......................... OK.\n" );
// #endif

/* #if 0
		// These tests are disabled because the library crashes with null states
		if( JOpusDecoder.opus_decoder_ctl( 0,Jopus_defines.OPUS_RESET_STATE )          != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_decoder_init( 0,48000,1 )                  != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_decode( 0,packet,1,outbuf,2880,0 )         != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_decode_float( 0,packet,1,0,2880,0 )        != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_decoder_get_nb_samples( 0,packet,1 )       != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_packet_get_nb_frames( NULL,1 )             != Jopus_defines.OPUS_BAD_ARG ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_packet_get_bandwidth( NULL )               != Jopus_defines.OPUS_BAD_ARG ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_packet_get_samples_per_frame( NULL,48000 ) != Jopus_defines.OPUS_BAD_ARG ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
#endif */
		dec = null;// opus_decoder_destroy( dec );
		cfgs++;
		System.out.printf( "                   All decoder interface tests passed\n" );
		System.out.printf( "                             (%6d API invocations)\n", Integer.valueOf( cfgs ) );
		return cfgs;
	}

	private static final int test_msdec_api()
	{
		final String method_name = "test_msdec_api";
		JOpusMSDecoder dec;
		JOpusDecoder streamdec;
		int i, j, cfgs;
		final byte packet[] = new byte[1276];
		final char mapping[] = new char[256];
// #ifndef DISABLE_FLOAT_API
		final float fbuf[] = new float[960 * 2];
// #endif
		final short sbuf[] = new short[960 * 2];
		int a, b, c;
		final int[] err = new int[1];
		final Object[] request = new Object[1];// java helper

		mapping[0] = 0;
		mapping[1] = 1;
		/*for( i = 2; i < 256; i++ ) {
			VG_UNDEF( &mapping[i], sizeof( unsigned char ) );
		}*/

		cfgs = 0;
		/*First test invalid configurations which should fail*/
		System.out.printf( "\n  Multistream decoder basic API tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );
		for( a = -1; a < 4; a++ )
		{
			for( b = -1; b < 4; b++ )
			{
				/* java: opus_multistream_decoder_get_size not implementeted
				i = opus_multistream_decoder_get_size( a, b );
				if( ( (a > 0 && b <= a && b >= 0) && (i <= 2048 || i > ((1 << 16) * a) ) ) || ( (a < 1 || b > a || b < 0) && i != 0 ) ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				System.out.printf( "    opus_multistream_decoder_get_size( %2d,%2d ) = %d %sOK.\n", a, b, i, i > 0 ? "" : "... " );
				*/
				cfgs++;
			}
		}

		/*Test with unsupported sample rates*/
		for( c = 1; c < 3; c++ )
		{
			for( i = -7; i <= 96000; i++ )
			{
				int fs;
				if( (i == 8000 || i == 12000 || i == 16000 || i == 24000 || i == 48000) && (c == 1 || c == 2) ) {
					continue;
				}
				switch( i )
				{
				case( -5 ): fs = -8000; break;
				case( -6 ): fs = Integer.MAX_VALUE; break;
				case( -7 ): fs = Integer.MIN_VALUE; break;
				default: fs = i;
				}
				err[0] = Jopus_defines.OPUS_OK;
				// VG_UNDEF( &err,sizeof( err ) );
				dec = JOpusMSDecoder.opus_multistream_decoder_create( fs, c, 1, c - 1, mapping, err );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG || dec != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				dec = JOpusMSDecoder.opus_multistream_decoder_create( fs, c, 1, c - 1, mapping, null );
				if( dec != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				/* java: not implemented
				dec = malloc( opus_multistream_decoder_get_size( 1, 1 ) );
				if( dec == NULL ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}*/
				dec = new JOpusMSDecoder();
				err[0] = dec.opus_multistream_decoder_init( fs, c, 1, c - 1, mapping );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				dec = null;// free( dec );
			}
		}

		for( c = 0; c < 2; c++ )
		{
			final int[] ret_err = c != 0 ? null : err;

			mapping[0] = 0;
			mapping[1] = 1;
			/* for( i = 2; i < 256; i++ ) {
				VG_UNDEF( &mapping[i],sizeof( unsigned char ) );
			}*/

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 2, 1, 0, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			mapping[0] = mapping[1] = 0;
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 2, 1, 0, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_OK ) || dec == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
			dec = null;// opus_multistream_decoder_destroy( dec );
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 1, 4, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_OK ) || dec == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
			}
			cfgs++;

			err[0] = dec.opus_multistream_decoder_init( 48000, 1, 0, 0, mapping );
			if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			err[0] = dec.opus_multistream_decoder_init( 48000, 1, 1, -1, mapping );
			if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			dec = null;// opus_multistream_decoder_destroy( dec );
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 2, 1, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_OK) || dec == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
			dec = null;// opus_multistream_decoder_destroy( dec );
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 255, 255, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, -1, 1, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 0, 1, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 1, -1, 2, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 1, -1, -1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 256, 255, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 256, 255, 0, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			mapping[0] = 255;
			mapping[1] = 1;
			mapping[2] = 2;
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 3, 2, 0, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			mapping[0] = 0;
			mapping[1] = 0;
			mapping[2] = 0;
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 3, 2, 1, mapping, ret_err );
			// if( ret_err ) {VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_OK) || dec == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
			dec = null;// opus_multistream_decoder_destroy( dec );
			cfgs++;

			// VG_UNDEF( ret_err,sizeof( *ret_err ) );
			mapping[0] = 0;
			mapping[1] = 255;
			mapping[2] = 1;
			mapping[3] = 2;
			mapping[4] = 3;
			dec = JOpusMSDecoder.opus_multistream_decoder_create( 48001, 5, 4, 1, mapping, ret_err );
			// if( ret_err ) { VG_CHECK( ret_err,sizeof( *ret_err ) ); }
			if( (null != ret_err && ret_err[0] != Jopus_defines.OPUS_BAD_ARG ) || dec != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
		}

		// VG_UNDEF( &err,sizeof( err ) );
		mapping[0] = 0;
		mapping[1] = 255;
		mapping[2] = 1;
		mapping[3] = 2;
		dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 4, 2, 1, mapping, err );
		// VG_CHECK( &err,sizeof( err ) );
		if( err[0] != Jopus_defines.OPUS_OK || dec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;

		System.out.printf( "    opus_multistream_decoder_create() ............ OK.\n" );
		System.out.printf( "    opus_multistream_decoder_init() .............. OK.\n" );

		// VG_UNDEF( &dec_final_range,sizeof( dec_final_range ) );
		err[0] = dec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
		((Long)request[0]).longValue();
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_CHECK( &dec_final_range,sizeof( dec_final_range ) );
		System.out.printf( "    OPUS_GET_FINAL_RANGE ......................... OK.\n" );
		cfgs++;

		streamdec = null;
		// VG_UNDEF( &streamdec,sizeof( streamdec ) );
		err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, -1, request );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, 1, request );
		streamdec = (JOpusDecoder)request[0];// java
		if( err[0] != Jopus_defines.OPUS_OK || streamdec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_CHECK( streamdec,opus_decoder_get_size( 1 ) );
		cfgs++;
		err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, 2, request );
		streamdec = (JOpusDecoder)request[0];// java
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, 0, request );
		streamdec = (JOpusDecoder)request[0];// java
		if( err[0] != Jopus_defines.OPUS_OK || streamdec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_CHECK( streamdec,opus_decoder_get_size( 1 ) );
		System.out.printf( "    OPUS_MULTISTREAM_GET_DECODER_STATE ........... OK.\n" );
		cfgs++;

		for( j = 0; j < 2; j++ )
		{
			err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, j, request );
			final JOpusDecoder od = (JOpusDecoder)request[0];// java
			if( err[0] != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			// VG_UNDEF( &i,sizeof( i ) );
			err[0] = od.opus_decoder_ctl( Jopus_defines.OPUS_GET_GAIN, request );
			i = ((Integer)request[0]).intValue();// java
			// VG_CHECK( &i,sizeof( i ) );
			if( err[0] != Jopus_defines.OPUS_OK || i != 0 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
		}
		err[0] = dec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_SET_GAIN, 15 );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_SET_GAIN ................................ OK.\n" );
		for( j = 0; j < 2; j++ )
		{
			err[0] = dec.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, j, request );
			final JOpusDecoder od = (JOpusDecoder)request[0];// java
			if( err[0] != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			// VG_UNDEF( &i,sizeof( i ) );
			err[0] = od.opus_decoder_ctl( Jopus_defines.OPUS_GET_GAIN, request );
			i = ((Integer)request[0]).intValue();// java
			// VG_CHECK( &i,sizeof( i ) );
			if( err[0] != Jopus_defines.OPUS_OK || i != 15 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			cfgs++;
		}
		System.out.printf( "    OPUS_GET_GAIN ................................ OK.\n" );

		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = dec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_GET_BANDWIDTH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_GET_BANDWIDTH ........................... OK.\n" );
		cfgs++;

		err[0] = dec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_UNIMPLEMENTED );
		if( err[0] != Jopus_defines.OPUS_UNIMPLEMENTED ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_UNIMPLEMENTED ........................... OK.\n" );
		cfgs++;

/* #if 0
		// Currently unimplemented for multistream
		// GET_PITCH has different execution paths depending on the previously decoded frame.
		err = opus_multistream_decoder_ctl( dec, Jopus_defines.OPUS_GET_PITCH( nullvalue ) );
		if( err != Jopus_defines.OPUS_BAD_ARG )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		VG_UNDEF( &i,sizeof( i ) );
		err = opus_multistream_decoder_ctl( dec, Jopus_defines.OPUS_GET_PITCH( &i ) );
		if( err != Jopus_defines.OPUS_OK || i > 0 || i < -1 )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		VG_UNDEF( packet,sizeof( packet ) );
		packet[0] = 63 << 2; packet[1] = packet[2] = 0;
		if( opus_multistream_decode( dec, packet, 3, sbuf, 960, 0 ) != 960 )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		VG_UNDEF( &i,sizeof( i ) );
		err = opus_multistream_decoder_ctl( dec, Jopus_defines.OPUS_GET_PITCH( &i ) );
		if( err != Jopus_defines.OPUS_OK || i > 0 || i < -1 )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		packet[0] = 1;
		if( opus_multistream_decode( dec, packet, 1, sbuf, 960, 0 ) != 960 )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		VG_UNDEF( &i,sizeof( i ) );
		err = opus_multistream_decoder_ctl( dec, Jopus_defines.OPUS_GET_PITCH( &i ) );
		if( err != Jopus_defines.OPUS_OK || i > 0 || i < -1 )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		System.out.printf( "    OPUS_GET_PITCH ............................... OK.\n" );
#endif */

		/*Reset the decoder*/
		if( dec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_RESET_STATE ............................. OK.\n" );
		cfgs++;

		dec = null;// opus_multistream_decoder_destroy( dec );
		cfgs++;
		// VG_UNDEF( &err,sizeof( err ) );
		dec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 2, 1, 1, mapping, err );
		if( err[0] != Jopus_defines.OPUS_OK || dec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;

		packet[0] = (byte)((63 << 2) + 3);
		packet[1] = 49;
		for( j = 2; j < 51; j++ ) {
			packet[j] = 0;
		}
		// VG_UNDEF( sbuf,sizeof( sbuf ) );
		if( dec.opus_multistream_decode( packet, 0, 51, sbuf, 0, 960, false ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] = (byte)(63 << 2);
		packet[1] = packet[2] = 0;
		if( dec.opus_multistream_decode( packet, 0, -1, sbuf, 0, 960, false ) != Jopus_defines.OPUS_BAD_ARG ) {
			System.out.printf("%d\n", Integer.valueOf( dec.opus_multistream_decode( packet, 0, -1, sbuf, 0, 960, false ) ) );
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_multistream_decode( packet, 0, 3, sbuf, 0, -960, false ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_multistream_decode( packet, 0, 3, sbuf, 0, 60, false ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_multistream_decode( packet, 0, 3, sbuf, 0, 480, false ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( dec.opus_multistream_decode( packet, 0, 3, sbuf, 0, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    opus_multistream_decode() .................... OK.\n" );
// #ifndef DISABLE_FLOAT_API
		// VG_UNDEF( fbuf,sizeof( fbuf ) );
		if( dec.opus_multistream_decode_float( packet, 0, 3, fbuf, 0, 960, false ) != 960 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    opus_multistream_decode_float() .............. OK.\n" );
// #endif

/* #if 0
		// These tests are disabled because the library crashes with null states
		if( opus_multistream_decoder_ctl( 0,Jopus_defines.OPUS_RESET_STATE )          != Jopus_defines.OPUS_INVALID_STATE )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_multistream_decoder_init( 0,48000,1 )                  != Jopus_defines.OPUS_INVALID_STATE )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_multistream_decode( 0,packet,1,outbuf,2880,0 )         != Jopus_defines.OPUS_INVALID_STATE )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_multistream_decode_float( 0,packet,1,0,2880,0 )        != Jopus_defines.OPUS_INVALID_STATE )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_multistream_decoder_get_nb_samples( 0,packet,1 )       != Jopus_defines.OPUS_INVALID_STATE )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
#endif */
		dec = null;// opus_multistream_decoder_destroy( dec );
		cfgs++;
		System.out.printf( "       All multistream decoder interface tests passed\n" );
		System.out.printf( "                             (%6d API invocations)\n", Integer.valueOf( cfgs ) );
		return cfgs;
	}

/* #ifdef VALGRIND
	#define UNDEFINE_FOR_PARSE  toc = -1;  \
	frames[0] = ( unsigned char * )0;  \
	frames[1] = ( unsigned char * )0;  \
	payload_offset = -1;  \
	VG_UNDEF( &toc,sizeof( toc ) );  \
	VG_UNDEF( frames,sizeof( frames ) ); \
	VG_UNDEF( &payload_offset,sizeof( payload_offset ) );
#else */
/* java: extracted in place. FIXME toc is unsigned char
#define UNDEFINE_FOR_PARSE  toc = -1;  \
	frames[0] = ( unsigned char * )0;  \
	frames[1] = ( unsigned char * )0;  \
	payload_offset = -1;*/
// #endif

	/** This test exercises the heck out of the libopus parser.
	It is much larger than the parser itself in part because
	it tries to hit a lot of corner cases that could never
	fail with the libopus code, but might be problematic for
	other implementations.
	* @return counter
	*/
	private static final int test_parse()
	{
		final String method_name = "test_parse";

		int i, j, jj, sz;

		int cfgs, cfgs_total;
		// final byte toc;
		// final int payload_offset;
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();// java, replacing toc and payload_offset
		final int frames[] = new int[48];// java: offsets. packet[ frames[ i ] ]
		final short size[] = new short[48];
		int ret;
		System.out.printf( "\n  Packet header parsing tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );
		final byte packet[] = new byte[1276];
		// memset( packet, 0, sizeof( char )*1276 );// java already zeroed
		packet[0] = (byte)(63 << 2);
		if( Jopus.opus_packet_parse( packet, 0, 1, /* &toc,*/ frames, null/*, &payload_offset*/, aux ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs_total = cfgs = 1;
		/*code 0*/
		for( i = 0; i < 64; i++ )
		{
			packet[0] = (byte)(i << 2);
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 4, /*&toc,*/ frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != 1 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( size[0] != 3 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( frames[0] != /* packet + */1 ) {// java changed
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}
		System.out.printf( "    code 0 (%2d cases) ............................ OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		/*code 1, two frames of the same size*/
		for( i = 0; i < 64; i++ )
		{
			packet[0] = (byte)((i << 2) + 1);
			for( jj = 0; jj <= 1275 * 2 + 3; jj++ )
			{
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, jj,/* &toc,*/ frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( (jj & 1) == 1 && jj <= 2551 )
				{
					/* Must pass if payload length even ( packet length odd ) and
					size <= 2551, must fail otherwise. */
					if( ret != 2 ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( size[0] != size[1] || size[0] != ((jj - 1) >> 1) ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( frames[0] != /*packet + */1 ) {// java changed
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( frames[1] != frames[0] + size[0] ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( (((int)aux.mToc & 0xff) >> 2) != i ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
				} else if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
		}
		System.out.printf( "    code 1 (%6d cases) ........................ OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			/*code 2, length code overflow*/
			packet[0] = (byte)((i << 2) + 2);
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 1,/* &toc,*/ frames, size/*, &payload_offset */, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			packet[1] = (byte)252;
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			for( j = 0; j < 1275; j++ )
			{
				if( j < 252 ) {
					packet[1] = (byte)j;
				} else{
					packet[1] = (byte)(252 + (j & 3)); packet[2] = (byte)((j - 252) >> 2);
				}
				/*Code 2, one too short*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, j + (j < 252 ? 2 : 3 ) - 1/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*Code 2, one too long*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, j + (j < 252 ? 2 : 3) + 1276/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*Code 2, second zero*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, j + (j < 252 ? 2 : 3)/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != 2 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( size[0] != j || size[1] != 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( frames[1] != frames[0]+size[0] ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( (((int)aux.mToc & 0xff) >> 2 ) != i ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*Code 2, normal*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, (j << 1) + 4/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != 2 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( size[0] != j || size[1] != (j << 1) + 3 - j - (j < 252 ? 1 : 2) ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( frames[1] != frames[0] + size[0] ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( (((int)aux.mToc & 0xff) >> 2) != i ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
		}
		System.out.printf( "    code 2 (%6d cases) ........................ OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			packet[0] = (byte)((i << 2) + 3);
			/*code 3, length code overflow*/
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 1/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}
		System.out.printf( "    code 3 m-truncation (%2d cases) ............... OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			/*code 3, m is zero or 49-63*/
			packet[0] = (byte)((i << 2) + 3);
			for( jj = 49; jj <= 64; jj++ )
			{
				packet[1] = (byte)(0 + (jj & 63));  /*CBR, no padding*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 1275/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				packet[1] = (byte)(128 + (jj & 63));  /*VBR, no padding*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 1275/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				packet[1] = (byte)(64 + (jj & 63));  /*CBR, padding*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 1275/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				packet[1] = (byte)(128 + 64 + (jj & 63));  /*VBR, padding*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 1275/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
		}
		System.out.printf( "    code 3 m=0,49-64 (%2d cases) ................ OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			packet[0] = (byte)((i << 2) + 3);
			/*code 3, m is one, cbr*/
			packet[1] = 1;
			for( j = 0; j < 1276; j++ )
			{
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, j + 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != 1 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( size[0] != j ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( (((int)aux.mToc & 0xff) >> 2) != i ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 1276 + 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}
		System.out.printf( "    code 3 m=1 CBR (%2d cases) ................. OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			/*code 3, m > 1 CBR*/
			packet[0] = (byte)((i << 2) + 3);
			final int frame_samp = Jopus.opus_packet_get_samples_per_frame( packet, 0, 48000 );
			for( j = 2; j < 49; j++ )
			{
				packet[1] = (byte)j;
				for( sz = 2; sz < ((j + 2) * 1275); sz++ )
				{
					aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
					ret = Jopus.opus_packet_parse( packet, 0, sz/*, &toc*/, frames, size/*, &payload_offset*/, aux );
					cfgs++;
					/*Must be  <= 120ms, must be evenly divisible, can't have frames > 1275 bytes*/
					if( frame_samp * j <= 5760 && (sz - 2) % j == 0 && (sz - 2) / j < 1276 )
					{
						if( ret != j ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						for( jj = 1; jj < ret; jj++ ) {
							if( frames[jj] != frames[jj - 1] + size[jj - 1] ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
						}
						if( (((int)aux.mToc & 0xff) >> 2) != i ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					} else if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
				}
			}
			/*Super jumbo packets*/
			packet[1] = (byte)(5760 / frame_samp);
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 1275 * packet[1] + 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != packet[1] ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			for( jj = 0; jj < ret; jj++ ) {
				if( size[jj] != 1275 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
		}
		System.out.printf( "    code 3 m=1-48 CBR (%2d cases) .......... OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			/*Code 3 VBR, m one*/
			packet[0] = (byte)((i << 2) + 3);
			packet[1] = (byte)(128 + 1);
			final int frame_samp = Jopus.opus_packet_get_samples_per_frame( packet, 0, 48000 );
			for( jj = 0; jj < 1276; jj++ )
			{
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + jj/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != 1 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( size[0] != jj ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( (((int)aux.mToc & 0xff) >> 2 ) != i ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
			}
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 2 + 1276/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			for( j = 2; j < 49; j++ )
			{
				packet[1] = (byte)(128 + j);
				/*Length code overflow*/
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + j - 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				packet[2] = (byte)252;
				packet[3] = 0;
				for( jj = 4; jj < 2 + j; jj++ ) {
					packet[jj] = 0;
				}
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + j/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*One byte too short*/
				for( jj = 2; jj < 2+j; jj++ ) {
					packet[jj] = 0;
				}
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + j - 2/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*One byte too short thanks to length coding*/
				packet[2] = (byte)252;
				packet[3] = 0;
				for( jj = 4; jj < 2+j; jj++ ) {
					packet[jj] = 0;
				}
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + j + 252 - 1/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*Most expensive way of coding zeros*/
				for( jj = 2; jj < 2 + j; jj++ ) {
					packet[jj] = 0;
				}
				aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
				ret = Jopus.opus_packet_parse( packet, 0, 2 + j - 1/*, &toc*/, frames, size/*, &payload_offset*/, aux );
				cfgs++;
				if( frame_samp*j <= 5760 ) {
					if( ret != j ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					for( jj = 0; jj < j; jj++ ) {
						if( size[jj] != 0 ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					if( (((int)aux.mToc & 0xff) >> 2) != i ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
				} else if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				/*Quasi-CBR use of mode 3*/
				for( sz = 0; sz < 8; sz++ )
				{
					final int tsz[/* 8 */] = { 50, 201, 403, 700, 1472, 5110, 20400, 61298 };
					int pos = 0;
					final int as = (tsz[sz] + i - j - 2) / j;
					for( jj = 0; jj < j - 1; jj++ )
					{
						if( as < 252 ) {packet[2 + pos] = (byte)as; pos++; }
						else{ packet[2 + pos] = (byte)(252 + (as & 3)); packet[3 + pos] = (byte)((as - 252) >> 2); pos += 2; }
					}
					aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
					ret = Jopus.opus_packet_parse( packet, 0, tsz[sz] + i/*, &toc*/, frames, size/*, &payload_offset*/, aux );
					cfgs++;
					if( frame_samp * j <= 5760 && as < 1276 && (tsz[sz] + i - 2 - pos - as * (j - 1) ) < 1276 ) {
						if( ret != j ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						for( jj = 0; jj < j - 1; jj++ ) {
							if( size[jj] != as ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
						}
						if( size[j - 1] != (tsz[sz] + i - 2 - pos - as * (j - 1)) ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( (((int)aux.mToc & 0xff) >> 2) != i ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					} else if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
				}
			}
		}
		System.out.printf( "    code 3 m=1-48 VBR (%2d cases) ............. OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs; cfgs = 0;

		for( i = 0; i < 64; i++ )
		{
			packet[0] = (byte)((i << 2) + 3);
			/*Padding*/
			packet[1] = (byte)(128 + 1 + 64);
			/*Overflow the length coding*/
			for( jj = 2; jj < 127; jj++ ) {
				packet[jj] = (byte)255;
			}
			aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
			ret = Jopus.opus_packet_parse( packet, 0, 127/*, &toc*/, frames, size/*, &payload_offset*/, aux );
			cfgs++;
			if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			for( sz = 0; sz < 4; sz++ )
			{
				final int tsz[/* 4 */] = {0, 72, 512, 1275};
				for( jj = sz; jj < 65025; jj += 11 )
				{
					int pos;
					for( pos = 0; pos < jj / 254; pos++ ) {
						packet[2 + pos] = (byte)255;
					}
					packet[2 + pos] = (byte)(jj % 254);
					pos++;
					if( sz == 0 && i == 63 )
					{
						/*Code more padding than there is room in the packet*/
						aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
						ret = Jopus.opus_packet_parse( packet, 0, 2 + jj + pos - 1/*, &toc*/, frames, size/*, &payload_offset*/, aux );
						cfgs++;
						if( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					aux.mToc = -1; frames[0] = 0; frames[1] = 0;// UNDEFINE_FOR_PARSE
					ret = Jopus.opus_packet_parse( packet, 0, 2 + jj + tsz[sz] + i + pos/*, &toc*/, frames, size/*, &payload_offset*/, aux );
					cfgs++;
					if( tsz[sz] + i < 1276 )
					{
						if( ret != 1 ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( size[0] != tsz[sz] + i ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( (((int)aux.mToc & 0xff) >> 2) != i ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					} else if ( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
				}
			}
		}
		System.out.printf( "    code 3 padding (%2d cases) ............... OK.\n", Integer.valueOf( cfgs ) );
		cfgs_total += cfgs;
		System.out.printf( "    opus_packet_parse ............................ OK.\n" );
		System.out.printf( "                      All packet parsing tests passed\n" );
		System.out.printf( "                          (%d API invocations)\n", Integer.valueOf( cfgs_total ) );
		return cfgs_total;
	}

	/**
	 * This is a helper macro for the encoder tests.
	 * The encoder api tests all have a pattern of set-must-fail, set-must-fail,
	 * set-must-pass, get-and-compare, set-must-pass, get-and-compare.
	 * @param enc
	 * @param method_name
	 * @param setcall
	 * @param getcall
	 * @param badv
	 * @param badv2
	 * @param goodv
	 * @param goodv2
	 * @param sok
	 * @param gok
	 * @return
	 */
	private static final int CHECK_SETGET(final JOpusEncoder enc, final String method_name,// java
			final int setcall, final int getcall, final int badv, final int badv2, final int goodv, final int goodv2, final String sok, final String gok ) {
		int i, j;
		final Object[] request = new Object[1];// java
		i = ( badv );
		if( enc.opus_encoder_ctl( setcall, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = ( badv2 );
		if( enc.opus_encoder_ctl( setcall, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		j = i = ( goodv );
		if( enc.opus_encoder_ctl( setcall, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = -12345;
		// VG_UNDEF( &i, sizeof( i ) );
		int err = enc.opus_encoder_ctl( getcall, request );
		i = ((Integer)request[0]).intValue();// java
		if( err != Jopus_defines.OPUS_OK || i != j ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		j = i = ( goodv2 );
		if( enc.opus_encoder_ctl( setcall, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( sok );
		i = -12345;
		// VG_UNDEF( &i, sizeof( i ) );
		err = enc.opus_encoder_ctl( getcall, request );
		i = ((Integer)request[0]).intValue();// java
		if( err != Jopus_defines.OPUS_OK || i != j ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( gok );
		// cfgs += 6;
		return 6;// java
	}

	/**
	 * java: variant of the macro for boolean values
	 * @param enc
	 * @param method_name
	 * @param setcall
	 * @param getcall
	 * @param goodv
	 * @param goodv2
	 * @param sok
	 * @param gok
	 * @return
	 */
	private static final int CHECK_SETGET(final JOpusEncoder enc, final String method_name,// java
			final int setcall, final int getcall/*, final boolean badv, final boolean badv2*/, final boolean goodv, final boolean goodv2, final String sok, final String gok ) {
		boolean i, j;
		final Object[] request = new Object[1];// java
		/* java: boolean can not have incorrect value as in C ( < 0 or > 1 )
		i = ( badv );
		if( JOpusEncoder.opus_encoder_ctl( enc, setcall, Boolean.valueOf( i ) ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = ( badv2 );
		if( JOpusEncoder.opus_encoder_ctl( enc, setcall, Boolean.valueOf( i ) ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		*/
		j = i = ( goodv );
		if( enc.opus_encoder_ctl( setcall, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = true;// -12345;
		// VG_UNDEF( &i, sizeof( i ) );
		int err = enc.opus_encoder_ctl( getcall, request );
		i = ((Boolean)request[0]).booleanValue();// java
		if( err != Jopus_defines.OPUS_OK || i != j ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		j = i = ( goodv2 );
		if( enc.opus_encoder_ctl( setcall, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( sok );
		i = true;//-12345;
		// VG_UNDEF( &i, sizeof( i ) );
		err = enc.opus_encoder_ctl( getcall, request );
		i = ((Boolean)request[0]).booleanValue();// java
		if( err != Jopus_defines.OPUS_OK || i != j ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( gok );
		// cfgs += 6;
		return 6;// java
	}

	private static final int test_enc_api()
	{
		final String method_name = "test_enc_api";
		JOpusEncoder enc;
		int i;
		final byte packet[] = new byte[1276];
// #ifndef DISABLE_FLOAT_API
		final float fbuf[] = new float[960 * 2];
// #endif
		final short sbuf[] = new short[960 * 2];
		int c, cfgs;
		final int[] err = new int[1];
		final Object[] request = new Object[1];// java helper

		cfgs = 0;
		/*First test invalid configurations which should fail*/
		System.out.printf( "\n  Encoder basic API tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );
		for( c = 0; c < 4; c++ )
		{
			/* java: not realized
			i = JOpusEncoder.opus_encoder_get_size( c );
			if( ((c == 1 || c == 2) && (i <= 2048 || i > 1 << 17)) || ((c != 1 && c != 2) && i != 0) ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			System.out.printf( "    opus_encoder_get_size( %d ) = %d ...............%s OK.\n", c, i, i > 0 ? "" : "...." );
			*/
			cfgs++;
		}

		/*Test with unsupported sample rates, channel counts*/
		for( c = 0; c < 4; c++ )
		{
			for( i = -7; i <= 96000; i++ )
			{
				int fs;
				if( (i == 8000 || i == 12000 || i == 16000 || i == 24000 || i == 48000) && (c == 1 || c == 2) ) {
					continue;
				}
				switch( i )
				{
				case( -5 ): fs = -8000; break;
				case( -6 ): fs = Integer.MAX_VALUE; break;
				case( -7 ): fs = Integer.MIN_VALUE; break;
				default: fs = i;
				}
				err[0] = Jopus_defines.OPUS_OK;
				// VG_UNDEF( &err,sizeof( err ) );
				enc = JOpusEncoder.opus_encoder_create( fs, c, Jopus_defines.OPUS_APPLICATION_VOIP, err );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG || enc != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				enc = JOpusEncoder.opus_encoder_create( fs, c, Jopus_defines.OPUS_APPLICATION_VOIP, null );
				if( enc != null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				enc = null;// opus_encoder_destroy( enc );
				enc = new JOpusEncoder();// ( opus_encoder_get_size( 2 ) );
				/*if( enc == null ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}*/
				err[0] = enc.opus_encoder_init( fs, c, Jopus_defines.OPUS_APPLICATION_VOIP );
				if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				enc = null;// free( enc );
			}
		}

		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_AUTO, null );
		if( enc != null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		// VG_UNDEF( &err,sizeof( err ) );
		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_AUTO, err );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG || enc != null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		// VG_UNDEF( &err,sizeof( err ) );
		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_APPLICATION_VOIP, null );
		if( enc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		enc = null;// opus_encoder_destroy( enc );
		cfgs++;

		// VG_UNDEF( &err,sizeof( err ) );
		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY, err );
		if( err[0] != Jopus_defines.OPUS_OK || enc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i < 0 || i > 32766 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		enc = null;// opus_encoder_destroy( enc );

		// VG_UNDEF( &err,sizeof( err ) );
		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_APPLICATION_AUDIO, err );
		if( err[0] != Jopus_defines.OPUS_OK || enc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i < 0 || i > 32766 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		enc = null;// opus_encoder_destroy( enc );
		cfgs++;

		// VG_UNDEF( &err,sizeof( err ) );
		enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_APPLICATION_VOIP, err );
		if( err[0] != Jopus_defines.OPUS_OK || enc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;

		System.out.printf( "    opus_encoder_create() ........................ OK.\n" );
		System.out.printf( "    opus_encoder_init() .......................... OK.\n" );

		i = -12345;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = enc.opus_encoder_ctl(Jopus_defines.OPUS_GET_LOOKAHEAD, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i < 0 || i > 32766 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_LOOKAHEAD ........................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || i != 48000 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_SAMPLE_RATE, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_SAMPLE_RATE ......................... OK.\n" );

		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_UNIMPLEMENTED ) != Jopus_defines.OPUS_UNIMPLEMENTED ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    OPUS_UNIMPLEMENTED ........................... OK.\n" );
		cfgs++;

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_APPLICATION, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_APPLICATION, Jopus_defines.OPUS_GET_APPLICATION, -1, Jopus_defines.OPUS_AUTO,
			Jopus_defines.OPUS_APPLICATION_AUDIO, Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY,
			"    OPUS_SET_APPLICATION ......................... OK.\n",
			"    OPUS_GET_APPLICATION ......................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_BITRATE, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, 1073741832 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// VG_UNDEF( &i,sizeof( i ) );
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_BITRATE, request ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = ((Integer)request[0]).intValue();// java
		if( i > 700000 || i < 256000 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_BITRATE, Jopus_defines.OPUS_GET_BITRATE, -12345, 0,
			500, 256000,
			"    OPUS_SET_BITRATE ............................. OK.\n",
			"    OPUS_GET_BITRATE ............................. OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FORCE_CHANNELS, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_FORCE_CHANNELS, Jopus_defines.OPUS_GET_FORCE_CHANNELS, -1, 3,
			1, Jopus_defines.OPUS_AUTO,
			"    OPUS_SET_FORCE_CHANNELS ...................... OK.\n",
			"    OPUS_GET_FORCE_CHANNELS ...................... OK.\n" );

		i = -2;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_FULLBAND+1;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_SET_BANDWIDTH ........................... OK.\n" );
		/*We don't test if the bandwidth has actually changed.
		because the change may be delayed until the encoder is advanced.*/
		i = -12345;
		// VG_UNDEF( &i,sizeof( i ) );
		err [0]= enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_BANDWIDTH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || ( i != Jopus_defines.OPUS_BANDWIDTH_NARROWBAND &&
			i != Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND && i != Jopus_defines.OPUS_BANDWIDTH_WIDEBAND &&
			i != Jopus_defines.OPUS_BANDWIDTH_FULLBAND && i != Jopus_defines.OPUS_AUTO ) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_AUTO ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_BANDWIDTH, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_BANDWIDTH ........................... OK.\n" );

		i = -2;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_FULLBAND+1;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) == Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, i ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_SET_MAX_BANDWIDTH ....................... OK.\n" );
		/*We don't test if the bandwidth has actually changed.
		because the change may be delayed until the encoder is advanced.*/
		i = -12345;
		// VG_UNDEF( &i,sizeof( i ) );
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_MAX_BANDWIDTH, request );
		i = ((Integer)request[0]).intValue();// java
		if( err[0] != Jopus_defines.OPUS_OK || ( i != Jopus_defines.OPUS_BANDWIDTH_NARROWBAND &&
			i != Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND && i != Jopus_defines.OPUS_BANDWIDTH_WIDEBAND &&
			i != Jopus_defines.OPUS_BANDWIDTH_FULLBAND ) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_MAX_BANDWIDTH, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_GET_MAX_BANDWIDTH ....................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_DTX, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_DTX, Jopus_defines.OPUS_GET_DTX,// -1, 2,// java
			true, false,// 1, 0,
			"    OPUS_SET_DTX ................................. OK.\n",
			"    OPUS_GET_DTX ................................. OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_COMPLEXITY, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_COMPLEXITY, Jopus_defines.OPUS_GET_COMPLEXITY, -1, 11,
			0, 10,
			"    OPUS_SET_COMPLEXITY .......................... OK.\n",
			"    OPUS_GET_COMPLEXITY .......................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_INBAND_FEC, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_INBAND_FEC, Jopus_defines.OPUS_GET_INBAND_FEC,// -1, 2,// java
			true, false,// 1, 0,
			"    OPUS_SET_INBAND_FEC .......................... OK.\n",
			"    OPUS_GET_INBAND_FEC .......................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_PACKET_LOSS_PERC, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, Jopus_defines.OPUS_GET_PACKET_LOSS_PERC, -1, 101,
			100, 0,
			"    OPUS_SET_PACKET_LOSS_PERC .................... OK.\n",
			"    OPUS_GET_PACKET_LOSS_PERC .................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_VBR, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_VBR, Jopus_defines.OPUS_GET_VBR,// -1, 2,// java
			true, false,// 1, 0,
			"    OPUS_SET_VBR ................................. OK.\n",
			"    OPUS_GET_VBR ................................. OK.\n" );

		/*   err = opus_encoder_ctl( enc,Jopus_defines.OPUS_GET_VOICE_RATIO( ( opus_int32 * )NULL ) );
		if( err != Jopus_defines.OPUS_BAD_ARG )Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		cfgs++;
		CHECK_SETGET( Jopus_defines.OPUS_SET_VOICE_RATIO( i ),Jopus_defines.OPUS_GET_VOICE_RATIO( &i ),-2,101,
			0,50,
			"    Jopus_defines.OPUS_SET_VOICE_RATIO ......................... OK.\n",
			"    Jopus_defines.OPUS_GET_VOICE_RATIO ......................... OK.\n" )*/

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_VBR_CONSTRAINT, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_VBR_CONSTRAINT, Jopus_defines.OPUS_GET_VBR_CONSTRAINT,// -1, 2,// java
			true, false,// 1, 0,
			"    OPUS_SET_VBR_CONSTRAINT ...................... OK.\n",
			"    OPUS_GET_VBR_CONSTRAINT ...................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_SIGNAL, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_SIGNAL, Jopus_defines.OPUS_GET_SIGNAL, -12345, 0x7FFFFFFF,
			Jopus_defines.OPUS_SIGNAL_MUSIC, Jopus_defines.OPUS_AUTO,
			"    OPUS_SET_SIGNAL .............................. OK.\n",
			"    OPUS_GET_SIGNAL .............................. OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LSB_DEPTH, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_LSB_DEPTH, Jopus_defines.OPUS_GET_LSB_DEPTH, 7, 25, 16, 24,
			"    OPUS_SET_LSB_DEPTH ........................... OK.\n",
			"    OPUS_GET_LSB_DEPTH ........................... OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_PREDICTION_DISABLED, request );
		final boolean b = ((Boolean)request[0]).booleanValue();// java
		if( b ) {// if( i != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_PREDICTION_DISABLED, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_PREDICTION_DISABLED, Jopus_defines.OPUS_GET_PREDICTION_DISABLED,// -1, 2,// java
			true, false,// 1, 0,
			"    OPUS_SET_PREDICTION_DISABLED ................. OK.\n",
			"    OPUS_GET_PREDICTION_DISABLED ................. OK.\n" );

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		// FIXME OPUS_SET_EXPERT_FRAME_DURATION is not implemented and the returned status is not correct
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_2_5_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_5_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_10_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_20_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_40_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_60_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_80_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_100_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_120_MS );
		if( err[0] != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		cfgs += CHECK_SETGET( enc, method_name,
			Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION, 0, -1,
			Jopus_defines.OPUS_FRAMESIZE_60_MS,Jopus_defines.OPUS_FRAMESIZE_ARG,
			"    OPUS_SET_EXPERT_FRAME_DURATION ............... OK.\n",
			"    OPUS_GET_EXPERT_FRAME_DURATION ............... OK.\n" );

		/*Jopus_defines.OPUS_SET_FORCE_MODE is not tested here because it's not a public API, however the encoder tests use it*/

		err[0] = enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, null );
		if( err[0] != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		((Long)request[0]).longValue();
		cfgs++;
		System.out.printf( "    OPUS_GET_FINAL_RANGE ......................... OK.\n" );

		/*Reset the encoder*/
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    OPUS_RESET_STATE ............................. OK.\n" );

		// memset( sbuf,0,sizeof( short )*2*960 );
		for( i = 0; i < 2 * 960; i++ ) {
			sbuf[i] = 0;
		}
		// VG_UNDEF( packet,sizeof( packet ) );
		i = enc.opus_encode( sbuf, 0, 960, packet, packet.length );
		if( i < 1 || i > packet.length ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_CHECK( packet,i );
		cfgs++;
		System.out.printf( "    opus_encode() ................................ OK.\n" );
// #ifndef DISABLE_FLOAT_API
		// memset( fbuf, 0, sizeof( float )*2*960 );
		for( i = 0; i < 2 * 960; i++ ) {
			fbuf[i] = 0;
		}
		// VG_UNDEF( packet, sizeof( packet ) );
		i = enc.opus_encode_float( fbuf, 960, packet, packet.length );
		if( i < 1 || i > packet.length ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		// VG_CHECK( packet,i );
		cfgs++;
		System.out.printf( "    opus_encode_float() .......................... OK.\n" );
// #endif

/* #if 0
		// These tests are disabled because the library crashes with null states
		if( JOpusEncoder.opus_encoder_ctl( 0,Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_encoder_init( 0,48000,1,Jopus_defines.OPUS_APPLICATION_VOIP ) != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_encode( 0,sbuf,960,packet,sizeof( packet ) )       != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		if( opus_encode_float( 0,fbuf,960,packet,sizeof( packet ) ) != Jopus_defines.OPUS_INVALID_STATE ) Jtest_opus_common.test_failed( CLASS_NAME, method_name );
#endif */
		enc = null;// opus_encoder_destroy( enc );
		cfgs++;
		System.out.printf( "                   All encoder interface tests passed\n" );
		System.out.printf( "                             (%d API invocations)\n", Integer.valueOf( cfgs ) );
		return cfgs;
	}

	private static final int max_out = (1276 * 48 + 48 * 2 + 2);

	private static final int test_repacketizer_api()
	{
		final String method_name = "test_repacketizer_api";
		int ret, i,j,k;
		int cfgs = 0;
		System.out.printf( "\n  Repacketizer tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );

		byte[] packet = new byte[ max_out ];
		/* if( packet == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}*/
		// memset( packet, 0, max_out );// java: already zeroed
		byte[] po = new byte[ max_out + 256 ];
		/* if( po == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}*/
		/* java: not realized
		i = JOpusRepacketizer.opus_repacketizer_get_size();
		if( i <= 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf( "    opus_repacketizer_get_size() = %d ............. OK.\n", i );
		*/
		cfgs++;

		JOpusRepacketizer rp = new JOpusRepacketizer();
		rp = rp.opus_repacketizer_init();
		if( rp == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		rp = null;// free( rp );
		System.out.printf( "    opus_repacketizer_init ....................... OK.\n" );

		rp = JOpusRepacketizer.opus_repacketizer_create();
		if( rp == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return cfgs;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}
		cfgs++;
		System.out.printf( "    opus_repacketizer_create ..................... OK.\n" );

		if( rp.opus_repacketizer_get_nb_frames() != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		System.out.printf( "    opus_repacketizer_get_nb_frames .............. OK.\n" );

		/*Length overflows*/
		// VG_UNDEF( packet,4 );
		if( rp.opus_repacketizer_cat( packet, 0, 0 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Zero len */
		cfgs++;
		packet[0] = 1;
		if( rp.opus_repacketizer_cat( packet, 0, 2 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Odd payload code 1 */
		cfgs++;
		packet[0] = 2;
		if( rp.opus_repacketizer_cat( packet, 0, 1 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 2 overflow one */
		cfgs++;
		packet[0] = 3;
		if( rp.opus_repacketizer_cat( packet, 0, 1 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 3 no count */
		cfgs++;
		packet[0] = 2;
		packet[1] = (byte)255;
		if( rp.opus_repacketizer_cat( packet, 0, 2 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 2 overflow two */
		cfgs++;
		packet[0] = 2;
		packet[1] = (byte)250;
		if( rp.opus_repacketizer_cat( packet, 0, 251 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 2 overflow three */
		cfgs++;
		packet[0] = 3;
		packet[1] = 0;
		if( rp.opus_repacketizer_cat( packet, 0, 2 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 3 m = 0 */
		cfgs++;
		packet[1] = 49;
		if( rp.opus_repacketizer_cat( packet, 0, 100 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Code 3 m = 49 */
		cfgs++;
		packet[0] = 0;
		if( rp.opus_repacketizer_cat( packet, 0, 3 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] = 1 << 2;
		if( rp.opus_repacketizer_cat( packet, 0, 3 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}  /* Change in TOC */
		cfgs++;

		/* Code 0,1,3 CBR - >  Code 0,1,3 CBR */
		rp.opus_repacketizer_init();
		for( j = 0; j < 32; j++ )
		{
			/* TOC types, test half with stereo */
			packet[0] = (byte)(((j << 1) + (j & 1)) << 2);
			final int maxi = 960 / Jopus.opus_packet_get_samples_per_frame( packet, 0, 8000 );
			for( i = 1; i <= maxi; i++ )
			{
				/* Number of CBR frames in the input packets */
				packet[0] = (byte)(( (j << 1) + (j & 1) ) << 2);
				if( i > 1 ) {
					packet[0] += i == 2 ? 1 : 3;
				}
				packet[1] = (byte)(i > 2 ? i : 0);
				final int maxp = 960 / ( i * Jopus.opus_packet_get_samples_per_frame( packet, 0, 8000 ) );
				for( k = 0; k <= (1275 + 75); k += 3 )
				{
					/*Payload size*/
					int cnt, rcnt;
					if( k % i != 0 ) {
						continue;
					}  /* Only testing CBR here, payload must be a multiple of the count */
					for( cnt = 0; cnt < maxp + 2; cnt++ )
					{
						if( cnt > 0 )
						{
							ret = rp.opus_repacketizer_cat( packet, 0, k + ( i > 2 ? 2 : 1 ) );
							if( (cnt <= maxp && k <= (1275 * i)) ? ret != Jopus_defines.OPUS_OK : ret != Jopus_defines.OPUS_INVALID_PACKET ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
						}
						rcnt = k <= (1275 * i) ? (cnt < maxp ? cnt : maxp) : 0;
						if( rp.opus_repacketizer_get_nb_frames() != rcnt * i ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						cfgs++;
						ret = rp.opus_repacketizer_out_range( 0, rcnt * i, po, max_out );
						if( rcnt > 0 )
						{
							final int len = k * rcnt + ((rcnt * i) > 2 ? 2 : 1);
							if( ret != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							if( (rcnt * i) < 2 && (po[0] & 3) != 0 ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}                       /* Code 0 */
							if( (rcnt * i) == 2 && (po[0] & 3) != 1 ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}                      /* Code 1 */
							if( (rcnt * i) > 2 && (((po[0] & 3) != 3) || (po[1] != rcnt * i)) ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}  /* Code 3 CBR */
							cfgs++;
							if( rp.opus_repacketizer_out( po, len ) != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_packet_unpad( po, len ) != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_packet_pad( po, 0, len, len + 1 ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_packet_pad( po, 0, len + 1, len + 256 ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_packet_unpad( po, len + 256 ) != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_multistream_packet_unpad( po, len, 1 ) != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_multistream_packet_pad( po, len, len + 1, 1 ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_multistream_packet_pad( po, len + 1, len + 256, 1 ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( JOpusRepacketizer.opus_multistream_packet_unpad( po, len + 256, 1 ) != len ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( rp.opus_repacketizer_out( po, len - 1 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
							if( len > 1 )
							{
								if( rp.opus_repacketizer_out( po, 1 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
									Jtest_opus_common.test_failed( CLASS_NAME, method_name );
								}
								cfgs++;
							}
							if( rp.opus_repacketizer_out( po, 0 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
							cfgs++;
						} else if ( ret != Jopus_defines.OPUS_BAD_ARG ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}                         /* M must not be 0 */
					}
					rp.opus_repacketizer_init();
				}
			}
		}

		/*Change in input count code, CBR out*/
		rp.opus_repacketizer_init();
		packet[0] = 0;
		if( rp.opus_repacketizer_cat( packet, 0, 5 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] += 1;
		if( rp.opus_repacketizer_cat( packet, 0, 9 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out( po, max_out );
		if( (i != (4 + 8 + 2) ) || ((po[0] & 3) != 3) || ((po[1] & 63) != 3) || ((po[1] >> 7) != 0) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out_range( 0, 1, po, max_out );
		if( i != 5 || (po[0] & 3) != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out_range( 1, 2, po, max_out );
		if( i != 5 || (po[0] & 3) != 0 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		/*Change in input count code, VBR out*/
		rp.opus_repacketizer_init();
		packet[0] = 1;
		if( rp.opus_repacketizer_cat( packet, 0, 9 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		packet[0] = 0;
		if( rp.opus_repacketizer_cat( packet, 0, 3 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out( po, max_out );
		if( (i != (2 + 8 + 2 + 2)) || ((po[0] & 3) != 3) || ((po[1] & 63) != 3) || ((((int)po[1] & 0xff) >> 7) != 1) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		/*VBR in, VBR out*/
		rp.opus_repacketizer_init();
		packet[0] = 2;
		packet[1] = 4;
		if( rp.opus_repacketizer_cat( packet, 0, 8 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( rp.opus_repacketizer_cat( packet, 0, 8 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out( po, max_out );
		if( (i != (2 + 1 + 1 + 1 + 4 + 2 + 4 + 2)) || ((po[0] & 3) != 3) || ((po[1] & 63) != 4) || ((((int)po[1] & 0xff) >> 7) != 1) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		/*VBR in, CBR out*/
		rp.opus_repacketizer_init();
		packet[0] = 2;
		packet[1] = 4;
		if( rp.opus_repacketizer_cat( packet, 0, 10 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( rp.opus_repacketizer_cat( packet, 0, 10 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		i = rp.opus_repacketizer_out( po, max_out );
		if( (i != (2 + 4 + 4 + 4 + 4)) || ((po[0] & 3) != 3) || ((po[1] & 63) != 4) || ((((int)po[1] & 0xff) >> 7) != 0) ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		/*Count 0 in, VBR out*/
		for( j = 0; j < 32; j++ )
		{
			/* TOC types, test half with stereo */
			packet[0] = (byte)(((j << 1) + (j & 1)) << 2);
			final int maxi = 960 / Jopus.opus_packet_get_samples_per_frame( packet, 0, 8000 );
			int sum = 0;
			int rcnt = 0;
			rp.opus_repacketizer_init();
			for( i = 1; i <= maxi + 2; i++ )
			{
				ret = rp.opus_repacketizer_cat( packet, 0, i );
				if( rcnt < maxi )
				{
					if( ret != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					rcnt++;
					sum += i - 1;
				} else if ( ret != Jopus_defines.OPUS_INVALID_PACKET ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				final int len = sum + (rcnt < 2 ? 1 : rcnt < 3 ? 2 : 2 + rcnt - 1);
				if( rp.opus_repacketizer_out( po, max_out ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( rcnt > 2 && (po[1] & 63) != rcnt ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( rcnt == 2 && (po[0] & 3) != 2 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( rcnt == 1 && (po[0] & 3) != 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( rp.opus_repacketizer_out( po, len ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_packet_unpad( po, len ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_packet_pad( po, 0, len, len + 1 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_packet_pad( po, 0, len + 1, len + 256 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_packet_unpad( po, len + 256 ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_multistream_packet_unpad( po, len, 1 ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_multistream_packet_pad( po, len, len + 1, 1 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_multistream_packet_pad( po, len + 1, len + 256, 1 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( JOpusRepacketizer.opus_multistream_packet_unpad( po, len + 256, 1 ) != len ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( rp.opus_repacketizer_out( po, len - 1 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
				if( len > 1 )
				{
					if( rp.opus_repacketizer_out( po, 1 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					cfgs++;
				}
				if( rp.opus_repacketizer_out( po, 0 ) != Jopus_defines.OPUS_BUFFER_TOO_SMALL ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				cfgs++;
			}
		}

		po[0] = 'O';
		po[1] = 'p';
		if( JOpusRepacketizer.opus_packet_pad( po, 0, 4, 4 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_pad( po, 4, 4, 1 ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_packet_pad( po, 0, 4, 5 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_pad( po, 4, 5, 1 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_packet_pad( po, 0, 0, 5 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_pad( po, 0, 5, 1 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_packet_unpad( po, 0 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_unpad( po, 0, 1 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_packet_unpad( po, 4 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_unpad( po, 4, 1 ) != Jopus_defines.OPUS_INVALID_PACKET ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		po[0] = 0;
		po[1] = 0;
		po[2] = 0;
		if( JOpusRepacketizer.opus_packet_pad( po, 0, 5, 4 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		if( JOpusRepacketizer.opus_multistream_packet_pad( po, 5, 4, 1 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;

		System.out.printf( "    opus_repacketizer_cat ........................ OK.\n" );
		System.out.printf( "    opus_repacketizer_out ........................ OK.\n" );
		System.out.printf( "    opus_repacketizer_out_range .................. OK.\n" );
		System.out.printf( "    opus_packet_pad .............................. OK.\n" );
		System.out.printf( "    opus_packet_unpad ............................ OK.\n" );
		System.out.printf( "    opus_multistream_packet_pad .................. OK.\n" );
		System.out.printf( "    opus_multistream_packet_unpad ................ OK.\n" );

		rp = null;// opus_repacketizer_destroy( rp );
		cfgs++;
		packet = null;// free( packet );
		po = null;// free( po );
		System.out.printf( "                        All repacketizer tests passed\n" );
		System.out.printf( "                            (%7d API invocations)\n", Integer.valueOf( cfgs ) );

		return cfgs;
	}

	private static final int test_malloc_fail()
	{
/* #ifdef MALLOC_FAIL
		JOpusDecoder dec;
		JOpusEncoder enc;
		JOpusRepacketizer rp;
		final byte mapping[256] = {0,1};
		JOpusMSDecoder msdec;
		JOpusMSEncoder msenc;
		int rate,c,app,cfgs,err,useerr;
		int[] ep;
		final mhook orig_malloc;
		cfgs = 0;
#endif */
		System.out.printf( "\n  malloc() failure tests\n" );
		System.out.printf( "  ---------------------------------------------------\n" );
/* #ifdef MALLOC_FAIL
		orig_malloc = __malloc_hook;
		__malloc_hook = malloc_hook;
		ep = ( int * )opus_alloc( sizeof( int ) );
		if( ep != NULL )
		{
			if( ep ) {
				free( ep );
			}
			__malloc_hook = orig_malloc;
#endif */
			System.out.printf( "    opus_decoder_create() ................... SKIPPED.\n" );
			System.out.printf( "    opus_encoder_create() ................... SKIPPED.\n" );
			System.out.printf( "    opus_repacketizer_create() .............. SKIPPED.\n" );
			System.out.printf( "    opus_multistream_decoder_create() ....... SKIPPED.\n" );
			System.out.printf( "    opus_multistream_encoder_create() ....... SKIPPED.\n" );
			System.out.printf( "(Test only supported with GLIBC and without valgrind)\n" );
			return 0;
/* #ifdef MALLOC_FAIL
		}
		for( useerr = 0; useerr < 2; useerr++ )
		{
			ep = useerr ? &err : 0;
			for( rate = 0; rate < 5; rate++ )
			{
				for( c = 1; c < 3; c++ )
				{
					err = 1;
					if( useerr )
					{
						VG_UNDEF( &err,sizeof( err ) );
					}
					dec = opus_decoder_create( opus_rates[rate], c, ep );
					if( dec != NULL || ( useerr && err != Jopus_defines.OPUS_ALLOC_FAIL ) )
					{
						__malloc_hook = orig_malloc;
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					cfgs++;
					msdec = JOpusMSDecoder.opus_multistream_decoder_create( opus_rates[rate], c, 1, c - 1, mapping, ep );
					if( msdec != null || ( useerr && err != Jopus_defines.OPUS_ALLOC_FAIL ) )
					{
						__malloc_hook = orig_malloc;
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					cfgs++;
					for( app = 0; app < 3; app++ )
					{
						if( useerr )
						{
							VG_UNDEF( &err,sizeof( err ) );
						}
						enc = JOpusEncoder.opus_encoder_create( opus_rates[rate], c, opus_apps[app],ep );
						if( enc != NULL || ( useerr && err != Jopus_defines.OPUS_ALLOC_FAIL ) )
						{
							__malloc_hook = orig_malloc;
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						cfgs++;
						msenc = JOpusMSDecoder.opus_multistream_encoder_create( opus_rates[rate], c, 1, c - 1, mapping, opus_apps[app], ep );
						if( msenc != NULL || ( useerr && err != Jopus_defines.OPUS_ALLOC_FAIL ) )
						{
							__malloc_hook = orig_malloc;
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						cfgs++;
					}
				}
			}
		}
		rp = JOpusRepacketizer.opus_repacketizer_create();
		if( rp != null )
		{
			__malloc_hook = orig_malloc;
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		cfgs++;
		__malloc_hook = orig_malloc;
		System.out.printf( "    opus_decoder_create() ........................ OK.\n" );
		System.out.printf( "    opus_encoder_create() ........................ OK.\n" );
		System.out.printf( "    opus_repacketizer_create() ................... OK.\n" );
		System.out.printf( "    opus_multistream_decoder_create() ............ OK.\n" );
		System.out.printf( "    opus_multistream_encoder_create() ............ OK.\n" );
		System.out.printf( "                      All malloc failure tests passed\n" );
		System.out.printf( "                                 (%2d API invocations)\n", cfgs );
		return cfgs;
#endif */
	}

	public static final void main(final String args[])// main( final int _argc, char **_argv )
	{
		final String method_name = "main";
		int total;
		if( args.length > 1 - 1 )// java -1, no path
		{
			System.out.printf("Usage: %s\n", CLASS_NAME );
			System.exit( 1 );
			return;
		}
		Jtest_opus_common.iseed = 0;

		final String oversion = Jcelt.opus_get_version_string();
		if( null == oversion ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		System.out.printf("Testing the %s API deterministically\n", oversion );
		if( Jcelt.opus_strerror( -32768 ) == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( Jcelt.opus_strerror( 32767 ) == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( Jcelt.opus_strerror( 0 ).length() < 1 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		total = 4;

		total += test_dec_api();
		total += test_msdec_api();
		total += test_parse();
		total += test_enc_api();
		total += test_repacketizer_api();
		total += test_malloc_fail();

		System.out.printf("\nAll API tests passed.\nThe libopus API was invoked %d times.\n", Integer.valueOf( total ) );

		System.exit( 0 );
		return;
	}
}