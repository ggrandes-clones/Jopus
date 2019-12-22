package celt;

// celt.h

public final class JAnalysisInfo {
	public boolean valid;
	public float tonality;
	public float tonality_slope;
	public float noisiness;
	public float activity;
	public float music_prob;
	public float music_prob_min;
	public float music_prob_max;
	public int bandwidth;
	public float activity_probability;
	public float max_pitch_ratio;
	/* Store as Q6 char to save space. */
	public final char leak_boost[] = new char[ Jcelt.LEAK_BANDS ];// java unsigned char. used range 0-255
	//
	public final void clear() {
		valid = false;
		tonality = 0;
		tonality_slope = 0;
		noisiness = 0;
		activity = 0;
		music_prob = 0;
		music_prob_min = 0;
		music_prob_max = 0;
		bandwidth = 0;
		activity_probability = 0;
		max_pitch_ratio = 0;
		int i = Jcelt.LEAK_BANDS;
		do {
			leak_boost[ --i ] = 0;
		} while( i > 0 );
	}
	public final void copyFrom(final JAnalysisInfo info) {
		valid = info.valid;
		tonality = info.tonality;
		tonality_slope = info.tonality_slope;
		noisiness = info.noisiness;
		activity = info.activity;
		music_prob = info.music_prob;
		music_prob_min = info.music_prob_min;
		music_prob_max = info.music_prob_max;
		bandwidth = info.bandwidth;
		activity_probability = info.activity_probability;
		max_pitch_ratio = info.max_pitch_ratio;
		System.arraycopy( info.leak_boost, 0, leak_boost, 0, Jcelt.LEAK_BANDS );
	}
}
