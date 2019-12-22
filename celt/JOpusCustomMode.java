package celt;

// modes.h

/** Mode definition (opaque)
@brief Mode definition
*/
abstract class JOpusCustomMode extends Jcelt_codec_API {
	int         Fs;
	public int  overlap;

	public int         nbEBands;
	int         effEBands;
	public final float preemph[] = new float[4];
	/** Definition for each "pseudo-critical band" */
	short[]     eBands;

	public int  maxLM;
	int         nbShortMdcts;
	public int  shortMdctSize;

	/** Number of lines in the matrix below */
	int         nbAllocVectors;
	/** Number of bits in each band for several rates */
	char[]      allocVectors;// java uint8 to char
	short[]     logN;

	public float[] window;
	public final Jmdct_lookup mdct = new Jmdct_lookup();
	final JPulseCache cache = new JPulseCache();
	//
	JOpusCustomMode() {
	}
}
