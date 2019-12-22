package opus;

import celt.Jmath_ops;

/* Copyright (c) 2017 Google Inc.
   Written by Andrew Allen */
/*
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

/** Opus projection encoder state.
 * This contains the complete state of a projection Opus encoder.
 * It is position independent and can be freely copied.
 * @see opus_projection_ambisonics_encoder_create
 */
public final class JOpusProjectionEncoder {
	// TODO java remove mixing_matrix_size_in_bytes and demixing_matrix_size_in_bytes
	int mixing_matrix_size_in_bytes;
	int demixing_matrix_size_in_bytes;
	/* Encoder states go here */
	private JMappingMatrix mMixingMatrix;
	private JMappingMatrix mDemixingMatrix;
	private JOpusMSEncoder mMultistreamEncoder;

// #if !defined(DISABLE_FLOAT_API)
	// opus_projection_copy_channel_in_float
	private static final class Jopus_projection_copy_channel_in_float extends Iopus_copy_channel_in {

		@Override
		final void copy_channel_in(final float[] dst, final int doffset, final int dst_stride,
				final Object src, final int scroffset, final int src_stride, final int src_channel,
				final int frame_size, final Object user_data) {
			((JMappingMatrix)user_data).mapping_matrix_multiply_channel_in_float(
					(float[])src, scroffset, src_stride, dst, doffset, src_channel, dst_stride, frame_size);
		}

	}

	private static final Jopus_projection_copy_channel_in_float opus_projection_copy_channel_in_float = new Jopus_projection_copy_channel_in_float();

// #endif

	// opus_projection_copy_channel_in_short
	private static final class Jopus_projection_copy_channel_in_short extends Iopus_copy_channel_in {

		@Override
		final void copy_channel_in(final float[] dst, final int doffset, final int dst_stride,
				final Object src, final int scroffset, final int src_stride, final int src_channel,
				final int frame_size, final Object user_data) {
			((JMappingMatrix)user_data).mapping_matrix_multiply_channel_in_short(
					(short[])src, scroffset, src_stride, dst, doffset, src_channel, dst_stride, frame_size);
		}

	}

	private static final Jopus_projection_copy_channel_in_short opus_projection_copy_channel_in_short = new Jopus_projection_copy_channel_in_short();

