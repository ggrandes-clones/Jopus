package examples;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;

import opusfile.JOggOpusFile;
import opusfile.JOpusFileCallbacks;
import opusfile.JOpusFileInputStream;
import opusfile.JOpusServerInfo;

/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE libopusfile SOFTWARE CODEC SOURCE CODE. *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD - STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE libopusfile SOURCE CODE IS (C) COPYRIGHT 1994 - 2012           *
 * by the Xiph.Org Foundation and contributors http://www.xiph.org/ *
 *                                                                  *
 ********************************************************************/

// seeking_example.c

public final class Jseeking_example {
	private static final String CLASS_NAME = "Jseeking_example";
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;
	private static final int RAND_MAX = 0x7fff + 1;

	/*Use shorts, they're smaller.*/
/* #if !defined( OP_FIXED_POINT )
# define OP_FIXED_POINT ( 1 )
#endif */
/*
#if defined( OP_FIXED_POINT )
	private static final int MATCH_TOL = 16384;
	private static final boolean MATCH( int _a, int _b ) {
		_a -= _b;
		if( _a < 0 ) _a = -_a;
		return _a < MATCH_TOL;
	}
#else
*/
	/*TODO: The convergence after 80 ms of preroll is far from exact.
	Our comparison is very rough.
	Need to find some way to do this better.*/
	private static final float MATCH_TOL = 16384f / 32768;

	private static final boolean MATCH( float _a, final float _b ) {
		_a -= _b;
		if( _a < 0 ) {
			_a = -_a;
		}
		return _a < MATCH_TOL;
	}

// # if defined( OP_WRITE_SEEK_SAMPLES )
	/** Matrices for downmixing from the supported channel counts to stereo. */
/*	private static final float DOWNMIX_MATRIX[][][] = {// [8][8][2] = {
		// mono
		{
			{1.F, 1.F}
		},
		// stereo
		{
			{1.F, 0.F}, {0.F, 1.F}
		},
		// 3.0
		{
			{0.5858F, 0.F}, {0.4142F, 0.4142F}, {0, 0.5858F}
		},
		// quadrophonic
		{
			{0.4226F, 0.F}, {0, 0.4226F}, {0.366F, 0.2114F}, {0.2114F, 0.336F}
		},
		// 5.0
		{
			{0.651F, 0.F}, {0.46F, 0.46F}, {0, 0.651F}, {0.5636F, 0.3254F}, {0.3254F, 0.5636F}
		},
		// 5.1
		{
			{0.529F, 0.F}, {0.3741F, 0.3741F}, {0.F, 0.529F}, {0.4582F, 0.2645F},
			{0.2645F, 0.4582F}, {0.3741F, 0.3741F}
		},
		// 6.1
		{
			{0.4553F, 0.F}, {0.322F, 0.322F}, {0.F, 0.4553F}, {0.3943F, 0.2277F},
			{0.2277F, 0.3943F}, {0.2788F, 0.2788F}, {0.322F, 0.322F}
		},
		// 7.1
		{
			{0.3886F, 0.F}, {0.2748F, 0.2748F}, {0.F, 0.3886F}, {0.3366F, 0.1943F},
			{0.1943F, 0.3366F}, {0.3366F, 0.1943F}, {0.1943F, 0.3366F}, {0.2748F, 0.2748F}
		}
	};

	private static void write_samples( final float[] _samples, final int _nsamples, final int _nchannels ) {
		final float stereo_pcm[] = new float[120 * 48 * 2];
		for( int i = 0; i < _nsamples; i++ ) {
			float l, r;
			l = r = 0.F;
			for( int ci = 0; ci < _nchannels; ci++ ) {
				l += DOWNMIX_MATRIX[_nchannels - 1][ci][0] * _samples[i * _nchannels + ci];
				r += DOWNMIX_MATRIX[_nchannels - 1][ci][1] * _samples[i * _nchannels + ci];
			}
			stereo_pcm[2 * i + 0] = l;
			stereo_pcm[2 * i + 1] = r;
		}
		fwrite( stereo_pcm, sizeof( *stereo_pcm ) * 2, _nsamples, stdout );
	}
// # endif

// #endif
*/
	private static long nfailures;

