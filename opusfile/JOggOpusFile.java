package opusfile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import libogg.Jogg_packet;
import libogg.Jogg_page;
import libogg.Jogg_stream_state;
import libogg.Jogg_sync_state;
import opus.JOpusDecoder;
import opus.JOpusMSDecoder;
import opus.Jopus;
import opus.Jopus_defines;

/** This implementation is largely based off of libvorbisfile.
All of the Ogg bits work roughly the same, though I have made some
 "improvements" that have not been folded back there, yet.

<p>A 'chained bitstream' is an Ogg Opus bitstream that contains more than one
 logical bitstream arranged end to end ( the only form of Ogg multiplexing
 supported by this library.
Grouping ( parallel multiplexing ) is not supported, except to the extent that
 if there are multiple logical Ogg streams in a single link of the chain, we
 will ignore all but the first Opus stream we find.

<p>An Ogg Opus file can be played beginning to end ( streamed ) without worrying
 ahead of time about chaining ( see opusdec from the opus-tools package ).
If we have the whole file, however, and want random access
 ( seeking/scrubbing ) or desire to know the total length/time of a file, we
 need to account for the possibility of chaining.

<p>We can handle things a number of ways.
We can determine the entire bitstream structure right off the bat, or find
 pieces on demand.
This library determines and caches structure for the entire bitstream, but
 builds a virtual decoder on the fly when moving between links in the chain.

<p>There are also different ways to implement seeking.
Enough information exists in an Ogg bitstream to seek to sample-granularity
 positions in the output.
Or, one can seek by picking some portion of the stream roughly in the desired
 area if we only want coarse navigation through the stream.
We implement and expose both strategies. */
public final class JOggOpusFile {
	/** Use shorts, they're smaller. */
	// static final boolean OP_FIXED_POINT = false;
	/** */
	static final boolean OP_SMALL_FOOTPRINT = false;

	 // internal.h
	/*We're using this define to test for libopus 1.1 or later until libopus
	   provides a better mechanism.*/
	// #  if defined(OPUS_GET_EXPERT_FRAME_DURATION_REQUEST)
	/** Enable soft clipping prevention in 16-bit decodes. */
	private static final boolean OP_SOFT_CLIP = Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION != 0;// Jopus_defines.OPUS_GET_EXPERT_FRAME_DURATION_REQUEST != 0;
	// #  endif
	/** The maximum channel count for any mapping we'll actually decode. */
	static final int OP_NCHANNELS_MAX = 8;

	/** Initial state. */
	private static final int OP_NOTOPEN   = 0;
	/** We've found the first Opus stream in the first link. */
	private static final int OP_PARTOPEN  = 1;
	private static final int OP_OPENED    = 2;
	/** We've found the first Opus stream in the current link. */
	private static final int OP_STREAMSET = 3;
	/** We've initialized the decoder for the chosen Opus stream in the current link. */
	private static final int OP_INITSET   = 4;

	private static final int OP_CLAMP(final int _lo, int _x, final int _hi) {
		_x = _x <= _hi ? _x : _hi;
		return _lo >= _x ? _lo : _x;
	}
	private static final float OP_CLAMP(final float _lo, float _x, final float _hi) {
		_x = _x <= _hi ? _x : _hi;
		return _lo >= _x ? _lo : _x;
	}

	/** Advance a file offset by the given amount, clamping against OP_INT64_MAX.
	  This is used to advance a known offset by things like OP_CHUNK_SIZE or
	   OP_PAGE_SIZE_MAX, while making sure to avoid signed overflow.
	  It assumes that both _offset and _amount are non-negative. */
	private static final long OP_ADV_OFFSET(final long _offset, final int _amount) {
		final long v = Long.MAX_VALUE - _amount;
		return ((_offset <= v ? _offset : v) + _amount);
	}

	// opusfile.h
	/**\defgroup error_codes Error Codes*/
	/*@{*/
	/**\name List of possible error codes
	   Many of the functions in this library return a negative error code when a
	    function fails.
	   This list provides a brief explanation of the common errors.
	   See each individual function for more details on what a specific error code
	    means in that context.*/
	/*@{*/

	/** A request did not succeed. */
	static final int OP_FALSE         = -1;
	/*Currently not used externally.*/
	private static final int OP_EOF           = -2;
	/** There was a hole in the page sequence numbers (e.g., a page was corrupt or
	    missing). */
	public static final int OP_HOLE          = -3;
	/** An underlying read, seek, or tell operation failed when it should have
	    succeeded. */
	private static final int OP_EREAD         = -128;
	/** A <code>NULL</code> pointer was passed where one was unexpected, or an
	    internal memory allocation failed, or an internal library error was
	    encountered. */
	static final int OP_EFAULT        = -129;
	/** The stream used a feature that is not implemented, such as an unsupported
	    channel family. */
	static final int OP_EIMPL         = -130;
	/** One or more parameters to a function were invalid. */
	static final int OP_EINVAL        = -131;
	/** A purported Ogg Opus stream did not begin with an Ogg page, a purported
	    header packet did not start with one of the required strings, "OpusHead" or
	    "OpusTags", or a link in a chained file was encountered that did not
	    contain any logical Opus streams.*/
	static final int OP_ENOTFORMAT    = -132;
	/** A required header packet was not properly formatted, contained illegal
	    values, or was missing altogether.*/
	static final int OP_EBADHEADER    = -133;
	/** The ID header contained an unrecognized version number. */
	static final int OP_EVERSION      = -134;
	/*Currently not used at all.*/
	@SuppressWarnings("unused")
	private static final int OP_ENOTAUDIO     = -135;
	/** An audio packet failed to decode properly.
	   This is usually caused by a multistream Ogg packet where the durations of
	    the individual Opus packets contained in it are not all the same. */
	private static final int OP_EBADPACKET    = -136;
	/** We failed to find data we had seen before, or the bitstream structure was
	    sufficiently malformed that seeking to the target destination was
	    impossible. */
	private static final int OP_EBADLINK      = -137;
	/** An operation that requires seeking was requested on an unseekable stream. */
	private static final int OP_ENOSEEK       = -138;
	/** The first or last granule position of a link failed basic validity checks. */
	private static final int OP_EBADTIMESTAMP = -139;

	/*@}*/
	/*@}*/

	/**\defgroup header_info Header Information*/
	/*@{*/

	/**The maximum number of channels in an Ogg Opus stream.*/
	public static final int OPUS_CHANNEL_COUNT_MAX = 255;

	/*@}*/

	// opusfile.c
	/** The maximum number of bytes in a page (including the page headers). */
	private static final int OP_PAGE_SIZE_MAX = 65307;
	/** The default amount to seek backwards per step when trying to find the
	   previous page.
	  This must be at least as large as the maximum size of a page. */
	private static final int OP_CHUNK_SIZE    = 65536;
	/** The maximum amount to seek backwards per step when trying to find the
	   previous page. */
	private static final int OP_CHUNK_SIZE_MAX = 1024 * 1024;
	/** A smaller read size is needed for low-rate streaming. */
	private static final int OP_READ_SIZE      = 2048;

	/** Gain offset type that indicates that the provided offset is relative to the
    header gain. This is the default. */
	public static final int OP_HEADER_GAIN   = 0;

	/** Gain offset type that indicates that the provided offset is relative to the
	    R128_ALBUM_GAIN value (if any), in addition to the header gain. */
	private static final int OP_ALBUM_GAIN    = 3007;

	/** Gain offset type that indicates that the provided offset is relative to the
	    R128_TRACK_GAIN value (if any), in addition to the header gain. */
	private static final int OP_TRACK_GAIN    = 3008;

	/** Gain offset type that indicates that the provided offset should be used as
	    the gain directly, without applying any the header or track gains. */
	private static final int OP_ABSOLUTE_GAIN = 3009;

	/** Indicates that the decoding callback should produce signed 16-bit
	    native-endian output samples. */
	public static final int OP_DEC_FORMAT_SHORT = 7008;// java: #if defined(OP_FIXED_POINT)
	/** Indicates that the decoding callback should produce 32-bit native-endian
	    float samples. */
	public static final int OP_DEC_FORMAT_FLOAT = 7040;

	/** Indicates that the decoding callback did not decode anything, and that
	    <tt>libopusfile</tt> should decode normally instead. */
	private static final int OP_DEC_USE_DEFAULT  = 6720;

	/** The callbacks used to access the stream. */
	private JOpusFileCallbacks callbacks;
	/** A FILE *, memory buffer, etc. */
	// Object             stream;// java don't need. this is JOpusFileCallbacks object
	/** Whether or not we can seek with this stream. */
	private boolean        seekable;
	/** The number of links in this chained Ogg Opus file. */
	private int            nlinks;
	/** The cached information from each link in a chained Ogg Opus file.
	If stream isn't seekable (e.g., it's a pipe), only the current link
	appears. */
	private JOggOpusLink[] links;
	/** The number of serial numbers from a single link. */
	private int   nserialnos;
	/** The capacity of the list of serial numbers from a single link. */
	private int   cserialnos;
	/** Storage for the list of serial numbers from a single link.
	  This is a scratch buffer used when scanning the BOS pages at the start of
	   each link.*/
	private int[] serialnos;
	/** This is the current offset of the data processed by the ogg_sync_state.
	After a seek, this should be set to the target offset so that we can track
	the byte offsets of subsequent pages.
	After a call to op_get_next_page(), this will point to the first byte after
	that page. */
	private long  offset;
	/** The total size of this stream, or -1 if it's unseekable. */
	private long  end;
	/** Used to locate pages in the stream. */
	private final Jogg_sync_state oy = new Jogg_sync_state();
	/** One of OP_NOTOPEN, OP_PARTOPEN, OP_OPENED, OP_STREAMSET, OP_INITSET. */
	private int   ready_state;
	/** The current link being played back. */
	private int   cur_link;
	/** The number of decoded samples to discard from the start of decoding. */
	private int   cur_discard_count;
	/** The granule position of the previous packet (current packet start time). */
	private long  prev_packet_gp;
	/** The stream offset of the most recent page with completed packets, or -1.
	This is only needed to recover continued packet data in the seeking logic,
	when we use the current position as one of our bounds, only to later
	discover it was the correct starting point. */
	private long  prev_page_offset;
	/** The number of bytes read since the last bitrate query, including framing. */
	private long  bytes_tracked;
	/** The number of samples decoded since the last bitrate query. */
	private long  samples_tracked;
	/** Takes physical pages and welds them into a logical stream of packets. */
	private final Jogg_stream_state os = new Jogg_stream_state();
	/** Re-timestamped packets from a single page.
	Buffering these relies on the undocumented libogg behavior that ogg_packet
	pointers remain valid until the next page is submitted to the
	ogg_stream_state they came from. */
	private final Jogg_packet op[] = new Jogg_packet[255];
	/** The index of the next packet to return. */
	private int   op_pos;
	/** The total number of packets available. */
	private int   op_count;
	/** Central working state for the packet-to-PCM decoder. */
	private JOpusMSDecoder od;
	/** The application-provided packet decode callback. */
	private Jop_decode_cb_func decode_cb;
	/** The application-provided packet decode callback context. */
	private Object decode_cb_ctx;
	/** The stream count used to initialize the decoder. */
	private int   od_stream_count;
	/** The coupled stream count used to initialize the decoder. */
	private int   od_coupled_count;
	/** The channel count used to initialize the decoder. */
	private int   od_channel_count;
	/** The channel mapping used to initialize the decoder. */
	private final char od_mapping[] = new char[ OP_NCHANNELS_MAX ];
	/** The buffered data for one decoded packet. */
	private float[] od_buffer;
	/** The current position in the decoded buffer. */
	private int   od_buffer_pos;
	/** The number of valid samples in the decoded buffer. */
	private int   od_buffer_size;
	/** The type of gain offset to apply.
	One of OP_HEADER_GAIN, OP_ALBUM_GAIN, OP_TRACK_GAIN, or OP_ABSOLUTE_GAIN. */
	private int   gain_type;
	/** The offset to apply to the gain. */
	private int   gain_offset_q8;
	/** Internal state for soft clipping and dithering float->short output. */
//#if !defined(OP_FIXED_POINT)
//# if defined(OP_SOFT_CLIP)
	private final float[] clip_state = new float[ OP_NCHANNELS_MAX ];
//# endif
	private final float[] dither_a = new float[ OP_NCHANNELS_MAX * 4 ];
	private final float[] dither_b = new float[ OP_NCHANNELS_MAX * 4 ];
	private int           dither_seed;
	private int           dither_mute;
	private boolean       dither_disabled;
	/** The number of channels represented by the internal state.
	This gets set to 0 whenever anything that would prevent state propagation
	occurs (switching between the float/short APIs, or between the
	stereo/multistream APIs). */
	private int           state_channel_count;
//#endif
	private static final class Jserialno_aux {// TODO it is possible to replace with JOggOpusFile pointer?
		/** The number of serial numbers from a single link. */
		private int   nserialnos = 0;
		/** The capacity of the list of serial numbers from a single link. */
		private int   cserialnos = 0;
		/** Storage for the list of serial numbers from a single link. */
		private int[] serialnos = null;
		//
		private Jserialno_aux() {
		}
		private Jserialno_aux(final int[] iserialnos, final int inserialnos, final int icserialnos) {
			serialnos = iserialnos;
			nserialnos = inserialnos;
			cserialnos = icserialnos;
		}
	}
	//
	JOggOpusFile() {
		int i = 255;
		do {
			op[--i] = new Jogg_packet();
		} while( i > 0);
	}
	final void clear() {
		callbacks = null;
		// source = null;
		seekable = false;
		nlinks = 0;
		links = null;
		nserialnos = 0;
		cserialnos = 0;
		serialnos = null;
		offset = 0;
		end = 0;
		oy.clear();
		ready_state = OP_NOTOPEN;//0;// java uses OP_NOTOPEN to avoid get warning about unusing
		cur_link = 0;
		cur_discard_count = 0;
		prev_packet_gp = 0;
		prev_page_offset = 0;
		bytes_tracked = 0;
		samples_tracked = 0;
		os.clear();
		//
		final Jogg_packet[] pb = op;
		int i = 255;
		do {
			pb[--i].clear();
		} while( i > 0 );
		//
		op_pos = 0;
		op_count = 0;
		od = null;
		decode_cb = null;
		decode_cb_ctx = null;
		od_stream_count = 0;
		od_coupled_count = 0;
		od_channel_count = 0;
		//
		final char[] cbuff = od_mapping;
		i = OP_NCHANNELS_MAX;
		do {
			cbuff[--i] = 0;
		} while( i > 0 );
		//
		od_buffer = null;
		od_buffer_pos = 0;
		od_buffer_size = 0;
		gain_type = 0;
		gain_offset_q8 = 0;
		/** Internal state for soft clipping and dithering float->short output. */
	//#if !defined(OP_FIXED_POINT)
	//# if defined(OP_SOFT_CLIP)
		float[] fbuff = clip_state;
		i = OP_NCHANNELS_MAX;
		do {
			fbuff[--i] = 0;
		} while( i > 0 );
	//# endif
		fbuff = dither_a;
		i = OP_NCHANNELS_MAX * 4;
		do {
			fbuff[--i] = 0;
		} while( i > 0 );
		//
		fbuff = dither_b;
		i = OP_NCHANNELS_MAX * 4;
		do {
			fbuff[--i] = 0;
		} while( i > 0 );
		//
		dither_seed = 0;
		dither_mute = 0;
		dither_disabled = false;
		state_channel_count = 0;
	}
	/** Test to see if this is an Opus stream.
	   For good results, you will need at least 57 bytes (for a pure Opus-only
	    stream).
	   Something like 512 bytes will give more reliable results for multiplexed
	    streams.
	   This function is meant to be a quick-rejection filter.
	   Its purpose is not to guarantee that a stream is a valid Opus stream, but to
	    ensure that it looks enough like Opus that it isn't going to be recognized
	    as some other format (except possibly an Opus stream that is also
	    multiplexed with other codecs, such as video).
	   @param[out] _head     The parsed ID header contents.
	                         You may pass <code>NULL</code> if you do not need
	                          this information.
	                         If the function fails, the contents of this structure
	                          remain untouched.
	   @param _initial_data  An initial buffer of data from the start of the
	                          stream.
	   @param _initial_bytes The number of bytes in \a _initial_data.
	   @return 0 if the data appears to be Opus, or a negative value on error.
	   @retval #OP_FALSE      There was not enough data to tell if this was an Opus
	                           stream or not.
	   @retval #OP_EFAULT     An internal memory allocation failed.
	   @retval #OP_EIMPL      The stream used a feature that is not implemented,
	                           such as an unsupported channel family.
	   @retval #OP_ENOTFORMAT If the data did not contain a recognizable ID
	                           header for an Opus stream.
	   @retval #OP_EVERSION   If the version field signaled a version this library
	                           does not know how to parse.
	   @retval #OP_EBADHEADER The ID header was not properly formatted or contained
	                           illegal values. */
	public static final int op_test( final JOpusHead _head, final byte[] _initial_data, final int _initial_bytes ) {
		/*The first page of a normal Opus file will be at most 57 bytes (27 Ogg
		page header bytes+1 lacing value+21 Opus header bytes+8 channel
		mapping bytes).
		It will be at least 47 bytes (27 Ogg page header bytes+1 lacing value+
		19 Opus header bytes using channel mapping family 0).
		If we don't have at least that much data, give up now.*/
		if( _initial_bytes < 47 ) {
			return OP_FALSE;
		}
		/*Only proceed if we start with the magic OggS string.
		This is to prevent us spending a lot of time allocating memory and looking
		for Ogg pages in non-Ogg files.*/
		if( _initial_data[0] != 'O' || _initial_data[1] != 'g' || _initial_data[2] != 'g' || _initial_data[3] != 'S' ) {// if( memcmp( _initial_data, "OggS", 4 ) != 0 ) {
			return OP_ENOTFORMAT;
		}
		if( _initial_bytes < 0 )
		 {
			return OP_EFAULT;// java if( _initial_bytes > LONG_MAX )return OP_EFAULT;
		}
		final Jogg_sync_state oy = new Jogg_sync_state();
		oy.ogg_sync_init();
		int err;
		final byte[] data = oy.data;// oy.ogg_sync_buffer( _initial_bytes );// java changed
		if( data != null ) {
			final Jogg_stream_state os = new Jogg_stream_state();
			final Jogg_page         og = new Jogg_page();
			System.arraycopy( _initial_data, 0, data, oy.ogg_sync_buffer( _initial_bytes ), _initial_bytes );
			oy.ogg_sync_wrote( _initial_bytes );
			os.ogg_stream_init( -1 );
			err = OP_FALSE;
			final Jogg_packet op = new Jogg_packet();// java
			do {
				// final Jogg_packet op = new Jogg_packet();// java moved up
				int ret = oy.ogg_sync_pageout( og );
				/*Ignore holes.*/
				if( ret < 0 ) {
					continue;
				}
				/*Stop if we run out of data.*/
				if( 0 == ret ) {
					break;
				}
				os.ogg_stream_reset_serialno( og.ogg_page_serialno() );
				os.ogg_stream_pagein( og );
				/*Only process the first packet on this page ( if it's a BOS packet,
				it's required to be the only one ).*/
				if( os.ogg_stream_packetout( op ) == 1 ) {
					if( op.b_o_s ) {
						ret = JOpusHead.opus_head_parse( _head, op.packet_base, op.packet, op.bytes );
						/*If this didn't look like Opus, keep going.*/
						if( ret == OP_ENOTFORMAT ) {
							continue;
						}
						/*Otherwise we're done, one way or another.*/
						err = ret;
					} else {
						err = OP_ENOTFORMAT;
					}
				}
			}
			while( err == OP_FALSE );
			os.ogg_stream_clear();
		} else {
			err = OP_EFAULT;
		}
		oy.ogg_sync_clear();
		return err;
	}

	/* Many, many internal helpers.
	The intention is not to be confusing.
	Rampant duplication and monolithic function implementation ( though we do have
	some large, omnibus functions still ) would be harder to understand anyway.
	The high level functions are last.
	Begin grokking near the end of the file if you prefer to read things
	top-down. */

	/* The read/seek functions track absolute position within the stream. */

	/** Read a little more data from the file/pipe into the ogg_sync framer.
	_nbytes: The maximum number of bytes to read.
	Return: A positive number of bytes read on success, 0 on end-of-file, or a
	negative value on failure. */
	private final int op_get_data( final int _nbytes ) throws IOException {
		// OP_ASSERT( _nbytes > 0 );
		final int buffer = this.oy.ogg_sync_buffer( _nbytes );// java changed
		final int nbytes = this.callbacks.read( this.oy.data, buffer, _nbytes );
		// OP_ASSERT( nbytes <= _nbytes );
		if( nbytes > 0 ) {
			this.oy.ogg_sync_wrote( nbytes );
		}
		return nbytes;
	}

	/** Save a tiny smidge of verbosity to make the code more readable. */
	private final int op_seek_helper( final long _offset ) {
		if( _offset == this.offset ) {
			return 0;
		}
		try {
			if( ! this.callbacks.mIsSeek ) {
				return OP_EREAD;
			}
			this.callbacks.seek( _offset, JOpusFileCallbacks.SEEK_SET );
		} catch(final IOException ie) {
			return OP_EREAD;
		}
		this.offset = _offset;
		this.oy.ogg_sync_reset();
		return 0;
	}

	/** Get the current position indicator of the underlying stream.
	This should be the same as the value reported by tell(). */
	private final long op_position() {
		/*The current position indicator is _not_ simply offset.
		We may also have unprocessed, buffered data in the sync state.*/
		return this.offset + this.oy.fill - this.oy.returned;
	}

	/** From the head of the stream, get the next page.
	_boundary specifies if the function is allowed to fetch more data from the
	stream (and how much) or only use internally buffered data.
	_boundary: -1: Unbounded search.
	0: Read no additional data.
	Use only cached data.
	n: Search for the start of a new page up to file position n.
	Return: n >= 0:     Found a page at absolute offset n.
	OP_FALSE:   Hit the _boundary limit.
	OP_EREAD:   An underlying read operation failed.
	OP_BADLINK: We hit end-of-file before reaching _boundary. */
	private final long op_get_next_page( final Jogg_page _og, final long _boundary ) {
		while( _boundary <= 0 || this.offset < _boundary ) {
			final int more = this.oy.ogg_sync_pageseek( _og );
			/*Skipped ( -more ) bytes.*/
			if( more < 0 ) {
				this.offset -= more;
			} else if( more == 0 ) {
				int read_nbytes;
				/*Send more paramedics.*/
				if( 0 == _boundary ) {
					return OP_FALSE;
				}
				if( _boundary < 0 ) {
					read_nbytes = OP_READ_SIZE;
				} else{
					final long position = op_position();
					if( position >= _boundary ) {
						return OP_FALSE;
					}
					read_nbytes = (int)(_boundary - position);
					read_nbytes = read_nbytes < OP_READ_SIZE ? read_nbytes : OP_READ_SIZE;
				}
				try {
					final int ret = op_get_data( read_nbytes );
					if( ret < 0 ) {
						return OP_EREAD;
					}
					if( ret == 0 ) {
						/*Only fail cleanly on EOF if we didn't have a known boundary.
						Otherwise, we should have been able to reach that boundary, and this
						is a fatal error.*/
						return _boundary < 0 ? OP_FALSE : OP_EBADLINK;
					}
				} catch(final IOException ie) {
					return OP_EREAD;
				}
			}
			else {
				/*Got a page.
				Return the page start offset and advance the internal offset past the
				page end.*/
				final long page_offset = this.offset;
				this.offset += more;
				// OP_ASSERT( page_offset >= 0 );
				return page_offset;
			}
		}
		return OP_FALSE;
	}

	private static final int op_add_serialno( final Jogg_page _og,
			// final int[][] _serialnos, final int[] _nserialnos, final int[] _cserialnos
			final Jserialno_aux aux// java helper
		)
	{
		final int s = _og.ogg_page_serialno();
		int[] serialnos = aux.serialnos;// _serialnos[0];
		int nserialnos = aux.nserialnos;// _nserialnos[0];
		int cserialnos = aux.cserialnos;// _cserialnos[0];
		if( nserialnos >= cserialnos ) {
			if( cserialnos > Integer.MAX_VALUE / (Integer.SIZE / 8)/* sizeof( *serialnos ) */ - 1 >> 1 ) {
				return OP_EFAULT;
			}
			cserialnos = (cserialnos << 1) + 1;
			// OP_ASSERT( nserialnos < cserialnos );
			serialnos = serialnos == null ? new int[ cserialnos ] : Arrays.copyOf( serialnos, cserialnos );// _ogg_realloc( serialnos, sizeof( *serialnos ) * cserialnos );
			if( serialnos == null ) {
				return OP_EFAULT;
			}
		}
		serialnos[nserialnos++] = s;
		aux.serialnos = serialnos;// _serialnos[0] = serialnos;
		aux.nserialnos = nserialnos;// _nserialnos[0] = nserialnos;
		aux.cserialnos = cserialnos;//_cserialnos[0] = cserialnos;
		return 0;
	}