	private static int get_order_plus_one_from_channels(final int channels, final int[] order_plus_one)
	{
		/* Allowed numbers of channels:
		 * (1 + n)^2 + 2j, for n = 0...14 and j = 0 or 1.
		 */
		if( channels < 1 || channels > 227 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final int order_plus_one_ = Jmath_ops.isqrt32( channels );
		final int acn_channels = order_plus_one_ * order_plus_one_;
		final int nondiegetic_channels = channels - acn_channels;
		if( nondiegetic_channels != 0 && nondiegetic_channels != 2 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		if( null != order_plus_one ) {
			order_plus_one[0] = order_plus_one_;
		}
		return Jopus_defines.OPUS_OK;
	}

	private static int get_streams_from_channels(final int channels, final int mapping_family,
				final Jms_encoder_data_aux data,// java final int[] streams, final int[] coupled_streams,
				final int[] order_plus_one)
	{
		if( mapping_family == 3 )
		{
			if( get_order_plus_one_from_channels( channels, order_plus_one ) != Jopus_defines.OPUS_OK ) {
				return Jopus_defines.OPUS_BAD_ARG;
			}
			if( null != data ) {
				data.streams = (channels + 1) >>> 1;
				data.coupled_streams = channels >>> 1;
			}
			return Jopus_defines.OPUS_OK;
		}
		return Jopus_defines.OPUS_BAD_ARG;
	}
/*
	private final JMappingMatrix get_mixing_matrix()
	{
		return mMixingMatrix;
	}

	private final JMappingMatrix get_enc_demixing_matrix()
	{
		return mDemixingMatrix;
	}

	private final JOpusMSEncoder get_multistream_encoder()
	{
		return mMultistreamEncoder;
	}
*/
	/** Gets the size of an OpusProjectionEncoder structure.
	  * @param channels <tt>int</tt>: The total number of input channels to encode.
	  *                               This must be no more than 255.
	  * @param mapping_family <tt>int</tt>: The mapping family to use for selecting
	  *                                     the appropriate projection.
	  * @returns The size in bytes on success, or a negative error code
	  *          (see @ref opus_errorcodes) on error.
	  */
/* java: replaced by the class members
	private static int opus_projection_ambisonics_encoder_get_size(final int channels,
					final int mapping_family)
	{
		final int nb_streams;
		final int nb_coupled_streams;
		final int order_plus_one;

		final int ret = get_streams_from_channels( channels, mapping_family, &nb_streams,
						&nb_coupled_streams, &order_plus_one );
		if( ret != Jopus_defines.OPUS_OK ) {
			return 0;
		}

		int mixing_matrix_rows, mixing_matrix_cols, demixing_matrix_rows, demixing_matrix_cols;
		if( order_plus_one == 2 )
		{
			mixing_matrix_rows = JMappingMatrix.mapping_matrix_foa_mixing.rows;
			mixing_matrix_cols = JMappingMatrix.mapping_matrix_foa_mixing.cols;
			demixing_matrix_rows = JMappingMatrix.mapping_matrix_foa_demixing.rows;
			demixing_matrix_cols = JMappingMatrix.mapping_matrix_foa_demixing.cols;
		}
		else if( order_plus_one == 3 )
		{
			mixing_matrix_rows = JMappingMatrix.mapping_matrix_soa_mixing.rows;
			mixing_matrix_cols = JMappingMatrix.mapping_matrix_soa_mixing.cols;
			demixing_matrix_rows = JMappingMatrix.mapping_matrix_soa_demixing.rows;
			demixing_matrix_cols = JMappingMatrix.mapping_matrix_soa_demixing.cols;
		}
		else if( order_plus_one == 4 )
		{
			mixing_matrix_rows = JMappingMatrix.mapping_matrix_toa_mixing.rows;
			mixing_matrix_cols = JMappingMatrix.mapping_matrix_toa_mixing.cols;
			demixing_matrix_rows = JMappingMatrix.mapping_matrix_toa_demixing.rows;
			demixing_matrix_cols = JMappingMatrix.mapping_matrix_toa_demixing.cols;
		} else {
			return 0;
		}

		final int mixing_matrix_size = mapping_matrix_get_size( mixing_matrix_rows, mixing_matrix_cols );
		if( 0 == mixing_matrix_size ) {
			return 0;
		}

		final int demixing_matrix_size = mapping_matrix_get_size( demixing_matrix_rows, demixing_matrix_cols );
		if( 0 == demixing_matrix_size ) {
			return 0;
		}

		final int encoder_size = opus_multistream_encoder_get_size( nb_streams, nb_coupled_streams );
		if( 0 == encoder_size ) {
			return 0;
		}

		return align(sizeof(OpusProjectionEncoder)) +
				mixing_matrix_size + demixing_matrix_size + encoder_size;
	}
*/
	/** Initialize a previously allocated projection encoder state.
	  * The memory pointed to by \a st must be at least the size returned by
	  * opus_projection_ambisonics_encoder_get_size().
	  * This is intended for applications which use their own allocator instead of
	  * malloc.
	  * To reset a previously initialized state, use the #OPUS_RESET_STATE CTL.
	  * @see opus_projection_ambisonics_encoder_create
	  * @see opus_projection_ambisonics_encoder_get_size
	  * @param st <tt>OpusProjectionEncoder*</tt>: Projection encoder state to initialize.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate of the input signal (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels in the input signal.
	  *                               This must be at most 255.
	  *                               It may be greater than the number of
	  *                               coded channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param streams <tt>int</tt>: The total number of streams to encode from the
	  *                              input.
	  *                              This must be no more than the number of channels.
	  * @param coupled_streams <tt>int</tt>: Number of coupled (2 channel) streams
	  *                                      to encode.
	  *                                      This must be no larger than the total
	  *                                      number of streams.
	  *                                      Additionally, The total number of
	  *                                      encoded channels (<code>streams +
	  *                                      coupled_streams</code>) must be no
	  *                                      more than the number of input channels.
	  * @param application <tt>int</tt>: The target encoder application.
	  *                                  This must be one of the following:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @returns #OPUS_OK on success, or an error code (see @ref opus_errorcodes)
	  *          on failure.
	  */
	private final int opus_projection_ambisonics_encoder_init(// final JOpusProjectionEncoder st,
											final int Fs, final int channels, final int mapping_family,
											final Jms_encoder_data_aux data,// java final int[] streams, final int[] coupled_streams,
											final int application)
	{
		if( data == null ) {// if( streams == null || coupled_streams == null ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final int order_plus_one[] = new int[1];// java
		// if( get_streams_from_channels( channels, mapping_family, streams, coupled_streams, order_plus_one ) != Jopus_defines.OPUS_OK ) {
		if( get_streams_from_channels( channels, mapping_family, data, order_plus_one ) != Jopus_defines.OPUS_OK ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		JMappingMatrix mixing_matrix = null;
		JMappingMatrix demixing_matrix = null;
		if( mapping_family == 3 )
		{
			/* Assign mixing matrix based on available pre-computed matrices. */
			this.mMixingMatrix = new JMappingMatrix();// java
			mixing_matrix = this.mMixingMatrix;// st.get_mixing_matrix();
			if( order_plus_one[0] == 2 )
			{
				mixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_foa_mixing.rows,
						JMappingMatrix.mapping_matrix_foa_mixing.cols, JMappingMatrix.mapping_matrix_foa_mixing.gain,
						JMappingMatrix.mapping_matrix_foa_mixing_data );//,
						//JMappingMatrix.mapping_matrix_foa_mixing_data.length );
			}
			else if( order_plus_one[0] == 3 )
			{
				mixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_soa_mixing.rows,
						JMappingMatrix.mapping_matrix_soa_mixing.cols, JMappingMatrix.mapping_matrix_soa_mixing.gain,
						JMappingMatrix.mapping_matrix_soa_mixing_data );//,
						//JMappingMatrix.mapping_matrix_soa_mixing_data.length );
			}
			else if( order_plus_one[0] == 4 )
			{
				mixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_toa_mixing.rows,
						JMappingMatrix.mapping_matrix_toa_mixing.cols, JMappingMatrix.mapping_matrix_toa_mixing.gain,
						JMappingMatrix.mapping_matrix_toa_mixing_data );//,
						//JMappingMatrix.mapping_matrix_toa_mixing_data.length );
			} else {
				return Jopus_defines.OPUS_BAD_ARG;
			}

			/* java: don't need st.mixing_matrix_size_in_bytes = JMappingMatrix.mapping_matrix_get_size( mixing_matrix.rows, mixing_matrix.cols );
			if( 0 == st.mixing_matrix_size_in_bytes ) {
				return Jopus_defines.OPUS_BAD_ARG;
			} */

			/* Assign demixing matrix based on available pre-computed matrices. */
			this.mDemixingMatrix = new JMappingMatrix();// java
			demixing_matrix = this.mDemixingMatrix;// get_enc_demixing_matrix(st);
			if( order_plus_one[0] == 2 )
			{
				demixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_foa_demixing.rows,
						JMappingMatrix.mapping_matrix_foa_demixing.cols, JMappingMatrix.mapping_matrix_foa_demixing.gain,
						JMappingMatrix.mapping_matrix_foa_demixing_data );//,
						// JMappingMatrix.mapping_matrix_foa_demixing_data.length );
			}
			else if( order_plus_one[0] == 3 )
			{
				demixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_soa_demixing.rows,
						JMappingMatrix.mapping_matrix_soa_demixing.cols, JMappingMatrix.mapping_matrix_soa_demixing.gain,
						JMappingMatrix.mapping_matrix_soa_demixing_data );//,
						// JMappingMatrix.mapping_matrix_soa_demixing_data.length );
			}
			else if( order_plus_one[0] == 4 )
			{
				demixing_matrix.mapping_matrix_init( JMappingMatrix.mapping_matrix_toa_demixing.rows,
						JMappingMatrix.mapping_matrix_toa_demixing.cols, JMappingMatrix.mapping_matrix_toa_demixing.gain,
						JMappingMatrix.mapping_matrix_toa_demixing_data );//,
						//JMappingMatrix.mapping_matrix_toa_demixing_data.length );
			} else {
				return Jopus_defines.OPUS_BAD_ARG;
			}

			/* java: don't need st.demixing_matrix_size_in_bytes = JMappingMatrix.mapping_matrix_get_size( demixing_matrix.rows, demixing_matrix.cols );
			if( 0 == st.demixing_matrix_size_in_bytes ) {
				return Jopus_defines.OPUS_BAD_ARG;
			} */
		} else {
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}

