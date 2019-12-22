package opusfile;

/** Information cached for a single link in a chained Ogg Opus file.
We choose the first Opus stream encountered in each link to play back (and
require at least one). */
final class JOggOpusLink {
	/** The byte offset of the first header page in this link. */
	long   offset;
	/** The byte offset of the first data page from the chosen Opus stream in this
	link (after the headers). */
	long   data_offset;
	/** The byte offset of the last page from the chosen Opus stream in this link.
	This is used when seeking to ensure we find a page before the last one, so
	that end-trimming calculations work properly.
	This is only valid for seekable sources. */
	long   end_offset;
	/** The total duration of all prior links.
	This is always zero for non-seekable sources. */
	long pcm_file_offset;
	/** The granule position of the last sample.
	This is only valid for seekable sources. */
	long  pcm_end;
	/** The granule position before the first sample. */
	long  pcm_start;
	/** The serial number. */
	int serialno;
	/** The contents of the info header. */
	final JOpusHead     head = new JOpusHead();
	/** The contents of the comment header. */
	final JOpusTags     tags = new JOpusTags();
}
