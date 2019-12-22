package libenc;
/* Copyright (C) 2007-2008 Jean-Marc Valin
Copyright (C) 2008      Thorvald Natvig

File: resample.c
Arbitrary resampling code

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

3. The name of the author may not be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

import java.util.Arrays;

/**
The design goals of this code are:
   - Very fast algorithm
   - SIMD-friendly algorithm
   - Low memory requirement
   - Good *perceptual* quality (and not best SNR)

Warning: This resampler is relatively new. Although I think I got rid of
all the major bugs and I don't expect the API to change anymore, there
may be something I've missed. So use with caution.

This algorithm is based on this original resampling algorithm:
Smith, Julius O. Digital Audio Resampling Home Page
Center for Computer Research in Music and Acoustics (CCRMA),
Stanford University, 2007.
Web published at https://ccrma.stanford.edu/~jos/resample/.

There is one main difference, though. This resampler uses cubic
interpolation instead of linear interpolation in the above paper. This
makes the table much smaller and makes it possible to compute that table
on a per-stream basis. In turn, being able to tweak the table for each
stream makes it possible to both reduce complexity on simple ratios
(e.g. 2/3), and get rid of the rounding operations in the inner loop.
The latter both reduces CPU time and makes the algorithm more SIMD-friendly.
*/
final class JSpeexResampler {
	/** FIXME never uses
	private static final int SPEEX_RESAMPLER_QUALITY_MAX = 10;
	private static final int SPEEX_RESAMPLER_QUALITY_MIN = 0;
	private static final int SPEEX_RESAMPLER_QUALITY_DEFAULT = 4;
	private static final int SPEEX_RESAMPLER_QUALITY_VOIP = 3;
	private static final int SPEEX_RESAMPLER_QUALITY_DESKTOP = 5;
	*/
	// java: error codes are changed to negative values to separate them from good return values
	private static final int RESAMPLER_ERR_SUCCESS         = 0;
	private static final int RESAMPLER_ERR_ALLOC_FAILED    = -1;
	private static final int RESAMPLER_ERR_BAD_STATE       = -2;
	private static final int RESAMPLER_ERR_INVALID_ARG     = -3;
	private static final int RESAMPLER_ERR_PTR_OVERLAP     = -4;
	private static final int RESAMPLER_ERR_OVERFLOW        = -5;
	// private static final int RESAMPLER_ERR_MAX_ERROR

	private static final boolean RESAMPLE_FULL_SINC_TABLE = true;

	private static final int FIXED_STACK_ALLOC = 1024;// 8192
	private static final int resampler_basic_zero = 1;
	private static final int resampler_basic_direct_single = 2;
	private static final int resampler_basic_direct_double = 3;
	private static final int resampler_basic_interpolate_single = 4;
	private static final int resampler_basic_interpolate_double = 5;

	private int mInRate;
	private int mOutRate;
	private int mNumeratorRate;
	private int mDenominatorRate;

	private int mQuality;
	private int mChannels;
	private int mFiltLen;
	private int mMemAllocSize;
	private int mBufferSize;
	private int mIntAdvance;
	private int mFracAdvance;
	private float mCutoff;
	private int mOversample;
	private boolean mIsInitialised;
	private boolean mIsStarted;

	/* These are per-channel */
	private int[] mLastSample;
	private int[] mSampFracNum;
	private int[] mMagicSamples;

	private float[] mMem;
	private float[] mSincTable = new float[0];// java to make resize lately
	// private int sinc_table_length;
	private int /*resampler_basic_func*/ mResamplerPtr = 0;

	private int mInStride;
	private int mOutStride;
	//
	private static final double kaiser12_table[] = {// [68] = {
		0.99859849, 1.00000000, 0.99859849, 0.99440475, 0.98745105, 0.97779076,
		0.96549770, 0.95066529, 0.93340547, 0.91384741, 0.89213598, 0.86843014,
		0.84290116, 0.81573067, 0.78710866, 0.75723148, 0.72629970, 0.69451601,
		0.66208321, 0.62920216, 0.59606986, 0.56287762, 0.52980938, 0.49704014,
		0.46473455, 0.43304576, 0.40211431, 0.37206735, 0.34301800, 0.31506490,
		0.28829195, 0.26276832, 0.23854851, 0.21567274, 0.19416736, 0.17404546,
		0.15530766, 0.13794294, 0.12192957, 0.10723616, 0.09382272, 0.08164178,
		0.07063950, 0.06075685, 0.05193064, 0.04409466, 0.03718069, 0.03111947,
		0.02584161, 0.02127838, 0.01736250, 0.01402878, 0.01121463, 0.00886058,
		0.00691064, 0.00531256, 0.00401805, 0.00298291, 0.00216702, 0.00153438,
		0.00105297, 0.00069463, 0.00043489, 0.00025272, 0.00013031, 0.0000527734,
		0.00001000, 0.00000000};
	/*
	static double kaiser12_table[36] = {
		0.99440475, 1.00000000, 0.99440475, 0.97779076, 0.95066529, 0.91384741,
		0.86843014, 0.81573067, 0.75723148, 0.69451601, 0.62920216, 0.56287762,
		0.49704014, 0.43304576, 0.37206735, 0.31506490, 0.26276832, 0.21567274,
		0.17404546, 0.13794294, 0.10723616, 0.08164178, 0.06075685, 0.04409466,
		0.03111947, 0.02127838, 0.01402878, 0.00886058, 0.00531256, 0.00298291,
		0.00153438, 0.00069463, 0.00025272, 0.0000527734, 0.00000500, 0.00000000};
	*/
	private static final double kaiser10_table[] = {// [36] = {
		0.99537781, 1.00000000, 0.99537781, 0.98162644, 0.95908712, 0.92831446,
		0.89005583, 0.84522401, 0.79486424, 0.74011713, 0.68217934, 0.62226347,
		0.56155915, 0.50119680, 0.44221549, 0.38553619, 0.33194107, 0.28205962,
		0.23636152, 0.19515633, 0.15859932, 0.12670280, 0.09935205, 0.07632451,
		0.05731132, 0.04193980, 0.02979584, 0.02044510, 0.01345224, 0.00839739,
		0.00488951, 0.00257636, 0.00115101, 0.00035515, 0.00000000, 0.00000000};

	private static final double kaiser8_table[] = {// [36] = {
		0.99635258, 1.00000000, 0.99635258, 0.98548012, 0.96759014, 0.94302200,
		0.91223751, 0.87580811, 0.83439927, 0.78875245, 0.73966538, 0.68797126,
		0.63451750, 0.58014482, 0.52566725, 0.47185369, 0.41941150, 0.36897272,
		0.32108304, 0.27619388, 0.23465776, 0.19672670, 0.16255380, 0.13219758,
		0.10562887, 0.08273982, 0.06335451, 0.04724088, 0.03412321, 0.02369490,
		0.01563093, 0.00959968, 0.00527363, 0.00233883, 0.00050000, 0.00000000};

