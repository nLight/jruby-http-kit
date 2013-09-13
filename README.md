jruby-http-kit
==============

JRuby wrapper for Clojure HTTP Kit library. Just a  proof of a concept for now.

```
jruby -s ring_example.rb

curl -v 0.0.0.0:8089
```

MacBook Pro Retina 13" (2.5 GHz i5, 8GB RAM)

server runs in irb :)

```
λ ~/ wrk -t4 -c400 -d30 http://0.0.0.0:8089
Running 30s test @ http://0.0.0.0:8089
  4 threads and 400 connections

  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    10.09ms    6.96ms  67.70ms   81.66%
    Req/Sec     6.84k     1.99k    9.00k    82.93%
  1000916 requests in 30.00s, 142.48MB read
  Socket errors: connect 0, read 756, write 0, timeout 67
Requests/sec:  33367.54
Transfer/sec:      4.75MB
```

## Installation

Add this line to your application's Gemfile:

    gem 'jruby-http-kit'

And then execute:

    $ bundle

Or install it yourself as:

    $ gem install jruby-http-kit

## Contributing

1. Fork it
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request


## License

### JRuby HTTP Kit wrapper

Copyright (c) 2013 Dmitriy Rozhkov


### HTTP Kit

Copyright &copy; 2012-2013 [Feng Shen](http://shenfeng.me/). Distributed under the [Apache License Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).


### Clojure

Copyright (c) Rich Hickey. All rights reserved.

The use and distribution terms for this software are covered by the

Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)

