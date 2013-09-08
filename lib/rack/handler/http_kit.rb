require 'http_kit/server'

Rack::Handler.register 'http_kit', 'HttpKit::Server'
