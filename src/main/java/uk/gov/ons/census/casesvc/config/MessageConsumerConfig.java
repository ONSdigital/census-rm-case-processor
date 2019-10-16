package uk.gov.ons.census.casesvc.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.messaging.ManagedMessageRecoverer;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@Configuration
public class MessageConsumerConfig {
  private final ExceptionManagerClient exceptionManagerClient;
  private final RabbitTemplate rabbitTemplate;
  private final ConnectionFactory connectionFactory;

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  @Value("${queueconfig.consumers}")
  private int consumers;

  @Value("${queueconfig.retry-attempts}")
  private int retryAttempts;

  @Value("${queueconfig.retry-delay}")
  private int retryDelay;

  @Value("${queueconfig.retry-exchange}")
  private String retryExchange;

  @Value("${queueconfig.quarantine-exchange}")
  private String quarantineExchange;

  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

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

  @Value("${queueconfig.ccs-property-listed-queue}")
  private String ccsPropertyListedQueue;

  public MessageConsumerConfig(
      ExceptionManagerClient exceptionManagerClient,
      RabbitTemplate rabbitTemplate,
      ConnectionFactory connectionFactory) {
    this.exceptionManagerClient = exceptionManagerClient;
    this.rabbitTemplate = rabbitTemplate;
    this.connectionFactory = connectionFactory;
  }

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
  public MessageChannel ccsPropertyListedInputChannel() {
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
  AmqpInboundChannelAdapter ccsPropertyListedInbound(
      @Qualifier("ccsPropertyListedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("ccsPropertyListedInputChannel") MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    return adapter;
  }

  @Bean
  public SimpleMessageListenerContainer sampleContainer() {
    return setupListenerContainer(inboundQueue, CreateCaseSample.class);
  }

  @Bean
  public SimpleMessageListenerContainer surveyLaunchedContainer() {
    return setupListenerContainer(surveyLaunchedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer unaddressedContainer() {
    return setupListenerContainer(unaddressedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer receiptContainer() {
    return setupListenerContainer(receiptInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer refusalContainer() {
    return setupListenerContainer(refusalInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer fulfilmentContainer() {
    return setupListenerContainer(fulfilmentInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer questionnaireLinkedContainer() {
    return setupListenerContainer(questionnaireLinkedInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer actionCaseContainer() {
    return setupListenerContainer(actionCaseQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer uacCreatedContainer() {
    return setupListenerContainer(uacQidCreatedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer invalidAddressContainer() {
    return setupListenerContainer(invalidAddressInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer undeliveredMailContainer() {
    return setupListenerContainer(undeliveredMailQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer ccsPropertyListedContainer() {
    return setupListenerContainer(ccsPropertyListedQueue, ResponseManagementEvent.class);
  }

  private SimpleMessageListenerContainer setupListenerContainer(
      String queueName, Class expectedMessageType) {
    FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
    fixedBackOffPolicy.setBackOffPeriod(retryDelay);

    ManagedMessageRecoverer managedMessageRecoverer =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            expectedMessageType,
            logStackTraces,
            "Action Scheduler",
            queueName,
            retryExchange,
            quarantineExchange,
            rabbitTemplate);

    RetryOperationsInterceptor retryOperationsInterceptor =
        RetryInterceptorBuilder.stateless()
            .maxAttempts(retryAttempts)
            .backOffPolicy(fixedBackOffPolicy)
            .recoverer(managedMessageRecoverer)
            .build();

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(queueName);
    container.setConcurrentConsumers(consumers);
    container.setChannelTransacted(true);
    container.setAdviceChain(retryOperationsInterceptor);
    return container;
  }
}
