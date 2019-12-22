package celt;

// modes.h

final class JPulseCache {
	int size;
	short[] index;
	char[] bits;// java uint8 to char
	char[] caps;// java uint8 to char
	//
	JPulseCache() {
		size = 0;
		index = null;
		bits = null;
		caps = null;
	}
	JPulseCache(final int isize, final short[] iindex, final char[] ibits, final char[] icaps) {
		size = isize;
		index = iindex;
		bits = ibits;
		caps = icaps;
	}
	final void copyFrom(final JPulseCache cache) {
		size = cache.size;
		index = cache.index;
		bits = cache.bits;
		caps = cache.caps;
	}
}
