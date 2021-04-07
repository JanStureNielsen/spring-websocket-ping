# Spring WebSocket ping test

Send a `long` from `System.nanoTime()` to the ping server which returns the value immediately. The client can send
any number of messages at a particular rate, collecting the results in a histogram.

## Building

```
mvn clean install
```

## Running

Start the server on a port:

```
java -Dserver.port=7654 server/target/*.jar
```

Then start the client providing the host and port, number of messages and rate:

```
java -jar client/target/*.jar localhost 7654 40000 1000
```

### Results

```
Warming up...

Results (n = 40000 @ 1000 per second)

   50.00 :     379.14 µs
   90.00 :     412.42 µs
   99.00 :     445.95 µs
   99.90 :     507.14 µs
   99.99 :    2340.86 µs
  100.00 :    3420.16 µs
```
