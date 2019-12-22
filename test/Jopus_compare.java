/* Copyright (c) 2011-2012 Xiph.Org Foundation, Mozilla Corporation
   Written by Jean-Marc Valin and Timothy B. Terriberry */
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

package test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

// opus/opus_compare.c
//java: modified for test using Jrun_vectors
final class Jopus_compare {
	private static final String CLASS_NAME = "Jopus_compare";
	private static final int EXIT_SUCCESS = 0;
	private static final int EXIT_FAILURE = 1;

	private static final float OPUS_PI = (3.14159265F);

	private static final float OPUS_COSF(final float _x) { return ((float)Math.cos(_x) ); }
	private static final float OPUS_SINF(final float _x) { return ((float)Math.sin(_x) ); }
/*
	private static final void *check_alloc(void *_ptr) {
		if( _ptr == null ) {
			System.err.printf("Out of memory.\n");
			System.exit( EXIT_FAILURE );
		}
		return _ptr;
	}

	private static final void *opus_malloc(final size_t _size) {
		return check_alloc( malloc(_size) );
	}

	private static final void *opus_realloc(void *_ptr,size_t _size) {
		return check_alloc(realloc(_ptr,_size));
	}
*/
	@SuppressWarnings("null")// incorrect warning
	private static int read_pcm16(final float[][] _samples, final RandomAccessFile _fin, final int _nchannels ) {
		final byte buf[] = new byte[1024];
		float[] samples = null;
		int nsamples = 0, csamples = 0;
		try {
			for( ; ; ) {
				int nread = _fin.read( buf, 0, (2 *_nchannels) * (1024 / (2 * _nchannels)) );
				if( nread <= 0 ) {
					break;
				}
				nread /= (2 *_nchannels);// java, element size
				if( nsamples + nread > csamples ) {
					do {
						csamples = csamples << 1 | 1;
					} while( nsamples + nread > csamples );
					if( samples == null ) {
						samples = new float[ _nchannels * csamples ];
					} else {
						samples = Arrays.copyOf( samples, _nchannels * csamples );
					}
				}
				for( int xi = 0; xi < nread; xi++ ) {
					for( int ci = 0; ci < _nchannels; ci++ ) {
						int s = (((int)buf[2 * (xi * _nchannels + ci) + 1] & 0xff) << 8) | ((int)buf[2 * (xi * _nchannels + ci)] & 0xff);
						s = ((s & 0xFFFF) ^ 0x8000) - 0x8000;
						samples[(nsamples + xi) * _nchannels + ci] = (float) s;
					}
				}
				nsamples += nread;
			}
		} catch (final IOException ie) {
		}
		if( samples == null ) {
			_samples[0] = new float[ _nchannels * nsamples ];
			return nsamples;
		}
		_samples[0] = Arrays.copyOf( samples, _nchannels * nsamples );
		return nsamples;
	}

