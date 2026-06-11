package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.model.TransactionMessage;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final MessageParser messageParser;
    private final SegmentResolver segmentResolver;
    private final StatisticsRepository statisticsRepository;

    public MessageHandler(MessageParser messageParser, SegmentResolver segmentResolver, StatisticsRepository statisticsRepository) {
        this.messageParser = messageParser;
        this.segmentResolver = segmentResolver;
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional
    public void handle(TransactionMessage message) throws Exception {
        Transaction transaction = messageParser.parse(message.body());
        if (transaction == null) {
            log.warn("Skipping message - parser returned null");
            return;
        }
        String debitSegment = segmentResolver.resolve(transaction.getDebitCustomerId());
        String creditSegment = segmentResolver.resolve(transaction.getCreditCustomerId());
        if ("N/A".equals(debitSegment) && "N/A".equals(creditSegment)) {
            log.info("Skipping message - no client on either side");
            return;
        }
        int channelId = transaction.getChannelId();
        LocalDate date = transaction.getDate();
        String routingKey = message.routingKey();
        log.info("Processing transaction - debitSegment: {}, creditSegment: {}, channelId: {}, date: {}",
                debitSegment, creditSegment, channelId, date);
        statisticsRepository.upsert(debitSegment, creditSegment, channelId, date, routingKey);
    }
}