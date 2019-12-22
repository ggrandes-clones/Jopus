package opus;

/**
 * java: interface for copy_channel_in_func function
 */
abstract class Iopus_copy_channel_in {
	abstract void copy_channel_in(float[] dst, int doffset, int dst_stride,
			Object src, int scroffset,// java
			int src_stride, int src_channel, int frame_size, Object user_data);
}
