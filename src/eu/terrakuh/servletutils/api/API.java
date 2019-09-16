package eu.terrakuh.servletutils.api;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
			if (target.isArray()) {
				String[] values = OBJECT_MAPPER.readValue(value, String[].class);
				Object[] array = new Object[values.length];

				for (int i = 0; i < values.length; ++i) {
					array[i] = convert(values[i], target.getComponentType());
				}

				return array;
			} else if (target.isAssignableFrom(String.class)) {
				return value;
			} else if (target.isAssignableFrom(Integer.class)) {
				return Integer.parseInt(value);
			} else if (target.isAssignableFrom(URI.class)) {
				return URI.create(value);
			} else if (target.isAssignableFrom(URL.class)) {
				return new URL(value);
			} else if (target.isAssignableFrom(Path.class)) {
				return Paths.get(value);
			}
		} catch (Exception e) {
			throw new ConversionException(e);
		}

		throw new ConversionException("Unknown target type.");
	}

	private Object[] getParameterValues(HttpSession session, HttpServletRequest request, HttpServletResponse response, Method method) throws Exception
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
				} else if (getClass().isAssignableFrom(parameterClass)) {
					parameters[index++] = this;
				} else {
					parameters[index++] = getInstance(parameterClass, session, false);
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

	private Lock getLock(HttpSession session, String lockName)
	{
		if (lockName.isEmpty()) {
			return null;
		}

		String name = String.format("%s/:lock/%s", getClass().getName(), lockName);
		Lock lock = (Lock) session.getAttribute(name);

		if (lock == null) {
			session.setAttribute(name, lock = new ReentrantLock());
		}

		return lock;
	}

	private Object getInstance(Class<?> klass, HttpSession session, boolean createIfNotExists) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException
	{
		String name = String.format("%s/%s", getClass().getName(), klass.getName());
		Object instance = klass.cast(session.getAttribute(name));

		// Create
		if (instance == null && createIfNotExists) {
			instance = klass.getDeclaredConstructor().newInstance();

			session.setAttribute(name, instance);
		}

		return instance;
	}

	public final void execute(String apiClass, String apiMethod, HttpServletRequest request, HttpServletResponse response) throws IOException
	{
		try {
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

					Object[] parameters = getParameterValues(session, request, response, method);
					Object instance = getInstance(klass, session, !Modifier.isStatic(method.getModifiers()));

					// Create task
					Runnable task = () -> {
						try {
							Lock lock = getLock(session, annotation.lockingGroup());

							if (lock != null && !lock.tryLock()) {
								throw new APIAsyncException("Async task already running.");
							}

							Object result;

							try {
								result = method.invoke(instance, parameters);
							} finally {
								if (lock != null) {
									lock.unlock();
								}
							}

							OBJECT_MAPPER.writeValue(response.getWriter(), result);
						} catch (Exception e) {
							try {
								sendError(response, e);
							} catch (IOException ex) {
								ex.printStackTrace();
							}
						}
					};

					// Async operation
					if (annotation.async()) {
						AsyncContext context = request.startAsync(request, response);

						context.setTimeout(0);
						context.start(task);
					} else {
						task.run();
					}

					return;
				}
			}

			throw new APIAccessException("Method not found.");
		} catch (Exception e) {
			sendError(response, e);
		}
	}
}
