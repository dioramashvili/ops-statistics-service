package ge.bsb.ops.statistics;

import ge.bsb.ops.statistics.consumer.RabbitMQConsumer;
import ge.bsb.ops.statistics.handler.MessageHandler;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting ops-statistics-service...");
        MessageParser parser = new MessageParser();
        SegmentResolver resolver = new SegmentResolver();
        StatisticsRepository repository = new StatisticsRepository();
        MessageHandler handler = new MessageHandler(parser, resolver, repository);
        RabbitMQConsumer consumer = new RabbitMQConsumer(handler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping consumer...");
            try {
                consumer.stop();
                log.info("Consumer stopped gracefully");
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));
        consumer.start();

    }
}