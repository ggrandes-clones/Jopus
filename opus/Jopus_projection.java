package opus;

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
public final class Jopus_projection {

	/** These are the actual encoder and decoder CTL ID numbers.
	  * They should not be used directly by applications.c
	  * In general, SETs should be even and GETs should be odd.*/
	static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN_REQUEST    = 6001;
	public static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST    = 6003;
	public static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST         = 6005;

/** @defgroup opus_projection_ctls Projection specific encoder and decoder CTLs
  *
  * These are convenience macros that are specific to the
  * opus_projection_encoder_ctl() and opus_projection_decoder_ctl()
  * interface.
  * The CTLs from @ref opus_genericctls, @ref opus_encoderctls,
  * @ref opus_decoderctls, and @ref opus_multistream_ctls may be applied to a
  * projection encoder or decoder as well.
  */

/** Gets the gain (in dB. S7.8-format) of the demixing matrix from the encoder.
  * @param x [out] <tt>opus_int32 *</tt>: Returns the gain (in dB. S7.8-format)
  *                                      of the demixing matrix.
  * @hideinitializer
  */
// #define OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN(x) OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN = OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN_REQUEST;

/** Gets the size in bytes of the demixing matrix from the encoder.
  * @param x [out] <tt>opus_int32 *</tt>: Returns the size in bytes of the
  *                                      demixing matrix.
  * @hideinitializer
  */
// #define OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE(x) OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST, __opus_check_int_ptr(x)
	public static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE = OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE_REQUEST;

/** Copies the demixing matrix to the supplied pointer location.
  * @param x [out] <tt>unsigned char *</tt>: Returns the demixing matrix to the
  *                                         supplied pointer location.
  * @param y <tt>opus_int32</tt>: The size in bytes of the reserved memory at the
  *                              pointer location.
  * @hideinitializer
  */
// #define OPUS_PROJECTION_GET_DEMIXING_MATRIX(x,y) OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST, x, __opus_check_int(y)
	public static final int OPUS_PROJECTION_GET_DEMIXING_MATRIX = OPUS_PROJECTION_GET_DEMIXING_MATRIX_REQUEST;
}