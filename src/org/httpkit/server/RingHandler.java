package org.httpkit.server;

import org.httpkit.HeaderMap;
import org.httpkit.HttpUtils;
import org.httpkit.PrefixThreadFactory;
import org.httpkit.server.IRubyHandler;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.httpkit.HttpUtils.HttpEncode;
import static org.httpkit.HttpVersion.HTTP_1_0;
import static org.httpkit.server.ClojureRing.*;
import static org.httpkit.server.Frame.TextFrame;

@SuppressWarnings({"rawtypes"})
class ClojureRing {

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
        // ring spec
        Map<Object, Object> m = new TreeMap<Object, Object>();
        m.put(SERVER_PORT, req.serverPort);
        m.put(SERVER_NAME, req.serverName);
        m.put(REMOTE_ADDR, req.getRemoteAddr());
        m.put(URI, req.uri);
        m.put(QUERY_STRING, req.queryString);
        m.put(SCHEME, HTTP); // only http is supported
        m.put(ASYC_CHANNEL, req.channel);
        m.put(WEBSOCKET, req.isWebSocket);
        m.put(REQUEST_METHOD, req.method.KEY);

        // key is already lower cased, required by ring spec
        m.put(HEADERS, req.headers);
        m.put(CONTENT_TYPE, req.contentType);
        m.put(CONTENT_LENGTH, req.contentLength);
        m.put(CHARACTER_ENCODING, req.charset);
        m.put(BODY, req.getBody());

        return m;
    }
}


@SuppressWarnings({"rawtypes", "unchecked"})
class HttpHandler implements Runnable {

    final HttpRequest req;
    final RespCallback cb;
    final IRubyHandler handler;

    public HttpHandler(HttpRequest req, RespCallback cb, IRubyHandler handler) {
        this.req = req;
        this.cb = cb;
        this.handler = handler;
    }

    public void run() {
        try {
            Map resp = (Map) handler.call(buildRequestMap(req));
            if (resp == null) { // handler return null
                cb.run(HttpEncode(404, new HeaderMap(), null));
            } else {
                Object body = resp.get(BODY);
                if (!(body instanceof AsyncChannel)) { // hijacked
                    HeaderMap headers = HeaderMap.camelCase((Map) resp.get(HEADERS));
                    if (req.version == HTTP_1_0 && req.isKeepAlive) {
                        headers.put("Connection", "Keep-Alive");
                    }
                    cb.run(HttpEncode(getStatus(resp), headers, body));
                }
            }
        } catch (Throwable e) {
            cb.run(HttpEncode(500, new HeaderMap(), e.getMessage()));
            HttpUtils.printError(req.method + " " + req.uri, e);
        }
    }
}

class LinkingRunnable implements Runnable {
    private final Runnable impl;
    AtomicReference<LinkingRunnable> next = new AtomicReference<LinkingRunnable>(null);

    public LinkingRunnable(Runnable r) {
        this.impl = r;
    }

    public void run() {
        impl.run();
        if (!next.compareAndSet(null, this)) { // has more job to run
            next.get().run();
        }
    }
}

class WSHandler implements Runnable {
    private Frame frame;
    private AsyncChannel channel;

    protected WSHandler(AsyncChannel channel, Frame frame) {
        this.channel = channel;
        this.frame = frame;
    }

    @Override
    public void run() {
        try {
            if (frame instanceof TextFrame) {
                channel.messageReceived(((TextFrame) frame).getText());
            } else {
                channel.messageReceived(frame.data);
            }
        } catch (Throwable e) {
            HttpUtils.printError("handle websocket frame " + frame, e);
        }
    }
}

public class RingHandler implements IHandler {
    final ExecutorService execs;
    final IRubyHandler handler;

    public RingHandler(int thread, IRubyHandler handler, String prefix, int queueSize) {
        PrefixThreadFactory factory = new PrefixThreadFactory(prefix);
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(queueSize);
        execs = new ThreadPoolExecutor(thread, thread, 0, TimeUnit.MILLISECONDS, queue, factory);
        this.handler = handler;
    }

    public void handle(HttpRequest req, RespCallback cb) {
        try {
            execs.submit(new HttpHandler(req, cb, handler));
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
