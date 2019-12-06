package uk.gov.ons.census.casesvc.messaging;

import org.jeasy.random.EasyRandom;
import org.json.JSONException;
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

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementReceiptEvent;

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
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testBlankQuestionnaireReceiptHappyPath()
      throws InterruptedException, IOException, JSONException {
    // GIVEN
    BlockingQueue<String> rhUacOutboundQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> rhCaseOutboundQueue = rabbitQueueHelper.listen(rhCaseQueue);
    BlockingQueue<String> fieldworkAdapterUacUpdated =
        rabbitQueueHelper.listen(fieldworkAdapterQueue);
    BlockingQueue<String> fieldworkFollowupQueue = rabbitQueueHelper.listen(fieldWorkFollowupQueue);

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
    uacQidLink.setQid(TEST_QID_ID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    // TODO Look at changing the management event to PQRS
    ResponseManagementEvent managementEvent = getTestResponseManagementReceiptEvent();
    managementEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    managementEvent.getPayload().getResponse().setUnreceipt(false);

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
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(fieldworkAdapterUacUpdated);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);

    UacQidLink actualUacQidLink = event.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID_ID);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualUacQidLink.isActive()).isFalse();

    // sending unreceipted event
    ResponseManagementEvent unreceiptedEvent = createResponseReceivedUnreceiptedEvent();
    unreceiptedEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());
    unreceiptedEvent.getEvent().setTransactionId(UUID.randomUUID());
    unreceiptedEvent.getPayload().getResponse().setUnreceipt(true);

    rabbitQueueHelper.sendMessage(inboundQueue, unreceiptedEvent);

    responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceivedUnreceipt(fieldworkAdapterUacUpdated);
    UacDTO unreceiptUacDTO = responseManagementEvent.getPayload().getUac();
    assertThat(unreceiptUacDTO.getUnreceipted()).isTrue();

    FieldWorkFollowup fieldWorkFollowup = rabbitQueueHelper.checkFieldWorkFollowUpSent(fieldworkFollowupQueue);

    assertThat(fieldWorkFollowup.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(fieldWorkFollowup.getBlankQreReturned()).isTrue();
    actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isFalse();


    //    responseManagementEvent =
    // rabbitQueueHelper.checkExpectedMessageReceived(rhUacOutboundQueue);
    ////
    // assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    ////    actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    ////    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    ////    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_NON_CCS_QID_ID);
    ////    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    ////
    ////    responseManagementEvent =
    // rabbitQueueHelper.checkExpectedMessageReceived(fieldworkAdapterUacUpdated);
    ////
    // assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    ////    actualUacDTOObject = responseManagementEvent.getPayload().getUac();
    ////    assertThat(actualUacDTOObject.getUac()).isEqualTo(TEST_UAC);
    ////    assertThat(actualUacDTOObject.getQuestionnaireId()).isEqualTo(TEST_NON_CCS_QID_ID);
    ////    assertThat(actualUacDTOObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

  }

  private ResponseManagementEvent createResponseReceivedUnreceiptedEvent() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();

    EventDTO event = managementEvent.getEvent();
    event.setType(EventTypeDTO.RESPONSE_RECEIVED);
    event.setSource("RECEIPT_SERVICE");
    event.setChannel("EQ");

    PayloadDTO payload = managementEvent.getPayload();
    payload.setUac(null);
    payload.setCollectionCase(null);
    payload.setRefusal(null);
    payload.setPrintCaseSelected(null);
    payload.getResponse().setUnreceipt(true);

    return managementEvent;
  }
}
