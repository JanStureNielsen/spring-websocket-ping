package sample.spring.websocket.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.HdrHistogram.Histogram;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

public class Application {
    public static void main(String args[]) throws Exception {
        try {
            String host           = args[0];
            int port              = Integer.parseInt(args[1]);
            int messages          = Integer.parseInt(args[2]);
            int messagesPerSecond = Integer.parseInt(args[3]);

            var stompClient = new StompClient();

            String url = String.format("ws://%s:%d/ping", host, port);

            stompClient.connect(url);

            System.out.printf("\nSleeping after connecting...\n");
            sleep(Duration.ofSeconds(10));

            System.out.printf("\nWarming up...\n");
            stompClient.send(messages, nanosDelayForRate(messagesPerSecond));
            //printHistogramPercentiles(messages, messagesPerSecond, histogram);

            stompClient.resetCounters();

            System.out.printf(String.format("\nTesting with %d messages at %d per second...\n", messages, messagesPerSecond));
            stompClient.send(messages, nanosDelayForRate(messagesPerSecond));
            printHistogramPercentiles(messages, messagesPerSecond, stompClient.getHistogram());

        } catch (Exception x) {
            usage();
            x.printStackTrace();
        }
    }

    static class StompClient {
        private final AtomicBoolean reconnecting = new AtomicBoolean(false);
        private final WebSocketStompClient wsStompClient;

        private String url;
        private StompHandler stompHandler;
        private volatile StompSession session;

        private final BlockingQueue<Object> queue;
        private final List<Object> events;

        private final Histogram histogram = new Histogram(3);
        private final AtomicLong receiveCount = new AtomicLong(0);

        public StompClient() {
            wsStompClient = new WebSocketStompClient(webSocketClient(new StandardWebSocketClient(), true));
            wsStompClient.setMessageConverter(new MappingJackson2MessageConverter());
            wsStompClient.setTaskScheduler(new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor()));
            wsStompClient.setDefaultHeartbeat(new long[]{30_000, 30_000});

            this.queue = new ArrayBlockingQueue<>(1000);
            this.events = new ArrayList<>();
        }

        public void connect(String url) {
            try {
                this.url = url;
                this.stompHandler = new StompHandler(queue);
                this.session = wsStompClient.connectAsync(url, stompHandler).get();
            } catch (InterruptedException | ExecutionException x) {
                reconnect();
            }
        }

        private final AtomicLong reconnects = new AtomicLong();
        private final ReentrantLock reconnectLock = new ReentrantLock();

        public void _reconnect(TransportErrorEvent error) {
            reconnects.incrementAndGet();

            while (!this.session.isConnected()) {
                try {
                    Thread.sleep(Duration.ofSeconds(2));
                    this.session = wsStompClient.connectAsync(url, stompHandler).get();
                    //connected = true;
                    System.out.println("jsn: reconnected! notifications: " + reconnects.get());
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (/*Execution*/Exception x) {
                    System.err.println("jsn: reconnect failed -- retrying");
                }
            }
        }

        public void send(long messages, long intervalNanos) {
            for (var sendAtNanoTime = System.nanoTime(); receiveCount.get() < messages; _receive()) {
                if (System.nanoTime() >= sendAtNanoTime) {
                    session.send("/app/ping", sendAtNanoTime);
                    sendAtNanoTime += intervalNanos;
                }
            }
        }

        public void resetCounters() {
            histogram.reset();
            receiveCount.set(0);
        }

        public Histogram getHistogram() {
            return histogram;
        }

        private void _receive() {
            events.clear();
            if (0 < queue.drainTo(events)) {
                for (var event : events) {
                    switch (event) {
                        case PongEvent p -> _update(p);
                        case TransportErrorEvent t -> _transportError(t);
                        case ExceptionEvent e -> _exception(e);
                        default -> {System.err.println("jsn: ignored event: " + event);}
                    }
                }
                events.clear();
            }
        }

        private void _update(PongEvent pong) {
            histogram.recordValue(pong.time());
            receiveCount.incrementAndGet();
        }

        private void _transportError(TransportErrorEvent error) {
            _reconnect(error);
        }

        private void _exception(ExceptionEvent error) {
            System.err.println("jsn: exception event! " + error);
        }

        record ExceptionEvent(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable cause) {
        }

        record TransportErrorEvent(StompSession session, Throwable cause) {
        }

        record PongEvent(long time) {
        }

    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (Exception ignored) {
        }
    }

    private static WebSocketClient webSocketClient(WebSocketClient webSocketClient, boolean sockJs) {
        if (sockJs) {
            List<Transport> transports = new ArrayList<>(1);
            transports.add(new WebSocketTransport(webSocketClient));

            return new SockJsClient(transports);
        }

        return webSocketClient;
    }

    private static long nanosDelayForRate(long rate) {
        return 1_000_000_000 / rate;
    }

    private static void printHistogramPercentiles(long messages, long messagesPerSecond, Histogram histogram) {
        System.out.printf("\nResults (n = %d @ %d per second)\n\n", messages, messagesPerSecond);
        for (var percentage : List.of(50.00, 90.00, 99.00, 99.90, 99.99, 100.00)) {
            System.out.printf("%8.2f : %10.2f µs\n", percentage, histogram.getValueAtPercentile(percentage) / 1000.0);
        }
        System.out.printf("\n");
    }

    private static void usage() {
        System.out.println("\n\nUsage: websocket-client.jar <host> <port> <messages> <messages-per-second>\n\n");
    }

}
