package silk;

/***********************************************************************
Copyright (c) 2006-2011, Skype Limited. All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
- Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
- Neither the name of Internet Society, IETF or IETF Trust, nor the
names of specific contributors, may be used to endorse or promote
products derived from this software without specific prior written
permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
***********************************************************************/

// define.h

public final class Jdefine {

	/** Max number of encoder channels (1/2) */
	public static final int ENCODER_NUM_CHANNELS           = 2;
	/** Number of decoder channels (1/2) */
	static final int DECODER_NUM_CHANNELS                  = 2;

	static final int MAX_FRAMES_PER_PACKET                 = 3;

	/* Limits on bitrate */
	static final int MIN_TARGET_RATE_BPS                   = 5000;
	static final int MAX_TARGET_RATE_BPS                   = 80000;

	/* LBRR thresholds */
	static final int LBRR_NB_MIN_RATE_BPS                  = 12000;
	static final int LBRR_MB_MIN_RATE_BPS                  = 14000;
	static final int LBRR_WB_MIN_RATE_BPS                  = 16000;

	/* DTX settings */
	public static final int NB_SPEECH_FRAMES_BEFORE_DTX           = 10;      /* eq 200 ms */
	public static final int MAX_CONSECUTIVE_DTX                   = 20;      /* eq 400 ms */
	public static final float DTX_ACTIVITY_THRESHOLD              = 0.1f;

	/* VAD decision */
	public static final int VAD_NO_DECISION                = -1;
	static final int VAD_NO_ACTIVITY                = 0;
	static final int VAD_ACTIVITY                   = 1;

	/* Maximum sampling frequency */
	static final int MAX_FS_KHZ                            = 16;
	static final int MAX_API_FS_KHZ                        = 48;

	/* Signal types */
	public static final int TYPE_NO_VOICE_ACTIVITY                = 0;
	public static final int TYPE_UNVOICED                         = 1;
	public static final int TYPE_VOICED                           = 2;

	/* Conditional coding types */
	public static final int CODE_INDEPENDENTLY                    = 0;
	static final int CODE_INDEPENDENTLY_NO_LTP_SCALING     = 1;
	public static final int CODE_CONDITIONALLY                    = 2;

	/* Settings for stereo processing */
	static final int STEREO_QUANT_TAB_SIZE                 = 16;
	static final int STEREO_QUANT_SUB_STEPS                = 5;
	static final int STEREO_INTERP_LEN_MS                  = 8;       /* must be even */
	static final double STEREO_RATIO_SMOOTH_COEF           = 0.01;    /* smoothing coef for signal norms and stereo width */

	/* Range of pitch lag estimates */
	static final int PITCH_EST_MIN_LAG_MS                  = 2;       /* 2 ms -> 500 Hz */
	static final int PITCH_EST_MAX_LAG_MS                  = 18;      /* 18 ms -> 56 Hz */

	/** Maximum number of subframes */
	public static final int MAX_NB_SUBFR                          = 4;

	/* Number of samples per frame */
	static final int LTP_MEM_LENGTH_MS                     = 20;
	public static final int SUB_FRAME_LENGTH_MS                   = 5;
	public static final int MAX_SUB_FRAME_LENGTH                  = ( SUB_FRAME_LENGTH_MS * MAX_FS_KHZ );
	static final int MAX_FRAME_LENGTH_MS                   = ( SUB_FRAME_LENGTH_MS * MAX_NB_SUBFR );
	public static final int MAX_FRAME_LENGTH                      = ( MAX_FRAME_LENGTH_MS * MAX_FS_KHZ );

	/* Milliseconds of lookahead for pitch analysis */
	static final int LA_PITCH_MS                           = 2;
	public static final int LA_PITCH_MAX                          = ( LA_PITCH_MS * MAX_FS_KHZ );

	/** Order of LPC used in find pitch */
	public static final int MAX_FIND_PITCH_LPC_ORDER              = 16;

	/* Length of LPC window used in find pitch */
	static final int FIND_PITCH_LPC_WIN_MS                 = ( 20 + (LA_PITCH_MS << 1) );
	static final int FIND_PITCH_LPC_WIN_MS_2_SF            = ( 10 + (LA_PITCH_MS << 1) );
	public static final int FIND_PITCH_LPC_WIN_MAX                = ( FIND_PITCH_LPC_WIN_MS * MAX_FS_KHZ );

	/* Milliseconds of lookahead for noise shape analysis */
	public static final int LA_SHAPE_MS                           = 5;
	public static final int LA_SHAPE_MAX                          = ( LA_SHAPE_MS * MAX_FS_KHZ );

	/** Maximum length of LPC window used in noise shape analysis */
	public static final int SHAPE_LPC_WIN_MAX                     = ( 15 * MAX_FS_KHZ );

	/** dB level of lowest gain quantization level */
	public static final int MIN_QGAIN_DB                          = 2;
	/** dB level of highest gain quantization level */
	static final int MAX_QGAIN_DB                          = 88;
	/** Number of gain quantization levels */
	public static final int N_LEVELS_QGAIN                        = 64;
	/** Max increase in gain quantization index */
	static final int MAX_DELTA_GAIN_QUANT                  = 36;
	/** Max decrease in gain quantization index */
	static final int MIN_DELTA_GAIN_QUANT                  = -4;

