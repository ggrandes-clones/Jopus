package opus;

/* Copyright (c ) 2011 Xiph.Org Foundation, Skype Limited
   Written by Jean-Marc Valin and Koen Vos */

// opus.c

public final class Jopus {

// #ifndef DISABLE_FLOAT_API
	/** Applies soft-clipping to bring a float signal within the [-1,1] range. If
	  * the signal is already in that range, nothing is done. If there are values
	  * outside of [-1,1], then the signal is clipped as smoothly as possible to
	  * both fit in the range and avoid creating excessive distortion in the
	  * process.
	  * @param pcm [in,out] <tt>float*</tt>: Input PCM and modified PCM
	  * @param frame_size [in] <tt>int</tt> Number of samples per channel to process
	  * @param channels [in] <tt>int</tt>: Number of channels
	  * @param softclip_mem [in,out] <tt>float*</tt>: State memory for the soft clipping process (one float per channel, initialized to zero)
	  */
	public static final void opus_pcm_soft_clip(final float[] _x, int offset_x, final int N, final int C, final float[] declip_mem )
	{
		if( C < 1 || N < 1 || null == _x || null == declip_mem ) {
			return;
		}

		/* First thing: saturate everything to +/- 2 which is the highest level our
		  non-linearity can handle. At the point where the signal reaches +/-2,
		  the derivative will be zero anyway, so this doesn't introduce any
		  discontinuity in the derivative. */
		for( int i = offset_x, nc = offset_x + N * C; i < nc; i++ ) {
			float v = _x[i];// java
			v = 2.f < v ? 2.f : v;// java
			_x[i] = -2.f > v ? -2.f : v;
		}
		for( int c = 0; c < C; c++, offset_x++ )// java added offset_x++
		{
			//x = _x + c;// _x[c]
			float a = declip_mem[c];
			/* Continue applying the non-linearity from the previous frame to avoid
			 any discontinuity. */
			for( int i = 0; i < N; i++ )
			{
				final int ic = offset_x + i * C;// java
				final float x_ic = _x[ic];// java
				final float x_ic_a = x_ic * a;// java
				if( x_ic_a >= 0 ) {
					break;
				}
				_x[ic] = x_ic + x_ic_a * x_ic;// java x[i * C] = x[i * C] + a * x[i * C] * x[i * C];
			}

			int curr = 0;
			final float x0 = _x[offset_x];
			while( true )
			{
				int i;
				int x_offset;// java
				float x_c_ic;// java
				for( i = curr, x_offset = offset_x + i * C; i < N; i++, x_offset += C )
				{
					x_c_ic = _x[x_offset];
					if( x_c_ic > 1f || x_c_ic < -1f ) {
						break;
					}
				}
				if( i == N )
				{
					a = 0;
					break;
				}
				int peak_pos = i;
				int start, end;
				start = end = i;
				x_c_ic = _x[x_offset];
				float maxval = x_c_ic;
				if( maxval < 0 ) {
					maxval = -maxval;
				}
				/* Look for first zero crossing before clipping */
				int x_scan = offset_x + (start - 1) * C;// java
				while( start > 0 && x_c_ic * _x[x_scan] >= 0 ) {
					start--;
					x_scan -= C;
				}
				/* Look for first zero crossing after clipping */
				x_scan = offset_x + end * C;// java
				while( end < N && x_c_ic * _x[x_scan] >= 0 )
				{
					/* Look for other peaks until the next zero-crossing. */
					float v = _x[x_scan];// java
					if( v < 0 ) {
						v = -v;
					}
					if( v > maxval )
					{
						maxval = v;
						peak_pos = end;
					}
					end++;
					x_scan += C;
				}
				/* Detect the special case where we clip before the first zero crossing */
				final boolean special = (start == 0 && x_c_ic * _x[offset_x] >= 0);

				/* Compute a such that maxval + a*maxval^2 = 1 */
				a = (maxval - 1) / (maxval * maxval);
				/* Slightly boost "a" by 2^-22. This is just enough to ensure -ffast-math
				 does not cause output values larger than +/-1, but small enough not
				 to matter even for 24-bit output.  */
				a += a * 2.4e-7f;
				if( x_c_ic > 0 ) {
					a = -a;
				}
				/* Apply soft clipping */
				for( i = start, x_offset = offset_x + i * C; i < end; i++, x_offset += C ) {
					final float x_ic = _x[x_offset];// java
					_x[x_offset] = x_ic + a * x_ic * x_ic;
				}

				if( special && peak_pos >= 2 )
				{
					/* Add a linear ramp from the first sample to the signal peak.
					  This avoids a discontinuity at the beginning of the frame. */
					float delta;
					float offset = x0 - _x[offset_x];
					delta = offset / peak_pos;
					for( i = curr, x_offset = offset_x + i * C; i < peak_pos; i++, x_offset += C )
					{
						offset -= delta;
						final float x_ic = _x[x_offset] + offset;
						final float v = 1.f < x_ic ? 1.f : x_ic;// java
						_x[x_offset] = -1.f > v ? -1.f : v;
					}
				}
				curr = end;
				if( curr == N ) {
					break;
				}
			}
			declip_mem[c] = a;
		}
	}
// #endif

