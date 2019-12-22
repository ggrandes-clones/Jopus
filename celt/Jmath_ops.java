package celt;

/** mathops.c */
public final class Jmath_ops {
	// start mathops.c
	/**Compute floor(sqrt(_val)) with exact arithmetic.
	  _val must be greater than 0.
	  This has been tested on all possible 32-bit inputs greater than 0.*/
	public static final int isqrt32(int _val) {// java: using only with numbers less or equal then 0x7fffffff
		/*Uses the second method from
		http://www.azillionmonkeys.com/qed/sqroot.html
		The main idea is to search for the largest binary digit b such that
		(g+b)*(g+b) <= _val, and add it to the solution g.*/
		int g = 0;
		int bshift = (Jec_ctx.EC_ILOG( _val ) - 1) >> 1;
		int b = 1 << bshift;
		do {
			final int t = ((g << 1) + b) << bshift;
			if( t <= _val ) {
				g += b;
				_val -= t;
			}
			b >>= 1;
			bshift--;
		}
		while( bshift >= 0 );
		return g;
	}
	// end mathops.c
}
