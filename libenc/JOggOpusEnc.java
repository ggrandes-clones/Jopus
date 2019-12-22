package libenc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;

import celt.Jcelt;
import opus.JOpusMSEncoder;
import opus.Jms_encoder_data_aux;
import opus.Jopus_defines;

/* Copyright (C)2002-2017 Jean-Marc Valin
Copyright (C)2007-2013 Xiph.Org Foundation
Copyright (C)2008-2013 Gregory Maxwell
File: opusenc.c

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/** Opaque encoder struct. */
public final class JOggOpusEnc {
	private static final String CLASS_NAME = "JOggOpusEnc";

	// # Identity of this package.
	static final String PACKAGE_NAME = "libopusenc";
	// private static final String PACKAGE_TARNAME = "libopusenc";
	static final String PACKAGE_VERSION = "0.2.1";
	// private static final String PACKAGE_STRING = "libopusenc 0.2.1";
	// private static final String PACKAGE_BUGREPORT = "opus@xiph.org";
	// private static final String PACKAGE_URL = "";

	// opusenc.h
	/* Bump this when we change the API. */
	/** API version for this header. Can be used to check for features at compile time. */
	// #define OPE_API_VERSION 0

	public static final int OPE_OK = 0;
	/* Based on the relevant libopus code minus 10. */
	static final int OPE_BAD_ARG = -11;
	static final int OPE_INTERNAL_ERROR = -13;
	static final int OPE_UNIMPLEMENTED = -15;
	static final int OPE_ALLOC_FAIL = -17;

	/* Specific to libopusenc. */
	private static final int OPE_CANNOT_OPEN = -30;
	private static final int OPE_TOO_LATE = -31;
	private static final int OPE_INVALID_PICTURE = -32;
	private static final int OPE_INVALID_ICON = -33;
	private static final int OPE_WRITE_FAIL = -34;
	private static final int OPE_CLOSE_FAIL = -35;

	/* These are the "raw" request values -- they should usually not be used. */
	public static final int OPE_SET_DECISION_DELAY_REQUEST      = 14000;
	static final int OPE_GET_DECISION_DELAY_REQUEST      = 14001;
	public static final int OPE_SET_MUXING_DELAY_REQUEST        = 14002;
	static final int OPE_GET_MUXING_DELAY_REQUEST        = 14003;
	public static final int OPE_SET_COMMENT_PADDING_REQUEST     = 14004;
	static final int OPE_GET_COMMENT_PADDING_REQUEST     = 14005;
	public static final int OPE_SET_SERIALNO_REQUEST            = 14006;
	static final int OPE_GET_SERIALNO_REQUEST            = 14007;
	static final int OPE_SET_PACKET_CALLBACK_REQUEST     = 14008;
	/*#define OPE_GET_PACKET_CALLBACK_REQUEST     14009*/
	public static final int OPE_SET_HEADER_GAIN_REQUEST         = 14010;
	static final int OPE_GET_HEADER_GAIN_REQUEST         = 14011;
	static final int OPE_GET_NB_STREAMS_REQUEST          = 14013;
	static final int OPE_GET_NB_COUPLED_STREAMS_REQUEST  = 14015;

	/**\defgroup encoder_ctl Encoding Options*/
	/**\name Control parameters

	   Macros for setting encoder options.*/

	static final int OPE_SET_DECISION_DELAY = OPE_SET_DECISION_DELAY_REQUEST;
	static final int OPE_GET_DECISION_DELAY = OPE_GET_DECISION_DELAY_REQUEST;
	public static final int OPE_SET_MUXING_DELAY = OPE_SET_MUXING_DELAY_REQUEST;
	static final int OPE_GET_MUXING_DELAY = OPE_GET_MUXING_DELAY_REQUEST;
	public static final int OPE_SET_COMMENT_PADDING = OPE_SET_COMMENT_PADDING_REQUEST;
	static final int OPE_GET_COMMENT_PADDING = OPE_GET_COMMENT_PADDING_REQUEST;
	public static final int OPE_SET_SERIALNO = OPE_SET_SERIALNO_REQUEST;
	static final int OPE_GET_SERIALNO = OPE_GET_SERIALNO_REQUEST;
	/* FIXME: Add type-checking macros to these. */
	public static final int OPE_SET_PACKET_CALLBACK = OPE_SET_PACKET_CALLBACK_REQUEST;
	/*#define OPE_GET_PACKET_CALLBACK(x,u) OPE_GET_PACKET_CALLBACK_REQUEST, (x), (u)*/
	public static final int OPE_SET_HEADER_GAIN = OPE_SET_HEADER_GAIN_REQUEST;
	private static final int OPE_GET_HEADER_GAIN = OPE_GET_HEADER_GAIN_REQUEST;
	public static final int OPE_GET_NB_STREAMS = OPE_GET_NB_STREAMS_REQUEST;
	public static final int OPE_GET_NB_COUPLED_STREAMS = OPE_GET_NB_COUPLED_STREAMS_REQUEST;

	/** Bump this when we change the ABI. */
	private static final int OPE_ABI_VERSION = 0;

	private static final int LPC_PADDING = 120;
	private static final int LPC_ORDER = 24;
	private static final int LPC_INPUT = 480;
	/** Make the following constant always equal to 2*cos(M_PI/LPC_PADDING) */
	private static final float LPC_GOERTZEL_CONST = 1.99931465f;

	/** Allow up to 2 seconds for delayed decision. */
	private static final int MAX_LOOKAHEAD = 96000;
	/** We can't have a circular buffer (because of delayed decision), so let's not copy too often. */
	private static final int BUFFER_EXTRA = 24000;

	private static final int BUFFER_SAMPLES = (MAX_LOOKAHEAD + BUFFER_EXTRA);

	private static final class JEncStream {
		// java don't need, Object user_data;
		private boolean serialno_is_set;
		private int serialno;
		private boolean stream_is_init;
		private int packetno;
		private byte[] comment;
		// private int comment_length;// java: comment.length
		private boolean seen_file_icons;
		private boolean close_at_end;
		private boolean header_is_frozen;
		private long end_granule;
		private long granule_offset;
		private JEncStream next;

		private static final JEncStream stream_create(final JOggOpusComments comments) {
			final JEncStream stream = new JEncStream();
			// if( null == stream ) {
			//	return null;
			// }
			stream.next = null;
			stream.close_at_end = true;
			stream.serialno_is_set = false;
			stream.stream_is_init = false;
			stream.header_is_frozen = false;
			stream.granule_offset = 0;
			stream.comment = new byte[ comments.comment.length ];
			// if( stream.comment == null ) goto fail;
			System.arraycopy( comments.comment, 0, stream.comment, 0, comments.comment.length );
			// stream.comment_length = comments.comment_length;
			stream.seen_file_icons = comments.seen_file_icons;
			return stream;
	//fail:
//			stream.comment = null;
//			stream = null;
//			return null;
		}

		/** java: use stream = null */
		/* static final void stream_destroy(final JEncStream stream) {
			stream.comment = null;
			// free(stream);
		} */
	}

