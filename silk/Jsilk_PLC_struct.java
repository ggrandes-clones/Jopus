package silk;

// structs.h

// java: uses only in Jsilk_decoder_state, but has fields with same names
/** Struct for Packet Loss Concealment */
final class Jsilk_PLC_struct {
	/** Pitch lag to use for voiced concealment */
	int         pitchL_Q8;
	/** LTP coeficients to use for voiced concealment */
	final short LTPCoef_Q14[] = new short[ Jdefine.LTP_ORDER ];
	final short prevLPC_Q12[] = new short[ Jdefine.MAX_LPC_ORDER ];
	/** Was previous frame lost */
	boolean     last_frame_lost;
	/** Seed for unvoiced signal generation */
	int         rand_seed;
	/** Scaling of unvoiced random signal */
	short       randScale_Q14;
	int         conc_energy;
	int         conc_energy_shift;
	short       prevLTP_scale_Q14;
	final int   prevGain_Q16[] = new int[ 2 ];
	int         fs_kHz;
	int         nb_subfr;
	int         subfr_length;
	//
	final void clear() {
		pitchL_Q8 = 0;
		int i = Jdefine.LTP_ORDER;
		do {
			LTPCoef_Q14[--i] = 0;
		} while( i > 0 );
		i = Jdefine.MAX_LPC_ORDER;
		do {
			prevLPC_Q12[--i] = 0;
		} while( i > 0 );
		last_frame_lost = false;
		rand_seed = 0;
		randScale_Q14 = 0;
		conc_energy = 0;
		conc_energy_shift = 0;
		prevLTP_scale_Q14 = 0;
		prevGain_Q16[0] = 0; prevGain_Q16[1] = 0;
		fs_kHz = 0;
		nb_subfr = 0;
		subfr_length = 0;
	}
	final void copyFrom(final Jsilk_PLC_struct s) {
		pitchL_Q8 = s.pitchL_Q8;
		System.arraycopy( s.LTPCoef_Q14, 0, LTPCoef_Q14, 0, Jdefine.LTP_ORDER );
		System.arraycopy( s.prevLPC_Q12, 0, prevLPC_Q12, 0, Jdefine.MAX_LPC_ORDER );
		last_frame_lost = s.last_frame_lost;
		rand_seed = s.rand_seed;
		randScale_Q14 = s.randScale_Q14;
		conc_energy = s.conc_energy;
		conc_energy_shift = s.conc_energy_shift;
		prevLTP_scale_Q14 = s.prevLTP_scale_Q14;
		prevGain_Q16[0] = s.prevGain_Q16[0]; prevGain_Q16[1] = s.prevGain_Q16[1];
		fs_kHz = s.fs_kHz;
		nb_subfr = s.nb_subfr;
		subfr_length = s.subfr_length;
	}
}
