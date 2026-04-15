package uk.gov.ons.census.caseprocessor.testutils;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.PublisherFactory;
import com.google.cloud.spring.pubsub.support.SubscriberFactory;
import com.google.cloud.spring.pubsub.support.converter.JacksonPubSubMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.census.caseprocessor.utils.ObjectMapperFactory;

@Configuration
@ActiveProfiles("test")
public class TestConfig {
  @Bean("pubSubTemplateForIntegrationTests")
  public PubSubTemplate pubSubTemplate(
      PublisherFactory publisherFactory,
      SubscriberFactory subscriberFactory,
      JacksonPubSubMessageConverter jacksonPubSubMessageConverter) {
    PubSubTemplate pubSubTemplate = new PubSubTemplate(publisherFactory, subscriberFactory);
    pubSubTemplate.setMessageConverter(jacksonPubSubMessageConverter);
    return pubSubTemplate;
  }

  @Bean
  public JacksonPubSubMessageConverter jacksonPubSubMessageConverterForIntegrationTests() {
    return new JacksonPubSubMessageConverter(ObjectMapperFactory.objectMapper());
  }
}
