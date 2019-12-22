package silk;

import celt.Jec_dec;

//dec_API.c

/************************/
/* Decoder Super Struct */
/************************/
public final class Jsilk_decoder extends Jdec_API {
	// start control.h
	/* Decoder API flags */
	static final int FLAG_DECODE_NORMAL = 0;
	private static final int FLAG_PACKET_LOST   = 1;
	static final int FLAG_DECODE_LBRR   = 2;
	// end start control.h

	private final Jsilk_decoder_state channel_state[] = new Jsilk_decoder_state[ Jdefine.DECODER_NUM_CHANNELS ];
	private final Jstereo_dec_state   sStereo = new Jstereo_dec_state();
	private int                       nChannelsAPI;
	private int                       nChannelsInternal;
	private boolean                   prev_decode_only_middle;
	//
	public Jsilk_decoder() {
		for( int i = 0; i < Jdefine.DECODER_NUM_CHANNELS; i++ ) {
			channel_state[i] = new Jsilk_decoder_state();
		}
	}
	public final void copyFrom(final Jsilk_decoder d) {
		int i = Jdefine.DECODER_NUM_CHANNELS;
		do {
			i--;
			channel_state[i].copyFrom( d.channel_state[i] );
		} while( i > 0 );
		sStereo.copyFrom( d.sStereo );
		nChannelsAPI = d.nChannelsAPI;
		nChannelsInternal = d.nChannelsInternal;
		prev_decode_only_middle = d.prev_decode_only_middle;
	}

	// stereo_decode_pred.c
	/**
	 * Decode mid/side predictors
	 *
	 * @param psRangeDec I/O  Compressor data structure
	 * @param pred_Q13 O    Predictors
	 */
	private static final void silk_stereo_decode_pred(final Jec_dec psRangeDec, final int pred_Q13[])
	{
		int n;
		final int ix[][] = new int[ 2 ][ 3 ];
		int low_Q13, step_Q13;

		/* Entropy decoding */
		n = psRangeDec.ec_dec_icdf( Jtables_other.silk_stereo_pred_joint_iCDF, 0, 8 );
		ix[ 0 ][ 2 ] = n / 5;
		ix[ 1 ][ 2 ] = n - 5 * ix[ 0 ][ 2 ];
		for( n = 0; n < 2; n++ ) {
			ix[ n ][ 0 ] = psRangeDec.ec_dec_icdf( Jtables_other.silk_uniform3_iCDF, 0, 8 );
			ix[ n ][ 1 ] = psRangeDec.ec_dec_icdf( Jtables_other.silk_uniform5_iCDF, 0, 8 );
		}

		/* Dequantize */
		for( n = 0; n < 2; n++ ) {
			ix[ n ][ 0 ] += 3 * ix[ n ][ 2 ];
			low_Q13 = Jtables_other.silk_stereo_pred_quant_Q13[ ix[ n ][ 0 ] ];
			// step_Q13 = Jmacros.silk_SMULWB( Jtables_other.silk_stereo_pred_quant_Q13[ ix[ n ][ 0 ] + 1 ] - low_Q13,
			//				SILK_FIX_CONST( 0.5 / Jdefine.STEREO_QUANT_SUB_STEPS, 16 ) );
			step_Q13 = (int)(((Jtables_other.silk_stereo_pred_quant_Q13[ ix[ n ][ 0 ] + 1 ] - low_Q13) *
						(long)((0.5 / Jdefine.STEREO_QUANT_SUB_STEPS) * (1 << 16) + .5)) >> 16);
			pred_Q13[ n ] = ( low_Q13 + step_Q13 * ((ix[ n ][ 1 ] << 1) + 1) );
		}

		/* Subtract second from first predictor (helps when actually applying these) */
		pred_Q13[ 0 ] -= pred_Q13[ 1 ];
	}

