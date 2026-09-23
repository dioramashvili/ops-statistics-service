package ge.bsb.ops.statistics.model;

public record DeadLetter(
        String messageId,
        String originalRoutingKey,
        String deathReason,
        Integer deathCount,
        String body
) {
}
