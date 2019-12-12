package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementReceiptEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
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
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
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
  private final String TEST_NON_CCS_QID_ID = "1234567890123456";
  private final String TEST_CCS_QID_ID = "7134567890123456";
  private final String TEST_QID_ID = "1234567890123456";
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.fieldwork-followup-queue}")
  private String fieldWorkFollowupQueue;

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
    rabbitQueueHelper.purgeQueue(fieldWorkFollowupQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testReceiptEmitsMessageAndEventIsLoggedForNonCCSCase()
      throws InterruptedException, IOException, JSONException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setCcsCase(false);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(false);
    uacQidLink.setQid(TEST_NON_CCS_QID_ID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getPayload().getResponse().setUnreceipt(false);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, managementEvent);

    // THEN

    // check messages sent
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(rhCaseOutboundQueue);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getReceiptReceived()).isTrue();

    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(rhUacOutboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_NON_CCS_QID_ID);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isCcsCase()).isFalse();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_NON_CCS_QID_ID);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();

    // Test date saved format here
    String utcDateAsString = new JSONObject(event.getEventPayload()).getString("dateTime");
    assertThat(isStringFormattedAsUTCDate(utcDateAsString)).isTrue();
  }

  @Test
  public void testReceiptDoesNotEmitMessagesButEventIsLoggedForCCSCase()
      throws InterruptedException, JSONException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setCcsCase(true);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(true);
    uacQidLink.setQid(TEST_CCS_QID_ID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getPayload().getResponse().setUnreceipt(false);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // check no messages sent
    rabbitQueueHelper.checkMessageIsNotReceived(rhUacOutboundQueue, 5);
    rabbitQueueHelper.checkMessageIsNotReceived(rhCaseOutboundQueue, 2);

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isCcsCase()).isTrue();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_CCS_QID_ID);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();

    // Test date saved format here
    String utcDateAsString = new JSONObject(event.getEventPayload()).getString("dateTime");
    assertThat(isStringFormattedAsUTCDate(utcDateAsString)).isTrue();
  }

  @Test
  public void testPQRSSendReciptForQidThenQMSendBlankEvent()
          throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);
    BlockingQueue<String> fieldworkFollowupQueue = rabbitQueueHelper.listen(fieldWorkFollowupQueue);

    Case caze = setUpTestCase();
    setUpUacQidLink(caze);

    ResponseManagementEvent managementEvent = createValidReceiptEvent();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, managementEvent);

    // THEN
    // check messages sent out after 1st unreceipted msg
    boolean expectedReceipted = true;
    checkMsgSentToRhCaseAndUac(
            expectedReceipted, rhCaseOutboundQueue, rhUacOutboundQueue);
    checkDatabaseUpdatedCorrectly(expectedReceipted, 1);

    // Now send out a unreceipted event
    ResponseManagementEvent unreceiptedEvent = createBlankQuestionaireEvent();
    rabbitQueueHelper.sendMessage(inboundQueue, unreceiptedEvent);

    checkMsgSentToRhCaseAndUac(
            false, rhCaseOutboundQueue, rhUacOutboundQueue);

    // Finally a new Event we're really interested in, a fieldworkFollowup should be sent out to
    // fieldworkAdapter
    FieldWorkFollowup fieldWorkFollowup =
            rabbitQueueHelper.checkExpectedMessageReceived(
                    fieldworkFollowupQueue, FieldWorkFollowup.class);

    assertThat(fieldWorkFollowup.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(fieldWorkFollowup.getBlankQreReturned()).isTrue();

    // Also check all the database gubbins again
    expectedReceipted = false;
    checkDatabaseUpdatedCorrectly(expectedReceipted, 2);
  }

  private void checkDatabaseUpdatedCorrectly(boolean expectedReceipted, int expectedEventCount) {
    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isEqualTo(expectedReceipted);

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(expectedEventCount);
    Event event = events.get(expectedEventCount - 1);
    assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();
  }

  private void setUpUacQidLink(Case caze) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setCcsCase(false);
    uacQidLink.setQid(TEST_QID_ID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);
  }

  private Case setUpTestCase() {
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setCcsCase(false);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze = caseRepository.saveAndFlush(caze);

    return caze;
  }

  private ResponseManagementEvent createBlankQuestionaireEvent() {
    return createResponseReceivedUnreceiptedEvent(true);
  }

  private ResponseManagementEvent createValidReceiptEvent() {
    return createResponseReceivedUnreceiptedEvent(false);
  }

  private ResponseManagementEvent createResponseReceivedUnreceiptedEvent(boolean unreceiped) {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.RESPONSE_RECEIVED);
    event.setSource("RECEIPT_SERVICE");
    event.setChannel("PQRS");
    managementEvent.setEvent(event);

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setQuestionnaireId(TEST_QID_ID);
    responseDTO.setUnreceipt(unreceiped);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setResponse(responseDTO);
    managementEvent.setPayload(payloadDTO);

    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    return managementEvent;
  }

  private void checkMsgSentToRhCaseAndUac(
          boolean receipted,
          BlockingQueue<String> rhCaseOutboundQueue,
          BlockingQueue<String> rhUacOutboundQueue)
          throws IOException, InterruptedException {

    // Check That Rh receives a case with corrected receipted value
    CollectionCase actualCollectionCase =
            rabbitQueueHelper
                    .checkExpectedMessageReceived(rhCaseOutboundQueue)
                    .getPayload()
                    .getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getReceiptReceived()).isEqualTo(receipted);

    // Check that rh receive an uac_updated event
    ResponseManagementEvent responseManagementEvent =
            rabbitQueueHelper.checkExpectedMessageReceived(rhUacOutboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
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
