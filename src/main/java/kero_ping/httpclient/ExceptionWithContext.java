package kero_ping.httpclient;

import org.apache.hc.client5.http.protocol.HttpClientContext;

public class ExceptionWithContext extends Exception {

	private static final long serialVersionUID = 8718442515026742318L;

	private final HttpClientContext context;
	private final Exception exception;

	public ExceptionWithContext(final HttpClientContext context, final Exception exception) {
		super(exception.getMessage(), exception);
		this.context = context;
		this.exception = exception;
	}

	public HttpClientContext getContext() {
		return this.context;
	}

	public Exception getException() {
		return this.exception;
	}

}
