package opusfile;

// http.c
// TODO implements http

/** HTTP/Shoutcast/Icecast server information associated with a URL. */
public final class JOpusServerInfo {
	/**\defgroup url_options URL Reading Options*/
	/*@{*/
	/**\name URL reading options
	   Options for op_url_stream_create() and associated functions.
	   These allow you to provide proxy configuration parameters, skip SSL
	    certificate checks, etc.
	   Options are processed in order, and if the same option is passed multiple
	    times, only the value specified by the last occurrence has an effect
	    (unless otherwise specified).
	   They may be expanded in the future.*/
	/*@{*/

	/**@cond PRIVATE*/

	/*These are the raw numbers used to define the request codes.
	  They should not be used directly.*/// java now using directly TODO find a way to check argument type
	private static final int OP_SSL_SKIP_CERTIFICATE_CHECK_REQUEST = 6464;
	private static final int OP_HTTP_PROXY_HOST_REQUEST            = 6528;
	private static final int OP_HTTP_PROXY_PORT_REQUEST            = 6592;
	private static final int OP_HTTP_PROXY_USER_REQUEST            = 6656;
	private static final int OP_HTTP_PROXY_PASS_REQUEST            = 6720;
	private static final int OP_GET_SERVER_INFO_REQUEST            = 6784;
	//
	/** Skip the certificate check when connecting via TLS/SSL (https).
	   @param _b <code>opus_int32</code>: Whether or not to skip the certificate
	              check.
	             The check will be skipped if \a _b is non-zero, and will not be
	              skipped if \a _b is zero.
	   \hideinitializer */
	public static final int OP_SSL_SKIP_CERTIFICATE_CHECK = OP_SSL_SKIP_CERTIFICATE_CHECK_REQUEST;
	/** Proxy connections through the given host.
	   If no port is specified via #OP_HTTP_PROXY_PORT, the port number defaults
	    to 8080 (http-alt).
	   All proxy parameters are ignored for non-http and non-https URLs.
	   @param _host <code>const char *</code>: The proxy server hostname.
	                This may be <code>NULL</code> to disable the use of a proxy
	                 server.
	   \hideinitializer */
	public static final int OP_HTTP_PROXY_HOST            = OP_HTTP_PROXY_HOST_REQUEST;
	/**Use the given port when proxying connections.
	   This option only has an effect if #OP_HTTP_PROXY_HOST is specified with a
	    non-<code>NULL</code> \a _host.
	   If this option is not provided, the proxy port number defaults to 8080
	    (http-alt).
	   All proxy parameters are ignored for non-http and non-https URLs.
	   @param _port <code>opus_int32</code>: The proxy server port.
	                This must be in the range 0...65535 (inclusive), or the
	                 URL function this is passed to will fail.
	   \hideinitializer*/
	public static final int OP_HTTP_PROXY_PORT            = OP_HTTP_PROXY_PORT_REQUEST;
	/** Use the given user name for authentication when proxying connections.
	   All proxy parameters are ignored for non-http and non-https URLs.
	   @param _user const char *: The proxy server user name.
	                              This may be <code>NULL</code> to disable proxy
	                               authentication.
	                              A non-<code>NULL</code> value only has an effect
	                               if #OP_HTTP_PROXY_HOST and #OP_HTTP_PROXY_PASS
	                               are also specified with non-<code>NULL</code>
	                               arguments.
	   \hideinitializer */
	public static final int OP_HTTP_PROXY_USER            = OP_HTTP_PROXY_USER_REQUEST;
	/** Use the given password for authentication when proxying connections.
	   All proxy parameters are ignored for non-http and non-https URLs.
	   @param _pass const char *: The proxy server password.
	                              This may be <code>NULL</code> to disable proxy
	                               authentication.
	                              A non-<code>NULL</code> value only has an effect
	                               if #OP_HTTP_PROXY_HOST and #OP_HTTP_PROXY_USER
	                               are also specified with non-<code>NULL</code>
	                               arguments.
	   \hideinitializer */
	public static final int OP_HTTP_PROXY_PASS            = OP_HTTP_PROXY_PASS_REQUEST;
	/** Parse information about the streaming server (if any) and return it.
	   Very little validation is done.
	   In particular, OpusServerInfo::url may not be a valid URL,
	    OpusServerInfo::bitrate_kbps may not really be in kbps, and
	    OpusServerInfo::content_type may not be a valid MIME type.
	   The character set of the string fields is not specified anywhere, and should
	    not be assumed to be valid UTF-8.
	   @param _info OpusServerInfo *: Returns information about the server.
	                                  If there is any error opening the stream, the
	                                   contents of this structure remain
	                                   unmodified.
	                                  On success, fills in the structure with the
	                                   server information that was available, if
	                                   any.
	                                  After a successful return, the contents of
	                                   this structure should be freed by calling
	                                   opus_server_info_clear().
	   \hideinitializer */
	public static final int OP_GET_SERVER_INFO            = OP_GET_SERVER_INFO_REQUEST;
	//
	/** The name of the server (icy-name/ice-name).
	This is <code>NULL</code> if there was no <code>icy-name</code> or
	<code>ice-name</code> header. */
	public String        name;
	/** A short description of the server (icy-description/ice-description).
	This is <code>NULL</code> if there was no <code>icy-description</code> or
	<code>ice-description</code> header. */
	public String        description;
	/** The genre the server falls under (icy-genre/ice-genre).
	This is <code>NULL</code> if there was no <code>icy-genre</code> or
	<code>ice-genre</code> header. */
	public String        genre;
	/** The homepage for the server (icy-url/ice-url).
	This is <code>NULL</code> if there was no <code>icy-url</code> or
	<code>ice-url</code> header. */
	public String        url;
	/** The software used by the origin server (Server).
	This is <code>NULL</code> if there was no <code>Server</code> header. */
	public String        server;
	/** The media type of the entity sent to the recepient (Content-Type).
	This is <code>NULL</code> if there was no <code>Content-Type</code>
	header. */
	public String        content_type;
	/** The nominal stream bitrate in kbps (icy-br/ice-bitrate).
	This is <code>-1</code> if there was no <code>icy-br</code> or
	<code>ice-bitrate</code> header. */
	public int   bitrate_kbps;
	/** Flag indicating whether the server is public (<code>1</code>) or not
	(<code>0</code>) (icy-pub/ice-public).
	This is <code>-1</code> if there was no <code>icy-pub</code> or
	<code>ice-public</code> header. */
	public int          is_public;
	/** Flag indicating whether the server is using HTTPS instead of HTTP.
	This is <code>0</code> unless HTTPS is being used.
	This may not match the protocol used in the original URL if there were
	redirections. */
	public boolean          is_ssl;
	//
	// http.c
	/** Initializes an #OpusServerInfo structure.
	   All fields are set as if the corresponding header was not available.
	   @param _info The #OpusServerInfo structure to initialize.
	   @note If you use this function, you must link against <tt>libopusurl</tt>. */
	/* public final void opus_server_info_init() {
		this.name = null;
		this.description = null;
		this.genre = null;
		this.url = null;
		this.server = null;
		this.content_type = null;
		this.bitrate_kbps = -1;
		this.is_public = -1;
		this.is_ssl = false;
	} */

