package opusfile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * java variant of the stream.c for files.
 */
public final class JOpusFileInputStream extends JOpusFileCallbacks {
	private final RandomAccessFile mFile;
	/**
	 * op_fopen
	 *
	 * @throws FileNotFoundException
	 */
	public JOpusFileInputStream(final String path, final String mode) throws FileNotFoundException {
		super( true, true, true, true );
		mFile = new RandomAccessFile( path, mode );
	}
	@Override
	public final int read(final byte[] _ptr, final int poffset, final int _nbytes) throws IOException {
		return mFile.read( _ptr, poffset, _nbytes );
	}

	@Override
	public final void seek(final long _offset, final int _whence) throws IOException {
		if( _whence == SEEK_SET ) {
			mFile.seek( _offset );
			return;
		}
		if( _whence == SEEK_END ) {
			mFile.seek( mFile.length() + _offset );
			return;
		}
		if( _whence == SEEK_CUR ) {
			mFile.seek( mFile.getFilePointer() + _offset );
			return;
		}
		throw new IOException("Incorrect argument _whence: " + Integer.toString( _whence ) );
	}

	@Override
	public final long tell() throws IOException {
		return mFile.getFilePointer();
	}

	@Override
	public final void close() throws IOException {
		mFile.close();
	}

}
