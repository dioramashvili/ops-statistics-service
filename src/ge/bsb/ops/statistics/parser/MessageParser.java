package ge.bsb.ops.statistics.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.bsb.ops.statistics.consumer.RabbitMQConsumer;
import ge.bsb.ops.statistics.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class MessageParser {
    private static final Logger log = LoggerFactory.getLogger(MessageParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Transaction parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        if (root.get("debitCustomerId") == null || root.get("creditCustomerId") == null) {
            log.warn("Message missing customer IDs, skipping. Body: {}", body);
            return null;
        }
        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(root.get("debitCustomerId").asInt());
        transaction.setCreditCustomerId(root.get("creditCustomerId").asInt());
        transaction.setChannelId(root.get("channelId").asInt());
        transaction.setDate(LocalDate.parse(root.get("date").asText()));

        return transaction;
    }
}