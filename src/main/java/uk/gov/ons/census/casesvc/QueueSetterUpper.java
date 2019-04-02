package uk.gov.ons.census.casesvc;

import static org.springframework.amqp.core.Binding.DestinationType.QUEUE;
import static org.springframework.amqp.core.BindingBuilder.bind;

import java.util.Arrays;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueSetterUpper {
  @Bean
  public Queue inboundQueue() {
    return new Queue("exampleInboundQueue");
  }

  @Bean
  public Queue fanoutOneQueue() {
    return new Queue("myfanout.queue1");
  }

  @Bean
  public Queue fanoutTwoQueue() {
    return new Queue("myfanout.queue2");
  }

  @Bean
  public Exchange myFanoutExchange() {
    return new FanoutExchange("myfanout.exchange");
  }

  @Bean
  public Binding bindingOne() {
    return new Binding("myfanout.queue1", QUEUE,
        "myfanout.exchange", "", null);
  }

  @Bean
  public Binding bindingTwo() {
    return new Binding("myfanout.queue2", QUEUE,
        "myfanout.exchange", "", null);
  }

  @Bean
  public List<Declarable> fanoutBindings() {
    Queue fanoutQueue1 = new Queue("myfanout.queue1", true);
    Queue fanoutQueue2 = new Queue("myfanout.queue2", true);
    FanoutExchange fanoutExchange = new FanoutExchange("myfanout.exchange");

    return Arrays.asList(
        fanoutQueue1,
        fanoutQueue2,
        fanoutExchange,
        bind(fanoutQueue1).to(fanoutExchange),
        BindingBuilder.bind(fanoutQueue2).to(fanoutExchange));
  }
}
