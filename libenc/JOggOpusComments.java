package libenc;

import java.nio.charset.Charset;
import java.util.Arrays;

import celt.Jcelt;

/** Opaque comments struct. */
/**
Comments will be stored in the Vorbis style.
It is described in the "Structure" section of
   http://www.xiph.org/ogg/vorbis/doc/v-comment.html

However, Opus and other non-vorbis formats omit the "framing_bit".

The comment header is decoded as follows:
 1) [vendor_length] = read an unsigned integer of 32 bits
 2) [vendor_string] = read a UTF-8 vector as [vendor_length] octets
 3) [user_comment_list_length] = read an unsigned integer of 32 bits
 4) iterate [user_comment_list_length] times {
    5) [length] = read an unsigned integer of 32 bits
    6) this iteration's user comment = read a UTF-8 vector as [length] octets
    }
 7) done.
*/
public final class JOggOpusComments {// java: check ope_comments_copy if a new field is added or removed.
	byte[] comment;
	// int comment_length;// java: comment.length
	boolean seen_file_icons;

	/** Create a new comments object. The vendor string is optional. */
	/** Create a new comments object.
	@return Newly-created comments object. */
	public static final JOggOpusComments ope_comments_create() {
		// char vendor_str[1024];
		final JOggOpusComments c = new JOggOpusComments();
		// final byte[] libopus_str = Jcelt.opus_get_version_string();
		// snprintf( vendor_str, sizeof(vendor_str), "%s, %s %s", libopus_str, PACKAGE_NAME, PACKAGE_VERSION );
		final String vendor_str = String.format( "%s, %s %s", Jcelt.opus_get_version_string(), JOggOpusEnc.PACKAGE_NAME, JOggOpusEnc.PACKAGE_VERSION );
		c.opeint_comment_init( vendor_str );
		c.seen_file_icons = false;
		if( c.comment == null  ) {
			return null;
		}
		return c;
	}

	/** Create a deep copy of a comments object. */
	/** Create a deep copy of a comments object.
	@param comments Comments object to copy
	@return Deep copy of input. */
	public static final JOggOpusComments ope_comments_copy(final JOggOpusComments comments) {
		final JOggOpusComments c = new JOggOpusComments();
		// if( c == null ) return null;
		//memcpy(c, comments, sizeof(*c));
		// c.comment_length = comments.comment_length;
		c.seen_file_icons = comments.seen_file_icons;
		c.comment = new byte[ comments.comment.length ];
		//if( c.comment == null ) {
		//	free(c);
		//	return null;
		//} else {
		//	memcpy(c.comment, comments.comment, comments.comment_length);
		//	return c;
		//}
		c.comment = Arrays.copyOf( comments.comment, comments.comment.length );
		return c;
	}

	/** Destroys a comments object. */
	/** Destroys a comments object.
 	@param comments Comments object to destroy*/
	public static final void ope_comments_destroy(JOggOpusComments comments) {
		comments.comment = null;
		comments = null;
	}

	/** Add a comment. */
	/** Add a comment.
	@param comments [in,out] Where to add the comments
	@param         tag      Tag for the comment (must not contain = char)
	@param         val      Value for the tag
	@return Error code
	 */
	public final int ope_comments_add(final String tag, final String val) {
		if( tag == null || val == null ) {
			return JOggOpusEnc.OPE_BAD_ARG;
		}
		if( tag.indexOf('=') >= 0 ) {
			return JOggOpusEnc.OPE_BAD_ARG;
		}
		// if( opeint_comment_add( &comments.comment, &comments.comment_length, tag, val) ) return OPE_ALLOC_FAIL;
		if( this.opeint_comment_add( tag, val) ) {
			return JOggOpusEnc.OPE_ALLOC_FAIL;
		}
		return JOggOpusEnc.OPE_OK;
	}

	/** Add a comment. */
	/** Add a comment as a single tag=value string.
	@param comments [in,out]   Where to add the comments
	@param         tag_and_val string of the form tag=value (must contain = char)
	@return Error code
	 */
	public final int ope_comments_add_string(final String tag_and_val) {
		if( tag_and_val.indexOf('=') < 0 ) {
			return JOggOpusEnc.OPE_BAD_ARG;
		}
		if( this.opeint_comment_add( null, tag_and_val) ) {
			return JOggOpusEnc.OPE_ALLOC_FAIL;
		}
		return JOggOpusEnc.OPE_OK;
	}