		/* Ensure matrices are large enough for desired coding scheme. */
		if( data.streams + data.coupled_streams > mixing_matrix.rows ||
				channels > mixing_matrix.cols ||
				channels > demixing_matrix.rows ||
				data.streams + data.coupled_streams > demixing_matrix.cols ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final char mapping[] = new char[255];// FIXME why 255?
		/* Set trivial mapping so each input channel pairs with a matrix column. */
		for( int i = 0; i < channels; i++ ) {
			mapping[i] = (char)i;
		}

		/* Initialize multistream encoder with provided settings. */
		// final JOpusMSEncoder ms_encoder = get_multistream_encoder( st );
		this.mMultistreamEncoder = new JOpusMSEncoder();// java
		final int ret = this.mMultistreamEncoder.opus_multistream_encoder_init( Fs, channels,
				data.streams, data.coupled_streams, mapping, application );
		return ret;
	}

	/** Allocates and initializes a projection encoder state.
	  * Call opus_projection_encoder_destroy() to release
	  * this object when finished.
	  * @param Fs <tt>opus_int32</tt>: Sampling rate of the input signal (in Hz).
	  *                                This must be one of 8000, 12000, 16000,
	  *                                24000, or 48000.
	  * @param channels <tt>int</tt>: Number of channels in the input signal.
	  *                               This must be at most 255.
	  *                               It may be greater than the number of
	  *                               coded channels (<code>streams +
	  *                               coupled_streams</code>).
	  * @param mapping_family <tt>int</tt>: The mapping family to use for selecting
	  *                                     the appropriate projection.
	  * @param streams [out] <tt>int *</tt>: The total number of streams that will
	  *                                     be encoded from the input.
	  * @param coupled_streams [out] <tt>int *</tt>: Number of coupled (2 channel)
	  *                                 streams that will be encoded from the input.
	  * @param application <tt>int</tt>: The target encoder application.
	  *                                  This must be one of the following:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @param error [out] <tt>int *</tt>: Returns #OPUS_OK on success, or an error
	  *                                   code (see @ref opus_errorcodes) on
	  *                                   failure.
	  */
	public static final JOpusProjectionEncoder opus_projection_ambisonics_encoder_create(
				final int Fs, final int channels, final int mapping_family,
				final Jms_encoder_data_aux data,// java final int[] streams, final int[] coupled_streams,
				final int application, final int[] error)
	{
		/* java: don't need
		// Allocate space for the projection encoder.
		final int size = opus_projection_ambisonics_encoder_get_size( channels, mapping_family );
		if( 0 == size ) {
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}
		JOpusProjectionEncoder st = (JOpusProjectionEncoder)opus_alloc(size);
		if( null == st )
		{
			if( null != error ) {
				error[0] = Jopus_defines.OPUS_ALLOC_FAIL;
			}
			return null;
		}*/
		JOpusProjectionEncoder st = new JOpusProjectionEncoder();

		/* Initialize projection encoder with provided settings. */
		final int ret = st.opus_projection_ambisonics_encoder_init( Fs, channels, mapping_family,
				data,// java streams, coupled_streams,
				application );
		if( ret != Jopus_defines.OPUS_OK )
		{
			// opus_free( st );
			st = null;
		}
		if( null != error ) {
			error[0] = ret;
		}
		return st;
	}

