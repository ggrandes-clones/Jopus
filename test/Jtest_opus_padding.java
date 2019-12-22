package test;

import java.util.Arrays;

import celt.Jcelt;
import opus.JOpusDecoder;
import opus.Jopus_defines;

/* Copyright (c) 2012 Xiph.Org Foundation
   Written by Jüri Aedla and Ralph Giles */
/*
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
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* Check for overflow in reading the padding length.
 * http://lists.xiph.org/pipermail/opus/2012-November/001834.html
 */

// test_opus_padding.c

final class Jtest_opus_padding {
	private static final String CLASS_NAME = "Jtest_opus_padding";

	private static final int PACKETSIZE = 16909318;
	private static final int CHANNELS = 2;
	private static final int FRAMESIZE = 5760;

	private static final int test_overflow()
	{
		final String method_name = "test_overflow";

		byte[] in = new byte[ PACKETSIZE ];
		short[] out = new short[ FRAMESIZE * CHANNELS ];

		System.out.printf("  Checking for padding overflow... ");
		if( null == in || null == out ) {
			System.out.printf("FAIL (out of memory)\n");
			return -1;
		}
		in[0] = -1;// 0xff;
		in[1] = 0x41;
		Arrays.fill( in, 2, PACKETSIZE - 1, (byte)0xff );
		in[ PACKETSIZE - 1 ] = 0x0b;

		final int[] error = new int[1];
		JOpusDecoder decoder = JOpusDecoder.opus_decoder_create( 48000, CHANNELS, error );
		final int result = decoder.opus_decode( in, PACKETSIZE, out, 0, FRAMESIZE, false );
		decoder = null;// opus_decoder_destroy( decoder );

		in = null;
		out = null;

		if( result != Jopus_defines.OPUS_INVALID_PACKET ) {
			System.out.printf("FAIL!\n");
			Jtest_opus_common.test_failed( CLASS_NAME, method_name );
		}

		System.out.printf("OK.\n");

		return 1;
	}

	public static final void main(final String args[])// int main()
	{
		Jtest_opus_common.iseed = 0;
		final String oversion = Jcelt.opus_get_version_string();
		if( null == oversion ) {
			Jtest_opus_common.test_failed( CLASS_NAME, "null == oversion" );
		}
		System.out.printf("Testing %s padding.\n", oversion );

		test_overflow();

		System.out.printf("All padding tests passed.\n");

		System.exit( 0 );
		return;
	}
}