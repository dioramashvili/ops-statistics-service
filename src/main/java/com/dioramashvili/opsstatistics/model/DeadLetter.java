package com.dioramashvili.opsstatistics.model;

public record DeadLetter(
        String messageId,
        String originalRoutingKey,
        String deathReason,
        Integer deathCount,
        String body
) {
}
