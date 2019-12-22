package examples;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

import opusfile.JOggOpusFile;
import opusfile.JOpusHead;
import opusfile.JOpusPictureTag;
import opusfile.JOpusServerInfo;
import opusfile.JOpusTags;

/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE libopusfile SOFTWARE CODEC SOURCE CODE. *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE libopusfile SOURCE CODE IS (C) COPYRIGHT 1994-2012           *
 * by the Xiph.Org Foundation and contributors http://www.xiph.org/ *
 *                                                                  *
 ********************************************************************/

// opusfile_example.c

public final class Jopusfile_example {
	private static final String CLASS_NAME = "Jopusfile_example";
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;

	@SuppressWarnings("boxing")
	private static final void print_duration( final PrintStream _fp, long _nsamples, final int _frac ) {
		long seconds;
		long minutes;
		long hours;
		long days;
		long weeks;
		_nsamples += _frac != 0 ? 24 : 24000;
		seconds = _nsamples / 48000;
		_nsamples -= seconds * 48000;
		minutes = seconds / 60;
		seconds -= minutes * 60;
		hours = minutes / 60;
		minutes -= hours * 60;
		days = hours / 24;
		hours -= days * 24;
		weeks = days / 7;
		days -= weeks * 7;
		if( weeks != 0 ) {
			_fp.printf("%dw", weeks );
		}
		if( weeks != 0 || days != 0 ) {
			_fp.printf("%dd", days );
		}
		if( weeks != 0 || days != 0 || hours != 0 ) {
			if( weeks != 0 || days != 0 ) {
				_fp.printf("%02dh", hours );
			} else {
				_fp.printf("%dh", hours );
			}
		}
		if( weeks != 0 || days != 0 || hours != 0 || minutes != 0 ) {
			if( weeks != 0 || days != 0 || hours != 0 ) {
				_fp.printf("%02dm", minutes );
			} else {
				_fp.printf("%dm", minutes );
			}
			_fp.printf("%02d", seconds );
		} else {
			_fp.printf("%d", seconds );
		}
		if( _frac != 0 ) {
			_fp.printf(".%03d", (_nsamples / 48) );
		}
		_fp.printf("s" );
	}

	@SuppressWarnings("boxing")
	private static final void print_size( final PrintStream _fp, final long _nbytes, final int _metric, final String _spacer ) {
		final char SUFFIXES[/* 7 */] = {' ', 'k', 'M', 'G', 'T', 'P', 'E'};
		long val;
		long den;
		long round;
		int        base;
		int        shift;
		base = _metric != 0 ? 1000 : 1024;
		round = 0;
		den = 1;
		for( shift = 0; shift < 6; shift++ ) {
			if( _nbytes < den * base - round ) {
				break;
			}
			den *= base;
			round = den >> 1;
		}
		val = ( _nbytes + round ) / den;
		if( den > 1 && val < 10 ) {
			if( den >= 1000000000 ) {
				val = (_nbytes + (round / 100)) / (den / 100);
			} else {
				val = (_nbytes * 100 + round) / den;
			}
			_fp.printf("%d.%02d%s%c", (val / 100), (val % 100), _spacer, SUFFIXES[shift] );
		}
		else if( den > 1 && val < 100 ) {
			if( den >= 1000000000 ) {
				val = (_nbytes + (round / 10)) / (den / 10);
			} else {
				val = (_nbytes * 10 + round) / den;
			}
			_fp.printf("%d.%d%s%c", (val / 10),(val % 10), _spacer, SUFFIXES[shift] );
		} else {
			_fp.printf("%d%s%c", val, _spacer, SUFFIXES[shift] );
		}
	}

	private static final void put_le32( final byte[] _dst, int doffset, final int _x ) {
		_dst[doffset++] = (byte)_x;// ( _x & 0xFF );
		_dst[doffset++] = (byte)(_x >> 8);//( _x >> 8 & 0xFF );
		_dst[doffset++] = (byte)(_x >> 16);//( _x >> 16 & 0xFF );
		_dst[doffset++] = (byte)(_x >> 24);//( _x >> 24 & 0xFF );
	}