	private final JOpusGenericEncoder st = new JOpusGenericEncoder();
	private Joggpacker oggp;
	private int unrecoverable;
	private boolean pull_api;
	private int rate;
	private int channels;
	private float[] buffer;
	private int buffer_start;
	private int buffer_end;
	private JSpeexResampler re;
	private int frame_size;
	private int decision_delay;
	private int max_ogg_delay;
	private int global_granule_offset;
	private long curr_granule;
	private long write_granule;
	private long last_page_granule;// FIXME last_page_granule isn't used
	private boolean draining;
	private int frame_size_request;
	private float[] lpc_buffer;
	private byte[] chaining_keyframe;
	private int chaining_keyframe_length;
	private IOpusEncCallbacks callbacks;// = new JOpusEncCallbacks();
	private Iope_packet_func packet_callback;
	// private Object packet_callback_data;
	private final JOpusHeader header = new JOpusHeader();
	private int comment_padding;
	private JEncStream streams;
	private JEncStream last_stream;


	private final boolean output_pages() {
		final Joggp_page_aux aux = new Joggp_page_aux();// java
		while( this.oggp.oggp_get_next_page( aux ) ) {
			final boolean ret = this.callbacks.write( /* enc.streams.user_data,*/ aux );
			if( ret ) {
				return ret;
			}
		}
		return false;
	}

	private final boolean oe_flush_page() {
		this.oggp.oggp_flush_page();
		if( ! this.pull_api ) {
			return output_pages();
		}
		return false;
	}

	/* java: moved to JStdioObject
	 * start JOpusEncCallbacks
	static boolean stdio_write(Object user_data, final byte[] ptr, int len) {
		final JStdioObject obj = (JStdioObject)user_data;
		try {
			obj.file.write( ptr, 0, len );
		} catch(final IOException ie) {
			return true;
		}
		return false;
	}

	// java: use obj = null after calling
	private static final boolean stdio_close(Object user_data) {
		final JStdioObject obj = (JStdioObject)user_data;
		try {
			if( obj.file != null ) obj.file.close();
		} catch(final IOException ie) {
			return true;
		}
		return false;
	}

	private static final JOpusEncCallbacks stdio_callbacks = {
		stdio_write,
		stdio_close
	};

	// end JOpusEncCallbacks */

	/** Create a new OggOpus file. */
	/** Create a new OggOpus file.
	@param path       Path where to create the file
	@param comments   Comments associated with the stream
	@param rate       Input sampling rate (48 kHz is faster)
	@param channels   Number of channels
	@param family     Mapping family (0 for mono/stereo, 1 for surround)
	@param error [out] Error code (NULL if no error is to be returned)
	@return Newly-created encoder.
    */
	public static final JOggOpusEnc ope_encoder_create_file(final String path, final JOggOpusComments comments, final int rate, final int channels, final int family)
		throws IOException// java
	{
		JStdioObject obj = new JStdioObject();
		//if( obj == null ) {
			// if( error != null ) error[0] = OPE_ALLOC_FAIL;
		//	return null;
		//}
		JOggOpusEnc enc = ope_encoder_create_callbacks( (IOpusEncCallbacks)obj, /*obj,*/ comments, rate, channels, family );//, error );
		if( enc == null ) {// || (error != null && error[0] != 0) ) {
			obj = null;
			return null;
		}
		try {
			obj.file = new RandomAccessFile( path, "rw" );
		} catch(final Exception e) {
			// if( error != null ) error[0] = OPE_CANNOT_OPEN;
			enc.ope_encoder_destroy();
			enc = null;
			throw new IOException( ope_strerror( OPE_CANNOT_OPEN ) );
			// return null;
		}
		return enc;
	}


