package ge.bsb.ops.statistics.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.bsb.ops.statistics.model.Transaction;

import java.time.LocalDate;

public class MessageParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Transaction parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        Transaction transaction = new Transaction();
        transaction.setDebitCustomerId(root.get("debitCustomerId").asInt());
        transaction.setCreditCustomerId(root.get("creditCustomerId").asInt());
        transaction.setChannelId(root.get("channelId").asInt());
        transaction.setDate(LocalDate.parse(root.get("date").asText()));

        return transaction;
    }
}