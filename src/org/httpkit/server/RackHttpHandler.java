package org.httpkit.server;

import java.util.Map;

@SuppressWarnings({"rawtypes", "unchecked"})
public class RackHttpHandler implements Runnable {

    final HttpRequest req;
    final RespCallback cb;
    final Object handler;

    public RackHttpHandler(HttpRequest req, RespCallback cb, Object handler) {
        this.req = req;
        this.cb = cb;
        this.handler = handler;
    }

    public void run() {
    }
}
