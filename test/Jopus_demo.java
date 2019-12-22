package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

import celt.Jcelt;
import opus.JOpusDecoder;
import opus.JOpusEncoder;
import opus.Jopus;
import opus.Jopus_defines;
import opus.Jopus_private;

/* Copyright (c) 2007-2008 CSIRO
   Copyright (c) 2007-2009 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// opus/opus_demo.c
// java: modified for test using Jrun_vectors
final class Jopus_demo {
	private static final String CLASS_NAME = "Jopus_demo";

	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;

	private static final int MAX_PACKET = 1500;

	private static final void print_usage( final String appName )
	{
		System.err.printf("Usage: %s [-e] <application> <sampling rate (Hz)> <channels (1/2)> <bits per second>  [options] <input> <output>\n", appName );
		System.err.printf("       %s -d <sampling rate (Hz)> <channels (1/2)> [options] <input> <output>\n\n", appName );
		System.err.printf("application: voip | audio | restricted-lowdelay\n" );
		System.err.printf("options:\n" );
		System.err.printf("-e                   : only runs the encoder (output the bit-stream)\n" );
		System.err.printf("-d                   : only runs the decoder (reads the bit-stream as input)\n" );
		System.err.printf("-cbr                 : enable constant bitrate; default: variable bitrate\n" );
		System.err.printf("-cvbr                : enable constrained variable bitrate; default: unconstrained\n" );
		System.err.printf("-delayed-decision    : use look-ahead for speech/music detection (experts only); default: disabled\n" );
		System.err.printf("-bandwidth <NB|MB|WB|SWB|FB> : audio bandwidth (from narrowband to fullband); default: sampling rate\n" );
		System.err.printf("-framesize <2.5|5|10|20|40|60|80|100|120> : frame size in ms; default: 20 \n" );
		System.err.printf("-max_payload <bytes> : maximum payload size in bytes, default: 1024\n" );
		System.err.printf("-complexity <comp>   : complexity, 0 (lowest) ... 10 (highest); default: 10\n" );
		System.err.printf("-inbandfec           : enable SILK inband FEC\n" );
		System.err.printf("-forcemono           : force mono encoding, even for stereo input\n" );
		System.err.printf("-dtx                 : enable SILK DTX\n" );
		System.err.printf("-loss <perc>         : simulate packet loss, in percent (0-100); default: 0\n" );
	}

	private static final void int_to_char(final int i, final byte ch[/* 4 */])
	{
		ch[0] = (byte)(i >>> 24);
		ch[1] = (byte)(i >> 16);// (i >> 16)&0xFF;
		ch[2] = (byte)(i >> 8);// (i >> 8)&0xFF;
		ch[3] = (byte)i;// i&0xFF;
	}

	private static final int char_to_int(final byte ch[/* 4 */])
	{
		return ((int)ch[0] << 24) | (((int)ch[1] & 0xff) << 16)
				| (((int)ch[2] & 0xff) << 8) | ((int)ch[3] & 0xff);
	}

	private static boolean check_encoder_option(final boolean decode_only, final String opt)
	{
		if( decode_only )
		{
			System.err.printf("option %s is only for encoding\n", opt);
			return true;// EXIT_FAILURE;
		}
		return false;
	}

	private static final int silk8_test[][/* 4 */] = {
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960*3, 1},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960*2, 1},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960,   1},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 480,   1},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960*3, 2},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960*2, 2},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 960,   2},
		{Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND, 480,   2}
	};

	private static final int silk12_test[][/* 4 */] = {
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960*3, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960*2, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960,   1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 480,   1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960*3, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960*2, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 960,   2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND, 480,   2}
	};

	private static final int silk16_test[][/* 4 */] = {
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960*3, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960*2, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960,   1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 480,   1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960*3, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960*2, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 960,   2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND, 480,   2}
	};

	private static final int hybrid24_test[][/* 4 */] = {
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 960, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 480, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 960, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 480, 2}
	};

	private static final int hybrid48_test[][/* 4 */] = {
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND, 960, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND, 480, 1},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND, 960, 2},
		{ Jopus_private.MODE_SILK_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND, 480, 2}
	};

	private static final int celt_test[][/* 4 */] = {
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      960, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 960, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      960, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    960, 1},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      480, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 480, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      480, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    480, 1},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      240, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 240, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      240, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    240, 1},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      120, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 120, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      120, 1},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    120, 1},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      960, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 960, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      960, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    960, 2},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      480, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 480, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      480, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    480, 2},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      240, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 240, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      240, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    240, 2},

		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      120, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND, 120, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_WIDEBAND,      120, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_NARROWBAND,    120, 2},

	};

	private static final int celt_hq_test[][/* 4 */] = {
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      960, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      480, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      240, 2},
		{ Jopus_private.MODE_CELT_ONLY, Jopus_defines.OPUS_BANDWIDTH_FULLBAND,      120, 2},
	};

