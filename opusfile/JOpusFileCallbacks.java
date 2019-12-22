package opusfile;

import java.io.IOException;

/** The callbacks used to access non-<code>FILE</code> stream resources.
The function prototypes are basically the same as for the stdio functions
 <code>fread()</code>, <code>fseek()</code>, <code>ftell()</code>, and
 <code>fclose()</code>.
The differences are that the <code>FILE *</code> arguments have been
 replaced with a <code>void *</code>, which is to be used as a pointer to
 whatever internal data these functions might need, that #seek and #tell
 take and return 64-bit offsets, and that #seek <em>must</em> return -1 if
 the stream is unseekable. */
public abstract class JOpusFileCallbacks {
	/* Seek method constants */
	public static final int SEEK_CUR = 1;
	public static final int SEEK_END = 2;
	public static final int SEEK_SET = 0;

	/** Used to read data from the stream.
	This must not be <code>NULL</code>. */
	// Jop_read_func  read;
	public final boolean mIsRead;
	/** Used to seek in the stream.
	This may be <code>NULL</code> if seeking is not implemented. */
	// Jop_seek_func  seek;
	public final boolean mIsSeek;
	/** Used to return the current read position in the stream.
	This may be <code>NULL</code> if seeking is not implemented. */
	// Jop_tell_func  tell;
	public final boolean mIsTell;
	/** Used to close the stream when the decoder is freed.
	This may be <code>NULL</code> to leave the stream open. */
	// Jop_close_func close;
	public final boolean mIsClose;
	//
	/* JOpusFileCallbacks() {
		read = null;
		seek = null;
		tell = null;
		close = null;
	}*/
	public JOpusFileCallbacks(final boolean isRead, final boolean isSeek, final boolean isTell, final boolean isClose) {
		mIsRead = isRead;
		mIsSeek = isSeek;
		mIsTell = isTell;
		mIsClose = isClose;
	}
	/** Reads up to \a _nbytes bytes of data from \a _stream.
	   @param      _stream The stream to read from.
	   @param _ptr [out]   The buffer to store the data in.
	   @param      _nbytes The maximum number of bytes to read.
	                       This function may return fewer, though it will not
	                        return zero unless it reaches end-of-file.
	   @return The number of bytes successfully read, or a negative value on
	            error. */
	public abstract int read(byte[] _ptr, int poffset, int _nbytes) throws IOException;
	/** Sets the position indicator for \a _stream.
	   The new position, measured in bytes, is obtained by adding \a _offset
	    bytes to the position specified by \a _whence.
	   If \a _whence is set to <code>SEEK_SET</code>, <code>SEEK_CUR</code>, or
	    <code>SEEK_END</code>, the offset is relative to the start of the stream,
	    the current position indicator, or end-of-file, respectively.
	   @retval 0  Success.
	   @retval -1 Seeking is not supported or an error occurred.
	              <code>errno</code> need not be set. */
	public abstract void seek(long _offset, int _whence) throws IOException;
	/** Obtains the current value of the position indicator for \a _stream.
	   @return The current position indicator. */
	public abstract long tell() throws IOException;
	/** Closes the underlying stream.
	   @retval 0   Success.
	   @retval EOF An error occurred.
	               <code>errno</code> need not be set. */
	public abstract void close() throws IOException;
}
