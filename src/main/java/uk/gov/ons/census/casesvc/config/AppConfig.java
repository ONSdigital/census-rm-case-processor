package uk.gov.ons.census.casesvc.config;

import java.util.TimeZone;
import javax.annotation.PostConstruct;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.cloud.gcp.pubsub.support.PublisherFactory;
import org.springframework.cloud.gcp.pubsub.support.SubscriberFactory;
import org.springframework.cloud.gcp.pubsub.support.converter.JacksonPubSubMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.gov.ons.census.casesvc.utility.ObjectMapperFactory;

@Configuration
@EnableScheduling
public class AppConfig {

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter);
    rabbitTemplate.setChannelTransacted(true);
    return rabbitTemplate;
  }

  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter(ObjectMapperFactory.objectMapper());
  }

  @Bean
  public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean
  public MapperFacade mapperFacade() {
    MapperFactory mapperFactory = new DefaultMapperFactory.Builder().build();

    return mapperFactory.getMapperFacade();
  }

  @Bean
  public PubSubTemplate pubSubTemplate(
      PublisherFactory publisherFactory,
      SubscriberFactory subscriberFactory,
      JacksonPubSubMessageConverter jacksonPubSubMessageConverter) {
    PubSubTemplate pubSubTemplate = new PubSubTemplate(publisherFactory, subscriberFactory);
    pubSubTemplate.setMessageConverter(jacksonPubSubMessageConverter);
    return pubSubTemplate;
  }

  @Bean
  public JacksonPubSubMessageConverter jacksonPubSubMessageConverter() {
    return new JacksonPubSubMessageConverter(ObjectMapperFactory.objectMapper());
  }

  @PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }
}