	@SuppressWarnings("boxing")
	private static final void verify_seek( final JOggOpusFile _of, final long _byte_offset,
			final long _pcm_offset, final long _pcm_length, final float[] _bigassbuffer ) {
		int bigassbuffer_off = 0;// java
		final long byte_offset = _of.op_raw_tell();
		if( _byte_offset !=  -1 && byte_offset < _byte_offset ) {
			System.err.printf("\nRaw position out of tolerance: requested %d, got %d.\n", _byte_offset, byte_offset );
			nfailures++;
		}
		long pcm_offset = _of.op_pcm_tell();
		if( _pcm_offset !=  -1 && pcm_offset > _pcm_offset ) {
			System.err.printf("\nPCM position out of tolerance: requested %d, got %d.\n", _pcm_offset, pcm_offset );
			nfailures++;
		}
		if( pcm_offset < 0 || pcm_offset > _pcm_length ) {
			System.err.printf("\nPCM position out of bounds: got %d.\n", pcm_offset );
			nfailures++;
		}
		final float[] buffer = new float[120 * 48 * 8];
		final int[] li = new int[1];// java changed
		final int nsamples = _of.op_read_native( buffer, 0, buffer.length, li );
		if( nsamples < 0 ) {
			System.err.printf("\nFailed to read PCM data after seek: %d\n", nsamples );
			nfailures++;
			li[0] = _of.op_current_link();
		}
		long duration;
		for( int lj = 0; lj < li[0]; lj++ ) {
			duration = _of.op_pcm_total( lj );
			if( 0 <= pcm_offset && pcm_offset < duration ) {
				System.err.printf("\nPCM data after seek came from the wrong link: expected %d, got %d.\n", lj, li );
				nfailures++;
			}
			pcm_offset -= duration;
			if( _bigassbuffer != null ) {
				bigassbuffer_off += _of.op_channel_count( lj ) * duration;
			}
		}
		duration = _of.op_pcm_total( li[0] );
		if( pcm_offset + nsamples > duration ) {
			System.err.printf("\nPCM data after seek exceeded link duration: limit %d, got %d.\n", duration, (pcm_offset + nsamples) );
			nfailures++;
		}
		final int nchannels = _of.op_channel_count( li[0] );
		if( _bigassbuffer != null ) {
			for( int i = 0, samples_count = nsamples * nchannels; i < samples_count; i++ ) {
				if( ! MATCH( buffer[i], _bigassbuffer[bigassbuffer_off + (int)(pcm_offset * nchannels) + i] ) ) {
					System.err.printf("\nData after seek doesn't match declared PCM position: mismatch %e\n",
							buffer[i] - _bigassbuffer[bigassbuffer_off + (int)(pcm_offset * nchannels) + i] );
					for( long j = 0, je = duration - nsamples; j < je; j++ ) {
						for( i = 0; i < samples_count; i++ ) {
							if( ! MATCH( buffer[i], _bigassbuffer[bigassbuffer_off + (int)(j * nchannels) + i] ) ) {
								break;
							}
						}
						if( i == samples_count ) {
							System.err.printf("\nData after seek appears to match position %d.\n", i );
						}
					}
					nfailures++;
					break;
				}
			}
		}
/* #if defined( OP_WRITE_SEEK_SAMPLES )
		write_samples( buffer, nsamples, nchannels );
#endif */
	}

	/** A simple wrapper that lets us count the number of underlying seek calls. */
	private static final class Jcounter extends JOpusFileCallbacks {
		private final JOpusFileCallbacks mStream;
		private Jcounter(final JOpusFileCallbacks stream) {
			super( stream.mIsRead, stream.mIsSeek, stream.mIsTell, stream.mIsClose );
			mStream = stream;
		}

