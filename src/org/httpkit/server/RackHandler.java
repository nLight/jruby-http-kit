package org.httpkit.server;

import org.httpkit.HeaderMap;
import org.httpkit.HttpUtils;
import org.httpkit.PrefixThreadFactory;

import java.util.Map;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.httpkit.HttpUtils.HttpEncode;
import static org.httpkit.HttpVersion.HTTP_1_0;
import static org.httpkit.server.Rack.*;
import org.httpkit.server.RackHttpHandler;
import org.httpkit.server.RackApplication;
import static org.httpkit.server.Frame.TextFrame;

@SuppressWarnings({"rawtypes", "unchecked"})
class Rack {

    static final String SERVER_PORT = "server-port";
    static final String SERVER_NAME = "server-name";
    static final String REMOTE_ADDR = "remote-addr";
    static final String URI = "uri";
    static final String QUERY_STRING = "query-string";
    static final String SCHEME = "scheme";
    static final String REQUEST_METHOD = "request-method";
    static final String HEADERS = "headers";
    static final String CONTENT_TYPE = "content-type";
    static final String CONTENT_LENGTH = "content-length";
    static final String CHARACTER_ENCODING = "character-encoding";
    static final String BODY = "body";
    static final String WEBSOCKET = "websocket?";
    static final String ASYC_CHANNEL = "async-channel";

    static final String HTTP = "http";

    static final String STATUS = "status";

    public static int getStatus(Map<String, Object> resp) {
        int status = 200;
        Object s = resp.get(STATUS);
        if (s instanceof Long) {
            status = ((Long) s).intValue();
        } else if (s instanceof Integer) {
            status = (Integer) s;
        }
        return status;
    }

    public static Map buildRequestMap(HttpRequest req) {
        Map<String, Object> m = new TreeMap<String, Object>();
        m.put("SERVER_SOFTWARE"            , "HTTP Kit");
        m.put("SERVER_NAME"                , req.serverName);
        m.put("rack.input"                 , "#<StringIO:0x007fa1bce039f8>");
        // m.put("rack.version"               , [1, 0]);
        m.put("rack.errors"                , System.err);
        m.put("rack.multithread"           , true);
        m.put("rack.multiprocess"          , false);
        m.put("rack.run_once"              , false);
        m.put("REQUEST_METHOD"             , req.method.KEY);
        m.put("REQUEST_PATH"               , "/favicon.ico");
        m.put("PATH_INFO"                  , "/favicon.ico");
        m.put("REQUEST_URI"                , req.uri);
        m.put("HTTP_VERSION"               , "HTTP/1.1");
        m.put("HTTP_HOST"                  , "localhost:8080");
        m.put("HTTP_CONNECTION"            , "keep-alive");
        m.put("HTTP_ACCEPT"                , "*/*");
        m.put("HTTP_USER_AGENT", "Mozilla/ , 0 (Macintosh; Intel Mac OS X 10_7_4) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.47 Safari/536.11");
        m.put("HTTP_ACCEPT_ENCODING"       , "gzip,deflate,sdch");
        m.put("HTTP_ACCEPT_LANGUAGE"       , "en-US,en;q=0.8");
        m.put("HTTP_ACCEPT_CHARSET"        , "ISO-8859-1,utf-8;q=0.7,*;q=0.3");
        m.put("HTTP_COOKIE"                , "_gauges_unique_year=1;  _gauges_unique_month=1");
        m.put("GATEWAY_INTERFACE"          , "CGI/1.2");
        m.put("SERVER_PORT"                , req.serverPort);
        m.put("QUERY_STRING"               , req.queryString);
        m.put("SERVER_PROTOCOL"            , "HTTP/1.1");
        m.put("rack.url_scheme"            , "http"); // only http is supported
        m.put("SCRIPT_NAME"                , "");
        m.put("REMOTE_ADDR"                , req.getRemoteAddr());
        // m.put("async.callback",  "#<Method: Thin::Connection#post_process>");
        // m.put("async.close",  "#<EventMachine::DefaultDeferrable:0x007fa1bce35b8");




        // m.put(URI, req.uri);
        // m.put(ASYC_CHANNEL, req.channel);
        // m.put(WEBSOCKET, req.isWebSocket);

        // // key is already lower cased, required by ring spec
        // m.put(HEADERS, PersistentArrayMap.create(req.headers));
        // m.put(CONTENT_TYPE, req.contentType);
        // m.put(CONTENT_LENGTH, req.contentLength);
        // m.put(CHARACTER_ENCODING, req.charset);
        // m.put(BODY, req.getBody());
        return m;
    }
}

