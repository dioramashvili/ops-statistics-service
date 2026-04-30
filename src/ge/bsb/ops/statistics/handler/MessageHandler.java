package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.Transaction;
import ge.bsb.ops.statistics.model.TransactionMessage;
import ge.bsb.ops.statistics.parser.MessageParser;
import ge.bsb.ops.statistics.repository.StatisticsRepository;
import ge.bsb.ops.statistics.service.SegmentResolver;

import java.time.LocalDate;

public class MessageHandler {
    private final MessageParser messageParser;
    private final SegmentResolver segmentResolver;
    private final StatisticsRepository statisticsRepository;

    public MessageHandler(MessageParser messageParser, SegmentResolver segmentResolver, StatisticsRepository statisticsRepository) {
        this.messageParser = messageParser;
        this.segmentResolver = segmentResolver;
        this.statisticsRepository = statisticsRepository;
    }

    public void handle(TransactionMessage message) throws Exception {
        Transaction transaction = messageParser.parse(message.body());
        String debitSegment = segmentResolver.resolve(transaction.getDebitCustomerId());
        String creditSegment = segmentResolver.resolve(transaction.getCreditCustomerId());
        if (debitSegment.equals("N/A") && creditSegment.equals("N/A")) {
            System.out.println("Skipping - no client on either side");
            return;
        }
        int channelId = transaction.getChannelId();
        LocalDate date = transaction.getDate();
        String routingKey = message.routingKey();
        statisticsRepository.upsert(debitSegment, creditSegment, channelId, date, routingKey);
    }
}