package silk;

// structs.h

final class JSideInfoIndices {
	final byte GainsIndices[] = new byte[ Jdefine.MAX_NB_SUBFR ];
	final byte LTPIndex[] = new byte[ Jdefine.MAX_NB_SUBFR ];
	final byte NLSFIndices[] = new byte[ Jdefine.MAX_LPC_ORDER + 1 ];
	short      lagIndex;
	byte       contourIndex;
	byte       signalType;
	byte       quantOffsetType;
	byte       NLSFInterpCoef_Q2;
	byte       PERIndex;
	byte       LTP_scaleIndex;
	byte       Seed;
	//
	void clear() {
		int i = Jdefine.MAX_NB_SUBFR;
		do {
			GainsIndices[--i] = 0;
		} while( i > 0 );
		i = Jdefine.MAX_NB_SUBFR;
		do {
			LTPIndex[--i] = 0;
		} while( i > 0 );
		i = Jdefine.MAX_LPC_ORDER + 1;
		do {
			NLSFIndices[--i] = 0;
		} while( i > 0 );
		lagIndex = 0;
		contourIndex = 0;
		signalType = 0;
		quantOffsetType = 0;
		NLSFInterpCoef_Q2 = 0;
		PERIndex = 0;
		LTP_scaleIndex = 0;
		Seed = 0;
	}
	final void copyFrom(final JSideInfoIndices indices) {
		System.arraycopy( indices.GainsIndices, 0, GainsIndices, 0, Jdefine.MAX_NB_SUBFR );
		System.arraycopy( indices.LTPIndex, 0, LTPIndex, 0, Jdefine.MAX_NB_SUBFR );
		System.arraycopy( indices.NLSFIndices, 0, NLSFIndices, 0, Jdefine.MAX_LPC_ORDER + 1 );
		lagIndex = indices.lagIndex;
		contourIndex = indices.contourIndex;
		signalType = indices.signalType;
		quantOffsetType = indices.quantOffsetType;
		NLSFInterpCoef_Q2 = indices.NLSFInterpCoef_Q2;
		PERIndex = indices.PERIndex;
		LTP_scaleIndex = indices.LTP_scaleIndex;
		Seed = indices.Seed;
	}
}
