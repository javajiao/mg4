package org.g4studio.core.orm.xibatis.sqlmap.engine.mapping.result.loader;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

import org.g4studio.core.orm.xibatis.common.beans.ClassInfo;
import org.g4studio.core.orm.xibatis.sqlmap.engine.impl.SqlMapClientImpl;
import org.g4studio.core.orm.xibatis.sqlmap.engine.type.DomTypeMarker;

/**
 * Class to lazily load results into objects (uses CGLib to improve performance)
 */
public class EnhancedLazyResultLoader {

    private static final Class[] SET_INTERFACES = new Class[]{Set.class};
    private static final Class[] LIST_INTERFACES = new Class[]{List.class};
    private Object loader;

    /**
     * Constructor for an enhanced lazy list loader
     *
     * @param client          - the client that is creating the lazy list
     * @param statementName   - the statement to be used to build the list
     * @param parameterObject - the parameter object to be used to build the list
     * @param targetType      - the type we are putting data into
     */
    public EnhancedLazyResultLoader(SqlMapClientImpl client, String statementName, Object parameterObject,
                                    Class targetType) {
        loader = new EnhancedLazyResultLoaderImpl(client, statementName, parameterObject, targetType);
    }

    /**
     * Loads the result
     *
     * @return the results - a list or object
     * @throws SQLException if there is a problem
     */
    public Object loadResult() throws SQLException {
        return ((EnhancedLazyResultLoaderImpl) loader).loadResult();
    }

    private static class EnhancedLazyResultLoaderImpl implements InvocationHandler {

        protected SqlMapClientImpl client;
        protected String statementName;
        protected Object parameterObject;
        protected Class targetType;

        protected boolean loaded;
        protected Object resultObject;

        /**
         * Constructor for an enhanced lazy list loader implementation
         *
         * @param client          - the client that is creating the lazy list
         * @param statementName   - the statement to be used to build the list
         * @param parameterObject - the parameter object to be used to build the list
         * @param targetType      - the type we are putting data into
         */
        public EnhancedLazyResultLoaderImpl(SqlMapClientImpl client, String statementName, Object parameterObject,
                                            Class targetType) {
            this.client = client;
            this.statementName = statementName;
            this.parameterObject = parameterObject;
            this.targetType = targetType;
        }

        /**
         * Loads the result
         *
         * @return the results - a list or object
         * @throws SQLException if there is a problem
         */
        public Object loadResult() throws SQLException {
            if (DomTypeMarker.class.isAssignableFrom(targetType)) {
                return ResultLoader.getResult(client, statementName, parameterObject, targetType);
            } else if (Collection.class.isAssignableFrom(targetType)) {
                if (Set.class.isAssignableFrom(targetType)) {
                    return Enhancer.create(Object.class, SET_INTERFACES, this);
                } else {
                    return Enhancer.create(Object.class, LIST_INTERFACES, this);
                }
            } else if (targetType.isArray() || ClassInfo.isKnownType(targetType)) {
                return ResultLoader.getResult(client, statementName, parameterObject, targetType);
            } else {
                return Enhancer.create(targetType, this);
            }
        }

        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            if ("finalize".hashCode() == method.getName().hashCode() && "finalize".equals(method.getName())) {
                return null;
            } else {
                loadObject();
                if (resultObject != null) {
                    try {
                        return method.invoke(resultObject, objects);
                    } catch (Throwable t) {
                        throw ClassInfo.unwrapThrowable(t);
                    }
                } else {
                    return null;
                }
            }
        }

        private synchronized void loadObject() {
            if (!loaded) {
                try {
                    loaded = true;
                    resultObject = ResultLoader.getResult(client, statementName, parameterObject, targetType);
                } catch (SQLException e) {
                    throw new RuntimeException("Error lazy loading result. Cause: " + e, e);
                }
            }
        }
    }

}
