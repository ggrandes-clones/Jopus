package opus;

/* Copyright (c) 2010-2011 Xiph.Org Foundation, Skype Limited
   Written by Jean-Marc Valin and Koen Vos */

// opus_defines.h

/**
 * @file opus_defines.h
 * @brief Opus reference implementation constants
 */
public final class Jopus_defines {
	/** Disable bitstream fixes from RFC 8251 */
	public static final boolean DISABLE_UPDATE_DRAFT = false;

	/** @defgroup opus_errorcodes Error codes
	 * @{
	 */
	/** No error @hideinitializer*/
	public static final int OPUS_OK               =  0;
	/** One or more invalid/out of range arguments @hideinitializer*/
	public static final int OPUS_BAD_ARG          = -1;
	/** Not enough bytes allocated in the buffer @hideinitializer*/
	public static final int OPUS_BUFFER_TOO_SMALL = -2;
	/** An internal error was detected @hideinitializer*/
	public static final int OPUS_INTERNAL_ERROR   = -3;
	/** The compressed data passed is corrupted @hideinitializer*/
	public static final int OPUS_INVALID_PACKET   = -4;
	/** Invalid/unsupported request number @hideinitializer*/
	public static final int OPUS_UNIMPLEMENTED    = -5;
	/** An encoder or decoder structure is invalid or already freed @hideinitializer*/
	public static final int OPUS_INVALID_STATE    = -6;
	/** Memory allocation has failed @hideinitializer*/
	public static final int OPUS_ALLOC_FAIL       = -7;
	/**@}*/