	/** Returns nonzero if found. */
	private static final boolean op_lookup_serialno( final int _s, final int[] _serialnos, final int _nserialnos ) {
		int i;
		for( i = 0; i < _nserialnos && _serialnos[i] != _s; i++ ) {
			;
		}
		return i < _nserialnos;
	}

	private static final boolean op_lookup_page_serialno( final Jogg_page _og, final int[] _serialnos, final int _nserialnos ) {
		return op_lookup_serialno( _og.ogg_page_serialno(), _serialnos, _nserialnos );
	}

	/** We use this to remember the pages we found while enumerating the links of a
	chained stream.
	We keep track of the starting and ending offsets, as well as the point we
	started searching from, so we know where to bisect.
	We also keep the serial number, so we can tell if the page belonged to the
	current link or not, as well as the granule position, to aid in estimating
	the start of the link. */
	private static final class JOpusSeekRecord {
		/** The earliest byte we know of such that reading forward from it causes
		capture to be regained at this page. */
		private long search_start;
		/** The offset of this page. */
		private long offset;
		/** The size of this page. */
		private int  size;
		/** The serial number of this page. */
		private int  serialno;
		/** The granule position of this page. */
		private long gp;
		//
		private final void copyFrom(final JOpusSeekRecord sr) {
			search_start = sr.search_start;
			offset = sr.offset;
			size = sr.size;
			serialno = sr.serialno;
			gp = sr.gp;
		}
	};

	/** Find the last page beginning before _offset with a valid granule position.
	There is no '_boundary' parameter as it will always have to read more data.
	This is much dirtier than the above, as Ogg doesn't have any backward search
	linkage.
	This search prefers pages of the specified serial number.
	If a page of the specified serial number is spotted during the
	seek-back-and-read-forward, it will return the info of last page of the
	matching serial number, instead of the very last page, unless the very last
	page belongs to a different link than preferred serial number.
	If no page of the specified serial number is seen, it will return the info of
	the last page.
	[out] _sr:   Returns information about the page that was found on success.
	_offset:     The _offset before which to find a page.
	Any page returned will consist of data entirely before _offset.
	_serialno:   The preferred serial number.
	If a page with this serial number is found, it will be returned
	even if another page in the same link is found closer to
	_offset.
	This is purely opportunistic: there is no guarantee such a page
	will be found if it exists.
	_serialnos:  The list of serial numbers in the link that contains the
	preferred serial number.
	_nserialnos: The number of serial numbers in the current link.
	Return: 0 on success, or a negative value on failure.
	OP_EREAD:    Failed to read more data ( error or EOF ).
	OP_EBADLINK: We couldn't find a page even after seeking back to the
	start of the stream. */
	private final int op_get_prev_page_serial( final JOpusSeekRecord _sr,
		long _offset, final int _serialno, final int[] _serialnos, final int _nserialnos )
	{
		final JOpusSeekRecord preferred_sr = new JOpusSeekRecord();
		final Jogg_page og = new Jogg_page();
		long  begin, end1, original_end;// java renamed
		original_end = end1 = begin = _offset;
		boolean preferred_found = false;
		_offset = -1L;
		int chunk_size = OP_CHUNK_SIZE;
		do {
			// OP_ASSERT( chunk_size >= OP_PAGE_SIZE_MAX );
			begin -= chunk_size;
			begin = begin > 0 ? begin : 0;
			final int ret = op_seek_helper( begin );
			if( ret < 0 ) {
				return ret;
			}
			long search_start = begin;
			while( this.offset < end1 ) {
				final long llret = op_get_next_page( og, end1 );
				if( llret < OP_FALSE ) {
					return (int)llret;
				} else if( llret == OP_FALSE ) {
					break;
				}
				final int serialno = og.ogg_page_serialno();
				/*Save the information for this page.
				We're not interested in the page itself... just the serial number, byte
				offset, page size, and granule position.*/
				_sr.search_start = search_start;
				_sr.offset = _offset = llret;
				_sr.serialno = serialno;
				// OP_ASSERT( _of.offset-_offset >= 0 );
				// OP_ASSERT( _of.offset-_offset <= OP_PAGE_SIZE_MAX );
				_sr.size = (int)(this.offset - _offset);
				_sr.gp = og.ogg_page_granulepos();
				/*If this page is from the stream we're looking for, remember it.*/
				if( serialno == _serialno ) {
					preferred_found = true;
					preferred_sr.copyFrom( _sr );
				}
				if( ! op_lookup_serialno( serialno, _serialnos, _nserialnos ) ) {
					/*We fell off the end of the link, which means we seeked back too far
					and shouldn't have been looking in that link to begin with.
					If we found the preferred serial number, forget that we saw it.*/
					preferred_found = false;
				}
				search_start = llret + 1;
			}
			/*We started from the beginning of the stream and found nothing.
			This should be impossible unless the contents of the stream changed out
			from under us after we read from it.*/
			if( 0 == begin && _offset < 0 ) {
				return OP_EBADLINK;
			}
			/*Bump up the chunk size.
			This is mildly helpful when seeks are very expensive ( http ).*/
			chunk_size <<= 1;
			chunk_size = chunk_size <= OP_CHUNK_SIZE_MAX ? chunk_size : OP_CHUNK_SIZE_MAX;
			/*Avoid quadratic complexity if we hit an invalid patch of the file.*/
			end1 = begin + OP_PAGE_SIZE_MAX - 1;
			end1 = end1 <= original_end ? end1 : original_end;
		}
		while( _offset < 0 );
		if( preferred_found ) {
			_sr.copyFrom( preferred_sr );
		}
		return 0;
	}

	/** Find the last page beginning before _offset with the given serial number and
	a valid granule position.
	Unlike the above search, this continues until it finds such a page, but does
	not stray outside the current link.
	We could implement it ( inefficiently ) by calling op_get_prev_page_serial()
	repeatedly until it returned a page that had both our preferred serial
	number and a valid granule position, but doing it with a separate function
	allows us to avoid repeatedly re-scanning valid pages from other streams as
	we seek-back-and-read-forward.
	[out] _gp:   Returns the granule position of the page that was found on
	success.
	_offset:     The _offset before which to find a page.
	Any page returned will consist of data entirely before _offset.
	_serialno:   The target serial number.
	_serialnos:  The list of serial numbers in the link that contains the
	preferred serial number.
	_nserialnos: The number of serial numbers in the current link.
	Return: The offset of the page on success, or a negative value on failure.
	OP_EREAD:    Failed to read more data ( error or EOF ).
	OP_EBADLINK: We couldn't find a page even after seeking back past the
	beginning of the link. */
	private final long op_get_last_page( final long[] _gp,
		long _offset, final int _serialno, final int[] _serialnos, final int _nserialnos )
	{
		final Jogg_page og = new Jogg_page();
		long  begin, end1, original_end;// java renamed
		/*The target serial number must belong to the current link.*/
		// OP_ASSERT( op_lookup_serialno( _serialno, _serialnos, _nserialnos ) );
		original_end = end1 = begin = _offset;
		_offset = -1L;
		/*We shouldn't have to initialize gp, but gcc is too dumb to figure out that
		ret >= 0 implies we entered the if( page_gp != -1 ) block at least once.*/
		long gp = -1L;
		int chunk_size = OP_CHUNK_SIZE;
		do {
			// OP_ASSERT( chunk_size >= OP_PAGE_SIZE_MAX );
			begin -= chunk_size;
			begin = begin >= 0 ? begin : 0;
			final int ret = op_seek_helper( begin );
			if( ret < 0 ) {
				return ret;
			}
			boolean left_link = false;
			while( this.offset < end1 ) {
				final long llret = op_get_next_page( og, end1 );
				if( llret < OP_FALSE ) {
					return llret;
				} else if( llret == OP_FALSE ) {
					break;
				}
				final int serialno = og.ogg_page_serialno();
				if( serialno == _serialno ) {
					/*The page is from the right stream...*/
					final long page_gp = og.ogg_page_granulepos();
					if( page_gp != -1 ) {
						/*And has a valid granule position.
						Let's remember it.*/
						_offset = llret;
						gp = page_gp;
					}
				}
				else if( ! op_lookup_serialno( serialno, _serialnos, _nserialnos ) ) {
					/*We fell off the start of the link, which means we don't need to keep
					seeking any farther back.*/
					left_link = true;
				}
			}
			/*We started from at or before the beginning of the link and found nothing.
			This should be impossible unless the contents of the source changed out
			from under us after we read from it.*/
			if( ( left_link || 0 == begin ) && _offset < 0 ) {
				return OP_EBADLINK;
			}
			/*Bump up the chunk size.
			This is mildly helpful when seeks are very expensive ( http ).*/
			chunk_size <<= 1;
			chunk_size = chunk_size <= OP_CHUNK_SIZE_MAX ? chunk_size : OP_CHUNK_SIZE_MAX;
			/*Avoid quadratic complexity if we hit an invalid patch of the file.*/
			end1 = begin + OP_PAGE_SIZE_MAX - 1;
			end1 = end1 <= original_end ? end1 : original_end;
		}
		while( _offset < 0 );
		_gp[0] = gp;
		return _offset;
	}

	/** Uses the local ogg_stream storage in _of.
	This is important for non-streaming input sources. */
	private final int op_fetch_headers_impl( final JOpusHead _head, final JOpusTags _tags,
		// final int[][] _serialnos, final int[] _nserialnos, final int[] _cserialnos,
		final Jserialno_aux aux,// java
		final Jogg_page _og )
	{
		final Jogg_packet ogg = new Jogg_packet();
		if( aux != null && aux.serialnos != null ) {// if( _serialnos != null ) {
			aux.nserialnos = 0;// _nserialnos[0] = 0;
		}
		/*Extract the serialnos of all BOS pages plus the first set of Opus headers
		we see in the link.*/
		while( _og.ogg_page_bos() ) {
			if( aux != null ) {// if( _serialnos != null ) {
				if( op_lookup_page_serialno( _og, aux.serialnos /*_serialnos[0]*/, aux.nserialnos/*_nserialnos[0]*/ ) ) {
					/*A dupe serialnumber in an initial header packet set == invalid stream.*/
					return OP_EBADHEADER;
				}
				final int ret = op_add_serialno( _og, aux /* _serialnos, _nserialnos, _cserialnos */ );
				if( ret < 0 ) {
					return ret;
				}
			}
			if( this.ready_state < OP_STREAMSET ) {
				/*We don't have an Opus stream in this link yet, so begin prospective
				stream setup.
				We need a stream to get packets.*/
				this.os.ogg_stream_reset_serialno( _og.ogg_page_serialno() );
				this.os.ogg_stream_pagein( _og );
				if( this.os.ogg_stream_packetout( ogg ) > 0 ) {
					final int ret = JOpusHead.opus_head_parse( _head, ogg.packet_base, ogg.packet, ogg.bytes );
					/*Found a valid Opus header.
					Continue setup.*/
					if( ret >= 0 ) {
						this.ready_state = OP_STREAMSET;
					} else if( ret != OP_ENOTFORMAT ) {
						return ret;
					}
				}
				/*TODO: Should a BOS page with no packets be an error?*/
			}
			/*Get the next page.
			No need to clamp the boundary offset against _of.end, as all errors
			become OP_ENOTFORMAT or OP_EBADHEADER.*/
			if( op_get_next_page( _og, OP_ADV_OFFSET( this.offset, OP_CHUNK_SIZE ) ) < 0 ) {
				return this.ready_state < OP_STREAMSET ? OP_ENOTFORMAT : OP_EBADHEADER;
			}
		}
		if( this.ready_state != OP_STREAMSET ) {
			return OP_ENOTFORMAT;
		}
		/*If the first non-header page belonged to our Opus stream, submit it.*/
		if( this.os.serialno == _og.ogg_page_serialno() ) {
			this.os.ogg_stream_pagein( _og );
		}
		/*Loop getting packets.*/
		for( ; ; ) {
			switch( this.os.ogg_stream_packetout( ogg ) ) {
			case 0: {
				/*Loop getting pages.*/
				for( ; ; ) {
					/*No need to clamp the boundary offset against _of.end, as all
					errors become OP_EBADHEADER.*/
					if( op_get_next_page( _og, OP_ADV_OFFSET( this.offset, OP_CHUNK_SIZE ) ) < 0 ) {
						return OP_EBADHEADER;
					}
					/*If this page belongs to the correct stream, go parse it.*/
					if( this.os.serialno == _og.ogg_page_serialno() ) {
						this.os.ogg_stream_pagein( _og );
						break;
					}
					/*If the link ends before we see the Opus comment header, abort.*/
					if( _og.ogg_page_bos() ) {
						return OP_EBADHEADER;
						/*Otherwise, keep looking.*/
					}
				}
				} break;
			/*We shouldn't get a hole in the headers!*/
			case -1: return OP_EBADHEADER;
			default: {
				/*Got a packet.
				It should be the comment header.*/
				int ret = JOpusTags.opus_tags_parse( _tags, ogg.packet_base, ogg.packet, ogg.bytes );
				if( ret < 0 ) {
					return ret;
				}
				/*Make sure the page terminated at the end of the comment header.
				If there is another packet on the page, or part of a packet, then
				reject the stream.
				Otherwise seekable sources won't be able to seek back to the start
				properly.*/
				ret = this.os.ogg_stream_packetout( ogg );
				if( ret != 0 || _og.header_base[ _og.header + _og.header_len - 1 ] == -1/* 255 */ ) {// java: 255 -> -1 to avoid & 0xff
					/*If we fail, the caller assumes our tags are uninitialized.*/
					_tags.opus_tags_clear();
					return OP_EBADHEADER;
				}
				return 0;
			}
			}
		}
	}

	private final int op_fetch_headers( final JOpusHead _head,
		final JOpusTags _tags,
		// final int[][] _serialnos, final int[] _nserialnos, final int[] _cserialnos,
		final Jserialno_aux aux,// java helper
		Jogg_page _og )
	{
		if( null == _og ) {
			final Jogg_page og = new Jogg_page();
			/*No need to clamp the boundary offset against _of.end, as all errors
			become OP_ENOTFORMAT.*/
			if( op_get_next_page( og, OP_ADV_OFFSET( this.offset, OP_CHUNK_SIZE ) ) < 0 ) {
				return OP_ENOTFORMAT;
			}
			_og = og;
		}
		this.ready_state = OP_OPENED;
		// final int ret = op_fetch_headers_impl( _of, _head,_tags, _serialnos, _nserialnos, _cserialnos, _og );
		final int ret = op_fetch_headers_impl( _head,_tags, aux, _og );
		/*Revert back from OP_STREAMSET to OP_OPENED on failure, to prevent
		double-free of the tags in an unseekable stream.*/
		if( ret < 0 ) {
			this.ready_state = OP_OPENED;
		}
		return ret;
	}

	/*Granule position manipulation routines.
	A granule position is defined to be an unsigned 64-bit integer, with the
	special value -1 in two's complement indicating an unset or invalid granule
	position.
	We are not guaranteed to have an unsigned 64-bit type, so we construct the
	following routines that
	a ) Properly order negative numbers as larger than positive numbers, and
	b ) Check for underflow or overflow past the special -1 value.
	This lets us operate on the full, valid range of granule positions in a
	consistent and safe manner.
	This full range is organized into distinct regions:
	[ -1 ( invalid ) ][ 0 ... OP_INT64_MAX ][ OP_INT64_MIN ... -2 ][-1 ( invalid ) ]

	No one should actually use granule positions so large that they're negative,
	even if they are technically valid, as very little software handles them
	correctly ( including most of Xiph.Org's ).
	This library also refuses to support durations so large they won't fit in a
	signed 64-bit integer ( to avoid exposing this mess to the application, and
	to simplify a good deal of internal arithmetic ), so the only way to use them
	successfully is if pcm_start is very large.
	This means there isn't anything you can do with negative granule positions
	that you couldn't have done with purely non-negative ones.
	The main purpose of these routines is to allow us to think very explicitly
	about the possible failure cases of all granule position manipulations.*/

	/** Safely adds a small signed integer to a valid ( not -1 ) granule position.
	The result can use the full 64-bit range of values ( both positive and
	negative ), but will fail on overflow ( wrapping past -1; wrapping past
	OP_INT64_MAX is explicitly okay ).
	[out] _dst_gp: The resulting granule position.
	Only modified on success.
	_src_gp:       The granule position to add to.
	This must not be -1.
	_delta:        The amount to add.
	This is allowed to be up to 32 bits to support the maximum
	duration of a single Ogg page ( 255 packets * 120 ms per
	packet  ==  1,468,800 samples at 48 kHz ).
	Return: 0 on success, or OP_EINVAL if the result would wrap around past -1. */
	private static final int op_granpos_add( final long[] _dst_gp, long _src_gp, int _delta ) {
		/*The code below handles this case correctly, but there's no reason we
		should ever be called with these values, so make sure we aren't.*/
		// OP_ASSERT( _src_gp != -1 );
		if( _delta > 0 ) {
			/*Adding this amount to the granule position would overflow its 64-bit
			range.*/
			if( _src_gp < 0 && (_src_gp >= -1 - _delta) ) {
				return OP_EINVAL;
			}
			if(  _src_gp > Long.MAX_VALUE - (long)_delta ) {
				/*Adding this amount to the granule position would overflow the positive
				half of its 64-bit range.
				Since signed overflow is undefined in C, do it in a way the compiler
				isn't allowed to screw up.*/
				_delta -= (int)(Long.MAX_VALUE - _src_gp) + 1;
				_src_gp = Long.MIN_VALUE;
			}
		}
		else if( _delta < 0 ) {
			/*Subtracting this amount from the granule position would underflow its
			64-bit range.*/
			if( _src_gp >= 0 && (_src_gp < -_delta) ) {
				return OP_EINVAL;
			}
			if(  _src_gp < Long.MIN_VALUE - _delta ) {
				/*Subtracting this amount from the granule position would underflow the
				negative half of its 64-bit range.
				Since signed underflow is undefined in C, do it in a way the compiler
				isn't allowed to screw up.*/
				_delta += (int)(_src_gp - Long.MIN_VALUE) + 1;
				_src_gp = Long.MAX_VALUE;
			}
		}
		_dst_gp[0] = _src_gp + _delta;
		return 0;
	}

	/*Safely computes the difference between two granule positions.
	The difference must fit in a signed 64-bit integer, or the function fails.
	It correctly handles the case where the granule position has wrapped around
	from positive values to negative ones.
	[out] _delta: The difference between the granule positions.
	Only modified on success.
	_gp_a:        The granule position to subtract from.
	This must not be -1.
	_gp_b:        The granule position to subtract.
	This must not be -1.
	Return: 0 on success, or OP_EINVAL if the result would not fit in a signed
	64-bit integer.*/
	private static final int op_granpos_diff( final long[] _delta, final long _gp_a, final long _gp_b ) {
		/*The code below handles these cases correctly, but there's no reason we
		should ever be called with these values, so make sure we aren't.*/
		// OP_ASSERT( _gp_a != -1 );
		// OP_ASSERT( _gp_b != -1 );
		final boolean gp_a_negative = _gp_a < 0;
		final boolean gp_b_negative = _gp_b < 0;
		if( gp_a_negative ^ gp_b_negative ) {
			long da;
			long db;
			if( gp_a_negative ) {
				/*_gp_a has wrapped to a negative value but _gp_b hasn't: the difference
				should be positive.*/
				/*Step 1: Handle wrapping.*/
				/*_gp_a < 0 => da < 0.*/
				da = ( Long.MIN_VALUE - _gp_a ) - 1L;
				/*_gp_b >= 0 => db >= 0.*/
				db = Long.MAX_VALUE - _gp_b;
				/*Step 2: Check for overflow.*/
				if( Long.MAX_VALUE + da < db ) {
					return OP_EINVAL;
				}
				_delta[0] = db - da;
			}
			else {
				/*_gp_b has wrapped to a negative value but _gp_a hasn't: the difference
				should be negative.*/
				/*Step 1: Handle wrapping.*/
				/*_gp_a >= 0 => da <= 0*/
				da = _gp_a + Long.MIN_VALUE;
				/*_gp_b < 0 => db <= 0*/
				db = Long.MIN_VALUE - _gp_b;
				/*Step 2: Check for overflow.*/
				if( da < Long.MIN_VALUE - db ) {
					return OP_EINVAL;
				}
				_delta[0] = da + db;
			}
		} else {
			_delta[0] = _gp_a - _gp_b;
		}
		return 0;
	}

	private static final int op_granpos_cmp( final long _gp_a, final long _gp_b ) {
		/*The invalid granule position -1 should behave like NaN: neither greater
		than nor less than any other granule position, nor equal to any other
		granule position, including itself.
		However, that means there isn't anything we could sensibly return from this
		function for it.*/
		// OP_ASSERT( _gp_a != -1 );
		// OP_ASSERT( _gp_b != -1 );
		/*Handle the wrapping cases.*/
		if( _gp_a < 0 ) {
			if( _gp_b >= 0 ) {
				return 1;
				/*Else fall through.*/
			}
		}
		else if( _gp_b < 0 ) {
			return -1;
		}
		/*No wrapping case.*/
		return ( _gp_a > _gp_b ? 1 : 0) - ( _gp_b > _gp_a ? 1 : 0);
	}

	/** Returns the duration of the packet (in samples at 48 kHz), or a negative
	value on error. */
	private static final int op_get_packet_duration( final byte[] _data, final int doffset, final int _len ) {
		final int nframes = JOpusDecoder.opus_packet_get_nb_frames( _data, doffset, _len );
		if( nframes < 0 ) {
			return OP_EBADPACKET;
		}
		final int frame_size = Jopus.opus_packet_get_samples_per_frame( _data, doffset, 48000 );
		final int nsamples = nframes * frame_size;
		if( nsamples > 120 * 48 ) {
			return OP_EBADPACKET;
		}
		return nsamples;
	}

	/* This function more properly belongs in info.c, but we define it here to allow
	the static granule position manipulation functions to remain static. */
	/** Converts a granule position to a sample offset for a given Ogg Opus stream.
	   The sample offset is simply <code>_gp-_head->pre_skip</code>.
	   Granule position values smaller than OpusHead#pre_skip correspond to audio
	    that should never be played, and thus have no associated sample offset.
	   This function returns -1 for such values.
	   This function also correctly handles extremely large granule positions,
	    which may have wrapped around to a negative number when stored in a signed
	    ogg_int64_t value.
	   @param _head The #OpusHead information from the ID header of the stream.
	   @param _gp   The granule position to convert.
	   @return The sample offset associated with the given granule position
	            (counting at a 48 kHz sampling rate), or the special value -1 on
	            error (i.e., the granule position was smaller than the pre-skip
	            amount). */
	public static final long opus_granule_sample( final JOpusHead _head, final long _gp ) {
		final int pre_skip = _head.pre_skip;
		/* if( _gp != -1 && op_granpos_add( &_gp, _gp, -pre_skip ) ) {
			_gp = -1;
		}
		return _gp;*/
		final long[] request = new long[1];// java helper
		if( _gp != -1 && op_granpos_add( request, _gp, -pre_skip ) != 0 ) {
			return -1;
		}
		return request[0];
	}

	/** Grab all the packets currently in the stream state, and compute their
	durations.
	_of.op_count is set to the number of packets collected.
	[out] _durations: Returns the durations of the individual packets.
	Return: The total duration of all packets, or OP_HOLE if there was a hole. */
	private final int op_collect_audio_packets( final int _durations[/* 255 */] ) {
		/*Count the durations of all packets in the page.*/
		int count = 0;// java renamed
		int total_duration = 0;
		for( ; ; ) {
			/*This takes advantage of undocumented libogg behavior that returned
			ogg_packet buffers are valid at least until the next page is
			submitted.
			Relying on this is not too terrible, as _none_ of the Ogg memory
			ownership/lifetime rules are well-documented.
			But I can read its code and know this will work.*/
			final int ret = this.os.ogg_stream_packetout( this.op[ count ] );
			if( 0 == ret ) {
				break;
			}
			if( ret < 0 ) {
				/*We shouldn't get holes in the middle of pages.*/
				// OP_ASSERT( op_count == 0 );
				/*Set the return value and break out of the loop.
				We want to make sure op_count gets set to 0, because we've ingested a
				page, so any previously loaded packets are now invalid.*/
				total_duration = OP_HOLE;
				break;
			}
			/*Unless libogg is broken, we can't get more than 255 packets from a
			single page.*/
			// OP_ASSERT( op_count < 255 );
			_durations[count] = op_get_packet_duration( this.op[count].packet_base, this.op[count].packet, this.op[count].bytes );
			if( _durations[count] > 0 ) {
				/*With at most 255 packets on a page, this can't overflow.*/
				total_duration += _durations[count++];
			}
			/*Ignore packets with an invalid TOC sequence.*/
			else if( count > 0 ) {
				/*But save the granule position, if there was one.*/
				this.op[count - 1].granulepos = this.op[count].granulepos;
			}
		}
		this.op_pos = 0;
		this.op_count = count;
		return total_duration;
	}

