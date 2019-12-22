package silk;

import celt.Jec_dec;

// structs.h

/** Decoder state */
final class Jsilk_decoder_state extends Jdec_API {
	/** Decoder control */
	private static final class Jsilk_decoder_control {
		/* Prediction and coding parameters */
		private final int   pitchL[] = new int[ Jdefine.MAX_NB_SUBFR ];
		private final int   Gains_Q16[] = new int[ Jdefine.MAX_NB_SUBFR ];
		/* Holds interpolated and final coefficients, 4-byte aligned */
		private final short PredCoef_Q12[][] = new short[ 2 ][ Jdefine.MAX_LPC_ORDER ];
		private final short LTPCoef_Q14[] = new short[ Jdefine.LTP_ORDER * Jdefine.MAX_NB_SUBFR ];
		private int         LTP_scale_Q14;
	}
	private int            prev_gain_Q16;
	private final int      exc_Q14[] = new int[ Jdefine.MAX_FRAME_LENGTH ];
	final int              sLPC_Q14_buf[] = new int[ Jdefine.MAX_LPC_ORDER ];
	/** Buffer for output signal */
	final short            outBuf[] = new short[ Jdefine.MAX_FRAME_LENGTH + 2 * Jdefine.MAX_SUB_FRAME_LENGTH ];
	/** Previous Lag */
	int                    lagPrev;
	/** Previous gain index */
	byte                   LastGainIndex;
	/** Sampling frequency in kHz */
	int                    fs_kHz;
	/** API sample frequency (Hz) */
	private int            fs_API_hz;
	/** Number of 5 ms subframes in a frame */
	int                    nb_subfr;
	/** Frame length (samples) */
	int                    frame_length;
	/** Subframe length (samples) */
	private int            subfr_length;
	/** Length of LTP memory */
	private int            ltp_mem_length;
	/** LPC order */
	private int            LPC_order;
	/** Used to interpolate LSFs */
	private final short    prevNLSF_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
	/** Flag for deactivating NLSF interpolation */
	boolean                first_frame_after_reset;
	/** Pointer to iCDF table for low bits of pitch lag index */
	private char[]         pitch_lag_low_bits_iCDF;
	/** Pointer to iCDF table for pitch contour index */
	private char[]         pitch_contour_iCDF;

	/* For buffering payload in case of more frames per packet */
	int                    nFramesDecoded;
	int                    nFramesPerPacket;

	/* Specifically for entropy coding */
	private int            ec_prevSignalType;
	private short          ec_prevLagIndex;

	final boolean VAD_flags[] = new boolean[ Jdefine.MAX_FRAMES_PER_PACKET ];
	boolean       LBRR_flag;
	final boolean LBRR_flags[] = new boolean[ Jdefine.MAX_FRAMES_PER_PACKET ];

	final Jsilk_resampler_state_struct resampler_state = new Jsilk_resampler_state_struct();

	/** Pointer to NLSF codebook */
	private Jsilk_NLSF_CB_struct psNLSF_CB;

	/** Quantization indices */
	final JSideInfoIndices indices = new JSideInfoIndices();

	/** CNG state */
	private final Jsilk_CNG_struct sCNG = new Jsilk_CNG_struct();

	/* Stuff used for PLC */
	private int            lossCnt;
	int                    prevSignalType;
	// int                    arch;// java unused

	private final Jsilk_PLC_struct sPLC = new Jsilk_PLC_struct();