	/** Encodes a projection Opus frame.
	  * @param st <tt>OpusProjectionEncoder*</tt>: Projection encoder state.
	  * @param pcm [in] <tt>const opus_int16*</tt>: The input signal as interleaved
	  *                                            samples.
	  *                                            This must contain
	  *                                            <code>frame_size*channels</code>
	  *                                            samples.
	  * @param frame_size <tt>int</tt>: Number of samples per channel in the input
	  *                                 signal.
	  *                                 This must be an Opus frame size for the
	  *                                 encoder's sampling rate.
	  *                                 For example, at 48 kHz the permitted values
	  *                                 are 120, 240, 480, 960, 1920, and 2880.
	  *                                 Passing in a duration of less than 10 ms
	  *                                 (480 samples at 48 kHz) will prevent the
	  *                                 encoder from using the LPC or hybrid modes.
	  * @param data [out] <tt>unsigned char*</tt>: Output payload.
	  *                                           This must contain storage for at
	  *                                           least \a max_data_bytes.
	  * @param max_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
	  *                                                 memory for the output
	  *                                                 payload. This may be
	  *                                                 used to impose an upper limit on
	  *                                                 the instant bitrate, but should
	  *                                                 not be used as the only bitrate
	  *                                                 control. Use #OPUS_SET_BITRATE to
	  *                                                 control the bitrate.
	  * @returns The length of the encoded packet (in bytes) on success or a
	  *          negative error code (see @ref opus_errorcodes) on failure.
	  */
	public final int opus_projection_encode(final short[] pcm,
							final int frame_size, final byte[] data, final int max_data_bytes)
	{
		return mMultistreamEncoder.opus_multistream_encode_native(
				opus_projection_copy_channel_in_short, pcm, 0, frame_size, data, 0,
				max_data_bytes, 16, JOpusEncoder.downmix_int, false, mMixingMatrix );// get_mixing_matrix(st) );
	}

// #ifndef DISABLE_FLOAT_API
/* #ifdef FIXED_POINT
	private static int opus_projection_encode_float(OpusProjectionEncoder *st, const float *pcm,
								int frame_size, unsigned char *data,
								opus_int32 max_data_bytes)
	{
		return opus_multistream_encode_native(get_multistream_encoder(st),
				opus_projection_copy_channel_in_float, pcm, frame_size, data,
				max_data_bytes, 16, downmix_float, 1, get_mixing_matrix(st));
	}
#else */
	/** Encodes a projection Opus frame from floating point input.
	  * @param st <tt>OpusProjectionEncoder*</tt>: Projection encoder state.
	  * @param pcm [in] <tt>const float*</tt>: The input signal as interleaved
	  *                                       samples with a normal range of
	  *                                       +/-1.0.
	  *                                       Samples with a range beyond +/-1.0
	  *                                       are supported but will be clipped by
	  *                                       decoders using the integer API and
	  *                                       should only be used if it is known
	  *                                       that the far end supports extended
	  *                                       dynamic range.
	  *                                       This must contain
	  *                                       <code>frame_size*channels</code>
	  *                                       samples.
	  * @param frame_size <tt>int</tt>: Number of samples per channel in the input
	  *                                 signal.
	  *                                 This must be an Opus frame size for the
	  *                                 encoder's sampling rate.
	  *                                 For example, at 48 kHz the permitted values
	  *                                 are 120, 240, 480, 960, 1920, and 2880.
	  *                                 Passing in a duration of less than 10 ms
	  *                                 (480 samples at 48 kHz) will prevent the
	  *                                 encoder from using the LPC or hybrid modes.
	  * @param data [out] <tt>unsigned char*</tt>: Output payload.
	  *                                           This must contain storage for at
	  *                                           least \a max_data_bytes.
	  * @param max_data_bytes [in] <tt>opus_int32</tt>: Size of the allocated
	  *                                                 memory for the output
	  *                                                 payload. This may be
	  *                                                 used to impose an upper limit on
	  *                                                 the instant bitrate, but should
	  *                                                 not be used as the only bitrate
	  *                                                 control. Use #OPUS_SET_BITRATE to
	  *                                                 control the bitrate.
	  * @returns The length of the encoded packet (in bytes) on success or a
	  *          negative error code (see @ref opus_errorcodes) on failure.
	  */
	public final int opus_projection_encode_float(final float[] pcm, final int pcmoffset,
								final int frame_size, final byte[] data, final int doffset, final int max_data_bytes)
	{
		return this.mMultistreamEncoder.opus_multistream_encode_native(
				opus_projection_copy_channel_in_float, pcm, pcmoffset, frame_size, data, doffset,
				max_data_bytes, 24, JOpusEncoder.downmix_float, true, this.mMixingMatrix );
	}
// #endif
//#endif