	private static final double kaiser6_table[] = {// [36] = {
		0.99733006, 1.00000000, 0.99733006, 0.98935595, 0.97618418, 0.95799003,
		0.93501423, 0.90755855, 0.87598009, 0.84068475, 0.80211977, 0.76076565,
		0.71712752, 0.67172623, 0.62508937, 0.57774224, 0.53019925, 0.48295561,
		0.43647969, 0.39120616, 0.34752997, 0.30580127, 0.26632152, 0.22934058,
		0.19505503, 0.16360756, 0.13508755, 0.10953262, 0.08693120, 0.06722600,
		0.05031820, 0.03607231, 0.02432151, 0.01487334, 0.00752000, 0.00000000};

	private static final class JFuncDef {
		private final double[] mTable;
		private final int mOversample;
		//
		private JFuncDef(final double[] t, final int over) {
			mTable = t;
			mOversample = over;
		}
		/*8,24,40,56,80,104,128,160,200,256,320*/
		private final double compute_func(final float x) {
			final double y = x * mOversample;
			int ind = (int)Math.floor( y );
			final double frac = (y - ind);
			final double frac2 = frac * frac;
			final double frac3 = frac2 * frac;
			/* CSE with handle the repeated powers */
			final double interp3 = 0.1666666667 * (frac3 - frac);
			final double interp2 = frac + 0.5 * (frac2 - frac3);
			/*interp[2] = 1.f - 0.5f*frac - frac*frac + 0.5f*frac*frac*frac;*/
			final double interp0 = -0.3333333333 * frac + 0.5 * frac2 - 0.1666666667 * frac3;
			/* Just to make sure we don't have rounding problems */
			final double interp1 = 1. - interp3 - interp2 - interp0;

			/*sum = frac*accum[1] + (1-frac)*accum[2];*/
			final double[] t = this.mTable;// java
			double res = interp0 * t[ind++];
			res += interp1 * t[ind++];
			res += interp2 * t[ind++];
			res += interp3 * t[ind];
			return res;
		}
	};

	private static final JFuncDef KAISER12 = new JFuncDef( kaiser12_table, 64 );
	private static final JFuncDef KAISER10 = new JFuncDef( kaiser10_table, 32 );
	private static final JFuncDef KAISER8 = new JFuncDef( kaiser8_table, 32 );
	private static final JFuncDef KAISER6 = new JFuncDef( kaiser6_table, 32 );

	private static final class JQualityMapping {
		private final int mBaseLength;
		private final int mOversample;
		private final float mDownsampleBandwidth;
		private final float mUpsampleBandwidth;
		private final JFuncDef mWindowFunc;
		//
		private JQualityMapping(final int length, final int over, final float ds_band, final float up_band, final JFuncDef wf ) {
			mBaseLength = length;
			mOversample = over;
			mDownsampleBandwidth = ds_band;
			mUpsampleBandwidth = up_band;
			mWindowFunc = wf;
		}
	};


	/** This table maps conversion quality to internal parameters. There are two
	   reasons that explain why the up-sampling bandwidth is larger than the
	   down-sampling bandwidth:
	   1) When up-sampling, we can assume that the spectrum is already attenuated
		  close to the Nyquist rate (from an A/D or a previous resampling filter)
	   2) Any aliasing that occurs very close to the Nyquist rate will be masked
		  by the sinusoids/noise just below the Nyquist rate (guaranteed only for
		  up-sampling).
	*/
	private static final JQualityMapping quality_map[] = {// [11] = {
		new JQualityMapping(  8,  4, 0.830f, 0.860f, KAISER6 ), /* Q0 */
		new JQualityMapping( 16,  4, 0.850f, 0.880f, KAISER6 ), /* Q1 */
		new JQualityMapping( 32,  4, 0.882f, 0.910f, KAISER6 ), /* Q2 */  /* 82.3% cutoff ( ~60 dB stop) 6  */
		new JQualityMapping( 48,  8, 0.895f, 0.917f, KAISER8 ), /* Q3 */  /* 84.9% cutoff ( ~80 dB stop) 8  */
		new JQualityMapping( 64,  8, 0.921f, 0.940f, KAISER8 ), /* Q4 */  /* 88.7% cutoff ( ~80 dB stop) 8  */
		new JQualityMapping( 80, 16, 0.922f, 0.940f, KAISER10), /* Q5 */  /* 89.1% cutoff (~100 dB stop) 10 */
		new JQualityMapping( 96, 16, 0.940f, 0.945f, KAISER10), /* Q6 */  /* 91.5% cutoff (~100 dB stop) 10 */
		new JQualityMapping(128, 16, 0.950f, 0.950f, KAISER10), /* Q7 */  /* 93.1% cutoff (~100 dB stop) 10 */
		new JQualityMapping(160, 16, 0.960f, 0.960f, KAISER10), /* Q8 */  /* 94.5% cutoff (~100 dB stop) 10 */
		new JQualityMapping(192, 32, 0.968f, 0.968f, KAISER12), /* Q9 */  /* 95.5% cutoff (~100 dB stop) 10 */
		new JQualityMapping(256, 32, 0.975f, 0.975f, KAISER12), /* Q10 */ /* 96.6% cutoff (~100 dB stop) 10 */
	};


	/** The slow way of computing a sinc for the table. Should improve that some day */
	private static final float sinc(final float cutoff, float x, final int N, final JFuncDef window_func) {
		/*fprintf (stderr, "%f ", x);*/
		final double xx = (double)(x * cutoff) * Math.PI;
		if( x < 0f ) {
			x = -x;
		}
		if( x < 1e-6f ) {
			return cutoff;
		} else if( x > .5f * (float)N ) {
			return 0;
		}
		/*FIXME: Can it really be any slower than this? */
		return (float)(cutoff * Math.sin( xx ) / xx * window_func.compute_func( Math.abs(2.f * x / N) ));
	}

	/* java: extracted inplace
	private static final void cubic_coef(final float frac, final float interp[]) {
		// Compute interpolation coefficients. I'm not sure whether this corresponds to cubic interpolation
		// but I know it's MMSE-optimal on a sinc
		final float frac2 = frac * frac;
		final float frac3 = frac2 * frac;
		interp[0] =  -0.16667f * frac + 0.16667f * frac3;
		interp[1] = frac + 0.5f *  frac2 - 0.5f * frac3;
		// interp[2] = 1.f - 0.5f*frac - frac*frac + 0.5f*frac*frac*frac;
		interp[3] = -0.33333f * frac + 0.5f * frac2 - 0.16667f * frac3;
		// Just to make sure we don't have rounding problems
		interp[2] = 1.f - interp[0] - interp[1] - interp[3];
	}
	*/

