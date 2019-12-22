package tools;

import java.io.IOException;

interface Jaudio_read_func {
	int audio_read_func(/* Object src,*/ float[] buffer, int offset, int samples) throws IOException;
	void close();
}
