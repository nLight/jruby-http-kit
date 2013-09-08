package org.httpkit.server;

import java.util.Map;

public interface RackApplication {
    void destroy();

    /** Make a request into the Rack-based Ruby web application. */
    public Object[] call(Map<Object, Object> env);
}
