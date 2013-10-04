package org.httpkit;

public enum HttpMethod {

    GET("get"),
    HEAD("head"),
    POST("post"),
    PUT("put"),
    DELETE("delete"),
    TRACE("trace"),
    OPTIONS("options"),
    CONNECT("connect"),
    PATCH("patch");

    public final String KEY;

    private HttpMethod(String key) {
        this.KEY = key;
    }
}
