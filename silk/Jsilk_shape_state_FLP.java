package silk;

// structs_FLP.h

/** Noise shaping analysis state */
final class Jsilk_shape_state_FLP {
	byte LastGainIndex;
	float HarmBoost_smth;
	float HarmShapeGain_smth;
	float Tilt_smth;
	//
	final void clear() {
		LastGainIndex = 0;
		HarmBoost_smth = 0;
		HarmShapeGain_smth = 0;
		Tilt_smth = 0;
	}
	final void copyFrom(final Jsilk_shape_state_FLP s) {
		LastGainIndex = s.LastGainIndex;
		HarmBoost_smth = s.HarmBoost_smth;
		HarmShapeGain_smth = s.HarmShapeGain_smth;
		Tilt_smth = s.Tilt_smth;
	}
}