	/** Clears the #OpusServerInfo structure.
	   This should be called on an #OpusServerInfo structure after it is no longer
	    needed.
	   It will free all memory used by the structure members.
	   @param _info The #OpusServerInfo structure to clear.
	   @note If you use this function, you must link against <tt>libopusurl</tt>. */
	public final void opus_server_info_clear() {
		this.content_type = null;
		this.server = null;
		this.url = null;
		this.genre = null;
		this.description = null;
		this.name = null;
	}

	/** Creates a stream that reads from the given URL.
	   @note If you use this function, you must link against <tt>libopusurl</tt>.
	   @param _cb [out]  The callbacks to use for this stream.
	                    If there is an error creating the stream, nothing will be
	                     filled in here.
	   @param      _url The URL to read from.
	                    Currently only the <file:>, <http:>, and <https:> schemes
	                     are supported.
	                    Both <http:> and <https:> may be disabled at compile time,
	                     in which case opening such URLs will always fail.
	                    Currently this only supports URIs.
	                    IRIs should be converted to UTF-8 and URL-escaped, with
	                     internationalized domain names encoded in punycode, before
	                     passing them to this function.
	   @param      ...  The \ref url_options "optional flags" to use.
	                    This is a variable-length list of options terminated with
	                     <code>NULL</code>.
	   @return A stream handle to use with the callbacks, or <code>NULL</code> on
	            error. */
	public static final JOpusFileCallbacks op_url_stream_create(// OpusFileCallbacks _cb,// java change: will return
			final String _url, final int request, final int val, final Object arg) {
		/* java: not implemented
		va_list  ap;
		void    *ret;
		va_start( ap, _url );
		ret = op_url_stream_vcreate( _cb, _url, ap );
		va_end(ap);
		return ret; */
		return null;
	}

	/** Open a stream from a URL.
	   @note If you use this function, you must link against <tt>libopusurl</tt>.
	   @param      _url   The URL to open.
	                      Currently only the <file:>, <http:>, and <https:> schemes
	                       are supported.
	                      Both <http:> and <https:> may be disabled at compile
	                       time, in which case opening such URLs will always fail.
	                      Currently this only supports URIs.
	                      IRIs should be converted to UTF-8 and URL-escaped, with
	                       internationalized domain names encoded in punycode,
	                       before passing them to this function.
	   @param _error [out] Returns 0 on success, or a failure code on error.
	                      You may pass in <code>NULL</code> if you don't want the
	                       failure code.
	                      See op_open_callbacks() for a full list of failure codes.
	   @param      ...    The \ref url_options "optional flags" to use.
	                      This is a variable-length list of options terminated with
	                       <code>NULL</code>.
	   @return A freshly opened \c OggOpusFile, or <code>NULL</code> on error. */
	public static final JOggOpusFile op_open_url(final String _url, final int[] _error, final int request, final JOpusServerInfo info, final Object obj ) {
		/* java: not implemented
		OggOpusFile *ret;
		va_list      ap;
		va_start( ap,_error );
		ret = op_vopen_url( _url, _error, ap );
		va_end( ap );
		return ret;*/
		// _error[0] = OP_EFAULT;// java
		return null;
	}
}
