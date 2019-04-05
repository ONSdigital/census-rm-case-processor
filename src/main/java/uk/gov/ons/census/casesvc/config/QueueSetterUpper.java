package uk.gov.ons.census.casesvc.config;

import static org.springframework.amqp.core.Binding.DestinationType.QUEUE;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueSetterUpper {
  @Bean
  public Queue inboundQueue() {
    Queue queue = new Queue("exampleInboundQueue");
    return queue;
  }

  @Bean
  public Queue fanoutOneQueue() {
    return new Queue("myfanout.queue1", true);
  }

  @Bean
  public Queue fanoutTwoQueue() {
    return new Queue("myfanout.queue2", true);
  }

  @Bean
  public Exchange myFanoutExchange() {
    return new FanoutExchange("myfanout.exchange");
  }

  @Bean
  public Binding bindingOne() {
    return new Binding("myfanout.queue1", QUEUE, "myfanout.exchange", "", null);
  }

  @Bean
  public Binding bindingTwo() {
    return new Binding("myfanout.queue2", QUEUE, "myfanout.exchange", "", null);
  }
}