	/** The chunk sizes are set to 0x7FFFFFFF by default.
	Many,  though not all,  programs will interpret this to mean the duration is
	"undefined",  and continue to read from the file so long as there is actual
	data. */
	private static final byte WAV_HEADER_TEMPLATE[/* 44 */] = {
		'R', 'I', 'F', 'F', (byte)0xFF, (byte)0xFF, (byte)0xFF, 0x7F,
		'W', 'A', 'V', 'E', 'f', 'm', 't', ' ',
		0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00,
		(byte)0x80, (byte)0xBB, 0x00, 0x00, 0x00, (byte)0xEE, 0x02, 0x00,
		0x04, 0x00, 0x10, 0x00, 'd', 'a', 't', 'a',
		(byte)0xFF, (byte)0xFF, (byte)0xFF, 0x7F
	};
	/** Make a header for a 48 kHz,  stereo,  signed,  16 - bit little - endian PCM WAV. */
	private static final void make_wav_header( final byte _dst[/* 44 */], final long _duration ) {
		System.arraycopy( WAV_HEADER_TEMPLATE, 0, _dst, 0, WAV_HEADER_TEMPLATE.length );
		if( _duration > 0 ) {
			if( _duration > 0x1FFFFFF6 ) {
				System.err.printf("WARNING: WAV output would be larger than 2 GB.\n");
				System.err.printf("Writing non-standard WAV header with invalid chunk sizes.\n" );
			}
			else{
				final long audio_size = _duration << 2;
				put_le32( _dst, 4, (int)(audio_size + 36) );
				put_le32( _dst, 40, (int)(audio_size) );
			}
		}
	}

