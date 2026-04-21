package kero_ping;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jetty.util.SocketAddressResolver;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kero_ping.util.DaemonThreadFactory;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.ThreadExports;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	@SuppressWarnings("unused")
	private static HTTPServer METRICS_SERVER;

	private Main() {
		throw new AssertionError();
	}

	public static void main(final String[] rawArgs) throws Exception { // NOSONAR
		final PrintStream err = System.err;
		final Args args = new Args();
		final CmdLineParser parser = new CmdLineParser(args);
		try {
			parser.parseArgument(rawArgs);
			run(args);
		}
		catch (final CmdLineException e) {
			err.println(e.getMessage());
			help(parser, err);
			return;
		}
		catch (final Exception e) {
			err.println("An unhandled error occured.");
			e.printStackTrace(err);
			System.exit(1);
		}
	}

	private static void run(final Args args) throws Exception {
		final InetAddress bindAddress = InetAddress.getByName(args.getInterface());
		LOG.info("Bind address: {}", bindAddress);

		final List<AddressAndName> clientAddresses = args.getClientInterfaceNames();
		LOG.info("Client addresses: {}", clientAddresses);

		final int httpPort = args.getHttpPort();
		if (httpPort >= 0) {
			LOG.info("HTTP listen listenPort: {}", httpPort);

			if (args.isJvmMetrics()) {
				// Subset of DefaultExports.initialize()
				new StandardExports().register();
				new MemoryPoolsExports().register();
				new MemoryAllocationExports().register();
				new BufferPoolsExports().register();
				new GarbageCollectorExports().register();
				new ThreadExports().register();
			}

			final InetSocketAddress httpSA = new InetSocketAddress(bindAddress, httpPort);
			METRICS_SERVER = new HTTPServer(httpSA, CollectorRegistry.defaultRegistry, true);
		}

		maybeStartWsPings(args, clientAddresses);
	}

	private static void maybeStartWsPings(final Args args, final List<AddressAndName> clientAddresses) throws URISyntaxException, IOException {
		final String wsEchoUrl = args.getWsEchoUrl();
		if (wsEchoUrl == null) return;
		final URI wsUrl = new URI(wsEchoUrl);

		final ScheduledExecutorService schEx = Executors.newScheduledThreadPool(clientAddresses.size(),
				new DaemonThreadFactory("ws"));

		for (final AddressAndName clientAddress : clientAddresses) {
			final WsPing wsPing = new WsPing(wsUrl, clientAddress);
			wsPing.start(schEx);
		}
	}

	private static void help(final CmdLineParser parser, final PrintStream ps) {
		ps.print("Usage: ");
		parser.printSingleLineUsage(ps);
		ps.println();
		parser.printUsage(ps);
		ps.println();
	}

}
