require 'webmachine/adapter'
require 'webmachine/chunked_body'

module Webmachine
  module Adapters
    class Ring < Webmachine::Adapter

      java_import Java::JavaNio::ByteBuffer

      java_import Java::ClojureLang::Keyword
      java_import Java::ClojureLang::PersistentHashMap

      java_import Java::OrgHttpkit::DynamicBytes
      java_import Java::OrgHttpkit::HttpUtils
      java_import Java::OrgHttpkitServer::RingHandler
      java_import Java::OrgHttpkitServer::HttpServer


      STATUS       = Keyword.intern("status")
      HEADERS      = Keyword.intern("headers")
      BODY         = Keyword.intern("body")
      REQUEST_URI  = Keyword.intern("uri")
      QUERY_STRING = Keyword.intern("query-string")
      METHOD       = Keyword.intern("request-method")

      DEFAULT_OPTIONS = {
        :port          => 9292,
        :host          => "0.0.0.0",
        :threads       => 50,
        :queue_size    => 1000,
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

        @ring_handler = RingHandler.new(options[:threads],
                                        req_handler,
                                        options[:worker_prefix],
                                        options[:queue_size])

        @server = HttpServer.new(options[:host],
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

          Ring::RingResponse.from_webmachine(response)
        end
      end

      class RingRequest
        def initialize(request)
          @request = request
        end

        def headers
          ruby_headers = {}

          ring_headers = @request.get(Ring::HEADERS)
          ring_headers.iterator().each do |h|
            ruby_headers[h[0]] = h[1]
          end

          Webmachine::Headers[ruby_headers]
        end

        def body
          _body = @request.get( Ring::BODY )
          _body.to_io.read if _body
        end

        def url
          uri          = @request.get(Ring::REQUEST_URI)
          query_string = @request.get(Ring::QUERY_STRING)
          uri << "?#{query_string}" if query_string && query_string != ""
          URI.parse(uri)
        end

        def method
          @request.get(Ring::METHOD).to_s.delete(':').upcase
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
        def self.from_webmachine(response)
          headers = response.headers

          # HTTP-Kit sets its own Content-Length
          headers.delete("Content-Length")

          headers.each do |k, v|
            headers[k] = v.join(", ") if v.is_a? Array
          end

          response_body = if response.body.respond_to?(:call)
                            Webmachine::ChunkedBody.new([response.body.call])
                          elsif response.body.respond_to?(:each)
                            # This might be an IOEncoder with a Content-Length, which shouldn't be chunked.
                            if response.headers["Transfer-Encoding"] == "chunked"
                              Webmachine::ChunkedBody.new(response.body)
                            else
                              response.body
                            end
                          elsif response.body.is_a? IO
                            response.body.to_outputstream
                          else
                            response.body
                          end

          if response_body.respond_to?(:each)
            ring_body = ""
            response_body.each do |chunk|
              ring_body << chunk.to_s
            end
          else
            ring_body = response_body
          end

          headers = Java::JavaUtil::HashMap.new(headers)

          ring_response = [
            Ring::STATUS  , response.code,
            Ring::HEADERS , headers,
            Ring::BODY    , ring_body
          ]

          return PersistentHashMap.create(ring_response.to_java)
        end
      end

    end
  end
end