	/**
	 * Decode mid-only flag
	 *
	 * java changed: return decode_only_mid, Flag that only mid channel has been coded
	 *
	 * @param psRangeDec I/O  Compressor data structure
	 * @param decode_only_mid O    Flag that only mid channel has been coded
	 * @return Flag that only mid channel has been coded
	 */
	private static final boolean silk_stereo_decode_mid_only(final Jec_dec psRangeDec/*, final int[] decode_only_mid*/)
	{
		/* Decode flag that only mid channel is coded */
		return /*decode_only_mid[0] = */psRangeDec.ec_dec_icdf( Jtables_other.silk_stereo_only_code_mid_iCDF, 0, 8 ) != 0;
	}
	// end  stereo_decode_pred.c

	// start dec_API.c
	/*********************/
	/* Decoder functions */
	/*********************/
	/**
	 *
	 * @param decSizeBytes Number of bytes in SILK decoder state
	 * @return O    Returns error code
	 */
	/*public static final int silk_Get_Decoder_Size(int[] decSizeBytes)
	{
		final int ret = Jerrors.SILK_NO_ERROR;

		decSizeBytes[0] = sizeof( Jsilk_decoder );

		return ret;
	}*/

	/** Reset decoder state
	 * @param decState I/O  State
	 * @return O    Returns error code
	 */
	public final int silk_InitDecoder()
	{
		int ret = Jerrors.SILK_NO_ERROR;
		final Jsilk_decoder_state[] state = this.channel_state;

		for( int n = 0; n < Jdefine.DECODER_NUM_CHANNELS; n++ ) {
			ret = state[ n ].silk_init_decoder();
		}
		this.sStereo.clear();
		/* Not strictly needed, but it's cleaner that way */
		this.prev_decode_only_middle = false;

		return ret;
	}

