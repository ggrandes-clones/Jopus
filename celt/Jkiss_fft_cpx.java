package celt;

public final class Jkiss_fft_cpx {
	public float r;
	public float i;
	//
	/**
	 * Set values
	 * @param cpx source
	 */
	final void copyFrom(final Jkiss_fft_cpx cpx) {
		r = cpx.r;
		i = cpx.i;
	}
	final void set(final float real, final float image) {
		r = real;
		i = image;
	}
}
