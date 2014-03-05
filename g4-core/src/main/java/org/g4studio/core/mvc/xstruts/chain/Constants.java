package org.g4studio.core.mvc.xstruts.chain;

/**
 * <p>
 * Global constants for the Chain of Responsibility Library.
 * </p>
 */
public final class Constants {
    // -------------------------------------------------- Context Attribute Keys

    /**
     * <p>
     * The default context attribute under which the Action for the current
     * request will be stored.
     * </p>
     */
    public static final String ACTION_KEY = "action";

    /**
     * <p>
     * The default context attribute under which the ActionConfig for the
     * current request will be stored.
     * </p>
     */
    public static final String ACTION_CONFIG_KEY = "actionConfig";

    /**
     * <p>
     * The default context attribute under which the ActionForm for the current
     * request will be stored.
     * </p>
     */
    public static final String ACTION_FORM_KEY = "actionForm";

    /**
     * <p>
     * The default context attribute under which the ActionServet for the
     * current application will be stored.
     * </p>
     */
    public static final String ACTION_SERVLET_KEY = "actionServlet";

    /**
     * <p>
     * The default context attribute under which a boolean flag indicating
     * whether this request has been cancelled will be stored.
     * </p>
     */
    public static final String CANCEL_KEY = "cancel";

    /**
     * <p>
     * The default context attribute under which an Exception will be stored
     * before passing it to an exception handler chain.
     * </p>
     */
    public static final String EXCEPTION_KEY = "exception";

    /**
     * <p>
     * The default context attribute under which the ForwardConfig for the
     * current request will be stored.
     * </p>
     */
    public static final String FORWARD_CONFIG_KEY = "forwardConfig";

    /**
     * <p>
     * The default context attribute under which the include path for the
     * current request will be stored.
     * </p>
     */
    public static final String INCLUDE_KEY = "include";

    /**
     * <p>
     * The default context attribute under which the Locale for the current
     * request will be stored.
     * </p>
     */
    public static final String LOCALE_KEY = "locale";

    /**
     * <p>
     * The default context attribute under which the MessageResources for the
     * current request will be stored.
     * </p>
     */
    public static final String MESSAGE_RESOURCES_KEY = "messageResources";

    /**
     * <p>
     * The default context attribute under which the ModuleConfig for the
     * current request will be stored.
     * </p>
     */
    public static final String MODULE_CONFIG_KEY = "moduleConfig";

    /**
     * <p/>
     * The default context attribute key under which a Boolean is stored,
     * indicating the valid state of the current request. If not present, a
     * value of Boolean.FALSE should be assumed.
     */
    public static final String VALID_KEY = "valid";

    // --------------------------------------------------------- Other Constants

    /**
     * <p>
     * The base part of the context attribute under which a Map containing the
     * Action instances associated with this module are stored. This value must
     * be suffixed with the module prefix in order to create a unique key per
     * module.
     * </p>
     */
    public static final String ACTIONS_KEY = "actions";

    /**
     * <p>
     * The context attribute under which the Catalog containing our defined
     * command chains has been stored.
     * </p>
     */
    public static final String CATALOG_ATTR = "org.g4studio.core.mvc.xstruts.chain.CATALOG";

    /**
     * <p>
     * The request attribute under which the path information is stored for
     * processing during a RequestDispatcher.include() call.
     * </p>
     */
    public static final String INCLUDE_PATH_INFO = "javax.servlet.include.path_info";

    /**
     * <p>
     * The request attribute under which the servlet path is stored for
     * processing during a RequestDispatcher.include() call.
     * </p>
     */
    public static final String INCLUDE_SERVLET_PATH = "javax.servlet.include.servlet_path";
}