	// FIXME using macros to call functions forces calling an argument twice, so if the argument is a function, may be some problems
	/** @cond OPUS_INTERNAL_DOC */
	/* These are the actual Encoder CTL ID numbers.
	  * They should not be used directly by applications.
	  * In general, SETs should be even and GETs should be odd.*/
	/** Configures the encoder's intended application.
	  * The initial value is a mandatory argument to the encoder_create function.
	  * @see OPUS_GET_APPLICATION
	  * @param x [in] <tt>int</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_APPLICATION_REQUEST          = 4000;
	/** Gets the encoder's configured application.
	  * @see OPUS_SET_APPLICATION
	  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>#OPUS_APPLICATION_VOIP</dt>
	  * <dd>Process signal for improved speech intelligibility.</dd>
	  * <dt>#OPUS_APPLICATION_AUDIO</dt>
	  * <dd>Favor faithfulness to the original input.</dd>
	  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
	  * <dd>Configure the minimum possible coding delay by disabling certain modes
	  * of operation.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_APPLICATION_REQUEST          = 4001;
	/** Configures the bitrate in the encoder.
	  * Rates from 500 to 512000 bits per second are meaningful, as well as the
	  * special values #OPUS_AUTO and #OPUS_BITRATE_MAX.
	  * The value #OPUS_BITRATE_MAX can be used to cause the codec to use as much
	  * rate as it can, which is useful for controlling the rate by adjusting the
	  * output buffer size.
	  * @see OPUS_GET_BITRATE
	  * @param x [in] <tt>int</tt>: Bitrate in bits per second. The default
	  *                                   is determined based on the number of
	  *                                   channels and the input sampling rate.
	  * @hideinitializer */
	public static final int OPUS_SET_BITRATE_REQUEST              = 4002;
	/** Gets the encoder's bitrate configuration.
	  * @see OPUS_SET_BITRATE
	  * @param x [out] <tt>Integer</tt>: Returns the bitrate in bits per second.
	  *                                      The default is determined based on the
	  *                                      number of channels and the input
	  *                                      sampling rate.
	  * @hideinitializer */
	public static final int OPUS_GET_BITRATE_REQUEST              = 4003;
	/** Configures the maximum bandpass that the encoder will select automatically.
	  * Applications should normally use this instead of #OPUS_SET_BANDWIDTH
	  * (leaving that set to the default, #OPUS_AUTO). This allows the
	  * application to set an upper bound based on the type of input it is
	  * providing, but still gives the encoder the freedom to reduce the bandpass
	  * when the bitrate becomes too low, for better overall quality.
	  * @see OPUS_GET_MAX_BANDWIDTH
	  * @param x [in] <tt>int</tt>: Allowed values:
	  * <dl>
	  * <dt>OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
	  * <dt>OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
	  * <dt>OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
	  * <dt>OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
	  * <dt>OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband (default)</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_MAX_BANDWIDTH_REQUEST        = 4004;
	/** Gets the encoder's configured maximum allowed bandpass.
	  * @see OPUS_SET_MAX_BANDWIDTH
	  * @param x [out] <tt>Integer</tt>: Allowed values:
	  * <dl>
	  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband (default)</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_MAX_BANDWIDTH_REQUEST        = 4005;
	/** Enables or disables variable bitrate (VBR) in the encoder.
	  * The configured bitrate may not be met exactly because frames must
	  * be an integer number of bytes in length.
	  * @see OPUS_GET_VBR
	  * @see OPUS_SET_VBR_CONSTRAINT
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Hard CBR. For LPC/hybrid modes at very low bit-rate, this can
	  *               cause noticeable quality degradation.</dd>
	  * <dt>1</dt><dd>VBR (default). The exact type of VBR is controlled by
	  *               #OPUS_SET_VBR_CONSTRAINT.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_VBR_REQUEST                  = 4006;
	/** Determine if variable bitrate (VBR) is enabled in the encoder.
	  * @see OPUS_SET_VBR
	  * @see OPUS_GET_VBR_CONSTRAINT
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>Hard CBR.</dd>
	  * <dt>1</dt><dd>VBR (default). The exact type of VBR may be retrieved via
	  *               #OPUS_GET_VBR_CONSTRAINT.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_VBR_REQUEST                  = 4007;
	/** Sets the encoder's bandpass to a specific value.
	  * This prevents the encoder from automatically selecting the bandpass based
	  * on the available bitrate. If an application knows the bandpass of the input
	  * audio it is providing, it should normally use #OPUS_SET_MAX_BANDWIDTH
	  * instead, which still gives the encoder the freedom to reduce the bandpass
	  * when the bitrate becomes too low, for better overall quality.
	  * @see OPUS_GET_BANDWIDTH
	  * @param x [in] <tt>int</tt>: Allowed values:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt>                    <dd>(default)</dd>
	  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_BANDWIDTH_REQUEST            = 4008;
	/** Gets the encoder's configured bandpass or the decoder's last bandpass.
	  * @see OPUS_SET_BANDWIDTH
	  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt>                    <dd>(default)</dd>
	  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
	  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_BANDWIDTH_REQUEST            = 4009;
	/** Configures the encoder's computational complexity.
	  * The supported range is 0-10 inclusive with 10 representing the highest complexity.
	  * @see OPUS_GET_COMPLEXITY
	  * @param x [in] <tt>int</tt>: Allowed values: 0-10, inclusive.
	  *
	  * @hideinitializer */
	public static final int OPUS_SET_COMPLEXITY_REQUEST           = 4010;
	/** Gets the encoder's complexity configuration.
	  * @see OPUS_SET_COMPLEXITY
	  * @param x [out] <tt>Integer</tt>: Returns a value in the range 0-10,
	  *                                      inclusive.
	  * @hideinitializer */
	public static final int OPUS_GET_COMPLEXITY_REQUEST           = 4011;
	/** Configures the encoder's use of inband forward error correction (FEC).
	  * @note This is only applicable to the LPC layer
	  * @see OPUS_GET_INBAND_FEC
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Disable inband FEC (default).</dd>
	  * <dt>1</dt><dd>Enable inband FEC.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_INBAND_FEC_REQUEST           = 4012;
	/** Gets encoder's configured use of inband forward error correction.
	  * @see OPUS_SET_INBAND_FEC
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>Inband FEC disabled (default).</dd>
	  * <dt>1</dt><dd>Inband FEC enabled.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_INBAND_FEC_REQUEST           = 4013;
	/** Configures the encoder's expected packet loss percentage.
	  * Higher values trigger progressively more loss resistant behavior in the encoder
	  * at the expense of quality at a given bitrate in the absence of packet loss, but
	  * greater quality under loss.
	  * @see OPUS_GET_PACKET_LOSS_PERC
	  * @param x [in] <tt>int</tt>:   Loss percentage in the range 0-100, inclusive (default: 0).
	  * @hideinitializer */
	public static final int OPUS_SET_PACKET_LOSS_PERC_REQUEST     = 4014;
	/** Gets the encoder's configured packet loss percentage.
	  * @see OPUS_SET_PACKET_LOSS_PERC
	  * @param x [out] <tt>Integer</tt>: Returns the configured loss percentage
	  *                                      in the range 0-100, inclusive (default: 0).
	  * @hideinitializer */
	public static final int OPUS_GET_PACKET_LOSS_PERC_REQUEST     = 4015;
	/** Configures the encoder's use of discontinuous transmission (DTX).
	  * @note This is only applicable to the LPC layer
	  * @see OPUS_GET_DTX
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Disable DTX (default).</dd>
	  * <dt>1</dt><dd>Enabled DTX.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_DTX_REQUEST                  = 4016;
	/** Gets encoder's configured use of discontinuous transmission.
	  * @see OPUS_SET_DTX
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>DTX disabled (default).</dd>
	  * <dt>1</dt><dd>DTX enabled.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_DTX_REQUEST                  = 4017;
	/** Enables or disables constrained VBR in the encoder.
	  * This setting is ignored when the encoder is in CBR mode.
	  * @warning Only the MDCT mode of Opus currently heeds the constraint.
	  *  Speech mode ignores it completely, hybrid mode may fail to obey it
	  *  if the LPC layer uses more bitrate than the constraint would have
	  *  permitted.
	  * @see OPUS_GET_VBR_CONSTRAINT
	  * @see OPUS_SET_VBR
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Unconstrained VBR.</dd>
	  * <dt>1</dt><dd>Constrained VBR (default). This creates a maximum of one
	  *               frame of buffering delay assuming a transport with a
	  *               serialization speed of the nominal bitrate.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_VBR_CONSTRAINT_REQUEST       = 4020;
	/** Determine if constrained VBR is enabled in the encoder.
	  * @see OPUS_SET_VBR_CONSTRAINT
	  * @see OPUS_GET_VBR
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>Unconstrained VBR.</dd>
	  * <dt>1</dt><dd>Constrained VBR (default).</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_VBR_CONSTRAINT_REQUEST       = 4021;
	/** Configures mono/stereo forcing in the encoder.
	  * This can force the encoder to produce packets encoded as either mono or
	  * stereo, regardless of the format of the input audio. This is useful when
	  * the caller knows that the input signal is currently a mono source embedded
	  * in a stereo stream.
	  * @see OPUS_GET_FORCE_CHANNELS
	  * @param x [in] <tt>int</tt>: Allowed values:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt><dd>Not forced (default)</dd>
	  * <dt>1</dt>         <dd>Forced mono</dd>
	  * <dt>2</dt>         <dd>Forced stereo</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_FORCE_CHANNELS_REQUEST       = 4022;
	/** Gets the encoder's forced channel configuration.
	  * @see OPUS_SET_FORCE_CHANNELS
	  * @param x [out] <tt>Integer</tt>:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt><dd>Not forced (default)</dd>
	  * <dt>1</dt>         <dd>Forced mono</dd>
	  * <dt>2</dt>         <dd>Forced stereo</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_FORCE_CHANNELS_REQUEST       = 4023;
	/** Configures the type of signal being encoded.
	  * This is a hint which helps the encoder's mode selection.
	  * @see OPUS_GET_SIGNAL
	  * @param x [in] <tt>int</tt>: Allowed values:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt>        <dd>(default)</dd>
	  * <dt>#OPUS_SIGNAL_VOICE</dt><dd>Bias thresholds towards choosing LPC or Hybrid modes.</dd>
	  * <dt>#OPUS_SIGNAL_MUSIC</dt><dd>Bias thresholds towards choosing MDCT modes.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_SIGNAL_REQUEST               = 4024;
	/** Gets the encoder's configured signal type.
	  * @see OPUS_SET_SIGNAL
	  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>#OPUS_AUTO</dt>        <dd>(default)</dd>
	  * <dt>#OPUS_SIGNAL_VOICE</dt><dd>Bias thresholds towards choosing LPC or Hybrid modes.</dd>
	  * <dt>#OPUS_SIGNAL_MUSIC</dt><dd>Bias thresholds towards choosing MDCT modes.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_SIGNAL_REQUEST               = 4025;
	/** Gets the total samples of delay added by the entire codec.
	  * This can be queried by the encoder and then the provided number of samples can be
	  * skipped on from the start of the decoder's output to provide time aligned input
	  * and output. From the perspective of a decoding application the real data begins this many
	  * samples late.
	  *
	  * The decoder contribution to this delay is identical for all decoders, but the
	  * encoder portion of the delay may vary from implementation to implementation,
	  * version to version, or even depend on the encoder's initial configuration.
	  * Applications needing delay compensation should call this CTL rather than
	  * hard-coding a value.
	  * @param x [out] <tt>Integer</tt>:   Number of lookahead samples
	  * @hideinitializer */
	public static final int OPUS_GET_LOOKAHEAD_REQUEST            = 4027;
	/* public static final int OPUS_RESET_STATE 4028 */
	/** Gets the sampling rate the encoder or decoder was initialized with.
	  * This simply returns the <code>Fs</code> value passed to opus_encoder_init()
	  * or opus_decoder_init().
	  * @param x [out] <tt>Integer</tt>: Sampling rate of encoder or decoder.
	  * @hideinitializer
	  */
	static final int OPUS_GET_SAMPLE_RATE_REQUEST          = 4029;
	/** Gets the final state of the codec's entropy coder.
	  * This is used for testing purposes,
	  * The encoder and decoder state should be identical after coding a payload
	  * (assuming no data corruption or software bugs)
	  *
	  * @param x [out] <tt>Long</tt>: Entropy coder state
	  *
	  * @hideinitializer */
	public static final int OPUS_GET_FINAL_RANGE_REQUEST          = 4031;
	/** Gets the pitch of the last decoded frame, if available.
	  * This can be used for any post-processing algorithm requiring the use of pitch,
	  * e.g. time stretching/shortening. If the last frame was not voiced, or if the
	  * pitch was not coded in the frame, then zero is returned.
	  *
	  * This CTL is only implemented for decoder instances.
	  *
	  * @param x [out] <tt>Integer</tt>: pitch period at 48 kHz (or 0 if not available)
	  *
	  * @hideinitializer */
	public static final int OPUS_GET_PITCH_REQUEST                = 4033;
	/** Configures decoder gain adjustment.
	  * Scales the decoded output by a factor specified in Q8 dB units.
	  * This has a maximum range of -32768 to 32767 inclusive, and returns
	  * OPUS_BAD_ARG otherwise. The default is zero indicating no adjustment.
	  * This setting survives decoder reset.
	  *
	  * gain = pow(10, x/(20.0*256))
	  *
	  * @param x [in] <tt>int</tt>:   Amount to scale PCM signal by in Q8 dB units.
	  * @hideinitializer */
	public static final int OPUS_SET_GAIN_REQUEST                 = 4034;
	/** Gets the decoder's configured gain adjustment. @see OPUS_SET_GAIN
	  *
	  * @param x [out] <tt>Integer</tt>: Amount to scale PCM signal by in Q8 dB units.
	  * @hideinitializer */
	static final int OPUS_GET_GAIN_REQUEST                 = 4045; /* Should have been 4035 */
	/** Configures the depth of signal being encoded.
	  *
	  * This is a hint which helps the encoder identify silence and near-silence.
	  * It represents the number of significant bits of linear intensity below
	  * which the signal contains ignorable quantization or other noise.
	  *
	  * For example, OPUS_SET_LSB_DEPTH(14) would be an appropriate setting
	  * for G.711 u-law input. OPUS_SET_LSB_DEPTH(16) would be appropriate
	  * for 16-bit linear pcm input with opus_encode_float().
	  *
	  * When using opus_encode() instead of opus_encode_float(), or when libopus
	  * is compiled for fixed-point, the encoder uses the minimum of the value
	  * set here and the value 16.
	  *
	  * @see OPUS_GET_LSB_DEPTH
	  * @param x [in] <tt>int</tt>: Input precision in bits, between 8 and 24
	  *                                   (default: 24).
	  * @hideinitializer */
	public static final int OPUS_SET_LSB_DEPTH_REQUEST            = 4036;
	/** Gets the encoder's configured signal depth.
	  * @see OPUS_SET_LSB_DEPTH
	  * @param x [out] <tt>Integer</tt>: Input precision in bits, between 8 and
	  *                                      24 (default: 24).
	  * @hideinitializer */
	public static final int OPUS_GET_LSB_DEPTH_REQUEST            = 4037;
	/** Gets the duration (in samples) of the last packet successfully decoded or concealed.
	  * @param x [out] <tt>Integer</tt>: Number of samples (at current sampling rate).
	  * @hideinitializer */
	static final int OPUS_GET_LAST_PACKET_DURATION_REQUEST = 4039;
	/** Configures the encoder's use of variable duration frames.
	  * When variable duration is enabled, the encoder is free to use a shorter frame
	  * size than the one requested in the opus_encode*() call.
	  * It is then the user's responsibility
	  * to verify how much audio was encoded by checking the ToC byte of the encoded
	  * packet. The part of the audio that was not encoded needs to be resent to the
	  * encoder for the next call. Do not use this option unless you <b>really</b>
	  * know what you are doing.
	  * @see OPUS_GET_EXPERT_FRAME_DURATION
	  * @param x [in] <tt>int</tt>: Allowed values:
	  * <dl>
	  * <dt>OPUS_FRAMESIZE_ARG</dt><dd>Select frame size from the argument (default).</dd>
	  * <dt>OPUS_FRAMESIZE_2_5_MS</dt><dd>Use 2.5 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_5_MS</dt><dd>Use 5 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_10_MS</dt><dd>Use 10 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_20_MS</dt><dd>Use 20 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_40_MS</dt><dd>Use 40 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_60_MS</dt><dd>Use 60 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_VARIABLE</dt><dd>Optimize the frame size dynamically.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_EXPERT_FRAME_DURATION_REQUEST = 4040;
	/** Gets the encoder's configured use of variable duration frames.
	  * @see OPUS_SET_EXPERT_FRAME_DURATION
	  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>OPUS_FRAMESIZE_ARG</dt><dd>Select frame size from the argument (default).</dd>
	  * <dt>OPUS_FRAMESIZE_2_5_MS</dt><dd>Use 2.5 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_5_MS</dt><dd>Use 5 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_10_MS</dt><dd>Use 10 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_20_MS</dt><dd>Use 20 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_40_MS</dt><dd>Use 40 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_60_MS</dt><dd>Use 60 ms frames.</dd>
	  * <dt>OPUS_FRAMESIZE_VARIABLE</dt><dd>Optimize the frame size dynamically.</dd>
	  * </dl>
	  * @hideinitializer */
	static final int OPUS_GET_EXPERT_FRAME_DURATION_REQUEST = 4041;
	/** If set to 1, disables almost all use of prediction, making frames almost
	  * completely independent. This reduces quality.
	  * @see OPUS_GET_PREDICTION_DISABLED
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Enable prediction (default).</dd>
	  * <dt>1</dt><dd>Disable prediction.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_PREDICTION_DISABLED_REQUEST  = 4042;
	/** Gets the encoder's configured prediction status.
	  * @see OPUS_SET_PREDICTION_DISABLED
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>Prediction enabled (default).</dd>
	  * <dt>1</dt><dd>Prediction disabled.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_PREDICTION_DISABLED_REQUEST  = 4043;

	/* Don't use 4045, it's already taken by OPUS_GET_GAIN_REQUEST */
	// FIXME why not in the celt.h?
	/** If set to 1, disables the use of phase inversion for intensity stereo,
	  * improving the quality of mono downmixes, but slightly reducing normal
	  * stereo quality. Disabling phase inversion in the decoder does not comply
	  * with RFC 6716, although it does not cause any interoperability issue and
	  * is expected to become part of the Opus standard once RFC 6716 is updated
	  * by draft-ietf-codec-opus-update.
	  * @see OPUS_GET_PHASE_INVERSION_DISABLED
	  * @param x [in] <tt>boolean</tt>: Allowed values:
	  * <dl>
	  * <dt>0</dt><dd>Enable phase inversion (default).</dd>
	  * <dt>1</dt><dd>Disable phase inversion.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST	= 4046;
	/** Gets the encoder's configured phase inversion status.
	  * @see OPUS_SET_PHASE_INVERSION_DISABLED
	  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
	  * <dl>
	  * <dt>0</dt><dd>Stereo phase inversion enabled (default).</dd>
	  * <dt>1</dt><dd>Stereo phase inversion disabled.</dd>
	  * </dl>
	  * @hideinitializer */
	public static final int OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST	= 4047;

