package celt;

public final class Jec_dec extends Jec_ctx {
	// ecintrin.c
	/*Modern gcc (4.x) can compile the naive versions of min and max with cmov if
	   given an appropriate architecture, but the branchless bit-twiddling versions
	   are just as fast, and do not require any special target architecture.
	  Earlier gcc versions (3.x) compiled both code to the same assembly
	   instructions, because of the way they represented ((_b)>(_a)) internally.*/
	/* private static final long EC_MINI(final long a, long b) {// java extracted inplace
		// return (a + ((b - a) & (b < a ? -1 : 0)));
		b -= a;
		if( b > 0 ) {
			b = 0;
		}
		return (a + b);
	} */
	// ecintrin.c

	// start entdec.c

	/*A range decoder.
	This is an entropy decoder based upon \cite{Mar79}, which is itself a
	 rediscovery of the FIFO arithmetic code introduced by \cite{Pas76}.
	It is very similar to arithmetic encoding, except that encoding is done with
	 digits in any base, instead of with bits, and so it is faster when using
	 larger bases (i.e.: a byte).
	The author claims an average waste of $\frac{1}{2}\log_b(2b)$ bits, where $b$
	 is the base, longer than the theoretical optimum, but to my knowledge there
	 is no published justification for this claim.
	This only seems true when using near-infinite precision arithmetic so that
	 the process is carried out with no rounding errors.

	An excellent description of implementation details is available at
	 http://www.arturocampos.com/ac_range.html
	A recent work \cite{MNW98} which proposes several changes to arithmetic
	 encoding for efficiency actually re-discovers many of the principles
	 behind range encoding, and presents a good theoretical analysis of them.

	End of stream is handled by writing out the smallest number of bits that
	 ensures that the stream will be correctly decoded regardless of the value of
	 any subsequent bits.
	ec_tell() can be used to determine how many bits were needed to decode
	 all the symbols thus far; other data can be packed in the remaining bits of
	 the input buffer.
	@PHDTHESIS{Pas76,
	  author="Richard Clark Pasco",
	  title="Source coding algorithms for fast data compression",
	  school="Dept. of Electrical Engineering, Stanford University",
	  address="Stanford, CA",
	  month=May,
	  year=1976
	}
	@INPROCEEDINGS{Mar79,
	 author="Martin, G.N.N.",
	 title="Range encoding: an algorithm for removing redundancy from a digitised
	  message",
	 booktitle="Video & Data Recording Conference",
	 year=1979,
	 address="Southampton",
	 month=Jul
	}
	@ARTICLE{MNW98,
	 author="Alistair Moffat and Radford Neal and Ian H. Witten",
	 title="Arithmetic Coding Revisited",
	 journal="{ACM} Transactions on Information Systems",
	 year=1998,
	 volume=16,
	 number=3,
	 pages="256--294",
	 month=Jul,
	 URL="http://www.stanford.edu/class/ee398a/handouts/papers/Moffat98ArithmCoding.pdf"
	}*/
	private final int ec_read_byte() {
		return this.offs < this.storage ? (((int)this.buf[this.buf_start + this.offs++]) & 0xff) : 0;
	}

	private final int ec_read_byte_from_end() {
		return this.end_offs < this.storage ?
				(((int)this.buf[this.buf_start + this.storage - ++(this.end_offs)]) & 0xff) : 0;
	}

	/** Normalizes the contents of val and rng so that rng lies entirely in the
	    high-order symbol.*/
	void ec_dec_normalize() {
		/*If the range is too small, rescale it and input some bits.*/
		while( this.rng <= EC_CODE_BOT ) {
			this.nbits_total += EC_SYM_BITS;
			this.rng <<= EC_SYM_BITS;
			/*Use up the remaining bits from our last symbol.*/
			int sym = this.rem;
			/*Read the next value from the input.*/
			this.rem = ec_read_byte();
			/*Take the rest of the bits we need from this new symbol.*/
			sym = (sym << EC_SYM_BITS | this.rem) >> (EC_SYM_BITS - EC_CODE_EXTRA);
			/*And subtract them from val, capped to be less than EC_CODE_TOP.*/
			this.val = ((this.val << EC_SYM_BITS) + (EC_SYM_MAX & ~sym)) & (EC_CODE_TOP - 1);
		}
	}

