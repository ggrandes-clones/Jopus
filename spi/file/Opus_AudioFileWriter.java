package spi.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.AudioFileWriter;

import celt.Jcelt;
import libogg.Jogg_packet;
import libogg.Jogg_page;
import libogg.Jogg_stream_state;
import opus.JOpusMSEncoder;
import opus.Jms_encoder_data_aux;
import opus.Jopus_defines;

/**
 * Reading-Writing scheme:
 * <p>Audio Reader -> [Mixer] -> Opus_AudioFileWriter -> [Resampler] -> Encoder
 */
public class Opus_AudioFileWriter extends AudioFileWriter implements FloatInputStream {
	private final Type[] OPUS = { new Type("Opus", "opus") };
	/** Opus header version */
	private static final byte VERSION = 1;

	@Override
	public Type[] getAudioFileTypes() {
		return OPUS;
	}

	@Override
	public Type[] getAudioFileTypes(final AudioInputStream stream) {
		return OPUS;
	}

	private static final int sWavPermuteMatrixes[][] =// [8][8] =
		{
			{ 0 },                     // 1.0 mono
			{ 0, 1 },                  // 2.0 stereo
			{ 0, 2, 1 },               // 3.0 channel ('wide') stereo
			{ 0, 1, 2, 3 },            // 4.0 discrete quadraphonic
			{ 0, 2, 1, 3, 4 },         // 5.0 surround
			{ 0, 2, 1, 4, 5, 3 },      // 5.1 surround
			{ 0, 2, 1, 5, 6, 4, 3 },   // 6.1 surround
			{ 0, 2, 1, 6, 7, 4, 5, 3 } // 7.1 surround (classic theater 8-track)
		};
	private static final class AudioReader implements FloatInputStream {
		private final int mSampleSizeInBits;
		private final int mChannels;
		private final int mSampleStride;
		private final int[] mChannelPermuteMatrix;
		// private final long mTotalSamples;
		// private long mSamplesRead;
		private final boolean mIsUnsigned8bit;
		private final boolean mIsBigEndian;
		private AudioInputStream mInStream;
		//
		private AudioReader(final AudioInputStream in) throws IOException {
			mInStream = in;
			int v = in.getFormat().getSampleSizeInBits();
			mSampleSizeInBits = v == AudioSystem.NOT_SPECIFIED ? 16 : v;
			if( mSampleSizeInBits != 8 && mSampleSizeInBits != 16 && mSampleSizeInBits != 24 ) {
				throw new IOException("Internal error: attempt to read unsupported bitdepth " + Integer.toString( mSampleSizeInBits ) );
			}
			//
			v = in.getFormat().getChannels();
			mChannels = v == AudioSystem.NOT_SPECIFIED ? 2 : v;
			mSampleStride = (mSampleSizeInBits >> 3) * mChannels;
			// mTotalSamples = total_samples_per_channel;
			// mSamplesRead = 0;
			mIsUnsigned8bit = in.getFormat().getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED;
			//
			mIsBigEndian = in.getFormat().isBigEndian();
			if( mIsBigEndian && mSampleSizeInBits == 24 ) {
				throw new IOException("Big endian 24 bit PCM data is not currently supported, aborting.");
			}
			//
			if( mChannels <= 8 ) {
				// Where we know the mappings, use them.
				mChannelPermuteMatrix = sWavPermuteMatrixes[ mChannels - 1 ];
				return;
			}
			mChannelPermuteMatrix = new int[ mChannels ];
			// Use a default 1-1 mapping
			for( int i = 0; i < mChannels; i++ ) {
				mChannelPermuteMatrix[i] = i;
			}
		}

		@Override
		public int audio_read( final float[] buffer, final int offset, final int samples) throws IOException {
			final int channel_count = mChannels;
			final int stride = mSampleStride;

			// wavfile *f = (wavfile *)in;
			// int sampbyte = this.samplesize >>> 3;// java: stride = sampbyte * this.channels
			// int realsamples = this.totalsamples > 0 && samples > (this.totalsamples - this.samplesread)
			//			? (int)(this.totalsamples - this.samplesread) : samples;
			final byte[] buf = new byte[samples * stride];// new byte[ realsamples * stride /* sampbyte * this.channels */ ];
			final int[] ch_permute = mChannelPermuteMatrix;

			int realsamples = mInStream.read( buf, 0, buf.length /* sampbyte * this.channels * realsamples */ );
			/* if( realsamples < 0 ) {// java: if oef, realsamples = -1
				realsamples = 0;
			}*/ // don't need because of check in a for loop
			realsamples /= stride;// java: byte count to sample count
			// mSamplesRead += (long)realsamples;

			final int monosamples = realsamples * channel_count;// java
			if( mSampleSizeInBits == 16 ) {
				if( mIsBigEndian ) {
					for( int i = 0, o = offset; i < monosamples; i += channel_count ) {
						int j = 0;
						do {
							int off = (i + ch_permute[ j ]) << 1;
							int v = buf[ off++ ] << 8;
							v |= (int)buf[ off ] & 0xff;
							buffer[ o++ ] = (float)v / 32768.0f;
						} while( ++j < channel_count );
					}
					return realsamples;
				}
				for( int i = 0, o = offset; i < monosamples; i += channel_count ) {
					int j = 0;
					do {
						int off = (i + ch_permute[ j ]) << 1;
						int v = (int)buf[ off++ ] & 0xff;
						v |= (int)buf[ off ] << 8;
						buffer[ o++ ] = (float)v / 32768.0f;
					} while( ++j < channel_count );
				}
				return realsamples;
			}
			else if( mSampleSizeInBits == 24 ) {
				if( ! mIsBigEndian ) {
					for( int i = 0, o = offset; i < monosamples; i += channel_count ) {
						int j = 0;
						do {
							int inoff = 3 * (i + ch_permute[j]);
							int v = (int)buf[ inoff++ ] & 0xff;
							v |= ((int)buf[ inoff++ ] & 0xff) << 8;
							v |= (int)buf[ inoff ] << 16;
							buffer[ o++ ] = (float)v / 8388608.0f;

						} while( ++j < channel_count );
					}
					return realsamples;
				}
				return 0;
			} else if( mSampleSizeInBits == 8 ) {
				if( mIsUnsigned8bit ) {
					for( int i = 0, o = offset; i < monosamples; i += channel_count ) {
						int j = 0;
						do {
							buffer[ o++ ] = (float)(((int)buf[ i + ch_permute[j] ] & 0xff) - 128) / 128.0f;
						} while( ++j < channel_count );
					}
					return realsamples;
				}
				for( int i = 0, o = offset; i < monosamples; i += channel_count ) {
					int j = 0;
					do {
						buffer[ o++ ] = (float)buf[ i + ch_permute[j] ] / 128.0f;
					} while( ++j < channel_count );
				}
				return realsamples;
			}
			return 0;
		}

