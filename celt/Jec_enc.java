package celt;

public final class Jec_enc extends Jec_ctx {
	public Jec_enc() {
	}
	Jec_enc(final Jec_enc enc) {
		copyFrom( enc );
	}

	// start entenc.c
	/*A range encoder.
	See entdec.c and the references for implementation details \cite{Mar79,MNW98}.

	@INPROCEEDINGS{Mar79,
	 author="Martin, G.N.N.",
	 title="Range encoding: an algorithm for removing redundancy from a digitised
	  message",
	 booktitle="Video \& Data Recording Conference",
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
	 URL="http://www.stanford.edu/class/ee398/handouts/papers/Moffat98ArithmCoding.pdf"
	}*/
	private final int ec_write_byte(final int _value) {
		if( this.offs + this.end_offs >= this.storage ) {
			return -1;
		}
		this.buf[this.buf_start + this.offs++] = (byte)_value;
		return 0;
	}

	private final int ec_write_byte_at_end(final int _value) {
		if( this.offs + this.end_offs >= this.storage ) {
			return -1;
		}
		this.buf[this.buf_start + this.storage - ++(this.end_offs)] = (byte)_value;
		return 0;
	}

	/** Outputs a symbol, with a carry bit.
	  If there is a potential to propagate a carry over several symbols, they are
	   buffered until it can be determined whether or not an actual carry will
	   occur.
	  If the counter for the buffered symbols overflows, then the stream becomes
	   undecodable.
	  This gives a theoretical limit of a few billion symbols in a single packet on
	   32-bit systems.
	  The alternative is to truncate the range in order to force a carry, but
	   requires similar carry tracking in the decoder, needlessly slowing it down.*/
	private final void ec_enc_carry_out(final int _c) {
		if( _c != EC_SYM_MAX ) {
			/*No further carry propagation possible, flush buffer.*/
			final int carry = _c >> EC_SYM_BITS;
			/*Don't output a byte on the first write.
			This compare should be taken care of by branch-prediction thereafter.*/
			if( this.rem >= 0) {
				this.error |= ec_write_byte( this.rem + carry );
			}
			if( this.ext > 0 ) {
				final int sym = (EC_SYM_MAX + carry) & EC_SYM_MAX;
				do {
					this.error |= ec_write_byte( sym );
				} while( --(this.ext) > 0 );
			}
			this.rem = _c & EC_SYM_MAX;
		} else {
			this.ext++;
		}
	}

	private final void ec_enc_normalize() {
		/*If the range is too small, output some bits and rescale it.*/
		while( this.rng <= EC_CODE_BOT ) {
			ec_enc_carry_out( (int)(this.val >>> EC_CODE_SHIFT ) );
			/*Move the next-to-high-order symbol into the high-order position.*/
			this.val = (this.val << EC_SYM_BITS) & (EC_CODE_TOP - 1);
			this.rng <<= EC_SYM_BITS;
			this.nbits_total += EC_SYM_BITS;
		}
	}

	/**Initializes the encoder.
	  _buf:  The buffer to store output bytes in.
	  _size: The size of the buffer, in chars.*/
	public final void ec_enc_init(final byte[] _buf, final int start, final int _size) {// FIXME why using uint32?
		this.buf = _buf;
		this.buf_start = start;
		this.end_offs = 0;
		this.end_window = 0;
		this.nend_bits = 0;
		/*This is the offset from which ec_tell() will subtract partial bits.*/
		this.nbits_total = EC_CODE_BITS + 1;
		this.offs = 0;
		this.rng = EC_CODE_TOP;
		this.rem = -1;
		this.val = 0;
		this.ext = 0;
		this.storage = _size;
		this.error = 0;
	}

