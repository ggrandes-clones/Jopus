package opus;

final class JDenseLayer extends Jmlp {
	private final byte[] bias;
	private final byte[] input_weights;
	private final int nb_inputs;
	private final int nb_neurons;
	private final boolean sigmoid;

	JDenseLayer(final byte[] b, final byte[] w, final int i, final int n, final boolean s) {
		bias = b;
		input_weights = w;
		nb_inputs = i;
		nb_neurons = n;
		sigmoid = s;
	}

	final void compute_dense(final float[] output, final float[] input)
	{
		final int M = this.nb_inputs;
		final int N = this.nb_neurons;
		final int stride = N;
		// java
		final byte[] b = this.bias;
		final byte[] iw = this.input_weights;
		for( int i = 0; i < N; i++ )
		{
			/* Compute update gate. */
			float sum = (float)b[i];
			for( int j = 0, js = i; j < M; j++, js += stride ) {
				sum += (float)iw[ js ] * input[ j ];
			}
			output[i] = WEIGHTS_SCALE * sum;
		}
		if( this.sigmoid ) {
			for( int i = 0; i < N; i++ ) {
				output[i] = .5f + .5f * tansig_approx( .5f * output[i] );// sigmoid_approx( output[i] );
			}
		} else {
			for( int i = 0; i < N; i++ ) {
				output[i] = tansig_approx( output[i] );
			}
		}
	}
}
