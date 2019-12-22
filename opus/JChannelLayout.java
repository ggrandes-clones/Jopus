package opus;

/* Copyright (c) 2011 Xiph.Org Foundation
   Written by Jean-Marc Valin */

// opus_multistream.c

final class JChannelLayout {
	int nb_channels;
	int nb_streams;
	int nb_coupled_streams;
	final char mapping[] = new char[256];

	final int validate_layout()
	{
		final int max_channel = this.nb_streams + this.nb_coupled_streams;
		if( max_channel > 255 ) {
			return 0;
		}
		final char[] maps = this.mapping;// java
		for( int i = 0, n = this.nb_channels; i < n; i++ )
		{
			if( (int)maps[i] >= max_channel && (int)maps[i] != 255 ) {
				return 0;
			}
		}
		return 1;
	}

	final int get_left_channel(int stream_id, final int prev)
	{
		stream_id <<= 1;// java
		final char[] maps = this.mapping;// java
		for( int i = (prev < 0) ? 0 : prev + 1, n = this.nb_channels; i < n; i++ )
		{
			if( (int)maps[i] == stream_id /* stream_id * 2 */ ) {
				return i;
			}
		}
		return -1;
	}

	final int get_right_channel(int stream_id, final int prev)
	{
		stream_id <<= 1;// java
		stream_id++;// java
		final char[] maps = this.mapping;// java
		for( int i = (prev < 0) ? 0 : prev + 1, n = this.nb_channels; i < n; i++ )
		{
			if( (int)maps[i] == stream_id /* stream_id * 2 + 1 */ ) {
				return i;
			}
		}
		return -1;
	}

	final int get_mono_channel(int stream_id, final int prev)
	{
		stream_id += this.nb_coupled_streams;// java
		final char[] maps = this.mapping;// java
		for( int i = (prev < 0) ? 0 : prev + 1, n = this.nb_channels; i < n; i++ )
		{
			if( (int)maps[i] == stream_id /* stream_id + layout.nb_coupled_streams */ ) {
				return i;
			}
		}
		return -1;
	}
	// end opus_multistream.c
	// start opus_multistream_encoder.c
	final boolean validate_encoder_layout()
	{
		final int ncs = this.nb_coupled_streams;// java
		for( int s = 0, n = this.nb_streams; s < n; s++ )
		{
			if( s < ncs )
			{
				if( get_left_channel( s, -1 ) == -1 ) {
					return false;
				}
				if( get_right_channel( s, -1 ) == -1 ) {
					return false;
				}
			} else {
				if( get_mono_channel( s, -1 ) == -1 ) {
					return false;
				}
			}
		}
		return true;
	}
	// end opus_multistream_encoder.c

}