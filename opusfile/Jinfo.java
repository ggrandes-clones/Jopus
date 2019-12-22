package opusfile;

/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE libopusfile SOFTWARE CODEC SOURCE CODE. *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE libopusfile SOURCE CODE IS (C) COPYRIGHT 2012                *
 * by the Xiph.Org Foundation and contributors http://www.xiph.org/ *
 *                                                                  *
 ********************************************************************/
// info.c

public abstract class Jinfo {
	static final int memcmp(final byte[] buf1, int offset1, final byte[] buf2, final int count) {
		for ( int offset2 = 0; offset2 < count; ) {
			int u1 = buf1[offset1++];
			final int u2 = buf2[offset2++];
			u1 -= u2;
			if ( u1 != 0 ) {
				return u1;
			}
		}
		return 0;
	}
	static final int strlen(final byte[] str, int offset) {
		do {
			if( str[offset] == '\0' ) {
				return offset;
			}
			offset++;
		} while( true );
	}
	static final int op_parse_uint32le( final byte[] _data, int doffset ) {
		int v = (int)_data[doffset++] & 0xff;
		v |= ((int)_data[doffset++] & 0xff) << 8;
		v |= ((int)_data[doffset++] & 0xff) << 16;
		v |= ((int)_data[doffset]) << 24;
		return v;
	}
	static final int op_parse_uint32be( final byte[] _data, int doffset ) {
		int v = ((int)_data[doffset++]) << 24;
		v |= ((int)_data[doffset++] & 0xff) << 16;
		v |= ((int)_data[doffset++] & 0xff) << 8;
		v |= (int)_data[doffset] & 0xff;
		return v;
	}
}