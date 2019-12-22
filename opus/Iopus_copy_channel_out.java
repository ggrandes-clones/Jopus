package opus;

/**
 * java: interface for copy_channel_out_func function
 */
abstract class Iopus_copy_channel_out {
	abstract void copy_channel_out(Object dst, int doffset, int dst_stride, int dst_channel, float[] src, int srcoffset, int src_stride, int frame_size, Object user_data);
}