	private static final int parse_size(final byte[] data, int doffset,// java
			final int len,
			final short[] size, final int soffset// java
		)
	{
		if( len < 1 )
		{
			size[soffset] = -1;
			return -1;
		}
		final int v = (int)data[doffset] & 0xff;// java
		if( v < 252 )
		{
			size[soffset] = (short)v;
			return 1;
		}
		if( len < 2 )
		{
			size[soffset] = -1;
			return -1;
		}
		doffset++;
		size[soffset] = (short)((((int)data[doffset] & 0xff) << 2) + v);
		return 2;
	}

	/** Gets the number of samples per frame from an Opus packet.
	  * @param [in] data <tt>char*</tt>: Opus packet.
	  *                                  This must contain at least one byte of
	  *                                  data.
	  * @param [in] Fs <tt>opus_int32</tt>: Sampling rate in Hz.
	  *                                     This must be a multiple of 400, or
	  *                                     inaccurate results will be returned.
	  * @returns Number of samples per frame.
	  */
	public static final int opus_packet_get_samples_per_frame(final byte[] data, final int doffset,// java
			final int Fs)
	{
		final int d = (int)data[doffset];
		if( 0 != (d & 0x80) )
		{
			final int audiosize = ((d >> 3) & 0x3);
			return (Fs << audiosize ) / 400;
		}
		if( (d & 0x60 ) == 0x60 )
		{
			return (d & 0x08 ) != 0 ? Fs / 50 : Fs / 100;
		}
		final int audiosize = ((d >> 3 ) & 0x3);
		if( audiosize == 3 ) {
			return Fs * 60 / 1000;
		}
		return (Fs << audiosize) / 100;
	}

