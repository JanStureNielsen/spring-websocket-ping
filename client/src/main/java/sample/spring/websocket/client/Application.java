package sample.spring.websocket.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.HdrHistogram.Histogram;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
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

            wsConnect(host, port, messages, messagesPerSecond);
        } catch (Exception x) {
            usage();

            x.printStackTrace();
        }
    }

    private static void wsConnect(String host, int port, int messages, int messagesPerSecond) throws InterruptedException, ExecutionException {
        WebSocketClient webSocketClient = webSocketClient(new StandardWebSocketClient(), true);

        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor()));
        stompClient.setDefaultHeartbeat(new long[]{30_000, 30_000});

        String url = String.format("ws://%s:%d/ping", host, port);
        StompHandler stompHandler = new StompHandler();

        StompSession session = stompClient.connectAsync(url, stompHandler).get();
        Histogram histogram = stompHandler.getHistogram();

        System.out.printf("\nWarming up...\n");
        stompHandler.sendAndReceive(session, messages, nanosDelayForRate(messagesPerSecond));
        //printHistogramPercentiles(messages, messagesPerSecond, histogram);

        stompHandler.reset();

        System.out.printf(String.format("\nTesting with %d messages at %d per second...\n", messages, messagesPerSecond));
        stompHandler.sendAndReceive(session, messages, nanosDelayForRate(messagesPerSecond));
        printHistogramPercentiles(messages, messagesPerSecond, histogram);
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
        printHistogramPercentile( 50.00, histogram);
        printHistogramPercentile( 90.00, histogram);
        printHistogramPercentile( 99.00, histogram);
        printHistogramPercentile( 99.90, histogram);
        printHistogramPercentile( 99.99, histogram);
        printHistogramPercentile(100.00, histogram);
        System.out.printf("\n");
    }

    private static void printHistogramPercentile(double percentile, Histogram histogram) {
        System.out.printf(String.format("%8.2f : %10.2f µs\n", percentile, histogram.getValueAtPercentile(percentile) / 1000.0));
    }

    private static void usage() {
        System.out.println("\n\nUsage: websocket-client.jar <host> <port> <messages> <messages-per-second>\n\n");
    }

}