		@Override
		public void close() {
			// don't close input stream
			mInStream = null;
		}
	}
	//
	private static final int LPC_ORDER = 32;
	//
	private FloatInputStream mInputStream = null;
	private int nChannels = 0;
	private long mOriginalSamples = 0;
	private int mLpcPtr = 0;
	private int mExtraSamples = 0;
	private float[] mLpcOut = null;
	private final char[] mStreamMap = new char[ 255 ];
	//
	/* Autocorrelation LPC coeff generation algorithm invented by
	   N. Levinson in 1947, modified by J. Durbin in 1959. */

	/** Input : n elements of time doamin data
	   Output: m lpc coefficients, excitation energy */
	private static final float vorbis_lpc_from_data(final float[] data, final int doffset,// java
			final float[] lpci, int n, final int m, final int stride) {
		final double[] aut = new double[ m + 1 ];
		final double[] lpc = new double[ m ];

		/* autocorrelation, p+1 lag coefficients */
		n *= stride;
		n += doffset;
		int j = m + 1;
		int js = j * stride;
		while( j != 0 ) {
			j--;
			js -= stride;
			double d = 0; /* double needed for accumulator depth */
			for( int i = doffset + js; i < n; i += stride ) {
				d += (double)data[ i ] * data[ i - js ];
			}
			aut[j] = d;
		}

		/* Generate lpc coefficients from autocorr values */

		/* set our noise floor to about -100dB */
		double error = aut[0] * (1. + 1e-10);
		final double epsilon = 1e-9 * aut[0] + 1e-10;

		for( int i = 0; i < m; i++ ) {
			double r = -aut[i + 1];

			if( error < epsilon ) {
				for( j = i; j < m; j++ ) {
					lpc[ j ] = 0;
				}
				break;
			}

			/* Sum up this iteration's reflection coefficient; note that in
			Vorbis we don't save it.  If anyone wants to recycle this code
			and needs reflection coefficients, save the results of 'r' from
			each iteration. */

			for( j = 0; j < i; j++ ) {
				r -= lpc[ j ] * aut[ i - j ];
			}
			r /= error;

			/* Update LPC coefficients and total error */

			lpc[i] = r;
			j = 0;
			int jm = i - 1;
			for( final int je = i >> 1; j < je; j++, jm-- ) {
				final double tmp = lpc[j];
				lpc[j] += r * lpc[ jm ];
				lpc[ jm ] += r * tmp;
			}
			if( (i & 1) != 0 ) {
				lpc[ j ] += lpc[ j ] * r;
			}
			error *= 1. - r * r;
		}
		/* slightly damp the filter */
		{
			final double g = .99;
			double damp = g;
			for( j = 0; j < m; j++ ) {
				lpc[j] *= damp;
				damp *= g;
			}
		}

		for( j = 0; j < m; j++ ) {
			lpci[ j ] = (float)lpc[ j ];
		}

		/* we need the error value to know how big an impulse to hit the
		filter with later */

		return (float)error;
	}

	private static final void vorbis_lpc_predict(final float[] coeff,
			final float[] prime, int poffset,// java
			final int m,
			final float[] data, int doffset,// java
			final int n, final int stride) {

		/* in: coeff[0...m-1] LPC coefficients
		prime[0...m-1] initial values (allocated size of n+m-1)
		out: data[0...n-1] data samples */

		final float[] work = new float[ m + n ];

		if( null != prime ) {
			for( int i = 0; i < m; i++, poffset += stride ) {
				work[ i ] = prime[ poffset ];
			}
		}/* else {
			for( int i = 0; i < m; i++ ) {
				work[i] = 0.f;
			}
		}*/// java: don't need, already zeroed

		for( int i = 0; i < n; i++, doffset += stride ) {
			float y = 0;
			int o = i;
			int p = m;
			for( int j = 0; j < m; j++ ) {
				y -= work[ o++ ] * coeff[ --p ];
			}
			data[ doffset ] = work[ o ] = y;
		}
	}

