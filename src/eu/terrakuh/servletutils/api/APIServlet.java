package eu.terrakuh.servletutils.api;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class APIServlet extends HttpServlet
{
	private final static Logger LOGGER = Logger.getLogger(APIServlet.class.getName());
	private final static Pattern PATTERN = Pattern.compile("/(\\w+?)/(\\w+?)(?:\\?.*)?$");
	private API api;

	@Override
	public void init(ServletConfig config) throws ServletException
	{
		try {
			Class<?> klass = Thread.currentThread().getContextClassLoader().loadClass(config.getInitParameter("apiClass"));

			if (!API.class.isAssignableFrom(klass)) {
				throw new ServletException("Specified class is not an API class.");
			}

			api = (API) klass.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			throw new ServletException(e);
		}

		super.init(config);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		LOGGER.info(String.format("Processing request to '%s'.", req.getRequestURI()));

		Matcher matcher = PATTERN.matcher(req.getRequestURI());

		if (!matcher.find()) {
			api.sendError(resp, new ServletException("Malformed request."));
		} else {
			api.execute(matcher.group(1), matcher.group(2), req, resp);
		}
	}
}