	/**
	 * The program entry point.
	 *
	 * @param args
	 */
	@SuppressWarnings("boxing")
	public static final void main(final String[] args) {// int main( final int _argc, const char **_argv ) {
		final int[] ret = new int[1];
// #if defined( _WIN32 )
		// win32_utf8_setup( &_argc, &_argv );
// #endif
		if( args.length != 2 + 1 - 1 ) {// java -1, no path, +1, output file name
			System.err.printf("Usage: %s <file.opus> <pcmfile.wav>\n", CLASS_NAME );

			System.exit( EXIT_FAILURE );
			return;
		}
		JOggOpusFile of;
		boolean is_ssl = false;
		if( args[1 - 1].compareTo("-") == 0 ) {// java -1, no path
			// final JOpusFileCallbacks cb = new JOpusFileCallbacks( null, null, null, null );
			// of = JOggOpusFile.op_open_callbacks( Jstream.op_fdopen( cb, System.in, "r" ), cb, null, 0, ret );
			// java: to read data from stdin need write JOpusInputStream extends JOpusFileCallbacks
			System.exit( EXIT_FAILURE );
			return;
		}
		else {
			final JOpusServerInfo info = new JOpusServerInfo();
			/*Try to treat the argument as a URL.*/
			of = JOpusServerInfo.op_open_url( args[1 - 1], ret, JOpusServerInfo.OP_GET_SERVER_INFO, info, null );// java -1, no path
/* #if 0
			if( of == NULL ) {
				OpusFileCallbacks cb = {NULL, NULL, NULL, NULL};
				void *fp;
				// For debugging: force a file to not be seekable.
				fp = op_fopen( &cb, _argv[1], "rb" );
				cb.seek = NULL;
				cb.tell = NULL;
				of = op_open_callbacks( fp, &cb, NULL, 0, NULL );
			}
#else */
			if( of == null ) {
				of = JOggOpusFile.op_open_file( args[1 - 1], ret );
			} else {
				if( info.name != null ) {
					System.err.printf("Station name: %s\n", info.name );
				}
				if( info.description != null ) {
					System.err.printf("Station description: %s\n", info.description );
				}
				if( info.genre != null ) {
					System.err.printf("Station genre: %s\n", info.genre );
				}
				if( info.url != null ) {
					System.err.printf("Station homepage: %s\n", info.url );
				}
				if( info.bitrate_kbps >= 0 ) {
					System.err.printf("Station bitrate: %d kbps\n", info.bitrate_kbps );
				}
				if( info.is_public >= 0 ) {
					System.err.printf("%s\n", info.is_public != 0 ? "Station is public." : "Station is private." );
				}
				if( info.server != null ) {
					System.err.printf("Server software: %s\n", info.server );
				}
				if( info.content_type != null ) {
					System.err.printf("Content-Type: %s\n", info.content_type );
				}
				is_ssl = info.is_ssl;
				info.opus_server_info_clear();
			}
		}
		if( of == null ) {
			System.err.printf("Failed to open file '%s': %d\n", args[1 - 1], ret[0] );// java -1, no path
			System.exit( EXIT_FAILURE );
			return;
		}
		RandomAccessFile streamout = null;
		try {
			streamout = new RandomAccessFile( args[2 - 1], "rw" );
		} catch( final FileNotFoundException e ) {
			System.err.printf("Failed to open file '%s': %s\n", args[1 - 1], e.getMessage() );// java -1, no path
			of.op_free();// closing input stream
			System.exit( EXIT_FAILURE );
			return;
		}// java
		long duration = 0;
		boolean output_seekable = true;
		try { streamout.seek( 0 ); } catch(final IOException ie) { output_seekable = false; }// java changed
		if( of.op_seekable() ) {
			System.err.printf("Total number of links: %d\n", of.op_link_count() );
			duration = of.op_pcm_total( -1 );
			System.err.printf("Total duration: " );
			print_duration( System.err, duration, 3 );
			System.err.printf("(%d samples @ 48 kHz)\n", duration );
			final long size = of.op_raw_total( -1 );
			System.err.printf("Total size: ");
			print_size( System.err, size, 0, "");
			System.err.printf("\n");
		}
		else if( ! output_seekable ) {
			System.err.printf("WARNING: Neither input nor output are seekable.\n" );
			System.err.printf("Writing non-standard WAV header with invalid chunk sizes.\n" );
		}
		final byte wav_header[] = new byte[44];
		make_wav_header( wav_header, duration );
		try {
			streamout.write( wav_header );
		} catch(final IOException ie) {
			try { streamout.close(); } catch( final IOException e ) {}
			streamout = null;
			System.err.printf("Error writing WAV header: %s\n", ie.getMessage() );
			of.op_free();// closing input stream
			System.exit( EXIT_FAILURE );
			return;
		}
		int prev_li = -1;
		long nsamples = 0;
		long pcm_offset = of.op_pcm_tell();
		if( pcm_offset != 0 ) {
			System.err.printf("Non-zero starting PCM offset: %d\n", pcm_offset );
		}
		long pcm_print_offset = pcm_offset - 48000;
		long bitrate = 0;
		final int[] binary_suffix_len = new int[1];// java
		final short pcm[] = new short[120 * 48 * 2];// java moved up
		final byte out[] = new byte[120 * 48 * 2 * 2];// java moved up
		for( ; ; ) {
			/*Although we would generally prefer to use the float interface, WAV
			files with signed, 16-bit little-endian samples are far more
			universally supported, so that's what we output.*/
			ret[0] = of.op_read_stereo( pcm, pcm.length );
			if( ret[0] == JOggOpusFile.OP_HOLE ) {
				System.err.printf("\nHole detected! Corrupt file segment?\n");
				continue;
			} else if( ret[0] < 0 ) {
				System.err.printf("\nError decoding '%s': %d\n", args[1 - 1], ret[0] );// java -1, no path
				if( is_ssl ) {
					System.err.printf("Possible truncation attack?\n" );
				}
				ret[0] = EXIT_FAILURE;
				break;
			}
			final int li = of.op_current_link();
			if( li != prev_li ) {
				/*We found a new link.
				Print out some information.*/
				System.err.printf("Decoding link %d:                          \n", li );
				final JOpusHead head = of.op_head( li );
				System.err.printf("  Channels: %d\n", head.channel_count );
				if( of.op_seekable() ) {
					// ogg_int64_t duration;// FIXME duration already has declared
					final long dur = of.op_pcm_total( li );
					System.err.printf("  Duration: " );
					print_duration( System.err, dur, 3 );
					System.err.printf("(%d samples @ 48 kHz)\n", dur );
					final long size = of.op_raw_total( li );
					System.err.printf("  Size: " );
					print_size( System.err, size, 0, "" );
					System.err.printf("\n" );
				}
				if( head.input_sample_rate != 0 ) {
					System.err.printf("  Original sampling rate: %d Hz\n", head.input_sample_rate );
				}
				final JOpusTags tags = of.op_tags( li );
				System.err.printf("  Encoded by: %s\n", tags.vendor );
				for( int ci = 0; ci < tags.comments; ci++ ) {
					final byte[] comment = tags.user_comments[ci];
					if( JOpusTags.opus_tagncompare( "METADATA_BLOCK_PICTURE".getBytes(), 22, comment ) == 0 ) {
						final JOpusPictureTag pic = new JOpusPictureTag();
						final int err = pic.opus_picture_tag_parse( comment );
						System.err.printf("  %023s", new String( comment, 0, comment.length < 23 ? comment.length : 23, Charset.forName("ASCII") ) );
						if( err >= 0 ) {
							System.err.printf("%d|%s|%s|%d%d%d", pic.type, pic.mime_type, new String( pic.description, Charset.forName("UTF-8") ), pic.width, pic.height, pic.depth );
							if( pic.colors != 0 ) {
								System.err.printf("/%d", pic.colors );
							}
							if( pic.format == JOpusPictureTag.OP_PIC_FORMAT_URL ) {
								System.err.printf("|%s\n", new String( pic.data, Charset.forName("ASCII") ) );
							}
							else {
								System.err.printf("|<%d bytes of image data>\n", pic.data_length );
							}
							pic.opus_picture_tag_clear();
						} else {
							System.err.printf("<error parsing picture tag>\n" );
						}
					} else {
						System.err.printf("  %s\n", new String( tags.user_comments[ci], Charset.forName("UTF-8") ) );
					}
				}
				if( tags.opus_tags_get_binary_suffix( binary_suffix_len ) != null ) {
					System.err.printf("<%d bytes of unknown binary metadata>\n", binary_suffix_len[0] );
				}
				System.err.printf("\n" );
				if( ! of.op_seekable() ) {
					pcm_offset = of.op_pcm_tell() - (long)ret[0];
					if( pcm_offset != 0 ) {
						System.err.printf("Non-zero starting PCM offset in link %d: %d\n", li, pcm_offset );
					}
				}
			}
			if( li != prev_li || pcm_offset >= pcm_print_offset + 48000 ) {
				final int next_bitrate = of.op_bitrate_instant();
				if( next_bitrate >= 0 ) {
					bitrate = next_bitrate;
				}
				final long raw_offset = of.op_raw_tell();
				System.err.printf("\r ");
				print_size( System.err, raw_offset, 0, "");
				System.err.printf("  ");
				print_duration( System.err, pcm_offset, 0 );
				System.err.printf("  (");
				print_size( System.err, bitrate, 1, " ");
				System.err.printf("bps)                    \r");
				pcm_print_offset = pcm_offset;
				System.err.flush();
			}
			final long next_pcm_offset = of.op_pcm_tell();
			if( pcm_offset + ret[0] != next_pcm_offset ) {
				System.err.printf("\nPCM offset gap! %d + %d != %d\n", pcm_offset, ret[0], next_pcm_offset );
			}
			pcm_offset = next_pcm_offset;
			if( ret[0] <= 0 ) {
				ret[0] = EXIT_SUCCESS;
				break;
			}
			/*Ensure the data is little - endian before writing it out.*/
			for( int si = 0, si2 = 0, count = ret[0] << 1; si < count; si++ ) {
				out[si2++] = (byte)pcm[si];// ( pcm[si] & 0xFF );
				out[si2++] = (byte)(pcm[si] >> 8);// ( pcm[si] >> 8 & 0xFF );
			}
			try {
				streamout.write( out, 0, ret[0] << 2 );
			} catch(final IOException ie) {
				System.err.printf("\nError writing decoded audio data: %s\n", ie.getMessage() );
				ret[0] = EXIT_FAILURE;
				break;
			}
			nsamples += ret[0];
			prev_li = li;
		}
		if( ret[0] == EXIT_SUCCESS ) {
			System.err.printf("\nDone: played " );
			print_duration( System.err, nsamples, 3 );
			System.err.printf("(%d samples @ 48 kHz).\n", nsamples );
		}
		if( of.op_seekable() && nsamples != duration ) {
			System.err.printf("\nWARNING: Number of output samples does not match declared file duration.\n" );
			if( ! output_seekable ) {
				System.err.printf("Output WAV file will be corrupt.\n" );
			}
		}
		if( output_seekable && nsamples != duration ) {
			make_wav_header( wav_header, nsamples );
			try {
				streamout.seek( 0 );
				streamout.write( wav_header );
			} catch(final IOException ie) {
				System.err.printf("Error rewriting WAV header: %s\n", ie.getMessage() );
				ret[0] = EXIT_FAILURE;
			}
		}
		try { streamout.close(); } catch( final IOException e ) {}
		of.op_free();// closing input stream
		System.exit( ret[0] );
		return;
	}
}