	private final int resampler_basic_direct_single(
			final int channel_index, final float[] in, final int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len) {
		final int N = mFiltLen;
		int out_sample = 0;
		int last_sample = mLastSample[channel_index];
		int samp_frac_num = mSampFracNum[channel_index];
		final float[] sinc_table = mSincTable;
		final int out_stride = mOutStride;
		final int int_advance = mIntAdvance;
		final int frac_advance = mFracAdvance;
		final int den_rate = mDenominatorRate;

		while( !(last_sample >= in_len || out_sample >= out_len ) ) {
			float sum = 0;
			for( int j = samp_frac_num * N, je = j + N, i = inoffset + last_sample; j < je; j++, i++ ) {
				sum += (sinc_table[j] * in[i]);
			}

			out[outoffset] = sum;
			out_sample++;
			outoffset += out_stride;// java
			last_sample += int_advance;
			samp_frac_num += frac_advance;
			if( samp_frac_num >= den_rate ) {
				samp_frac_num -= den_rate;
				last_sample++;
			}
		}

		mLastSample[channel_index] = last_sample;
		mSampFracNum[channel_index] = samp_frac_num;
		return out_sample;
	}

	/** This is the same as the previous function, except with a double-precision accumulator */
	private final int resampler_basic_direct_double(
			final int channel_index, final float[] in, final int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len)
	{
		final int N = mFiltLen;
		int out_sample = 0;
		int last_sample = mLastSample[channel_index];
		int samp_frac_num = mSampFracNum[channel_index];
		final float[] sinc_table = mSincTable;
		final int out_stride = mOutStride;
		final int int_advance = mIntAdvance;
		final int frac_advance = mFracAdvance;
		final int den_rate = mDenominatorRate;

		while( !(last_sample >= in_len || out_sample >= out_len) ) {
			int sinct = samp_frac_num * N;
			int iptr = inoffset + last_sample;

			float sum = 0;

			for( final int j = iptr + N; iptr < j; ) {
				sum += sinc_table[sinct++] * in[iptr++];
				sum += sinc_table[sinct++] * in[iptr++];
				sum += sinc_table[sinct++] * in[iptr++];
				sum += sinc_table[sinct++] * in[iptr++];
			}

			out[outoffset] = sum;
			out_sample++;
			outoffset += out_stride;
			last_sample += int_advance;
			samp_frac_num += frac_advance;
			if( samp_frac_num >= den_rate) {
				samp_frac_num -= den_rate;
				last_sample++;
			}
		}

		mLastSample[channel_index] = last_sample;
		mSampFracNum[channel_index] = samp_frac_num;
		return out_sample;
	}


	private final int resampler_basic_interpolate_single(
			final int channel_index, final float[] in, final int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len) {
		final int oversample = mOversample;
		final int N = mFiltLen * oversample;
		int out_sample = 0;
		int last_sample = mLastSample[channel_index];
		int samp_frac_num = mSampFracNum[channel_index];
		final int out_stride = mOutStride;
		final int int_advance = mIntAdvance;
		final int frac_advance = mFracAdvance;
		final int den_rate = mDenominatorRate;

		final float[] table = mSincTable;
		while( !(last_sample >= in_len || out_sample >= out_len) ) {
			int iptr = inoffset + last_sample;

			int offset = samp_frac_num * oversample;
			final float frac = ((float)(offset % den_rate)) / den_rate;
			offset = 4 + oversample - (offset / den_rate);

			float accum0 = 0;
			float accum1 = 0;
			float accum2 = 0;
			float accum3 = 0;

			for( int j = offset, je = j + N; j < je; j += oversample ) {
				final float curr_in = in[iptr++];
				accum0 += (curr_in * table[j - 2]);
				accum1 += (curr_in * table[j - 1]);
				accum2 += (curr_in * table[j]);
				accum3 += (curr_in * table[j + 1]);
			}

			// cubic_coef( frac, interp );
			final float frac2 = frac * frac;
			final float frac3 = frac2 * frac;
			final float interp0 =  0.16667f * (frac3 - frac);
			final float interp1 = frac + 0.5f * (frac2 - frac3);
			final float interp3 = -0.33333f * frac + 0.5f * frac2 - 0.16667f * frac3;
			final float interp2 = 1.f - interp0 - interp1 - interp3;

			final float sum = (interp0 * accum0) + (interp1 * accum1) + (interp2 * accum2) + (interp3 * accum3);

			out[outoffset] = sum;
			outoffset += out_stride;
			out_sample++;
			last_sample += int_advance;
			samp_frac_num += frac_advance;
			if( samp_frac_num >= den_rate) {
				samp_frac_num -= den_rate;
				last_sample++;
			}
		}

		mLastSample[channel_index] = last_sample;
		mSampFracNum[channel_index] = samp_frac_num;
		return out_sample;
	}

	/** This is the same as the previous function, except with a double-precision accumulator */
	private final int resampler_basic_interpolate_double(
			final int channel_index, final float[] in, final int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len)
	{
		final int oversample = mOversample;
		final int N = mFiltLen * oversample;
		int out_sample = 0;
		int last_sample = mLastSample[channel_index];
		int samp_frac_num = mSampFracNum[channel_index];
		final int out_stride = mOutStride;
		final int int_advance = mIntAdvance;
		final int frac_advance = mFracAdvance;
		final int den_rate = mDenominatorRate;

		final float[] table = mSincTable;
		while( !(last_sample >= in_len || out_sample >= out_len) ) {
			int iptr = inoffset + last_sample;

			int offset = samp_frac_num * oversample;
			final float frac = ((float)(offset % den_rate)) / den_rate;
			offset = 4 + oversample - offset / den_rate;

			double accum0 = 0;
			double accum1 = 0;
			double accum2 = 0;
			double accum3 = 0;

			for( int j = offset, je = j + N; j < je; j += oversample ) {
				final double curr_in = (double)in[iptr++];
				accum0 += (curr_in * table[j - 2]);
				accum1 += (curr_in * table[j - 1]);
				accum2 += (curr_in * table[j]);
				accum3 += (curr_in * table[j + 1]);
			}

			//cubic_coef( frac, interp );
			final float frac2 = frac * frac;
			final float frac3 = frac2 * frac;
			final float interp0 =  0.16667f * (frac3 - frac);
			final float interp1 = frac + 0.5f * (frac2 - frac3);
			final float interp3 = -0.33333f * frac + 0.5f * frac2 - 0.16667f * frac3;
			final float interp2 = 1.f - interp0 - interp1 - interp3;

			final float sum = (float)((interp0 * accum0) + (interp1 * accum1) + (interp2 * accum2) + (interp3 * accum3));

			out[outoffset] = sum;
			outoffset += out_stride;
			out_sample++;
			last_sample += int_advance;
			samp_frac_num += frac_advance;
			if( samp_frac_num >= den_rate) {
				samp_frac_num -= den_rate;
				last_sample++;
			}
		}

		mLastSample[channel_index] = last_sample;
		mSampFracNum[channel_index] = samp_frac_num;
		return out_sample;
	}

