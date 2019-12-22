package celt;

// TODO java: optimize long mathematics in ec_dec and ec_enc

/** The entropy encoder/decoder context.
 We use the same structure for both, so that common functions like ec_tell()
 can be used on either one. */
public class Jec_ctx {// java: public only for EC_ILOG
	// mfrngcod.h
	/* Constants used by the entropy encoder/decoder. */
	/** The number of bits to output at a time. */
	static final int EC_SYM_BITS   = 8;
	/** The total number of bits in each of the state registers. */
	static final int EC_CODE_BITS  = 32;
	/** The maximum symbol value. */
	static final int EC_SYM_MAX    = (1 << EC_SYM_BITS) - 1;
	/** Bits to shift by to move a symbol into the high-order position. */
	static final int EC_CODE_SHIFT = EC_CODE_BITS - EC_SYM_BITS - 1;
	/** Carry bit of the high-order range symbol. */
	static final long EC_CODE_TOP  = 1L << (EC_CODE_BITS - 1);
	/** Low-order bit of the high-order range symbol. */
	static final long EC_CODE_BOT  = (EC_CODE_TOP >> EC_SYM_BITS);
	/** The number of bits available for the last, partial symbol in the code field. */
	static final int EC_CODE_EXTRA = (EC_CODE_BITS - 2) % EC_SYM_BITS + 1;
	// mfrngcod.h

	/** Buffered input/output. java changed: buf[ buf_start + ... ] */
	public byte[] buf;
	/** java added. using: buf[ buf_start + ... ] */
	public int    buf_start;
	/** The size of the buffer. */
	public int    storage;// uint32
	/** The offset at which the last byte containing raw bits was read/written. */
	int           end_offs;// uint32
	/** Bits that will be read from/written at the end. */
	int           end_window;// uint32
	/** Number of valid bits in end_window. */
	int           nend_bits;
	/** The total number of whole bits read/written.
	 This does not include partial bits currently in the range coder. */
	int           nbits_total;
	/** The offset at which the next range coder byte will be read/written. */
	public int    offs;// uint32
	/** The number of values in the current range. */
	public long   rng;
	/** In the decoder: the difference between the top of the current range and
	  the input value, minus one.
	  In the encoder: the low end of the current range. */
	long          val;
	/** In the decoder: the saved normalization factor from ec_decode().
	  In the encoder: the number of oustanding carry propagating symbols. */
	long          ext;
	/** A buffered input/output symbol, awaiting carry propagation. */
	int           rem;
	/** Nonzero if an error occurred. */
	int           error;
	//
	/** Default constructor */
	public Jec_ctx() {
	}
	Jec_ctx(final Jec_ctx ctx) {
		copyFrom( ctx );
	}

	/**
	 * java version of c memcpy
	 * @param ctx the Jec_ctx to copy from
	 */
	public final void copyFrom(final Jec_ctx ctx) {
		buf = ctx.buf;
		buf_start = ctx.buf_start;
		storage = ctx.storage;
		end_offs = ctx.end_offs;
		end_window = ctx.end_window;
		nend_bits = ctx.nend_bits;
		nbits_total = ctx.nbits_total;
		offs = ctx.offs;
		rng = ctx.rng;
		val = ctx.val;
		ext = ctx.ext;
		rem = ctx.rem;
		error = ctx.error;
	}
	//
	// entcode.h
	// entcode.c
	// entcode.h
	static final int EC_WINDOW_SIZE = Integer.SIZE;

	/** The number of bits to use for the range-coded part of unsigned integers.*/
	static final int EC_UINT_BITS = 8;

	/** The resolution of fractional-precision bit usage measurements, i.e.,
	   3 => 1/8th bits. */
	static final int BITRES = 3;

	final int ec_range_bytes() {// uint32
		return this.offs;
	}

	/**
	 * java changed: use only together with ec_get_buffer_start.
	 *
	 * @param _this
	 * @return buf. using: buf[ start + ... ].
	 */
	final byte[] ec_get_buffer() {// unsigned byte
		return this.buf;
	}// java: may be more safe way is using class{ byte[] buf; int offset; }