/* #if 0 // This is a hack that replaces the normal encoder/decoder with the multistream version
#define OpusEncoder OpusMSEncoder
#define OpusDecoder OpusMSDecoder
#define opus_encode opus_multistream_encode
#define opus_decode opus_multistream_decode
#define opus_encoder_ctl opus_multistream_encoder_ctl
#define opus_decoder_ctl opus_multistream_decoder_ctl
#define opus_encoder_create ms_opus_encoder_create
#define opus_decoder_create ms_opus_decoder_create
#define opus_encoder_destroy opus_multistream_encoder_destroy
#define opus_decoder_destroy opus_multistream_decoder_destroy

	static OpusEncoder *ms_opus_encoder_create(opus_int32 Fs, int channels, int application, int *error)
	{
		int streams, coupled_streams;
		unsigned char mapping[256];
		return (OpusEncoder *)opus_multistream_surround_encoder_create(Fs, channels, 1, &streams, &coupled_streams, mapping, application, error);
	}
	static OpusDecoder *ms_opus_decoder_create(opus_int32 Fs, int channels, int *error)
	{
		int streams;
		int coupled_streams;
		unsigned char mapping[256]={0,1};
		streams = 1;
		coupled_streams = channels==2;
		return (OpusDecoder *)opus_multistream_decoder_create(Fs, channels, streams, coupled_streams, mapping, error);
	}
#endif */

	@SuppressWarnings({ "boxing", "null" })
	public static final int maininternal(final String argv[])
	{
		if( argv.length < 5 - 1 )// java
		{
			print_usage( CLASS_NAME );
			return EXIT_FAILURE;
		}

		System.err.printf("%s\n", Jcelt.opus_get_version_string() );

		boolean encode_only = false, decode_only = false;
		int args = 1 - 1;// java
		if( argv[args].compareTo("-e") == 0 )
		{
			encode_only = true;
			args++;
		} else if( argv[args].compareTo("-d") == 0 )
		{
			decode_only = true;
			args++;
		}
		if( ! decode_only && argv.length < 7 - 1 )// java
		{
			print_usage( CLASS_NAME );
			return EXIT_FAILURE;
		}

		int application = Jopus_defines.OPUS_APPLICATION_AUDIO;
		if( ! decode_only )
		{
			if( argv[args].compareTo("voip") == 0 ) {
				application = Jopus_defines.OPUS_APPLICATION_VOIP;
			} else if( argv[args].compareTo("restricted-lowdelay") == 0 ) {
				application = Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY;
			} else if( argv[args].compareTo("audio") != 0 ) {
				System.err.printf("unknown application: %s\n", argv[args] );
				print_usage( CLASS_NAME );
				return EXIT_FAILURE;
			}
			args++;
		}
		final int sampling_rate = Integer.parseInt( argv[args] );
		args++;

		if( sampling_rate != 8000 && sampling_rate != 12000
			&& sampling_rate != 16000 && sampling_rate != 24000
			&& sampling_rate != 48000)
		{
			System.err.printf("Supported sampling rates are 8000, 12000, 16000, 24000 and 48000.\n");
			return EXIT_FAILURE;
		}
		int frame_size = sampling_rate / 50;

		final int channels = Integer.parseInt( argv[args] );
		args++;

		if( channels < 1 || channels > 2 )
		{
			System.err.printf("Opus_demo supports only 1 or 2 channels.\n");
			return EXIT_FAILURE;
		}

		int bitrate_bps = 0;
		if( ! decode_only )
		{
			bitrate_bps = Integer.parseInt( argv[args] );
			args++;
		}

		/* defaults: */
		boolean use_vbr = true;
		boolean random_fec = false, random_framesize = false, use_dtx = false, delayed_decision = false;
		int bandwidth = Jopus_defines.OPUS_AUTO;
		int complexity = 10;
		int max_payload_bytes = MAX_PACKET;
		int forcechannels = Jopus_defines.OPUS_AUTO;
		int use_inbandfec = 0;
		int packet_loss_perc = 0;
		int sweep_bps = 0, sweep_max = 0, sweep_min = 0;
		int variable_duration = Jopus_defines.OPUS_FRAMESIZE_ARG;
		int nb_modes_in_list = 0;
		boolean cvbr = false;
		int mode_list[][] = null;
		while( args < argv.length - 2 ) {
			/* process command line options */
			if( argv[ args ].compareTo( "-cbr" ) == 0 ) {
				if( check_encoder_option( decode_only, "-cbr") ) {
					return EXIT_FAILURE;
				}
				use_vbr = false;
				args++;
			} else if( argv[ args ].compareTo( "-bandwidth" ) == 0 ) {
				if( check_encoder_option( decode_only, "-bandwidth") ) {
					return EXIT_FAILURE;
				}
				if( argv[ args + 1 ].compareTo("NB") == 0 ) {
					bandwidth = Jopus_defines.OPUS_BANDWIDTH_NARROWBAND;
				} else if( argv[ args + 1 ].compareTo("MB") == 0 ) {
					bandwidth = Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND;
				} else if( argv[ args + 1 ].compareTo("WB") == 0 ) {
					bandwidth = Jopus_defines.OPUS_BANDWIDTH_WIDEBAND;
				} else if( argv[ args + 1 ].compareTo("SWB") == 0 ) {
					bandwidth = Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND;
				} else if( argv[ args + 1 ].compareTo("FB") == 0 ) {
					bandwidth = Jopus_defines.OPUS_BANDWIDTH_FULLBAND;
				} else {
					System.err.printf("Unknown bandwidth %s. Supported are NB, MB, WB, SWB, FB.\n", argv[ args + 1 ]);
					return EXIT_FAILURE;
				}
				args += 2;
			} else if( argv[ args ].compareTo( "-framesize" ) == 0 ) {
				if( check_encoder_option( decode_only, "-framesize") ) {
					return EXIT_FAILURE;
				}
				if( argv[ args + 1 ].compareTo("2.5") == 0 ) {
					frame_size = sampling_rate/400;
				} else if( argv[ args + 1 ].compareTo("5") == 0 ) {
					frame_size = sampling_rate/200;
				} else if( argv[ args + 1 ].compareTo("10") == 0 ) {
					frame_size = sampling_rate/100;
				} else if( argv[ args + 1 ].compareTo("20") == 0 ) {
					frame_size = sampling_rate/50;
				} else if( argv[ args + 1 ].compareTo("40") == 0 ) {
					frame_size = sampling_rate/25;
				} else if( argv[ args + 1 ].compareTo("60") == 0 ) {
					frame_size = 3*sampling_rate/50;
				} else if( argv[ args + 1 ].compareTo("80") == 0 ) {
					frame_size = 4*sampling_rate/50;
				} else if( argv[ args + 1 ].compareTo("100") == 0 ) {
					frame_size = 5*sampling_rate/50;
				} else if( argv[ args + 1 ].compareTo("120") == 0 ) {
					frame_size = 6*sampling_rate/50;
				} else {
					System.err.printf("Unsupported frame size: %s ms. Supported are 2.5, 5, 10, 20, 40, 60, 80, 100, 120.\n", argv[ args + 1 ]);
					return EXIT_FAILURE;
				}
				args += 2;
			} else if( argv[ args ].compareTo( "-max_payload" ) == 0 ) {
				if( check_encoder_option( decode_only, "-max_payload") ) {
					return EXIT_FAILURE;
				}
				max_payload_bytes = Integer.parseInt( argv[ args + 1 ] );
				args += 2;
			} else if( argv[ args ].compareTo( "-complexity" ) == 0 ) {
				if( check_encoder_option( decode_only, "-complexity") ) {
					return EXIT_FAILURE;
				}
				complexity = Integer.parseInt( argv[ args + 1 ] );
				args += 2;
			} else if( argv[ args ].compareTo( "-inbandfec" ) == 0 ) {
				use_inbandfec = 1;
				args++;
			} else if( argv[ args ].compareTo( "-forcemono" ) == 0 ) {
				if( check_encoder_option( decode_only, "-forcemono") ) {
					return EXIT_FAILURE;
				}
				forcechannels = 1;
				args++;
			} else if( argv[ args ].compareTo( "-cvbr" ) == 0 ) {
				if( check_encoder_option( decode_only, "-cvbr") ) {
					return EXIT_FAILURE;
				}
				cvbr = true;
				args++;
			} else if( argv[ args ].compareTo( "-delayed-decision" ) == 0 ) {
				if( check_encoder_option( decode_only, "-delayed-decision") ) {
					return EXIT_FAILURE;
				}
				delayed_decision = true;
				args++;
			} else if( argv[ args ].compareTo( "-dtx") == 0 ) {
				if( check_encoder_option( decode_only, "-dtx") ) {
					return EXIT_FAILURE;
				}
				use_dtx = true;
				args++;
			} else if( argv[ args ].compareTo( "-loss" ) == 0 ) {
				packet_loss_perc = Integer.parseInt( argv[ args + 1 ] );
				args += 2;
			} else if( argv[ args ].compareTo( "-sweep" ) == 0 ) {
				if( check_encoder_option(decode_only, "-sweep") ) {
					return EXIT_FAILURE;
				}
				sweep_bps = Integer.parseInt( argv[ args + 1 ] );
				args += 2;
			} else if( argv[ args ].compareTo( "-random_framesize" ) == 0 ) {
				if( check_encoder_option( decode_only, "-random_framesize") ) {
					return EXIT_FAILURE;
				}
				random_framesize = true;
				args++;
			} else if( argv[ args ].compareTo( "-sweep_max" ) == 0 ) {
				if( check_encoder_option(decode_only, "-sweep_max") ) {
					return EXIT_FAILURE;
				}
				sweep_max = Integer.parseInt( argv[ args + 1 ] );
				args += 2;
			} else if( argv[ args ].compareTo( "-random_fec" ) == 0 ) {
				if( check_encoder_option( decode_only, "-random_fec") ) {
					return EXIT_FAILURE;
				}
				random_fec = true;
				args++;
			} else if( argv[ args ].compareTo( "-silk8k_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-silk8k_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = silk8_test;
				nb_modes_in_list = 8;
				args++;
			} else if( argv[ args ].compareTo( "-silk12k_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-silk12k_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = silk12_test;
				nb_modes_in_list = 8;
				args++;
			} else if( argv[ args ].compareTo( "-silk16k_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-silk16k_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = silk16_test;
				nb_modes_in_list = 8;
				args++;
			} else if( argv[ args ].compareTo( "-hybrid24k_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-hybrid24k_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = hybrid24_test;
				nb_modes_in_list = 4;
				args++;
			} else if( argv[ args ].compareTo( "-hybrid48k_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-hybrid48k_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = hybrid48_test;
				nb_modes_in_list = 4;
				args++;
			} else if( argv[ args ].compareTo( "-celt_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-celt_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = celt_test;
				nb_modes_in_list = 32;
				args++;
			} else if( argv[ args ].compareTo( "-celt_hq_test" ) == 0 ) {
				if( check_encoder_option( decode_only, "-celt_hq_test") ) {
					return EXIT_FAILURE;
				}
				mode_list = celt_hq_test;
				nb_modes_in_list = 4;
				args++;
			} else {
				System.err.printf("Error: unrecognized setting: %s\n\n", argv[ args ] );
				print_usage( CLASS_NAME );
				return EXIT_FAILURE;
			}
		}

		if( 0 != sweep_max ) {
			sweep_min = bitrate_bps;
		}

		if( max_payload_bytes < 0 || max_payload_bytes > MAX_PACKET )
		{
			System.err.printf("max_payload_bytes must be between 0 and %d\n", MAX_PACKET);
			return EXIT_FAILURE;
		}

		final String inFile = argv[argv.length - 2];
		RandomAccessFile fin = null;
		RandomAccessFile fout = null;
		try {
			fin = new RandomAccessFile( inFile, "r" );// fin = fopen(inFile, "rb");
			/*if( null == fin )
			{
				System.err.printf("Could not open input file %s\n", argv[argv.length - (2 - 1)]);// java
				System.exit( EXIT_FAILURE );
				return;
			}*/
			int mode_switch_time = 48000;
			if( null != mode_list )
			{
				// fseek( fin, 0, SEEK_END );
				//int size = ftell( fin );
				final long size = fin.length();
				System.err.printf("File size is %d bytes\n", size );
				// fseek( fin, 0, SEEK_SET );// java don't need
				mode_switch_time = (int)(size / (Short.SIZE >> 3)/*sizeof(short)*/ / channels / nb_modes_in_list);
				System.err.printf("Switching mode every %d samples\n", mode_switch_time );
			}

			final String outFile = argv[argv.length - 1];
			new File( outFile ).delete();// java to remove existing file
			fout = new RandomAccessFile( outFile, "rw" );// fopen( outFile, "wb+" );
			/*if( null == fout )
			{
				System.err.printf("Could not open output file %s\n", argv[argv.length - (1 - 1)]);// java
				System.exit( EXIT_FAILURE );
				return;
			}*/

			final int[] err = new int[1];
			final Object request[] = new Object[1];// java to get data back
			int skip = 0;
			JOpusEncoder enc = null;
			JOpusDecoder dec = null;
			if( ! decode_only )
			{
				enc = JOpusEncoder.opus_encoder_create( sampling_rate, channels, application, err );
				if( err[0] != Jopus_defines.OPUS_OK )
				{
					System.err.printf("Cannot create encoder: %s\n", Jcelt.opus_strerror( err[0] ) );
					return EXIT_FAILURE;
				}
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate_bps );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, bandwidth );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR, use_vbr );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, cvbr );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, complexity );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, use_inbandfec != 0 );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, forcechannels );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_DTX, use_dtx );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, packet_loss_perc );

				enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );// ( &skip ) );
				skip = ((Integer)request[0]).intValue();// java
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_LSB_DEPTH, 16 );
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, variable_duration );
			}
			if( ! encode_only )
			{
				dec = JOpusDecoder.opus_decoder_create( sampling_rate, channels, err );
				if( err[0] != Jopus_defines.OPUS_OK )
				{
					System.err.printf("Cannot create decoder: %s\n", Jcelt.opus_strerror( err[0] ) );
					return EXIT_FAILURE;
				}
			}

			String bandwidth_string;
			switch( bandwidth )
			{
			case Jopus_defines.OPUS_BANDWIDTH_NARROWBAND:
				bandwidth_string = "narrowband";
				break;
			case Jopus_defines.OPUS_BANDWIDTH_MEDIUMBAND:
				bandwidth_string = "mediumband";
				break;
			case Jopus_defines.OPUS_BANDWIDTH_WIDEBAND:
				bandwidth_string = "wideband";
				break;
			case Jopus_defines.OPUS_BANDWIDTH_SUPERWIDEBAND:
				bandwidth_string = "superwideband";
				break;
			case Jopus_defines.OPUS_BANDWIDTH_FULLBAND:
				bandwidth_string = "fullband";
				break;
			case Jopus_defines.OPUS_AUTO:
				bandwidth_string = "auto bandwidth";
				break;
			default:
				bandwidth_string = "unknown";
				break;
			}

			if( decode_only ) {
				System.err.printf("Decoding with %d Hz output (%d channels)\n", sampling_rate, channels );
			} else {
				System.err.printf("Encoding %d Hz input at %.3f kb/s in %s with %d-sample frames.\n",
							sampling_rate, bitrate_bps * 0.001,
							bandwidth_string, frame_size );
			}

			final int max_frame_size = 48000 * 2;
			final short[] in = new short[ max_frame_size * channels ];
			final short[] out = new short[ max_frame_size * channels ];
			/* We need to allocate for 16-bit PCM data, but we store it as unsigned char. */
			byte[] fbytes = new byte[ max_frame_size * channels * (Short.SIZE / 8)/*sizeof(short)*/ ];
			final byte data[][] = new byte[2][];// { null, null }
			data[0] = new byte[ max_payload_bytes ];
			if( 0 != use_inbandfec ) {
				data[1] = new byte[ max_payload_bytes ];
			}
			if( delayed_decision )
			{
				if( frame_size == sampling_rate / 400 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_2_5_MS;
				} else if( frame_size == sampling_rate / 200 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_5_MS;
				} else if( frame_size == sampling_rate / 100 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_10_MS;
				} else if( frame_size == sampling_rate / 50 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_20_MS;
				} else if( frame_size == sampling_rate / 25 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_40_MS;
				} else if( frame_size == 3 * sampling_rate / 50 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_60_MS;
				} else if( frame_size == 4 * sampling_rate / 50 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_80_MS;
				} else if( frame_size == 5 * sampling_rate / 50 ) {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_100_MS;
				} else {
					variable_duration = Jopus_defines.OPUS_FRAMESIZE_120_MS;
				}
				enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, variable_duration );
				frame_size = 2 * 48000;
			}

			final Random rand = new Random();// java
			final byte ch[] = new byte[4];// java moved up
			final byte int_field[] = new byte[4];// java moved up
			final int len[] = new int[2];
			double bits = 0.0, bits_max = 0.0, bits_act = 0.0, bits2 = 0.0, tot_samples = 0.0;
			boolean lost_prev = true;
			boolean delayed_celt = false, stop = false, lost = false;
			int newsize = 0;
			int curr_read = 0, curr_mode = 0, curr_mode_count = 0;
			int nb_encoded = 0;
			int remaining = 0;
			int toggle = 0;
			final long enc_final_range[] = new long[2];// uint32
			int count = 0, count_act = 0;
			long tot_in = 0, tot_out = 0;
			while( ! stop )
			{
				if( delayed_celt )
				{
					frame_size = newsize;
					delayed_celt = false;
				} else if( random_framesize && rand.nextInt( 0x8000 ) % 20 == 0 )
				{
					newsize = rand.nextInt( 0x8000 ) % 6;
					switch( newsize )
					{
					case 0: newsize = sampling_rate / 400; break;
					case 1: newsize = sampling_rate / 200; break;
					case 2: newsize = sampling_rate / 100; break;
					case 3: newsize = sampling_rate / 50; break;
					case 4: newsize = sampling_rate / 25; break;
					case 5: newsize = 3 * sampling_rate / 50; break;
					}
					while( newsize < sampling_rate / 25 && bitrate_bps - ( sweep_bps >= 0 ? sweep_bps : -sweep_bps ) <= 3 * 12 * sampling_rate / newsize ) {
						newsize <<= 1;
					}
					if( newsize < sampling_rate / 100 && frame_size >= sampling_rate / 100 )
					{
						enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, Jopus_private.MODE_CELT_ONLY );
						delayed_celt = true;
					} else {
						frame_size = newsize;
					}
				}
				if( random_fec && rand.nextInt( 0x8000 ) % 30 == 0 )
				{
					enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_INBAND_FEC, rand.nextInt( 0x8000 ) % 4 == 0 );
				}
				if( decode_only )
				{
					int num_read = fin.read( ch, 0, 4 );
					if( num_read != 4 ) {
						break;
					}
					len[toggle] = char_to_int( ch );
					if( len[toggle] > max_payload_bytes || len[toggle] < 0 )
					{
						System.err.printf("Invalid payload length: %d\n", len[toggle] );
						break;
					}
					num_read = fin.read( ch, 0, 4 );
					if( num_read != 4 ) {
						break;
					}
					enc_final_range[toggle] = ((long)char_to_int( ch )) & 0xffffffff;// java
					num_read = fin.read( data[toggle], 0, len[toggle] );// fread( data[toggle], 1, len[toggle], fin );
					if( num_read != len[toggle] )
					{
						System.err.printf("Ran out of input, expecting %d bytes got %d\n", len[toggle], num_read );
						break;
					}
				} else {
					if( mode_list != null )
					{
						enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BANDWIDTH, mode_list[curr_mode][1] );
						enc.opus_encoder_ctl( Jopus_private.OPUS_SET_FORCE_MODE, mode_list[curr_mode][0] );
						enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_FORCE_CHANNELS, mode_list[curr_mode][3] );
						frame_size = mode_list[curr_mode][2];
					}
					final int num_read = fin.read( fbytes, 0, (Short.SIZE / 8) /*sizeof(short)*/ * channels * (frame_size - remaining) );
					curr_read = num_read;
					tot_in += curr_read;
					for( int i = 0, ie = curr_read * channels; i < ie; i++ )
					{
						final int i2 = i << 1;// java
						int s = (((int)fbytes[ i2 + 1 ]) << 8) | ((int)fbytes[ i2 ] & 0xff);
						s = (s ^ 0x8000) - 0x8000;// ((s & 0xFFFF) ^ 0x8000) - 0x8000;
						in[i + remaining * channels] = (short)s;
					}
					if( curr_read + remaining < frame_size )
					{
						for( int i = (curr_read + remaining) * channels, ie = frame_size * channels; i < ie; i++ ) {
							in[i] = 0;
						}
						if( encode_only || decode_only ) {
							stop = true;
						}
					}
					len[toggle] = enc.opus_encode( in, 0, frame_size, data[toggle], max_payload_bytes );
					nb_encoded = Jopus.opus_packet_get_samples_per_frame( data[toggle], 0, sampling_rate ) *
									JOpusDecoder.opus_packet_get_nb_frames( data[toggle], 0, len[toggle] );
					remaining = frame_size - nb_encoded;
					for( int i = 0, ie = remaining * channels, k = nb_encoded * channels; i < ie; i++ ) {
						in[i] = in[k++];// java
					}
					if( sweep_bps != 0 )
					{
						bitrate_bps += sweep_bps;
						if( 0 != sweep_max )
						{
							if( bitrate_bps > sweep_max ) {
								sweep_bps = -sweep_bps;
							} else if( bitrate_bps < sweep_min ) {
								sweep_bps = -sweep_bps;
							}
						}
						/* safety */
						if( bitrate_bps < 1000 ) {
							bitrate_bps = 1000;
						}
						enc.opus_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate_bps );
					}
					enc.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );// ( &enc_final_range[toggle] ) );
					enc_final_range[toggle] = ((long)((Long)request[0]).longValue()) & 0xffffffff;// java
					if( len[toggle] < 0 )
					{
						System.err.printf("opus_encode() returned %d\n", len[toggle] );
						return EXIT_FAILURE;
					}
					curr_mode_count += frame_size;
					if( curr_mode_count > mode_switch_time && curr_mode < nb_modes_in_list - 1 )
					{
						curr_mode++;
						curr_mode_count = 0;
					}
				}