	/* This resampler is used to produce zero output in situations where memory
	   for the filter could not be allocated.  The expected numbers of input and
	   output samples are still processed so that callers failing to check error
	   codes are not surprised, possibly getting into infinite loops. */
	private final int resampler_basic_zero(
			final int channel_index, final float[] in, final int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len)
	{
		final int out_sample = 0;
		int last_sample = mLastSample[channel_index];
		int samp_frac_num = mSampFracNum[channel_index];
		final int out_stride = mOutStride;
		final int int_advance = mIntAdvance;
		final int frac_advance = mFracAdvance;
		final int den_rate = mDenominatorRate;

		// (void)in;
		for( ; !(last_sample >= in_len || out_sample >= out_len); outoffset += out_stride )// java: while( !(last_sample >= in_len || out_sample >= out_len) )
		{
			out[ outoffset ] = 0;//java: out[out_stride * out_sample++] = 0;
			last_sample += int_advance;
			samp_frac_num += frac_advance;
			if( samp_frac_num >= den_rate )
			{
				samp_frac_num -= den_rate;
				last_sample++;
			}
		}

		mLastSample[channel_index] = last_sample;
		mSampFracNum[channel_index] = samp_frac_num;
		return out_sample;
	}

	private static final int multiply_frac(// int[] result,// java: result is returned
			final int value, final int num, final int den)
	{
		final int major = value / den;
		final int remain = value % den;
		/* TODO: Could use 64 bits operation to check for overflow. But only guaranteed in C99+ */
		//if( remain > UINT32_MAX / num || major > UINT32_MAX / num
		//		|| major * num > UINT32_MAX - remain * num / den ) {
		//	return RESAMPLER_ERR_OVERFLOW;
		//}
		// XXX java: UINT32_MAX is replaced by INT32_MAX, because java doesn't have uint32
		if( remain > Integer.MAX_VALUE / num || major > Integer.MAX_VALUE / num
				|| major * num > Integer.MAX_VALUE - remain * num / den ) {
			return RESAMPLER_ERR_OVERFLOW;
		}
		// result[0] = remain * num / den + major * num;
		// return RESAMPLER_ERR_SUCCESS;
		return remain * num / den + major * num;
	}

	private final int update_filter() {

		final int old_length = mFiltLen;
		final int old_alloc_size = mMemAllocSize;

		mIntAdvance = mNumeratorRate / mDenominatorRate;
		mFracAdvance = mNumeratorRate % mDenominatorRate;

		final JQualityMapping qmap = quality_map[mQuality];
		mOversample = qmap.mOversample;
		mFiltLen = qmap.mBaseLength;

		if( mNumeratorRate > mDenominatorRate ) {
			/* down-sampling */
			mCutoff = qmap.mDownsampleBandwidth * mDenominatorRate / mNumeratorRate;
			if( (mFiltLen = multiply_frac( mFiltLen, mNumeratorRate, mDenominatorRate )) < 0 ) {// java: != RESAMPLER_ERR_SUCCESS ) {
				// goto fail;
				mResamplerPtr = resampler_basic_zero;
				mFiltLen = old_length;
				return RESAMPLER_ERR_ALLOC_FAILED;
			}
			/* Round up to make sure we have a multiple of 8 for SSE */
			mFiltLen = ((mFiltLen - 1) & (~0x7)) + 8;
			if( (mDenominatorRate << 1) < mNumeratorRate ) {
				mOversample >>= 1;
			}
			if( (mDenominatorRate << 2) < mNumeratorRate ) {
				mOversample >>= 1;
			}
			if( (mDenominatorRate << 3) < mNumeratorRate ) {
				mOversample >>= 1;
			}
			if( (mDenominatorRate << 4) < mNumeratorRate ) {
				mOversample >>= 1;
			}
			if( mOversample < 1 ) {
				mOversample = 1;
			}
		} else {
			/* up-sampling */
			mCutoff = qmap.mUpsampleBandwidth;
		}

		final boolean use_direct;
		if( RESAMPLE_FULL_SINC_TABLE ) {
			use_direct = true;
			if( Integer.MAX_VALUE / (Float.SIZE / 8) / mDenominatorRate < mFiltLen ) {
				// goto fail;
				mResamplerPtr = resampler_basic_zero;
				mFiltLen = old_length;
				return RESAMPLER_ERR_ALLOC_FAILED;
			}
		}
		else {
			/* Choose the resampling type that requires the least amount of memory */
			use_direct = mFiltLen * mDenominatorRate <= mFiltLen * mOversample + 8
					&& Integer.MAX_VALUE / (Float.SIZE / 8) / mDenominatorRate >= mFiltLen;
		}
		final int min_sinc_table_length;
		if( use_direct )
		{
			min_sinc_table_length = mFiltLen * mDenominatorRate;
		} else {
			if( (Integer.MAX_VALUE / (Float.SIZE / 8) - 8) / mOversample < mFiltLen ) {
				// goto fail;
				mResamplerPtr = resampler_basic_zero;
				mFiltLen = old_length;
				return RESAMPLER_ERR_ALLOC_FAILED;
			}

			min_sinc_table_length = mFiltLen * mOversample + 8;
		}
		if( mSincTable.length < min_sinc_table_length )
		{
			mSincTable = Arrays.copyOf( mSincTable, min_sinc_table_length );
			//if( !sinc_table ) {// java: out of memory exception
			//	goto fail;
			//}

			// st->sinc_table = sinc_table;
			// st->sinc_table_length = min_sinc_table_length;
		}
		if( use_direct )
		{
			for( int i = 0; i < mDenominatorRate; i++ ) {
				for( int j = 0, fi = i * mFiltLen; j < mFiltLen; j++ ) {
					mSincTable[fi + j] =
						sinc( mCutoff, ((j - (mFiltLen >> 1) + 1) - ((float)i) / mDenominatorRate), mFiltLen, qmap.mWindowFunc );
				}
			}
			if( mQuality > 8 ) {
				mResamplerPtr = resampler_basic_direct_double;
			} else {
				mResamplerPtr = resampler_basic_direct_single;
			}
		/*fprintf (stderr, "resampler uses direct sinc table and normalised cutoff %f\n", cutoff);*/
		} else {
			for( int i = -4, ie = (mOversample * mFiltLen + 4); i < ie; i++ ) {
				mSincTable[i + 4] =
					sinc(mCutoff, (i / (float)mOversample - (mFiltLen >> 1)), mFiltLen, qmap.mWindowFunc );
			}

			if( mQuality > 8 ) {
				mResamplerPtr = resampler_basic_interpolate_double;
			} else {
				mResamplerPtr = resampler_basic_interpolate_single;
			}

			/*fprintf (stderr, "resampler uses interpolated sinc table and normalised cutoff %f\n", cutoff);*/
		}

		/* Here's the place where we update the filter memory to take into account
		  the change in filter length. It's probably the messiest part of the code
		  due to handling of lots of corner cases. */

		/* Adding buffer_size to filt_len won't overflow here because filt_len
		  could be multiplied by sizeof(spx_word16_t) above. */
		final int min_alloc_size = mFiltLen - 1 + mBufferSize;
		if( min_alloc_size > mMemAllocSize )
		{
			if( Integer.MAX_VALUE / (Float.SIZE / 8) / mChannels < min_alloc_size ) {
				// goto fail;
				mResamplerPtr = resampler_basic_zero;
				mFiltLen = old_length;
				return RESAMPLER_ERR_ALLOC_FAILED;
			}
			mMem = Arrays.copyOf( mMem, mChannels * min_alloc_size );
			mMemAllocSize = min_alloc_size;
		}
		if( ! mIsStarted ) {
			final float[] mem = mMem;
			for( int i = 0, len = mChannels * mMemAllocSize; i < len; i++ ) {
				mem[ i ] = 0;
			}
			/*speex_warning("reinit filter");*/
		} else if( mFiltLen > old_length ) {
			/* Increase the filter length */
			/*speex_warning("increase filter size");*/
			for( int i = mChannels; i-- > 0; ) {
				int olen = old_length;
				final int im = i * mMemAllocSize;
				/*if( st.magic_samples[i])*/
				{
					/* Try and remove the magic samples as if nothing had happened */
					/* FIXME: This is wrong but for now we need it to avoid going over the array bounds */
					olen = mMagicSamples[i];

					final int je = im + olen;
					for( int j = old_length - 2 + olen, io = i * old_alloc_size + j; j >= 0; j-- ) {// java changed for (j=old_length-1+st->magic_samples[i];j--;)
						mMem[ je + j ] = mMem[io--];
					}
					for( int j = im; j < je; j++ ) {
						mMem[j] = 0;
					}
					olen = old_length + (olen << 1);
					mMagicSamples[i] = 0;
				}
				if( mFiltLen > olen ) {
					/* If the new filter length is still bigger than the "augmented" length */
					/* Copy data going backward */
					int j = 1;
					int imf = im + (mFiltLen - 2);
					for( int imj = im + (olen - 2); j < olen; j++ ) {
						mMem[ imf-- ] = mMem[ imj-- ];
					}
					/* Then put zeros for lack of anything better */
					for( ; j < mFiltLen; j++ ) {
						mMem[ imf-- ] = 0;
					}
					/* Adjust last_sample */
					mLastSample[i] += (mFiltLen - olen) >>> 1;
				} else {
					/* Put back some of the magic! */
					olen = (olen - mFiltLen) >>> 1;
					mMagicSamples[i] = olen;
					for( int j = im, je = j + mFiltLen - 1 + olen; j < je; j++ ) {
						mMem[j] = mMem[j + olen];
					}
				}
			}
		} else if( mFiltLen < old_length ) {
			/* Reduce filter length, this a bit tricky. We need to store some of the memory as "magic"
			 samples so they can be used directly as input the next time(s) */
			for( int i = 0; i < mChannels; i++ ) {
				final int old_magic = mMagicSamples[i];
				int magic = (old_length - mFiltLen) >>> 1;
				/* We must copy some of the memory that's no longer used */
				/* Copy data going backward */
				for( int j = i * mMemAllocSize, je = j + mFiltLen - 1 + magic + old_magic; j < je; j++ ) {
					mMem[ j ] = mMem[ j + magic ];
				}
				magic += old_magic;
				mMagicSamples[i] = magic;
			}
		}
		return RESAMPLER_ERR_SUCCESS;
//fail:// java: used inline
//		mResamplerPtr = resampler_basic_zero;
		/* st->mem may still contain consumed input samples for the filter.
		  Restore filt_len so that filt_len - 1 still points to the position after
		  the last of these samples. */
//		mFiltLen = old_length;
//		return RESAMPLER_ERR_ALLOC_FAILED;
	}

