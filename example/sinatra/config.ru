$LOAD_PATH << File.expand_path('../../../lib', __FILE__)

require 'rubygems'
require 'rack'
require 'http_kit'
require 'app'

run Sinatra::Application