	/** Read audio data, appending padding to make up any gap
	* between the available and requested number of samples
	* with LPC-predicted data to minimize the pertubation of
	* the valid data that falls in the same frame.
	*/
	@Override
	public final int audio_read(final float[] buffer, final int offset, final int samples) throws IOException {
			final int in_samples = mInputStream.audio_read( buffer, offset, samples );
			mOriginalSamples += in_samples;
			final int channels = nChannels;
			int extra = 0;
			if( in_samples < samples ) {
				if( mLpcPtr < 0 ) {
					mLpcOut = new float[ channels * mExtraSamples ];
					if( in_samples > LPC_ORDER * 2 ) {
						int k = offset + (in_samples - LPC_ORDER) * channels;
						final float[] lpc = new float[ LPC_ORDER ];
						for( int i = 0, oi = offset; i < channels; i++, oi++, k++ ) {
							vorbis_lpc_from_data( buffer, oi, lpc, in_samples, LPC_ORDER, channels );
							vorbis_lpc_predict( lpc, buffer, k,
									LPC_ORDER, mLpcOut, i, mExtraSamples, channels );
						}
					}
					mLpcPtr = 0;
				}
				extra = samples - in_samples;
				if( extra > mExtraSamples ) {
					extra = mExtraSamples;
				}
				mExtraSamples -= extra;
			}
			if( mLpcOut != null ) {
				System.arraycopy( mLpcOut, mLpcPtr * channels, buffer, offset + in_samples * channels, extra * channels );
			}
			mLpcPtr += extra;
			return in_samples + extra;
	}
	@Override
	public final void close() {
		mInputStream.close();
		mInputStream = null;
	}
	//
	private static final float sMixerMatrixes[][][] = {// [7][8][2] = {
			/*2*/  {{1, 0}, {0, 1}},
			/*3*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}},
			/*4*/  {{1, 0}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}},
			/*5*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}},
			/*6*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.7071f, 0.7071f}},
			/*7*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.6123f, 0.6123f}, {0.7071f, 0.7071f}},
			/*8*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.7071f, 0.7071f}},
		};
	private static final class Mixer implements FloatInputStream {
		private FloatInputStream mInStream;
		private float[] mBufs;
		private float[] mMatrix;
		private int mInChannels;
		private int mOutChannels;
		//
		private Mixer(final FloatInputStream in) {
			mInStream = in;
		}
		/**
		 * this is not actually close input stream, just releasing resources.
		 * input stream must be closed manually
		 */
		@Override
		public void close() {
			mInStream.close();
			mInStream = null;
		}
		private final int setup(final int in_channels, final int out_channels ) throws IOException {

			if( in_channels <= out_channels || out_channels > 2 || in_channels <= 0 || out_channels <= 0 ) {
				throw new IOException("Downmix must actually downmix and only knows mono/stereo out.");
			}

			if( out_channels == 2 && in_channels > 8 ) {
				throw new IOException("Downmix only knows how to mix >8ch to mono.");
			}

			mBufs = new float[ in_channels * 4096 ];
			mMatrix = new float[ in_channels * out_channels ];
			mInChannels = in_channels;
			mOutChannels = out_channels;

			if( out_channels == 1 && in_channels > 8 ) {
				for( int i = 0; i < in_channels; i++ ) {
					mMatrix[i] = 1.0f / in_channels;
				}
			} else if( out_channels == 2 ) {
				for( int j = 0; j < out_channels; j++ ) {
					for( int i = 0, k = in_channels * j; i < in_channels; i++, k++ ) {
						mMatrix[ k ] = sMixerMatrixes[ in_channels - 2 ][ i ][ j ];
					}
				}
			} else {
				for( int i = 0, ch2 = in_channels - 2; i < in_channels; i++ ) {
					mMatrix[i] = (sMixerMatrixes[ ch2 ][ i ][ 0 ]) + (sMixerMatrixes[ ch2 ][ i ][ 1 ]);
				}
			}
			float sum = 0;
			final int count = in_channels * out_channels;
			for( int i = 0; i < count; i++ ) {
				sum += mMatrix[i];
			}
			sum = (float)out_channels / sum;
			for( int i = 0; i < count; i++ ) {
				mMatrix[i] *= sum;
			}
			return out_channels;
		}
		@Override
		public int audio_read(final float[] buffer, final int offset, final int samples) throws IOException {
			final float[] b = mBufs;
			final int in_samples = mInStream.audio_read( b, 0, samples );
			final int in_ch = mInChannels;
			final int out_ch = mOutChannels;
			final float[] m = mMatrix;

			for( int i = 0, ie = in_samples * in_ch, o = offset; i < ie; i += in_ch, o += out_ch ) {
				for( int j = o, je = o + out_ch, ij = 0; j < je; j++, ij += in_ch ) {
					int sample = 0;
					for( int bi = i, be = i + in_ch, mi = ij; bi < be; bi++, mi++ ) {
						sample += b[bi] * m[mi];
					}
					buffer[ j ] = sample;
				}
			}
			return in_samples;
		}
	}

	/**
	 * Resampler
	 */
	private static final class Resampler implements FloatInputStream {
		private static final int RESAMPLER_BUF_SIZE = 5760 * 2;// Have at least two output frames worth, just in case of ugly ratios
		private FloatInputStream mInStream;
		private JSpeexResampler mResampler = null;
		private float[] mResamplerBuf = null;
		private int mResamplerBufPos = 0;
		//
		private Resampler(final FloatInputStream in, final int channels, final int in_sr, final int out_sr, final int complexity) {
			mInStream = in;
			mResamplerBuf = new float[RESAMPLER_BUF_SIZE * channels];
			mResampler = new JSpeexResampler( channels, in_sr, out_sr, out_sr == 48000 ? (complexity + 1) >> 1 : 5 );
			mResamplerBufPos = 0;
		}
		private final void skipZeros() {
			mResampler.speex_resampler_skip_zeros();
		}
		@Override
		public int audio_read(final float[] buffer, final int offset, final int samples) throws IOException {
		 	final int channels = mResampler.speex_resampler_get_channel_count();
			int out_samples = 0;
			final float[] pcmbuf = mResamplerBuf;
			int inbuf = mResamplerBufPos;
			while( out_samples < samples ) {
				int out_len = samples - out_samples;
				int reading = RESAMPLER_BUF_SIZE - inbuf;
				if( reading > 1024 ) {
					reading = 1024;
				}
				final int ret = mInStream.audio_read( pcmbuf, inbuf * channels, reading );
				inbuf += ret;
				int in_len = inbuf;
				final long tmp = mResampler.speex_resampler_process_interleaved_float( pcmbuf, 0, in_len, buffer, offset + out_samples * channels, out_len );
				if( tmp < 0 ) {// java: added
					// throw new IOException( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
					System.err.println( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
				}
				out_len = (int)tmp;
				in_len = (int)(tmp >> 32);
				out_samples += out_len;
				if( ret == 0 && in_len == 0 ) {
					for( int i = offset + out_samples * channels, ie = offset + samples * channels; i < ie; i++ ) {
						buffer[i] = 0;
					}
					mResamplerBufPos = inbuf;
					return out_samples;
				}
				for( int i = 0, ie = channels * (inbuf - in_len), j = channels * in_len; i < ie; i++, j++ ) {
					pcmbuf[i] = pcmbuf[j];
				}
				inbuf -= in_len;
			}
			mResamplerBufPos = inbuf;
			return out_samples;
		}
		@Override
		public void close() {
			mInStream.close();
			mInStream = null;
		}
	}
	/*
	Comments will be stored in the Vorbis style.
	It is described in the "Structure" section of
	http://www.xiph.org/ogg/vorbis/doc/v-comment.html

	However, Opus and other non-vorbis formats omit the "framing_bit".

	The comment header is decoded as follows:
	1 )  [vendor_length] = read an unsigned integer of 32 bits
	2 )  [vendor_string] = read a UTF-8 vector as [vendor_length] octets
	3 )  [user_comment_list_length] = read an unsigned integer of 32 bits
	4 )  iterate [user_comment_list_length] times {
		5 )  [length] = read an unsigned integer of 32 bits
		6 )  this iteration's user comment = read a UTF-8 vector as [length] octets
		}
	7 )  done.
	*/

	private static final int readint( final byte[] buf, int base ) {
		int v = buf[base++] & 0xff;
		v |= ( buf[base++] << 8 ) & 0xff00;
		v |= ( buf[base++] << 16 ) & 0xff0000;
		v |= ( buf[base] << 24 ) & 0xff000000;
		return v;
	}
	private static final void writeint( final byte[] buf, int base, final int val ) {
		buf[base++] = (byte)val;
		buf[base++] = (byte)( val >> 8 );
		buf[base++] = (byte)( val >> 16 );
		buf[base] = (byte)( val >> 24 );
	}
	/**
	 *
	 * @param comments [out] byte array data, comment_length
	 * @param tag string only with ASCII characters
	 * @param comment string will be encoded to UTF-8. May be null.
	 * @return byte array with added comment
	 */
	private static final byte[] comment_add( byte[] comments, final String tag, final String comment ) {//final byte[] val ) {
		if( comment != null ) {
			try {
				final byte[] val = tag.getBytes("UTF-8");

				final int vendor_length = readint( comments, 8 );
				final int user_comment_list_length = readint( comments, 8 + 4 + vendor_length );
				int tag_len = ( tag != null ? tag.length() + 1 : 0 );// +1 for '='
				final int len = comments.length + 4 + tag_len + val.length;

				final int comments_length = comments.length;
				comments = Arrays.copyOf( comments, len );

				writeint( comments, comments_length, tag_len + val.length );// length of comment
				if( tag != null ) {
					/* comment tag */
					System.arraycopy( tag.getBytes(), 0, comments, comments_length + 4, --tag_len );
					comments[ comments_length + 4 + tag_len++/* - 1*/ ] = '=';// separator
				}
				System.arraycopy( val, 0, comments, comments_length + 4 + tag_len, val.length );// comment
				writeint( comments, 8 + 4 + vendor_length, user_comment_list_length + 1 );
			} catch( final UnsupportedEncodingException e ) {// never happens
			}
		}
		return comments;
	}

	/** Write an Ogg page to a file pointer */
	private static final int oe_write_page( final Jogg_page page, final OutputStream out ) throws IOException
	{
		out.write( page.header_base, page.header, page.header_len );
		out.write( page.body_base, page.body, page.body_len );
		return page.header_len + page.body_len;// written
	}
	@Override
	public int write(final AudioInputStream stream, final Type fileType, final OutputStream out) throws IOException {
		if( ! fileType.equals( OPUS[0] ) ) {
			throw new IllegalArgumentException();
		}
		FloatInputStream float_stream = new AudioReader( stream );
		// user settings
		int in_channels = stream.getFormat().getChannels();
		boolean is_hard_cbr = false;
		boolean is_cvbr = false;
		int     complexity = 10;
		final int expect_loss = 0;
		final int max_ogg_delay = 48000;// 48kHz samples
		int frame_size = 960;
		// 0 dB gain is recommended unless you know what you're doing
		final int gain = 0;
		int bitrate = -1;// auto
		int out_channels = -1;// as input
		if( fileType instanceof EncoderFileFormatType ) {// get user input
			final int type = ((EncoderFileFormatType) fileType).mStreamType;
			if( type == EncoderFileFormatType.VBR ) {
				is_cvbr = false;
				is_hard_cbr = false;
				complexity = (int)((EncoderFileFormatType) fileType).mStreamTypeParameter;
			}
			if( type == EncoderFileFormatType.CBR ) {
				is_hard_cbr = true;
				is_cvbr = false;
				bitrate = (int)((EncoderFileFormatType) fileType).mStreamTypeParameter;
			}
			if( type == EncoderFileFormatType.ABR ) {
				is_cvbr = true;
				is_hard_cbr = false;
				bitrate = (int)((EncoderFileFormatType) fileType).mStreamTypeParameter;
			}
		}
		// {// initialization
			int coding_rate, rate;
			coding_rate = rate = (int)stream.getFormat().getSampleRate();
			int samplesize = stream.getFormat().getSampleSizeInBits();
			if( samplesize == AudioSystem.NOT_SPECIFIED ) {
				samplesize = 16;
			}

			final int serialno = new Random( System.currentTimeMillis() ).nextInt( 0x8000 );

			// Vendor string should just be the encoder library, the ENCODER comment specifies the tool used.
			// The 'vendor' field should be the actual encoding library used.
			final String vendor_string = Jcelt.opus_get_version_string();
			final int comments_length = 8 + 4 + vendor_string.length() + 4;
			byte[] comments = new byte[ comments_length ];
			System.arraycopy( "OpusTags".getBytes(), 0, comments, 0, 8 );
			writeint( comments, 8, vendor_string.length() );
			System.arraycopy( vendor_string.getBytes(), 0, comments, 12, vendor_string.length() );
			writeint( comments, 12 + vendor_string.length(), 0 );// user_comment_list_length now is 0

			comments = comment_add( comments, "ENCODER", "Jspi_opusenc" );
			{
				String str = String.format("--bitrate %.3f --comp %s",
						Float.valueOf( bitrate / 100.f ), Integer.toString( complexity ) );
				if( is_hard_cbr ) {
					str += " --hard-cbr";
				} else {
					if( is_cvbr ) {
						str += " --cvbr";
					} else {
						str += " --vbr";
					}
				}
				comments = comment_add( comments, "ENCODER_OPTIONS", str );
			}
			/* Property key Value type Description
			"duration" Long playback duration of the file in microseconds
			"author" String name of the author of this file
			"title" String title of this file
			"copyright" String copyright message
			"date" Date date of the recording or release
			"comment" String an arbitrary text */
			{
				String str = (String)stream.getFormat().getProperty("author");
				comments = comment_add( comments, "author", str );
				str = (String)stream.getFormat().getProperty("title");
				comments = comment_add( comments, "title", str );
				str = (String)stream.getFormat().getProperty("copyright");
				comments = comment_add( comments, "copyright", str );
				str = (String)stream.getFormat().getProperty("date");
				comments = comment_add( comments, "date", str );
				str = (String)stream.getFormat().getProperty("comment");
				comments = comment_add( comments, "comment", str );
			}

			if( rate < 100 || rate > 768000 ) {
				/*Crazy rates excluded to avoid excessive memory usage for padding/resampling.*/
				throw new IOException(String.format("Error: unhandled sampling rate: %d Hz\n", Integer.valueOf(rate)));
			}

			if( in_channels > 255 || in_channels < 1 ) {
				throw new IOException("Error: unhandled number of channels: " + in_channels);
			}

			if( out_channels == 0 && in_channels > 2 && bitrate > 0 && bitrate < (16000 * in_channels) ) {
				System.err.println("Notice: Surround bitrate less than 16kbit/sec/channel, downmixing.");
				out_channels = in_channels > 8 ? 1 : 2;
			}

			if( out_channels > 0 && out_channels < in_channels ) {
				final Mixer mixer = new Mixer( float_stream );
				in_channels = mixer.setup( in_channels, out_channels );
				float_stream = mixer;
			}

			// In order to code the complete length we'll need to do a little padding
			if( rate > 24000 ) {
				coding_rate = 48000;
			} else if( rate > 16000 ) {
				coding_rate = 24000;
			} else if( rate > 12000 ) {
				coding_rate = 16000;
			} else if( rate > 8000 ) {
				coding_rate = 12000;
			} else {
				coding_rate = 8000;
			}

			/* must be resampler
			if( rate != coding_rate && complexity != 10 ) {
				System.err.println("Notice: Using resampling with complexity < 10.");
				System.err.println("Opusenc is fastest with 48, 24, 16, 12, or 8kHz input.\n\n");
			}
			*/
			frame_size = frame_size / (48000 / coding_rate);

			/* OggOpus headers */ /*FIXME: broke forcemono*/
			final int channel_mapping = in_channels > 8 ? 255 : in_channels > 2 ? 1 : 0;

			// Initialize Opus encoder
			// Frame sizes < 10ms can only use the MDCT modes, so we switch on RESTRICTED_LOWDELAY
			// to save the extra 4ms of codec lookahead when we'll be using only small frames.
			final int[] pret = new int[1];// java
			final Jms_encoder_data_aux aux = new Jms_encoder_data_aux();// java
			final JOpusMSEncoder st = JOpusMSEncoder.opus_multistream_surround_encoder_create( coding_rate, in_channels, channel_mapping, aux,
					mStreamMap,
					frame_size < 480 / (48000 / coding_rate) ? Jopus_defines.OPUS_APPLICATION_RESTRICTED_LOWDELAY : Jopus_defines.OPUS_APPLICATION_AUDIO,
					pret );
			if( pret[0] != Jopus_defines.OPUS_OK ) {
				throw new IOException("Error cannot create encoder: " + Jcelt.opus_strerror( pret[0] ) );
			}
			final int nb_streams = aux.streams;// java
			final int nb_coupled = aux.coupled_streams;// java

			if( bitrate < 0 ) {
				// Lower default rate for sampling rates [8000 - 44100) by a factor of (rate + 16k) / (64k)
				bitrate = ( (64000 * nb_streams + 32000 * nb_coupled ) *
						( Math.min( 48, Math.max( 8, ((rate < 44100 ? rate : 48000) + 1000) / 1000)) + 16) + 32) >> 6;
			}

			if( bitrate > (1024000 * in_channels) || bitrate < 500 ) {
				throw new IOException(
					String.format("Bitrate %s bits/sec is insane. Values from 6-256 kbit/sec per channel are meaningful.",
					Integer.toString( bitrate ) ) );
			}
			bitrate = Math.min( in_channels * 256000, bitrate );

			final Object[] request = new Object[1];// java helper
			int ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_BITRATE, bitrate );
			if( ret != Jopus_defines.OPUS_OK ) {
				throw new IOException("Error OPUS_SET_BITRATE returned: " + Jcelt.opus_strerror( ret ) );
			}

			st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_VBR, ! is_hard_cbr );
			if( ret != Jopus_defines.OPUS_OK ) {
				throw new IOException("Error OPUS_SET_VBR returned: " + Jcelt.opus_strerror( ret ) );
			}

			if( ! is_hard_cbr ) {
				ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_VBR_CONSTRAINT, is_cvbr );
				if( ret != Jopus_defines.OPUS_OK ) {
					throw new IOException("Error OPUS_SET_VBR_CONSTRAINT returned: " + Jcelt.opus_strerror( ret ) );
				}
			}

			ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_COMPLEXITY, complexity );
			if( ret != Jopus_defines.OPUS_OK ) {
				throw new IOException("Error OPUS_SET_COMPLEXITY returned: " + Jcelt.opus_strerror( ret ) );
			}

			ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_PACKET_LOSS_PERC, expect_loss );
			if( ret != Jopus_defines.OPUS_OK ) {
				throw new IOException("Error OPUS_SET_PACKET_LOSS_PERC returned: " + Jcelt.opus_strerror( ret ) );
			}

			ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_SET_LSB_DEPTH, Math.max( 8, Math.min( 24, samplesize ) ) );
			if( ret != Jopus_defines.OPUS_OK ) {
				System.err.printf("Warning OPUS_SET_LSB_DEPTH returned: %s\n", Jcelt.opus_strerror( ret ) );
			}

			// This should be the last set of CTLs, except the lookahead get, so it can override the defaults.
			/* for( int i3 = 0; i3 < opt_ctls_ctlval.length; i3 += 3 ) {// for custom option list
				final int target = opt_ctls_ctlval[ i3 ];
				if( target == -1 ) {
					ret = st.opus_multistream_encoder_ctl( opt_ctls_ctlval[ i3 + 1 ], opt_ctls_ctlval[ i3 + 2 ] );
					if( ret != Jopus_defines.OPUS_OK ) {
						throw new IOException( String.format("Error opus_multistream_encoder_ctl( st, %d, %d ) returned: %s\n",
								opt_ctls_ctlval[ i3 + 1 ], opt_ctls_ctlval[ i3 + 2 ], Jcelt.opus_strerror( ret ) ) );
					}
				} else if( target < header.nb_streams ) {
					st.opus_multistream_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, target, request );
					final JOpusEncoder oe = (JOpusEncoder)request[0];// java
					ret = oe.opus_encoder_ctl( opt_ctls_ctlval[ i3 + 1 ], opt_ctls_ctlval[ i3 + 2 ] );
					if( ret != Jopus_defines.OPUS_OK ) {
						throw new IOException( String.format("Error opus_encoder_ctl( st[%d], %d, %d ) returned: %s\n",
								target, opt_ctls_ctlval[ i3 + 1 ], opt_ctls_ctlval[ i3 + 2 ], Jcelt.opus_strerror( ret ) ) );
					}
				} else {
					// if( opt_ctls != 0 ) {
						opt_ctls_ctlval = null;
					//}
					throw new IOException( String.format("Error target stream %d is higher than the maximum stream number %d.", target, header.nb_streams - 1 ) );
				}
			}
			// if( opt_ctls != 0 ) {
				opt_ctls_ctlval = null;
			//}
			*/
		// }
		// We do the lookahead check late so user CTLs can change it
		ret = st.opus_multistream_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );
		if( ret != Jopus_defines.OPUS_OK ) {
			throw new IOException("Error OPUS_GET_LOOKAHEAD returned: " + Jcelt.opus_strerror( ret ) );
		}
		// Regardless of the rate we're coding at the ogg timestamping/skip is always timed at 48000.
		final int preskip = (int)((double)((Integer)request[0]).intValue() * (48000. / (double)coding_rate));
		//
		mInputStream = float_stream;
		float_stream = this;
		nChannels = in_channels;
		mLpcPtr = -1;
		// Scale the resampler complexity, but only for 48000 output because
		// the near-cutoff behavior matters a lot more at lower rates.
		if( rate != coding_rate ) {
			final Resampler r = new Resampler( float_stream, in_channels, rate, coding_rate, complexity );
			r.skipZeros();
			float_stream = r;
		}
		// Extra samples that need to be read to compensate for the pre-skip
		mExtraSamples = (int)((double)preskip * ((double)rate / 48000.));

		// Initialize Ogg stream struct
		final Jogg_stream_state   os = new Jogg_stream_state();
		final Jogg_page           og = new Jogg_page();
		final Jogg_packet         op = new Jogg_packet();
		os.ogg_stream_init( serialno );

		int bytes_written = 0;
		/*Write header*/
		{
			{// write header
				/*The Identification Header is 19 bytes, plus a Channel Mapping Table for
				  mapping families other than 0. The Channel Mapping Table is 2 bytes +
				  1 byte per channel. Because the maximum number of channels is 255, the
				  maximum size of this header is 19 + 2 + 255 = 276 bytes.*/
				final byte header_data[] = new byte[ 276 ];

				final byte[] head = "OpusHead".getBytes();
				for( ; bytes_written < 8; bytes_written++ ) {
					header_data[ bytes_written ] = head[ bytes_written ];
				}
				header_data[ bytes_written++ ] = VERSION;// Version is 1
				header_data[ bytes_written++ ] = (byte)in_channels;

				header_data[ bytes_written++] = (byte)(preskip    );// preskip
				header_data[ bytes_written++] = (byte)(preskip >> 8);

				header_data[ bytes_written++ ] = (byte)(rate      );// sample rate
				header_data[ bytes_written++ ] = (byte)(rate >> 8 );
				header_data[ bytes_written++ ] = (byte)(rate >> 16);
				header_data[ bytes_written++ ] = (byte)(rate >> 24);

				header_data[ bytes_written++] = (byte)(gain    );// gain
				header_data[ bytes_written++] = (byte)(gain >> 8);

				header_data[ bytes_written++ ] = (byte)channel_mapping;
				if( channel_mapping != 0 ) {// Multi-stream support
					header_data[ bytes_written++ ] = (byte)nb_streams;
					header_data[ bytes_written++ ] = (byte)nb_coupled;
					for( int i = 0; i < in_channels; i++ ) {
						header_data[ bytes_written++ ] = (byte)mStreamMap[i];
					}
				}
				op.packet_base = header_data;
				op.packet = 0;
			}
			op.bytes = bytes_written;
			op.b_o_s = true;
			op.e_o_s = false;
			op.granulepos = 0;
			op.packetno = 0;
			os.ogg_stream_packetin( op );

			while( ( ret = os.ogg_stream_flush( og ) ) != 0 ) {
				if( 0 == ret ) {
					break;
				}
				ret = oe_write_page( og, out );
				if( ret != og.header_len + og.body_len ) {
					throw new IOException("Error: failed writing header to output stream");
				}
				bytes_written += ret;
			}

			final int comment_padding = 512;
			// comment_pad
			if( comment_padding > 0 ) {
				/*Make sure there is at least amount worth of padding free, and
				round up to the maximum that fits in the current ogg segments.*/
				final int newlen = (comments.length + comment_padding + 255) / 255 * 255 - 1;
				comments = Arrays.copyOf( comments, newlen );
				for( int i = comments.length; i < newlen; i++ ) {
					comments[i] = 0;
				}
			}
			//
			op.packet_base = comments;
			op.packet = 0;
			op.bytes = comments.length;
			op.b_o_s = false;
			op.e_o_s = false;
			op.granulepos = 0;
			op.packetno = 1;
			os.ogg_stream_packetin( op );
		}

		// writing the rest of the opus header packets
		while( ( ret = os.ogg_stream_flush( og ) ) != 0 ) {
			if( 0 == ret ) {
				break;
			}
			ret = oe_write_page( og, out );
			if( ret != og.header_len + og.body_len ) {
				throw new IOException("Error: failed writing header to output stream");
			}
			bytes_written += ret;
		}

		final float[] input = new float[ frame_size * in_channels ];

		// Main encoding loop (one frame per iteration)
		// int min_bytes, max_frame_bytes;
		final int max_frame_bytes = (1275 * 3 + 7) * nb_streams;
		final byte[] packet = new byte[ max_frame_bytes ];
		mOriginalSamples = 0;
		long last_granulepos = 0;
		long enc_granulepos = 0;
		final long granule_inc = (long)(frame_size * 48000 / coding_rate);
		int  last_segments = 0;
		int nb_samples = -1;
		int id = -1 + 2;
		while( ! op.e_o_s ) {
			id++;

			if( nb_samples < 0 ) {
				nb_samples = float_stream.audio_read( input, 0, frame_size );
			}

			int cur_frame_size = frame_size;
			if( nb_samples < cur_frame_size ) {
				op.e_o_s = true;
				/*Avoid making the final packet 20ms or more longer than needed.*/
				cur_frame_size -= ((cur_frame_size - (nb_samples > 0 ? nb_samples : 1))
						/ (coding_rate / 50)) * (coding_rate / 50);
				/*No fancy end padding, just fill with zeros for now.*/
				for( int i = nb_samples * in_channels, ie = cur_frame_size * in_channels; i < ie; i++ ) {
					input[i] = 0;
				}
			}

			// Encode current frame
			final int nbBytes = st.opus_multistream_encode_float( input, 0, frame_size, packet, 0, max_frame_bytes );
			if( nbBytes < 0 ) {
				throw new IOException("Encoding failed: " + Jcelt.opus_strerror( nbBytes ));
			}

			enc_granulepos += granule_inc;
			final int size_segments = (nbBytes + 255) / 255;
			// peak_bytes = Math.max( nbBytes, peak_bytes );
			// min_bytes = Math.min( nbBytes, min_bytes );

			// Flush early if adding this packet would make us end up with a
			// continued page which we wouldn't have otherwise.
			while( (((size_segments <= 255) && (last_segments + size_segments > 255)) ||
					(enc_granulepos - last_granulepos > max_ogg_delay)) &&
					os.ogg_stream_flush_fill( og, 255 * 255 ) != 0 ) {
				if( og.ogg_page_packets() != 0 ) {
					last_granulepos = og.ogg_page_granulepos();
				}
				last_segments -= ((int)og.header_base[ og.header + 26 ]) & 0xff;
				ret = oe_write_page( og, out );
				if( ret != og.header_len + og.body_len ) {
					throw new IOException("Error: failed writing data to output stream");
				}
				bytes_written += ret;
			}

			// The downside of early reading is if the input is an exact
			// multiple of the frame_size you'll get an extra frame that needs
			// to get cropped off. The downside of late reading is added delay.
			// If your ogg_delay is 120ms or less we'll assume you want the
			// low delay behavior.
			if( ( ! op.e_o_s ) && max_ogg_delay > 5760 ) {
				nb_samples = float_stream.audio_read( input, 0, frame_size );
				if( nb_samples == 0 ) {
					op.e_o_s = true;
				}
			} else {
				nb_samples = -1;
			}

			op.packet_base = packet;
			op.packet = 0;
			op.bytes = nbBytes;
			op.b_o_s = false;
			op.granulepos = enc_granulepos;
			if( op.e_o_s ) {
				/*We compute the final GP as ceil(len*48k/input_rate)+preskip. When a
				  resampling decoder does the matching floor((len-preskip)*input_rate/48k)
				  conversion, the resulting output length will exactly equal the original
				  input length when 0<input_rate<=48000.*/
				op.granulepos = ((mOriginalSamples * 48000 + rate - 1) / rate) + preskip;
			}
			op.packetno = id;// 2 + id;
			os.ogg_stream_packetin( op );
			last_segments += size_segments;

			/*If the stream is over or we're sure that the delayed flush will fire,
			go ahead and flush now to avoid adding delay.*/
			while( 0 != (( op.e_o_s || ( enc_granulepos + granule_inc - last_granulepos > max_ogg_delay ) || (last_segments >= 255) ) ?
							os.ogg_stream_flush_fill( og, 255 * 255 ) :
							os.ogg_stream_pageout_fill( og, 255 * 255 )) ) {
				if( og.ogg_page_packets() != 0 ) {
					last_granulepos = og.ogg_page_granulepos();
				}
				last_segments -= ((int)og.header_base[ og.header + 26 ]) & 0xff;
				ret = oe_write_page( og, out );
				if( ret != og.header_len + og.body_len ) {
					throw new IOException("Error: failed writing data to output stream");
				}
				bytes_written += ret;
			}
		}// while op eos
		// st = null;
		// os = null;
		// packet = null;
		// input = null;
		float_stream.close();
		float_stream = null;
		return bytes_written;
	}

	@Override
	public int write(final AudioInputStream stream, final Type fileType, final File file) throws IOException {
		if( ! fileType.equals( OPUS[0] ) ) {
			throw new IllegalArgumentException();
		}
		FileOutputStream outs = null;
		try {
			outs = new FileOutputStream( file );
			return write( stream, fileType, outs );
		} catch(final IOException e) {
			throw e;
		} finally {
			if( outs != null ) {
				try { outs.close(); } catch( final IOException e ) {}
			}
		}
	}
}
