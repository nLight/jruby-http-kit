# coding: utf-8
lib = File.expand_path('../lib', __FILE__)
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'http_kit/version'

Gem::Specification.new do |spec|
  spec.name          = "jruby-http-kit"
  spec.version       = HttpKit::VERSION
  spec.authors       = ["Dmitriy Rozhkov"]
  spec.email         = ["rojkov.dmitry@gmail.com"]
  spec.description   = %q{JRuby wrapper for Clojure HTTP Kit library.}
  spec.summary       = %q{JRuby wrapper for Clojure HTTP Kit library.}
  spec.homepage      = "https://github.com/nLight/jruby-http-kit"
  spec.license       = ""

  spec.files         = `git ls-files`.split($/)
  spec.executables   = spec.files.grep(%r{^bin/}) { |f| File.basename(f) }
  spec.test_files    = spec.files.grep(%r{^(test|spec|features)/})
  spec.require_paths = ["lib"]

  spec.add_development_dependency "bundler", "~> 1.3"
  spec.add_development_dependency "rake"
  spec.add_development_dependency "rspec"
  spec.add_development_dependency "webmachine", "~> 1.2.1"
end
