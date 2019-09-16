package eu.terrakuh.servletutils.api;

public class APIAsyncException extends Exception
{
	public APIAsyncException()
	{
	}

	public APIAsyncException(String message)
	{
		super(message);
	}

	public APIAsyncException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public APIAsyncException(Throwable cause)
	{
		super(cause);
	}
}
