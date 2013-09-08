require 'http_kit/rack_handler'

module HttpKit
  class Server

    @lock ||= Mutex.new

    def Server.run(app, options = {})
      @lock.synchronize do
        return if @server
        @server = new(app)
        @server.start
      end
    end

    def Server.stop
      @lock.synchronize do
        return unless @server

        @handler.close
        @server.stop
        @handler = nil
        @server  = nil
      end
    end

    def initialize(app, options={})
      options = {
          :host    => "0.0.0.0",
          :port    => 9292,
          :threads => 50,
          :queue   => 1000,
        }.merge(options)

      # @handler   = Java::OrgHttpkitServer::RingHandler.new(options[:threads], app, "prefix-", options[:queue])
      # @handler   = Java::OrgHttpkitServer::RackHandler.new(options[:threads], app, "prefix-", options[:queue])
      @handler   = HttpKit::RackHandler.new(options[:threads], app, "prefix-", options[:queue])
      @http_kit  = Java::OrgHttpkitServer::HttpServer.new(options[:host], options[:port], @handler, 80000, 4000)

      $stdout.printf("HTTP Kit is listening on %s:%s\n", options[:host], options[:port])

      trap("SIGINT") { @http_kit.stop and exit }
    end

    def start
      @http_kit.start
    end

    def stop
      @http_kit.stop
    end

  end
end
