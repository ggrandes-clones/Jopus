package tools;

import java.io.PrintStream;

import opus.JOpusDecoder;
import opus.Jopus;
import opus.Jopus_defines;
import opus.Jopus_packet_data_aux;

// diag_range.c

/** This is some non-exported code copied wholesale from libopus.
 *Normal programs shouldn't need these functions, but we use them here
 *to parse deep inside multichannel packets in order to get diagnostic
 *data for save-range. If you're thinking about copying it and you aren't
 *making an opus stream diagnostic tool, you're probably doing something
 *wrong. */
final class Jdiag_range {
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
	/**
	 * java changed: out_toc, payload_offset, packet_offset replaced by Jopus_packet_data_aux
	 * important: before calling save data pointer to later use with frames[]
	 */
	private static final int opus_packet_parse_impl(final byte[] data, int doffset,// java
			int len,
			final boolean self_delimited,
			final short size[/* 48 */],
			int soffset,// java added
			// final int[] payload_offset, final int[] packet_offset
			final Jopus_packet_data_aux aux// java
			)
	{
		if( size == null || len < 0 ) {
			return Jopus_defines.OPUS_BAD_ARG;
		}
		if( len == 0 ) {
			return Jopus_defines.OPUS_INVALID_PACKET;
		}

		final int data0 = doffset;
		final int framesize = Jopus.opus_packet_get_samples_per_frame( data, doffset, 48000 );

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

		for( final int ie = soffset + count; soffset < ie; ) {
			doffset += size[soffset++];
		}

		if( aux != null ) {// if( null != packet_offset ) {
			aux.mPacketOffset = pad + (doffset - data0);
		}

		return count;
	}

	private static final String bw_strings[/* 5 */] = {"NB", "MB", "WB", "SWB", "FB"};
	private static final String mode_strings[/* 3 */] = {"LP", "HYB", "MDCT"};

	@SuppressWarnings("boxing")
	static final void save_range(final PrintStream frange, final int frame_size, final byte[] packet, int subpkt, final int nbBytes, final long[] rngs, final int nb_streams ) {
		frange.printf("%d, %d, ", frame_size, nbBytes );
		// int subpkt = 0;// packet[ subpkt ]
		int parsed_size = nbBytes;
		final short size[] = new short[48];
		final Jopus_packet_data_aux aux = new Jopus_packet_data_aux();
		for( int i = 0; i < nb_streams; i++ ) {
			aux.mPacketOffset = 0;
			aux.mPayloadOffset = 0;
			final int nf = opus_packet_parse_impl( packet, subpkt, parsed_size, i + 1 != nb_streams, size, 0, aux );
			frange.printf("[[%d", aux.mPayloadOffset );
			for( int j = 0; j < nf; j++ ) {
				frange.printf(", %d", (int)size[j] );
			}
			frange.printf("], %s, %s, %c, %d", mode_strings[ ((((((int)packet[ subpkt ] & 0xff) >> 3) + 48) & 92) + 4) >> 5 ],
							bw_strings[JOpusDecoder.opus_packet_get_bandwidth( packet, subpkt ) - Jopus_defines.OPUS_BANDWIDTH_NARROWBAND],
							(packet[ subpkt ] & 4) != 0 ? 'S' : 'M', Jopus.opus_packet_get_samples_per_frame( packet, subpkt, 48000 ) );
			frange.printf(", %d]%s", rngs[i], i + 1 == nb_streams ? "\n" : ", ");
			parsed_size -= aux.mPacketOffset;
			subpkt += aux.mPacketOffset;
		}
	}
	// end diag_range.c
}
