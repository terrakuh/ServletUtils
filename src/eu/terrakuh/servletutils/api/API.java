package eu.terrakuh.servletutils.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public abstract class API
{
	private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private final static Logger LOGGER = Logger.getLogger(API.class.getName());
	private final Map<String, Class<?>> apiClasses;

	protected API(Map<String, Class<?>> apiClasses)
	{
		this.apiClasses = new HashMap<>(apiClasses);
	}

	public void sendError(HttpServletResponse response, Exception e) throws IOException
	{
		response.sendError(HttpServletResponse.SC_CONFLICT);
	}

	protected Object convert(String value, Class<?> target) throws ConversionException
	{
		try {
			if (target.isAssignableFrom(String.class)) {
				return value;
			} else if (target.isAssignableFrom(Integer.class)) {
				return Integer.parseInt(value);
			}
		} catch (Exception e) {
			throw new ConversionException(e);
		}

		throw new ConversionException("Unknown target type.");
	}

	private Object[] getParameterValues(HttpServletRequest request, HttpServletResponse response, Method method) throws Exception
	{
		Object[] parameters = new Object[method.getParameterCount()];
		int index = 0;

		// Check parameters
		for (Parameter parameter : method.getParameters()) {
			APIParameter apiParameter = parameter.getAnnotation(APIParameter.class);

			if (apiParameter == null) {
				// Check if it is a response or request
				Class<?> parameterClass = parameter.getType();

				if (HttpServletResponse.class.isAssignableFrom(parameterClass)) {
					parameters[index++] = response;
				} else if (HttpServletRequest.class.isAssignableFrom(parameterClass)) {
					parameters[index++] = request;
				} else if (HttpSession.class.isAssignableFrom(parameterClass)) {
					parameters[index++] = request.getSession();
				} else {
					throw new Exception("");
				}
			} else {
				String value = request.getParameter(apiParameter.value());

				// Value not provided
				if (value == null && !apiParameter.optional()) {
					throw new Exception("");
				}

				parameters[index++] = convert(value, parameter.getType());
			}
		}

		return parameters;
	}

	public void setAccessLevel(HttpSession session, int accessLevel)
	{
		session.setAttribute(String.format("%s/:accessLevel", getClass().getName()), accessLevel);
	}

	public int getAccessLevel(HttpSession session)
	{
		return Optional.ofNullable((Integer) session.getAttribute(String.format("%s/:accessLevel", getClass().getName()))).orElse(-1);
	}

	public final void execute(String apiClass, String apiMethod, HttpServletRequest request, HttpServletResponse response) throws Exception
	{
		LOGGER.info(String.format("Execute '%s' from '%s'.", apiMethod, apiClass));

		Class<?> klass = apiClasses.get(apiClass);

		// No api class available
		if (klass == null) {
			throw new APIAccessException("Class not found.");
		}

		HttpSession session = request.getSession();
		int accessLevel = getAccessLevel(session);

		LOGGER.info(String.format("Access level at: %d", accessLevel));

		// Search for the api method
		for (Method method : klass.getMethods()) {
			if (method.getName().equals(apiMethod)) {
				// Check method and access level
				APIMethod annotation = method.getAnnotation(APIMethod.class);

				// Not allowed
				if (annotation == null) {
					throw new APIAccessException("Api method not available.");
				} else if (annotation.accessLevel() > accessLevel) {
					throw new APIAccessException("Access denied.");
				}

				Object[] parameters = getParameterValues(request, response, method);
				Object instance = null;

				// Get object
				if (!Modifier.isStatic(method.getModifiers())) {
					String name = String.format("%s/%s", getClass().getName(), apiClass);

					instance = klass.cast(session.getAttribute(name));

					// Create
					if (instance == null) {
						instance = klass.newInstance();

						session.setAttribute(name, instance);
					}
				}

				// Invoke and send result
				OBJECT_MAPPER.writeValue(response.getWriter(), method.invoke(instance, parameters));

				return;
			}
		}

		throw new APIAccessException("Method not found.");
	}
}
