package celt;

import opus.Jopus_defines;

// celt_encoder.c
/**
 * #define CELTEncoder OpusCustomEncoder
 */
public final class JCELTEncoder extends JOpusCustomEncoder {
	/** default constructor */
	public JCELTEncoder() {
	}
	/**
	 * java helper to replace code:
	 * <pre>
	 * int decsize = opus_decoder_get_size( 1 );
	 * OpusDecoder *dec = (OpusDecoder*)malloc( decsize );
	 * </pre>
	 * @param channels
	 */
	public JCELTEncoder(final int channels) {
		final JCELTMode m = JCELTMode.opus_custom_mode_create( 48000, 960, null );
		opus_custom_encoder_init_arch( m, channels  );
	}
	//
	// start celt_encoder.c
	/*
	private static final int celt_encoder_get_size(final int channels)
	{
		final JCELTMode mode = JCELTMode.opus_custom_mode_create( 48000, 960, null );
		return opus_custom_encoder_get_size( mode, channels );
	}
*/
/*
	private static final int opus_custom_encoder_get_size(final JCELTMode mode, final int channels )
	{
		int size = sizeof( final struct CELTEncoder )
				+ (channels * mode.overlap - 1) * sizeof( celt_sig )   // celt_sig in_mem[channels*mode.overlap];
				+ channels * COMBFILTER_MAXPERIOD * sizeof( celt_sig ) // celt_sig prefilter_mem[channels*COMBFILTER_MAXPERIOD];
				+ 4 * channels * mode.nbEBands * sizeof( opus_val16 ); // opus_val16 oldBandE[channels*mode.nbEBands];
							  // opus_val16 oldLogE[channels*mode.nbEBands];
							  // opus_val16 oldLogE2[channels*mode.nbEBands];
							  // opus_val16 energyError[channels*mode->nbEBands];
		return size;
	}
*/
/* #ifdef CUSTOM_MODES
	CELTEncoder *opus_custom_encoder_create( const CELTMode *mode, int channels, int *error )
	{
		int ret;
		CELTEncoder *st = ( CELTEncoder * )opus_alloc( opus_custom_encoder_get_size( mode, channels ) );
		// init will handle the NULL case
		ret = opus_custom_encoder_init( st, mode, channels );
		if( ret != OPUS_OK )
		{
			opus_custom_encoder_destroy( st );
			st = NULL;
		}
		if( error )
			*error = ret;
		return st;
	}
#endif */ /* CUSTOM_MODES */

