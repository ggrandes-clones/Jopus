package opusfile;

import java.util.Arrays;

/** The metadata from an Ogg Opus stream.

This structure holds the in-stream metadata corresponding to the 'comment'
 header packet of an Ogg Opus stream.
The comment header is meant to be used much like someone jotting a quick
 note on the label of a CD.
It should be a short, to the point text note that can be more than a couple
 words, but not more than a short paragraph.

The metadata is stored as a series of (tag, value) pairs, in length-encoded
 string vectors, using the same format as Vorbis (without the final "framing
 bit"), Theora, and Speex, except for the packet header.
The first occurrence of the '=' character delimits the tag and value.
A particular tag may occur more than once, and order is significant.
The character set encoding for the strings is always UTF-8, but the tag
 names are limited to ASCII, and treated as case-insensitive.
See <a href="http://www.xiph.org/vorbis/doc/v-comment.html">the Vorbis
 comment header specification</a> for details.

In filling in this structure, <tt>libopusfile</tt> will null-terminate the
 #user_comments strings for safety.
However, the bitstream format itself treats them as 8-bit clean vectors,
 possibly containing NUL characters, so the #comment_lengths array should be
 treated as their authoritative length.

This structure is binary and source-compatible with a
 <code>vorbis_comment</code>, and pointers to it may be freely cast to
 <code>vorbis_comment</code> pointers, and vice versa.
It is provided as a separate type to avoid introducing a compile-time
 dependency on the libvorbis headers. */
