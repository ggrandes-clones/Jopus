package celt;

import opus.Jopus_defines;

// celt_decoder.c

/**********************************************************************/
/*                                                                    */
/*                             DECODER                                */
/*                                                                    */
/**********************************************************************/

/** Decoder state
@brief Decoder state
*/
class JOpusCustomDecoder extends Jcelt_codec_API {
	private static final String CLASS_NAME = "JOpusCustomDecoder";

	static final int DECODE_BUFFER_SIZE = 2048;

	JOpusCustomMode mode;
	int overlap;
	int channels;
	int stream_channels;

	int downsample;
	int start;
	int end;
	boolean signalling;
	boolean disable_inv;
	// int arch;// java don't need

	/* Everything beyond this point gets cleared on a reset */
	//#define DECODER_RESET_START rng

	int rng;
	int error;
	int last_pitch_index;
	int loss_count;
	boolean skip_plc;
	int postfilter_period;
	int postfilter_period_old;
	float postfilter_gain;
	float postfilter_gain_old;
	int postfilter_tapset;
	int postfilter_tapset_old;

	final float preemph_memD[] = new float[2];
	float _decode_mem[] = null;//new float[1]; /* Size = channels*(DECODE_BUFFER_SIZE+mode->overlap) */
	float lpc[] = null;/* opus_val16 lpc[],  Size = channels*LPC_ORDER */
	float oldEBands[] = null;/* opus_val16 oldEBands[], Size = 2*mode->nbEBands */
	float oldLogE[] = null;/* opus_val16 oldLogE[], Size = 2*mode->nbEBands */
	float oldLogE2[] = null;/* opus_val16 oldLogE2[], Size = 2*mode->nbEBands */
	float backgroundLogE[] = null;/* opus_val16 backgroundLogE[], Size = 2*mode->nbEBands */
	//
	final void clear(final boolean isFull) {
		if( isFull ) {
			mode = null;
			overlap = 0;
			channels = 0;
			stream_channels = 0;
			downsample = 0;
			start = 0;
			end = 0;
			signalling = false;
			disable_inv = false;
			_decode_mem = null;// java: c uses dirty way to save this mem unclear
			lpc = null;
			oldEBands = null;
			oldLogE = null;
			oldLogE2 = null;
			backgroundLogE = null;
		}
		rng = 0;
		error = 0;
		last_pitch_index = 0;
		loss_count = 0;
		skip_plc = false;
		postfilter_period = 0;
		postfilter_period_old = 0;
		postfilter_gain = 0;
		postfilter_gain_old = 0;
		postfilter_tapset = 0;
		postfilter_tapset_old = 0;
		preemph_memD[0] = 0; preemph_memD[1] = 0;
		//
		if( _decode_mem != null ) {
			int i = _decode_mem.length;
			do {
				_decode_mem[ --i ] = 0f;
			} while( i > 0 );
		}
		if( lpc != null ) {
			int i = lpc.length;
			do {
				lpc[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldEBands != null ) {
			int i = oldEBands.length;
			do {
				oldEBands[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldLogE != null ) {
			int i = oldLogE.length;
			do {
				oldLogE[ --i ] = 0f;
			} while( i > 0 );
		}
		if( oldLogE2 != null ) {
			int i = oldLogE2.length;
			do {
				oldLogE2[ --i ] = 0f;
			} while( i > 0 );
		}
		if( backgroundLogE != null ) {
			int i = backgroundLogE.length;
			do {
				backgroundLogE[ --i ] = 0f;
			} while( i > 0 );
		}
	}
	public final void copyFrom(final JOpusCustomDecoder d) {
		mode = d.mode;
		overlap = d.overlap;
		channels = d.channels;
		stream_channels = d.stream_channels;
		downsample = d.downsample;
		start = d.start;
		end = d.end;
		signalling = d.signalling;
		disable_inv = d.disable_inv;
		rng = d.rng;
		error = d.error;
		last_pitch_index = d.last_pitch_index;
		loss_count = d.loss_count;
		skip_plc = d.skip_plc;
		postfilter_period = d.postfilter_period;
		postfilter_period_old = d.postfilter_period_old;
		postfilter_gain = d.postfilter_gain;
		postfilter_gain_old = d.postfilter_gain_old;
		postfilter_tapset = d.postfilter_tapset;
		postfilter_tapset_old = d.postfilter_tapset_old;
		preemph_memD[0] = 0; preemph_memD[1] = 0;
		System.arraycopy( d._decode_mem, 0, _decode_mem, 0, _decode_mem.length );
		System.arraycopy( d.lpc, 0, lpc, 0, lpc.length );
		System.arraycopy( d.oldEBands, 0, oldEBands, 0, oldEBands.length );
		System.arraycopy( d.oldLogE, 0, oldLogE, 0, oldLogE.length );
		System.arraycopy( d.oldLogE2, 0, oldLogE2, 0, oldLogE2.length );
		System.arraycopy( d.backgroundLogE, 0, backgroundLogE, 0, backgroundLogE.length );
	}
	// java: Object... args don't uses because impossible to control arg type
	// java: uses different functions for getters and setters
	// TODO may be better way is throwing exception, because return state don't checked
	/**
	 * Getters
	 *
	 * @param st
	 * @param request
	 * @param arg
	 * @return
	 */
	final int opus_custom_decoder_ctl(final int request, final Object[] arg)
	{
		if( arg == null || arg.length == 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}

		switch ( request )
		{
		case Jcelt.CELT_GET_AND_CLEAR_ERROR_REQUEST:
			{
				arg[0] = Integer.valueOf( this.error );
				this.error = 0;
			}
			break;
		case Jopus_defines.OPUS_GET_LOOKAHEAD_REQUEST:
			{
				arg[0] = Integer.valueOf( this.overlap / this.downsample );
			}
			break;
		case Jopus_defines.OPUS_GET_PITCH_REQUEST:
			{
				arg[0] = Integer.valueOf( this.postfilter_period );
			}
			break;
		case Jcelt.CELT_GET_MODE_REQUEST:
			{
				arg[0] = this.mode;
			}
			break;
		case Jopus_defines.OPUS_GET_FINAL_RANGE_REQUEST:
			{
				arg[0] = Long.valueOf( ((long)this.rng) & 0xffffffffL );// java truncate to uint32
			}
			break;
		case Jopus_defines.OPUS_GET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				arg[0] = Boolean.valueOf( this.disable_inv );
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * Setters for int
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return
	 */
	final int opus_custom_decoder_ctl(final int request, final int value)
	{
		switch ( request )
		{
		case Jcelt.CELT_SET_START_BAND_REQUEST:
			{
				if( value < 0 || value >= this.mode.nbEBands ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.start = value;
			}
			break;
		case Jcelt.CELT_SET_END_BAND_REQUEST:
			{
				if( value < 1 || value > this.mode.nbEBands ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.end = value;
			}
			break;
		case Jcelt.CELT_SET_CHANNELS_REQUEST:
			{
				if( value < 1 || value > 2 ) {
					return Jopus_defines.OPUS_BAD_ARG;
				}
				this.stream_channels = value;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * Setters for boolean
	 *
	 * @param st
	 * @param request
	 * @param value
	 * @return
	 */
	final int opus_custom_decoder_ctl(final int request, final boolean value)
	{
		switch ( request )
		{
		case Jcelt.CELT_SET_SIGNALLING_REQUEST:
			{
				this.signalling = value;
			}
			break;
		case Jopus_defines.OPUS_SET_PHASE_INVERSION_DISABLED_REQUEST:
			{
				this.disable_inv = value;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
	/**
	 * requests without arguments
	 *
	 * @param request
	 * @return
	 */
	final int opus_custom_decoder_ctl(final int request)
	{
		switch( request )
		{
		case Jopus_defines.OPUS_RESET_STATE:
			{
				this.clear( false );
				final float[] old_LogE = this.oldLogE;
				final float[] old_LogE2 = this.oldLogE2;
				for( int i = 0, ie = this.mode.nbEBands << 1; i < ie; i++ ) {
					old_LogE[ i ] = old_LogE2[ i ] = -28.f;
				}
				this.skip_plc = true;
			}
			break;
		default:
			System.err.println( CLASS_NAME + " unknown request: " + request );
			return Jopus_defines.OPUS_UNIMPLEMENTED;
		}
		return Jopus_defines.OPUS_OK;
	}
}
