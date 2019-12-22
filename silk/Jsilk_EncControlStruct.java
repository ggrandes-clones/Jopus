package silk;

//silk/control.h

/***********************************************/
/* Structure for controlling encoder operation */
/***********************************************/
public final class Jsilk_EncControlStruct {
	/** I:   Number of channels; 1/2 */
	public int nChannelsAPI;

	/** I:   Number of channels; 1/2 */
	public int nChannelsInternal;

	/** I:   Input signal sampling rate in Hertz; 8000/12000/16000/24000/32000/44100/48000 */
	public int API_sampleRate;

	/** I:   Maximum internal sampling rate in Hertz; 8000/12000/16000 */
	public int maxInternalSampleRate;

	/** I:   Minimum internal sampling rate in Hertz; 8000/12000/16000 */
	public int minInternalSampleRate;

	/** I:   Soft request for internal sampling rate in Hertz; 8000/12000/16000 */
	public int desiredInternalSampleRate;

	/** I:   Number of samples per packet in milliseconds; 10/20/40/60 */
	public int payloadSize_ms;

	/** I:   Bitrate during active speech in bits/second; internally limited */
	public int bitRate;

	/** I:   Uplink packet loss in percent (0-100) */
	public int packetLossPercentage;

	/** I:   Complexity mode; 0 is lowest, 10 is highest complexity */
	public int complexity;

	/** I:   Flag to enable in-band Forward Error Correction (FEC); 0/1 */
	public boolean useInBandFEC;

	/** I:   Flag to actually code in-band Forward Error Correction (FEC) in the current packet; 0/1 */
	public boolean LBRR_coded;

	/** I:   Flag to enable discontinuous transmission (DTX); 0/1 */
	public boolean useDTX;

	/** I:   Flag to use constant bitrate */
	public boolean useCBR;

	/** I:   Maximum number of bits allowed for the frame */
	public int maxBits;

	/** I:   Causes a smooth downmix to mono */
	public boolean toMono;

	/** I:   Opus encoder is allowing us to switch bandwidth */
	public boolean opusCanSwitch;

	/** I: Make frames as independent as possible (but still use LPC) */
	public boolean reducedDependency;

	/** O:   Internal sampling rate used, in Hertz; 8000/12000/16000 */
	public int internalSampleRate;

	/** O: Flag that bandwidth switching is allowed (because low voice activity) */
	public boolean allowBandwidthSwitch;

	/** O:   Flag that SILK runs in WB mode without variable LP filter (use for switching between WB/SWB/FB) */
	public boolean inWBmodeWithoutVariableLP;

	/** O:   Stereo width */
	public int stereoWidth_Q14;

	/** O:   Tells the Opus encoder we're ready to switch */
	public boolean switchReady;

	/** O: SILK Signal type */
	public int signalType;

	/** O: SILK offset (dithering) */
	public int offset;
	//
	/**
	 * java version for c memset 0
	 */
	public final void clear() {
		nChannelsAPI = 0;
		nChannelsInternal = 0;
		API_sampleRate = 0;
		maxInternalSampleRate = 0;
		minInternalSampleRate = 0;
		desiredInternalSampleRate = 0;
		payloadSize_ms = 0;
		bitRate = 0;
		packetLossPercentage = 0;
		complexity = 0;
		useInBandFEC = false;
		LBRR_coded = false;
		useDTX = false;
		useCBR = false;
		maxBits = 0;
		toMono = false;
		opusCanSwitch = false;
		reducedDependency = false;
		internalSampleRate = 0;
		allowBandwidthSwitch = false;
		inWBmodeWithoutVariableLP = false;
		stereoWidth_Q14 = 0;
		switchReady = false;
		signalType = 0;
		offset = 0;
	}
	/**
	 * Java version for c operation =
	 * @param c another Jsilk_EncControlStruct to copy data from
	 */
	public final void copyFrom(final Jsilk_EncControlStruct c) {
		nChannelsAPI = c.nChannelsAPI;
		nChannelsInternal = c.nChannelsInternal;
		API_sampleRate = c.API_sampleRate;
		maxInternalSampleRate = c.maxInternalSampleRate;
		minInternalSampleRate = c.minInternalSampleRate;
		desiredInternalSampleRate = c.desiredInternalSampleRate;
		payloadSize_ms = c.payloadSize_ms;
		bitRate = c.bitRate;
		packetLossPercentage = c.packetLossPercentage;
		complexity = c.complexity;
		useInBandFEC = c.useInBandFEC;
		LBRR_coded = c.LBRR_coded;
		useDTX = c.useDTX;
		useCBR = c.useCBR;
		maxBits = c.maxBits;
		toMono = c.toMono;
		opusCanSwitch = c.opusCanSwitch;
		reducedDependency = c.reducedDependency;
		internalSampleRate = c.internalSampleRate;
		allowBandwidthSwitch = c.allowBandwidthSwitch;
		inWBmodeWithoutVariableLP = c.inWBmodeWithoutVariableLP;
		stereoWidth_Q14 = c.stereoWidth_Q14;
		switchReady = c.switchReady;
		signalType = c.signalType;
		offset = c.offset;
	}
}
