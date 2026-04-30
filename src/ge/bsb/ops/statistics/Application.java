package ge.bsb.ops.statistics;

import ge.bsb.ops.statistics.consumer.RabbitMQConsumer;
import ge.bsb.ops.statistics.handler.MessageHandler;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;


public class Application {
    public static void main(String[] args) throws Exception {
        MessageParser parser = new MessageParser();
        SegmentResolver resolver = new SegmentResolver();
        StatisticsRepository repository = new StatisticsRepository();
        MessageHandler handler = new MessageHandler(parser, resolver, repository);

        RabbitMQConsumer consumer = new RabbitMQConsumer(handler);
        consumer.start();
    }
}