	// java: use st = null instead
	/** Frees an <code>OpusProjectionEncoder</code> allocated by
	  * opus_projection_ambisonics_encoder_create().
	  * @param st <tt>OpusProjectionEncoder*</tt>: Projection encoder state to be freed.
	  */
	/* private static void opus_projection_encoder_destroy(final JOpusProjectionEncoder st)
	{
		opus_free(st);
	} */

	// java: overloaded methods
	/**
	 * Set an integer value
	 *
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_projection_encoder_ctl(final int request, final int value) {
		return mMultistreamEncoder.opus_multistream_encoder_ctl( request, value );
	}

	/**
	 * Set a boolean value
	 *
	 * @param request
	 * @param value
	 * @return status
	 */
	public final int opus_projection_encoder_ctl(final int request, final boolean value) {
		return mMultistreamEncoder.opus_multistream_encoder_ctl( request, value );
	}

	/**
	 * A request without a parameter
	 *
	 * @param request
	 * @return status
	 */
	public final int opus_projection_encoder_ctl(final int request) {
		return mMultistreamEncoder.opus_multistream_encoder_ctl( request );
	}

	/**
	 * Set a complex parameter
	 *
	 * @param request
	 * @param streamId
	 * @param value
	 * @return status
	 */
	public final int opus_projection_encoder_ctl(final int request, final int streamId, final Object[] arg) {
		return mMultistreamEncoder.opus_multistream_encoder_ctl( request, streamId, arg );
	}

