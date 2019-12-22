package celt;

// kiss_fft.h

final class Jkiss_twiddle_cpx {
	final float r;
	final float i;
	//
	Jkiss_twiddle_cpx(final float ir, final float ii) {
		r = ir;
		i = ii;
	}
	/**
	 * Set values
	 * @param cpx source
	 */
	Jkiss_twiddle_cpx(final Jkiss_twiddle_cpx cpx) {
		r = cpx.r;
		i = cpx.i;
	}
}
