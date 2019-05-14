package uk.gov.ons.census.casesvc.config;

import static org.springframework.amqp.core.Binding.DestinationType.QUEUE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueSetterUpper {
  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  @Value("${queueconfig.emit-case-event-rh-queue}")
  private String emitCaseEventRhQueue;

  @Value("${queueconfig.emit-case-event-action-queue}")
  private String emitCaseEventActionQueue;

  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String receiptInboundQueue;

  @Bean
  public Queue inboundQueue() {
    return new Queue(inboundQueue, true);
  }

  @Bean
  public Queue unaddressedQueue() {
    return new Queue(unaddressedQueue, true);
  }

  @Bean
  public Queue fanoutOneQueue() {
    return new Queue(emitCaseEventRhQueue, true);
  }

  @Bean
  public Queue fanoutTwoQueue() {
    return new Queue(emitCaseEventActionQueue, true);
  }

  @Bean
  public Exchange myFanoutExchange() {
    return new FanoutExchange(emitCaseEventExchange, true, false);
  }

  @Bean
  public Binding bindingOne() {
    return new Binding(emitCaseEventRhQueue, QUEUE, emitCaseEventExchange, "", null);
  }

  @Bean
  public Binding bindingTwo() {
    return new Binding(emitCaseEventActionQueue, QUEUE, emitCaseEventExchange, "", null);
  }

  @Bean
  public Queue receiptInboundQueue() { return new Queue(receiptInboundQueue, true); }
}
