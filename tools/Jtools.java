package tools;

final class Jtools {
	static final String PACKAGE_NAME = "Jopus-tools";
	static final String PACKAGE_VERSION = "0.2";
	/*
	private static final boolean DEBUG = false;//true;
	static void debug_write(final int id, final byte[] data, final int length) {
		if( DEBUG ) {
			FileOutputStream out = null;
			try {
				out = new FileOutputStream( String.format( "packet_%s.bin", Integer.toString( id ) ) );
				out.write( data, 0, length );
			} catch(final Exception e) {

			} finally {
				if( out != null ) {
					try {
						out.close();
					} catch( final IOException e ) {
					}
				}
			}
		}
	}
	static void debug_write(final int id, final float[] data, final int length) {
		if( DEBUG ) {
			FileOutputStream out = null;
			try {
				final byte[] buf = new byte[ length * 2 ];
				for( int i = 0, i2 = 0; i < length; i++ ) {
					short v = (short)(data[i] * 32768f);
					v = v > 32767 ? 32767 : (v >= -32768 ? v : -32768);
					buf[ i2++ ] = (byte)v;
					buf[ i2++ ] = (byte)(v >> 8);
				}
				out = new FileOutputStream( String.format( "packet_%s.bin", Integer.toString( id ) ) );
				out.write( buf );
			} catch(final Exception e) {

			} finally {
				if( out != null ) {
					try {
						out.close();
					} catch( final IOException e ) {
					}
				}
			}
		}
	}*/
}
