package tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Random;

import celt.Jcelt;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import libogg.Jogg_packet;
import opus.JOpusDecoder;
import opus.JOpusMSDecoder;
import opus.Jopus;
import opus.Jopus_defines;
import opusfile.JOggOpusFile;
import opusfile.JOpusHead;
import opusfile.JOpusPictureTag;
import opusfile.JOpusServerInfo;
import opusfile.JOpusTags;
import opusfile.Jop_decode_cb_func;

/* Copyright (c) 2002-2007 Jean-Marc Valin
   Copyright (c) 2008 CSIRO
   Copyright (c) 2007-2013 Xiph.Org Foundation
   File: opusdec.c

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
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;  LOSS OF USE, DATA, OR
   PROFITS;  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

// opusdec.c

final class Jopusdec implements Jop_decode_cb_func {
	private static final String CLASS_NAME = "Jopusdec";
	/*We're using this define to test for libopus 1.1 or later until libopus
	  provides a better mechanism.*/
//#if defined(OPUS_GET_EXPERT_FRAME_DURATION_REQUEST)
	/*Enable soft clipping prevention.*/
	private static final boolean HAVE_SOFT_CLIP = true;
//#endif
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;
	// private static final String CHAR_ENCODE = "UTF-8";
	private static final int RAND_MAX = 0x7fff + 1;

	private static final class Jshapestate {
		private float[] b_buf = null;
		private float[] a_buf = null;
		private int fs;
		private int mute;
		//
		private Jshapestate(final int fsr, final int m) {
			fs = fsr;
			mute = m;
		}
		/** This implements a 16 bit quantization with full triangular dither
		and IIR noise shaping. The noise shaping filters were designed by
		Sebastian Gesemann based on the LAME ATH curves with flattening
		to limit their peak gain to 20 dB.
		( Everyone elses' noise shaping filters are mildly crazy )
		The 48kHz version of this filter is just a warped version of the
		44.1kHz filter and probably could be improved by shifting the
		HF shelf up in frequency a little bit since 48k has a bit more
		room and being more conservative against bat-ears is probably
		more important than more noise suppression.
		This process can increase the peak level of the signal ( in theory
		by the peak error of 1.5 +20 dB though this much is unobservable rare )
		so to avoid clipping the signal is attenuated by a couple thousandths
		of a dB. Initially the approach taken here was to only attenuate by
		the 99.9th percentile, making clipping rare but not impossible ( like
		SoX )  but the limited gain of the filter means that the worst case was
		only two thousandths of a dB more, so this just uses the worst case.
		The attenuation is probably also helpful to prevent clipping in the DAC
		reconstruction filters or downstream resampling in any case.*/
		private final void shape_dither_toshort( final short[] _o, final float[] _i, final int inoffset, int _n, final int _CC )
		{
			final int rate = this.fs == 44100 ? 1 : ( this.fs == 48000 ? 0 : 2 );
			final float gain = gains[rate];
			int this_mute = this.mute;
			final float[] b_buff = this.b_buf;
			final float[] a_buff = this.a_buf;
			/*In order to avoid replacing digital silence with quiet dither noise
			we mute if the output has been silent for a while*/
			if( this_mute > 64 ) {
				// memset( a_buf, 0, sizeof( float ) * _CC * 4 );
				for( int i = _CC << 2; --i >= 0; ) {
					a_buff[i] = 0;
				}
			}
			_n *= _CC;// java
			for( int pos = 0; pos < _n; pos += _CC )
			{
				boolean silent = true;
				for( int c = 0; c < _CC; c++ )
				{
					final int c4 = c << 2;// java
					final int pos_c = pos + c;// java
					float err = _i[inoffset + pos_c];// java ;)
					silent &= err == 0;
					float s = err * gain;
					err = 0;
					for( int j = 0; j < 4; j++ ) {
						err += fcoef[rate][j] * b_buff[c4 + j] - fcoef[rate][j + 4] * a_buff[c4 + j];
					}
					System.arraycopy( a_buff, c4, a_buff, c4 + 1, 3 );
					System.arraycopy( b_buff, c4, b_buff, c4 + 1, 3 );
					a_buff[c4] = err;
					s -= err;
					float r = (float)((long)fast_rand() & 0xffffffffL) * (1f / (float) 0xffffffffL) -
								(float)((long)fast_rand() & 0xffffffffL) * (1f / (float) 0xffffffffL);
					if( this_mute > 16 ) {
						r = 0;
					}
					/*Clamp in float out of paranoia that the input will be  > 96 dBFS and wrap if the
					integer is clamped.*/
					r += s;
					r = r <= 32767f ? r : 32767f;
					r = -32768f >= r ? -32768f : r;
					final int si = (int)Math.floor( (double)(.5f + r) );
					_o[pos_c] = (short)si;
					/*Including clipping in the noise shaping is generally disastrous:
					the futile effort to restore the clipped energy results in more clipping.
					However, small amounts-- at the level which could normally be created by
					dither and rounding-- are harmless and can even reduce clipping somewhat
					due to the clipping sometimes reducing the dither+rounding error.*/
					r = 0;
					if( this_mute <= 16 ) {
						r = (float)si - s;
						r = r <= 1.5f ? r : 1.5f;
						r = -1.5f >= r ? -1.5f : r;
					}
					b_buff[c4] = r;
				}
				this_mute++;
				if( ! silent ) {
					this_mute = 0;
				}
			}
			this.mute = this_mute <= 960 ? this_mute : 960;
		}
	}

	// # define float2int( flt )  ( (int)(floor( .5+flt )) )

	/* private static final int CLAMPI( final int a, int b, final int c ) {// never uses
		b = b <= c ? b : c;
		return a >= b ? a : b;
	}*/

	/** 120ms at 48000 */
	private static final int MAX_FRAME_SIZE = 960 * 6;