	/** Starting from current cursor position, get the initial PCM offset of the next
	page.
	This also validates the granule position on the first page with a completed
	audio data packet, as required by the spec.
	If this link is completely empty ( no pages with completed packets ), then this
	function sets pcm_start = pcm_end = 0 and returns the BOS page of the next link
	( if any ).
	In the seekable case, we initialize pcm_end = -1 before calling this function,
	so that later we can detect that the link was empty before calling
	op_find_final_pcm_offset().
	[inout] _link: The link for which to find pcm_start.
	[out] _og:     Returns the BOS page of the next link if this link was empty.
	In the unseekable case, we can then feed this to
	op_fetch_headers() to start the next link.
	The caller may pass NULL ( e.g., for seekable streams ), in
	which case this page will be discarded.
	Return: 0 on success, 1 if there is a buffered BOS page available, or a
	negative value on unrecoverable error. */
	private final int op_find_initial_pcm_offset( final JOggOpusLink _link, Jogg_page _og ) {
		final Jogg_page og = new Jogg_page();
		final int durations[] = new int[255];
		if( _og == null ) {
			_og = og;
		}
		final int serialno = this.os.serialno;
		int count = 0;// java renamed
		/*We shouldn't have to initialize total_duration, but gcc is too dumb to
		figure out that op_count>0 implies we've been through the whole loop at
		least once.*/
		int total_duration = 0;
		long page_offset;
		do {
			page_offset = op_get_next_page( _og, this.end );
			/*We should get a page unless the file is truncated or mangled.
			Otherwise there are no audio data packets in the whole logical stream.*/
			if( page_offset < 0 ) {
				/*Fail if there was a read error.*/
				if( page_offset < OP_FALSE ) {
					return (int)page_offset;
				}
				/*Fail if the pre-skip is non-zero, since it's asking us to skip more
				samples than exist.*/
				if( _link.head.pre_skip > 0 ) {
					return OP_EBADTIMESTAMP;
				}
				_link.pcm_file_offset = 0;
				/*Set pcm_end and end_offset so we can skip the call to
				op_find_final_pcm_offset().*/
				_link.pcm_start = _link.pcm_end = 0;
				_link.end_offset = _link.data_offset;
				return 0;
			}
			/*Similarly, if we hit the next link in the chain, we've gone too far.*/
			if( _og.ogg_page_bos() ) {
				if( _link.head.pre_skip > 0 ) {
					return OP_EBADTIMESTAMP;
				}
				/*Set pcm_end and end_offset so we can skip the call to
				op_find_final_pcm_offset().*/
				_link.pcm_file_offset = 0;
				_link.pcm_start = _link.pcm_end = 0;
				_link.end_offset = _link.data_offset;
				/*Tell the caller we've got a buffered page for them.*/
				return 1;
			}
			/*Ignore pages from other streams ( not strictly necessary, because of the
			checks in ogg_stream_pagein(), but saves some work ).*/
			if( serialno != _og.ogg_page_serialno() ) {
				continue;
			}
			this.os.ogg_stream_pagein( _og );
			/*Bitrate tracking: add the header's bytes here.
			The body bytes are counted when we consume the packets.*/
			this.bytes_tracked += _og.header_len;
			/*Count the durations of all packets in the page.*/
			do {
				total_duration = op_collect_audio_packets( durations );
			} while( total_duration < 0 );
			count = this.op_count;
		}
		while( count <= 0 );
		/*We found the first page with a completed audio data packet: actually look
		at the granule position.
		RFC 3533 says, "A special value of -1 ( in two's complement ) indicates that
		no packets finish on this page," which does not say that a granule
		position that is NOT -1 indicates that some packets DO finish on that page
		( even though this was the intention, libogg itself violated this intention
		for years before we fixed it ).
		The Ogg Opus specification only imposes its start-time requirements
		on the granule position of the first page with completed packets,
		so we ignore any set granule positions until then.*/
		final long cur_page_gp = this.op[count - 1].granulepos;
		/*But getting a packet without a valid granule position on the page is not
		okay.*/
		if( cur_page_gp == -1 ) {
			return OP_EBADTIMESTAMP;
		}
		final boolean cur_page_eos = this.op[count - 1].e_o_s;
		final long[] request = new long[1];// java helper
		long pcm_start;
		if( ! cur_page_eos ) {
			/*The EOS flag wasn't set.
			Work backwards from the provided granule position to get the starting PCM
			offset.*/
			if( op_granpos_add( request /* &pcm_start*/, cur_page_gp, -total_duration ) < 0 ) {
				/*The starting granule position MUST not be smaller than the amount of
				audio on the first page with completed packets.*/
				return OP_EBADTIMESTAMP;
			}
			pcm_start = request[0];// java
		}
		else {
			/*The first page with completed packets was also the last.*/
			if( op_granpos_add( request /* &pcm_start */, cur_page_gp, -total_duration ) < 0 ) {
				/*If there's less audio on the page than indicated by the granule
				position, then we're doing end-trimming, and the starting PCM offset
				is zero by spec mandate.*/
				request[0] = 0;
				/*However, the end-trimming MUST not ask us to trim more samples than
				exist after applying the pre-skip.*/
				if( op_granpos_cmp( cur_page_gp, _link.head.pre_skip ) < 0 ) {
					return OP_EBADTIMESTAMP;
				}
			}
			pcm_start = request[0];// java
		}
		/*Timestamp the individual packets.*/
		long previous_packet_gp = pcm_start;// java renamed
		int pi;
		for( pi = 0; pi < count; pi++ ) {
			if( cur_page_eos ) {
				// OP_ALWAYS_TRUE( ! op_granpos_diff( diff, cur_page_gp, prev_packet_gp ) );
				op_granpos_diff( request /* &diff*/, cur_page_gp, previous_packet_gp );
				final long diff = durations[pi] - request[0];// diff;// java
				/*If we have samples to trim...*/
				if( diff > 0 ) {
					/*If we trimmed the entire packet, stop ( the spec says encoders
					shouldn't do this, but we support it anyway ).*/
					if( diff > durations[pi] ) {
						break;
					}
					this.op[pi].granulepos = previous_packet_gp = cur_page_gp;
					/*Move the EOS flag to this packet, if necessary, so we'll trim the
					samples.*/
					this.op[pi].e_o_s = true;
					continue;
				}
			}
			/*Update the granule position as normal.*/
			// OP_ALWAYS_TRUE( ! op_granpos_add( &_of.op[pi].granulepos, prev_packet_gp, durations[pi] ) );
			op_granpos_add( request /* &_of.op[pi].granulepos*/, previous_packet_gp, durations[pi] );
			this.op[pi].granulepos = request[0];// java
			previous_packet_gp = request[0];// _of.op[pi].granulepos;// java
		}
		/*Update the packet count after end-trimming.*/
		this.op_count = pi;
		this.cur_discard_count = _link.head.pre_skip;
		_link.pcm_file_offset = 0;
		this.prev_packet_gp = _link.pcm_start = pcm_start;
		this.prev_page_offset = page_offset;
		return 0;
	}

	/** Starting from current cursor position, get the final PCM offset of the
	previous page.
	This also validates the duration of the link, which, while not strictly
	required by the spec, we need to ensure duration calculations don't
	overflow.
	This is only done for seekable sources.
	We must validate that op_find_initial_pcm_offset() succeeded for this link
	before calling this function, otherwise it will scan the entire stream
	backwards until it reaches the start, and then fail. */
	private final int op_find_final_pcm_offset(
		final int[] _serialnos, final int _nserialnos, final JOggOpusLink _link,
		long _offset, final int _end_serialno, long _end_gp,
		final long[] _total_duration )
	{
		final long[] request = new long[1];// java helper
		/*For the time being, fetch end PCM offset the simple way.*/
		final int cur_serialno = _link.serialno;
		if( _end_serialno != cur_serialno || _end_gp == -1L ) {
			_offset = op_get_last_page( request /* &_end_gp*/, _offset, cur_serialno, _serialnos, _nserialnos );
			if( _offset < 0 ) {
				return (int)_offset;
			}
			_end_gp = request[0];// java
		}
		/*At worst we should have found the first page with completed packets.*/
		if( _offset < _link.data_offset ) {
			return OP_EBADLINK;
		}
		/*This implementation requires that the difference between the first and last
		granule positions in each link be representable in a signed, 64-bit
		number, and that each link also have at least as many samples as the
		pre-skip requires.*/
		if( op_granpos_diff( request /* &duration*/, _end_gp, _link.pcm_start ) < 0
				|| request[0] /* duration */ < _link.head.pre_skip ) {
			return OP_EBADTIMESTAMP;
		}
		long duration = request[0];// java
		/*We also require that the total duration be representable in a signed,
		64-bit number.*/
		duration -= _link.head.pre_skip;
		final long total_duration = _total_duration[0];
		if( Long.MAX_VALUE - duration < total_duration ) {
			return OP_EBADTIMESTAMP;
		}
		_total_duration[0] = total_duration + duration;
		_link.pcm_end = _end_gp;
		_link.end_offset = _offset;
		return 0;
	}

	/** Rescale the number _x from the range [0, _from] to [0, _to].
	_from and _to must be positive. */
	private static final long op_rescale64( long _x, final long _from, final long _to ) {
		if( _x >= _from ) {
			return _to;
		}
		if( _x <= 0 ) {
			return 0;
		}
		long frac = 0;
		for( int i = 0; i < 63; i++ ) {
			frac <<= 1;
			// OP_ASSERT( _x <= _from );
			if( _x >= _from >> 1 ) {
			_x -= _from - _x;
			frac |= 1L;
			} else {
				_x <<= 1;
			}
		}
		long ret = 0;
		for( int i = 0; i < 63; i++ ) {
			if( (frac & 1) != 0 ) {
				ret = (ret & _to & 1) + (ret >> 1) + (_to >> 1);
			} else {
				ret >>= 1;
			}
			frac >>= 1;
		}
		return ret;
	}

	/** The minimum granule position spacing allowed for making predictions.
	This corresponds to about 1 second of audio at 48 kHz for both Opus and
	Vorbis, or one keyframe interval in Theora with the default keyframe spacing
	of 256. */
	private static final int OP_GP_SPACING_MIN = 48000;

	/** Try to estimate the location of the next link using the current seek
	records, assuming the initial granule position of any streams we've found is 0. */
	private static final long op_predict_link_start( final JOpusSeekRecord[] _sr, final int _nsr,
			final long _searched, long _end_searched, final int _bias ) {
		/*Require that we be at least OP_CHUNK_SIZE from the end.
		We don't require that we be at least OP_CHUNK_SIZE from the beginning,
		because if we are we'll just scan forward without seeking.*/
		_end_searched -= OP_CHUNK_SIZE;
		if( _searched >= _end_searched ) {
			return -1;
		}
		final long[] request = new long[1];// java helper
		long bisect = _end_searched;
		for( int sri = 0; sri < _nsr; sri++ ) {
			/*If the granule position is negative, either it's invalid or we'd cause
			overflow.*/
			final long gp1 = _sr[sri].gp;
			if( gp1 < 0 ) {
				continue;
			}
			/*We require some minimum distance between granule positions to make an
			estimate.
			We don't actually know what granule position scheme is being used,
			because we have no idea what kind of stream these came from.
			Therefore we require a minimum spacing between them, with the
			expectation that while bitrates and granule position increments might
			vary locally in quite complex ways, they are globally smooth.*/
			;
			if( op_granpos_add( request /* &gp2_min */, gp1, OP_GP_SPACING_MIN ) < 0 ) {
				/*No granule position would satisfy us.*/
				continue;
			}
			final long gp2_min = request[0];// java
			final long offset1 = _sr[sri].offset;
			final int serialno1 = _sr[sri].serialno;
			for( int srj = sri; srj-- > 0; ) {
				long gp2 = _sr[ srj ].gp;
				if( gp2 < gp2_min ) {
					continue;
				}
				/*Oh, and also make sure these came from the same stream.*/
				if( _sr[ srj ].serialno != serialno1 ) {
					continue;
				}
				long offset2 = _sr[ srj ].offset;
				/*For once, we can subtract with impunity.*/
				final long den = gp2 - gp1;
				final long ipart = gp2 / den;
				final long num = offset2 - offset1;
				// OP_ASSERT( num > 0 );
				if( ipart > 0 && (offset2 - _searched) / ipart < num ) {
					continue;
				}
				offset2 -= ipart * num;
				gp2 -= ipart * den;
				offset2 -= op_rescale64( gp2, den, num ) - _bias;
				if( offset2 < _searched ) {
					continue;
				}
				bisect = bisect <= offset2 ? bisect : offset2;
				break;
			}
		}
		return bisect >= _end_searched ? -1 : bisect;
	}

	/** Finds each bitstream link, one at a time, using a bisection search.
	This has to begin by knowing the offset of the first link's initial page. */
	private final int op_bisect_forward_serialno(
			long _searched, final JOpusSeekRecord[] _sr, final int _csr,
			// final int[][] _serialnos, final int[] _nserialnos, final int[] _cserialnos
			final Jserialno_aux aux// java helper
		)
	{
		final Jogg_page og = new Jogg_page();
		int numlinks, clinks;
		JOggOpusLink[] olinks = this.links;
		numlinks = clinks = this.nlinks;
		long total_duration = 0;
		final long[] request = new long[1];// java helper
		/*We start with one seek record, for the last page in the file.
		We build up a list of records for places we seek to during link
		enumeration.
		This list is kept sorted in reverse order.
		We only care about seek locations that were _not_ in the current link,
		therefore we can add them one at a time to the end of the list as we
		improve the lower bound on the location where the next link starts.*/
		int nsr = 1;
		int[] serial_nos;// java renamed
		int nserial_nos;
		for( ; ; ) {
			serial_nos = aux.serialnos;// _serialnos[0];
			nserial_nos = aux.nserialnos;// _nserialnos[0];
			if( numlinks >= clinks ) {
				if( clinks > Integer.MAX_VALUE - 1 >> 1 ) {
					return OP_EFAULT;
				}
				clinks = (clinks << 1) + 1;
				// OP_ASSERT( nlinks < clinks );
				olinks = Arrays.copyOf( olinks, clinks );
				/* if( olinks == null ) {// java never null
					return OP_EFAULT;
				}*/
				for( int i = numlinks; i < clinks; i++ ) {// java creating new holders
					olinks[i] = new JOggOpusLink();
				}
				this.links = olinks;
			}
			/*Invariants:
			We have the headers and serial numbers for the link beginning at 'begin'.
			We have the offset and granule position of the last page in the file
			( potentially not a page we care about ).*/
			/*Scan the seek records we already have to save us some bisection.*/
			int sri;
			for( sri = 0; sri < nsr; sri++ ) {
				if( op_lookup_serialno( _sr[sri].serialno, serial_nos, nserial_nos ) ) {
					break;
				}
			}
			/*Is the last page in our current list of serial numbers?*/
			if( sri <= 0 ) {
				break;
			}
			/*Last page wasn't found.
			We have at least one more link.*/
			long last = -1L;
			long end_searched = _sr[sri - 1].search_start;
			long next = _sr[sri - 1].offset;
			long end_gp = -1L;
			long end_offset = 0;// java to fix "The local variable end_offset may not have been initialized"
			if( sri < nsr ) {
				_searched = _sr[sri].offset + _sr[sri].size;
				if( _sr[sri].serialno == olinks[numlinks - 1].serialno ) {
					end_gp = _sr[sri].gp;
					end_offset = _sr[sri].offset;
				}
			}
			nsr = sri;
			long bisect = -1;
			/*If we've already found the end of at least one link, try to pick the
			first bisection point at twice the average link size.
			This is a good choice for files with lots of links that are all about the
			same size.*/
			if( numlinks > 1 ) {
				final long last_offset = olinks[numlinks - 1].offset;
				final long avg_link_size = last_offset / ( numlinks - 1 );
				final long upper_limit = end_searched - OP_CHUNK_SIZE - avg_link_size;
				if( ( last_offset > _searched - avg_link_size )
						&& ( last_offset < upper_limit ) ) {
					bisect = last_offset + avg_link_size;
					if( bisect < upper_limit ) {
						bisect += avg_link_size;
					}
				}
			}
			/*We guard against garbage separating the last and first pages of two
			links below.*/
			while( _searched < end_searched ) {
				/*If we don't have a better estimate, use simple bisection.*/
				if( bisect == -1 ) {
					bisect = _searched + ( end_searched - _searched >> 1 );
				}
				/*If we're within OP_CHUNK_SIZE of the start, scan forward.*/
				if( bisect - _searched < OP_CHUNK_SIZE ) {
					bisect = _searched;
				} else {
					end_gp = -1;
				}
				final int ret = op_seek_helper( bisect );
				if( ret < 0 ) {
					return ret;
				}
				last = op_get_next_page( og, _sr[nsr - 1].offset );
				if( last < OP_FALSE ) {
					return (int)last;
				}
				int next_bias = 0;
				if( last == OP_FALSE ) {
					end_searched = bisect;
				} else {
					final int serialno = og.ogg_page_serialno();
					final long gp = og.ogg_page_granulepos();
					if( ! op_lookup_serialno( serialno, serial_nos, nserial_nos ) ) {
						end_searched = bisect;
						next = last;
						/*In reality we should always have enough room, but be paranoid.*/
						if( nsr < _csr ) {
							_sr[nsr].search_start = bisect;
							_sr[nsr].offset = last;
							// OP_ASSERT( _of.offset-last >= 0 );
							// OP_ASSERT( _of.offset-last <= OP_PAGE_SIZE_MAX );
							_sr[nsr].size = (int)(this.offset - last);
							_sr[nsr].serialno = serialno;
							_sr[nsr].gp = gp;
							nsr++;
						}
					}
					else {
						_searched = this.offset;
						next_bias = OP_CHUNK_SIZE;
						if( serialno == olinks[numlinks - 1].serialno ) {
							/*This page was from the stream we want, remember it.
							If it's the last such page in the link, we won't have to go back
							looking for it later.*/
							end_gp = gp;
							end_offset = last;
						}
					}
				}
				bisect = op_predict_link_start( _sr, nsr, _searched, end_searched, next_bias );
			}
			/*Bisection point found.
			Get the final granule position of the previous link, assuming
			op_find_initial_pcm_offset() didn't already determine the link was
			empty.*/
			if( olinks[numlinks - 1].pcm_end == -1 ) {
				if( end_gp == -1 ) {
					/*If we don't know where the end page is, we'll have to seek back and
					look for it, starting from the end of the link.*/
					end_offset = next;
					/*Also forget the last page we read.
					It won't be available after the seek.*/
					last = -1;
				}
				request[0] = total_duration;// java
				final int ret = op_find_final_pcm_offset( serial_nos, nserial_nos,
						olinks[numlinks - 1], end_offset, olinks[numlinks - 1].serialno, end_gp,
						request /* &total_duration*/ );// FIXME "The local variable end_offset may not have been initialized"
				if( ret < 0 ) {
					return ret;
				}
				total_duration = request[0];// java
			}
			if( last != next ) {
				/*The last page we read was not the first page the next link.
				Move the cursor position to the offset of that first page.
				This only performs an actual seek if the first page of the next link
				does not start at the end of the last page from the current Opus
				stream with a valid granule position.*/
				final int ret = op_seek_helper( next );
				if( ret < 0 ) {
					return ret;
				}
			}
			int ret = op_fetch_headers( olinks[numlinks].head, olinks[numlinks].tags,
					aux /* _serialnos, _nserialnos, _cserialnos */, last != next ? null : og );
			if( ret < 0 ) {
				return ret;
			}
			olinks[numlinks].offset = next;
			olinks[numlinks].data_offset = this.offset;
			olinks[numlinks].serialno = this.os.serialno;
			olinks[numlinks].pcm_end = -1;
			/*This might consume a page from the next link, however the next bisection
			always starts with a seek.*/
			ret = op_find_initial_pcm_offset( olinks[numlinks], null );
			if( ret < 0 ) {
				return ret;
			}
			olinks[numlinks].pcm_file_offset = total_duration;
			_searched = this.offset;
			/*Mark the current link count so it can be cleaned up on error.*/
			this.nlinks = ++numlinks;
		}
		/*Last page is in the starting serialno list, so we've reached the last link.
		Now find the last granule position for it ( if we didn't the first time we
		looked at the end of the stream, and if op_find_initial_pcm_offset()
		didn't already determine the link was empty ).*/
		if( olinks[numlinks - 1].pcm_end == -1 ) {
			request[0] = total_duration;// java
			final int ret = op_find_final_pcm_offset( serial_nos, nserial_nos,
					olinks[numlinks - 1], _sr[0].offset, _sr[0].serialno, _sr[0].gp, request /* &total_duration */ );
			if( ret < 0 ) {
				return ret;
			}
			total_duration = request[0];// java
		}
		/*Trim back the links array if necessary.*/
		olinks = Arrays.copyOf( olinks, numlinks );// TODO check if need to create new objects
		/* if( olinks != null ) {// java never null
			this.links = olinks;
		}*/
		/*We also don't need these anymore.*/
		// _ogg_free( *_serialnos );
		// _serialnos[0] = null;
		aux.serialnos = null;
		// _cserialnos[0] = _nserialnos[0] = 0;
		aux.cserialnos = aux.nserialnos = 0;
		return 0;
	}

	private final void op_update_gain() {
		/*If decode isn't ready, then we'll apply the gain when we initialize the
		decoder.*/
		if( this.ready_state < OP_INITSET ) {
			return;
		}
		final int[] request = { 0 };// java helper
		int gain_q8 = this.gain_offset_q8;
		final int li = this.seekable ? this.cur_link : 0;
		final JOpusHead head = this.links[li].head;
		/*We don't have to worry about overflow here because the header gain and
		track gain must lie in the range [-32768,32767], and the user-supplied
		offset has been pre-clamped to [-98302,98303].*/
		switch( this.gain_type ) {
		case OP_ALBUM_GAIN : {
				// final int album_gain_q8 = 0;// java, helper already zeroed
				this.links[li].tags.opus_tags_get_album_gain( request /* &album_gain_q8 */ );
				gain_q8 += request[0];// album_gain_q8;
				gain_q8 += head.output_gain;
			} break;
		case OP_TRACK_GAIN: {
				// final int track_gain_q8 = 0;// java, helper already zeroed
				this.links[li].tags.opus_tags_get_track_gain( request /* &track_gain_q8 */ );
				gain_q8 += request[0];// track_gain_q8;
				gain_q8 += head.output_gain;
			} break;
		case OP_HEADER_GAIN: gain_q8 += head.output_gain; break;
		case OP_ABSOLUTE_GAIN: break;
		default:// OP_ASSERT( 0 );
		}
		gain_q8 = OP_CLAMP( -32768, gain_q8, 32767 );
		// OP_ASSERT( _of.od != NULL );
// #if defined( OPUS_SET_GAIN )
		this.od.opus_multistream_decoder_ctl( Jopus_defines.OPUS_SET_GAIN, gain_q8 );
// #else
		/*A fallback that works with both float and fixed-point is a bunch of work,
		so just force people to use a sufficiently new version.
		This is deployed well enough at this point that this shouldn't be a burden.*/
// # error "libopus 1.0.1 or later required"
// #endif
	}

	private final int op_make_decode_ready() {
		if( this.ready_state > OP_STREAMSET ) {
			return 0;
		}
		if( this.ready_state < OP_STREAMSET ) {
			return OP_EFAULT;
		}
		final int li = this.seekable ? this.cur_link : 0;
		final JOpusHead head = this.links[li].head;
		final int stream_count = head.stream_count;
		final int coupled_count = head.coupled_count;
		final int channel_count = head.channel_count;
		/*Check to see if the current decoder is compatible with the current link.*/
		if( this.od != null && this.od_stream_count == stream_count
				&& this.od_coupled_count == coupled_count && this.od_channel_count == channel_count
				&& Arrays.equals( this.od_mapping, head.mapping )/* channel_count *//* == 0 */ ) {
			this.od.opus_multistream_decoder_ctl( Jopus_defines.OPUS_RESET_STATE );
		}
		else {
			final int err[] = new int[1];
			// _of.od = null;// opus_multistream_decoder_destroy( _of.od );
			this.od = JOpusMSDecoder.opus_multistream_decoder_create( 48000, channel_count, stream_count, coupled_count, head.mapping, err );
			if( this.od == null ) {
				return OP_EFAULT;
			}
			this.od_stream_count = stream_count;
			this.od_coupled_count = coupled_count;
			this.od_channel_count = channel_count;
			System.arraycopy( head.mapping, 0, this.od_mapping, 0, channel_count );
		}
		this.ready_state = OP_INITSET;
		this.bytes_tracked = 0;
		this.samples_tracked = 0;
// if( ! OP_FIXED_POINT ) {
		this.state_channel_count = 0;
		// Use the serial number for the PRNG seed to get repeatable output for straight play-throughs.
		this.dither_seed = this.links[li].serialno;
// }
		op_update_gain();
		return 0;
	}

