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

// pitch_est_defines.h

final class Jpitch_est_defines {

	/********************************************************/
	/* Definitions for pitch estimator                      */
	/********************************************************/

	/** Maximum sampling frequency used */
	static final int PE_MAX_FS_KHZ             = 16;

	static final int PE_MAX_NB_SUBFR           = 4;
	static final int PE_SUBFR_LENGTH_MS        = 5;   /* 5 ms */

	static final int PE_LTP_MEM_LENGTH_MS      = ( 4 * PE_SUBFR_LENGTH_MS );

	static final int PE_MAX_FRAME_LENGTH_MS    = ( PE_LTP_MEM_LENGTH_MS + PE_MAX_NB_SUBFR * PE_SUBFR_LENGTH_MS );
	static final int PE_MAX_FRAME_LENGTH       = ( PE_MAX_FRAME_LENGTH_MS * PE_MAX_FS_KHZ );
	static final int PE_MAX_FRAME_LENGTH_ST_1  = ( PE_MAX_FRAME_LENGTH >> 2 );
	static final int PE_MAX_FRAME_LENGTH_ST_2  = ( PE_MAX_FRAME_LENGTH >> 1 );

	static final int PE_MAX_LAG_MS             = 18;           /* 18 ms -> 56 Hz */
	static final int PE_MIN_LAG_MS             = 2;            /* 2 ms -> 500 Hz */
	static final int PE_MAX_LAG                = ( PE_MAX_LAG_MS * PE_MAX_FS_KHZ );
	static final int PE_MIN_LAG                = ( PE_MIN_LAG_MS * PE_MAX_FS_KHZ );

	static final int PE_D_SRCH_LENGTH          = 24;

	static final int PE_NB_STAGE3_LAGS         = 5;

	static final int PE_NB_CBKS_STAGE2         = 3;
	static final int PE_NB_CBKS_STAGE2_EXT     = 11;

	static final int PE_NB_CBKS_STAGE3_MAX     = 34;
	static final int PE_NB_CBKS_STAGE3_MID     = 24;
	static final int PE_NB_CBKS_STAGE3_MIN     = 16;

	static final int PE_NB_CBKS_STAGE3_10MS    = 12;
	static final int PE_NB_CBKS_STAGE2_10MS    = 3;

	static final float PE_SHORTLAG_BIAS        = 0.2f;    /* for logarithmic weighting    */
	static final float PE_PREVLAG_BIAS         = 0.2f;    /* for logarithmic weighting    */
	static final float PE_FLATCONTOUR_BIAS     = 0.05f;

	static final int SILK_PE_MIN_COMPLEX       = 0;
	static final int SILK_PE_MID_COMPLEX       = 1;
	static final int SILK_PE_MAX_COMPLEX       = 2;

}