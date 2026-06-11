package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

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

    public void handle(Transaction transaction, String routingKey) throws Exception {
        if (transaction == null) return;
        String debitSegment = segmentResolver.resolve(transaction.getDebitCustomerId());
        String creditSegment = segmentResolver.resolve(transaction.getCreditCustomerId());
        if (debitSegment.equals("N/A") && creditSegment.equals("N/A")) {
            log.info("Skipping message - no client on either side");
            return;
        }
        int channelId = transaction.getChannelId();
        LocalDate date = transaction.getDate();

        log.info("Processing transaction - debitSegment: {}, creditSegment: {}, channelId: {}, date: {}",
                debitSegment, creditSegment, channelId, date);
        statisticsRepository.upsert(debitSegment, creditSegment, channelId, date, routingKey);
    }
}