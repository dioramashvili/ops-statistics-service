package ge.bsb.ops.statistics.parser;

import ge.bsb.ops.statistics.model.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageParserTest {

    private final MessageParser parser = new MessageParser();

    @Test
    public void testParse() throws Exception {
        String body = "{\"debitCustomerId\":950933,\"creditCustomerId\":950933,\"channelId\":1,\"date\":\"2026-04-30\"}";
        Transaction transaction = parser.parse(body);

        assertEquals(950933, transaction.getDebitCustomerId());
        assertEquals(950933, transaction.getCreditCustomerId());
        assertEquals(1, transaction.getChannelId());
        assertEquals("2026-04-30", transaction.getDate().toString());
    }

    @Test
    public void testParseDefaultsMissingCustomerIdsToZero() throws Exception {
        String body = "{\"channelId\":1,\"date\":\"2026-04-30\"}";
        Transaction transaction = parser.parse(body);

        assertEquals(0, transaction.getDebitCustomerId());
        assertEquals(0, transaction.getCreditCustomerId());
    }

    @Test
    public void testParserDefaultsMissingCustomerIdToZero() throws Exception {
        ge.bsb.ops.statistics.parser.MessageParser parser =
                new ge.bsb.ops.statistics.parser.MessageParser();
        String body = "{\"channelId\":1,\"date\":\"2026-05-01\"}";
        ge.bsb.ops.statistics.model.Transaction transaction = parser.parse(body);

        assertEquals(0, transaction.getDebitCustomerId());
        assertEquals(0, transaction.getCreditCustomerId());
    }
}