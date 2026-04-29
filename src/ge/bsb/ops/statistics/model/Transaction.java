package ge.bsb.ops.statistics.model;

public record Transaction(String debitAccount, String creditAccount, String channelId, String docDate) {
}
