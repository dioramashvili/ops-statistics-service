package ge.bsb.ops.statistics.consumer;

import com.rabbitmq.client.*;
import ge.bsb.ops.statistics.handler.MessageHandler;
import ge.bsb.ops.statistics.model.TransactionMessage;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class RabbitMQConsumer {
    private Connection connection;
    private Channel channel;

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
        channel = connection.createChannel();

        // Declare a temporary, auto-delete queue (disappears when you disconnect)
        String queueName = channel.queueDeclare(
                "basis.statistics.queue",     // empty = server generates a unique name
                false,  // durable
                true,   // exclusive
                true,   // auto-delete
                null    // Extra configs
        ).getQueue();

        System.out.println("Temp queue created: " + queueName);

        // Bind to the exchange with '#' wildcard — catches ALL routing keys
        channel.queueBind(queueName, "B6.Transactions", "b6.transaction.create");
        channel.queueBind(queueName, "B6.Transactions", "b6.transaction.delete");

        System.out.println("Waiting for messages... (press Ctrl+C to stop)");

        channel.basicQos(1);
        DeliverCallback deliverCallback = getDeliverCallback(channel);
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> System.out.println("Consumer cancelled: " + consumerTag));

        // Keep the main thread alive
        Thread.currentThread().join();
    }

    private DeliverCallback getDeliverCallback(Channel channel) {
        MessageHandler messageHandler = new MessageHandler();

        return (consumerTag, delivery) -> {
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            long deliveryTag = delivery.getEnvelope().getDeliveryTag();

            try {
                TransactionMessage message = new TransactionMessage(routingKey, body);
                //messageHandler.handle(message);

                System.out.println("Routing key: " + routingKey);
                System.out.println("Body: " + body);

                channel.basicAck(deliveryTag, false);
            } catch (Exception e) {
                System.out.println("Failed to proces: " + e.getMessage());
                channel.basicNack(deliveryTag, false, false);
            }
        };
    }

    public void stop() throws Exception {
        channel.close();
        connection.close();
    }
}