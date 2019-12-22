package test;

import java.util.Random;

import celt.Jcelt;

/* Copyright ( c ) 2011-2013 Xiph.Org Foundation
Written by Gregory Maxwell */

import opus.JOpusDecoder;
import opus.Jopus;
import opus.Jopus_defines;

final class Jtest_opus_decode {
	private static final String CLASS_NAME = "Jtest_opus_decode";

	private static final int MAX_PACKET = 1500;
	private static final int MAX_FRAME_SAMP = 5760;

	@SuppressWarnings("boxing")
	private static final int test_decoder_code0( final boolean no_fuzz )
	{
		final String method_name = "test_decoder_code0";
		final int fsv[] = { 48000, 24000, 16000, 12000, 8000 };
		final int err[] = new int[1];

		System.out.printf("  Starting %d decoders...\n", 5 * 2 );
		final JOpusDecoder dec[] = new JOpusDecoder[5 * 2];
		for( int t = 0; t < 5 * 2; t++ )
		{
			final int fs = fsv[t >> 1];
			final int c = (t & 1) + 1;
			err[0] = Jopus_defines.OPUS_INTERNAL_ERROR;
			dec[t] = JOpusDecoder.opus_decoder_create( fs, c, err );
			if( err[0] != Jopus_defines.OPUS_OK || dec[t] == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_create failed" );
			}
			System.out.printf(" opus_decoder_create( %5d, %d ) OK. Copy ", fs, c );
			{
				/*The opus state structures contain no pointers and can be freely copied*/
				final JOpusDecoder dec2 = new JOpusDecoder( c );// malloc( JOpusDecoder.opus_decoder_get_size( c ) );
				/* if( dec2 == null ) {
					Jtest_opus_common.test_failed();
				}*/
				dec2.copyFrom( dec[t] );
				dec[t].clear( true );// TODO memset( dec[t], 255, JOpusDecoder.opus_decoder_get_size( c ) );
				dec[t] = null;// opus_decoder_destroy( dec[t] );
				System.out.printf("OK.\n");
				dec[t] = dec2;
			}
		}

		//decsize = JOpusDecoder.opus_decoder_get_size( 1 );
		JOpusDecoder decbak = new JOpusDecoder( 1 );// (JOpusDecoder)malloc( decsize );
		//if( decbak == null ) {
		//	Jtest_opus_common.test_failed( CLASS_NAME, method_name + " JOpusDecoder memory allocation problem" );
		//}
		short[] outbuf_int = new short[(MAX_FRAME_SAMP + 16) * 2];
		for( int i = 0; i < (MAX_FRAME_SAMP + 16) * 2; i++ ) {
			outbuf_int[i] = 32749;
		}
		final int outbuf = 8 * 2;
		byte[] packet = new byte[MAX_PACKET];
		final Object[] request = new Object[1];// java helper
		for( int t = 0; t < 5 * 2; t++ )
		{
			final int factor = 48000 / fsv[t >> 1];
			for( int ifec = 0; ifec < 2; ifec++ )// java changed
			{
				final boolean fec = ifec > 0;// java
				/*Test PLC on a fresh decoder*/
				int out_samples = dec[t].opus_decode( null, 0, outbuf_int, outbuf, 120 / factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor" );
				}
				if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
				}
				int dur = ((Integer)request[0]).intValue();// java
				if( dur != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != 120 / factor");
				}

				/*Test on a size which isn't a multiple of 2.5ms*/
				out_samples = dec[t].opus_decode( null, 0, outbuf_int, outbuf, 120 / factor + 2, fec );
				if( out_samples != Jopus_defines.OPUS_BAD_ARG ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != OPUS_BAD_ARG");
				}

				/*Test null pointer input*/
				out_samples = dec[t].opus_decode( null, -1, outbuf_int, outbuf, 120/factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}
				out_samples = dec[t].opus_decode( null, 1, outbuf_int, outbuf, 120 / factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}
				out_samples = dec[t].opus_decode( null, 10, outbuf_int, outbuf, 120 / factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}
				out_samples = dec[t].opus_decode( null, Jtest_opus_common.fast_rand(), outbuf_int, outbuf, 120 / factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}
				if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != OPUS_OK");
				}
				dur = ((Integer)request[0]).intValue();// java
				if( dur != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != 120 / factor");
				}

				/*Zero lengths*/
				out_samples = dec[t].opus_decode( packet, 0, outbuf_int, outbuf, 120 / factor, fec );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}

