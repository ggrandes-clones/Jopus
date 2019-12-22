package silk;

// structs.h

/** Struct for CNG */
final class Jsilk_CNG_struct {// java: uses only in Jsilk_decoder_state, but has field with same name
	final int   CNG_exc_buf_Q14[] = new int[ Jdefine.MAX_FRAME_LENGTH ];
	final short CNG_smth_NLSF_Q15[] = new short[ Jdefine.MAX_LPC_ORDER ];
	final int   CNG_synth_state[] = new int[ Jdefine.MAX_LPC_ORDER ];
	int         CNG_smth_Gain_Q16;
	int         rand_seed;
	int         fs_kHz;
	//
	final void clear() {
		int i = Jdefine.MAX_FRAME_LENGTH;
		do {
			CNG_exc_buf_Q14[--i] = 0;
		} while( i > 0 );
		i = Jdefine.MAX_LPC_ORDER;
		do {
			CNG_smth_NLSF_Q15[--i] = 0;
		} while( i > 0 );
		i = Jdefine.MAX_LPC_ORDER;
		do {
			CNG_synth_state[--i] = 0;
		} while( i > 0 );
		CNG_smth_Gain_Q16 = 0;
		rand_seed = 0;
		fs_kHz = 0;
	}
	final void copyFrom(final Jsilk_CNG_struct s) {
		System.arraycopy( s.CNG_exc_buf_Q14, 0, CNG_exc_buf_Q14, 0, Jdefine.MAX_FRAME_LENGTH  );
		System.arraycopy( s.CNG_smth_NLSF_Q15, 0, CNG_smth_NLSF_Q15, 0, Jdefine.MAX_LPC_ORDER );
		System.arraycopy( s.CNG_synth_state, 0, CNG_synth_state, 0, Jdefine.MAX_LPC_ORDER );
		CNG_smth_Gain_Q16 = s.CNG_smth_Gain_Q16;
		rand_seed = s.rand_seed;
		fs_kHz = s.fs_kHz;
	}
}
