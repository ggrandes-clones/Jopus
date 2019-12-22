package tools;

import libenc.JOggOpusComments;

final class Joe_enc_opt {
	Jaudio_read_func read_samples;
	// Object readdata;
	long total_samples_per_channel;
	boolean rawmode;
	int channels;
	int rate;
	int gain;
	int samplesize;
	int endianness;
	int ignorelength;
	JOggOpusComments comments;
	boolean copy_comments;
	boolean copy_pictures;
}
