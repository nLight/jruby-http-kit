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
