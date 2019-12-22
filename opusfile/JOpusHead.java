package opusfile;

/** Ogg Opus bitstream information.
This contains the basic playback parameters for a stream, and corresponds to
 the initial ID header packet of an Ogg Opus stream. */
public final class JOpusHead extends Jinfo {
	/** byte array "OpusHead" */
	private static final byte[] OpusHead = { 'O', 'p', 'u', 's', 'H', 'e', 'a', 'd' };

	private static final int op_parse_uint16le( final byte[] _data, int doffset ) {
		final int v0 = ((int)_data[doffset++] & 0xff);
		final int v1 = ((int)_data[doffset] & 0xff);
		return v0 | (v1 << 8);
	}

	private static final int op_parse_int16le( final byte[] _data, int doffset ) {
		int ret = ((int)_data[doffset++] & 0xff);
		final int v1 = ((int)_data[doffset] & 0xff);
		ret |= v1 << 8;
		return ( ret ^ 0x8000 ) - 0x8000;// FIXME may be short out?
	}
	/** The Ogg Opus format version, in the range 0...255.
	  The top 4 bits represent a "major" version, and the bottom four bits
	  represent backwards-compatible "minor" revisions.
	  The current specification describes version 1.
	  This library will recognize versions up through 15 as backwards compatible
	  with the current specification.
	  An earlier draft of the specification described a version 0, but the only
	  difference between version 1 and version 0 is that version 0 did
	  not specify the semantics for handling the version field. */
	public int        version;
	/** The number of channels, in the range 1...255. */
	public int channel_count;
	/** The number of samples that should be discarded from the beginning of the stream. */
	int      pre_skip;// FIXME uses as int
	/** The sampling rate of the original input.
	  All Opus audio is coded at 48 kHz, and should also be decoded at 48 kHz
	  for playback (unless the target hardware does not support this sampling
	  rate).
	  However, this field may be used to resample the audio back to the original
	  sampling rate, for example, when saving the output to a file. */
	public int input_sample_rate;
	/** The gain to apply to the decoded output, in dB, as a Q8 value in the range
	  -32768...32767.
	  The <tt>libopusfile</tt> API will automatically apply this gain to the
	  decoded output before returning it, scaling it by
	  <code>pow(10,output_gain/(20.0*256))</code>.
	  You can adjust this behavior with op_set_gain_offset().*/
	public int        output_gain;
	/** The channel mapping family, in the range 0...255.
	  Channel mapping family 0 covers mono or stereo in a single stream.
	  Channel mapping family 1 covers 1 to 8 channels in one or more streams,
	  using the Vorbis speaker assignments.
	  Channel mapping family 255 covers 1 to 255 channels in one or more
	  streams, but without any defined speaker assignment. */
	public int        mapping_family;
	/** The number of Opus streams in each Ogg packet, in the range 1...255. */
	int        stream_count;
	/** The number of coupled Opus streams in each Ogg packet, in the range
	  0...127.
	  This must satisfy <code>0 <= coupled_count <= stream_count</code> and
	  <code>coupled_count + stream_count <= 255</code>.
	  The coupled streams appear first, before all uncoupled streams, in an Ogg
	  Opus packet. */
	int        coupled_count;
	/** The mapping from coded stream channels to output channels.
	  Let <code>index=mapping[k]</code> be the value for channel <code>k</code>.
	  If <code>index<2*coupled_count</code>, then it refers to the left channel
	  from stream <code>(index/2)</code> if even, and the right channel from
	  stream <code>(index/2)</code> if odd.
	  Otherwise, it refers to the output of the uncoupled stream
	  <code>(index-coupled_count)</code>. */
	final char mapping[] = new char[ JOggOpusFile.OPUS_CHANNEL_COUNT_MAX ];
	//
	final void copyFrom(final JOpusHead h, final boolean isCopyMapping) {
		version = h.version;
		channel_count = h.channel_count;
		pre_skip = h.pre_skip;
		input_sample_rate = h.input_sample_rate;
		output_gain = h.output_gain;
		mapping_family = h.mapping_family;
		stream_count = h.stream_count;
		coupled_count = h.coupled_count;
		if( isCopyMapping ) {
			System.arraycopy( h.mapping, 0, mapping, 0, JOggOpusFile.OPUS_CHANNEL_COUNT_MAX );
		}
	}
	// start info.c
	/** Parses the contents of the ID header packet of an Ogg Opus stream.
	   @param _head [out] Returns the contents of the parsed packet.
	                     The contents of this structure are untouched on error.
	                     This may be <code>NULL</code> to merely test the header
	                      for validity.
	   @param _data [in] The contents of the ID header packet.
	   @param _len The number of bytes of data in the ID header packet.
	   @return 0 on success or a negative value on error.
	   @retval #OP_ENOTFORMAT If the data does not start with the "OpusHead"
	                           string.
	   @retval #OP_EVERSION   If the version field signaled a version this library
	                           does not know how to parse.
	   @retval #OP_EIMPL      If the channel mapping family was 255, which general
	                           purpose players should not attempt to play.
	   @retval #OP_EBADHEADER If the contents of the packet otherwise violate the
	                           Ogg Opus specification:
	                          <ul>
	                           <li>Insufficient data,</li>
	                           <li>Too much data for the known minor versions,</li>
	                           <li>An unrecognized channel mapping family,</li>
	                           <li>Zero channels or too many channels,</li>
	                           <li>Zero coded streams,</li>
	                           <li>Too many coupled streams, or</li>
	                           <li>An invalid channel mapping index.</li>
	                          </ul> */
	static final int opus_head_parse( final JOpusHead _head, final byte[] _data, final int doffset, final int _len ) {
		if( _len < 8 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		if( memcmp( _data, doffset, OpusHead, 8 ) != 0 ) {
			return JOggOpusFile.OP_ENOTFORMAT;
		}
		if( _len < 9 ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		final JOpusHead head = new JOpusHead();
		head.version = ((int)_data[doffset + 8] & 0xff);
		if( head.version > 15 ) {
			return JOggOpusFile.OP_EVERSION;
		}
		if( _len < 19 ) {
			return JOggOpusFile.OP_EBADHEADER;
		}
		head.channel_count = ((int)_data[doffset + 9] & 0xff);
		head.pre_skip = op_parse_uint16le( _data, doffset + 10 );
		head.input_sample_rate = op_parse_uint32le( _data, doffset + 12 );
		head.output_gain = op_parse_int16le( _data, doffset + 16 );
		head.mapping_family = ((int)_data[doffset + 18] & 0xff);
		if( head.mapping_family == 0 ) {
			if( head.channel_count < 1 || head.channel_count > 2 ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			if( head.version <= 1 && _len > 19 ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			head.stream_count = 1;
			head.coupled_count = head.channel_count - 1;
			if( _head != null ) {
				_head.mapping[0] = 0;
				_head.mapping[1] = 1;
			}
		}
		else if( head.mapping_family == 1 ) {
			if( head.channel_count < 1 || head.channel_count > 8 ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			final int size = 21 + head.channel_count;
			if( _len < size || head.version <= 1 && _len > size ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			head.stream_count = ((int)_data[doffset + 19] & 0xff);
			if( head.stream_count < 1 ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			head.coupled_count = ((int)_data[doffset + 20] & 0xff);
			if( head.coupled_count > head.stream_count ) {
				return JOggOpusFile.OP_EBADHEADER;
			}
			for( int ci = doffset + 21, ce = ci + head.channel_count; ci < ce; ci++ ) {
				if( ((int)_data[ci] & 0xff) >= head.stream_count + head.coupled_count
						// && ((int)_data[doffset + 21 + ci] & 0xff) != 255 ) {
						&& ((int)_data[ci]) != -1 ) {// java: signed 255 = -1
					return JOggOpusFile.OP_EBADHEADER;
				}
			}
			if( _head != null ) {
				System.arraycopy( _data, doffset + 21, _head.mapping, 0, head.channel_count );
			}
		}
		/*General purpose players should not attempt to play back content with
		channel mapping family 255.*/
		else if( head.mapping_family == 255 ) {
			return JOggOpusFile.OP_EIMPL;
		} else {
			return JOggOpusFile.OP_EBADHEADER;
		}
		if( _head != null ) {
			// memcpy( _head, &head, head.mapping - (unsigned char *) &head );// FIXME dirty way to partial copy
			_head.copyFrom( head, false );
		}
		return 0;
	}
	// end info.c
}
