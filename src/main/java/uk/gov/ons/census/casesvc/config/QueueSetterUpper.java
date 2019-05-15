package uk.gov.ons.census.casesvc.config;

import static org.springframework.amqp.core.Binding.DestinationType.QUEUE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueSetterUpper {
  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.rh-case-routing-key}")
  private String rhCaseRoutingKey;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.rh-uac-routing-key}")
  private String rhUacRoutingKey;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionSchedulerQueue;

  @Value("${queueconfig.action-scheduler-routing-key}")
  private String actionSchedulerRoutingKey;

  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

  @Bean
  public Queue inboundQueue() {
    return new Queue(inboundQueue, true);
  }

  @Bean
  public Queue unaddressedQueue() {
    return new Queue(unaddressedQueue, true);
  }

  @Bean
  public Queue rhCaseQueue() {
    return new Queue(rhCaseQueue, true);
  }

  @Bean
  public Queue rhUacQueue() {
    return new Queue(rhUacQueue, true);
  }

  @Bean
  public Queue actionSchedulerQueue() {
    return new Queue(actionSchedulerQueue, true);
  }

  @Bean
  public Exchange myTopicExchange() {
    return new TopicExchange(outboundExchange, true, false);
  }

  @Bean
  public Binding bindingRhCase() {
    return new Binding(rhCaseQueue, QUEUE, outboundExchange, rhCaseRoutingKey, null);
  }

  @Bean
  public Binding bindingRhUac() {
    return new Binding(rhUacQueue, QUEUE, outboundExchange, rhUacRoutingKey, null);
  }

  @Bean
  public Binding bindingAction() {
    return new Binding(
        actionSchedulerQueue, QUEUE, outboundExchange, actionSchedulerRoutingKey, null);
  }
}
