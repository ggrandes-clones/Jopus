package opus;

/* Copyright (c) 2012 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// opus_private.h

public final class Jopus_private {

	public static final int MODE_SILK_ONLY = 1000;
	static final int MODE_HYBRID    = 1001;
	public static final int MODE_CELT_ONLY = 1002;

	/** Configures the encoder's expected percentage of voice
	  * opposed to music or other signals.
	  *
	  * @note This interface is currently more aspiration than actuality. It's
	  * ultimately expected to bias an automatic signal classifier, but it currently
	  * just shifts the static bitrate to mode mapping around a little bit.
	  *
	  * @param[in] x <tt>int</tt>:   Voice percentage in the range 0-100, inclusive.
	  * @hideinitializer */
	static final int OPUS_SET_VOICE_RATIO_REQUEST = 11018;

	/** Gets the encoder's configured voice ratio value, @see OPUS_SET_VOICE_RATIO
	  *
	  * @param[out] x <tt>int*</tt>:  Voice percentage in the range 0-100, inclusive.
	  * @hideinitializer */
	static final int OPUS_GET_VOICE_RATIO_REQUEST = 11019;

	/** Configures the encoder's expected percentage of voice
	  * opposed to music or other signals.
	  *
	  * @note This interface is currently more aspiration than actuality. It's
	  * ultimately expected to bias an automatic signal classifier, but it currently
	  * just shifts the static bitrate to mode mapping around a little bit.
	  *
	  * @param[in] x <tt>int</tt>:   Voice percentage in the range 0-100, inclusive.
	  * @hideinitializer */
	// #define OPUS_SET_VOICE_RATIO(x) OPUS_SET_VOICE_RATIO_REQUEST, __opus_check_int(x)
	static final int OPUS_SET_VOICE_RATIO = OPUS_SET_VOICE_RATIO_REQUEST;

	/** Gets the encoder's configured voice ratio value, @see OPUS_SET_VOICE_RATIO
	  *
	  * @param[out] x <tt>int*</tt>:  Voice percentage in the range 0-100, inclusive.
	  * @hideinitializer */
	// #define OPUS_GET_VOICE_RATIO(x) OPUS_GET_VOICE_RATIO_REQUEST, __opus_check_int_ptr(x)
	static final int OPUS_GET_VOICE_RATIO = OPUS_GET_VOICE_RATIO_REQUEST;

	static final int  OPUS_SET_FORCE_MODE_REQUEST = 11002;
	// #define OPUS_SET_FORCE_MODE(x) OPUS_SET_FORCE_MODE_REQUEST, __opus_check_int(x)
	public static final int  OPUS_SET_FORCE_MODE = OPUS_SET_FORCE_MODE_REQUEST;
}