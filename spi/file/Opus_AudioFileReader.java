package spi.file;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import opusfile.JOggOpusFile;
import opusfile.JOpusFileCallbacks;
import opusfile.JOpusHead;
import spi.convert.Opus_FormatConversionProvider;

public class Opus_AudioFileReader extends AudioFileReader
{
	// there is a real problem: a decoder must process all metadata block. this block can have a huge size.
	private static final int MAX_BUFFER = 1 << 19;// FIXME a metadata block can be 1 << 24.

	private class OpusCallbacks extends JOpusFileCallbacks {
		private final InputStream mInStream;
		OpusCallbacks(final InputStream in) {
			super( true /* read */, false /* seek */, false /* tell */, false /* close */ );
			mInStream = in;
		}

		@Override
		public int read(final byte[] b, final int offset, final int len) throws IOException {
			return mInStream.read( b, offset, len );
		}

		@Override
		public void seek(final long _offset, final int _whence) throws IOException {
		}

		@Override
		public long tell() throws IOException {
			return 0;
		}

		@Override
		public void close() throws IOException {
			// mInStream.close();
		}
	}
	@Override
	public AudioFileFormat getAudioFileFormat(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {

		final JOggOpusFile of = JOggOpusFile.op_open_callbacks( new OpusCallbacks( stream ), null, 0, null );
		if( of != null ) {
			final int li = of.op_current_link();
			if( li != -1 ) {
				final JOpusHead head = of.op_head( li );
				// opusenc uses resampler
				if( head.input_sample_rate > 24000 ) {
					head.input_sample_rate = 48000;
				} else if( head.input_sample_rate > 16000 ) {
					head.input_sample_rate = 24000;
				} else if( head.input_sample_rate > 12000 ) {
					head.input_sample_rate = 16000;
				} else if( head.input_sample_rate > 8000 ) {
					head.input_sample_rate = 12000;
				} else {
					head.input_sample_rate = 8000;
				}
				// can be added properties with additional information
				final AudioFormat af = new AudioFormat( Opus_FormatConversionProvider.ENCODING,
						head.input_sample_rate,
						AudioSystem.NOT_SPECIFIED,
						head.channel_count,
						1,
						head.input_sample_rate,
						false );
				final AudioFileFormat aff = new AudioFileFormat(
						new AudioFileFormat.Type("Opus", ""), af, AudioSystem.NOT_SPECIFIED );

				of.op_free();
				return aff;
			}
		}
		throw new UnsupportedAudioFileException();
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException e) {
			throw e;
		} catch(final IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioFileFormat getAudioFileFormat(final File file)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = new BufferedInputStream( new FileInputStream( file ) );
			return getAudioFileFormat( is );
		} catch(final UnsupportedAudioFileException e) {
			throw e;
		} catch(final IOException e) {
			throw e;
		} finally {
			if( is != null ) {
				try{ is.close(); } catch(final IOException e) {}
			}
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final InputStream stream)
			throws UnsupportedAudioFileException, IOException {

		// doc says: If the input stream does not support this, this method may fail with an IOException.
		//if( ! stream.markSupported() ) {// possible resources leak
		//	stream = new BufferedInputStream( stream, BUFF_SIZE );
		//}
		try {
			stream.mark( MAX_BUFFER );
			final AudioFileFormat af = getAudioFileFormat( stream );
			stream.reset();// to start read header again
			return new AudioInputStream( stream, af.getFormat(), af.getFrameLength() );
		} catch(final UnsupportedAudioFileException e) {
			stream.reset();
			throw e;
		} catch(final IOException e) {
			System.out.println( e.getMessage() );
			stream.reset();
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final URL url)
			throws UnsupportedAudioFileException, IOException {

		InputStream is = null;
		try {
			is = url.openStream();
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}

	@Override
	public AudioInputStream getAudioInputStream(final File file)
			throws UnsupportedAudioFileException, IOException {

		BufferedInputStream is = null;
		try {
			is = new BufferedInputStream( new FileInputStream( file ), MAX_BUFFER );
			return getAudioInputStream( is );
		} catch(final UnsupportedAudioFileException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		} catch(final IOException e) {
			if( is != null ) {
				try{ is.close(); } catch(final IOException ie) {}
			}
			throw e;
		}
	}
}
