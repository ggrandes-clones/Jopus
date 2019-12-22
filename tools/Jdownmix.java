package tools;

import java.io.IOException;

final class Jdownmix implements Jaudio_read_func {
	private static final float stupid_matrix[][][] = {// [7][8][2] = {
			/*2*/  {{1, 0}, {0, 1}},
			/*3*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}},
			/*4*/  {{1, 0}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}},
			/*5*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}},
			/*6*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.7071f, 0.7071f}},
			/*7*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.6123f, 0.6123f}, {0.7071f, 0.7071f}},
			/*8*/  {{1, 0}, {0.7071f, 0.7071f}, {0, 1}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.866f, 0.5f}, {0.5f, 0.866f}, {0.7071f, 0.7071f}},
		};
	//
	private Jaudio_read_func real_reader;
	// private Object real_readdata;
	private float[] bufs;
	private float[] matrix;
	private int in_channels;
	private int out_channels;
	//
	private Jdownmix() {
	}
	//
	@Override
	public int audio_read_func(final float[] buffer, final int offset, final int samples) throws IOException {
		// private static long read_downmix( void *data, float *buffer, int samples )
		final float[] b = this.bufs;// java
		final int in_samples = this.real_reader.audio_read_func( /* this.real_readdata,*/ b, 0, samples );

		final int in_ch = this.in_channels;
		final int out_ch = this.out_channels;

		final float[] m = this.matrix;

		for( int i = 0, ie = in_samples * in_ch, o = offset; i < ie; i += in_ch, o += out_ch ) {
			for( int j = o, je = o + out_ch, ij = 0; j < je; j++, ij += in_ch ) {
				int sample = 0;
				for( int bi = i, be = i + in_ch, mi = ij; bi < be; bi++, mi++ ) {
					sample += b[bi] * m[mi];
				}
				buffer[ j ] = sample;
			}
		}
		return in_samples;
	}
	@Override
	public void close() {
	}

	static final int setup_downmix( final Joe_enc_opt opt, final int out_channels ) {

		if( opt.channels <= out_channels || out_channels > 2 || opt.channels <= 0 || out_channels <= 0 ) {
			System.err.printf("Downmix must actually downmix and only knows mono/stereo out.\n");
			return 0;
		}

		if( out_channels == 2 && opt.channels > 8 ) {
			System.err.printf("Downmix only knows how to mix >8ch to mono.\n");
			return 0;
		}

		final Jdownmix d = new Jdownmix();
		d.bufs = new float[ opt.channels * 4096 ];
		d.matrix = new float[ opt.channels * out_channels ];
		d.real_reader = opt.read_samples;
		// d.real_readdata = opt.readdata;
		d.in_channels = opt.channels;
		d.out_channels = out_channels;

		if( out_channels == 1 && d.in_channels > 8 ) {
			for( int i = 0; i < d.in_channels; i++ ) {
				d.matrix[i] = 1.0f / d.in_channels;
			}
		} else if( out_channels == 2 ) {
			for( int j = 0; j < d.out_channels; j++ ) {
				for( int i = 0, k = d.in_channels * j; i < d.in_channels; i++, k++ ) {
					d.matrix[k] = stupid_matrix[opt.channels - 2][i][j];
				}
			}
		} else {
			for( int i = 0, ch2 = opt.channels - 2; i < d.in_channels; i++ ) {
				d.matrix[i] = (stupid_matrix[ch2][i][0]) + (stupid_matrix[ch2][i][1]);
			}
		}
		float sum = 0;
		final int count = d.in_channels * d.out_channels;// java
		for( int i = 0; i < count; i++ ) {
			sum += d.matrix[i];
		}
		sum = (float)out_channels / sum;
		for( int i = 0; i < count; i++ ) {
			d.matrix[i] *= sum;
		}
		opt.read_samples = d;// read_downmix;
		// opt.readdata = d;

		opt.channels = out_channels;
		return out_channels;
	}

	static final void clear_downmix( final Joe_enc_opt opt ) {
		final Jdownmix d = (Jdownmix)opt.read_samples;// opt.readdata;

		opt.read_samples = d.real_reader;
		// opt.readdata = d.real_readdata;
		opt.channels = d.in_channels; // other things in cleanup rely on this

		d.bufs = null;
		d.matrix = null;
		// free( d );
	}
}
