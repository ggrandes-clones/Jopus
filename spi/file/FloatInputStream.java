package spi.file;

import java.io.IOException;

/**
 * Interface to read floating point data
 */
interface FloatInputStream {
	int audio_read(float[] buffer, int offset, int samples) throws IOException;
	/**
	 * this is not actually close input stream, just releasing resources.
	 * input stream must be closed manually
	 */
	void close();
}