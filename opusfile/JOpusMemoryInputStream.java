package opusfile;

import java.io.IOException;

/**
 * java variant of the stream.c for memory.
 */
public class JOpusMemoryInputStream extends JOpusFileCallbacks {
	private static final long OP_MEM_SIZE_MAX = Integer.MAX_VALUE >> 1;// ( ~( size_t ) 0 >> 1 );
	private static final long OP_MEM_DIFF_MAX = OP_MEM_SIZE_MAX;
	/** The block of memory to read from. */
	private byte[] data;
	/** The total size of the block.
	This must be at most OP_MEM_SIZE_MAX to prevent signed overflow while
	seeking. */
	private final int size;
	/** The current file position.
	This is allowed to be set arbitrarily greater than size ( i.e., past the end
	of the block, though we will not read data past the end of the block ) , but
	is not allowed to be negative ( i.e., before the beginning of the block ) . */
	private int       pos;
	//
	public JOpusMemoryInputStream(final byte[] bdata, final int length) {
		super( true, true, true, true );
		data = bdata;
		size = length;
		pos = 0;
	}
	@Override
	public int read(final byte[] _ptr, final int poffset, final int _nbytes) throws IOException {
		int _buf_size = _nbytes - poffset;// java
		/*Check for empty read.*/
		if( _buf_size <= 0 ) {
			return 0;
		}
		int currr_size = this.size;
		int curr_pos = this.pos;
		/*Check for EOF.*/
		if( curr_pos >= currr_size ) {
			return 0;
		}
		/*Check for a short read.*/
		currr_size -= curr_pos;
		_buf_size = ( currr_size <= _buf_size ? currr_size : _buf_size );
		System.arraycopy( this.data, curr_pos, _ptr, poffset, _buf_size );
		curr_pos += _buf_size;
		this.pos = curr_pos;
		return _buf_size;
	}

	@Override
	public void seek(final long _offset, final int _whence) throws IOException {
		long curr_pos = (long)this.pos;
		// OP_ASSERT( pos >= 0 );
		switch( _whence ) {
		case SEEK_SET: {
				/*Check for overflow:*/
				if( _offset < 0 || _offset > OP_MEM_DIFF_MAX ) {
					throw new IOException();// return -1;
				}
				curr_pos = _offset;
			} break;
		case SEEK_CUR:{
				/*Check for overflow:*/
				if( _offset < -curr_pos || _offset > OP_MEM_DIFF_MAX - curr_pos ) {
					throw new IOException();// return -1;
				}
				curr_pos += _offset;
			} break;
		case SEEK_END: {
				final long curr_size = (long)this.size;
				// OP_ASSERT( size >= 0 );
				/*Check for overflow:*/
				if( _offset > curr_size || _offset < curr_size - OP_MEM_DIFF_MAX ) {
					throw new IOException();// return -1;
				}
				curr_pos = curr_size - _offset;// FIXME may be curr_size - _offset ?
			} break;
		default: throw new IOException();// return -1;
		}
		this.pos = (int)curr_pos;
	}

	@Override
	public long tell() throws IOException {
		return (long) this.pos;
	}

	@Override
	public void close() throws IOException {
		data = null;
	}

}
