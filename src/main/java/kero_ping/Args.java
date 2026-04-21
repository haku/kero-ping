package kero_ping;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Option;

import kero_ping.util.NetHelper;

public class Args {

	@Option(name = "--interface", required = true, usage = "Hostname or IP address of interface to bind to.") private String iface;
	@Option(name = "--http", usage = "Local port to bind HTTP server to.  Omit to disable.") private int httpPort = -1;

	@Option(name = "--client", metaVar = "<IP address or subnet(=name)>", required = true, usage = "Interfaces for outbound connections. Append =<name> to emit friendly name.")
	private List<String> clientInterfaceNames;

	@Option(name = "--wsechourl", metaVar = "<WS Echo URL>", usage = "URL of a WebSocket echo service.")
	private String wsEchoUrl = null;

	@Option(name = "--wsechourlfile", metaVar = "<Path to WS Echo URL>", usage = "Path to file containing URL of a WebSocket echo service.")
	private String wsEchoUrlFile = null;

	@Option(name = "--jvmmetrics", usage = "Export JVM Prometheus metrics.")
	private boolean jvmMetrics;

	public String getInterface () {
		return this.iface;
	}

	public int getHttpPort() {
		return this.httpPort;
	}

	public List<AddressAndName> getClientInterfaceNames() throws IOException {
		final List<AddressAndName> ret = new ArrayList<>();
		for (final String arg : this.clientInterfaceNames) {
			final String[] parts = StringUtils.split(arg, "=", 2);
			final InetAddress address = NetHelper.resolveSubnetToAddress(parts[0]);
			String name = parts.length > 1 ? parts[1] : "";
			name = StringUtils.trimToEmpty(name);
			ret.add(new AddressAndName(address, name));
		}
		return ret;
	}

	public String getWsEchoUrl() throws IOException {
		if (!StringUtils.isBlank(this.wsEchoUrl)) {
			return this.wsEchoUrl;
		}

		if (StringUtils.isBlank(this.wsEchoUrlFile)) {
			return null;
		}

		final File file = new File(this.wsEchoUrlFile);
		final List<String> lines = FileUtils.readLines(file, Charset.defaultCharset());
		if (lines.size() < 1) {
			throw new IllegalArgumentException("Empty file: " + this.wsEchoUrlFile);
		}

		final String line = StringUtils.trimToEmpty(lines.get(0));
		if (StringUtils.isBlank(line)) {
			throw new IllegalArgumentException("File missing URL: " + this.wsEchoUrlFile);
		}

		return line;
	}

	public boolean isJvmMetrics() {
		return this.jvmMetrics;
	}

}