/*
	private static final int memcmp(final byte[] buf1, int offset1, final byte[] buf2, final int count) {
		for( int offset2 = 0; offset2 < count; ) {
			int u1 = buf1[offset1++];
			final int u2 = buf2[offset2++];
			u1 -= u2;
			if( u1 != 0 ) {
				return u1;
			}
		}
		return 0;
	}
*/
	private static int rngseed = 22222;
	private static final int fast_rand() {// unsigned
		rngseed = ( rngseed * 96314165 ) + 907633515;
		return rngseed;
	}

	private static final float gains[/* 3 */] = { 32768.f-15.f, 32768.f-15.f, 32768.f-3.f };
	private static final float fcoef[/* 3 */][/* 8 */] =
	{
		{2.2374f, -.7339f, -.1251f, -.6033f, 0.9030f, .0116f, -.5853f, -.2571f}, /* 48.0kHz noise shaping filter sd = 2.34*/
		{2.2061f, -.4706f, -.2534f, -.6214f, 1.0587f, .0676f, -.6054f, -.2738f}, /* 44.1kHz noise shaping filter sd = 2.51*/
		{1.0000f, 0.0000f, 0.0000f, 0.0000f, 0.0000f,0.0000f, 0.0000f, 0.0000f}, /* lowpass noise shaping filter sd = 0.65*/
	};

	private static final String[] pic_format_str = {//[4]
			"image", "JPEG", "PNG", "GIF"
	};

	@SuppressWarnings("boxing")
	private static final void print_comments(final JOpusTags _tags) {
		System.err.printf("Encoded with %s\n", _tags.vendor);
		for( int i = 0, ncomments = _tags.comments; i < ncomments; i++ ) {
			final byte[] comment = _tags.user_comments[i];
			if( JOpusTags.opus_tagncompare("METADATA_BLOCK_PICTURE".getBytes(), 22, comment ) == 0 ) {
				final JOpusPictureTag pic = new JOpusPictureTag();
				final int err = pic.opus_picture_tag_parse( comment );
				System.err.printf("%.23s", comment);
				if( err < 0 ) {
					System.err.print("<error parsing picture tag>\n");
				} else {
					System.err.printf("%d|%s|%s|%d%d%d", pic.type, pic.mime_type,
							new String( pic.description, Charset.forName("UTF-8")), pic.width, pic.height, pic.depth);
					if( pic.colors != 0 ) {
						System.err.printf("/%d", pic.colors);
					}
					if( pic.format == JOpusPictureTag.OP_PIC_FORMAT_URL ) {
						System.err.printf("|%s\n", new String( pic.data, Charset.forName("ASCII") ) );
					} else {
						final int format_idx = pic.format < 1 || pic.format >= 4 ? 0 : pic.format;
						System.err.printf("|<%d bytes of %s data>\n", pic.data_length,
								pic_format_str[format_idx] );
					}
					pic.opus_picture_tag_clear();
				}
			} else {
				System.err.printf("%s\n", new String( comment ) );
			}
		}
	}

	/* java: extracted inplace
	private static final RandomAccessFile out_file_open(final String outFile, final int file_output, final int[] wav_format,
			final int rate, final int mapping_family, final int channels, final boolean fp) throws IOException
	{
		RandomAccessFile fout = null;
		// Open output file
		if( 0 == file_output )
		{
			System.err.printf("No soundcard support\n");
			System.exit( EXIT_FAILURE );
			return null;
		} else {
			if( outFile.compareTo("-") == 0 )
			{
				// fout = System.out;
				System.err.printf("stdout output not implemented" );
				throw new IOException("stdout output not implemented");
			}
			else
			{
				fout = new RandomAccessFile( outFile, "rw");
			}
			if( wav_format[0] != 0 )
			{
				wav_format[0] = Jwav_io.write_wav_header( fout, rate, mapping_family, channels, fp );
				if( wav_format[0] < 0 )
				{
					System.err.printf("Error writing WAV header.\n");
					System.exit( EXIT_FAILURE );
					return null;
				}
			}
		}
		return fout;
	}
	*/

	private static final void usage()
	{
// #if defined HAVE_LIBSNDIO || defined HAVE_SYS_SOUNDCARD_H || \
//	    defined HAVE_MACHINE_SOUNDCARD_H || defined HAVE_SOUNDCARD_H || \
//	    defined HAVE_SYS_AUDIOIO_H || defined WIN32 || defined _WIN32
	    System.out.printf("Usage: opusdec [options] input [output]\n");
// #else
//		System.out.printf("Usage: opusdec [options] input output\n");
// #endif
		System.out.printf("\n");
		System.out.printf("Decode audio in Opus format to Wave or raw PCM\n");
		System.out.printf("\n");
		System.out.printf("input can be:\n");
		System.out.printf("  file:filename.opus   Opus URL\n");
		System.out.printf("  filename.opus        Opus file\n");
		System.out.printf("  -                    stdin\n");
		System.out.printf("\n");
		System.out.printf("output can be:\n");
		System.out.printf("  filename.wav         Wave file\n");
		System.out.printf("  filename.*           Raw PCM file (any extension other than .wav)\n");
		System.out.printf("  -                    stdout (raw; unless --force-wav)\n");
// #if defined HAVE_LIBSNDIO || defined HAVE_SYS_SOUNDCARD_H || \
//	    defined HAVE_MACHINE_SOUNDCARD_H || defined HAVE_SOUNDCARD_H || \
//	    defined HAVE_SYS_AUDIOIO_H || defined WIN32 || defined _WIN32
	    System.out.printf("  (default)            Play audio\n");
// #endif
		System.out.printf("\n");
		System.out.printf("Options:\n");
		System.out.printf(" -h, --help            Show this help\n");
		System.out.printf(" -V, --version         Show version information\n");
		System.out.printf(" --quiet               Suppress program output\n");
		System.out.printf(" --rate n              Force decoding at sampling rate n Hz\n");
		System.out.printf(" --force-stereo        Force decoding to stereo\n");
		System.out.printf(" --gain n              Adjust output volume n dB (negative is quieter)\n");
		System.out.printf(" --no-dither           Do not dither 16-bit output\n");
		System.out.printf(" --float               Output 32-bit floating-point samples\n");
		System.out.printf(" --force-wav           Force Wave header on output\n");
		System.out.printf(" --packet-loss n       Simulate n %% random packet loss\n");
		System.out.printf(" --save-range file     Save check values for every frame to a file\n");
		System.out.printf("\n");
	}

	private static final void version()
	{
		System.out.printf("Jopusdec %s %s (using %)\n", Jtools.PACKAGE_NAME, Jtools.PACKAGE_VERSION, Jcelt.opus_get_version_string() );
		System.out.printf("Copyright (C) 2008-2018 Xiph.Org Foundation\n");
	}

	private static final void version_short()
	{
		version();
	}

	private static final long audio_write(final float[] pcm, final int channels, int frame_size, final RandomAccessFile fout,
			 final JSpeexResampler resampler, final float[] clipmem, final Jshapestate shapemem,
			 final boolean file, final int rate, final long link_read, final long link_out, final boolean fp)
	{// FIXME write errors aren't processed
		int pcmoffset = 0;// java
		long sampout = 0;
		final short[] out = new short[ MAX_FRAME_SIZE * channels ];
		final float[] buf = new float[ MAX_FRAME_SIZE * channels ];
		long maxout = ((link_read / 48000) * rate + (link_read % 48000) * rate / 48000) - link_out;
		maxout = maxout < 0 ? 0 : maxout;
		do {
			int out_len;
			float[] output;
			int outoffset;// java
			if( resampler != null ) {
				output = buf;
				outoffset = 0;
				int in_len = frame_size;
				out_len = 1024 < maxout ? 1024 : (int)maxout;
				final long tmp = resampler.speex_resampler_process_interleaved_float( pcm, pcmoffset, in_len, buf, 0, out_len );
				if( tmp < 0 ) {// java: added
					// throw new IOException( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
					System.err.println( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
				}
				out_len = (int)tmp;
				in_len = (int)(tmp >> 32);
				pcmoffset += channels * (in_len);
				frame_size -= in_len;
			} else {
				output = pcm;
				outoffset = 0;// java
				out_len = (long)frame_size < maxout ? frame_size : (int)maxout;
				frame_size = 0;
			}

			if( ! file || ! fp )
			{
				/*Convert to short and save to output file*/
if( HAVE_SOFT_CLIP ) {
				Jopus.opus_pcm_soft_clip( output, outoffset, out_len, channels, clipmem );
} // else
				// (void)clipmem;
//#endif

				if( shapemem != null ) {
					shapemem.shape_dither_toshort( out, output, outoffset, out_len, channels );
				} else {
					for( int i = 0, oi = outoffset, ie = out_len * channels; i < ie; i++ ) {
						float v = output[ oi ] * 32768.f;
						v = v > 32767f ? 32767f : (v >= -32768f ? v : -32768f);
						out[ i ] = (short)Math.floor( (double)(.5f + v) );
					}
				}
				/* if( ( le_short( 1 ) != 1 ) && file ) {// java: don't need
					for( int i = 0, ie = out_len * channels; i < ie; i++ ) {
						out[i] = (short)out[i];
					}
				}*/
			}
			/* else if( le_short( 1 ) != (1) ) {// java: don't need
				// ensure the floats are little endian
				for( int i = 0, ie = out_len * channels; i < ie; i++ ) {
					put_le_float( buf + i, output[i] );
				}
				output = buf;
			}*/

			if( maxout > 0 )
			{
				int ret = 0;
				if( ! file ) {
					System.err.print("Error playing audio.\n");// FIXME ret is negative and will be used
				} else {
					try {
						// ret = fwrite( fp ? (char *) output : (char *) out, ( fp ? 4 : 2 ) * channels, out_len < maxout ? out_len : maxout, fout );
						//
						final boolean is_big_endian = false;
						out_len = (int)(out_len <= maxout ? out_len : maxout);
						ret = out_len;// java
						out_len *= channels;
						if( fp ) {
							// float -> byte
							final byte[] bytebuffer = new byte[ out_len * (Float.SIZE / 8) ];
							ByteBuffer.wrap( bytebuffer ).order( is_big_endian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN )
									.asFloatBuffer().put( output, outoffset, out_len );
							fout.write( bytebuffer );// FIXME ret is not checked
						} else {
							// short -> byte little endian
							final byte[] bytebuffer = new byte[ out_len * (Short.SIZE / 8) ];
							ByteBuffer.wrap( bytebuffer ).order( is_big_endian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN )
									.asShortBuffer().put( out, 0, out_len );
							fout.write( bytebuffer );// FIXME ret is not checked
						}
					} catch(final IOException ie) {
						System.err.println( ie.getMessage() );// java: added
					}
				}
				sampout += ret;
				maxout -= ret;
			}
		} while( frame_size > 0 && maxout > 0 );
		return sampout;
	}

	private static final class Jdecode_cb_ctx {
		private PrintStream frange;
		private float loss_percent;
	};

	private static final Random rand = new Random();// java

	// Jop_decode_cb_func interface
	// decode_cb

	@Override
	public final int op_decode_cb_func(final /*Jdecode_cb_ctx*/Object _ctx, final JOpusMSDecoder decoder, final Object pcm, final int poffset,
			final Jogg_packet op, final int nsamples, final int nchannels, final int format, final int li)
	{
		int ret;
		// (void)nchannels;
		// (void)li;
		final Jdecode_cb_ctx ctx = (Jdecode_cb_ctx)_ctx;
		final boolean lost = ctx.loss_percent > 0
				&& 100 * ((float)rand.nextInt( RAND_MAX )) / RAND_MAX < ctx.loss_percent;
		switch( format )
		{
			case JOggOpusFile.OP_DEC_FORMAT_SHORT:
			{
				if( lost )
				{
					ret = decoder.opus_multistream_decode( null, 0, 0, (short[])pcm, poffset, nsamples, false );
				} else {
					ret = decoder.opus_multistream_decode( op.packet_base, op.packet, op.bytes, (short[])pcm, poffset, nsamples, false );
				}
				break;
			}
			case JOggOpusFile.OP_DEC_FORMAT_FLOAT:
			{
				if( lost )
				{
					ret = decoder.opus_multistream_decode_float( null, 0, 0, (float[])pcm, poffset, nsamples, false );
				} else {
					ret = decoder.opus_multistream_decode_float( op.packet_base, op.packet, op.bytes, (float[])pcm, poffset, nsamples, false );
				}
				break;
			}
			default:
			{
				return Jopus_defines.OPUS_BAD_ARG;
			}
		}
		/*On success, either we got as many samples as we wanted, or something went
		  wrong.*/
		if( ret >= 0 )
		{
			ret = ret == nsamples ? 0 : Jopus_defines.OPUS_INTERNAL_ERROR;
			if( ret == 0 && ctx.frange != null )
			{
				final long rngs[] = new long[256];
				final Object[] request = new Object[1];// java helper
				int si;
				/*If we're collecting --save-range debugging data, collect it now.*/
				for( si = 0; si < 255; si++ )
				{
					final int err = decoder.opus_multistream_decoder_ctl( JOpusMSDecoder.OPUS_MULTISTREAM_GET_DECODER_STATE, si, request );
					final JOpusDecoder od = (JOpusDecoder)request[0];
					/*This will fail with OPUS_BAD_ARG the first time we ask for a
					  stream that isn't there, which is currently the only way to find
					  out how many streams there are using the libopus API.*/
					if( err < 0 ) {
						break;
					}
					od.opus_decoder_ctl( Jopus_defines.OPUS_GET_FINAL_RANGE, request );
					rngs[si] = ((Long)request[0]).longValue();
				}
				Jdiag_range.save_range( ctx.frange, nsamples, op.packet_base, op.packet, op.bytes, rngs, si );
			}
		}
		return ret;
	}

	/**
	 * java changed.
	 *
	 * @return new audio_size
	 */
	private static final long drain_resampler(final RandomAccessFile fout, final boolean file_output,
			final JSpeexResampler resampler, final int channels, final int rate,
			final long link_read, long link_out, final float[] clipmem,
			final Jshapestate shapemem, long audio_size, final boolean fp)
	{
		final float[] zeros = new float[ 100 * channels ];
		int drain = resampler.speex_resampler_get_input_latency();
		final long stride = (fp ? (Float.SIZE / 8) : (Short.SIZE / 8)) * channels;// java
		do
		{
			int tmp = drain;// MINI(drain, 100);
			if( tmp > 100 ) {
				tmp = 100;
			}
			final long outsamp = audio_write( zeros, channels, tmp, fout, resampler, clipmem,
					shapemem, file_output, rate, link_read, link_out, fp );
			link_out += outsamp;
			// (*audio_size) += (fp ? sizeof(float):sizeof(short)) * outsamp * channels;
			audio_size += stride * outsamp;// java
			drain -= tmp;
		} while( drain > 0 );
		// free(zeros);
		return audio_size;// java
	}

	@SuppressWarnings({ "boxing", "null" })
	public static final void main( final String[] args )// int main( final int argc, char **argv )
	{
		final LongOpt long_options[] =
			{
				new LongOpt("help", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("quiet", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("version", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("version-short", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("rate", LongOpt.REQUIRED_ARGUMENT, null, 0),
				new LongOpt("force-stereo", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("gain", LongOpt.REQUIRED_ARGUMENT, null, 0),
				new LongOpt("no-dither", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("float", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("force-wav", LongOpt.NO_ARGUMENT, null, 0),
				new LongOpt("packet-loss", LongOpt.REQUIRED_ARGUMENT, null, 0),
				new LongOpt("save-range", LongOpt.REQUIRED_ARGUMENT, null, 0),
				// new Joption{0, 0, 0, 0}
			};
		PrintStream frange = null;
		float loss_percent = -1;
		float manual_gain = 0;
		boolean force_stereo = false;
		int rate = 0;
		boolean dither = true;
		boolean fp = false;
		boolean quiet = false;
		boolean forcewav = false;

		/*Process options*/
		final Getopt g = new Getopt( CLASS_NAME, args, "hV", long_options );
		g.setOpterr( false );
		int c;
		while( (c = g.getopt()) != -1 )
		{
			final String optarg = g.getOptarg();
			switch( c )
			{
			case 0:
				final String name = long_options[ g.getLongind() ].getName();// java
				if( name.compareTo("help" ) == 0 )
				{
					usage();
					System.exit( EXIT_SUCCESS );
					return;
				} else if( name.compareTo("quiet" ) == 0 )
				{
					quiet = true;
				} else if( name.compareTo("version" ) == 0 )
				{
					version();
					System.exit( EXIT_SUCCESS );
					return;
				} else if( name.compareTo("version-short" ) == 0 )
				{
					version_short();
					System.exit( EXIT_SUCCESS );
					return;
				} else if( name.compareTo("no-dither" ) == 0 )
				{
					dither = false;
				} else if( name.compareTo("float" ) == 0 )
				{
					fp = true;
				} else if( name.compareTo("force-wav" ) == 0 )
				{
					forcewav = true;
				} else if( name.compareTo("rate" ) == 0 )
				{
					rate = Integer.parseInt( optarg );
				} else if( name.compareTo("force-stereo") == 0 )
				{
					force_stereo = true;
				} else if( name.compareTo("gain" ) == 0 )
				{
					manual_gain = Float.parseFloat( optarg );
				}else if( name.compareTo("save-range" ) == 0 ) {
					try {
						frange = new PrintStream( optarg );
					} catch(final Exception e){
						System.err.printf("Could not open save-range file: %s\n", optarg );
						System.err.printf("Must provide a writable file name.\n");
						System.exit( EXIT_FAILURE );
						return;
					}
				} else if( name.compareTo("packet-loss" ) == 0 )
				{
					loss_percent = Float.parseFloat( optarg );
				}
				break;
			case 'h':
				usage();
				System.exit( EXIT_SUCCESS );
				return;
				// break;
			case 'V':
				version();
				System.exit( EXIT_SUCCESS );
				return;
				// break;
			case '?':
				usage();
				System.exit( EXIT_FAILURE );
				return;
				//break;
			}
		}
		final int optind = g.getOptind();
		if( args.length - optind != 2 && args.length - optind != 1 )
		{
			usage();
			System.exit( EXIT_FAILURE );
			return;
		}
		final String inFile = args[ optind ];

		/*Output to a file or playback?*/
		final boolean file_output = args.length - optind == 2;
		String outFile;
		int wav_format = 0;
		if( file_output ) {
			/*If we're outputting to a file, should we apply a wav header?*/
			outFile = args[ optind + 1 ];
			wav_format = outFile.endsWith(".wav") ? 1 : 0;
			wav_format |= forcewav ? 1 : 0;
		} else {
			outFile = "";
			wav_format = 0;
			/*If playing to audio out, default the rate to 48000
			instead of the original rate. The original rate is
			only important for minimizing surprise about the rate
			of output files and preserving length, which aren't
			relevant for playback. Many audio devices sound
			better at 48kHz and not resampling also saves CPU.*/
			if( rate == 0 ) {
				rate = 48000;
			}
			/*Playback is 16-bit only.*/
			fp = false;
		}
		/*If the output is floating point, don't dither.*/
		if( fp ) {
			dither = false;
		}

		int old_li = -1;
		int li;
		/*Open input file*/
		final InputStream fin = null;
		final boolean close_in = false;
		RandomAccessFile fout = null;
		JOggOpusFile st = null;// FIXME is not closed!
try {
		if( inFile.compareTo("-") == 0 )
		{
			//fin = System.in;
			//final JOpusFileCallbacks cb = new JOpusFileCallbacks( false, false, false, false );
			//st = JOggOpusFile.op_open_callbacks( Jstream.op_fdopen( cb, fd, "rb"), &cb, null, 0, null );
			// java: to read data from stdin need write JOpusInputStream extends JOpusFileCallbacks
			System.exit( EXIT_FAILURE );
			return;
		}
		else
		{
			st = JOpusServerInfo.op_open_url( inFile, null, 0, null, null );// java changed
			if( st == null )
			{
				st = JOggOpusFile.op_open_file( inFile, null );
			}
		}
		if( st == null )
		{
			System.err.printf("Failed to open '%s'.\n", inFile);
			System.exit( EXIT_FAILURE );
			return;
		}

		if( manual_gain != 0.F )
		{
			st.op_set_gain_offset( JOggOpusFile.OP_HEADER_GAIN, (int)Math.round( manual_gain * 256.F ) );// java: TODO check float2int( manual_gain * 256.F ) );
		}

		JOpusHead head = st.op_head( 0 );
		if( st.op_seekable() )
		{
			/*If we have a seekable file, we can make some intelligent decisions
	    	  about how to decode.*/
			final int nlinks = st.op_link_count();
			if( rate == 0 )
			{
				int initial_rate = head.input_sample_rate;
				/*We decode unknown rates at 48 kHz, so don't complain about a
				  mismatch between 48 kHz and "unknown".*/
				if( initial_rate == 0 )
				{
					initial_rate = 48000;
				}
				for( li = 1; li < nlinks; li++ ) {
					int cur_rate = st.op_head( li ).input_sample_rate;
					if( cur_rate == 0 )
					{
						cur_rate = 48000;
					}
					if( initial_rate != cur_rate )
					{
						System.err.printf(
								"Warning: Chained stream with multiple input sample rates: " +
								"forcing decode to 48 kHz.\n");
						rate = 48000;
						break;
					}
				}
			}
			if( ! force_stereo )
			{
				final int initial_channels = head.channel_count;
				for( li = 1; li < nlinks; li++ ) {
					final int cur_channels = st.op_head( li ).channel_count;
					if( initial_channels != cur_channels )
					{
						System.err.printf(
								"Warning: Chained stream with multiple channel counts: " +
								"forcing decode to stereo.\n");
						force_stereo = true;
						break;
					}
				}
			}
		}

		boolean force_rate = false;
		if( rate == 0 )
		{
			rate = head.input_sample_rate;
			/*If the rate is unspecified, we decode to 48000.*/
			if( rate == 0 )
			{
				rate = 48000;
			}
		} else {
			/*Remember that we forced the rate, so we don't complain if it changes in
			  an unseekable chained stream.*/
			force_rate = true;
		}
		if( rate < 8000 || rate > 192000 )
		{
			System.err.printf(
					"Warning: Crazy input_rate %d, decoding to 48000 instead.\n", rate);
			rate = 48000;
			force_rate = true;
		}

		final int requested_channels = force_stereo ? 2 : head.channel_count;
		/*TODO: For seekable sources, write the output length in the WAV header.*/
		final int channels = requested_channels;
		// java: extracted inplace to avoid of problems of returning of data
		// fout = out_file_open( outFile, file_output, &wav_format, rate, head.mapping_family, &channels, fp );
		// start out_file_open
		// RandomAccessFile fout = null;
		// Open output file
		if( ! file_output )
		{
			System.err.printf("No soundcard support\n");
			System.exit( EXIT_FAILURE );
			fout = null;
			return;
		} else {
			if( outFile.compareTo("-") == 0 )
			{
				// fout = System.out;
				System.err.printf("stdout output not implemented" );
				throw new IOException("stdout output not implemented");
			}
			else
			{
				fout = new RandomAccessFile( outFile, "rw");
			}
			if( wav_format != 0 )
			{
				wav_format = Jwav_io.write_wav_header( fout, rate, head.mapping_family, channels, fp );
				if( wav_format < 0 )
				{
					System.err.printf("Error writing WAV header.\n");
					System.exit( EXIT_FAILURE );
					fout = null;
				}
			}
		}
		// return fout;
		// end out_file_open
		if( channels != requested_channels ) {
			force_stereo = true;
		}
		/*Setup the memory for the dithered output*/
		final Jshapestate shapemem = new Jshapestate( 0, 960 );
		if( null == shapemem.a_buf )
		{
			shapemem.a_buf = new float[ channels << 2 ];
			shapemem.b_buf = new float[ channels << 2 ];
			shapemem.fs = rate;
		}
		final char channel_map[] = new char[ JOggOpusFile.OPUS_CHANNEL_COUNT_MAX ];
		float[] output = new float[ MAX_FRAME_SIZE * channels ];
		float[] permuted_output = null;
		if( wav_format != 0 && (channels == 3 || channels > 4) )
		{
			for( int ci = 0; ci < channels; ci++ )
			{
				channel_map[ci] = (char)ci;
			}
			Jwav_io.adjust_wav_mapping( head.mapping_family, channels, channel_map );
			permuted_output = new float[ MAX_FRAME_SIZE * channels ];
			/* if( ! permuted_output )// java: out of memory exception
			{
				System.err.printf( "Memory allocation failure.\n");
				System.exit( 1 );
				return;
			} */
		}

		/*If we're simulating packet loss or saving range data, then we need to
		  install a decoder callback.*/
		final Jdecode_cb_ctx cb_ctx = new Jdecode_cb_ctx();
		if( loss_percent > 0 || frange != null )
		{
			cb_ctx.loss_percent = loss_percent;
			cb_ctx.frange = frange;
			// st.op_set_decode_callback( (op_decode_cb_func)decode_cb, cb_ctx );
			st.op_set_decode_callback( new Jopusdec(), cb_ctx );
		}

		final float clipmem[] = new float[8];// ={0};
		int last_spin = 0;

		long nb_read_total = 0;
		long link_read = 0;
		long link_out = 0;
		long audio_size = 0;
		double last_coded_seconds = 0;
		JSpeexResampler resampler = null;
		final int pli[] = new int[1];// java: to get li back
		/*Main decoding loop*/
		while( true )
		{
			int nb_read;
			if( force_stereo )
			{
				nb_read = st.op_read_float_stereo( output, MAX_FRAME_SIZE * channels );
				li = st.op_current_link();
			} else {
				nb_read = st.op_read_float( output, MAX_FRAME_SIZE * channels, pli );
				li = pli[0];// java
			}
			if( nb_read < 0 ) {
				if( nb_read == JOggOpusFile.OP_HOLE ) {
					/*TODO: At...?*/
					System.err.printf("Warning: Hole in data.\n");
					continue;
				} else {
					System.err.printf("Decoding error.\n");
					break;
				}
			}
			if( nb_read == 0 )
			{
				if( ! quiet )
				{
					System.err.printf("\rDecoding complete.        \n");
					System.err.flush();
				}
				break;
			}
			if( li != old_li )
			{
				/*Drain and reset the resampler to be sure we get an accurate number
				  of output samples.*/
				if( resampler != null )
				{
					audio_size = drain_resampler(fout, file_output, resampler, channels, rate,
							link_read, link_out, clipmem, dither ? shapemem : null, audio_size, fp);// java: audio_size is returned
					/*Neither speex_resampler_reset_mem() nor
					  speex_resampler_skip_zeros() clear the number of fractional
					  samples properly, so we just destroy it. It will get re-created
					  below.*/
					// resampler.speex_resampler_destroy();
					resampler = null;
				}
				/*We've encountered a new link.*/
				link_read = link_out = 0;
				head = st.op_head( li );
				if( ! force_stereo && channels != head.channel_count )
				{
					/*In theory if the first link was stereo, we could downmix the
					  remaining links, but we've already decoded the first packet, and
					  this stream is unseekable, so we'd have to write our own downmix
					  code. That's more trouble than it's worth.*/
					System.err.printf(
							"Error: channel count changed in a chained stream: " +
							"aborting.\n");
					break;
				}
				if( ! force_rate
						&& rate !=
						(head.input_sample_rate == 0 ? 48000 : head.input_sample_rate) )
				{
					System.err.printf(
							"Warning: input sampling rate changed in a chained stream: " +
							"resampling remaining links to %d. Use --rate to override.\n",
							rate );
				}
				if( ! quiet )
				{
					if( old_li >= 0 )
					{
						/*Clear the progress indicator from the previous link.*/
						System.err.printf("\r");
					}
					System.err.printf("Decoding to %d Hz (%d %s)", rate,
							channels, channels > 1 ? "channels" : "channel" );
					if( head.version != 1 )
					{
						System.err.printf(", Header v%d", head.version );
					}
					System.err.printf("\n");
					if( head.output_gain != 0 )
					{
						System.err.printf("Playback gain: %f dB\n", head.output_gain / 256. );
					}
					if( manual_gain != 0 )
					{
						System.err.printf("Manual gain: %f dB\n", manual_gain );
					}
					print_comments( st.op_tags( li ) );
				}
			}
			nb_read_total += nb_read;
			link_read += nb_read;
			if( ! quiet )
			{
				/*Display a progress spinner while decoding.*/
				final char spinner[] = {'|', '/', '-', '\\'};
				final double coded_seconds = nb_read_total / (double)rate;
				if( coded_seconds >= last_coded_seconds + 1 || li != old_li )
				{
					System.err.printf("\r[%c] %02d:%02d:%02d", spinner[ last_spin & 3 ],
							(int)(coded_seconds / 3600), (int)(coded_seconds / 60) % 60,
							(int)(coded_seconds) % 60);
					System.err.flush();
				}
				if( coded_seconds >= last_coded_seconds + 1 )
				{
					last_spin++;
					last_coded_seconds = coded_seconds;
				}
			}
			old_li = li;
			if( permuted_output != null )
			{
				for( int i = 0; i < nb_read; i++ )
				{
					for( int ci = 0; ci < channels; ci++ )
					{
						permuted_output[i * channels + ci] = output[ i * channels + channel_map[ci] ];
					}
				}
			}
			/*Normal players should just play at 48000 or their maximum rate,
			  as described in the OggOpus spec.  But for commandline tools
			  like opusdec it can be desirable to exactly preserve the original
			  sampling rate and duration, so we have a resampler here.*/
			if( rate != 48000 && resampler == null )
			{
				try {
					resampler = JSpeexResampler.speex_resampler_init( channels, 48000, rate, 5 );
				} catch(final IllegalArgumentException ie) {
					System.err.printf( "resampler error: %s\n", ie.getMessage() );
				}
				resampler.speex_resampler_skip_zeros();
			}
			final long outsamp = audio_write( permuted_output != null ? permuted_output : output, channels,
					nb_read, fout, resampler, clipmem, dither ? shapemem : null, file_output,
							rate, link_read, link_out, fp );
			link_out += outsamp;
			audio_size += (fp ? (Float.SIZE / 8) : (Short.SIZE / 8)) * outsamp * channels;
		}

		if( resampler != null )
		{
			audio_size = drain_resampler( fout, file_output, resampler, channels, rate,
					link_read, link_out, clipmem, dither ? shapemem : null, audio_size, fp );// java: audio_size is returned
			// resampler.speex_resampler_destroy();
			resampler = null;
		}

		/*If we were writing wav, go set the duration.*/
		if( file_output && fout != null && wav_format > 0 && audio_size < 0x7FFFFFFF )
		{
			try {
				fout.seek( 4 );
				try {
					final int tmp = (int)audio_size + 20 + wav_format;
					fout.write( tmp );// low endian
					fout.write( tmp >> 8 );
					fout.write( tmp >> 16 );
					fout.write( tmp >> 24 );
				} catch(final IOException e) {
					System.err.printf("Error writing end length.\n");
				}
				try {
					fout.seek( 4 + 4 + 16 + wav_format );// SEEK_CUR, cur = 4 + 4
					final int tmp = (int)audio_size;
					try {
						fout.write( tmp );// low endian
						fout.write( tmp >> 8 );
						fout.write( tmp >> 16 );
						fout.write( tmp >> 24 );
					} catch(final IOException e) {
						System.err.printf("Error writing header length.\n");
					}
				} catch( final IOException e ) {
					System.err.printf("First seek worked, second didn't\n");
				}
			} catch( final IOException e ) {
				System.err.printf("Cannot seek on wav file output, wav size chunk will be incorrect\n");
			}
		}

		if( ! file_output ) {
			// WIN_Audio_close();
		}

		shapemem.a_buf = null;
		shapemem.b_buf = null;
		output = null;
		permuted_output = null;
		if( frange != null )
		{
			frange.close();
		}
		if( fout != null )
		{
			fout.close();
		}
} catch(final Exception e) {
		e.printStackTrace();
} finally {
		if( frange != null ) {
			frange.close();
		}
		if( close_in ) {
			if( fin != null ) {
				try { fin.close(); } catch( final IOException e ) {}
			}
		}
		if( fout != null ) {
			try { fout.close(); } catch( final IOException e ) {}
		}
		if( st != null ) {
			st.op_free();// closing input stream
		}
}
		System.exit( EXIT_SUCCESS );
		return;
	}
}