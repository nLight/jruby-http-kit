$LOAD_PATH << 'lib'

require 'http_kit'

class HelloWorldHandler
  include Java::ClojureLang::IFn

  def invoke(request)
    headers = ["Content-Type", "text/html"].to_java(:string)
    headers = Java::clojure::lang::PersistentHashMap.create(headers)

    resp = [
          Java::ClojureLang::Keyword.intern("status")  , 200,
          Java::ClojureLang::Keyword.intern("headers") , headers,
          Java::ClojureLang::Keyword.intern("body")    , "hello HTTP! #{request}"
        ].to_java

    return Java::clojure::lang::PersistentHashMap.create(resp)
  end
end

req_handler  = HelloWorldHandler.new()
ring_handler = Java::OrgHttpkitServer::RingHandler.new(100, req_handler, "prefix-", 1000)
server       = Java::OrgHttpkitServer::HttpServer.new("0.0.0.0", 8089, ring_handler, 80000, 4000)

server.start()

Signal.trap("TERM") do
  ring_handler.close()
  server.stop()
end