	/** Encodes a symbol given its frequency information.
	  The frequency information must be discernable by the decoder, assuming it
	   has read only the previous symbols from the stream.
	  It is allowable to change the frequency information, or even the entire
	   source alphabet, so long as the decoder can tell from the context of the
	   previously encoded information that it is supposed to do so as well.
	  _fl: The cumulative frequency of all symbols that come before the one to be
	        encoded.
	  _fh: The cumulative frequency of all symbols up to and including the one to
	        be encoded.
	       Together with _fl, this defines the range [_fl,_fh) in which the
	        decoded value will fall.
	  _ft: The sum of the frequencies of all the symbols */
	final void ec_encode(final int _fl, final int _fh, final int _ft) {// FIXME why using uint? it is calling with int arguments
		final long fl = (long)_fl & 0xffffffffL;// java
		final long fh = (long)_fh & 0xffffffffL;// java
		final long ft = (long)_ft & 0xffffffffL;// java
		final long r = ( this.rng / ft );
		// java: unsigned _fl, so _fl > 0 equals _fl != 0
		if( _fl != 0 ) {// if( _fl > 0 ) {
			this.val += this.rng - (r * (ft - fl));
			this.rng = (r * (fh - fl));
		} else {
			this.rng -= (r * (ft - fh));
		}
		ec_enc_normalize();
	}

	/** Equivalent to ec_encode() with _ft == 1 << _bits. */
	final void ec_encode_bin(final int _fl, final int _fh, final int _bits) {
		final long r = this.rng >>> _bits;
		final long fl = (long)_fl & 0xffffffffL;// java
		final long fh = (long)_fh & 0xffffffffL;// java
		// java: unsigned _fl, so _fl > 0 equals _fl != 0
		if( _fl != 0 ) {// if( _fl > 0 ) {
			this.val += this.rng - (r * ((1L << _bits) - fl));
			this.rng = (r * (fh - fl));
		} else {
			this.rng -= (r * ((1L << _bits) - fh));
		}
		ec_enc_normalize();
	}

	/** The probability of having a "one" is 1/(1<<_logp).
	 * Encode a bit that has a 1/(1<<_logp) probability of being a one */
	public final void ec_enc_bit_logp(final boolean _val, final int _logp) {
		long r = this.rng;
		final long l = this.val;
		final long s = r >>> _logp;
		r -= s;
		if( _val ) {
			this.val = l + r;
		}
		this.rng = _val ? s : r;
		ec_enc_normalize();
	}

	/**Encodes a symbol given an "inverse" CDF table.
	  _s:    The index of the symbol to encode.
	  _icdf: The "inverse" CDF, such that symbol _s falls in the range
	          [_s>0?ft-_icdf[_s-1]:0,ft-_icdf[_s]), where ft=1<<_ftb.
	         The values must be monotonically non-increasing, and the last value
	          must be 0.
	  _ftb: The number of bits of precision in the cumulative distribution.*/
	public final void ec_enc_icdf(final int _s,
			final char[] _icdf, int offset,// java
			final int _ftb) {
		final long r = this.rng >>> _ftb;
		offset += _s;// java
		final int o0 = _icdf[offset];// java
		if( _s > 0 ) {
			final int o1 = _icdf[--offset];// java
			this.val += this.rng - (r * o1);
			this.rng = (r * (o1 - o0));
		} else {
			this.rng -= (r * o0);
		}
		ec_enc_normalize();
	}

	/**Encodes a raw unsigned integer in the stream.
	  _fl: The integer to encode.
	  _ft: The number of integers that can be encoded (one more than the max).
	       This must be at least 2, and no more than 2**32-1.*/
	public final void ec_enc_uint(final int _fl, int _ft) {
		/*In order to optimize EC_ILOG(), it is undefined for the value 0.*/
		// celt_assert( _ft > 1 );
		_ft--;
		int ftb = EC_ILOG( _ft );
		if( ftb > Jec_ctx.EC_UINT_BITS ) {
			ftb -= Jec_ctx.EC_UINT_BITS;
			final int ft = (_ft >>> ftb) + 1;
			final int fl = (_fl >>> ftb);
			ec_encode( fl, fl + 1, ft );
			ec_enc_bits( _fl & ((1 << ftb) - 1), ftb );
		} else {
			ec_encode( _fl, _fl + 1, _ft + 1 );
		}
	}

