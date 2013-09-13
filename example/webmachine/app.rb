$LOAD_PATH << File.expand_path('../../../lib', __FILE__)

require 'http_kit'
require 'webmachine'
require 'webmachine/adapters/ring'

class TestResource < Webmachine::Resource
  def allowed_methods
    %W{ GET HEAD OPTIONS POST PUT }
  end

  def content_types_provided
    [['text/html', :render]]
  end

  def render
    "Hello!"
  end

  def process_post
    true
  end
end

Service = Webmachine::Application.new do |app|
  app.routes do
    add ['*' ], TestResource
  end

  app.configure do |config|
    config.ip      = "0.0.0.0"
    config.port    = 9292
    config.adapter = :Ring
  end
end

Service.adapter.run
