package celt;

import opus.Jopus_defines;

/**
 * java struct CELTDecoder
 */
public final class JCELTDecoder extends JOpusCustomDecoder {
	// start celt_lpc.c
	private static final int LPC_ORDER = 24;
	// end celt_lpc.c
	private static final int MAX_PERIOD = 1024;

	// celt_decoder.c
	/** The maximum pitch lag to allow in the pitch - based PLC. It's possible to save
	   CPU time in the PLC pitch search by making this smaller than MAX_PERIOD. The
	   current value corresponds to a pitch of 66.67 Hz. */
	private static final int PLC_PITCH_LAG_MAX = 720;
	/** The minimum pitch lag to allow in the pitch - based PLC. This corresponds to a
	   pitch of 480 Hz. */
	private static final int PLC_PITCH_LAG_MIN = 100;

//#if defined(ENABLE_HARDENING) || defined(ENABLE_ASSERTIONS)
	/* Make basic checks on the CELT state to ensure we don't end
       up writing all over memory. */
/*	private void validate_celt_decoder()
	{
#ifndef CUSTOM_MODES
		celt_assert(st->mode == opus_custom_mode_create(48000, 960, NULL));
		celt_assert(st->overlap == 120);
#endif
		celt_assert(st->channels == 1 || st->channels == 2);
		celt_assert(st->stream_channels == 1 || st->stream_channels == 2);
		celt_assert(st->downsample > 0);
		celt_assert(st->start == 0 || st->start == 17);
		celt_assert(st->start < st->end);
		celt_assert(st->end <= 21);
#ifdef OPUS_ARCHMASK
		celt_assert(st->arch >= 0);
		celt_assert(st->arch <= OPUS_ARCHMASK);
#endif
		celt_assert(st->last_pitch_index <= PLC_PITCH_LAG_MAX);
		celt_assert(st->last_pitch_index >= PLC_PITCH_LAG_MIN || st->last_pitch_index == 0);
		celt_assert(st->postfilter_period < MAX_PERIOD);
		celt_assert(st->postfilter_period >= COMBFILTER_MINPERIOD || st->postfilter_period == 0);
		celt_assert(st->postfilter_period_old < MAX_PERIOD);
		celt_assert(st->postfilter_period_old >= COMBFILTER_MINPERIOD || st->postfilter_period_old == 0);
		celt_assert(st->postfilter_tapset <= 2);
		celt_assert(st->postfilter_tapset >= 0);
		celt_assert(st->postfilter_tapset_old <= 2);
		celt_assert(st->postfilter_tapset_old >= 0);
	}
#endif
*/
	/* java don't need
	private static final int celt_decoder_get_size( int channels )
	{
		final JCELTMode mode = opus_custom_mode_create( 48000, 960, null );
		return opus_custom_decoder_get_size(  mode, channels  );
	}
	*/

	/* java don't need
	private static final int opus_custom_decoder_get_size(final JCELTMode mode, int channels)
	{
		int size = sizeof( struct CELTDecoder )
			+ ( channels * ( DECODE_BUFFER_SIZE + mode.overlap ) - 1 ) * sizeof( celt_sig )
			+ channels * LPC_ORDER * sizeof( opus_val16 )
			+ 4 * 2 * mode.nbEBands * sizeof( opus_val16 );
		return size;
	}
	*/

/* #ifdef CUSTOM_MODES
	private static final JCELTDecoder opus_custom_decoder_create(final JCELTMode mode, int channels, int[] error )
	{
		int ret;
		JCELTDecoder st = (JCELTDecoder)opus_alloc( opus_custom_decoder_get_size( mode, channels ) );
		ret = opus_custom_decoder_init( st, mode, channels );
		if( ret != OPUS_OK )
		{
			opus_custom_decoder_destroy( st );
			st = NULL;
		}
		if( null != error )
			error[0] = ret;
		return st;
	}
#endif */ /* CUSTOM_MODES */
	/**
	 * default constructor
	 */
	public JCELTDecoder() {
	}
	/**
	 * java helper to replace code:
	 * <pre>
	 * int decsize = opus_decoder_get_size( 1 );
	 * OpusDecoder *dec = (OpusDecoder*)malloc( decsize );
	 * </pre>
	 * @param channels
	 */
	public JCELTDecoder(final int channels) {
		final JCELTMode m = JCELTMode.opus_custom_mode_create( 48000, 960, null );
		opus_custom_decoder_init( m, channels  );
	}
	/**
	 *
	 * @param sampling_rate
	 * @param nchannels
	 * @return status
	 */
	public final int celt_decoder_init(final int sampling_rate, final int nchannels)
	{
		final int ret = opus_custom_decoder_init( JCELTMode.opus_custom_mode_create( 48000, 960, null ), nchannels );
		if( ret != Jopus_defines.OPUS_OK ) {
			return ret;
		}
		this.downsample = Jcelt.resampling_factor( sampling_rate );
		if( this.downsample == 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		} else {
			return Jopus_defines.OPUS_OK;
		}
	}

