package test;

import celt.Jcelt;
import opus.JOpusDecoder;
import opus.JOpusEncoder;
import opus.JOpusMSDecoder;
import opus.JOpusMSEncoder;
import opus.JOpusRepacketizer;
import opus.Jopus;
import opus.Jopus_defines;
import opus.Jopus_packet_data_aux;
import opus.Jopus_private;

/*  Copyright ( c )  2011 - 2013 Xiph.Org Foundation
   Written by Gregory Maxwell  */
/*
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

    -  Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

    -  Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES ( INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR
   PROFITS;  OR BUSINESS INTERRUPTION )  HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT ( INCLUDING
   NEGLIGENCE OR OTHERWISE )  ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// test_opus_encode.c

final class Jtest_opus_encode {
	private static final String CLASS_NAME = "Jtest_opus_encode";
	private static final int EXIT_FAILURE = 1;

	private static final int MAX_PACKET = 1500;
	private static final int SAMPLES = 48000  *  30;
	private static final int SSAMPLES = SAMPLES / 3;
	private static final int MAX_FRAME_SAMP = 5760;

	// private static final float PI = 3.141592653589793238462643f;
	// #define RAND_SAMPLE(a) (a[fast_rand() % sizeof(a)/sizeof(a[0])])
	private static final int RAND_SAMPLE(final int[] a) {
		return a[ (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % a.length) ];
	}

	private static final void generate_music( final short[] buf, final int len )
	{
		int a1, b1, a2, b2;
		int c1, c2, d1, d2;
		int i, j;
		a1 = b1 = a2 = b2 = 0;
		c1 = c2 = d1 = d2 = 0;
		j = 0;
		/* 60ms silence */
		for( i = 0; i < 2880; i++ ) {
			buf[i << 1] = buf[(i << 1) + 1] = 0;
		}
		for( i = 2880; i < len; i++ )
		{
			int v1, v2;
			v1 = v2 = (((j * ((j >> 12) ^ ((j >> 10 | j >> 12) & 26 & j >> 7))) & 128) + 128) << 15;
			int r = Jtest_opus_common.fast_rand(); v1 += r & 65535; v1 -= r >>> 16;
			r = Jtest_opus_common.fast_rand(); v2 += r & 65535; v2 -= r >>> 16;
			b1 = v1 - a1 + ((b1 * 61 + 32)  >> 6); a1 = v1;
			b2 = v2 - a2 + ((b2 * 61 + 32)  >> 6); a2 = v2;
			c1 = (30 * (c1 + b1 + d1)  + 32)  >> 6; d1 = b1;
			c2 = (30 * (c2 + b2 + d2)  + 32)  >> 6; d2 = b2;
			v1 = (c1 + 128) >> 8;
			v2 = (c2 + 128) >> 8;
			buf[i << 1] = (short)(v1 > 32767 ? 32767 : (v1 < -32768 ? -32768 : v1 ));
			buf[(i << 1) + 1] = (short)(v2 > 32767 ? 32767 : (v2 < -32768? -32768 : v2));
			if( i % 6 == 0 ) {
				j++;
			}
		}
	}

