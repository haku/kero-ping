package kero_ping.util;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class AsyncHelpers {

	public static <T> CompletableFuture<T> anyOf(final List<? extends CompletionStage<? extends T>> l) {
		final CompletableFuture<T> f = new CompletableFuture<>();
		final Consumer<T> complete = f::complete;
		CompletableFuture.allOf(l.stream().map(s -> s.thenAccept(complete)).toArray(CompletableFuture<?>[]::new))
				.exceptionally(ex -> {
					f.completeExceptionally(ex);
					return null;
				});
		return f;
	}

	public static Throwable unwrapExecutionException(final Exception e) {
		Throwable t = e;
		while (t instanceof ExecutionException) {
			if (t.getCause() == null) return t;
			t = t.getCause();
		}
		return t;
	}

}