// RackEvn
// {
//   "SERVER_SOFTWARE"=>"thin 1.4.1 codename Chromeo",
//   "SERVER_NAME"=>"localhost",
//   "rack.input"=>#<StringIO:0x007fa1bce039f8>,
//   "rack.version"=>[1, 0],
//   "rack.errors"=>#<IO:<STDERR>>,
//   "rack.multithread"=>false,
//   "rack.multiprocess"=>false,
//   "rack.run_once"=>false,
//   "REQUEST_METHOD"=>"GET",
//   "REQUEST_PATH"=>"/favicon.ico",
//   "PATH_INFO"=>"/favicon.ico",
//   "REQUEST_URI"=>"/favicon.ico",
//   "HTTP_VERSION"=>"HTTP/1.1",
//   "HTTP_HOST"=>"localhost:8080",
//   "HTTP_CONNECTION"=>"keep-alive",
//   "HTTP_ACCEPT"=>"*/*",
//   "HTTP_USER_AGENT"=>
//   "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_4) AppleWebKit/536.11 (KHTML, like Gecko) Chrome/20.0.1132.47 Safari/536.11",
//   "HTTP_ACCEPT_ENCODING"=>"gzip,deflate,sdch",
//   "HTTP_ACCEPT_LANGUAGE"=>"en-US,en;q=0.8",
//   "HTTP_ACCEPT_CHARSET"=>"ISO-8859-1,utf-8;q=0.7,*;q=0.3",
//   "HTTP_COOKIE"=> "_gauges_unique_year=1;  _gauges_unique_month=1",
//   "GATEWAY_INTERFACE"=>"CGI/1.2",
//   "SERVER_PORT"=>"8080",
//   "QUERY_STRING"=>"",
//   "SERVER_PROTOCOL"=>"HTTP/1.1",
//   "rack.url_scheme"=>"http",
//   "SCRIPT_NAME"=>"",
//   "REMOTE_ADDR"=>"127.0.0.1",
//   "async.callback"=>#<Method: Thin::Connection#post_process>,
//   "async.close"=>#<EventMachine::DefaultDeferrable:0x007fa1bce35b88
// }

// @SuppressWarnings({"rawtypes", "unchecked"})
// public class RackHttpHandler implements Runnable {

//     final HttpRequest req;
//     final RespCallback cb;
//     final RackApplication handler;

//     public RackHttpHandler(HttpRequest req, RespCallback cb, RackApplication handler) {
//         this.req = req;
//         this.cb = cb;
//         this.handler = handler;
//     }

//     public void run() {
//     	System.out.println("RackHttpHandler run");

//         try {
//             Object resp_arr[] = handler.call(buildRequestMap(req));

//             if (resp_arr == null) { // handler return null
//                 cb.run(HttpEncode(404, new HeaderMap(), null));
//             } else {
//                 Map<String, Object> resp = new TreeMap<String, Object>();

//                 resp.put(STATUS  , resp_arr[0]);
//                 resp.put(HEADERS , PersistentArrayMap.create((Map) resp_arr[1]));
//                 resp.put(BODY    , resp_arr[2]);

//                 System.out.println( resp.get(STATUS) );

//                 Object body = resp.get(BODY);

//                 if (!(body instanceof AsyncChannel)) { // hijacked
//                     HeaderMap headers = HeaderMap.camelCase((Map) resp.get(HEADERS));
//                     if (req.version == HTTP_1_0 && req.isKeepAlive) {
//                         headers.put("Connection", "Keep-Alive");
//                     }
//                     cb.run(HttpEncode(getStatus(resp), headers, body));
//                 }
//             }
//         } catch (Throwable e) {
//             cb.run(HttpEncode(500, new HeaderMap(), e.getMessage()));
//             HttpUtils.printError(req.method + " " + req.uri, e);
//         }
//     }
// }

public class RackHandler implements IHandler {
    final ExecutorService execs;
    final RackApplication handler;

    public RackHandler(int thread, RackApplication handler, String prefix, int queueSize) {
        PrefixThreadFactory factory = new PrefixThreadFactory(prefix);
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(queueSize);
        execs = new ThreadPoolExecutor(thread, thread, 0, TimeUnit.MILLISECONDS, queue, factory);
        this.handler = handler;
    }

    public void handle(HttpRequest req, RespCallback cb) {
        try {
            execs.submit(new RackHttpHandler(req, cb, handler));
        } catch (RejectedExecutionException e) {
            HttpUtils.printError("increase :queue-size if this happens often", e);
            cb.run(HttpEncode(503, new HeaderMap(), "Server is overloaded, please try later"));
        }
    }

    public void close() {
        execs.shutdownNow();
    }

    public void handle(AsyncChannel channel, Frame frame) {
        WSHandler task = new WSHandler(channel, frame);

        // messages from the same client are handled orderly
        LinkingRunnable job = new LinkingRunnable(task);
        LinkingRunnable old = channel.serialTask;
        channel.serialTask = job;
        try {
            if (old == null) { // No previous job
                execs.submit(job);
            } else {
                if (!old.next.compareAndSet(null, job)) { // successfully append to previous task
                    // previous message is handled, order is guaranteed.
                    execs.submit(job);
                }
            }
        } catch (RejectedExecutionException e) {
            // TODO notify client if server is overloaded
            HttpUtils.printError("increase :queue-size if this happens often", e);
        }
    }

    public void clientClose(final AsyncChannel channel, final int status) {
        if (channel.closedRan == 0) { // server did not close it first
            // has close handler, execute it in another thread
            if (channel.closeHandler != null) {
                try {
                    // no need to maintain order
                    execs.submit(new Runnable() {
                        public void run() {
                            try {
                                channel.onClose(status);
                            } catch (Exception e) {
                                HttpUtils.printError("on close handler", e);
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    HttpUtils.printError("increase :queue-size if this happens often", e);
                }
            } else {
                // no close handler, mark the connection as closed
                // channel.closedRan = 1;
                // lazySet
                AsyncChannel.unsafe.putOrderedInt(channel, AsyncChannel.closedRanOffset, 1);
            }
        }
    }
}