		@Override
		public int read(final byte[] _ptr, final int poffset, final int _nbytes) throws IOException {
			return mStream.read( _ptr, poffset, _nbytes );
		}

		@Override
		public void seek(final long _offset, final int _whence) throws IOException {
		// private static final int seek_stat_counter( void *_stream, long _offset, int _whence ) {
				if( _whence == JOpusFileCallbacks.SEEK_SET ) {
					nreal_seeks++;
				} else if( _offset != 0 ) {
					nreal_seeks++;
				}
				mStream.seek( _offset, _whence );
		//}
		}

		@Override
		public long tell() throws IOException {
			return mStream.tell();
		}

		@Override
		public void close() throws IOException {
			mStream.close();
		}
	}
	// private static op_seek_func real_seek;

	private static long nreal_seeks;

	private static final int NSEEK_TESTS = 1000;

	@SuppressWarnings("boxing")
	private static final void print_duration( final PrintStream _fp, long _nsamples ) {
		long seconds = _nsamples / 48000;
		_nsamples -= seconds * 48000;
		long minutes = seconds / 60;
		seconds -= minutes * 60;
		long hours = minutes / 60;
		minutes -= hours * 60;
		long days = hours / 24;
		hours -= days * 24;
		final long weeks = days / 7;
		days -= weeks * 7;
		if( 0 != weeks ) {
			_fp.printf("%dw", weeks );
		}
		if( 0 != weeks || 0 != days ) {
			_fp.printf("%dd", days );
		}
		if( 0 != weeks || 0 != days || 0 != hours ) {
			if( 0 != weeks || 0 != days ) {
				_fp.printf("%02dh", hours );
			} else {
				_fp.printf("%dh", (int)hours );
			}
		}
		if( 0 != weeks || 0 != days || 0 != hours || 0 != minutes ) {
			if( 0 != weeks || 0 != days || 0 != hours ) {
				_fp.printf("%02dm", minutes );
			} else {
				_fp.printf("%dm", minutes );
			}
			_fp.printf("%02d", seconds );
		} else {
			_fp.printf("%d", seconds );
		}
		_fp.printf(".%03ds", (_nsamples + 24) / 48 );
	}

