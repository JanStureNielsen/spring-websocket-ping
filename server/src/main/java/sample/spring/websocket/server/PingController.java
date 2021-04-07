package sample.spring.websocket.server;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class PingController {
    @MessageMapping("/ping")
    @SendTo("/topic/messages")
    public long send(long nanoTime) throws Exception {
//System.out.println("rec&snd: " + nanoTime);
        return nanoTime;
    }

}
