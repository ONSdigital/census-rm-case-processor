package uk.gov.ons.census.casesvc.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.amqp.support.DefaultAmqpHeaderMapper;
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
  private final ConnectionFactory connectionFactory;

  @Value("${messagelogging.logstacktraces}")
  private boolean logStackTraces;

  @Value("${queueconfig.consumers}")
  private int consumers;

  @Value("${queueconfig.retry-attempts}")
  private int retryAttempts;

  @Value("${queueconfig.retry-delay}")
  private int retryDelay;

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

  @Value("${queueconfig.address-inbound-queue}")
  private String addressInboundQueue;

  @Value("${queueconfig.survey-launched-queue}")
  private String surveyLaunchedQueue;

  @Value("${queueconfig.undelivered-mail-queue}")
  private String undeliveredMailQueue;

  @Value("${queueconfig.ccs-property-listed-queue}")
  private String ccsPropertyListedQueue;

  @Value("${queueconfig.fulfilment-confirmed-queue}")
  private String fulfilmentConfirmedQueue;

  @Value("${queueconfig.field-case-updated-queue}")
  private String fieldCaseUpdatedQueue;

  @Value("${queueconfig.deactivate-uac-queue}")
  private String deactivateUacQueue;

  @Value("${queueconfig.rm-case-updated-queue}")
  private String rmCaseUpdatedQueue;

  public MessageConsumerConfig(
      ExceptionManagerClient exceptionManagerClient, ConnectionFactory connectionFactory) {
    this.exceptionManagerClient = exceptionManagerClient;
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
  public MessageChannel addressInputChannel() {
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
  public MessageChannel fulfilmentConfirmedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel fieldCaseUpdatedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel deactivateUacInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public MessageChannel rmCaseUpdatedInputChannel() {
    return new DirectChannel();
  }

  @Bean
  public AmqpInboundChannelAdapter inboundSamples(
      @Qualifier("sampleContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("caseSampleInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter inboundUnaddressed(
      @Qualifier("unaddressedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("unaddressedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter receiptInbound(
      @Qualifier("receiptContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("receiptInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter refusalInbound(
      @Qualifier("refusalContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("refusalInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter fulfilmentInbound(
      @Qualifier("fulfilmentContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("fulfilmentInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter questionnaireLinkedInbound(
      @Qualifier("questionnaireLinkedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("questionnaireLinkedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  public AmqpInboundChannelAdapter actionCaseInbound(
      @Qualifier("actionCaseContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("actionCaseInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter uacCreatedInbound(
      @Qualifier("uacCreatedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("uacCreatedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter addressInbound(
      @Qualifier("addressContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("addressInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter surveyLaunchedInbound(
      @Qualifier("surveyLaunchedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("surveyLaunchedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter undeliveredMailInbound(
      @Qualifier("undeliveredMailContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("undeliveredMailInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter ccsPropertyListedInbound(
      @Qualifier("ccsPropertyListedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("ccsPropertyListedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter fulfilmentConfirmedInbound(
      @Qualifier("fulfilmentConfirmedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("fulfilmentConfirmedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter fieldCaseUpdatedInbound(
      @Qualifier("fieldCaseUpdatedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("fieldCaseUpdatedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter deactivateUacInbound(
      @Qualifier("deactivateUacContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("deactivateUacInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
  }

  @Bean
  AmqpInboundChannelAdapter rmCaseUpdatedInbound(
      @Qualifier("rmCaseUpdatedContainer") SimpleMessageListenerContainer listenerContainer,
      @Qualifier("rmCaseUpdatedInputChannel") MessageChannel channel) {
    return makeAdapter(listenerContainer, channel);
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
  public SimpleMessageListenerContainer addressContainer() {
    return setupListenerContainer(addressInboundQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer undeliveredMailContainer() {
    return setupListenerContainer(undeliveredMailQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer ccsPropertyListedContainer() {
    return setupListenerContainer(ccsPropertyListedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer fulfilmentConfirmedContainer() {
    return setupListenerContainer(fulfilmentConfirmedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer fieldCaseUpdatedContainer() {
    return setupListenerContainer(fieldCaseUpdatedQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer deactivateUacContainer() {
    return setupListenerContainer(deactivateUacQueue, ResponseManagementEvent.class);
  }

  @Bean
  public SimpleMessageListenerContainer rmCaseUpdatedContainer() {
    return setupListenerContainer(rmCaseUpdatedQueue, ResponseManagementEvent.class);
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
            "Case Processor",
            queueName);

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
    container.setAdviceChain(retryOperationsInterceptor);
    return container;
  }

  private AmqpInboundChannelAdapter makeAdapter(
      AbstractMessageListenerContainer listenerContainer, MessageChannel channel) {
    AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
    adapter.setOutputChannel(channel);
    adapter.setHeaderMapper(
        new DefaultAmqpHeaderMapper(null, null) {
          @Override
          public Map<String, Object> toHeadersFromRequest(MessageProperties source) {
            Map<String, Object> headers = new HashMap<>();

            /* We strip EVERYTHING out of the headers and put the content type back in because we
             * don't want the __TYPE__ to be processed by Spring Boot, which would cause
             * ClassNotFoundException because the type which was sent doesn't match the type we
             * want to receive. This is an ugly workaround, but it works.
             */
            headers.put("contentType", "application/json");
            return headers;
          }
        });
    return adapter;
  }
}