/* #if 0
	static int save_ctr = 0;
	static void int_to_char( opus_uint32 i, unsigned char ch[4] )
	{
		ch[0] = i >> 24;
		ch[1] = ( i >> 16 )  & 0xFF;
		ch[2] = ( i >> 8 )  & 0xFF;
		ch[3] = i & 0xFF;
	}

	static OPUS_INLINE void save_packet( unsigned char *  p, int len, opus_uint32 rng )
	{
		FILE  * fout;
		unsigned char int_field[4];
		char name[256];
		snprintf( name,255,"test_opus_encode.%llu.%d.bit",( unsigned long long ) iseed,save_ctr );
		fprintf( stdout,"writing %d byte packet to %s\n",len,name );
		fout = fopen( name, "wb + " );
		if( fout == NULL ) test_failed();
		int_to_char( len, int_field );
		fwrite( int_field, 1, 4, fout );
		int_to_char( rng, int_field );
		fwrite( int_field, 1, 4, fout );
		fwrite( p, 1, len, fout );
		fclose( fout );
		save_ctr++;
	}
#endif */

	private static final int get_frame_size_enum(final int frame_size, final int sampling_rate)
	{
		final String method_name = "get_frame_size_enum";
		int frame_size_enum = -1;// java warning fix

		if( frame_size == sampling_rate / 400 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_2_5_MS;
		} else if( frame_size == sampling_rate / 200 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_5_MS;
		} else if( frame_size == sampling_rate / 100 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_10_MS;
		} else if( frame_size == sampling_rate / 50 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_20_MS;
		} else if( frame_size == sampling_rate / 25 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_40_MS;
		} else if( frame_size == 3 * sampling_rate / 50 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_60_MS;
		} else if( frame_size == 4 * sampling_rate / 50 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_80_MS;
		} else if( frame_size == 5 * sampling_rate / 50 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_100_MS;
		} else if( frame_size == 6 * sampling_rate / 50 ) {
			frame_size_enum = Jopus_defines.OPUS_FRAMESIZE_120_MS;
		} else {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		return frame_size_enum;// FIXME The local variable frame_size_enum may not have been initialized
	}

	@SuppressWarnings("boxing")
	private static final void test_encode(final JOpusEncoder enc, final int channels, final int frame_size, final JOpusDecoder dec, final String debug_info)
	{
		final String method_name = "test_encode";
		int samp_count = 0;
		final byte[] packet = new byte[MAX_PACKET + 257];

		/* Generate input data */
		short[] inbuf = new short[ SSAMPLES ];
		generate_music( inbuf, SSAMPLES / 2 );

		/* Allocate memory for output data */
		short[] outbuf = new short[ MAX_FRAME_SAMP * 3 ];

		/* Encode data, then decode for sanity check */
		do {
			final int len = enc.opus_encode( inbuf, samp_count * channels, frame_size, packet, MAX_PACKET );
			if( len < 0 || len > MAX_PACKET ) {
				System.err.printf("%s\n", debug_info );
				System.err.printf("opus_encode() returned %d\n", len );
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			final int out_samples = dec.opus_decode( packet, len, outbuf, 0, MAX_FRAME_SAMP, false );
			if( out_samples != frame_size ) {
				System.err.printf("%s\n", debug_info );
				System.err.printf("opus_decode() returned %d\n", out_samples );
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			samp_count += frame_size;
		} while( samp_count < ((SSAMPLES / 2) - MAX_FRAME_SAMP) );

		/* Clean up */
		inbuf = null;
		outbuf = null;
	}

	@SuppressWarnings("boxing")
	private static final void fuzz_encoder_settings(final int num_encoders, final int num_setting_changes)
	{
		final String method_name = "fuzz_encoder_settings";
		JOpusEncoder enc;
		JOpusDecoder dec;
		final int[] err = new int[1];

		/* Parameters to fuzz. Some values are duplicated to increase their probability of being tested. */
		final int sampling_rates[/* 5 */] = { 8000, 12000, 16000, 24000, 48000 };
		final int channels[/* 2 */] = { 1, 2 };
		final int applications[/* 3 */] = { Jopus_defines.OPUS_APPLICATION_AUDIO, Jopus_defines.OPUS_APPLICATION_VOIP, Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY };
		final int bitrates[/* 11 */] = {6000, 12000, 16000, 24000, 32000, 48000, 64000, 96000, 510000, Jopus_defines.OPUS_AUTO, Jopus_defines.OPUS_BITRATE_MAX};
		final int force_channels[/* 4 */] = {Jopus_defines.OPUS_AUTO, Jopus_defines.OPUS_AUTO, 1, 2};
		final int use_vbr[/* 3 */] = {0, 1, 1};
		final int vbr_constraints[/* 3 */] = {0, 1, 1};
		final int complexities[/* 11 */] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
		final int max_bandwidths[/* 6 */] = {Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND,
								Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND,
								Jopus_defines.OPUS_BANDWIDTH_FULLBAND, Jopus_defines.OPUS_BANDWIDTH_FULLBAND};
		final int signals[/* 4 */] = {Jopus_defines.OPUS_AUTO, Jopus_defines.OPUS_AUTO, Jopus_defines.OPUS_SIGNAL_VOICE, Jopus_defines.OPUS_SIGNAL_MUSIC};
		final int inband_fecs[/* 3 */] = {0, 0, 1};
		final int packet_loss_perc[/* 4 */] = {0, 1, 2, 5};
		final int lsb_depths[/* 2 */] = {8, 24};
		final int prediction_disabled[/* 3 */] = {0, 0, 1};
		final int use_dtx[/* 2 */] = {0, 1};
		final int frame_sizes_ms_x2[/* 9 */] = {5, 10, 20, 40, 80, 120, 160, 200, 240};  /* x2 to avoid 2.5 ms */

		for( int i = 0; i < num_encoders; i++ ) {
			final int sampling_rate = RAND_SAMPLE( sampling_rates );
			final int num_channels = RAND_SAMPLE( channels );
			final int application = RAND_SAMPLE( applications );

			dec = JOpusDecoder.opus_decoder_create( sampling_rate, num_channels, err );
			if( err[0] != Jopus_defines.OPUS_OK || dec == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				return;
			}

			enc = JOpusEncoder.opus_encoder_create( sampling_rate, num_channels, application, err );
			if( err[0] != Jopus_defines.OPUS_OK || enc == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				return;
			}

			for( int j = 0; j < num_setting_changes; j++ ) {
				final int bitrate = RAND_SAMPLE( bitrates );
				int force_channel = RAND_SAMPLE( force_channels );
				final int vbr = RAND_SAMPLE( use_vbr );
				final int vbr_constraint = RAND_SAMPLE( vbr_constraints );
				final int complexity = RAND_SAMPLE( complexities );
				final int max_bw = RAND_SAMPLE( max_bandwidths );
				final int sig = RAND_SAMPLE( signals );
				final int inband_fec = RAND_SAMPLE( inband_fecs );
				final int pkt_loss = RAND_SAMPLE( packet_loss_perc );
				final int lsb_depth = RAND_SAMPLE( lsb_depths );
				final int pred_disabled = RAND_SAMPLE( prediction_disabled );
				final int dtx = RAND_SAMPLE( use_dtx );
				final int frame_size_ms_x2 = RAND_SAMPLE( frame_sizes_ms_x2 );
				final int frame_size = frame_size_ms_x2 * sampling_rate / 2000;
				final int frame_size_enum = get_frame_size_enum( frame_size, sampling_rate );
				force_channel = ( force_channel < num_channels ? force_channel : num_channels );

				final String debug_info = String.format(
					"fuzz_encoder_settings: %d kHz, %d ch, application: %d, " +
					"%d bps, force ch: %d, vbr: %d, vbr constraint: %d, complexity: %d, " +
					"max bw: %d, signal: %d, inband fec: %d, pkt loss: %d%%, lsb depth: %d, " +
					"pred disabled: %d, dtx: %d, (%d/2) ms\n",
					sampling_rate / 1000, num_channels, application, bitrate,
					force_channel, vbr, vbr_constraint, complexity, max_bw, sig, inband_fec,
					pkt_loss, lsb_depth, pred_disabled, dtx, frame_size_ms_x2 );

				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, force_channel ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR, vbr != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, vbr_constraint != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, complexity ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_MAX_BANDWIDTH, max_bw ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_SIGNAL, sig ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, inband_fec != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, pkt_loss ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_LSB_DEPTH, lsb_depth ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_PREDICTION_DISABLED, pred_disabled != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_DTX, dtx != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, frame_size_enum ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}// FIXME the returned status must be "unimplemented"

				test_encode( enc, num_channels, frame_size, dec, debug_info );
			}

			enc = null;// opus_encoder_destroy( enc );
			dec = null;// opus_decoder_destroy( dec );
		}
	}

	@SuppressWarnings("boxing")
	private static final int run_test1( final boolean no_fuzz )
	{
		final String method_name = "run_test1";
		final int fsizes[/* 6 */] = { 960 * 3, 960 * 2, 120, 240, 480, 960 };
		final String mstrings[/* 3 */] = { "    LP", "Hybrid", "  MDCT" };
		final char mapping[] = new char[256]; mapping[0] = 0; mapping[1] = 1; mapping[2] = 255;// = {0,1,255};
		final byte db62[] = new byte[36];
		final int[] err = new int[1];
		final Object[] request = new Object[1];// java helper
		final JOpusDecoder dec_err[] = new JOpusDecoder[10];
		final byte packet[] = new byte[MAX_PACKET + 257];

		/* FIXME: encoder api tests, fs! = 48k, mono, VBR */

		System.out.printf("  Encode + Decode tests.\n" );

		JOpusEncoder enc = JOpusEncoder.opus_encoder_create( 48000, 2, Jopus_defines.OPUS_APPLICATION_VOIP, err );
		if( err[0] != Jopus_defines.OPUS_OK || enc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		int i;
		for( i = 0; i < 2; i++ )
		{
			final int[] ret_err = i != 0 ? null : err;
			JOpusMSEncoder MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 2, 2, 0, mapping, Jopus_defines.OPUS_UNIMPLEMENTED, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 0, 1, 0, mapping, Jopus_defines.OPUS_APPLICATION_VOIP, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 44100, 2, 2, 0, mapping, Jopus_defines.OPUS_APPLICATION_VOIP, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 2, 2, 3, mapping, Jopus_defines.OPUS_APPLICATION_VOIP, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 2, - 1, 0, mapping, Jopus_defines.OPUS_APPLICATION_VOIP, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG) || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 256, 2, 0, mapping, Jopus_defines.OPUS_APPLICATION_VOIP, ret_err );
			if( (ret_err != null && ret_err[0] != Jopus_defines.OPUS_BAD_ARG)  || MSenc != null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}

		JOpusMSEncoder MSenc = JOpusMSEncoder.opus_multistream_encoder_create( 8000, 2, 2, 0, mapping, Jopus_defines.OPUS_APPLICATION_AUDIO, err );
		if( err[0] != Jopus_defines.OPUS_OK || MSenc == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return -1;// java: don't need. to suppress "Potential null pointer access: The variable dec may be null at this location"
		}

		/* Some multistream encoder API tests */
		if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_GET_BITRATE, request ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}// FIXME why is i not checked?
		if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_GET_LSB_DEPTH, request ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		i = ((Integer)request[0]).intValue();// java
		if( i < 16 ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		{
			if( MSenc.opus_multistream_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, 1, request ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			final JOpusEncoder tmp_enc = (JOpusEncoder)request[0];// java
			if( tmp_enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LSB_DEPTH, request ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			final int j = ((Integer)request[0]).intValue();// java
			if( i != j ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( MSenc.opus_multistream_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, 2, request ) != Jopus_defines.OPUS_BAD_ARG ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}

		JOpusDecoder dec = JOpusDecoder.opus_decoder_create( 48000, 2, err );
		if( err[0] != Jopus_defines.OPUS_OK || dec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return -1;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}

		JOpusMSDecoder MSdec = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 2, 2, 0, mapping, err );
		if( err[0] != Jopus_defines.OPUS_OK || MSdec == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return -1;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}

		JOpusMSDecoder MSdec_err = JOpusMSDecoder.opus_multistream_decoder_create( 48000, 3, 2, 0, mapping, err );
		if( err[0] != Jopus_defines.OPUS_OK || MSdec_err == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			return -1;// java: don't need. to supress "Potential null pointer access: The variable dec may be null at this location"
		}

		dec_err[0] = new JOpusDecoder( 2 );
		dec_err[0].copyFrom( dec );
		dec_err[1] = JOpusDecoder.opus_decoder_create( 48000, 1, err );
		dec_err[2] = JOpusDecoder.opus_decoder_create( 24000, 2, err );
		dec_err[3] = JOpusDecoder.opus_decoder_create( 24000, 1, err );
		dec_err[4] = JOpusDecoder.opus_decoder_create( 16000, 2, err );
		dec_err[5] = JOpusDecoder.opus_decoder_create( 16000, 1, err );
		dec_err[6] = JOpusDecoder.opus_decoder_create( 12000, 2, err );
		dec_err[7] = JOpusDecoder.opus_decoder_create( 12000, 1, err );
		dec_err[8] = JOpusDecoder.opus_decoder_create( 8000, 2, err );
		dec_err[9] = JOpusDecoder.opus_decoder_create( 8000, 1, err );
		for( i = 0; i < 10; i++ ) {
			if( dec_err[i] == null ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
		}

		{
			/* The opus state structures contain no pointers and can be freely copied */
			final JOpusEncoder enccpy = new JOpusEncoder( 2 );
			enccpy.copyFrom( enc );
			// memset( enc, 255, opus_encoder_get_size( 2 )  );
			enc = null;// opus_encoder_destroy( enc );
			enc = enccpy;
		}

		short[] inbuf = new short[ SAMPLES * 2 ];
		short[] outbuf = new short[ SAMPLES * 2 ];
		short[] out2buf = new short[ MAX_FRAME_SAMP * 3 ];
		if( inbuf == null || outbuf == null || out2buf == null ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		generate_music( inbuf, SAMPLES );

		/* FILE *foo;
		foo = fopen( "foo.sw", "wb + " );
		fwrite( inbuf, 1, SAMPLES * 2 * 2, foo );
		fclose( foo );  */

		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, Jopus_defines.OPUS_AUTO ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, -2 ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( enc.opus_encode( inbuf, 0, 500, packet, MAX_PACKET ) != Jopus_defines.OPUS_BAD_ARG ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		for( int rc = 0; rc < 3; rc++ )
		{
			if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR, rc < 2 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, rc == 1 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, rc == 1 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, rc == 0 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			for( int j = 0; j < 13; j++ )
			{
				final int modes[/* 13 */] = {0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2};
				final int rates[/* 13 */] = {6000, 12000, 48000, 16000, 32000, 48000, 64000, 512000, 13000, 24000, 48000, 64000, 96000};
				final int frame[/* 13 */] = {960 * 2, 960, 480, 960, 960, 960, 480, 960 * 3, 960 * 3, 960, 480, 240, 120};
				final int rate = rates[j] + (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % rates[j]);
				int count = i = 0;
				do {
					final int frame_size = frame[j];
					//if( (Jtest_opus_common.fast_rand() & 255) == 0 )
					{
						if( enc.opus_encoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( dec.opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( (Jtest_opus_common.fast_rand() & 1) != 0 )
						{
							if( dec_err[Jtest_opus_common.fast_rand() & 1].opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
						}
					}
					if( (Jtest_opus_common.fast_rand() & 127) == 0 )
					{
						if( dec_err[Jtest_opus_common.fast_rand() & 1].opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					if( (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 10) == 0 ) {
						final int complex = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 11);
						if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, complex ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					if( (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 50) == 0 ) {
						dec.opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE );
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, rc == 0 ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_private.MODE_SILK_ONLY + modes[j] ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_DTX, (Jtest_opus_common.fast_rand() & 1) != 0 ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, rate ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, rates[j] >= 64000 ? 2 : 1 ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, (count >> 2) % 11 ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, (int)((Jtest_opus_common.fast_rand() & 15) & (((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 15)) ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					int bw = (int)(modes[j] == 0 ? Jopus_defines.OPUS_BANDWIDTH_NARROWBAND + (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 3) :
							modes[j] == 1 ? Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND + ( Jtest_opus_common.fast_rand() & 1 ) :
							Jopus_defines.OPUS_BANDWIDTH_NARROWBAND + (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 5));
					if( modes[j] == 2 && bw == Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND ) {
						bw += 3;
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, bw ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					int len = enc.opus_encode( inbuf, i << 1, frame_size, packet, MAX_PACKET );
					if( len < 0 || len > MAX_PACKET ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					final long enc_final_range = ((Long)request[0]).longValue();// java
					if( (Jtest_opus_common.fast_rand() & 3) == 0 )
					{
						if( JOpusRepacketizer.opus_packet_pad( packet, 0, len, len + 1 ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						len++;
					}
					if( (Jtest_opus_common.fast_rand() & 7) == 0 )
					{
						if( JOpusRepacketizer.opus_packet_pad( packet, 0, len, len + 256 ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						len += 256;
					}
					if( (Jtest_opus_common.fast_rand() & 3) == 0 )
					{
						len = JOpusRepacketizer.opus_packet_unpad( packet, len );
						if( len < 1 ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					int out_samples = dec.opus_decode( packet, len, outbuf, i << 1, MAX_FRAME_SAMP, false );
					if( out_samples != frame_size ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					final long dec_final_range = ((Long)request[0]).longValue();// java
					if( enc_final_range != dec_final_range ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					/* LBRR decode */
					out_samples = dec_err[0].opus_decode( packet, len, out2buf, 0, frame_size, (Jtest_opus_common.fast_rand() & 3) != 0 );
					if( out_samples != frame_size ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					out_samples = dec_err[1].opus_decode( packet, (Jtest_opus_common.fast_rand() & 3) == 0 ? 0 : len, out2buf, 0, MAX_FRAME_SAMP, (Jtest_opus_common.fast_rand() & 7) != 0 );
					if( out_samples < 120 ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					i += frame_size;
					count++;
				} while( i < (SSAMPLES - MAX_FRAME_SAMP) );
				System.out.printf("    Mode %s FB encode %s, %6d bps OK.\n", mstrings[ modes[j] ], rc == 0 ? " VBR" : rc == 1 ? "CVBR" : " CBR", rate );
			}
		}

		if( enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_defines.OPUS_AUTO ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, Jopus_defines.OPUS_AUTO ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, false ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_DTX, false ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		for( int rc = 0; rc < 3; rc++ )
		{
			if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_VBR, rc < 2 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, rc == 1 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, rc == 1 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, rc == 0 ) != Jopus_defines.OPUS_OK ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			for( int j = 0; j < 16; j++ )
			{
				final int modes[/* 16 */] = {0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2};
				final int rates[/* 16 */] = {4000, 12000, 32000, 8000, 16000, 32000, 48000, 88000, 4000, 12000, 32000, 8000, 16000, 32000, 48000, 88000};
				final int frame[/* 16 */] = {160 * 1, 160, 80, 160, 160, 80, 40, 20, 160 * 1, 160, 80, 160, 160, 80, 40, 20};
				if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, (rc == 0 && j == 1) ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( MSenc.opus_multistream_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_private.MODE_SILK_ONLY + modes[j] ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				final int rate = rates[j] + (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % rates[j]);
				Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
				if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_DTX, (Jtest_opus_common.fast_rand() & 1) != 0 ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, rate ) != Jopus_defines.OPUS_OK ) {
					Jtest_opus_common.test_failed( CLASS_NAME, method_name );
				}
				int count = i = 0;
				do {
					int len, out_samples, frame_size;
					if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_GET_PREDICTION_DISABLED, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					final boolean pred = ((Boolean)request[0]).booleanValue();// java
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_PREDICTION_DISABLED, (Jtest_opus_common.fast_rand() & 15) < (pred ? 11 : 4) ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					frame_size = frame[j];
					if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, (count >> 2) % 11 ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					Jtest_opus_common.fast_rand();// java added to get equal rand value. C macros calls arg
					if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, (Jtest_opus_common.fast_rand() & 15) & (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 15) ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( (Jtest_opus_common.fast_rand() & 255) == 0 )
					{
						if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( MSdec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						if( (Jtest_opus_common.fast_rand() & 3) != 0 )
						{
							if( MSdec_err.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
								Jtest_opus_common.test_failed( CLASS_NAME, method_name );
							}
						}
					}
					if( (Jtest_opus_common.fast_rand() & 255) == 0 )
					{
						if( MSdec_err.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					len = MSenc.opus_multistream_encode( inbuf, i << 1, frame_size, packet, MAX_PACKET );
					if( len < 0 || len > MAX_PACKET ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					final long enc_final_range = ((Long)request[0]).longValue();// java
					if( (Jtest_opus_common.fast_rand() & 3) == 0 )
					{
						if( JOpusRepacketizer.opus_multistream_packet_pad( packet, len, len + 1, 2 ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						len++;
					}
					if( (Jtest_opus_common.fast_rand() & 7) == 0 )
					{
						if( JOpusRepacketizer.opus_multistream_packet_pad( packet, len, len + 256, 2 ) != Jopus_defines.OPUS_OK ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
						len += 256;
					}
					if( (Jtest_opus_common.fast_rand() & 3) == 0 )
					{
						len = JOpusRepacketizer.opus_multistream_packet_unpad( packet, len, 2 );
						if( len < 1 ) {
							Jtest_opus_common.test_failed( CLASS_NAME, method_name );
						}
					}
					out_samples = MSdec.opus_multistream_decode( packet, 0, len, out2buf, 0, MAX_FRAME_SAMP, false );
					if( out_samples != frame_size * 6 ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					if( MSdec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request ) != Jopus_defines.OPUS_OK ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					final long dec_final_range = ((Long)request[0]).longValue();// java
					if( enc_final_range != dec_final_range ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					/* LBRR decode */
					final boolean loss = (Jtest_opus_common.fast_rand() & 63) == 0;
					out_samples = MSdec_err.opus_multistream_decode( packet, 0, loss ? 0 : len, out2buf, 0, frame_size * 6, (Jtest_opus_common.fast_rand() & 3) != 0 );
					if( out_samples != ( frame_size * 6 )  ) {
						Jtest_opus_common.test_failed( CLASS_NAME, method_name );
					}
					i += frame_size;
					count++;
				} while( i < ( SSAMPLES/12 - MAX_FRAME_SAMP )  );
				System.out.printf("    Mode %s NB dual - mono MS encode %s, %6d bps OK.\n", mstrings[ modes[j] ], rc == 0 ? " VBR" : rc == 1 ? "CVBR" : " CBR", rate );
			}
		}

		int bitrate_bps = 512000;
		int fsize = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 31);
		int fswitch = 100;

		Jtest_opus_common.debruijn2( 6, db62 );
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();// java helper to replace toc, payload_offset

		int count = i = 0;
		do {
			final int frames[] = new int[48];// packet[ frames[ i ] ]
			final short size[] = new short[48];
			final long dec_final_range2;
			int jj, dec2;
			int len, out_samples;
			final int frame_size = fsizes[ db62[fsize] ];
			final int offset = i % (SAMPLES - MAX_FRAME_SAMP);

			enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate_bps );

			len = enc.opus_encode( inbuf, offset << 1, frame_size, packet, MAX_PACKET );
			if( len < 0 || len > MAX_PACKET ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			count++;

			enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			final long enc_final_range = ((Long)request[0]).longValue();// java

			out_samples = dec.opus_decode( packet, len, outbuf, offset << 1, MAX_FRAME_SAMP, false );
			if( out_samples != frame_size ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			long dec_final_range = ((Long)request[0]).longValue();// java

			/*  compare final range encoder rng values of encoder and decoder  */
			if( dec_final_range != enc_final_range ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			/*  We fuzz the packet, but take care not to only corrupt the payload
			Corrupted headers are tested elsewhere and we need to actually run
			the decoders in order to compare them.  */
			if( Jopus.opus_packet_parse( packet, 0, len/*, &toc*/, frames, size/*, &payload_offset*/, aux ) <= 0 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( (Jtest_opus_common.fast_rand() & 1023) == 0 ) {
				len = 0;
			}
			for( int j = (frames[0]/* - packet*/); j < len; j++ ) {// java -packet don't needed, frames[0] is offset
				for( jj = 0; jj < 8; jj++ ) {
					packet[j] ^= ((( ! no_fuzz ) && ((Jtest_opus_common.fast_rand() & 1023) == 0 )) ? 1 : 0 ) << jj;
				}
			}
			out_samples = dec_err[0].opus_decode( len > 0 ? packet : null, len, out2buf, 0, MAX_FRAME_SAMP, false );
			if( out_samples < 0 || out_samples > MAX_FRAME_SAMP ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}
			if( len > 0 && out_samples != frame_size ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}  /* FIXME use lastframe */

			dec_err[0].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			dec_final_range = ((Long)request[0]).longValue();// java

			/* randomly select one of the decoders to compare with */
			dec2 = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 9) + 1;
			out_samples = dec_err[dec2].opus_decode( len > 0 ? packet : null, len, out2buf, 0, MAX_FRAME_SAMP, false );
			if( out_samples < 0 || out_samples > MAX_FRAME_SAMP ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}  /* FIXME, use factor, lastframe for loss */

			dec_err[dec2].opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
			dec_final_range2 = ((Long)request[0]).longValue();// java
			if( len > 0 && dec_final_range != dec_final_range2 ) {
				Jtest_opus_common.test_failed( CLASS_NAME, method_name );
			}

			fswitch--;
			if( fswitch < 1 )
			{
				fsize = ( fsize + 1 ) % 36;
				final int new_size = fsizes[ db62[fsize] ];
				if( new_size == 960 || new_size == 480 ) {
					fswitch = 2880 / new_size * ((int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 19) + 1);
				} else {
					fswitch = (int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % (2880 / new_size)) + 1;
				}
			}
			bitrate_bps = (((int)(((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 508000) + 4000) + bitrate_bps) >> 1;
			i += frame_size;
		} while( i < SAMPLES * 4 );
		System.out.printf("    All framesize pairs switching encode, %d frames OK.\n", count );

		if( enc.opus_encoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		enc = null;// opus_encoder_destroy( enc );
		if( MSenc.opus_multistream_encoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		MSenc = null;// opus_multistream_encoder_destroy( MSenc );
		if( dec.opus_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		dec = null;// opus_decoder_destroy( dec );
		if( MSdec.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE ) != Jopus_defines.OPUS_OK ) {
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}
		MSdec = null;// opus_multistream_decoder_destroy( MSdec );
		MSdec_err = null;// opus_multistream_decoder_destroy( MSdec_err );
		for( i = 0; i < 10; i++ ) {
			dec_err[i] = null;
		}// opus_decoder_destroy( dec_err[i] );
		inbuf = null;// free( inbuf );
		outbuf = null;// free( outbuf );
		out2buf = null;// free( out2buf );
		return 0;
	}

	private static final void print_usage(final String _argv)
	{
		System.err.printf("Usage: %s [<seed>] [-fuzz <num_encoders> <num_settings_per_encoder>]\n", _argv );
	}

	@SuppressWarnings("boxing")
	public static final void main(final String argv[])// int main( final int _argc, char ** _argv )
	{
		int args = 1 - 1;// java -1, no path
		// char * strtol_str = NULL;// java: don't need
		int num_encoders_to_fuzz = 5;
		int num_setting_changes = 40;

		boolean env_used = false;
		final String env_seed = System.getenv("SEED");
		if( argv.length > 1 - 1 ) {// java -1, no path
			try {
				Jtest_opus_common.iseed = Integer.parseInt( argv[1 - 1] );// java -1, no path // the first input argument might be the seed
				args++;// iseed is a valid number
			} catch( final NumberFormatException ne ) {
				Jtest_opus_common.iseed = 0;
			}
		}
		//if( strtol_str != NULL && strtol_str[0] == '\0' ) // iseed is a valid number
		//	args++;
		if( args == 2 - 1 ) {// java check if iseed is a valid number
		}
		else if( env_seed != null )
		{
			try {
				Jtest_opus_common.iseed = Integer.parseInt( env_seed );
			} catch( final NumberFormatException ne ) {
				Jtest_opus_common.iseed = 0;
			}
			env_used = true;
		} else {
			Jtest_opus_common.iseed = (int)System.currentTimeMillis();
		}
		Jtest_opus_common.Rw = Jtest_opus_common.Rz = Jtest_opus_common.iseed;

		while( args < argv.length )
		{
			if( argv[args].compareTo("-fuzz") == 0 && argv.length == (args + 3) ) {
				try {
					num_encoders_to_fuzz = Integer.parseInt( argv[args + 1] );
				} catch( final NumberFormatException ne ) {
					print_usage( CLASS_NAME );// _argv );
					System.exit( EXIT_FAILURE );
					return;
				}
				try {
					num_setting_changes = Integer.parseInt( argv[args + 2] );
				} catch( final NumberFormatException ne ) {
					print_usage( CLASS_NAME );// _argv );
					System.exit( EXIT_FAILURE );
					return;
				}
				args += 3;
			}
			else {
				print_usage( CLASS_NAME );// _argv );
				System.exit( EXIT_FAILURE );
				return;
			}
		}

		final String oversion = Jcelt.opus_get_version_string();
		if( null == oversion ) {
			Jtest_opus_common.test_failed( CLASS_NAME, "null == oversion" );
		}
		System.err.printf("Testing %s encoder. Random seed: %d (%04X) \n", oversion, Jtest_opus_common.iseed, ((long)Jtest_opus_common.fast_rand() & 0xffffffffL) % 65535 );
		if( env_used ) {
			System.err.printf("  Random seed set from the environment (SEED = %s) .\n", env_seed );
		}

		Jopus_encode_regressions.regression_test();

		/* Setting TEST_OPUS_NOFUZZ tells the tool not to send garbage data
		  into the decoders. This is helpful because garbage data
		  may cause the decoders to clip, which angers CLANG IOC. */
		run_test1( System.getenv("TEST_OPUS_NOFUZZ") != null );

		/* Fuzz encoder settings online */
		if( System.getenv("TEST_OPUS_NOFUZZ") == null ) {
			System.err.printf("Running fuzz_encoder_settings with %d encoder(s) and %d setting change(s) each.\n",
					num_encoders_to_fuzz, num_setting_changes );
			fuzz_encoder_settings( num_encoders_to_fuzz, num_setting_changes );
		}

		System.err.printf("Tests completed successfully.\n" );

		System.exit( 0 );
		return;
	}
}