	/* Quantization offsets (multiples of 4) */
	static final int OFFSET_VL_Q10                         = 32;
	static final int OFFSET_VH_Q10                         = 100;
	static final int OFFSET_UVL_Q10                        = 100;
	static final int OFFSET_UVH_Q10                        = 240;

	static final int QUANT_LEVEL_ADJUST_Q10                = 80;

	/* Maximum numbers of iterations used to stabilize an LPC vector */
	static final int MAX_LPC_STABILIZE_ITERATIONS          = 16;
	public static final float MAX_PREDICTION_POWER_GAIN             = 1e4f;
	public static final float MAX_PREDICTION_POWER_GAIN_AFTER_RESET = 1e2f;

	public static final int MAX_LPC_ORDER                         = 16;
	static final int MIN_LPC_ORDER                                = 10;

	/** Find Pred Coef defines */
	public static final int LTP_ORDER                             = 5;

	/** LTP quantization settings */
	static final int NB_LTP_CBKS                                  = 3;

	/** Flag to use harmonic noise shaping */
	public static final boolean USE_HARM_SHAPING                  = true;

	/** Max LPC order of noise shaping filters */
	public static final int MAX_SHAPE_LPC_ORDER                   = 24;

	public static final int HARM_SHAPE_FIR_TAPS                   = 3;

	/** Maximum number of delayed decision states */
	static final int MAX_DEL_DEC_STATES                           = 4;

	public static final int LTP_BUF_LENGTH                        = 512;
	public static final int LTP_MASK                              = ( LTP_BUF_LENGTH - 1 );

	static final int DECISION_DELAY                        = 40;

	/* Number of subframes for excitation entropy coding */
	static final int SHELL_CODEC_FRAME_LENGTH              = 16;
	static final int LOG2_SHELL_CODEC_FRAME_LENGTH         = 4;
	static final int MAX_NB_SHELL_BLOCKS                   = ( MAX_FRAME_LENGTH / SHELL_CODEC_FRAME_LENGTH );

	/** Number of rate levels, for entropy coding of excitation */
	static final int N_RATE_LEVELS                         = 10;

	/** Maximum sum of pulses per shell coding frame */
	static final int SILK_MAX_PULSES                       = 16;

	public static final int MAX_MATRIX_SIZE                       = MAX_LPC_ORDER; /* Max of LPC Order and LTP order */

	static final int NSQ_LPC_BUF_LENGTH                    = MAX_LPC_ORDER;

	/***************************/
	/* Voice activity detector */
	/***************************/
	static final int VAD_N_BANDS                           = 4;

	static final int VAD_INTERNAL_SUBFRAMES_LOG2           = 2;
	static final int VAD_INTERNAL_SUBFRAMES                = ( 1 << VAD_INTERNAL_SUBFRAMES_LOG2 );

	static final int VAD_NOISE_LEVEL_SMOOTH_COEF_Q16       = 1024;    /* Must be <  4096 */
	static final int VAD_NOISE_LEVELS_BIAS                 = 50;

	/* Sigmoid settings */
	static final int VAD_NEGATIVE_OFFSET_Q5                = 128;     /* sigmoid is 0 at -128 */
	static final int VAD_SNR_FACTOR_Q16                    = 45000;

	/** smoothing for SNR measurement */
	static final int VAD_SNR_SMOOTH_COEF_Q18               = 4096;

	/** Size of the piecewise linear cosine approximation table for the LSFs */
	static final int LSF_COS_TAB_SZ_FIX                    = 128;

	/******************/
	/* NLSF quantizer */
	/******************/
	static final int NLSF_W_Q                              = 2;
	static final int NLSF_VQ_MAX_VECTORS                   = 32;
	static final int NLSF_QUANT_MAX_AMPLITUDE              = 4;
	static final int NLSF_QUANT_MAX_AMPLITUDE_EXT          = 10;
	static final double NLSF_QUANT_LEVEL_ADJ               = 0.1;
	static final int NLSF_QUANT_DEL_DEC_STATES_LOG2        = 2;
	static final int NLSF_QUANT_DEL_DEC_STATES             = ( 1 << NLSF_QUANT_DEL_DEC_STATES_LOG2 );

	/* Transition filtering for mode switching */
	static final int TRANSITION_TIME_MS                    = 5120;    /* 5120 = 64 * FRAME_LENGTH_MS * ( TRANSITION_INT_NUM - 1 ) = 64*(20*4)*/
	static final int TRANSITION_NB                         = 3;       /* Hardcoded in tables */
	static final int TRANSITION_NA                         = 2;       /* Hardcoded in tables */
	static final int TRANSITION_INT_NUM                    = 5;       /* Hardcoded in tables */
	static final int TRANSITION_FRAMES                     = ( TRANSITION_TIME_MS / MAX_FRAME_LENGTH_MS );
	static final int TRANSITION_INT_STEPS                  = ( TRANSITION_FRAMES  / ( TRANSITION_INT_NUM - 1 ) );

	/** BWE factors to apply after packet loss */
	static final int BWE_AFTER_LOSS_Q16                    = 63570;

	/* Defines for CN generation */
	static final int CNG_BUF_MASK_MAX                      = 255;     /* 2^floor(log2(MAX_FRAME_LENGTH))-1    */
	static final int CNG_GAIN_SMTH_Q16                     = 4634;    /* 0.25^(1/4)                           */
	static final int CNG_NLSF_SMTH_Q16                     = 16348;   /* 0.25                                 */

}