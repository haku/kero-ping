package kero_ping;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WsPingTest {

	private Server server;
	private WsPing undertest;
	private AtomicBoolean doEcho = new AtomicBoolean();

	@Before
	public void before() throws Exception {
		final InetAddress serverAddress = InetAddress.getLocalHost();
		final URI serverUri = setupMockServer(serverAddress);
		final AddressAndName aan = new AddressAndName(serverAddress, "testname");
		this.undertest = new WsPing(serverUri, aan);
		this.undertest.resetMetrics();
	}

	@After
	public void after() throws Exception {
		if (this.server != null) this.server.stop();
	}

	public URI setupMockServer(final InetAddress serverAddress) throws Exception {
		this.server = new Server();

		final ServerConnector connector = new ServerConnector(this.server);
		final String hostAddress = serverAddress.getHostAddress();
		connector.setHost(hostAddress);
		connector.setPort(0);
		this.server.addConnector(connector);

		final ServletContextHandler contextHandler = new ServletContextHandler();
		contextHandler.setContextPath("/");
		contextHandler.addServlet(new ServletHolder(new MyServlet()), "/");
		JettyWebSocketServletContainerInitializer.configure(contextHandler, null);
		this.server.setHandler(contextHandler);

		this.server.start();
		return new URI("ws", "user:passwd", hostAddress, connector.getLocalPort(), "/", null, null);
	}

	public class MyServlet extends JettyWebSocketServlet {
		private static final long serialVersionUID = 1L;
		@Override
		public void configure(JettyWebSocketServletFactory factory) {
			factory.setCreator(new MyWebSocketCreator());
		}
	}

	public class MyWebSocketCreator implements JettyWebSocketCreator {
		@Override
		public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
			return new EchoSocket();
		}
	}

	@WebSocket
	public class EchoSocket {
		@OnWebSocketMessage
		public void onWebSocketText(final Session sess, final String message) throws IOException {
			if (WsPingTest.this.doEcho.get()) sess.getRemote().sendString(message);
		}
	}

	@Test
	public void itCountsSingleCheck() throws Exception {
		this.doEcho.set(true);
		this.undertest.run();
		assertMetrics(1, 1, 1);
	}

	@Test
	public void itNoticesWhenResponsesStop() throws Exception {
		this.doEcho.set(true);
		this.undertest.run();
		assertMetrics(1, 1, 1);
		Thread.sleep(1000);

		this.doEcho.set(false);
		for (int i = 0; i < 10; i ++) {
			this.undertest.run();
			Thread.sleep(1000);
		}

		this.undertest.run();
		assertMetrics(2, 11, 1);
	}

	private void assertMetrics(long expConnects, long expRequests, long expResponses) throws InterruptedException {
		Thread.sleep(200); // FIXME
		assertEquals(expConnects, this.undertest.getConnectAttempts());
		assertEquals(expRequests, this.undertest.getPingRequestCount());
		assertEquals(expResponses, this.undertest.getPongResponseCount());
	}

}