	private int op_open_seekable2_impl() {
		/*We can seek, so set out learning all about this file.*/
		try {
			this.callbacks.seek( 0, JOpusFileCallbacks.SEEK_END );
			this.offset = this.end = this.callbacks.tell();
			if( this.end < 0 ) {
				return OP_EREAD;
			}
		} catch(final IOException ie) {
			return OP_EREAD;
		}
		final long data_offset = this.links[0].data_offset;
		if( this.end < data_offset ) {
			return OP_EBADLINK;
		}
		/*64 seek records should be enough for anybody.
		Actually, with a bisection search in a 63-bit range down to OP_CHUNK_SIZE
		granularity, much more than enough.*/
		final JOpusSeekRecord sr[] = new JOpusSeekRecord[64];
		int i = 64;
		do {
			sr[--i] = new JOpusSeekRecord();
		} while( i > 0 );
		/*Get the offset of the last page of the physical bitstream, or, if we're
		lucky, the last Opus page of the first link, as most Ogg Opus files will
		contain a single logical bitstream.*/
		int ret = op_get_prev_page_serial( sr[0], this.end, this.links[0].serialno, this.serialnos, this.nserialnos );
		if( ret < 0 ) {
			return ret;
		}
		/*If there's any trailing junk, forget about it.*/
		this.end = sr[0].offset + sr[0].size;
		if( this.end < data_offset ) {
			return OP_EBADLINK;
		}
		/*Now enumerate the bitstream structure.*/
		final Jserialno_aux aux = new Jserialno_aux( this.serialnos, this.nserialnos, this.cserialnos );
		ret = op_bisect_forward_serialno( data_offset, sr, sr.length,
				aux /* &_of.serialnos, &_of.nserialnos, &_of.cserialnos*/ );
		this.serialnos = aux.serialnos;
		this.nserialnos = aux.nserialnos;
		this.cserialnos = aux.cserialnos;
		return ret;
	}

	private final int op_open_seekable2() {
		/*We're partially open and have a first link header state in storage in _of.
		Save off that stream state so we can come back to it.
		It would be simpler to just dump all this state and seek back to
		links[0].data_offset when we're done.
		But we do the extra work to allow us to seek back to _exactly_ the same
		stream position we're at now.
		This allows, e.g., the HTTP backend to continue reading from the original
		connection ( if it's still available ), instead of opening a new one.
		This means we can open and start playing a normal Opus file with a single
		link and reasonable packet sizes using only two HTTP requests.*/
		final int start_op_count = this.op_count;
		/*This is a bit too large to put on the stack unconditionally.*/
		final Jogg_packet op_start[] = new Jogg_packet[ start_op_count ];
		/* if( op_start == null ) {
			return OP_EFAULT;
		} */
		final Jogg_sync_state oy_start = new Jogg_sync_state( this.oy );
		final Jogg_stream_state os_start = new Jogg_stream_state( this.os );
		final long previous_page_offset = this.prev_page_offset;
		final long start_offset = this.offset;
		for( int i = 0; i < start_op_count; i++ ) {
			op_start[i] = new Jogg_packet( this.op[i] );
		}
		// OP_ASSERT( ( *_of.callbacks.tell )( _of.source ) == op_position( _of ) );
		this.oy.ogg_sync_init();
		this.os.ogg_stream_init( -1 );
		final int ret = op_open_seekable2_impl();
		/*Restore the old stream state.*/
		this.os.ogg_stream_clear();
		this.oy.ogg_sync_clear();
		this.oy.copyFrom( oy_start );
		this.os.copyFrom( os_start );
		this.offset = start_offset;
		this.op_count = start_op_count;
		for( int i = 0; i < start_op_count; i++ ) {
			this.op[i].copyFrom( op_start[i] );
		}
		// op_start = null;
		this.prev_packet_gp = this.links[0].pcm_start;
		this.prev_page_offset = previous_page_offset;
		this.cur_discard_count = this.links[0].head.pre_skip;
		if( ret < 0 ) {
			return ret;
		}
		/*And restore the position indicator.*/
		// ret = _of.callbacks.seek( op_position( _of ), JOpusFileCallbacks.SEEK_SET );
		// return ret < 0 ? OP_EREAD : 0;
		try {
			this.callbacks.seek( op_position(), JOpusFileCallbacks.SEEK_SET );
		} catch(final IOException ie) {
			return OP_EREAD;
		}
		return 0;
	}

	/** Clear out the current logical bitstream decoder. */
	private final void op_decode_clear() {
		/*We don't actually free the decoder.
		We might be able to re-use it for the next link.*/
		this.op_count = 0;
		this.od_buffer_size = 0;
		this.prev_packet_gp = -1;
		this.prev_page_offset = -1;
		if( !this.seekable ) {
			// OP_ASSERT( _of.ready_state >= OP_INITSET );
			this.links[0].tags.opus_tags_clear();
		}
		this.ready_state = OP_OPENED;
	}

	private final void op_clear() {
		this.od_buffer = null;
		/* if( _of.od != null ) {
			opus_multistream_decoder_destroy( _of.od );
		} */
		this.od = null;
		final JOggOpusLink olinks[] = this.links;// java renamed
		if( ! this.seekable ) {
			if( this.ready_state > OP_OPENED || this.ready_state == OP_PARTOPEN ) {
				olinks[0].tags.opus_tags_clear();
			}
		}
		else if( olinks != null ) {
			final int n_links = this.nlinks;// java renamed
			for( int link = 0; link < n_links; link++ ) {
				olinks[link].tags.opus_tags_clear();
			}
		}
		this.links = null;
		this.serialnos = null;
		this.os.ogg_stream_clear();
		this.oy.ogg_sync_clear();
		if( this.callbacks.mIsClose ) {
			try { this.callbacks.close(); } catch(final IOException ie) {}
		}
	}

	private final int op_open1(
		// final Object _stream,// java don't need. this is _cb
		final JOpusFileCallbacks _cb,
		final byte[] _initial_data, final int _initial_bytes )
	{
		this.clear();// memset( _of, 0, sizeof( *_of ) );
		if( _initial_bytes < 0 )
		 {
			return OP_EFAULT;// java if( _initial_bytes > LONG_MAX ) return OP_EFAULT;
		}
		this.end = -1;
		// _of.stream = _stream;// java don't need. this is _cb
		this.callbacks = _cb;
		/*At a minimum,  we need to be able to read data.*/
		if( ! this.callbacks.mIsRead ) {
			return OP_EREAD;
		}
		/*Initialize the framing state.*/
		this.oy.ogg_sync_init();
		/*Perhaps some data was previously read into a buffer for testing against
		other stream types.
		Allow initialization from this previously read data (especially as we may
		be reading from a non-seekable stream).
		This requires copying it into a buffer allocated by ogg_sync_buffer() and
		doesn't support seeking, so this is not a good mechanism to use for
		decoding entire files from RAM.*/
		if( _initial_bytes > 0 ) {
			final int buffer = this.oy.ogg_sync_buffer( _initial_bytes );// java changed
			System.arraycopy( _initial_data, 0, this.oy.data, buffer, _initial_bytes );
			this.oy.ogg_sync_wrote( _initial_bytes );
		}
		/*Can we seek?
		Stevens suggests the seek test is portable.*/
		boolean is_seekable = _cb.mIsSeek;
		try {
			_cb.seek( 0, JOpusFileCallbacks.SEEK_CUR );
		} catch(final IOException ie) {
			is_seekable = false;
		}
		/*If seek is implemented, tell must also be implemented.*/
		if( is_seekable ) {
			if( ! this.callbacks.mIsTell ) {
				return OP_EINVAL;
			}
			try {
				final long pos = this.callbacks.tell();
				/*If the current position is not equal to the initial bytes consumed,
				absolute seeking will not work.*/
				if( pos != (long)_initial_bytes ) {
					return OP_EINVAL;
				}
			} catch(final IOException ie) {
				return OP_EINVAL;
			}
		}
		this.seekable = is_seekable;
		/*Don't seek yet.
		Set up a 'single' (current) logical bitstream entry for partial open.*/
		this.links = new JOggOpusLink[1];
		this.links[0] = new JOggOpusLink();// java
		/*The serialno gets filled in later by op_fetch_headers().*/
		this.os.ogg_stream_init( -1 );
		final Jogg_page og = new Jogg_page();
		Jogg_page pog = null;
		final Jserialno_aux aux = new Jserialno_aux();// java helper
		int ret;
		for( ; ; ) {
			/*Fetch all BOS pages, store the Opus header and all seen serial numbers,
			and load subsequent Opus setup headers.*/
			aux.serialnos = this.serialnos;// java
			aux.nserialnos = this.nserialnos;// java
			aux.cserialnos = this.cserialnos;// java
			ret = op_fetch_headers( this.links[0].head, this.links[0].tags,
							aux /* &_of.serialnos, &_of.nserialnos, &_of.cserialnos*/, pog );
			this.serialnos = aux.serialnos;// java
			this.nserialnos = aux.nserialnos;// java
			this.cserialnos = aux.cserialnos;// java
			if( ret < 0 ) {
				break;
			}
			this.nlinks = 1;
			this.links[0].offset = 0;
			this.links[0].data_offset = this.offset;
			this.links[0].pcm_end = -1;
			this.links[0].serialno = this.os.serialno;
			/*Fetch the initial PCM offset.*/
			ret = op_find_initial_pcm_offset( this.links[0], og );
			if( is_seekable || ret <= 0 ) {
				break;
			}
			/*This link was empty, but we already have the BOS page for the next one in og.
			We can't seek, so start processing the next link right now.*/
			this.links[0].tags.opus_tags_clear();
			this.nlinks = 0;
			if( ! is_seekable ) {
				this.cur_link++;
			}
			pog = og;
		}
		if( ret >= 0 ) {
			this.ready_state = OP_PARTOPEN;
		}
		return ret;
	}

	private final int op_open2() {
		int ret;
		// OP_ASSERT( _of.ready_state == OP_PARTOPEN );
		if( this.seekable ) {
			this.ready_state = OP_OPENED;
			ret = op_open_seekable2();
		} else {
			ret = 0;
		}
		if( ret >= 0 ) {
			/*We have buffered packets from op_find_initial_pcm_offset().
			Move to OP_INITSET so we can use them.*/
			this.ready_state = OP_STREAMSET;
			ret = op_make_decode_ready();
			if( ret >= 0 ) {
				return 0;
			}
		}
		/*Don't auto-close the stream on failure.*/
		// _of.callbacks.close = null;// FIXME why? calling methods don't checks the value
		op_clear();
		return ret;
	}

	/** Partially open a stream using the given set of callbacks to access it.
	   This tests for Opusness and loads the headers for the first link.
	   It does not seek (although it tests for seekability).
	   You can query a partially open stream for the few pieces of basic
	    information returned by op_serialno(), op_channel_count(), op_head(), and
	    op_tags() (but only for the first link).
	   You may also determine if it is seekable via a call to op_seekable().
	   You cannot read audio from the stream, seek, get the size or duration,
	    get information from links other than the first one, or even get the total
	    number of links until you finish opening the stream with op_test_open().
	   If you do not need to do any of these things, you can dispose of it with
	    op_free() instead.

	   This function is provided mostly to simplify porting existing code that used
	    <tt>libvorbisfile</tt>.
	   For new code, you are likely better off using op_test() instead, which
	    is less resource-intensive, requires less data to succeed, and imposes a
	    hard limit on the amount of data it examines (important for unseekable
	    sources, where all such data must be buffered until you are sure of the
	    stream type).
	   @param _stream        The stream to read from (e.g., a <code>FILE *</code>).
	   @param _cb            The callbacks with which to access the stream.
	                         <code><a href="#op_read_func">read()</a></code> must
	                          be implemented.
	                         <code><a href="#op_seek_func">seek()</a></code> and
	                          <code><a href="#op_tell_func">tell()</a></code> may
	                          be <code>NULL</code>, or may always return -1 to
	                          indicate a source is unseekable, but if
	                          <code><a href="#op_seek_func">seek()</a></code> is
	                          implemented and succeeds on a particular source, then
	                          <code><a href="#op_tell_func">tell()</a></code> must
	                          also.
	                         <code><a href="#op_close_func">close()</a></code> may
	                          be <code>NULL</code>, but if it is not, it will be
	                          called when the \c OggOpusFile is destroyed by
	                          op_free().
	                         It will not be called if op_open_callbacks() fails
	                          with an error.
	   @param _initial_data  An initial buffer of data from the start of the
	                          stream.
	                         Applications can read some number of bytes from the
	                          start of the stream to help identify this as an Opus
	                          stream, and then provide them here to allow the
	                          stream to be tested more thoroughly, even if it is
	                          unseekable.
	   @param _initial_bytes The number of bytes in \a _initial_data.
	                         If the stream is seekable, its current position (as
	                          reported by
	                          <code><a href="#opus_tell_func">tell()</a></code>
	                          at the start of this function) must be equal to
	                          \a _initial_bytes.
	                         Otherwise, seeking to absolute positions will
	                          generate inconsistent results.
	   @param _error [out]    Returns 0 on success, or a failure code on error.
	                         You may pass in <code>NULL</code> if you don't want
	                          the failure code.
	                         See op_open_callbacks() for a full list of failure
	                          codes.
	   @return A partially opened \c OggOpusFile, or <code>NULL</code> on error.
	           <tt>libopusfile</tt> does <em>not</em> take ownership of the source
	            if the call fails.
	           The calling application is responsible for closing the source if
	            this call returns an error. */
	static final JOggOpusFile op_test_callbacks(
			// final Object _stream,// java don't need. this is _cb
			final JOpusFileCallbacks _cb,
			final byte[] _initial_data, final int _initial_bytes, final int[] _error )
	{
		JOggOpusFile of = new JOggOpusFile();
		int ret = OP_EFAULT;
		if( of != null ) {
			ret = of.op_open1( /* _stream,*/ _cb, _initial_data, _initial_bytes );
			if( ret >= 0 ) {
				if( _error != null ) {
					_error[0] = 0;
				}
				return of;
			}
			/*Don't auto-close the stream on failure.*/
			// of.callbacks.close = null;// FIXME why? calling methods don't checks the value
			of.op_clear();
			of = null;// _ogg_free( of );
		}
		if( _error != null ) {
			_error[0] = ret;
		}
		return null;
	}

	/** Open a stream using the given set of callbacks to access it.
	   @param _stream        The stream to read from (e.g., a <code>FILE *</code>).
                            This value will be passed verbatim as the first
                             argument to all of the callbacks.
	   @param _cb            The callbacks with which to access the stream.
	                         <code><a href="#op_read_func">read()</a></code> must
	                          be implemented.
	                         <code><a href="#op_seek_func">seek()</a></code> and
	                          <code><a href="#op_tell_func">tell()</a></code> may
	                          be <code>NULL</code>, or may always return -1 to
	                          indicate a stream is unseekable, but if
	                          <code><a href="#op_seek_func">seek()</a></code> is
	                          implemented and succeeds on a particular stream, then
	                          <code><a href="#op_tell_func">tell()</a></code> must
	                          also.
	                         <code><a href="#op_close_func">close()</a></code> may
	                          be <code>NULL</code>, but if it is not, it will be
	                          called when the \c OggOpusFile is destroyed by
	                          op_free().
	                         It will not be called if op_open_callbacks() fails
	                          with an error.
	   @param _initial_data  An initial buffer of data from the start of the
	                          stream.
	                         Applications can read some number of bytes from the
	                          start of the stream to help identify this as an Opus
	                          stream, and then provide them here to allow the
	                          stream to be opened, even if it is unseekable.
	   @param _initial_bytes The number of bytes in \a _initial_data.
	                         If the stream is seekable, its current position (as
	                          reported by
	                          <code><a href="#opus_tell_func">tell()</a></code>
	                          at the start of this function) must be equal to
	                          \a _initial_bytes.
	                         Otherwise, seeking to absolute positions will
	                          generate inconsistent results.
	   @param[out] _error    Returns 0 on success, or a failure code on error.
	                         You may pass in <code>NULL</code> if you don't want
	                          the failure code.
	                         The failure code will be one of
	                         <dl>
	                           <dt>#OP_EREAD</dt>
	                           <dd>An underlying read, seek, or tell operation
	                            failed when it should have succeeded, or we failed
	                            to find data in the stream we had seen before.</dd>
	                           <dt>#OP_EFAULT</dt>
	                           <dd>There was a memory allocation failure, or an
	                            internal library error.</dd>
	                           <dt>#OP_EIMPL</dt>
	                           <dd>The stream used a feature that is not
	                            implemented, such as an unsupported channel
	                            family.</dd>
	                           <dt>#OP_EINVAL</dt>
	                           <dd><code><a href="#op_seek_func">seek()</a></code>
	                            was implemented and succeeded on this source, but
	                            <code><a href="#op_tell_func">tell()</a></code>
	                            did not, or the starting position indicator was
	                            not equal to \a _initial_bytes.</dd>
	                           <dt>#OP_ENOTFORMAT</dt>
	                           <dd>The stream contained a link that did not have
	                            any logical Opus streams in it.</dd>
	                           <dt>#OP_EBADHEADER</dt>
	                           <dd>A required header packet was not properly
	                            formatted, contained illegal values, or was missing
	                            altogether.</dd>
	                           <dt>#OP_EVERSION</dt>
	                           <dd>An ID header contained an unrecognized version
	                            number.</dd>
	                           <dt>#OP_EBADLINK</dt>
	                           <dd>We failed to find data we had seen before after
	                            seeking.</dd>
	                           <dt>#OP_EBADTIMESTAMP</dt>
	                           <dd>The first or last timestamp in a link failed
	                            basic validity checks.</dd>
	                         </dl>
	   @return A freshly opened \c OggOpusFile, or <code>NULL</code> on error.
	           <tt>libopusfile</tt> does <em>not</em> take ownership of the stream
	            if the call fails.
	           The calling application is responsible for closing the stream if
	            this call returns an error. */
	public static final JOggOpusFile op_open_callbacks(
			// final Object _stream,// java don't need. this is _cb
			final JOpusFileCallbacks _cb,
			final byte[] _initial_data, final int _initial_bytes, final int[] _error )
	{
		JOggOpusFile of = op_test_callbacks( /*_stream,*/ _cb, _initial_data, _initial_bytes, _error );
		if( of != null ) {
			final int ret = of.op_open2();
			if( ret >= 0 ) {
				return of;
			}
			if( _error != null ) {
				_error[0] = ret;
			}
			of = null;
		}
		return null;
	}

	/** Convenience routine to clean up from failure for the open functions that
	create their own streams. */
	private static final JOggOpusFile op_open_close_on_failure(
			// final Object _stream,// java don't need. this is _cb
			final JOpusFileCallbacks _cb, final int[] _error )
	{
		if( _cb == null ) {// if( _stream == null ) {
			if( _error != null ) {
				_error[0] = OP_EFAULT;
			}
			return null;
		}
		final JOggOpusFile of = op_open_callbacks( /* _stream,*/ _cb, null, 0, _error );
		if( of == null ) {
			try { _cb.close(); } catch( final IOException e ) {}
		}
		return of;
	}

	/** Open a stream from the given file path.
	   @param      _path  The path to the file to open.
	   @param[out] _error Returns 0 on success, or a failure code on error.
	                      You may pass in <code>NULL</code> if you don't want the
	                       failure code.
	                      The failure code will be #OP_EFAULT if the file could not
	                       be opened, or one of the other failure codes from
	                       op_open_callbacks() otherwise.
	   @return A freshly opened \c OggOpusFile, or <code>NULL</code> on error. */
	public static final JOggOpusFile op_open_file( final String _path, final int[] _error ) {
		try {
			final JOpusFileInputStream cb = new JOpusFileInputStream( _path, "r" );
			return op_open_close_on_failure( cb, _error );
		} catch(final FileNotFoundException fe) {
		}
		return null;
	}

	/** Open a stream from a memory buffer.
	   @param      _data  The memory buffer to open.
	   @param      _size  The number of bytes in the buffer.
	   @param[out] _error Returns 0 on success, or a failure code on error.
	                      You may pass in <code>NULL</code> if you don't want the
	                       failure code.
	                      See op_open_callbacks() for a full list of failure codes.
	   @return A freshly opened \c OggOpusFile, or <code>NULL</code> on error. */
	public static final JOggOpusFile op_open_memory( final byte[] _data, final int _size, final int[] _error ) {
		final JOpusMemoryInputStream cb = new JOpusMemoryInputStream( _data, _size );
		return op_open_close_on_failure( cb, _error );
	}

	/** Convenience routine to clean up from failure for the open functions that
	create their own streams. */
	private static final JOggOpusFile op_test_close_on_failure(
			// final Object _stream,// java don't need. this is _cb
			final JOpusFileCallbacks _cb, final int[] _error )
	{
		if( _cb == null ) {// if( _stream == null ) {
			if( _error != null ) {
				_error[0] = OP_EFAULT;
			}
			return null;
		}
		final JOggOpusFile of = op_test_callbacks( /* _stream,*/ _cb, null, 0, _error );
		if( of == null ) {
			try { _cb.close(); } catch( final IOException e ) {}
		}
		return of;
	}

	/** Partially open a stream from the given file path.
	   \see op_test_callbacks
	   @param      _path  The path to the file to open.
	   @param[out] _error Returns 0 on success, or a failure code on error.
	                      You may pass in <code>NULL</code> if you don't want the
	                       failure code.
	                      The failure code will be #OP_EFAULT if the file could not
	                       be opened, or one of the other failure codes from
	                       op_open_callbacks() otherwise.
	   @return A partially opened \c OggOpusFile, or <code>NULL</code> on error. */
	public static final JOggOpusFile op_test_file( final String _path, final int[] _error ) {
		try {
			final JOpusFileInputStream cb = new JOpusFileInputStream( _path, "r" );
			return op_test_close_on_failure( cb, _error );
		} catch(final FileNotFoundException fe) {
		}
		return null;
	}

	/** Partially open a stream from a memory buffer.
	   \see op_test_callbacks
	   @param      _data  The memory buffer to open.
	   @param      _size  The number of bytes in the buffer.
	   @param[out] _error Returns 0 on success, or a failure code on error.
	                      You may pass in <code>NULL</code> if you don't want the
	                       failure code.
	                      See op_open_callbacks() for a full list of failure codes.
	   @return A partially opened \c OggOpusFile, or <code>NULL</code> on error. */
	public static final JOggOpusFile op_test_memory( final byte[] _data, final int _size, final int[] _error ) {
		final JOpusMemoryInputStream cb = new JOpusMemoryInputStream( _data, _size );
		return op_test_close_on_failure( cb, _error );
	}

	/** Finish opening a stream partially opened with op_test_callbacks() or one of
	    the associated convenience functions.
	   If this function fails, you are still responsible for freeing the
	    \c OggOpusFile with op_free().
	   @param _of The \c OggOpusFile to finish opening.
	   @return 0 on success, or a negative value on error.
	   @retval #OP_EREAD         An underlying read, seek, or tell operation failed
	                              when it should have succeeded.
	   @retval #OP_EFAULT        There was a memory allocation failure, or an
	                              internal library error.
	   @retval #OP_EIMPL         The stream used a feature that is not implemented,
	                              such as an unsupported channel family.
	   @retval #OP_EINVAL        The stream was not partially opened with
	                              op_test_callbacks() or one of the associated
	                              convenience functions.
	   @retval #OP_ENOTFORMAT    The stream contained a link that did not have any
	                              logical Opus streams in it.
	   @retval #OP_EBADHEADER    A required header packet was not properly
	                              formatted, contained illegal values, or was
	                              missing altogether.
	   @retval #OP_EVERSION      An ID header contained an unrecognized version
	                              number.
	   @retval #OP_EBADLINK      We failed to find data we had seen before after
	                              seeking.
	   @retval #OP_EBADTIMESTAMP The first or last timestamp in a link failed basic
	                              validity checks. */
	public final int op_test_open() {
		if( this.ready_state != OP_PARTOPEN ) {
			return OP_EINVAL;
		}
		final int ret = op_open2();
		/*op_open2() will clear this structure on failure.
		Reset its contents to prevent double-frees in op_free().*/
		if( ret < 0 ) {
			// memset( _of, 0, sizeof( *_of ) );
			clear();
		}
		return ret;
	}

