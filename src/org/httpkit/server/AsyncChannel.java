package org.httpkit.server;

import org.httpkit.server.IRubyHandler;
import org.httpkit.DynamicBytes;
import org.httpkit.HeaderMap;
import org.httpkit.HttpVersion;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;

import static org.httpkit.HttpUtils.*;
import static org.httpkit.server.ClojureRing.*;
import static org.httpkit.server.WSDecoder.*;

@SuppressWarnings({"unchecked"})
public class AsyncChannel {
    static final Unsafe unsafe;
    static final long closedRanOffset;
    static final long closeHandlerOffset;
    static final long receiveHandlerOffset;
    static final long headerSentOffset;

    private final SelectionKey key;
    private final HttpServer server;

    private HttpRequest request;     // package private, for http 1.0 keep-alive

    volatile int closedRan = 0; // 0 => false, 1 => run
    // streaming
    private volatile int isHeaderSent = 0;

    private volatile IRubyHandler receiveHandler = null;
    volatile IRubyHandler closeHandler = null;

    static {
        try {
            // Unsafe instead of AtomicReference to save few bytes of RAM per connection
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);

            closedRanOffset = unsafe.objectFieldOffset(
                    AsyncChannel.class.getDeclaredField("closedRan"));
            closeHandlerOffset = unsafe.objectFieldOffset(
                    AsyncChannel.class.getDeclaredField("closeHandler"));
            receiveHandlerOffset = unsafe.objectFieldOffset(
                    AsyncChannel.class.getDeclaredField("receiveHandler"));
            headerSentOffset = unsafe.objectFieldOffset(
                    AsyncChannel.class.getDeclaredField("isHeaderSent"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    // messages sent from a WebSocket client should be handled orderly by server
    // Changed from a Single Thread(IO event thread), no volatile needed
    LinkingRunnable serialTask;

    public AsyncChannel(SelectionKey key, HttpServer server) {
        this.key = key;
        this.server = server;
    }

    public void reset(HttpRequest request) {
        this.request = request;
        serialTask = null;
        unsafe.putOrderedInt(this, closedRanOffset, 0); // lazySet to false
        unsafe.putOrderedInt(this, headerSentOffset, 0);

        unsafe.putOrderedObject(this, closeHandlerOffset, null); // lazySet to null
        unsafe.putOrderedObject(this, receiveHandlerOffset, null); // lazySet to null
    }

    private static final byte[] finalChunkBytes = "0\r\n\r\n".getBytes();
    private static final byte[] newLineBytes = "\r\n".getBytes();

    private static ByteBuffer chunkSize(int size) {
        String s = Integer.toHexString(size) + "\r\n";
        return ByteBuffer.wrap(s.getBytes());
    }

    private void firstWrite(Object data, boolean close) throws IOException {
        ByteBuffer buffers[];
        int status = 200;
        Object body = data;
        HeaderMap headers;
        if (data instanceof Map) {
            Map<String, Object> resp = (Map<String, Object>) data;
            headers = HeaderMap.camelCase((Map) resp.get(HEADERS));
            status = getStatus(resp);
            body = resp.get(BODY);
        } else {
            headers = new HeaderMap();
        }

        if (headers.isEmpty()) { // default 200 and text/html
            headers.put("Content-Type", "text/html; charset=utf-8");
        }

        if (request.isKeepAlive && request.version == HttpVersion.HTTP_1_0) {
            headers.put("Connection", "Keep-Alive");
        }

        if (close) { // normal response, Content-Length. Every http client understand it
            buffers = HttpEncode(status, headers, body);
        } else {
            headers.put("Transfer-Encoding", "chunked"); // first chunk
            ByteBuffer[] bb = HttpEncode(status, headers, body);
            if (body == null) {
                buffers = bb;
            } else {
                buffers = new ByteBuffer[]{bb[0], chunkSize(bb[1].remaining()), bb[1],
                        ByteBuffer.wrap(newLineBytes)};
            }
        }
        if (close) {
            onClose(0);
        }
        server.tryWrite(key, buffers);
    }


    private void writeChunk(Object body, boolean close) throws IOException {
        if (body instanceof Map) { // only get body if a map
            body = ((Map<String, Object>) body).get(BODY);
        }
        if (body != null) { // null is ignored
            ByteBuffer buffers[];
            ByteBuffer t = bodyBuffer(body);
            if (t.hasRemaining()) {
                ByteBuffer size = chunkSize(t.remaining());
                buffers = new ByteBuffer[]{size, t, ByteBuffer.wrap(newLineBytes)};
                server.tryWrite(key, buffers);
            }
        }
        if (close) {
            serverClose(0);
        }
    }

    public void setReceiveHandler(IRubyHandler fn) {
        if (!unsafe.compareAndSwapObject(this, receiveHandlerOffset, null, fn)) {
            throw new IllegalStateException("receive handler exist: " + receiveHandler);
        }
    }

    public void messageReceived(final Object mesg) {
        IRubyHandler f = receiveHandler;
        if (f != null) {
            f.call(mesg); // byte[] or String
        }
    }

    public void sendHandshake(Map<String, Object> headers) {
        HeaderMap map = HeaderMap.camelCase(headers);
        server.tryWrite(key, HttpEncode(101, map, null));
    }

    public void setCloseHandler(IRubyHandler fn) {
        if (!unsafe.compareAndSwapObject(this, closeHandlerOffset, null, fn)) { // only once
            throw new IllegalStateException("close handler exist: " + closeHandler);
        }
        if (closedRan == 1) { // no handler, but already closed
            fn.call(K_UNKNOWN);
        }
    }

    public void onClose(int status) {
        if (unsafe.compareAndSwapInt(this, closedRanOffset, 0, 1)) {
            IRubyHandler f = closeHandler;
            if (f != null) {
                f.call(readable(status));
            }
        }
    }

    // also sent CloseFrame a final Chunk
    public boolean serverClose(int status) {
        if (!unsafe.compareAndSwapInt(this, closedRanOffset, 0, 1)) {
            return false; // already closed
        }
        if (isWebSocket()) {
            server.tryWrite(key, WsEncode(OPCODE_CLOSE, ByteBuffer.allocate(2)
                    .putShort((short) status).array()));
        } else {
            server.tryWrite(key, ByteBuffer.wrap(finalChunkBytes));
        }
        IRubyHandler f = closeHandler;
        if (f != null) {
            f.call(readable(0)); // server close is 0
        }
        return true;
    }

    public boolean send(Object data, boolean close) throws IOException {
        if (closedRan == 1) {
            return false;
        }

        if (isWebSocket()) {
            if (data instanceof Map) { // only get the :body if map
                Object tmp = ((Map<String, Object>) data).get(BODY);
                if (tmp != null) { // save contains(BODY) && get(BODY)
                    data = tmp;
                }
            }

            if (data instanceof String) { // null is not allowed
                server.tryWrite(key, WsEncode(OPCODE_TEXT, ((String) data).getBytes(UTF_8)));
            } else if (data instanceof byte[]) {
                server.tryWrite(key, WsEncode(OPCODE_BINARY, (byte[]) data));
            } else if (data instanceof InputStream) {
                DynamicBytes bytes = readAll((InputStream) data);
                server.tryWrite(key, WsEncode(OPCODE_BINARY, bytes.get(), bytes.length()));
            } else if (data != null) { // ignore null
                String mesg = "send! called with data: " + data.toString() +
                        "(" + data.getClass() + "), but only string, byte[], InputStream expected";
                throw new IllegalArgumentException(mesg);
            }

            if (close) {
                serverClose(1000);
            }
        } else {
            if (isHeaderSent == 1) {  // HTTP Streaming
                writeChunk(data, close);
            } else {
                isHeaderSent = 1;
                firstWrite(data, close);
            }
        }
        return true;
    }

    public String toString() {
        Socket s = ((SocketChannel) key.channel()).socket();
        return s.getLocalSocketAddress() + "<->" + s.getRemoteSocketAddress();
    }

    public boolean isWebSocket() {
        return key.attachment() instanceof WsAtta;
    }

    public boolean isClosed() {
        return closedRan == 1;
    }

    static String K_BY_SERVER = "server-close";
    static String K_CLIENT_CLOSED = "client-close";

    // http://datatracker.ietf.org/doc/rfc6455/?include_text=1
    // 7.4.1. Defined Status Codes
    static String K_WS_1000 = "normal";
    static String K_WS_1001 = "going-away";
    static String K_WS_1002 = "protocol-error";
    static String K_WS_1003 = "unsupported";
    static String K_UNKNOWN = "unknown";

    private static String readable(int status) {
        switch (status) {
            case 0:
                return K_BY_SERVER;
            case -1:
                return K_CLIENT_CLOSED;
            case 1000:
                return K_WS_1000;
            case 1001:
                return K_WS_1001;
            case 1002:
                return K_WS_1002;
            case 1003:
                return K_WS_1003;
            default:
                return K_UNKNOWN;
        }
    }
}
