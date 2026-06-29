package ge.bsb.ops.statistics.listener;

import com.rabbitmq.client.Channel;
import ge.bsb.ops.statistics.model.DeadLetter;
import ge.bsb.ops.statistics.repository.DeadLetterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterListener(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    @RabbitListener(queues = "${app.rabbitmq.dlq}")
    public void listen(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            deadLetterRepository.save(toDeadLetter(message));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // Keep the dead letter in the DLQ if we cannot persist it (e.g. DB down)
            // rather than losing it. Requeue so it is retried once the store recovers.
            log.error("Failed to persist dead letter - requeueing to DLQ", e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * Builds a {@link DeadLetter} from the raw message. The original routing key,
     * failure reason and redelivery count are recovered from the broker's {@code x-death}
     * header (the DLQ's own received routing key is the dead-letter key, not the original).
     */
    private DeadLetter toDeadLetter(Message message) {
        MessageProperties props = message.getMessageProperties();
        String messageId = props.getMessageId();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        String originalRoutingKey = props.getReceivedRoutingKey();
        String reason = null;
        Integer count = null;

        Object xDeath = props.getHeaders().get("x-death");
        if (xDeath instanceof List<?> deaths && !deaths.isEmpty()
                && deaths.get(0) instanceof Map<?, ?> first) {
            Object r = first.get("reason");
            if (r != null) {
                reason = r.toString();
            }
            Object c = first.get("count");
            if (c instanceof Number number) {
                count = number.intValue();
            }
            Object routingKeys = first.get("routing-keys");
            if (routingKeys instanceof List<?> keys && !keys.isEmpty() && keys.get(0) != null) {
                originalRoutingKey = keys.get(0).toString();
            }
        }

        return new DeadLetter(messageId, originalRoutingKey, reason, count, body);
    }
}