	/**
	 * Release all memory used by an \c OggOpusFile.
	 * @param _of The \c OggOpusFile to free.
	 */
	public final void op_free() {
		// if( _of != null ) {
			op_clear();
			// _of = null;
		//}
	}

	/**\defgroup stream_info Stream Information*/
	/*@{*/
	/**\name Functions for obtaining information about streams

	   These functions allow you to get basic information about a stream, including
	    seekability, the number of links (for chained streams), plus the size,
	    duration, bitrate, header parameters, and meta information for each link
	    (or, where available, the stream as a whole).
	   Some of these (size, duration) are only available for seekable streams.
	   You can also query the current stream position, link, and playback time,
	    and instantaneous bitrate during playback.

	   Some of these functions may be used successfully on the partially open
	    streams returned by op_test_callbacks() or one of the associated
	    convenience functions.
	   Their documention will indicate so explicitly.*/
	/*@{*/

	/** Returns whether or not the stream being read is seekable.
	   This is true if
	   <ol>
	   <li>The <code><a href="#op_seek_func">seek()</a></code> and
	    <code><a href="#op_tell_func">tell()</a></code> callbacks are both
	    non-<code>NULL</code>,</li>
	   <li>The <code><a href="#op_seek_func">seek()</a></code> callback was
	    successfully executed at least once, and</li>
	   <li>The <code><a href="#op_tell_func">tell()</a></code> callback was
	    successfully able to report the position indicator afterwards.</li>
	   </ol>
	   This function may be called on partially-opened streams.
	   @param _of The \c OggOpusFile whose seekable status is to be returned.
	   @return A non-zero value if seekable, and 0 if unseekable. */
	public final boolean op_seekable() {
		return this.seekable;
	}

	/** Returns the number of links in this chained stream.
	   This function may be called on partially-opened streams, but it will always
	    return 1.
	   The actual number of links is not known until the stream is fully opened.
	   @param _of The \c OggOpusFile from which to retrieve the link count.
	   @return For fully-open seekable streams, this returns the total number of
	            links in the whole stream, which will be at least 1.
	           For partially-open or unseekable streams, this always returns 1. */
	public final int op_link_count() {
		return this.nlinks;
	}

	/** Get the serial number of the given link in a (possibly-chained) Ogg Opus
	    stream.
	   This function may be called on partially-opened streams, but it will always
	    return the serial number of the Opus stream in the first link.
	   @param _of The \c OggOpusFile from which to retrieve the serial number.
	   @param _li The index of the link whose serial number should be retrieved.
	              Use a negative number to get the serial number of the current
	               link.
	   @return The serial number of the given link.
	           If \a _li is greater than the total number of links, this returns
	            the serial number of the last link.
	           If the stream is not seekable, this always returns the serial number
	            of the current link. */
	public final int op_serialno( int _li ) {
		if( _li >= this.nlinks ) {
			_li = this.nlinks - 1;
		}
		if( ! this.seekable ) {
			_li = 0;
		}
		return this.links[ _li < 0 ? this.cur_link : _li ].serialno;
	}

	/** Get the channel count of the given link in a (possibly-chained) Ogg Opus
	    stream.
	   This is equivalent to <code>op_head(_of,_li)->channel_count</code>, but
	    is provided for convenience.
	   This function may be called on partially-opened streams, but it will always
	    return the channel count of the Opus stream in the first link.
	   @param _of The \c OggOpusFile from which to retrieve the channel count.
	   @param _li The index of the link whose channel count should be retrieved.
	              Use a negative number to get the channel count of the current
	               link.
	   @return The channel count of the given link.
	           If \a _li is greater than the total number of links, this returns
	            the channel count of the last link.
	           If the stream is not seekable, this always returns the channel count
	            of the current link. */
	public final int op_channel_count(final int _li ) {
		return op_head( _li ).channel_count;
	}

	/** Get the total (compressed) size of the stream, or of an individual link in
	    a (possibly-chained) Ogg Opus stream, including all headers and Ogg muxing
	    overhead.
	   \warning If the Opus stream (or link) is concurrently multiplexed with other
	    logical streams (e.g., video), this returns the size of the entire stream
	    (or link), not just the number of bytes in the first logical Opus stream.
	   Returning the latter would require scanning the entire file.
	   @param _of The \c OggOpusFile from which to retrieve the compressed size.
	   @param _li The index of the link whose compressed size should be computed.
	              Use a negative number to get the compressed size of the entire
	               stream.
	   @return The compressed size of the entire stream if \a _li is negative, the
	            compressed size of link \a _li if it is non-negative, or a negative
	            value on error.
	           The compressed size of the entire stream may be smaller than that
	            of the underlying stream if trailing garbage was detected in the
	            file.
	   @retval #OP_EINVAL The stream is not seekable (so we can't know the length),
	                       \a _li wasn't less than the total number of links in
	                       the stream, or the stream was only partially open. */
	public final long op_raw_total( final int _li ) {
		if( this.ready_state < OP_OPENED || ! this.seekable || _li >= this.nlinks ) {
			return OP_EINVAL;
		}
		if( _li < 0 ) {
			return this.end;
		}
		return ( _li + 1 >= this.nlinks ? this.end : this.links[_li + 1].offset )
				- ( _li > 0 ? this.links[_li].offset : 0 );
	}

	/** Get the total PCM length (number of samples at 48 kHz) of the stream, or of
	    an individual link in a (possibly-chained) Ogg Opus stream.
	   Users looking for <code>op_time_total()</code> should use op_pcm_total()
	    instead.
	   Because timestamps in Opus are fixed at 48 kHz, there is no need for a
	    separate function to convert this to seconds (and leaving it out avoids
	    introducing floating point to the API, for those that wish to avoid it).
	   @param _of The \c OggOpusFile from which to retrieve the PCM offset.
	   @param _li The index of the link whose PCM length should be computed.
	              Use a negative number to get the PCM length of the entire stream.
	   @return The PCM length of the entire stream if \a _li is negative, the PCM
	            length of link \a _li if it is non-negative, or a negative value on
	            error.
	   @retval #OP_EINVAL The stream is not seekable (so we can't know the length),
	                       \a _li wasn't less than the total number of links in
	                       the stream, or the stream was only partially open. */
	public final long op_pcm_total( int _li ) {
		final int n_links = this.nlinks;
		if( this.ready_state < OP_OPENED || ! this.seekable || _li >= n_links ) {
			return OP_EINVAL;
		}
		final JOggOpusLink olinks[] = this.links;// java renamed
		/*We verify that the granule position differences are larger than the
		pre-skip and that the total duration does not overflow during link
		enumeration, so we don't have to check here.*/
		long pcm_total = 0;
		if( _li < 0 ) {
			pcm_total = olinks[n_links - 1].pcm_file_offset;
			_li = n_links - 1;
		}
		// OP_ALWAYS_TRUE( ! op_granpos_diff( &diff, links[_li].pcm_end, links[_li].pcm_start ) );
		final long diff[] = new long[1];// java changed
		op_granpos_diff( diff, olinks[_li].pcm_end, olinks[_li].pcm_start );
		return pcm_total + diff[0] - (long)olinks[_li].head.pre_skip;
	}

	/** Get the ID header information for the given link in a (possibly chained) Ogg
	    Opus stream.
	   This function may be called on partially-opened streams, but it will always
	    return the ID header information of the Opus stream in the first link.
	   @param _of The \c OggOpusFile from which to retrieve the ID header
	               information.
	   @param _li The index of the link whose ID header information should be
	               retrieved.
	              Use a negative number to get the ID header information of the
	               current link.
	              For an unseekable stream, \a _li is ignored, and the ID header
	               information for the current link is always returned, if
	               available.
	   @return The contents of the ID header for the given link. */
	public final JOpusHead op_head(int _li) {
		if( _li >= this.nlinks ) {
			_li = this.nlinks - 1;
		}
		if( ! this.seekable ) {
			_li = 0;
		}
		return this.links[ _li < 0 ? this.cur_link : _li ].head;
	}

	/** Get the comment header information for the given link in a (possibly
	    chained) Ogg Opus stream.
	   This function may be called on partially-opened streams, but it will always
	    return the tags from the Opus stream in the first link.
	   @param _of The \c OggOpusFile from which to retrieve the comment header
	               information.
	   @param _li The index of the link whose comment header information should be
	               retrieved.
	              Use a negative number to get the comment header information of
	               the current link.
	              For an unseekable stream, \a _li is ignored, and the comment
	               header information for the current link is always returned, if
	               available.
	   @return The contents of the comment header for the given link, or
	            <code>NULL</code> if this is an unseekable stream that encountered
	            an invalid link. */
	public final JOpusTags op_tags( int _li ) {
		if( _li >= this.nlinks ) {
			_li = this.nlinks - 1;
		}
		if( ! this.seekable ) {
			if( this.ready_state < OP_STREAMSET && this.ready_state != OP_PARTOPEN ) {
				return null;
			}
			_li = 0;
		}
		else if( _li < 0 ) {
			_li = this.ready_state >= OP_STREAMSET ? this.cur_link : 0;
		}
		return this.links[ _li ].tags;
	}

	/** Retrieve the index of the current link.
	   This is the link that produced the data most recently read by
	    op_read_float() or its associated functions, or, after a seek, the link
	    that the seek target landed in.
	   Reading more data may advance the link index (even on the first read after a
	    seek).
	   @param _of The \c OggOpusFile from which to retrieve the current link index.
	   @return The index of the current link on success, or a negative value on
	            failure.
	           For seekable streams, this is a number between 0 (inclusive) and the
                value returned by op_link_count() (exclusive).
	           For unseekable streams, this value starts at 0 and increments by one
	            each time a new link is encountered (even though op_link_count()
	            always returns 1).
	   @retval #OP_EINVAL The stream was only partially open. */
	public final int op_current_link() {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		return this.cur_link;
	}

	/** Compute an average bitrate given a byte and sample count.
	Return: The bitrate in bits per second. */
	private static final int op_calc_bitrate( final long _bytes, final long _samples ) {
		/*These rates are absurd, but let's handle them anyway.*/
		if( _bytes > (Long.MAX_VALUE - (_samples >> 1)) / (48000 * 8) ) {
			if( _bytes / (Integer.MAX_VALUE / (48000 * 8)) >= _samples ) {
				return Integer.MAX_VALUE;
			}
			final long den = _samples / ( 48000 * 8 );
			return (int)((_bytes + (den >> 1)) / den);
		}
		if( _samples <= 0 ) {
			return Integer.MAX_VALUE;
		}
		/*This can't actually overflow in normal operation: even with a pre-skip of
		545 2.5 ms frames with 8 streams running at 1282*8+1 bytes per packet
		( 1275 byte frames + Opus framing overhead + Ogg lacing values ), that all
		produce a single sample of decoded output, we still don't top 45 Mbps.
		The only way to get bitrates larger than that is with excessive Opus
		padding, more encoded streams than output channels, or lots and lots of
		Ogg pages with no packets on them.*/
		final long v = ( _bytes * 48000 * 8 + ( _samples >> 1 ) ) / _samples;// java
		return (int)( v <= Integer.MAX_VALUE ? v : Integer.MAX_VALUE );
	}

	/** Computes the bitrate of the stream, or of an individual link in a
	    (possibly-chained) Ogg Opus stream.
	   The stream must be seekable to compute the bitrate.
	   For unseekable streams, use op_bitrate_instant() to get periodic estimates.
	   \warning If the Opus stream (or link) is concurrently multiplexed with other
	    logical streams (e.g., video), this uses the size of the entire stream (or
	    link) to compute the bitrate, not just the number of bytes in the first
	    logical Opus stream.
	   Returning the latter requires scanning the entire file, but this may be done
	    by decoding the whole file and calling op_bitrate_instant() once at the
	    end.
	   Install a trivial decoding callback with op_set_decode_callback() if you
	    wish to skip actual decoding during this process.
	   @param _of The \c OggOpusFile from which to retrieve the bitrate.
	   @param _li The index of the link whose bitrate should be computed.
	              Use a negative number to get the bitrate of the whole stream.
	   @return The bitrate on success, or a negative value on error.
	   @retval #OP_EINVAL The stream was only partially open, the stream was not
	                       seekable, or \a _li was larger than the number of
	                       links. */
	public final int op_bitrate( final int _li ) {
		if( this.ready_state < OP_OPENED || ! this.seekable || _li >= this.nlinks ) {
			return OP_EINVAL;
		}
		return op_calc_bitrate( op_raw_total( _li ), op_pcm_total( _li ) );
	}

	/** Compute the instantaneous bitrate, measured as the ratio of bits to playable
	    samples decoded since a) the last call to op_bitrate_instant(), b) the last
	    seek, or c) the start of playback, whichever was most recent.
	   This will spike somewhat after a seek or at the start/end of a chain
	    boundary, as pre-skip, pre-roll, and end-trimming causes samples to be
	    decoded but not played.
	   @param _of The \c OggOpusFile from which to retrieve the bitrate.
	   @return The bitrate, in bits per second, or a negative value on error.
	   @retval #OP_FALSE  No data has been decoded since any of the events
	                       described above.
	   @retval #OP_EINVAL The stream was only partially open. */
	public final int op_bitrate_instant() {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		final long tracked = this.samples_tracked;// java renamed
		if( tracked == 0 ) {
			return OP_FALSE;
		}
		final int ret = op_calc_bitrate( this.bytes_tracked, tracked );
		this.bytes_tracked = 0;
		this.samples_tracked = 0;
		return ret;
	}

	/** Given a serialno, find a link with a corresponding Opus stream, if it exists.
		@return: The index of the link to which the page belongs, or a negative number
		          if it was not a desired Opus bitstream section. */
	private final int op_get_link_from_serialno(/* final JOggOpusFile _of,*/ int _cur_link,
			final long _page_offset, final int _serialno) {
		// OP_ASSERT(_of->seekable);
		final JOggOpusLink[] loc_links = this.links;
		final int n_links = this.nlinks;
		int li_lo = 0;
		/*Start off by guessing we're just a multiplexed page in the current link.*/
		int li_hi = _cur_link + 1 < n_links && _page_offset < loc_links[_cur_link + 1].offset ?
				_cur_link + 1 : n_links;
		do {
			if( _page_offset >= loc_links[_cur_link].offset ) {
				li_lo = _cur_link;
			} else {
				li_hi = _cur_link;
			}
			_cur_link = li_lo + (li_hi - li_lo >> 1);
		}
		while( li_hi - li_lo > 1 );
		/*We've identified the link that should contain this page.
		  Make sure it's a page we care about.*/
		if( loc_links[_cur_link].serialno != _serialno ) {
			return OP_FALSE;
		}
		return _cur_link;
	}

	/** Fetch and process a page.
	  This handles the case where we're at a bitstream boundary and dumps the
	   decoding machine.
	  If the decoding machine is unloaded, it loads it.
	  It also keeps prev_packet_gp up to date (seek and read both use this).

	  @return: <0) Error, OP_HOLE (lost packet), or OP_EOF.
	           0) Got at least one audio data packet. */
	private final int op_fetch_and_process_page(
		Jogg_page _og, long _page_offset, final boolean _spanp, boolean _ignore_holes )
	{
		/*We shouldn't get here if we have unprocessed packets.*/
		// OP_ASSERT( _of.ready_state < OP_INITSET || _of.op_pos >= _of.op_count );
		final long[] request = new long[1];// java helper
		int ret;
		final boolean is_seekable = this.seekable;
		final JOggOpusLink olinks[] = this.links;// java renamed
		int curr_link = is_seekable ? this.cur_link : 0;
		int cur_serialno = olinks[curr_link].serialno;
		/*Handle one page.*/
		final Jogg_page og = new Jogg_page();// java moved
		for( ; ; ) {
			// final ogg_page og;// java moved up
			// OP_ASSERT( _of.ready_state >= OP_OPENED );
			/*If we were given a page to use, use it.*/
			if( _og != null ) {
				og.copyFrom( _og );
				_og = null;
			} else {
				/*Keep reading until we get a page with the correct serialno.*/
				_page_offset = op_get_next_page( og, this.end );
			}
			/*EOF: Leave uninitialized.*/
			if( _page_offset < 0 ) {
				return _page_offset < OP_FALSE ? (int)_page_offset : OP_EOF;
			}
			if( this.ready_state >= OP_STREAMSET
				&& cur_serialno != og.ogg_page_serialno() ) {
				/*Two possibilities:
				1) Another stream is multiplexed into this logical section, or*/
				if( ! og.ogg_page_bos() ) {
					continue;
				}
				/* 2) Our decoding just traversed a bitstream boundary.*/
				if( ! _spanp ) {
					return OP_EOF;
				}
				if( this.ready_state >= OP_INITSET ) {
					op_decode_clear();
				}
			} else {
				this.bytes_tracked += og.header_len;
			}
			/*Do we need to load a new machine before submitting the page?
			This is different in the seekable and non-seekable cases.
			In the seekable case, we already have all the header information loaded
			and cached.
			We just initialize the machine with it and continue on our merry way.
			In the non-seekable ( streaming ) case, we'll only be at a boundary if we
			just left the previous logical bitstream, and we're now nominally at the
			header of the next bitstream.*/
			if( this.ready_state < OP_STREAMSET ) {
				if( is_seekable ) {
					final int serialno = og.ogg_page_serialno();
					/*Match the serialno to bitstream section.*/
					// OP_ASSERT(curr_link >= 0 && curr_link < this.nlinks);
					if( olinks[curr_link].serialno != serialno ) {
						/*It wasn't a page from the current link.
						  Is it from the next one?*/
						if( curr_link + 1 < this.nlinks && olinks[curr_link + 1].serialno ==
								serialno ) {
							curr_link++;
						}
						else {
							final int new_link =
								op_get_link_from_serialno( curr_link, _page_offset, serialno );
							/*Not a desired Opus bitstream section. Keep trying.*/
							if( new_link < 0 ) {
								continue;
							}
							curr_link = new_link;
						}
					}
					cur_serialno = serialno;
					this.cur_link = curr_link;
					this.os.ogg_stream_reset_serialno( serialno );
					this.ready_state = OP_STREAMSET;
					/*If we're at the start of this link, initialize the granule position
					and pre-skip tracking.*/
					if( _page_offset <= olinks[curr_link].data_offset ) {
						this.prev_packet_gp = olinks[curr_link].pcm_start;
						this.prev_page_offset = -1;
						this.cur_discard_count = olinks[curr_link].head.pre_skip;
						/*Ignore a hole at the start of a new link ( this is common for
						streams joined in the middle ) or after seeking.*/
						_ignore_holes = true;
					}
				}
				else {
					do {
						/*We're streaming.
						  Fetch the two header packets, build the info struct.*/
						ret = op_fetch_headers( olinks[0].head, olinks[0].tags,
								null /* null, null, null */, og );
						if( ret < 0 ) {
							return ret;
						}
						/*op_find_initial_pcm_offset() will suppress any initial hole for us,
						so no need to set _ignore_holes.*/
						ret = op_find_initial_pcm_offset( olinks[0], og );
						if( ret < 0 ) {
							return ret;
						}
						this.links[0].serialno = cur_serialno = this.os.serialno;
						this.cur_link++;
					}
					/*If the link was empty, keep going, because we already have the
					  BOS page of the next one in og.*/
					while( ret > 0 );
					/*If we didn't get any packets out of op_find_initial_pcm_offset(),
					  keep going ( this is possible if end-trimming trimmed them all ).*/
					if( this.op_count <= 0 ) {
						continue;
					}
					/*Otherwise, we're done.
	  				  TODO: This resets bytes_tracked, which misses the header bytes
	   				   already processed by op_find_initial_pcm_offset().*/
					ret = op_make_decode_ready();
					if( ret < 0 ) {
						return ret;
					}
					return 0;
				}
			}
			/*The buffered page is the data we want, and we're ready for it.
			Add it to the stream state.*/
			if( this.ready_state == OP_STREAMSET ) {
				ret = op_make_decode_ready();
				if( ret < 0 ) {
					return ret;
				}
			}
			/*Extract all the packets from the current page.*/
			this.os.ogg_stream_pagein( og );
			if( this.ready_state >= OP_INITSET ) {
				final int durations[] = new int[255];
				boolean report_hole = false;
				int total_duration = op_collect_audio_packets( durations );
				if( total_duration < 0 ) {
					/*libogg reported a hole (a gap in the page sequence numbers).
					  Drain the packets from the page anyway.
					  If we don't, they'll still be there when we fetch the next page.
					  Then, when we go to pull out packets, we might get more than 255,
					  which would overrun our packet buffer.*/
					total_duration = op_collect_audio_packets( durations );
					// OP_ASSERT( total_duration >= 0 );
					if( ! _ignore_holes ) {
						/*Report the hole to the caller after we finish timestamping the
						  packets.*/
						report_hole = true;
						/*We had lost or damaged pages, so reset our granule position
						  tracking.
						  This makes holes behave the same as a small raw seek.
						  If the next page is the EOS page, we'll discard it (because we
						   can't perform end trimming properly), and we'll always discard at
						   least 80 ms of audio (to allow decoder state to re-converge).
						  We could try to fill in the gap with PLC by looking at timestamps
						   in the non-EOS case, but that's complicated and error prone and we
						   can't rely on the timestamps being valid.*/
						this.prev_packet_gp = -1;
					}
				}
				int count = this.op_count;// java renamed
				/*If we found at least one audio data packet, compute per-packet granule
				positions for them.*/
				if( count > 0 ) {
					int  pi = 0;// java: = 0 to fix "The local variable pi may not have been initialized"
					long cur_page_gp = this.op[count - 1].granulepos;
					final boolean cur_page_eos = this.op[count - 1].e_o_s;
					long previous_packet_gp = this.prev_packet_gp;
					if( previous_packet_gp == -1 ) {
						/*This is the first call after a raw seek.
						Try to reconstruct prev_packet_gp from scratch.*/
						// OP_ASSERT( seekable );
						if( cur_page_eos ) {
							/*If the first page we hit after our seek was the EOS page, and
							we didn't start from data_offset or before, we don't have
							enough information to do end-trimming.
							Proceed to the next link, rather than risk playing back some
							samples that shouldn't have been played.*/
							this.op_count = 0;
							if( report_hole ) {
								return OP_HOLE;
							}
							continue;
						}
						/*By default discard 80 ms of data after a seek, unless we seek
						into the pre-skip region.*/
						int curr_discard_count1 = 80 * 48;
						cur_page_gp = this.op[count - 1].granulepos;
						/*Try to initialize prev_packet_gp.
						If the current page had packets but didn't have a granule
						position, or the granule position it had was too small ( both
						illegal ), just use the starting granule position for the link.*/
						previous_packet_gp = olinks[curr_link].pcm_start;
						if( cur_page_gp != -1 ) {
							op_granpos_add( request /* &prev_packet_gp */, cur_page_gp, -total_duration );
							previous_packet_gp = request[0];// java
						}
						if( 0 == op_granpos_diff( request /* &diff */, previous_packet_gp, olinks[curr_link].pcm_start ) ) {
							final long diff = request[0];// java
							/*If we start at the beginning of the pre-skip region, or we're
							at least 80 ms from the end of the pre-skip region, we discard
							to the end of the pre-skip region.
							Otherwise, we still use the 80 ms default, which will discard
							past the end of the pre-skip region.*/
							final int pre_skip = olinks[curr_link].head.pre_skip;
							final int v = pre_skip - 80 * 48;// java
							if( diff >= 0 && diff <= ( 0 >= v ? 0 : v ) ) {
								curr_discard_count1 = pre_skip - (int)diff;
							}
						}
						this.cur_discard_count = curr_discard_count1;
					}
					if( cur_page_gp == -1 ) {
						/*This page had completed packets but didn't have a valid granule
						   position.
						 This is illegal, but we'll try to handle it by continuing to count
						  forwards from the previous page.*/
						if( op_granpos_add( request /* &cur_page_gp */, previous_packet_gp, total_duration ) < 0 ) {
							/*The timestamp for this page overflowed.*/
							request[0] /* cur_page_gp */ = olinks[curr_link].pcm_end;
						}
						cur_page_gp = request[0];// java
					}
					/*If we hit the last page, handle end-trimming.*/
					if( cur_page_eos && 0 == op_granpos_diff( request /* &diff */, cur_page_gp, previous_packet_gp ) ) {
						long diff = request[0];// java
						if( diff < total_duration ) {
							long cur_packet_gp = previous_packet_gp;
							for( pi = 0; pi < count; pi++ ) {
								/*Check for overflow.*/
								if( diff < 0 && (Long.MAX_VALUE + diff < (long)durations[pi]) ) {
									diff = (long)durations[pi] + 1;
								} else {
									diff = (long)durations[pi] - diff;
								}
								/*If we have samples to trim...*/
								if( diff > 0 ) {
									/*If we trimmed the entire packet, stop ( the spec says encoders
									shouldn't do this, but we support it anyway ).*/
									if( diff > (long)durations[pi] ) {
										break;
									}
									cur_packet_gp = cur_page_gp;
									/*Move the EOS flag to this packet, if necessary, so we'll trim
									the samples during decode.*/
									this.op[pi].e_o_s = true;
								}
								else {
									/*Update the granule position as normal.*/
									// OP_ALWAYS_TRUE( !op_granpos_add( &cur_packet_gp, cur_packet_gp, durations[pi] ) );
									op_granpos_add( request /* &cur_packet_gp */, cur_packet_gp, durations[pi] );
									cur_packet_gp = request[0];// java
								}
								this.op[pi].granulepos = cur_packet_gp;
								// OP_ALWAYS_TRUE( !op_granpos_diff( &diff, cur_page_gp, cur_packet_gp ) );
								op_granpos_diff( request /* &diff */, cur_page_gp, cur_packet_gp );
								diff = request[0];// java
							}
						}
					}
					else {
						/*Propagate timestamps to earlier packets.
						op_granpos_add( &prev_packet_gp,prev_packet_gp,total_duration )
						should succeed and give prev_packet_gp == cur_page_gp.
						But we don't bother to check that, as there isn't much we can do
						if it's not true, and it actually will not be true on the first
						page after a seek, if there was a continued packet.
						The only thing we guarantee is that the start and end granule
						positions of the packets are valid, and that they are monotonic
						within a page.
						They might be completely out of range for this link ( we'll check
						that elsewhere ), or non-monotonic between pages.*/
						if( op_granpos_add( request /* &prev_packet_gp */, cur_page_gp, -total_duration ) < 0 ) {
							/*The starting timestamp for the first packet on this page
							underflowed.
							This is illegal, but we ignore it.*/
							request[0] /* prev_packet_gp */ = 0;
						}
						previous_packet_gp = request[0];// java
						for( pi = 0; pi < count; pi++ ) {
							if( op_granpos_add( request /* &cur_packet_gp */, cur_page_gp, -total_duration ) < 0 ) {
								/*The start timestamp for this packet underflowed.
								This is illegal, but we ignore it.*/
								request[0] /* cur_packet_gp */ = 0;
							}
							// cur_packet_gp = request[0];// java
							total_duration -= durations[pi];
							// OP_ASSERT( total_duration >= 0 );
							// OP_ALWAYS_TRUE( !op_granpos_add( &cur_packet_gp, cur_packet_gp, durations[pi] ) );
							op_granpos_add( request /* &cur_packet_gp */, request[0] /* cur_packet_gp */, durations[pi] );
							this.op[pi].granulepos = request[0];// cur_packet_gp;
						}
						// OP_ASSERT( total_duration == 0 );
					}
					this.prev_packet_gp = previous_packet_gp;
					this.prev_page_offset = _page_offset;
					this.op_count = count = pi;// FIXME "The local variable pi may not have been initialized"
				}
				if( report_hole ) {
					return OP_HOLE;
				}
				/*If end-trimming didn't trim all the packets, we're done.*/
				if( count > 0 ) {
					return 0;
				}
			}
		}
	}

