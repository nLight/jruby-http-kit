require 'http_kit/rack_handler'

module HttpKit
  class Server

    @lock ||= Mutex.new

    def Server.run(app, options = {})
      @lock.synchronize do
        return if @server
        @server = new
        @server.run(app, options)

        @server
      end
    end

    def Server.stop
      @lock.synchronize do
        return unless @server

        @server.stop
        @handler = nil
      end
    end

    def run(app, options={})
      options = {
          :host    => "0.0.0.0",
          :port    => 9292,
          :threads => 50,
          :queue   => 1000,
        }.merge(options)

      @handler  = HttpKit::RackHandler.new(options[:threads], app, "prefix-", options[:queue])
      @http_kit = Java::OrgHttpkitServer::HttpServer.new(options[:host], options[:port], @handler, 80000, 4000)

      @http_kit.start

      $stdout.printf("HTTP Kit is listening on %s:%s\n", options[:host], options[:port])

      trap("SIGINT") {
        @http_kit.stop
        exit
      }
    end

    def start
      @http_kit.start
    end

    def stop
      return unless @http_kit
      $stdout.print "Stopping Http kit..." unless @options[:quiet]
      @http_kit.stop
      $stdout.puts "done." unless @options[:quiet]
    end

    alias_method :shutdown, :stop

  end
end
