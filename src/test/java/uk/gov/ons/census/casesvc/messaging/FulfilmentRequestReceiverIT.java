package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.convertJsonToObject;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFulfilmentRequestedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.jeasy.random.EasyRandom;
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
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class FulfilmentRequestReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final String TEST_REPLACEMENT_FULFILMENT_CODE = "UACHHT1";
  private static final String TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE = "P_OR_I1";
  private static final String TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE_SMS = "UACIT1";
  private static final UUID TEST_INDIVIDUAL_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.fulfilment-request-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testReplacementFulfilmentRequestLogged() throws InterruptedException {
    // GIVEN
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentRequestedEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(TEST_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(TEST_REPLACEMENT_FULFILMENT_CODE);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    Thread.sleep(1000);

    // THEN
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    FulfilmentRequestDTO actualFulfilmentRequest =
        convertJsonToObject(event.getEventPayload(), FulfilmentRequestDTO.class);
    assertThat(actualFulfilmentRequest.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualFulfilmentRequest.getFulfilmentCode())
        .isEqualTo(TEST_REPLACEMENT_FULFILMENT_CODE);
  }

  @Test
  public void testReplacementFulfilmentWithUacQidRequestLogged() throws InterruptedException {
    // GIVEN
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentRequestedEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(TEST_CASE_ID.toString());
    managementEvent.getPayload().getFulfilmentRequest().setIndividualCaseId(null);
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(TEST_REPLACEMENT_FULFILMENT_CODE);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacCreatedDTO uacQidCreated = new UacCreatedDTO();
    uacQidCreated.setCaseId(TEST_CASE_ID);
    uacQidCreated.setQid("123");
    uacQidCreated.setUac(UUID.randomUUID().toString());
    managementEvent.getPayload().getFulfilmentRequest().setUacQidCreated(uacQidCreated);

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    Thread.sleep(1000);

    // THEN
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(2);

    int remainingMatches = 2;

    for (Event event : events) {
      if (event.getEventType() == EventType.FULFILMENT_REQUESTED) {
        FulfilmentRequestDTO actualFulfilmentRequest =
            convertJsonToObject(event.getEventPayload(), FulfilmentRequestDTO.class);
        assertThat(actualFulfilmentRequest.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
        assertThat(actualFulfilmentRequest.getFulfilmentCode())
            .isEqualTo(TEST_REPLACEMENT_FULFILMENT_CODE);
        remainingMatches--;
      } else if (event.getEventType() == EventType.RM_UAC_CREATED) {
        PayloadDTO payload = convertJsonToObject(event.getEventPayload(), PayloadDTO.class);
        UacCreatedDTO actualUacCreated = payload.getFulfilmentRequest().getUacQidCreated();
        assertThat(actualUacCreated.getCaseId()).isEqualTo(TEST_CASE_ID);
        assertThat(actualUacCreated.getQid()).isEqualTo("123");
        assertThat(actualUacCreated.getUac()).isEqualTo(uacQidCreated.getUac());
        remainingMatches--;
      } else {
        fail("Unexpected event logged");
      }
    }

    assertThat(remainingMatches).isZero();
  }

  @Test
  public void testIndividualResponseFulfilmentRequestLogged()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL.toString());
    Case parentCase = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentRequestedEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(TEST_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);
    assertThat(responseManagementEvent.getPayload().getCollectionCase().getAddress().getEstabUprn())
        .isEqualTo(parentCase.getEstabUprn());
    assertThat(responseManagementEvent.getPayload().getFulfilmentRequest())
        .isEqualTo(managementEvent.getPayload().getFulfilmentRequest());

    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    FulfilmentRequestDTO actualFulfilmentRequest =
        convertJsonToObject(event.getEventPayload(), FulfilmentRequestDTO.class);
    assertThat(actualFulfilmentRequest.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualFulfilmentRequest.getFulfilmentCode())
        .isEqualTo(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE);

    assertThat(actualFulfilmentRequest.getContact()).isNull();

    List<Case> cases = caseRepository.findAll();
    assertThat(cases.size()).isEqualTo(2);

    Case actualParentCase = caseRepository.findById(parentCase.getCaseId()).get();
    Case actualChildCase = caseRepository.findById(TEST_INDIVIDUAL_CASE_ID).get();

    // Ensure emitted RM message matches new case
    assertThat(UUID.fromString(responseManagementEvent.getPayload().getCollectionCase().getId()))
        .isEqualTo(actualChildCase.getCaseId());

    assertThat(actualParentCase.getEstabUprn()).isEqualTo(actualChildCase.getEstabUprn());
    assertThat(actualParentCase.getAddressLine1()).isEqualTo(actualChildCase.getAddressLine1());
    assertThat(actualParentCase.getCaseRef()).isNotEqualTo(actualChildCase.getCaseRef());

    assertThat(actualChildCase.getAddressType()).isEqualTo(actualParentCase.getAddressType());
    assertThat(actualChildCase.getCaseType()).isEqualTo("HI");
    assertThat(actualChildCase.isReceiptReceived()).isEqualTo(false);
    assertThat(actualChildCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL.toString());

    assertThat(actualChildCase.getHtcWillingness()).isNull();
    assertThat(actualChildCase.getTreatmentCode()).isNull();
  }

  @Test
  public void testIndividualResponseFulfilmentRequestWithUacLogged()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL.toString());
    Case parentCase = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentRequestedEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(TEST_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    UacCreatedDTO uacQidCreated = new UacCreatedDTO();
    uacQidCreated.setCaseId(TEST_INDIVIDUAL_CASE_ID);
    uacQidCreated.setQid("123");
    uacQidCreated.setUac(UUID.randomUUID().toString());
    managementEvent.getPayload().getFulfilmentRequest().setUacQidCreated(uacQidCreated);

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);
    assertThat(responseManagementEvent.getPayload().getCollectionCase().getAddress().getEstabUprn())
        .isEqualTo(parentCase.getEstabUprn());
    assertThat(responseManagementEvent.getPayload().getFulfilmentRequest())
        .isEqualTo(managementEvent.getPayload().getFulfilmentRequest());

    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(2);

    int remainingMatches = 2;

    for (Event event : events) {
      if (event.getEventType() == EventType.FULFILMENT_REQUESTED) {
        FulfilmentRequestDTO actualFulfilmentRequest =
            convertJsonToObject(event.getEventPayload(), FulfilmentRequestDTO.class);
        assertThat(actualFulfilmentRequest.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
        assertThat(actualFulfilmentRequest.getFulfilmentCode())
            .isEqualTo(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE);

        assertThat(actualFulfilmentRequest.getContact()).isNull();
        remainingMatches--;
      } else if (event.getEventType() == EventType.RM_UAC_CREATED) {
        PayloadDTO payload = convertJsonToObject(event.getEventPayload(), PayloadDTO.class);
        UacCreatedDTO actualUacCreated = payload.getFulfilmentRequest().getUacQidCreated();
        assertThat(actualUacCreated.getCaseId()).isEqualTo(TEST_INDIVIDUAL_CASE_ID);
        assertThat(actualUacCreated.getQid()).isEqualTo("123");
        assertThat(actualUacCreated.getUac()).isEqualTo(uacQidCreated.getUac());
        remainingMatches--;
      } else {
        fail("Unexpected event loggeed");
      }
    }

    assertThat(remainingMatches).isZero();

    Event event = events.get(0);

    List<Case> cases = caseRepository.findAll();
    assertThat(cases.size()).isEqualTo(2);

    Case actualParentCase = caseRepository.findById(parentCase.getCaseId()).get();
    Case actualChildCase = caseRepository.findById(TEST_INDIVIDUAL_CASE_ID).get();

    // Ensure emitted RM message matches new case
    assertThat(UUID.fromString(responseManagementEvent.getPayload().getCollectionCase().getId()))
        .isEqualTo(actualChildCase.getCaseId());

    assertThat(actualParentCase.getEstabUprn()).isEqualTo(actualChildCase.getEstabUprn());
    assertThat(actualParentCase.getAddressLine1()).isEqualTo(actualChildCase.getAddressLine1());
    assertThat(actualParentCase.getCaseRef()).isNotEqualTo(actualChildCase.getCaseRef());

    assertThat(actualChildCase.getAddressType()).isEqualTo(actualParentCase.getAddressType());
    assertThat(actualChildCase.getCaseType()).isEqualTo("HI");
    assertThat(actualChildCase.isReceiptReceived()).isEqualTo(false);
    assertThat(actualChildCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL.toString());

    assertThat(actualChildCase.getHtcWillingness()).isNull();
    assertThat(actualChildCase.getTreatmentCode()).isNull();
  }

  @Test
  public void testIndividualResponseFulfilmentRequestSMS()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setRefusalReceived(RefusalType.HARD_REFUSAL.toString());
    Case parentCase = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentRequestedEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(TEST_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE_SMS);
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);
    assertThat(responseManagementEvent.getPayload().getCollectionCase().getAddress().getEstabUprn())
        .isEqualTo(parentCase.getEstabUprn());
    assertThat(responseManagementEvent.getPayload().getFulfilmentRequest()).isNull();

    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    FulfilmentRequestDTO actualFulfilmentRequest =
        convertJsonToObject(event.getEventPayload(), FulfilmentRequestDTO.class);
    assertThat(actualFulfilmentRequest.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualFulfilmentRequest.getFulfilmentCode())
        .isEqualTo(TEST_INDIVIDUAL_RESPONSE_FULFILMENT_CODE_SMS);

    List<Case> cases = caseRepository.findAll();
    assertThat(cases.size()).isEqualTo(2);

    Case actualParentCase = caseRepository.findById(parentCase.getCaseId()).get();
    Case actualChildCase = caseRepository.findById(TEST_INDIVIDUAL_CASE_ID).get();

    // Ensure emitted RM message matches new case
    assertThat(UUID.fromString(responseManagementEvent.getPayload().getCollectionCase().getId()))
        .isEqualTo(actualChildCase.getCaseId());

    assertThat(actualParentCase.getEstabUprn()).isEqualTo(actualChildCase.getEstabUprn());
    assertThat(actualParentCase.getAddressLine1()).isEqualTo(actualChildCase.getAddressLine1());
    assertThat(actualParentCase.getCaseRef()).isNotEqualTo(actualChildCase.getCaseRef());

    assertThat(actualChildCase.getAddressType()).isEqualTo(actualParentCase.getAddressType());
    assertThat(actualChildCase.getCaseType()).isEqualTo("HI");
    assertThat(actualChildCase.isReceiptReceived()).isEqualTo(false);
    assertThat(actualChildCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL.toString());

    assertThat(actualChildCase.getHtcWillingness()).isNull();
    assertThat(actualChildCase.getTreatmentCode()).isNull();
  }
}
