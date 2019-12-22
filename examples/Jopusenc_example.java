package examples;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import libenc.JOggOpusComments;
import libenc.JOggOpusEnc;

final class Jopusenc_example {
	private static final String CLASS_NAME = "Jopusenc_example";

	private static final int READ_SIZE = 256;

	public static final void main(final String[] argv) {
		if( argv.length != 3 - 1 ) {// java -1
			System.err.printf("usage: %s <raw pcm input> <Ogg Opus output>\n", CLASS_NAME );
			System.exit( 1 );
			return;
		}
		RandomAccessFile fin = null;
		try {
			fin = new RandomAccessFile( argv[1 - 1], "r" );
			// if( null == fin ) {
			//	System.err.printf("cannot open input file: %s\n", argv[1 - 1]);
			//	System.exit( 1 );
			//	return;
			// }
			final JOggOpusComments comments = JOggOpusComments.ope_comments_create();
			comments.ope_comments_add( "ARTIST", "Someone" );
			comments.ope_comments_add( "TITLE", "Some track" );
			final JOggOpusEnc enc = JOggOpusEnc.ope_encoder_create_file( argv[2 - 1], comments, 44100, 2, 0 );
			// if( null == enc ) {
			//	System.err.printf("error encoding to file %s: %s\n", argv[2 - 1], JOggOpusEnc.ope_strerror( error ) );
			//	JOggOpusComments.ope_comments_destroy( comments );
			//	fin.close();
			//	System.exit( 1 );
			//	return;
			// }
			final short buf[] = new short[ 2 * READ_SIZE ];
			final byte bytebuf[] = new byte[ 2 * READ_SIZE * (Short.SIZE / 8) ];
			while( true ) {
				final int ret = fin.read( bytebuf );
				if( ret > 0 ) {// TODO java: should we check ret is an even value?
					// java: byte little endian -> short
					ByteBuffer.wrap( bytebuf, 0, ret ).order( ByteOrder.LITTLE_ENDIAN ).asShortBuffer().get( buf, 0, ret >>> 1 );
					enc.ope_encoder_write( buf, ret );
				} else {
					break;
				}
			}
			enc.ope_encoder_drain();
			enc.ope_encoder_destroy();
			JOggOpusComments.ope_comments_destroy( comments );
		} catch(final Exception e) {
			System.err.printf( e.getMessage() );
			e.printStackTrace();
		} finally {
			try { if( fin != null ) {
				fin.close();
			} } catch( final IOException e ) {}
		}
		return;
	}
}