	public final void ec_dec_init(final byte[] _buf, final int start, final int _storage) {
		this.buf = _buf;
		this.buf_start = start;
		this.storage = _storage;
		this.end_offs = 0;
		this.end_window = 0;
		this.nend_bits = 0;
		/*This is the offset from which ec_tell() will subtract partial bits.
		The final value after the ec_dec_normalize() call will be the same as in
		the encoder, but we have to compensate for the bits that are added there.*/
		this.nbits_total = EC_CODE_BITS + 1
				-((EC_CODE_BITS - EC_CODE_EXTRA) / EC_SYM_BITS) * EC_SYM_BITS;
		this.offs = 0;
		this.rng = 1L << EC_CODE_EXTRA;
		this.rem = ec_read_byte();
		this.val = this.rng - 1 - (this.rem >> (EC_SYM_BITS - EC_CODE_EXTRA));
		this.error = 0;
		/*Normalize the interval.*/
		ec_dec_normalize();
	}

	/**Calculates the cumulative frequency for the next symbol.
	  This can then be fed into the probability model to determine what that
	   symbol is, and the additional frequency information required to advance to
	   the next symbol.
	  This function cannot be called more than once without a corresponding call to
	   ec_dec_update(), or decoding will not proceed correctly.
	  _ft: The total frequency of the symbols in the alphabet the next symbol was
	        encoded with.
	  Return: A cumulative frequency representing the encoded symbol.
	          If the cumulative frequency of all the symbols before the one that
	           was encoded was fl, and the cumulative frequency of all the symbols
	           up to and including the one encoded is fh, then the returned value
	           will fall in the range [fl,fh).*/
	final int ec_decode(final int _fti) {// FIXME it is called with int _ft, result uses as int
		final long _ft = ((long)_fti & 0xffffffffL);// java
		this.ext = ( this.rng / _ft );
		long s = (this.val / this.ext);
		// return (int)(_ft - EC_MINI( s + 1L, _ft ));
		s++;
		long b = _ft;
		b -= s;
		if( b > 0 ) {
			b = 0;
		}
		s += b;
		return (int)(_ft - s);
	}

	/** Equivalent to ec_decode() with _ft == 1<<_bits. */
	final int ec_decode_bin(final int _bits) {
		this.ext = this.rng >>> _bits;
		long s = (this.val / this.ext);
		// return (int)((1L << _bits) - EC_MINI( s + 1L, 1L << _bits ));
		s++;
		long b = 1L << _bits;
		b -= s;
		if( b > 0 ) {
			b = 0;
		}
		s += b;
		return (int)((1L << _bits) - s);
	}

	/**Advance the decoder past the next symbol using the frequency information the
	   symbol was encoded with.
	  Exactly one call to ec_decode() must have been made so that all necessary
	   intermediate calculations are performed.
	  _fl:  The cumulative frequency of all symbols that come before the symbol
	         decoded.
	  _fh:  The cumulative frequency of all symbols up to and including the symbol
	         decoded.
	        Together with _fl, this defines the range [_fl,_fh) in which the value
	         returned above must fall.
	  _ft:  The total frequency of the symbols in the alphabet the symbol decoded
	         was encoded in.
	        This must be the same as passed to the preceding call to ec_decode().*/
	final void ec_dec_update(final int _fl, final int _fh, final int _ft) {// FIXME calling with int
		// java: if ft > fh > fl, than & 0xffffffff don't need
		/*
		final long s = ( _this.ext * ((long)(_ft - _fh) & 0xffffffffL) );
		_this.val -= s;
		_this.rng = _fl > 0 ? ( _this.ext * ((long)(_fh - _fl) & 0xffffffffL) ) : _this.rng - s;
		ec_dec_normalize( _this );
		*/
		final long fl = (long)_fl & 0xffffffffL;// java
		final long fh = (long)_fh & 0xffffffffL;// java
		final long ft = (long)_ft & 0xffffffffL;// java
		final long s = ( this.ext * (ft - fh) );
		this.val -= s;
		// _this.rng = fl > 0 ? ( _this.ext * (fh - fl) ) : _this.rng - s;
		// java: unsigned _fl, so _fl > 0 equals _fl != 0
		this.rng = _fl != 0 ? (this.ext * (fh - fl)) : this.rng - s;
		ec_dec_normalize();
	}

	/** The probability of having a "one" is 1/(1<<_logp). */
	/** Decode a bit that has a 1/(1<<_logp) probability of being a one */
	public final boolean ec_dec_bit_logp(final int _logp) {
		final long r = this.rng;
		final long d = this.val;
		final long s = r >>> _logp;
		final boolean ret = d < s;
		if( ! ret ) {
			this.val = d - s;
		}
		this.rng = ret ? s : r - s;
		ec_dec_normalize();
		return ret;
	}

