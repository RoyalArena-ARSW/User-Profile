package edu.eci.arsw.RoyalArena.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología del lado CONSUMIDOR.
 *
 * Profile declara SU cola y la enlaza al exchange. Game Engine no sabe que
 * esta cola existe. Si Profile está caído, RabbitMQ igual acumula los eventos
 * aquí (la cola es durable) y se procesan al volver: eso es el desacoplamiento
 * temporal que REST no da.
 *
 * Declarar el exchange aquí también lo hace resiliente al orden de arranque:
 * declarar algo que ya existe con los mismos parámetros es idempotente.
 */
@Configuration
public class RabbitConfig {

    @Value("${royalarena.events.exchange}")
    private String exchangeName;

    @Value("${royalarena.events.queue.match-finished}")
    private String queueName;

    @Value("${royalarena.events.routing-key.match-finished}")
    private String routingKey;

    @Bean
    public TopicExchange matchesExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Cola durable con Dead Letter Queue: si un mensaje falla y se rechaza
     * definitivamente, va a la DLQ en vez de perderse o reencolarse infinitamente
     * (el "poison message" que bloquea el consumo).
     */
    @Bean
    public Queue matchFinishedQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", queueName + ".dlq")
                .build();
    }

    @Bean
    public Queue matchFinishedDlq() {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    @Bean
    public Binding matchFinishedBinding(Queue matchFinishedQueue, TopicExchange matchesExchange) {
        return BindingBuilder.bind(matchFinishedQueue).to(matchesExchange).with(routingKey);
    }

    /** Debe coincidir con el del productor: JSON en ambos lados. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}