package opus;

final class JGRULayer extends Jmlp {
	private final byte[] bias;
	private final byte[] input_weights;
	private final byte[] recurrent_weights;
	private final int nb_inputs;
	private final int nb_neurons;

	JGRULayer(final byte[] b, final byte[] iw, final byte[] rw, final int i, final int n) {
		bias = b;
		input_weights = iw;
		recurrent_weights = rw;
		nb_inputs = i;
		nb_neurons = n;
	}

	final void compute_gru(final float[] state, final float[] input)
	{
		final float z[] = new float[MAX_NEURONS];
		final float r[] = new float[MAX_NEURONS];
		final float h[] = new float[MAX_NEURONS];
		final int M = this.nb_inputs;
		final int N = this.nb_neurons;
		final int stride = 3 * N;
		// java
		final byte[] b = this.bias;
		final byte[] iw = this.input_weights;
		final byte[] rw = this.recurrent_weights;
		for( int i = 0; i < N; i++ )
		{
			/* Compute update gate. */
			float sum = (float)b[i];
			for( int j = 0, js = i; j < M; j++, js += stride ) {
				sum += (float)iw[ js ] * input[ j ];
			}
			for( int j = 0, js = i; j < N; j++, js += stride ) {
				sum += (float)rw[ js ] * state[ j ];
			}
			z[i] = .5f + .5f * tansig_approx( .5f * WEIGHTS_SCALE * sum );// sigmoid_approx( WEIGHTS_SCALE * sum );
		}
		for( int i = 0, ni = N; i < N; i++, ni++ )
		{
			/* Compute reset gate. */
			float sum = (float)b[ ni ];
			for( int j = 0, js = ni; j < M; j++, js += stride ) {
				sum += (float)iw[ js ] * input[ j ];
			}
			for( int j = 0, js = ni; j < N; j++, js += stride ) {
				sum += (float)rw[ js ] * state[ j ];
			}
			r[i] = .5f + .5f * tansig_approx( .5f * WEIGHTS_SCALE * sum );// sigmoid_approx( WEIGHTS_SCALE * sum );
		}
		for( int i = 0, in2 = (N << 1); i < N; i++, in2++ )
		{
			/* Compute output. */
			float sum = (float)b[ in2 ];
			for( int j = 0, js = in2; j < M; j++, js += stride ) {
				sum += (float)iw[ js ] * input[ j ];
			}
			for( int j = 0, js = in2; j < N; j++, js += stride ) {
				sum += (float)rw[ js ] * state[ j ] * r[ j ];
			}
			h[i] = z[i] * state[i] + (1 - z[i]) * tansig_approx( WEIGHTS_SCALE * sum );
		}
		for( int i = 0; i < N; i++ ) {
			state[i] = h[i];
		}
	}
}
