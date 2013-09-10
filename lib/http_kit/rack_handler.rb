require 'stringio'
require 'rack/rewindable_input'

module HttpKit

  java_import java.nio.ByteBuffer

  java_import org.httpkit.HttpUtils
  java_import org.httpkit.HeaderMap
  java_import org.httpkit.HttpVersion
  java_import org.httpkit.DynamicBytes
  java_import org.httpkit.server.RackHttpHandler
  java_import org.httpkit.server.RingHandler
  java_import org.httpkit.server.HttpRequest
  java_import org.httpkit.server.RespCallback
  java_import org.httpkit.server.AsyncChannel

  class HttpHandler < RackHttpHandler
    field_reader :req
    field_reader :cb
    field_reader :handler

    def http_request_to_rack(req)
      env = {}

      body = req.getBody() || Rack::RewindableInput.new(StringIO.new(""))

      env["SERVER_SOFTWARE"]      = "HTTP Kit"
      env["SERVER_NAME"]          = req.serverName
      env["rack.input"]           = body
      env["rack.version"]         = [1, 0]
      env["rack.errors"]          = $stderr
      env["rack.multithread"]     = true
      env["rack.multiprocess"]    = false
      env["rack.run_once"]        = false
      env["REQUEST_METHOD"]       = req.method.KEY.to_s.gsub(':', '').upcase
      env["REQUEST_PATH"]         = req.uri
      env["PATH_INFO"]            = req.uri
      env["REQUEST_URI"]          = "http://%s:%s%s" % [req.serverName, req.serverPort.to_s, req.uri]
      env["HTTP_VERSION"]         = "HTTP/1.1"
      env["HTTP_HOST"]            = "%s:%s" % [req.serverName, req.serverPort.to_s]
      env["HTTP_ACCEPT"]          = "*/*"
      env["SERVER_PORT"]          = req.serverPort.to_s
      env["QUERY_STRING"]         = req.queryString || ""
      env["SERVER_PROTOCOL"]      = "HTTP/1.1"
      env["rack.url_scheme"]      = "http" # only http is supported
      env["REMOTE_ADDR"]          = req.getRemoteAddr()

      env["CONTENT_TYPE"]         = req.contentType || ""
      env["CONTENT_LENGTH"]       = req.contentLength.to_s

      # // m.put(URI, req.uri);
      # // m.put(ASYC_CHANNEL, req.channel);
      # // m.put(WEBSOCKET, req.isWebSocket);

      # // // key is already lower cased, required by ring spec
      # // m.put(HEADERS, PersistentArrayMap.create(req.headers));
      # // m.put(CONTENT_TYPE, req.contentType);
      # // m.put(CONTENT_LENGTH, req.contentLength);
      # // m.put(CHARACTER_ENCODING, req.charset);
      # // m.put(BODY, req.getBody());

      env
    end

    def run
      begin
        resp = handler.call(http_request_to_rack(req))
        if ! resp
          cb.run(HttpUtils.HttpEncode(404, HeaderMap.new(), nil));
        else
          status, headers, body = resp

          if ! body.is_a?(AsyncChannel)
            b = DynamicBytes.new(512)

            body.each do |chunk|
              b.append(chunk.to_s)
            end

            body = ByteBuffer.wrap(b.get(), 0, b.length())

            headers = HeaderMap.camelCase(headers)

            # In HTTP 1.1, all connections are considered persistent unless declared otherwise.
            if req.version == HttpVersion::HTTP_1_0 && req.isKeepAlive
              headers.put("Connection", "Keep-Alive")
            end
            cb.run(HttpUtils.HttpEncode(status.to_i, headers, body))
          end
        end
      rescue => e
        cb.run(HttpUtils.HttpEncode(500, HeaderMap.new(), e.message))
        HttpUtils.printError(req.method + " " + req.uri, e);
      end
    end
  end

  class RackHandler < RingHandler

    field_reader :execs
    field_reader :handler

    def handle(*args)
      if args[0].is_a?(HttpRequest) && args[1].is_a?(RespCallback)
        handle_http(args[0], args[1])
      else
        handle_async(args[0], args[1])
      end
    end

    # void handle(HttpRequest request, RespCallback callback)
    def handle_http(request, callback)
      begin
        execs.submit( HttpKit::HttpHandler.new(request, callback, handler) )
      rescue java.util.concurrent.RejectedExecutionException => e
        HttpUtils.printError("increase :queue-size if this happens often", e);
        callback.run(HttpUtils.HttpEncode(503, HeaderMap.new(), "Server is overloaded, please try later"));
      end
    end

    # void handle(AsyncChannel channel, Frame frame)
    def handle_async(channel, frame)

    end

    # java_signature %Q{ @override public void clientClose(AsyncChannel channel, int status) }
    def client_close(channel, status)

    end


    # java_signature %Q{ @override void close() }
    def close

    end
  end

end
