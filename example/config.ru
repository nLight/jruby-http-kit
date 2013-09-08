$LOAD_PATH << File.expand_path('../../lib', __FILE__)

require 'rubygems'
require 'rack'
require 'http_kit'
require 'hello_world'

run HelloWorld.new