	private static final void band_energy(final float[] _out, final float[] _ps, final int[] _bands, final int _nbands,
			final float[] _in, final int _nchannels, final int _nframes, final int _window_sz,
			final int _step, final int _downsample) {
		float[] window = new float[ (3 + _nchannels) * _window_sz ];
		final int c = 0 + _window_sz;// java window[ c ]
		final int s = c + _window_sz;// java window[ s ]
		final int x = s + _window_sz;// java window[ x ]
		final int ps_sz = _window_sz / 2;
		int xj;
		for( xj = 0; xj < _window_sz; xj++ ) {
			window[xj] = 0.5F - 0.5F * OPUS_COSF((2 * OPUS_PI / (_window_sz - 1)) * xj);
		}
		for( xj = 0; xj < _window_sz; xj++ ) {
			window[c + xj] = OPUS_COSF( (2 * OPUS_PI / _window_sz) * xj );
		}
		for( xj = 0; xj < _window_sz; xj++ ) {
			window[s + xj] = OPUS_SINF( (2 * OPUS_PI / _window_sz) * xj );
		}
		for( int xi = 0; xi < _nframes; xi++ ) {
			for( int ci = 0; ci <_nchannels; ci++ ) {
				for( int xk = 0; xk < _window_sz; xk++ ) {
					window[x + ci * _window_sz + xk] = window[xk] * _in[(xi * _step + xk) * _nchannels + ci];
				}
			}
			for( int bi = xj = 0; bi < _nbands; bi++ ) {
				final float p[] = new float[2];// = { 0 };
				for( ; xj < _bands[bi + 1]; xj++ ) {
					for( int ci = 0; ci < _nchannels; ci++ ) {
						float re;
						float im;
						int   ti;
						ti = 0;
						re = im = 0;
						for( int xk = 0; xk < _window_sz; xk++ ) {
							re += window[c + ti] * window[x + ci * _window_sz + xk];
							im -= window[s + ti] * window[x + ci * _window_sz + xk];
							ti += xj;
							if( ti >= _window_sz ) {
								ti -= _window_sz;
							}
						}
						re *= _downsample;
						im *= _downsample;
						_ps[(xi * ps_sz + xj) * _nchannels + ci] = re * re + im * im + 100000;
						p[ci] += _ps[(xi * ps_sz + xj) * _nchannels + ci];
					}
				}
				if( _out != null ) {
					_out[(xi * _nbands + bi) * _nchannels] = p[0] / (_bands[bi + 1] - _bands[bi]);
					if( _nchannels == 2 ) {
						_out[(xi * _nbands + bi) * _nchannels + 1] = p[1] / (_bands[bi + 1] - _bands[bi]);
					}
				}
			}
		}
		window = null;
	}

	private static final int NBANDS = 21;
	private static final int NFREQS = 240;

	/*Bands on which we compute the pseudo-NMR (Bark-derived CELT bands).*/
	private static final int BANDS[/* NBANDS + 1*/] = {
		0, 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 56, 68, 80, 96, 120, 156, 200
	};

	private static final int TEST_WIN_SIZE = 480;
	private static final int TEST_WIN_STEP = 120;

