package com.dioramashvili.opsstatistics.listener;

import com.rabbitmq.client.Channel;
import com.dioramashvili.opsstatistics.model.DeadLetter;
import com.dioramashvili.opsstatistics.repository.DeadLetterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeadLetterListenerTest {

    private static final long DELIVERY_TAG = 7L;

    private DeadLetterRepository deadLetterRepository;
    private Channel channel;
    private DeadLetterListener listener;

    @BeforeEach
    void setUp() {
        deadLetterRepository = mock(DeadLetterRepository.class);
        channel = mock(Channel.class);
        // zero requeue delay so the failure-path test does not sleep
        listener = new DeadLetterListener(deadLetterRepository, 0L);
    }

    private Message message(boolean withXDeath) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(DELIVERY_TAG);
        props.setMessageId("msg-1");
        props.setReceivedRoutingKey("statistics.queue.dead");
        if (withXDeath) {
            props.setHeader("x-death", List.of(Map.of(
                    "reason", "rejected",
                    "count", 2L,
                    "routing-keys", List.of("transaction.create")
            )));
        }
        return new Message("{\"channelId\":5}".getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void shouldPersistDeadLetterAndAck() throws Exception {
        listener.listen(message(true), channel);

        ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(deadLetterRepository).save(captor.capture());

        DeadLetter saved = captor.getValue();
        assertEquals("msg-1", saved.messageId());
        assertEquals("transaction.create", saved.originalRoutingKey());
        assertEquals("rejected", saved.deathReason());
        assertEquals(2, saved.deathCount());
        assertEquals("{\"channelId\":5}", saved.body());

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldFallBackToReceivedRoutingKeyWhenNoXDeathHeader() throws Exception {
        listener.listen(message(false), channel);

        ArgumentCaptor<DeadLetter> captor = ArgumentCaptor.forClass(DeadLetter.class);
        verify(deadLetterRepository).save(captor.capture());

        DeadLetter saved = captor.getValue();
        assertEquals("statistics.queue.dead", saved.originalRoutingKey());
        assertEquals(null, saved.deathReason());
        assertEquals(null, saved.deathCount());

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void shouldRequeueWhenPersistFails() throws Exception {
        doThrow(new RuntimeException("db down")).when(deadLetterRepository).save(any());

        listener.listen(message(true), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
