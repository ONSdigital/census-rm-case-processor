package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;
import static uk.gov.ons.census.casesvc.service.QidReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementReceiptEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.jeasy.random.EasyRandom;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class ReceiptReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private final String ENGLAND_HOUSEHOLD = "0134567890123456";
  private static final String TEST_UAC = easyRandom.nextObject(String.class);
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "21";
  public static final String BLANK_QUESTIONNAIRE_RECEIVED = "Blank questionnaire received";


  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.questionnaire-linked-inbound-queue}")
  private String questionnaireLinkedQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(actionQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testReceiptEmitsMessageAndEventIsLoggedForCase()
      throws InterruptedException, IOException, JSONException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setCaseType("HH");
    caze.setAddressLevel("U");
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(false);
    uacQidLink.setQid(ENGLAND_HOUSEHOLD);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // check messages sent
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(rhCaseOutboundQueue);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getReceiptReceived()).isTrue();

    // check the metadata is included with field close decision
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.CLOSE);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.RESPONSE_RECEIVED);

    Metadata metaData = responseManagementEvent.getPayload().getMetadata();
    assertThat(metaData.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metaData.getFieldDecision()).isEqualTo(ActionInstructionType.CLOSE);

    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(rhUacOutboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(ENGLAND_HOUSEHOLD);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(ENGLAND_HOUSEHOLD);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();
    assertThat(actualUacQidLink.isBlankQuestionnaire()).isFalse();

    // Test date saved format here
    String utcDateAsString = new JSONObject(event.getEventPayload()).getString("dateTime");
    assertThat(isStringFormattedAsUTCDate(utcDateAsString)).isTrue();
  }

  @Test
  public void testBlankQuestionnaireReceiptEmitsMessageAndEventIsLoggedForCase()
      throws InterruptedException, IOException, JSONException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setCaseType("HH");
    caze.setAddressLevel("U");
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(false);
    uacQidLink.setQid(ENGLAND_HOUSEHOLD);
    uacQidLink.setUac(TEST_UAC);
    uacQidLink.setBlankQuestionnaire(false);
    uacQidLink.setActive(true);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    managementEvent.getPayload().getResponse().setUnreceipt(true);
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // check messages sent
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(rhCaseOutboundQueue);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getReceiptReceived()).isFalse();

    // check the metadata is included with field close decision
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.UPDATE);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.RESPONSE_RECEIVED);

    Metadata metaData = responseManagementEvent.getPayload().getMetadata();
    assertThat(metaData.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metaData.getFieldDecision()).isEqualTo(ActionInstructionType.UPDATE);

    // TODO:  Could test the FWMT adapter queue instead?
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(rhUacOutboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(ENGLAND_HOUSEHOLD);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(actualCase.isReceiptReceived()).isFalse();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo(BLANK_QUESTIONNAIRE_RECEIVED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(ENGLAND_HOUSEHOLD);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();
    assertThat(actualUacQidLink.isBlankQuestionnaire()).isTrue();

    // Test date saved format here
    String utcDateAsString = new JSONObject(event.getEventPayload()).getString("dateTime");
    assertThat(isStringFormattedAsUTCDate(utcDateAsString)).isTrue();
  }

  // TODO: Test for already receipted blank q're


  @Test
  public void testParallelReceiptAndLinkingOfReceiptedQidUpdatesToCorrectNumbeAndIsReceipted()
      throws Exception {
    int numberOfReceiptsAndLinkToSend = 3;
    int expectedResponseCount = numberOfReceiptsAndLinkToSend * 2;

    // GIVEN
    BlockingQueue<String> rhCaseOutboundQueue =
        rabbitQueueHelper.listen(rhCaseQueue, expectedResponseCount + 10);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setReceiptReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setAddressLevel("U");
    caze.setCaseType("CE");
    caze.setCeActualResponses(0);
    caze.setCeExpectedCapacity(expectedResponseCount);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(false);
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    assertThat(caze.getCeActualResponses()).isEqualTo(0);
    CopyOnWriteArrayList<Integer> expectedActualResponses = new CopyOnWriteArrayList<Integer>();
    final UUID caseId = caze.getCaseId();

    Message[] qidLinkingMessages =
        buildLinkReceiptedQidToCaseMsgs(caseId.toString(), numberOfReceiptsAndLinkToSend);

    IntStream.range(0, numberOfReceiptsAndLinkToSend)
        .parallel()
        .forEach(
            count -> {
              rabbitQueueHelper.sendMessage(inboundQueue, message);
              rabbitQueueHelper.sendMessage(questionnaireLinkedQueue, qidLinkingMessages[count]);

              expectedActualResponses.add(count + 1);
              expectedActualResponses.add(numberOfReceiptsAndLinkToSend + count + 1);
            });

    // THEN
    Case actualCase =
        pollDatabaseUntilCorrectActualResponseCount(caseId, expectedResponseCount, 100);
    assertThat(actualCase.getCeActualResponses())
        .as("ActualResponses Count")
        .isEqualTo(expectedResponseCount);
    assertThat(actualCase.isReceiptReceived()).as("Case Receipted").isTrue();

    checkExpectedResponsesEmitted(
        expectedActualResponses, rhCaseOutboundQueue, caze.getCaseId().toString());
  }

  private void checkExpectedResponsesEmitted(
      List<Integer> expectedActualResponses,
      BlockingQueue<String> rhCaseOutboundQueue,
      String caseId)
      throws IOException {

    List<Integer> actualResponsesList =
        rabbitQueueHelper.collectAllActualResponseCountsForCaseId(rhCaseOutboundQueue, caseId);

    assertThat(actualResponsesList).hasSameElementsAs(expectedActualResponses);
  }

  private Case pollDatabaseUntilCorrectActualResponseCount(
      UUID caseID, int expectedActualResponses, int retryAttempts) throws InterruptedException {

    Case actualCase = null;

    for (int i = 0; i < retryAttempts; i++) {
      actualCase = caseRepository.findById(caseID).get();

      if (actualCase.getCeActualResponses() >= expectedActualResponses) {
        return actualCase;
      }

      System.out.println("Current Actual Count: " + actualCase.getCeActualResponses());

      Thread.sleep(1000);
    }

    return actualCase;
  }

  private Message[] buildLinkReceiptedQidToCaseMsgs(String caseId, int count) {

    Message[] qidLinkingMsgs = new Message[count];

    for (int i = 0; i < count; i++) {
      UacQidLink receiptedUacQid = new UacQidLink();
      receiptedUacQid.setId(UUID.randomUUID());
      receiptedUacQid.setBatchId(UUID.randomUUID());
      receiptedUacQid.setUac("test uac");
      receiptedUacQid.setQid("21" + i);
      receiptedUacQid.setActive(false);
      UacQidLink createdUacQidLink = uacQidLinkRepository.save(receiptedUacQid);

      String expectedQuestionnaireId = createdUacQidLink.getQid();
      ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());
      UacDTO uac = new UacDTO();
      uac.setCaseId(caseId);
      uac.setQuestionnaireId(expectedQuestionnaireId);
      managementEvent.getPayload().setUac(uac);

      qidLinkingMsgs[i] =
          MessageBuilder.withBody(convertObjectToJson(managementEvent).getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
    }

    return qidLinkingMsgs;
  }

  private boolean isStringFormattedAsUTCDate(String dateAsString) {
    try {
      OffsetDateTime.parse(dateAsString);
      return true;
    } catch (DateTimeParseException dtpe) {
      return false;
    }
  }
}