	/**Encodes a sequence of raw bits in the stream.
	  _fl:  The bits to encode.
	  _ftb: The number of bits to encode.
	        This must be between 1 and 25, inclusive.*/
	final void ec_enc_bits(final int _fl, final int _bits) {// FIXME why using unsigned?
		int window = this.end_window;
		int used = this.nend_bits;
		// celt_assert( _bits > 0 );
		if( used + _bits > Jec_ctx.EC_WINDOW_SIZE ) {
			do {
				this.error |= ec_write_byte_at_end( window & EC_SYM_MAX );
				window >>>= EC_SYM_BITS;
				used -= EC_SYM_BITS;
			}
			while( used >= EC_SYM_BITS );
		}
		window |= _fl << used;
		used += _bits;
		this.end_window = window;
		this.nend_bits = used;
		this.nbits_total += _bits;
	}

	/**Overwrites a few bits at the very start of an existing stream, after they
	   have already been encoded.
	  This makes it possible to have a few flags up front, where it is easy for
	   decoders to access them without parsing the whole stream, even if their
	   values are not determined until late in the encoding process, without having
	   to buffer all the intermediate symbols in the encoder.
	  In order for this to work, at least _nbits bits must have already been
	   encoded using probabilities that are an exact power of two.
	  The encoder can verify the number of encoded bits is sufficient, but cannot
	   check this latter condition.
	  _val:   The bits to encode (in the least _nbits significant bits).
	          They will be decoded in order from most-significant to least.
	  _nbits: The number of bits to overwrite.
	          This must be no more than 8.*/
	public final void ec_enc_patch_initial_bits(final int _val, final int _nbits)
	{// FIXME calling with int val
		// celt_assert( _nbits <= EC_SYM_BITS );
		final int shift = EC_SYM_BITS - _nbits;
		final int mask = ((1 << _nbits) - 1) << shift;
		if( this.offs > 0 ) {
			/*The first byte has been finalized.*/
			this.buf[this.buf_start] = (byte)(((int)this.buf[this.buf_start] & ~mask) | _val << shift);
		}
		else if( this.rem >= 0 ) {
			/*The first byte is still awaiting carry propagation.*/
			this.rem = (this.rem & ~mask) | _val << shift;
		}
		else if( this.rng <= (EC_CODE_TOP >>> _nbits) ) {
			/*The renormalization loop has never been run.*/
			this.val = ((this.val & ~(mask << EC_CODE_SHIFT)) | _val << (EC_CODE_SHIFT + shift)) & 0xffffffffL;// java added &
		} else {
			this.error = -1;
		}
	}

	/**Compacts the data to fit in the target size.
	  This moves up the raw bits at the end of the current buffer so they are at
	   the end of the new buffer size.
	  The caller must ensure that the amount of data that's already been written
	   will fit in the new size.
	  _size: The number of bytes in the new buffer.
	         This must be large enough to contain the bits already written, and
	          must be no larger than the existing size.*/
	public final void ec_enc_shrink(final int _size) {// FIXME why using uint32?
		// celt_assert( _this.offs + _this.end_offs <= _size );
		//OPUS_MOVE( _this.buf + _size - _this.end_offs,
		//		_this.buf + _this.storage - _this.end_offs, _this.end_offs );
		final int d = this.buf_start - this.end_offs;
		System.arraycopy( this.buf, d + this.storage, this.buf, d + _size, this.end_offs );
		this.storage = _size;
	}

