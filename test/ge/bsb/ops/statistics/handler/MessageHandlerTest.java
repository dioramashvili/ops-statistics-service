package ge.bsb.ops.statistics.handler;

import ge.bsb.ops.statistics.model.TransactionMessage;
import org.junit.Test;

import static org.junit.Assert.*;

public class MessageHandlerTest {

    @Test
    public void testTransactionMessageHoldsValues() {
        TransactionMessage message = new TransactionMessage("b6.transaction.create", "{}");
        assertEquals("b6.transaction.create", message.routingKey());
        assertEquals("{}", message.body());
    }

    @Test
    public void testTransactionMessageDeleteRoutingKey() {
        TransactionMessage message = new TransactionMessage("b6.transaction.delete", "{}");
        assertEquals("b6.transaction.delete", message.routingKey());
    }

    @Test
    public void testHandleDoesNotThrowOnNAMessage() {
        TransactionMessage message = new TransactionMessage(
                "b6.transaction.create",
                "{\"debitCustomerId\":0,\"creditCustomerId\":0,\"channelId\":1,\"date\":\"2026-05-01\"}"
        );
        try {
            MessageHandler handler = new MessageHandler(
                    new ge.bsb.ops.statistics.parser.MessageParser(),
                    new ge.bsb.ops.statistics.service.SegmentResolver(),
                    new ge.bsb.ops.statistics.repository.StatisticsRepository()
            );
            handler.handle(message);
        } catch (Exception e) {
            fail("Handler should not throw exception for N/A message");
        }
    }
}