	private final int opus_custom_decoder_init(final JCELTMode celt_mode, final int nchannels)
	{
		if( nchannels < 0 || nchannels > 2 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		/* if( st == null ) {
			return Jopus_defines.OPUS_ALLOC_FAIL;
		} */

		this.clear( true );// OPUS_CLEAR( ( char *  )st, opus_custom_decoder_get_size( mode, channels ) );
		//st._decode_mem = new float[(channels * (DECODE_BUFFER_SIZE + mode.overlap)/* - 1*/)// java: c uses -1 to account default size of the _decode_mem[1]
		//	               			+ channels * Jcelt_lpc.LPC_ORDER + (mode.nbEBands << 3) ];
		this._decode_mem = new float[ nchannels * (DECODE_BUFFER_SIZE + celt_mode.overlap) ];
		this.lpc = new float[ nchannels * LPC_ORDER ];
		final int size = celt_mode.nbEBands << 1;
		this.oldEBands      = new float[ size ];
		this.oldLogE        = new float[ size ];
		this.oldLogE2       = new float[ size ];
		this.backgroundLogE = new float[ size ];

		this.mode = celt_mode;
		this.overlap = celt_mode.overlap;
		this.stream_channels = this.channels = nchannels;

		this.downsample = 1;
		this.start = 0;
		this.end = this.mode.effEBands;
		this.signalling = true;
if( ! Jopus_defines.DISABLE_UPDATE_DRAFT ) {
		this.disable_inv = nchannels == 1;
} else {
		this.disable_inv = false;
}
		//st.arch = opus_select_arch();

		this.opus_custom_decoder_ctl( Jopus_defines.OPUS_RESET_STATE );

		return Jopus_defines.OPUS_OK;
	}

/* #ifdef CUSTOM_MODES
	void opus_custom_decoder_destroy( CELTDecoder  * st )
	{
		opus_free( st );
	}
#endif */ /* CUSTOM_MODES */

// #ifndef CUSTOM_MODES
	/* Special case for stereo with no downsampling and no accumulation. This is
	   quite common and we can make it faster by processing both channels in the
	   same loop, reducing overhead due to the dependency loop in the IIR filter. */
	private static final void deemphasis_stereo_simple(final float in[], final int[/* 2 */] out_syn,// java in[ out_syn[0, 1] ]
			final float[] pcm, int pcmofset,// java
			final int N, final float coef0,
			final float[] mem)
	{
		int x0 = out_syn[0];// in[0]; java using: in[x0]
		int x1 = out_syn[1];// in[1]; java using: in[x1]
		float m0 = mem[0];
		float m1 = mem[1];
		for( final int j = x0 + N; x0 < j; )
		{
			/* Add VERY_SMALL to x[] first to reduce dependency chain. */
			final float tmp0 = in[ x0++ ] + Jfloat_cast.VERY_SMALL + m0;
			final float tmp1 = in[ x1++ ] + Jfloat_cast.VERY_SMALL + m1;
			m0 = (coef0 * tmp0);
			m1 = (coef0 * tmp1);
			pcm[pcmofset++] = tmp0 / Jfloat_cast.CELT_SIG_SCALE;// SCALEOUT( tmp0 );
			pcm[pcmofset++] = tmp1 / Jfloat_cast.CELT_SIG_SCALE;// SCALEOUT( tmp1 );
		}
		mem[0] = m0;
		mem[1] = m1;
	}
// #endif

	private static final void deemphasis(final float in[], final int[/* 2 */] out_syn,// java in[ out_syn[0, 1] ]
			final float[] pcm, final int pcmofset,// java
			final int N, final int C, final int downsample,
			final float[] coef, final float[] mem, final boolean accum)
	{
		boolean apply_downsampling = false;
		//SAVE_STACK;
// #ifndef CUSTOM_MODES
		/* Short version for common case. */
		if( downsample == 1 && C == 2 && ! accum )
		{
			deemphasis_stereo_simple( in, out_syn, pcm, pcmofset, N, coef[0], mem );
			return;
		}
// #endif
//#ifndef FIXED_POINT
		//(void)accum;
		//celt_assert( accum == 0 );
//#endif
		final float[] scratch = new float[N];
		final float coef0 = coef[0];
		final int Nd = N / downsample;
		int c = 0;
		do {
			float m = mem[c];
			int x = out_syn[c];// using: in[ x ]
			// final int y = c;// pcm[y]
/* #ifdef CUSTOM_MODES
			if( coef[1] != 0 )
			{
				opus_val16 coef1 = coef[1];
				opus_val16 coef3 = coef[3];
				for( j = 0; j < N; j++ )
				{
					celt_sig tmp = x[j] + m + VERY_SMALL;
					m = MULT16_32_Q15( coef0, tmp )
							-  MULT16_32_Q15( coef1, x[j] );
					tmp = SHL32( MULT16_32_Q15( coef3, tmp ), 2 );
					scratch[j] = tmp;
				}
				apply_downsampling = 1;
			} else
#endif */
			if( downsample > 1 )
			{
				/* Shortcut for the standard ( non - custom modes ) case */
				for( int j = 0; j < N; j++ )
				{
					final float tmp = in[ x++ ] + Jfloat_cast.VERY_SMALL + m;
					m = coef0 * tmp;
					scratch[j] = tmp;
				}
				apply_downsampling = true;
			} else {
				/* Shortcut for the standard ( non - custom modes ) case */
/* #ifdef FIXED_POINT
				if( accum )
				{
					for( j = 0; j < N; j++ )
					{
						celt_sig tmp = x[j] + m + VERY_SMALL;
						m = MULT16_32_Q15( coef0, tmp );
						y[j * C] = SAT16( ADD32( y[j * C], SCALEOUT( SIG2WORD16( tmp ) ) ) );
					}
				} else
#endif */
				{
					for( int j = x + N, yjc = pcmofset + c/*y*/; x < j; yjc += C )
					{
						final float tmp = in[ x++ ] + Jfloat_cast.VERY_SMALL + m;
						m = coef0 * tmp;
						pcm[yjc] = tmp / Jfloat_cast.CELT_SIG_SCALE;
					}
				}
			}
			mem[c] = m;

			if( apply_downsampling )
			{
				/* Perform down - sampling */
/* #ifdef FIXED_POINT
				if( accum )
				{
					for( j = 0; j < Nd; j++ )
						y[j * C] = SAT16( ADD32( y[j * C], SCALEOUT( SIG2WORD16( scratch[j * downsample] ) ) ) );
				} else
#endif */
				{
					for( int j = 0, je = Nd * downsample, yjc = pcmofset + c/*y*/; j < je; j += downsample, yjc += C ) {
						pcm[yjc] = scratch[j] / Jfloat_cast.CELT_SIG_SCALE;
					}
				}
			}
		} while ( ++c < C );
		//RESTORE_STACK;
	}

	private static final void tf_decode(final int start, final int end, final boolean isTransient, final int[] tf_res, final int LM, final Jec_dec dec )
	{
		int budget = dec.storage << 3;// FIXME why uint32 budget?
		int tell = dec.ec_tell();// FIXME why uint32 tell?
		int logp = isTransient ? 2 : 4;
		final boolean tf_select_rsv = LM > 0 && tell + logp + 1 <= budget;
		if( tf_select_rsv ) {
			budget--;
		}
		boolean tf_changed, curr;
		tf_changed = curr = false;
		for( int i = start; i < end; i++ )
		{
			if( tell + logp <= budget )
			{
				curr ^=  dec.ec_dec_bit_logp( logp );
				tell = dec.ec_tell();
				tf_changed |=  curr;
			}
			tf_res[i] = curr ? 1 : 0;
			logp = isTransient ? 4 : 5;
		}
		boolean tf_select = false;
		int j = 0;// int j = (isTransient ? 4 : 0) + (tf_changed ? 1 : 0);
		if( isTransient ) {
			j += 4;
		}
		if( tf_changed ) {
			j++;
		}

		if( tf_select_rsv && tf_select_table[LM][j] != tf_select_table[LM][j + 2] )
		{
			tf_select = dec.ec_dec_bit_logp( 1 );
		}
		j = 0;// j = (isTransient ? 4 : 0) + (tf_select ? 2 : 0);// java
		if( isTransient ) {
			j += 4;
		}
		if( tf_select ) {
			j += 2;
		}
		for( int i = start; i < end; i++ )
		{
			tf_res[i] = (int)tf_select_table[LM][j + tf_res[i]];
		}
	}

	private static final int celt_plc_pitch_search(final float decode_mem[], final int offset[/* 2 */], final int C)//, int arch)
	{// java offset is added. decode_mem[ offset[] ]
		//SAVE_STACK;
		final float[] lp_pitch_buf = new float[DECODE_BUFFER_SIZE >> 1];
		pitch_downsample( decode_mem, offset, lp_pitch_buf, DECODE_BUFFER_SIZE, C );//, arch );
		int pitch_index = pitch_search( lp_pitch_buf, (PLC_PITCH_LAG_MAX >> 1), lp_pitch_buf,
					DECODE_BUFFER_SIZE - PLC_PITCH_LAG_MAX,
					PLC_PITCH_LAG_MAX - PLC_PITCH_LAG_MIN );//, &pitch_index, arch );
		pitch_index = PLC_PITCH_LAG_MAX - pitch_index;
		//RESTORE_STACK;
		return pitch_index;
	}

	// start celt_lpc.c
	//private static final void celt_fir_c(final float[] _x, final float[] num, final float[] _y,
	//	final int N, final int ord, final int arch )
	// java renamed
	private static final void celt_fir(final float[] x, int xoffset,// java
			final float[] num, final int noffset,// java
			final float[] y, int yoffset,// java
			int N, final int ord )
	{
		// SAVE_STACK;
		// celt_assert(x != y);
		final float[] rnum = new float[ord];
		for( int i = 0, j = noffset + ord - 1; i < ord; i++, j-- ) {
			rnum[i] = num[j];
		}
		final float sum[] = new float[4];// java
		N += xoffset;// java
		for( final int n3 = N - 3; xoffset < n3; /* xoffset += 4 */ )
		{
			final int x_ord = xoffset - ord;// java
			sum[0] = x[xoffset++];
			sum[1] = x[xoffset++];
			sum[2] = x[xoffset++];
			sum[3] = x[xoffset++];
			xcorr_kernel( rnum, 0, x, x_ord, sum, ord );//, arch );
			y[yoffset++] = sum[0];
			y[yoffset++] = sum[1];
			y[yoffset++] = sum[2];
			y[yoffset++] = sum[3];
		}
		for( ; xoffset < N; xoffset++ )
		{
			float s = x[xoffset];// java sum is changed to s
			for( int j = 0, i = xoffset - ord; j < ord; j++ ) {
				s += rnum[j] * x[i++];
			}
			y[yoffset++] = s;
		}
		// RESTORE_STACK;
	}

	private static final void celt_iir(final float[] _x, final int xoffset,// java
			final float[] den, final int doffset,// java
			final float[] _y, final int yoffset,// java
			final int N, final int ord, final float[] mem)// , final int arch)
	{
/* #ifdef SMALL_FOOTPRINT
		int i, j;
		(void)arch;
		for( i = 0; i < N; i++ )
		{
			opus_val32 sum = _x[i];
			for( j = 0; j < ord; j++ )
			{
				sum -= MULT16_16( den[j], mem[j] );
			}
			for( j = ord - 1; j >= 1; j-- )
			{
				mem[j] = mem[j - 1];
			}
			mem[0] = SROUND16( sum, SIG_SHIFT );
			_y[i] = sum;
		}
#else */
		int i, j;
		// SAVE_STACK;

		//celt_assert( (ord & 3 )==0 );
		final float[] rden = new float[ord];
		final float[] y = new float[N + ord];
		for( i = 0, j = doffset + ord - 1; i < ord; i++, j-- ) {
			rden[i] = den[j];
		}
		for( i = 0, j = ord - 1; i < ord; i++, j-- ) {
			y[i] = -mem[j];
		}
		for( j = N + ord; i < j; i++ ) {
			y[i] = 0;
		}
		final float sum[] = new float[4];// java out of the loop
		for( i = 0, j = N - 3; i < j; i += 4 )
		{
			/* Unroll by 4 as if it were an FIR filter */
			int oi = xoffset + i;// java
			sum[0] = _x[oi++];
			sum[1] = _x[oi++];
			sum[2] = _x[oi++];
			sum[3] = _x[oi];
			xcorr_kernel( rden, 0, y, i, sum, ord );// , arch );

			/* Patch up the result to compensate for the fact that this is an IIR */
			oi = yoffset + i;// java
			final int iord = i + ord;// java
			y[iord] = -sum[0];
			_y[oi++] = sum[0];
			sum[1] += y[iord] * den[doffset];
			y[iord + 1] = -sum[1];
			_y[oi++] = sum[1];
			sum[2] += y[iord + 1] * den[doffset];
			sum[2] += y[iord    ] * den[doffset + 1];
			y[iord + 2] = -sum[2];
			_y[oi++] = sum[2];

			sum[3] += y[iord + 2] * den[doffset];
			sum[3] += y[iord + 1] * den[doffset + 1];
			sum[3] += y[iord    ] * den[doffset + 2];
			y[iord + 3] = -sum[3];
			_y[oi] = sum[3];
		}
		for( ; i < N; i++ )
		{
			float s = _x[xoffset + i];// java sum renamed
			int ij = i;// java
			for( j = 0; j < ord; j++ ) {
				s -= rden[j] * y[ ij++ ];
			}
			y[i + ord] = s;
			_y[yoffset + i] = s;
		}
		for( i = 0, j = yoffset + N - 1; i < ord; i++, j-- ) {
			mem[i] = _y[j];
		}
		// RESTORE_STACK;
// #endif
	}
	// end celt_lpc.c

	private final void celt_decode_lost(final int N, final int LM)
	{
		final int C = this.channels;
		// SAVE_STACK;

		final JCELTMode celt_mode = (JCELTMode)this.mode;// java renamed
		final int nbEBands = celt_mode.nbEBands;
		final int m_overlap = celt_mode.overlap;
		final int overlap2 = m_overlap >> 1;// java
		final short[] eBands = celt_mode.eBands;
		final float[] st_mem = this._decode_mem;// java
		final int decode_mem[] = new int[2];// st_mem[ decode_mem[c] ]
		final int out_syn[] = new int[2];// st_mem[ out_syn[c] ]

		int c = 0;
		do {
			final int i = c * (DECODE_BUFFER_SIZE + m_overlap);// java
			decode_mem[c] = i;
			out_syn[c] = i + DECODE_BUFFER_SIZE - N;
		} while( ++c < C );
		/*
		lpc = (opus_val16 *)(st._decode_mem + (DECODE_BUFFER_SIZE + overlap) * C);
		oldBandE = lpc + C * LPC_ORDER;
		oldLogE = oldBandE + 2 * nbEBands;
		oldLogE2 = oldLogE + 2 * nbEBands;
		backgroundLogE = oldLogE2 + 2 * nbEBands;
		*/
		final float[] flpc = this.lpc;// (DECODE_BUFFER_SIZE + overlap) * C;
		final float[] oldBandE = this.oldEBands;// lpc + C * Jcelt_lpc.LPC_ORDER;
		final float[] background_LogE = this.backgroundLogE;// oldLogE2 + 2 * nbEBands;

		final int iloss_count = this.loss_count;
		final int istart = this.start;
		final boolean noise_based = iloss_count >= 5 || istart != 0 || this.skip_plc;
		if( noise_based )
		{
			/* Noise - based PLC/CNG */
/* #ifdef NORM_ALIASING_HACK
			celt_norm  * X;
#else */
			//VARDECL( celt_norm, X );
//#endif
			final int iend = this.end;
			int effEnd = iend <= celt_mode.effEBands ? iend : celt_mode.effEBands;
			effEnd = istart >= effEnd ? istart : effEnd;

//#ifdef NORM_ALIASING_HACK
			/* This is an ugly hack that breaks aliasing rules and would be easily broken,
		   	   but it saves almost 4kB of stack. */
//			X = ( celt_norm *  )( out_syn[C - 1] + overlap/2 );
//#else
			final float[] X = new float[C * N];   /** < Interleaved normalised MDCTs */
//#endif

			/* Energy decay */
			final float decay = iloss_count == 0 ? 1.5f : .5f;
			c = 0;
			do
			{
				for( int i = istart; i < iend; i++ ) {
					final int cni = c * nbEBands + i;// java
					final float v1 = background_LogE[ cni ];// java
					final float v2 = oldBandE[ cni ] - decay;// java
					oldBandE[ cni ] = v1 >= v2 ? v1 : v2;
				}
			} while( ++c < C );
			int seed = this.rng;
			for( c = 0; c < C; c++ )
			{
				for( int i = istart; i < effEnd; i++ )
				{
					final int boffs = N * c + (eBands[i] << LM);
					final int blen = (eBands[i + 1] - eBands[i]) << LM;
					for( int j = boffs, je = blen + boffs; j < je; j++ )
					{
						seed = Jband_ctx.celt_lcg_rand( seed );
						X[j] = (float)(seed >> 20);
					}
					Jband_ctx.renormalise_vector( X, boffs, blen, Jfloat_cast.Q15ONE );//, st.arch );
				}
			}
			this.rng = seed;

			c = 0;
			do {
				System.arraycopy( st_mem, decode_mem[c] + N, st_mem, decode_mem[c], DECODE_BUFFER_SIZE - N + overlap2 );
			} while ( ++c < C );

			celt_mode.celt_synthesis( X, st_mem, out_syn, oldBandE, istart, effEnd, C, C, false, LM, this.downsample, false );//, st.arch );
		} else {
			/* Pitch - based PLC */
			float fade = Jfloat_cast.Q15ONE;
			final int pitch_index;

			if( iloss_count == 0 )
			{
				this.last_pitch_index = pitch_index = celt_plc_pitch_search( st_mem, decode_mem, C );//, st.arch );
			} else {
				pitch_index = this.last_pitch_index;
				fade = .8f;
			}

			/* We want the excitation for 2 pitch periods in order to look for a
			  decaying signal, but we can't get more than MAX_PERIOD. */
			int exc_length = pitch_index << 1;
			if( exc_length > MAX_PERIOD ) {
				exc_length = MAX_PERIOD;
			}

			final float[] etmp = new float[m_overlap];
			final float[] _exc = new float[MAX_PERIOD + LPC_ORDER];// FIXME why need size MAX_PERIOD + LPC_ORDER if _exc is never used?
			final float[] fir_tmp = new float[ exc_length ];
			final int exc = LPC_ORDER;// _exc + LPC_ORDER;// java: _exc[ exc ]
			final float[] window = celt_mode.window;
			c = 0;
			do {
				final int buf = decode_mem[c];// st_mem[ buf ]
				for( int i = exc - LPC_ORDER, ie = i + MAX_PERIOD, k = buf + DECODE_BUFFER_SIZE - MAX_PERIOD - LPC_ORDER; i < ie; i++ ) {
					_exc[i] = st_mem[k++];
				}

				if( iloss_count == 0 )
				{
					final float[] ac = new float[LPC_ORDER + 1];
					/* Compute LPC coefficients for the last MAX_PERIOD samples before
					   the first loss so we can work in the excitation - filter domain. */
					_celt_autocorr( _exc, exc, ac, window, m_overlap, LPC_ORDER, MAX_PERIOD );//, st.arch );
					/* Add a noise floor of  - 40 dB. */
/* #ifdef FIXED_POINT
					ac[0] += SHR32( ac[0],13 );
#else */
					ac[0] *= 1.0001f;
// #endif
					/* Use lag windowing to stabilize the Levinson - Durbin recursion. */
					for( int i = 1; i <= LPC_ORDER; i++ )
					{
						/* ac[i] *= exp(  - .5 * ( 2 * M_PI * .002 * i ) * ( 2 * M_PI * .002 * i ) ); */
/* #ifdef FIXED_POINT
						ac[i] -= MULT16_32_Q15( 2 * i * i, ac[i] );
#else */
						ac[i] -= ac[i] * (0.008f * 0.008f) * i * i;
// #endif
					}
					_celt_lpc( flpc, c * LPC_ORDER, ac, LPC_ORDER );
/* #ifdef FIXED_POINT
					// For fixed-point, apply bandwidth expansion until we can guarantee that
					//  no overflow can happen in the IIR filter. This means:
					//  32768*sum(abs(filter)) < 2^31
					while( 1 ) {
						opus_val16 tmp = Q15ONE;
						opus_val32 sum = QCONST16( 1., SIG_SHIFT );
						for( i = 0; i < LPC_ORDER; i++ )
							sum += ABS16(lpc[c * LPC_ORDER + i]);
						if( sum < 65535 ) break;
						for( i = 0; i < LPC_ORDER; i++ )
						{
							tmp = MULT16_16_Q15( QCONST16( .99f, 15 ), tmp );
							lpc[c * LPC_ORDER + i] = MULT16_16_Q15( lpc[c * LPC_ORDER + i], tmp );
						}
					}
#endif */
				}
				/* Initialize the LPC history with the samples just before the start
				   of the region for which we're computing the excitation. */
				{
					final int exc_shift = exc - exc_length + MAX_PERIOD;// java
					/* Compute the excitation for exc_length samples before the loss. We need the copy
					   because celt_fir() cannot filter in-place. */
					celt_fir( _exc, exc_shift,
							flpc, c * LPC_ORDER,
							fir_tmp, 0,
							exc_length, LPC_ORDER );//, st.arch );
		            System.arraycopy( fir_tmp, 0, _exc, exc_shift, exc_length );
				}

				/* Check if the waveform is decaying, and if so how fast.
				   We do this to avoid adding energy when concealing in a segment
				   with decaying energy. */
				final float decay;
				{
					float E1 = 1, E2 = 1;
/* #ifdef FIXED_POINT
					int shift = IMAX( 0,2 * celt_zlog2( celt_maxabs16( &exc[MAX_PERIOD - exc_length], exc_length ) ) - 20 );
#endif */
					final int decay_length = (exc_length >> 1);
					for( int i = exc + MAX_PERIOD - decay_length, ie = i + decay_length; i < ie; i++ )// java
					{
						float e = _exc[i];
						E1 += e * e;
						e = _exc[i - decay_length];
						E2 += e * e;
					}
					E1 = E1 <= E2 ? E1 : E2;
					decay = (float)Math.sqrt( (double)(E1 / E2) );
				}

				/* Move the decoder memory one frame to the left to give us room to
				   add the data for the new frame. We ignore the overlap that extends
				   past the end of the buffer, because we aren't going to use it. */
				System.arraycopy( st_mem, buf + N, st_mem, buf, DECODE_BUFFER_SIZE - N );

				/* Extrapolate from the end of the excitation with a period of
				   "pitch_index", scaling down each period by an additional factor of
				   "decay". */
				final int extrapolation_offset = MAX_PERIOD - pitch_index;
				/* We need to extrapolate enough samples to cover a complete MDCT
				   window ( including overlap/2 samples on both sides ). */
				final int extrapolation_len = N + m_overlap;
				/* We also apply fading if this is not the first loss. */
				float attenuation = fade * decay;
				float S1 = 0;
				final int buf_size_n = buf + DECODE_BUFFER_SIZE - N;// java
				final int buf_size_period = buf_size_n - MAX_PERIOD;// java
				for( int i = 0, j = extrapolation_offset, pi = j + pitch_index; i < extrapolation_len; i++, j++ )
				{
					if( j >= pi ) {// java
						j -= pitch_index;
						attenuation *= decay;
					}
					st_mem[buf_size_n + i] = attenuation * _exc[exc + j];
					/* Compute the energy of the previously decoded signal whose
					   excitation we're copying. */
					final float tmp = st_mem[buf_size_period + j];
					S1 += tmp * tmp;
				}
				{
					final float lpc_mem[] = new float[LPC_ORDER];
					/* Copy the last decoded samples ( prior to the overlap region ) to
					   synthesis filter memory so we can have a continuous signal. */
					for( int i = 0, j = buf_size_n - 1; i < LPC_ORDER; i++ ) {
						lpc_mem[i] = st_mem[j--];
					}
					/* Apply the synthesis filter to convert the excitation back into
					   the signal domain. */
					celt_iir( st_mem, buf_size_n,
							flpc, c * LPC_ORDER,
							st_mem, buf_size_n,
							extrapolation_len, LPC_ORDER,
							lpc_mem );//, st.arch );
/* #ifdef FIXED_POINT
					for( i = 0; i < extrapolation_len; i++ )
						buf[DECODE_BUFFER_SIZE-N+i] = SATURATE(buf[DECODE_BUFFER_SIZE-N+i], SIG_SAT);
#endif */
				}

				/* Check if the synthesis energy is higher than expected, which can
				   happen with the signal changes during our window. If so,
				   attenuate. */
				{
					float S2 = 0;
					for( int i = buf_size_n, ie = buf_size_n + extrapolation_len; i < ie; i++ )
					{
						final float tmp = st_mem[i];
						S2 += tmp * tmp;
					}
					/* This checks for an "explosion" in the synthesis. */
/* #ifdef FIXED_POINT
		if( !( S1 > SHR32( S2,2 ) ) )
#else */
					/* The float test is written this way to catch NaNs in the output
					   of the IIR filter at the same time. */
					if( !(S1 > 0.2f * S2) )
// #endif
					{
						for( int i = buf_size_n, ie = buf_size_n + extrapolation_len; i < ie; i++ ) {
							st_mem[i] = 0;
						}
					} else if( S1 < S2 )
					{
						final float ratio = (float)Math.sqrt( (double)((S1 + 1f) / (S2 + 1f)) );
						for( int i = 0, j = buf_size_n; i < m_overlap; i++ )
						{
							final float tmp_g = Jfloat_cast.Q15ONE - window[i] * (Jfloat_cast.Q15ONE - ratio);
							st_mem[j++] *= tmp_g;
						}
						for( int i = buf_size_n + m_overlap, ie = buf_size_n + extrapolation_len; i < ie; i++ )
						{
							st_mem[i] *= ratio;
						}
					}
				}

				/* Apply the pre - filter to the MDCT overlap for the next frame because
				   the post - filter will be re - applied in the decoder after the MDCT
				   overlap. */
				Jcelt.comb_filter( etmp, 0, st_mem, buf + DECODE_BUFFER_SIZE,
							this.postfilter_period, this.postfilter_period, m_overlap,
							-this.postfilter_gain, -this.postfilter_gain, this.postfilter_tapset, this.postfilter_tapset, null, 0 );//, st.arch );

				/* Simulate TDAC on the concealed audio so that it blends with the
				   MDCT of the next frame. */
				for( int i = 0, bi = buf + DECODE_BUFFER_SIZE, j = m_overlap - 1; i < overlap2; i++, j-- )
				{
					st_mem[bi++] = window[i] * etmp[j] + window[j] * etmp[i];
				}
			} while ( ++c < C );
		}

		this.loss_count = iloss_count + 1;

		//RESTORE_STACK;
	}

	// start quant_bands.c
	private static final void unquant_fine_energy(final int bands,// java replace CELTMode *m. FIXME why need CELTMode?
			final int start, final int end, final float[] oldEBands,
			final int[] fine_quant, final Jec_dec dec, int C)
	{
		/* Decode finer resolution */
		C *= bands;// java
		for( int i = start; i < end; i++ )
		{
			if( fine_quant[i] <= 0 ) {
				continue;
			}
			int c = 0;
			do {
				final int q2 = dec.ec_dec_bits( fine_quant[i] );
/* #ifdef FIXED_POINT
				offset = SUB16(SHR32(SHL32(EXTEND32(q2), DB_SHIFT) + QCONST16(.5f, DB_SHIFT), fine_quant[i]), QCONST16(.5f, DB_SHIFT));
#else */
				final float offset = (q2 + .5f) * (1 << (14 - fine_quant[i])) * (1.f / 16384f) - .5f;
// #endif
				oldEBands[i + c] += offset;
				c += bands;// java
			} while( c < C );
		}
	}

	private static final void unquant_energy_finalise(final int bands,// java replace CELTMode *m. FIXME why need CELTMode?
			final int start, final int end, final float[] oldEBands,
			final int[] fine_quant, final int[] fine_priority, int bits_left, final Jec_dec dec, final int C)
	{
		/* Use up the remaining bits */
		final int cb = C * bands;// java
		for( int prio = 0; prio < 2; prio++ )
		{
			for( int i = start; i < end && bits_left >= C; i++ )
			{
				if( fine_quant[i] >= JCELTMode.MAX_FINE_BITS || fine_priority[i] != prio ) {
					continue;
				}
				int c = 0;
				do {
					final int q2 = dec.ec_dec_bits( 1 );
/* #ifdef FIXED_POINT
					offset = SHR16(SHL16(q2, DB_SHIFT) - QCONST16(.5f, DB_SHIFT), fine_quant[i] + 1);
#else */
					final float offset = (q2 - .5f) * (1 << (14 - fine_quant[i] - 1)) * (1.f / 16384f);
// #endif
					oldEBands[i + c] += offset;
					bits_left--;
					c += bands;
				} while( c < cb );// java cb = C * bands
			}
		}
	}
	// end quant_bands.c

	/**
	 *
	 * @param data
	 * @param doffset
	 * @param len
	 * @param pcm
	 * @param pcmoffset
	 * @param frame_size
	 * @param dec
	 * @param accum
	 * @return audio size
	 */
	public final int celt_decode_with_ec(
			final byte[] data, final int doffset,// java
			final int len,
			final float[] pcm, final int pcmoffset,// java
			int frame_size, Jec_dec dec, final boolean accum )
	{
/* #ifdef NORM_ALIASING_HACK
		celt_norm  * X;
#else */
		// VARDECL( celt_norm, X );
// #endif

		final int CC = this.channels;
		final int C = this.stream_channels;
		// ALLOC_STACK;

		// VALIDATE_CELT_DECODER( st );
		// final JOpusCustomMode mode = st.mode;
		final JCELTMode celt_mode = (JCELTMode)this.mode;// java local vars renamed
		final int nbEBands = celt_mode.nbEBands;
		final int ioverlap = celt_mode.overlap;
		final short[] eBands = celt_mode.eBands;
		final int istart = this.start;
		final int iend = this.end;
		frame_size *= this.downsample;
		final float[] st_mem = this._decode_mem;// java
		final float[] oldBandE = this.oldEBands;// lpc + CC * Jcelt_lpc.LPC_ORDER;
		final float[] old_LogE = this.oldLogE;// oldBandE + 2 * nbEBands;
		final float[] old_LogE2 = this.oldLogE2;// oldLogE + 2 * nbEBands;
		final float[] background_LogE = this.backgroundLogE;// oldLogE2 + 2 * nbEBands;

		int LM;
/* #ifdef CUSTOM_MODES
		if( st.signalling && data != NULL )
		{
			int data0 = data[0];
			// Convert "standard mode" to Opus header
			if( mode.Fs == 48000 && mode.shortMdctSize == 120 )
			{
				data0 = fromOpus( data0 );
				if( data0 < 0 )
					return OPUS_INVALID_PACKET;
			}
			st.end = end = IMAX( 1, mode.effEBands - 2 * ( data0 >> 5 ) );
			LM = ( data0 >> 3 ) & 0x3;
			C = 1 + ( ( data0 >> 2 ) & 0x1 );
			data++;
			len--;
			if( LM > mode.maxLM )
				return OPUS_INVALID_PACKET;
			if( frame_size < mode.shortMdctSize << LM )
				return OPUS_BUFFER_TOO_SMALL;
			else
				frame_size = mode.shortMdctSize << LM;
		} else {
#else */
		{
//#endif
			for( LM = 0; LM <= celt_mode.maxLM; LM++ ) {
				if( celt_mode.shortMdctSize << LM == frame_size ) {
					break;
				}
			}
			if( LM > celt_mode.maxLM ) {
				return Jopus_defines.OPUS_BAD_ARG;
			}
		}
		final int M = 1 << LM;

		if( len < 0 || len > 1275 || pcm == null ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		final int decode_mem[] = new int[2];// st._decode_mem[ decode_mem[c] ]
		final int out_syn[] = new int[2];// st._decode_mem[ out_syn[c] ]
		final int N = M * celt_mode.shortMdctSize;
		int c = 0;
		do {
			final int i = c * (DECODE_BUFFER_SIZE + ioverlap);
			decode_mem[c] = i;// st._decode_mem[ decode_mem[c] ]
			out_syn[c] = i + DECODE_BUFFER_SIZE - N;// st._decode_mem[ out_syn[c] ]
		} while ( ++c < CC );

		int effEnd = iend;
		if( effEnd > celt_mode.effEBands ) {
			effEnd = celt_mode.effEBands;
		}

		if( data == null || len <= 1 )
		{
			celt_decode_lost( N, LM );
			deemphasis( st_mem, out_syn, pcm, pcmoffset, N, CC, this.downsample, celt_mode.preemph, this.preemph_memD, accum );
			// RESTORE_STACK;
			return frame_size / this.downsample;
		}
		/* Check if there are at least two packets received consecutively before
		 * turning on the pitch-based PLC */
		this.skip_plc = this.loss_count != 0;

		if( dec == null )
		{
			final Jec_dec _dec = new Jec_dec();
			_dec.ec_dec_init( data, doffset, len );
			dec = _dec;
		}

		if( C == 1 )
		{
			for( int i = 0; i < nbEBands; i++ ) {
				final float v1 = oldBandE[ i ];// java
				final float v2 = oldBandE[ nbEBands + i ];// java
				oldBandE[ i ] = v1 >= v2 ? v1 : v2;
			}
		}

		int total_bits = len << 3;
		int tell = dec.ec_tell();

		boolean silence;
		if( tell >= total_bits ) {
			silence = true;
		} else if( tell == 1 ) {
			silence = dec.ec_dec_bit_logp( 15 );
		} else {
			silence = false;
		}
		if( silence )
		{
			/* Pretend we've read all the remaining bits */
			tell = len << 3;
			dec.nbits_total += tell - dec.ec_tell();
		}

		float fpostfilter_gain = 0f;
		int postfilter_pitch = 0;
		int ipostfilter_tapset = 0;
		if( istart == 0 && tell + 16 <= total_bits )
		{
			if( dec.ec_dec_bit_logp( 1 ) )
			{
				final int octave = dec.ec_dec_uint( 6 );
				postfilter_pitch = (16 << octave) + dec.ec_dec_bits( 4 + octave ) - 1;
				final int qg = dec.ec_dec_bits( 3 );
				if( dec.ec_tell() + 2 <= total_bits ) {
					ipostfilter_tapset = dec.ec_dec_icdf( Jcelt.tapset_icdf, 0, 2 );
				}
				fpostfilter_gain = .09375f * (float)(qg + 1);
			}
			tell = dec.ec_tell();
		}

		boolean isTransient;
		if( LM > 0 && tell + 3 <= total_bits )
		{
			isTransient = dec.ec_dec_bit_logp( 3 );
			tell = dec.ec_tell();
		} else {
			isTransient = false;
		}

		final int shortBlocks = isTransient ? M : 0;

		/* Decode the global flags ( first symbols in the stream ) */
		final boolean intra_ener = tell + 3 <= total_bits ? dec.ec_dec_bit_logp( 3 ) : false;
		/* Get band energies */
		celt_mode.unquant_coarse_energy( istart, iend, oldBandE, intra_ener, dec, C, LM );

		final int tf_res[] = new int[nbEBands];
		tf_decode( istart, iend, isTransient, tf_res, LM, dec );

		tell = dec.ec_tell();
		int spread_decision = JCELTMode.SPREAD_NORMAL;
		if( tell + 4 <= total_bits ) {
			spread_decision = dec.ec_dec_icdf( Jcelt.spread_icdf, 0, 5 );
		}

		final int cap[] = new int[nbEBands];

		celt_mode.init_caps( cap, LM, C );

		final int offsets[] = new int[nbEBands];

		int dynalloc_logp = 6;
		total_bits <<= Jec_ctx.BITRES;
		tell = dec.ec_tell_frac();
		for( int i = istart; i < iend; i++ )
		{
			int width = C * (eBands[i + 1] - eBands[i]) << LM;
			/* quanta is 6 bits, but no more than 1 bit/sample
			   and no less than 1/8 bit/sample */
			int quanta = (6 << Jec_ctx.BITRES) > width ? 6 << Jec_ctx.BITRES : width;
			width <<= Jec_ctx.BITRES;
			quanta = width < quanta ? width : quanta;
			int dynalloc_loop_logp = dynalloc_logp;
			int boost = 0;
			while( tell + (dynalloc_loop_logp << Jec_ctx.BITRES) < total_bits && boost < cap[i] )
			{
				final boolean flag = dec.ec_dec_bit_logp( dynalloc_loop_logp );
				tell = dec.ec_tell_frac();
				if( ! flag ) {
					break;
				}
				boost += quanta;
				total_bits -= quanta;
				dynalloc_loop_logp = 1;
			}
			offsets[i] = boost;
			/* Making dynalloc more likely */
			if( boost > 0 ) {
				dynalloc_logp--;
				dynalloc_logp = 2 > dynalloc_logp ? 2 : dynalloc_logp;
			}
		}

		final int fine_quant[] = new int[nbEBands];
		final int alloc_trim = tell + (6 << Jec_ctx.BITRES) <= total_bits ? dec.ec_dec_icdf( Jcelt.trim_icdf, 0, 7 ) : 5;

		int bits = ((len << 3) << Jec_ctx.BITRES) - dec.ec_tell_frac() - 1;
		final int anti_collapse_rsv = isTransient && LM >= 2 && bits >= ((LM + 2) << Jec_ctx.BITRES) ? (1 << Jec_ctx.BITRES) : 0;
		bits -= anti_collapse_rsv;

		final int pulses[] = new int[nbEBands];
		final int fine_priority[] = new int[nbEBands];

		// final int intensity = 0;
		// final boolean dual_stereo = false;
		// final int balance;
		final Jallocation_aux aux = new Jallocation_aux( 0, false );
		final int codedBands = celt_mode.clt_compute_allocation( istart, iend, offsets, cap, alloc_trim,
					// &intensity, &dual_stereo,
					aux,// java
					bits,
					// &balance,
					pulses,
					fine_quant, fine_priority, C, LM, dec, false, 0, 0 );
		// final int intensity = aux.intensity;// java
		// final boolean dual_stereo = aux.dual_stereo;// java
		// final int balance = aux.balance;// java

		unquant_fine_energy( celt_mode.nbEBands /* celt_mode */, istart, iend, oldBandE, fine_quant, dec, C );

		c = 0;
		do {
			System.arraycopy( st_mem, decode_mem[c] + N, st_mem, decode_mem[c], DECODE_BUFFER_SIZE - N + (ioverlap >> 1) );
		} while ( ++c < CC );

		/* Decode fixed codebook */
		final byte collapse_masks[] = new byte[C * nbEBands];

//#ifdef NORM_ALIASING_HACK
		/* This is an ugly hack that breaks aliasing rules and would be easily broken,
		   but it saves almost 4kB of stack. */
//		X = ( celt_norm *  )( out_syn[CC - 1] + overlap/2 );
//#else
		final float X[] = new float[C * N];   /** < Interleaved normalised MDCTs */
//#endif

		this.rng = celt_mode.quant_all_bands( false, istart, iend, X, C == 2 ? X : null, N, collapse_masks,
					null, pulses, shortBlocks, spread_decision, aux.mDualStereo, aux.mIntensity, tf_res,
					((len << 3) << Jec_ctx.BITRES) - anti_collapse_rsv, aux.mBalance, dec, LM, codedBands, this.rng, 0,
					/*st.arch, */ this.disable_inv );

		int anti_collapse_on = 0;
		if( anti_collapse_rsv > 0 )
		{
			anti_collapse_on = dec.ec_dec_bits( 1 );
		}

		unquant_energy_finalise( celt_mode.nbEBands /* celt_mode */, istart, iend, oldBandE,
					fine_quant, fine_priority, (len << 3) - dec.ec_tell(), dec, C );

		if( 0 != anti_collapse_on ) {
			celt_mode.anti_collapse( X, collapse_masks, LM, C, N,
					istart, iend, oldBandE, old_LogE, old_LogE2, pulses, this.rng );//, st.arch );
		}

		if( silence )
		{
			for( int i = 0, ie = C * nbEBands; i < ie; i++ ) {
				oldBandE[i] = -28.f;
			}
		}

		celt_mode.celt_synthesis( X, st_mem, out_syn, oldBandE, istart, effEnd,
					C, CC, isTransient, LM, this.downsample, silence );//, st.arch );

		c = 0;
		do {
			this.postfilter_period = this.postfilter_period > Jcelt.COMBFILTER_MINPERIOD ? this.postfilter_period : Jcelt.COMBFILTER_MINPERIOD;
			this.postfilter_period_old = this.postfilter_period_old > Jcelt.COMBFILTER_MINPERIOD ? this.postfilter_period_old : Jcelt.COMBFILTER_MINPERIOD;
			Jcelt.comb_filter( st_mem, out_syn[c], st_mem, out_syn[c], this.postfilter_period_old, this.postfilter_period, celt_mode.shortMdctSize,
						this.postfilter_gain_old, this.postfilter_gain, this.postfilter_tapset_old, this.postfilter_tapset,
						celt_mode.window, ioverlap );//, st.arch );
			if( LM != 0 ) {
				Jcelt.comb_filter( st_mem, out_syn[c] + celt_mode.shortMdctSize, st_mem, out_syn[c] + celt_mode.shortMdctSize, this.postfilter_period, postfilter_pitch, N - celt_mode.shortMdctSize,
							this.postfilter_gain, fpostfilter_gain, this.postfilter_tapset, ipostfilter_tapset,
							celt_mode.window, ioverlap );//, st.arch );
			}

		} while ( ++c < CC );
		this.postfilter_period_old = this.postfilter_period;
		this.postfilter_gain_old = this.postfilter_gain;
		this.postfilter_tapset_old = this.postfilter_tapset;
		this.postfilter_period = postfilter_pitch;
		this.postfilter_gain = fpostfilter_gain;
		this.postfilter_tapset = ipostfilter_tapset;
		if( LM != 0 )
		{
			this.postfilter_period_old = this.postfilter_period;
			this.postfilter_gain_old = this.postfilter_gain;
			this.postfilter_tapset_old = this.postfilter_tapset;
		}

		if( C == 1 ) {
			System.arraycopy( oldBandE, 0, oldBandE, nbEBands, nbEBands );
		}

		/* In case start or end were to change */
		if( ! isTransient )
		{
			final int count = nbEBands << 1;
			System.arraycopy( old_LogE, 0, old_LogE2, 0, count );
			System.arraycopy( oldBandE, 0, old_LogE, 0, count );
			/* In normal circumstances, we only allow the noise floor to increase by
			   up to 2.4 dB/second, but when we're in DTX, we allow up to 6 dB
			   increase for each update. */
			float max_background_increase;
			if( this.loss_count < 10 ) {
				max_background_increase = (float)M * 0.001f;
			} else {
				max_background_increase = 1.f;
			}
			for( int i = 0; i < count; i++ ) {
				final float v1 = background_LogE[ i ] + max_background_increase;// java
				final float v2 = oldBandE[ i ];// java
				background_LogE[ i ] = (v1 <= v2 ? v1 : v2);
			}
		} else {
			for( int i = 0, ie = nbEBands << 1; i < ie; i++ ) {
				final float v1 = old_LogE[ i ];// java
				final float v2 = oldBandE[ i ];// java
				old_LogE[ i ] = (v1 <= v2 ? v1 : v2);
			}
		}
		c = 0;
		final int count = nbEBands << 1;// java
		do
		{
			for( int i = c, ie = c + istart; i < ie; i++ )
			{
				oldBandE[ i ] = 0;
				old_LogE[ i ] = old_LogE2[ i ] = -28.f;
			}
			for( int i = c + iend, ie = c + nbEBands; i < ie; i++ )
			{
				oldBandE[ i ] = 0;
				old_LogE[ i ] = old_LogE2[ i ] = -28.f;
			}
			c += nbEBands;
		} while ( c < count );
		this.rng = (int)dec.rng;

		deemphasis( st_mem, out_syn, pcm, pcmoffset, N, CC, this.downsample, celt_mode.preemph, this.preemph_memD, accum );
		this.loss_count = 0;
		// RESTORE_STACK;
		if( dec.ec_tell() > (len << 3) ) {
			return Jopus_defines.OPUS_INTERNAL_ERROR;
		}
		if( 0 != dec.ec_get_error() ) {
			this.error = 1;
		}
		return frame_size / this.downsample;
	}


/*#ifdef CUSTOM_MODES

#ifdef FIXED_POINT
	private static final int opus_custom_decode(JCELTDecoder st, const unsigned char  * data, int len, short[] pcm, int frame_size )
	{
		return celt_decode_with_ec( st, data, len, pcm, frame_size, NULL, 0 );
	}

#ifndef DISABLE_FLOAT_API
	private static final int opus_custom_decode_float(JCELTDecoder st, const unsigned char  * data, int len, float[] pcm, int frame_size )
	{
		int j, ret, C, N;
		VARDECL( opus_int16, out );
		//ALLOC_STACK;

		if( pcm == NULL )
			return OPUS_BAD_ARG;

		C = st.channels;
		N = frame_size;

		ALLOC( out, C * N, opus_int16 );
		ret = celt_decode_with_ec( st, data, len, out, frame_size, NULL, 0 );
		if( ret > 0 )
			for( j = 0;j < C * ret;j++ )
				pcm[j] = out[j] * ( 1.f/32768.f );

		//RESTORE_STACK;
		return ret;
	}
#endif */ /* DISABLE_FLOAT_API */

/*#else

	private static final int opus_custom_decode_float(final JCELTDecoder st, const unsigned char  * data, int len, float[] pcm, int frame_size )
	{
		return celt_decode_with_ec( st, data, len, pcm, frame_size, NULL, 0 );
	}

	private static final int opus_custom_decode(JCELTDecoder st, const unsigned char  * data, int len, short[] pcm, int frame_size )
	{
		int j, ret, C, N;
		VARDECL( celt_sig, out );
		//ALLOC_STACK;

		if( pcm == NULL )
			return OPUS_BAD_ARG;

		C = st.channels;
		N = frame_size;
		ALLOC( out, C * N, celt_sig );

		ret = celt_decode_with_ec( st, data, len, out, frame_size, NULL, 0 );

		if( ret > 0 )
			for( j = 0;j < C * ret;j++ )
				pcm[j] = FLOAT2INT16 ( out[j] );

		//RESTORE_STACK;
		return ret;
	}

#endif
#endif */ /* CUSTOM_MODES */

	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	// #define celt_decoder_ctl opus_custom_decoder_ctl
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_decoder_ctl(final int request, final Object[] arg) {
		return opus_custom_decoder_ctl( request, arg );
	}
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_decoder_ctl(final int request, final int arg) {
		return opus_custom_decoder_ctl( request, arg );
	}
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return status
	 */
	public final int celt_decoder_ctl(final int request, final boolean arg) {
		return opus_custom_decoder_ctl( request, arg );
	}
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return status
	 */
	public final int celt_decoder_ctl(final int request) {
		return opus_custom_decoder_ctl( request );
	}
	// end celt_decoder.c
}
