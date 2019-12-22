package opusfile;

import java.util.Arrays;

/** The contents of a METADATA_BLOCK_PICTURE tag. */
public final class JOpusPictureTag extends Jinfo {
	/** java helper class */
	private static final class Jpic_data {
		private int width = 0;
		private int height = 0;
		private int depth = 0;
		private int colors = 0;
		private int has_palette = -1;
	}
	/** The MIME type was not recognized, or the image data did not match the
    declared MIME type. */
	public static final int OP_PIC_FORMAT_UNKNOWN = -1;
	/** The MIME type indicates the image data is really a URL. */
	public static final int OP_PIC_FORMAT_URL     = 0;
	/** The image is a JPEG. */
	public static final int OP_PIC_FORMAT_JPEG    = 1;
	/** The image is a PNG. */
	public static final int OP_PIC_FORMAT_PNG     = 2;
	/** The image is a GIF. */
	public static final int OP_PIC_FORMAT_GIF     = 3;
	/** The picture type according to the ID3v2 APIC frame:
	<ol start="0">
	  <li>Other</li>
	  <li>32x32 pixels 'file icon' (PNG only)</li>
	  <li>Other file icon</li>
	  <li>Cover (front)</li>
	  <li>Cover (back)</li>
	  <li>Leaflet page</li>
	  <li>Media (e.g. label side of CD)</li>
	  <li>Lead artist/lead performer/soloist</li>
	  <li>Artist/performer</li>
	  <li>Conductor</li>
	  <li>Band/Orchestra</li>
	  <li>Composer</li>
	  <li>Lyricist/text writer</li>
	  <li>Recording Location</li>
	  <li>During recording</li>
	  <li>During performance</li>
	  <li>Movie/video screen capture</li>
	  <li>A bright colored fish</li>
	  <li>Illustration</li>
	  <li>Band/artist logotype</li>
	  <li>Publisher/Studio logotype</li>
	</ol>
	Others are reserved and should not be used.
	There may only be one each of picture type 1 and 2 in a file. */
	public int     type;
	/** The MIME type of the picture, in printable ASCII characters 0x20-0x7E.
	The MIME type may also be <code>"-->"</code> to signify that the data part
	is a URL pointing to the picture instead of the picture data itself.
	In this case, a terminating NUL is appended to the URL string in #data,
	but #data_length is set to the length of the string excluding that
	terminating NUL. */
	public String         mime_type;
	/** The description of the picture, in UTF-8. */
	public byte[]         description;
	/** The width of the picture in pixels. */
	public int    width;
	/** The height of the picture in pixels. */
	public int    height;
	/** The color depth of the picture in bits-per-pixel (<em>not</em>
	bits-per-channel). */
	public int    depth;
	/** For indexed-color pictures (e.g., GIF), the number of colors used, or 0
	for non-indexed pictures. */
	public int    colors;
	/** The length of the picture data in bytes. */
	public int    data_length;
	/** The binary picture data. */
	public byte[] data;
	/** The format of the picture data, if known.
	One of
	<ul>
	 <li>#OP_PIC_FORMAT_UNKNOWN,</li>
	 <li>#OP_PIC_FORMAT_URL,</li>
	 <li>#OP_PIC_FORMAT_JPEG,</li>
	 <li>#OP_PIC_FORMAT_PNG, or</li>
	 <li>#OP_PIC_FORMAT_GIF.</li>
	</ul> */
	public int            format;
	//
	final void clear() {
		type = 0;
		mime_type = null;
		description = null;
		width = 0;
		height = 0;
		depth = 0;
		colors = 0;
		data_length = 0;
		data = null;
	}

	final void copyFrom(final JOpusPictureTag t) {
		type = t.type;
		mime_type = t.mime_type;
		description = t.description;
		width = t.width;
		height = t.height;
		depth = t.depth;
		colors = t.colors;
		data_length = t.data_length;
		data = t.data;
	}

	/** Initializes an #OpusPictureTag structure.
	   This should be called on a freshly allocated #OpusPictureTag structure
	    before attempting to use it.
	   @param _pic The #OpusPictureTag structure to initialize. */
	private final void opus_picture_tag_init( ) {
		clear();
	}

	/** Clears the #OpusPictureTag structure.
	   This should be called on an #OpusPictureTag structure after it is no longer
	    needed.
	   It will free all memory used by the structure members.
	   @param _pic The #OpusPictureTag structure to clear. */
	public final void opus_picture_tag_clear() {
		this.description = null;
		this.mime_type = null;
		this.data = null;
	}