/* #if 0 // This is for testing the padding code, do not enable by default
	if (len[toggle]<1275)
	{
		int new_len = len[toggle]+rand()%(max_payload_bytes-len[toggle]);
		if ((err = opus_packet_pad(data[toggle], len[toggle], new_len)) != OPUS_OK)
		{
			fprintf(stderr, "padding failed: %s\n", opus_strerror(err));
			return EXIT_FAILURE;
		}
		len[toggle] = new_len;
	}
#endif */
				if( encode_only )
				{
					int_to_char( len[toggle], int_field) ;
					fout.write( int_field );/* if( fwrite(int_field, 1, 4, fout) != 4 ) {
						System.err.printf("Error writing.\n");
						System.exit( EXIT_FAILURE );
						return;
					}*/
					int_to_char( (int)enc_final_range[toggle], int_field );
					fout.write( int_field );/* if( fwrite(int_field, 1, 4, fout) != 4 ) {
						System.err.printf("Error writing.\n");
						System.exit( EXIT_FAILURE );
						return;
					}*/
					fout.write( data[toggle], 0, len[toggle] ); /* if( fwrite(data[toggle], 1, len[toggle], fout) != (unsigned)len[toggle] ) {
						System.err.printf("Error writing.\n");
						System.exit( EXIT_FAILURE );
						return;
					}*/
					tot_samples += nb_encoded;
				} else {
					int output_samples;
					lost = len[toggle] == 0 || (packet_loss_perc > 0 && rand.nextInt( 0x8000 ) % 100 < packet_loss_perc );
					if( lost ) {
						dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request );// ( &output_samples ) );
						output_samples = ((Integer)request[0]).intValue();// java
					} else {
						output_samples = max_frame_size;
					}
					if( count >= use_inbandfec ) {
						/* delay by one packet when using in-band FEC */
						if( 0 != use_inbandfec  ) {
							if( lost_prev ) {
								/* attempt to decode with in-band FEC from next packet */
								dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_LAST_PACKET_DURATION, request );// ( &output_samples ) );
								output_samples = ((Integer)request[0]).intValue();// java
								output_samples = dec.opus_decode( lost ? null : data[toggle], len[toggle], out, 0, output_samples, true );
							} else {
								/* regular decode */
								output_samples = max_frame_size;
								output_samples = dec.opus_decode( data[1 - toggle], len[1 - toggle], out, 0, output_samples, false );
							}
						} else {
							output_samples = dec.opus_decode( lost ? null : data[toggle], len[toggle], out, 0, output_samples, false );
						}
						if( output_samples > 0 )
						{
							if( ! decode_only && tot_out + (long)output_samples > tot_in )
							{
								stop = true;
								output_samples = (int)(tot_in - tot_out);
							}
							if( output_samples > skip ) {
								int outoffset = skip * channels;// java
								final int data_count = (output_samples - skip) * channels * (Short.SIZE / 8);// java
								for( int i = 0; i < data_count; )
								{
									final short s = out[ outoffset++ ];
									fbytes[i++] = (byte)s;// s & 0xFF;
									fbytes[i++] = (byte)(s >> 8);// (s >> 8) & 0xFF;
								}
								fout.write( fbytes, 0, /*sizeof(short) * */ data_count );
								/* if( fwrite( fbytes, sizeof(short)*channels, output_samples - skip, fout) != (unsigned)(output_samples - skip) ) {
									System.err.printf("Error writing.\n");
									System.exit( EXIT_FAILURE );
									return;
								}*/
								tot_out += output_samples - skip;
							}
							if( output_samples < skip ) {
								skip -= output_samples;
							} else {
								skip = 0;
							}
						} else {
							System.err.printf("error decoding frame: %s\n", Jcelt.opus_strerror( output_samples ) );
						}
						tot_samples += output_samples;
					}
				}

				long dec_final_range = 0;// uint32, = 0 to avoid "The local variable dec_final_range may not have been initialized"
				if( ! encode_only ) {
					dec.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );// ( &dec_final_range ) );
					dec_final_range = ((Long)request[0]).longValue();// java
				}
				/* compare final range encoder rng values of encoder and decoder */
				if( enc_final_range[toggle ^ use_inbandfec] != 0 && ! encode_only
						&& ! lost && ! lost_prev
						&& dec_final_range != enc_final_range[toggle ^ use_inbandfec] ) {
					System.err.printf("Error: Range coder state mismatch between encoder and decoder in frame %d: 0x%8x vs 0x%8x\n",
									count,
									enc_final_range[toggle ^ use_inbandfec],
									dec_final_range);
					return EXIT_FAILURE;
				}

				lost_prev = lost;
				if( count >= use_inbandfec ) {
					/* count bits */
					final double len8 = (double)(len[toggle] << 3);// java
					bits += len8;
					bits_max = ( len8 > bits_max ) ? len8 : bits_max;
					bits2 += len8 * len8;
					if( ! decode_only )
					{
						final int data_size = frame_size * channels;// java
						double nrg = 0.0;
						for( int k = 0; k < data_size; k++ ) {
							double v = (double)in[ k ];// java
							v *= v;
							nrg += v;
						}
						nrg /= (double)data_size;
						if( nrg > 1e5 ) {
							bits_act += len8;
							count_act++;
						}
					}
				}
				count++;
				toggle = (toggle + use_inbandfec) & 1;
			}// while( ! stop )

			if( decode_only && count > 0 ) {
				frame_size = (int)(tot_samples / count);
			}
			count -= use_inbandfec;
			if( tot_samples >= 1 && count > 0 && 0 != frame_size )
			{
				/* Print out bitrate statistics */
				System.err.printf("average bitrate:             %7.3f kb/s\n", 1e-3 * bits * sampling_rate / tot_samples );
				System.err.printf("maximum bitrate:             %7.3f kb/s\n", 1e-3 * bits_max * sampling_rate / frame_size);
				if( ! decode_only ) {
					System.err.printf("active bitrate:              %7.3f kb/s\n", 1e-3 * bits_act * sampling_rate / (1e-15 + frame_size * (double)count_act) );
				}
				double var = bits2 / count - bits * bits / (count * (double)count);
				if( var < 0 ) {
					var = 0;
				}
				System.err.printf("bitrate standard deviation:  %7.3f kb/s\n",
						1e-3 * Math.sqrt( var ) * sampling_rate / frame_size );
			} else {
				System.err.printf("bitrate statistics are undefined\n");
			}
			// silk_TimerSave("opus_timing.txt");
			// ret = EXIT_SUCCESS;
			enc = null;// opus_encoder_destroy(enc);
			dec = null;// opus_decoder_destroy(dec);
			data[0] = null;
			if( 0 != use_inbandfec ) {
				data[1] = null;
			}
			// in = null;
			// out = null;
			fbytes = null;
		} catch(final IllegalArgumentException ie) {
			ie.printStackTrace();
			return EXIT_FAILURE;
		} catch(final FileNotFoundException fe) {
			fe.printStackTrace();
			return EXIT_FAILURE;
		} catch(final SecurityException se ) {
			se.printStackTrace();
			return EXIT_FAILURE;
		} catch(final IOException ie) {
			ie.printStackTrace();
			return EXIT_FAILURE;
		} catch(final Exception ie) {
			ie.printStackTrace();
			return EXIT_FAILURE;
		}
		finally {
			if( fin != null ) {
				try { fin.close(); } catch( final IOException e ) {}// fclose( fin );
			}
			fin = null;
			if( fout != null ) {
				try { fout.close(); } catch( final IOException e ) {}// fclose( fout );
			}
			fout = null;
		}
		return EXIT_SUCCESS;
	}

	public static final void main(final String argv[]) {
		System.exit( maininternal( argv ) );
	}
}