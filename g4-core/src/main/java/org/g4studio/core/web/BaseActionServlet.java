package org.g4studio.core.web;

import javax.servlet.ServletException;

import org.g4studio.core.mvc.xstruts.action.ActionServlet;

/**
 * ActionServlet基类
 *
 * @author XiongChun
 * @see ActionServlet
 * @since 2009-09-03
 */
public class BaseActionServlet extends ActionServlet {
    public BaseActionServlet() {
    }

    /**
     * @param
     * @return void
     */
    public void init() throws ServletException {
        super.init();
    }
}