public final class JOpusTags extends Jinfo {
	/** java: encoding charset for comments */
	public static final String CHAR_ENCODING = "UTF-8";
	/** byte array "OpusTags" */
	private static final byte[] OpusTags = { 'O', 'p', 'u', 's', 'T', 'a', 'g', 's' };
	/** The array of comment string vectors. */
	public byte[][] user_comments;// java: don't using String, because it may be picture BASE64 encoded data
	/** An array of the corresponding length of each vector, in bytes. */
	int[] comment_lengths;// java: is it possible using user_comments[].length instead and don't set '\0' at the end of string?
	/** The total number of comment streams. */
	public int    comments;// java: comment_lengths != user_comments, beacuse uses binary suffix. is it possible remove it?
	/** The null-terminated vendor string.
	  This identifies the software used to encode the stream. */
	public String vendor;
	//
	final void clear() {
		user_comments = null;
		comment_lengths = null;
		comments = 0;
		vendor = null;
	}
	final void copyFrom(final JOpusTags t) {
		user_comments = t.user_comments;
		comment_lengths = t.comment_lengths;
		comments = t.comments;
		vendor = t.vendor;
	}
	/** Initializes an #OpusTags structure.
	   This should be called on a freshly allocated #OpusTags structure before
	    attempting to use it.
	   @param _tags The #OpusTags structure to initialize. */
	private final void opus_tags_init() {
		clear();
	}
	/** Clears the #OpusTags structure.
	   This should be called on an #OpusTags structure after it is no longer
	    needed.
	   It will free all memory used by the structure members.
	   @param _tags The #OpusTags structure to clear. */
	final void opus_tags_clear() {
		int ncomments = this.comments;
		if( this.user_comments != null ) {
			ncomments++;
		}
		for( int ci = ncomments; ci-- > 0;  ) {
			this.user_comments[ci] = null;
		}
		this.user_comments = null;
		this.comment_lengths = null;
		this.vendor = null;
	}
	/** Ensure there's room for up to _ncomments comments. */
	private final int op_tags_ensure_capacity( final int _ncomments ) {
		if( _ncomments < 0 || _ncomments >= Integer.MAX_VALUE ) {
			return JOggOpusFile.OP_EFAULT;
		}
		int size = _ncomments + 1;
		// if( size / sizeof(*_tags.comment_lengths) != _ncomments + 1) return JOggOpusFile.OP_EFAULT;
		final int cur_ncomments = this.comments;
		/*We only support growing.
		  Trimming requires cleaning up the allocated strings in the old space, and
		  is best handled separately if it's ever needed.*/
		// OP_ASSERT(_ncomments>=(size_t)cur_ncomments);
		int[] tag_comment_lengths = this.comment_lengths;// java renamed to avoid hiding
		tag_comment_lengths = tag_comment_lengths == null ? new int[ size ] : Arrays.copyOf( tag_comment_lengths, size );
		if( tag_comment_lengths == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		if( this.comment_lengths == null ) {
			// OP_ASSERT(cur_ncomments==0);
			tag_comment_lengths[ cur_ncomments ]=0;
		}
		tag_comment_lengths[_ncomments] = tag_comment_lengths[cur_ncomments];
		this.comment_lengths = tag_comment_lengths;
		size = _ncomments + 1;
		// if( size / sizeof(*_tags.user_comments) != _ncomments + 1 ) return JOggOpusFile.OP_EFAULT;
		final byte[][] tag_user_comments = this.user_comments == null ? new byte[ size ][] : Arrays.copyOf( this.user_comments, size );
		if( tag_user_comments == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		if( this.user_comments == null ) {
			// OP_ASSERT(cur_ncomments==0);
			tag_user_comments[cur_ncomments] = null;
		}
		tag_user_comments[_ncomments] = tag_user_comments[cur_ncomments];
		this.user_comments = tag_user_comments;
		return 0;
	}

	/** A version of strncasecmp() that is guaranteed to only ignore the case of ASCII characters. */
	private static final int op_strncasecmp(final byte[] _a, final byte[] _b, final int _n) {
		for(int i = 0; i < _n; i++ ) {
			int a = _a[i];
			int b = _b[i];
			if( a >= 'a' && a <= 'z' ) {
				a -= 'a' - 'A';
			}
			if( b >= 'a' && b <= 'z' ) {
				b -= 'a' - 'A';
			}
			final int d = a - b;
			if( d != 0 ) {
				return d;
			}
		}
		return 0;
	}
	/**
	 * Duplicate a (possibly non-NUL terminated) string with a known length.
	 */
	private static final byte[] op_strdup_with_len( final byte[] _s, final int soffset, final int _len ) {
		final int size = _len + 1;
		if( size < _len ) {// java overflow checking?
			return null;
		}
		final byte[] ret = new byte[ size ];
		if( ret != null ) {
			System.arraycopy( _s, soffset, ret, 0, _len );
			ret[_len] = '\0';
		}
		return ret;
	}
	/** The actual implementation of opus_tags_parse().
	Unlike the public API, this function requires _tags to already be
	initialized, modifies its contents before success is guaranteed, and assumes
	the caller will clear it on error. */
	private static final int opus_tags_parse_impl( final JOpusTags _tags, final byte[] _data, int doffset, final int _len ) {
		int len = _len;
		if( len < 8 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		if( memcmp( _data, doffset, OpusTags, 8 ) != 0 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		if( len < 16 ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		doffset += 8;
		len -= 8;
		int count = op_parse_uint32le( _data, doffset );
		doffset += 4;
		len -= 4;
		if( count > len ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		if( _tags != null ) {
			_tags.vendor = new String( _data, doffset, count );// op_strdup_with_len( _data, doffset, count );
			/* if( _tags.vendor == null ) {
				return JOggOpusFile.OP_EFAULT;
			}*/
		}
		doffset += count;
		len -= count;
		if( len < 4 ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		count = op_parse_uint32le( _data, doffset );
		doffset += 4;
		len -= 4;
		/*Check to make sure there's minimally sufficient data left in the packet.*/
		if( count > len >>> 2 ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		/*Check for overflow ( the API limits this to an int ).*/
		if( count > Integer.MAX_VALUE - 1 ) {
			return JOggOpusFile.OP_EFAULT;
		}
		if( _tags != null ) {
			final int ret = _tags.op_tags_ensure_capacity( count );
			if( ret < 0 ) {
				return ret;
			}
		}
		final int ncomments = (int)count;
		for( int ci = 0; ci < ncomments; ci++ ) {
			/*Check to make sure there's minimally sufficient data left in the packet.*/
			if( ( ncomments - ci ) > len >>> 2 ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			count = op_parse_uint32le( _data, doffset );
			doffset += 4;
			len -= 4;
			if( count > len ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			/*Check for overflow ( the API limits this to an int ).*/
			if( count < 0 ) {// if( count > ( opus_uint32 )INT_MAX ) {// java changed
				return JOggOpusFile.OP_EFAULT;
			}
			if( _tags != null ) {
				_tags.user_comments[ci] = op_strdup_with_len( _data, doffset, count );
				if( _tags.user_comments[ci] == null ) {
					return JOggOpusFile.OP_EFAULT;
				}
				_tags.comment_lengths[ci] = (int)count;
				_tags.comments = ci + 1;
				/*Needed by opus_tags_clear() if we fail before parsing the (optional)
	 			 binary metadata.*/
				_tags.user_comments[ci + 1] = null;
			}
			doffset += count;
			len -= count;
		}
		if( len < 0 ) {// java changed, len is signed
			return JOggOpusFile.OP_EFAULT;
		}
		if( len > 0 && (_data[doffset] & 1) != 0 ) {
			// java moved up
			/* if( len > ( opus_uint32 )INT_MAX ) {
				return JOggOpusFile.OP_EFAULT;
			}*/
			// _tags.user_comments[ncomments] = new byte[ len ];
			/* if( _tags.user_comments[ncomments] == null ) {
				return JOggOpusFile.OP_EFAULT;
			}*/
			if( _tags != null ) {
				final byte[] tmp = new byte[ len ];
				System.arraycopy( _data, doffset, tmp, 0, len );
				_tags.user_comments[ncomments] = tmp;
				_tags.comment_lengths[ncomments] = (int)len;
			}
		}
		return 0;
	}
	/** Parses the contents of the 'comment' header packet of an Ogg Opus stream.
	   @param _tags [out] An uninitialized #OpusTags structure.
	                     This returns the contents of the parsed packet.
	                     The contents of this structure are untouched on error.
	                     This may be <code>NULL</code> to merely test the header
	                      for validity.
	   @param _data [in] The contents of the 'comment' header packet.
	   @param      _len  The number of bytes of data in the 'info' header packet.
	   @retval 0              Success.
	   @retval #OP_ENOTFORMAT If the data does not start with the "OpusTags"
	                           string.
	   @retval #OP_EBADHEADER If the contents of the packet otherwise violate the
	                           Ogg Opus specification.
	   @retval #OP_EFAULT     If there wasn't enough memory to store the tags. */
	static final int opus_tags_parse( final JOpusTags _tags, final byte[] _data, final int doffset, final int _len ) {
		if( _tags != null ) {
			final JOpusTags tags = new JOpusTags();
			tags.opus_tags_init();
			final int ret = opus_tags_parse_impl( tags, _data, doffset, _len );
			if( ret < 0 ) {
				tags.opus_tags_clear();
			} else {
				_tags.copyFrom( tags );
			}
			return ret;
		}
		return opus_tags_parse_impl( null, _data, doffset, _len );
	}
	/** The actual implementation of opus_tags_copy().
	Unlike the public API, this function requires _dst to already be
	initialized, modifies its contents before success is guaranteed, and assumes
	the caller will clear it on error. */
	private final int opus_tags_copy_impl( final JOpusTags _src ) {
		// java changed, because java uses String
		// String vendor = _src.vendor;
		this.vendor = _src.vendor;// op_strdup_with_len( vendor, strlen( vendor ) );
		if( this.vendor == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		final int ncomments = _src.comments;
		final int ret = this.op_tags_ensure_capacity( ncomments );
		if( ret < 0 ) {
			return ret;
		}
		for( int ci = 0; ci < ncomments; ) {
			final int len = _src.comment_lengths[ci];
			// OP_ASSERT( len >= 0 );
			this.user_comments[ci] = op_strdup_with_len( _src.user_comments[ci], 0, len );
			if( this.user_comments[ci] == null ) {
				return JOggOpusFile.OP_EFAULT;
			}
			this.comment_lengths[ci] = len;
			this.comments = ++ci;
		}
		if( _src.comment_lengths != null ) {// FIXME _src.comment_lengths uses above without checking!!!
			final int len = _src.comment_lengths[ncomments];// FIXME ncomments is correct index?
			if( len > 0 ) {// FIXME why need special copy code for this position? why no op_strdup_with_len? why no set '\0' at end of string?
				this.user_comments[ncomments] = new byte[ len ];
				/* if( this.user_comments[ncomments] == null ) {
					return JOggOpusFile.OP_EFAULT;
				}*/
				System.arraycopy( _src.user_comments[ncomments], 0, this.user_comments[ncomments], 0, len );
				this.comment_lengths[ncomments] = len;
			}
		}
		return 0;
	}

	/** Performs a deep copy of an #OpusTags structure.
	   @param _dst The #OpusTags structure to copy into.
	               If this function fails, the contents of this structure remain
	                untouched.
	   @param _src The #OpusTags structure to copy from.
	   @retval 0          Success.
	   @retval #OP_EFAULT If there wasn't enough memory to copy the tags. */
	public static final int opus_tags_copy( final JOpusTags _dst, final JOpusTags _src ) {
		final JOpusTags dst = new JOpusTags();
		dst.opus_tags_init();
		final int ret = dst.opus_tags_copy_impl( _src );
		if( ret < 0 ) {
			dst.opus_tags_clear();
		} else {
			_dst.copyFrom( dst );
		}
		return ret;
	}

	/** Add a (tag, value) pair to an initialized #OpusTags structure.
	   @note Neither opus_tags_add() nor opus_tags_add_comment() support values
	    containing embedded NULs, although the bitstream format does support them.
	   To add such tags, you will need to manipulate the #OpusTags structure
	    directly.
	   @param _tags  The #OpusTags structure to add the (tag, value) pair to.
	   @param _tag   A NUL-terminated, case-insensitive, ASCII string containing
	                  the tag to add (without an '=' character).
	   @param _value A NUL-terminated UTF-8 containing the corresponding value.
	   @return 0 on success, or a negative value on failure.
	   @retval #OP_EFAULT An internal memory allocation failed. */
	public final int opus_tags_add( final byte[] _tag, final byte[] _value ) {
		final int ncomments = this.comments;
		final int ret = op_tags_ensure_capacity( ncomments + 1 );
		if( ret < 0 ) {
			return ret;
		}
		final int tag_len = strlen( _tag, 0 );
		final int value_len = strlen( _value, 0 );
		/* +2 for '=' and '\0'.*/
		if( tag_len + value_len < tag_len ) {
			return JOggOpusFile.OP_EFAULT;
		}
		if( tag_len + value_len > Integer.MAX_VALUE - 2 ) {
			return JOggOpusFile.OP_EFAULT;
		}
		final byte[] comment = new byte[ tag_len + value_len + 2 ];
		/* if( comment == null ) {
			return JOggOpusFile.OP_EFAULT;
		} */
		System.arraycopy( _tag, 0, comment, 0, tag_len );
		comment[tag_len] = '=';
		System.arraycopy( _value, 0, comment, tag_len + 1, value_len + 1 );
		this.user_comments[ncomments] = comment;
		this.comment_lengths[ncomments] = tag_len + value_len + 1;
		this.comments = ncomments + 1;
		return 0;
	}

	/** Add a comment to an initialized #OpusTags structure.
	   @note Neither opus_tags_add_comment() nor opus_tags_add() support comments
	    containing embedded NULs, although the bitstream format does support them.
	   To add such tags, you will need to manipulate the #OpusTags structure
	    directly.
	   @param _tags    The #OpusTags structure to add the comment to.
	   @param _comment A NUL-terminated UTF-8 string containing the comment in
	                    "TAG=value" form.
	   @return 0 on success, or a negative value on failure.
	   @retval #OP_EFAULT An internal memory allocation failed. */
	public final int opus_tags_add_comment( final byte[] _comment ) {
		final int ncomments = this.comments;
		final int ret = op_tags_ensure_capacity( ncomments + 1 );
		if( ret < 0 ) {
			return ret;
		}
		final int comment_len = strlen( _comment, 0 );
		final byte[] comment = op_strdup_with_len( _comment, 0, comment_len );
		if( comment == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		this.user_comments[ncomments] = comment;
		this.comment_lengths[ncomments] = comment_len;
		this.comments = ncomments + 1;
		return 0;
	}

	/** Replace the binary suffix data at the end of the packet (if any).
	   @param _tags An initialized #OpusTags structure.
	   @param _data A buffer of binary data to append after the encoded user
	                 comments.
	                The least significant bit of the first byte of this data must
	                 be set (to ensure the data is preserved by other editors).
	   @param _len  The number of bytes of binary data to append.
	                This may be zero to remove any existing binary suffix data.
	   @return 0 on success, or a negative value on error.
	   @retval #OP_EINVAL \a _len was negative, or \a _len was positive but
	                       \a _data was <code>NULL</code> or the least significant
	                       bit of the first byte was not set.
	   @retval #OP_EFAULT An internal memory allocation failed. */
	public final int opus_tags_set_binary_suffix( final byte[] _data, final int _len ) {
		if( _len < 0 || _len > 0 && ( _data == null || 0 == (_data[0] & 1) ) ) {
			return JOggOpusFile.OP_EINVAL;
		}
		final int ncomments = this.comments;
		final int ret = op_tags_ensure_capacity( ncomments );
		if( ret < 0 ) {
			return ret;
		}
		final byte[] binary_suffix_data = Arrays.copyOf( this.user_comments[ncomments], _len );
		if( binary_suffix_data == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		System.arraycopy( _data, 0, binary_suffix_data, 0, _len );
		this.user_comments[ncomments] = binary_suffix_data;
		this.comment_lengths[ncomments] = _len;
		return 0;
	}
	/** Check if \a _comment is an instance of a \a _tag_name tag.
	   \see opus_tagncompare
	   @param _tag_name A NUL-terminated, case-insensitive, ASCII string containing
	                     the name of the tag to check for (without the terminating
	                     '=' character).
	   @param _comment  The comment string to check.
	   @return An integer less than, equal to, or greater than zero if \a _comment
	            is found respectively, to be less than, to match, or be greater
	            than a "tag=value" string whose tag matches \a _tag_name. */
	public static final int opus_tagcompare( final byte[] _tag_name, final byte[] _comment ) {
		final int tag_len = strlen( _tag_name, 0 );
		if( tag_len > Integer.MAX_VALUE ) {
			return -1;
		}
		return opus_tagncompare( _tag_name, tag_len, _comment );
	}

	/**Check if \a _comment is an instance of a \a _tag_name tag.
	   This version is slightly more efficient than opus_tagcompare() if the length
	    of the tag name is already known (e.g., because it is a constant).
	   \see opus_tagcompare
	   @param _tag_name A case-insensitive ASCII string containing the name of the
	                     tag to check for (without the terminating '=' character).
	   @param _tag_len  The number of characters in the tag name.
	                    This must be non-negative.
	   @param _comment  The comment string to check.
	   @return An integer less than, equal to, or greater than zero if \a _comment
	            is found respectively, to be less than, to match, or be greater
	            than a "tag=value" string whose tag matches the first \a _tag_len
	            characters of \a _tag_name.*/
	public static final int opus_tagncompare( final byte[] _tag_name, final int _tag_len, final byte[] _comment ) {
		// OP_ASSERT( _tag_len >= 0 );
		final int ret = op_strncasecmp( _tag_name, _comment, _tag_len );
		return ret != 0 ? ret : '=' - _comment[_tag_len];
	}

	/** Look up a comment value by its tag.
	   @param _tags  An initialized #OpusTags structure.
	   @param _tag   The tag to look up.
	   @param _count The instance of the tag.
	                 The same tag can appear multiple times, each with a distinct
	                  value, so an index is required to retrieve them all.
	                 The order in which these values appear is significant and
	                  should be preserved.
	                 Use opus_tags_query_count() to get the legal range for the
	                  \a _count parameter.
	   @return A pointer to the queried tag's value.
	           This points directly to data in the #OpusTags structure.
	           It should not be modified or freed by the application, and
	            modifications to the structure may invalidate the pointer.
	   @retval NULL If no matching tag is found. */
	public final byte[] opus_tags_query( final byte[] _tag, final int _count ) {
		final int tag_len = strlen( _tag, 0 );
		if( tag_len > Integer.MAX_VALUE ) {
			return null;
		}
		final int ncomments = this.comments;
		final byte[][] tag_user_comments = this.user_comments;
		int found = 0;
		for( int ci = 0; ci < ncomments; ci++ ) {
			if( 0 == opus_tagncompare( _tag, tag_len, tag_user_comments[ci] ) ) {
				/*We return a pointer to the data, not a copy.*/
				if( _count == found++ ) {
					// return user_comments[ci] + tag_len + 1;
					final byte[] tmp = new byte[ tag_user_comments[ci].length - tag_len - 1 ];
					System.arraycopy( tag_user_comments[ci], tag_len + 1, tmp, 0, tmp.length );
					return tmp;
				}
			}
		}
		/*Didn't find anything.*/
		return null;
	}

	/** Look up the number of instances of a tag.
	   Call this first when querying for a specific tag and then iterate over the
	    number of instances with separate calls to opus_tags_query() to retrieve
	    all the values for that tag in order.
	   @param _tags An initialized #OpusTags structure.
	   @param _tag  The tag to look up.
	   @return The number of instances of this particular tag. */
	public final int opus_tags_query_count( final byte[] _tag ) {
		final int tag_len = strlen( _tag, 0 );
		if( tag_len > Integer.MAX_VALUE ) {
			return 0;
		}
		final int ncomments = this.comments;
		final byte[][] tag_user_comments = this.user_comments;
		int found = 0;
		for( int ci = 0; ci < ncomments; ci++ ) {
			if( 0 == opus_tagncompare( _tag, tag_len, tag_user_comments[ci] ) ) {
				found++;
			}
		}
		return found;
	}

	/** Retrieve the binary suffix data at the end of the packet (if any).
	   @param      _tags An initialized #OpusTags structure.
	   @param _len [out] Returns the number of bytes of binary suffix data returned.
	   @return A pointer to the binary suffix data, or <code>NULL</code> if none
	            was present. */
	public final byte[] opus_tags_get_binary_suffix(final int[] _len) {
		final int ncomments = this.comments;
		final int len = this.comment_lengths == null ? 0 : this.comment_lengths[ncomments];
		_len[0] = len;
		// OP_ASSERT( len == 0 || _tags.user_comments != null );
		return len > 0 ? this.user_comments[ncomments] : null;
	}

	private final int opus_tags_get_gain( final int[] _gain_q8, final byte[] _tag_name, final int _tag_len ) {
		final byte[][] tag_user_comments = this.user_comments;
		final int ncomments = this.comments;
		/*Look for the first valid tag with the name _tag_name and use that.*/
		for( int ci = 0; ci < ncomments; ci++ ) {
			// OP_ASSERT(_tag_len<=(size_t)INT_MAX);
			if( opus_tagncompare( _tag_name, _tag_len, tag_user_comments[ci] ) == 0 ) {
				final byte[] comment = tag_user_comments[ci];// java
				int p = _tag_len + 1;// java comment[ p ]. comments[ci] + _tag_len + 1;
				int negative = 0;
				if( (int)comment[ p ] == '-' ) {
					negative = -1;
					p++;
				}
				else if( (int)comment[ p ] == '+' ) {
					p++;
				}
				int gain_q8 = 0;
				while( (int)comment[ p ] >= '0' && (int)comment[ p ] <= '9' ) {
					gain_q8 = 10 * gain_q8 + (int)comment[ p ] - '0';
					if( gain_q8 > 32767 - negative ) {
						break;
					}
					p++;
				}
				/*This didn't look like a signed 16 - bit decimal integer.
				Not a valid gain tag.*/
				if( (int)comment[ p ] != '\0' ) {
					continue;
				}
				_gain_q8[0] = ( gain_q8 + negative ^ negative );
				return 0;
			}
		}
		return JOggOpusFile.OP_FALSE;
	}

	/** Get the album gain from an R128_ALBUM_GAIN tag, if one was specified.
	   This searches for the first R128_ALBUM_GAIN tag with a valid signed,
	    16-bit decimal integer value and returns the value.
	   This routine is exposed merely for convenience for applications which wish
	    to do something special with the album gain (i.e., display it).
	   If you simply wish to apply the album gain instead of the header gain, you
	    can use op_set_gain_offset() with an #OP_ALBUM_GAIN type and no offset.
	   @param      _tags    An initialized #OpusTags structure.
	   @param[out] _gain_q8 The album gain, in 1/256ths of a dB.
	                        This will lie in the range [-32768,32767], and should
	                         be applied in <em>addition</em> to the header gain.
	                        On error, no value is returned, and the previous
	                         contents remain unchanged.
	   @return 0 on success, or a negative value on error.
	   @retval #OP_FALSE There was no album gain available in the given tags. */
	final int opus_tags_get_album_gain( final int[] _gain_q8 ) {
		return opus_tags_get_gain( _gain_q8, "R128_ALBUM_GAIN".getBytes(), 15 );
	}

	/** Get the track gain from an R128_TRACK_GAIN tag, if one was specified.
	   This searches for the first R128_TRACK_GAIN tag with a valid signed,
	    16-bit decimal integer value and returns the value.
	   This routine is exposed merely for convenience for applications which wish
	    to do something special with the track gain (i.e., display it).
	   If you simply wish to apply the track gain instead of the header gain, you
	    can use op_set_gain_offset() with an #OP_TRACK_GAIN type and no offset.
	   @param      _tags    An initialized #OpusTags structure.
	   @param[out] _gain_q8 The track gain, in 1/256ths of a dB.
	                        This will lie in the range [-32768,32767], and should
	                         be applied in <em>addition</em> to the header gain.
	                        On error, no value is returned, and the previous
	                         contents remain unchanged.
	   @return 0 on success, or a negative value on error.
	   @retval #OP_FALSE There was no track gain available in the given tags. */
	final int opus_tags_get_track_gain( final int[] _gain_q8 ) {
		return opus_tags_get_gain( _gain_q8, "R128_TRACK_GAIN".getBytes(), 15 );
	}
}
