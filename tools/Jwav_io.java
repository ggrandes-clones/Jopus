package tools;

import java.io.IOException;
import java.io.RandomAccessFile;

/* Copyright (C) 2002 Jean-Marc Valin
   File: wav_io.c
   Routines to handle wav (RIFF) headers

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

// wav_io.c

final class Jwav_io {

	/** Adjust the stream->channel mapping to ensure the proper output order for
	   WAV files. */
	static final void adjust_wav_mapping(final int mapping_family, final int channels, final char[] stream_map)
	{
		/* If we aren't using one of the defined semantic channel maps, or we have
		more channels than we know what to do with, use a default 1-1 mapping. */
		if( mapping_family != 1 || channels > 8 ) {
			return;
		}
		final char new_stream_map[] = new char[8];
		for( int i = 0; i < channels; i++ )
		{
			new_stream_map[ JOpusHeader.wav_permute_matrix[channels - 1][i] ] = stream_map[i];
		}
		System.arraycopy( new_stream_map, 0, stream_map, 0, channels );
	}

	private static final void fwrite_le32(final int i32, final RandomAccessFile file) throws IOException
	{
		final byte buf[] = new byte[4];
		buf[0] = (byte)(i32);
		buf[1] = (byte)(i32 >> 8);
		buf[2] = (byte)(i32 >> 16);
		buf[3] = (byte)(i32 >> 24);
		file.write( buf, 0, 4 );
	}

	private static final void fwrite_le16(final int i16, final RandomAccessFile file) throws IOException
	{
		final byte buf[] = new byte[2];
		buf[0] = (byte)(i16);
		buf[1] = (byte)(i16 >> 8);
		file.write( buf, 0, 2 );
	}

	private static final byte ksdataformat_subtype_pcm[] = //[16] =
	{
		0x01, 0x00, 0x00, 0x00,
		0x00, 0x00,
		0x10, 0x00,
		(byte)0x80, 0x00,
		0x00, (byte)0xaa, 0x00, 0x38, (byte)0x9b, 0x71
	};
	private static final byte ksdataformat_subtype_float[] = //[16]=
	{
		0x03, 0x00, 0x00, 0x00,
		0x00, 0x00,
		0x10, 0x00,
		(byte)0x80, 0x00,
		0x00, (byte)0xaa, 0x00, 0x38, (byte)0x9b, 0x71
	};
	private static final int wav_channel_masks[] = //[8] =
	{
		4,                      /* 1.0 mono */
		1|2,                    /* 2.0 stereo */
		1|2|4,                  /* 3.0 channel ('wide') stereo */
		1|2|16|32,              /* 4.0 discrete quadrophonic */
		1|2|4|16|32,            /* 5.0 */
		1|2|4|8|16|32,          /* 5.1 */
		1|2|4|8|256|512|1024,   /* 6.1 */
		1|2|4|8|16|32|512|1024, /* 7.1 */
	};

	static int write_wav_header(final RandomAccessFile file, final int rate, final int mapping_family, final int channels, final boolean fp) throws IOException
	{
		/* Multichannel files require a WAVEFORMATEXTENSIBLE header to declare the
		proper channel meanings. */
		boolean extensible = mapping_family == 1 && 3 <= channels && channels <= 8;

		/* >16 bit audio also requires WAVEFORMATEXTENSIBLE. */
		extensible |= fp;

		file.write( "RIFF".getBytes() );
		fwrite_le32( 0x7fffffff, file );

		file.write("WAVEfmt ".getBytes() );
		fwrite_le32( extensible ? 40 : 16, file );
		fwrite_le16( extensible ? 0xfffe : (fp ? 3 : 1), file );
		fwrite_le16( channels, file );
		fwrite_le32( rate, file );
		fwrite_le32( (fp ? 4 : 2) * channels * rate, file );
		fwrite_le16( (fp ? 4 : 2) * channels, file );
		fwrite_le16( fp ? 32 : 16, file );

		if( extensible )
		{
			fwrite_le16( 22, file );
			fwrite_le16( fp ? 32 : 16, file );
			fwrite_le32( wav_channel_masks[channels - 1], file );
			if( ! fp )
			{
				file.write( ksdataformat_subtype_pcm, 0, 16 );
			} else {
				file.write( ksdataformat_subtype_float, 0, 16 );
			}
		}

		file.write("data".getBytes() );
		fwrite_le32( 0x7fffffff, file );

		return extensible ? 40 : 16;
	}
}