				/*Zero buffer*/
				outbuf_int[outbuf + 0] = 32749;
				out_samples = dec[t].opus_decode( packet, 0, outbuf_int, outbuf, 0, fec );
				if( out_samples > 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples > 0");
				}
/* #if !defined(OPUS_BUILD) && (OPUS_GNUC_PREREQ(4, 6) || (defined(__clang_major__) && __clang_major__ >= 3))
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wnonnull"
#endif */
				out_samples = dec[t].opus_decode( packet, 0, outbuf_int, 0, 0, fec );
/* #if !defined(OPUS_BUILD) && (OPUS_GNUC_PREREQ(4, 6) || (defined(__clang_major__) && __clang_major__ >= 3))
#pragma GCC diagnostic pop
#endif */
				if( out_samples > 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples > 0");
				}
				if( outbuf_int[outbuf + 0] != 32749 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " outbuf_int[outbuf + 0] != 32749");
				}

				/*Invalid lengths*/
				out_samples = dec[t].opus_decode( packet, -1, outbuf_int, outbuf, MAX_FRAME_SAMP, fec );
				if( out_samples >= 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples >= 0");
				}
				out_samples = dec[t].opus_decode( packet, Integer.MIN_VALUE, outbuf_int, outbuf, MAX_FRAME_SAMP, fec );
				if( out_samples >= 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples >= 0");
				}
				out_samples = dec[t].opus_decode( packet, -1, outbuf_int, outbuf, -1, fec );
				if( out_samples >= 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples >= 0");
				}

				/*Crazy FEC values*/// java: fec is boolean
				/* out_samples = JOpusDecoder.opus_decode( dec[t], packet, 1, outbuf_int, outbuf, MAX_FRAME_SAMP, fec != 0 ? -1 : 2 );
				if( out_samples >= 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples >= 0");
				}*/

				/*Reset the decoder*/
				if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
				}
			}
		}
		System.out.printf("  dec[all] initial frame PLC OK.\n");

		long dec_final_range1 = 2, dec_final_range2 = 2;// uint32
		/*Count code 0 tests*/
		for( int i = 0; i < 64; i++ )
		{
			final int expected[] = new int [5 * 2];
			packet[0] = (byte)(i << 2);
			packet[1] = (byte)255;
			packet[2] = (byte)255;
			err[0] = JOpusDecoder.opus_packet_get_nb_channels( packet, 0 );
			if( err[0] != (i & 1) + 1 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " err[0] != (i & 1) + 1");
			}

			for( int t = 0; t < 5 * 2; t++ ) {
				expected[t] = dec[t].opus_decoder_get_nb_samples( packet, 1 );
				if( expected[t] > 2880 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " expected[t] > 2880");
				}
			}

			for( int j = 0; j < 256; j++ )
			{
				packet[1] = (byte)j;
				for( int t = 0; t < 5 * 2; t++ )
				{
					final int out_samples = dec[t].opus_decode( packet, 3, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
					if( out_samples != expected[t] ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != expected[t]");
					}
					if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
					}
					final int dur = ((Integer)request[0]).intValue();// java
					if( dur != out_samples ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != out_samples");
					}
					dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
					dec_final_range1 = ((Long)request[0]).longValue();// java
					if( t == 0 ) {
						dec_final_range2 = dec_final_range1;
					} else if( dec_final_range1 != dec_final_range2 ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dec_final_range1 != dec_final_range2");
					}
				}
			}

			for( int t = 0; t < 5 * 2; t++ ) {
				final int factor = 48000 / fsv[t >> 1];
				/* The PLC is run for 6 frames in order to get better PLC coverage. */
				for( int j = 0; j < 6; j++ )
				{
					final int out_samples = dec[t].opus_decode( null, 0, outbuf_int, outbuf, expected[t], false );
					if( out_samples != expected[t] ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != expected[t]");
					}
					if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
					}
					final int dur = ((Integer)request[0]).intValue();// java
					if( dur != out_samples ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != out_samples");
					}
				}

				/* Run the PLC once at 2.5ms, as a simulation of someone trying to
				   do small drift corrections. */
				if( expected[t] != 120 / factor )
				{
					final int out_samples = dec[t].opus_decode( null, 0, outbuf_int, outbuf, 120 / factor, false );
					if( out_samples != 120 / factor ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
					}
					if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
					}
					final int dur = ((Integer)request[0]).intValue();// java
					if( dur != out_samples ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != out_samples");
					}
				}
				final int out_samples = dec[t].opus_decode( packet, 2, outbuf_int, outbuf, expected[t] - 1, false );
				if( out_samples > 0 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples > 0");
				}
			}// for t
		}// for i 0 to 64
		System.out.printf("  dec[all] all 2-byte prefix for length 3 and PLC, all modes (64) OK.\n");
		if( no_fuzz )
		{
			System.out.printf("  Skipping many tests which fuzz the decoder as requested.\n");
			decbak = null;
			for( int t = 0; t < 5 * 2; t++ ) {
				dec[t] = null;// opus_decoder_destroy( dec[t] );
			}
			System.out.printf("  Decoders stopped.\n");

			err[0] = 0;
			for( int i = 0; i < 8 * 2; i++ ) {
				err[0] |= (outbuf_int[i] != 32749) ? 1 : 0;
			}
			for( int i = MAX_FRAME_SAMP * 2; i < (MAX_FRAME_SAMP + 8) * 2; i++ ) {
				err[0] |= (outbuf_int[outbuf + i] != 32749) ? 1 : 0;
			}
			if( 0 != err[0] ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " 0 != err");
			}

			outbuf_int = null;
			packet = null;
			return 0;
		}

		{
			/*We only test a subset of the modes here simply because the longer
	 		  durations end up taking a long time.*/
			final int cmodes[/* 4 */] = { 16, 20, 24, 28 };
			final long cres[/* 4 */] = { 116290185L, 2172123586L, 2172123586L, 2172123586L };// uint32
			final long lres[/* 3 */] = { 3285687739L, 1481572662L, 694350475L };// uint32
			final int lmodes[/* 3 */] = { 0, 4, 8 };
			int mode = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 4);

			packet[0] = (byte)(cmodes[mode] << 3);
			long dec_final_acc = 0;// uint32
			int t = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 10);

			for( int i = 0; i < 65536; i++ )
			{
				final int factor = 48000 / fsv[t >> 1];
				packet[1] = (byte)(i >> 8);
				packet[2] = (byte)(i & 255);
				packet[3] = (byte)255;
				final int out_samples = dec[t].opus_decode( packet, 4, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
				if( out_samples != 120 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 120 / factor");
				}
				dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
				dec_final_range1 = ((Long)request[0]).longValue();// java
				dec_final_acc += dec_final_range1;
			}
			dec_final_acc &= 0xffffffffL;// java truncate to uint32
			if( dec_final_acc != cres[mode] ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dec_final_acc != cres[mode]");
			}
			System.out.printf("  dec[%3d] all 3-byte prefix for length 4, mode %2d OK.\n", t, cmodes[mode] );

			mode = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 3);
			packet[0] = (byte)(lmodes[mode] << 3);
			dec_final_acc = 0;
			t = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 10);
			for( int i = 0; i < 65536; i++ )
			{
				final int factor = 48000 / fsv[t >> 1];
				packet[1] = (byte)(i >> 8);
				packet[2] = (byte)(i & 255);
				packet[3] = (byte)255;
				final int out_samples = dec[t].opus_decode( packet, 4, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
				if( out_samples != 480 / factor ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != 480 / factor");
				}
				dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
				dec_final_range1 = ((Long)request[0]).longValue();// java
				dec_final_acc += dec_final_range1;
			}
			dec_final_acc &= 0xffffffffL;// java truncate to uint32
			if( dec_final_acc != lres[mode] ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dec_final_acc != lres[mode]");
			}
			System.out.printf("  dec[%3d] all 3-byte prefix for length 4, mode %2d OK.\n", t, lmodes[mode] );
		}

		final int skip = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 7);
		for( int i = 0; i < 64; i++ )
		{
			final int expected[] = new int[5 * 2];
			packet[0] = (byte)(i << 2);
			for( int t = 0; t < 5 * 2; t++ ) {
				expected[t] = dec[t].opus_decoder_get_nb_samples( packet, 1 );
			}
			for( int j = 2 + skip; j < 1275; j += 4 )
			{
				for( int jj = 0; jj < j; jj++ ) {
					packet[jj + 1] = (byte)(Jtest_opus_common.fast_rand() & 255);
				}
				for( int t = 0; t < 5 * 2; t++ )
				{
					final int out_samples = dec[t].opus_decode( packet, j + 1, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
					if( out_samples != expected[t] ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != expected[t]");
					}
					dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
					dec_final_range1 = ((Long)request[0]).longValue();// java
					if( t == 0 ) {
						dec_final_range2 = dec_final_range1;
					} else if( dec_final_range1 != dec_final_range2 ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dec_final_range1 != dec_final_range2");
					}
				}
			}
		}
		System.out.printf("  dec[all] random packets, all modes (64), every 8th size from from %d bytes to maximum OK.\n", 2 + skip );

		final byte modes[] = new byte[4096];
		Jtest_opus_common.debruijn2( 64, modes );
		int plen = ((int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 18) + 3) * 8 + skip + 3;
		for( int i = 0; i < 4096; i++ )
		{
			final int expected[] = new int[5 * 2];
			packet[0] = (byte)(modes[i] << 2);
			for( int t = 0; t < 5 * 2; t++ ) {
				expected[t] = dec[t].opus_decoder_get_nb_samples( packet, plen );
			}
			for( int j = 0; j < plen; j++ ) {
				packet[j + 1] = (byte)((Jtest_opus_common.fast_rand() | Jtest_opus_common.fast_rand()) & 255);
			}
			decbak.copyFrom( dec[0] );
			if( decbak.opus_decode( packet, plen + 1, outbuf_int, outbuf, expected[0], true ) != expected[0] ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " decoded != expected[0]");
			}
			decbak.copyFrom( dec[0] );
			if( decbak.opus_decode( null, 0, outbuf_int, outbuf, MAX_FRAME_SAMP, true ) < 20 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " decoded < 0");
			}
			decbak.copyFrom( dec[0] );
			if( decbak.opus_decode( null, 0, outbuf_int, outbuf, MAX_FRAME_SAMP, false ) < 20 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name + " decoded < 0");
			}
			for( int t = 0; t < 5 * 2; t++ )
			{
				final int out_samples = dec[t].opus_decode( packet, plen + 1, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
				if( out_samples != expected[t] ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != expected[t]");
				}
				if( t == 0 ) {
					dec_final_range2 = dec_final_range1;
				} else if( dec_final_range1 != dec_final_range2 ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dec_final_range1 != dec_final_range2");
				}
				if( dec[t].opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " opus_decoder_ctl != OPUS_OK");
				}
				final int dur = ((Integer)request[0]).intValue();// java
				if( dur != out_samples ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " dur != out_samples");
				}
			}
		}
		System.out.printf("  dec[all] random packets, all mode pairs (4096), %d bytes/frame OK.\n", plen + 1 );

		plen = ((int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 18) + 3 ) * 8 + skip + 3;
		int t = new Random().nextInt() & 3;
		for( int i = 0; i < 4096; i++ )
		{
			packet[0] = (byte)(modes[i] << 2);
			final int expected = dec[t].opus_decoder_get_nb_samples( packet, plen );
			for( int count = 0; count < 10; count++ )
			{
				for( int j = 0; j < plen; j++ ) {
					packet[j + 1] = (byte)((Jtest_opus_common.fast_rand() | Jtest_opus_common.fast_rand()) & 255);
				}
				final int out_samples = dec[t].opus_decode( packet, plen + 1, outbuf_int, outbuf, MAX_FRAME_SAMP, false );
				if( out_samples != expected ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != expected");
				}
			}
		}
		System.out.printf("  dec[%3d] random packets, all mode pairs (4096)*10, %d bytes/frame OK.\n", t, plen + 1 );

		{
			final int tmodes[/* 1 */] = { 25 << 2 };
			final int tseeds[/* 1 */] = { 140441 };
			final int tlen[/* 1 */] = { 157 };
			final int tret[/* 1 */] = { 480 };
			t = Jtest_opus_common.fast_rand() & 1;
			for( int i = 0; i < 1; i++ )
			{
				int j;
				packet[0] = (byte)tmodes[i];
				Jtest_opus_common.Rw = Jtest_opus_common.Rz = tseeds[i];
				for( j = 1; j < tlen[i]; j++ ) {
					packet[j] = (byte)(Jtest_opus_common.fast_rand() & 255);
				}
				final int out_samples = dec[t].opus_decode( packet, tlen[i], outbuf_int, outbuf, MAX_FRAME_SAMP, false );
				if( out_samples != tret[i] ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " out_samples != tret[i]");
				}
			}
			System.out.printf("  dec[%3d] pre-selected random packets OK.\n", t );
		}

		decbak = null;
		for( t = 0; t < 5 * 2; t++ ) {
			dec[t] = null;
		}
		System.out.printf("  Decoders stopped.\n");

		err[0] = 0;
		for( int i = 0; i < 8 * 2; i++ ) {
			err[0] |= (outbuf_int[i] != 32749) ? 1 : 0;
		}
		for( int i = MAX_FRAME_SAMP * 2; i < (MAX_FRAME_SAMP + 8) * 2; i++ ) {
			err[0] |= (outbuf_int[outbuf + i] != 32749) ? 1 : 0;
		}
		if( 0 != err[0] ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name + " 0 != err");
		}

		outbuf_int = null;
		packet = null;
		return 0;
	}