	private static final byte[] JPEG = new byte[]{ (byte)0xFF, (byte)0xD8, (byte)0xFF };

	private static final boolean op_is_jpeg( final byte[] _buf, final int boffset, final int _buf_sz ) {
		return _buf_sz >= 3 && memcmp( _buf, boffset, JPEG, 3 ) == 0;
	}

	/** Tries to extract the width, height, bits per pixel, and palette size of a JPEG.
	On failure, simply leaves its outputs unmodified. */
	private static final void op_extract_jpeg_params( final byte[] _buf, int boffset, final int _buf_sz, final Jpic_data data)// java
		// opus_uint32 *_width, opus_uint32 *_height,
		// opus_uint32 *_depth, opus_uint32 *_colors, int* _has_palette )
	{
		if( op_is_jpeg( _buf, boffset, _buf_sz ) ) {
			boffset += 2;// java renamed
			for( ; ; ) {
				while( boffset < _buf_sz && (int)_buf[boffset] != -1 ) {// != 0xFF ) {// signed 0xff = -1
					boffset++;
				}
				while( boffset < _buf_sz && (int)_buf[boffset] == -1 ) {// == 0xFF ) {// signed 0xff = -1
					boffset++;
				}
				final int marker = (int)_buf[boffset] & 0xff;
				boffset++;
				/*If we hit EOI* ( end of image ), or another SOI* ( start of image ),
				or SOS ( start of scan ), then stop now.*/
				if( boffset >= _buf_sz || ( marker >= 0xD8 && marker <= 0xDA ) ) {
					break;
				} else if( marker >= 0xD0 && marker <= 0xD7 ) {
					continue;
				}
				/*Read the length of the marker segment.*/
				if( _buf_sz - boffset < 2 ) {
					break;
				}
				final int segment_len = ((int)_buf[boffset] & 0xff) << 8 | ((int)_buf[boffset + 1] & 0xff);
				if( segment_len < 2 || _buf_sz - boffset < segment_len ) {
					break;
				}
				if( marker == 0xC0 || ( marker > 0xC0 && marker < 0xD0 && ( marker & 3 ) != 0 ) ) {
					/*Found a SOFn ( start of frame ) marker segment:*/
					if( segment_len >= 8 ) {
						data.height = ((int)_buf[boffset + 3] & 0xff) << 8 | ((int)_buf[boffset + 4] & 0xff);
						data.width = ((int)_buf[boffset + 5] & 0xff) << 8 | ((int)_buf[boffset + 6] & 0xff);
						data.depth = ((int)_buf[boffset + 2] & 0xff) * ((int)_buf[boffset + 7] & 0xff);
						data.colors = 0;
						data.has_palette = 0;
					}
					break;
				}
				/*Other markers: skip the whole marker segment.*/
				boffset += segment_len;
			}
		}
	}

