package kero_ping;

import java.net.InetAddress;

public class AddressAndName {

	final InetAddress address;
	final String name;

	public AddressAndName(InetAddress address, String name) {
		this.address = address;
		this.name = name != null ? name : "";
	}

	@Override
	public String toString() {
		if (this.name.length() > 0) {
			return String.format("%s=%s", this.address, this.name);
		}
		return this.address.toString();
	}

}
