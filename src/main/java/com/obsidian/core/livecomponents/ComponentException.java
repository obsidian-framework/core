package com.obsidian.core.livecomponents;

/**
 * Base exception for LiveComponent errors.
 * Provides specific exception types for common component-related failures.
 */
public class ComponentException extends RuntimeException
{
    /**
     * Constructor with message.
     *
     * @param message Error message
     */
    public ComponentException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause.
     *
     * @param message Error message
     * @param cause Underlying exception
     */
    public ComponentException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Exception thrown when component is not found in registry.
     */
    public static class ComponentNotFoundException extends ComponentException {
        /**
         * Constructor.
         *
         * @param componentName Name of component that wasn't found
         */
        public ComponentNotFoundException(String componentName) {
            super("Component not found: " + componentName + ". Make sure it's registered with @LiveComponent annotation.");
        }
    }

    /**
     * Exception thrown when component template file is not found.
     */
    public static class TemplateNotFoundException extends ComponentException {
        /**
         * Constructor.
         *
         * @param templatePath Path to missing template
         * @param cause Underlying exception
         */
        public TemplateNotFoundException(String templatePath, Throwable cause) {
            super("Template not found: " + templatePath + ". Check that the file exists in src/main/resources/" + templatePath, cause);
        }
    }

    /**
     * Exception thrown when action method is not found on component.
     */
    public static class ActionNotFoundException extends ComponentException {
        /**
         * Constructor.
         *
         * @param componentName Component class name
         * @param actionName Action method name
         */
        public ActionNotFoundException(String componentName, String actionName) {
            super("Action '" + actionName + "' not found in component '" + componentName + "'. Make sure the method exists and is public.");
        }
    }

    /**
     * Exception thrown when state hydration fails.
     */
    public static class StateHydrationException extends ComponentException {
        /**
         * Constructor.
         *
         * @param fieldName Name of field that failed to hydrate
         * @param cause Underlying exception
         */
        public StateHydrationException(String fieldName, Throwable cause) {
            super("Failed to hydrate state field: " + fieldName, cause);
        }
    }
}