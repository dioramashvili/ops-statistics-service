package com.dioramashvili.opsstatistics.model;

public record TransactionMessage(String messageId, String routingKey, String body) {
}