	/**
	 * Decode a frame
	 *
	 * @param decState I/O  State
	 * @param decControl I/O  Control Structure
	 * @param lostFlag I    0: no loss, 1 loss, 2 decode fec
	 * @param newPacketFlag I    Indicates first decoder call for this packet
	 * @param psRangeDec I/O  Compressor data structure
	 * @param samplesOut O    Decoded output speech vector
	 * @param nSamplesOut O    Number of samples decoded
	 * @param arch I    Run-time architecture
	 * @return O    Returns error code
	 */
	public final int silk_Decode(final Jsilk_DecControlStruct decControl,
			final int lostFlag, final boolean newPacketFlag,
			final Jec_dec psRangeDec,
			final short[] samplesOut, int soffset,// java
			final int[] nSamplesOut)// , int arch)
	{
		int ret = Jerrors.SILK_NO_ERROR;
		final int MS_pred_Q13[] = new int[ 2 ];// = { 0 };
		//final Jsilk_decoder psDec = ( silk_decoder * )decState;
		final Jsilk_decoder_state[] ch_state = this.channel_state;
		// SAVE_STACK;

		// celt_assert( decControl.nChannelsInternal == 1 || decControl.nChannelsInternal == 2 );

		/**********************************/
		/* Test if first frame in payload */
		/**********************************/
		final int dec_ctrl_channels = decControl.nChannelsInternal;// java
		if( newPacketFlag ) {
			for( int n = 0; n < dec_ctrl_channels; n++ ) {
				ch_state[ n ].nFramesDecoded = 0;  /* Used to count frames in packet */
			}
		}

		/* If Mono . Stereo transition in bitstream: init state of second channel */
		if( dec_ctrl_channels > this.nChannelsInternal ) {
			ret += ch_state[ 1 ].silk_init_decoder();
		}

		final boolean stereo_to_mono = dec_ctrl_channels == 1 && this.nChannelsInternal == 2 &&
						(decControl.internalSampleRate == 1000 * ch_state[ 0 ].fs_kHz);

		if( ch_state[ 0 ].nFramesDecoded == 0 ) {
			for( int n = 0; n < dec_ctrl_channels; n++ ) {
				if( decControl.payloadSize_ms == 0 ) {
					/* Assuming packet loss, use 10 ms */
					ch_state[ n ].nFramesPerPacket = 1;
					ch_state[ n ].nb_subfr = 2;
				} else if( decControl.payloadSize_ms == 10 ) {
					ch_state[ n ].nFramesPerPacket = 1;
					ch_state[ n ].nb_subfr = 2;
				} else if( decControl.payloadSize_ms == 20 ) {
					ch_state[ n ].nFramesPerPacket = 1;
					ch_state[ n ].nb_subfr = 4;
				} else if( decControl.payloadSize_ms == 40 ) {
					ch_state[ n ].nFramesPerPacket = 2;
					ch_state[ n ].nb_subfr = 4;
				} else if( decControl.payloadSize_ms == 60 ) {
					ch_state[ n ].nFramesPerPacket = 3;
					ch_state[ n ].nb_subfr = 4;
				} else {
					// celt_assert( 0 );
					// RESTORE_STACK;
					return Jerrors.SILK_DEC_INVALID_FRAME_SIZE;
				}
				final int fs_kHz_dec = (decControl.internalSampleRate >> 10) + 1;
				if( fs_kHz_dec != 8 && fs_kHz_dec != 12 && fs_kHz_dec != 16 ) {
					// celt_assert( 0 );
					// RESTORE_STACK;
					return Jerrors.SILK_DEC_INVALID_SAMPLING_FREQUENCY;
				}
				ret += ch_state[ n ].silk_decoder_set_fs( fs_kHz_dec, decControl.API_sampleRate );
			}
		}

		if( decControl.nChannelsAPI == 2 && dec_ctrl_channels == 2 && ( this.nChannelsAPI == 1 || this.nChannelsInternal == 1 ) ) {
			// silk_memset( psDec.sStereo.pred_prev_Q13, 0, sizeof( psDec.sStereo.pred_prev_Q13 ) );
			short[] buff = this.sStereo.pred_prev_Q13;// java
			for( int i = 0, ie = buff.length; i < ie; i++ ) {
				buff[i] = 0;
			}
			buff = this.sStereo.sSide;// java
			// silk_memset( psDec.sStereo.sSide, 0, sizeof( psDec.sStereo.sSide ) );
			for( int i = 0, ie = buff.length; i < ie; i++ ) {
				buff[i] = 0;
			}
			ch_state[ 1 ].resampler_state.copyFrom( ch_state[ 0 ].resampler_state );
		}
		this.nChannelsAPI      = decControl.nChannelsAPI;
		this.nChannelsInternal = dec_ctrl_channels;

		if( decControl.API_sampleRate > Jdefine.MAX_API_FS_KHZ * 1000 || decControl.API_sampleRate < 8000 ) {
			ret = Jerrors.SILK_DEC_INVALID_SAMPLING_FREQUENCY;
			// RESTORE_STACK;
			return( ret );
		}

		boolean decode_only_middle = false;
		if( lostFlag != FLAG_PACKET_LOST && ch_state[ 0 ].nFramesDecoded == 0 ) {
			/* First decoder call for this payload */
			/* Decode VAD flags and LBRR flag */
			for( int n = 0; n < dec_ctrl_channels; n++ ) {
				final Jsilk_decoder_state st = ch_state[ n ];// java
				for( int i = 0; i < st.nFramesPerPacket; i++ ) {
					st.VAD_flags[ i ] = psRangeDec.ec_dec_bit_logp( 1 );
				}
				st.LBRR_flag = psRangeDec.ec_dec_bit_logp( 1 );
			}
			/* Decode LBRR flags */
			for( int n = 0; n < dec_ctrl_channels; n++ ) {
				final Jsilk_decoder_state st = ch_state[ n ];// java
				final boolean[] buff = st.LBRR_flags;// java
				for( int i = 0, ie = buff.length; i < ie; i++ ) {
					buff[i] = false;
				}
				if( st.LBRR_flag ) {
					if( st.nFramesPerPacket == 1 ) {
						st.LBRR_flags[ 0 ] = true;
					} else {
						final int LBRR_symbol = psRangeDec.ec_dec_icdf( Jtables_other.silk_LBRR_flags_iCDF_ptr[ st.nFramesPerPacket - 2 ], 0, 8 ) + 1;
						for( int i = 0, ie = st.nFramesPerPacket; i < ie; i++ ) {
							st.LBRR_flags[ i ] = (((LBRR_symbol >> i) & 1) != 0);
						}
					}
				}
			}

			if( lostFlag == FLAG_DECODE_NORMAL ) {
				/* Regular decoding: skip all LBRR data */
				for( int i = 0, ie = ch_state[ 0 ].nFramesPerPacket; i < ie; i++ ) {
					for( int n = 0; n < dec_ctrl_channels; n++ ) {
						final Jsilk_decoder_state st = ch_state[ n ];// java
						if( st.LBRR_flags[ i ] ) {
							if( dec_ctrl_channels == 2 && n == 0 ) {
								silk_stereo_decode_pred( psRangeDec, MS_pred_Q13 );
								if( ! ch_state[ 1 ].LBRR_flags[ i ] ) {
									decode_only_middle = silk_stereo_decode_mid_only( psRangeDec/*, &decode_only_middle*/ );
								}
							}
							/* Use conditional coding if previous frame available */
							final int condCoding = ( i > 0 && st.LBRR_flags[ i - 1 ] ) ? Jdefine.CODE_CONDITIONALLY : Jdefine.CODE_INDEPENDENTLY;

							st.silk_decode_indices( psRangeDec, i, true, condCoding );

							final short pulses[] = new short[ Jdefine.MAX_FRAME_LENGTH ];
							silk_decode_pulses( psRangeDec, pulses, st.indices.signalType,
									st.indices.quantOffsetType, st.frame_length );
						}
					}
				}
			}
		}

		/* Get MS predictor index */
		if( dec_ctrl_channels == 2 ) {
			if( lostFlag == FLAG_DECODE_NORMAL ||
				( lostFlag == FLAG_DECODE_LBRR && ch_state[ 0 ].LBRR_flags[ ch_state[ 0 ].nFramesDecoded ] ) )
			{
				silk_stereo_decode_pred( psRangeDec, MS_pred_Q13 );
				/* For LBRR data, decode mid-only flag only if side-channel's LBRR flag is false */
				if( ( lostFlag == FLAG_DECODE_NORMAL && ! ch_state[ 1 ].VAD_flags[ ch_state[ 0 ].nFramesDecoded ] ) ||
						( lostFlag == FLAG_DECODE_LBRR && ! ch_state[ 1 ].LBRR_flags[ ch_state[ 0 ].nFramesDecoded ] ) )
				{
					decode_only_middle = silk_stereo_decode_mid_only( psRangeDec/*, &decode_only_middle*/ );
				} else {
					decode_only_middle = false;
				}
			} else {
				for( int n = 0; n < 2; n++ ) {
					MS_pred_Q13[ n ] = this.sStereo.pred_prev_Q13[ n ];
				}
			}
		}

		/* Reset side channel decoder prediction memory for first frame with side coding */
		if( dec_ctrl_channels == 2 && ! decode_only_middle && this.prev_decode_only_middle ) {
			//silk_memset( psDec.channel_state[ 1 ].outBuf, 0, sizeof(psDec.channel_state[ 1 ].outBuf) );
			final short[] sbuff = this.channel_state[ 1 ].outBuf;// java
			for( int i = 0, ie = sbuff.length; i < ie; i++ ) {
				sbuff[i] = 0;
			}
			//silk_memset( psDec.channel_state[ 1 ].sLPC_Q14_buf, 0, sizeof(psDec.channel_state[ 1 ].sLPC_Q14_buf) );
			final int[] ibuff = this.channel_state[ 1 ].sLPC_Q14_buf;// java
			for( int i = 0, ie = ibuff.length; i < ie; i++ ) {
				ibuff[i] = 0;
			}
			this.channel_state[ 1 ].lagPrev        = 100;
			this.channel_state[ 1 ].LastGainIndex  = 10;
			this.channel_state[ 1 ].prevSignalType = Jdefine.TYPE_NO_VOICE_ACTIVITY;
			this.channel_state[ 1 ].first_frame_after_reset = true;
		}

		/* Check if the temp buffer fits into the output PCM buffer. If it fits,
		   we can delay allocating the temp buffer until after the SILK peak stack
		   usage. We need to use a < and not a <= because of the two extra samples. */
		final boolean delay_stack_alloc = decControl.internalSampleRate * dec_ctrl_channels < decControl.API_sampleRate * decControl.nChannelsAPI;
		final short[] samplesOut1_tmp_storage1 = new short[delay_stack_alloc ? 0 : dec_ctrl_channels * (ch_state[ 0 ].frame_length + 2)];
		short[] samples_buff;// java
		// it could be possible using delay_stack_alloc ? samplesOut[ samplesOut1_tmp[ x ] ] : samplesOut1_tmp_storage1[ samplesOut1_tmp[ x ] ]
		final int samplesOut1_tmp[] = new int[ 2 ];// samles_buff[ samplesOut1_tmp[ x ] ]
		if( delay_stack_alloc )
		{
			samples_buff = samplesOut;// java
			samplesOut1_tmp[ 0 ] = soffset;// samplesOut;
			samplesOut1_tmp[ 1 ] = soffset + ch_state[ 0 ].frame_length + 2;// samplesOut + channel_state[ 0 ].frame_length + 2;
		} else {
			samples_buff = samplesOut1_tmp_storage1;
			samplesOut1_tmp[ 0 ] = 0;// samplesOut1_tmp_storage1;
			samplesOut1_tmp[ 1 ] = ch_state[ 0 ].frame_length + 2;// samplesOut1_tmp_storage1 + channel_state[ 0 ].frame_length + 2;
		}

		final boolean has_side = (lostFlag == FLAG_DECODE_NORMAL) ? ! decode_only_middle :
					! this.prev_decode_only_middle ||
					(dec_ctrl_channels == 2 && lostFlag == FLAG_DECODE_LBRR && ch_state[1].LBRR_flags[ ch_state[1].nFramesDecoded ] );
		/* Call decoder for one frame */
		int nSamplesOutDec = 0;// java = 0 to avoid warning "The local variable nSamplesOutDec may not have been initialized"
		for( int n = 0; n < dec_ctrl_channels; n++ ) {
			if( n == 0 || has_side ) {
				final int FrameIndex = ch_state[ 0 ].nFramesDecoded - n;
				/* Use independent coding if no previous frame available */
				int condCoding;
				if( FrameIndex <= 0 ) {
					condCoding = Jdefine.CODE_INDEPENDENTLY;
				} else if( lostFlag == FLAG_DECODE_LBRR ) {
					condCoding = ch_state[ n ].LBRR_flags[ FrameIndex - 1 ] ? Jdefine.CODE_CONDITIONALLY : Jdefine.CODE_INDEPENDENTLY;
				} else if( n > 0 && this.prev_decode_only_middle ) {
					/* If we skipped a side frame in this packet, we don't
					   need LTP scaling; the LTP state is well-defined. */
					condCoding = Jdefine.CODE_INDEPENDENTLY_NO_LTP_SCALING;
				} else {
					condCoding = Jdefine.CODE_CONDITIONALLY;
				}
				// ret += Jdecode_frame.silk_decode_frame( channel_state[ n ], psRangeDec, &samplesOut1_tmp[ n ][ 2 ], &nSamplesOutDec, lostFlag, condCoding );//, arch);
				// java changed
				nSamplesOutDec = ch_state[ n ].silk_decode_frame( psRangeDec, samples_buff, samplesOut1_tmp[ n ] + 2, lostFlag, condCoding );//, arch);
			} else {
				// silk_memset( &samplesOut1_tmp[ n ][ 2 ], 0, nSamplesOutDec * sizeof( opus_int16 ) );
				for( int i = samplesOut1_tmp[ n ] + 2, ie = i + nSamplesOutDec; i < ie; i++ ) {
					samples_buff[ i ] = 0;
				}
			}
			ch_state[ n ].nFramesDecoded++;
		}

		if( decControl.nChannelsAPI == 2 && dec_ctrl_channels == 2 ) {
			/* Convert Mid/Side to Left/Right */
			// Jstereo_MS_to_LR.silk_stereo_MS_to_LR( psDec.sStereo, samplesOut1_tmp[ 0 ], samplesOut1_tmp[ 1 ], MS_pred_Q13, channel_state[ 0 ].fs_kHz, nSamplesOutDec );
			this.sStereo.silk_stereo_MS_to_LR(
							samples_buff,samplesOut1_tmp[ 0 ], samples_buff, samplesOut1_tmp[ 1 ],
							MS_pred_Q13, ch_state[ 0 ].fs_kHz, nSamplesOutDec );
		} else {
			/* Buffering */
			int v = samplesOut1_tmp[ 0 ];// java
			samples_buff[ v++ ] = this.sStereo.sMid[0]; samples_buff[ v-- ] = this.sStereo.sMid[ 1 ];
			v += nSamplesOutDec;// java
			this.sStereo.sMid[0] = samples_buff[v++]; this.sStereo.sMid[1] = samples_buff[v];
		}

		/* Number of output samples */
		int n_samples = (nSamplesOutDec * decControl.API_sampleRate) / (ch_state[ 0 ].fs_kHz * 1000);
		nSamplesOut[0] = n_samples;// java

		/* Set up pointers to temp buffers */
		final short[] samplesOut2_tmp = new short[decControl.nChannelsAPI == 2 ? n_samples : 0];
		final short[] resample_out_ptr = (decControl.nChannelsAPI == 2 ? samplesOut2_tmp : samplesOut);
		int resample_out_ptr_offset = (decControl.nChannelsAPI == 2 ? 0 : soffset);// java added resample_out_ptr[resample_out_ptr_offset]

		final short[] samplesOut1_tmp_storage2 = new short[ delay_stack_alloc ?
						dec_ctrl_channels * (ch_state[ 0 ].frame_length + 2)
						: 0 ];
		if ( delay_stack_alloc ) {
			System.arraycopy( samplesOut, soffset, samplesOut1_tmp_storage2, 0, dec_ctrl_channels * (ch_state[ 0 ].frame_length + 2) );
			samples_buff = samplesOut1_tmp_storage2;// java
			samplesOut1_tmp[ 0 ] = 0;// samplesOut1_tmp_storage2;
			samplesOut1_tmp[ 1 ] = ch_state[ 0 ].frame_length + 2;// samplesOut1_tmp_storage2 + channel_state[ 0 ].frame_length + 2;
		}
		for( int n = 0, ne = (decControl.nChannelsAPI < dec_ctrl_channels ? decControl.nChannelsAPI : dec_ctrl_channels); n < ne; n++ ) {

			/* Resample decoded signal to API_sampleRate */
			// ret += Jresampler.silk_resampler( channel_state[ n ].resampler_state, resample_out_ptr, &samplesOut1_tmp[ n ][ 1 ], nSamplesOutDec );
			ret += ch_state[ n ].resampler_state.silk_resampler( resample_out_ptr, resample_out_ptr_offset, samples_buff, samplesOut1_tmp[ n ] + 1, nSamplesOutDec );

			/* Interleave if stereo output and stereo stream */
			if( decControl.nChannelsAPI == 2 ) {
				for( int i = 0; i < n_samples; i++ ) {
					samplesOut[ soffset + n + (i << 1) ] = resample_out_ptr[ resample_out_ptr_offset + i ];
				}
			}
		}

		/* Create two channel output from mono stream */
		if( decControl.nChannelsAPI == 2 && dec_ctrl_channels == 1 ) {
			if ( stereo_to_mono ) {
				/* Resample right channel for newly collapsed stereo just in case
				   we weren't doing collapsing when switching to mono */
				// ret += Jresampler.silk_resampler( channel_state[ 1 ].resampler_state, resample_out_ptr, &samplesOut1_tmp[ 0 ][ 1 ], nSamplesOutDec );
				ret += ch_state[ 1 ].resampler_state.silk_resampler( resample_out_ptr, resample_out_ptr_offset, samples_buff, samplesOut1_tmp[ 0 ] + 1, nSamplesOutDec );

				soffset++;// java
				for( n_samples += resample_out_ptr_offset; resample_out_ptr_offset < n_samples; soffset += 2 ) {
					samplesOut[ soffset ] = resample_out_ptr[ resample_out_ptr_offset++ ];
				}
			} else {
				n_samples <<= 1;// java
				for( n_samples += soffset; soffset < n_samples; soffset += 2 ) {
					samplesOut[ soffset + 1 ] = samplesOut[ soffset ];
				}
			}
		}

		/* Export pitch lag, measured at 48 kHz sampling rate */
		if( ch_state[ 0 ].prevSignalType == Jdefine.TYPE_VOICED ) {
			final int mult_tab[/* 3 */] = { 6, 4, 3 };
			decControl.prevPitchLag = ch_state[ 0 ].lagPrev * mult_tab[ ( ch_state[ 0 ].fs_kHz - 8 ) >> 2 ];
		} else {
			decControl.prevPitchLag = 0;
		}

		if( lostFlag == FLAG_PACKET_LOST ) {
			/* On packet loss, remove the gain clamping to prevent having the energy "bounce back"
			   if we lose packets when the energy is going down */
			final Jsilk_decoder_state[] state = this.channel_state;// java
			for( int i = 0, n = this.nChannelsInternal; i < n; i++ ) {
				state[ i ].LastGainIndex = 10;
			}
		} else {
			this.prev_decode_only_middle = decode_only_middle;
		}
		// RESTORE_STACK;
		return ret;
	}

/*#if 0
	 Getting table of contents for a packet
	opus_int silk_get_TOC(
		const opus_uint8                *payload,            I    Payload data
		const opus_int                  nBytesIn,            I    Number of input bytes
		const opus_int                  nFramesPerPayload,   I    Number of SILK frames per payload
		silk_TOC_struct                 *Silk_TOC            O    Type of content
	)
	{
		opus_int i, flags, ret = SILK_NO_ERROR;

		if( nBytesIn < 1 ) {
			return -1;
		}
		if( nFramesPerPayload < 0 || nFramesPerPayload > 3 ) {
			return -1;
		}

		silk_memset( Silk_TOC, 0, sizeof( *Silk_TOC ) );

		 For stereo, extract the flags for the mid channel
		flags = silk_RSHIFT( payload[ 0 ], 7 - nFramesPerPayload ) & ( silk_LSHIFT( 1, nFramesPerPayload + 1 ) - 1 );

		Silk_TOC.inbandFECFlag = flags & 1;
		for( i = nFramesPerPayload - 1; i >= 0 ; i-- ) {
			flags = silk_RSHIFT( flags, 1 );
			Silk_TOC.VADFlags[ i ] = flags & 1;
			Silk_TOC.VADFlag |= flags & 1;
		}

		return ret;
	}
#endif*/
	// end dec_API.c
}
