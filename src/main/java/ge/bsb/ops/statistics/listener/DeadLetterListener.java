package ge.bsb.ops.statistics.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    @RabbitListener(queues = "${app.rabbitmq.dlq}")
    public void listen(String message) {
        log.error("Dead letter message received: {}", message);
    }
}