	private static final int readint(final byte[] buf, int offset) {
		int v = ((int)buf[ offset++ ]) & 0xFF;
		v |= ((int)buf[ offset++ ] << 8) & 0xFF00;
		v |= ((int)buf[ offset++ ] << 16) & 0xFF0000;
		v |= ((int)buf[ offset ] << 24);// & 0xFF000000;
		return v;
	}

	private static final void writeint(final byte[] buf, int offset, final int val) {
		buf[ offset++ ] = (byte)val;
		buf[ offset++ ] = (byte)(val >> 8);
		buf[ offset++ ] = (byte)(val >> 16);
		buf[ offset ] = (byte)(val >> 24);
	}

	// java: char **comments, int* length are replaced by JOggOpusComments
	final void opeint_comment_init(final String vendor_string)
	{
		/*The 'vendor' field should be the actual encoding library used.*/
		// int vendor_length = strlen(vendor_string);
		// int user_comment_list_length = 0;
		// int len = 8 + 4 + vendor_length + 4;
		// char *p = (char*)malloc(len);
		// if( p == NULL ) {
		//	len = 0;
		// } else {
		//	memcpy(p, "OpusTags", 8);
		//	writeint(p, 8, vendor_length);
		//	memcpy(p + 12, vendor_string, vendor_length);
		//	writeint(p, 12 + vendor_length, user_comment_list_length);
		//}
		// *length = len;
		// *comments = p;
		// java:
		final byte sbyte[] = vendor_string.getBytes( Charset.forName("UTF-8") );
		final int vendor_length = sbyte.length;
		final int user_comment_list_length = 0;
		final int len = 8 + 4 + vendor_length + 4;
		final byte[] p = new byte[ len ];
		System.arraycopy( "OpusTags".getBytes( Charset.forName("UTF-8") ), 0, p, 0, 8 );
		writeint( p, 8, vendor_length );
		System.arraycopy( p, 12, sbyte, 0, vendor_length );
		writeint( p, 12 + vendor_length, user_comment_list_length );
		// this.comment_length = len;
		this.comment = p;
	}

	private static final int strlen(final byte[] str, int offset) {
		do {
			if( str[offset] == '\0' ) {
				return offset;
			}
			offset++;
		} while( offset < str.length );
		return offset;
	}

	// java: char **comments, int* length are replaced by JOggOpusComments
	/**
	 *
	 * @param comments the comments
	 * @param tag the tag name.
	 * @param val 0-ended data.
	 * @return false - ok.
	 */
	final boolean opeint_comment_add(final String tag, final byte[] val)
	{
		byte[] p = this.comment;
		final int vendor_length = readint( p, 8 );
		final int user_comment_list_length = readint( p, 8 + 4 + vendor_length );
		//int tag_len = (tag ? strlen( tag ) + 1 : 0);
		final byte[] stag = tag != null ? tag.getBytes( Charset.forName("UTF-8") ) : new byte[0];
		final int val_len = strlen( val, 0 );
		//int len = (*length) + 4 + tag_len + val_len;
		final int len = this.comment.length + 4 + 1 + stag.length + val_len;

		p = Arrays.copyOf( p, len );
		// if( p == null ) return true;

		writeint( p, this.comment.length, 1 + stag.length + val_len );/* length of comment */
		if( tag != null ) {
			System.arraycopy( tag, 0, p, this.comment.length + 4, stag.length );/* comment tag */
			p[this.comment.length + 4 + stag.length] = '=';/* separator */
		}
		System.arraycopy( val, 0, p, this.comment.length + 4 + 1 + stag.length, val_len );/* comment */
		writeint( p, 8 + 4 + vendor_length, user_comment_list_length + 1 );
		this.comment = p;
		// this.comment_length = len;
		return false;
	}

