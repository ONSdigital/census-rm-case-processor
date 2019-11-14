package uk.gov.ons.census.casesvc.config;

import java.util.function.Consumer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.Transformers;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.messaging.ManagedMessageRecoverer;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ActionSchedulerEventService;
import uk.gov.ons.census.casesvc.service.CCSPropertyListedService;
import uk.gov.ons.census.casesvc.service.EventService;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestService;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.service.QuestionnaireLinkedService;
import uk.gov.ons.census.casesvc.service.ReceiptService;
import uk.gov.ons.census.casesvc.service.RefusalService;
import uk.gov.ons.census.casesvc.service.SurveyService;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.service.UnaddressedService;
import uk.gov.ons.census.casesvc.service.UndeliveredMailService;

@Configuration
public class MessageConsumerConfig {
  private final ExceptionManagerClient exceptionManagerClient;
  private final RabbitTemplate rabbitTemplate;
  private final ConnectionFactory connectionFactory;
  private final Environment env;

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

  public MessageConsumerConfig(
      ExceptionManagerClient exceptionManagerClient,
      RabbitTemplate rabbitTemplate,
      ConnectionFactory connectionFactory,
      Environment env) {
    this.exceptionManagerClient = exceptionManagerClient;
    this.rabbitTemplate = rabbitTemplate;
    this.connectionFactory = connectionFactory;
    this.env = env;
  }

  @Bean
  public IntegrationFlow inboundSampleFlow(@Autowired EventService eventService) {

    return listenToThisQueueAndSendMsgToThisMethodWithThisType(
        env.getProperty("queueconfig.inbound-queue"),
        eventService::processSampleReceivedMessage,
        CreateCaseSample.class);
  }

  @Bean
  public IntegrationFlow inboundReceiptFlow(@Autowired ReceiptService receiptService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.receipt-response-inbound-queue"),
        receiptService::processReceipt);
  }

  @Bean
  public IntegrationFlow refusalFlow(@Autowired RefusalService refusalService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.refusal-response-inbound-queue"),
        refusalService::processRefusal);
  }

  @Bean
  public IntegrationFlow fulfilmentFlow(
      @Autowired FulfilmentRequestService fulfilmentRequestService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.fulfilment-request-inbound-queue"),
        fulfilmentRequestService::processFulfilmentRequest);
  }

  @Bean
  public IntegrationFlow actionCaseFlow(
      @Autowired ActionSchedulerEventService actionSchedulerEventService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.action-case-queue"),
        actionSchedulerEventService::receiveMessage);
  }

  @Bean
  public IntegrationFlow questionaireLinkedFlow(
      @Autowired QuestionnaireLinkedService questionnaireLinkedService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.questionnaire-linked-inbound-queue"),
        questionnaireLinkedService::processQuestionnaireLinked);
  }

  @Bean
  public IntegrationFlow uacQudCreatedFlow(@Autowired UacService uacService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.uac-qid-created-queue"), uacService::ingestUacCreatedEvent);
  }

  @Bean
  public IntegrationFlow SurveyLaunchedFlow(@Autowired SurveyService surveyService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.survey-launched-queue"), surveyService::processMessage);
  }

  @Bean
  public IntegrationFlow ccsPropertyListedFlow(
      @Autowired CCSPropertyListedService ccsPropertyListedService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.ccs-property-listed-queue"),
        ccsPropertyListedService::processCCSPropertyListed);
  }

  @Bean
  public IntegrationFlow unaddressedFlow(@Autowired UnaddressedService unaddressedService) {
    return listenToThisQueueAndSendMsgToThisMethodWithThisType(
        env.getProperty("queueconfig.unaddressed-inbound-queue"),
        unaddressedService::receiveMessage,
        CreateUacQid.class);
  }

  @Bean
  public IntegrationFlow invalidAddresseddFlow(
      @Autowired InvalidAddressService invalidAddressService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.invalid-address-inbound-queue"),
        invalidAddressService::processMessage);
  }

  @Bean
  public IntegrationFlow undeliveredMailFlow(
      @Autowired UndeliveredMailService undeliveredMailService) {
    return listenToThisQueueAndSendMsgToThisMethod(
        env.getProperty("queueconfig.undelivered-mail-queue"),
        undeliveredMailService::processMessage);
  }

  private <T> StandardIntegrationFlow listenToThisQueueAndSendMsgToThisMethod(
      String queueName, Consumer<T> methodReference) {
    return listenToThisQueueAndSendMsgToThisMethodWithThisType(
        queueName, methodReference, ResponseManagementEvent.class);
  }

  private <T> StandardIntegrationFlow listenToThisQueueAndSendMsgToThisMethodWithThisType(
      String queueName, Consumer<T> methodReference, Class expectedType) {

    return IntegrationFlows.from(
            Amqp.inboundAdapter(connectionFactory, queueName)
                .configureContainer(
                    c -> {
                      c.concurrentConsumers(consumers);
                      c.adviceChain(getRetryOperationsInterceptor(expectedType, queueName));
                    }))
        .transform(Transformers.fromJson(expectedType), e -> e.transactional(true))
        .handle(methodReference)
        .get();
  }

  private RetryOperationsInterceptor getRetryOperationsInterceptor(
      Class expectedMessageType, String queueName) {
    FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
    fixedBackOffPolicy.setBackOffPeriod(retryDelay);

    ManagedMessageRecoverer managedMessageRecoverer =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            expectedMessageType,
            logStackTraces,
            "Case Processor",
            queueName,
            retryExchange,
            quarantineExchange,
            rabbitTemplate);

    return RetryInterceptorBuilder.stateless()
        .maxAttempts(retryAttempts)
        .backOffPolicy(fixedBackOffPolicy)
        .recoverer(managedMessageRecoverer)
        .build();
  }
}
