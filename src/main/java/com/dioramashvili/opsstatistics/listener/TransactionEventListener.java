package com.dioramashvili.opsstatistics.listener;

import com.rabbitmq.client.Channel;
import com.dioramashvili.opsstatistics.handler.MessageHandler;
import com.dioramashvili.opsstatistics.model.TransactionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TransactionEventListener {
    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final MessageHandler messageHandler;
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;

    public TransactionEventListener(
            MessageHandler messageHandler,
            @Value("${app.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.retry.initial-interval-ms:500}") long initialBackoffMs,
            @Value("${app.retry.multiplier:2.0}") double backoffMultiplier
    ) {
        this.messageHandler = messageHandler;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void listen(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String messageId = message.getMessageProperties().getMessageId();

        TransactionMessage transactionMessage = new TransactionMessage(messageId, routingKey, body);

        // Retry a bounded number of times with backoff so a transient failure
        // (DB blip, deadlock) doesn't immediately dead-letter the message. Each
        // attempt runs in its own transaction; idempotency makes the retry safe.
        boolean processed = false;
        long backoffMs = initialBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts && !processed; attempt++) {
            try {
                log.info("Processing message - routing key: {}, attempt: {}/{}", routingKey, attempt, maxAttempts);
                messageHandler.handle(transactionMessage);
                processed = true;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("Processing failed (attempt {}/{}) for routing key: {} - retrying in {} ms",
                            attempt, maxAttempts, routingKey, backoffMs, e);
                    if (!sleep(backoffMs)) {
                        break; // interrupted - stop retrying and dead-letter
                    }
                    backoffMs = (long) (backoffMs * backoffMultiplier);
                } else {
                    log.error("Processing failed after {} attempts for routing key: {} - routing to DLQ",
                            maxAttempts, routingKey, e);
                }
            }
        }

        if (processed) {
            channel.basicAck(deliveryTag, false);
            log.info("Message acknowledged successfully - routing key: {}", routingKey);
        } else {
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
