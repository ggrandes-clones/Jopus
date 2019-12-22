package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * <p>Java version of the bash script "run_vectors.sh" to test the codec.
 *
 * <p>The app can be used to verify all test vectors.
 * <p>This can be done with
 *
 * <blockquote>
 * <code><pre>java -jar Jrun_vectors.jar &lt;vector path&gt; &lt;rate&gt;</pre></code>
 * </blockquote>
 *
 * <p>&lt;vector path&gt; is the directory containing the test vectors,<br>
 * and &lt;rate&gt; is the sampling rate to test (8000, 12000, 16000, 24000, or 48000).
 */
final class Jrun_vectors {
	private static final String CLASS_NAME = "Jrun_vectors";

	private static final String join(final String dir, final String file) {
		if( dir == null || dir.isEmpty() || dir.equals("../..") || dir.equals("..\\..") ) {
			return file;
		}
		return dir + File.separator + file;
	}

	private static final double getQuality(final String filePath) {
		double q = 0.;
		int nr = 0;
		BufferedReader in = null;
		try {
			in = new BufferedReader( new InputStreamReader( new FileInputStream( filePath ), "UTF-8") );
			String str;
			while( (str = in.readLine()) != null ) {
				// "Opus quality metric: %.1f %% (internal weighted error is %f)\n"
				if( str.indexOf("quality") >= 0 ) {
					final String[] s = str.split(" ");
					if( s.length >= 4 ) {
						try {
							q += Double.parseDouble( s[3].replace( ',', '.' ) );
							nr++;
						} catch (final NumberFormatException ne) {
						}
					}
				}
			}
		} catch( final UnsupportedEncodingException e ) {
		} catch( final FileNotFoundException e ) {
		} catch( final IOException e ) {
		} finally {
			if( in != null ) {
				try { in.close(); } catch (final IOException e) {}
			}
		}
		if( nr != 12 ) {
			return 0.;
		}
		return q / (double)nr;
	}