	/**
	 * java changed: out_toc, payload_offset, packet_offset replaced by Jopus_packet_data_aux
	 * important: before calling save data pointer to later use with frames[]
	 */
	static final int opus_packet_parse_impl(final byte[] data, int doffset,// java
			int len,
			final boolean self_delimited,
			// final byte[] out_toc,// FIXME toc using only to test
			final int frames[/* 48 */],// java changed: offsets for data.
			int foffset,// java added
			final short size[/* 48 */],
			int soffset,// java added
			// final int[] payload_offset, final int[] packet_offset
			final Jopus_packet_data_aux aux// java
			)
	{
		final int data0 = doffset;

		if( size == null || len < 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( len == 0 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}

		final int framesize = opus_packet_get_samples_per_frame( data, doffset, 48000 );

		int pad = 0;
		final int toc = data[doffset++];
		len--;
		int last_size = len;
		boolean cbr = false;
		int count;
		int bytes;
		switch( toc & 0x3 )
		{
			/* One frame */
		case 0:
			count = 1;
			break;
		/* Two CBR frames */
		case 1:
			count = 2;
			cbr = true;
			if( ! self_delimited )
			{
				if( 0 != (len & 0x1) ) {
					return Jopus_defines.OPUS_INVALID_PACKET;
				}
				last_size = len >>> 1;
				/* If last_size doesn't fit in size[0], we'll catch it later */
				size[soffset] = (short)last_size;
			}
			break;
		/* Two VBR frames */
		case 2:
			count = 2;
			bytes = parse_size( data, doffset, len, size, soffset );
			len -= bytes;
			if( size[soffset] < 0 || size[soffset] > len ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			doffset += bytes;
			last_size = len - size[soffset];
			break;
		/* Multiple CBR/VBR frames (from 0 to 120 ms ) */
		default: /*case 3:*/
			if( len < 1 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			/* Number of frames encoded in bits 0 to 5 */
			final int ch = data[doffset++];
			count = ch & 0x3F;
			if( count <= 0 || framesize * count > 5760 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			len--;
			/* Padding flag is bit 6 */
			if( 0 != (ch & 0x40) )
			{
				int p;
				do {
					if( len <= 0 ) {
						return Jopus_defines.OPUS_INVALID_PACKET;
					}
					p = ((int)data[doffset++]) & 0xff;
					len--;
					final int tmp = p == 255 ? 254 : p;
					len -= tmp;
					pad += tmp;
				} while( p == 255 );
			}
			if( len < 0 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			/* VBR flag is bit 7 */
			cbr = (ch & 0x80) == 0;
			if( ! cbr )
			{
				/* VBR case */
				last_size = len;
				for( int i = soffset, ie = soffset + count - 1; i < ie; i++ )
				{
					bytes = parse_size( data, doffset, len, size, i );
					len -= bytes;
					if( size[i] < 0 || size[i] > len ) {
						return Jopus_defines.OPUS_INVALID_PACKET;
					}
					doffset += bytes;
					last_size -= bytes + size[i];
				}
				if( last_size < 0 ) {
					return Jopus_defines.OPUS_INVALID_PACKET;
				}
			} else if( ! self_delimited )
			{
				/* CBR case */
				last_size = len / count;
				if( last_size * count != len ) {
					return Jopus_defines.OPUS_INVALID_PACKET;
				}
				for( int i = soffset, ie = soffset + count - 1; i < ie; i++ ) {
					size[i] = (short)last_size;
				}
			}
			break;
		}
		final int c1 = soffset + count - 1;// java
		/* Self-delimited framing has an extra size for the last frame. */
		if( self_delimited )
		{
			bytes = parse_size( data, doffset, len, size, c1 );
			len -= bytes;
			if( size[c1] < 0 || size[c1] > len ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			doffset += bytes;
			/* For CBR packets, apply the size to all the frames. */
			if( cbr )
			{
				if( size[c1] * count > len ) {
					return Jopus_defines.OPUS_INVALID_PACKET;
				}
				final short size_c1 = size[c1];// java
				for( int i = soffset; i < c1; i++ ) {
					size[i] = size_c1;
				}
			} else if( bytes + size[c1] > last_size ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
		} else
		{
			/* Because it's not encoded explicitly, it's possible the size of the
	 		last packet (or all the packets, for the CBR case ) is larger than
	 		1275. Reject them here.*/
			if( last_size > 1275 ) {
				return Jopus_defines.OPUS_INVALID_PACKET;
			}
			size[c1] = (short)last_size;
		}

		if( aux != null ) {// if( null != payload_offset ) {
			aux.mPayloadOffset = (doffset - data0);
		}

		for( final int ie = soffset + count; soffset < ie; )
		{
			if( null != frames ) {
				frames[foffset++] = doffset;// data;
			}
			doffset += size[soffset++];
		}

		if( aux != null ) {// if( null != packet_offset ) {
			aux.mPacketOffset = pad + (doffset - data0);
		}

		if( aux != null ) {// if( null != out_toc ) {
			aux.mToc = (byte)toc;
		}

		return count;
	}

	/**
	 * Parse an opus packet into one or more frames.
	 * Opus_decode will perform this operation internally so most applications do
	 * not need to use this function.
	 * This function does not copy the frames, the returned pointers are pointers into
	 * the input packet.
	 *
	 * java changed: out_toc, payload_offset replaced by Jopus_packet_data_aux
	 * important: before calling save data pointer to later use with frames[]
	 *
	 * @param data [in] <tt>char*</tt>: Opus packet to be parsed
	 * @param len [in] <tt>opus_int32</tt>: size of data
	 * @param out_toc [out] <tt>char*</tt>: TOC pointer
	 * @param frames [out] <tt>char*[48]</tt> encapsulated frames
	 * @param size [out] <tt>opus_int16[48]</tt> sizes of the encapsulated frames
	 * @param payload_offset [out] <tt>int*</tt>: returns the position of the payload within the packet (in bytes)
	 * @returns number of frames
	 */
	public static final int opus_packet_parse(final byte[] data, final int doffset,// java
			final int len,
			// final byte[] out_toc,// java: aux
			final int frames[/* 48 */], final short size[/* 48 */],
			// final int[] payload_offset)
			final Jopus_packet_data_aux aux )// java
	{// FIXME opus_packet_parse is used only in test codes
		return opus_packet_parse_impl( data, doffset, len, false,
				// out_toc,// java: aux
				frames, 0, size, 0,
				// payload_offset, null );// java: aux
				aux );// java
	}
}