	/** Defines for the presence of extended APIs. */
	public static final boolean OPUS_HAVE_OPUS_PROJECTION_H = true;

	/* Macros to trigger compilation errors when the wrong types are provided to a CTL */
	/*#define __opus_check_int(x) (((void)((x) == (opus_int32)0)), (opus_int32)(x))
	#define __opus_check_int_ptr(ptr) ((ptr) + ((ptr) - (opus_int32*)(ptr)))
	#define __opus_check_uint_ptr(ptr) ((ptr) + ((ptr) - (opus_uint32*)(ptr)))
	#define __opus_check_val16_ptr(ptr) ((ptr) + ((ptr) - (opus_val16*)(ptr)))*/
	/** @endcond */

	/** @defgroup opus_ctlvalues Pre-defined values for CTL interface
	  * @see opus_genericctls, opus_encoderctls
	  * @{
	  */
	/* Values for the various encoder CTLs */
	/** Auto/default setting @hideinitializer */
	public static final int OPUS_AUTO               = -1000;
	/** Maximum bitrate @hideinitializer */
	public static final int OPUS_BITRATE_MAX        =   -1;

	/** Best for most VoIP/videoconference applications where listening quality and intelligibility matter most
	 * @hideinitializer */
	public static final int OPUS_APPLICATION_VOIP                = 2048;
	/** Best for broadcast/high-fidelity application where the decoded audio should be as close as possible to the input
	 * @hideinitializer */
	public static final int OPUS_APPLICATION_AUDIO               = 2049;
	/** Only use when lowest-achievable latency is what matters most. Voice-optimized modes cannot be used.
	 * @hideinitializer */
	public static final int OPUS_APPLICATION_RESTRICTED_LOWDELAY = 2051;