	/**
	 * java: added to use together with ec_get_buffer.
	 * @param _this
	 * @return start offset. using: buf[ start + ... ].
	 */
	final int ec_get_buffer_start() {
		return this.buf_start;
	}

	final int ec_get_error() {
		return this.error;
	}

	/**
	 *
	 * @param _v
	 * @return log of the _v
	 */
	public static final int EC_ILOG(int _v) {
		// On a Pentium M, this branchless version tested as the fastest on
		// 1,000,000,000 random 32-bit integers, edging out a similar version with
		// branches, and a 256-entry LUT version.
		// int ret = _v != 0 ? 1 : 0;
		// int m = (_v & 0xFFFF0000) != 0 ? 1 << 4 : 0;
		int ret = 0;
		if( _v != 0 ) {
			ret = 1;
			if( (_v & 0xFFFF0000) != 0 ) {
				final int m = 1 << 4;
				_v >>>= m;
				ret |= m;
			}
		}
		// m = (_v & 0xFF00) != 0 ? 1 << 3 : 0;
		if( (_v & 0xFF00) != 0 ) {
			final int m = 1 << 3;
			_v >>>= m;
			ret |= m;
		}
		// m = (_v & 0xF0) != 0 ? 1 << 2 : 0;
		if( (_v & 0xF0) != 0 ) {
			final int m = 1 << 2;
			_v >>>= m;
			ret |= m;
		}
		// m = (_v & 0xC) != 0 ? 1 << 1 : 0;
		if( (_v & 0xC) != 0  ) {
			final int m = 1 << 1;
			_v >>>= m;
			ret |= m;
		}
		// ret += (_v & 0x2) != 0 ? 1 : 0;
		if( (_v & 0x2) != 0 ) {
			ret++;
		}
		return ret;
	}

