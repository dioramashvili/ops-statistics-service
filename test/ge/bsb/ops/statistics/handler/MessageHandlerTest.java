package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.TransactionMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageHandlerTest {

    @Test
    public void testHandleSkipsWhenBothSegmentsAreNA() throws Exception {
        // create a message with customer IDs = 0 so both resolve to N/A
        TransactionMessage message = new TransactionMessage(
                "b6.transaction.create",
                "{\"debitCustomerId\":0,\"creditCustomerId\":0,\"channelId\":1,\"date\":\"2026-05-01\"}"
        );

        MessageHandler handler = new MessageHandler(
                new ge.bsb.ops.statistics.parser.MessageParser(),
                new ge.bsb.ops.statistics.service.SegmentResolver(),
                new ge.bsb.ops.statistics.repository.StatisticsRepository()
        );

        // should complete without exception and skip upsert
        handler.handle(message);
    }
}