// #ifndef DISABLE_FLOAT_API
	private static final void test_soft_clip()
	{
		final String method_name = "test_soft_clip";
		final float x[] = new float[1024];
		final float s[/* 8 */] = { 0, 0, 0, 0, 0, 0, 0, 0 };
		System.out.printf("  Testing opus_pcm_soft_clip... ");
		for( int i = 0; i < 1024; i++ )
		{
			for( int j = 0; j < 1024; j++ )
			{
				x[j] = (j & 255) * (1 / 32.f) - 4.f;
			}
			Jopus.opus_pcm_soft_clip( x, i, 1024 - i, 1, s );
			for( int j = i; j < 1024; j++ )
			{
				if( x[j] > 1.f ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " x[j] > 1.f");
				}
				if( x[j] < -1.f ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " x[j] < -1.f");
				}
			}
		}
		for( int i = 1; i < 9; i++ )
		{
			for( int j = 0; j < 1024; j++ )
			{
				x[j] = (j & 255) * (1 / 32.f) - 4.f;
			}
			Jopus.opus_pcm_soft_clip( x, 0, 1024 / i, i, s );
			for( int j = 0; j < (1024 / i) * i; j++ )
			{
				if( x[j] > 1.f ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " x[j] > 1.f");
				}
				if( x[j] < -1.f ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name + " x[j] < -1.f");
				}
			}
		}
		Jopus.opus_pcm_soft_clip( x, 0, 0, 1, s );
		Jopus.opus_pcm_soft_clip( x, 0, 1, 0, s );
		Jopus.opus_pcm_soft_clip( x, 0, 1, 1, null );
		Jopus.opus_pcm_soft_clip( x, 0, 1, -1, s );
		Jopus.opus_pcm_soft_clip( x, 0, -1, 1, s );
		Jopus.opus_pcm_soft_clip( null, 0, 1, 1, s );
		System.out.printf("OK.\n");
	}