	/** Create a new resampler with integer input and output rates.
	 * @param nb_channels Number of channels to be processed
	 * @param in_rate Input sampling rate (integer number of Hz).
	 * @param out_rate Output sampling rate (integer number of Hz).
	 * @param quality Resampling quality between 0 and 10, where 0 has poor quality
	 * and 10 has very high quality.
	 * @return Newly created resampler state
	 * @retval NULL Error: not enough memory
	 */
	public static final JSpeexResampler speex_resampler_init(final int nb_channels,
			final int in_rate, final int out_rate, final int quality) throws IllegalArgumentException {
		return speex_resampler_init_frac( nb_channels, in_rate, out_rate, in_rate, out_rate, quality );
	}

	/** Create a new resampler with fractional input/output rates. The sampling
	 * rate ratio is an arbitrary rational number with both the numerator and
	 * denominator being 32-bit integers.
	 * @param nb_channels Number of channels to be processed
	 * @param ratio_num Numerator of the sampling rate ratio
	 * @param ratio_den Denominator of the sampling rate ratio
	 * @param in_rate Input sampling rate rounded to the nearest integer (in Hz).
	 * @param out_rate Output sampling rate rounded to the nearest integer (in Hz).
	 * @param quality Resampling quality between 0 and 10, where 0 has poor quality
	 * and 10 has very high quality.
	 * @return Newly created resampler state
	 * @retval NULL Error: not enough memory
	 */
	public static final JSpeexResampler speex_resampler_init_frac(final int nb_channels,
			final int ratio_num, final int ratio_den, final int in_rate, final int out_rate,
			final int quality)
		throws IllegalArgumentException
	{
		if( nb_channels == 0 || ratio_num == 0 || ratio_den == 0 || quality > 10 || quality < 0 ) {
			throw new IllegalArgumentException( speex_resampler_strerror( RESAMPLER_ERR_INVALID_ARG ) );
		}
		final JSpeexResampler st = new JSpeexResampler();
		st.mIsInitialised = false;
		st.mIsStarted = false;
		st.mInRate = 0;
		st.mOutRate = 0;
		st.mNumeratorRate = 0;
		st.mDenominatorRate = 0;
		st.mQuality = -1;
		// st.sinc_table_length = 0;
		st.mMemAllocSize = 0;
		st.mFiltLen = 0;
		st.mMem = new float[0];// java to make resize lately
		// FIXME sinc_table is not assigned
		st.mResamplerPtr = 0;

		st.mCutoff = 1.f;
		st.mChannels = nb_channels;
		st.mInStride = 1;
		st.mOutStride = 1;

		st.mBufferSize = 160;

		/* Per channel data */
		st.mLastSample = new int[ nb_channels ];
		st.mMagicSamples = new int[ nb_channels ];
		st.mSampFracNum = new int[ nb_channels ];
		/* for( int i = 0; i < nb_channels; i++ ) {
			st.last_sample[i] = 0;
			st.magic_samples[i] = 0;
			st.samp_frac_num[i] = 0;
		}*/

		st.speex_resampler_set_quality( quality );
		st.speex_resampler_set_rate_frac( ratio_num, ratio_den, in_rate, out_rate );

		final int filter_err = st.update_filter();
		if( filter_err == RESAMPLER_ERR_SUCCESS )
		{
			st.mIsInitialised = true;
		} else {
			return null;
		}
		//if( err != RESAMPLER_ERR_SUCCESS )
		//	throw new IllegalArgumentException( speex_resampler_strerror( filter_err ) );
		return st;
	}

