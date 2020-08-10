package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;
import static uk.gov.ons.census.casesvc.service.QidReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementReceiptEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jeasy.random.EasyRandom;
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
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;
import uk.gov.ons.census.casesvc.utility.ObjectMapperFactory;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class ReceiptReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();
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
  public void testReceiptEmitsMessageAndEventIsLoggedForCase() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue);
        QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
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
          rhCaseQueueSpy.checkExpectedMessageReceived();
      CollectionCase actualCollectionCase =
          responseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID);
      assertThat(actualCollectionCase.getReceiptReceived()).isTrue();
      assertThat(actualCollectionCase.getCreatedDateTime()).isEqualTo(caze.getCreatedDateTime());

      Optional<Case> updatedCase = caseRepository.findById(caze.getCaseId());
      assertThat(actualCollectionCase.getLastUpdated())
          .isEqualTo(updatedCase.get().getLastUpdated());

      // check the metadata is included with field CANCEL decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.CANCEL);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.RESPONSE_RECEIVED);

      Metadata metaData = responseManagementEvent.getPayload().getMetadata();
      assertThat(metaData.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
      assertThat(metaData.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);

      responseManagementEvent = rhUacQueueSpy.checkExpectedMessageReceived();
      assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
      UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
      assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
      assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(ENGLAND_HOUSEHOLD);
      assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID);

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
  }

  @Test
  public void testBlankQuestionnaireReceiptEmitsMessageAndEventIsLoggedForCase() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue);
        QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setReceiptReceived(false);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setCaseType("HH");
      caze.setAddressLevel("U");
      caze.setRefusalReceived(null);
      caze.setAddressInvalid(false);
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
          rhCaseQueueSpy.checkExpectedMessageReceived();
      CollectionCase actualCollectionCase =
          responseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID);
      assertThat(actualCollectionCase.getReceiptReceived()).isFalse();
      assertThat(actualCollectionCase.getCreatedDateTime()).isEqualTo(caze.getCreatedDateTime());

      Optional<Case> updatedCase = caseRepository.findById(caze.getCaseId());
      assertThat(actualCollectionCase.getLastUpdated())
          .isEqualTo(updatedCase.get().getLastUpdated());

      // check the metadata is included with field close decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.UPDATE);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.RESPONSE_RECEIVED);

      Metadata metaData = responseManagementEvent.getPayload().getMetadata();
      assertThat(metaData.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
      assertThat(metaData.getFieldDecision()).isEqualTo(ActionInstructionType.UPDATE);

      responseManagementEvent = rhUacQueueSpy.checkExpectedMessageReceived();
      assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
      UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
      assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
      assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(ENGLAND_HOUSEHOLD);
      assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID);

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
  }

  // The purpose of this test is to prove that we can cope with locking and updating the case when
  // multiple receipt messages are trying to lock it simultaneously
  @Test
  public void testParallelReceiptingUpdatesExpectedResponseCount() throws Exception {
    int numberOfResponses = 10;

    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setReceiptReceived(false);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressLevel("U");
      caze.setCaseType("CE");
      caze.setCeActualResponses(0);
      caze.setCeExpectedCapacity(numberOfResponses);
      caze = caseRepository.saveAndFlush(caze);

      List<Message> receiptMessages = new LinkedList<>();
      List<Integer> expectedActualResponses = new LinkedList<>();

      Case finalCaze = caze;
      IntStream.range(0, numberOfResponses)
          .forEach(
              i -> {
                expectedActualResponses.add(i + 1);

                UacQidLink uacQidLink = new UacQidLink();
                uacQidLink.setId(UUID.randomUUID());
                uacQidLink.setCaze(finalCaze);
                uacQidLink.setCcsCase(false);
                uacQidLink.setQid(
                    HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND
                        + easyRandom.nextObject(String.class));
                uacQidLink.setUac(easyRandom.nextObject(String.class));
                uacQidLink = uacQidLinkRepository.saveAndFlush(uacQidLink);

                ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
                managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
                managementEvent.getEvent().setTransactionId(UUID.randomUUID());

                String json = convertObjectToJson(managementEvent);
                Message message =
                    MessageBuilder.withBody(json.getBytes())
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build();
                receiptMessages.add(message);
              });

      // WHEN
      assertThat(caze.getCeActualResponses()).isEqualTo(0);

      receiptMessages.stream()
          .parallel()
          .forEach(message -> rabbitQueueHelper.sendMessage(inboundQueue, message));

      // THEN
      Case actualCase =
          pollDatabaseUntilCorrectActualResponseCount(caze.getCaseId(), numberOfResponses, 100);
      assertThat(actualCase.getCeActualResponses())
          .as("ActualResponses Count")
          .isEqualTo(numberOfResponses);
      assertThat(actualCase.isReceiptReceived()).as("Case Receipted").isTrue();

      checkExpectedResponsesEmitted(expectedActualResponses, rhCaseQueueSpy, caze.getCaseId());
    }
  }

  private void checkExpectedResponsesEmitted(
      List<Integer> expectedActualResponses, QueueSpy queueSpy, UUID caseId) throws IOException {

    List<Integer> actualResponsesList = collectAllActualResponseCountsForCaseId(queueSpy, caseId);

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

      Thread.sleep(5000);
    }

    return actualCase;
  }

  private boolean isStringFormattedAsUTCDate(String dateAsString) {
    try {
      OffsetDateTime.parse(dateAsString);
      return true;
    } catch (DateTimeParseException dtpe) {
      return false;
    }
  }

  private List<Integer> collectAllActualResponseCountsForCaseId(QueueSpy queueSpy, UUID caseId)
      throws IOException {
    List<String> jsonList = new ArrayList<>();
    queueSpy.getQueue().drainTo(jsonList);

    List<Integer> actualActualResponseCountList = new ArrayList<>();

    for (String jsonString : jsonList) {
      ResponseManagementEvent responseManagementEvent =
          objectMapper.readValue(jsonString, ResponseManagementEvent.class);

      assertThat(responseManagementEvent.getPayload().getCollectionCase().getId())
          .isEqualTo(caseId);

      actualActualResponseCountList.add(
          responseManagementEvent.getPayload().getCollectionCase().getCeActualResponses());
    }

    return actualActualResponseCountList;
  }
}
