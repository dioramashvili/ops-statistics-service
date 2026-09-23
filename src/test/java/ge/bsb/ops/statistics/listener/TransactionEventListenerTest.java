package ge.bsb.ops.statistics.listener;

import com.rabbitmq.client.Channel;
import ge.bsb.ops.statistics.handler.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TransactionEventListenerTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long DELIVERY_TAG = 1L;

    private MessageHandler messageHandler;
    private Channel channel;
    private TransactionEventListener listener;

    @BeforeEach
    void setUp() {
        messageHandler = mock(MessageHandler.class);
        channel = mock(Channel.class);
        // zero backoff so retries do not slow the test down
        listener = new TransactionEventListener(messageHandler, MAX_ATTEMPTS, 0L, 1.0);
    }

    private Message message() {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(DELIVERY_TAG);
        props.setReceivedRoutingKey("transaction.create");
        props.setMessageId("msg-1");
        return new Message("{}".getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void shouldAckOnceWhenProcessingSucceedsFirstTry() throws Exception {
        listener.listen(message(), channel);

        verify(messageHandler, times(1)).handle(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldRetryThenAckWhenAttemptFailsThenSucceeds() throws Exception {
        doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(messageHandler).handle(any());

        listener.listen(message(), channel);

        verify(messageHandler, times(2)).handle(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldExhaustRetriesThenNackToDlq() throws Exception {
        doThrow(new RuntimeException("always")).when(messageHandler).handle(any());

        listener.listen(message(), channel);

        verify(messageHandler, times(MAX_ATTEMPTS)).handle(any());
        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
