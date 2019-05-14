package uk.gov.ons.census.casesvc.config;

import static org.springframework.amqp.core.Binding.DestinationType.QUEUE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueSetterUpper {
  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  @Value("${queueconfig.emit-case-event-rh-queue-case}")
  private String emitCaseEventRhQueueCase;

  @Value("${queueconfig.emit-case-event-rh-queue-uac}")
  private String emitCaseEventRhQueueUac;

  @Value("${queueconfig.emit-case-event-action-queue}")
  private String emitCaseEventActionQueue;

  @Bean
  public Queue inboundQueue() {
    return new Queue(inboundQueue, true);
  }

  @Bean
  public Queue rhCaseQueue() {
    return new Queue(emitCaseEventRhQueueCase, true);
  }

  @Bean
  public Queue rhUacQueue() {
    return new Queue(emitCaseEventRhQueueUac, true);
  }

  @Bean
  public Queue actionQueue() {
    return new Queue(emitCaseEventActionQueue, true);
  }

  @Bean
  public Exchange myTopicExchange() {
    return new TopicExchange(emitCaseEventExchange, true, false);
  }

  @Bean
  public Binding bindingRhCase() {
    return new Binding(emitCaseEventRhQueueCase, QUEUE, emitCaseEventExchange, "event.case.*", null);
  }

  @Bean
  public Binding bindingRhUac() {
    return new Binding(emitCaseEventRhQueueUac, QUEUE, emitCaseEventExchange, "event.uac.*", null);
  }

  @Bean
  public Binding bindingAction() {
    return new Binding(emitCaseEventActionQueue, QUEUE, emitCaseEventExchange, "*", null);
  }
}
