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
//      System.out.println("RackHttpHandler run");
//
//        try {
//            Object resp_arr[] = handler.call(buildRequestMap(req));
//
//            if (resp_arr == null) { // handler return null
//                cb.run(HttpEncode(404, new HeaderMap(), null));
//            } else {
//                Map<Keyword, Object> resp = new TreeMap<Keyword, Object>();
//
//                resp.put(STATUS  , resp_arr[0]);
//                resp.put(HEADERS , PersistentArrayMap.create((Map) resp_arr[1]));
//                resp.put(BODY    , resp_arr[2]);
//
//                System.out.println( resp.get(STATUS) );
//
//                Object body = resp.get(BODY);
//
//                if (!(body instanceof AsyncChannel)) { // hijacked
//                    HeaderMap headers = HeaderMap.camelCase((Map) resp.get(HEADERS));
//                    if (req.version == HTTP_1_0 && req.isKeepAlive) {
//                        headers.put("Connection", "Keep-Alive");
//                    }
//                    cb.run(HttpEncode(getStatus(resp), headers, body));
//                }
//            }
//        } catch (Throwable e) {
//            cb.run(HttpEncode(500, new HeaderMap(), e.getMessage()));
//            HttpUtils.printError(req.method + " " + req.uri, e);
//        }
    }
}
