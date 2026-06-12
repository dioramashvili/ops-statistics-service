package ge.bsb.ops.statistics.model;

public record TransactionMessage(String messageId, String routingKey, String body) {
}