	final int opus_custom_encoder_init_arch(final JCELTMode celt_mode, final int nchannels)//, final int arch)
	{
		if( nchannels < 0 || nchannels > 2 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		if( /* st == null ||*/ celt_mode == null ) {
			return Jopus_defines.OPUS_ALLOC_FAIL;
		}

		// OPUS_CLEAR( ( char* )st, opus_custom_encoder_get_size( mode, channels ) );
		clear( true );

		this.in_mem = new float[ nchannels * celt_mode.overlap ];
		this.prefilter_mem = new float[ nchannels * Jcelt.COMBFILTER_MAXPERIOD ];
		final int size = nchannels * celt_mode.nbEBands;
		this.oldBandE = new float[ size ];
		this.oldLogE  = new float[ size ];
		this.oldLogE2 = new float[ size ];
		this.energyError = new float[ size ];

		this.mode = celt_mode;
		this.stream_channels = this.channels = nchannels;

		this.upsample = 1;
		this.start = 0;
		this.end = this.mode.effEBands;
		this.signalling = true;
		// st.arch = arch;

		this.constrained_vbr = true;
		this.clip = true;

		this.bitrate = Jopus_defines.OPUS_BITRATE_MAX;
		this.vbr = false;
		this.force_intra = false;
		this.complexity = 5;
		this.lsb_depth = 24;

		opus_custom_encoder_ctl( Jopus_defines.OPUS_RESET_STATE );

		return Jopus_defines.OPUS_OK;
	}

/* #ifdef CUSTOM_MODES
	private static final int opus_custom_encoder_init( CELTEncoder *st, const CELTMode *mode, int channels )
	{
		return opus_custom_encoder_init_arch( st, mode, channels, opus_select_arch() );
	}
#endif */

	/**
	 *
	 * @param sampling_rate
	 * @param nchannels
	 * @return status
	 */
	public final int celt_encoder_init(final int sampling_rate, final int nchannels)//, final int arch)
	{
		int ret;
		ret = opus_custom_encoder_init_arch( JCELTMode.opus_custom_mode_create( 48000, 960, null ), nchannels );//, arch );
		if( ret != Jopus_defines.OPUS_OK ) {
			return ret;
		}
		this.upsample = Jcelt.resampling_factor( sampling_rate );
		return Jopus_defines.OPUS_OK;
	}

/* #ifdef CUSTOM_MODES
	void opus_custom_encoder_destroy( CELTEncoder *st )
	{
		opus_free( st );
	}
#endif */ /* CUSTOM_MODES */

	/**
	 * Auxiliary structure to exchange data with the transient_analysis in java
	 */
	private static final class Janalysis_aux {
		float tf_estimate;
		int tf_chan;
		boolean weak_transient;
	}

	/** Table of 6*64/x, trained on real data to minimize the average error */
	private static final int inv_table[/* 128 */] = {
			255,255,156,110, 86, 70, 59, 51, 45, 40, 37, 33, 31, 28, 26, 25,
			23, 22, 21, 20, 19, 18, 17, 16, 16, 15, 15, 14, 13, 13, 12, 12,
			12, 12, 11, 11, 11, 10, 10, 10,  9,  9,  9,  9,  9,  9,  8,  8,
			8,  8,  8,  7,  7,  7,  7,  7,  7,  6,  6,  6,  6,  6,  6,  6,
			6,  6,  6,  6,  6,  6,  6,  6,  6,  5,  5,  5,  5,  5,  5,  5,
			5,  5,  5,  5,  5,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,
			4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  4,  3,  3,
			3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  3,  2,
		};
	private static final boolean transient_analysis(final float[] in, final int len, final int C,
			final Janalysis_aux aux, // java replaces tf_estimate, tf_chan, weak_transient to get data back
			// final float[] tf_estimate, final int[] tf_chan,
			final boolean allow_weak_transients/*, boolean[] weak_transient*/)
	{
		boolean is_transient = false;
		int mask_metric = 0;
		/* Forward masking: 6.7 dB/ms. */
/* #ifdef FIXED_POINT
		int forward_shift = 4;
#else */
		float forward_decay = .0625f;
// #endif
		/* Table of 6*64/x, trained on real data to minimize the average error */

		// SAVE_STACK;
		final float[] tmp = new float[len];

		aux.weak_transient = false;
		/* For lower bitrates, let's be more conservative and have a forward masking
		  decay of 3.3 dB/ms. This avoids having to code transients at very low
		  bitrate (mostly for hybrid), which can result in unstable energy and/or
		  partial collapse. */
		if( allow_weak_transients )
		{
/* #ifdef FIXED_POINT
			forward_shift = 5;
#else */
			forward_decay = .03125f;
// #endif
		}
		final int len2 = len >>> 1;
		for( int c = 0; c < C; c++ )
		{
			int unmask = 0;
			float mem0 = 0;
			float mem1 = 0;
			/* High - pass filter: ( 1 - 2*z^-1 + z^-2 ) / ( 1 - z^-1 + .5*z^-2 ) */
			for( int i = 0; i < len; i++ )
			{
				final float x = in[i + c * len];
				final float y = mem0 + x;
/* #ifdef FIXED_POINT
				mem0 = mem1 + y - SHL32( x, 1 );
				mem1 = x - SHR32( y, 1 );
#else */
				mem0 = mem1 + y - 2f * x;
				mem1 = x - .5f * y;
// #endif
				tmp[i] = y;
				/*printf( "%f ", tmp[i] ); */
			}
			/*printf( "\n" ); */
			/* First few samples are bad because we don't propagate the memory */
			// OPUS_CLEAR( tmp, 12 );
			{
				int i = 0;
				do {
					tmp[i] = 0;
				} while( ++i < 12 );
			}

/* #ifdef FIXED_POINT
			// Normalize tmp to max range
			{
				int shift = 0;
				shift = 14 - celt_ilog2( MAX16( 1, celt_maxabs16( tmp, len ) ) );
				if( shift!= 0 )
				{
					for( i = 0; i < len; i++ )
						tmp[i] = SHL16( tmp[i], shift );
				}
			}
#endif */

			float mean = 0;
			mem0 = 0;
			/* Grouping by two to reduce complexity */
			/* Forward pass to compute the post - echo threshold*/
			for( int i = 0; i < len2; i++ )
			{
				int i2 = i << 1;// java
				float v = tmp[i2++];// java
				v *= v;// java
				float x2 = tmp[i2];// java
				x2 *= x2;// java
				x2 += v;
				mean += x2;
/* #ifdef FIXED_POINT
				// FIXME: Use PSHR16() instead
				tmp[i] = mem0 + PSHR32( x2 - mem0, forward_shift );
#else */
				// tmp[i] = mem0 + (forward_decay * (x2 - mem0));
				mem0 += forward_decay * (x2 - mem0);
// #endif
				// mem0 = tmp[i];
				tmp[i] = mem0;// java order of operations is changed
			}

			mem0 = 0;
			float maxE = 0;
			/* Backward pass to compute the pre - echo threshold */
			for( int i = len2 - 1; i >= 0; i-- )
			{
				/* Backward masking: 13.9 dB/ms. */
/* #ifdef FIXED_POINT
				// FIXME: Use PSHR16() instead
				tmp[i] = mem0 + PSHR32( tmp[i] - mem0, 3 );
#else */
				// tmp[i] = mem0 + 0.125f * (tmp[i] - mem0);
				mem0 += 0.125f * (tmp[i] - mem0);
// #endif
				//mem0 = tmp[i];
				tmp[i] = mem0;// java order of operations is changed
				maxE = maxE > mem0 ? maxE : mem0;
			}
			/*for( i = 0; i < len2; i++ )printf( "%f ", tmp[i]/mean ); printf( "\n" ); */

			/* Compute the ratio of the "frame energy" over the harmonic mean of the energy.
			   This essentially corresponds to a bitrate - normalized temporal noise - to - mask
			   ratio */

			/* As a compromise with the old transient detector, frame energy is the
			   geometric mean of the energy and half the max */
/* #ifdef FIXED_POINT
			// Costs two sqrt() to avoid overflows
			mean = MULT16_16( celt_sqrt( mean ), celt_sqrt( MULT16_16( maxE, len2 >> 1 ) ) );
#else */
			mean = (float)Math.sqrt( (double)(mean * maxE * .5f * (float)len2) );
// #endif
			/* Inverse of the mean energy in Q15 + 6 */
			final float norm = len2 / (Jfloat_cast.EPSILON + mean);
			/* Compute harmonic mean discarding the unreliable boundaries
			   The data is smooth, so we only take 1/4th of the samples */
			unmask = 0;
			/* We should never see NaNs here. If we find any, then something really bad happened and we better abort
			 before it does any damage later on. If these asserts are disabled (no hardening), then the table
			 lookup a few lines below (id = ...) is likely to crash dur to an out-of-bounds read. DO NOT FIX
			 that crash on NaN since it could result in a worse issue later on. */
			// celt_assert(!celt_isnan(tmp[0]));
			// celt_assert(!celt_isnan(norm));

			for( int i = 12, ie = len2 - 5; i < ie; i += 4 )
			{
/* #ifdef FIXED_POINT
				id = MAX32( 0,MIN32( 127,MULT16_32_Q15( tmp[i] + EPSILON,norm ) ) );  // Do not round to nearest
#else */
				float fid = (float)Math.floor( (double)(64f * norm * (tmp[i] + Jfloat_cast.EPSILON)) );
				fid = 127f <= fid ? 127f : fid;
				fid = 0f >= fid ? 0f : fid;
				//id = (int)MAX32( 0,MIN32( 127, floor( 64f * norm * (tmp[i] + EPSILON) )) );  /* Do not round to nearest */
// #endif
				unmask += inv_table[(int)fid];
			}
			/*printf( "%d\n", unmask ); */
			/* Normalize, compensate for the 1/4th of the sample and the factor of 6 in the inverse table */
			// unmask = 64 * unmask * 4 / (6 * (len2 - 17));
			unmask <<= 8;
			unmask /= (6 * (len2 - 17));
			if( unmask > mask_metric )
			{
				aux.tf_chan = c;
				mask_metric = unmask;
			}
		}
		is_transient = mask_metric > 200;
		/* For low bitrates, define "weak transients" that need to be
		  handled differently to avoid partial collapse. */
		if( allow_weak_transients && is_transient && mask_metric < 600 ) {
			is_transient = false;
			aux.weak_transient = true;
		}
		/* Arbitrary metric for VBR boost */
		// tf_max = MAX16( 0, celt_sqrt( 27 * mask_metric ) - 42 );
		float tf_max = (float)Math.sqrt( 27f * mask_metric ) - 42f;
		tf_max = 0 >= tf_max ? 0 : tf_max;
		/* *tf_estimate = 1 + MIN16( 1, sqrt( MAX16( 0, tf_max - 30 ) )/20 );  */
		// tf_estimate[0] = celt_sqrt( MAX32( 0, 0.0069f * MIN16( 163f, tf_max ) - 0.139f ) );
		tf_max = 163f <= tf_max ? 163f : tf_max;
		tf_max *= 0.0069f;
		tf_max -= 0.139f;
		tf_max = 0 >= tf_max ? 0 : tf_max;
		aux.tf_estimate = (float)Math.sqrt( (double)tf_max );
		/*printf( "%d %f\n", tf_max, mask_metric ); */
		// RESTORE_STACK;
/* #ifdef FUZZING
		is_transient = rand() & 0x1;
#endif */
		/*printf( "%d %f %d\n", is_transient, ( float )*tf_estimate, tf_max ); */
		return is_transient;
	}

	/**
	 * Looks for sudden increases of energy to decide whether we need to patch
	 * the transient decision
	 * @param newE
	 * @param oldE
	 * @param nbEBands
	 * @param start
	 * @param end
	 * @param C
	 * @return
	 */
	private static final boolean patch_transient_decision(final float[] newE,
			final float[] oldE,
			final int nbEBands, final int start, int end, final int C)
	{
		float mean_diff = 0;
		final float spread_old[] = new float[26];
		/* Apply an aggressive (  - 6 dB/Bark ) spreading function to the old frame to
		   avoid false detection caused by irrelevant bands */
		if( C == 1 )
		{
			spread_old[start] = oldE[start];
			for( int i = start + 1; i < end; i++ ) {
				final float v1 = spread_old[i - 1] - 1.0f;
				final float v2 = oldE[i];
				spread_old[i] = v1 >= v2 ? v1 : v2;
			}
		} else {
			float v1 = oldE[start];
			float v2 = oldE[start + nbEBands];
			spread_old[start] = v1 > v2 ? v1 : v2;
			for( int i = start + 1; i < end; i++ ) {
				v1 = oldE[i];
				v2 = oldE[i + nbEBands];
				v2 = v1 >= v2 ? v1 : v2;
				v1 = spread_old[i - 1] - 1.0f;
				spread_old[i] = v1 >= v2 ? v1 : v2;
			}
		}
		for( int i = end - 2; i >= start; i-- ) {
			final float v1 = spread_old[i];
			final float v2 = spread_old[i + 1] - 1.0f;
			spread_old[i] = v1 >= v2 ? v1 : v2;
		}
		/* Compute mean increase */
		end--;// java
		final int cn = C * nbEBands;// java
		int c = 0;
		do {
			for( int i = 2 >= start ? 2 : start; i < end; i++ )
			{
				float x1 = newE[i + c];
				x1 = 0 >= x1 ? 0 : x1;
				final float x2 = spread_old[i];
				x1 -= 0 >= x2 ? 0 : x2;
				mean_diff += 0 >= x1 ? 0 : x1;
			}
			c += nbEBands;
		} while ( c < cn );
		mean_diff /= (float)(C * (end - (2 > start ? 2 : start)));
		/*printf( "%f %f %d\n", mean_diff, max_diff, count ); */
		return mean_diff > 1.f;
	}

	/**
	 *
	 * @param pcmp
	 * @param pcmoffset
	 * @param inp
	 * @param inoffset
	 * @param N
	 * @param CC
	 * @param upsample
	 * @param coef
	 * @param mem
	 * @param moffset
	 * @param clip
	 */
	public static final void celt_preemphasis(final float[] pcmp, final int pcmoffset,// java
			final float[] inp, final int inoffset,// java
			final int N, final int CC, final int upsample, final float[] coef,
			final float[] mem, final int moffset,// java
			final boolean clip )
	{
		final float coef0 = coef[0];
		float m = mem[moffset];

		/* Fast path for the normal 48kHz case and no clipping */
		if( coef[1] == 0 && upsample == 1 && ! clip )
		{
			for( int i = inoffset, ie = inoffset + N, pi = pcmoffset; i < ie; i++, pi += CC )
			{
				final float x = pcmp[pi] * Jfloat_cast.CELT_SIG_SCALE;
				/* Apply pre - emphasis */
				inp[i] = x - m;
				m = coef0 * x;
			}
			mem[moffset] = m;
			return;
		}

		final int Nu = N / upsample;
		if( upsample != 1 )
		{
			// OPUS_CLEAR( inp, N );
			for( int i = inoffset, ie = N + inoffset; i < ie; i++ ) {
				inp[i] = 0f;
			}
		}
		for( int i = inoffset, ie = Nu * upsample + inoffset, pi = pcmoffset; i < ie; i += upsample, pi += CC ) {
			inp[i] = pcmp[pi] * Jfloat_cast.CELT_SIG_SCALE;
		}

// #ifndef FIXED_POINT
		if( clip )
		{
			/* Clip input to avoid encoding non - portable files */
			for( int i = inoffset, ie = Nu * upsample + inoffset; i < ie; i += upsample ) {
				float v = inp[i];// java
				v = 65536.f <= v ? 65536.f : v;
				inp[i] = -65536.f >= v ? -65536.f : v;
			}
		}
/* #else
		(void)clip;  // Avoids a warning about clip being unused.
#endif */
/* #ifdef CUSTOM_MODES
		if( coef[1] != 0 )
		{
			opus_val16 coef1 = coef[1];
			opus_val16 coef2 = coef[2];
			for( i = 0; i < N; i++ )
			{
				celt_sig x, tmp;
				x = inp[i];
				// Apply pre - emphasis
				tmp = MULT16_16( coef2, x );
				inp[i] = tmp + m;
				m = MULT16_32_Q15( coef1, inp[i] ) - MULT16_32_Q15( coef0, tmp );
			}
		} else
#endif */
		{
			for( int i = inoffset, ie = N + inoffset; i < ie; i++ )
			{
				final float x = inp[i];
				/* Apply pre - emphasis */
				inp[i] = x - m;
				m = coef0 * x;
			}
		}
		mem[moffset] = m;
	}

	private static final void tf_encode(final int start, final int end, final boolean isTransient,
			final int[] tf_res, final int LM, int tf_select, final Jec_enc enc)
	{
		int budget = enc.storage << 3;
		int tell = enc.ec_tell();
		int logp = isTransient ? 2 : 4;
		/* Reserve space to code the tf_select decision. */
		final boolean tf_select_rsv = LM > 0 && tell + logp + 1 <= budget;
		budget -= tf_select_rsv ? 1 : 0;
		int curr = 0, tf_changed = 0;
		final byte[] table_lm = tf_select_table[LM];// java
		for( int i = start; i < end; i++ )
		{
			if( tell + logp <= budget )
			{
				enc.ec_enc_bit_logp( (tf_res[i] ^ curr) != 0, logp );
				tell = enc.ec_tell();
				curr = tf_res[i];
				tf_changed |= curr;
			} else {
				tf_res[i] = curr;
			}
			logp = isTransient ? 4 : 5;
		}
		/* Only code tf_select if it would actually make a difference. */
		int v = tf_changed;// java
		if( isTransient ) {
			v += 4;
		}
		if( tf_select_rsv && table_lm[v] != table_lm[v + 2] ) {
			enc.ec_enc_bit_logp( tf_select != 0, 1 );
		} else {
			tf_select = 0;
		}
		tf_select <<= 1;// java
		for( int i = start; i < end; i++ ) {
			v = tf_select;// java
			if( isTransient ) {
				v += 4;
			}
			tf_res[i] = (int)table_lm[v + tf_res[i]];
		}
		/*for( i = 0; i < end; i++ )printf( "%d ", isTransient ? tf_res[i] : LM + tf_res[i] ); printf( "\n" ); */
	}

	// #define MSWAP( a, b ) do {opus_val16 tmp = a; a = b; b = tmp; } while( 0 )
	private static final float median_of_5(final float[] x, final int xoffset)// java
	{
		float t0 = x[xoffset];
		float t1 = x[xoffset + 1];
		if( t0 > t1 ) {
			final float t2 = t0;
			t0 = t1;
			t1 = t2;
		}
		float t3 = x[xoffset + 3];
		float t4 = x[xoffset + 4];
		if( t3 > t4 )
		{
			final float t2 = t3;
			t3 = t4;
			t4 = t2;
		}
		if( t0 > t3 )
		{
			float t2 = t0; t0 = t3; t3 = t2;// MSWAP( t0, t3 );
			t2 = t1; t1 = t4; t4 = t2;// MSWAP( t1, t4 );
		}
		final float t2 = x[xoffset + 2];
		if( t2 > t1 )
		{
			if( t1 < t3 ) {
				return (t2 <= t3 ? t2 : t3);
			}// else {
				return (t4 <= t1 ? t4 : t1);
			//}
		}// else {
			if( t2 < t3 ) {
				return (t1 <= t3 ? t1 : t3);
			}// else {
				return (t2 <= t4 ? t2 : t4);
			//}
		//}
	}

	private static final float median_of_3(final float[] x, final int xoffset)// java
	{
		float t0 = x[xoffset + 0];
		float t1 = x[xoffset + 1];
		if( t0 > t1 )
		{
			final float t2 = t0;
			t0 = t1;
			t1 = t2;
		}
		final float t2 = x[xoffset + 2];
		if( t1 < t2 ) {
			return t1;
		} else if( t0 < t2 ) {
			return t2;
		}// else {
			return t0;
		//}
	}

	private static final float dynalloc_analysis(final float[] bandLogE, final float[] bandLogE2,
			final int nbEBands, final int start, final int end, final int C, final int[] offsets, final int lsb_depth, final short[] logN,
			final boolean isTransient, final boolean vbr, final boolean constrained_vbr, final short[] eBands, final int LM,
			int effectiveBytes, final int[] tot_boost_, final boolean lfe, final float[] surround_dynalloc,
			final JAnalysisInfo analysis, final int[] importance, final int[] spread_weight)
	{
		int tot_boost = 0;
		// SAVE_STACK;
		int c = C * nbEBands;// java
		final float[] follower = new float[c];
		final float[] noise_floor = new float[c];
		// OPUS_CLEAR( offsets, nbEBands );
		for( int i = 0; i < nbEBands; i++ ) {
			offsets[i] = 0;
		}
		/* Dynamic allocation code */
		float maxDepth = -31.9f;
		for( int i = 0; i < end; i++ )
		{
			/* Noise floor must take into account eMeans, the depth, the width of the bands
			  and the preemphasis filter ( approx. square of bark band ID ) */
			int i5 = i + 5;// java
			i5 *= i5;
			noise_floor[i] = 0.0625f * (float)logN[i]
						+ .5f + (float)(9 - lsb_depth) - JCELTMode.eMeans[i]
						+ .0062f * (float)i5;
		}
		c = 0;
		do
		{
			final int cb = c * nbEBands;// java
			for( int i = 0; i < end; i++ ) {
				final float v = bandLogE[cb + i] - noise_floor[i];// java
				maxDepth = maxDepth >= v ? maxDepth : v;
			}
		} while( ++c < C );
		{
			/* Compute a really simple masking model to avoid taking into account completely masked
			   bands when computing the spreading decision. */
			final float mask[] = new float[ nbEBands ];
			final float sig[] = new float[ nbEBands ];
			for( int i = 0; i < end; i++ ) {
				mask[i] = bandLogE[i] - noise_floor[i];
			}
			if( C == 2 )
			{
				for( int i = 0; i < end; i++ ) {
					final float v = bandLogE[nbEBands + i] - noise_floor[i];// java
					mask[i] = (mask[i] >= v ? mask[i] : v);
				}
			}
			System.arraycopy( mask, 0, sig, 0, end );
			for( int i = 1; i < end; i++ ) {
				final float v = mask[i - 1] - 2.f;// java
				mask[i] = (mask[i] >= v ? mask[i] : v);
			}
			for( int i = end - 2; i >= 0; i-- ) {
				final float v = mask[i + 1] - 3.f;// java
				mask[i] = (mask[i] >= v ? mask[i] : v);
			}
			for( int i = 0; i < end; i++ )
			{
				/* Compute SMR: Mask is never more than 72 dB below the peak and never below the noise floor.*/
				float smr = maxDepth - 12.f;
				if( smr < 0 ) {
					smr = 0;
				}
				final float v = mask[ i ];
				if( smr < v ) {
					smr = v;
				}
				smr = sig[ i ] - smr;
				/* Clamp SMR to make sure we're not shifting by something negative or too large. */
// #ifdef FIXED_POINT
				/* FIXME: Use PSHR16() instead */
//				int shift = -PSHR32(MAX16(-QCONST16(5.f, DB_SHIFT), MIN16(0, smr)), DB_SHIFT);
// #else
				int shift = -(int)Math.floor( .5f + smr );
				if( shift < 0 ) {
					shift = 0;
				}
				if( shift > 5 ) {
					shift = 5;
				}
// #endif
				spread_weight[i] = 32 >> shift;
			}
			/*for (i=0;i<end;i++)
				printf("%d ", spread_weight[i]);
			printf("\n");*/
		}
		/* Make sure that dynamic allocation can't make us bust the budget */
		if( effectiveBytes > 50 && LM >= 1 && ! lfe )
		{
			effectiveBytes = (effectiveBytes << 1) / 3;// java
			int last = 0;
			c = 0;
			do
			{
				final int f = c * nbEBands;// follower[f]
				follower[f] = bandLogE2[f];// [c * nbEBands];
				for( int i = 1, fi = f + 1; i < end; i++, fi++ )
				{
					/* The last band to be at least 3 dB higher than the previous one
					   is the last we'll consider. Otherwise, we run into problems on
					   bandlimited signals. */
					final float v1 = bandLogE2[fi];// java
					if( v1 > bandLogE2[fi - 1] + .5f ) {
						last = i;
					}
					final float v2 = follower[fi - 1] + 1.5f;// java
					follower[fi] = v2 <= v1 ? v2 : v1;
				}
				for( int i = last - 1, fi = f + i; i >= 0; i--, fi-- ) {
					float v1 = follower[fi + 1] + 2.f;// java
					float v2 = bandLogE2[fi];// java
					v2 = v1 <= v2 ? v1 : v2;
					v1 = follower[fi];
					follower[fi] = v1 <= v2 ? v1 : v2;
				}

				/* Combine with a median filter to avoid dynalloc triggering unnecessarily.
				   The "offset" value controls how conservative we are--  a higher offset
				   reduces the impact of the median filter and makes dynalloc use more bits. */
				final float offset = ( 1.f );
				for( int i = 2, fi = f + i, ie = end - 2; i < ie; i++, fi++ ) {
					final float v1 = follower[fi];// java
					final float v2 = median_of_5( bandLogE2, fi - 2 ) - offset;// java
					follower[fi] = v1 >= v2 ? v1 : v2;
				}
				float tmp = median_of_3( bandLogE2, f ) - offset;
				float v = follower[f];// java
				follower[f] = v >= tmp ? v : tmp;
				v = follower[f + 1];// java
				follower[f + 1] = v >= tmp ? v : tmp;
				tmp = median_of_3( bandLogE2, f + end - 3 ) - offset;
				v = follower[f + end - 2];// java
				follower[f + end - 2] = v >= tmp ? v : tmp;
				v = follower[f + end - 1];// java
				follower[f + end - 1] = v >= tmp ? v : tmp;

				for( int i = 0, fi = f; i < end; i++, fi++ ) {
					v = follower[fi];// java
					tmp = noise_floor[i];// java
					follower[fi] = v >= tmp ? v : tmp;
				}
			} while ( ++c < C );
			if( C == 2 )
			{
				for( int i = start; i < end; i++ )
				{
					/* Consider 24 dB "cross - talk" */
					float v1 = follower[nbEBands + i];// java
					float v2 = follower[i];// java
					float v3 = v2 - 4.f;// java
					v1 = v1 >= v3 ? v1 : v3;
					follower[nbEBands + i] = v1;
					v3 = v1 - 4.f;
					v2 = v2 >= v3 ? v2 : v3;
					v2 = bandLogE[i] - v2;
					v1 = bandLogE[nbEBands + i] - v1;
					follower[i] = .5f * ((0 >= v2 ? 0 : v2) + (0 >= v1 ? 0 : v1));
				}
			} else {
				for( int i = start; i < end; i++ )
				{
					final float v = bandLogE[i] - follower[i];// java
					follower[i] = 0 >= v ? 0 : v;
				}
			}
			for( int i = start; i < end; i++ ) {
				final float v1 = follower[i];// java
				final float v2 = surround_dynalloc[i];// java
				follower[i] = v1 >= v2 ? v1 : v2;
			}
			for( int i = start; i < end; i++ )
			{
// #ifdef FIXED_POINT
//				importance[i] = PSHR32(13*celt_exp2(MIN16(follower[i], QCONST16(4.f, DB_SHIFT))), 16);
// #else
				float v = follower[i];// java
				if( v > 4.f ) {
					v = 4.f;
				}
				importance[ i ] = (int)Math.floor( .5f + 13f * ((float)Math.exp( 0.6931471805599453094 * v )) );
// #endif
			}
			/* For non-transient CBR/CVBR frames, halve the dynalloc contribution */
			if( ( ! vbr || constrained_vbr ) && ! isTransient )
			{
				for( int i = start; i < end; i++ ) {
					follower[i] *= .5f;
				}
			}
			for( int i = start; i < end; i++ )
			{
				float v = follower[i];// java
				if( i < 8 ) {
					v *= 2f;
				}
				if( i >= 12 ) {
					v *= .5f;
				}
				follower[i] = v;
			}
/* #ifdef DISABLE_FLOAT_API
			(void)analysis;
#else */
			if( analysis.valid )
			{
				final char[] leak_boost = analysis.leak_boost;// java
				for( int i = start, ie = (Jcelt.LEAK_BANDS < end ? Jcelt.LEAK_BANDS : end); i < ie; i++ ) {
					follower[i] += (1.f / 64.f) * leak_boost[i];
				}
			}
// #endif
			for( int i = start; i < end; i++ )
			{
				int boost;
				int boost_bits;
				float v = follower[i];// java
				v = v < 4f ? v : 4f;
				follower[i] = v;

				final int width = C * (eBands[i + 1] - eBands[i]) << LM;
				if( width < 6 )
				{
					boost = (int)v;// follower[i];
					boost_bits = boost * width << Jec_ctx.BITRES;
				} else if( width > 48 ) {
					boost = (int)(v * 8f);// (follower[i] * 8f);
					boost_bits = (boost * width << Jec_ctx.BITRES) >>> 3;
				} else {
					boost = (int)(v * width / 6f);// (follower[i] * width / 6f);
					boost_bits = boost * 6 << Jec_ctx.BITRES;
				}
				/* For CBR and non - transient CVBR frames, limit dynalloc to 2/3 of the bits */
				if( ( ! vbr || (constrained_vbr && ! isTransient) )
						&& (tot_boost + boost_bits) >> Jec_ctx.BITRES >> 3 > effectiveBytes )// java effectiveBytes = 2 * effectiveBytes / 3
				{
					final int cap = (effectiveBytes << Jec_ctx.BITRES << 3);
					offsets[i] = cap - tot_boost;
					tot_boost = cap;
					break;
				} else {
					offsets[i] = boost;
					tot_boost += boost_bits;
				}
			}
		} else {
			for( int i = start; i < end; i++ ) {
				importance[i] = 13;
			}
		}
		tot_boost_[0] = tot_boost;
		// RESTORE_STACK;
		return maxDepth;
	}

	// start pitch.c
/* #ifdef FIXED_POINT
	static opus_val16 compute_pitch_gain(opus_val32 xy, opus_val32 xx, opus_val32 yy)
	{
		opus_val32 x2y2;
		int sx, sy, shift;
		opus_val32 g;
		opus_val16 den;
		if( xy == 0 || xx == 0 || yy == 0 )
			return 0;
		sx = celt_ilog2( xx ) - 14;
		sy = celt_ilog2( yy )-14;
		shift = sx + sy;
		x2y2 = SHR32( MULT16_16( VSHR32( xx, sx ), VSHR32( yy, sy ) ), 14 );
		if( shift & 1 ) {
			if( x2y2 < 32768 )
			{
				x2y2 <<= 1;
				shift--;
			} else {
				x2y2 >>= 1;
				shift++;
			}
		}
		den = celt_rsqrt_norm( x2y2 );
		g = MULT16_32_Q15( den, xy );
		g = VSHR32( g, ( shift >> 1 ) - 1 );
		return EXTRACT16( MIN32( g, Q15ONE ) );
	}
#else
	static float compute_pitch_gain(float xy, float xx, float yy)// java: extracted in place
	{
		return xy / (float)Math.sqrt( (double)(1f + xx * yy) );
	}
// #endif */

	private static final int second_check[/* 16 */] = { 0, 0, 3, 2, 3, 2, 5, 2, 3, 2, 3, 2, 5, 2, 3, 2 };

	private static final float remove_doubling(final float[] x, int maxperiod, int minperiod,
			int N, final int[] T0_, int prev_period, final float prev_gain)// , final int arch)
	{
		final float[] xyret = new float[2];// java to get data back

		// SAVE_STACK;

		final int minperiod0 = minperiod;
		maxperiod >>= 1;
		minperiod >>= 1;
		int T0 = T0_[0];// java
		T0 >>= 1;
		prev_period >>= 1;
		N /= 2;
		final int xoffset = maxperiod;// java added. x[ xoffset ]
		// x += maxperiod;
		if( T0 >= maxperiod ) {
			T0 = maxperiod - 1;
		}

		int T;// , T0;// java T0 using as *T0_
		T = T0;// = t0;
		final float[] yy_lookup = new float[maxperiod + 1];
		dual_inner_prod( x, xoffset, x, xoffset, x, xoffset - T0, N, xyret );// xx, xy );//, arch );
		final float xx = xyret[0];
		float xy = xyret[1];
		yy_lookup[0] = xx;
		float yy = xx;
		for( int i = 1; i <= maxperiod; i++ )
		{
			final int k = xoffset - i;// java
			final float v1 = x[k];// java
			final float v2 = x[k + N];// java
			yy += v1 * v1 - v2 * v2;
			yy_lookup[i] = 0f >= yy ? 0f : yy;
		}
		yy = yy_lookup[T0];
		float best_xy = xy;
		float best_yy = yy;
		float g, g0;
		g = g0 = xy / (float)Math.sqrt( (double)(1f + xx * yy) );// java compute_pitch_gain( xy, xx, yy );
		final int T0_2 = T0 << 1;// java
		/* Look for any pitch at T/k */
		for( int k = 2; k <= 15; k++ )
		{
			int T1b;
			float cont = 0;
			final int T1 = (T0_2 + k) / (k << 1);
			if( T1 < minperiod ) {
				break;
			}
			/* Look for another strong correlation at T1b */
			if( k == 2 )
			{
				if( T1 + T0 > maxperiod ) {
					T1b = T0;
				} else {
					T1b = T0 + T1;
				}
			} else
			{
				T1b = (second_check[k] * T0_2 + k ) / (k << 1);
			}
			dual_inner_prod( x, xoffset, x, xoffset - T1, x, xoffset - T1b, N, xyret );// xy, xy2 );//, arch );
			xy = xyret[0];
			final float xy2 = xyret[1];
			xy = 0.5f * (xy + xy2);
			yy = 0.5f * (yy_lookup[T1] + yy_lookup[T1b]);
			final float g1 = xy / (float)Math.sqrt( (double)(1f + xx * yy) );// java compute_pitch_gain( xy, xx, yy );
			int d = T1 - prev_period;// java
			if( d < 0 ) {
				d = -d;
			}
			if( d <= 1 ) {
				cont = prev_gain;
			} else if( d <= 2 && 5 * k * k < T0 ) {
				cont = .5f * prev_gain;
			} else {
				cont = 0;
			}
			float v = (.7f * g0) - cont;// java
			float thresh = .3f >= v ? .3f : v;
			/* Bias against very high pitch (very short period) to avoid false-positives
			due to short-term correlation */
			if( T1 < 3 * minperiod ) {
				v = (.85f * g0) - cont;// java
				thresh = .4f >= v ? .4f : v;
			} else if( T1 < (minperiod << 1) ) {
				v = (.9f * g0) - cont;// java
				thresh = .5f >= v ? .5f : v;
			}
			if( g1 > thresh )
			{
				best_xy = xy;
				best_yy = yy;
				T = T1;
				g = g1;
			}
		}
		best_xy = 0 >= best_xy ? 0 : best_xy;
		float pg = ( best_yy <= best_xy ) ? Jfloat_cast.Q15ONE : (best_xy / (best_yy + 1));

		final float xcorr[] = new float[3];
		for( int k = 0; k < 3; k++ ) {
			xcorr[k] = celt_inner_prod( x, xoffset, x, xoffset - (T + k - 1), N );//, arch );
		}
		int offset;
		if( (xcorr[2] - xcorr[0]) > .7f * (xcorr[1] - xcorr[0]) ) {
			offset = 1;
		} else if( (xcorr[0] - xcorr[2]) > .7f * (xcorr[1] - xcorr[2]) ) {
			offset = -1;
		} else {
			offset = 0;
		}
		if( pg > g ) {
			pg = g;
		}
		T0 = (T << 1) + offset;

		if( T0 < minperiod0 ) {
			T0 = minperiod0;
		}
		T0_[0] = T0;// java
		// RESTORE_STACK;
		return pg;
	}
	// end pitch.c
	/**
	 * Auxilary structure to exchange data on java
	 */
	private static final class Jprefilter_aux {
		private float gain;
		private int pitch;
		private int qgain;
	}
	private final boolean run_prefilter(final float[] in,
				final float[] prefilter_mem_ptr,
				final int CC, final int N,
				final int prefilter_tap_set,
				// int[] pitch, final float[] gain, int[] qgain,// java replaced with aux
				final Jprefilter_aux aux,// java
				final boolean enabled, final int nbAvailableBytes, final JAnalysisInfo analysis)
	{
		final int pre[] = new int[2];// _pre[pre[i]]
		// SAVE_STACK;

		final JCELTMode celt_mode = (JCELTMode)this.mode;
		final int overlap = celt_mode.overlap;
		final float[] _pre = new float[CC * (N + Jcelt.COMBFILTER_MAXPERIOD)];

		pre[0] = 0;
		pre[1] = N + Jcelt.COMBFILTER_MAXPERIOD;


		int c = 0;
		do {
			System.arraycopy( prefilter_mem_ptr, c * Jcelt.COMBFILTER_MAXPERIOD, _pre, pre[c], Jcelt.COMBFILTER_MAXPERIOD );
			System.arraycopy( in,  + c * (N + overlap) + overlap, _pre, pre[c] + Jcelt.COMBFILTER_MAXPERIOD, N );
		} while ( ++c < CC );

		int pitch_index;
		float gain1;
		if( enabled )
		{
			final float[] pitch_buf = new float[(Jcelt.COMBFILTER_MAXPERIOD + N) >> 1];

			pitch_downsample( _pre, pre, pitch_buf, Jcelt.COMBFILTER_MAXPERIOD + N, CC );//, st.arch );
			/* Don't search for the fir last 1.5 octave of the range because
			   there's too many false - positives due to short - term correlation */
			pitch_index = pitch_search( pitch_buf, (Jcelt.COMBFILTER_MAXPERIOD >> 1), pitch_buf, N,
					Jcelt.COMBFILTER_MAXPERIOD - 3 * Jcelt.COMBFILTER_MINPERIOD );//, &pitch_index, st.arch );
			pitch_index = Jcelt.COMBFILTER_MAXPERIOD - pitch_index;

			final int[] data = { pitch_index };// java TODO perhaps there is a better way?
			gain1 = remove_doubling( pitch_buf, Jcelt.COMBFILTER_MAXPERIOD, Jcelt.COMBFILTER_MINPERIOD,
						N, data/*&pitch_index*/, this.prefilter_period, this.prefilter_gain );//, st.arch );
			pitch_index = data[0];// java
			if( pitch_index > Jcelt.COMBFILTER_MAXPERIOD - 2 ) {
				pitch_index = Jcelt.COMBFILTER_MAXPERIOD - 2;
			}
			gain1 = .7f * gain1;
			/*printf( "%d %d %f %f\n", pitch_change, pitch_index, gain1, st.analysis.tonality ); */
			if( this.loss_rate > 2 ) {
				gain1 = .5f * gain1;
			}
			if( this.loss_rate > 4 ) {
				gain1 = .5f * gain1;
			}
			if( this.loss_rate > 8 ) {
				gain1 = 0;
			}
		} else {
			gain1 = 0;
			pitch_index = Jcelt.COMBFILTER_MINPERIOD;
		}
// #ifndef DISABLE_FLOAT_API
		if( analysis.valid ) {
			gain1 *= analysis.max_pitch_ratio;
		}
// #else
//		(void)analysis;
//#endif
		/* Gain threshold for enabling the prefilter/postfilter */
		float pf_threshold = .2f;

		/* Adjusting the threshold based on rate and continuity */
		c = pitch_index - this.prefilter_period;// java
		if( c < 0 ) {
			c = -c;
		}
		if( c * 10 > pitch_index ) {
			pf_threshold += .2f;
		}
		if( nbAvailableBytes < 25 ) {
			pf_threshold += .1f;
		}
		if( nbAvailableBytes < 35 ) {
			pf_threshold += .1f;
		}
		if( this.prefilter_gain > .4f ) {
			pf_threshold -= .1f;
		}
		if( this.prefilter_gain > .55f ) {
			pf_threshold -= .1f;
		}

		/* Hard threshold at 0.2 */
		int qg;
		boolean pf_on;
		pf_threshold = pf_threshold > .2f ? pf_threshold : .2f;
		if( gain1 < pf_threshold )
		{
			gain1 = 0;
			pf_on = false;
			qg = 0;
		} else {
			/*This block is not gated by a total bits check only because
			   of the nbAvailableBytes check above.*/
			float v = gain1 - this.prefilter_gain;// java
			if( v < 0 ) {
				v = -v;
			}
			if( v < .1f ) {
				gain1 = this.prefilter_gain;
			}

/* #ifdef FIXED_POINT
			qg = ( ( gain1 + 1536 ) >> 10 ) / 3 - 1;
#else */
			qg = (int)Math.floor( (double)(.5f + gain1 * 32f / 3f) ) - 1;
// #endif
			qg = 7 < qg ? 7 : qg;
			qg = 0 > qg ? 0 : qg;
			gain1 = 0.09375f * (float)(qg + 1);
			pf_on = true;
		}
		/*printf( "%d %f\n", pitch_index, gain1 ); */

		c = 0;
		do {
			final int offset = celt_mode.shortMdctSize - overlap;
			this.prefilter_period = this.prefilter_period > Jcelt.COMBFILTER_MINPERIOD ? this.prefilter_period : Jcelt.COMBFILTER_MINPERIOD;
			final int co = c * overlap;// java
			final int cno = c * (N + overlap);// java
			System.arraycopy( this.in_mem, co, in, cno, overlap );
			if( 0 != offset ) {
				Jcelt.comb_filter( in, cno + overlap, _pre, pre[c] + Jcelt.COMBFILTER_MAXPERIOD,
						this.prefilter_period, this.prefilter_period, offset, -this.prefilter_gain, -this.prefilter_gain,
						this.prefilter_tapset, this.prefilter_tapset, null, 0 );//, st.arch );
			}

			Jcelt.comb_filter( in, cno + overlap + offset, _pre, pre[c] + Jcelt.COMBFILTER_MAXPERIOD + offset,
						this.prefilter_period, pitch_index, N - offset,  - this.prefilter_gain, -gain1,
						this.prefilter_tapset, prefilter_tap_set, celt_mode.window, overlap );//, st.arch );
			System.arraycopy( in, cno + N, this.in_mem, co, overlap );

			if( N > Jcelt.COMBFILTER_MAXPERIOD )
			{
				System.arraycopy( _pre, pre[c] + N, prefilter_mem_ptr, c * Jcelt.COMBFILTER_MAXPERIOD, Jcelt.COMBFILTER_MAXPERIOD );
			} else {
				final int mem_offset = c * Jcelt.COMBFILTER_MAXPERIOD;// java
				System.arraycopy( prefilter_mem_ptr, mem_offset + N, prefilter_mem_ptr, mem_offset, Jcelt.COMBFILTER_MAXPERIOD - N );
				System.arraycopy( _pre, pre[c] + Jcelt.COMBFILTER_MAXPERIOD, prefilter_mem_ptr,  mem_offset + Jcelt.COMBFILTER_MAXPERIOD - N, N );
			}
		} while( ++c < CC );

		// RESTORE_STACK;
		aux.gain = gain1;
		aux.pitch = pitch_index;
		aux.qgain = qg;
		return pf_on;
	}

	private static int compute_vbr(final JCELTMode mode, final JAnalysisInfo analysis, final int base_target,
				final int LM, final int bitrate, final int lastCodedBands, final int C, final int intensity,
				final boolean constrained_vbr, float stereo_saving, final int tot_boost,
				final float tf_estimate, final boolean pitch_change, final float maxDepth,
				final boolean lfe, final boolean has_surround_mask, final float surround_masking,
				final float temporal_vbr )
	{
		final int nbEBands = mode.nbEBands;
		final short[] eBands = mode.eBands;

		final int coded_bands = lastCodedBands != 0 ? lastCodedBands : nbEBands;
		int coded_bins = eBands[coded_bands] << LM;
		if( C == 2 ) {
			coded_bins += eBands[intensity <= coded_bands ? intensity : coded_bands] << LM;
		}
		final float f_coded_bins = (float)(coded_bins << Jec_ctx.BITRES);// java
		/* The target rate in 8th bits per frame */
		int target = base_target;

		/*printf( "%f %f %f %f %d %d ", st.analysis.activity, st.analysis.tonality, tf_estimate, st.stereo_saving, tot_boost, coded_bands ); */
// #ifndef DISABLE_FLOAT_API
		if( analysis.valid && analysis.activity < .4f ) {// FIXME using double
			target -= (int)(f_coded_bins * (.4f - analysis.activity));
		}
// #endif
		/* Stereo savings */
		if( C == 2 )
		{
			final int coded_stereo_bands = intensity <= coded_bands ? intensity : coded_bands;
			int coded_stereo_dof = (eBands[coded_stereo_bands] << LM) - coded_stereo_bands;
			/* Maximum fraction of the bits we can save if the signal is mono. */
			float max_frac = 0.8f * (float)coded_stereo_dof / (float)coded_bins;
			stereo_saving = stereo_saving <= 1.f ? stereo_saving : 1.f;
			/*printf( "%d %d %d ", coded_stereo_dof, coded_bins, tot_boost ); */
			coded_stereo_dof <<= Jec_ctx.BITRES;
			max_frac *= (float)target;
			final float v = (stereo_saving - 0.1f) * (float)coded_stereo_dof;// java
			target -= (int)(max_frac <= v ? max_frac : v);
		}
		/* Boost the rate according to dynalloc ( minus the dynalloc average for calibration ). */
		target += tot_boost - (19 << LM);
		/* Apply transient boost, compensating for average boost. */
		final float tf_calibration = 0.044f;
		target += (int)((tf_estimate - tf_calibration) * (float)target);

// #ifndef DISABLE_FLOAT_API
		/* Apply tonality boost */
		if( analysis.valid && ! lfe )
		{
			/* Tonality boost ( compensating for the average ). */
			float tonal = analysis.tonality - .15f;// java
			tonal = (0.f >= tonal ? 0.f : tonal) - 0.12f;
			int tonal_target = target + (int)(f_coded_bins * 1.2f * tonal);
			if( pitch_change ) {
				tonal_target += (int)(f_coded_bins * .8f);
			}
			/*printf( "%f %f ", analysis.tonality, tonal ); */
			target = tonal_target;
		}
/* #else
		( void )analysis;
		( void )pitch_change;
#endif */

		if( has_surround_mask && ! lfe )
		{
			final int surround_target = target + (int)(surround_masking * f_coded_bins);
			/*printf( "%f %d %d %d %d %d %d ", surround_masking, coded_bins, st.end, st.intensity, surround_target, target, st.bitrate ); */
			target >>>= 2;
			target = target >= surround_target ? target : surround_target;
		}

		{
			final int bins = eBands[nbEBands - 2] << LM;
			/*floor_depth = SHR32( MULT16_16( ( C * bins << BITRES ), celt_log2( SHL32( MAX16( 1, sample_max ), 13 ) ) ), DB_SHIFT ); */
			int floor_depth = (int)((float)(C * bins << Jec_ctx.BITRES) * maxDepth);
			final int v = target >> 2;// java
			floor_depth = floor_depth >= v ? floor_depth : v;
			target = target <= floor_depth ? target : floor_depth;
			/*printf( "%f %d\n", maxDepth, floor_depth ); */
		}

		/* Make VBR less aggressive for constrained VBR because we can't keep a higher bitrate
		  for long. Needs tuning. */
		if( (! has_surround_mask || lfe) && constrained_vbr )
		{
			target = base_target + (int)(0.67f * (target - base_target));
		}

		if( ! has_surround_mask && tf_estimate < .2f )
		{
			int v = 96000 - bitrate;// java
			v = 32000 <= v ? 32000 : v;
			// final float amount = .0000031f * (0 > v ? 0f : (float)v);
			// final float tvbr_factor = temporal_vbr * amount;
			// target += (int)tvbr_factor * target;
			if( 0 < v ) {// java variant
				final float amount = .0000031f * (float)v;
				final float tvbr_factor = temporal_vbr * amount;
				target += (int)(tvbr_factor * (float)target);
			}
		}

		/* Don't allow more than doubling the rate */
		final int v = base_target << 1;// java
		target = v <= target ? v : target;

		return target;
	}

	// start bands.c
	private static final int hysteresis_decision(final float val, final float[] thresholds, final float[] hysteresis, final int N, final int prev)
	{
		int i;
		for( i = 0; i < N; i++ )
		{
			if( val < thresholds[i] ) {
				break;
			}
		}
		if( i > prev && val < thresholds[prev] + hysteresis[prev] ) {
			i = prev;
		}
		final int p1 = prev - 1;// java
		if( i < prev && val > thresholds[p1] - hysteresis[p1] ) {
			i = prev;
		}
		return i;
	}
	// end bands.c

	// start quant_bands.c
	private static final void quant_fine_energy(final int bands,// java replace CELTMode *m,// FIXME why need CELTMode?
			final int start, final int end,
			final float[] oldEBands,
			final float[] error, final int[] fine_quant, final Jec_enc enc, int C)
	{
		C *= bands;// java
		/* Encode finer resolution */
		for( int i = start; i < end; i++ )
		{
			// final int frac = (1 << fine_quant[i]);// FIXME why int16? why calculation before checking?
			if( fine_quant[i] <= 0 ) {
				continue;
			}
			final int frac = (1 << fine_quant[i]);// java moved
			final float f = (float)frac;// java

			int c = 0;
			do {
				final int cmb = i + c;// java
/* #ifdef FIXED_POINT
				// Has to be without rounding
				q2 = (error[i + c * m.nbEBands] + QCONST16(.5f, DB_SHIFT)) >> (DB_SHIFT - fine_quant[i]);
#else */
				int q2 = (int)Math.floor( (double)((error[cmb] + .5f) * f) );
//#endif
				if( q2 >= frac ) {// if( q2 > frac - 1 ) {
					q2 = frac - 1;
				}
				if( q2 < 0 ) {
					q2 = 0;
				}
				enc.ec_enc_bits( q2, fine_quant[i] );
/* #ifdef FIXED_POINT
				offset = SUB16(SHR32(SHL32(EXTEND32(q2), DB_SHIFT) + QCONST16(.5f, DB_SHIFT), fine_quant[i]), QCONST16(.5f, DB_SHIFT));
#else */
				final float offset = (q2 + .5f) * (1 << (14 - fine_quant[i])) * (1.f / 16384f) - .5f;
//#endif
				oldEBands[cmb] += offset;
				error[cmb] -= offset;
				/*printf ("%f ", error[i] - offset);*/
				c += bands;// java
			} while( c < C );
		}
	}
	private static final void quant_energy_finalise(final int bands,// java replace CELTMode *m. FIXME why need CELTMode?
			final int start, final int end,
			final float[] oldEBands,
			final float[] error, final int[] fine_quant, final int[] fine_priority, int bits_left,
			final Jec_enc enc, final int C)
	{
		/* Use up the remaining bits */
		for( int prio = 0; prio < 2; prio++ )
		{
			for( int i = start; i < end && bits_left >= C; i++ )
			{
				if( fine_quant[i] >= JCELTMode.MAX_FINE_BITS || fine_priority[i] != prio ) {// FIXME strange using boolean fine_priority
					continue;
				}
				int c = 0;
				do {
					final int cmb = i + c * bands;// java
					final int q2 = error[cmb] < 0 ? 0 : 1;
					enc.ec_enc_bits( q2, 1 );
/* #ifdef FIXED_POINT
					offset = SHR16(SHL16(q2, DB_SHIFT) - QCONST16(.5f, DB_SHIFT), fine_quant[i] + 1);
#else */
					final float offset = (q2 - .5f) * (1 << (14 - fine_quant[i] - 1)) * (1.f / 16384f);
// #endif
					oldEBands[cmb] += offset;
					error[cmb] -= offset;
					bits_left--;
				} while( ++c < C );
			}
		}
	}

	// end quant_bands.c

	private static final boolean stereo_analysis(final short[] bands, final float[] X, final int LM, final int N0)
	{// java bands replaced CELTMode *m. FIXME why need CELTMode?
		float sumLR = Jfloat_cast.EPSILON, sumMS = Jfloat_cast.EPSILON;

		/* Use the L1 norm to model the entropy of the L/R signal vs the M/S signal */
		// final short[] eb = this.eBands;// java moved up and renamed to bands
		for( int i = 0; i < 13; i++ )
		{
			for( int j = bands[i] << LM, je = bands[i + 1] << LM; j < je; j++ )
			{
				/* We cast to 32 - bit first because of the -32768 case */
				float L = X[j];
				float R = X[N0 + j];
				float M = L + R;
				float S = L - R;
				if( L < 0 ) {
					L = -L;
				}
				if( R < 0 ) {
					R = -R;
				}
				if( M < 0 ) {
					M = -M;
				}
				if( S < 0 ) {
					S = -S;
				}
				sumLR += L + R;
				sumMS += M + S;
			}
		}
		sumMS *= 0.707107f;
		int thetas = 13;
		/* We don't need thetas for lower bands with LM <= 1 */
		if( LM <= 1 ) {
			thetas -= 8;
		}
		final int v = bands[13] << (LM + 1);// java
		return ((v + thetas) * sumMS) > (v * sumLR);
	}

// #ifndef OVERRIDE_CELT_MAXABS16
	/**
	 *
	 * @param x buff
	 * @param xoffset offset
	 * @param len data size
	 * @return max
	 */
	public static final float celt_maxabs16(final float[] x, int xoffset, int len)
	{
		float maxval = 0f;
		float minval = 0f;
		for( len += xoffset; xoffset < len; xoffset++ )
		{
			final float v = x[xoffset];// java
			maxval = maxval >= v ? maxval : v;
			minval = minval <= v ? minval : v;
		}
		minval = -minval;// java
		return maxval >= minval ? maxval : minval;
	}
// #endif

	private static final float intensity_thresholds[/* 21 */] =
			/* 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19  20  off*/
			{  1, 2, 3, 4, 5, 6, 7, 8,16,24,36,44,50,56,62,67,72,79,88,106,134};
	private static final float intensity_histeresis[/* 21 */] =
			{  1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 4, 5, 6,  8,  8};

	/**
	 *
	 * @param pcm
	 * @param pcmoffset
	 * @param frame_size
	 * @param compressed
	 * @param coffset
	 * @param nbCompressedBytes
	 * @param enc
	 * @return nbCompressedBytes or an error code
	 */
	public final int celt_encode_with_ec(
			final float[] pcm, final int pcmoffset,// java
			int frame_size,
			final byte[] compressed, final int coffset,// java
			int nbCompressedBytes, Jec_enc enc)
	{// java unsigned char* compressed
		final Jec_enc _enc = new Jec_enc();
		final int CC = this.channels;
		final int C = this.stream_channels;
		int prefilter_tap_set = 0;
		int anti_collapse_rsv;
		boolean silence = false;

		float surround_masking = 0;
		float temporal_vbr = 0;
		float surround_trim = 0;
		// ALLOC_STACK;

		// final JOpusCustomMode mode = st.mode;
		final JCELTMode celt_mode = (JCELTMode)this.mode;// java renamed
		final int nbEBands = celt_mode.nbEBands;
		final int overlap = celt_mode.overlap;
		final short[] eBands = celt_mode.eBands;
		final int curr_start = this.start;
		final int curr_end = this.end;
		final boolean hybrid = curr_start != 0;

		if( nbCompressedBytes < 2 || pcm == null )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}

		frame_size *= this.upsample;
		int LM;
		for( LM = 0; LM <= celt_mode.maxLM; LM++ ) {
			if( celt_mode.shortMdctSize << LM == frame_size ) {
				break;
			}
		}
		if( LM > celt_mode.maxLM )
		{
			// RESTORE_STACK;
			return Jopus_defines.OPUS_BAD_ARG;
		}
		final int M = 1 << LM;
		final int N = M * celt_mode.shortMdctSize;

		int tell, tell0_frac, nbFilledBytes;
		if( enc == null )
		{
			tell0_frac = tell = 1;
			nbFilledBytes = 0;
		} else {
			tell0_frac = enc.ec_tell_frac();
			tell = enc.ec_tell();
			nbFilledBytes = (tell + 4) >> 3;
		}

/* #ifdef CUSTOM_MODES
		if( st.signalling && enc == NULL )
		{
			int tmp = ( mode.effEBands - end ) >> 1;
			end = st.end = IMAX( 1, mode.effEBands - tmp );
			compressed[0] = tmp << 5;
			compressed[0] |= LM << 3;
			compressed[0] |= ( C == 2 ) << 2;
			// Convert "standard mode" to Opus header
			if( mode.Fs == 48000 && mode.shortMdctSize == 120 )
			{
				int c0 = toOpus( compressed[0] );
				if( c0 < 0 )
				{
					RESTORE_STACK;
					return OPUS_BAD_ARG;
				}
				compressed[0] = c0;
			}
			compressed++;
			nbCompressedBytes--;
		}
#else */
		// celt_assert( st.signalling == 0 );
// #endif

		/* Can't produce more than 1275 output bytes */
		nbCompressedBytes = nbCompressedBytes < 1275 ? nbCompressedBytes : 1275;
		int nbAvailableBytes = nbCompressedBytes - nbFilledBytes;

		int vbr_rate;
		int effectiveBytes;
		if( this.vbr && this.bitrate != Jopus_defines.OPUS_BITRATE_MAX )
		{
			final int den = celt_mode.Fs >> Jec_ctx.BITRES;
			vbr_rate = ( this.bitrate * frame_size + ( den >> 1 ) ) / den;
/* #ifdef CUSTOM_MODES
			if( st.signalling )
				vbr_rate -= 8 << BITRES;
#endif */
			effectiveBytes = vbr_rate >> ( 3 + Jec_ctx.BITRES );
		} else {
			vbr_rate = 0;
			int tmp = this.bitrate * frame_size;
			if( tell > 1 ) {
				tmp += tell;
			}
			if( this.bitrate != Jopus_defines.OPUS_BITRATE_MAX ) {
				tmp = (tmp + (celt_mode.Fs << 2)) / (celt_mode.Fs << 3) - (this.signalling ? 1 : 0);// !! st.signalling;// java
				nbCompressedBytes = nbCompressedBytes < tmp ? nbCompressedBytes : tmp;
				nbCompressedBytes = 2 > nbCompressedBytes ? 2 : nbCompressedBytes;
			}
			effectiveBytes = nbCompressedBytes - nbFilledBytes;
		}
		int equiv_rate = (nbCompressedBytes * 8 * 50 >> (3 - LM)) - (40 * C + 20) * ((400 >> LM) - 50);
		if( this.bitrate != Jopus_defines.OPUS_BITRATE_MAX ) {
			final int v = this.bitrate - (40 * C + 20) * ((400 >> LM) - 50);// java
			equiv_rate = equiv_rate <= v ? equiv_rate : v;
		}

		if( enc == null )
		{
			_enc.ec_enc_init( compressed, coffset, nbCompressedBytes );
			enc = _enc;
		}

		if( vbr_rate > 0 )
		{
			/* Computes the max bit - rate allowed in VBR mode to avoid violating the
			   target rate and buffering.
			   We must do this up front so that bust - prevention logic triggers
			   correctly if we don't have enough bits. */
			if( this.constrained_vbr )
			{
				/* We could use any multiple of vbr_rate as bound (depending on the delay).
				   This is clamped to ensure we use at least two bytes if the encoder
				   was entirely empty, but to allow 0 in hybrid mode. */
				//final int vbr_bound = vbr_rate;// FIXME why need vbr_bound?
				int max_allowed = tell == 1 ? 2 : 0;// java
				final int v = (vbr_rate + vbr_rate - this.vbr_reservoir) >> (Jec_ctx.BITRES + 3);// java
				max_allowed = max_allowed >= v ? max_allowed : v;
				max_allowed = max_allowed <= nbAvailableBytes ? max_allowed : nbAvailableBytes;
				if( max_allowed < nbAvailableBytes )
				{
					nbCompressedBytes = nbFilledBytes + max_allowed;
					nbAvailableBytes = max_allowed;
					enc.ec_enc_shrink( nbCompressedBytes );
				}
			}
		}
		int total_bits = nbCompressedBytes << 3;

		int effEnd = curr_end;
		if( effEnd > celt_mode.effEBands ) {
			effEnd = celt_mode.effEBands;
		}

		float sample_max = celt_maxabs16( pcm, pcmoffset, C * (N - overlap) / this.upsample );
		sample_max = this.overlap_max > sample_max ? this.overlap_max : sample_max;
		this.overlap_max = celt_maxabs16( pcm, pcmoffset + C * (N - overlap) / this.upsample, C * overlap / this.upsample );
		sample_max = sample_max > this.overlap_max ? sample_max : this.overlap_max;
/* #ifdef FIXED_POINT
		silence = (sample_max == 0);
#else */
		silence = (sample_max <= 1f / (float)(1 << this.lsb_depth));
// #endif
/* #ifdef FUZZING
		if( ( rand() & 0x3F ) == 0 )
			silence = 1;
#endif */
		if( tell == 1 ) {
			enc.ec_enc_bit_logp( silence, 15 );
		} else {
			silence = false;
		}
		if( silence )
		{
			/*In VBR mode there is no need to send more than the minimum. */
			if( vbr_rate > 0 )
			{
				final int v = nbFilledBytes + 2;// java
				effectiveBytes = nbCompressedBytes = (nbCompressedBytes < v ? nbCompressedBytes : v);
				total_bits = nbCompressedBytes << 3;
				nbAvailableBytes = 2;
				enc.ec_enc_shrink( nbCompressedBytes );
			}
			/* Pretend we've filled all the remaining bits with zeros
			( that's what the initialiser did anyway ) */
			tell = nbCompressedBytes << 3;
			enc.nbits_total += tell - enc.ec_tell();
		}
		final float[] in = new float[CC * (N + overlap)];
		int c = 0;
		do {
			// int need_clip = 0;
// #ifndef FIXED_POINT
			final boolean need_clip = this.clip && sample_max > 65536.f;
// #endif
			celt_preemphasis( pcm, pcmoffset + c, in, c * (N + overlap) + overlap, N, CC, this.upsample,
						celt_mode.preemph, this.preemph_memE, c, need_clip );
		} while( ++c < CC );

		/* Find pitch period and gain */
		final float[] prefilt_mem = this.prefilter_mem;// CC * overlap;
		boolean pitch_change = false;
		int pitch_index = Jcelt.COMBFILTER_MINPERIOD;
		float gain1 = 0;
		final boolean pf_on;
		{
			//final int qg;
			final boolean enabled = ((this.lfe && nbAvailableBytes > 3) || nbAvailableBytes > 12 * C) && ! hybrid && ! silence && ! this.disable_pf
					&& this.complexity >= 5;

			prefilter_tap_set = this.tapset_decision;
			final Jprefilter_aux aux = new Jprefilter_aux();// java added to replace &pitch_index, &gain1, &qg
			pf_on = run_prefilter( in, prefilt_mem, CC, N, prefilter_tap_set,
					// &pitch_index, &gain1, &qg,
					aux,// java
					enabled, nbAvailableBytes, this.analysis );
			pitch_index = aux.pitch;// java
			gain1 = aux.gain;// java
			// qg = aux.qgain;// java
			if( ( gain1 > .4f || this.prefilter_gain > .4f ) && ( ! this.analysis.valid || this.analysis.tonality > .3f )
					&& ((float)pitch_index > 1.26f * (float)this.prefilter_period || (float)pitch_index < .79f * (float)this.prefilter_period ) ) {
				pitch_change = true;
			}
			if( ! pf_on )
			{
				if( ! hybrid && tell + 16 <= total_bits ) {
					enc.ec_enc_bit_logp( false, 1 );
				}
			} else {
				/*This block is not gated by a total bits check only because
				   of the nbAvailableBytes check above.*/
				enc.ec_enc_bit_logp( true, 1 );
				pitch_index += 1;
				final int octave = Jec_ctx.EC_ILOG( pitch_index ) - 5;
				enc.ec_enc_uint( octave, 6 );
				enc.ec_enc_bits( pitch_index - (16 << octave), 4 + octave );
				pitch_index -= 1;
				enc.ec_enc_bits( aux.qgain, 3 );
				enc.ec_enc_icdf( prefilter_tap_set, Jcelt.tapset_icdf, 0, 2 );
			}
		}

		int tf_chan = 0;
		float tf_estimate = 0;
		boolean isTransient = false;
		int shortBlocks = 0;
		boolean weak_transient = false;
		if( this.complexity >= 1 && ! this.lfe )
		{
			/* Reduces the likelihood of energy instability on fricatives at low bitrate
			  in hybrid mode. It seems like we still want to have real transients on vowels
			  though (small SILK quantization offset value). */
			final boolean allow_weak_transients = hybrid && effectiveBytes < 15 && this.silk_info.signalType != 2;
			final Janalysis_aux aux = new Janalysis_aux();// java
			isTransient = transient_analysis( in, N + overlap, CC, aux,/* &tf_estimate, &tf_chan, */allow_weak_transients/*, &weak_transient*/ );
			tf_estimate = aux.tf_estimate;// java
			tf_chan = aux.tf_chan;// java
			weak_transient = aux.weak_transient;// java
		}
		boolean transient_got_disabled = false;
		if( LM > 0 && enc.ec_tell() + 3 <= total_bits )
		{
			if( isTransient ) {
				shortBlocks = M;
			}
		} else {
			isTransient = false;
			transient_got_disabled = true;
		}

		final float[] freq = new float[CC * N];  /** <  Interleaved signal MDCTs */
		final int cc_nbEBands = nbEBands * CC;// java
		final float[] bandE = new float[ cc_nbEBands ];

		final boolean secondMdct = (shortBlocks != 0) && (this.complexity >= 8);
		final int c_nbEBands = C * nbEBands;// java
		final float[] bandLogE2 = new float[ c_nbEBands ];
		if( secondMdct )
		{
			celt_mode.compute_mdcts( 0, in, freq, C, CC, LM, this.upsample );//, st.arch );
			celt_mode.compute_band_energies( freq, bandE, effEnd, C, LM );//, st.arch );
			JCELTMode.amp2Log2( celt_mode.nbEBands /* celt_mode */, effEnd, curr_end, bandE, bandLogE2, 0, C );
			final float half_lm = .5f * (float)LM;// java
			for( int i = 0; i < c_nbEBands; i++ ) {
				bandLogE2[i] += half_lm;
			}
		}

		celt_mode.compute_mdcts( shortBlocks, in, freq, C, CC, LM, this.upsample );//, st.arch );
		/* This should catch any NaN in the CELT input. Since we're not supposed to see any (they're filtered
		  at the Opus layer), just abort. */
		// celt_assert(!celt_isnan(freq[0]) && (C==1 || !celt_isnan(freq[N])));
		if( CC == 2 && C == 1 ) {
			tf_chan = 0;
		}
		celt_mode.compute_band_energies( freq, bandE, effEnd, C, LM );//, st.arch );

		if( this.lfe )
		{
			final float v = 1e-4f * bandE[0];// java
			for( int i = 2; i < curr_end; i++ )
			{
				float bi = bandE[i];// java
				bi = bi <= v ? bi : v;
				bandE[i] = bi >= Jfloat_cast.EPSILON ? bi : Jfloat_cast.EPSILON;
			}
		}
		final float[] bandLogE = new float[ cc_nbEBands ];
		JCELTMode.amp2Log2( celt_mode.nbEBands /* celt_mode */, effEnd, curr_end, bandE, bandLogE, 0, C );

		final float[] surround_dynalloc = new float[ c_nbEBands ];
		// OPUS_CLEAR( surround_dynalloc, end );
		/* This computes how much masking takes place between surround channels */
		if( ! hybrid && null != this.energy_mask && ! this.lfe )
		{
			float mask_avg = 0f;
			float diff = 0f;
			int count = 0;
			final int mask_end = 2 > this.lastCodedBands ? 2 : this.lastCodedBands;
			for( c = 0; c < c_nbEBands; c += nbEBands )
			{
				for( int i = 0; i < mask_end; i++ )
				{
					float mask = this.energy_mask[ c + i ];
					mask = mask <= .25f ? mask : .25f;
					mask = mask >= -2.0f ? mask : -2.0f;
					if( mask > 0f ) {
						mask = .5f * mask;
					}
					final int v = eBands[i + 1] - eBands[i];// java
					mask_avg += mask * (float)v;
					count += v;
					diff += mask * (1 + (i << 1) - mask_end);
				}
			}
			// celt_assert( count > 0 );
			mask_avg /= (float)count;
			mask_avg += .2f;
			diff = diff * 6 / (C * (mask_end - 1) * (mask_end + 1) * mask_end);
			/* Again, being conservative */
			diff = .5f * diff;
			diff = diff <= .031f ? diff : .031f;
			diff = diff >= -.031f ? diff : -.031f;
			/* Find the band that's in the middle of the coded spectrum */
			int midband;
			for( midband = 0, c = eBands[mask_end] >> 1; eBands[midband + 1] < c; midband++ ) {
				;
			}
			int count_dynalloc = 0;
			for( int i = 0; i < mask_end; i++ )
			{
				float unmask;
				final float lin = mask_avg + diff * (i - midband);
				if( C == 2 ) {
					unmask = this.energy_mask[i];
					final float v = this.energy_mask[nbEBands + i];
					unmask = unmask >= v ? unmask : v;
				} else {
					unmask = this.energy_mask[i];
				}
				unmask = unmask <= .0f ? unmask : .0f;
				unmask -= lin;
				if( unmask > .25f )
				{
					surround_dynalloc[i] = unmask - .25f;
					count_dynalloc++;
				}
			}
			if( count_dynalloc >= 3 )
			{
				/* If we need dynalloc in many bands, it's probably because our
				   initial masking rate was too low. */
				mask_avg += .25f;
				if( mask_avg > 0 )
				{
					/* Something went really wrong in the original calculations,
					   disabling masking. */
					mask_avg = 0f;
					diff = 0f;
					//OPUS_CLEAR( surround_dynalloc, mask_end );
					for( int i = 0; i < mask_end; i++ ) {
						surround_dynalloc[i] = 0f;
					}
				} else {
					for( int i = 0; i < mask_end; i++ ) {
						final float v = surround_dynalloc[i] - .25f;// java
						surround_dynalloc[i] = 0 >= v ? 0 : v;
					}
				}
			}
			mask_avg += .2f;
			/* Convert to 1/64th units used for the trim */
			surround_trim = 64f * diff;
			/*printf( "%d %d ", mask_avg, surround_trim ); */
			surround_masking = mask_avg;
		}
		/* Temporal VBR ( but not for LFE ) */
		if( ! this.lfe )
		{
			float follow = -10.0f;
			float frame_avg = 0f;
			final float offset = shortBlocks != 0 ? .5f * (float)LM : 0f;
			for( int i = curr_start; i < curr_end; i++ )
			{
				float v1 = follow - 1.f;// java
				final float v2 = bandLogE[i] - offset;// java
				follow = v1 >= v2 ? v1 : v2;
				if( C == 2 ) {
					v1 = bandLogE[i + nbEBands] - offset;
					follow = follow >= v1 ? follow : v1;
				}
				frame_avg += follow;
			}
			frame_avg /= (curr_end - curr_start);
			temporal_vbr = frame_avg - this.spec_avg;
			temporal_vbr = -1.5f >= temporal_vbr ? -1.5f : temporal_vbr;
			temporal_vbr = 3.f <= temporal_vbr ? 3.f : temporal_vbr;
			this.spec_avg += .02f * temporal_vbr;
		}
		/*for( i = 0; i < 21; i++ )
		printf( "%f ", bandLogE[i] );
		printf( "\n" ); */

		if( ! secondMdct )
		{
			System.arraycopy( bandLogE, 0, bandLogE2, 0, c_nbEBands );
		}

		/* Last chance to catch any transient we might have missed in the
		   time - domain analysis */
		final float[] old_BandE = this.oldBandE;// CC * (overlap + Jcelt.COMBFILTER_MAXPERIOD);
		if( LM > 0 && enc.ec_tell() + 3 <= total_bits && ! isTransient && this.complexity >= 5 && ! this.lfe && ! hybrid )
		{
			if( patch_transient_decision( bandLogE, old_BandE, nbEBands, curr_start, curr_end, C ) )
			{
				isTransient = true;
				shortBlocks = M;
				celt_mode.compute_mdcts( shortBlocks, in, freq, C, CC, LM, this.upsample );//, st.arch );
				celt_mode.compute_band_energies( freq, bandE, effEnd, C, LM );//, st.arch );
				JCELTMode.amp2Log2( celt_mode.nbEBands /* celt_mode */, effEnd, curr_end, bandE, bandLogE, 0, C );
				/* Compensate for the scaling of short vs long mdcts */
				for( int i = 0; i < c_nbEBands; i++ ) {
					bandLogE2[i] += .5f * LM;
				}
				tf_estimate = .2f;
			}
		}

		if( LM > 0 && enc.ec_tell() + 3 <= total_bits ) {
			enc.ec_enc_bit_logp( isTransient, 3 );
		}

		final float[] X = new float[ C * N ]; /** <  Interleaved normalised MDCTs */

		/* Band normalisation */
		celt_mode.normalise_bands( freq, X, bandE, effEnd, C, M );

		final boolean enable_tf_analysis = effectiveBytes >= 15 * C && ! hybrid && this.complexity >= 2 && ! this.lfe;

		final int offsets[] = new int[ nbEBands ];
		final int importance[] = new int[ nbEBands ];
		final int spread_weight[] = new int[ nbEBands ];

		final int[] tot_boost = new int[1];// java changed to int[] to get data back
		final float maxDepth = dynalloc_analysis( bandLogE, bandLogE2, nbEBands, start, end, C, offsets,
				this.lsb_depth, mode.logN, isTransient, this.vbr, this.constrained_vbr,
				eBands, LM, effectiveBytes, tot_boost, this.lfe, surround_dynalloc, this.analysis, importance, spread_weight );

		final int[] tf_res = new int[ nbEBands ];
		int tf_select = 0;
		/* Disable variable tf resolution for hybrid and at very low bitrate */
		if( enable_tf_analysis )
		{
			int lambda = 20480 / effectiveBytes + 2;
			if( lambda < 80 ) {
				lambda = 80;
			}
			tf_select = celt_mode.tf_analysis( effEnd, isTransient, tf_res, lambda, X, N, LM, tf_estimate, tf_chan, importance );
			for( int i = effEnd; i < curr_end; i++ ) {
				tf_res[i] = tf_res[effEnd - 1];
			}
		} else if( hybrid && weak_transient )
		{
			/* For weak transients, we rely on the fact that improving time resolution using
			  TF on a long window is imperfect and will not result in an energy collapse at
			  low bitrate. */
			for( int i = 0; i < curr_end; i++ ) {
				tf_res[i] = 1;
			}
			tf_select = 0;
		} else if( hybrid && effectiveBytes < 15 && this.silk_info.signalType != 2 )
		{
			/* For low bitrate hybrid, we force temporal resolution to 5 ms rather than 2.5 ms. */
			for( int i = 0; i < curr_end; i++ ) {
				tf_res[i] = 0;
			}
			tf_select = isTransient ? 1 : 0;
		} else {
			final int v = isTransient ? 1 : 0;// java
			for( int i = 0; i < curr_end; i++ ) {
				tf_res[i] = v;
			}
			tf_select = 0;
		}

		final float[] error = new float[ c_nbEBands ];
		final float[] energy_Error = this.energyError;// oldLogE2 + CC*nbEBands; java renamed to avoid hiding
		c = 0;
		do {
			for( int i = curr_start + c; i < curr_end; i++ )// java
			{
				/* When the energy is stable, slightly bias energy quantization towards
				  the previous error to make the gain more stable (a constant offset is
				  better than fluctuations). */
				float v = bandLogE[ i ] - old_BandE[ i ];// java
				if( v < 0 ) {
					v = -v;
				}// java abs v
				if( v < 2.f )
				{
					bandLogE[ i ] -= (energy_Error[ i ] * 0.25f);
				}
			}
			c += nbEBands;
		} while( c < c_nbEBands );// ( ++c < C );
		this.delayedIntra = JCELTMode.quant_coarse_energy( celt_mode.nbEBands /* celt_mode */,// java changed
						curr_start, curr_end, effEnd, bandLogE,
						old_BandE, total_bits, error, enc,
						C, LM, nbAvailableBytes, this.force_intra,
						this.delayedIntra, this.complexity >= 4, this.loss_rate, this.lfe );

		tf_encode( curr_start, curr_end, isTransient, tf_res, LM, tf_select, enc );

		if( enc.ec_tell() + 4 <= total_bits )
		{
			if( this.lfe )
			{
				this.tapset_decision = 0;
				this.spread_decision = JCELTMode.SPREAD_NORMAL;
			} else if( hybrid )
			{
				if( this.complexity == 0 ) {
					this.spread_decision = JCELTMode.SPREAD_NONE;
				} else if( isTransient ) {
					this.spread_decision = JCELTMode.SPREAD_NORMAL;
				} else {
					this.spread_decision = JCELTMode.SPREAD_AGGRESSIVE;
				}
			} else if( 0 != shortBlocks || this.complexity < 3 || nbAvailableBytes < 10 * C )
			{
				if( this.complexity == 0 ) {
					this.spread_decision = JCELTMode.SPREAD_NONE;
				} else {
					this.spread_decision = JCELTMode.SPREAD_NORMAL;
				}
			} else {
				/* Disable new spreading + tapset estimator until we can show it works
				   better than the old one. So far it seems like spreading_decision()
				   works best. */
/* #if 0
				if( st.analysis.valid )
				{
					static const opus_val16 spread_thresholds[3] = {-QCONST16( .6f, 15 ), -QCONST16( .2f, 15 ), -QCONST16( .07f, 15 )};
					static const opus_val16 spread_histeresis[3] = {QCONST16( .15f, 15 ), QCONST16( .07f, 15 ), QCONST16( .02f, 15 )};
					static const opus_val16 tapset_thresholds[2] = {QCONST16( .0f, 15 ), QCONST16( .15f, 15 )};
					static const opus_val16 tapset_histeresis[2] = {QCONST16( .1f, 15 ), QCONST16( .05f, 15 )};
					st.spread_decision = hysteresis_decision( -st.analysis.tonality, spread_thresholds, spread_histeresis, 3, st.spread_decision );
					st.tapset_decision = hysteresis_decision( st.analysis.tonality_slope, tapset_thresholds, tapset_histeresis, 2, st.tapset_decision );
				} else
#endif */
				{
					/*st.spread_decision =*/ celt_mode.spreading_decision( this,// java
									X,
									// &st.tonal_average, st.spread_decision, &st.hf_average, &st.tapset_decision,
									pf_on && (0 == shortBlocks), effEnd, C, M, spread_weight );
				}
				/*printf( "%d %d\n", st.tapset_decision, st.spread_decision ); */
				/*printf( "%f %d %f %d\n\n", st.analysis.tonality, st.spread_decision, st.analysis.tonality_slope, st.tapset_decision ); */
			}
			enc.ec_enc_icdf( this.spread_decision, Jcelt.spread_icdf, 0, 5 );
		}

		/* For LFE, everything interesting is in the first band */
		if( this.lfe ) {
			final int v = effectiveBytes / 3;
			offsets[0] = ( 8 <= v ? 8 : v );
		}
		final int[] cap = new int[ nbEBands ];
		celt_mode.init_caps( cap, LM, C );

		int dynalloc_logp = 6;
		total_bits <<= Jec_ctx.BITRES;
		int total_boost = 0;
		tell = enc.ec_tell_frac();
		for( int i = curr_start; i < curr_end; i++ )
		{
			int width = C * (eBands[i + 1] - eBands[i]) << LM;
			/* quanta is 6 bits, but no more than 1 bit/sample
			and no less than 1/8 bit/sample */
			int quanta = ((6 << Jec_ctx.BITRES) >= width ? (6 << Jec_ctx.BITRES) : width);
			width <<= Jec_ctx.BITRES;
			quanta = ( width <= quanta ? width : quanta );
			int dynalloc_loop_logp = dynalloc_logp;
			int boost = 0;
			int j;
			for( j = 0; tell + (dynalloc_loop_logp << Jec_ctx.BITRES) < total_bits - total_boost
					&& boost < cap[i]; j++ )
			{
				final boolean flag = j < offsets[i];
				enc.ec_enc_bit_logp( flag, dynalloc_loop_logp );
				tell = enc.ec_tell_frac();
				if( ! flag ) {
					break;
				}
				boost += quanta;
				total_boost += quanta;
				dynalloc_loop_logp = 1;
			}
			/* Making dynalloc more likely */
			if( 0 != j ) {
				dynalloc_logp--;
				dynalloc_logp = 2 >= dynalloc_logp ? 2 : dynalloc_logp;
			}
			offsets[i] = boost;
		}

		boolean dual_stereo = false;
		if( C == 2 )
		{
			//static final const opus_val16 intensity_thresholds[21] =
			/* 0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19  20  off*/
			//{  1, 2, 3, 4, 5, 6, 7, 8,16,24,36,44,50,56,62,67,72,79,88,106,134};
			//static final const opus_val16 intensity_histeresis[21] =
			//{  1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 4, 5, 6,  8, 8};

			/* Always use MS for 2.5 ms frames until we can do a better analysis */
			if( LM != 0 ) {
				dual_stereo = stereo_analysis( celt_mode.eBands /* celt_mode */, X, LM, N );
			}

			this.intensity = hysteresis_decision( (float)(equiv_rate / 1000),
								intensity_thresholds, intensity_histeresis, 21, this.intensity );
			final int v = curr_start >= this.intensity ? curr_start : this.intensity;// java
			this.intensity = curr_end <= v ? curr_end : v;
		}

		int alloc_trim = 5;
		if( tell + ( 6 << Jec_ctx.BITRES ) <= total_bits - total_boost )
		{
			if( curr_start > 0 || this.lfe )
			{
				this.stereo_saving = 0;
				// alloc_trim = 5;// FIXME alloc_trim already set to 5
			} else {
				final float[] stereo = { this.stereo_saving };// helper to set and return data
				alloc_trim = celt_mode.alloc_trim_analysis( X, bandLogE,
						curr_end, LM, C, N, this.analysis, stereo, tf_estimate,
						this.intensity, surround_trim, equiv_rate );//, st.arch );
				this.stereo_saving = stereo[0];
			}
			enc.ec_enc_icdf( alloc_trim, Jcelt.trim_icdf, 0, 7 );
			tell = enc.ec_tell_frac();
		}

		/* Variable bitrate */
		if( vbr_rate > 0 )
		{
			/* The target rate in 8th bits per frame */
			int target, base_target;
			final int lm_diff = celt_mode.maxLM - LM;

			/* Don't attempt to use more than 510 kb/s, even for frames smaller than 20 ms.
			The CELT allocator will just not be able to use more than that anyway. */
			int v = 1275 >> (3 - LM);// java
			nbCompressedBytes = ( nbCompressedBytes <= v ? nbCompressedBytes : v );
			if( ! hybrid )
			{
				base_target = vbr_rate - ((40 * C + 20) << Jec_ctx.BITRES);
			} else {
				base_target = vbr_rate - ((9 * C + 4) << Jec_ctx.BITRES);
				base_target = (0 > base_target ? 0 : base_target);
			}

			if( this.constrained_vbr ) {
				base_target += (this.vbr_offset >> lm_diff);
			}

			if( ! hybrid )
			{
				target = compute_vbr( celt_mode, this.analysis, base_target, LM, equiv_rate,
						this.lastCodedBands, C, this.intensity, this.constrained_vbr,
						this.stereo_saving, tot_boost[0], tf_estimate, pitch_change, maxDepth,
						this.lfe, this.energy_mask != null, surround_masking,
						temporal_vbr );
			} else {
				target = base_target;
				/* Tonal frames (offset<100) need more bits than noisy (offset>100) ones. */
				if( this.silk_info.offset < 100 ) {
					target += 12 << Jec_ctx.BITRES >> (3 - LM);
				}
				if( this.silk_info.offset > 100 ) {
					target -= 18 << Jec_ctx.BITRES >> (3 - LM);
				}
				/* Boosting bitrate on transients and vowels with significant temporal spikes. */
				target += (int)(tf_estimate - .25f) * (50 << Jec_ctx.BITRES);
				/* If we have a strong transient, let's make sure it has enough bits to code
				   the first two bands, so that it can use folding rather than noise. */
				if( tf_estimate > .7f ) {
					target = (target > (50 << Jec_ctx.BITRES) ? target : (50 << Jec_ctx.BITRES));
				}
			}

			/* The current offset is removed from the target and the space used
			   so far is added*/
			target += tell;
			/* In VBR mode the frame size must not be reduced so much that it would
			   result in the encoder running out of bits.
			   The margin of 2 bytes ensures that none of the bust - prevention logic
			   in the decoder will have triggered so far. */
			int min_allowed = ((tell + total_boost + (1 << (Jec_ctx.BITRES + 3)) - 1) >> (Jec_ctx.BITRES + 3)) + 2;
			/* Take into account the 37 bits we need to have left in the packet to
			   signal a redundant frame in hybrid mode. Creating a shorter packet would
			   create an entropy coder desync. */
			if( hybrid ) {
				v = (tell0_frac + (37 << Jec_ctx.BITRES) + total_boost + (1 << (Jec_ctx.BITRES + 3)) - 1) >> (Jec_ctx.BITRES + 3);// java
				min_allowed = (min_allowed >= v ? min_allowed : v);
			}
			nbAvailableBytes = (target + (1 << (Jec_ctx.BITRES + 2))) >> (Jec_ctx.BITRES + 3);
			nbAvailableBytes = min_allowed >= nbAvailableBytes ? min_allowed : nbAvailableBytes;
			nbAvailableBytes = (nbCompressedBytes <= nbAvailableBytes ? nbCompressedBytes : nbAvailableBytes);

			/* By how much did we "miss" the target on that frame */
			int delta = target - vbr_rate;

			target = nbAvailableBytes << (Jec_ctx.BITRES + 3);

			/*If the frame is silent we don't adjust our drift, otherwise
			   the encoder will shoot to very high rates after hitting a
			   span of silence, but we do allow the bitres to refill.
			   This means that we'll undershoot our target in CVBR/VBR modes
			   on files with lots of silence. */
			if( silence )
			{
				nbAvailableBytes = 2;
				target = 2 * 8 << Jec_ctx.BITRES;
				delta = 0;
			}

			float alpha;
			if( this.vbr_count < 970 )
			{
				this.vbr_count++;
				alpha = 1.f / (float)(this.vbr_count + 20);
			} else {
				alpha = .001f;
			}
			/* How many bits have we used in excess of what we're allowed */
			if( this.constrained_vbr ) {
				this.vbr_reservoir += target - vbr_rate;
			}
			/*printf ( "%d\n", st.vbr_reservoir ); */

			/* Compute the offset we need to apply in order to reach the target */
			if( this.constrained_vbr )
			{
				this.vbr_drift += (int)(alpha * ((delta * (1 << lm_diff)) - this.vbr_offset - this.vbr_drift));
				this.vbr_offset = -this.vbr_drift;
			}
			/*printf ( "%d\n", st.vbr_drift ); */

			if( this.constrained_vbr && this.vbr_reservoir < 0 )
			{
				/* We're under the min value -- increase rate */
				final int adjust = (-this.vbr_reservoir) / (8 << Jec_ctx.BITRES);
				/* Unless we're just coding silence */
				if( ! silence ) {
					nbAvailableBytes += adjust;
				}
				this.vbr_reservoir = 0;
				/*printf ( " + %d\n", adjust ); */
			}
			nbCompressedBytes = nbCompressedBytes <= nbAvailableBytes ? nbCompressedBytes : nbAvailableBytes;
			/*printf( "%d\n", nbCompressedBytes*50*8 ); */
			/* This moves the raw bits to take into account the new compressed size */
			enc.ec_enc_shrink( nbCompressedBytes );
		}

		/* bits =          packet size                   - where we are - safety*/
		int bits = ((nbCompressedBytes << 3) << Jec_ctx.BITRES ) - enc.ec_tell_frac() - 1;
		anti_collapse_rsv = isTransient && LM >= 2 && bits >= ((LM + 2) << Jec_ctx.BITRES) ? (1 << Jec_ctx.BITRES) : 0;
		bits -= anti_collapse_rsv;
		int signalBandwidth = curr_end - 1;
// #ifndef DISABLE_FLOAT_API
		if( this.analysis.valid )
		{
			int min_bandwidth;
			if( equiv_rate < 32000 * C ) {
				min_bandwidth = 13;
			} else if( equiv_rate < 48000 * C ) {
				min_bandwidth = 16;
			} else if( equiv_rate < 60000 * C ) {
				min_bandwidth = 18;
			} else  if( equiv_rate < 80000 * C ) {
				min_bandwidth = 19;
			} else {
				min_bandwidth = 20;
			}
			signalBandwidth = this.analysis.bandwidth > min_bandwidth ? this.analysis.bandwidth : min_bandwidth;
		}
// #endif
		if( this.lfe ) {
			signalBandwidth = 1;
		}

		/* Bit allocation */
		final int[] fine_quant = new int[ nbEBands ];
		final int[] pulses = new int[ nbEBands];
		final int[] fine_priority = new int[ nbEBands ];

		final Jallocation_aux aux = new Jallocation_aux( this.intensity, dual_stereo );// java
		final int codedBands = celt_mode.clt_compute_allocation( curr_start, curr_end, offsets, cap, alloc_trim,
						// &st.intensity, &dual_stereo,
						aux,// java replaced st.intensity, dual_stereo, balance
						bits,
						// &balance,
						pulses, fine_quant, fine_priority, C, LM, enc, true, this.lastCodedBands, signalBandwidth );
		// final int balance = aux.balance;// java
		this.intensity = aux.mIntensity;// java
		// dual_stereo = aux.dual_stereo;// java

		if( 0 != this.lastCodedBands ) {
			int v = this.lastCodedBands - 1;// java
			v = v >= codedBands ? v : codedBands;
			final int v1 = this.lastCodedBands + 1;// java
			this.lastCodedBands = ( v1 <= v ? v1 : v );
		} else {
			this.lastCodedBands = codedBands;
		}

		quant_fine_energy( celt_mode.nbEBands /* celt_mode */, curr_start, curr_end, old_BandE, error, fine_quant, enc, C );

		/* Residual quantisation */
		final byte[] collapse_masks = new byte[ c_nbEBands ];// unsigned char );
		this.rng = celt_mode.quant_all_bands( true, curr_start, curr_end, X, C == 2 ? X : null, N, collapse_masks,
					bandE, pulses, shortBlocks, this.spread_decision,
					aux.mDualStereo, this.intensity, tf_res, nbCompressedBytes * (8 << Jec_ctx.BITRES) - anti_collapse_rsv,
					aux.mBalance, enc, LM, codedBands, this.rng, this.complexity,/* st.arch,*/ this.disable_inv );

		if( anti_collapse_rsv > 0 )
		{
			final int anti_collapse_on = this.consec_transient < 2 ? 1 : 0;
/* #ifdef FUZZING
			anti_collapse_on = rand() & 0x1;
#endif */
			enc.ec_enc_bits( anti_collapse_on, 1 );
		}
		quant_energy_finalise( celt_mode.nbEBands /* celt_mode */, curr_start, curr_end, old_BandE,
				error, fine_quant, fine_priority, (nbCompressedBytes << 3) - enc.ec_tell(), enc, C );
		c = cc_nbEBands;
		do {
			energy_Error[ --c ] = 0f;
		} while( c > 0 );
		// c = 0;// java: c already 0
		do {
			for( int i = curr_start + c; i < curr_end; i++ )
			{
				float v = error[i];// java
				v = 0.5f < v ? 0.5f : v;
				v = -0.5f > v ? -0.5f : v;
				energy_Error[i] = v;
			}
			c += nbEBands;
		} while( ++c < c_nbEBands );

		if( silence )
		{
			for( int i = 0; i < c_nbEBands; i++ ) {
				old_BandE[ i ] = -28.f;
			}
		}

/* #ifdef RESYNTH
		// Re - synthesis of the coded audio if required
		{
			celt_sig *out_mem[2];

			if( anti_collapse_on )
			{
				anti_collapse( mode, X, collapse_masks, LM, C, N,
							start, end, oldBandE, oldLogE, oldLogE2, pulses, st.rng );
			}

			c = 0;
			do {
				OPUS_MOVE( st.syn_mem[c], st.syn_mem[c] + N, 2 * MAX_PERIOD - N + overlap / 2 );
			} while ( ++c < CC );

			c = 0;
			do {
				out_mem[c] = st.syn_mem[c] + 2 * MAX_PERIOD - N;
			} while ( ++c < CC );

			celt_synthesis( mode, X, out_mem, oldBandE, start, effEnd,
						C, CC, isTransient, LM, st.upsample, silence, st.arch );

			c = 0;
			do {
				st.prefilter_period = IMAX( st.prefilter_period, COMBFILTER_MINPERIOD );
				st.prefilter_period_old = IMAX( st.prefilter_period_old, COMBFILTER_MINPERIOD );
				comb_filter( out_mem[c], out_mem[c], st.prefilter_period_old, st.prefilter_period, mode.shortMdctSize,
							st.prefilter_gain_old, st.prefilter_gain, st.prefilter_tapset_old, st.prefilter_tapset,
							mode.window, overlap );
				if( LM!= 0 )
					comb_filter( out_mem[c] + mode.shortMdctSize, out_mem[c] + mode.shortMdctSize, st.prefilter_period, pitch_index, N - mode.shortMdctSize,
							st.prefilter_gain, gain1, st.prefilter_tapset, prefilter_tapset,
							mode.window, overlap );
			} while( ++c < CC );

			// We reuse freq[] as scratch space for the de - emphasis
			deemphasis( out_mem, ( opus_val16* )pcm, N, CC, st.upsample, mode.preemph, st.preemph_memD );
			st.prefilter_period_old = st.prefilter_period;
			st.prefilter_gain_old = st.prefilter_gain;
			st.prefilter_tapset_old = st.prefilter_tapset;
		}
#endif */

		this.prefilter_period = pitch_index;
		this.prefilter_gain = gain1;
		this.prefilter_tapset = prefilter_tap_set;
/* #ifdef RESYNTH
		if( LM != 0 )
		{
			st.prefilter_period_old = st.prefilter_period;
			st.prefilter_gain_old = st.prefilter_gain;
			st.prefilter_tapset_old = st.prefilter_tapset;
		}
#endif */

		if( CC == 2 && C == 1 ) {
			System.arraycopy( old_BandE, 0, old_BandE, nbEBands, nbEBands );
		}

		final float[] old_LogE = this.oldLogE;// oldBandE + CC * nbEBands;
		final float[] old_LogE2 = this.oldLogE2;// oldLogE + CC * nbEBands;
		if( ! isTransient )
		{
			System.arraycopy( old_LogE, 0, old_LogE2, 0, cc_nbEBands );
			System.arraycopy( old_BandE, 0, old_LogE, 0, cc_nbEBands );
		} else {
			for( int i = 0; i < cc_nbEBands; i++ ) {
				final float v1 = old_LogE[i];// java
				final float v2 = old_BandE[i];// java
				old_LogE[i] = v1 <= v2 ? v1 : v2;
			}
		}
		/* In case start or end were to change */
		c = 0;
		do
		{
			for( int i = c, ie = i + curr_start; i < ie; i++ )
			{
				old_BandE[ i ] = 0;
				old_LogE[ i ] = old_LogE2[ i ] = -28.f;
			}
			for( int i = curr_end + c, k = c + nbEBands; i < k; i++ )
			{
				old_BandE[ i ] = 0;
				old_LogE[ i ] = old_LogE2[ i ] = -28.f;
			}
			c += nbEBands;
		} while( c < cc_nbEBands );

		if( isTransient || transient_got_disabled ) {
			this.consec_transient++;
		} else {
			this.consec_transient = 0;
		}
		this.rng = (int)enc.rng;

		/* If there's any room left ( can only happen for very high rates ),
		  it's already filled with zeros */
		enc.ec_enc_done();

/* #ifdef CUSTOM_MODES
		if( st.signalling )
			nbCompressedBytes++;
#endif */

		// RESTORE_STACK;
		if( 0 != enc.ec_get_error() ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}// else {
			return nbCompressedBytes;
		//}
	}


/* #ifdef CUSTOM_MODES

#ifdef FIXED_POINT
	int opus_custom_encode(CELTEncoder * OPUS_RESTRICT st, const opus_int16 * pcm, int frame_size, unsigned char *compressed, int nbCompressedBytes )
	{
		return celt_encode_with_ec( st, pcm, frame_size, compressed, nbCompressedBytes, NULL );
	}

#ifndef DISABLE_FLOAT_API
	int opus_custom_encode_float( CELTEncoder * OPUS_RESTRICT st, const float * pcm, int frame_size, unsigned char *compressed, int nbCompressedBytes )
	{
		int j, ret, C, N;
		VARDECL( opus_int16, in );
		ALLOC_STACK;

		if( pcm == NULL )
			return OPUS_BAD_ARG;

		C = st.channels;
		N = frame_size;
		ALLOC( in, C*N, opus_int16 );

		for( j = 0; j < C*N; j++ )
			in[j] = FLOAT2INT16( pcm[j] );

		ret = celt_encode_with_ec( st,in,frame_size,compressed,nbCompressedBytes, NULL );
#ifdef RESYNTH
		for( j = 0; j < C*N; j++ )
			( ( float* )pcm )[j] = in[j] * ( 1.f / 32768.f );
#endif
		RESTORE_STACK;
		return ret;
	}
#endif */ /* DISABLE_FLOAT_API */
/* #else

	int opus_custom_encode( CELTEncoder * OPUS_RESTRICT st, const opus_int16 * pcm, int frame_size, unsigned char *compressed, int nbCompressedBytes )
	{
		int j, ret, C, N;
		VARDECL( celt_sig, in );
		ALLOC_STACK;

		if( pcm == NULL )
			return OPUS_BAD_ARG;

		C = st.channels;
		N = frame_size;
		ALLOC( in, C*N, celt_sig );
		for( j = 0; j < C*N; j++ ) {
			in[j] = SCALEOUT( pcm[j] );
		}

		ret = celt_encode_with_ec( st,in,frame_size,compressed,nbCompressedBytes, NULL );
#ifdef RESYNTH
		for( j = 0; j < C*N; j++ )
			( ( opus_int16* )pcm )[j] = FLOAT2INT16( in[j] );
#endif
		RESTORE_STACK;
		return ret;
	}

	int opus_custom_encode_float( CELTEncoder * OPUS_RESTRICT st, const float * pcm, int frame_size, unsigned char *compressed, int nbCompressedBytes )
	{
		return celt_encode_with_ec( st, pcm, frame_size, compressed, nbCompressedBytes, NULL );
	}

#endif

#endif */ /* CUSTOM_MODES */

	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	// #define celt_encoder_ctl opus_custom_encoder_ctl
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_encoder_ctl(final int request, final Object[] arg) {
		return opus_custom_encoder_ctl( request, arg );
	}
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_encoder_ctl(final int request, final int arg) {
		return opus_custom_encoder_ctl( request, arg );
	}
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_encoder_ctl(final int request, final boolean arg) {
		return opus_custom_encoder_ctl( request, arg );
	}
	/**
	 * Setters for objects
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_encoder_ctl(final int request, final Object arg) {
		return opus_custom_encoder_ctl( request, arg );
	}
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return status
	 */
	public final int celt_encoder_ctl(final int request) {
		return opus_custom_encoder_ctl( request );
	}

}
