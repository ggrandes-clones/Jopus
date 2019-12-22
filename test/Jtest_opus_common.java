package test;

import celt.Jcelt;

/* Copyright (c) 2011 Xiph.Org Foundation
   Written by Gregory Maxwell */

// test_opus_common.h

final class Jtest_opus_common {
	/**
	 * java added the output value to control the offset
	 * @param _t
	 * @param _p
	 * @param poffset
	 * @param _k
	 * @param _x
	 * @param _y
	 * @return
	 */
	static final int deb2_impl(final byte[] _t, final byte[] _p, int poffset, final int _k, final int _x, final int _y)
	{
		int i;
		if( _x > 2 ) {
			if( _y < 3 ) {
				for( i = 0; i < _y; i++ )
				 {
					_p[--poffset] = _t[i + 1];// *(--*_p) = _t[i + 1];
				}
			}
		} else {
			_t[_x] = _t[_x - _y];
			poffset = deb2_impl( _t, _p, poffset, _k, _x + 1, _y );
			for( i = _t[_x - _y] + 1; i < _k; i++ ) {
				_t[_x] = (byte)i;
				poffset = deb2_impl( _t, _p, poffset, _k, _x + 1, _x );
			}
		}
		return poffset;
	}

	/** Generates a De Bruijn sequence (k,2) with length k^2
	 * @param _k
	 * @param _res
	 */
	static final void debruijn2(final int _k, final byte[] _res)
	{
		// byte[] p;
		byte[] t = new byte[_k << 1];
		// Arrays.fill( t, (byte)0 );
		// p = &_res[_k * _k];
		deb2_impl( t, _res, _k * _k, _k, 1, 1 );
		t = null;
	}

	static int Rz, Rw;
	/** MWC RNG of George Marsaglia
	 *
	 * @return the pseudo random value
	 */
	static final int fast_rand()
	{
		Rz = 36969 * (Rz & 65535) + (Rz >>> 16);// ((Rz >>> 16) & 65535);
		Rw = 18000 * (Rw & 65535) + (Rw >>> 16);// ((Rw >>> 16) & 65535);
		return (Rz << 16) + Rw;
	}
	static int iseed;

	@SuppressWarnings("boxing")
	static final void test_failed(final String file, final String line)
	{
		System.err.printf("\n ***************************************************\n");
		System.err.printf(" ***         A fatal error was detected.         ***\n");
		System.err.printf(" ***************************************************\n");
		System.err.printf("Please report this failure and include\n");
		System.err.printf("'make check SEED=%d fails %s at line %s for %s'\n", iseed, file, line, Jcelt.opus_get_version_string() );
		System.err.printf("and any relevant details about your system.\n\n");
		System.exit( 1 );// abort();
	}
	//#define test_failed() _test_failed(__FILE__, __LINE__);

	// void regression_test(void);
}