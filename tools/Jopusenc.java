package tools;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import celt.Jcelt;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import libenc.JOggOpusComments;
import libenc.JOggOpusEnc;
import libenc.IOpusEncCallbacks;
import libenc.Joggp_page_aux;
import libenc.Iope_packet_func;
import opus.JOpusDecoder;
import opus.JOpusEncoder;
import opus.JOpusMSEncoder;
import opus.Jopus_defines;

/* Copyright (C)2002-2011 Jean-Marc Valin
   Copyright (C)2007-2013 Xiph.Org Foundation
   Copyright (C)2008-2013 Gregory Maxwell
   File: opusenc.c

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
   A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
final class Jopusenc {
	private static final String CLASS_NAME = "Jopusenc";
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;

//	static void fatal(const char *format, ...) FORMAT_PRINTF;

	private static final void fatal(final String format, final Object... args)
	{
		System.err.printf( format, args );
		System.exit( EXIT_FAILURE );
	}

	private static void opustoolsversion(final String opusversion)
	{
		System.out.printf("Jopusenc %s %s (using %s)\n", Jtools.PACKAGE_NAME, Jtools.PACKAGE_VERSION, opusversion );
		System.out.printf("Copyright (C) 2008-2018 Xiph.Org Foundation\n");
	}

	private static void opustoolsversion_short(final String opusversion)
	{
		opustoolsversion( opusversion );
	}

	private static final String getSupportedAudioEncoding() {// java audio system
		final StringBuilder sb = new StringBuilder("It can read the ");
		final AudioFormat af = new AudioFormat( 48000, 16, 2, true, false );
		// final Encoding[] encodings = ;
		int length = sb.length();
		for( final Encoding e : AudioSystem.getTargetEncodings( af ) ) {
			final String s = e.toString();
			length += s.length();
			if( length > 70 ) {// 70 is max console chars in a line
				sb.append("\n                ");
				length = 16;// space count
			}
			sb.append( s ).append(',').append(' ');
			length += 2;
		}
		if( length + 13 /*"or raw files.\n".length()*/ > 70 ) {
			sb.append("\n                ");
		}
		sb.append("or raw files.\n");
		return sb.toString();
	}

	private static void usage()
	{
		System.out.printf("Usage: java -jar Jopusenc.jar [options] input_file output_file.opus\n");
		System.out.printf("\n");
		System.out.printf("Encode audio using Opus.\n");
/* #if defined(HAVE_LIBFLAC)
		System.out.printf("The input format can be Wave, AIFF, FLAC, Ogg/FLAC, or raw PCM.\n");
#else
		System.out.printf("The input format can be Wave, AIFF, or raw PCM.\n");
#endif */
		System.out.print( getSupportedAudioEncoding() );
		System.out.printf("\ninput_file can be:\n");
		System.out.printf("  filename.wav      file\n");
		System.out.printf("  -                 stdin\n");
		System.out.printf("\noutput_file can be:\n");
		System.out.printf("  filename.opus     compressed file\n");
		System.out.printf("  -                 stdout\n");
		System.out.printf("\nGeneral options:\n");
		System.out.printf(" -h, --help         Show this help\n");
		System.out.printf(" -V, --version      Show version information\n");
		System.out.printf(" --help-picture     Show help on attaching album art\n");
		System.out.printf(" --quiet            Enable quiet mode\n");
		System.out.printf("\nEncoding options:\n");
		System.out.printf(" --bitrate n.nnn    Set target bitrate in kbit/s (6-256/channel)\n");
		System.out.printf(" --vbr              Use variable bitrate encoding (default)\n");
		System.out.printf(" --cvbr             Use constrained variable bitrate encoding\n");
		System.out.printf(" --hard-cbr         Use hard constant bitrate encoding\n");
		System.out.printf(" --music            Tune low bitrates for music (override automatic detection)\n");
		System.out.printf(" --speech           Tune low bitrates for speech (override automatic detection)\n");
		System.out.printf(" --comp n           Set encoding complexity (0-10, default: 10 (slowest))\n");
		System.out.printf(" --framesize n      Set maximum frame size in milliseconds\n");
		System.out.printf("                      (2.5, 5, 10, 20, 40, 60, default: 20)\n");
		System.out.printf(" --expect-loss n    Set expected packet loss in percent (default: 0)\n");
		System.out.printf(" --downmix-mono     Downmix to mono\n");
		System.out.printf(" --downmix-stereo   Downmix to stereo (if >2 channels)\n");
// #ifdef OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST
		System.out.printf(" --no-phase-inv     Disable use of phase inversion for intensity stereo\n");
// #endif
		System.out.printf(" --max-delay n      Set maximum container delay in milliseconds\n");
		System.out.printf("                      (0-1000, default: 1000)\n");
		System.out.printf("\nMetadata options:\n");
		System.out.printf(" --title title      Set track title\n");
		System.out.printf(" --artist artist    Set artist or author, may be used multiple times\n");
		System.out.printf(" --album album      Set album or collection\n");
		System.out.printf(" --tracknumber n    Set track number\n");
		System.out.printf(" --genre genre      Set genre, may be used multiple times\n");
		System.out.printf(" --date YYYY-MM-DD  Set date of track (YYYY, YYYY-MM, or YYYY-MM-DD)\n");
		System.out.printf(" --comment tag=val  Add the given string as an extra comment\n");
		System.out.printf("                      This may be used multiple times\n");
		System.out.printf(" --picture file     Attach album art (see --help-picture)\n");
		System.out.printf("                      This may be used multiple times\n");
		System.out.printf(" --padding n        Reserve n extra bytes for metadata (default: 512)\n");
		System.out.printf(" --discard-comments Don't keep metadata when transcoding\n");
		System.out.printf(" --discard-pictures Don't keep pictures when transcoding\n");
		System.out.printf("\nInput options:\n");
		System.out.printf(" --raw              Interpret input as raw PCM data without headers\n");
		System.out.printf(" --raw-bits n       Set bits/sample for raw input (default: 16)\n");
		System.out.printf(" --raw-rate n       Set sampling rate for raw input (default: 48000)\n");
		System.out.printf(" --raw-chan n       Set number of channels for raw input (default: 2)\n");
		System.out.printf(" --raw-endianness n 1 for big endian, 0 for little (default: 0)\n");
		System.out.printf(" --ignorelength     Ignore the data length in Wave headers\n");
		System.out.printf("\nDiagnostic options:\n");
		System.out.printf(" --serial n         Force use of a specific stream serial number\n");
		System.out.printf(" --save-range file  Save check values for every frame to a file\n");
		System.out.printf(" --set-ctl-int x=y  Pass the encoder control x with value y (advanced)\n");
		System.out.printf("                      Preface with s: to direct the ctl to multistream s\n");
		System.out.printf("                      This may be used multiple times\n");
	}

	private static void help_picture()
	{
		System.out.printf("  The --picture option can be used with a FILENAME, naming a JPEG,\n");
		System.out.printf("  PNG, or GIF image file, or a more complete SPECIFICATION. The\n");
		System.out.printf("  SPECIFICATION is a string whose parts are separated by | (pipe)\n");
		System.out.printf("  characters. Some parts may be left empty to invoke default values.\n");
		System.out.printf("  A plain FILENAME is just shorthand for \"||||FILENAME\".\n");
		System.out.printf("\n");
		System.out.printf("  The format of SPECIFICATION is:\n");
		System.out.printf("  [TYPE]|[MEDIA-TYPE]|[DESCRIPTION]|[WIDTHxHEIGHTxDEPTH[/COLORS]]|FILENAME\n");
		System.out.printf("\n");
		System.out.printf("  TYPE is an optional number from one of:\n");
		System.out.printf("     0: Other\n");
		System.out.printf("     1: 32x32 pixel 'file icon' (PNG only)\n");
		System.out.printf("     2: Other file icon\n");
		System.out.printf("     3: Cover (front)\n");
		System.out.printf("     4: Cover (back)\n");
		System.out.printf("     5: Leaflet page\n");
		System.out.printf("     6: Media (e.g., label side of a CD)\n");
		System.out.printf("     7: Lead artist/lead performer/soloist\n");
		System.out.printf("     8: Artist/performer\n");
		System.out.printf("     9: Conductor\n");
		System.out.printf("    10: Band/Orchestra\n");
		System.out.printf("    11: Composer\n");
		System.out.printf("    12: Lyricist/text writer\n");
		System.out.printf("    13: Recording location\n");
		System.out.printf("    14: During recording\n");
		System.out.printf("    15: During performance\n");
		System.out.printf("    16: Movie/video screen capture\n");
		System.out.printf("    17: A bright colored fish\n");
		System.out.printf("    18: Illustration\n");
		System.out.printf("    19: Band/artist logotype\n");
		System.out.printf("    20: Publisher/studio logotype\n");
		System.out.printf("\n");
		System.out.printf("  The default is 3 (front cover). More than one --picture option can\n");
		System.out.printf("  be specified to attach multiple pictures. There may only be one\n");
		System.out.printf("  picture each of type 1 and 2 in a file.\n");
		System.out.printf("\n");
		System.out.printf("  MEDIA-TYPE is optional and is now ignored.\n");
		System.out.printf("\n");
		System.out.printf("  DESCRIPTION is optional. The default is an empty string.\n");
		System.out.printf("\n");
		System.out.printf("  The next part specifies the resolution and color information, but\n");
		System.out.printf("  is now ignored.\n");
		System.out.printf("\n");
		System.out.printf("  FILENAME is the path to the picture file to be imported.\n");
	}

	@SuppressWarnings("boxing")
	private static void print_time(double seconds)
	{
		final long hours = (long)(seconds / 3600);
		seconds -= hours * 3600.;
		final long minutes = (long)(seconds / 60);
		seconds -= minutes * 60.;
		if( hours != 0 ) {
			System.err.printf(" %d hour%s%s", hours, hours != 1 ? "s" : "",
					minutes != 0 && seconds > 0 ? "," : minutes != 0 || seconds > 0 ? " and" : "");
		}
		if( minutes != 0 ) {
			System.err.printf(" %d minute%s%s", minutes, minutes != 1 ? "s" : "",
					seconds > 0 ? (hours != 0 ? ", and" : " and") : "");
		}
		if( seconds > 0 || (0 == hours && 0 == minutes) ) {
			System.err.printf(" %.4g second%s", seconds, seconds != 1 ? "s" : "");
		}
	}

	private static final class JEncData implements Iope_packet_func, IOpusEncCallbacks {
		private JOggOpusEnc enc;
		private FileOutputStream fout;
		private long total_bytes;
		private long bytes_written;
		private long nb_encoded;
		private long pages_out;
		private long packets_out;
		private int peak_bytes;
		private int min_bytes;
		private int last_length;
		private int nb_streams;
		private int nb_coupled;
		private PrintStream frange;

		// Jope_packet_func
		@Override
		public final void ope_packet_func(final byte[] packet_ptr, final int poffset, final int packet_len, final int flags) {
			// final JEncData data = (JEncData)user_data;// java this
			final int nb_samples = JOpusDecoder.opus_packet_get_nb_samples( packet_ptr, 0, packet_len, 48000 );
			if( nb_samples <= 0 ) {
				return;
			}  /* ignore header packets */
			this.total_bytes += packet_len;
			if( packet_len > this.peak_bytes ) {
				this.peak_bytes = packet_len;
			}
			if( packet_len < this.min_bytes ) {
				this.min_bytes = packet_len;
			}
			this.nb_encoded += nb_samples;
			this.packets_out++;
			this.last_length = packet_len;
			if( this.frange != null ) {
				final Object[] request = new Object[1];// java helper
				final long rngs[] = new long[256];
				for( int s = 0; s < this.nb_streams; ++s ) {
					rngs[ s ] = 0;
					final int ret = this.enc.ope_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, s, request );
					final JOpusEncoder oe = (JOpusEncoder)request[0];// java
					if( ret == JOggOpusEnc.OPE_OK && oe != null ) {
						oe.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
						rngs[s] = ((Long)request[0]).longValue();// java
					}
				}
				Jdiag_range.save_range( this.frange, nb_samples, packet_ptr, 0, packet_len, rngs, this.nb_streams );
			}
			// (void)flags;
		}

		// JOpusEncCallbacks
		@Override
		public final boolean write(/* final Object user_data,*/ final Joggp_page_aux data)
		{
			// final JEncData data = (JEncData)user_data;// java this
			this.bytes_written += data.bytes;
			this.pages_out++;
			try {
				this.fout.write( data.page_base, data.page, data.bytes );
				return false;
			} catch( final IOException e ) {
			}
			return true;
		}

		@Override
		public final boolean close(/* final Object user_data */)
		{
			// final JEncData obj = (JEncData)user_data;// java this
			try {
				this.fout.close();
				return false;
			} catch( final IOException e ) {
			}
			return true;
		}
	}

	// java: moved to JEncData class
	/* private static int write_callback(final Object user_data, final byte[] ptr, final int len)
	{
		final JEncData data = (JEncData)user_data;
		data.bytes_written += len;
		data.pages_out++;
		return data.fout.write( ptr, 1, len ) != len;
	}

	private static int close_callback(final Object user_data)
	{
		final JEncData obj = (JEncData)user_data;
		return obj.fout.fclose() != 0;
	} */

	/* private static void packet_callback(final Object user_data, final byte[] packet_ptr, final int packet_len, final int flags)
	{
		final JEncData data = (JEncData)user_data;
		final int nb_samples = JOpusDecoder.opus_packet_get_nb_samples( packet_ptr, 0, packet_len, 48000 );
		if( nb_samples <= 0 ) {
			return;
		}// ignore header packets
		data.total_bytes += packet_len;
		if( packet_len > data.peak_bytes ) {
			data.peak_bytes = packet_len;
		}
		if( packet_len < data.min_bytes ) {
			data.min_bytes = packet_len;
		}
		data.nb_encoded += nb_samples;
		data.packets_out++;
		data.last_length = packet_len;
		if( data.frange != null ) {
			final Object[] request = new Object[1];// java helper
			final long rngs[] = new long[256];
			for( int s = 0; s < data.nb_streams; ++s ) {
				rngs[ s ] = 0;
				final int ret = JOggOpusEnc.ope_encoder_ctl( data.enc, JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, s, request );
				final JOpusEncoder oe = (JOpusEncoder)request[0];// java
				if( ret == JOggOpusEnc.OPE_OK && oe != null ) {
					oe.opus_encoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
					rngs[s] = ((Long)request[0]).longValue();// java
				}
			}
			Jdiag_range.save_range( data.frange, nb_samples, packet_ptr, 0, packet_len, rngs, data.nb_streams );
		}
		// (void)flags;
	} */

	private static boolean is_valid_ctl(final int request)
	{
		/*
		* These encoder ctls can be overridden for testing purposes without any
		* special handling in opusenc.  Some have their own option that should
		* be preferred but can still be overridden on a per-stream basis.  The
		* default settings are tuned to produce the best quality at the chosen
		* bitrate, so in general lower quality should be expected if these
		* settings are overridden.
		*/
		switch( request ) {
		case Jopus_defines.OPUS_SET_APPLICATION_REQUEST:
		case Jopus_defines.OPUS_SET_BITRATE_REQUEST:
		case Jopus_defines.OPUS_SET_MAX_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_VBR_REQUEST:
		case Jopus_defines.OPUS_SET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST:
		case Jopus_defines.OPUS_SET_INBAND_FEC_REQUEST:
		case Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST:
		case Jopus_defines.OPUS_SET_DTX_REQUEST:
		case Jopus_defines.OPUS_SET_VBR_CONSTRAINT_REQUEST:
		case Jopus_defines.OPUS_SET_FORCE_CHANNELS_REQUEST:
		case Jopus_defines.OPUS_SET_SIGNAL_REQUEST:
		case Jopus_defines.OPUS_SET_LSB_DEPTH_REQUEST:
		case Jopus_defines.OPUS_SET_PREDICTION_DISABLED_REQUEST:
// #ifdef OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
// #endif
		case JOggOpusEnc.OPE_SET_DECISION_DELAY_REQUEST:
		case JOggOpusEnc.OPE_SET_MUXING_DELAY_REQUEST:
		case JOggOpusEnc.OPE_SET_COMMENT_PADDING_REQUEST:
		case JOggOpusEnc.OPE_SET_SERIALNO_REQUEST:
		case JOggOpusEnc.OPE_SET_HEADER_GAIN_REQUEST:
			return true;
		}
		return false;
	}

	// private static final Jinput_format raw_format = new Jinput_format( null, 0, raw_open, wav_close, "raw", "RAW file reader");
	private static final int ENCODER_STRING_MAX_LENGTH = 1024;

	@SuppressWarnings("boxing")
	public static final void main(final String[] args)
	{
		final LongOpt long_options[] =
		{
			new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("bitrate", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("hard-cbr", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("vbr", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("cvbr", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("music", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("speech", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("comp", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("complexity", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("framesize", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("expect-loss", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("downmix-mono", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("downmix-stereo", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("no-downmix", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("no-phase-inv", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("max-delay", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("serial", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("save-range", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("set-ctl-int", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("help", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("help-picture", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("raw", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("raw-bits", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("raw-rate", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("raw-chan", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("raw-endianness", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("ignorelength", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("version", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("version-short", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("comment", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("artist", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("title", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("album", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("tracknumber", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("date", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("genre", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("picture", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("padding", LongOpt.REQUIRED_ARGUMENT, null, 0 ),
			new LongOpt("discard-comments", LongOpt.NO_ARGUMENT, null, 0 ),
			new LongOpt("discard-pictures", LongOpt.NO_ARGUMENT, null, 0 ),
			// {0, 0, 0, 0}
		};
		int i;
		int ret;
		int                cline_size;
		// java: implemented in JEncData. final JOpusEncCallbacks   callbacks = {write_callback, close_callback};
		JOggOpusEnc        enc = null;
		final JEncData     data = new JEncData();
		String             opus_version;
		float[]            input;
		/*I/O*/
		final Joe_enc_opt  inopt = new Joe_enc_opt();
		// final Jinput_format in_format;
		String             inFile;
		String             outFile;
		String             range_file;
		BufferedInputStream fin = null;
		String             ENCODER_string = null;// [1024];
		/*Counters*/
		int                nb_samples;
		long               start_time;
		long               stop_time;
		long               last_spin = 0;
		int                last_spin_len = 0;
		/*Settings*/
		boolean            quiet = false;
		int                bitrate = -1;
		int                rate = 48000;
		int                frame_size = 960;
		int                opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_20_MS;
		int                chan = 2;
		boolean            with_hard_cbr = false;
		boolean            with_cvbr = false;
		int                signal_type = Jopus_defines.OPUS_AUTO;
		int                expect_loss = 0;
		int                complexity = 10;
		int                downmix = 0;
		boolean            no_phase_inv = false;
		int[]              opt_ctls_ctlval;
		int                opt_ctls = 0;
		int                max_ogg_delay = 48000; /*48kHz samples*/
		int                seen_file_icons = 0;
		int                comment_padding = 512;
		int                serialno;
		int                lookahead = 0;

		/* if( query_cpu_support() ) {
			System.err.printf("\n\n** WARNING: This program was compiled with SSE%s\n", query_cpu_support() > 1 ? "2" : "");
			System.err.printf("            but this CPU claims to lack these instructions. **\n\n");
		} */

		opt_ctls_ctlval = new int[0];
		range_file = null;
		// in_format = null;
		inopt.channels = chan;
		inopt.rate = rate;
		/* 0 dB gain is recommended unless you know what you're doing */
		inopt.gain = 0;
		inopt.samplesize = 16;
		inopt.endianness = 0;
		inopt.rawmode = false;
		inopt.ignorelength = 0;
		inopt.copy_comments = true;
		inopt.copy_pictures = true;

		start_time = System.currentTimeMillis() / 1000;
		serialno = new Random( start_time ).nextInt( 0x8000 );

		inopt.comments = JOggOpusComments.ope_comments_create();
		if( inopt.comments == null ) {
			fatal("Error: failed to allocate memory for comments\n");
		}
		opus_version = Jcelt.opus_get_version_string();
		/*Vendor string should just be the encoder library,
		the ENCODER comment specifies the tool used.*/
		ENCODER_string = String.format("opusenc from %s %s", Jtools.PACKAGE_NAME, Jtools.PACKAGE_VERSION );
		ret = inopt.comments.ope_comments_add( "ENCODER", ENCODER_string );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: failed to add ENCODER comment: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}

		data.enc = null;
		data.fout = null;
		data.total_bytes = 0;
		data.bytes_written = 0;
		data.nb_encoded = 0;
		data.pages_out = 0;
		data.packets_out = 0;
		data.peak_bytes = 0;
		data.min_bytes = 256 * 1275 * 6;
		data.last_length = 0;
		data.nb_streams = 1;
		data.nb_coupled = 0;
		data.frange = null;

try {
		/*Process command-line options*/
		cline_size = 0;
		final Getopt g = new Getopt( CLASS_NAME, args, "hV", long_options );
		g.setOpterr( false );
		int c;
		while( (c = g.getopt()) != -1 ) {

			boolean save_cmd;
			final String optarg = g.getOptarg();

			switch( c ) {
			case 0:
				final String optname = long_options[ g.getLongind() ].getName();// java
				save_cmd = true;
				if( optname.compareTo("quiet") == 0 ) {
					quiet = true;
					save_cmd = false;
				} else if( optname.compareTo("bitrate") == 0 ) {
					bitrate = (int)(Float.parseFloat( optarg ) * 1000.f);
				} else if( optname.compareTo("hard-cbr") == 0 ) {
					with_hard_cbr = true;
					with_cvbr = false;
				} else if( optname.compareTo("cvbr") == 0 ) {
					with_cvbr = true;
					with_hard_cbr = false;
				} else if( optname.compareTo("vbr") == 0 ) {
					with_cvbr = false;
					with_hard_cbr = false;
				} else if( optname.compareTo("help") == 0 ) {
					usage();
					System.exit( EXIT_SUCCESS );
				} else if( optname.compareTo("help-picture") == 0 ) {
					help_picture();
					System.exit( EXIT_SUCCESS );
				} else if( optname.compareTo("version") == 0 ) {
					opustoolsversion( opus_version );
					System.exit( EXIT_SUCCESS );
				} else if( optname.compareTo("version-short") == 0 ) {
					opustoolsversion_short( opus_version );
					System.exit( EXIT_SUCCESS );
				} else if( optname.compareTo("ignorelength") == 0 ) {
					inopt.ignorelength = 1;
					save_cmd = false;
				} else if( optname.compareTo("raw") == 0 ) {
					inopt.rawmode = true;
					save_cmd = false;
				} else if( optname.compareTo("raw-bits") == 0 ) {
					inopt.rawmode = true;
					inopt.samplesize = Integer.parseInt( optarg );
					save_cmd = false;
					if( inopt.samplesize != 8 && inopt.samplesize != 16 && inopt.samplesize != 24 ) {
						fatal("Invalid bit-depth: %s\n" +
								"--raw-bits must be one of 8, 16, or 24\n", optarg );
					}
				} else if( optname.compareTo("raw-rate") == 0 ) {
					inopt.rawmode = true;
					inopt.rate = Integer.parseInt( optarg );
					save_cmd = false;
				} else if( optname.compareTo("raw-chan") == 0 ) {
					inopt.rawmode = true;
					inopt.channels = Integer.parseInt( optarg );
					save_cmd = false;
				} else if( optname.compareTo("raw-endianness") == 0 ) {
					inopt.rawmode = true;
					inopt.endianness = Integer.parseInt( optarg );
					save_cmd = false;
				} else if( optname.compareTo("downmix-mono") == 0 ) {
					downmix = 1;
				} else if( optname.compareTo("downmix-stereo") == 0 ) {
					downmix = 2;
				} else if( optname.compareTo("no-downmix") == 0 ) {
					downmix = -1;
				} else if( optname.compareTo("no-phase-inv") == 0 ) {
					no_phase_inv = true;
				} else if( optname.compareTo("music") == 0 ) {
					signal_type = Jopus_defines.OPUS_SIGNAL_MUSIC;
				} else if( optname.compareTo("speech") == 0 ) {
					signal_type = Jopus_defines.OPUS_SIGNAL_VOICE;
				} else if( optname.compareTo("expect-loss") == 0 ) {
					expect_loss = Integer.parseInt( optarg );
				if( expect_loss > 100 || expect_loss < 0 ) {
					fatal("Invalid expect-loss: %s\n" +
							"Expected loss is a percentage in the range 0 to 100.\n", optarg);
					}
				} else if( optname.compareTo("comp") == 0 ||
						optname.compareTo("complexity") == 0 ) {
					complexity = Integer.parseInt( optarg );
					if( complexity > 10 || complexity < 0 ) {
						fatal("Invalid complexity: %s\n" +
								"Complexity must be in the range 0 to 10.\n", optarg);
					}
				} else if( optname.compareTo("framesize") == 0 ) {
					if( optarg.compareTo("2.5") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_2_5_MS;
					} else if( optarg.compareTo("5") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_5_MS;
					} else if( optarg.compareTo("10") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_10_MS;
					} else if( optarg.compareTo("20") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_20_MS;
					} else if( optarg.compareTo("40") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_40_MS;
					} else if( optarg.compareTo("60") == 0 ) {
						opus_frame_param = Jopus_defines.OPUS_FRAMESIZE_60_MS;
					} else {
						fatal("Invalid framesize: %s\n" +
								"Value is in milliseconds and must be 2.5, 5, 10, 20, 40, or 60.\n",
								optarg);
					}
					frame_size = opus_frame_param <= Jopus_defines.OPUS_FRAMESIZE_40_MS
							? 120 << (opus_frame_param - Jopus_defines.OPUS_FRAMESIZE_2_5_MS)
									: (opus_frame_param - Jopus_defines.OPUS_FRAMESIZE_20_MS + 1) * 960;
				} else if( optname.compareTo("max-delay") == 0 ) {
					final double val = Double.parseDouble( optarg );
					if( val < 0. || val > 1000. ) {
						fatal("Invalid max-delay: %s\n" +
								"Value is in milliseconds and must be in the range 0 to 1000.\n",
								optarg);
					}
					max_ogg_delay = (int)Math.floor( val * 48. );
				} else if( optname.compareTo("serial") == 0 ) {
					serialno = Integer.parseInt( optarg );
				} else if( optname.compareTo("set-ctl-int") == 0 ) {
					final int len = optarg.length();
					final int spos = optarg.indexOf('=');
					if( len < 3 || spos < 1 || spos >= len ) {
						fatal("Invalid set-ctl-int: %s\n" +
								"Syntax is --set-ctl-int intX=intY\n" +
								"       or --set-ctl-int intS:intX=intY\n", optarg );
					}
					int tpos = optarg.indexOf(':');
					int target;
					if( tpos < 0 ) {
						target = -1;
						tpos = -1;
					} else {
						target = Integer.parseInt( optarg );
					}
					final int request = Integer.parseInt( optarg.substring( tpos + 1 ) );
					if( ! is_valid_ctl( request ) ) {
						fatal("Invalid set-ctl-int: %s\n", optarg );
					}
					if( opt_ctls == 0 ) {
						opt_ctls_ctlval = new int[ 3 ];
					} else {
						opt_ctls_ctlval = Arrays.copyOf( opt_ctls_ctlval, (opt_ctls + 1) * 3 );
					}
					//if( null == opt_ctls_ctlval ) {
					//	fatal("Error: failed to allocate memory for ctls\n");
					//}
					tpos = opt_ctls * 3;// java
					opt_ctls_ctlval[ tpos++ ] = target;
					opt_ctls_ctlval[ tpos++ ] = request;
					opt_ctls_ctlval[ tpos   ] = Integer.parseInt( optarg.substring( spos + 1 ) );
					opt_ctls++;
				} else if( optname.compareTo("save-range") == 0 ) {
					if( data.frange != null ) {
						data.frange.close();
					}
					try {
						data.frange = new PrintStream( optarg );
						save_cmd = false;
					} catch (final Exception e) {
					}
					if( data.frange == null ) {
						System.err.println( optarg );
						fatal("Error: cannot open save-range file: %s\n" +
								"Must provide a writable file name.\n", optarg );
					}
					range_file = optarg;
				} else if( optname.compareTo("comment") == 0 ) {
					save_cmd = false;
					if( optarg.indexOf('=') < 0 ) {
						fatal("Invalid comment: %s\n" +
								"Comments must be of the form name=value\n", optarg );
					}
					ret = inopt.comments.ope_comments_add_string( optarg );
					if( ret != JOggOpusEnc.OPE_OK ) {
						fatal("Error: failed to add comment: %s\n", JOggOpusEnc.ope_strerror( ret ) );
					}
				} else if( optname.compareTo("artist") == 0 ||
						optname.compareTo("title") == 0 ||
						optname.compareTo("album") == 0 ||
						optname.compareTo("tracknumber") == 0 ||
						optname.compareTo("date") == 0 ||
						optname.compareTo("genre") == 0 ) {
					save_cmd = false;
					ret = inopt.comments.ope_comments_add( optname, optarg );
					if( ret != JOggOpusEnc.OPE_OK ) {
						fatal("Error: failed to add %s comment: %s\n", optname, JOggOpusEnc.ope_strerror( ret ) );
					}
				} else if( optname.compareTo("picture") == 0 ) {
					String filename, spec, description_copy;
					int description_end, description;
					description_end = description = 0;
					save_cmd = false;
					spec = optarg;
					int picture_type = 3;
					filename = spec;
					RandomAccessFile picture_file = null;
					description_copy = null;
					try {
						picture_file = new RandomAccessFile( filename, "rw" );
					} catch(final Exception e) {
					} finally {
						if( picture_file != null ) {
							try{ picture_file.close(); } catch(final IOException ie) {}
						}
					}
					if( picture_file == null && spec.indexOf('|') >= 0 ) {
						/*We don't have a plain file, and there is a pipe character: assume it's
						the full form of the specification.*/
						long val = 0;
						int q = spec.indexOf('|');
						if( q >= 0 ) {
							val = Long.parseLong( spec.substring( 0, q ), 10 );
						}
						if( q < 0 || val > 20 ) {
							if( q < 0 ) {
								q = spec.length();
							}
							fatal("Invalid picture type: %." + q + "s\n" +
									"Picture type must be in the range 0 to 20; see --help-picture.\n",
									spec );
						}
						/*An empty field implies a default of 'Cover (front)'.*/
						if( q != 0 ) {
							picture_type = (int)val;
						}
						final int media_type = q + 1;
						q = spec.indexOf( '|', media_type );
						final int media_type_end = media_type + (q < 0 ? spec.length() - media_type : q);// strcspn(media_type, "|");
						if( spec.charAt( media_type_end ) == '|' ) {
							description = media_type_end + 1;
							q = spec.indexOf( '|', description );
							description_end = description + (q < 0 ? spec.length() - description : q);
							if( spec.charAt( description_end ) == '|' ) {
								int p = description_end + 1;
								/*Ignore WIDTHxHEIGHTxDEPTH/COLORS.*/
								q = spec.indexOf( '|', p );
								p += (q < 0 ? spec.length() - p : q);
								if( p < spec.length() && spec.charAt( p ) == '|' ) {
									filename = spec.substring( p + 1 );
								}
							}
						}
						if( filename == spec ) {
							fatal("Not enough fields in picture specification:\n  %s\n" +
									"The format of a picture specification is:\n" +
									"  [TYPE]|[MEDIA-TYPE]|[DESCRIPTION]|[WIDTHxHEIGHTxDEPTH[/COLORS]]" +
									"|FILENAME\nSee --help-picture.\n", spec );
						}

						if( media_type_end - media_type == 3 && spec.indexOf( "-->", media_type ) == media_type ) {
							fatal("Picture URLs are no longer supported.\n" +
									"See --help-picture.\n");
						}
						if( picture_type >= 1 && picture_type <= 2 && (seen_file_icons & picture_type) != 0 ) {
							fatal("Error: only one picture of type %d (%s) is allowed\n",
									picture_type, picture_type == 1 ? "32x32 icon" : "icon");
						}
					}
					if( description_end - description != 0 ) {
						description_copy = spec.substring( description );
					}
					ret = inopt.comments.ope_comments_add_picture( filename, picture_type, description_copy );
					if( ret != JOggOpusEnc.OPE_OK ) {
						fatal("Error: %s: %s\n", JOggOpusEnc.ope_strerror( ret ), filename );
					}
					// if( description_copy != null ) {
						description_copy = null;
					// }
					if( picture_type >= 1 && picture_type <= 2 ) {
						seen_file_icons |= picture_type;
					}
				} else if( optname.compareTo("padding") == 0 ) {
					comment_padding = Integer.parseInt( optarg );
				} else if( optname.compareTo("discard-comments") == 0 ) {
					inopt.copy_comments = false;
					inopt.copy_pictures = false;
				} else if( optname.compareTo("discard-pictures") == 0 ) {
					inopt.copy_pictures = false;
				}
				/*Options whose arguments would leak file paths or just end up as
				metadata, or that relate only to input file handling or console
				output, should have save_cmd=0; to prevent them from being saved
				in the ENCODER_OPTIONS tag.*/
				if( save_cmd && cline_size < ENCODER_STRING_MAX_LENGTH ) {
					ENCODER_string += String.format("%s--%s", cline_size == 0 ? "" : " ", optname );
					ret = ENCODER_string.length();
					if( ret < 0 || ret >= (ENCODER_STRING_MAX_LENGTH - cline_size) ) {
						cline_size = ENCODER_STRING_MAX_LENGTH;
					} else {
						cline_size += ret;
						if( optarg != null ) {
							ENCODER_string += String.format(" %s", optarg );
							ret = ENCODER_string.length();
							if( ret < 0 || ret >= (ENCODER_STRING_MAX_LENGTH - cline_size) ) {
								cline_size = ENCODER_STRING_MAX_LENGTH;
							} else {
								cline_size += ret;
							}
						}
					}
				}
				break;
			case 'h':
				usage();
				System.exit( EXIT_SUCCESS );
				break;
			case 'V':
				opustoolsversion(opus_version);
				System.exit( EXIT_SUCCESS );
				break;
			case '?':
				usage();
				System.exit( EXIT_FAILURE );
				break;
			}
		}// while opt
		final int optind = g.getOptind();
		if( args.length - optind != 2 ) {
			usage();
			System.exit( EXIT_FAILURE );
		}
		inFile = args[ optind ];
		outFile = args[ optind + 1 ];

		if( cline_size > 0 ) {
			ret = inopt.comments.ope_comments_add( "ENCODER_OPTIONS", ENCODER_string );
			if( ret != JOggOpusEnc.OPE_OK ) {
				fatal("Error: failed to add ENCODER_OPTIONS comment: %s\n", JOggOpusEnc.ope_strerror( ret ) );
			}
		}

		if( inFile.compareTo("-") == 0 ) {
			// fin = stdin;
			System.err.print("stdin input not implemented\n" );
			System.exit( EXIT_FAILURE );
			return;
		} else {
			try {
				fin = new BufferedInputStream( new FileInputStream( inFile ), 65536 * 2 );
			} catch(final Exception e) {
				System.err.printf("Can not open input file: %s\n", e.getMessage() );
				System.exit( EXIT_FAILURE );
				return;
			}
		}

		if( inopt.rawmode ) {
			// in_format = &raw_format;
			// in_format.open_func( fin, &inopt, null, 0 );
			System.err.print("rawmode input not implemented\n");
			System.exit( EXIT_FAILURE );
			// return;
		} else {
			// in_format = open_audio_file( fin, &inopt );
			//
			AudioInputStream in = null;
			AudioInputStream din = null;
			try {
				in = AudioSystem.getAudioInputStream( fin );
				if( in != null ) {
					final AudioFormat audio_in_format = in.getFormat();
					final int channels = audio_in_format.getChannels();
					final AudioFormat decoded_format = new AudioFormat(
							AudioFormat.Encoding.PCM_SIGNED,
							audio_in_format.getSampleRate(),
							16, channels, channels * (16 / 8),
							audio_in_format.getSampleRate(),
							false );
					din = AudioSystem.getAudioInputStream( decoded_format, in );
					//
					inopt.rate = (int)audio_in_format.getSampleRate();
					inopt.channels = channels;
					inopt.samplesize = 16;
					inopt.total_samples_per_channel = 0;
					// inopt.readdata = din;
					inopt.read_samples = new Jaudio_spi_reader( din, inopt, false, false );
				}
			} catch(final Exception e) {
				System.err.printf("Error parsing input file: %s, %s\n", inFile, e.getMessage() );
				System.exit( EXIT_FAILURE );
				return;
			} finally {
			}
		}

		/* if( null == in_format ) {
			fatal("Error: unsupported input file: %s\n", inFile );
		} */

		if( inopt.rate < 100 || inopt.rate > 768000 ) {
			/*Crazy rates excluded to avoid excessive memory usage for padding/resampling.*/
			fatal("Error: unsupported sample rate in input file: %ld Hz\n", inopt.rate);
		}

		if( inopt.channels > 255 || inopt.channels < 1 ) {
			fatal("Error: unsupported channel count in input file: %d\n" +
					"Channel count must be in the range 1 to 255.\n", inopt.channels);
		}

		if( downmix == 0 && inopt.channels > 2 && bitrate > 0 && bitrate < (16000 * inopt.channels) ) {
			if( ! quiet ) {
				System.err.printf("Notice: Surround bitrate less than 16 kbit/s per channel, downmixing.\n");
			}
			downmix = inopt.channels > 8 ? 1 : 2;
		}

		if( downmix > 0 && downmix < inopt.channels ) {
			downmix = Jdownmix.setup_downmix( inopt, downmix );
		} else {
			downmix = 0;
		}

		rate = inopt.rate;
		chan = inopt.channels;

		if( inopt.total_samples_per_channel != 0 && rate != 48000 ) {
			inopt.total_samples_per_channel = (long)
				((double)inopt.total_samples_per_channel * (48000. / (double)rate));
		}

		final Object[] request = new Object[1];// java helper
		/*Initialize Opus encoder*/
		try {
			enc = JOggOpusEnc.ope_encoder_create_callbacks( (IOpusEncCallbacks)data, inopt.comments, rate,
					chan, (chan > 8 ? 255 : (chan > 2 ? 1 : 0)) );// , ret );
		} catch(final IOException ie) {
			fatal("Error: failed to create encoder: %s\n", JOggOpusEnc.ope_strerror( ret ) );
			return;
		}
		if( enc == null ) {
			fatal("Error: failed to create encoder: %s\n", JOggOpusEnc.ope_strerror( ret ) );
			return;
		}
		data.enc = enc;

		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, opus_frame_param );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_EXPERT_FRAME_DURATION failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_SET_MUXING_DELAY, max_ogg_delay );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_SET_MUXING_DELAY failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_SET_SERIALNO, serialno );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_SET_SERIALNO failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_SET_HEADER_GAIN, inopt.gain );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_SET_HEADER_GAIN failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_SET_PACKET_CALLBACK, (Iope_packet_func)data );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_SET_PACKET_CALLBACK failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_SET_COMMENT_PADDING, comment_padding );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_SET_COMMENT_PADDING failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}

		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_GET_NB_STREAMS, request );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_GET_NB_STREAMS failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		data.nb_streams = ((Integer)request[0]).intValue();// java
		ret = enc.ope_encoder_ctl( JOggOpusEnc.OPE_GET_NB_COUPLED_STREAMS, request );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPE_GET_NB_COUPLED_STREAMS failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		data.nb_coupled = ((Integer)request[0]).intValue();// java

		if( bitrate < 0 ) {
			/*Lower default rate for sampling rates [8000-44100) by a factor of (rate+16k)/(64k)*/
			bitrate = ((64000 * data.nb_streams + 32000 * data.nb_coupled) *
					(Math.min(48, Math.max(8, ((rate < 44100 ? rate : 48000) + 1000) / 1000)) + 16) + 32) >> 6;
		}

		if( bitrate > (1024000 * chan) || bitrate < 500 ) {
			fatal("Error: bitrate %d bits/sec is insane\n%s" +
					"--bitrate values from 6 to 256 kbit/s per channel are meaningful.\n",
					bitrate, bitrate >= 1000000 ? "Did you mistake bits for kilobits?\n" : "");
		}
		ret = chan * 256000;
		if( bitrate > ret ) {
			bitrate = ret;
		}

		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_BITRATE %d failed: %s\n", bitrate, JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_VBR, ! with_hard_cbr );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_VBR %d failed: %s\n", ! with_hard_cbr, JOggOpusEnc.ope_strerror( ret ) );
		}
		if( ! with_hard_cbr ) {
			ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, with_cvbr );
			if( ret != JOggOpusEnc.OPE_OK ) {
				fatal("Error: OPUS_SET_VBR_CONSTRAINT %d failed: %s\n",
						with_cvbr, JOggOpusEnc.ope_strerror( ret ) );
			}
		}
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_SIGNAL, signal_type );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_SIGNAL failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, complexity );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_COMPLEXITY %d failed: %s\n", complexity, JOggOpusEnc.ope_strerror( ret ) );
		}
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, expect_loss );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_SET_PACKET_LOSS_PERC %d failed: %s\n",
					expect_loss, JOggOpusEnc.ope_strerror( ret ) );
		}
// #ifdef OPUS_SET_LSB_DEPTH
		ret = inopt.samplesize;
		if( ret > 24 ) {
			ret = 24;
		} else if( ret < 8 ) {
			ret = 8;
		}
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_LSB_DEPTH, ret );
		if( ret != JOggOpusEnc.OPE_OK ) {
			System.err.printf("Warning: OPUS_SET_LSB_DEPTH failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
// #endif
		if( no_phase_inv ) {
// #ifdef OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST
			ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED, true );
			if( ret != JOggOpusEnc.OPE_OK ) {
				System.err.printf("Warning: OPUS_SET_PHASE_INVERSION_DISABLED failed: %s\n",
						JOggOpusEnc.ope_strerror( ret ) );
			}
//#else
//			System.err.printf("Warning: Disabling phase inversion is not supported.\n");
//#endif
		}

		/*This should be the last set of SET ctls, so it can override the defaults.*/
		for( i = 0; i < opt_ctls; i++ ) {
			final int i3 = i * 3;// java
			final int target = opt_ctls_ctlval[ i3 ];
			if( target == -1 ) {
				ret = enc.ope_encoder_ctl( opt_ctls_ctlval[i3 + 1], opt_ctls_ctlval[i3 + 2]);
				if( ret != JOggOpusEnc.OPE_OK ) {
					fatal("Error: failed to set encoder ctl %d=%d: %s\n",
							opt_ctls_ctlval[i3 + 1], opt_ctls_ctlval[i3 + 2], JOggOpusEnc.ope_strerror( ret ) );
				}
			} else if( target < data.nb_streams ) {
				ret = enc.ope_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, target, request );
				if( ret != JOggOpusEnc.OPE_OK ) {
					fatal("Error: OPUS_MULTISTREAM_GET_ENCODER_STATE %d failed: %s\n",
							target, JOggOpusEnc.ope_strerror( ret ) );
				}
				final JOpusEncoder oe = (JOpusEncoder)request[0];// java
				ret = oe.opus_encoder_ctl( opt_ctls_ctlval[i3 + 1], opt_ctls_ctlval[i3 + 2] );
				if( ret != Jopus_defines.OPUS_OK ) {
					fatal("Error: failed to set stream %d encoder ctl %d=%d: %s\n",
							target, opt_ctls_ctlval[i3 + 1], opt_ctls_ctlval[i3 + 2], Jcelt.opus_strerror( ret ) );
				}
			} else {
				fatal("Error: --set-ctl-int stream %d is higher than the highest " +
						"stream number %d\n", target, data.nb_streams - 1 );
			}
		}

		/*We do the lookahead check late so user ctls can change it*/
		ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Error: OPUS_GET_LOOKAHEAD failed: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		lookahead = ((Integer)request[0]).intValue();// java

		if( ! quiet ) {
			System.err.printf("Encoding using %s", opus_version );
			ret = enc.ope_encoder_ctl( Jopus_defines.OPUS_GET_APPLICATION, request );
			if( ret != JOggOpusEnc.OPE_OK ) {
				System.err.printf("\n");
			}
			final int opus_app = ((Integer)request[0]).intValue();// java
			if( opus_app == Jopus_defines.OPUS_APPLICATION_VOIP ) {
				System.err.printf(" (VoIP)\n");
			} else if( opus_app == Jopus_defines.OPUS_APPLICATION_AUDIO ) {
				System.err.printf(" (audio)\n");
			} else if( opus_app == Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY ) {
				System.err.printf(" (low-delay)\n");
			} else {
				System.err.printf(" (unknown application)\n");
			}
			System.err.printf("-----------------------------------------------------\n");
			System.err.printf("   Input: %.6g kHz, %d channel%s\n",
					rate / 1000., chan, chan < 2 ? "" : "s");
			System.err.printf("  Output: %d channel%s (", chan, chan < 2 ? "" : "s" );
			if( data.nb_coupled > 0 ) {
				System.err.printf("%d coupled", data.nb_coupled << 1 );
			}
			if( data.nb_streams - data.nb_coupled > 0 ) {
				System.err.printf(
						"%s%d uncoupled", data.nb_coupled > 0 ? ", " : "",
								data.nb_streams - data.nb_coupled );
			}
			System.err.printf(")\n          %.2gms packets, %.6g kbit/s%s\n",
					frame_size / (48000 / 1000.), bitrate / 1000.,
					with_hard_cbr ? " CBR" : with_cvbr ? " CVBR" : " VBR");
			System.err.printf(" Preskip: %d\n", lookahead );
			if( data.frange != null ) {
				System.err.printf("          Writing final range file %s\n", range_file );
			}
			System.err.printf("\n");
		}

		if( outFile.compareTo("-") == 0 ) {
			// data.fout = stdout;
			System.err.printf("stdout output not implemented %s\n" );
			System.exit( EXIT_FAILURE );
			return;
		} else {
			try {
				data.fout = new FileOutputStream( outFile );
			} catch(final Exception e) {
				System.err.printf("Can not open output file: %s\n", e.getMessage() );
				System.exit( EXIT_FAILURE );
				return;
			}
		}

		input = new float[ frame_size * chan ];
		/* if( input == null ) {
			fatal("Error: failed to allocate sample buffer\n");
		} */

		/*Main encoding loop (one frame per iteration)*/
		while( true ) {
			// nb_samples = inopt.read_samples( inopt.readdata, input, frame_size );
			nb_samples = inopt.read_samples.audio_read_func( /* inopt.readdata,*/ input, 0, frame_size );
			ret = enc.ope_encoder_write_float( input, nb_samples );
			if( ret != JOggOpusEnc.OPE_OK || nb_samples < frame_size) {
				break;
			}

			if( ! quiet ) {
				stop_time = System.currentTimeMillis() / 1000;
				if( stop_time > last_spin ) {
					double estbitrate;
					final double coded_seconds = data.nb_encoded / 48000.;
					final double wall_time = (double)(stop_time - start_time);
					final char spinner[] = {'|', '/', '-', '\\'};
					if( with_hard_cbr ) {
						estbitrate = data.last_length * (8 * 48000. / frame_size);
					} else if( data.nb_encoded <= 0 ) {
						estbitrate = 0;
					} else {
						final double tweight = 1. / (1 + Math.exp( -((coded_seconds / 10.) - 3.) ));
						estbitrate = (data.total_bytes * 8.0 / coded_seconds) * tweight+
								bitrate * (1. - tweight);
					}
					System.err.printf("\r");
					for( i = 0; i < last_spin_len; i++ ) {
						System.err.printf(" ");
					}
					String sbuf;
					if( inopt.total_samples_per_channel > 0 &&
							data.nb_encoded < inopt.total_samples_per_channel + lookahead ) {
						sbuf = String.format("\r[%c] %2d%% ", spinner[ ((int)last_spin) & 3 ],
								(int)Math.floor( data.nb_encoded
										/ (double)(inopt.total_samples_per_channel + lookahead) * 100.) );
					} else {
						sbuf = String.format("\r[%c] ", spinner[ ((int)last_spin) & 3 ] );
					}
					// last_spin_len = sbuf.length();
					sbuf += String.format("%02d:%02d:%02d.%02d %4.3gx realtime, %5.4g kbit/s",
							(int)(coded_seconds / 3600), (int)(coded_seconds / 60) % 60,
							(int)(coded_seconds) % 60, (int)(coded_seconds * 100) % 100,
							coded_seconds / (wall_time > 0 ? wall_time : 1e-6),
							estbitrate / 1000. );
					System.err.printf("%s", sbuf );
					System.err.flush();
					last_spin_len = sbuf.length();
					last_spin = stop_time;
				}
			}
		}

		if( last_spin_len != 0 ) {
			System.err.printf("\r");
			for( i = 0; i < last_spin_len; i++ ) {
				System.err.printf(" ");
			}
			System.err.printf("\r");
		}

		if( ret == JOggOpusEnc.OPE_OK ) {
			ret = enc.ope_encoder_drain();
		}
		if( ret != JOggOpusEnc.OPE_OK ) {
			fatal("Encoding aborted: %s\n", JOggOpusEnc.ope_strerror( ret ) );
		}
		stop_time = System.currentTimeMillis() / 1000;

		if( ! quiet ) {
			final double coded_seconds = data.nb_encoded / 48000.;
			final double wall_time = (double)(stop_time - start_time);
			System.err.printf("Encoding complete\n");
			System.err.printf("-----------------------------------------------------\n");
			System.err.printf("       Encoded:");
			print_time( coded_seconds );
			System.err.printf("\n       Runtime:");
			print_time( wall_time );
			System.err.printf("\n");
			if( wall_time > 0 ) {
				System.err.printf("                (%.4gx realtime)\n", coded_seconds / wall_time);
			}
			System.err.printf("         Wrote: %d bytes, %d packets, %d pages\n",
					data.bytes_written, data.packets_out, data.pages_out );
			if( data.nb_encoded > 0 ) {
				System.err.printf("       Bitrate: %.6g kbit/s (without overhead)\n",
						data.total_bytes * 8.0 / (coded_seconds) / 1000.0);
				System.err.printf(" Instant rates: %.6g to %.6g kbit/s\n" +
						"                (%d to %d bytes per packet)\n",
						data.min_bytes * (8 * 48000. / frame_size / 1000.),
						data.peak_bytes * (8 * 48000. / frame_size / 1000.), data.min_bytes, data.peak_bytes);
			}
			if( data.bytes_written > 0 ) {
				System.err.printf("      Overhead: %.3g%% (container+metadata)\n",
						(data.bytes_written - data.total_bytes) / (double)data.bytes_written * 100.);
			}
			System.err.printf("\n");
		}

		enc.ope_encoder_destroy();
		JOggOpusComments.ope_comments_destroy( inopt.comments );
		inopt.comments = null;
		input = null;
		// if( opt_ctls != 0 ) {
			opt_ctls_ctlval = null;
		// }

		if( downmix != 0 ) {
			Jdownmix.clear_downmix( inopt );
		}
} catch(final Exception e) {
	e.printStackTrace();
	System.exit( EXIT_FAILURE );
	return;
} finally {
		// in_format.close_func( inopt.readdata );
		inopt.read_samples.close();
		if( fin != null ) {
			try { fin.close(); } catch( final IOException e ) {}
		}
		if( data.frange != null ) {
			data.frange.close();
		}
}
		System.exit( EXIT_SUCCESS );
		return;
	}
}