// #endif

	@SuppressWarnings("boxing")
	public static final void main(final String args[])// ( final int _argc, char **_argv )
	{
		if( args.length > 2 - 1 )// java -1, no path
		{
			System.err.printf("Usage: %s [<seed>]\n", CLASS_NAME );
			System.exit( 1 );
			return;
		}

		boolean env_used = false;
		final String env_seed = System.getenv("SEED");
		if( args.length > 1 - 1 ) {
			try {
				Jtest_opus_common.iseed = Integer.parseInt( args[1 - 1] );// java -1, no path
			} catch( final NumberFormatException ne ) {
				Jtest_opus_common.iseed = 0;
			}
		} else if( env_seed != null )
		{
			try {
				Jtest_opus_common.iseed = Integer.parseInt( env_seed );
			} catch( final NumberFormatException ne ) {
				Jtest_opus_common.iseed = 0;
			}
			env_used = true;
		} else {
			// iseed = (opus_uint32)time( NULL ) ^ ( ( (opus_uint32)getpid() & 65535 ) << 16 );
			Jtest_opus_common.iseed = (int)System.currentTimeMillis();
		}
		Jtest_opus_common.Rw = Jtest_opus_common.Rz = Jtest_opus_common.iseed;

		final String oversion = Jcelt.opus_get_version_string();
		if( null == oversion ) {
			Jtest_opus_common.test_failed( CLASS_NAME, "null == oversion");
		}
		System.err.printf("Testing %s decoder. Random seed: %d (%04X)\n", oversion, Jtest_opus_common.iseed, (Jtest_opus_common.fast_rand() & 0x7fffffff) % 65535 );
		if( env_used ) {
			System.err.printf("  Random seed set from the environment (SEED = %s).\n", env_seed );
		}

		/*Setting TEST_OPUS_NOFUZZ tells the tool not to send garbage data
		  into the decoders. This is helpful because garbage data
		  may cause the decoders to clip, which angers CLANG IOC.*/
		test_decoder_code0( System.getenv("TEST_OPUS_NOFUZZ") != null );
//if( ! DISABLE_FLOAT_API ) {
		test_soft_clip();
//}

		System.exit( 0 );
		return;
	}
}