	@SuppressWarnings("boxing")
	public static final int maininternal(final String[] _argv) {
		if( _argv.length < 3 - 1 || _argv.length > 6 - 1 ) {// java
			System.err.printf("Usage: %s [-s] [-r rate2] <file1.sw> <file2.sw>\n",
					CLASS_NAME );
			return EXIT_FAILURE;
		}
		int nchannels = 1;
		int argvn = -1;// java
		if( _argv[1 - 1].compareTo("-s") == 0 ) {// java
			nchannels = 2;
			argvn++;// java _argv++;
		}
		int rate = 48000;
		int ybands = NBANDS;
		int yfreqs = NFREQS;
		int downsample = 1;
		if( _argv[argvn + 1].compareTo("-r") == 0 ) {
			rate = Integer.parseInt( _argv[argvn + 2] );
			if( rate != 8000 && rate != 12000 && rate != 16000 && rate != 24000 && rate != 48000 ) {
				System.err.printf("Sampling rate must be 8000, 12000, 16000, 24000, or 48000\n");
				return EXIT_FAILURE;
			}
			downsample = 48000 / rate;
			switch( rate ) {
			case  8000: ybands = 13; break;
			case 12000: ybands = 15; break;
			case 16000: ybands = 17; break;
			case 24000: ybands = 19; break;
			}
			yfreqs = NFREQS / downsample;
			argvn += 2;// java _argv += 2;
		}
		RandomAccessFile fin1 = null, fin2 = null;
		float[] x, y;
		try {
			fin1 = new RandomAccessFile( _argv[argvn + 1], "r" );
			/* if( fin1 == null ) {
				System.err.printf("Error opening '%s'.\n",_argv[argvn + 1]);
				System.exit( EXIT_FAILURE );
				return;
			} */
			fin2 = new RandomAccessFile( _argv[argvn + 2], "r" );
			/* if( fin2 == null ) {
				System.err.printf("Error opening '%s'.\n",_argv[argvn + 2]);
				fclose(fin1);
				System.exit( EXIT_FAILURE );
				return;
			} */
			/*Read in the data and allocate scratch space.*/
			final float[][] xy = new float[1][];// java helper
			final int xlength = read_pcm16( xy, fin1, 2 );// java
			x = xy[0];// java
			if( nchannels == 1 ) {
				for( int xi = 0; xi < xlength; xi++ ) {
					x[xi] = .5f * (x[2 * xi] + x[2 * xi + 1]);
				}
			}
			// fclose( fin1 );
			final int ylength = read_pcm16( xy, fin2, nchannels );// java
			y = xy[0];// java
			// fclose( fin2 );
			if( xlength != ylength * downsample ) {
				System.err.printf("Sample counts do not match (%d!=%d).\n",
							xlength, ylength * downsample);
				return EXIT_FAILURE;
			}
			if( xlength < TEST_WIN_SIZE ) {
				System.err.printf("Insufficient sample data (%d<%d).\n",
							xlength, TEST_WIN_SIZE );
				return EXIT_FAILURE;
			}
			final int nframes = (xlength - TEST_WIN_SIZE + TEST_WIN_STEP) / TEST_WIN_STEP;
			final float[] xb = new float[ nframes * NBANDS * nchannels ];
			final float[] X = new float[ nframes * NFREQS * nchannels ];
			final float[] Y = new float[ nframes * yfreqs * nchannels ];
			/*Compute the per-band spectral energy of the original signal and the error.*/
			band_energy( xb, X, BANDS, NBANDS, x, nchannels, nframes,
					TEST_WIN_SIZE, TEST_WIN_STEP, 1 );
			x = null;
			band_energy( null, Y, BANDS, ybands, y, nchannels, nframes,
					TEST_WIN_SIZE / downsample, TEST_WIN_STEP / downsample, downsample);
			y = null;
			for( int xi = 0; xi < nframes; xi++ ) {
				/*Frequency masking (low to high): 10 dB/Bark slope.*/
				for( int bi = 1; bi < NBANDS; bi++ ) {
					for( int ci = 0; ci < nchannels; ci++ ) {
						xb[(xi * NBANDS + bi) * nchannels + ci ] +=
								0.1F * xb[(xi * NBANDS + bi - 1) * nchannels + ci];
					}
				}
				/*Frequency masking (high to low): 15 dB/Bark slope.*/
				for( int bi = NBANDS - 1; bi-- > 0; ) {
					for( int ci = 0; ci < nchannels; ci++ ) {
						xb[(xi * NBANDS + bi) * nchannels + ci] +=
								0.03F * xb[(xi * NBANDS + bi + 1) * nchannels + ci];
					}
				}
				if( xi > 0 ) {
					/*Temporal masking: -3 dB/2.5ms slope.*/
					for( int bi = 0; bi < NBANDS; bi++ ) {
						for( int ci = 0; ci < nchannels; ci++ ) {
							xb[(xi * NBANDS + bi) * nchannels + ci] +=
									0.5F * xb[((xi - 1) * NBANDS + bi) * nchannels + ci];
						}
					}
				}
				/* Allowing some cross-talk */
				if( nchannels == 2 ) {
					for( int bi = 0; bi < NBANDS; bi++ ) {
						final float l = xb[(xi * NBANDS + bi) * nchannels + 0];
						final float r = xb[(xi * NBANDS + bi) * nchannels + 1];
						xb[(xi * NBANDS + bi) * nchannels + 0] += 0.01F * r;
						xb[(xi * NBANDS + bi) * nchannels + 1] += 0.01F * l;
					}
				}

				/* Apply masking */
				for( int bi = 0; bi < ybands; bi++ ) {
					for( int xj = BANDS[bi]; xj < BANDS[bi + 1]; xj++ ) {
						for( int ci = 0; ci < nchannels; ci++ ) {
							X[(xi * NFREQS + xj) * nchannels + ci] +=
									0.1F * xb[(xi * NBANDS + bi) * nchannels + ci];
							Y[(xi * yfreqs + xj) * nchannels + ci] +=
									0.1F * xb[(xi * NBANDS + bi) * nchannels + ci];
						}
					}
				}
			}

			/* Average of consecutive frames to make comparison slightly less sensitive */
			for( int bi = 0; bi < ybands; bi++ ) {
				for( int xj = BANDS[bi]; xj < BANDS[bi + 1]; xj++ ) {
					for( int ci = 0; ci < nchannels; ci++ ) {
						float xtmp = X[xj * nchannels + ci];
						float ytmp = Y[xj * nchannels + ci];
						for( int xi = 1; xi < nframes; xi++ ) {
							final float xtmp2 = X[(xi * NFREQS + xj) * nchannels + ci];
							final float ytmp2 = Y[(xi * yfreqs + xj) * nchannels + ci];
							X[(xi * NFREQS + xj) * nchannels + ci] += xtmp;
							Y[(xi * yfreqs + xj) * nchannels + ci] += ytmp;
							xtmp = xtmp2;
							ytmp = ytmp2;
						}
					}
				}
			}

			/*If working at a lower sampling rate, don't take into account the last
			  300 Hz to allow for different transition bands.
			  For 12 kHz, we don't skip anything, because the last band already skips
			  400 Hz.*/
			int      max_compare;
			if( rate == 48000 ) {
				max_compare = BANDS[NBANDS];
			} else if( rate == 12000 ) {
				max_compare = BANDS[ybands];
			} else {
				max_compare = BANDS[ybands] - 3;
			}
			double err = 0;
			for( int xi = 0; xi < nframes; xi++ ) {
				double Ef = 0;
				for( int bi = 0; bi < ybands; bi++ ) {
					double Eb = 0;
					for( int xj = BANDS[bi]; xj < BANDS[bi + 1] && xj < max_compare; xj++ ) {
						for( int ci = 0; ci < nchannels; ci++ ) {
							final float re = Y[(xi * yfreqs + xj) * nchannels + ci] / X[(xi * NFREQS + xj) * nchannels + ci];
							float im = re - (float)Math.log( re ) - 1f;
							/*Make comparison less sensitive around the SILK/CELT cross-over to
								allow for mode freedom in the filters.*/
							if( xj >= 79 && xj <= 81 ) {
								im *= 0.1F;
							}
							if( xj == 80 ) {
								im *= 0.1F;
							}
							Eb += im;
						}
					}
					Eb /= (BANDS[bi + 1] - BANDS[bi]) * nchannels;
					Ef += Eb * Eb;
				}
				/*Using a fixed normalization value means we're willing to accept slightly
				  lower quality for lower sampling rates.*/
				Ef /= NBANDS;
				Ef *= Ef;
				err += Ef * Ef;
			}
			// xb = null;// free( xb );
			// X = null;// free( X );
			// Y = null;// free( Y );
			err = Math.pow( err / nframes, 1.0 / 16 );
			final float Q = (float)(100 * (1 - 0.5 * Math.log( 1 + err ) / Math.log( 1.13 )));
			if( Q < 0 ) {
				System.err.printf("Test vector FAILS\n");
				System.err.printf("Internal weighted error is %f\n", err );
				return EXIT_FAILURE;
			}
			else {
				System.err.printf("Test vector PASSES\n");
				System.err.printf(
						"Opus quality metric: %.1f %% (internal weighted error is %f)\n", Q, err );
				return EXIT_SUCCESS;
			}
		} catch (final Exception e) {
			System.err.println( e.getMessage() );
			e.printStackTrace();
			return EXIT_FAILURE;
		} finally {
			if( fin1 != null ) {
				try{ fin1.close(); } catch(final IOException ie) {}
			}
			if( fin2 != null ) {
				try{ fin2.close(); } catch(final IOException ie) {}
			}
		}
	}

	public static final void main(final String[] _argv) {
		System.exit( maininternal( _argv ) );
	}
}