	/** Perform a CTL function on a projection Opus encoder.
	  *
	  * Generally the request and subsequent arguments are generated by a
	  * convenience macro.
	  * @param st <tt>OpusProjectionEncoder*</tt>: Projection encoder state.
	  * @param request This and all remaining parameters should be replaced by one
	  *                of the convenience macros in @ref opus_genericctls,
	  *                @ref opus_encoderctls, @ref opus_multistream_ctls, or
	  *                @ref opus_projection_ctls
	  * @see opus_genericctls
	  * @see opus_encoderctls
	  * @see opus_multistream_ctls
	  * @see opus_projection_ctls
	  */
	public final int opus_projection_encoder_ctl(final int request, final Object[] arg)
	{
		if( arg == null || arg.length == 0 )
		{
			return Jopus_defines.OPUS_BAD_ARG;
		}
		// va_list ap;
		int ret = Jopus_defines.OPUS_OK;

		final JOpusMSEncoder ms_encoder = mMultistreamEncoder;// get_multistream_encoder(st);
		final JMappingMatrix demixing_matrix = mDemixingMatrix;// get_enc_demixing_matrix(st);

		// va_start(ap, request);
		switch( request )
		{
		case Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST:
		{
			arg[ 0 ] = Integer.valueOf( ms_encoder.layout.nb_channels * (ms_encoder.layout.nb_streams
							+ ms_encoder.layout.nb_coupled_streams) * (Short.SIZE / 8) );
		}
		break;
		case Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN_REQUEST:
		{
			arg[ 0 ] = Integer.valueOf( demixing_matrix.gain );
		}
		break;
		/* java: moved to the separate call, case Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST:
		{
			if( ! (arg[ 0 ] instanceof char[]) ) {
				return Jopus_defines.OPUS_BAD_ARG;
			}
			final char[] external_char = (char[])arg[ 0 ];// va_arg(ap, unsigned char *);
			if( null == external_char )
			{
				return Jopus_defines.OPUS_BAD_ARG;
			}
			final int external_size = external_char.length;// va_arg(ap, opus_int32);
			final short[] internal_short = demixing_matrix.mMatrix;// JMappingMatrix.mapping_matrix_get_data( demixing_matrix );
			// (I/O is in relation to the decoder's perspective).
			final int nb_input_streams = ms_encoder.layout.nb_streams +
					ms_encoder.layout.nb_coupled_streams;
			final int nb_output_streams = ms_encoder.layout.nb_channels;
			final int internal_size = nb_input_streams * nb_output_streams * (Short.SIZE / 8);
			if( external_size != internal_size )
			{
				return Jopus_defines.OPUS_BAD_ARG;
			}

			// Copy demixing matrix subset to output destination.
			int l = 0;
			for( int i = 0, ie = demixing_matrix.rows * nb_input_streams; i < ie; i += demixing_matrix.rows ) {
				for( int j = i, je = j + nb_output_streams; j < je; j++ ) {
					final short v = internal_short[ j ];// java
					external_char[ l++ ] = (char)v;
					external_char[ l++ ] = (char)(v >>> 8);
				}
			}
		}
		break; */
		default:
		{
			// ret = ms_encoder.opus_multistream_encoder_ctl_va_list( request, arg );
			ret = ms_encoder.opus_multistream_encoder_ctl( request, arg );
		}
		break;
		}
		// va_end(ap);
		return ret;
	}

