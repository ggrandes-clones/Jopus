package opusfile;

import libogg.Jogg_packet;
import opus.JOpusMSDecoder;

/**Called to decode an Opus packet.
This should invoke the functional equivalent of opus_multistream_decode() or
 opus_multistream_decode_float(), except that it returns 0 on success
 instead of the number of decoded samples (which is known a priori).*/
public interface Jop_decode_cb_func {
	/** Called to decode an Opus packet.
	   This should invoke the functional equivalent of opus_multistream_decode() or
	    opus_multistream_decode_float(), except that it returns 0 on success
	    instead of the number of decoded samples (which is known a priori).
	   @param _ctx       The application-provided callback context.
	   @param _decoder   The decoder to use to decode the packet.
	   @param _pcm [out]  The buffer to decode into.
	                     This will always have enough room for \a _nchannels of
	                      \a _nsamples samples, which should be placed into this
	                      buffer interleaved.
	   @param poffset    java: offset in _pcm.
	   @param _op        The packet to decode.
	                     This will always have its granule position set to a valid
	                      value.
	   @param _nsamples  The number of samples expected from the packet.
	   @param _nchannels The number of channels expected from the packet.
	   @param _format    The desired sample output format.
	                     This is either #OP_DEC_FORMAT_SHORT or
	                      #OP_DEC_FORMAT_FLOAT.
	   @param _li        The index of the link from which this packet was decoded.
	   @return A non-negative value on success, or a negative value on error.
	       Any error codes should be the same as those returned by
	        opus_multistream_decode() or opus_multistream_decode_float().
	       Success codes are as follows:
	   @retval 0                   Decoding was successful.
	                           The application has filled the buffer with
	                            exactly <code>\a _nsamples*\a
	                            _nchannels</code> samples in the requested
	                            format.
	   @retval #OP_DEC_USE_DEFAULT No decoding was done.
	                           <tt>libopusfile</tt> should do the decoding
	                            by itself instead.*/
	int op_decode_cb_func(Object _ctx, JOpusMSDecoder _decoder, Object _pcm, int poffset, Jogg_packet _op, int _nsamples, int _nchannels, int _format, int _li);
}