	// java: char **comments, int* length are replaced by JOggOpusComments
	final boolean opeint_comment_add(final String tag, String val)
	{
		byte[] p = this.comment;
		final int vendor_length = readint( p, 8 );
		final int user_comment_list_length = readint( p, 8 + 4 + vendor_length );
		//int tag_len = (tag ? strlen( tag ) + 1 : 0);
		//int val_len = strlen( val );
		//int len = (*length) + 4 + tag_len + val_len;

		//p = Arrays.copyOf( p, len );
		//if( p == null ) return true;

		//writeint( p, *length, tag_len + val_len );      /* length of comment */
		//if( tag ) {
		//	memcpy( p + *length + 4, tag, tag_len );        /* comment tag */
		//	(p + *length + 4)[tag_len - 1] = '=';           /* separator */
		//}
		//memcpy( p + *length + 4 + tag_len, val, val_len );  /* comment */
		//writeint( p, 8 + 4 + vendor_length, user_comment_list_length + 1 );
		//*comments = p;
		//*length = len;
		if( tag != null && ! tag.isEmpty() ) {
			val = tag + '=' + val;
		}
		final byte[] sbyte = val.getBytes( Charset.forName("UTF-8") );
		final int len = this.comment.length + 4 + sbyte.length;
		p = Arrays.copyOf( p, len );
		writeint( p, this.comment.length, sbyte.length );// length of comment
		System.arraycopy( sbyte, 0, p, this.comment.length + 4, sbyte.length );// comment
		writeint( p, 8 + 4 + vendor_length, user_comment_list_length + 1 );
		this.comment = p;
		// this.comment_length = len;
		return false;
	}

	/**
	 * Java changed: a new comment buffer is returned
	 *
	 * @param comments a buffer
	 * @param amount a required size
	 * @return the buffer to hold data
	 */
	static final byte[] opeint_comment_pad(byte[] comments, final int amount)
	{
		if( amount > 0 ) {
			if( comments == null ) {
				final int newlen = (amount + 255) / 255 * 255 - 1;
				comments = new byte[ newlen ];
				return comments;
			}
			/*Make sure there is at least amount worth of padding free, and
			   round up to the maximum that fits in the current ogg segments.*/
			final int newlen = (comments.length + amount + 255) / 255 * 255 - 1;
			comments = Arrays.copyOf( comments, newlen );
			// if( p == NULL ) return;
			// for( int i = comments.comment_length; i < newlen; i++ ) p[i] = 0;// java: already zeroed
		}
		return comments;
	}

	/** Add a picture from a file.
	@param comments [in,out]    Where to add the comments
	@param         filename     File name for the picture
	@param         picture_type Type of picture (-1 for default)
	@param         description  Description (NULL means no comment)
	@return Error code
	 */
	public final int ope_comments_add_picture(final String filename, final int picture_type, final String description) {
		//int err;
		//byte[] picture_data = Jpicture.opeint_parse_picture_specification( filename, picture_type, description, &err, &comments.seen_file_icons );
		//if( picture_data == null || err != OPE_OK ) {
		//	return err;
		//}
		//JOpusHeader.opeint_comment_add( comments, "METADATA_BLOCK_PICTURE", picture_data );
		//picture_data = null;
		return JOggOpusEnc.OPE_OK;// TODO java: read a picture
	}

	/** Add a picture already in memory.
	@param comments [in,out]    Where to add the comments
	@param         ptr          Pointer to picture in memory
	@param         size         Size of picture pointed to by ptr
	@param         picture_type Type of picture (-1 for default)
	@param         description  Description (NULL means no comment)
	@return Error code
	 */
	public final int ope_comments_add_picture_from_memory(final byte[] ptr, final int size, final int picture_type, final String description ) {
		//int err;
		//final byte[] picture_data = JOpusHeader.opeint_parse_picture_specification_from_memory( ptr, size, picture_type, description, &err, &comments.seen_file_icons );
		//if( picture_data == null || err != OPE_OK ) {
		//	return err;
		//}
		//JOpusHeader.opeint_comment_add( comments, "METADATA_BLOCK_PICTURE", picture_data );
		// picture_data = null;
		return JOggOpusEnc.OPE_OK;// TODO java: read a picture
	}
}
