package libenc;

import java.io.IOException;
import java.io.RandomAccessFile;

final class JStdioObject implements IOpusEncCallbacks {
	RandomAccessFile file;
	// start JOpusEncCallbacks
	// static boolean stdio_write(Object user_data, final byte[] ptr, int len) {
	@Override
	public final boolean write(/*Object user_data, */final Joggp_page_aux data) {
		// final JStdioObject obj = (JStdioObject)user_data;
		try {
			this.file.write( data.page_base, data.page, data.bytes );
		} catch(final IOException ie) {
			return true;
		}
		return false;
	}

	/** java: use obj = null after calling */
	// private static final boolean stdio_close(Object user_data) {
	@Override
	public final boolean close(/*Object user_data*/) {
		// final JStdioObject obj = (JStdioObject)user_data;
		try {
			if( this.file != null ) {
				this.file.close();
			}
		} catch(final IOException ie) {
			return true;
		}
		return false;
	}

	/* private static final JOpusEncCallbacks stdio_callbacks = {
		stdio_write,
		stdio_close
	}; */

	// end JOpusEncCallbacks
}
