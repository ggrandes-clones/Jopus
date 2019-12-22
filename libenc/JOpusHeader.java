package libenc;

import java.nio.charset.Charset;
import opus.Jopus_defines;
import opus.Jopus_projection;

/* Copyright (C)2012 Xiph.Org Foundation
File: opus_header.c

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* Header contents:
- "OpusHead" (64 bits)
- version number (8 bits)
- Channels C (8 bits)
- Pre-skip (16 bits)
- Sampling rate (32 bits)
- Gain in dB (16 bits, S7.8)
- Mapping (8 bits, 0=single stream (mono/stereo) 1=Vorbis mapping,
           2=ambisonics, 3=projection ambisonics, 4..239: reserved,
           240..254: experiments, 255: multistream with no mapping)

- if (mapping != 0)
   - N = total number of streams (8 bits)
   - M = number of paired streams (8 bits)
   - if (mapping != a projection family)
     - C times channel origin
        - if (C<2*M)
           - stream = byte/2
           - if (byte&0x1 == 0)
               - left
             else
               - right
        - else
           - stream = byte-M
  - else
     - D demixing matrix (C*(N+M)*16 bits)
*/
final class JOpusHeader {
	/** java: data is an offset to base. Use base[ data ]. */
	private static final class JPacket {
		private final byte[] base;// java added
		private final int data;// java changed to offset
		private final int maxlen;
		private int pos;

		private JPacket(final byte[] b, final int d, final int len, final int p) {
			base = b;
			data = d;
			maxlen = len;
			pos = p;
		}

		private final boolean write_uint32(final int val)
		{
			if( this.pos > this.maxlen - 4 ) {
				return false;
			}
			int off = this.data + this.pos;// java
			this.base[ off++ ] = (byte)(val      );
			this.base[ off++ ] = (byte)(val >> 8 );
			this.base[ off++ ] = (byte)(val >> 16);
			this.base[ off   ] = (byte)(val >> 24);
			this.pos += 4;
			return true;
		}

		private final boolean write_uint16(final short val)// TODO java: use int to increase speed
		{
			if( this.pos > this.maxlen - 2 ) {
				return false;
			}
			int off = this.data + this.pos;// java
			this.base[ off++ ] = (byte)(val    );
			this.base[ off   ] = (byte)(val >> 8);
			this.pos += 2;
			return true;
		}

		private final boolean write_chars(final byte[] str, final int nb_chars)
		{
			if( this.pos > this.maxlen - nb_chars ) {
				return false;
			}
			for( int i = 0, pi = this.data + this.pos; i < nb_chars; i++, pi++ ) {
				this.base[ pi ] = str[ i ];
			}
			this.pos += nb_chars;
			return true;
		}

		// java: added a version for 1 byte
		private final boolean write_chars(final byte str)
		{
			if( this.pos > this.maxlen - 1 ) {
				return false;
			}
			this.base[ this.data + this.pos++ ] = str;
			return true;
		}

		private final boolean write_matrix_chars(final JOpusGenericEncoder st)
		{
	// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
			final Object request[] = new Object[1];// java helper
			int ret = st.opeint_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_SIZE, request );
			final int size = ((Integer)request[0]).intValue();
			if( ret != Jopus_defines.OPUS_OK ) {
				return false;
			}
			if( size > this.maxlen - this.pos ) {
				return false;
			}
			ret = st.opeint_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX, this.base, this.data + this.pos, size );
			if( ret != Jopus_defines.OPUS_OK ) {
				return false;
			}
			this.pos += size;
			return true;
	/* #else
			(void)p;
			(void)st;
			return 0;
	#endif */
		}
	}// JPacket

	// private int version;// FIXME isn't used
	int channels; /* Number of channels: 1..255 */
	int preskip;
	int input_sample_rate;
	int gain; /* in dB S7.8 should be zero whenever possible */
	int channel_mapping;
	/* The rest is only used if channel_mapping != 0 */
	int nb_streams;
	int nb_coupled;
	final char stream_map[] = new char[255];// java: uint16 instead of unsigned byte

	final int opeint_opus_header_get_size()
	{
		// int len;
		if( JOpusGenericEncoder.opeint_use_projection( this.channel_mapping ) )
		{
			/* 19 bytes from fixed header,
			 * 2 bytes for nb_streams & nb_coupled,
			 * 2 bytes per cell of demixing matrix, where:
			 *    rows=channels, cols=nb_streams+nb_coupled
			 */
			return 21 + ((this.channels * (this.nb_streams + this.nb_coupled)) << 1);
		}
		//else
		//{
			/* 19 bytes from fixed header,
			 * 2 bytes for nb_streams & nb_coupled,
			 * 1 byte per channel
			 */
			return 21 + this.channels;
		//}
		//return len;
	}

	final int opeint_opus_header_to_packet(final byte[] packet, final int poffset, final int len, final JOpusGenericEncoder st)
	{
		final JPacket p = new JPacket( packet, poffset, len, 0 );
		// p.base = packet;// java
		// p.data = poffset;// java packet;
		// p.maxlen = len;
		// p.pos = 0;
		if( len < 19 ) {
			return 0;
		}
		if( ! p.write_chars( "OpusHead".getBytes( Charset.forName( "UTF-8" ) ), 8 ) ) {
			return 0;
		}
		/* Version is 1 */
		if( ! p.write_chars( (byte)1 ) ) {
			return 0;
		}

		if( ! p.write_chars( (byte)this.channels ) ) {
			return 0;
		}

		if( ! p.write_uint16( (short)this.preskip ) ) {
			return 0;
		}

		if( ! p.write_uint32( this.input_sample_rate ) ) {
			return 0;
		}

		if( JOpusGenericEncoder.opeint_use_projection( this.channel_mapping ) )
		{
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
			final Object request[] = new Object[1];// java helper
			final int ret = st.opeint_encoder_ctl( Jopus_projection.OPUS_PROJECTION_GET_DEMIXING_MATRIX_GAIN, request );
			final int matrix_gain = ((Integer)request[0]).intValue();
			if( ret != Jopus_defines.OPUS_OK ) {
				return 0;
			}
			if( ! p.write_uint16( (short)(this.gain + matrix_gain) ) ) {
				return 0;
			}
// #else
//			return 0;
// #endif
		}
		else
		{
			if( ! p.write_uint16( (short)this.gain ) ) {
				return 0;
			}
		}

		if( ! p.write_chars( (byte)this.channel_mapping ) ) {
			return 0;
		}

		if( this.channel_mapping != 0 )
		{
			if( ! p.write_chars( (byte)this.nb_streams ) ) {
				return 0;
			}

			if( ! p.write_chars( (byte)this.nb_coupled ) ) {
				return 0;
			}

			/* Multi-stream support */
			if( JOpusGenericEncoder.opeint_use_projection( this.channel_mapping ) )
			{
				if( ! p.write_matrix_chars( st ) ) {
					return 0;
				}
			}
			else
			{
				final char[] map = this.stream_map;
				for( int i = 0, ie = this.channels; i < ie; i++ )
				{
					if( ! p.write_chars( (byte)map[i] ) ) {
						return 0;
					}
				}
			}
		}

		return p.pos;
	}
}