	/**\defgroup stream_seeking Seeking*/
	/*@{*/
	/**\name Functions for seeking in Opus streams

	   These functions let you seek in Opus streams, if the underlying stream
	    support it.
	   Seeking is implemented for all built-in stream I/O routines, though some
	    individual sources may not be seekable (pipes, live HTTP streams, or HTTP
	    streams from a streams that does not support <code>Range</code> requests).

	   op_raw_seek() is the fastest: it is guaranteed to perform at most one
	    physical seek, but, since the target is a byte position, makes no guarantee
	    how close to a given time it will come.
	   op_pcm_seek() provides sample-accurate seeking.
	   The number of physical seeks it requires is still quite small (often 1 or
	    2, even in highly variable bitrate streams).

	   Seeking in Opus requires decoding some pre-roll amount before playback to
	    allow the internal state to converge (as if recovering from packet loss).
	   This is handled internally by <tt>libopusfile</tt>, but means there is
	    little extra overhead for decoding up to the exact position requested
	    (since it must decode some amount of audio anyway).
	   It also means that decoding after seeking may not return exactly the same
	    values as would be obtained by decoding the stream straight through.
	   However, such differences are expected to be smaller than the loss
	    introduced by Opus's lossy compression.*/
	/*@{*/
	/** Seek to a byte offset relative to the <b>compressed</b> data.
	   This also scans packets to update the PCM cursor.
	   It will cross a logical bitstream boundary, but only if it can't get any
	    packets out of the tail of the link to which it seeks.
	   @param _of          The \c OggOpusFile in which to seek.
	   @param _byte_offset The byte position to seek to.
                           This must be between 0 and #op_raw_total(\a _of,\c -1)
                           (inclusive).
	   @return 0 on success, or a negative error code on failure.
	   @retval #OP_EREAD    The underlying seek operation failed.
	   @retval #OP_EINVAL   The stream was only partially open, or the target was
	                         outside the valid range for the stream.
	   @retval #OP_ENOSEEK  This stream is not seekable.
	   @retval #OP_EBADLINK Failed to initialize a decoder for a stream for an
	                         unknown reason. */
	public final int op_raw_seek( final long _pos ) {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		/*Don't dump the decoder state if we can't seek.*/
		if( ! this.seekable ) {
			return OP_ENOSEEK;
		}
		if( _pos < 0 || _pos > this.end ) {
			return OP_EINVAL;
		}
		/*Clear out any buffered, decoded data.*/
		op_decode_clear();
		this.bytes_tracked = 0;
		this.samples_tracked = 0;
		int ret = op_seek_helper( _pos );
		if( ret < 0 ) {
			return OP_EREAD;
		}
		ret = op_fetch_and_process_page( null, -1, true, true );
		/*If we hit EOF, op_fetch_and_process_page() leaves us uninitialized.
		Instead, jump to the end.*/
		if( ret == OP_EOF ) {
			op_decode_clear();
			final int curr_link = this.nlinks - 1;
			this.cur_link = curr_link;
			this.prev_packet_gp = this.links[curr_link].pcm_end;
			this.cur_discard_count = 0;
			ret = 0;
		}
		return ret;
	}

	/** Convert a PCM offset relative to the start of the whole stream to a granule
	position in an individual link. */
	private final long op_get_granulepos( long _pcm_offset, final int[] _li ) {
		// OP_ASSERT( _pcm_offset >= 0 );
		final int n_links = this.nlinks;
		final JOggOpusLink olinks[] = this.links;
		final long duration[] = new long[1];// java changed
		int li_lo = 0;
		int li_hi = n_links;
		do {
		    final int li = li_lo + (li_hi - li_lo >> 1);
		    if( links[li].pcm_file_offset <= _pcm_offset ) {
				li_lo = li;
			} else {
				li_hi = li;
			}
		}
		while( li_hi - li_lo > 1 );
		_pcm_offset -= links[li_lo].pcm_file_offset;
		long pcm_start = links[li_lo].pcm_start;
		final int pre_skip = links[li_lo].head.pre_skip;
		// OP_ALWAYS_TRUE(!op_granpos_diff(&duration,links[li_lo].pcm_end,pcm_start));
		op_granpos_diff( duration, olinks[li_lo].pcm_end, pcm_start );
		duration[0] -= pre_skip;
		if( _pcm_offset >= duration[0] ) {
			return -1;
		}
		_pcm_offset += pre_skip;
		if( pcm_start > Long.MIN_VALUE - _pcm_offset ) {
			/*Adding this amount to the granule position would overflow the positive
			   half of its 64-bit range.
			  Since signed overflow is undefined in C, do it in a way the compiler
			   isn't allowed to screw up.*/
			_pcm_offset -= Long.MIN_VALUE - pcm_start + 1;
			pcm_start = Long.MIN_VALUE;
		}
		pcm_start += _pcm_offset;
		_li[0] = li_lo;
		return pcm_start;
	}

	/** A small helper to determine if an Ogg page contains data that continues onto
	a subsequent page. */
	private static final boolean op_page_continues( final Jogg_page _og ) {
		// OP_ASSERT( _og.header_len >= 27 );
		final int nlacing = (int)_og.header_base[ _og.header + 26] & 0xff;
		// OP_ASSERT( _og.header_len >= 27+nlacing );
		/*This also correctly handles the ( unlikely ) case of nlacing == 0, because
		0 != 255.*/
		return _og.header_base[ _og.header + 27 + nlacing - 1] == -1;// 255;// java: signed byte, so 255 = -1
	}

	/*A small helper to buffer the continued packet data from a page.*/
	private final void op_buffer_continued_data( final Jogg_page _og ) {
		this.os.ogg_stream_pagein( _og );
		/*Drain any packets that did end on this page ( and ignore holes ).
		We only care about the continued packet data.*/
		final Jogg_packet p = new Jogg_packet();
		while( this.os.ogg_stream_packetout( p ) != 0 ) {
			;
		}
	}

	/** This controls how close the target has to be to use the current stream
	position to subdivide the initial range.
	Two minutes seems to be a good default. */
	private static final int OP_CUR_TIME_THRESH = 120 * 48 * 1000;

	/*Note: The OP_SMALL_FOOTPRINT #define doesn't ( currently ) save much code size,
	but it's meant to serve as documentation for portions of the seeking
	algorithm that are purely optional, to aid others learning from/porting this
	code to other contexts.*/
	/*#define OP_SMALL_FOOTPRINT ( 1 )*/

	/** Search within link _li for the page with the highest granule position
	preceding ( or equal to ) _target_gp.
	There is a danger here: missing pages or incorrect frame number information
	in the bitstream could make our task impossible.
	Account for that ( and report it as an error condition ). */
	private final int op_pcm_seek_page( long _target_gp, final int _li ) {
		final long[] request = new long[1];// java helper
		this.bytes_tracked = 0;
		this.samples_tracked = 0;
		final JOggOpusLink link = this.links[_li];
		long pcm_start, best_gp;
		best_gp = pcm_start = link.pcm_start;
		long pcm_end = link.pcm_end;
		final int serialno = link.serialno;
		long begin, best, best_start;
		best = best_start = begin = link.data_offset;
		long page_offset = -1;
		boolean buffering = false;
		/*We discard the first 80 ms of data after a seek, so seek back that much
		farther.
		If we can't, simply seek to the beginning of the link.*/
		int ret = op_granpos_add( request /* &_target_gp */, _target_gp, -80 * 48 );// java
		_target_gp = request[0];// java
		if( ret < 0 || op_granpos_cmp( _target_gp, pcm_start ) < 0 ) {
			_target_gp = pcm_start;
		}
		/*Special case seeking to the start of the link.*/
		final int pre_skip = link.head.pre_skip;
		// OP_ALWAYS_TRUE( !op_granpos_add( &pcm_pre_skip, pcm_start, pre_skip ) );
		op_granpos_add( request /* &pcm_pre_skip */, pcm_start, pre_skip );
		final long pcm_pre_skip = request[0];// java
		long size, boundary;
		if( op_granpos_cmp( _target_gp, pcm_pre_skip ) < 0 ) {
			size = boundary = begin;
		} else {
			size = boundary = link.end_offset;
if( ! OP_SMALL_FOOTPRINT ) {
			/*If we were decoding from this link, we can narrow the range a bit.*/
			if( _li == this.cur_link && this.ready_state >= OP_INITSET ) {
				final int pockets_count = this.op_count;
				/*The only way the offset can be invalid _and_ we can fail the granule
				position checks below is if someone changed the contents of the last
				page since we read it.
				We'd be within our rights to just return OP_EBADLINK in that case, but
				we'll simply ignore the current position instead.*/
				final long curr_offset = this.offset;
				if( pockets_count > 0 && curr_offset <= size ) {
					/*Make sure the timestamp is valid.
					The granule position might be -1 if we collected the packets from a
					page without a granule position after reporting a hole.*/
					final long gp = this.op[pockets_count-1].granulepos;
					if( gp != -1 && op_granpos_cmp( pcm_start, gp ) < 0
							&& op_granpos_cmp( pcm_end, gp ) > 0 ) {
						// OP_ALWAYS_TRUE( !op_granpos_diff( &diff, gp, _target_gp ) );
						op_granpos_diff( request /* &diff */, gp, _target_gp );
						final long diff = request[0];// java
						/*We only actually use the current time if either
						a ) We can cut off at least half the range, or
						b ) We're seeking sufficiently close to the current position that
						it's likely to be informative.
						Otherwise it appears using the whole link range to estimate the
						first seek location gives better results, on average.*/
						if( diff < 0 ) {
							// OP_ASSERT( offset >= begin );
							if( curr_offset - begin >= size - begin >> 1 || diff > -OP_CUR_TIME_THRESH ) {
								best = begin = curr_offset;
								best_gp = pcm_start = gp;
								/*If we have buffered data from a continued packet, remember the
								offset of the previous page's start, so that if we do wind up
								having to seek back here later, we can prime the stream with
								the continued packet data.
								With no continued packet, we remember the end of the page.*/
								best_start = this.os.body_returned < this.os.body_fill ? this.prev_page_offset : best;
								/*If there's completed packets and data in the stream state,
								prev_page_offset should always be set.*/
								// OP_ASSERT( best_start >= 0 );
								/*Buffer any continued packet data starting from here.*/
								buffering = true;
							}
						}
						else {
							/*We might get lucky and already have the packet with the target
							buffered.
							Worth checking.
							For very small files ( with all of the data in a single page,
							generally 1 second or less ), we can loop them continuously
							without seeking at all.*/
							// OP_ALWAYS_TRUE( !op_granpos_add( &prev_page_gp, _of.op[0].granulepos, -op_get_packet_duration( _of.op[0].packet, _of.op[0].bytes ) ) );
							op_granpos_add( request /* &prev_page_gp */, this.op[0].granulepos,
									-op_get_packet_duration( this.op[0].packet_base, this.op[0].packet, this.op[0].bytes ) );
							final long prev_page_gp = request[0];// java
							if( op_granpos_cmp( prev_page_gp, _target_gp ) <= 0 ) {
								/*Don't call op_decode_clear(), because it will dump our
								packets.*/
								this.op_pos = 0;
								this.od_buffer_size = 0;
								this.prev_packet_gp = prev_page_gp;
								/*_of.prev_page_offset already points to the right place.*/
								this.ready_state = OP_STREAMSET;
								return op_make_decode_ready();
							}
							/*No such luck.
							Check if we can cut off at least half the range, though.*/
							if( curr_offset - begin <= size - begin >> 1 || diff < OP_CUR_TIME_THRESH ) {
								/*We really want the page start here, but this will do.*/
								size = boundary = curr_offset;
								pcm_end = gp;
							}
						}
					}
				}
			}
}
		}
		/*This code was originally based on the "new search algorithm by HB ( Nicholas
		Vinen )" from libvorbisfile.
		It has been modified substantially since.*/
		op_decode_clear();
		if( ! buffering ) {
			this.os.ogg_stream_reset_serialno( serialno );
		}
		this.cur_link = _li;
		this.ready_state = OP_STREAMSET;
		/*Initialize the interval size history.*/
		final Jogg_page og = new Jogg_page();
		long d0, d1, d2;
		d2 = d1 = d0 = size - begin;
		boolean force_bisect = false;
		while( begin < size ) {
			long bisect;
			if( size-begin < OP_CHUNK_SIZE ) {
				bisect = begin;
			} else {
				/*Update the interval size history.*/
				d0 = d1 >> 1;
				d1 = d2 >> 1;
				d2 = size - begin >> 1;
				if( force_bisect ) {
					bisect = begin + ( size - begin >> 1 );
				} else{
					// OP_ALWAYS_TRUE( !op_granpos_diff( &diff, _target_gp, pcm_start ) );
					// OP_ALWAYS_TRUE( !op_granpos_diff( &diff2, pcm_end, pcm_start ) );
					op_granpos_diff( request /* &diff */, _target_gp, pcm_start );
					// diff = request[0];// java
					op_granpos_diff( request /* &diff2 */, pcm_end, pcm_start );
					final long diff2 = request[0];// java
					/*Take a ( pretty decent ) guess.*/
					bisect = begin + op_rescale64( request[0] /* diff */, diff2, size - begin ) - OP_CHUNK_SIZE;
				}
				if( bisect - OP_CHUNK_SIZE < begin ) {
					bisect = begin;
				}
				force_bisect = false;
			}
			if( bisect != this.offset ) {
				/*Discard any buffered continued packet data.*/
				if( buffering ) {
					this.os.ogg_stream_reset();
				}
				buffering = false;
				page_offset = -1;
				ret = op_seek_helper( bisect );
				if( ret < 0 ) {
					return ret;
				}
			}
			int chunk_size = OP_CHUNK_SIZE;
			long next_boundary = boundary;
			/*Now scan forward and figure out where we landed.
			In the ideal case, we will see a page with a granule position at or
			before our target, followed by a page with a granule position after our
			target ( or the end of the search interval ).
			Then we can just drop out and will have all of the data we need with no
			additional seeking.
			If we landed too far before, or after, we'll break out and do another
			bisection.*/
			while( begin < size ) {
				page_offset = op_get_next_page( og, boundary );
				if( page_offset < 0 ) {
					if( page_offset < OP_FALSE ) {
						return (int)page_offset;
					}
					/*There are no more pages in our interval from our stream with a valid
					timestamp that start at position bisect or later.*/
					/*If we scanned the whole interval, we're done.*/
					if( bisect <= begin + 1 ) {
						size = begin;
					} else {
						/*Otherwise, back up one chunk.
						First, discard any data from a continued packet.*/
						if( buffering ) {
							this.os.ogg_stream_reset();
						}
						buffering = false;
						bisect -= chunk_size;
						bisect = bisect >= begin ? bisect : begin;
						ret = op_seek_helper( bisect );
						if( ret < 0 ) {
							return ret;
						}
						/*Bump up the chunk size.*/
						chunk_size <<= 1;
						chunk_size = chunk_size <= OP_CHUNK_SIZE_MAX ? chunk_size : OP_CHUNK_SIZE_MAX;
						/*If we did find a page from another stream or without a timestamp,
						don't read past it.*/
						boundary = next_boundary;
					}
				}
				else {
					/*Save the offset of the first page we found after the seek, regardless
					of the stream it came from or whether or not it has a timestamp.*/
					next_boundary = page_offset <= next_boundary ? page_offset : next_boundary;
					if( serialno != og.ogg_page_serialno() ) {
						continue;
					}
					final boolean has_packets = og.ogg_page_packets() > 0;
					/*Force the gp to -1 ( as it should be per spec ) if no packets end on
					this page.
					Otherwise we might get confused when we try to pull out a packet
					with that timestamp and can't find it.*/
					final long gp = has_packets ? og.ogg_page_granulepos() : -1L;
					if( gp == -1 ) {
						if( buffering ) {
							if( ! has_packets ) {
								this.os.ogg_stream_pagein( og );
							} else{
								/*If packets did end on this page, but we still didn't have a
								valid granule position ( in violation of the spec! ), stop
								buffering continued packet data.
								Otherwise we might continue past the packet we actually
								wanted.*/
								this.os.ogg_stream_reset();
								buffering = false;
							}
						}
						continue;
					}
					if( op_granpos_cmp( gp, _target_gp ) < 0 ) {
						/*We found a page that ends before our target.
						Advance to the raw offset of the next page.*/
						begin = this.offset;
						if( op_granpos_cmp( pcm_start, gp ) > 0
								|| op_granpos_cmp( pcm_end, gp ) < 0 ) {
							/*Don't let pcm_start get out of range!
							That could happen with an invalid timestamp.*/
							break;
						}
						/*Save the byte offset of the end of the page with this granule
						position.*/
						best = best_start = begin;
						/*Buffer any data from a continued packet, if necessary.
						This avoids the need to seek back here if the next timestamp we
						encounter while scanning forward lies after our target.*/
						if( buffering ) {
							this.os.ogg_stream_reset();
						}
						if( op_page_continues( og ) ) {
							op_buffer_continued_data( og );
							/*If we have a continued packet, remember the offset of this
							page's start, so that if we do wind up having to seek back here
							later, we can prime the stream with the continued packet data.
							With no continued packet, we remember the end of the page.*/
							best_start = page_offset;
						}
						/*Then force buffering on, so that if a packet starts ( but does not
						end ) on the next page, we still avoid the extra seek back.*/
						buffering = true;
						best_gp = pcm_start = gp;
						// OP_ALWAYS_TRUE( !op_granpos_diff( &diff, _target_gp, pcm_start ) );
						op_granpos_diff( request /* &diff */, _target_gp, pcm_start );
						// diff = request[0];// java
						/*If we're more than a second away from our target, break out and
						do another bisection.*/
						if( request[0] /* diff */ > 48000 ) {
							break;
						}
						/*Otherwise, keep scanning forward ( do NOT use begin+1 ).*/
						bisect = begin;
					}
					else {
						/*We found a page that ends after our target.*/
						/*If we scanned the whole interval before we found it, we're done.*/
						if( bisect <= begin + 1 ) {
							size = begin;
						} else {
							size = bisect;
							/*In later iterations, don't read past the first page we found.*/
							boundary = next_boundary;
							/*If we're not making much progress shrinking the interval size,
							start forcing straight bisection to limit the worst case.*/
							force_bisect = size - begin > d0 * 2;
							/*Don't let pcm_end get out of range!
							That could happen with an invalid timestamp.*/
							if( op_granpos_cmp( pcm_end, gp ) > 0
									&& op_granpos_cmp( pcm_start, gp ) <= 0 ) {
								pcm_end = gp;
							}
							break;
						}
					}
				}
			}
		}
		/*Found our page.*/
		// OP_ASSERT( op_granpos_cmp( best_gp, pcm_start ) >= 0 );
		/*Seek, if necessary.
		If we were buffering data from a continued packet, we should be able to
		continue to scan forward to get the rest of the data ( even if
		page_offset == -1 ).
		Otherwise, we need to seek back to best_start.*/
		if( ! buffering ) {
			if( best_start != page_offset ) {
				page_offset = -1;
				ret = op_seek_helper( best_start );
				if( ret < 0 ) {
					return ret;
				}
			}
			if( best_start < best ) {
				/*Retrieve the page at best_start, if we do not already have it.*/
				if( page_offset < 0 ) {
					page_offset = op_get_next_page( og, link.end_offset );
					if( page_offset < OP_FALSE ) {
						return (int)page_offset;
					}
					if( page_offset != best_start ) {
						return OP_EBADLINK;
					}
				}
				op_buffer_continued_data( og );
				page_offset = -1;
			}
		}
		/*Update prev_packet_gp to allow per-packet granule position assignment.*/
		this.prev_packet_gp = best_gp;
		this.prev_page_offset = best_start;
		ret = op_fetch_and_process_page( page_offset < 0 ? null : og, page_offset, false, true );
		if( ret < 0 ) {
			return OP_EBADLINK;
		}
		/*Verify result.*/
		if( op_granpos_cmp( this.prev_packet_gp, _target_gp ) > 0 ) {
			return OP_EBADLINK;
		}
		/*Our caller will set cur_discard_count to handle pre-roll.*/
		return 0;
	}