	private static final byte[] PNG = new byte[]{(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A};

	private static final boolean op_is_png( final byte[] _buf, final int boffset, final int _buf_sz ) {
		// return _buf_sz >= 8 && memcmp( _buf, "\x89PNG\x0D\x0A\x1A\x0A", 8 ) == 0;
		return _buf_sz >= 8 && memcmp( _buf, boffset, PNG, 8 ) == 0;
	}

	private static final byte[] IHDR = "IHDR".getBytes();
	private static final byte[] PLTE = "PLTE".getBytes();

	/** Tries to extract the width, height, bits per pixel, and palette size of a PNG.
	On failure, simply leaves its outputs unmodified. */
	private static final void op_extract_png_params( final byte[] _buf, int boffset, final int _buf_sz, final Jpic_data data)// java
		// opus_uint32 *_width, opus_uint32 *_height,
		// opus_uint32 *_depth, opus_uint32 *_colors, int *_has_palette )
	{
		if( op_is_png( _buf, boffset, _buf_sz ) ) {
			boffset += 8;// java renamed
			while( _buf_sz - boffset >= 12 ) {
				final int chunk_len = op_parse_uint32be( _buf, boffset );
				if( chunk_len > _buf_sz - ( boffset + 12 ) ) {
					break;
				} else if( chunk_len == 13 && memcmp( _buf, boffset + 4, IHDR, 4 ) == 0 ) {
					data.width = op_parse_uint32be( _buf, boffset + 8 );
					data.height = op_parse_uint32be( _buf, boffset + 12 );
					final int color_type = (int)_buf[boffset + 17] & 0xff;
					if( color_type == 3 ) {
						data.depth = 24;
						data.has_palette = 1;
					}
					else {
						final int sample_depth = (int)_buf[boffset + 16] & 0xff;
						if( color_type == 0 ) {
							data.depth = sample_depth;
						} else if( color_type == 2 ) {
							data.depth = sample_depth * 3;
						} else if( color_type == 4 ) {
							data.depth = sample_depth << 1;
						} else if( color_type == 6 ) {
							data.depth = sample_depth << 2;
						}
						data.colors = 0;
						data.has_palette = 0;
						break;
					}
				}
				else if( data.has_palette > 0 && memcmp( _buf, boffset + 4, PLTE, 4 ) == 0 ) {
					data.colors = chunk_len / 3;
					break;
				}
				boffset += 12 + chunk_len;
			}
		}
	}

	private static final byte[] GIF87a = "GIF87a".getBytes();
	private static final byte[] GIF89a = "GIF89a".getBytes();

	private static final boolean op_is_gif( final byte[] _buf, final int boffset, final int _buf_sz ) {
		// return _buf_sz >= 6 && ( memcmp( _buf, "GIF87a", 6 ) == 0 || memcmp( _buf, "GIF89a", 6 ) == 0 );
		return _buf_sz >= 6 && ( memcmp( _buf, boffset, GIF87a, 6 ) == 0 || memcmp( _buf, boffset, GIF89a, 6 ) == 0 );
	}

	/** Tries to extract the width, height, bits per pixel, and palette size of a GIF.
	On failure, simply leaves its outputs unmodified. */
	private static final void op_extract_gif_params( final byte[] _buf, int boffset, final int _buf_sz, final Jpic_data data)// java
		// opus_uint32 *_width, opus_uint32 *_height,
		// opus_uint32 *_depth, opus_uint32 *_colors, int *_has_palette )
	{
		if( op_is_gif( _buf, boffset, _buf_sz ) && _buf_sz >= 14 ) {
			boffset += 6;// java
			int v = ((int)_buf[boffset++] & 0xff);// java +6
			v |= ((int)_buf[boffset++] & 0xff) << 8;// +7
			data.width = v;
			v = ((int)_buf[boffset++] & 0xff);// java +8
			v |= ((int)_buf[boffset++] & 0xff) << 8;// +9
			data.height = v;
			/*libFLAC hard - codes the depth to 24.*/
			data.depth = 24;
			data.colors = 1 << (((int)_buf[boffset] & 7) + 1);// +10
			data.has_palette = 1;
		}
	}

	/** The actual implementation of opus_picture_tag_parse().
	Unlike the public API, this function requires _pic to already be
	initialized, modifies its contents before success is guaranteed, and assumes
	the caller will clear it on error. */
	private final int opus_picture_tag_parse_impl(
			final byte[] _tag, final int toffset,// java
			byte[] _buf, int _buf_sz, final int _base64_sz)
	{
		/*Decode the BASE64 data.*/
		for( int i = 0; i < _base64_sz; i++ ) {
			int value = 0;
			for( int j = 0; j < 4; j++ ) {
				int d;
				final int c = (int)_tag[toffset + (i << 2) + j];// & 0xff don't need
				if( c == '+' ) {
					d = 62;
				} else if( c == '/' ) {
					d = 63;
				} else if( c >= '0' && c <= '9' ) {
					d = 52 + c - '0';
				} else if( c >= 'a' && c <= 'z' ) {
					d = 26 + c - 'a';
				} else if( c >= 'A' && c <= 'Z' ) {
					d = c - 'A';
				} else if( c == '=' && 3 * i + j > _buf_sz ) {
					d = 0;
				} else {
					return JOggOpusFile.OP_ENOTFORMAT;
				}
				value = value << 6 | d;
			}
			_buf[3 * i] = (byte)( value >>> 16 );
			if( 3 * i + 1 < _buf_sz ) {
				_buf[3 * i + 1] = (byte)( value >>> 8 );
				if( 3 * i + 2 < _buf_sz ) {
					_buf[3 * i + 2] = (byte)value;
				}
			}
		}
		int i = 0;
		final int picture_type = op_parse_uint32be( _buf, i );
		i += 4;
		/*Extract the MIME type.*/
		final int mime_type_length = op_parse_uint32be( _buf, i );
		i += 4;
		if( mime_type_length > _buf_sz - 32 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		// final byte[] mime_type = new byte[ mime_type_length + 1 ];
		// if( mime_type == null ) return JOggOpusFile.OP_EFAULT;
		// System.arraycopy( _buf, i, mime_type, 0, mime_type_length );
		// mime_type[mime_type_length] = '\0';
		// _pic.mime_type = mime_type;
		this.mime_type = new String( _buf, i, mime_type_length );
		i += mime_type_length;
		/*Extract the description string.*/
		final int description_length = op_parse_uint32be( _buf, i );
		i += 4;
		if( description_length > _buf_sz - mime_type_length - 32 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		final byte[] pic_description = new byte[ description_length + 1 ];
		// if( description == null ) return JOggOpusFile.OP_EFAULT;
		System.arraycopy( _buf, i, pic_description, 0, description_length );
		pic_description[description_length] = '\0';
		this.description = pic_description;
		i += description_length;
		/*Extract the remaining fields.*/
		int pic_width = op_parse_uint32be( _buf, i );
		i += 4;
		int pic_height = op_parse_uint32be( _buf, i );
		i += 4;
		int pic_depth = op_parse_uint32be( _buf, i );
		i += 4;
		int pic_colors = op_parse_uint32be( _buf, i );
		i += 4;
		/*If one of these is set, they all must be, but colors == 0 is a valid value.*/
		final boolean colors_set = pic_width != 0 || pic_height != 0 || pic_depth != 0 || pic_colors != 0;
		if( ( pic_width == 0 || pic_height == 0 || pic_depth == 0 ) && colors_set ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		final int pic_data_length = op_parse_uint32be( _buf, i );
		i += 4;
		if( pic_data_length > _buf_sz - i ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		/*Trim extraneous data so we don't copy it below.*/
		_buf_sz = i + pic_data_length;
		/*Attempt to determine the image format.*/
		int pic_format = OP_PIC_FORMAT_UNKNOWN;
		// if( mime_type_length == 3 && strcmp( mime_type, "-->" ) == 0 ) {
		if( mime_type_length == 3 && mime_type.compareTo("-->") == 0 ) {
			pic_format = OP_PIC_FORMAT_URL;
			/*Picture type 1 must be a 32x32 PNG.*/
			if( picture_type == 1 && ( pic_width != 0 || pic_height != 0 ) && ( pic_width != 32 || pic_height != 32 ) ) {
				return JOggOpusFile.OP_ENOTFORMAT;
			}
			/*Append a terminating NUL for the convenience of our callers.*/
			_buf[_buf_sz++] = '\0';
		}
		else {
			// if( mime_type_length == 10 && op_strncasecmp( mime_type, "image/jpeg", mime_type_length ) == 0 ) {
			if( mime_type_length == 10 && mime_type.compareToIgnoreCase("image/jpeg") == 0 ) {
				if( op_is_jpeg( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_JPEG;
				}
			}
			// else if( mime_type_length == 9 && op_strncasecmp( mime_type, "image/png", mime_type_length ) == 0 ) {
			else if( mime_type_length == 9 && mime_type.compareToIgnoreCase("image/png") == 0 ) {
				if( op_is_png( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_PNG;
				}
			}
			// else if( mime_type_length == 9 && op_strncasecmp( mime_type, "image/gif", mime_type_length ) == 0 ) {
			else if( mime_type_length == 9 && mime_type.compareToIgnoreCase("image/gif") == 0 ) {
				if( op_is_gif( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_GIF;
				}
			}
			else if( mime_type_length == 0 || ( mime_type_length == 6
					// && op_strncasecmp( mime_type, "image/", mime_type_length ) == 0 ) ) {
					&& mime_type.compareToIgnoreCase("image/") == 0 ) ) {
				if( op_is_jpeg( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_JPEG;
				} else if( op_is_png( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_PNG;
				} else if( op_is_gif( _buf, i, pic_data_length ) ) {
					pic_format = OP_PIC_FORMAT_GIF;
				}
			}
			// file_width = file_height = file_depth = file_colors = 0;
			final Jpic_data pic_data = new Jpic_data();// java helper
			// final int has_palette = -1;
			switch( pic_format ) {
			case OP_PIC_FORMAT_JPEG: {
					op_extract_jpeg_params( _buf, i, pic_data_length, pic_data );
							// &file_width, &file_height, &file_depth, &file_colors, &has_palette );
				} break;
			case OP_PIC_FORMAT_PNG: {
					op_extract_png_params( _buf, i, pic_data_length, pic_data );
							// &file_width, &file_height, &file_depth, &file_colors, &has_palette );
				} break;
			case OP_PIC_FORMAT_GIF: {
					op_extract_gif_params( _buf, i, pic_data_length, pic_data );
							// &file_width, &file_height, &file_depth, &file_colors, &has_palette );
				} break;
			}
			if( pic_data.has_palette >= 0 ) {
				/*If we successfully extracted these parameters from the image, override
				any declared values.*/
				pic_width = pic_data.width;
				pic_height = pic_data.height;
				pic_depth = pic_data.depth;
				pic_colors = pic_data.colors;
			}
			/*Picture type 1 must be a 32x32 PNG.*/
			if( picture_type == 1 && ( pic_format != OP_PIC_FORMAT_PNG || pic_width != 32 || pic_height != 32 ) ) {
				return JOggOpusFile.OP_ENOTFORMAT;
			}
		}
		/*Adjust _buf_sz instead of using data_length to capture the terminating NUL
		for URLs.*/
		_buf_sz -= i;
		System.arraycopy( _buf, i, _buf, 0, _buf_sz );
		_buf = Arrays.copyOf( _buf, _buf_sz );
		if( _buf_sz > 0 && _buf == null ) {
			return JOggOpusFile.OP_EFAULT;
		}
		this.type = picture_type;
		this.width = pic_width;
		this.height = pic_height;
		this.depth = pic_depth;
		this.colors = pic_colors;
		this.data_length = pic_data_length;
		this.data = _buf;
		this.format = pic_format;
		return 0;
	}

	/** Parse a single METADATA_BLOCK_PICTURE tag.
	   This decodes the BASE64-encoded content of the tag and returns a structure
	    with the MIME type, description, image parameters (if known), and the
	    compressed image data.
	   If the MIME type indicates the presence of an image format we recognize
	    (JPEG, PNG, or GIF) and the actual image data contains the magic signature
	    associated with that format, then the OpusPictureTag::format field will be
	    set to the corresponding format.
	   This is provided as a convenience to avoid requiring applications to parse
	    the MIME type and/or do their own format detection for the commonly used
	    formats.
	   In this case, we also attempt to extract the image parameters directly from
	    the image data (overriding any that were present in the tag, which the
	    specification says applications are not meant to rely on).
	   The application must still provide its own support for actually decoding the
	    image data and, if applicable, retrieving that data from URLs.
	   @param[out] _pic Returns the parsed picture data.
	                    No sanitation is done on the type, MIME type, or
	                     description fields, so these might return invalid values.
	                    The contents of this structure are left unmodified on
	                     failure.
	   @param      _tag The METADATA_BLOCK_PICTURE tag contents.
	                    The leading "METADATA_BLOCK_PICTURE=" portion is optional,
	                     to allow the function to be used on either directly on the
	                     values in OpusTags::user_comments or on the return value
	                     of opus_tags_query().
	   @return 0 on success or a negative value on error.
	   @retval #OP_ENOTFORMAT The METADATA_BLOCK_PICTURE contents were not valid.
	   @retval #OP_EFAULT     There was not enough memory to store the picture tag
	                           contents. */
	public final int opus_picture_tag_parse(final byte[] _tag ) {
		int toffset = 0;// java
		if( JOpusTags.opus_tagncompare( "METADATA_BLOCK_PICTURE".getBytes(), 22, _tag ) == 0 ) {
			toffset += 23;
		}
		/*Figure out how much BASE64 - encoded data we have.*/
		int tag_length = strlen( _tag, toffset );
		if( 0 != (tag_length & 3) ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		final int base64_sz = tag_length >>> 2;
		int buf_sz = 3 * base64_sz;
		if( buf_sz < 32 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		tag_length += toffset;
		if( _tag[ tag_length - 1 ] == '=' ) {
			buf_sz--;
		}
		if( _tag[ tag_length - 2 ] == '=' ) {
			buf_sz--;
		}
		if( buf_sz < 32 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		/*Allocate an extra byte to allow appending a terminating NUL to URL data.*/
		byte[] buf = new byte[ buf_sz + 1 ];
		/* if( buf == null ) {
			return JOggOpusFile.OP_EFAULT;
		} */
		final JOpusPictureTag pic = new JOpusPictureTag();
		pic.opus_picture_tag_init();
		final int ret = pic.opus_picture_tag_parse_impl( _tag, toffset, buf, buf_sz, base64_sz );
		if( ret < 0 ) {
			opus_picture_tag_clear();
			buf = null;
		} else {
			copyFrom( pic );
		}
		return ret;
	}
}