	/** Destroy a resampler state.
	 * @param st Resampler state
	 */
	/* public static final void speex_resampler_destroy(final JSpeexResampler st)
	{// java: use st = null
		speex_free(st.mem);
		speex_free(st.sinc_table);
		speex_free(st.last_sample);
		speex_free(st.magic_samples);
		speex_free(st.samp_frac_num);
		speex_free(st);
	} */

	/**
	 *
	 * @param channel_index
	 * @param in_len
	 * @param out
	 * @param outoffset
	 * @param out_len
	 * @return (out samples) | (in_len << 32)
	 */
	private final long speex_resampler_process_native(
			final int channel_index, int in_len, final float[] out, final int outoffset, final int out_len) {
		final int offset = channel_index * mMemAllocSize;

		mIsStarted = true;

		/* Call the right resampler through the function ptr */
		final float[] m = mMem;
		final int out_sample;
		switch( mResamplerPtr ) {
		case resampler_basic_zero:
			out_sample = resampler_basic_zero( channel_index, m, offset, in_len, out, outoffset, out_len );
			break;
		case resampler_basic_direct_single:
			out_sample = resampler_basic_direct_single( channel_index, m, offset, in_len, out, outoffset, out_len );
			break;
		case resampler_basic_direct_double:
			out_sample = resampler_basic_direct_double( channel_index, m, offset, in_len, out, outoffset, out_len );
			break;
		case resampler_basic_interpolate_single:
			out_sample = resampler_basic_interpolate_single( channel_index, m, offset, in_len, out, outoffset, out_len );
			break;
		case resampler_basic_interpolate_double:
			out_sample = resampler_basic_interpolate_double( channel_index, m, offset, in_len, out, outoffset, out_len );
			break;
		default:
			return 0;
		}

		int last_sample = mLastSample[channel_index];
		if( last_sample < in_len ) {
			in_len = last_sample;
		}
		// *out_len = out_sample;// java: return
		last_sample -= in_len;
		mLastSample[channel_index] = last_sample;

		final int ilen = in_len;

		for( int j = offset, je = j + mFiltLen - 1; j < je; ++j ) {
			m[j] = m[j + ilen];
		}

		return ((long)out_sample) | ((long)in_len << 32);
	}

	/**
	 * @return out_len | (outoffset << 32)
	 */
	private final long speex_resampler_magic(
			final int channel_index, final float[] out, int outoffset, int out_len ) {
		int magic_sample = mMagicSamples[channel_index];
		int tmp_in_len = magic_sample;

		final long tmp = speex_resampler_process_native( channel_index, tmp_in_len, out, outoffset, out_len );
		out_len = (int)tmp;
		tmp_in_len = (int)(tmp >> 32);

		magic_sample -= tmp_in_len;

		/* If we couldn't process all "magic" input samples, save the rest for next time */
		if( magic_sample != 0 ) {
			final int offset = channel_index * mMemAllocSize;
			final float[] m = mMem;
			int i = offset + mFiltLen - 1;
			tmp_in_len += i;
			for( final int ie = i + magic_sample ; i < ie; ) {
				m[ i++ ] = m[ tmp_in_len++ ];
			}
		}
		mMagicSamples[channel_index] = magic_sample;
		outoffset += out_len * mOutStride;
		return ((long)out_len) | ((long)outoffset << 32);
	}

	/** Resample a float array. The input and output buffers must *not* overlap.
	 * @param channel_index Index of the channel to process for the multi-channel
	 * base (0 otherwise)
	 * @param in Input buffer
	 * @param in_len Number of input samples in the input buffer.
	 * @param out Output buffer
	 * @param out_len Size of the output buffer.
	 * @return (the number of samples written) | (Returns the number of samples processed << 32)
	 * or a negative error code.
	 */
	public final long speex_resampler_process_float(
			final int channel_index, final float[] in, int inoffset, final int in_len,
			final float[] out, int outoffset, final int out_len) {
		int ilen = in_len;
		int olen = out_len;
		int filt_offs = mFiltLen - 1;
		final int xlen = mMemAllocSize - filt_offs;
		filt_offs += channel_index * mMemAllocSize;
		final int istride = mInStride;

		if( mMagicSamples[channel_index] != 0 ) {
			final long tmp = speex_resampler_magic( channel_index, out, outoffset, olen );
			olen -= (int)tmp;
			outoffset = (int)(tmp >> 32);
		}
		if( 0 == mMagicSamples[channel_index] ) {
			final int ostride = mOutStride;
			final float[] m = mMem;
			while( ilen != 0 && olen != 0 ) {
				int ichunk = (ilen > xlen) ? xlen : ilen;
				int ochunk = olen;

				if( null != in ) {
					for( int j = filt_offs, je = j + ichunk, i = inoffset; j < je; ++j, i += istride ) {
						m[j] = in[i];
					}
				} else {
					for( int j = filt_offs, je = j + ichunk; j < je; ++j ) {
						m[j] = 0;
					}
				}
				final long tmp = speex_resampler_process_native( channel_index, ichunk, out, outoffset, ochunk );
				ochunk = (int)tmp;
				ichunk = (int)(tmp >> 32);
				ilen -= ichunk;
				olen -= ochunk;
				outoffset += ochunk * ostride;
				if( null != in ) {
					inoffset += ichunk * istride;
				}
			}
		}
		//return ((long)(out_len - olen)) | ((long)(in_len - ilen) << 32);
		//return mResamplerPtr == resampler_basic_zero ? RESAMPLER_ERR_ALLOC_FAILED : RESAMPLER_ERR_SUCCESS;
		if( mResamplerPtr == resampler_basic_zero ) {
			return (long)RESAMPLER_ERR_ALLOC_FAILED;
		}
		return ((long)(out_len - olen)) | ((long)(in_len - ilen) << 32);
	}

