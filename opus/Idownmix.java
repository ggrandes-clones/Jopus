package opus;

/**
 * java: interface for downmix function
 */
abstract class Idownmix {
	abstract void downmix(final Object _x, int xoffset,// java
			final float[] sub, int suboffset, final int subframe,
			final int offset, final int c1, final int c2, final int C);
}
