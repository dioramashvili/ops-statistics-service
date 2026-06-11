package ge.bsb.ops.statistics.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.QueueBuilder;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.create-routing-key}")
    private String createRoutingKey;

    @Value("${app.rabbitmq.delete-routing-key}")
    private String deleteRoutingKey;

    @Value("${app.rabbitmq.dlx}")
    private String deadLetterExchangeName;

    @Value("${app.rabbitmq.dlq}")
    private String deadLetterQueueName;

    @Value("${app.rabbitmq.dead-letter-routing-key}")
    private String deadLetterRoutingKey;

    @Bean
    public Queue statisticsQueue() {
        return QueueBuilder
                .durable(queueName)
                .deadLetterExchange(deadLetterExchangeName)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public TopicExchange statisticsExchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Binding createTransactionBinding() {
        return BindingBuilder
                .bind(statisticsQueue())
                .to(statisticsExchange())
                .with(createRoutingKey);
    }

    @Bean
    public Binding deleteTransactionBinding() {
        return BindingBuilder
                .bind(statisticsQueue())
                .to(statisticsExchange())
                .with(deleteRoutingKey);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPrefetchCount(1);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(deadLetterExchangeName);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(deadLetterRoutingKey);
    }
}