	public static final void main(final String args[]) {

		if( args.length != 2 ) {
		    System.out.println("usage: " + CLASS_NAME + " <vector path> <rate>");
		    return;
		}

		// final String CMD_PATH = args[ 0 ];
		final String VECTOR_PATH = args[ 0 ];
		final String RATE = args[ 1 ];

		// final String OPUS_DEMO = join( CMD_PATH, "Jopus_demo.jar");
		// final String OPUS_COMPARE = join( CMD_PATH, "Jopus_compare.jar");

		if( new File( VECTOR_PATH ).isDirectory() ) {
			System.out.println("Test vectors found in " + VECTOR_PATH );
		} else {
			System.out.println("No test vectors found");
			// Don't make the test fail here because the test vectors
			// will be distributed separately
			System.exit( 0 );
			return;
		}

		/* if( ! new File( OPUS_COMPARE ).canExecute() ) {
			System.out.println("ERROR: Compare program not found: " + OPUS_COMPARE );
			System.exit( 1 );
			return;
		}

		if( new File( OPUS_DEMO ).canExecute() ) {
			System.out.println("Decoding with " + OPUS_DEMO );
		}
		else {
			System.out.println("ERROR: Decoder not found: " + OPUS_DEMO );
			System.exit( 1 );
			return;
		} */

		System.out.println("==============");
		System.out.println("Testing mono");
		System.out.println("==============");
		System.out.println();

		new File("logs_mono.txt").delete();
		new File("logs_stereo.txt").delete();
		new File("logs_mono2.txt").delete();
		new File("logs_stereo2.txt").delete();

		final PrintStream stdout = System.out;
		final PrintStream stderr = System.err;

		PrintStream out1 = null;
		PrintStream out2 = null;
		try {
			out1 = new PrintStream( new FileOutputStream("logs_mono.txt", true), true, "UTF-8");
			out2 = new PrintStream( new FileOutputStream("logs_mono2.txt", true), true, "UTF-8");

			for( int f = 01; f <= 12; f++ ) {
				final String file = "testvector" + String.format("%02d", Integer.valueOf( f ) );
				if( new File( join( VECTOR_PATH, file + ".bit" ) ).isFile() ) {
					System.out.println("Testing " + file );
				} else {
					System.out.println("Bitstream file not found: " + file + ".bit" );
					continue;
				}

				System.setOut( out1 );
				System.setErr( out1 );
				if( Jopus_demo.maininternal( new String[]{"-d", RATE, "1", VECTOR_PATH + File.separator + file + ".bit", "tmp.out"} ) == 0 ) {
					System.setOut( stdout );
					System.out.println("successfully decoded");
				} else {
					System.setOut( stdout );
					System.out.println("ERROR: decoding failed");
					System.exit( 1 );
					return;
				}
				System.setOut( out1 );
				final int float_ret = Jopus_compare.maininternal( new String[] {"-r", RATE, VECTOR_PATH + File.separator + file + ".dec", "tmp.out"} );
				System.setOut( out2 );
				System.setErr( out2 );
				final int float_ret2 = Jopus_compare.maininternal( new String[] {"-r", RATE, VECTOR_PATH + File.separator + file + "m.dec", "tmp.out"} );
				if( float_ret == 0 || float_ret2 == 0 ) {
					System.setOut( stdout );
					System.out.println("output matches reference");
				} else {
					System.setOut( stdout );
					System.out.println("ERROR: output does not match reference");
					System.exit( 1 );
					return;
				}
				System.out.println();
			}
		} catch(final Exception e) {
			System.setOut( stdout );
			System.setErr( stderr );
			e.printStackTrace();
			System.exit( 1 );
			return;
		} finally {
			System.setOut( stdout );
			System.setErr( stderr );
			if( out1 != null ) {
				out1.close();
			}
			if( out2 != null ) {
				out2.close();
			}
			out1 = null;
			out2 = null;
		}

		System.out.println("==============");
		System.out.println("Testing stereo");
		System.out.println("==============");
		System.out.println();

		try {
			out1 = new PrintStream( new FileOutputStream("logs_stereo.txt", true), true, "UTF-8");
			out2 = new PrintStream( new FileOutputStream("logs_stereo2.txt", true), true, "UTF-8");
			for( int f = 01; f <= 12; f++ ) {
				final String file = "testvector" + String.format("%02d", Integer.valueOf( f ) );
				if( new File( join( VECTOR_PATH, file + ".bit" ) ).isFile() ) {
					System.out.println("Testing " + file);
				} else {
					System.out.println("Bitstream file not found: " + file );
					continue;
				}
				System.setOut( out1 );
				System.setErr( out1 );
				if( Jopus_demo.maininternal( new String[]{ "-d", RATE, "2", VECTOR_PATH + File.separator + file + ".bit", "tmp.out" } ) == 0 ) {
					System.setOut( stdout );
					System.out.println("successfully decoded");
				} else {
					System.setOut( stdout );
					System.out.println("ERROR: decoding failed");
					System.exit( 1 );
					return;
				}
				System.setOut( out1 );
				final int float_ret = Jopus_compare.maininternal( new String[]{ "-s", "-r", RATE, VECTOR_PATH + File.separator + file + ".dec", "tmp.out"} );
				System.setOut( out2 );
				System.setErr( out2 );
				final int float_ret2 = Jopus_compare.maininternal( new String[]{ "-s", "-r", RATE, VECTOR_PATH + File.separator + file + "m.dec", "tmp.out"} );
				if( float_ret == 0 || float_ret2 == 0 ) {
					System.setOut( stdout );
					System.out.println("output matches reference");
				} else {
					System.setOut( stdout );
					System.out.println("ERROR: output does not match reference");
					System.exit( 1 );
					return;
				}
				System.out.println();
			}
		} catch(final Exception e) {
			e.printStackTrace();
			System.exit( 1 );
			return;
		} finally {
			System.setOut( stdout );
			System.setErr( stderr );
			if( out1 != null ) {
				out1.close();
			}
			if( out2 != null ) {
				out2.close();
			}
			out1 = null;
			out2 = null;
		}

		System.out.println("All tests have passed successfully");

		final double mono1  = getQuality("logs_mono.txt");
		System.out.println( mono1 );
		final double mono2  = getQuality("logs_mono2.txt");
		System.out.println( mono2 );
		System.out.println("Average mono quality is " + Math.max( mono1, mono2 ) + "%");

		final double stereo1  = getQuality("logs_stereo.txt");
		System.out.println( stereo1 );
		final double stereo2  = getQuality("logs_stereo2.txt");
		System.out.println( stereo2 );
		System.out.println("Average stereo quality is " + Math.max( stereo1, stereo2 ) + "%");
	}
}