	/** Signal being encoded is voice */
	public static final int OPUS_SIGNAL_VOICE              = 3001;
	/** Signal being encoded is music */
	public static final int OPUS_SIGNAL_MUSIC              = 3002;
	/** 4 kHz bandpass @hideinitializer */
	public static final int OPUS_BANDWIDTH_NARROWBAND      = 1101;
	/** 6 kHz bandpass @hideinitializer */
	public static final int OPUS_BANDWIDTH_MEDIUMBAND      = 1102;
	/** 8 kHz bandpass @hideinitializer */
	public static final int OPUS_BANDWIDTH_WIDEBAND        = 1103;
	/** 12 kHz bandpass @hideinitializer */
	public static final int OPUS_BANDWIDTH_SUPERWIDEBAND   = 1104;
	/** 20 kHz bandpass @hideinitializer */
	public static final int OPUS_BANDWIDTH_FULLBAND        = 1105;

	/** Select frame size from the argument (default) */
	public static final int OPUS_FRAMESIZE_ARG     = 5000;
	/** Use 2.5 ms frames */
	public static final int OPUS_FRAMESIZE_2_5_MS  = 5001;
	/** Use 5 ms frames */
	public static final int OPUS_FRAMESIZE_5_MS    = 5002;
	/** Use 10 ms frames */
	public static final int OPUS_FRAMESIZE_10_MS   = 5003;
	/** Use 20 ms frames */
	public static final int OPUS_FRAMESIZE_20_MS   = 5004;
	/** Use 40 ms frames */
	public static final int OPUS_FRAMESIZE_40_MS   = 5005;
	/** Use 60 ms frames */
	public static final int OPUS_FRAMESIZE_60_MS   = 5006;
	/** Use 80 ms frames */
	public static final int OPUS_FRAMESIZE_80_MS   = 5007;
	/**< Use 100 ms frames */
	public static final int OPUS_FRAMESIZE_100_MS  = 5008;
	/**< Use 120 ms frames */
	public static final int OPUS_FRAMESIZE_120_MS  = 5009;

/**@}*/


/** @defgroup opus_encoderctls Encoder related CTLs
  *
  * These are convenience macros for use with the \c opus_encode_ctl
  * interface. They are used to generate the appropriate series of
  * arguments for that call, passing the correct type, size and so
  * on as expected for each particular request.
  *
  * Some usage examples:
  *
  * @code
  * int ret;
  * ret = opus_encoder_ctl(enc_ctx, OPUS_SET_BANDWIDTH(OPUS_AUTO));
  * if (ret != OPUS_OK) return ret;
  *
  * opus_int32 rate;
  * opus_encoder_ctl(enc_ctx, OPUS_GET_BANDWIDTH(&rate));
  *
  * opus_encoder_ctl(enc_ctx, OPUS_RESET_STATE);
  * @endcode
  *
  * @see opus_genericctls, opus_encoder
  * @{
  */

/** Configures the encoder's computational complexity.
  * The supported range is 0-10 inclusive with 10 representing the highest complexity.
  * @see OPUS_GET_COMPLEXITY
  * @param x [in] <tt>int</tt>: Allowed values: 0-10, inclusive.
  *
  * @hideinitializer */
// #define OPUS_SET_COMPLEXITY(x) OPUS_SET_COMPLEXITY_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_COMPLEXITY = OPUS_SET_COMPLEXITY_REQUEST;

/** Gets the encoder's complexity configuration.
  * @see OPUS_SET_COMPLEXITY
  * @param x [out] <tt>Integer</tt>: Returns a value in the range 0-10,
  *                                      inclusive.
  * @hideinitializer */
// #define OPUS_GET_COMPLEXITY(x) OPUS_GET_COMPLEXITY_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_COMPLEXITY = OPUS_GET_COMPLEXITY_REQUEST;

/** Configures the bitrate in the encoder.
  * Rates from 500 to 512000 bits per second are meaningful, as well as the
  * special values #OPUS_AUTO and #OPUS_BITRATE_MAX.
  * The value #OPUS_BITRATE_MAX can be used to cause the codec to use as much
  * rate as it can, which is useful for controlling the rate by adjusting the
  * output buffer size.
  * @see OPUS_GET_BITRATE
  * @param x [in] <tt>int</tt>: Bitrate in bits per second. The default
  *                                   is determined based on the number of
  *                                   channels and the input sampling rate.
  * @hideinitializer */
// #define OPUS_SET_BITRATE(x) OPUS_SET_BITRATE_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_BITRATE = OPUS_SET_BITRATE_REQUEST;

/** Gets the encoder's bitrate configuration.
  * @see OPUS_SET_BITRATE
  * @param x [out] <tt>Integer</tt>: Returns the bitrate in bits per second.
  *                                      The default is determined based on the
  *                                      number of channels and the input
  *                                      sampling rate.
  * @hideinitializer */
// #define OPUS_GET_BITRATE(x) OPUS_GET_BITRATE_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_BITRATE = OPUS_GET_BITRATE_REQUEST;

/** Enables or disables variable bitrate (VBR) in the encoder.
  * The configured bitrate may not be met exactly because frames must
  * be an integer number of bytes in length.
  * @see OPUS_GET_VBR
  * @see OPUS_SET_VBR_CONSTRAINT
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Hard CBR. For LPC/hybrid modes at very low bit-rate, this can
  *               cause noticeable quality degradation.</dd>
  * <dt>1</dt><dd>VBR (default). The exact type of VBR is controlled by
  *               #OPUS_SET_VBR_CONSTRAINT.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_VBR(x) OPUS_SET_VBR_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_VBR = OPUS_SET_VBR_REQUEST;

/** Determine if variable bitrate (VBR) is enabled in the encoder.
  * @see OPUS_SET_VBR
  * @see OPUS_GET_VBR_CONSTRAINT
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>Hard CBR.</dd>
  * <dt>1</dt><dd>VBR (default). The exact type of VBR may be retrieved via
  *               #OPUS_GET_VBR_CONSTRAINT.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_VBR(x) OPUS_GET_VBR_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_VBR = OPUS_GET_VBR_REQUEST;

/** Enables or disables constrained VBR in the encoder.
  * This setting is ignored when the encoder is in CBR mode.
  * @warning Only the MDCT mode of Opus currently heeds the constraint.
  *  Speech mode ignores it completely, hybrid mode may fail to obey it
  *  if the LPC layer uses more bitrate than the constraint would have
  *  permitted.
  * @see OPUS_GET_VBR_CONSTRAINT
  * @see OPUS_SET_VBR
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Unconstrained VBR.</dd>
  * <dt>1</dt><dd>Constrained VBR (default). This creates a maximum of one
  *               frame of buffering delay assuming a transport with a
  *               serialization speed of the nominal bitrate.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_VBR_CONSTRAINT(x) OPUS_SET_VBR_CONSTRAINT_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_VBR_CONSTRAINT = OPUS_SET_VBR_CONSTRAINT_REQUEST;

/** Determine if constrained VBR is enabled in the encoder.
  * @see OPUS_SET_VBR_CONSTRAINT
  * @see OPUS_GET_VBR
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>Unconstrained VBR.</dd>
  * <dt>1</dt><dd>Constrained VBR (default).</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_VBR_CONSTRAINT(x) OPUS_GET_VBR_CONSTRAINT_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_VBR_CONSTRAINT = OPUS_GET_VBR_CONSTRAINT_REQUEST;

/** Configures mono/stereo forcing in the encoder.
  * This can force the encoder to produce packets encoded as either mono or
  * stereo, regardless of the format of the input audio. This is useful when
  * the caller knows that the input signal is currently a mono source embedded
  * in a stereo stream.
  * @see OPUS_GET_FORCE_CHANNELS
  * @param x [in] <tt>int</tt>: Allowed values:
  * <dl>
  * <dt>#OPUS_AUTO</dt><dd>Not forced (default)</dd>
  * <dt>1</dt>         <dd>Forced mono</dd>
  * <dt>2</dt>         <dd>Forced stereo</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_FORCE_CHANNELS(x) OPUS_SET_FORCE_CHANNELS_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_FORCE_CHANNELS = OPUS_SET_FORCE_CHANNELS_REQUEST;

/** Gets the encoder's forced channel configuration.
  * @see OPUS_SET_FORCE_CHANNELS
  * @param x [out] <tt>Integer</tt>:
  * <dl>
  * <dt>#OPUS_AUTO</dt><dd>Not forced (default)</dd>
  * <dt>1</dt>         <dd>Forced mono</dd>
  * <dt>2</dt>         <dd>Forced stereo</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_FORCE_CHANNELS(x) OPUS_GET_FORCE_CHANNELS_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_FORCE_CHANNELS = OPUS_GET_FORCE_CHANNELS_REQUEST;

/** Configures the maximum bandpass that the encoder will select automatically.
  * Applications should normally use this instead of #OPUS_SET_BANDWIDTH
  * (leaving that set to the default, #OPUS_AUTO). This allows the
  * application to set an upper bound based on the type of input it is
  * providing, but still gives the encoder the freedom to reduce the bandpass
  * when the bitrate becomes too low, for better overall quality.
  * @see OPUS_GET_MAX_BANDWIDTH
  * @param x [in] <tt>int</tt>: Allowed values:
  * <dl>
  * <dt>OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
  * <dt>OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
  * <dt>OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
  * <dt>OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
  * <dt>OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband (default)</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_MAX_BANDWIDTH(x) OPUS_SET_MAX_BANDWIDTH_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_MAX_BANDWIDTH = OPUS_SET_MAX_BANDWIDTH_REQUEST;

/** Gets the encoder's configured maximum allowed bandpass.
  * @see OPUS_SET_MAX_BANDWIDTH
  * @param x [out] <tt>Integer</tt>: Allowed values:
  * <dl>
  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband (default)</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_MAX_BANDWIDTH(x) OPUS_GET_MAX_BANDWIDTH_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_MAX_BANDWIDTH = OPUS_GET_MAX_BANDWIDTH_REQUEST;

/** Sets the encoder's bandpass to a specific value.
  * This prevents the encoder from automatically selecting the bandpass based
  * on the available bitrate. If an application knows the bandpass of the input
  * audio it is providing, it should normally use #OPUS_SET_MAX_BANDWIDTH
  * instead, which still gives the encoder the freedom to reduce the bandpass
  * when the bitrate becomes too low, for better overall quality.
  * @see OPUS_GET_BANDWIDTH
  * @param x [in] <tt>int</tt>: Allowed values:
  * <dl>
  * <dt>#OPUS_AUTO</dt>                    <dd>(default)</dd>
  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_BANDWIDTH(x) OPUS_SET_BANDWIDTH_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_BANDWIDTH = OPUS_SET_BANDWIDTH_REQUEST;

/** Configures the type of signal being encoded.
  * This is a hint which helps the encoder's mode selection.
  * @see OPUS_GET_SIGNAL
  * @param x [in] <tt>int</tt>: Allowed values:
  * <dl>
  * <dt>#OPUS_AUTO</dt>        <dd>(default)</dd>
  * <dt>#OPUS_SIGNAL_VOICE</dt><dd>Bias thresholds towards choosing LPC or Hybrid modes.</dd>
  * <dt>#OPUS_SIGNAL_MUSIC</dt><dd>Bias thresholds towards choosing MDCT modes.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_SIGNAL(x) OPUS_SET_SIGNAL_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_SIGNAL = OPUS_SET_SIGNAL_REQUEST;

/** Gets the encoder's configured signal type.
  * @see OPUS_SET_SIGNAL
  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
  * <dl>
  * <dt>#OPUS_AUTO</dt>        <dd>(default)</dd>
  * <dt>#OPUS_SIGNAL_VOICE</dt><dd>Bias thresholds towards choosing LPC or Hybrid modes.</dd>
  * <dt>#OPUS_SIGNAL_MUSIC</dt><dd>Bias thresholds towards choosing MDCT modes.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_SIGNAL(x) OPUS_GET_SIGNAL_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_SIGNAL = OPUS_GET_SIGNAL_REQUEST;

/** Configures the encoder's intended application.
  * The initial value is a mandatory argument to the encoder_create function.
  * @see OPUS_GET_APPLICATION
  * @param x [in] <tt>int</tt>: Returns one of the following values:
  * <dl>
  * <dt>#OPUS_APPLICATION_VOIP</dt>
  * <dd>Process signal for improved speech intelligibility.</dd>
  * <dt>#OPUS_APPLICATION_AUDIO</dt>
  * <dd>Favor faithfulness to the original input.</dd>
  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
  * <dd>Configure the minimum possible coding delay by disabling certain modes
  * of operation.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_APPLICATION(x) OPUS_SET_APPLICATION_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_APPLICATION = OPUS_SET_APPLICATION_REQUEST;

/** Gets the encoder's configured application.
  * @see OPUS_SET_APPLICATION
  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
  * <dl>
  * <dt>#OPUS_APPLICATION_VOIP</dt>
  * <dd>Process signal for improved speech intelligibility.</dd>
  * <dt>#OPUS_APPLICATION_AUDIO</dt>
  * <dd>Favor faithfulness to the original input.</dd>
  * <dt>#OPUS_APPLICATION_RESTRICTED_LOWDELAY</dt>
  * <dd>Configure the minimum possible coding delay by disabling certain modes
  * of operation.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_APPLICATION(x) OPUS_GET_APPLICATION_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_APPLICATION = OPUS_GET_APPLICATION_REQUEST;

/** Gets the total samples of delay added by the entire codec.
  * This can be queried by the encoder and then the provided number of samples can be
  * skipped on from the start of the decoder's output to provide time aligned input
  * and output. From the perspective of a decoding application the real data begins this many
  * samples late.
  *
  * The decoder contribution to this delay is identical for all decoders, but the
  * encoder portion of the delay may vary from implementation to implementation,
  * version to version, or even depend on the encoder's initial configuration.
  * Applications needing delay compensation should call this CTL rather than
  * hard-coding a value.
  * @param x [out] <tt>Integer</tt>:   Number of lookahead samples
  * @hideinitializer */
// #define OPUS_GET_LOOKAHEAD(x) OPUS_GET_LOOKAHEAD_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_LOOKAHEAD = OPUS_GET_LOOKAHEAD_REQUEST;

/** Configures the encoder's use of inband forward error correction (FEC).
  * @note This is only applicable to the LPC layer
  * @see OPUS_GET_INBAND_FEC
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Disable inband FEC (default).</dd>
  * <dt>1</dt><dd>Enable inband FEC.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_INBAND_FEC(x) OPUS_SET_INBAND_FEC_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_INBAND_FEC = OPUS_SET_INBAND_FEC_REQUEST;

/** Gets encoder's configured use of inband forward error correction.
  * @see OPUS_SET_INBAND_FEC
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>Inband FEC disabled (default).</dd>
  * <dt>1</dt><dd>Inband FEC enabled.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_INBAND_FEC(x) OPUS_GET_INBAND_FEC_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_INBAND_FEC = OPUS_GET_INBAND_FEC_REQUEST;

/** Configures the encoder's expected packet loss percentage.
  * Higher values trigger progressively more loss resistant behavior in the encoder
  * at the expense of quality at a given bitrate in the absence of packet loss, but
  * greater quality under loss.
  * @see OPUS_GET_PACKET_LOSS_PERC
  * @param x [in] <tt>int</tt>:   Loss percentage in the range 0-100, inclusive (default: 0).
  * @hideinitializer */
// #define OPUS_SET_PACKET_LOSS_PERC(x) OPUS_SET_PACKET_LOSS_PERC_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_PACKET_LOSS_PERC = OPUS_SET_PACKET_LOSS_PERC_REQUEST;

/** Gets the encoder's configured packet loss percentage.
  * @see OPUS_SET_PACKET_LOSS_PERC
  * @param x [out] <tt>Integer</tt>: Returns the configured loss percentage
  *                                      in the range 0-100, inclusive (default: 0).
  * @hideinitializer */
// #define OPUS_GET_PACKET_LOSS_PERC(x) OPUS_GET_PACKET_LOSS_PERC_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_PACKET_LOSS_PERC = OPUS_GET_PACKET_LOSS_PERC_REQUEST;

/** Configures the encoder's use of discontinuous transmission (DTX).
  * @note This is only applicable to the LPC layer
  * @see OPUS_GET_DTX
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Disable DTX (default).</dd>
  * <dt>1</dt><dd>Enabled DTX.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_DTX(x) OPUS_SET_DTX_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_DTX = OPUS_SET_DTX_REQUEST;

/** Gets encoder's configured use of discontinuous transmission.
  * @see OPUS_SET_DTX
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>DTX disabled (default).</dd>
  * <dt>1</dt><dd>DTX enabled.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_DTX(x) OPUS_GET_DTX_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_DTX = OPUS_GET_DTX_REQUEST;

/** Configures the depth of signal being encoded.
  *
  * This is a hint which helps the encoder identify silence and near-silence.
  * It represents the number of significant bits of linear intensity below
  * which the signal contains ignorable quantization or other noise.
  *
  * For example, OPUS_SET_LSB_DEPTH(14) would be an appropriate setting
  * for G.711 u-law input. OPUS_SET_LSB_DEPTH(16) would be appropriate
  * for 16-bit linear pcm input with opus_encode_float().
  *
  * When using opus_encode() instead of opus_encode_float(), or when libopus
  * is compiled for fixed-point, the encoder uses the minimum of the value
  * set here and the value 16.
  *
  * @see OPUS_GET_LSB_DEPTH
  * @param x [in] <tt>int</tt>: Input precision in bits, between 8 and 24
  *                                   (default: 24).
  * @hideinitializer */
// #define OPUS_SET_LSB_DEPTH(x) OPUS_SET_LSB_DEPTH_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_LSB_DEPTH = OPUS_SET_LSB_DEPTH_REQUEST;

/** Gets the encoder's configured signal depth.
  * @see OPUS_SET_LSB_DEPTH
  * @param x Integerus_int32 *</tt>: Input precision in bits, between 8 and
  *                                      24 (default: 24).
  * @hideinitializer */
// #define OPUS_GET_LSB_DEPTH(x) OPUS_GET_LSB_DEPTH_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_LSB_DEPTH = OPUS_GET_LSB_DEPTH_REQUEST;

/** Configures the encoder's use of variable duration frames.
  * When variable duration is enabled, the encoder is free to use a shorter frame
  * size than the one requested in the opus_encode*() call.
  * It is then the user's responsibility
  * to verify how much audio was encoded by checking the ToC byte of the encoded
  * packet. The part of the audio that was not encoded needs to be resent to the
  * encoder for the next call. Do not use this option unless you <b>really</b>
  * know what you are doing.
  * @see OPUS_GET_EXPERT_FRAME_DURATION
  * @param x [in] <tt>int</tt>: Allowed values:
  * <dl>
  * <dt>OPUS_FRAMESIZE_ARG</dt><dd>Select frame size from the argument (default).</dd>
  * <dt>OPUS_FRAMESIZE_2_5_MS</dt><dd>Use 2.5 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_5_MS</dt><dd>Use 5 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_10_MS</dt><dd>Use 10 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_20_MS</dt><dd>Use 20 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_40_MS</dt><dd>Use 40 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_60_MS</dt><dd>Use 60 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_80_MS</dt><dd>Use 80 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_100_MS</dt><dd>Use 100 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_120_MS</dt><dd>Use 120 ms frames.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_EXPERT_FRAME_DURATION(x) OPUS_SET_EXPERT_FRAME_DURATION_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_EXPERT_FRAME_DURATION = OPUS_SET_EXPERT_FRAME_DURATION_REQUEST;

/** Gets the encoder's configured use of variable duration frames.
  * @see OPUS_SET_EXPERT_FRAME_DURATION
  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
  * <dl>
  * <dt>OPUS_FRAMESIZE_ARG</dt><dd>Select frame size from the argument (default).</dd>
  * <dt>OPUS_FRAMESIZE_2_5_MS</dt><dd>Use 2.5 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_5_MS</dt><dd>Use 5 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_10_MS</dt><dd>Use 10 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_20_MS</dt><dd>Use 20 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_40_MS</dt><dd>Use 40 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_60_MS</dt><dd>Use 60 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_80_MS</dt><dd>Use 80 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_100_MS</dt><dd>Use 100 ms frames.</dd>
  * <dt>OPUS_FRAMESIZE_120_MS</dt><dd>Use 120 ms frames.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_EXPERT_FRAME_DURATION(x) OPUS_GET_EXPERT_FRAME_DURATION_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_EXPERT_FRAME_DURATION = OPUS_GET_EXPERT_FRAME_DURATION_REQUEST;

/** If set to 1, disables almost all use of prediction, making frames almost
  * completely independent. This reduces quality.
  * @see OPUS_GET_PREDICTION_DISABLED
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Enable prediction (default).</dd>
  * <dt>1</dt><dd>Disable prediction.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_SET_PREDICTION_DISABLED(x) OPUS_SET_PREDICTION_DISABLED_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_PREDICTION_DISABLED = OPUS_SET_PREDICTION_DISABLED_REQUEST;

/** Gets the encoder's configured prediction status.
  * @see OPUS_SET_PREDICTION_DISABLED
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>Prediction enabled (default).</dd>
  * <dt>1</dt><dd>Prediction disabled.</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_PREDICTION_DISABLED(x) OPUS_GET_PREDICTION_DISABLED_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_PREDICTION_DISABLED = OPUS_GET_PREDICTION_DISABLED_REQUEST;

/**@}*/

/** @defgroup opus_genericctls Generic CTLs
  *
  * These macros are used with the \c opus_decoder_ctl and
  * \c opus_encoder_ctl calls to generate a particular
  * request.
  *
  * When called on an \c OpusDecoder they apply to that
  * particular decoder instance. When called on an
  * \c OpusEncoder they apply to the corresponding setting
  * on that encoder instance, if present.
  *
  * Some usage examples:
  *
  * @code
  * int ret;
  * opus_int32 pitch;
  * ret = opus_decoder_ctl(dec_ctx, OPUS_GET_PITCH(&pitch));
  * if (ret == OPUS_OK) return ret;
  *
  * opus_encoder_ctl(enc_ctx, OPUS_RESET_STATE);
  * opus_decoder_ctl(dec_ctx, OPUS_RESET_STATE);
  *
  * opus_int32 enc_bw, dec_bw;
  * opus_encoder_ctl(enc_ctx, OPUS_GET_BANDWIDTH(&enc_bw));
  * opus_decoder_ctl(dec_ctx, OPUS_GET_BANDWIDTH(&dec_bw));
  * if (enc_bw != dec_bw) {
  *   printf("packet bandwidth mismatch!\n");
  * }
  * @endcode
  *
  * @see opus_encoder, opus_decoder_ctl, opus_encoder_ctl, opus_decoderctls, opus_encoderctls
  * @{
  */

