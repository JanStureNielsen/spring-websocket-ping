package sample.spring.websocket.client;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicLong;

import org.HdrHistogram.Histogram;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

public class StompHandler extends StompSessionHandlerAdapter {
    private final Histogram histogram = new Histogram(3);
    private AtomicLong receiveCount = new AtomicLong();

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
        System.out.println("handle frame");
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        System.out.println("handle transport error");
    }
    
    public Histogram getHistogram() {
        return histogram;
    }

    public void sendAndReceive(StompSession session, long messages, long intervalNanos) {
        for (long sendAtNanoTime = System.nanoTime(); receiveCount.get() < messages; /*connection.receive()*/) {
            if (System.nanoTime() >= sendAtNanoTime) {
                session.send("/app/ping", sendAtNanoTime);
//System.out.println("snd: " + sendAtNanoTime);
                sendAtNanoTime += intervalNanos;
            }
        }
    }

    public void reset() {
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