	/**Decodes a symbol given an "inverse" CDF table.
	  No call to ec_dec_update() is necessary after this call.
	  _icdf: The "inverse" CDF, such that symbol s falls in the range
	          [s>0?ft-_icdf[s-1]:0,ft-_icdf[s]), where ft=1<<_ftb.
	         The values must be monotonically non-increasing, and the last value
	          must be 0.
	  _ftb: The number of bits of precision in the cumulative distribution.
	  Return: The decoded symbol s.*/
	public final int ec_dec_icdf(final char[] _icdf, final int ioffset, final int _ftb) {// java
		long s = this.rng;
		final long d = this.val;
		final long r = s >>> _ftb;
		int ret = ioffset - 1;
		long t;
		do {
			t = s;
			s = r * (long)_icdf[++ret];
		} while( d < s );
		this.val = d - s;// d >= s
		this.rng = (t - s) & 0xffffffffL;// java. & 0xffffffffL to uint32. t < s is possible?
		ec_dec_normalize();
		return ret - ioffset;
	}

	/**Extracts a raw unsigned integer with a non-power-of-2 range from the stream.
	  The bits must have been encoded with ec_enc_uint().
	  No call to ec_dec_update() is necessary after this call.
	  _ft: The number of integers that can be decoded (one more than the max).
	       This must be at least 2, and no more than 2**32-1.
	  Return: The decoded bits.*/
	public final int ec_dec_uint(int _ft) {
		// unsigned ft;// FIXME must be uint32
		/*In order to optimize EC_ILOG(), it is undefined for the value 0.*/
		// celt_assert( _ft > 1 );
		_ft--;
		int ftb = EC_ILOG( _ft );
		if( ftb > EC_UINT_BITS ) {
			ftb -= EC_UINT_BITS;
			final int ft = (_ft >>> ftb) + 1;
			final int s = ec_decode( ft );
			ec_dec_update( s, s + 1, ft );
			final int t = s << ftb | ec_dec_bits( ftb );
			if( t + Integer.MIN_VALUE <= _ft + Integer.MIN_VALUE ) {// java: + MIN_VALUE for unsigned comparing.
				return t;
			}
			this.error = 1;
			return _ft;
		}
		// else {
			_ft++;
			final int s = ec_decode( _ft );
			ec_dec_update( s, s + 1, _ft );
			return s;
		//}
	}

	/**
	 *
	 * @param _this
	 * @param _bits
	 * @return java changed: return type "signed int32"
	 */
	final int ec_dec_bits(final int _bits) {
		int window = this.end_window;
		int available = this.nend_bits;
		if( available < _bits ) {
			do {
				window |= ec_read_byte_from_end() << available;
				available += EC_SYM_BITS;
			}
			while( available <= EC_WINDOW_SIZE - EC_SYM_BITS );
		}
		final int ret = window & ((1 << _bits) - 1);
		window >>>= _bits;
		available -= _bits;
		this.end_window = window;
		this.nend_bits = available;
		this.nbits_total += _bits;
		return ret;
	}
	// end entdec.c

	// start laplace.c
	/** Decode a value that is assumed to be the realisation of a
	 * Laplace-distributed random process
	 * @param dec Entropy decoder state
	 * @param fs Probability of 0, multiplied by 32768
	 * @param decay Probability of the value +/- 1, multiplied by 16384
	 * @return Value decoded
	 */
	final int ec_laplace_decode(int fs, final int decay)
	{
		int value = 0;
		final int fm = ec_decode_bin( 15 );
		int fl = 0;
		if( fm >= fs )
		{
			value++;
			fl = fs;
			fs = ec_laplace_get_freq1( fs, decay ) + LAPLACE_MINP;
			/* Search the decaying part of the PDF.*/
			while( fs > LAPLACE_MINP && fm >= fl + (fs << 1) )
			{
				fs <<= 1;
				fl += fs;
				fs = ((fs - (2 * LAPLACE_MINP)) * decay) >> 15;
				fs += LAPLACE_MINP;
				value++;
			}
			/* Everything beyond that has probability LAPLACE_MINP. */
			if( fs <= LAPLACE_MINP )
			{
				final int di = (fm - fl) >> (LAPLACE_LOG_MINP + 1);
				value += di;
				fl += di * (2 * LAPLACE_MINP);
			}
			if( fm < fl + fs ) {
				value = -value;
			} else {
				fl += fs;
			}
		}
		// celt_assert( fl < 32768 );
		// celt_assert( fs > 0 );
		// celt_assert( fl <= fm );
		// celt_assert( fm < IMIN( fl + fs, 32768 ) );
		fs += fl;// java
		fs = fs <= 32768 ? fs : 32768;
		ec_dec_update( fl, fs, 32768 );
		return value;
	}
	// end laplace.c
}