	/** Resample an int array. The input and output buffers must *not* overlap.
	 * @param channel_index Index of the channel to process for the multi-channel
	 * base (0 otherwise)
	 * @param in Input buffer
	 * @param in_len Number of input samples in the input buffer.
	 * @param out Output buffer
	 * @param out_len Size of the output buffer.
	 * @return (the number of samples written) | (Returns the number of samples processed << 32)
	 */
	public final long speex_resampler_process_int(
			final int channel_index, final short[] in, int inoffset, final int in_len,
			final short[] out, int outoffset, final int out_len) {
		final int istride_save = mInStride;
		final int ostride_save = mOutStride;
		int ilen = in_len;
		int olen = out_len;
		final int x = channel_index * mMemAllocSize + mFiltLen - 1;
		final int xlen = mMemAllocSize - (mFiltLen - 1);

		final int ylen = FIXED_STACK_ALLOC;
		final float ystack[] = new float[FIXED_STACK_ALLOC];

		mOutStride = 1;

		final int[] m = mMagicSamples;
		final float[] mem = mMem;

		while( ilen != 0 && olen != 0 ) {
			int y = 0;// ystack;
			int ichunk = (ilen > xlen) ? xlen : ilen;
			int ochunk = (olen > ylen) ? ylen : olen;
			int omagic = 0;

			if( m[channel_index] != 0 ) {
				final long tmp = speex_resampler_magic( channel_index, ystack, y, ochunk );
				omagic = (int)tmp;
				y = (int)(tmp >> 32);
				ochunk -= omagic;
				olen -= omagic;
			}
			if( 0 == m[channel_index] ) {
				if( in != null ) {
					for( int j = x, je = j + ichunk, i = inoffset; j < je; ++j, i += istride_save ) {
						mem[j] = in[i];
					}
				} else {
					for( int j = x, je = j + ichunk; j < je; ++j ) {
						mem[j] = 0;
					}
				}

				final long tmp = speex_resampler_process_native( channel_index, ichunk, ystack, y, ochunk );
				ochunk = (int)tmp;
				ichunk = (int)(tmp >> 32);
			} else {
				ichunk = 0;
				ochunk = 0;
			}

			final int ochunk_omagic = ochunk + omagic;
			for( int j = 0; j < ochunk_omagic; ++j ) {
				final float v = ystack[j];
				out[outoffset + j * ostride_save] = (v < -32767.5f ? -32768 : (v > 32766.5f ? 32767 : (short)Math.floor((double)(.5f + v))));
			}

			ilen -= ichunk;
			olen -= ochunk;
			outoffset += ochunk_omagic * ostride_save;
			if( in != null ) {
				inoffset += ichunk * istride_save;
			}
		}
		mOutStride = ostride_save;

		//return ((long)(out_len - olen)) | ((long)(in_len - ilen) << 32);
		//return mResamplerPtr == resampler_basic_zero ? RESAMPLER_ERR_ALLOC_FAILED : RESAMPLER_ERR_SUCCESS;
		if( mResamplerPtr == resampler_basic_zero ) {
			return (long)RESAMPLER_ERR_ALLOC_FAILED;
		}
		return ((long)(out_len - olen)) | ((long)(in_len - ilen) << 32);
	}

	/** Resample an interleaved float array. The input and output buffers must *not* overlap.
	 * @param in Input buffer
	 * @param in_len Number of input samples in the input buffer.
	 * This is all per-channel.
	 * @param out Output buffer
	 * @param out_len Size of the output buffer.
	 * This is all per-channel.
	 * @return (the number of samples written) | (Returns the number of samples processed << 32)
	 * This is all per-channel.
	 */
	public final long speex_resampler_process_interleaved_float(
			final float[] in, final int inoffset, final int in_len, final float[] out, final int outoffset, final int out_len) {
		final int istride_save = mInStride;
		final int ostride_save = mOutStride;
		final int nchannels = mChannels;
		mInStride = mOutStride = nchannels;
		long tmp = 0;
		if( in != null ) {
			for( int i = 0; i < nchannels; i++ ) {
				tmp = speex_resampler_process_float( i, in, inoffset + i, in_len, out, outoffset + i, out_len );
			}
		} else {
			for( int i = 0; i < nchannels; i++ ) {
				tmp = speex_resampler_process_float( i, null, 0, in_len, out, outoffset + i, out_len );
			}
		}
		mInStride = istride_save;
		mOutStride = ostride_save;
		// return mResamplerPtr == resampler_basic_zero ? RESAMPLER_ERR_ALLOC_FAILED : RESAMPLER_ERR_SUCCESS;
		if( mResamplerPtr == resampler_basic_zero ) {
			return (long)RESAMPLER_ERR_ALLOC_FAILED;
		}
		return tmp;
	}

	/** Resample an interleaved int array. The input and output buffers must *not* overlap.
	 * @param in Input buffer
	 * @param in_len Number of input samples in the input buffer.
	 * This is all per-channel.
	 * @param out Output buffer
	 * @param out_len Size of the output buffer.
	 * This is all per-channel.
	 * @return (the number of samples written) | (Returns the number of samples processed << 32)
	 * This is all per-channel.
	 */
	public final long speex_resampler_process_interleaved_int(final short[] in, final int in_len, final short[] out, final int out_len) {
		final int istride_save = mInStride;
		final int ostride_save = mOutStride;
		final int nchannels = mChannels;
		mInStride = mOutStride = nchannels;
		long tmp = 0;
		if( in != null ) {
			for( int i = 0; i < nchannels; i++ ) {
				tmp = speex_resampler_process_int( i, in, i, in_len, out, i, out_len );
			}
		} else {
			for( int i = 0; i < nchannels; i++ ) {
				tmp = speex_resampler_process_int( i, null, 0, in_len, out, i, out_len );
			}
		}
		mInStride = istride_save;
		mOutStride = ostride_save;
		//return tmp;
		//return mResamplerPtr == resampler_basic_zero ? RESAMPLER_ERR_ALLOC_FAILED : RESAMPLER_ERR_SUCCESS;
		if( mResamplerPtr == resampler_basic_zero ) {
			return (long)RESAMPLER_ERR_ALLOC_FAILED;
		}
		return tmp;
	}

	/** Set (change) the input/output sampling rates (integer value).
	 * @param in_rate Input sampling rate (integer number of Hz).
	 * @param out_rate Output sampling rate (integer number of Hz).
	 */
	public final int speex_resampler_set_rate(final int in_rate, final int out_rate) {
		return speex_resampler_set_rate_frac( in_rate, out_rate, in_rate, out_rate );
	}

	/** Get the current input sampling rate (integer value).
	 * @return in_rate Input sampling rate (integer number of Hz) copied.
	 */
	public final int speex_resampler_get_in_rate() {
		return mInRate;
	}

