package kero_ping;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FixtureServlet extends HttpServlet {

	private static final long serialVersionUID = 1164486017828356643L;
	private final Map<String, Fixture> fixtures = new ConcurrentHashMap<>();

	private static String makeKey(final String method, final String path) {
		return method + "|" + path;
	}

	public void addFixture(final String method, final URI uri, int status, final String contentType, final byte[] body) {
		String path = uri.getPath();
		if (uri.getQuery() != null) path += "?" + uri.getQuery();
		addFixture(method, path, status, contentType, body);
	}

	public void addFixture(final String method, final String path, int status, final String contentType, final byte[] body) {
		final String key = makeKey(method, path);
		this.fixtures.put(key, new Fixture(status, contentType, body));
		System.out.println("Fxiture added: " + path);
	}

	@SuppressWarnings("resource")
	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		String url = req.getRequestURI();
		final String query = req.getQueryString();
		if (query != null) url += "?" + query;

		final String key = makeKey(req.getMethod(), url);
		final Fixture fixture = this.fixtures.get(key);
		if (fixture == null) {
			System.out.println("Fixture not found: " + url);
			error(resp, 404, "Fixture not found: " + url);
			return;
		}

		resp.setStatus(fixture.status);
		resp.setContentType(fixture.contentType);
		resp.getOutputStream().write(fixture.body);
		resp.flushBuffer();
	}

	@SuppressWarnings("resource")
	public static void error(final HttpServletResponse resp, final int status, final String msg) throws IOException {
		resp.reset();
		resp.setStatus(status);
		resp.setContentType("text/plain");
		resp.getWriter().println("HTTP Error " + status + ": " + msg);
	}

	private class Fixture {

		final int status;
		final String contentType;
		final byte[] body;

		public Fixture(int status, final String contentType, final byte[] body) {
			this.status = status;
			this.contentType = contentType;
			this.body = body;
		}

	}

}
