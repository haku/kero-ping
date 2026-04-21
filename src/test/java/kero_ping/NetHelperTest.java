package kero_ping;

import static org.junit.Assert.assertEquals;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.apache.commons.net.util.SubnetUtils;
import org.junit.Test;

import kero_ping.util.NetHelper;

public class NetHelperTest {

	@Test
	public void itLooksUpIpAddress() throws Exception {
		final String ipAddress = getLocalAddress().getAddress().getHostAddress();
		assertEquals(ipAddress, NetHelper.resolveSubnetToAddress(ipAddress).getHostAddress());
	}

	@Test
	public void itLooksUpSubnet() throws Exception {
		final InterfaceAddress localAddress = getLocalAddress();
		final String ipAddress = localAddress.getAddress().getHostAddress();
		final SubnetUtils sn = new SubnetUtils(ipAddress + "/" + localAddress.getNetworkPrefixLength());
		final String subnet = sn.getInfo().getLowAddress() + "/" + localAddress.getNetworkPrefixLength();
		assertEquals(ipAddress, NetHelper.resolveSubnetToAddress(subnet).getHostAddress());
	}

	private static InterfaceAddress getLocalAddress() throws SocketException {
		for (final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
				interfaces.hasMoreElements();) {
			final NetworkInterface iface = interfaces.nextElement();
			if (iface.isLoopback()) continue;
			for (final InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
				if (!(ifaceAddr.getAddress() instanceof Inet4Address)) continue;
				if (ifaceAddr.getNetworkPrefixLength() > 31) continue;
				return ifaceAddr;
			}
		}
		throw new IllegalStateException();
	}

}
