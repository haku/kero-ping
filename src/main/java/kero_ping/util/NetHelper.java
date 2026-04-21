package kero_ping.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.apache.commons.net.util.SubnetUtils;

public class NetHelper {

	public static InetAddress resolveSubnetToAddress(final String subnetOrAddress) throws SocketException, UnknownHostException {
		try {
			final InetAddress byName = InetAddress.getByName(subnetOrAddress);
			if (byName != null) return byName;
		}
		catch (UnknownHostException e) {}

		final SubnetUtils s = new SubnetUtils(subnetOrAddress);

		for (final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
			final NetworkInterface iface = interfaces.nextElement();
			for (final InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
				final InetAddress inetAddr = ifaceAddr.getAddress();
				if (!(inetAddr instanceof Inet4Address)) continue;
				if (s.getInfo().isInRange(inetAddr.getHostAddress())) {
					return inetAddr;
				}
			}
		}

		return null;
	}

}
