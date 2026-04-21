package kero_ping;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.javax.client.internal.JavaxWebSocketClientContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kero_ping.util.Time;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class WsPing implements Runnable {

	private static final String[] METRIC_LABELS = { "client_interface", "client_name" };
	private static final Counter METRIC_CONNECTION_ATTEMPTS = Counter.build()
			.name("wsping_connection_attempts")
			.labelNames(METRIC_LABELS)
			.help("Total requests send via WebSocket.")
			.register();
	private static final Counter METRIC_REQUESTS = Counter.build()
			.name("wsping_requests_total")
			.labelNames(METRIC_LABELS)
			.help("Total requests send via WebSocket.")
			.register();
	private static final Counter METRIC_RESPONSES = Counter.build()
			.name("wsping_responses_total")
			.labelNames(METRIC_LABELS)
			.help("Total responses received via WebSocket.")
			.register();
	private static final Histogram METRIC_ROUNDTRIP_LATENCY = Histogram.build()
			.name("wsping_roundtrip_latency_seconds")
			.labelNames(METRIC_LABELS)
			.help("Time from reqest send to response recevied in seconds.")
			.register();
	private static final Gauge METRIC_CONNECTION_DURATION = Gauge.build()
			.name("wsping_connection_duration_seconds")
			.labelNames(METRIC_LABELS)
			.help("How many seconds between connection and last response received.")
			.register();

	private static final int MAX_TIMEOUT_SECONDS = 10;
	private static final long PING_PERIOD_SECONDS = 1;

	private static final String PING_MSG_PREFIX = "ping_start_time=";
	private static final Logger LOG = LoggerFactory.getLogger(WsPing.class);
	private static final long LOG_SPAM_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(MAX_TIMEOUT_SECONDS + 30);

	private final URI uri;

	private final String clientInterfaceLabel;
	private final Counter.Child connectionAttemptsWithLabels;
	private final Counter.Child requestsWithLabels;
	private final Counter.Child responsesWithLabels;
	private final Histogram.Child roundtripLatencyWithLabels;
	private final Gauge.Child connectionDurationWithLabels;

	private final WebSocketContainer wsContainer;
	private final Time time = Time.DEFAULT;

	private final AtomicReference<EventSocket> activeSocket = new AtomicReference<>();
	private volatile long logSpamStartTime;
	private final AtomicBoolean logSpamActive = new AtomicBoolean(true);

	public WsPing(final URI uri, final AddressAndName clientAddress) {
		this.uri = uri;
		restartLogSpam();

		this.clientInterfaceLabel = clientAddress.address.getHostName();
		final String clientNameLabel = clientAddress.name;
		this.connectionAttemptsWithLabels = METRIC_CONNECTION_ATTEMPTS.labels(this.clientInterfaceLabel, clientNameLabel);
		this.requestsWithLabels = METRIC_REQUESTS.labels(this.clientInterfaceLabel, clientNameLabel);
		this.responsesWithLabels = METRIC_RESPONSES.labels(this.clientInterfaceLabel, clientNameLabel);
		this.roundtripLatencyWithLabels = METRIC_ROUNDTRIP_LATENCY.labels(this.clientInterfaceLabel, clientNameLabel);
		this.connectionDurationWithLabels = METRIC_CONNECTION_DURATION.labels(this.clientInterfaceLabel, clientNameLabel);

		this.wsContainer = makeClientContainer(clientAddress.address);
		this.wsContainer.setAsyncSendTimeout(TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS));
		this.wsContainer.setDefaultMaxSessionIdleTimeout(TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS));
	}

	private WebSocketContainer makeClientContainer(final InetAddress clientAddress) {
		final JavaxWebSocketClientContainer clientContainer;
		try {
			final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
			sslContextFactory.setTrustAll(false);
			sslContextFactory.setSslSessionTimeout(MAX_TIMEOUT_SECONDS);
			
			ClientConnector clientConnector = new ClientConnector();
			clientConnector.setSslContextFactory(sslContextFactory);
			
			final HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));

			httpClient.setAddressResolutionTimeout(TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS));
			httpClient.setConnectTimeout(TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS));
			httpClient.setIdleTimeout(TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS));

			final SocketAddress bindAddress = new InetSocketAddress(clientAddress, 0);
			LOG.info("{} BindAddress: {}", this.clientInterfaceLabel, bindAddress);
			httpClient.setBindAddress(bindAddress);

			httpClient.start();

			clientContainer = new JavaxWebSocketClientContainer(httpClient);
			clientContainer.start();
			return clientContainer;
		}
		catch (final Exception e) {
			throw new IllegalStateException("Failed to setup HTTP client.", e);
		}
	}

	public void start(final ScheduledExecutorService schEx) {
		LOG.info("{} Starting with period={}s.", this.clientInterfaceLabel, PING_PERIOD_SECONDS);
		schEx.scheduleAtFixedRate(this, 0, PING_PERIOD_SECONDS, TimeUnit.SECONDS);
	}

	@SuppressWarnings("resource")
	private void connect(final Endpoint eventSocket) throws DeploymentException, IOException {
		this.connectionAttemptsWithLabels.inc();
		this.connectionDurationWithLabels.set(0);
		this.wsContainer.connectToServer(eventSocket,
				ClientEndpointConfig.Builder.create()
						.configurator(getConfigurator())
						.build(),
				this.uri);
	}

	private Configurator getConfigurator() {
		return new Configurator() {
			@Override
			public void beforeRequest(final Map<String, List<String>> headers) {
				super.beforeRequest(headers);

				final String userInfo = WsPing.this.uri.getUserInfo();
				final String auth = String.format("Basic %s", Base64.getEncoder().encodeToString(userInfo.getBytes()));
				headers.put("Authorization", Collections.singletonList(auth));
			}
		};
	}

	@Override
	public void run() {
		try {
			runOrThrow();
		}
		catch (final Exception e) {
			if (logSpamActive()) LOG.warn("{} Failed to send ping: {}", this.clientInterfaceLabel, String.valueOf(e));
			try {
				cleanupReadyForRetry();
			}
			catch (final Exception e1) {
				LOG.warn("{} Failed to clean up: {}", this.clientInterfaceLabel, String.valueOf(e1));
			}
		}
	}

	private void cleanupReadyForRetry() throws IOException {
		final EventSocket es = this.activeSocket.getAndSet(null);
		if (es != null) {
			es.dispose("Clean up ready for retry.");
		}
	}

	private void runOrThrow() throws DeploymentException, IOException {
		EventSocket es = this.activeSocket.get();
		if (es == null) {
			es = new EventSocket();
			if (!this.activeSocket.compareAndSet(null, es)) {
				return;
			}
			connect(es);
		}

		if (es.isClosed()) {
			if (this.activeSocket.compareAndSet(es, null)) {
				LOG.debug("{} Discarded socket.", this.clientInterfaceLabel);
			}
			return;
		}

		if (!es.isOpen()) {
			if (logSpamActive()) LOG.info("{} Waiting for socket to open...", this.clientInterfaceLabel);
			return;
		}

		final long millisSinceLastPong = es.millisSinceLastPong();
		if (millisSinceLastPong > TimeUnit.SECONDS.toMillis(MAX_TIMEOUT_SECONDS)) {
			LOG.info("{} {}ms since last response, disposing session.", this.clientInterfaceLabel, millisSinceLastPong);
			this.activeSocket.compareAndSet(es, null);
			es.dispose(millisSinceLastPong + "ms since last response");
			return;
		}

		es.sendPingMsg();
	}

	private class EventSocket extends Endpoint {

		private volatile boolean open = false;
		private volatile boolean closed = false;
		private volatile boolean pongReceived = false;
		private Session currentSession;
		private volatile long firstPongTime = 0L;
		private volatile long lastPongTime = 0L;

		public boolean isOpen() {
			return this.open;
		}

		public boolean isClosed() {
			return this.closed;
		}

		public void dispose(final String reason) throws IOException {
			if (this.currentSession == null) return;
			this.currentSession.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, reason));
		}

		public void sendPingMsg() throws IOException {
			if (!this.open) throw new IllegalStateException("Session not open.");
			if (this.closed) throw new IllegalStateException("Session has closed.");

			WsPing.this.requestsWithLabels.inc();
			final long now = WsPing.this.time.now();
			final String msg = PING_MSG_PREFIX + now;
			this.currentSession.getAsyncRemote().sendText(msg, new SendHandler() {
				@Override
				public void onResult(final SendResult result) {
					if (!result.isOK()) {
						if (logSpamActive()) LOG.warn("{} Send failed: {}",
								WsPing.this.clientInterfaceLabel,
								String.valueOf(result.getException()));
					}
				}
			});
		}

		private boolean handlePongMsg(final String msg) {
			final long now = WsPing.this.time.now();
			if (!this.pongReceived) {
				this.firstPongTime = now;
				this.pongReceived = true;
			}
			this.lastPongTime = now;
			restartLogSpam();

			if (msg == null || !msg.startsWith(PING_MSG_PREFIX)) return false;

			final String pingTimeRaw = msg.substring(PING_MSG_PREFIX.length());
			if (pingTimeRaw == null || pingTimeRaw.length() < 1) return false;

			final long pingTime;
			try {
				pingTime = Long.parseLong(pingTimeRaw);
			}
			catch (final NumberFormatException e) {
				return false;
			}

			WsPing.this.responsesWithLabels.inc();
			WsPing.this.roundtripLatencyWithLabels.observe((now - pingTime) / Collector.NANOSECONDS_PER_SECOND);
			WsPing.this.connectionDurationWithLabels.set((now - this.firstPongTime) / Collector.NANOSECONDS_PER_SECOND);
			return true;
		}

		public long millisSinceLastPong() {
			if (!this.pongReceived) return 0L;
			return TimeUnit.NANOSECONDS.toMillis(WsPing.this.time.now() - this.lastPongTime);
		}

		@Override
		public void onOpen(final Session session, final EndpointConfig config) {
			this.currentSession = session;
			this.open = true;
			LOG.info("{} Connected.", WsPing.this.clientInterfaceLabel);

			session.addMessageHandler(new MessageHandler.Whole<PongMessage>() {
				@Override
				public void onMessage(final PongMessage message) {
					LOG.info("{} Received pong: \"{}\".", WsPing.this.clientInterfaceLabel,
							new String(message.getApplicationData().array()));
				}
			});
			session.addMessageHandler(new MessageHandler.Whole<String>() {
				@Override
				public void onMessage(final String message) {
					if (handlePongMsg(message)) return;
					LOG.info("{} Received non-pong msg: \"{}\".", WsPing.this.clientInterfaceLabel, message);
				}
			});
		}

		@Override
		public void onClose(final Session session, final CloseReason closeReason) {
			this.closed = true;
			LOG.info("{} Closed: {}", WsPing.this.clientInterfaceLabel, closeReason);
			WsPing.this.connectionDurationWithLabels.set(0);
			super.onClose(session, closeReason);
		}

		@Override
		public void onError(final Session session, final Throwable thr) {
			this.closed = true;
			if (logSpamActive()) LOG.warn("{} Session exception: {}", WsPing.this.clientInterfaceLabel, String.valueOf(thr));
			super.onError(session, thr);

			try {
				dispose(String.valueOf(thr));
			}
			catch (final IOException e) {
				LOG.warn("{} Failed to close cleanly: {}", WsPing.this.clientInterfaceLabel, e);
			}
		}

	}

	private boolean logSpamActive() {
		final boolean active = this.time.now() - this.logSpamStartTime < LOG_SPAM_TIMEOUT_NANOS;
		if (!active && this.logSpamActive.compareAndSet(true, false)) {
			LOG.info("Supressing log spam.");
		}
		return active;
	}

	private void restartLogSpam() {
		this.logSpamStartTime = this.time.now();
		if (this.logSpamActive.compareAndSet(false, true)) {
			LOG.info("Log spam reset.");
		}
	}

	void resetMetrics() {
		METRIC_CONNECTION_ATTEMPTS.clear();
		METRIC_REQUESTS.clear();
		METRIC_RESPONSES.clear();
	}

	long getConnectAttempts() {
		return (long) this.connectionAttemptsWithLabels.get();
	}

	long getPingRequestCount() {
		return (long) this.requestsWithLabels.get();
	}

	long getPongResponseCount() {
		return (long) this.responsesWithLabels.get();
	}

}