	/** Get the current output sampling rate (integer value).
	 * @return out_rate Output sampling rate (integer number of Hz) copied.
	 */
	public final int speex_resampler_get_out_rate() {
		return mOutRate;
	}

/* java: extracted inline
	private static final int compute_gcd(int a, int b)
	{
		while( b != 0 )
		{
			final int temp = a;

			a = b;
			b = temp % b;
		}
		return a;
	}
*/
	/** Set (change) the input/output sampling rates and resampling ratio
	 * (fractional values in Hz supported).
	 * @param st Resampler state
	 * @param ratio_num Numerator of the sampling rate ratio
	 * @param ratio_den Denominator of the sampling rate ratio
	 * @param in_rate Input sampling rate rounded to the nearest integer (in Hz).
	 * @param out_rate Output sampling rate rounded to the nearest integer (in Hz).
	 */
	public final int speex_resampler_set_rate_frac(int ratio_num, int ratio_den, final int in_rate, final int out_rate)
	{
		if( ratio_num == 0 || ratio_den == 0 ) {
			return RESAMPLER_ERR_INVALID_ARG;
		}

		if( mInRate == in_rate && mOutRate == out_rate && mNumeratorRate == ratio_num && mDenominatorRate == ratio_den ) {
			return RESAMPLER_ERR_SUCCESS;
		}

		final int old_den = mDenominatorRate;
		mInRate = in_rate;
		mOutRate = out_rate;
		mNumeratorRate = ratio_num;
		mDenominatorRate = ratio_den;
		// final int fact = compute_gcd( ratio_num, ratio_den );// java: inline
		while( ratio_den != 0 )
		{
			final int temp = ratio_num;

			ratio_num = ratio_den;
			ratio_den = temp % ratio_den;
		}
		final int fact = ratio_num;

		mNumeratorRate /= fact;
		mDenominatorRate /= fact;

		if( old_den > 0 ) {
			for( int i = 0; i < mChannels; i++ ) {
				if( (mSampFracNum[i] = multiply_frac( mSampFracNum[i], mDenominatorRate, old_den)) < 0 ) {// java: != RESAMPLER_ERR_SUCCESS ) {
					return RESAMPLER_ERR_OVERFLOW;
				}
				/* Safety net */
				if( mSampFracNum[i] >= mDenominatorRate ) {
					mSampFracNum[i] = mDenominatorRate - 1;
				}
			}
		}

		if( mIsInitialised ) {
			return update_filter();
		}
		return RESAMPLER_ERR_SUCCESS;
	}

	/** Get the current resampling ratio. This will be reduced to the least
	 * common denominator.
	 * @return ratio_num Numerator of the sampling rate ratio copied
	 */
	public final int speex_resampler_get_ratio_num() {
		return mNumeratorRate;
	}
	/** Get the current resampling ratio. This will be reduced to the least
	 * common denominator.
	 * @return ratio_den Denominator of the sampling rate ratio copied
	 */
	public final int speex_resampler_get_ratio_den() {
		return mDenominatorRate;
	}

	/** Set (change) the conversion quality.
	 * @param quality Resampling quality between 0 and 10, where 0 has poor
	 * quality and 10 has very high quality.
	 */
	public final int speex_resampler_set_quality(final int quality) {
		if( quality > 10 || quality < 0 ) {
			return RESAMPLER_ERR_INVALID_ARG;
		}
		if( mQuality == quality ) {
			return RESAMPLER_ERR_SUCCESS;
		}
		mQuality = quality;
		if( mIsInitialised ) {
			return update_filter();
		}
		return RESAMPLER_ERR_SUCCESS;
	}

	/** Get the conversion quality.
	 * @param quality Resampling quality between 0 and 10, where 0 has poor
	 * quality and 10 has very high quality.
	 */
	public final int speex_resampler_get_quality() {
		return mQuality;
	}

	/** Set (change) the input stride.
	 * @param stride Input stride
	 */
	public final void speex_resampler_set_input_stride(final int stride) {
		mInStride = stride;
	}

	/** Get the input stride.
	 * @param stride Input stride copied
	 */
	public final int speex_resampler_get_input_stride() {
		return mInStride;
	}

	/** Set (change) the output stride.
	 * @param stride Output stride
	 */
	public final void speex_resampler_set_output_stride(final int stride) {
		mOutStride = stride;
	}

	/** Get the output stride.
	 * @param stride Output stride
	 */
	public final int speex_resampler_get_output_stride() {
		return mOutStride;
	}

	/** Get the latency introduced by the resampler measured in input samples.
	 */
	public final int speex_resampler_get_input_latency() {
		return mFiltLen >>> 1;
	}

	/** Get the latency introduced by the resampler measured in output samples.
	 */
	public final int speex_resampler_get_output_latency() {
		return ((mFiltLen >>> 1) * mDenominatorRate + (mNumeratorRate >>> 1)) / mNumeratorRate;
	}

	/** Make sure that the first samples to go out of the resamplers don't have
	 * leading zeros. This is only useful before starting to use a newly created
	 * resampler. It is recommended to use that when resampling an audio file, as
	 * it will generate a file with the same length. For real-time processing,
	 * it is probably easier not to use this call (so that the output duration
	 * is the same for the first frame).
	 * @param st Resampler state
	 */
	public final int speex_resampler_skip_zeros() {
		final int len2 = mFiltLen >>> 1;
		final int sample[] = mLastSample;
		for( int i = 0, ie = mChannels; i < ie; i++ ) {
			sample[i] = len2;
		}
		return RESAMPLER_ERR_SUCCESS;
	}

	/** Reset a resampler so a new (unrelated) stream can be processed.
	 */
	public final int speex_resampler_reset_mem() {
		for( int i = 0, ie = mChannels; i < ie; i++ )
		{
			mLastSample[i] = 0;
			mMagicSamples[i] = 0;
			mSampFracNum[i] = 0;
		}
		final float[] m = mMem;
		for( int i = 0, ie = mChannels * (mFiltLen - 1); i < ie; i++ ) {
			m[i] = 0;
		}
		return RESAMPLER_ERR_SUCCESS;
	}

	/** Returns the English meaning for an error code
	 * @param err Error code
	 * @return English string
	 */
	public static final String speex_resampler_strerror(final int err) {
		switch( err ) {
		case RESAMPLER_ERR_SUCCESS:
			return "Success.";
		case RESAMPLER_ERR_ALLOC_FAILED:
			return "Memory allocation failed.";
		case RESAMPLER_ERR_BAD_STATE:
			return "Bad resampler state.";
		case RESAMPLER_ERR_INVALID_ARG:
			return "Invalid argument.";
		case RESAMPLER_ERR_PTR_OVERLAP:
			return "Input and output buffers overlap.";
		//FIXME there is not a string for case RESAMPLER_ERR_OVERFLOW:
			//return;
		default:
			return "Unknown error. Bad error code or strange version mismatch.";
		}
	}
}