	/** @return Returns the number of bits "used" by the encoded or decoded symbols so far.
	  This same number can be computed in either the encoder or the decoder, and is
	   suitable for making coding decisions.
	  Return: The number of bits.
	          This will always be slightly larger than the exact value (e.g., all
	           rounding error is in the positive direction).*/
	public final int ec_tell() {
		return this.nbits_total - EC_ILOG( (int)this.rng );
	}
	// entcode.c
// #if 1
	private static final int correction[/* 8 */] =
		{ 35733, 38967, 42495, 46340, 50535, 55109, 60097, 65535 };
	/** This is a faster version of ec_tell_frac() that takes advantage
	of the low (1/8 bit) resolution to use just a linear function
	followed by a lookup to determine the exact transition thresholds. */
	/** @return Returns the number of bits "used" by the encoded or decoded symbols so far.
	  This same number can be computed in either the encoder or the decoder, and is
	   suitable for making coding decisions.
	  Return: The number of bits scaled by 2**BITRES.
	          This will always be slightly larger than the exact value (e.g., all
	           rounding error is in the positive direction).*/
	final int ec_tell_frac() {
		final int nbits = this.nbits_total << BITRES;
		int l = EC_ILOG( (int)this.rng );
		final long r = this.rng >>> (l - 16);
		int b = (int)((r >>> 12) - 8);
		if( r > correction[b] ) {
			b++;
		}
		l = (l << 3) + b;
		return nbits - l;
	}
/* #else
	opus_uint32 ec_tell_frac(ec_ctx *_this) {
		opus_uint32 nbits;
		opus_uint32 r;
		int         l;
		int         i;
		// To handle the non-integral number of bits still left in the encoder/decoder
		// state, we compute the worst-case number of bits of val that must be
		// encoded to ensure that the value is inside the range for any possible
		// subsequent bits.
		// The computation here is independent of val itself (the decoder does not
		// even track that value), even though the real number of bits used after
		// ec_enc_done() may be 1 smaller if rng is a power of two and the
		// corresponding trailing bits of val are all zeros.
		// If we did try to track that special case, then coding a value with a
		// probability of 1/(1<<n) might sometimes appear to use more than n bits.
		// This may help explain the surprising result that a newly initialized
		// encoder or decoder claims to have used 1 bit.
		nbits = _this->nbits_total<<BITRES;
		l = EC_ILOG( _this->rng );
		r = _this->rng >> (l - 16);
		for( i = BITRES; i-- > 0; ) {
			int b;
			r = r * r >> 15;
			b = (int)(r >> 16);
			l = l << 1 | b;
			r >>= b;
		}
		return nbits - l;
	}
#endif */

// #ifdef USE_SMALL_DIV_TABLE
	/** Result of 2^32/(2*i+1), except for i=0. */
	/* const opus_uint32 SMALL_DIV_TABLE[129] = {
		0xFFFFFFFF, 0x55555555, 0x33333333, 0x24924924,
		0x1C71C71C, 0x1745D174, 0x13B13B13, 0x11111111,
		0x0F0F0F0F, 0x0D79435E, 0x0C30C30C, 0x0B21642C,
		0x0A3D70A3, 0x097B425E, 0x08D3DCB0, 0x08421084,
		0x07C1F07C, 0x07507507, 0x06EB3E45, 0x06906906,
		0x063E7063, 0x05F417D0, 0x05B05B05, 0x0572620A,
		0x05397829, 0x05050505, 0x04D4873E, 0x04A7904A,
		0x047DC11F, 0x0456C797, 0x04325C53, 0x04104104,
		0x03F03F03, 0x03D22635, 0x03B5CC0E, 0x039B0AD1,
		0x0381C0E0, 0x0369D036, 0x03531DEC, 0x033D91D2,
		0x0329161F, 0x03159721, 0x03030303, 0x02F14990,
		0x02E05C0B, 0x02D02D02, 0x02C0B02C, 0x02B1DA46,
		0x02A3A0FD, 0x0295FAD4, 0x0288DF0C, 0x027C4597,
		0x02702702, 0x02647C69, 0x02593F69, 0x024E6A17,
		0x0243F6F0, 0x0239E0D5, 0x02302302, 0x0226B902,
		0x021D9EAD, 0x0214D021, 0x020C49BA, 0x02040810,
		0x01FC07F0, 0x01F44659, 0x01ECC07B, 0x01E573AC,
		0x01DE5D6E, 0x01D77B65, 0x01D0CB58, 0x01CA4B30,
		0x01C3F8F0, 0x01BDD2B8, 0x01B7D6C3, 0x01B20364,
		0x01AC5701, 0x01A6D01A, 0x01A16D3F, 0x019C2D14,
		0x01970E4F, 0x01920FB4, 0x018D3018, 0x01886E5F,
		0x0183C977, 0x017F405F, 0x017AD220, 0x01767DCE,
		0x01724287, 0x016E1F76, 0x016A13CD, 0x01661EC6,
		0x01623FA7, 0x015E75BB, 0x015AC056, 0x01571ED3,
		0x01539094, 0x01501501, 0x014CAB88, 0x0149539E,
		0x01460CBC, 0x0142D662, 0x013FB013, 0x013C995A,
		0x013991C2, 0x013698DF, 0x0133AE45, 0x0130D190,
		0x012E025C, 0x012B404A, 0x01288B01, 0x0125E227,
		0x01234567, 0x0120B470, 0x011E2EF3, 0x011BB4A4,
		0x01194538, 0x0116E068, 0x011485F0, 0x0112358E,
		0x010FEF01, 0x010DB20A, 0x010B7E6E, 0x010953F3,
		0x01073260, 0x0105197F, 0x0103091B, 0x01010101
	}; */
// #endif

	// start laplace.c
	/** The minimum probability of an energy delta (out of 32768). */
	static final int LAPLACE_LOG_MINP = 0;
	static final int LAPLACE_MINP = (1 << LAPLACE_LOG_MINP);
	/** The minimum number of guaranteed representable energy deltas (in one
	direction). */
	static final int LAPLACE_NMIN = 16;

	/** When called, decay is positive and at most 11456. */
	static final int ec_laplace_get_freq1(final int fs0, final int decay)
	{
		final int ft = 32768 - LAPLACE_MINP * (2 * LAPLACE_NMIN) - fs0;
		return ft * (16384 - decay) >> 15;
	}
	// end laplace.c
}