	/**
	 * The program entry point.
	 *
	 * @param args
	 */
	@SuppressWarnings({ "boxing", "unused" })
	public static final void main( final String[] args ) {// ( final int _argc, const char **_argv ) {
		// void              *fp;// java replaced by JOpusFileCallbacks cb
		if( args.length != 2 - 1 ) {// java -1, no path
			System.err.printf("Usage: %s <file.opus>\n", CLASS_NAME );
			System.exit( EXIT_FAILURE );
			return;
		}
		// memset( &cb, 0, sizeof( cb ) );
		JOpusFileCallbacks cb;
		JOggOpusFile of;
		if( args[1 - 1].compareTo("-") == 0 ) {// java -1, no path
			// fp = JOggOpusFile.op_fdopen( &cb, fileno( stdin ), "rb" );
			// java: to read data from stdin need write JOpusInputStream extends JOpusFileCallbacks
			System.exit( EXIT_FAILURE );
			return;
		} else {
			/*Try to treat the argument as a URL.*/
			// fp = op_url_stream_create(&cb, _argv[1], OP_SSL_SKIP_CERTIFICATE_CHECK(1), NULL );
			cb = JOpusServerInfo.op_url_stream_create( args[1 - 1], JOpusServerInfo.OP_SSL_SKIP_CERTIFICATE_CHECK, 1, null );// java -1, no path
			/*Fall back assuming it's a regular file name.*/
			if( cb /* fp */ == null ) {
				// fp = op_fopen( cb, args[1], "rb" );/
				try {
					cb = new JOpusFileInputStream( args[1 - 1], "r" );// java -1, no path
				} catch( final FileNotFoundException e ) {
					System.err.printf("Failed to open file '%s'.\n", args[1 - 1] );// java -1, no path
					System.exit( EXIT_FAILURE );
					return;
				}
			}
		}
		if( cb.mIsSeek ) {
			// real_seek = cb.seek;
			// cb.seek = seek_stat_counter;
			cb = new Jcounter( cb );
		}
		of = JOggOpusFile.op_open_callbacks( /* fp, */cb, null, 0, null );
		if( of == null ) {
			System.err.printf("Failed to open file '%s'.\n", args[1 - 1] );// java -1, no path
			System.exit( EXIT_FAILURE );
			return;
		}
		if( of.op_seekable() ) {
			/*Because we want to do sample - level verification that the seek does what
			it claimed, decode the entire file into memory.*/
			final int nlinks = of.op_link_count();
			System.err.printf("Opened file containing %d links with %d seeks (%.3f per link) .\n",
					nlinks, nreal_seeks, nreal_seeks / (double)nlinks );
			/*Reset the seek counter.*/
			nreal_seeks = 0;
			long nsamples;
if( false ) {// FIXME nsamples never used
			nsamples = 0;
			for( int i = 0; i < nlinks; i++ ) {
				nsamples += of.op_pcm_total( i ) * of.op_channel_count( i );
			}
}
			/*Until we find another way to do the comparisons that solves the MATCH_TOL
			problem, disable this.*/
/* #if 0
			float[] bigassbuffer = new float[ nsamples ];
			if( bigassbuffer == null ) {
				System.err.printf("Buffer allocation failed. Seek offset detection disabled.\n" );
			}
#else */
			float[] bigassbuffer = null;
// #endif
			long pcm_offset = of.op_pcm_tell();
			if( pcm_offset != 0 ) {
				System.err.printf("Initial PCM offset was not 0, got %d instead.!\n", pcm_offset );
				nfailures++;
			}
			/*Disabling the linear scan for now.
			Only test on non - borken files!*/
if( false ) {
			{
				final int[] li = new int[1];// java
				final float[] smallerbuffer = new float[120 * 48 * 8];
				long pcm_print_offset = pcm_offset - 48000;
				int bitrate = 0;
				boolean saw_hole = false;
				for( long si = 0; si < nsamples; ) {
					final float[] buffer = bigassbuffer == null ? smallerbuffer : bigassbuffer;// java
					final int buf = bigassbuffer == null ? 0 : (int)si;// java changed buffer[ buf ]
					int buf_size = (int)(nsamples - si);
					buf_size = ( buf_size <= smallerbuffer.length ? buf_size : smallerbuffer.length );
					final int ret = of.op_read_native( buffer, buf, buf_size, li );
					if( ret == JOggOpusFile.OP_HOLE ) {
						/*Only warn once in a row.*/
						if( saw_hole ) {
							continue;
						}
						saw_hole = true;
						/*This is just a warning.
						As long as the timestamps are still contiguous we're okay.*/
						System.err.printf("\nHole in PCM data at sample %d\n", pcm_offset );
						continue;
					}
					else if( ret <= 0 ) {
						System.err.printf("\nFailed to read PCM data: %d\n", ret );
						System.exit( EXIT_FAILURE );
						return;
					}
					saw_hole = false;
					/*If we have gaps in the PCM positions, seeking is not likely to work
					near them.*/
					final long next_pcm_offset = of.op_pcm_tell();
					if( pcm_offset + ret != next_pcm_offset ) {
						System.err.printf("\nGap in PCM offset: expecting %d, got %d\n",
								(pcm_offset + ret), next_pcm_offset );
						nfailures++;
					}
					pcm_offset = next_pcm_offset;
					si += ret * of.op_channel_count( li[0] );
					if( pcm_offset >= pcm_print_offset + 48000 ) {
						final int next_bitrate = of.op_bitrate_instant();
						if( next_bitrate >= 0 ) {
							bitrate = next_bitrate;
						}
						System.err.printf("\r%s... [%d left] (%0.3f kbps)                ",
								bigassbuffer == null ? "Scanning" : "Loading", nsamples - si, bitrate / 1000.0 );
						pcm_print_offset = pcm_offset;
					}
				}
				final int ret = of.op_read_native( smallerbuffer, 0, 8, li );
				if( ret < 0 ) {
					System.err.printf("Failed to read PCM data: %d\n", ret );
					nfailures++;
				}
				if( ret > 0 ) {
					System.err.printf("Read too much PCM data!\n" );
					nfailures++;
				}
			}
}
			final Random rand = new Random();// java
			final long pcm_length = of.op_pcm_total( -1 );
			final long size = of.op_raw_total( -1 );
			System.err.printf("\rLoaded ( %.3f kbps average ) .                        \n", of.op_bitrate( -1 ) / 1000.0 );
			System.err.printf("Testing raw seeking to random places in %d bytes...\n", size );
			long max_seeks = 0;
			for( int i = 0; i < NSEEK_TESTS; i++ ) {
				long nseeks_tmp = nreal_seeks;
				final long byte_offset = (long)(rand.nextInt( RAND_MAX ) / (double) RAND_MAX * size);
				System.err.printf("\r\t%3d [raw position %d]...                ", i, byte_offset );
				final int ret = of.op_raw_seek( byte_offset );
				if( ret < 0 ) {
					System.err.printf("\nSeek failed: %d.\n", ret );
					nfailures++;
				}
				if( i == 28 ) {
					i = 28;
				}
				verify_seek( of, byte_offset,  - 1, pcm_length, bigassbuffer );
				nseeks_tmp = nreal_seeks - nseeks_tmp;
				max_seeks = nseeks_tmp > max_seeks ? nseeks_tmp : max_seeks;
			}
			System.err.printf("\rTotal seek operations: %d (%.3f per raw seek, %d maximum).\n",
								nreal_seeks, nreal_seeks / (double) NSEEK_TESTS, max_seeks );
			nreal_seeks = 0;
			System.err.printf("Testing exact PCM seeking to random places in %d samples (", pcm_length );
			print_duration( System.err, pcm_length );
			System.err.printf(")...\n" );
			max_seeks = 0;
			for( int i = 0; i < NSEEK_TESTS; i++ ) {
				long nseeks_tmp = nreal_seeks;
				pcm_offset = (long) (rand.nextInt( RAND_MAX ) / (double)RAND_MAX * pcm_length);
				System.err.printf("\r\t%3d [PCM position %d]...                ", i, pcm_offset );
				final int ret = of.op_pcm_seek( pcm_offset );
				if( ret < 0 ) {
					System.err.printf("\nSeek failed: %d.\n", ret );
					nfailures++;
				}
				final long pcm_offset2 = of.op_pcm_tell();
				if( pcm_offset != pcm_offset2 ) {
					System.err.printf("\nDeclared PCM position did not perfectly match request: requested %d, got %d.\n",
							pcm_offset, pcm_offset2 );
					nfailures++;
				}
				verify_seek( of, -1, pcm_offset, pcm_length, bigassbuffer );
				nseeks_tmp = nreal_seeks - nseeks_tmp;
				max_seeks = nseeks_tmp > max_seeks ? nseeks_tmp : max_seeks;
			}
			System.err.printf("\rTotal seek operations: %d (%.3f per exact seek, %d maximum).\n",
							nreal_seeks, nreal_seeks / (double)NSEEK_TESTS, max_seeks );
			nreal_seeks = 0;
			System.err.printf("OK.\n" );
			bigassbuffer = null;
		}
		else {
			System.err.printf("Input was not seekable.\n" );
			System.exit( EXIT_FAILURE );
			return;
		}
		of.op_free();// closing input stream
		if( nfailures > 0 ) {
			System.err.printf("FAILED: %d failure conditions encountered.\n", nfailures );
		}
		if( nfailures != 0 ) {
			System.exit( EXIT_FAILURE );
		} else {
			System.exit( EXIT_SUCCESS );
		}
		return;
	}
}