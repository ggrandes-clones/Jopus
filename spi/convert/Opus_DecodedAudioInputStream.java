package spi.convert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import opusfile.JOggOpusFile;
import opusfile.JOpusFileCallbacks;
import javax.sound.sampled.AudioInputStream;

public final class Opus_DecodedAudioInputStream extends AudioInputStream
{
	private class OpusCallbacks extends JOpusFileCallbacks {
		private final InputStream mInStream;
		OpusCallbacks(final InputStream in) {
			super( true /* read */, false /* seek */, false /* tell */, true /* close */ );
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
			mInStream.close();
		}
	}
	private JOggOpusFile mOpusFile;
	private final boolean mIsBigEndian;
	private static final int BUFF_SIZE = 120 * 48 * 2;
	private final short mBuffer[] = new short[ BUFF_SIZE ];
	private final byte mByteBuffer[] = new byte[ BUFF_SIZE * 2 ];
	private int mBufferReadPosition;
	private int mBytesInBuffer;
	//
	public Opus_DecodedAudioInputStream(final InputStream stream, final AudioFormat format, final long length) {
		super( stream, format, length );
		mIsBigEndian = format.isBigEndian();
		mBufferReadPosition = 0;
		mBytesInBuffer = 0;
		//
		mOpusFile = JOggOpusFile.op_open_callbacks( new OpusCallbacks( stream ), null, 0, null );
		if( mOpusFile == null ) {
			try { stream.close(); } catch( final IOException e ) {}
		}
	}
	@Override
	public void close() throws IOException {
		if( mOpusFile != null ) {
			mOpusFile.op_free();
		}
		mOpusFile = null;
		super.close();
	}
	@Override
	public boolean markSupported() {
		return false;
	}
	@Override
	public int read() throws IOException {
		final byte[] data = new byte[1];
		if( read( data ) <= 0 ) {// we have a weird situation if read(byte[]) returns 0!
			return -1;
		}
		return ((int) data[0]) & 0xff;
	}
	@Override
	public int read(final byte[] b, int off, int len) throws IOException {
		final int bytes_in_buffer = mBytesInBuffer - mBufferReadPosition;
		if( len <= bytes_in_buffer ) {
			System.arraycopy( mByteBuffer, mBufferReadPosition, b, off, len );
			mBufferReadPosition += len;
			return len;
		}
		System.arraycopy( mByteBuffer, mBufferReadPosition, b, off, bytes_in_buffer );
		// mBytesInBuffer = 0;
		int ret = mOpusFile.op_read_stereo( mBuffer, BUFF_SIZE );
		if( ret == JOggOpusFile.OP_HOLE ) {// corrupt file segment?
			return 0;
		}
		if( ret <= 0 ) {
			return -1;
		}
		ret <<= 1;// 2 is channel count
		// short -> byte little endian
		ByteBuffer.wrap( mByteBuffer ).order( mIsBigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN ).asShortBuffer().put( mBuffer, 0, ret );
		mBytesInBuffer = ret << 1;
		// if( ret != 0 ) {
			off += bytes_in_buffer;
			len -= bytes_in_buffer;
			if( len > mBytesInBuffer ) {
				len = mBytesInBuffer;
			}
			System.arraycopy( mByteBuffer, 0, b, off, len );
			mBufferReadPosition = len;
			return bytes_in_buffer + len;
		//}
		//return ret;
	}
}