	/** Resets the codec state to be equivalent to a freshly initialized state.
	  * This should be called when switching streams in order to prevent
	  * the back to back decoding from giving different results from
	  * one at a time decoding.
	  * @hideinitializer */
	public static final int OPUS_RESET_STATE = 4028;

/** Gets the final state of the codec's entropy coder.
  * This is used for testing purposes,
  * The encoder and decoder state should be identical after coding a payload
  * (assuming no data corruption or software bugs)
  *
  * @param x [out] <tt>opus_uint32 *</tt>: Entropy coder state
  *
  * @hideinitializer */
// #define OPUS_GET_FINAL_RANGE(x) OPUS_GET_FINAL_RANGE_REQUEST, __opus_check_uint_ptr(x)
	public static final int OPUS_GET_FINAL_RANGE = OPUS_GET_FINAL_RANGE_REQUEST;

/** Gets the encoder's configured bandpass or the decoder's last bandpass.
  * @see OPUS_SET_BANDWIDTH
  * @param x [out] <tt>Integer</tt>: Returns one of the following values:
  * <dl>
  * <dt>#OPUS_AUTO</dt>                    <dd>(default)</dd>
  * <dt>#OPUS_BANDWIDTH_NARROWBAND</dt>    <dd>4 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_MEDIUMBAND</dt>    <dd>6 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_WIDEBAND</dt>      <dd>8 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_SUPERWIDEBAND</dt><dd>12 kHz passband</dd>
  * <dt>#OPUS_BANDWIDTH_FULLBAND</dt>     <dd>20 kHz passband</dd>
  * </dl>
  * @hideinitializer */
// #define OPUS_GET_BANDWIDTH(x) OPUS_GET_BANDWIDTH_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_BANDWIDTH = OPUS_GET_BANDWIDTH_REQUEST;

/** Gets the sampling rate the encoder or decoder was initialized with.
  * This simply returns the <code>Fs</code> value passed to opus_encoder_init()
  * or opus_decoder_init().
  * @param x [out] <tt>Integer</tt>: Sampling rate of encoder or decoder.
  * @hideinitializer
  */
// #define OPUS_GET_SAMPLE_RATE(x) OPUS_GET_SAMPLE_RATE_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_SAMPLE_RATE = OPUS_GET_SAMPLE_RATE_REQUEST;

/** If set to 1, disables the use of phase inversion for intensity stereo,
  * improving the quality of mono downmixes, but slightly reducing normal
  * stereo quality. Disabling phase inversion in the decoder does not comply
  * with RFC 6716, although it does not cause any interoperability issue and
  * is expected to become part of the Opus standard once RFC 6716 is updated
  * by draft-ietf-codec-opus-update.
  * @see OPUS_GET_PHASE_INVERSION_DISABLED
  * @param x [in] <tt>boolean</tt>: Allowed values:
  * <dl>
  * <dt>0</dt><dd>Enable phase inversion (default).</dd>
  * <dt>1</dt><dd>Disable phase inversion.</dd>
  * </dl>
  * @hideinitializer */
	//#define OPUS_SET_PHASE_INVERSION_DISABLED(x) OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_PHASE_INVERSION_DISABLED = OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST;
/** Gets the encoder's configured phase inversion status.
  * @see OPUS_SET_PHASE_INVERSION_DISABLED
  * @param x [out] <tt>Boolean</tt>: Returns one of the following values:
  * <dl>
  * <dt>0</dt><dd>Stereo phase inversion enabled (default).</dd>
  * <dt>1</dt><dd>Stereo phase inversion disabled.</dd>
  * </dl>
  * @hideinitializer */
	// #define OPUS_GET_PHASE_INVERSION_DISABLED(x) OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_PHASE_INVERSION_DISABLED = OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST;

/**@}*/

/** @defgroup opus_decoderctls Decoder related CTLs
  * @see opus_genericctls, opus_encoderctls, opus_decoder
  * @{
  */

/** Configures decoder gain adjustment.
  * Scales the decoded output by a factor specified in Q8 dB units.
  * This has a maximum range of -32768 to 32767 inclusive, and returns
  * OPUS_BAD_ARG otherwise. The default is zero indicating no adjustment.
  * This setting survives decoder reset.
  *
  * gain = pow(10, x/(20.0*256))
  *
  * @param x [in] <tt>int</tt>:   Amount to scale PCM signal by in Q8 dB units.
  * @hideinitializer */
// #define OPUS_SET_GAIN(x) OPUS_SET_GAIN_REQUEST, __opus_check_int(x)
	public static final int OPUS_SET_GAIN = OPUS_SET_GAIN_REQUEST;

/** Gets the decoder's configured gain adjustment. @see OPUS_SET_GAIN
  *
  * @param x [out] <tt>Integer</tt>: Amount to scale PCM signal by in Q8 dB units.
  * @hideinitializer */
// #define OPUS_GET_GAIN(x) OPUS_GET_GAIN_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_GAIN = OPUS_GET_GAIN_REQUEST;

/** Gets the duration (in samples) of the last packet successfully decoded or concealed.
  * @param x [out] <tt>Integer</tt>: Number of samples (at current sampling rate).
  * @hideinitializer */
// #define OPUS_GET_LAST_PACKET_DURATION(x) OPUS_GET_LAST_PACKET_DURATION_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_LAST_PACKET_DURATION = OPUS_GET_LAST_PACKET_DURATION_REQUEST;

/** Gets the pitch of the last decoded frame, if available.
  * This can be used for any post-processing algorithm requiring the use of pitch,
  * e.g. time stretching/shortening. If the last frame was not voiced, or if the
  * pitch was not coded in the frame, then zero is returned.
  *
  * This CTL is only implemented for decoder instances.
  *
  * @param x [out] <tt>Integer</tt>: pitch period at 48 kHz (or 0 if not available)
  *
  * @hideinitializer */
// #define OPUS_GET_PITCH(x) OPUS_GET_PITCH_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_GET_PITCH = OPUS_GET_PITCH_REQUEST;
/**@}*/
}