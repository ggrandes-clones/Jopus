package silk;

// silk/control.h

/** Structure for controlling decoder operation and reading decoder status */
public final class Jsilk_DecControlStruct {
	/** I:   Number of channels; 1/2 */
	public int nChannelsAPI;
	/** I:   Number of channels; 1/2 */
	public int nChannelsInternal;
	/** I:   Output signal sampling rate in Hertz; 8000/12000/16000/24000/32000/44100/48000  */
	public int API_sampleRate;
	/** I:   Internal sampling rate used, in Hertz; 8000/12000/16000 */
	public int internalSampleRate;
	/** I:   Number of samples per packet in milliseconds; 10/20/40/60 */
	public int payloadSize_ms;
	/** O:   Pitch lag of previous frame (0 if unvoiced), measured in samples at 48 kHz */
	public int prevPitchLag;
	//
	public final void clear() {
		nChannelsAPI = 0;
		nChannelsInternal = 0;
		API_sampleRate = 0;
		internalSampleRate = 0;
		payloadSize_ms = 0;
		prevPitchLag = 0;
	}
	/**
	 * java: memcpy to test
	 * @param ctrl source
	 */
	public final void copyFrom(final Jsilk_DecControlStruct ctrl) {
		nChannelsAPI = ctrl.nChannelsAPI;
		nChannelsInternal = ctrl.nChannelsInternal;
		API_sampleRate = ctrl.API_sampleRate;
		internalSampleRate = ctrl.internalSampleRate;
		payloadSize_ms = ctrl.payloadSize_ms;
		prevPitchLag = ctrl.prevPitchLag;
	}
}
