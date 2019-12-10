package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.*;
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
@RunWith(SpringJUnit4ClassRunner.class)
public class BlankQuestionnaireIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private final String TEST_QID_ID = "1234567890123456";
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.receipt-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.fieldwork-uacupdated-queue}")
  private String fieldworkAdapterQueue;

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
    rabbitQueueHelper.purgeQueue(fieldworkAdapterQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testPQRSSendReciptForQidThenQMSendBlankEvent()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);
    BlockingQueue<String> fieldworkAdapterUacUpdated =
        rabbitQueueHelper.listen(fieldworkAdapterQueue);
    BlockingQueue<String> fieldworkFollowupQueue = rabbitQueueHelper.listen(fieldWorkFollowupQueue);

    Case caze = setUpTestCase();
    setUpUacQidLink(caze);

    ResponseManagementEvent managementEvent = createValidReceiptEvent();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, managementEvent);

    // THEN
    // check messages sent out after 1st unreceipted msg
    boolean expectedReceipted = true;
    checkMsgSentToRhCaseAndUacAndToFieldUAcUpdated(
        expectedReceipted, rhCaseOutboundQueue, rhUacOutboundQueue, fieldworkAdapterUacUpdated);
    checkDatabaseUpdatedCorrectly(expectedReceipted, 1);

    // Now send out a unreceipted event
    ResponseManagementEvent unreceiptedEvent = createBlankQuestionaireEvent();
    rabbitQueueHelper.sendMessage(inboundQueue, unreceiptedEvent);

    checkMsgSentToRhCaseAndUacAndToFieldUAcUpdated(
        false, rhCaseOutboundQueue, rhUacOutboundQueue, fieldworkAdapterUacUpdated);

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

  @Test
  public void testQMSendBlankForQidThenPQRSSendsItsReceipt()
      throws IOException, InterruptedException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);
    BlockingQueue<String> fieldworkAdapterUacUpdated =
        rabbitQueueHelper.listen(fieldworkAdapterQueue);
    BlockingQueue<String> fieldworkFollowupQueue = rabbitQueueHelper.listen(fieldWorkFollowupQueue);

    Case caze = setUpTestCase();
    setUpUacQidLink(caze);

    ResponseManagementEvent blankQuestionaireEvent = createResponseReceivedUnreceiptedEvent(true);

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, blankQuestionaireEvent);

    // THEN
    boolean expectedReceipted = false;
    checkMsgSentToRhCaseAndUacAndToFieldUAcUpdated(
        expectedReceipted, rhCaseOutboundQueue, rhUacOutboundQueue, fieldworkAdapterUacUpdated);
    checkDatabaseUpdatedCorrectly(expectedReceipted, 1);

    FieldWorkFollowup fieldWorkFollowup =
        rabbitQueueHelper.checkExpectedMessageReceived(
            fieldworkFollowupQueue, FieldWorkFollowup.class);

    assertThat(fieldWorkFollowup.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(fieldWorkFollowup.getBlankQreReturned()).isTrue();

    // Now we receive the PQRS 'valid' receipt, where they believe it to receipted and all is happy.
    ResponseManagementEvent validReceiptEvent = createValidReceiptEvent();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, validReceiptEvent);

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isEqualTo(false);

    // Send nothing to no one, the uacqid/case should remain unreceipted
    // With this ordering we wait a long time for the 1st one, if the others were ready they'd be
    // there
    rabbitQueueHelper.checkMessageIsNotReceived(rhUacOutboundQueue, 5);
    rabbitQueueHelper.checkMessageIsNotReceived(rhCaseOutboundQueue, 1);
    rabbitQueueHelper.checkMessageIsNotReceived(fieldworkAdapterUacUpdated, 1);
    rabbitQueueHelper.checkMessageIsNotReceived(fieldworkFollowupQueue, 1);
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

  private void checkMsgSentToRhCaseAndUacAndToFieldUAcUpdated(
      boolean receipted,
      BlockingQueue<String> rhCaseOutboundQueue,
      BlockingQueue<String> rhUacOutboundQueue,
      BlockingQueue<String> fieldworkAdapterQueue)
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

    // Check that fwmtadapter gets a uacUpdated Event with correct receipted
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(fieldworkAdapterQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    // This is the opposite of Receipted, with a BlandQuestionaire Event 'receipted' will be false.
    assertThat(actualUacDTOObject.getUnreceipted()).isNotEqualTo(receipted);
  }
}
