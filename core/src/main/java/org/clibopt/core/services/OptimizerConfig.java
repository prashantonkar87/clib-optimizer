package org.clibopt.core.services;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "ClientLib Optimizer", description = "Configurations for Clientlib Optimizer")
public @interface OptimizerConfig {

	@AttributeDefinition(name = "Enable Client Lib Optimization", description = "Enable/Disable clientlib optimization")
	boolean isOptEnabled() default false;

	@AttributeDefinition(name = "Enabled Content Paths", description = "List of content paths to be optimized")
	String[] paths() default { "/content/*" };

	@AttributeDefinition(name = "Ignore clientlib paths", description = "List of clientlibs which are to be ignored from optimization")
	String[] ignoredClibs();

}