	/** Indicates that there are no more symbols to encode.
	  All reamining output bytes are flushed to the output buffer.
	  ec_enc_init() must be called before the encoder can be used again. */
	public final void ec_enc_done() {
		/*We output the minimum number of bits that ensures that the symbols encoded
		thus far will be decoded correctly regardless of the bits that follow.*/
		int l = EC_CODE_BITS - EC_ILOG( (int)this.rng );
		long msk = (long)((EC_CODE_TOP - 1) >>> l);
		long end = (this.val + msk) & ~msk;
		if( (end | msk) >= this.val + this.rng ) {
			l++;
			msk >>>= 1;
			end = (this.val + msk) & ~msk;
		}
		while( l > 0 ) {
			ec_enc_carry_out( (int)(end >>> EC_CODE_SHIFT) );
			end = (end << EC_SYM_BITS) & (EC_CODE_TOP - 1);
			l -= EC_SYM_BITS;
		}
		/*If we have a buffered byte flush it into the output buffer.*/
		if( this.rem >= 0 || this.ext > 0 ) {
			ec_enc_carry_out( 0 );
		}
		/*If we have buffered extra bits, flush them as well.*/
		int window = this.end_window;
		int used = this.nend_bits;
		while( used >= EC_SYM_BITS ) {
			this.error |= ec_write_byte_at_end( window & EC_SYM_MAX );
			window >>>= EC_SYM_BITS;
			used -= EC_SYM_BITS;
		}
		/*Clear any excess space and add any remaining extra bits to the last byte.*/
		if( 0 == this.error ) {
			//OPUS_CLEAR(_this.buf + _this.offs, _this.storage - _this.offs - _this.end_offs );
			final byte[] buff = this.buf;// java
			for( int i = this.buf_start + this.offs, ie = i + this.storage - this.offs - this.end_offs; i < ie; i++ ) {
				buff[i] = 0;
			}
			if( used > 0 ) {
				/*If there's no range coder data at all, give up.*/
				if( this.end_offs >= this.storage ) {
					this.error = -1;
				} else {
					l = -l;
					/*If we've busted, don't add too many extra bits to the last byte; it
					would corrupt the range coder data, and that's more important.*/
					if( this.offs + this.end_offs >= this.storage && l < used ) {
						window &= (1 << l) - 1;
						this.error = -1;
					}
					buff[this.buf_start + this.storage - this.end_offs - 1] |= (byte)window;
				}
			}
		}
	}
	// end entenc.c

	// start laplace.c
	/**
	 * Encode a value that is assumed to be the realisation of a
	 * Laplace-distributed random process
	 *
	 * java changed: new value of the value is returned
	 *
	 * @param enc Entropy encoder state
	 * @param value Value to encode
	 * @param fs Probability of 0, multiplied by 32768
	 * @param decay Probability of the value +/- 1, multiplied by 16384
	 */
	final int ec_laplace_encode(int value, int fs, final int decay)
	{// FIXME fs and fl unsigned and uses with signed math
		int v = value;
		int fl = 0;
		if( 0 != v )
		{
			final int s = (v < 0 ? -1 : 0);
			v = (v + s) ^ s;
			fl = fs;
			fs = ec_laplace_get_freq1( fs, decay );
			/* Search the decaying part of the PDF.*/
			int i;
			for( i = 1; fs > 0 && i < v; i++ )
			{
				fs <<= 1;
				fl += fs + 2 * LAPLACE_MINP;
				fs = (fs * decay) >> 15;
			}
			/* Everything beyond that has probability LAPLACE_MINP. */
			if( 0 == fs )
			{
				int ndi_max = (32768 - fl + LAPLACE_MINP - 1) >> LAPLACE_LOG_MINP;
				ndi_max = (ndi_max - s) >> 1;
				ndi_max--;// java
				v -= i;// java
				final int di = v <= ndi_max ? v : ndi_max;
				fl += ((di << 1) + 1 + s) * LAPLACE_MINP;
				ndi_max = 32768 - fl;// java
				fs = LAPLACE_MINP < ndi_max ? LAPLACE_MINP : ndi_max;
				value = (i + di + s) ^ s;
			}
			else
			{
				fs += LAPLACE_MINP;
				fl += fs & ~s;
			}
			// celt_assert( fl + fs <= 32768 );
			// celt_assert( fs > 0 );
		}
		ec_encode_bin( fl, fl + fs, 15 );
		return value;// java
	}
	// end laplace.c
}