	// java: an overloaded variant to get data back
	/**
	 * OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST
	 *
	 * @param request the request
	 * @param matrix the array to get data back
	 * @param offset the array offset
	 * @param external_size the requested size
	 * @return status
	 */
	public final int opus_projection_encoder_ctl(final int request, final char[] matrix, final int offset, final int external_size)
	{
		final int ret = Jopus_defines.OPUS_OK;

		final JOpusMSEncoder ms_encoder = mMultistreamEncoder;// get_multistream_encoder(st);
		final JMappingMatrix demixing_matrix = mDemixingMatrix;// get_enc_demixing_matrix(st);

		// va_start(ap, request);
		switch( request )
		{
		case Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST:
		{
			if( null == matrix )
			{
				return Jopus_defines.OPUS_BAD_ARG;
			}
			final short[] internal_short = demixing_matrix.mMatrix;// JMappingMatrix.mapping_matrix_get_data( demixing_matrix );
			/* (I/O is in relation to the decoder's perspective). */
			final int nb_input_streams = ms_encoder.layout.nb_streams +
					ms_encoder.layout.nb_coupled_streams;
			final int nb_output_streams = ms_encoder.layout.nb_channels;
			final int internal_size = nb_input_streams * nb_output_streams * (Short.SIZE / 8);
			if( external_size != internal_size )
			{
				return Jopus_defines.OPUS_BAD_ARG;
			}

			/* Copy demixing matrix subset to output destination. */
			int l = 0;
			for( int i = 0, ie = demixing_matrix.rows * nb_input_streams; i < ie; i += demixing_matrix.rows ) {
				for( int j = i, je = j + nb_output_streams; j < je; j++ ) {
					final short v = internal_short[ j ];// java
					matrix[ l++ ] = (char)v;
					matrix[ l++ ] = (char)(v >>> 8);
				}
			}
		}
		break;
		default:
			System.err.println( "JOpusProjectionEncoder unknown request: " + request );
			break;
		}
		return ret;
	}

	// java: an overloaded variant to get data back
		/**
		 * OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST
		 *
		 * @param request the request
		 * @param matrix the array to get data back
		 * @param offset the array offset
		 * @param external_size the requested size
		 * @return status
		 */
		public final int opus_projection_encoder_ctl(final int request, final byte[] matrix, final int offset, final int external_size)
		{
			final int ret = Jopus_defines.OPUS_OK;

			final JOpusMSEncoder ms_encoder = mMultistreamEncoder;// get_multistream_encoder(st);
			final JMappingMatrix demixing_matrix = mDemixingMatrix;// get_enc_demixing_matrix(st);

			// va_start(ap, request);
			switch( request )
			{
			case Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST:
			{
				if( null == matrix )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}
				final short[] internal_short = demixing_matrix.mMatrix;// JMappingMatrix.mapping_matrix_get_data( demixing_matrix );
				/* (I/O is in relation to the decoder's perspective). */
				final int nb_input_streams = ms_encoder.layout.nb_streams +
						ms_encoder.layout.nb_coupled_streams;
				final int nb_output_streams = ms_encoder.layout.nb_channels;
				final int internal_size = nb_input_streams * nb_output_streams * (Short.SIZE / 8);
				if( external_size != internal_size )
				{
					return Jopus_defines.OPUS_BAD_ARG;
				}

				/* Copy demixing matrix subset to output destination. */
				int l = 0;
				for( int i = 0, ie = demixing_matrix.rows * nb_input_streams; i < ie; i += demixing_matrix.rows ) {
					for( int j = i, je = j + nb_output_streams; j < je; j++ ) {
						final short v = internal_short[ j ];// java
						matrix[ l++ ] = (byte)v;
						matrix[ l++ ] = (byte)(v >>> 8);
					}
				}
			}
			break;
			default:
				System.err.println( "JOpusProjectionEncoder unknown request: " + request );
				break;
			}
			return ret;
		}
}