package ge.bsb.ops.statistics.listener;

import com.rabbitmq.client.Channel;
import ge.bsb.ops.statistics.handler.MessageHandler;
import ge.bsb.ops.statistics.model.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TransactionEventListener {
    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final MessageHandler messageHandler;

    public TransactionEventListener(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void listen(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            log.info("Received message - routing key: {}", routingKey);

            TransactionMessage transactionMessage = new TransactionMessage(routingKey, body);
            messageHandler.handle(transactionMessage);

            channel.basicAck(deliveryTag, false);
            log.info("Message acknowledged successfully - routing key: {}", routingKey);

        } catch (Exception e) {
            log.error("Failed to process message with routing key: {}", routingKey, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}