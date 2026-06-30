package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Segment;
import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.model.TransactionMessage;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.ProcessedMessageRepository;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final MessageParser messageParser;
    private final SegmentResolver segmentResolver;
    private final StatisticsRepository statisticsRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final String createRoutingKey;
    private final String deleteRoutingKey;

    public MessageHandler(
            MessageParser messageParser,
            SegmentResolver segmentResolver,
            StatisticsRepository statisticsRepository,
            ProcessedMessageRepository processedMessageRepository,
            @Value("${app.rabbitmq.create-routing-key}") String createRoutingKey,
            @Value("${app.rabbitmq.delete-routing-key}") String deleteRoutingKey
    ) {
        this.messageParser = messageParser;
        this.segmentResolver = segmentResolver;
        this.statisticsRepository = statisticsRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.createRoutingKey = createRoutingKey;
        this.deleteRoutingKey = deleteRoutingKey;
    }

    @Transactional
    public void handle(TransactionMessage message) throws Exception {

        String messageId = message.messageId();
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message is missing a messageId - cannot guarantee idempotent processing");
        }

        if (!processedMessageRepository.tryMarkProcessed(messageId)) {
            log.info("Skipping duplicate message: {}", messageId);
            return;
        }

        String routingKey = message.routingKey();
        Transaction transaction = messageParser.parse(message.body());

        String debitSegment = segmentResolver.resolve(transaction.getDebitCustomerId());
        String creditSegment = segmentResolver.resolve(transaction.getCreditCustomerId());
        if (Segment.NOT_APPLICABLE.equals(debitSegment) && Segment.NOT_APPLICABLE.equals(creditSegment)) {
            log.info("Skipping message - no client on either side");
            return;
        }
        int channelId = transaction.getChannelId();
        LocalDate date = transaction.getDate();
        log.info("Processing transaction - debitSegment: {}, creditSegment: {}, channelId: {}, date: {}",
                debitSegment, creditSegment, channelId, date);

        if (createRoutingKey.equals(routingKey)) {
            statisticsRepository.increment(debitSegment, creditSegment, channelId, date);
        } else if (deleteRoutingKey.equals(routingKey)) {
            statisticsRepository.decrement(debitSegment, creditSegment, channelId, date);
        } else {
            throw new IllegalArgumentException("Unsupported routing key: " + routingKey);
        }
    }
}