	/** Seek to the specified PCM offset, such that decoding will begin at exactly
	    the requested position.
	   @param _of         The \c OggOpusFile in which to seek.
	   @param _pcm_offset The PCM offset to seek to.
	                      This is in samples at 48 kHz relative to the start of the
	                       stream.
	   @return 0 on success, or a negative value on error.
	   @retval #OP_EREAD    An underlying read or seek operation failed.
	   @retval #OP_EINVAL   The stream was only partially open, or the target was
	                         outside the valid range for the stream.
	   @retval #OP_ENOSEEK  This stream is not seekable.
	   @retval #OP_EBADLINK We failed to find data we had seen before, or the
	                         bitstream structure was sufficiently malformed that
	                         seeking to the target destination was impossible. */
	public final int op_pcm_seek( long _pcm_offset ) {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		if( ! this.seekable ) {
			return OP_ENOSEEK;
		}
		if( _pcm_offset < 0 ) {
			return OP_EINVAL;
		}
		final int[] irequest = new int[1];// java helper
		final long target_gp = op_get_granulepos( _pcm_offset, irequest /* &li */ );
		if( target_gp == -1L ) {
			return OP_EINVAL;
		}
		final int li = irequest[0];// java
		final JOggOpusLink link = this.links[li];
		final long pcm_start = link.pcm_start;
		// OP_ALWAYS_TRUE( !op_granpos_diff( &_pcm_offset, target_gp, pcm_start ) );
		final long[] request = new long[1];// java
		op_granpos_diff( request /* &_pcm_offset */, target_gp, pcm_start );
		_pcm_offset = request[0];// java
if( ! OP_SMALL_FOOTPRINT ) {
		/*For small ( 90 ms or less ) forward seeks within the same link, just decode
		forward.
		This also optimizes the case of seeking to the current position.*/
		if( li == this.cur_link && this.ready_state >= OP_INITSET ) {
			final long gp = this.prev_packet_gp;
			if( gp != -1 ) {
				int nbuffered = this.od_buffer_size - this.od_buffer_pos;
				nbuffered = nbuffered >= 0 ? nbuffered : 0;
				// OP_ALWAYS_TRUE( !op_granpos_add( &gp, gp, -nbuffered ) );
				op_granpos_add( request /* &gp */, gp, -nbuffered );
				// gp = request[0];// java
				/*We do _not_ add cur_discard_count to gp.
				Otherwise the total amount to discard could grow without bound, and it
				would be better just to do a full seek.*/
				if( 0 == op_granpos_diff( request /* &diff */, target_gp, request[0] /* gp */) ) {
					final long discard_count = request[0] /* diff */;
					/*We use a threshold of 90 ms instead of 80, since 80 ms is the
					_minimum_ we would have discarded after a full seek.
					Assuming 20 ms frames ( the default ), we'd discard 90 ms on average.*/
					if( discard_count >= 0 && ( discard_count < 90 * 48 ) ) {
						this.cur_discard_count = (int)discard_count;
						return 0;
					}
				}
			}
		}
}
		int ret = op_pcm_seek_page( target_gp, li );
		if( ret < 0 ) {
			return ret;
		}
		/*Now skip samples until we actually get to our target.*/
		/*Figure out where we should skip to.*/
		long skip;
		if( _pcm_offset <= link.head.pre_skip ) {
			skip = 0;
		} else {
			skip = _pcm_offset - 80 * 48;
			skip = skip >= 0 ? skip : 0;
		}
		// OP_ASSERT( _pcm_offset - skip >= 0 );
		// OP_ASSERT( _pcm_offset - skip < OP_INT32_MAX - 120 * 48 );
		/*Skip packets until we find one with samples past our skip target.*/
		long prev_packet_gpos;
		for( ; ; ) {
			final int pocket_count = this.op_count;
			prev_packet_gpos = this.prev_packet_gp;
			int pos;
			for( pos = this.op_pos; pos < pocket_count; pos++ ) {
				final long cur_packet_gp = this.op[pos].granulepos;
				if( 0 == op_granpos_diff( request /* &diff */, cur_packet_gp, pcm_start ) && request[0] /* diff */ > skip ) {
					break;
				}
				prev_packet_gpos = cur_packet_gp;
			}
			this.prev_packet_gp = prev_packet_gpos;
			this.op_pos = pos;
			if( pos < pocket_count ) {
				break;
			}
			/*We skipped all the packets on this page.
			Fetch another.*/
			ret = op_fetch_and_process_page( null, -1, false, true );
			if( ret < 0 ) {
				return OP_EBADLINK;
			}
		}
		// OP_ALWAYS_TRUE( !op_granpos_diff( &diff, prev_packet_gp, pcm_start ) );
		op_granpos_diff( request /* &diff */, prev_packet_gpos, pcm_start );
		final long diff = request[0];// java
		/*We skipped too far.
		Either the timestamps were illegal or there was a hole in the data.*/
		if( diff > skip ) {
			return OP_EBADLINK;
		}
		// OP_ASSERT( _pcm_offset-diff < OP_INT32_MAX );
		/*TODO: If there are further holes/illegal timestamps, we still won't decode
		to the correct sample.
		However, at least op_pcm_tell() will report the correct value immediately
		after returning.*/
		this.cur_discard_count = (int)( _pcm_offset - diff );
		return 0;
	}

	/** Obtain the current value of the position indicator for \a _of.
	   @param _of The \c OggOpusFile from which to retrieve the position indicator.
	   @return The byte position that is currently being read from.
	   @retval #OP_EINVAL The stream was only partially open. */
	public final long op_raw_tell() {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		return this.offset;
	}

	/** Convert a granule position from a given link to a PCM offset relative to the
	     start of the whole stream.
	  For unseekable sources, this gets reset to 0 at the beginning of each link. */
	private final long op_get_pcm_offset( long _gp, final int _li ) {
		final long[] request = new long[1];// java helper
		final JOggOpusLink olinks[] = this.links;
		// OP_ASSERT( _li >= 0 && _li < _of.nlinks );
		long pcm_offset = olinks[_li].pcm_file_offset;
		if( this.seekable && op_granpos_cmp( _gp, olinks[_li].pcm_end ) > 0 ) {
			_gp = olinks[_li].pcm_end;
		}
		if( op_granpos_cmp( _gp, olinks[_li].pcm_start ) > 0 ) {
			if( op_granpos_diff( request /* &delta */, _gp, olinks[_li].pcm_start ) < 0 ) {
				/*This means an unseekable stream claimed to have a page from more than
				2 billion days after we joined.*/
				// OP_ASSERT( !_of.seekable );
				return Long.MAX_VALUE;
			}
			long delta = request[0];// java
			/* java changed
			if( delta < links[_li].head.pre_skip ) {
				delta = 0;
			} else {
				delta -= links[_li].head.pre_skip;
			}
			*/
			delta -= olinks[_li].head.pre_skip;
			if( delta < 0 ) {
				delta = 0;
			}
			/*In the seekable case, _gp was limited by pcm_end.
			In the unseekable case, pcm_offset should be 0.*/
			// OP_ASSERT( pcm_offset <= OP_INT64_MAX - delta );
			pcm_offset += delta;
		}
		return pcm_offset;
	}

	/** Obtain the PCM offset of the next sample to be read.
	   If the stream is not properly timestamped, this might not increment by the
	    proper amount between reads, or even return monotonically increasing
	    values.
	   @param _of The \c OggOpusFile from which to retrieve the PCM offset.
	   @return The PCM offset of the next sample to be read.
	   @retval #OP_EINVAL The stream was only partially open. */
	public final long op_pcm_tell() {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		final long gp = this.prev_packet_gp;
		if( gp == -1 ) {
			return 0;
		}
		int nbuffered = this.od_buffer_size - this.od_buffer_pos;
		nbuffered = ( nbuffered >= 0 ? nbuffered : 0 );
		// OP_ALWAYS_TRUE( !op_granpos_add( &gp, gp, -nbuffered ) );
		final long[] request = { gp };// java
		op_granpos_add( request /* &gp */, gp, -nbuffered );
		final int li = this.seekable ? this.cur_link : 0;
		if( op_granpos_add( request /* &gp */, request[0] /* gp */, this.cur_discard_count ) < 0 ) {
			// gp = _of.links[li].pcm_end;
			return op_get_pcm_offset( this.links[li].pcm_end /* gp */, li );// java changed
		}
		return op_get_pcm_offset( request[0] /* gp */, li );
	}

	/** Sets the packet decode callback function.
	   If set, this is called once for each packet that needs to be decoded.
	   This can be used by advanced applications to do additional processing on the
	    compressed or uncompressed data.
	   For example, an application might save the final entropy coder state for
	    debugging and testing purposes, or it might apply additional filters
	    before the downmixing, dithering, or soft-clipping performed by
	    <tt>libopusfile</tt>, so long as these filters do not introduce any
	    latency.

	   A call to this function is no guarantee that the audio will eventually be
	    delivered to the application.
	   <tt>libopusfile</tt> may discard some or all of the decoded audio data
	    (i.e., at the beginning or end of a link, or after a seek), however the
	    callback is still required to provide all of it.
	   @param _of        The \c OggOpusFile on which to set the decode callback.
	   @param _decode_cb The callback function to call.
	                     This may be <code>NULL</code> to disable calling the
	                      callback.
	   @param _ctx       The application-provided context pointer to pass to the
	                      callback on each call. */
	public final void op_set_decode_callback(final Jop_decode_cb_func _decode_cb, final Object _ctx) {
		this.decode_cb = _decode_cb;
		this.decode_cb_ctx = _ctx;
	}

	/** Sets the gain to be used for decoded output.
	   By default, the gain in the header is applied with no additional offset.
	   The total gain (including header gain and/or track gain, if applicable, and
	    this offset), will be clamped to [-32768,32767]/256 dB.
	   This is more than enough to saturate or underflow 16-bit PCM.
	   @note The new gain will not be applied to any already buffered, decoded
	    output.
	   This means you cannot change it sample-by-sample, as at best it will be
	    updated packet-by-packet.
	   It is meant for setting a target volume level, rather than applying smooth
	    fades, etc.
	   @param _of             The \c OggOpusFile on which to set the gain offset.
	   @param _gain_type      One of #OP_HEADER_GAIN, #OP_ALBUM_GAIN,
	                           #OP_TRACK_GAIN, or #OP_ABSOLUTE_GAIN.
	   @param _gain_offset_q8 The gain offset to apply, in 1/256ths of a dB.
	   @return 0 on success or a negative value on error.
	   @retval #OP_EINVAL The \a _gain_type was unrecognized. */
	public final int op_set_gain_offset( final int _gain_type, final int _gain_offset_q8 ) {
		if( _gain_type != OP_HEADER_GAIN && _gain_type != OP_ALBUM_GAIN
				&& _gain_type != OP_TRACK_GAIN && _gain_type != OP_ABSOLUTE_GAIN ) {
			return OP_EINVAL;
		}
		this.gain_type = _gain_type;
		/*The sum of header gain and track gain lies in the range [-65536,65534].
		These bounds allow the offset to set the final value to anywhere in the
		range [-32768,32767], which is what we'll clamp it to before applying.*/
		this.gain_offset_q8 = OP_CLAMP( -98302, _gain_offset_q8, 98303 );
		op_update_gain();
		return 0;
	}

	/** Sets whether or not dithering is enabled for 16-bit decoding.
	   By default, when <tt>libopusfile</tt> is compiled to use floating-point
	    internally, calling op_read() or op_read_stereo() will first decode to
	    float, and then convert to fixed-point using noise-shaping dithering.
	   This flag can be used to disable that dithering.
	   When the application uses op_read_float() or op_read_float_stereo(), or when
	    the library has been compiled to decode directly to fixed point, this flag
	    has no effect.
	   @param _of      The \c OggOpusFile on which to enable or disable dithering.
	   @param _enabled A non-zero value to enable dithering, or 0 to disable it. */
	public final void op_set_dither_enabled( final boolean _enabled ) {
// if( ! OP_FIXED_POINT ) {
		this.dither_disabled = ! _enabled;
		if( ! _enabled ) {
			this.dither_mute = 65;
		}
//}
	}

	/** Allocate the decoder scratch buffer.
	This is done lazily, since if the user provides large enough buffers, we'll
	never need it. */
	private final int op_init_buffer() {
		int nchannels_max;
		if( this.seekable ) {
			final JOggOpusLink olinks[] = this.links;// java renamed
			final int n_links = this.nlinks;
			nchannels_max = 1;
			for( int li = 0; li < n_links; li++ ) {
				final int v = olinks[li].head.channel_count;// java
				nchannels_max = nchannels_max >= v ? nchannels_max : v;
			}
		} else {
			nchannels_max = OP_NCHANNELS_MAX;
		}
		this.od_buffer = new float[ nchannels_max * 120 * 48 ];
		if( this.od_buffer == null ) {
			return OP_EFAULT;
		}
		return 0;
	}

	/** Decode a single packet into the target buffer. */
	private final int op_decode( final float[] _pcm, final int poffset, final Jogg_packet _op, final int _nsamples, final int _nchannels ) {
		int ret;
		/*First we try using the application-provided decode callback.*/
		if( this.decode_cb != null ) {
/* if( OP_FIXED_POINT ) {
			ret = _of.decode_cb.op_decode_cb_func( _of.decode_cb_ctx, _of.od, _pcm, poffset, _op,
					_nsamples, _nchannels, OP_DEC_FORMAT_SHORT, _of.cur_link );
} else { */
			ret = this.decode_cb.op_decode_cb_func( this.decode_cb_ctx, this.od, _pcm, poffset, _op,
					_nsamples, _nchannels, OP_DEC_FORMAT_FLOAT, this.cur_link );
// }
		} else {
			ret = OP_DEC_USE_DEFAULT;
		}
		/*If the application didn't want to handle decoding, do it ourselves.*/
		if( ret == OP_DEC_USE_DEFAULT ) {
/* if( OP_FIXED_POINT ) {
			ret = JOpusMSDecoder.opus_multistream_decode( _of.od,
					_op.packet_base, _op.packet, _op.bytes, _pcm, poffset, _nsamples, false );
} else { */
			ret = this.od.opus_multistream_decode_float( _op.packet_base, _op.packet, _op.bytes, _pcm, poffset, _nsamples, false );
// }
		}
		// OP_ASSERT( ret < 0 || ret == _nsamples );
		/*If the application returned a positive value other than 0 or
		OP_DEC_USE_DEFAULT, fail.*/
		else if( ret > 0 ) {
			return OP_EBADPACKET;
		}
		if( ret < 0 ) {
			return OP_EBADPACKET;
		}
		return ret;
	}

	/** Read more samples from the stream, using the same API as op_read() or
	op_read_float(). */
	public final int op_read_native(final float[] _pcm, final int poffset, final int _buf_size, final int[] _li) {
		if( this.ready_state < OP_OPENED ) {
			return OP_EINVAL;
		}
		final long[] request = new long[1];// java
		for( ; ; ) {
			if( this.ready_state >= OP_INITSET ) {
				final int nchannels = this.links[this.seekable ? this.cur_link : 0].head.channel_count;
				int buffer_pos = this.od_buffer_pos;// java renamed
				int nsamples = this.od_buffer_size - buffer_pos;
				/*If we have buffered samples, return them.*/
				if( nsamples > 0 ) {
					if( nsamples * nchannels > _buf_size ) {
						nsamples = _buf_size / nchannels;
					}
					// FIXME _pcm may be null! if calling from op_filter_read_native, _pcm = null!
					// memcpy( _pcm, _of.od_buffer + nchannels * od_buffer_pos, sizeof( *_pcm ) * nchannels * nsamples );
					if( _pcm != null ) {
						System.arraycopy( this.od_buffer, nchannels * buffer_pos, _pcm, poffset, nchannels * nsamples );
					}
					buffer_pos += nsamples;
					this.od_buffer_pos = buffer_pos;
					if( _li != null ) {
						_li[0] = this.cur_link;
					}
					return nsamples;
				}
				/*If we have buffered packets, decode one.*/
				int pos = this.op_pos;// java renamed
				if( pos < this.op_count ) {
					final Jogg_packet pop = this.op[pos++];
					this.op_pos = pos;
					int curr_discard_count = this.cur_discard_count;
					final int duration = op_get_packet_duration( pop.packet_base, pop.packet, pop.bytes );
					/*We don't buffer packets with an invalid TOC sequence.*/
					// OP_ASSERT( duration > 0 );
					int trimmed_duration = duration;
					/*Perform end-trimming.*/
					if( pop.e_o_s ) {
						// final long diff;
						if( op_granpos_cmp( pop.granulepos, this.prev_packet_gp ) <= 0 ) {
							trimmed_duration = 0;
						}
						else if( 0 == op_granpos_diff( request /* &diff */, pop.granulepos, this.prev_packet_gp ) ) {
							trimmed_duration = (int)( request[0] /* diff */ <= trimmed_duration ? request[0] /* diff */ : trimmed_duration );
						}
					}
					this.prev_packet_gp = pop.granulepos;
					if( duration * nchannels > _buf_size ) {
						/*If the user's buffer is too small, decode into a scratch buffer.*/
						float[] buf = this.od_buffer;
						if( buf == null ) {
							final int ret = op_init_buffer();
							if( ret < 0 ) {
								return ret;
							}
							buf = this.od_buffer;
						}
						final int ret = op_decode( buf, 0, pop, duration, nchannels );
						if( ret < 0 ) {
							return ret;
						}
						/*Perform pre-skip/pre-roll.*/
						buffer_pos = (int)( trimmed_duration <= curr_discard_count ? trimmed_duration : curr_discard_count );
						curr_discard_count -= buffer_pos;
						this.cur_discard_count = curr_discard_count;
						this.od_buffer_pos = buffer_pos;
						this.od_buffer_size = trimmed_duration;
						/*Update bitrate tracking based on the actual samples we used from
						what was decoded.*/
						this.bytes_tracked += pop.bytes;
						this.samples_tracked += trimmed_duration - buffer_pos;
					}
					else {
						/*Otherwise decode directly into the user's buffer.*/
						final int ret = op_decode( _pcm, poffset, pop, duration, nchannels );
						if( ret < 0 ) {
							return ret;
						}
						if( trimmed_duration > 0 ) {
							/*Perform pre-skip/pre-roll.*/
							buffer_pos = (int)( trimmed_duration <= curr_discard_count ? trimmed_duration : curr_discard_count );
							curr_discard_count -= buffer_pos;
							this.cur_discard_count = curr_discard_count;
							trimmed_duration -= buffer_pos;
							if( trimmed_duration > 0 && buffer_pos > 0 ) {
								System.arraycopy( _pcm, poffset + buffer_pos * nchannels, _pcm, poffset, trimmed_duration * nchannels );
							}
							/*Update bitrate tracking based on the actual samples we used from
							what was decoded.*/
							this.bytes_tracked += pop.bytes;
							this.samples_tracked += trimmed_duration;
							if( trimmed_duration > 0 ) {
								if( _li != null ) {
									_li[0] = this.cur_link;
								}
								return trimmed_duration;
							}
						}
					}
					/*Don't grab another page yet.
					This one might have more packets, or might have buffered data now.*/
					continue;
				}
			}
			/*Suck in another page.*/
			final int ret = op_fetch_and_process_page( null, -1, true, false );
			if( ret == OP_EOF ) {
				if( _li != null ) {
					_li[0] = this.cur_link;
				}
				return 0;
			}
			if( ret < 0 ) {
				return ret;
			}
		}
	}

	/** A generic filter to apply to the decoded audio data.
	_src is non-const because we will destructively modify the contents of the
	source buffer that we consume in some cases. */
	private interface Jop_read_filter_func {
		int op_read_filter_func( JOggOpusFile _of, Object _dst, int doffset, int _dst_sz, float[] _src, int soffset, int _nsamples, int _nchannels );
	}

	/** Decode some samples and then apply a custom filter to them.
	This is used to convert to different output formats. */
	private final int op_filter_read_native(final Object _dst, final int _dst_sz, final Jop_read_filter_func _filter, final int[] _li) {
		/*Ensure we have some decoded samples in our buffer.*/
		int ret = op_read_native( null, 0, 0, _li );
		/*Now apply the filter to them.*/
		if( ret >= 0 && this.ready_state >= OP_INITSET ) {
			int buffer_pos = this.od_buffer_pos;// java renamed
			ret = this.od_buffer_size - buffer_pos;
			if( ret > 0 ) {
				final int nchannels = this.links[this.seekable ? this.cur_link : 0].head.channel_count;
				ret = _filter.op_read_filter_func( this, _dst, 0, _dst_sz, this.od_buffer, nchannels * buffer_pos, ret, nchannels );
				// OP_ASSERT( ret >= 0 );
				// OP_ASSERT( ret <= _of.od_buffer_size - od_buffer_pos );
				buffer_pos += ret;
				this.od_buffer_pos = buffer_pos;
			}
		}
		return ret;
	}

// #if !defined( OP_FIXED_POINT ) || !defined( OP_DISABLE_FLOAT_API )

	/** Matrices for downmixing from the supported channel counts to stereo.
	The matrices with 5 or more channels are normalized to a total volume of 2.0,
	since most mixes sound too quiet if normalized to 1.0 ( as there is generally
	little volume in the side/rear channels ). */
	private static final float OP_STEREO_DOWNMIX[/* OP_NCHANNELS_MAX - 2 */][/* OP_NCHANNELS_MAX */][/* 2 */] = {
		/*3.0*/
		{
			{0.5858F, 0.0F}, {0.4142F, 0.4142F}, {0.0F, 0.5858F}
		},
		/*quadrophonic*/
		{
			{0.4226F, 0.0F}, {0.0F, 0.4226F}, {0.366F, 0.2114F}, {0.2114F, 0.336F}
		},
		/*5.0*/
		{
			{0.651F, 0.0F}, {0.46F, 0.46F}, {0.0F, 0.651F}, {0.5636F, 0.3254F},
			{0.3254F, 0.5636F}
		},
		/*5.1*/
		{
			{0.529F, 0.0F}, {0.3741F, 0.3741F}, {0.0F, 0.529F}, {0.4582F, 0.2645F},
			{0.2645F, 0.4582F}, {0.3741F, 0.3741F}
		},
		/*6.1*/
		{
			{0.4553F, 0.0F}, {0.322F, 0.322F}, {0.0F, 0.4553F}, {0.3943F, 0.2277F},
			{0.2277F, 0.3943F}, {0.2788F, 0.2788F}, {0.322F, 0.322F}
		},
		/*7.1*/
		{
			{0.3886F, 0.0F}, {0.2748F, 0.2748F}, {0.0F, 0.3886F}, {0.3366F, 0.1943F},
			{0.1943F, 0.3366F}, {0.3366F, 0.1943F}, {0.1943F, 0.3366F}, {0.2748F, 0.2748F}
		}
	};

// #endif

// #if defined( OP_FIXED_POINT )

	/** Matrices for downmixing from the supported channel counts to stereo.
	The matrices with 5 or more channels are normalized to a total volume of 2.0,
	since most mixes sound too quiet if normalized to 1.0 ( as there is generally
	little volume in the side/rear channels ).
	Hence we keep the coefficients in Q14, so the downmix values won't overflow a
	32-bit number. */
/*	private static final short OP_STEREO_DOWNMIX_Q14[][][] = {// [OP_NCHANNELS_MAX - 2][OP_NCHANNELS_MAX][2] = {
		// 3.0
		{
			{9598, 0}, {6786, 6786}, {0, 9598}
		},
		// quadrophonic
		{
			{6924, 0}, {0, 6924}, {5996, 3464}, {3464, 5996}
		},
		// 5.0
		{
			{10666, 0}, {7537, 7537}, {0, 10666}, {9234, 5331}, {5331, 9234}
		},
		// 5.1
		{
			{8668, 0}, {6129, 6129}, {0, 8668}, {7507, 4335}, {4335, 7507}, {6129, 6129}
		},
		// 6.1
		{
			{7459, 0}, {5275, 5275}, {0, 7459}, {6460, 3731}, {3731, 6460}, {4568, 4568},
			{5275, 5275}
		},
		// 7.1
		{
			{6368, 0}, {4502, 4502}, {0, 6368}, {5515, 3183}, {3183, 5515}, {5515, 3183},
			{3183, 5515}, {4502, 4502}
		}
	};

	private static final int op_read( final JOggOpusFile _of, final short[] _pcm, final int _buf_size, final int[] _li ) {
		return op_read_native( _of, _pcm, _buf_size, _li );
	}

	private static final int op_stereo_filter( final JOggOpusFile _of, final Object _dst, final int _dst_sz, final float[] _src, int _nsamples, final int _nchannels ) {
		// (void)_of;
		_nsamples = OP_MIN( _nsamples,_dst_sz >> 1 );
		if( _nchannels == 2 ) {
			memcpy( _dst, _src, _nsamples * 2 * sizeof( *_src ) );
		} else{
			final short[] dst = (short[])_dst;
			if( _nchannels == 1 ) {
				for( int i = 0; i < _nsamples; i++ ) {
					dst[2 * i + 0] = dst[2 * i + 1] = _src[i];
				}
			}
			else{
				for( i = 0; i < _nsamples; i++ ) {
					int l, r;
					l = r = 0;
					for( int ci = 0; ci < _nchannels; ci++ ) {
						final int s = _src[_nchannels * i + ci];
						l += OP_STEREO_DOWNMIX_Q14[_nchannels - 3][ci][0] * s;
						r += OP_STEREO_DOWNMIX_Q14[_nchannels - 3][ci][1] * s;
					}
					// TODO: For 5 or more channels, we should do soft clipping here.
					dst[2 * i + 0] = (short)OP_CLAMP( -32768, l + 8192 >> 14, 32767 );
					dst[2 * i + 1] = (short)OP_CLAMP( -32768, r + 8192 >> 14, 32767 );
				}
			}
		}
		return _nsamples;
	}

	private static final int op_read_stereo( final JOggOpusFile _of, final short[] _pcm, final int _buf_size ) {
		return op_filter_read_native( _of, _pcm, _buf_size, op_stereo_filter, null );
	}
*/
/* # if !defined( OP_DISABLE_FLOAT_API )

	private static final int op_short2float_filter( final JOggOpusFile _of, final Object _dst, int _dst_sz, final float[] _src, int _nsamples, final int _nchannels ) {
		// (void)_of;
		final float[] dst = (float[])_dst;
		if( _nsamples * _nchannels > _dst_sz ) {
			_nsamples = _dst_sz / _nchannels;
		}
		_dst_sz = _nsamples * _nchannels;
		for( int i = 0; i < _dst_sz; i++ ) {
			dst[i] = (1.0F / 32768) * _src[i];
		}
		return _nsamples;
	}

	private static final int op_read_float( final JOggOpusFile _of, final float[] _pcm, final int _buf_size, final int[] _li ) {
		return op_filter_read_native( _of, _pcm, _buf_size, op_short2float_filter, _li );
	}

	private static final int op_short2float_stereo_filter( final JOggOpusFile _of,
			final Object _dst, final int _dst_sz, final float[] _src, int _nsamples, final int _nchannels ) {
		int    i;
		final float[] dst = (float[])_dst;
		_nsamples = OP_MIN( _nsamples,_dst_sz >> 1 );
		if( _nchannels == 1 ) {
			_nsamples = op_short2float_filter( _of, dst, _nsamples, _src, _nsamples, 1 );
			for( i = _nsamples; i - .0; ) {
				dst[2 * i + 0] = dst[2 * i + 1] = dst[i];
			}
		}
		else if( _nchannels < 5 ) {
			// For 3 or 4 channels, we can downmix in fixed point without risk of clipping.
			if( _nchannels > 2 ) {
				_nsamples = op_stereo_filter( _of, _src, _nsamples << 1, _src, _nsamples, _nchannels );
			}
			return op_short2float_filter( _of, dst, _dst_sz, _src, _nsamples, 2 );
		}
		else{
			// For 5 or more channels, we convert to floats and then downmix ( so that we
			// don't risk clipping ).
			for( i = 0; i < _nsamples; i++ ) {
				float l, r;
				l = r = 0;
				for( int ci = 0; ci < _nchannels; ci++ ) {
					final float s = (1.0F / 32768) * _src[_nchannels * i + ci];
					l += OP_STEREO_DOWNMIX[_nchannels - 3][ci][0] * s;
					r += OP_STEREO_DOWNMIX[_nchannels - 3][ci][1] * s;
				}
				dst[2 * i + 0] = l;
				dst[2 * i + 1] = r;
			}
		}
		return _nsamples;
	}

	private static final int op_read_float_stereo( final JOggOpusFile _of, final float[] _pcm, final int _buf_size ) {
		return op_filter_read_native( _of, _pcm, _buf_size, op_short2float_stereo_filter, null );
	}

// # endif
*/
// #else

