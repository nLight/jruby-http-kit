module Webmachine
  module Adapters
    class Ring < Webmachine::Adapter

      DEFAULT_OPTIONS = {
        :port   => 9292,
        :host   => "0.0.0.0",
        :threads => 50,
        :queue_size  => 1000,
        :worker_prefix => "wm-",
        :max_body_size => 8388608,
        :max_http_line => 4096
      }

      # Start the Rack adapter
      def run
        options = DEFAULT_OPTIONS.merge({
          :port => configuration.port,
          :host => configuration.ip
        }).merge(configuration.adapter_options)

        req_handler = Handler.new(dispatcher)

        @ring_handler = Java::OrgHttpkitServer::RingHandler.new(options[:threads],
                                                                req_handler,
                                                                options[:worker_prefix],
                                                                options[:queue_size])

        @server = Java::OrgHttpkitServer::HttpServer.new(options[:host],
                                                         options[:port].to_i,
                                                         @ring_handler,
                                                         options[:max_body_size],
                                                         options[:max_http_line])

        @server.start()

        $stdout.printf "Webmachine Http kit is listening on %s:%s\n\n",
          options[:host], options[:port]

        Signal.trap("INT") { shutdown }
      end

      def shutdown
        @ring_handler.close()
        @server.stop()
      end


      class Handler
        include Java::ClojureLang::IFn

        attr_reader :dispatcher

        def initialize(dispatcher)
          @dispatcher = dispatcher
        end

        def invoke(request)
          ring_request = Ring::RingRequest.new(request)

          request = Webmachine::Request.new(ring_request.method,
                                            ring_request.url,
                                            ring_request.headers,
                                            ring_request.body)

          response = Webmachine::Response.new

          dispatcher.dispatch(request, response)

          headers = Java::JavaUtil::HashMap.new(response.headers)

          Ring::RingResponse.create(response.code, headers, response.body)
        end
      end

      class RingRequest
        def initialize(request)
          @request = request
        end

        def headers
          Webmachine::Headers.from_cgi(@request.get(Java::ClojureLang::Keyword.intern("headers")))
        end

        def body
          _body = @request.get( Java::ClojureLang::Keyword.intern("body") )
          _body = _body.to_io if _body
        end

        def url
          uri          = @request.get(Java::ClojureLang::Keyword.intern("uri"))
          query_string = @request.get(Java::ClojureLang::Keyword.intern("query-string"))

          URI.parse("#{uri}?#{query_string}")
        end

        def method
          @request.get(Java::ClojureLang::Keyword.intern("request-method")).to_s.delete(':').upcase
        end

        # Map<Object, Object> m = new TreeMap<Object, Object>();
#         m.put(SERVER_PORT, req.serverPort);
#         m.put(SERVER_NAME, req.serverName);
#         m.put(REMOTE_ADDR, req.getRemoteAddr());
#         m.put(URI, req.uri);
#         m.put(SCHEME, HTTP); // only http is supported
#         m.put(ASYC_CHANNEL, req.channel);
#         m.put(WEBSOCKET, req.isWebSocket);
#         m.put(REQUEST_METHOD, req.method.KEY);

#         // key is already lower cased, required by ring spec
#         m.put(HEADERS, PersistentArrayMap.create(req.headers));
#         m.put(CONTENT_TYPE, req.contentType);
#         m.put(CONTENT_LENGTH, req.contentLength);
#         m.put(CHARACTER_ENCODING, req.charset);
#         m.put(BODY, req.getBody());
#         return PersistentArrayMap.create(m);

      end

      class RingResponse
        def self.create(code, headers, body)
          ring_response = [
            Java::ClojureLang::Keyword.intern("status")  , code,
            Java::ClojureLang::Keyword.intern("headers") , headers,
            Java::ClojureLang::Keyword.intern("body")    , body
          ]

          return Java::clojure::lang::PersistentHashMap.create(ring_response.to_java)
        end
      end

    end
  end
end
