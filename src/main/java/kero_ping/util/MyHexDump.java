package kero_ping.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * Modified org.apache.commons.io.HexDump with length and with params.
 */
public class MyHexDump {

	public static String dumpAsString(final byte[] data, final long offset, final int index, final int length,
			final int width) throws IOException, ArrayIndexOutOfBoundsException, IllegalArgumentException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		MyHexDump.dump(data, offset, out, index, length, width, false);
		out.close();  // To make the linter happy.
		return out.toString(Charset.defaultCharset());
	}

	public static void dump(final byte[] data, final long offset, final OutputStream stream, final int index,
			final int length, final int width, final boolean trailingNewLine)
			throws IOException, ArrayIndexOutOfBoundsException, IllegalArgumentException {
		if (index < 0 || index >= length) throw new ArrayIndexOutOfBoundsException(
				"illegal index: " + index + " into array of length " + data.length);
		if (stream == null) throw new IllegalArgumentException("cannot write to nullstream");

		long display_offset = offset + index;
		final StringBuilder buffer = new StringBuilder(75);

		for (int j = index; j < length; j += width) {
			int chars_read = length - j;

			if (chars_read > width) chars_read = width;
			dump(buffer, display_offset).append(' ');
			for (int k = 0; k < width; k++) {
				if (k < chars_read) {
					dump(buffer, data[k + j]);
				}
				else {
					buffer.append("  ");
				}
				buffer.append(' ');
			}
			buffer.append(' ');
			for (int k = 0; k < chars_read; k++) {
				if (data[k + j] >= ' ' && data[k + j] < 127) {
					buffer.append((char) data[k + j]);
				}
				else {
					buffer.append('.');
				}
			}

			if (trailingNewLine || j < length - width) {
				buffer.append("\n");
			}

			stream.write(buffer.toString().getBytes(Charset.defaultCharset()));
			stream.flush();
			buffer.setLength(0);
			display_offset += chars_read;
		}
	}

	private static final char[] _hexcodes = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E',
			'F' };
	private static final int[] _shifts = { 28, 24, 20, 16, 12, 8, 4, 0 };

	private static StringBuilder dump(final StringBuilder _lbuffer, final long value) {
		for (int j = 0; j < 8; j++) {
			_lbuffer.append(_hexcodes[(int) (value >> _shifts[j]) & 15]);
		}
		return _lbuffer;
	}

	private static StringBuilder dump(final StringBuilder _cbuffer, final byte value) {
		for (int j = 0; j < 2; j++) {
			_cbuffer.append(_hexcodes[value >> _shifts[j + 6] & 15]);
		}
		return _cbuffer;
	}

}
