package eu.terrakuh.servletutils.api;

public class ConversionException extends Exception
{
	public ConversionException()
	{
	}

	public ConversionException(String message)
	{
		super(message);
	}

	public ConversionException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ConversionException(Throwable cause)
	{
		super(cause);
	}
}
