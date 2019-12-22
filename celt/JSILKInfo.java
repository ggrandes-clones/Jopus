package celt;

/**
 * typedef struct SILKInfo
 *
 */
public final class JSILKInfo {
	int signalType;
	int offset;
	//
	/**
	 * Default constructor
	 */
	public JSILKInfo() {
	}
	/**
	 *
	 * @param type signalType
	 * @param off offset
	 */
	public JSILKInfo(final int type, final int off) {
		signalType = type;
		offset = off;
	}
	/**
	 * java variant for c memset 0
	 */
	public final void clear() {
		signalType = 0;
		offset = 0;
	}
	/**
	 * java variant for c memcpy
	 * @param info
	 */
	public void copyFrom(final JSILKInfo info) {
		signalType = info.signalType;
		offset = info.offset;
	}
}
