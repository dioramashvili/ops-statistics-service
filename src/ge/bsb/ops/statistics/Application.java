package ge.bsb.ops.statistics;

import ge.bsb.ops.statistics.consumer.RabbitMQConsumer;

public class Application {
    public static void main(String[] args) throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer();
        consumer.start();
    }
}