package spi.convert;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.FormatConversionProvider;

// TODO what must return getSourceEncodings(), getTargetEncodings ?
public class Opus_FormatConversionProvider extends FormatConversionProvider {
	public static final AudioFormat.Encoding ENCODING = new AudioFormat.Encoding("Opus");

	@Override
	public Encoding[] getSourceEncodings() {
		System.err.println("Opus_FormatConversionProvider.getSourceEncodings");
		return null;
	}

	@Override
	public Encoding[] getTargetEncodings() {
		System.err.println("Opus_FormatConversionProvider.getTargetEncodings");
		return null;
	}

	@Override
	public Encoding[] getTargetEncodings(final AudioFormat sourceFormat) {
		final int sample_size = sourceFormat.getSampleSizeInBits();
		if( sample_size == 8
				||
			(sourceFormat.getEncoding() == Encoding.PCM_SIGNED
			&& (sample_size == 16 || (sample_size == 24 && ! sourceFormat.isBigEndian()) )
			)
		)
		{
			// if the sample rate not equal 48000, 24000, 16000, 12000, 8000 will uses Speex resampler
			// final int sample_rate = (int)sourceFormat.getSampleRate();// hardcoded, because no check method in opus
			//if( sample_rate == 48000 || sample_rate == 24000 || sample_rate == 16000 || sample_rate == 12000 || sample_rate == 8000 ) {
				final Encoding enc[] = { ENCODING };
				return enc;
			// }
		}
		return new Encoding[0];
	}

	@Override
	public AudioFormat[] getTargetFormats(final Encoding targetEncoding, final AudioFormat sourceFormat) {

		if( sourceFormat.getEncoding().equals( ENCODING ) /*&& sample_rate_is_valid( (int)sourceFormat.getSampleRate() )*/) {
			if( targetEncoding == Encoding.PCM_SIGNED ) {
				final AudioFormat af[] = {
					new AudioFormat( sourceFormat.getSampleRate(), 16, 2 /* sourceFormat.getChannels() */, true, false ),
					new AudioFormat( sourceFormat.getSampleRate(), 16, 2 /* sourceFormat.getChannels() */, true, true )
				};
				return af;
			}
		}
		return new AudioFormat[0];
	}

	@Override
	public AudioInputStream getAudioInputStream(final Encoding targetEncoding,
					final AudioInputStream sourceStream) {

		final AudioFormat saf = sourceStream.getFormat();
		final AudioFormat taf = new AudioFormat( targetEncoding,
						saf.getSampleRate(), 16, saf.getChannels(),
						AudioSystem.NOT_SPECIFIED, -1.0f, saf.isBigEndian() );

		return getAudioInputStream( taf, sourceStream );
	}

	@Override
	public AudioInputStream getAudioInputStream(final AudioFormat targetFormat,
					final AudioInputStream sourceStream) {

		return new Opus_DecodedAudioInputStream( sourceStream, targetFormat, AudioSystem.NOT_SPECIFIED );
	}
}
