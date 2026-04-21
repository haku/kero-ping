package kero_ping.httpclient;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;

public class CallbackFuture implements FutureCallback<SimpleHttpResponse> {

	private final SimpleHttpRequest request;
	private final HttpClientContext context;
	private final CompletableFuture<RequestAndResponse> future;

	public CallbackFuture(final SimpleHttpRequest request, final HttpClientContext context,
			final CompletableFuture<RequestAndResponse> future) {
		this.request = request;
		this.context = context;
		this.future = future;
	}

	@Override
	public void completed(final SimpleHttpResponse result) {
		if (result.getCode() >= 200 && result.getCode() < 300) {
			this.future.complete(new RequestAndResponse(this.request, this.context, result));
		}
		else {
			failed(new HttpResponseException(result.getCode(), "Not HTTP success."));
		}
	}

	@Override
	public void failed(final Exception ex) {
		this.future.completeExceptionally(new ExceptionWithContext(this.context, ex));
	}

	@Override
	public void cancelled() {
		this.future.completeExceptionally(new CancellationException());
	}

}
