package ge.bsb.ops.statistics.consumer;

import com.rabbitmq.client.*;
import ge.bsb.ops.statistics.handler.MessageHandler;
import ge.bsb.ops.statistics.model.TransactionMessage;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class RabbitMQConsumer {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQConsumer.class);

    private Connection connection;
    private Channel channel;
    private final MessageHandler messageHandler;

    public RabbitMQConsumer(MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        Properties props = new Properties();
        props.load(new FileInputStream("config.properties"));

        factory.setHost(props.getProperty("rabbitmq.host"));
        factory.setPort(Integer.parseInt(props.getProperty("rabbitmq.port")));
        factory.setUsername(props.getProperty("rabbitmq.username"));
        factory.setPassword(props.getProperty("rabbitmq.password"));
        factory.setVirtualHost(props.getProperty("rabbitmq.virtualhost"));

        connection = factory.newConnection();
        log.info("Connected to RabbitMQ successfully");
        channel = connection.createChannel();

        String queueName = channel.queueDeclare(
                props.getProperty("rabbitmq.queue"),
                true,  // durable
                false,   // exclusive
                false,   // auto-delete
                null    // Extra configs
        ).getQueue();

        log.info("Queue declared: {}", queueName);

        channel.queueBind(queueName, props.getProperty("rabbitmq.exchange"), "b6.transaction.create");
        channel.queueBind(queueName, props.getProperty("rabbitmq.exchange"), "b6.transaction.delete");

        log.info("Waiting for messages...");

        channel.basicQos(1);
        DeliverCallback deliverCallback = getDeliverCallback(channel);
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> log.warn("Consumer cancelled: {}", consumerTag));

        Thread.currentThread().join();
    }

    private DeliverCallback getDeliverCallback(Channel channel) {
        return (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            long deliveryTag = delivery.getEnvelope().getDeliveryTag();

            try {
                TransactionMessage message = new TransactionMessage(routingKey, body);
                log.info("Received message - routing key: {}", routingKey);
                messageHandler.handle(message);
                channel.basicAck(deliveryTag, false);
                log.info("Message acknowledged successfully");

            } catch (Exception e) {
                log.error("Failed to process message", e);
                channel.basicNack(deliveryTag, false, false);
            }
        };
    }

    public void stop() throws Exception {
        channel.close();
        connection.close();
        log.info("Shutting down consumer...");
    }
}