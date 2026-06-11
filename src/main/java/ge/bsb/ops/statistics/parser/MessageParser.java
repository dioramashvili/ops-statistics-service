package ge.bsb.ops.statistics.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.bsb.ops.statistics.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class MessageParser {
    private static final Logger log = LoggerFactory.getLogger(MessageParser.class);

    private final ObjectMapper objectMapper;

    public MessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Transaction parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(
                root.has("debitCustomerId") ? root.get("debitCustomerId").asInt() : 0
        );
        transaction.setCreditCustomerId(
                root.has("creditCustomerId") ? root.get("creditCustomerId").asInt() : 0
        );
        if (!root.has("debitCustomerId")) {
            log.info("Debit account has no owner client - will be treated as N/A");
        }
        if (!root.has("creditCustomerId")) {
            log.info("Credit account has no owner client - will be treated as N/A");
        }
        transaction.setChannelId(root.get("channelId").asInt());
        transaction.setDate(LocalDate.parse(root.get("date").asText()));

        return transaction;
    }
}