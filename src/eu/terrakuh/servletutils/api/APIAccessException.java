package eu.terrakuh.servletutils.api;

public class APIAccessException extends Exception
{
	public APIAccessException()
	{
	}

	public APIAccessException(String message)
	{
		super(message);
	}

	public APIAccessException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public APIAccessException(Throwable cause)
	{
		super(cause);
	}
}
