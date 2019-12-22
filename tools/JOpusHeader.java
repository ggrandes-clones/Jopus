package tools;

import java.util.Arrays;

/** Header contents:
  - "OpusHead" (64 bits)
  - version number (8 bits)
  - Channels C (8 bits)
  - Pre-skip (16 bits)
  - Sampling rate (32 bits)
  - Gain in dB (16 bits, S7.8)
  - Mapping (8 bits, 0=single stream (mono/stereo) 1=Vorbis mapping,
			 2..254: reserved, 255: multistream with no mapping)

  - if( mapping != 0)
	 - N = total number of streams (8 bits)
	 - M = number of paired streams (8 bits)
	 - C times channel origin
		  - if( C < 2*M)
			 - stream = byte/2
			 - if( byte&0x1 == 0)
				 - left
			   else
				 - right
		  - else
			 - stream = byte-M
*/
final class JOpusHeader {
	/** This is just here because it's a convenient file linked by both opusenc and
	   opusdec (to guarantee this maps stays in sync). */
	static final int wav_permute_matrix[][] =// [8][8] =
		{
			{ 0 },              /* 1.0 mono   */
			{ 0, 1 },            /* 2.0 stereo */
			{ 0, 2, 1 },          /* 3.0 channel ('wide') stereo */
			{ 0, 1, 2, 3 },        /* 4.0 discrete quadraphonic */
			{ 0, 2, 1, 3, 4 },      /* 5.0 surround */
			{ 0, 2, 1, 4, 5, 3 },    /* 5.1 surround */
			{ 0, 2, 1, 5, 6, 4, 3 },  /* 6.1 surround */
			{ 0, 2, 1, 6, 7, 4, 5, 3 } /* 7.1 surround (classic theater 8-track) */
		};
	//
	private static final int OPUS_DEMIXING_MATRIX_SIZE_MAX = (18 * 18 * 2);
	//
	int version;
	/** Number of channels: 1..255 */
	int channels;
	int preskip;
	int input_sample_rate;
	int gain; /* in dB S7.8 should be zero whenever possible */
	int channel_mapping;
	/* The rest is only used if channel_mapping != 0 */
	int nb_streams;
	int nb_coupled;
	final char stream_map[] = new char[255];
	final byte dmatrix[] = new byte[OPUS_DEMIXING_MATRIX_SIZE_MAX];
	//
	JOpusHeader() {
	}
	//
	private static final class JROPacket {
		private final byte[] data;
		private final int maxlen;
		private int pos;
		//
		private JROPacket(final byte[] b, final int off, final int len) {
			data = b;
			pos = off;
			maxlen = len;
		}
		private int read_uint32() throws ArrayIndexOutOfBoundsException
		{
			if( this.pos > this.maxlen - 4 ) {
				throw new ArrayIndexOutOfBoundsException( this.pos );// return 0;
			}
			int i = this.pos;
			int val =  (int)this.data[i++] & 0xff;
			val |= ((int)this.data[i++] & 0xff) <<  8;
			val |= ((int)this.data[i++] & 0xff) << 16;
			val |= ((int)this.data[i++] & 0xff) << 24;
			this.pos = i;
			// return 1;
			return val;
		}

		private int read_uint16() throws ArrayIndexOutOfBoundsException
		{
			if( this.pos > this.maxlen - 2 ) {
				throw new ArrayIndexOutOfBoundsException( this.pos );// return 0;
			}
			int i = this.pos;
			int val = (int)this.data[i++] & 0xff;
			val |= ((int)this.data[i++] & 0xff) << 8;
			this.pos = i;
			// return 1;
			return val;
		}

		private void read_chars(final byte[] str, final int nb_chars)
		{
			if( this.pos > this.maxlen - nb_chars ) {
				throw new ArrayIndexOutOfBoundsException( this.pos );// return 0;
			}
			for( int i = 0; i < nb_chars; i++ ) {
				str[i] = this.data[this.pos++];
			}
			// return 1;
		}
		private int read_char()
		{
			if( this.pos > this.maxlen - 1 ) {
				throw new ArrayIndexOutOfBoundsException( this.pos );// return 0;
			}
			return this.data[this.pos++] & 0xff;
		}
	};

	final boolean opus_header_parse(final byte[] packet, final int offset, final int len)
	{
		try {
			final JROPacket p = new JROPacket( packet, offset, len );

			final byte str[] = new byte[8];// str[8] = 0;// java already zeroed FIXME why 9? why need 0 at end?
			if( len < 19 ) {
				return false;
			}
			p.read_chars( str, 8 );
			if( ! Arrays.equals( str, "OpusHead".getBytes() ) ) {
				return false;
			}

			this.version = p.read_char();
			if( (this.version & 240) != 0 ) {
				return false;
			}

			this.channels = p.read_char();
			if( this.channels == 0) {
				return false;
			}

			this.preskip = p.read_uint16();

			this.input_sample_rate = p.read_uint32();

			this.gain = p.read_uint16();

			this.channel_mapping = p.read_char();

			if( this.channel_mapping != 0 )
			{
				int ch = p.read_char();

				if( ch < 1 ) {
					return false;
				}
				this.nb_streams = ch;

				ch = p.read_char();

				if( ch > this.nb_streams || (ch + this.nb_streams) > 255 ) {
					return false;
				}
				this.nb_coupled = ch;

				/* Multi-stream support */
				if( this.channel_mapping == 3 )
				{
					final int dmatrix_size = (this.channels * (this.nb_streams + this.nb_coupled)) << 1;
					if( dmatrix_size > len - p.pos ) {
						return false;
					}
					if( dmatrix_size > OPUS_DEMIXING_MATRIX_SIZE_MAX ) {
						p.pos += dmatrix_size;
					} else {
						p.read_chars( this.dmatrix, dmatrix_size );
						// if( ! p.read_chars( this.dmatrix, dmatrix_size ) ) {
						//	return false;
						// }
					}
					for( int i = 0; i < this.channels; i++ ) {
						this.stream_map[i] = (char)i;
					}
				}
				else
				{

					for( int i = 0; i < this.channels; i++ )
					{
						ch = p.read_char();
						if( ch > (this.nb_streams + this.nb_coupled) && ch != 255 ) {
							return false;
						}
						this.stream_map[i] = (char)ch;
					}
				}
			} else {
				if( this.channels > 2 ) {
					return false;
				}
				this.nb_streams = 1;
				this.nb_coupled = this.channels > 1 ? 1 : 0;
				this.stream_map[0] = 0;
				this.stream_map[1] = 1;
			}
			/*For version 0/1 we know there won't be any more data
			so reject any that have data past the end.*/
			if( (this.version == 0 || this.version == 1) && p.pos != len ) {
				return false;
			}
			return true;
		} catch(final ArrayIndexOutOfBoundsException e) {
			return false;
		}
	}
}