	/** Create a new OggOpus file (callback-based). */
	/** Create a new OggOpus stream to be handled using callbacks
	@param callbacks  Callback functions
	@param user_data  Pointer to be associated with the stream and passed to the callbacks
	@param comments   Comments associated with the stream
	@param rate       Input sampling rate (48 kHz is faster)
	@param channels   Number of channels
	@param family     Mapping family (0 for mono/stereo, 1 for surround)
	@param error [out] Error code (NULL if no error is to be returned)
	@return Newly-created encoder.
    */
	public static final JOggOpusEnc ope_encoder_create_callbacks(final IOpusEncCallbacks callbacks,// java: using the callback class itself. Object user_data,
			final JOggOpusComments comments, final int rate, final int channels, final int family) // int[] error)
		throws IOException
	{
		JOggOpusEnc enc = null;
		int ret;
		if( family != 0 && family != 1 &&
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
			family != 2 && family != 3 &&
// #endif
			family != 255 && family != -1 ) {
			//if( error != null ) {
			//	if( family < -1 || family > 255 ) error[0] = OPE_BAD_ARG;
			//	else error[0] = OPE_UNIMPLEMENTED;
			//}
			//return null;
			throw new IOException();
		}
		if( channels <= 0 || channels > 255 ) {
			//if( error != null ) error[0] = OPE_BAD_ARG;
			//return null;
			throw new IOException();
		}
		if( rate <= 0 ) {
			//if( error != null ) error[0] = OPE_BAD_ARG;
			//return null;
			throw new IOException();
		}
		/* Setting the most common failure up-front. */
		// if( error != null ) error[0] = OPE_ALLOC_FAIL;
		// if( (enc = new JOggOpusEnc()) == null ) goto fail;
		enc = new JOggOpusEnc();
		enc.buffer = null;
		enc.lpc_buffer = null;
		// if( (enc.streams = stream_create( comments )) == null ) goto fail;
		enc.streams = JEncStream.stream_create( comments );
		enc.last_stream = enc.streams;
		enc.oggp = null;
		/* Not initializing anything is an unrecoverable error. */
		enc.unrecoverable = family == -1 ? OPE_TOO_LATE : 0;
		enc.pull_api = false;
		enc.packet_callback = null;
		enc.rate = rate;
		enc.channels = channels;
		enc.frame_size = 960;
		enc.decision_delay = 96000;
		enc.max_ogg_delay = 48000;
		enc.chaining_keyframe = null;
		enc.chaining_keyframe_length = -1;
		enc.comment_padding = 512;
		enc.header.channels = channels;
		enc.header.channel_mapping = family;
		enc.header.input_sample_rate = rate;
		enc.header.gain = 0;
		if( family != -1 ) {
			final Jms_encoder_data_aux data = new Jms_encoder_data_aux();// java
			ret = enc.st.opeint_encoder_surround_init( 48000, channels,
								enc.header.channel_mapping,
								data,// &enc.header.nb_streams, &enc.header.nb_coupled,
								enc.header.stream_map, Jopus_defines.OPUS_APPLICATION_AUDIO );
			if( ! (ret == Jopus_defines.OPUS_OK) ) {
				// if( ret == Jopus_defines.OPUS_BAD_ARG ) ret = OPE_BAD_ARG;
				// else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) ret = OPE_INTERNAL_ERROR;
				// else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) ret = OPE_UNIMPLEMENTED;
				// else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) ret = OPE_ALLOC_FAIL;
				// else ret = OPE_INTERNAL_ERROR;
				// if( error != null ) error[0] = ret;
				// goto fail;
				if( enc != null ) {
					enc.st.opeint_encoder_cleanup();
					enc.buffer = null;
					// if( enc.streams != null ) {
					//	stream_destroy( enc.streams );
					//}
					enc.streams = null;
					enc.lpc_buffer = null;
					enc = null;
				}
				throw new IOException();
			}
			enc.header.nb_streams = data.streams;
			enc.header.nb_coupled = data.coupled_streams;
			enc.st.opeint_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_20_MS );
		}
		if( rate != 48000 ) {
			enc.re = JSpeexResampler.speex_resampler_init( channels, rate, 48000, 5 );
			// if( enc.re == null ) goto fail;
			enc.re.speex_resampler_skip_zeros();
		} else {
			enc.re = null;
		}
		enc.global_granule_offset = -1;
		enc.curr_granule = 0;
		enc.write_granule = 0;
		enc.last_page_granule = 0;
		enc.draining = false;
		// if( (enc.buffer = malloc(sizeof(*enc.buffer)*BUFFER_SAMPLES*channels)) == null ) goto fail;
		enc.buffer = new float[ BUFFER_SAMPLES * channels ];
		if( rate != 48000 ) {
			/* Allocate an extra LPC_PADDING samples so we can do the padding in-place. */
			enc.lpc_buffer = new float[ (LPC_INPUT + LPC_PADDING) * channels ];
		}
		enc.buffer_start = enc.buffer_end = 0;
		if( callbacks != null )
		{
			enc.callbacks = callbacks;
		}
		// java: don't need. enc.streams.user_data = user_data;
		// if( error != null ) error[0] = OPE_OK;
		return enc;
/* fail:
		if( enc != null ) {
			opeint_encoder_cleanup( enc.st );
			enc.buffer = null;
			if( enc.streams != null ) stream_destroy( enc.streams );
			enc.lpc_buffer = null;
			enc = null;
		}
		return null; */
	}

	/** Create a new OggOpus stream, pulling one page at a time. */
	/** Create a new OggOpus stream to be used along with.ope_encoder_get_page().
	  This is mostly useful for muxing with other streams.
	    @param comments   Comments associated with the stream
	    @param rate       Input sampling rate (48 kHz is faster)
	    @param channels   Number of channels
	    @param family     Mapping family (0 for mono/stereo, 1 for surround)
	    @param error [out] Error code (NULL if no error is to be returned)
	    @return Newly-created encoder.
	    */
	static final JOggOpusEnc ope_encoder_create_pull(final JOggOpusComments comments, final int rate, final int channels, final int family)
		throws IOException//, int[] error) {
	{
		final JOggOpusEnc enc = ope_encoder_create_callbacks( null, /*null,*/ comments, rate, channels, family );// , error );
		if( enc != null ) {
			enc.pull_api = true;
		}
		return enc;
	}

	/** Deferred initialization of the encoder to force an explicit channel mapping. This can be used to override the default channel coupling,
	but using it for regular surround will almost certainly lead to worse quality.
	@param enc [in,out]        Encoder
	@param family              Mapping family (0 for mono/stereo, 1 for surround)
	@param nstreams             Total number of streams
	@param coupled_streams     Number of coupled streams
	@param mapping             Channel mapping
	@return Error code
	*/
	final int ope_encoder_deferred_init_with_mapping(final int family, final int nstreams,
				final int coupled_streams, final char[] mapping ) {
		if( family < 0 || family > 255 ) {
			return OPE_BAD_ARG;
		} else if( family != 1 &&
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
				family != 2 &&
// #endif
				family != 255) {
			return OPE_UNIMPLEMENTED;
		} else if( nstreams <= 0 || nstreams > 255 || coupled_streams < 0 || coupled_streams >= 128 || nstreams + coupled_streams > 255) {
			return OPE_BAD_ARG;
		}
		int ret = this.st.opeint_encoder_init( 48000, this.channels, nstreams, coupled_streams, mapping, Jopus_defines.OPUS_APPLICATION_AUDIO );
		if( ! (ret == Jopus_defines.OPUS_OK)  ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
			return ret;
		}
		this.st.opeint_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, Jopus_defines.OPUS_FRAMESIZE_20_MS );
		this.unrecoverable = 0;
		this.header.channel_mapping = family;
		this.header.nb_streams = nstreams;
		this.header.nb_coupled = coupled_streams;
		for( int i = 0, ie = nstreams + coupled_streams; i < ie; i++ ) {
			this.header.stream_map[ i ] = mapping[ i ];
		}
		return OPE_OK;
	}

	private final void init_stream() {
		// assert( ! enc.streams.stream_is_init );
		if( ! this.streams.serialno_is_set ) {
			this.streams.serialno = new Random( System.currentTimeMillis() ).nextInt( 0x8000 );// java rand();
		}

		if( this.oggp != null ) {
			this.oggp.oggp_chain( this.streams.serialno );
		} else {
			this.oggp = Joggpacker.oggp_create( this.streams.serialno );
			if( this.oggp == null ) {
				this.unrecoverable = OPE_ALLOC_FAIL;
				return;
			}
			this.oggp.oggp_set_muxing_delay( this.max_ogg_delay );
		}
		this.streams.comment = JOggOpusComments.opeint_comment_pad( this.streams.comment, this.comment_padding );

		/* Get preskip at the last minute (when it can no longer change). */
		if( this.global_granule_offset == -1 ) {
			final Object request[] = new Object[1];// java helper
			final int ret = this.st.opeint_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, request );
			if( ret == Jopus_defines.OPUS_OK ) {
				this.header.preskip = ((Integer)request[0]).intValue();
			} else {
				this.header.preskip = 0;
			}
			this.global_granule_offset = this.header.preskip;
		}
		/*Write header*/
		{
			final int header_size = this.header.opeint_opus_header_get_size();
			int p = this.oggp.oggp_get_packet_buffer( header_size );// java changed to an offset
			byte[] pbase = this.oggp.oggp_get_packet_buffer();// java added
			final int packet_size = this.header.opeint_opus_header_to_packet( pbase, p, header_size, this.st );
			if( this.packet_callback != null ) {
				this.packet_callback.ope_packet_func( /* enc.packet_callback_data,*/ pbase, p, packet_size, 0 );
			}
			this.oggp.oggp_commit_packet( packet_size, 0, false );
			boolean ret = oe_flush_page();
			if( ret ) {
				this.unrecoverable = OPE_WRITE_FAIL;
				return;
			}
			p = this.oggp.oggp_get_packet_buffer( this.streams.comment.length );
			pbase = this.oggp.oggp_get_packet_buffer();// java added
			System.arraycopy( this.streams.comment, 0, pbase, p, this.streams.comment.length );
			if( this.packet_callback != null ) {
				this.packet_callback.ope_packet_func( /* enc.packet_callback_data,*/ pbase, p, this.streams.comment.length, 0 );
			}
			this.oggp.oggp_commit_packet( this.streams.comment.length, 0, false );
			ret = oe_flush_page();
			if( ret ) {
				this.unrecoverable = OPE_WRITE_FAIL;
				return;
			}
		}
		this.streams.stream_is_init = true;
		this.streams.packetno = 2;
	}

	private final void shift_buffer() {
		/* Leaving enough in the buffer to do LPC extension if needed. */
		if( this.buffer_start > LPC_INPUT ) {
			final int shift = this.channels * (this.buffer_start - LPC_INPUT);// java
			final int length = this.channels * this.buffer_end - shift;// java
			System.arraycopy( this.buffer, shift, this.buffer, 0, length );
			this.buffer_end -= this.buffer_start - LPC_INPUT;
			this.buffer_start = LPC_INPUT;
		}
	}

	static final int compute_frame_samples(final int size_request) {
		if( size_request <= Jopus_defines.OPUS_FRAMESIZE_40_MS ) {
			return 120 << (size_request - Jopus_defines.OPUS_FRAMESIZE_2_5_MS);
		}// else {
			return (size_request - Jopus_defines.OPUS_FRAMESIZE_2_5_MS - 2) * 960;
		//}
	}

	private final void encode_buffer() {
		final Object request[] = new Object[1];// java helper
		/* Round up when converting the granule pos because the decoder will round down. */
		long end_granule48k = (this.streams.end_granule * 48000 + this.rate - 1) / this.rate + this.global_granule_offset;
		final int max_packet_size = (1277 * 6 + 2) * this.header.nb_streams;
		while( this.buffer_end - this.buffer_start > this.frame_size + this.decision_delay ) {
			if( this.unrecoverable != 0 ) {
				return;
			}
			this.st.opeint_encoder_ctl( Jopus_defines.OPUS_GET_PREDICTION_DISABLED, request );
			final boolean pred = ((Boolean)request[0]).booleanValue();// java
			/* FIXME: a frame that follows a keyframe generally doesn't need to be a keyframe
			unless there's two consecutive stream boundaries. */
			boolean is_keyframe = false;
			if( this.curr_granule + (this.frame_size << 1) >= end_granule48k && this.streams.next != null ) {
				this.st.opeint_encoder_ctl( Jopus_defines.OPUS_SET_PREDICTION_DISABLED, true );
				is_keyframe = true;
			}
			/* Handle the last packet by making sure not to encode too much padding. */
			if( this.curr_granule + this.frame_size >= end_granule48k && this.draining && this.frame_size_request > Jopus_defines.OPUS_FRAMESIZE_20_MS ) {
				int framesize_request = Jopus_defines.OPUS_FRAMESIZE_20_MS;
				/* Minimum frame size required for the current frame to still meet the e_o_s condition. */
				final int min_samples = (int)(end_granule48k - this.curr_granule);
				while( compute_frame_samples( framesize_request ) < min_samples ) {
					framesize_request++;
				}
				// assert(frame_size_request <= enc.frame_size_request);
				ope_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, framesize_request );
			}
			int packet = this.oggp.oggp_get_packet_buffer( max_packet_size );// java return type is changed
			byte[] packet_base = this.oggp.oggp_get_packet_buffer();// java: added
			final int nbBytes = this.st.opeint_encode_float( this.buffer, this.channels * this.buffer_start,
								this.buffer_end - this.buffer_start, packet_base, packet, max_packet_size );
			if( nbBytes < 0 ) {
				/* Anything better we can do here? */
				this.unrecoverable = OPE_INTERNAL_ERROR;
				return;
			}
			this.st.opeint_encoder_ctl( Jopus_defines.OPUS_SET_PREDICTION_DISABLED, pred );
			// assert(nbBytes > 0);
			byte[] packet_copy = null;
			boolean e_o_s;
			boolean cont;
			this.curr_granule += this.frame_size;
			do {
				boolean ret;
				long granulepos = this.curr_granule - this.streams.granule_offset;
				e_o_s = this.curr_granule >= end_granule48k;
				cont = false;
				if( e_o_s ) {
					granulepos = end_granule48k - this.streams.granule_offset;
				}
				if( packet_copy != null ) {
					packet = this.oggp.oggp_get_packet_buffer( max_packet_size );
					packet_base = this.oggp.oggp_get_packet_buffer();// java: added
					System.arraycopy( packet_copy, 0, packet_base, packet, nbBytes );
				}
				if( this.packet_callback != null ) {
					this.packet_callback.ope_packet_func( /* enc.packet_callback_data,*/ packet_base, packet, nbBytes, 0 );
				}
				if( (e_o_s || is_keyframe) && packet_copy == null ) {
					packet_copy = new byte[ nbBytes ];
					// if( packet_copy == null ) {
						/* Can't recover from allocation failing here. */
					//	enc.unrecoverable = OPE_ALLOC_FAIL;
					//	return;
					// }
					System.arraycopy( packet_base, packet, packet_copy, 0, nbBytes );
				}
				this.oggp.oggp_commit_packet( nbBytes, granulepos, e_o_s );
				if( e_o_s ) {
					ret = oe_flush_page();
				} else if( ! this.pull_api ) {
					ret = output_pages();
				} else {
					ret = false;
				}
				if( ret ) {
					this.unrecoverable = OPE_WRITE_FAIL;
					packet_copy = null;// if( packet_copy != null ) packet_copy = null;
					return;
				}
				if( e_o_s ) {
					final JEncStream tmp = this.streams.next;
					if( this.streams.close_at_end && ! this.pull_api ) {
						ret = this.callbacks.close( /* enc.streams.user_data */ );
						if( ret ) {
							this.unrecoverable = OPE_CLOSE_FAIL;
							packet_copy = null;
							return;
						}
					}
					// stream_destroy( enc.streams );// java isn't need
					this.streams = tmp;
					if( null == tmp ) {
						this.last_stream = null;
					}
					if( this.last_stream == null ) {
						packet_copy = null;
						return;
					}
					/* We're done with this stream, start the next one. */
					this.header.preskip = (int)(end_granule48k - this.curr_granule + this.frame_size);
					this.streams.granule_offset = this.curr_granule - this.frame_size;
					if( this.chaining_keyframe != null ) {
						this.header.preskip += this.frame_size;
						this.streams.granule_offset -=  this.frame_size;
					}
					init_stream();
					if( this.chaining_keyframe != null ) {
						final long granulepos2 = this.curr_granule - this.streams.granule_offset - this.frame_size;
						final int p = this.oggp.oggp_get_packet_buffer( this.chaining_keyframe_length );
						packet_base = this.oggp.oggp_get_packet_buffer();// java added
						System.arraycopy( this.chaining_keyframe, 0, packet_base, p, this.chaining_keyframe_length );
						if( this.packet_callback != null ) {
							this.packet_callback.ope_packet_func( /*enc.packet_callback_data,*/ this.chaining_keyframe, 0, this.chaining_keyframe_length, 0 );
						}
						this.oggp.oggp_commit_packet( this.chaining_keyframe_length, granulepos2, false );
					}
					end_granule48k = (this.streams.end_granule * 48000 + this.rate - 1) / this.rate + this.global_granule_offset;
					cont = true;
				}
			} while( cont );
			this.chaining_keyframe = null;
			if( is_keyframe ) {
				this.chaining_keyframe_length = nbBytes;
				this.chaining_keyframe = packet_copy;
				packet_copy = null;
			} else {
				this.chaining_keyframe = null;
				this.chaining_keyframe_length = -1;
			}
			packet_copy = null;
			this.buffer_start += this.frame_size;
		}
		/* If we've reached the end of the buffer, move everything back to the front. */
		if( this.buffer_end == BUFFER_SAMPLES ) {
			shift_buffer();
		}
		/* This function must never leave the buffer full. */
		// assert(enc.buffer_end < BUFFER_SAMPLES);
	}

	/** Add/encode any number of float samples to the stream.
	@param enc [in,out]        Encoder
	@param pcm                 Floating-point PCM values in the +/-1 range (interleaved if multiple channels)
	@param samples_per_channel Number of samples for each channel
	@return Error code*/
	public final int ope_encoder_write_float(final float[] pcm, int samples_per_channel) {
		final int nchannels = this.channels;
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		this.last_stream.header_is_frozen = true;
		if( ! this.streams.stream_is_init ) {
			init_stream();
		}
		if( samples_per_channel < 0 ) {
			return OPE_BAD_ARG;
		}
		this.write_granule += samples_per_channel;
		this.last_stream.end_granule = this.write_granule;
		if( null != this.lpc_buffer ) {
			if( samples_per_channel < LPC_INPUT ) {
				for( int i = 0, ie = (LPC_INPUT - samples_per_channel) * nchannels, bi = samples_per_channel * nchannels; i < ie; i++, bi++ ) {
					this.lpc_buffer[ i ] = this.lpc_buffer[ bi ];
				}
				for( int i = 0, ie = samples_per_channel * nchannels, bi = (LPC_INPUT - samples_per_channel) * nchannels; i < ie; i++, bi++ ) {
					this.lpc_buffer[ bi ] = pcm[ i ];
				}
			} else {
				for( int i = 0, ie = LPC_INPUT * nchannels, bi = (samples_per_channel - LPC_INPUT) * nchannels; i < ie; i++, bi++ ) {
					this.lpc_buffer[ i ] = pcm[ bi ];
				}
			}
		}
		int pcmoffset = 0;// java
		do {
			int in_samples, out_samples;
			out_samples = BUFFER_SAMPLES - this.buffer_end;
			if( this.re != null ) {
				in_samples = samples_per_channel;
				final long tmp = this.re.speex_resampler_process_interleaved_float( pcm, pcmoffset, in_samples, this.buffer, nchannels * this.buffer_end, out_samples );
				if( tmp < 0 ) {// java: added
					// throw new IOException( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
					System.err.println( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
				}
				out_samples = (int)tmp;
				in_samples = (int)(tmp >> 32);
			} else {
				final int curr = (samples_per_channel <= out_samples) ? samples_per_channel : out_samples;
				for( int i = pcmoffset, ie = pcmoffset + nchannels * curr, bi = nchannels * this.buffer_end; i < ie; i++, bi++ ) {
					this.buffer[ bi ] = pcm[ i ];
				}
				in_samples = out_samples = curr;
			}
			this.buffer_end += out_samples;
			pcmoffset += in_samples * nchannels;
			samples_per_channel -= in_samples;
			encode_buffer();
			if( this.unrecoverable != 0 ) {
				return this.unrecoverable;
			}
		} while( samples_per_channel > 0 );
		return OPE_OK;
	}

	private static final int CONVERT_BUFFER = 4096;

	/** Add/encode any number of 16-bit linear samples to the stream.
	@param enc [in,out]        Encoder
	@param pcm                 Linear 16-bit PCM values in the [-32768,32767] range (interleaved if multiple channels)
	@param samples_per_channel Number of samples for each channel
	@return Error code*/
	public final int ope_encoder_write(final short[] pcm_base, int samples_per_channel) {
		final int nchannels = this.channels;
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		this.last_stream.header_is_frozen = true;
		if( ! this.streams.stream_is_init ) {
			init_stream();
		}
		if( samples_per_channel < 0 ) {
			return OPE_BAD_ARG;
		}
		this.write_granule += samples_per_channel;
		this.last_stream.end_granule = this.write_granule;
		int pcm = 0;// java offset
		if( this.lpc_buffer != null ) {
			if( samples_per_channel < LPC_INPUT ) {
				for( int i = 0, ie = (LPC_INPUT - samples_per_channel) * nchannels, bi = samples_per_channel * nchannels; i < ie; i++, bi++ ) {
					this.lpc_buffer[ i ] = this.lpc_buffer[ bi ];
				}
				for( int i = pcm, ie = pcm + samples_per_channel * nchannels, bi = (LPC_INPUT - samples_per_channel) * nchannels; i < ie; i++, bi++ ) {
					this.lpc_buffer[ bi ] = (1.f / 32768f) * pcm_base[ i ];
				}
			} else {
				for( int i = 0, ie = LPC_INPUT * nchannels, pi = pcm + (samples_per_channel - LPC_INPUT) * nchannels; i < ie; i++, pi++ ) {
					this.lpc_buffer[ i ] = (1.f / 32768f) * pcm_base[ pi ];
				}
			}
		}
		do {
			int in_samples, out_samples;
			out_samples = BUFFER_SAMPLES - this.buffer_end;
			if( this.re != null ) {
				final float buf[] = new float[CONVERT_BUFFER];
				in_samples = CONVERT_BUFFER / nchannels;
				if( in_samples > samples_per_channel ) {
					in_samples = samples_per_channel;
				}
				for( int i = 0, ie = nchannels * in_samples, pi = pcm; i < ie; i++, pi++ ) {
					buf[ i ] = (1.f / 32768f) * pcm_base[ pi ];
				}
				final long tmp = this.re.speex_resampler_process_interleaved_float( buf, 0, in_samples, this.buffer, nchannels * this.buffer_end, out_samples );
				if( tmp < 0 ) {// java: added
					// throw new IOException( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
					System.err.println( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
				}
				out_samples = (int)tmp;
				in_samples = (int)(tmp >> 32);
			} else {
				final int curr = (samples_per_channel <= out_samples) ? samples_per_channel : out_samples;
				for( int i = pcm, ie = pcm + nchannels * curr, j = nchannels * this.buffer_end; i < ie; i++, j++ ) {
					this.buffer[ j ] = (1.f / 32768f) * pcm_base[ i ];
				}
				in_samples = out_samples = curr;
			}
			this.buffer_end += out_samples;
			pcm += in_samples * nchannels;
			samples_per_channel -= in_samples;
			encode_buffer();
			if( this.unrecoverable != 0 ) {
				return this.unrecoverable;
			}
		} while( samples_per_channel > 0 );
		return OPE_OK;
	}

	/** Get the next page from the stream (only if using ope_encoder_create_pull()).
	@param enc [in,out] Encoder
	@param page [out]   Next available encoded page
	@param len [out]    Size (in bytes) of the page returned
	@param flush        If non-zero, forces a flush of the page (if any data avaiable)
	@return 1 if there is a page available, 0 if not. */
	final boolean ope_encoder_get_page(final Joggp_page_aux data, final boolean flush) {
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable != 0;
		}
		if( ! this.pull_api ) {
			return false;
		} else {
			if( flush ) {
				this.oggp.oggp_flush_page();
			}
			return this.oggp.oggp_get_next_page( data );
		}
	}

	/** Finalizes the stream, but does not deallocate the object.
	@param[in,out] enc Encoder
	@return Error code
	*/
	public final int ope_encoder_drain() {
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		/* Check if it's already been drained. */
		if( this.streams == null ) {
			return OPE_TOO_LATE;
		}
		int resampler_drain = 0;
		if( this.re != null ) {
			resampler_drain = this.re.speex_resampler_get_output_latency();
		}
		int pad_samples = this.global_granule_offset + this.frame_size + resampler_drain + 1;
		if( pad_samples < LPC_PADDING ) {
			pad_samples = LPC_PADDING;
		}
		if( ! this.streams.stream_is_init ) {
			init_stream();
		}
		shift_buffer();
		// assert(enc.buffer_end + pad_samples <= BUFFER_SAMPLES);
		// memset(&enc.buffer[enc.channels*enc.buffer_end], 0, pad_samples * enc.channels * sizeof(enc.buffer[0]));
		{
			final float[] b = this.buffer;
			for( int i = this.channels * this.buffer_end, ie = i + pad_samples * this.channels; i < ie; i++ ) {
				b[ i ] = 0;
			}
		}
		if( this.re != null ) {
			extend_signal( this.lpc_buffer, LPC_INPUT * this.channels, LPC_INPUT, LPC_PADDING, this.channels );
			do {
				// in_samples = LPC_PADDING;
				// out_samples = pad_samples;
				final long tmp = this.re.speex_resampler_process_interleaved_float( this.lpc_buffer, LPC_INPUT * this.channels, LPC_PADDING, this.buffer, this.channels * this.buffer_end, pad_samples );
				if( tmp < 0 ) {// java: added
					// throw new IOException( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
					System.err.println( JSpeexResampler.speex_resampler_strerror( (int)tmp ) );
				}
				final int out_samples = (int)tmp;
				this.buffer_end += out_samples;
				pad_samples -= out_samples;
				/* If we don't have enough padding, zero all zeros and repeat. */
				// memset( &enc.lpc_buffer[LPC_INPUT * enc.channels], 0, LPC_PADDING * enc.channels * sizeof(enc.lpc_buffer[0]) );
				{
					final float[] b = this.lpc_buffer;
					for( int i = LPC_INPUT * this.channels, ie = i + LPC_PADDING * this.channels; i < ie; i++ ) {
						b[ i ] = 0;
					}
				}
			} while( pad_samples > 0 );
		} else {
			extend_signal( this.buffer, this.channels * this.buffer_end, this.buffer_end, LPC_PADDING, this.channels );
			this.buffer_end += pad_samples;
		}
		this.decision_delay = 0;
		this.draining = true;
		// assert(enc.buffer_end <=  BUFFER_SAMPLES);
		encode_buffer();
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		/* Draining should have called all the streams to complete. */
		// assert(enc.streams == null);
		return OPE_OK;
	}

	/** Deallocates the obect. Make sure to ope_drain() first.
	@param[in,out] enc Encoder
	 */
	public final void ope_encoder_destroy() {
		JEncStream stream = this.streams;
		while( stream != null ) {
			final JEncStream tmp = stream;
			stream = stream.next;
			/* Ignore any error on close. */
			if( tmp.close_at_end && ! this.pull_api ) {
				this.callbacks.close(/* tmp.user_data */);
			}
			// stream_destroy( tmp );// java isn't need
		}
		this.chaining_keyframe = null;
		this.buffer = null;
		this.oggp = null;// if( enc.oggp != null ) Jogg_packer.oggp_destroy( enc.oggp );
		this.st.opeint_encoder_cleanup();
		this.re = null;
		this.lpc_buffer = null;
		// enc = null;
	}

	/** Ends the stream and create a new stream within the same file.
	@param[in,out] enc Encoder
	@param comments   Comments associated with the stream
	@return Error code
	 */
	final int ope_encoder_chain_current(final JOggOpusComments comments) {
		this.last_stream.close_at_end = false;
		return ope_encoder_continue_new_callbacks( /* enc.last_stream.user_data,*/ comments );
	}

	/** Ends the stream and create a new file.
	@param[in,out] enc Encoder
	@param path        Path where to write the new file
	@param comments    Comments associated with the stream
	@return Error code
	 */
	final int ope_encoder_continue_new_file(final String path, final JOggOpusComments comments) {
		JStdioObject obj = new JStdioObject();
		// if( !(obj = malloc(sizeof(*obj))) ) return OPE_ALLOC_FAIL;
		try {
			obj.file = new RandomAccessFile( path, "rw" );
		} catch(final Exception e) {
			obj = null;
			/* By trying to open the file first, we can recover if we can't open it. */
			return OPE_CANNOT_OPEN;
			// throw new IOException( ope_strerror( OPE_CANNOT_OPEN ) );
		}
		final int ret = ope_encoder_continue_new_callbacks( /* obj, */ comments );
		if( ret == OPE_OK ) {
			return ret;
		}
		try { obj.file.close(); } catch( final IOException e ) {}
		obj = null;
		return ret;
	}

	/** Ends the stream and create a new file (callback-based).
	@param[in,out] enc Encoder
	@param user_data   Pointer to be associated with the new stream and passed to the callbacks
	@param comments    Comments associated with the stream
	@return Error code
	 */
	private final int ope_encoder_continue_new_callbacks(/* final Object user_data,*/ final JOggOpusComments comments) {
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		// assert(enc.streams);
		// assert(enc.last_stream);
		final JEncStream new_stream = JEncStream.stream_create( comments );
		if( null == new_stream ) {
			return OPE_ALLOC_FAIL;
		}
		// new_stream.user_data = user_data;
		new_stream.end_granule = this.write_granule;
		this.last_stream.next = new_stream;
		this.last_stream = new_stream;
		return OPE_OK;
	}

	/** Write out the header now rather than wait for audio to begin.
	@param[in,out] enc Encoder
	@return Error code
	 */
	final int ope_encoder_flush_header() {
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		if( this.last_stream.header_is_frozen ) {
			return OPE_TOO_LATE;
		}
		if( this.last_stream.stream_is_init ) {
			return OPE_TOO_LATE;
		}// else {
			init_stream();
		//}
		return OPE_OK;
	}

	private static final String ope_error_strings[] = {
			"cannot open file",
			"call cannot be made at this point",
			"invalid picture file",
			"invalid icon file (pictures of type 1 MUST be 32x32 PNGs)",
			"write failed",
			"close failed"
		};

	/** Converts a libopusenc error code into a human readable string.
	  *
	  * @param error Error number
	  * @returns Error string
	  */
	public static final String ope_strerror(final int error ) {
		if( error == 0 ) {
			return "success";
		} else if( error >= -10 ) {
			return "unknown error";
		} else if( error > -30 ) {
			return Jcelt.opus_strerror( error + 10 );
		} else if( error >= OPE_CLOSE_FAIL ) {
			return ope_error_strings[-error - 30];
		} else {
			return "unknown error";
		}
	}

	/** Returns a string representing the version of libopusenc being used at run time.
	@return A string describing the version of this library */
	static final String ope_get_version_string()
	{
		return "libopusenc " + PACKAGE_VERSION;
	}

	/** ABI version for this header. Can be used to check for features at run time.
	@return An integer representing the ABI version */
	static final int ope_get_abi_version() {
		return OPE_ABI_VERSION;
	}

	private static final void extend_signal(final float[] x, int xoffset, int before, final int after, final int channels) {
		if( after == 0 ) {
			return;
		}
		before = (LPC_INPUT <= before) ? LPC_INPUT : before;
		if( before < 4 * LPC_ORDER ) {
			for( final int ie = xoffset + after * channels; xoffset < ie; xoffset++ ) {
				x[xoffset] = 0;
			}
			return;
		}
		final float window[] = new float[ LPC_PADDING ];
		{
			/* Generate Window using a resonating IIR aka Goertzel's algorithm. */
			float m0 = 1f, m1 = .5f * LPC_GOERTZEL_CONST;
			final float a1 = LPC_GOERTZEL_CONST;
			window[0] = 1f;
			for( int i = 1; i < LPC_PADDING; i++ ) {
				window[i] = a1 * m0 - m1;
				m1 = m0;
				m0 = window[i];
			}
			for( int i = 0; i < LPC_PADDING; i++ ) {
				window[i] = .5f + .5f * window[i];
			}
		}
		final float lpc[] = new float[ LPC_ORDER ];
		for( int ce = xoffset + channels, cb = channels * before, ie = xoffset + after * channels; xoffset < ce; xoffset++, ie++ ) {
			vorbis_lpc_from_data( x, xoffset - cb, lpc, before, channels );
			for( int i = xoffset; i < ie; i += channels ) {
				float sum = 0;
				for( int j = 0, xi = i - channels; j < LPC_ORDER; j++, xi -= channels ) {
					// sum -= x[xoffset + (i - j - 1) * channels] * lpc[j];
					sum -= x[xi] * lpc[j];
				}
				x[i] = sum;
			}
			for( int i = 0, xi = xoffset; i < after; i++, xi += channels ) {
				x[xi] *= window[i];
			}
		}
	}

/* Some of these routines (autocorrelator, LPC coefficient estimator)
   are derived from code written by Jutta Degener and Carsten Bormann;
   thus we include their copyright below.  The entirety of this file
   is freely redistributable on the condition that both of these
   copyright notices are preserved without modification.  */

/* Preserved Copyright: *********************************************/

/* Copyright 1992, 1993, 1994 by Jutta Degener and Carsten Bormann,
Technische Universita"t Berlin

Any use of this software is permitted provided that this notice is not
removed and that neither the authors nor the Technische Universita"t
Berlin are deemed to have made any representations as to the
suitability of this software for any purpose nor are held responsible
for any defects of this software. THERE IS ABSOLUTELY NO WARRANTY FOR
THIS SOFTWARE.

As a matter of courtesy, the authors request to be informed about uses
this software has found, about bugs in this software, and about any
improvements that may be of general interest.

Berlin, 28.11.1994
Jutta Degener
Carsten Bormann

*********************************************************************/

	private static final void vorbis_lpc_from_data(final float[] data, final int doffset, final float[] lpci, int n, final int stride) {
		final double aut[] = new double[LPC_ORDER + 1];
		final double lpc[] = new double[LPC_ORDER];

		/* FIXME: Apply a window to the input. */
		/* autocorrelation, p+1 lag coefficients */
		int j = LPC_ORDER + 1;
		n = doffset + n * stride;// java
		for( int k = j * stride; j-- != 0; k += stride ) {// java
			double d = 0; /* double needed for accumulator depth */
			for( int i = doffset + k; i < n; i += stride ) {
				// d += (double)data[doffset + i * stride] * data[doffset + (i - j) * stride];
				d += (double)data[ i ] * data[i - k];
			}
			aut[j] = d;
		}

		/* Apply lag windowing (better than bandwidth expansion) */
		if( LPC_ORDER <= 64 ) {
			for( int i = 1; i <= LPC_ORDER; i++ ) {
				/* Approximate this gaussian for low enough order. */
				/* aut[i] *=  exp(-.5*(2*M_PI*.002*i)*(2*M_PI*.002*i));*/
				aut[i] -= aut[i] * (0.008f * 0.008f) * i * i;
			}
		}
		/* Generate lpc coefficients from autocorr values */

		/* set our noise floor to about -100dB */
		double error = aut[0] * (1. + 1e-7);
		final double epsilon = 1e-6 * aut[0] + 1e-7;

		int i;
		for( i = 0; i < LPC_ORDER; i++ ) {
			double r = -aut[i + 1];

			if( error < epsilon ) {
				// memset( lpc + i, 0, (LPC_ORDER - i) * sizeof(*lpc) );
				Arrays.fill( lpc, i, LPC_ORDER, 0 );
				// goto done;
				break;
			}

			/* Sum up this iteration's reflection coefficient; note that in
			   Vorbis we don't save it.  If anyone wants to recycle this code
			   and needs reflection coefficients, save the results of 'r' from
			   each iteration. */

			j = 0;
			for( int k = i; j < i; j++, k-- ) {
				r -= lpc[j] * aut[k];
			}
			r /= error;

			/* Update LPC coefficients and total error */

			lpc[i] = r;
			j = 0;
			for( int je = i >>> 1, k = i - 1 - j; j < je; j++, k-- ) {
				final double tmp = lpc[j];

				lpc[j] += r * lpc[k];
				lpc[k] += r * tmp;
			}
			if( (i & 1) != 0 ) {
				lpc[j] += lpc[j] * r;
			}

			error *= 1. - r * r;
		}

// done:
		/* slightly damp the filter */
		{
			final double g = .999;
			double damp = g;
			for( j = 0; j < LPC_ORDER; j++ ) {
				lpc[j] *= damp;
				damp *= g;
			}
		}

		for( j = 0; j < LPC_ORDER; j++ ) {
			lpci[j] = (float)lpc[j];
		}
	}


	/** Goes straight to the libopus ctl() functions. */
	public final int ope_encoder_ctl(final int request, final int value) {
		// va_list ap;
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		// va_start(ap, request);
		int ret = OPE_OK;
		switch( request ) {
		case Jopus_defines.OPUS_SET_APPLICATION_REQUEST:
		case Jopus_defines.OPUS_SET_BITRATE_REQUEST:
		case Jopus_defines.OPUS_SET_MAX_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_SET_COMPLEXITY_REQUEST:
		case Jopus_defines.OPUS_SET_PACKET_LOSS_PERC_REQUEST:
		case Jopus_defines.OPUS_SET_FORCE_CHANNELS_REQUEST:
		case Jopus_defines.OPUS_SET_SIGNAL_REQUEST:
		case Jopus_defines.OPUS_SET_LSB_DEPTH_REQUEST:
		{
			ret = this.st.opeint_encoder_ctl2( request, value );
		}
		break;
		case Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION_REQUEST:
		{
			int max_supported = Jopus_defines.OPUS_FRAMESIZE_60_MS;
// #ifdef OPUS_FRAMESIZE_120_MS
			max_supported = Jopus_defines.OPUS_FRAMESIZE_120_MS;
// #endif
			if( value < Jopus_defines.OPUS_FRAMESIZE_2_5_MS || value > max_supported ) {
				ret = Jopus_defines.OPUS_UNIMPLEMENTED;
				break;
			}
			ret = this.st.opeint_encoder_ctl( Jopus_defines.OPUS_SET_EXPERT_FRAME_DURATION, value );
			if( ret == Jopus_defines.OPUS_OK ) {
				this.frame_size = compute_frame_samples( value );
				this.frame_size_request = value;
			}
		}
		break;
		/* ****************** libopusenc-specific requests. ********************** */
		case OPE_SET_DECISION_DELAY_REQUEST:
		{
			if( value < 0 ) {
				ret = OPE_BAD_ARG;
				break;
			}
			this.decision_delay = (value <= MAX_LOOKAHEAD ? value : MAX_LOOKAHEAD);
		}
		break;
		case OPE_SET_MUXING_DELAY_REQUEST:
		{
			if( value < 0 ) {
				ret = OPE_BAD_ARG;
				break;
			}
			this.max_ogg_delay = value;
			if( this.oggp != null ) {
				this.oggp.oggp_set_muxing_delay( this.max_ogg_delay );
			}
		}
		break;
		case OPE_SET_COMMENT_PADDING_REQUEST:
		{
			if( value < 0 ) {
				ret = OPE_BAD_ARG;
				break;
			}
			this.comment_padding = value;
			ret = OPE_OK;
		}
		break;
		case OPE_SET_SERIALNO_REQUEST:
		{
			if( null == this.last_stream || this.last_stream.header_is_frozen ) {
				ret = OPE_TOO_LATE;
				break;
			}
			this.last_stream.serialno = value;
			this.last_stream.serialno_is_set = true;
			ret = OPE_OK;
		}
		break;
		case OPE_SET_HEADER_GAIN_REQUEST:
		{
			if( null == this.last_stream || this.last_stream.header_is_frozen ) {
				ret = OPE_TOO_LATE;
				break;
			}
			this.header.gain = value;
			ret = OPE_OK;
		}
		break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		// va_end(ap);
		final boolean translate = ret != 0 && (request < 14000 || (ret < 0 && ret >= -10));
		if( translate ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
		}
		// assert(ret == 0 || ret < -10);
		return ret;
	}

	/** Goes straight to the libopus ctl() functions. */
	public final int ope_encoder_ctl(final int request, final boolean value) {
		// va_list ap;
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		// va_start(ap, request);
		int ret = OPE_OK;
		switch( request ) {
		case Jopus_defines.OPUS_SET_VBR_REQUEST:
		case Jopus_defines.OPUS_SET_INBAND_FEC_REQUEST:
		case Jopus_defines.OPUS_SET_DTX_REQUEST:
		case Jopus_defines.OPUS_SET_VBR_CONSTRAINT_REQUEST:
		case Jopus_defines.OPUS_SET_PREDICTION_DISABLED_REQUEST:
//#ifdef OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
//#endif
		{
			ret = this.st.opeint_encoder_ctl2( request, value );
		}
		break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		// va_end(ap);
		final boolean translate = ret != 0 && (request < 14000 || (ret < 0 && ret >= -10));
		if( translate ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
		}
		// assert(ret == 0 || ret < -10);
		return ret;
	}
	////
	/** Sets encoder options.
	 * @param enc [in,out] Encoder
	 * @param request     Use a request macro
	 * @return Error code
	 */
	public final int ope_encoder_ctl(final int request, final Iope_packet_func value) {
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		int ret = OPE_OK;
		switch( request ) {
		case OPE_SET_PACKET_CALLBACK_REQUEST:
		{
			// final ope_packet_func value = va_arg(ap, ope_packet_func);
			// void *data = va_arg(ap, void *);
			this.packet_callback = value;
			// enc.packet_callback_data = data;
			// ret = OPE_OK;
		}
		break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		final boolean translate = ret != 0 && (request < 14000 || (ret < 0 && ret >= -10));
		if( translate ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
		}
		return ret;
	}

	/** Goes straight to the libopus ctl() functions. */
	public final int ope_encoder_ctl(final int request, final Object[] arg) {
		// va_list ap;
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		// va_start(ap, request);
		int ret = OPE_OK;
		switch( request ) {
		case Jopus_defines.OPUS_GET_LOOKAHEAD_REQUEST:
		{
			ret = this.st.opeint_encoder_ctl( Jopus_defines.OPUS_GET_LOOKAHEAD, arg );
		}
		break;
		case Jopus_defines.OPUS_GET_APPLICATION_REQUEST:
		case Jopus_defines.OPUS_GET_BITRATE_REQUEST:
		case Jopus_defines.OPUS_GET_MAX_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_GET_VBR_REQUEST:
		case Jopus_defines.OPUS_GET_BANDWIDTH_REQUEST:
		case Jopus_defines.OPUS_GET_COMPLEXITY_REQUEST:
		case Jopus_defines.OPUS_GET_INBAND_FEC_REQUEST:
		case Jopus_defines.OPUS_GET_PACKET_LOSS_PERC_REQUEST:
		case Jopus_defines.OPUS_GET_DTX_REQUEST:
		case Jopus_defines.OPUS_GET_VBR_CONSTRAINT_REQUEST:
		case Jopus_defines.OPUS_GET_FORCE_CHANNELS_REQUEST:
		case Jopus_defines.OPUS_GET_SIGNAL_REQUEST:
		case Jopus_defines.OPUS_GET_LSB_DEPTH_REQUEST:
		case Jopus_defines.OPUS_GET_PREDICTION_DISABLED_REQUEST:
// #ifdef OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
// #endif
		{
			ret = this.st.opeint_encoder_ctl2( request, arg );
		}
		break;

		/* ****************** libopusenc-specific requests. ********************** */
		case OPE_GET_DECISION_DELAY_REQUEST:
		{
			arg[0] = Integer.valueOf( this.decision_delay );
		}
		break;
		case OPE_GET_MUXING_DELAY_REQUEST:
		{
			arg[0] = Integer.valueOf( this.max_ogg_delay );
		}
		break;
		case OPE_GET_COMMENT_PADDING_REQUEST:
		{
			arg[0] = Integer.valueOf( this.comment_padding );
		}
		break;
		case OPE_GET_SERIALNO_REQUEST:
		{
			arg[0] = Integer.valueOf( this.last_stream.serialno );
		}
		break;
		case OPE_GET_HEADER_GAIN_REQUEST:
		{
			arg[0] = Integer.valueOf( this.header.gain );
		}
		break;
		case OPE_GET_NB_STREAMS_REQUEST:
		{
			arg[0] = Integer.valueOf( this.header.nb_streams );
		}
		break;
		case OPE_GET_NB_COUPLED_STREAMS_REQUEST:
		{
			arg[0] = Integer.valueOf( this.header.nb_coupled );
		}
		break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		// va_end(ap);
		final boolean translate = (ret != 0) && (request < 14000 || (ret < 0 && ret >= -10));
		if( translate ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
		}
		// assert(ret == 0 || ret < -10);
		return ret;
	}

	/** Sets encoder options.
	 * @param enc [in,out] Encoder
	 * @param request     Use a request macro
	 * @param streamId the stream id
	 * @param arg the helper object to return the data
	 * @return Error code
	 */
	public final int ope_encoder_ctl(final int request, final int streamId, final Object[] arg)
	{
		if( this.unrecoverable != 0 ) {
			return this.unrecoverable;
		}
		int ret = Jopus_defines.OPUS_OK;

		switch( request )
		{
		case JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE_REQUEST:
		{
			ret = this.st.opeint_encoder_ctl( JOpusMSEncoder.OPUS_MULTISTREAM_GET_ENCODER_STATE, streamId, arg );
		}
		break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			ret = Jopus_defines.OPUS_UNIMPLEMENTED;
			break;
		}
		// va_end(ap);
		final boolean translate = (ret != 0) && (request < 14000 || (ret < 0 && ret >= -10));
		if( translate ) {
			if( ret == Jopus_defines.OPUS_BAD_ARG ) {
				ret = OPE_BAD_ARG;
			} else if( ret == Jopus_defines.OPUS_INTERNAL_ERROR ) {
				ret = OPE_INTERNAL_ERROR;
			} else if( ret == Jopus_defines.OPUS_UNIMPLEMENTED ) {
				ret = OPE_UNIMPLEMENTED;
			} else if( ret == Jopus_defines.OPUS_ALLOC_FAIL ) {
				ret = OPE_ALLOC_FAIL;
			} else {
				ret = OPE_INTERNAL_ERROR;
			}
		}
		// assert(ret == 0 || ret < -10);
		return ret;
	}
}
