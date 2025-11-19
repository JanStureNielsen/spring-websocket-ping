package sample.spring.websocket.client;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import sample.spring.websocket.client.Application.StompClient.ExceptionEvent;
import sample.spring.websocket.client.Application.StompClient.PongEvent;
import sample.spring.websocket.client.Application.StompClient.TransportErrorEvent;

public class StompHandler extends StompSessionHandlerAdapter {
    private final BlockingQueue<Object> queue;

    public StompHandler(BlockingQueue<Object> queue) {
        this.queue = queue;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        session.subscribe("/topic/messages", this);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return Long.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        queue.add(new PongEvent(System.nanoTime() - (long)payload));
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable cause) {
        queue.add(new ExceptionEvent(session, command, headers, payload, cause));
    }

    @Override
    public void handleTransportError(StompSession session, Throwable cause) {
        queue.add(new TransportErrorEvent(session, cause));
    }

}
