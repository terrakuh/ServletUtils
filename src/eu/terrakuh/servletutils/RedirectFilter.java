package eu.terrakuh.servletutils;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RedirectFilter implements Filter
{
	private String to;

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		to = filterConfig.getInitParameter("to");

		if (to == null) {
			throw new ServletException("'to' parameter is missing for a redirect.");
		}
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException
	{
		((HttpServletResponse) servletResponse).sendRedirect(to);
	}
}