	private final void clear() {
		prev_gain_Q16 = 0;
		//
		int[] ibuff = exc_Q14;
		int i = Jdefine.MAX_FRAME_LENGTH;
		do {
			ibuff[--i] = 0;
		} while( i > 0 );
		//
		ibuff = sLPC_Q14_buf;
		i = Jdefine.MAX_LPC_ORDER;
		do {
			ibuff[--i] = 0;
		} while( i > 0 );
		//
		short[] sbuff = outBuf;
		i = Jdefine.MAX_FRAME_LENGTH + 2 * Jdefine.MAX_SUB_FRAME_LENGTH;
		do {
			sbuff[--i] = 0;
		} while( i > 0 );
		//
		lagPrev = 0;
		LastGainIndex = 0;
		fs_kHz = 0;
		fs_API_hz = 0;
		nb_subfr = 0;
		frame_length = 0;
		subfr_length = 0;
		ltp_mem_length = 0;
		LPC_order = 0;
		//
		sbuff = prevNLSF_Q15;
		i = Jdefine.MAX_LPC_ORDER;
		do {
			sbuff[--i] = 0;
		} while( i > 0 );
		first_frame_after_reset = false;
		pitch_lag_low_bits_iCDF = null;
		pitch_contour_iCDF = null;
		nFramesDecoded = 0;
		nFramesPerPacket = 0;
		ec_prevSignalType = 0;
		ec_prevLagIndex = 0;
		//
		boolean[] bbuff = VAD_flags;
		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			bbuff[--i] = false;
		} while( i > 0 );
		//
		LBRR_flag = false;
		//
		bbuff = LBRR_flags;
		i = Jdefine.MAX_FRAMES_PER_PACKET;
		do {
			bbuff[--i] = false;
		} while( i > 0 );
		//
		resampler_state.clear();
		psNLSF_CB = null;
		indices.clear();
		sCNG.clear();
		lossCnt = 0;
		prevSignalType = 0;
		sPLC.clear();
	}
	final void copyFrom(final Jsilk_decoder_state d) {
		prev_gain_Q16 = d.prev_gain_Q16;
		//
		System.arraycopy( d.exc_Q14, 0, exc_Q14, 0, Jdefine.MAX_FRAME_LENGTH );
		System.arraycopy( d.sLPC_Q14_buf, 0, sLPC_Q14_buf, 0, Jdefine.MAX_LPC_ORDER );
		System.arraycopy( d.outBuf, 0, outBuf, 0, Jdefine.MAX_FRAME_LENGTH + 2 * Jdefine.MAX_SUB_FRAME_LENGTH );
		//
		lagPrev = d.lagPrev;
		LastGainIndex = d.LastGainIndex;
		fs_kHz = d.fs_kHz;
		fs_API_hz = d.fs_API_hz;
		nb_subfr = d.nb_subfr;
		frame_length = d.frame_length;
		subfr_length = d.subfr_length;
		ltp_mem_length = d.ltp_mem_length;
		LPC_order = d.LPC_order;
		//
		System.arraycopy( d.prevNLSF_Q15, 0, prevNLSF_Q15, 0, Jdefine.MAX_LPC_ORDER );
		first_frame_after_reset = d.first_frame_after_reset;
		pitch_lag_low_bits_iCDF = d.pitch_lag_low_bits_iCDF;
		pitch_contour_iCDF = d.pitch_contour_iCDF;
		nFramesDecoded = d.nFramesDecoded;
		nFramesPerPacket = d.nFramesPerPacket;
		ec_prevSignalType = d.ec_prevSignalType;
		ec_prevLagIndex = d.ec_prevLagIndex;
		//
		System.arraycopy( d.VAD_flags, 0, VAD_flags, 0, Jdefine.MAX_FRAMES_PER_PACKET );
		//
		LBRR_flag = false;
		//
		System.arraycopy( d.LBRR_flags, 0, LBRR_flags, 0, Jdefine.MAX_FRAMES_PER_PACKET );
		//
		resampler_state.copyFrom( d.resampler_state );
		psNLSF_CB = d.psNLSF_CB;
		indices.copyFrom( d.indices );
		sCNG.copyFrom( d.sCNG );
		lossCnt = d.lossCnt;
		prevSignalType = d.prevSignalType;
		sPLC.copyFrom( d.sPLC );
	}

	// start CNG.c
	/**
	 *
	 * @param psDec I/O  Decoder state
	 */
	private final void silk_CNG_Reset()
	{
		final int NLSF_step_Q15 = Short.MAX_VALUE / (this.LPC_order + 1);
		int NLSF_acc_Q15 = 0;
		final short[] sb = this.sCNG.CNG_smth_NLSF_Q15;// java
		for( int i = 0, n = this.LPC_order; i < n; i++ ) {
			NLSF_acc_Q15 += NLSF_step_Q15;
			sb[ i ] = (short)NLSF_acc_Q15;
		}
		this.sCNG.CNG_smth_Gain_Q16 = 0;
		this.sCNG.rand_seed = 3176576;
	}

	/**
	 * Generates excitation for CNG LPC synthesis
	 *
	 * java changed: rand_seed is returned
	 *
	 * @param exc_Q14 O    CNG excitation signal Q10
	 * @param exc_buf_Q14 I    Random samples buffer Q10
	 * @param length I    Length
	 * @param rand_seed I/O  Seed to random index generator
	 * @return new rand_seed
	 */
	private static final int silk_CNG_exc(final int exc_Q14[], int eoffset,// java
			final int exc_buf_Q14[], int length, final int rand_seed)
	{
		int exc_mask = Jdefine.CNG_BUF_MASK_MAX;
		while( exc_mask > length ) {
			exc_mask = exc_mask >> 1;
		}

		int seed = rand_seed;// *rand_seed
		length += eoffset;// java
		for( ; eoffset < length; eoffset++ ) {
			seed = silk_RAND( seed );
			final int idx = (int)( (seed >> 24) & exc_mask );
			// silk_assert( idx >= 0 );
			// silk_assert( idx <= Jdefine.CNG_BUF_MASK_MAX );
			exc_Q14[ eoffset ] = exc_buf_Q14[ idx ];
		}
		//*rand_seed = seed;
		return seed;
	}

	/**
	 * Updates CNG estimate, and applies the CNG when packet was lost
	 *
	 * @param psDec I/O  Decoder state
	 * @param psDecCtrl I/O  Decoder control
	 * @param frame I/O  Signal
	 * @param length I    Length of residual
	 */
	final void silk_CNG(final Jsilk_decoder_control psDecCtrl,
			final short frame[], int foffset,// java
			final int length)
	{
		final short A_Q12[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final Jsilk_CNG_struct psCNG = this.sCNG;
		// SAVE_STACK;

		if( this.fs_kHz != psCNG.fs_kHz ) {
			/* Reset state */
			silk_CNG_Reset();

			psCNG.fs_kHz = this.fs_kHz;
		}
		if( this.lossCnt == 0 && this.prevSignalType == Jdefine.TYPE_NO_VOICE_ACTIVITY ) {
			/* Update CNG parameters */

			/* Smoothing of LSF's  */
			final short[] CNG_smth_NLSF_Q15 = psCNG.CNG_smth_NLSF_Q15;// java
			final short[] prev_NLSF_Q15 = this.prevNLSF_Q15;// java
			for( int i = 0; i < this.LPC_order; i++ ) {
				CNG_smth_NLSF_Q15[ i ] += ((int)((((int)prev_NLSF_Q15[ i ] - (int)CNG_smth_NLSF_Q15[ i ]) * (long)Jdefine.CNG_NLSF_SMTH_Q16) >> 16));
			}
			/* Find the subframe with the highest gain */
			int max_Gain_Q16 = 0;
			int subfr        = 0;
			final int this_nb_subfr = this.nb_subfr;// java
			final int[] Gains_Q16 = psDecCtrl.Gains_Q16;// java
			for( int i = 0; i < this_nb_subfr; i++ ) {
				if( Gains_Q16[ i ] > max_Gain_Q16 ) {
					max_Gain_Q16 = Gains_Q16[ i ];
					subfr        = i;
				}
			}
			/* Update CNG excitation buffer with excitation from this subframe */
			System.arraycopy( psCNG.CNG_exc_buf_Q14, 0, psCNG.CNG_exc_buf_Q14, this.subfr_length, (this.nb_subfr - 1) * this.subfr_length );
			System.arraycopy( this.exc_Q14, subfr * this.subfr_length, psCNG.CNG_exc_buf_Q14, 0, this.subfr_length );

			/* Smooth gains */
			for( int i = 0; i < this_nb_subfr; i++ ) {
				psCNG.CNG_smth_Gain_Q16 += (int)(((Gains_Q16[ i ] - psCNG.CNG_smth_Gain_Q16) * (long)Jdefine.CNG_GAIN_SMTH_Q16) >> 16);
			}
		}

		/* Add CNG when packet is lost or during DTX */
		if( 0 != this.lossCnt ) {
			final int CNG_sig_Q14[] = new int[length + Jdefine.MAX_LPC_ORDER];

			/* Generate CNG excitation */
			int gain_Q16 = (int)(((long)this.sPLC.randScale_Q14 * this.sPLC.prevGain_Q16[1]) >> 16);
			if( gain_Q16 >= (1 << 21) || psCNG.CNG_smth_Gain_Q16 > (1 << 23) ) {
				gain_Q16 = (gain_Q16 >> 16) * (gain_Q16 >> 16);
				gain_Q16 = ((psCNG.CNG_smth_Gain_Q16 >> 16) * (psCNG.CNG_smth_Gain_Q16 >> 16)) - (gain_Q16 << 5);
				gain_Q16 = silk_SQRT_APPROX( gain_Q16 ) << 16;
			} else {
				gain_Q16 = (int)(((long)gain_Q16 * gain_Q16) >> 16);
				gain_Q16 = (int)(((long)psCNG.CNG_smth_Gain_Q16 * psCNG.CNG_smth_Gain_Q16) >> 16) - (gain_Q16 << 5);
				gain_Q16 = silk_SQRT_APPROX( gain_Q16 ) << 8;
			}
			final int gain_Q10 = gain_Q16 >> 6;

			psCNG.rand_seed = silk_CNG_exc( CNG_sig_Q14, Jdefine.MAX_LPC_ORDER, psCNG.CNG_exc_buf_Q14, length, psCNG.rand_seed );

			/* Convert CNG NLSF to filter representation */
			silk_NLSF2A( A_Q12, psCNG.CNG_smth_NLSF_Q15, this.LPC_order );// this.arch );

			/* Generate CNG signal, by synthesis filtering */
			System.arraycopy( psCNG.CNG_synth_state, 0, CNG_sig_Q14, 0, Jdefine.MAX_LPC_ORDER );
			// celt_assert( psDec->LPC_order == 10 || psDec->LPC_order == 16 );
			// java
			final long A_Q12_0 = (long)A_Q12[ 0 ];
			final long A_Q12_1 = (long)A_Q12[ 1 ];
			final long A_Q12_2 = (long)A_Q12[ 2 ];
			final long A_Q12_3 = (long)A_Q12[ 3 ];
			final long A_Q12_4 = (long)A_Q12[ 4 ];
			final long A_Q12_5 = (long)A_Q12[ 5 ];
			final long A_Q12_6 = (long)A_Q12[ 6 ];
			final long A_Q12_7 = (long)A_Q12[ 7 ];
			final long A_Q12_8 = (long)A_Q12[ 8 ];
			final long A_Q12_9 = (long)A_Q12[ 9 ];
			final long A_Q12_10 = (long)A_Q12[ 10 ];
			final long A_Q12_11 = (long)A_Q12[ 11 ];
			final long A_Q12_12 = (long)A_Q12[ 12 ];
			final long A_Q12_13 = (long)A_Q12[ 13 ];
			final long A_Q12_14 = (long)A_Q12[ 14 ];
			final long A_Q12_15 = (long)A_Q12[ 15 ];
			for( int i = Jdefine.MAX_LPC_ORDER, ie = i + length; i < ie; i++ ) {
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				int si = i;// java
				int LPC_pred_Q10 = this.LPC_order >> 1;
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_0 ) >> 16);// - 1
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_1 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_2 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_3 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_4 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_5 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_6 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_7 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_8 ) >> 16);
				LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_9 ) >> 16);// - 10
				if( this.LPC_order == 16 ) {
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_10 ) >> 16);// - 11
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_11 ) >> 16);
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_12 ) >> 16);
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_13 ) >> 16);
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_14 ) >> 16);
					LPC_pred_Q10 += (int)(( CNG_sig_Q14[ --si ] * A_Q12_15 ) >> 16);// - 16
				}

				/* Update states */
				LPC_pred_Q10 = ((LPC_pred_Q10 > (Integer.MAX_VALUE >> 4) ? (Integer.MAX_VALUE >> 4) : (LPC_pred_Q10 < (Integer.MIN_VALUE >> 4) ? (Integer.MIN_VALUE >> 4) : LPC_pred_Q10)) << 4);
				int v = CNG_sig_Q14[ i ];
				CNG_sig_Q14[ i ] = (((v + LPC_pred_Q10) & 0x80000000) == 0 ?
						(((v & LPC_pred_Q10) & 0x80000000) != 0 ? Integer.MIN_VALUE : v + LPC_pred_Q10) :
							(((v | LPC_pred_Q10) & 0x80000000) == 0 ? Integer.MAX_VALUE : v + LPC_pred_Q10) );

				/* Scale with Gain and add to input signal */
				v = JSigProc_FIX.silk_RSHIFT_ROUND( (int)(((long)CNG_sig_Q14[ i ] * gain_Q10) >> 16), 8 );// java
				v = (v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
				v += frame[ foffset ];
				frame[ foffset++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			}
			System.arraycopy( CNG_sig_Q14, length, psCNG.CNG_synth_state, 0, Jdefine.MAX_LPC_ORDER );
			return;// java
		}// else {
			// silk_memset( psCNG.CNG_synth_state, 0, psDec.LPC_order *  sizeof( opus_int32 ) );
			final int[] state = psCNG.CNG_synth_state;// java
			for( int i = 0, ie = this.LPC_order; i < ie; i++ ) {
				state[i] = 0;
			}
		//}
		// RESTORE_STACK;
	}
	// end CNG.c

	// start bwexpander.c
	/**
	 * Chirp (bandwidth expand) LP AR filter
	 *
	 * @param ar I/O  AR filter to be expanded (without leading 1)
	 * @param d I    Length of ar
	 * @param chirp_Q16 I    Chirp factor (typically in the range 0 to 1)
	 */
	private static final void silk_bwexpander(final short[] ar, int d, int chirp_Q16)
	{
		final int chirp_minus_one_Q16 = chirp_Q16 - 65536;

		/* NB: Dont use silk_SMULWB, instead of silk_RSHIFT_ROUND( silk_MUL(), 16 ), below.  */
		/* Bias in silk_SMULWB can lead to unstable filters                                */
		d--;// java
		for( int i = 0; i < d ; i++ ) {
			ar[ i ]    = (short)JSigProc_FIX.silk_RSHIFT_ROUND( chirp_Q16 * ar[ i ], 16 );
			chirp_Q16 += JSigProc_FIX.silk_RSHIFT_ROUND( chirp_Q16 * chirp_minus_one_Q16, 16 );
		}
		ar[ d ] = (short)JSigProc_FIX.silk_RSHIFT_ROUND( chirp_Q16 * ar[ d ], 16 );
	}
	// end bwexpander.c

	// PLC.h

	private static final double BWE_COEF                  = 0.99;
	private static final int V_PITCH_GAIN_START_MIN_Q14   = 11469;               /* 0.7 in Q14               */
	private static final int V_PITCH_GAIN_START_MAX_Q14   = 15565;               /* 0.95 in Q14              */
	private static final int MAX_PITCH_LAG_MS             = 18;
	private static final int RAND_BUF_SIZE                = 128;
	private static final int RAND_BUF_MASK                = ( RAND_BUF_SIZE - 1 );
	private static final int LOG2_INV_LPC_GAIN_HIGH_THRES = 3;                   /* 2^3 = 8 dB LPC gain      */
	private static final int LOG2_INV_LPC_GAIN_LOW_THRES  = 8;                   /* 2^8 = 24 dB LPC gain     */
	private static final int PITCH_DRIFT_FAC_Q16          = 655;                 /* 0.01 in Q16              */
	// start PLC.c
	private static final class Jenergy_struct_aux {
		private int energy1;
		private int shift1;
		private int energy2;
		private int shift2;
	}
	private static final int NB_ATT = 2;
	private static final short HARM_ATT_Q15[/* NB_ATT */]              = { 32440, 31130 }; /* 0.99, 0.95 */
	private static final short PLC_RAND_ATTENUATE_V_Q15[/* NB_ATT */]  = { 31130, 26214 }; /* 0.95, 0.8 */
	private static final short PLC_RAND_ATTENUATE_UV_Q15[/* NB_ATT */] = { 32440, 29491 }; /* 0.99, 0.9 */
	/**
	 * java changed: energy1, shift1, energy2, shift2 is replaced by the Jenergy_struct_aux
	 */
	private static final void silk_PLC_energy(/*final int[] energy1, final int[] shift1, final int[] energy2, final int[] shift2,*/
			final Jenergy_struct_aux aux,// java
			final int[] exc_Q14, final int[] prevGain_Q10, final int subfr_length, final int nb_subfr)
	{
		// SAVE_STACK;
		final short[] exc_buf = new short[subfr_length << 1];
		/* Find random noise component */
		/* Scale previous excitation signal */
		int exc_buf_ptr = 0;// exc_buf[ exc_buf_ptr ]
		for( int k = 0; k < 2; k++ ) {
			for( int i = 0; i < subfr_length; i++ ) {
				final int v = ((int)(((long)exc_Q14[ i + ( k + nb_subfr - 2 ) * subfr_length ] * prevGain_Q10[ k ]) >> 16)) << 8;// java
				exc_buf[ exc_buf_ptr + i ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			}
			exc_buf_ptr += subfr_length;
		}
		/* Find the subframe with lowest energy of the last two and use that as random noise generator */
		final long energy_shift1 = silk_sum_sqr_shift( /*energy1, shift1,*/ exc_buf,            0, subfr_length );
		final long energy_shift2 = silk_sum_sqr_shift( /*energy2, shift2,*/ exc_buf, subfr_length, subfr_length );
		// RESTORE_STACK;
		aux.energy1 = (int)(energy_shift1 >>> 32);
		aux.shift1 = (int)energy_shift1;
		aux.energy2 = (int)(energy_shift2 >>> 32);
		aux.shift2 = (int)energy_shift2;
	}
	/**
	 *
	 * @param psDec I/O Decoder state
	 */
	private final void silk_PLC_Reset()
	{
		this.sPLC.pitchL_Q8 = ( this.frame_length << (8 - 1) );
		// this.sPLC.prevGain_Q16[ 0 ] = SILK_FIX_CONST( 1, 16 );
		this.sPLC.prevGain_Q16[ 0 ] = ( 1 << 16 );
		// this.sPLC.prevGain_Q16[ 1 ] = SILK_FIX_CONST( 1, 16 );
		this.sPLC.prevGain_Q16[ 1 ] = ( 1 << 16 );
		this.sPLC.subfr_length = 20;
		this.sPLC.nb_subfr = 2;
	}
	/**
	 *
	 * @param psDec I/O Decoder state
	 * @param psDecCtrl I/O Decoder control
	 * @param frame O LPC residual signal
	 * @param arch I Run-time architecture
	 */
	private final void silk_PLC_conceal(final Jsilk_decoder_control psDecCtrl,
			final short frame[], final int foffset)// java , final int arch)
	{
/* #ifdef SMALL_FOOTPRINT
		opus_int16 *sLTP;
#else */
		// VARDECL( opus_int16, sLTP );
//#endif
		final Jsilk_PLC_struct psPLC = this.sPLC;

		// SAVE_STACK;

//#ifdef SMALL_FOOTPRINT
		/* Ugly hack that breaks aliasing rules to save stack: put sLTP at the very end of sLTP_Q14. */
//		sLTP = ((opus_int16*)&sLTP_Q14[psDec.ltp_mem_length + psDec.frame_length])-psDec.ltp_mem_length;
//#else
		// ALLOC( sLTP, psDec.ltp_mem_length, opus_int16 );
//#endif

		final int prevGain_Q10[] = { ( psPLC.prevGain_Q16[ 0 ] >> 6), ( psPLC.prevGain_Q16[ 1 ] >> 6) };

		if( this.first_frame_after_reset ) {
			// silk_memset( psPLC.prevLPC_Q12, 0, sizeof( psPLC.prevLPC_Q12 ) );
			for( int i = 0, ie = psPLC.prevLPC_Q12.length; i < ie; i++ ) {
				psPLC.prevLPC_Q12[i] = 0;
			}
		}

		// final int energy1, shift1, energy2, shift2;// java
		final Jenergy_struct_aux aux = new Jenergy_struct_aux();// java
		silk_PLC_energy( aux, /*&energy1, &shift1, &energy2, &shift2,*/ this.exc_Q14, prevGain_Q10, this.subfr_length, this.nb_subfr );
		final int energy1 = aux.energy1;// java
		final int shift1 = aux.shift1;// java
		final int energy2 = aux.energy2;// java
		final int shift2 = aux.shift2;// java

		int rand_ptr;
		if( ( energy1 >> shift2 ) < ( energy2 >> shift1 ) ) {
			/* First sub-frame has lowest energy */
			rand_ptr = ( psPLC.nb_subfr - 1 ) * psPLC.subfr_length - RAND_BUF_SIZE;
			rand_ptr = 0 > rand_ptr ? 0 : rand_ptr;// psDec.exc_Q14[ rand_ptr ]
		} else {
			/* Second sub-frame has lowest energy */
			rand_ptr = psPLC.nb_subfr * psPLC.subfr_length - RAND_BUF_SIZE;
			rand_ptr = ( 0 > rand_ptr ? 0 : rand_ptr );// psDec.exc_Q14[ rand_ptr ]
		}

		/* Set up Gain to random noise component */
		final short[] B_Q14  = psPLC.LTPCoef_Q14;
		int rand_scale_Q14 = (int)psPLC.randScale_Q14;// FIXME int is better

		/* Set up attenuation gains */
		int harm_Gain_Q15 = (NB_ATT - 1) <= this.lossCnt ? (NB_ATT - 1) : this.lossCnt;
		int rand_Gain_Q15 = (int)(( this.prevSignalType == Jdefine.TYPE_VOICED ) ?
				PLC_RAND_ATTENUATE_V_Q15[ harm_Gain_Q15 ]
				:
				PLC_RAND_ATTENUATE_UV_Q15[ harm_Gain_Q15 ]);
		harm_Gain_Q15 = (int)HARM_ATT_Q15[ harm_Gain_Q15 ];

		final int this_LPC_order = this.LPC_order;// java
		/* LPC concealment. Apply BWE to previous LPC */
		// silk_bwexpander( psPLC.prevLPC_Q12, this.LPC_order, JSigProc_FIX.SILK_FIX_CONST( BWE_COEF, 16 ) );
		silk_bwexpander( psPLC.prevLPC_Q12, this_LPC_order, ((int)(BWE_COEF * (1 << 16) + .5)) );

		final short A_Q12[] = new short[ Jdefine.MAX_LPC_ORDER ];
		/* Preload LPC coeficients to array on stack. Gives small performance gain */
		System.arraycopy( psPLC.prevLPC_Q12, 0, A_Q12, 0, this_LPC_order  );

		/* First Lost frame */
		if( this.lossCnt == 0 ) {
			rand_scale_Q14 = 1 << 14;

			/* Reduce random noise Gain for voiced frames */
			if( this.prevSignalType == Jdefine.TYPE_VOICED ) {
				for( int i = 0; i < Jdefine.LTP_ORDER; i++ ) {
					rand_scale_Q14 -= B_Q14[ i ];
				}
				rand_scale_Q14 = ( 3277 >= rand_scale_Q14 ? 3277 : rand_scale_Q14 ); /* 0.2 */
				rand_scale_Q14 = ( ( rand_scale_Q14 * (int)psPLC.prevLTP_scale_Q14 ) >> 14 );
			} else {
				/* Reduce random noise for unvoiced frames with high LPC gain */
				final int invGain_Q30 = silk_LPC_inverse_pred_gain( psPLC.prevLPC_Q12, this_LPC_order );//, arch );

				int down_scale_Q30 = ( ((1 << 30) >> LOG2_INV_LPC_GAIN_HIGH_THRES) < invGain_Q30 ? ((1 << 30) >> LOG2_INV_LPC_GAIN_HIGH_THRES) : invGain_Q30 );
				down_scale_Q30 = ( ((1 << 30) >> LOG2_INV_LPC_GAIN_LOW_THRES) > down_scale_Q30 ? ((1 << 30) >> LOG2_INV_LPC_GAIN_LOW_THRES) : down_scale_Q30 );
				down_scale_Q30 <<= LOG2_INV_LPC_GAIN_HIGH_THRES;

				rand_Gain_Q15 = ( ((int)((down_scale_Q30 * (long)rand_Gain_Q15) >> 16)) >> 14 );
			}
		}

		int rand_seed    = psPLC.rand_seed;
		int lag          = JSigProc_FIX.silk_RSHIFT_ROUND( psPLC.pitchL_Q8, 8 );
		int sLTP_buf_idx = this.ltp_mem_length;

		/* Rewhiten LTP state */
		int idx = this.ltp_mem_length - lag - this_LPC_order - Jdefine.LTP_ORDER / 2;
		// celt_assert( idx > 0 );
		final short[] sLTP = new short[this.ltp_mem_length];
		silk_LPC_analysis_filter( sLTP, idx, this.outBuf, idx, A_Q12, this.ltp_mem_length - idx, this_LPC_order );//, arch );
		/* Scale LTP state */
		int inv_gain_Q30 = silk_INVERSE32_varQ( psPLC.prevGain_Q16[ 1 ], 46 );
		inv_gain_Q30 = ( inv_gain_Q30 < (Integer.MAX_VALUE >> 1) ? inv_gain_Q30 : (Integer.MAX_VALUE >> 1) );
		final int[] sLTP_Q14 = new int[this.ltp_mem_length + this.frame_length];
		for( int i = idx + this_LPC_order, ie = this.ltp_mem_length; i < ie; i++ ) {
			sLTP_Q14[ i ] = ( (int)((inv_gain_Q30 * (long)sLTP[ i ]) >> 16) );
		}

		/***************************/
		/* LTP synthesis filtering */
		/***************************/
		// java
		final long B_Q14_0 = (long)B_Q14[ 0 ];
		final long B_Q14_1 = (long)B_Q14[ 1 ];
		final long B_Q14_2 = (long)B_Q14[ 2 ];
		final long B_Q14_3 = (long)B_Q14[ 3 ];
		final long B_Q14_4 = (long)B_Q14[ 4 ];
		for( int k = 0, ke = this.nb_subfr; k < ke; k++ ) {
			/* Set up pointer */
			int pred_lag_ptr = sLTP_buf_idx - lag + Jdefine.LTP_ORDER / 2;// sLTP_Q14[ pred_lag_ptr ]
			for( int i = 0; i < this.subfr_length; i++ ) {
				/* Unrolled loop */
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				int LTP_pred_Q12 = 2;
				LTP_pred_Q12 += (int)((sLTP_Q14[ pred_lag_ptr-- ] * B_Q14_0 ) >> 16);// + 0
				LTP_pred_Q12 += (int)((sLTP_Q14[ pred_lag_ptr-- ] * B_Q14_1 ) >> 16);
				LTP_pred_Q12 += (int)((sLTP_Q14[ pred_lag_ptr-- ] * B_Q14_2 ) >> 16);
				LTP_pred_Q12 += (int)((sLTP_Q14[ pred_lag_ptr-- ] * B_Q14_3 ) >> 16);
				LTP_pred_Q12 += (int)((sLTP_Q14[ pred_lag_ptr   ] * B_Q14_4 ) >> 16);// - 4
				pred_lag_ptr += 1 + 4;// + 1

				/* Generate LPC excitation */
				rand_seed = silk_RAND( rand_seed );
				idx = ( rand_seed >> 25 ) & RAND_BUF_MASK;
				sLTP_Q14[ sLTP_buf_idx ] = LTP_pred_Q12 + (int)((this.exc_Q14[ rand_ptr + idx ] * (long)rand_scale_Q14) >> 16);
				sLTP_buf_idx++;
			}

			/* Gradually reduce LTP gain */
			int j = 0;
			do {
				B_Q14[ j ] = (short)(( harm_Gain_Q15 * (int)B_Q14[ j ] ) >> 15);
			} while( ++j < Jdefine.LTP_ORDER );
			if ( this.indices.signalType != Jdefine.TYPE_NO_VOICE_ACTIVITY ) {
				/* Gradually reduce excitation gain */
				rand_scale_Q14 = (( rand_scale_Q14 * rand_Gain_Q15 ) >> 15);
			}

			/* Slowly increase pitch lag */
			psPLC.pitchL_Q8 += (int)((psPLC.pitchL_Q8 * (long)PITCH_DRIFT_FAC_Q16) >> 16);
			psPLC.pitchL_Q8 = (psPLC.pitchL_Q8 < ((MAX_PITCH_LAG_MS * this.fs_kHz) << 8) ? psPLC.pitchL_Q8 : ((MAX_PITCH_LAG_MS * this.fs_kHz) << 8));
			lag = JSigProc_FIX.silk_RSHIFT_ROUND( psPLC.pitchL_Q8, 8 );
		}

		/***************************/
		/* LPC synthesis filtering */
		/***************************/
		final int sLPC_Q14_ptr = this.ltp_mem_length - Jdefine.MAX_LPC_ORDER;// sLTP_Q14[ sLPC_Q14_ptr ]

		/* Copy LPC state */
		System.arraycopy( this.sLPC_Q14_buf, 0, sLTP_Q14, sLPC_Q14_ptr, Jdefine.MAX_LPC_ORDER );

		// celt_assert( psDec.LPC_order >= 10 ); /* check that unrolling works */
		// java
		final long A_Q12_0 = (long)A_Q12[ 0 ];
		final long A_Q12_1 = (long)A_Q12[ 1 ];
		final long A_Q12_2 = (long)A_Q12[ 2 ];
		final long A_Q12_3 = (long)A_Q12[ 3 ];
		final long A_Q12_4 = (long)A_Q12[ 4 ];
		final long A_Q12_5 = (long)A_Q12[ 5 ];
		final long A_Q12_6 = (long)A_Q12[ 6 ];
		final long A_Q12_7 = (long)A_Q12[ 7 ];
		final long A_Q12_8 = (long)A_Q12[ 8 ];
		final long A_Q12_9 = (long)A_Q12[ 9 ];
		final int LPC_order2 = ( this_LPC_order >> 1 );
		for( int i = foffset, ie = foffset + this.frame_length, pi = sLPC_Q14_ptr + Jdefine.MAX_LPC_ORDER; i < ie; i++, pi++ ) {
			/* partly unrolled */
			/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
			int si = pi;
			int LPC_pred_Q10 = LPC_order2;
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_0 ) >> 16);// - 1
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_1 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_2 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_3 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_4 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_5 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_6 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_7 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_8 ) >> 16);
			LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * A_Q12_9 ) >> 16);// - 10
			for( int j = 10; j < this_LPC_order; j++ ) {
				LPC_pred_Q10 += (int)((sLTP_Q14[ --si ] * (long)A_Q12[ j ] ) >> 16);
			}

			/* Add prediction to LPC excitation */
			int v = sLTP_Q14[ pi ];
			final int b = ((LPC_pred_Q10 > (Integer.MAX_VALUE >> 4) ?
					(Integer.MAX_VALUE >> 4) : (LPC_pred_Q10 < (Integer.MIN_VALUE >> 4) ? (Integer.MIN_VALUE >> 4) : LPC_pred_Q10)) << 4);
			v = (((v + b) & 0x80000000) == 0 ?
					(((v & b) & 0x80000000) != 0 ? Integer.MIN_VALUE : v + b) :
						(((v | b) & 0x80000000) == 0 ? Integer.MAX_VALUE : v + b) );
			sLTP_Q14[ pi ] = v;

			/* Scale with Gain */
			// frame[ i ] = (short)silk_SAT16( silk_SAT16( JSigProc_FIX.silk_RSHIFT_ROUND( Jmacros.silk_SMULWW( v, prevGain_Q10[ 1 ] ), 8 ) ) );// FIXME why silk_SAT16( silk_SAT16() ) ?
			v = JSigProc_FIX.silk_RSHIFT_ROUND( ((int)(((long)v * prevGain_Q10[ 1 ]) >> 16)), 8 );
			frame[ i ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
		}

		/* Save LPC state */
		System.arraycopy( sLTP_Q14, sLPC_Q14_ptr + this.frame_length, this.sLPC_Q14_buf, 0, Jdefine.MAX_LPC_ORDER );

		/**************************************/
		/* Update states                      */
		/**************************************/
		psPLC.rand_seed     = rand_seed;
		psPLC.randScale_Q14 = (short)rand_scale_Q14;
		for( int i = 0; i < Jdefine.MAX_NB_SUBFR; i++ ) {
			psDecCtrl.pitchL[ i ] = lag;
		}
		// RESTORE_STACK;
	}
	/**
	 * Update state of PLC
	 *
	 * @param psDec I/O Decoder state
	 * @param psDecCtrl I/O Decoder control
	 */
	private final void silk_PLC_update(final Jsilk_decoder_control psDecCtrl)
	{
		final Jsilk_PLC_struct psPLC = this.sPLC;

		/* Update parameters used in case of packet loss */
		this.prevSignalType = this.indices.signalType;
		int LTP_Gain_Q14 = 0;
		final int nb_subfr1 = this.nb_subfr - 1;// java
		if( this.indices.signalType == Jdefine.TYPE_VOICED ) {
			/* Find the parameters for the last subframe which contains a pitch pulse */
			for( int j = 0, je = psDecCtrl.pitchL[ nb_subfr1 ], nb_j = nb_subfr1; j * this.subfr_length < je; j++, nb_j-- ) {
				if( j == this.nb_subfr ) {
					break;
				}
				int temp_LTP_Gain_Q14 = 0;
				final int nb_j_o = nb_j * Jdefine.LTP_ORDER;// java
				int i = 0;
				do {
					temp_LTP_Gain_Q14 += psDecCtrl.LTPCoef_Q14[ nb_j_o + i ];
				} while( ++i < Jdefine.LTP_ORDER );
				if( temp_LTP_Gain_Q14 > LTP_Gain_Q14 ) {
					LTP_Gain_Q14 = temp_LTP_Gain_Q14;
					System.arraycopy( psDecCtrl.LTPCoef_Q14, nb_j_o, psPLC.LTPCoef_Q14, 0, Jdefine.LTP_ORDER );

					psPLC.pitchL_Q8 = psDecCtrl.pitchL[ nb_j ] << 8;
				}
			}

			// silk_memset( psPLC.LTPCoef_Q14, 0, Jdefine.LTP_ORDER * sizeof( opus_int16 ) );
			int i = 0;
			do {
				psPLC.LTPCoef_Q14[i] = 0;
			} while( ++i < Jdefine.LTP_ORDER );
			psPLC.LTPCoef_Q14[ Jdefine.LTP_ORDER / 2 ] = (short)LTP_Gain_Q14;

			/* Limit LT coefs */
			if( LTP_Gain_Q14 < V_PITCH_GAIN_START_MIN_Q14 ) {
				final int tmp = V_PITCH_GAIN_START_MIN_Q14 << 10;
				final int scale_Q10 = ( tmp / ( LTP_Gain_Q14 > 1 ? LTP_Gain_Q14 : 1 ) );
				i = 0;
				do {
					psPLC.LTPCoef_Q14[ i ] = (short)(((int)psPLC.LTPCoef_Q14[ i ] * scale_Q10) >> 10);
				} while( ++i < Jdefine.LTP_ORDER );
			} else if( LTP_Gain_Q14 > V_PITCH_GAIN_START_MAX_Q14 ) {
				final int tmp = V_PITCH_GAIN_START_MAX_Q14 << 14;
				final int scale_Q14 = ( tmp / ( LTP_Gain_Q14 > 1 ? LTP_Gain_Q14 : 1 ) );
				i = 0;
				do{
					psPLC.LTPCoef_Q14[ i ] = (short)(( (int)psPLC.LTPCoef_Q14[ i ] * scale_Q14 ) >> 14);
				} while( ++i < Jdefine.LTP_ORDER );
			}
		} else {
			psPLC.pitchL_Q8 = ( this.fs_kHz * 18 ) << 8;
			// silk_memset( psPLC.LTPCoef_Q14, 0, Jdefine.LTP_ORDER * sizeof( opus_int16 ));
			int i = 0;
			do {
				psPLC.LTPCoef_Q14[i] = 0;
			} while( ++i < Jdefine.LTP_ORDER );
		}

		/* Save LPC coeficients */
		System.arraycopy( psDecCtrl.PredCoef_Q12[ 1 ], 0, psPLC.prevLPC_Q12, 0, this.LPC_order );
		psPLC.prevLTP_scale_Q14 = (short)psDecCtrl.LTP_scale_Q14;

		/* Save last two gains */
		psPLC.prevGain_Q16[0] = psDecCtrl.Gains_Q16[ nb_subfr1 - 1 ];
		psPLC.prevGain_Q16[1] = psDecCtrl.Gains_Q16[ nb_subfr1 ];

		psPLC.subfr_length = this.subfr_length;
		psPLC.nb_subfr = this.nb_subfr;
	}
	/**
	 *
	 * @param psDec I/O Decoder state
	 * @param psDecCtrl I/O Decoder control
	 * @param frame I/O  signal
	 * @param lost I Loss flag
	 * @param arch I Run-time architecture
	 */
	final void silk_PLC(final Jsilk_decoder_control psDecCtrl,
			final short frame[], final int foffset,// java
			final boolean lost)//, final int arch)
	{
		/* PLC control function */
		if( this.fs_kHz != this.sPLC.fs_kHz ) {
			silk_PLC_Reset();
			this.sPLC.fs_kHz = this.fs_kHz;
		}

		if( lost ) {
			/****************************/
			/* Generate Signal          */
			/****************************/
			silk_PLC_conceal( psDecCtrl, frame, foffset );//, arch );

			this.lossCnt++;
			return;
		}// else {
			/****************************/
			/* Update state             */
			/****************************/
			silk_PLC_update( psDecCtrl );
		//}
	}
	/**
	 * Glues concealed frames with new good received frames
	 *
	 * @param psDec I/O decoder state
	 * @param frame I/O signal
	 * @param length I length of signal
	 */
	final void silk_PLC_glue_frames(final short frame[], int foffset, int length)
	{// java foffset is added
		final Jsilk_PLC_struct psPLC = this.sPLC;

		if( 0 != this.lossCnt ) {
			/* Calculate energy in concealed residual */
			final long ret = silk_sum_sqr_shift( /*&psPLC.conc_energy, &psPLC.conc_energy_shift,*/ frame, foffset, length );// java
			psPLC.conc_energy = (int)(ret >>> 32);// java
			psPLC.conc_energy_shift = (int)ret;// java

			psPLC.last_frame_lost = true;
		} else {
			if( this.sPLC.last_frame_lost ) {
				/* Calculate residual in decoded signal if last frame was lost */
				final long ret = silk_sum_sqr_shift( /*&energy, &energy_shift,*/ frame, foffset, length );
				final int energy_shift = (int)ret;// java
				int energy = (int)(ret >>> 32);// java

				/* Normalize energies */
				if( energy_shift > psPLC.conc_energy_shift ) {
					psPLC.conc_energy = ( psPLC.conc_energy >> (energy_shift - psPLC.conc_energy_shift) );
				} else if( energy_shift < psPLC.conc_energy_shift ) {
					energy = ( energy >> (psPLC.conc_energy_shift - energy_shift) );
				}

				/* Fade in the energy difference */
				if( energy > psPLC.conc_energy ) {
					int LZ = Jmacros.silk_CLZ32( psPLC.conc_energy );
					LZ--;
					psPLC.conc_energy = ( psPLC.conc_energy << LZ );
					LZ = 24 - LZ;// java
					energy >>= ( LZ >= 0 ? LZ : 0 );

					final int frac_Q24 = psPLC.conc_energy / ( energy > 1 ? energy : 1 );

					int gain_Q16 = ( silk_SQRT_APPROX( frac_Q24 ) << 4 );
					int slope_Q16 = ((1 << 16) - gain_Q16) / length;
					/* Make slope 4x steeper to avoid missing onsets after DTX */
					slope_Q16 = ( slope_Q16 << 2 );

					for( length += foffset; foffset < length; foffset++ ) {
						frame[ foffset ] = (short)((gain_Q16 * (long)frame[ foffset ]) >> 16);
						gain_Q16 += slope_Q16;
						if( gain_Q16 > (1 << 16) ) {
							break;
						}
					}
				}
			}
			psPLC.last_frame_lost = false;
		}
	}
	// end PLC.c

	// init_decoder.c
	/**
	 * Init Decoder State
	 * @param psDec I/O  Decoder state pointer
	 */
	final int silk_init_decoder()
	{
		/* Clear the entire encoder state, except anything copied */
		clear();// silk_memset( psDec, 0, sizeof( silk_decoder_state ) );

		/* Used to deactivate LSF interpolation */
		this.first_frame_after_reset = true;
		this.prev_gain_Q16 = 65536;
		// this.arch = opus_select_arch();

		/* Reset CNG state */
		silk_CNG_Reset();

		/* Reset PLC state */
		silk_PLC_Reset();

		return 0;
	}
	// end init_decoder.c
	// start decoder_set_fs.c
	/**
	 * Set decoder sampling rate
	 *
	 * @param psDec I/O  Decoder state pointer
	 * @param fsr_kHz I    Sampling frequency (kHz)
	 * @param fs_API_Hz I    API Sampling frequency (Hz)
	 * @return
	 */
	final int silk_decoder_set_fs(final int fsr_kHz, final int fs_API_Hz)
	{
		int ret = 0;

		// celt_assert( fs_kHz == 8 || fs_kHz == 12 || fs_kHz == 16 );
		// celt_assert( psDec.nb_subfr == Jdefine.MAX_NB_SUBFR || psDec.nb_subfr == Jdefine.MAX_NB_SUBFR / 2 );

		/* New (sub)frame length */
		this.subfr_length = Jdefine.SUB_FRAME_LENGTH_MS * fsr_kHz;
		final int new_frame_length = this.nb_subfr * this.subfr_length;

		/* Initialize resampler when switching internal or external sampling frequency */
		if( this.fs_kHz != fsr_kHz || this.fs_API_hz != fs_API_Hz ) {
			/* Initialize the resampler for dec_API.c preparing resampling from fs_kHz to API_fs_Hz */
			ret += this.resampler_state.silk_resampler_init( fsr_kHz * 1000, fs_API_Hz, false );

			this.fs_API_hz = fs_API_Hz;
		}

		if( this.fs_kHz != fsr_kHz || new_frame_length != this.frame_length ) {
			if( fsr_kHz == 8 ) {
				if( this.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
					this.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_NB_iCDF;
				} else {
					this.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_NB_iCDF;
				}
			} else {
				if( this.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
					this.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_iCDF;
				} else {
					this.pitch_contour_iCDF = Jtables_pitch_lag.silk_pitch_contour_10_ms_iCDF;
				}
			}
			if( this.fs_kHz != fsr_kHz ) {
				this.ltp_mem_length = Jdefine.LTP_MEM_LENGTH_MS * fsr_kHz;
				if( fsr_kHz == 8 || fsr_kHz == 12 ) {
					this.LPC_order = Jdefine.MIN_LPC_ORDER;
					this.psNLSF_CB = Jtables_NLSF_CB_NB_MB.silk_NLSF_CB_NB_MB;
				} else {
					this.LPC_order = Jdefine.MAX_LPC_ORDER;
					this.psNLSF_CB = Jtables_NLSF_CB_WB.silk_NLSF_CB_WB;
				}
				if( fsr_kHz == 16 ) {
					this.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform8_iCDF;
				} else if( fsr_kHz == 12 ) {
					this.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform6_iCDF;
				} else if( fsr_kHz == 8 ) {
					this.pitch_lag_low_bits_iCDF = Jtables_other.silk_uniform4_iCDF;
				} else {
					/* unsupported sampling rate */
					// celt_assert( 0 );
				}
				this.first_frame_after_reset = true;
				this.lagPrev                 = 100;
				this.LastGainIndex           = 10;
				this.prevSignalType          = Jdefine.TYPE_NO_VOICE_ACTIVITY;
				//silk_memset( psDec.outBuf, 0, sizeof(psDec.outBuf));
				final short[] sbuff = this.outBuf;// java
				for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
					sbuff[i] = 0;
				}
				//silk_memset( psDec.sLPC_Q14_buf, 0, sizeof(psDec.sLPC_Q14_buf) );
				final int[] ibuff = this.sLPC_Q14_buf;// java
				for( int i = 0, ie = ibuff.length; i < ie; i++ ) {
					ibuff[i] = 0;
				}
			}

			this.fs_kHz       = fsr_kHz;
			this.frame_length = new_frame_length;
		}

		/* Check that settings are valid */
		// celt_assert( psDec.frame_length > 0 && psDec.frame_length <= Jdefine.MAX_FRAME_LENGTH );

		return ret;
	}
	// end decoder_set_fs.c
	// start decode_indices.c
	/**
	 * Decode side-information parameters from payload
	 *
	 * @param psDec I/O  State
	 * @param psRangeDec I/O  Compressor data structure
	 * @param FrameIndex I    Frame number
	 * @param decode_LBRR I    Flag indicating LBRR data is being decoded
	 * @param condCoding I    The type of conditional coding to use
	 */
	final void silk_decode_indices(final Jec_dec psRangeDec,
			final int FrameIndex, final boolean decode_LBRR, final int condCoding)
	{
		final short ec_ix[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final char pred_Q8[] = new char[ Jdefine.MAX_LPC_ORDER ];

		/*******************************************/
		/* Decode signal type and quantizer offset */
		/*******************************************/
		int Ix = ( decode_LBRR || this.VAD_flags[ FrameIndex ] ) ?
							psRangeDec.ec_dec_icdf( Jtables_other.silk_type_offset_VAD_iCDF, 0, 8 ) + 2
							:
							psRangeDec.ec_dec_icdf( Jtables_other.silk_type_offset_no_VAD_iCDF, 0, 8 );

		this.indices.signalType      = (byte)(Ix >> 1);
		this.indices.quantOffsetType = (byte)(Ix & 1);

		/****************/
		/* Decode gains */
		/****************/
		/* First subframe */
		if( condCoding == Jdefine.CODE_CONDITIONALLY ) {
			/* Conditional coding */
			this.indices.GainsIndices[ 0 ] = (byte)psRangeDec.ec_dec_icdf( Jtables_gain.silk_delta_gain_iCDF, 0, 8 );
		} else {
			/* Independent coding, in two stages: MSB bits followed by 3 LSBs */
			this.indices.GainsIndices[ 0 ]  = (byte)(psRangeDec.ec_dec_icdf( Jtables_gain.silk_gain_iCDF[ this.indices.signalType], 0, 8 ) << 3);
			this.indices.GainsIndices[ 0 ] += (byte)psRangeDec.ec_dec_icdf( Jtables_other.silk_uniform8_iCDF, 0, 8 );
		}

		/* Remaining subframes */
		for( int i = 1; i < this.nb_subfr; i++ ) {
			this.indices.GainsIndices[ i ] = (byte)psRangeDec.ec_dec_icdf( Jtables_gain.silk_delta_gain_iCDF, 0, 8 );
		}

		/**********************/
		/* Decode LSF Indices */
		/**********************/
		this.indices.NLSFIndices[ 0 ] = (byte)psRangeDec.ec_dec_icdf( this.psNLSF_CB.CB1_iCDF, ( this.indices.signalType >> 1 ) * this.psNLSF_CB.nVectors, 8 );
		silk_NLSF_unpack( ec_ix, pred_Q8, this.psNLSF_CB, this.indices.NLSFIndices[ 0 ] );
		// celt_assert( psDec.psNLSF_CB.order == psDec.LPC_order );
		for( int i = 0; i < this.psNLSF_CB.order; i++ ) {
			Ix = psRangeDec.ec_dec_icdf( this.psNLSF_CB.ec_iCDF, ec_ix[ i ], 8 );
			if( Ix == 0 ) {
				Ix -= psRangeDec.ec_dec_icdf( Jtables_other.silk_NLSF_EXT_iCDF, 0, 8 );
			} else if( Ix == 2 * Jdefine.NLSF_QUANT_MAX_AMPLITUDE ) {
				Ix += psRangeDec.ec_dec_icdf( Jtables_other.silk_NLSF_EXT_iCDF, 0, 8 );
			}
			this.indices.NLSFIndices[ i+1 ] = (byte)(Ix - Jdefine.NLSF_QUANT_MAX_AMPLITUDE);
		}

		/* Decode LSF interpolation factor */
		if( this.nb_subfr == Jdefine.MAX_NB_SUBFR ) {
			this.indices.NLSFInterpCoef_Q2 = (byte)psRangeDec.ec_dec_icdf( Jtables_other.silk_NLSF_interpolation_factor_iCDF, 0, 8 );
		} else {
			this.indices.NLSFInterpCoef_Q2 = 4;
		}

		if( this.indices.signalType == Jdefine.TYPE_VOICED )
		{
			/*********************/
			/* Decode pitch lags */
			/*********************/
			/* Get lag index */
			boolean decode_absolute_lagIndex = true;
			if( condCoding == Jdefine.CODE_CONDITIONALLY && this.ec_prevSignalType == Jdefine.TYPE_VOICED ) {
				/* Decode Delta index */
				int delta_lagIndex = (short)psRangeDec.ec_dec_icdf( Jtables_pitch_lag.silk_pitch_delta_iCDF, 0, 8 );
				if( delta_lagIndex > 0 ) {
					delta_lagIndex = delta_lagIndex - 9;
					this.indices.lagIndex = (short)( this.ec_prevLagIndex + delta_lagIndex );
					decode_absolute_lagIndex = false;
				}
			}
			if( decode_absolute_lagIndex ) {
				/* Absolute decoding */
				this.indices.lagIndex  = (short)(psRangeDec.ec_dec_icdf( Jtables_pitch_lag.silk_pitch_lag_iCDF, 0, 8 ) * ( this.fs_kHz >> 1 ));
				this.indices.lagIndex += (short)psRangeDec.ec_dec_icdf( this.pitch_lag_low_bits_iCDF, 0, 8 );
			}
			this.ec_prevLagIndex = this.indices.lagIndex;

			/* Get countour index */
			this.indices.contourIndex = (byte)psRangeDec.ec_dec_icdf( this.pitch_contour_iCDF, 0, 8 );

			/********************/
			/* Decode LTP gains */
			/********************/
			/* Decode PERIndex value */
			this.indices.PERIndex = (byte)psRangeDec.ec_dec_icdf( Jtables_LTP.silk_LTP_per_index_iCDF, 0, 8 );

			for( int k = 0; k < this.nb_subfr; k++ ) {
				this.indices.LTPIndex[ k ] = (byte)psRangeDec.ec_dec_icdf( Jtables_LTP.silk_LTP_gain_iCDF_ptrs[ this.indices.PERIndex ], 0, 8 );
			}

			/**********************/
			/* Decode LTP scaling */
			/**********************/
			if( condCoding == Jdefine.CODE_INDEPENDENTLY ) {
				this.indices.LTP_scaleIndex = (byte)psRangeDec.ec_dec_icdf( Jtables_other.silk_LTPscale_iCDF, 0, 8 );
			} else {
				this.indices.LTP_scaleIndex = 0;
			}
		}
		this.ec_prevSignalType = this.indices.signalType;

		/***************/
		/* Decode seed */
		/***************/
		this.indices.Seed = (byte)psRangeDec.ec_dec_icdf( Jtables_other.silk_uniform4_iCDF, 0, 8 );
	}
	// end decode_indices.c

	// start decode_core.c
	/**
	 * Core decoder. Performs inverse NSQ operation LTP + LPC
	 *
	 * @param psDec I/O  Decoder state
	 * @param psDecCtrl I    Decoder control
	 * @param xq O    Decoded speech
	 * @param pulses I    Pulse signal
	 * @param arch I    Run-time architecture
	 */
	private final void silk_decode_core(final Jsilk_decoder_control psDecCtrl,
			final short xq[], final int xoffset,// java
			final short pulses[/* Jdefine.MAX_FRAME_LENGTH */])//, final int arch)
	{
		final short A_Q12_tmp[] = new short[ Jdefine.MAX_LPC_ORDER ];
		// SAVE_STACK;

		// silk_assert( psDec.prev_gain_Q16 != 0 );

		final int subframe_length = this.subfr_length;// java

		final short[] sLTP = new short[this.ltp_mem_length];
		final int[] sLTP_Q15 = new int[this.ltp_mem_length + this.frame_length];
		final int[] res_Q14 = new int[subframe_length];
		final int[] sLPC_Q14 = new int[subframe_length + Jdefine.MAX_LPC_ORDER];

		final int offset_Q10 = (int)Jtables_other.silk_Quantization_Offsets_Q10[ this.indices.signalType >> 1 ][ this.indices.quantOffsetType ];

		final boolean NLSF_interpolation_flag = ( this.indices.NLSFInterpCoef_Q2 < 1 << 2 );

		final int[] excQ14 = this.exc_Q14;// java
		/* Decode excitation */
		int rand_seed = (int)this.indices.Seed;
		for( int i = 0, ie = this.frame_length; i < ie; i++ ) {
			rand_seed = silk_RAND( rand_seed );
			excQ14[ i ] = ((int)pulses[ i ]) << 14;
			if( excQ14[ i ] > 0 ) {
				excQ14[ i ] -= Jdefine.QUANT_LEVEL_ADJUST_Q10 << 4;
			} else
				if( excQ14[ i ] < 0 ) {
					excQ14[ i ] += Jdefine.QUANT_LEVEL_ADJUST_Q10 << 4;
			}
			excQ14[ i ] += offset_Q10 << 4;
			if( rand_seed < 0 ) {
				excQ14[ i ] = -excQ14[ i ];
			}

			rand_seed += pulses[ i ];
		}

		/* Copy LPC state */
		System.arraycopy( this.sLPC_Q14_buf, 0, sLPC_Q14, 0, Jdefine.MAX_LPC_ORDER );

		final short[] LTPCoef_Q14 = psDecCtrl.LTPCoef_Q14;// java
		int pexc_Q14 = 0;// exc_Q14[ pexc_Q14 ], psDec.exc_Q14[ pexc_Q14 ]
		int pxq = xoffset;// xq[ pxq ]
		int sLTP_buf_idx = this.ltp_mem_length;
		int lag = 0;
		/* Loop over subframes */
		for( int k = 0, ke = this.nb_subfr; k < ke; k++ ) {
			int[] dim_Q14 = res_Q14;// java
			int pres_Q14 = 0;// dim_Q14[ pres_Q14 ]
			final short[] A_Q12 = psDecCtrl.PredCoef_Q12[ k >> 1 ];

			/* Preload LPC coeficients to array on stack. Gives small performance gain */
			System.arraycopy( A_Q12, 0, A_Q12_tmp, 0, this.LPC_order );
			int B_Q14        = k * Jdefine.LTP_ORDER;// psDecCtrl.LTPCoef_Q14[ B_Q14 ]
			int signalType   = this.indices.signalType;

			final int Gain_Q10     = (psDecCtrl.Gains_Q16[ k ] >> 6);
			int inv_gain_Q31 = silk_INVERSE32_varQ( psDecCtrl.Gains_Q16[ k ], 47 );

			/* Calculate gain adjustment factor */
			int gain_adj_Q16;
			if( psDecCtrl.Gains_Q16[ k ] != this.prev_gain_Q16 ) {
				gain_adj_Q16 =  silk_DIV32_varQ( this.prev_gain_Q16, psDecCtrl.Gains_Q16[ k ], 16 );

				/* Scale short term state */
				for( int i = 0; i < Jdefine.MAX_LPC_ORDER; i++ ) {
					sLPC_Q14[ i ] = (int)(((long)gain_adj_Q16 * sLPC_Q14[ i ]) >> 16);
				}
			} else {
				gain_adj_Q16 = 1 << 16;
			}

			/* Save inv_gain */
			// silk_assert( inv_gain_Q31 != 0 );
			this.prev_gain_Q16 = psDecCtrl.Gains_Q16[ k ];

			/* Avoid abrupt transition from voiced PLC to unvoiced normal decoding */
			if( 0 != this.lossCnt && this.prevSignalType == Jdefine.TYPE_VOICED &&
					this.indices.signalType != Jdefine.TYPE_VOICED && k < Jdefine.MAX_NB_SUBFR / 2 ) {

				//silk_memset( B_Q14, 0, Jdefine.LTP_ORDER * sizeof( opus_int16 ) );
				for( int i = B_Q14, ie = B_Q14 + Jdefine.LTP_ORDER; i < ie; i++ ) {
					LTPCoef_Q14[ i ] = 0;
				}
				// LTPCoef_Q14[ B_Q14 + Jdefine.LTP_ORDER / 2 ] = (short)SILK_FIX_CONST( 0.25, 14 );
				LTPCoef_Q14[ B_Q14 + Jdefine.LTP_ORDER / 2 ] = (short)( 0.25 * (1 << 14) + .5 );

				signalType = Jdefine.TYPE_VOICED;
				psDecCtrl.pitchL[ k ] = this.lagPrev;
			}

			if( signalType == Jdefine.TYPE_VOICED ) {
				/* Voiced */
				lag = psDecCtrl.pitchL[ k ];

				/* Re-whitening */
				if( k == 0 || ( k == 2 && NLSF_interpolation_flag ) ) {
					/* Rewhiten with new A coefs */
					final int start_idx = this.ltp_mem_length - lag - this.LPC_order - Jdefine.LTP_ORDER / 2;
					// celt_assert( start_idx > 0 );

					if( k == 2 ) {
						System.arraycopy( xq, xoffset, this.outBuf, this.ltp_mem_length, subframe_length << 1 );
					}

					silk_LPC_analysis_filter( sLTP, start_idx, this.outBuf, start_idx + k * subframe_length,
										A_Q12, this.ltp_mem_length - start_idx, this.LPC_order );//, arch );

					/* After rewhitening the LTP state is unscaled */
					if( k == 0 ) {
						/* Do LTP downscaling to reduce inter-packet dependency */
						inv_gain_Q31 = ( (int)((inv_gain_Q31 * (long)psDecCtrl.LTP_scale_Q14) >> 16) ) << 2;
					}
					for( int i = this.ltp_mem_length - 1, ie = i - lag - Jdefine.LTP_ORDER / 2, idx = sLTP_buf_idx - 1; i > ie; i--, idx-- ) {
						sLTP_Q15[ idx ] = (int)((inv_gain_Q31 * (long)sLTP[ i ]) >> 16);
					}
				} else {
					/* Update LTP state when Gain changes */
					if( gain_adj_Q16 != 1 << 16 ) {
						for( int i = sLTP_buf_idx - 1, ie = i - lag - Jdefine.LTP_ORDER / 2; i > ie; i-- ) {
							sLTP_Q15[ i ] = (int)(((long)gain_adj_Q16 * sLTP_Q15[ i ]) >> 16);
						}
					}
				}
			}

			/* Long-term prediction */
			if( signalType == Jdefine.TYPE_VOICED ) {
				/* Set up pointer */
				int pred_lag_ptr = sLTP_buf_idx - lag + Jdefine.LTP_ORDER / 2;// sLTP_Q15[ pred_lag_ptr ]
				final long B_Q14_0 = (long)LTPCoef_Q14[ B_Q14++ ];// java
				final long B_Q14_1 = (long)LTPCoef_Q14[ B_Q14++ ];// java
				final long B_Q14_2 = (long)LTPCoef_Q14[ B_Q14++ ];// java
				final long B_Q14_3 = (long)LTPCoef_Q14[ B_Q14++ ];// java
				final long B_Q14_4 = (long)LTPCoef_Q14[ B_Q14   ];// java
				for( int i = 0; i < subframe_length; i++ ) {
					/* Unrolled loop */
					/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
					int LTP_pred_Q13 = 2;
					LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * B_Q14_0 ) >> 16);// + 0
					LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * B_Q14_1 ) >> 16);
					LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * B_Q14_2 ) >> 16);
					LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr-- ] * B_Q14_3 ) >> 16);
					LTP_pred_Q13 += (int)((sLTP_Q15[ pred_lag_ptr   ] * B_Q14_4 ) >> 16);// - 4
					pred_lag_ptr += 1 + 4;// +1

					/* Generate LPC excitation */
					dim_Q14[ pres_Q14 + i ] = (excQ14[ pexc_Q14 + i ] + (LTP_pred_Q13 << 1));

					/* Update states */
					sLTP_Q15[ sLTP_buf_idx ] = dim_Q14[ pres_Q14 + i ] << 1;
					sLTP_buf_idx++;
				}
			} else {
				dim_Q14 = excQ14;// java
				pres_Q14 = pexc_Q14;
			}

			// java
			final long A_Q12_tmp_0 = (long)A_Q12_tmp[ 0 ];
			final long A_Q12_tmp_1 = (long)A_Q12_tmp[ 1 ];
			final long A_Q12_tmp_2 = (long)A_Q12_tmp[ 2 ];
			final long A_Q12_tmp_3 = (long)A_Q12_tmp[ 3 ];
			final long A_Q12_tmp_4 = (long)A_Q12_tmp[ 4 ];
			final long A_Q12_tmp_5 = (long)A_Q12_tmp[ 5 ];
			final long A_Q12_tmp_6 = (long)A_Q12_tmp[ 6 ];
			final long A_Q12_tmp_7 = (long)A_Q12_tmp[ 7 ];
			final long A_Q12_tmp_8 = (long)A_Q12_tmp[ 8 ];
			final long A_Q12_tmp_9 = (long)A_Q12_tmp[ 9 ];
			final long A_Q12_tmp_10 = (long)A_Q12_tmp[ 10 ];
			final long A_Q12_tmp_11 = (long)A_Q12_tmp[ 11 ];
			final long A_Q12_tmp_12 = (long)A_Q12_tmp[ 12 ];
			final long A_Q12_tmp_13 = (long)A_Q12_tmp[ 13 ];
			final long A_Q12_tmp_14 = (long)A_Q12_tmp[ 14 ];
			final long A_Q12_tmp_15 = (long)A_Q12_tmp[ 15 ];
			for( int i = Jdefine.MAX_LPC_ORDER, ie = Jdefine.MAX_LPC_ORDER + subframe_length; i < ie; i++ ) {
				/* Short-term prediction */
				// celt_assert( psDec.LPC_order == 10 || psDec.LPC_order == 16 );
				/* Avoids introducing a bias because silk_SMLAWB() always rounds to -inf */
				int LPC_pred_Q10 = this.LPC_order >> 1;
				int si = i;
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_0 ) >> 16);// - 1
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_1 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_2 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_3 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_4 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_5 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_6 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_7 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_8 ) >> 16);
				LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_9 ) >> 16);// - 10
				if( this.LPC_order == 16 ) {
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_10 ) >> 16);// - 11
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_11 ) >> 16);
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_12 ) >> 16);
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_13 ) >> 16);
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_14 ) >> 16);
					LPC_pred_Q10 += (int)((sLPC_Q14[ --si ] * A_Q12_tmp_15 ) >> 16);// - 16
				}

				/* Add prediction to LPC excitation */
				final int a = dim_Q14[ pres_Q14++ ];
				final int b = ((LPC_pred_Q10 > (Integer.MAX_VALUE >> 4) ?
						(Integer.MAX_VALUE >> 4) : (LPC_pred_Q10 < (Integer.MIN_VALUE >> 4) ? (Integer.MIN_VALUE >> 4) : LPC_pred_Q10)) << 4);
				sLPC_Q14[ i ] = (((a + b) & 0x80000000) == 0 ?
						(((a & b) & 0x80000000) != 0 ? Integer.MIN_VALUE : a + b) :
							(((a | b) & 0x80000000) == 0 ? Integer.MAX_VALUE : a + b) );

				/* Scale with gain */
				final int v = JSigProc_FIX.silk_RSHIFT_ROUND( ((int)(((long)sLPC_Q14[ i ] * Gain_Q10) >> 16)), 8 );
				xq[ pxq++ ] = (short)(v > Short.MAX_VALUE ? Short.MAX_VALUE : (v < Short.MIN_VALUE ? Short.MIN_VALUE : v));
			}

			/* Update LPC filter state */
			System.arraycopy( sLPC_Q14, subframe_length, sLPC_Q14, 0, Jdefine.MAX_LPC_ORDER );
			pexc_Q14 += subframe_length;
			// pxq      += subframe_length;// java: increment inside the loop
		}

		/* Save LPC state */
		System.arraycopy( sLPC_Q14, 0, this.sLPC_Q14_buf, 0, Jdefine.MAX_LPC_ORDER );
		// RESTORE_STACK;
	}
	// end decode_core.c

	// start decode_frame.c
	/**
	 * Decode frame
	 *
	 * java changed: return size of output frame, because ret status don't use, always 0.
	 *
	 * @param psDec I/O  Pointer to Silk decoder state
	 * @param psRangeDec I/O  Compressor data structure
	 * @param pOut O    Pointer to output speech frame
	 * @param pN O    Pointer to size of output frame
	 * @param lostFlag I    0: no loss, 1 loss, 2 decode fec
	 * @param condCoding I    The type of conditional coding to use
	 * @param arch I    Run-time architecture
	 * @return size of output frame
	 */
	final int silk_decode_frame(final Jec_dec psRangeDec,
			final short pOut[], final int outoffset,// java
			// final int[] pN,// java returned
			final int lostFlag, final int condCoding)//, final int arch)
	{
		// final int ret = 0;// java not using
		// SAVE_STACK;

		final int L = this.frame_length;
		final Jsilk_decoder_control psDecCtrl = new Jsilk_decoder_control();
		psDecCtrl.LTP_scale_Q14 = 0;

		/* Safety checks */
		// celt_assert( L > 0 && L <= MAX_FRAME_LENGTH );

		if( lostFlag == Jsilk_decoder.FLAG_DECODE_NORMAL ||
				( lostFlag == Jsilk_decoder.FLAG_DECODE_LBRR && this.LBRR_flags[ this.nFramesDecoded ] ) )
		{
			final short[] pulses = new short[(L + Jdefine.SHELL_CODEC_FRAME_LENGTH - 1) & ~(Jdefine.SHELL_CODEC_FRAME_LENGTH - 1)];
			/*********************************************/
			/* Decode quantization indices of side info  */
			/*********************************************/
			this.silk_decode_indices( psRangeDec, this.nFramesDecoded, lostFlag != 0, condCoding );

			/*********************************************/
			/* Decode quantization indices of excitation */
			/*********************************************/
			silk_decode_pulses( psRangeDec, pulses, this.indices.signalType,
								this.indices.quantOffsetType, this.frame_length );

			/********************************************/
			/* Decode parameters and pulse signal       */
			/********************************************/
			silk_decode_parameters( psDecCtrl, condCoding );

			/********************************************************/
			/* Run inverse NSQ                                      */
			/********************************************************/
			silk_decode_core( psDecCtrl, pOut, outoffset, pulses );//, arch );

			/********************************************************/
			/* Update PLC state                                     */
			/********************************************************/
			silk_PLC( psDecCtrl, pOut, outoffset, false );//, arch );

			this.lossCnt = 0;
			this.prevSignalType = this.indices.signalType;
			// celt_assert( psDec.prevSignalType >= 0 && psDec.prevSignalType <= 2 );

			/* A frame has been decoded without errors */
			this.first_frame_after_reset = false;
		} else {
			/* Handle packet loss by extrapolation */
			this.indices.signalType = (byte)this.prevSignalType;// FIXME implicit int to char
			silk_PLC( psDecCtrl, pOut, outoffset, true );//, arch );
		}

		/*************************/
		/* Update output buffer. */
		/*************************/
		// celt_assert( psDec.ltp_mem_length >= psDec.frame_length );
		final int mv_len = this.ltp_mem_length - this.frame_length;
		System.arraycopy( this.outBuf, this.frame_length, this.outBuf, 0, mv_len );
		System.arraycopy( pOut, outoffset, this.outBuf, mv_len, this.frame_length  );

		/************************************************/
		/* Comfort noise generation / estimation        */
		/************************************************/
		silk_CNG( psDecCtrl, pOut, outoffset, L );

		/****************************************************************/
		/* Ensure smooth connection of extrapolated and good frames     */
		/****************************************************************/
		silk_PLC_glue_frames( pOut, outoffset, L );

		/* Update some decoder state variables */
		this.lagPrev = psDecCtrl.pitchL[ this.nb_subfr - 1 ];

		/* Set output frame length */
		// pN[0] = L;// java returned

		// RESTORE_STACK;
		// return ret;// java always 0
		return L;// java frame length
	}
	// end decode_frame.c

	// start decode_pitch.c
	/**
	 * Pitch analyser function
	 *
	 * @param lagIndex I
	 * @param contourIndex O
	 * @param pitch_lags O    4 pitch values
	 * @param Fs_kHz I    sampling frequency (kHz)
	 * @param nb_subfr I    number of sub frames
	 */
	private static final void silk_decode_pitch(final short lagIndex, final byte contourIndex, final int pitch_lags[], final int Fs_kHz, final int nb_subfr)
	{
		//final byte[] Lag_CB_ptr;
		final byte[][] Lag_CB_ptr;

		if( Fs_kHz == 8 ) {
			if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
				// Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2[ 0 ];//[ 0 ];
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2;
				// cbk_size   = Jpitch_est_defines.PE_NB_CBKS_STAGE2_EXT;
			} else {
				// celt_assert( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR >> 1 );
				// Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2_10_ms[ 0 ];//[ 0 ];
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage2_10_ms;
				// cbk_size   = Jpitch_est_defines.PE_NB_CBKS_STAGE2_10MS;
			}
		} else {
			if( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR ) {
				// Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3[ 0 ];//[ 0 ];
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3;
				// cbk_size   = Jpitch_est_defines.PE_NB_CBKS_STAGE3_MAX;
			} else {
				// celt_assert( nb_subfr == Jpitch_est_defines.PE_MAX_NB_SUBFR >> 1 );
				// Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3_10_ms[ 0 ];//[ 0 ];
				Lag_CB_ptr = Jpitch_est_tables.silk_CB_lags_stage3_10_ms;
				// cbk_size   = Jpitch_est_defines.PE_NB_CBKS_STAGE3_10MS;
			}
		}

		final int min_lag = Jpitch_est_defines.PE_MIN_LAG_MS * Fs_kHz;
		final int max_lag = Jpitch_est_defines.PE_MAX_LAG_MS * Fs_kHz;
		final int lag = min_lag + lagIndex;

		for( int k = 0; k < nb_subfr; k++ ) {
			// pitch_lags[ k ] = lag + (int)Lag_CB_ptr[k * cbk_size + contourIndex];// FIXME dirty way using 2-dimensional array
			int v = lag + (int)Lag_CB_ptr[ k ][ contourIndex ];
			v = (v > max_lag ? max_lag : (v < min_lag ? min_lag : v));
			pitch_lags[ k ] = v;
		}
	}
	// end decode_pitch.c

	// start decode_parameters.c
	/**
	 * Decode parameters from payload
	 *
	 * @param psDec I/O  State
	 * @param psDecCtrl I/O  Decoder control
	 * @param condCoding I    The type of conditional coding to use
	 */
	private final void silk_decode_parameters(final Jsilk_decoder_control psDecCtrl, final int condCoding)
	{
		final short pNLSF_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
		final short pNLSF0_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];

		/* Dequant Gains */
		this.LastGainIndex = silk_gains_dequant( psDecCtrl.Gains_Q16, this.indices.GainsIndices,
								this.LastGainIndex, condCoding == Jdefine.CODE_CONDITIONALLY, this.nb_subfr );

		/****************/
		/* Decode NLSFs */
		/****************/
		silk_NLSF_decode( pNLSF_Q15, this.indices.NLSFIndices, this.psNLSF_CB );

		/* Convert NLSF parameters to AR prediction filter coefficients */
		silk_NLSF2A( psDecCtrl.PredCoef_Q12[ 1 ], pNLSF_Q15, this.LPC_order );// this.arch );

		/* If just reset, e.g., because internal Fs changed, do not allow interpolation */
		/* improves the case of packet loss in the first frame after a switch           */
		if( this.first_frame_after_reset ) {
			this.indices.NLSFInterpCoef_Q2 = 4;
		}

		int k = (int)this.indices.NLSFInterpCoef_Q2;// java
		if( k < 4 ) {
			/* Calculation of the interpolated NLSF0 vector from the interpolation factor, */
			/* the previous NLSF1, and the current NLSF1                                   */
			final short[] buff = this.prevNLSF_Q15;// java
			for( int i = 0; i < this.LPC_order; i++ ) {
				final int v = buff[ i ];// java
				pNLSF0_Q15[ i ] = (short)(v + ((k * ((int)pNLSF_Q15[ i ] - v)) >> 2));
			}

			/* Convert NLSF parameters to AR prediction filter coefficients */
			silk_NLSF2A( psDecCtrl.PredCoef_Q12[ 0 ], pNLSF0_Q15, this.LPC_order );// this.arch );
		} else {
			/* Copy LPC coefficients for first half from second half */
			System.arraycopy( psDecCtrl.PredCoef_Q12[ 1 ], 0, psDecCtrl.PredCoef_Q12[ 0 ], 0, this.LPC_order );
		}

		System.arraycopy( pNLSF_Q15, 0, this.prevNLSF_Q15, 0, this.LPC_order );

		/* After a packet loss do BWE of LPC coefs */
		if( 0 != this.lossCnt ) {
			silk_bwexpander( psDecCtrl.PredCoef_Q12[ 0 ], this.LPC_order, Jdefine.BWE_AFTER_LOSS_Q16 );
			silk_bwexpander( psDecCtrl.PredCoef_Q12[ 1 ], this.LPC_order, Jdefine.BWE_AFTER_LOSS_Q16 );
		}

		if( this.indices.signalType == Jdefine.TYPE_VOICED ) {
			/*********************/
			/* Decode pitch lags */
			/*********************/

			/* Decode pitch values */
			silk_decode_pitch( this.indices.lagIndex, this.indices.contourIndex, psDecCtrl.pitchL, this.fs_kHz, this.nb_subfr );

			/* Decode Codebook Index */
			final byte[][] cbk_ptr_Q7 = Jtables_LTP.silk_LTP_vq_ptrs_Q7[ this.indices.PERIndex ]; /* set pointer to start of codebook */

			final short[] buff = psDecCtrl.LTPCoef_Q14;// java
			final byte[] indx = this.indices.LTPIndex;// java
			for( k = 0; k < this.nb_subfr; k++ ) {
				// int Ix = psDec.indices.LTPIndex[ k ];
				final byte[] ptr = cbk_ptr_Q7[ indx[ k ] ];// java
				for( int i = 0, ko = k * Jdefine.LTP_ORDER; i < Jdefine.LTP_ORDER; i++ ) {
					// psDecCtrl.LTPCoef_Q14[ k * Jdefine.LTP_ORDER + i ] = (short)( (int)cbk_ptr_Q7[ Ix * Jdefine.LTP_ORDER + i ] << 7 );// FIXME dirty way ...
					// psDecCtrl.LTPCoef_Q14[ k * Jdefine.LTP_ORDER + i ] = (short)( (int)cbk_ptr_Q7[ Ix ][ i ] << 7 );
					buff[ ko++ ] = (short)( (int)ptr[ i ] << 7 );
				}
			}

			/**********************/
			/* Decode LTP scaling */
			/**********************/
			// final int Ix = psDec.indices.LTP_scaleIndex;
			psDecCtrl.LTP_scale_Q14 = (int)Jtables_other.silk_LTPScales_table_Q14[ this.indices.LTP_scaleIndex ];// [ Ix ];
		} else {
			// silk_memset( psDecCtrl.pitchL,      0,             psDec.nb_subfr * sizeof( opus_int   ) );
			final int[] ibuff = psDecCtrl.pitchL;// java
			for( int i = this.nb_subfr; i > 0; ) {
				ibuff[--i] = 0;
			}
			// silk_memset( psDecCtrl->LTPCoef_Q14, 0, LTP_ORDER * psDec->nb_subfr * sizeof( opus_int16 ) );
			final short[] sbuff = psDecCtrl.LTPCoef_Q14;// java
			for( int i = Jdefine.LTP_ORDER * this.nb_subfr; i > 0; ) {
				sbuff[--i] = 0;
			}
			this.indices.PERIndex  = 0;
			psDecCtrl.LTP_scale_Q14 = 0;
		}
	}
	// end decode_parameters.c
}
