package tools;

import java.io.IOException;
import javax.sound.sampled.AudioInputStream;

/**
 * java: audio data reader using java spi interface to decode audio file
 */
final class Jaudio_spi_reader implements Jaudio_read_func {
	private final int samplesize;
	private final int channels;
	private final int sample_stride;
	private final int[] channel_permute;
	private final long totalsamples;
	private long samplesread;
	private final boolean unsigned8bit;
	private final boolean bigendian;
	private AudioInputStream mInStream;
	//
	Jaudio_spi_reader(final AudioInputStream in, final Joe_enc_opt opt, final boolean isUnsigned, final boolean isBigEndian) {
		this.mInStream = in;
		this.samplesize = opt.samplesize;
		this.channels = opt.channels;
		this.sample_stride = (this.samplesize >> 3) * this.channels;
		this.totalsamples = opt.total_samples_per_channel;
		this.samplesread = 0;
		this.unsigned8bit = isUnsigned;
		this.bigendian = isBigEndian;
		if( this.channels <= 8 ) {
			/* Where we know the mappings, use them. */
			// memcpy(wav->channel_permute, wav_permute_matrix[wav->channels-1], sizeof(int) * wav->channels);
			this.channel_permute = JOpusHeader.wav_permute_matrix[ this.channels - 1 ];// java: don't doing copy
			return;
		}// else {
			this.channel_permute = new int[ this.channels ];
			/* Use a default 1-1 mapping */
			for( int i = 0; i < this.channels; i++ ) {
				this.channel_permute[i] = i;
			}
		//}
	}

	@Override
	public int audio_read_func(/* final Object src,*/ final float[] buffer, final int offset, final int samples) throws IOException {
		//static final long wav_read( void *in, float *buffer, int samples )
		// final AudioInputStream f = (AudioInputStream) src;
		final int channel_count = this.channels;
		final int stride = this.sample_stride;

		// wavfile *f = (wavfile *)in;
		// int sampbyte = this.samplesize >>> 3;// java: stride = sampbyte * this.channels
		int realsamples = this.totalsamples > 0 && samples > (this.totalsamples - this.samplesread)
					? (int)(this.totalsamples - this.samplesread) : samples;
		final byte[] buf = new byte[ realsamples * stride /* sampbyte * this.channels */ ];
		final int[] ch_permute = this.channel_permute;

		realsamples = mInStream.read( buf, 0, buf.length /* sampbyte * this.channels * realsamples */ );
		if( realsamples < 0 ) {// java: if oef, realsamples = -1
			realsamples = 0;
		}
		realsamples /= stride;// java: byte count to sample count

		this.samplesread += (long)realsamples;

		final int monosamples = realsamples * channel_count;// java
		if( this.samplesize == 16 )
		{
			if( this.bigendian )
			{
				for( int i = 0, o = offset; i < monosamples; i += channel_count )
				{
					int j = 0;
					do
					{
						int off = (i + ch_permute[j]) << 1;
						int v = buf[ off++ ] << 8;
						v |= (int)buf[ off ] & 0xff;
						buffer[o++] = (float)v / 32768.0f;
					} while( ++j < channel_count );
				}
				return realsamples;
			}
			for( int i = 0, o = offset; i < monosamples; i += channel_count )
			{
				int j = 0;
				do
				{
					int off = (i + ch_permute[j]) << 1;
					int v = (int)buf[ off++ ] & 0xff;
					v |= (int)buf[ off ] << 8;
					buffer[o++] = (float)v / 32768.0f;
				} while( ++j < channel_count );
			}
			return realsamples;
		}
		else if( this.samplesize == 24 )
		{
			if( ! this.bigendian ) {
				for( int i = 0, o = offset; i < monosamples; i += channel_count )
				{
					int j = 0;
					do
					{
						int inoff = 3 * (i + ch_permute[j]);// java
						int v = (int)buf[ inoff++ ] & 0xff;
						v |= ((int)buf[ inoff++ ] & 0xff) << 8;
						v |= (int)buf[ inoff ] << 16;
						buffer[o++] = (float)v / 8388608.0f;

					} while( ++j < channel_count );
				}
				return realsamples;
			}
			System.err.printf("Big endian 24 bit PCM data is not currently supported, aborting.\n");
			return 0;
		} else if( this.samplesize == 8 )
		{
			if( this.unsigned8bit )
			{
				for( int i = 0, o = offset; i < monosamples; i += channel_count )
				{
					int j = offset;
					do
					{
						buffer[o++] = (float)(((int)buf[ i + ch_permute[j] ] & 0xff) - 128) / 128.0f;
					} while( ++j < channel_count );
				}
				return realsamples;
			}
			for( int i = 0, o = offset; i < monosamples; i += channel_count )
			{
				int j = 0;
				do
				{
					buffer[o++] = (float)buf[ i + ch_permute[j] ] / 128.0f;
				} while( ++j < channel_count );
			}
			return realsamples;
		}
		System.err.printf("Internal error: attempt to read unsupported bitdepth %d\n", Integer.valueOf( this.samplesize ) );
		return 0;
	}
	@Override
	public void close() {
		try {
			if( mInStream != null ) {
				mInStream.close();
			}
			mInStream = null;
		} catch( final IOException e ) {
		}
	}
}
