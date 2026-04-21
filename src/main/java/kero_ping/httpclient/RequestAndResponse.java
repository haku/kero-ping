package kero_ping.httpclient;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;

public class RequestAndResponse {
	public final SimpleHttpRequest request;
	public final HttpClientContext context;
	public final SimpleHttpResponse response;

	public RequestAndResponse(final SimpleHttpRequest request, final HttpClientContext context,
			final SimpleHttpResponse response) {
		this.request = request;
		this.context = context;
		this.response = response;
	}
}
