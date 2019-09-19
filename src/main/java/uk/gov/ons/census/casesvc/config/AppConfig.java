package uk.gov.ons.census.casesvc.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@Configuration
@EnableScheduling
public class AppConfig {
  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

  @Value("${queueconfig.consumers}")
  private int consumers;

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String receiptInboundQueue;

  @Value("${queueconfig.refusal-response-inbound-queue}")
  private String refusalInboundQueue;

  @Value("${queueconfig.fulfilment-request-inbound-queue}")
  private String fulfilmentInboundQueue;

  @Value("${queueconfig.questionnaire-linked-inbound-queue}")
  private String questionnaireLinkedInboundQueue;

  @Value("${queueconfig.action-case-queue}")
  private String actionCaseQueue;

  @Value("${queueconfig.uac-qid-created-queue}")
  private String uacQidCreatedQueue;

  @Value("${queueconfig.invalid-address-inbound-queue}")
  private String invalidAddressInboundQueue;

  @Value("${queueconfig.survey-launched-queue}")
  private String surveyLaunchedQueue;

  @Value("${queueconfig.undelivered-mail-queue}")
  private String undeliveredMailQueue;

  @Bean
  public MessageChannel caseSampleInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel unaddressedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel receiptInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel refusalInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel fulfilmentInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel questionnaireLinkedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel actionCaseInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel uacCreatedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel invalidAddressInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel surveyLaunchedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel undeliveredMailInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public AmqpInboundChannelAdapter inboundSamples(
      @Qualifier("sampleContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("caseSampleInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter inboundUnaddressed(
      @Qualifier("unaddressedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("unaddressedInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter receiptInbound(
      @Qualifier("receiptContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("receiptInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter refusalInbound(
      @Qualifier("refusalContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("refusalInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter fulfilmentInbound(
      @Qualifier("fulfilmentContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("fulfilmentInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter questionnaireLinkedInbound(
      @Qualifier("questionnaireLinkedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("questionnaireLinkedInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public AmqpInboundChannelAdapter actionCaseInbound(
      @Qualifier("actionCaseContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("actionCaseInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  AmqpInboundChannelAdapter uacCreatedInbound(
      @Qualifier("uacCreatedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("uacCreatedInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  AmqpInboundChannelAdapter invalidAddressInbound(
      @Qualifier("invalidAddressContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("invalidAddressInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  AmqpInboundChannelAdapter surveyLaunchedInbound(
      @Qualifier("surveyLaunchedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("surveyLaunchedInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  AmqpInboundChannelAdapter undeliveredMailInbound(
      @Qualifier("undeliveredMailContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("undeliveredMailInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

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
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  public SimpleMessageListenerContainer sampleContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, inboundQueue, messageErrorHandler, CreateCaseSample.class);
  }

  @Bean
  public SimpleMessageListenerContainer surveyLaunchedContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, surveyLaunchedQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer unaddressedContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, unaddressedQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer receiptContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, receiptInboundQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer refusalContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, refusalInboundQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer fulfilmentContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory,
        fulfilmentInboundQueue,
        messageErrorHandler,
        ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer questionnaireLinkedContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory,
        questionnaireLinkedInboundQueue,
        messageErrorHandler,
        ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer actionCaseContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, actionCaseQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer uacCreatedContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory, uacQidCreatedQueue, messageErrorHandler, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer invalidAddressContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory,
        invalidAddressInboundQueue,
        messageErrorHandler,
        ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer undeliveredMailContainer(
      ConnectionFactory connectionFactory, MessageErrorHandler messageErrorHandler) {
    return setupListenerContainer(
        connectionFactory,
        undeliveredMailQueue,
        messageErrorHandler,
        ResponseManagementEvent.class);
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

  @PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  private SimpleMessageListenerContainer setupListenerContainer(
      ConnectionFactory connectionFactory,
      String queueName,
      MessageErrorHandler messageErrorHandler,
      Class expectedClass) {
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(queueName);
    container.setConcurrentConsumers(consumers);
    messageErrorHandler.setExpectedType(expectedClass);
    container.setErrorHandler(messageErrorHandler);
    container.setChannelTransacted(true);
    return container;
  }
}
