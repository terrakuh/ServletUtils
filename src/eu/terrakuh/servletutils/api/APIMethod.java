package eu.terrakuh.servletutils.api;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface APIMethod
{
	/**
	 * @return the minimum required access level to execute this function
	 */
	int accessLevel();

	String method() default "GET";

	boolean async() default false;

	String lockingGroup() default "";
}
