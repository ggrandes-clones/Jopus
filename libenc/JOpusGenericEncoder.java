package libenc;

import opus.JOpusMSEncoder;
import opus.JOpusProjectionEncoder;
import opus.Jms_encoder_data_aux;
import opus.Jopus_defines;

final class JOpusGenericEncoder {
	private JOpusMSEncoder ms;
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
	private JOpusProjectionEncoder pr;
// #endif
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H

	static final boolean opeint_use_projection(final int channel_mapping) {
		return (channel_mapping == 3);
	}

	final int opeint_encoder_surround_init(
			final int Fs, final int channels, final int channel_mapping,
			final Jms_encoder_data_aux data,// java int[] nb_streams, int[] nb_coupled,
			final char[] stream_map, final int application ) {
		final int ret[] = new int[1];
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
		if( opeint_use_projection( channel_mapping ) ) {
			this.pr = JOpusProjectionEncoder.opus_projection_ambisonics_encoder_create( Fs, channels,
					channel_mapping,
					data,// nb_streams, nb_coupled,
					application, ret );
			for( int ci = 0; ci < channels; ci++ ) {
				stream_map[ci] = (char)ci;
			}
			this.ms = null;
			return ret[ 0 ];
		}
//		else
// #endif
		{
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
			this.pr = null;
// #endif
			this.ms = JOpusMSEncoder.opus_multistream_surround_encoder_create( Fs, channels,
						channel_mapping,
						data,// nb_streams, nb_coupled,
						stream_map, application, ret );
		}
		return ret[0];
	}

	final void opeint_encoder_cleanup() {
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
    	if( this.pr != null ) {
			this.pr = null;// opus_projection_encoder_destroy( st.pr );
		}
// #endif
		// if( st.ms != null ) JOpusMSEncoder.opus_multistream_encoder_destroy( st.ms );
		this.ms = null;
	}

	final int opeint_encoder_init(
			final int Fs, final int channels, final int streams,
			final int coupled_streams, final char[] mapping, final int application ) {
		final int ret[] = new int[1];
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
		this.pr = null;
// #endif
		this.ms = JOpusMSEncoder.opus_multistream_encoder_create( Fs, channels, streams,
					coupled_streams, mapping, application, ret );
		return ret[0];
	}

	final int opeint_encode_float(
			final float[] pcm, final int poffset,// java
			final int frame_size,
			final byte[] data, final int doffset,// java
			final int max_data_bytes)
	{
// #ifdef OPUS_HAVE_OPUS_PROJECTION_H
		if( this.pr != null ) {
			return this.pr.opus_projection_encode_float( pcm, poffset, frame_size, data, doffset, max_data_bytes );
		}
		// else {
//#endif
			return  this.ms.opus_multistream_encode_float( pcm, poffset, frame_size, data, doffset, max_data_bytes );
		//}
		//return ret;
	}

	// java: overload
	int opeint_encoder_ctl(final int request, final boolean value) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, value );
		}
		return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl(final int request, final int value) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, value );
		}
		return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl(final int request, final Object[] value) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, value );
		}
		return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl(final int request, final int streamId, final Object[] arg) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, streamId, arg );
		}
		return ms.opus_multistream_encoder_ctl( request, streamId, arg );
	}
	int opeint_encoder_ctl(final int request, final char[] matrix, final int offset, final int size) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, matrix, offset, size );
		}
		return Jopus_defines.OPUS_BAD_ARG;// java added
		// return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl(final int request, final byte[] matrix, final int offset, final int size) {
		if( pr != null ) {
			return pr.opus_projection_encoder_ctl( request, matrix, offset, size );
		}
		return Jopus_defines.OPUS_BAD_ARG;// java added
		// return ms.opus_multistream_encoder_ctl( request, value );
	}
	//
	int opeint_encoder_ctl2(final int request, final int value) {
		if( pr != null ) {
			pr.opus_projection_encoder_ctl( request, value );
		}
		return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl2(final int request, final boolean value) {
		if( pr != null ) {
			pr.opus_projection_encoder_ctl( request, value );
		}
		return ms.opus_multistream_encoder_ctl( request, value );
	}
	int opeint_encoder_ctl2(final int request, final Object[] arg) {
		if( pr != null ) {
			pr.opus_projection_encoder_ctl( request, arg );
		}
		return ms.opus_multistream_encoder_ctl( request, arg );
	}
/* #else
	int opeint_encoder_ctl(int request) {
		return ms.opus_multistream_encoder_ctl( request );
	}
	int opeint_encoder_ctl2(int request, int value) {
		return ms.opus_multistream_encoder_ctl( request, value );
	}
#endif */
}