	private static final int op_float2int(final float _x) {
		return (int)(_x + ( _x < 0 ? -0.5F : 0.5F));
	}

	/** The dithering code here is adapted from opusdec, part of opus-tools.
	It was originally written by Greg Maxwell. */
	private static final int op_rand( final int _seed ) {
		return _seed * 96314165 + 907633515;// & 0xFFFFFFFFU;// FIXME why need & 0xFFFFFFFFU for uint32?
	}

	/*This implements 16-bit quantization with full triangular dither and IIR noise
	shaping.
	The noise shaping filters were designed by Sebastian Gesemann, and are based
	on the LAME ATH curves with flattening to limit their peak gain to 20 dB.
	Everyone else's noise shaping filters are mildly crazy.
	The 48 kHz version of this filter is just a warped version of the 44.1 kHz
	filter and probably could be improved by shifting the HF shelf up in
	frequency a little bit, since 48 kHz has a bit more room and being more
	conservative against bat-ears is probably more important than more noise
	suppression.
	This process can increase the peak level of the signal ( in theory by the peak
	error of 1.5 +20 dB, though that is unobservably rare ).
	To avoid clipping, the signal is attenuated by a couple thousandths of a dB.
	Initially, the approach taken here was to only attenuate by the 99.9th
	percentile, making clipping rare but not impossible ( like SoX ), but the
	limited gain of the filter means that the worst case was only two
	thousandths of a dB more, so this just uses the worst case.
	The attenuation is probably also helpful to prevent clipping in the DAC
	reconstruction filters or downstream resampling, in any case.*/

	private static final float OP_GAIN = 32753.0F;

	private static final float OP_PRNG_GAIN = ( 1.0F / 0xFFFFFFFFL );

	/*48 kHz noise shaping filter, sd = 2.34.*/

	private static final float OP_FCOEF_B[/* 4 */] = {
		2.2374F, -0.7339F, -0.1251F, -0.6033F
	};

	private static final float OP_FCOEF_A[/* 4 */] = {
		0.9030F, 0.0116F, -0.5853F, -0.2571F
	};

	private static final class Jop_float2short_filter implements Jop_read_filter_func {

		@Override
		public int op_read_filter_func( final JOggOpusFile _of, final Object _dst, final int doffset, final int _dst_sz, final float[] _src, final int soffset, int _nsamples, final int _nchannels ) {
			int         ci;
			final short[] dst = (short[])_dst;
			if( _nsamples * _nchannels > _dst_sz ) {
				_nsamples = _dst_sz / _nchannels;
			}
if( OP_SOFT_CLIP ) {
			if( _of.state_channel_count != _nchannels ) {
				for( ci = 0; ci < _nchannels; ci++ ) {
					_of.clip_state[ci] = 0;
				}
			}
			Jopus.opus_pcm_soft_clip( _src, soffset, _nsamples, _nchannels, _of.clip_state );
}
			if( _of.dither_disabled ) {
				for( int i = 0, ie = _nchannels * _nsamples; i < ie; i++ ) {
					dst[doffset + i] = (short)op_float2int( OP_CLAMP( -32768f, 32768.0F * _src[soffset + i], 32767f ) );
				}
			}
			else {
				int seed = _of.dither_seed;
				int mute = _of.dither_mute;
				if( _of.state_channel_count != _nchannels ) {
					mute = 65;
				}
				/*In order to avoid replacing digital silence with quiet dither noise, we
				mute if the output has been silent for a while.*/
				if( mute > 64 ) {
					// memset( _of.dither_a, 0, sizeof( *_of.dither_a ) * 4 * _nchannels );
					final float[] dither_a = _of.dither_a;
					int i = _nchannels << 2;
					do {
						dither_a[--i] = 0;
					} while( i > 0 );
				}
				for( int i = 0; i < _nsamples; i++ ) {
					boolean silent = true;
					for( ci = 0; ci < _nchannels; ci++ ) {
						float s = _src[soffset + _nchannels * i + ci];
						silent &= s == 0;
						s *= OP_GAIN;
						float err = 0;
						for( int j = 0; j < 4; j++ ) {
							err += OP_FCOEF_B[j] * _of.dither_b[ci * 4 + j] - OP_FCOEF_A[j] * _of.dither_a[ci * 4 + j];
						}
						for( int j = 3; j-- > 0; ) {
							_of.dither_a[ci * 4 + j + 1] = _of.dither_a[ci * 4 + j];
						}
						for( int j = 3; j-- > 0; ) {
							_of.dither_b[ci * 4 + j + 1] = _of.dither_b[ci * 4 + j];
						}
						_of.dither_a[ci * 4] = err;
						s -= err;
						float r;
						if( mute > 16 ) {
							r = 0;
						} else {
							seed = op_rand( seed );// java: seed is uint32
							r = (float)((long)seed & 0xffffffffL) * OP_PRNG_GAIN;
							seed = op_rand( seed );
							r -= (float)((long)seed & 0xffffffffL) * OP_PRNG_GAIN;
						}
						/*Clamp in float out of paranoia that the input will be > 96 dBFS and
						wrap if the integer is clamped.*/
						final int si = op_float2int( OP_CLAMP( -32768f, s + r, 32767f ) );
						dst[ doffset + _nchannels * i + ci ] = (short)si;
						/*Including clipping in the noise shaping is generally disastrous: the
						futile effort to restore the clipped energy results in more clipping.
						However, small amounts---at the level which could normally be created
						by dither and rounding---are harmless and can even reduce clipping
						somewhat due to the clipping sometimes reducing the dither + rounding
						error.*/
						_of.dither_b[ci * 4] = mute > 16 ? 0 : OP_CLAMP( -1.5F, si - s, 1.5F );
					}
					mute++;
					if( ! silent ) {
						mute = 0;
					}
				}
				_of.dither_mute = mute <= 65 ? mute : 65;
				_of.dither_seed = seed;
			}
			_of.state_channel_count = _nchannels;
			return _nsamples;
		}
	}

	private static final Jop_float2short_filter op_float2short_filter = new Jop_float2short_filter();

	/** Reads more samples from the stream.
	   @note Although \a _buf_size must indicate the total number of values that
	    can be stored in \a _pcm, the return value is the number of samples
	    <em>per channel</em>.
	   This is done because
	   <ol>
	   <li>The channel count cannot be known a priori (reading more samples might
	        advance us into the next link, with a different channel count), so
	        \a _buf_size cannot also be in units of samples per channel,</li>
	   <li>Returning the samples per channel matches the <code>libopus</code> API
	        as closely as we're able,</li>
	   <li>Returning the total number of values instead of samples per channel
	        would mean the caller would need a division to compute the samples per
	        channel, and might worry about the possibility of getting back samples
	        for some channels and not others, and</li>
	   <li>This approach is relatively fool-proof: if an application passes too
	        small a value to \a _buf_size, they will simply get fewer samples back,
	        and if they assume the return value is the total number of values, then
	        they will simply read too few (rather than reading too many and going
	        off the end of the buffer).</li>
	   </ol>
	   @param      _of       The \c OggOpusFile from which to read.
	   @param[out] _pcm      A buffer in which to store the output PCM samples, as
	                          signed native-endian 16-bit values at 48&nbsp;kHz
	                          with a nominal range of <code>[-32768,32767)</code>.
	                         Multiple channels are interleaved using the
	                          <a href="http://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-800004.3.9">Vorbis
	                          channel ordering</a>.
	                         This must have room for at least \a _buf_size values.
	   @param      _buf_size The number of values that can be stored in \a _pcm.
	                         It is recommended that this be large enough for at
	                          least 120 ms of data at 48 kHz per channel (5760
	                          values per channel).
	                         Smaller buffers will simply return less data, possibly
	                          consuming more memory to buffer the data internally.
	                         <tt>libopusfile</tt> may return less data than
	                          requested.
	                         If so, there is no guarantee that the remaining data
	                          in \a _pcm will be unmodified.
	   @param[out] _li       The index of the link this data was decoded from.
	                         You may pass <code>NULL</code> if you do not need this
	                          information.
	                         If this function fails (returning a negative value),
	                          this parameter is left unset.
	   @return The number of samples read per channel on success, or a negative
	            value on failure.
	           The channel count can be retrieved on success by calling
	            <code>op_head(_of,*_li)</code>.
	           The number of samples returned may be 0 if the buffer was too small
	            to store even a single sample for all channels, or if end-of-file
	            was reached.
	           The list of possible failure codes follows.
	           Most of them can only be returned by unseekable, chained streams
	            that encounter a new link.
	   @retval #OP_HOLE          There was a hole in the data, and some samples
	                              may have been skipped.
	                             Call this function again to continue decoding
	                              past the hole.
	   @retval #OP_EREAD         An underlying read operation failed.
	                             This may signal a truncation attack from an
	                              <https:> source.
	   @retval #OP_EFAULT        An internal memory allocation failed.
	   @retval #OP_EIMPL         An unseekable stream encountered a new link that
	                              used a feature that is not implemented, such as
	                              an unsupported channel family.
	   @retval #OP_EINVAL        The stream was only partially open.
	   @retval #OP_ENOTFORMAT    An unseekable stream encountered a new link that
	                              did not have any logical Opus streams in it.
	   @retval #OP_EBADHEADER    An unseekable stream encountered a new link with a
	                              required header packet that was not properly
	                              formatted, contained illegal values, or was
	                              missing altogether.
	   @retval #OP_EVERSION      An unseekable stream encountered a new link with
	                              an ID header that contained an unrecognized
	                              version number.
	   @retval #OP_EBADPACKET    Failed to properly decode the next packet.
	   @retval #OP_EBADLINK      We failed to find data we had seen before.
	   @retval #OP_EBADTIMESTAMP An unseekable stream encountered a new link with
	                              a starting timestamp that failed basic validity
	                              checks. */
	public final int op_read( final short[] _pcm, final int _buf_size, final int[] _li ) {
		return op_filter_read_native( _pcm, _buf_size, op_float2short_filter, _li );
	}

	/** Reads more samples from the stream.
	   @note Although \a _buf_size must indicate the total number of values that
	    can be stored in \a _pcm, the return value is the number of samples
	    <em>per channel</em>.
	   <ol>
	   <li>The channel count cannot be known a priori (reading more samples might
	        advance us into the next link, with a different channel count), so
	        \a _buf_size cannot also be in units of samples per channel,</li>
	   <li>Returning the samples per channel matches the <code>libopus</code> API
	        as closely as we're able,</li>
	   <li>Returning the total number of values instead of samples per channel
	        would mean the caller would need a division to compute the samples per
	        channel, and might worry about the possibility of getting back samples
	        for some channels and not others, and</li>
	   <li>This approach is relatively fool-proof: if an application passes too
	        small a value to \a _buf_size, they will simply get fewer samples back,
	        and if they assume the return value is the total number of values, then
	        they will simply read too few (rather than reading too many and going
	        off the end of the buffer).</li>
	   </ol>
	   @param      _of       The \c OggOpusFile from which to read.
	   @param _pcm [out]     A buffer in which to store the output PCM samples as
	                          signed floats at 48&nbsp;kHz with a nominal range of
	                          <code>[-1.0,1.0]</code>.
	                         Multiple channels are interleaved using the
	                          <a href="http://www.xiph.org/vorbis/doc/Vorbis_I_spec.html#x1-800004.3.9">Vorbis
	                          channel ordering</a>.
	                         This must have room for at least \a _buf_size floats.
	   @param      _buf_size The number of floats that can be stored in \a _pcm.
	                         It is recommended that this be large enough for at
	                          least 120 ms of data at 48 kHz per channel (5760
	                          samples per channel).
	                         Smaller buffers will simply return less data, possibly
	                          consuming more memory to buffer the data internally.
	                         If less than \a _buf_size values are returned,
	                          <tt>libopusfile</tt> makes no guarantee that the
	                          remaining data in \a _pcm will be unmodified.
	   @param _li [out]      The index of the link this data was decoded from.
	                         You may pass <code>NULL</code> if you do not need this
	                          information.
	                         If this function fails (returning a negative value),
	                          this parameter is left unset.
	   @return The number of samples read per channel on success, or a negative
	            value on failure.
	           The channel count can be retrieved on success by calling
	            <code>op_head(_of,*_li)</code>.
	           The number of samples returned may be 0 if the buffer was too small
	            to store even a single sample for all channels, or if end-of-file
	            was reached.
	           The list of possible failure codes follows.
	           Most of them can only be returned by unseekable, chained streams
	            that encounter a new link.
	   @retval #OP_HOLE          There was a hole in the data, and some samples
	                              may have been skipped.
	                             Call this function again to continue decoding
	                              past the hole.
	   @retval #OP_EREAD         An underlying read operation failed.
	                             This may signal a truncation attack from an
	                              <https:> source.
	   @retval #OP_EFAULT        An internal memory allocation failed.
	   @retval #OP_EIMPL         An unseekable stream encountered a new link that
	                              used a feature that is not implemented, such as
	                              an unsupported channel family.
	   @retval #OP_EINVAL        The stream was only partially open.
	   @retval #OP_ENOTFORMAT    An unseekable stream encountered a new link that
	                              did not have any logical Opus streams in it.
	   @retval #OP_EBADHEADER    An unseekable stream encountered a new link with a
	                              required header packet that was not properly
	                              formatted, contained illegal values, or was
	                              missing altogether.
	   @retval #OP_EVERSION      An unseekable stream encountered a new link with
	                              an ID header that contained an unrecognized
	                              version number.
	   @retval #OP_EBADPACKET    Failed to properly decode the next packet.
	   @retval #OP_EBADLINK      We failed to find data we had seen before.
	   @retval #OP_EBADTIMESTAMP An unseekable stream encountered a new link with
	                              a starting timestamp that failed basic validity
	                              checks. */
	public final int op_read_float( final float[] _pcm, final int _buf_size, final int[] _li ) {
		this.state_channel_count = 0;
		return op_read_native( _pcm, 0, _buf_size, _li );
	}

	private static final class Jop_stereo_filter implements Jop_read_filter_func {

		@Override
		public int op_read_filter_func(final JOggOpusFile _of, final Object _dst, final int doffset, final int _dst_sz, final float[] _src, final int soffset, int _nsamples, final int _nchannels) {
			// (void)_of;
			int i = _dst_sz >> 1;// java
			_nsamples = _nsamples <= i ? _nsamples : i;
			if( _nchannels == 2 ) {
				System.arraycopy( _src, soffset, _dst, doffset, _nsamples << 1 );
			} else{
				final float[] dst = (float[])_dst;
				if( _nchannels == 1 ) {
					int i2 = doffset;// java
					for( i = 0; i < _nsamples; i++ ) {
						final float s = _src[soffset + i];// java
						dst[i2++] = s;
						dst[i2++] = s;
					}
				}
				else{
					int i2 = doffset;// java
					for( i = 0; i < _nsamples; i++ ) {
						float l, r;
						l = r = 0;
						for( int ci = 0; ci < _nchannels; ci++ ) {
							l += OP_STEREO_DOWNMIX[_nchannels - 3][ci][0] * _src[soffset + _nchannels * i + ci];
							r += OP_STEREO_DOWNMIX[_nchannels - 3][ci][1] * _src[soffset + _nchannels * i + ci];
						}
						dst[i2++] = l;
						dst[i2++] = r;
					}
				}
			}
			return _nsamples;
		}

	}
	private static final Jop_stereo_filter op_stereo_filter = new Jop_stereo_filter();

	private static final class Jop_float2short_stereo_filter implements Jop_read_filter_func {

		@Override
		public int op_read_filter_func(final JOggOpusFile _of, final Object _dst, final int doffset, final int _dst_sz, final float[] _src, final int soffset, int _nsamples, final int _nchannels) {
			final short[] dst = (short[])_dst;
			if( _nchannels == 1 ) {
				_nsamples = op_float2short_filter.op_read_filter_func( _of, dst, doffset, _dst_sz >> 1, _src, soffset, _nsamples, 1 );
				for( int i = _nsamples; i-- > 0; ) {
					dst[doffset + 2 * i + 0] = dst[doffset + 2 * i + 1] = dst[doffset + i];
				}
			}
			else{
				if( _nchannels > 2 ) {
					final int v = _dst_sz >> 1;// java
					_nsamples = _nsamples <= v ? _nsamples : v;
					_nsamples = op_stereo_filter.op_read_filter_func( _of, _src, soffset, _nsamples << 1, _src, soffset, _nsamples, _nchannels );
				}
				_nsamples = op_float2short_filter.op_read_filter_func( _of, dst, doffset, _dst_sz, _src, soffset, _nsamples, 2 );
			}
			return _nsamples;
		}
	}
	private static final Jop_float2short_stereo_filter op_float2short_stereo_filter = new Jop_float2short_stereo_filter();

	/** Reads more samples from the stream and downmixes to stereo, if necessary.
	   This function is intended for simple players that want a uniform output
	    format, even if the channel count changes between links in a chained
	    stream.
	   @note \a _buf_size indicates the total number of values that can be stored
	    in \a _pcm, while the return value is the number of samples <em>per
	    channel</em>, even though the channel count is known, for consistency with
	    op_read().
	   @param      _of       The \c OggOpusFile from which to read.
	   @param[out] _pcm      A buffer in which to store the output PCM samples, as
	                          signed native-endian 16-bit values at 48&nbsp;kHz
	                          with a nominal range of <code>[-32768,32767)</code>.
	                         The left and right channels are interleaved in the
	                          buffer.
	                         This must have room for at least \a _buf_size values.
	   @param      _buf_size The number of values that can be stored in \a _pcm.
	                         It is recommended that this be large enough for at
	                          least 120 ms of data at 48 kHz per channel (11520
	                          values total).
	                         Smaller buffers will simply return less data, possibly
	                          consuming more memory to buffer the data internally.
	                         If less than \a _buf_size values are returned,
	                          <tt>libopusfile</tt> makes no guarantee that the
	                          remaining data in \a _pcm will be unmodified.
	   @return The number of samples read per channel on success, or a negative
	            value on failure.
	           The number of samples returned may be 0 if the buffer was too small
	            to store even a single sample for both channels, or if end-of-file
	            was reached.
	           The list of possible failure codes follows.
	           Most of them can only be returned by unseekable, chained streams
	            that encounter a new link.
	   @retval #OP_HOLE          There was a hole in the data, and some samples
	                              may have been skipped.
	                             Call this function again to continue decoding
	                              past the hole.
	   @retval #OP_EREAD         An underlying read operation failed.
	                             This may signal a truncation attack from an
	                              <https:> source.
	   @retval #OP_EFAULT        An internal memory allocation failed.
	   @retval #OP_EIMPL         An unseekable stream encountered a new link that
	                              used a feature that is not implemented, such as
	                              an unsupported channel family.
	   @retval #OP_EINVAL        The stream was only partially open.
	   @retval #OP_ENOTFORMAT    An unseekable stream encountered a new link that
	                              did not have any logical Opus streams in it.
	   @retval #OP_EBADHEADER    An unseekable stream encountered a new link with a
	                              required header packet that was not properly
	                              formatted, contained illegal values, or was
	                              missing altogether.
	   @retval #OP_EVERSION      An unseekable stream encountered a new link with
	                              an ID header that contained an unrecognized
	                              version number.
	   @retval #OP_EBADPACKET    Failed to properly decode the next packet.
	   @retval #OP_EBADLINK      We failed to find data we had seen before.
	   @retval #OP_EBADTIMESTAMP An unseekable stream encountered a new link with
	                              a starting timestamp that failed basic validity
	                              checks. */
	public final int op_read_stereo(final short[] _pcm, final int _buf_size) {
		return op_filter_read_native( _pcm, _buf_size, op_float2short_stereo_filter, null );
	}

	/** Reads more samples from the stream and downmixes to stereo, if necessary.
	   This function is intended for simple players that want a uniform output
	    format, even if the channel count changes between links in a chained
	    stream.
	   @note \a _buf_size indicates the total number of values that can be stored
	    in \a _pcm, while the return value is the number of samples <em>per
	    channel</em>, even though the channel count is known, for consistency with
	    op_read_float().
	   @param      _of       The \c OggOpusFile from which to read.
	   @param[out] _pcm      A buffer in which to store the output PCM samples, as
	                          signed floats at 48&nbsp;kHz with a nominal range of
	                          <code>[-1.0,1.0]</code>.
	                         The left and right channels are interleaved in the
	                          buffer.
	                         This must have room for at least \a _buf_size values.
	   @param      _buf_size The number of values that can be stored in \a _pcm.
	                         It is recommended that this be large enough for at
	                          least 120 ms of data at 48 kHz per channel (11520
	                          values total).
	                         Smaller buffers will simply return less data, possibly
	                          consuming more memory to buffer the data internally.
	                         If less than \a _buf_size values are returned,
	                          <tt>libopusfile</tt> makes no guarantee that the
	                          remaining data in \a _pcm will be unmodified.
	   @return The number of samples read per channel on success, or a negative
	            value on failure.
	           The number of samples returned may be 0 if the buffer was too small
	            to store even a single sample for both channels, or if end-of-file
	            was reached.
	           The list of possible failure codes follows.
	           Most of them can only be returned by unseekable, chained streams
	            that encounter a new link.
	   @retval #OP_HOLE          There was a hole in the data, and some samples
	                              may have been skipped.
	                             Call this function again to continue decoding
	                              past the hole.
	   @retval #OP_EREAD         An underlying read operation failed.
	                             This may signal a truncation attack from an
	                              <https:> source.
	   @retval #OP_EFAULT        An internal memory allocation failed.
	   @retval #OP_EIMPL         An unseekable stream encountered a new link that
	                              used a feature that is not implemented, such as
	                              an unsupported channel family.
	   @retval #OP_EINVAL        The stream was only partially open.
	   @retval #OP_ENOTFORMAT    An unseekable stream encountered a new link that
	                              that did not have any logical Opus streams in it.
	   @retval #OP_EBADHEADER    An unseekable stream encountered a new link with a
	                              required header packet that was not properly
	                              formatted, contained illegal values, or was
	                              missing altogether.
	   @retval #OP_EVERSION      An unseekable stream encountered a new link with
	                              an ID header that contained an unrecognized
	                              version number.
	   @retval #OP_EBADPACKET    Failed to properly decode the next packet.
	   @retval #OP_EBADLINK      We failed to find data we had seen before.
	   @retval #OP_EBADTIMESTAMP An unseekable stream encountered a new link with
	                              a starting timestamp that failed basic validity
	                              checks. */
	public final int op_read_float_stereo(final float[] _pcm, final int _buf_size) {
		this.state_channel_count = 0;
		return op_filter_read_native( _pcm, _buf_size, op_stereo_filter, null );
	}

// #endif
}
