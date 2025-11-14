package sample.spring.websocket.client;

import java.lang.reflect.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.HdrHistogram.Histogram;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import sample.spring.websocket.client.Application.StompClient;

public class StompHandler extends StompSessionHandlerAdapter {
    private final Histogram histogram = new Histogram(3);
    private final AtomicLong receiveCount = new AtomicLong();
    private final StompClient stompClient;

    public StompHandler(StompClient stompClient) {
        this.stompClient = stompClient;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        subscribeTopic("/topic/messages", session);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        System.out.println("handle payload type");
        return Long.class;
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        System.out.println("handle exception");
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        System.out.println("handle frame: " + payload);
    }

    private final AtomicLong transportErrors = new AtomicLong();
    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        transportErrors.incrementAndGet();
        if (!session.isConnected()) {
            stompClient.reestablishConnection();
            System.err.println("jsn: transport error: " + transportErrors.get());
        }
    }

    public void send(StompSession session, long messages, long intervalNanos) {
        for (var sendAtNanoTime = System.nanoTime(); receiveCount.get() < messages;) {
            if (System.nanoTime() >= sendAtNanoTime) {
                session.send("/app/ping", sendAtNanoTime);
                sendAtNanoTime += intervalNanos;
            }
        }
    }

    public Histogram getHistogram() {
        return histogram;
    }

    public void resetCounters() {
        histogram.reset();
        receiveCount.set(0);
    }

    private void subscribeTopic(String topic, StompSession session) {
        session.subscribe(topic, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Long.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                histogram.recordValue(System.nanoTime() - (long)payload);

//System.out.println("rec: " + payload);

                receiveCount.incrementAndGet();
            }
        });
    }

}
