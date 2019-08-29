package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

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
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
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
public class QuestionnaireLinkedReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final String TEST_HH_QID = "0112345678901234";
  private final String TEST_HI_QID = "2112345678901234";
  private static final String QUESTIONNAIRE_LINKED_CHANNEL = "FIELD";
  private static final String QUESTIONNAIRE_LINKED_SOURCE = "FIELDWORK_GATEWAY";

  @Value("${queueconfig.questionnaire-linked-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

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
    rabbitQueueHelper.purgeQueue(actionQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testGoodQuestionnaireLinkedForUnreceiptedCase() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    UacQidLink testUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testUacQidLink.setQid(TEST_HH_QID);
    testUacQidLink.setActive(true);
    testUacQidLink.setCaze(null);
    testUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_HH_QID);
    managementEvent.getPayload().setUac(uac);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Uac updated message sent and contains expected data
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_HH_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isTrue();

    // Check database Case is still unreceipted and response received not set
    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isFalse();

    // Check database Case is now linked to questionnaire and still unreceipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    testUacQidLink = uacQidLinks.get(0);
    assertThat(testUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(testUacQidLink.isActive()).isTrue();

    validateEvents(eventRepository.findAll(), TEST_HH_QID);
  }

  @Test
  public void testGoodQuestionnaireLinkedAfterCaseReceipted() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(true);
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    UacQidLink testUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testUacQidLink.setQid(TEST_HH_QID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);
    testUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_HH_QID);
    managementEvent.getPayload().setUac(uac);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Uac updated message sent and contains expected data
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_HH_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isFalse();

    // Check Case updated message not sent
    rabbitQueueHelper.checkMessageIsNotReceived(outboundCaseQueue, 5);

    // Check database that Case is still receipted and has response received set
    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // Check database that Case is now linked to questionnaire and still receipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    testUacQidLink = uacQidLinks.get(0);
    assertThat(testUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(testUacQidLink.isActive()).isFalse();

    validateEvents(eventRepository.findAll(), TEST_HH_QID);
  }

  @Test
  public void testGoodQuestionnaireLinkedBeforeCaseReceipted() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    UacQidLink testUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testUacQidLink.setQid(TEST_HH_QID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);
    testUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_HH_QID);
    managementEvent.getPayload().setUac(uac);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Uac updated message sent and contains expected data
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_HH_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.getActive()).isFalse();

    // Check Case updated message sent and contains expected data
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundCaseQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());

    // Check database that Case is still receipted and has response received set
    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // Check database that Case is now linked to questionnaire and still receipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    testUacQidLink = uacQidLinks.get(0);
    assertThat(testUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(testUacQidLink.isActive()).isFalse();

    validateEvents(eventRepository.findAll(), TEST_HH_QID);
  }

  @Test
  public void testGoodIndividualQuestionnaireLinked() throws Exception {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testHHCase = easyRandom.nextObject(Case.class);
    testHHCase.setCaseId(TEST_CASE_ID);
    testHHCase.setReceiptReceived(false);
    testHHCase.setUacQidLinks(null);
    testHHCase.setEvents(null);
    caseRepository.saveAndFlush(testHHCase);

    UacQidLink testHIUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(null);
    testHIUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testHIUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    UacDTO uac = new UacDTO();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_HI_QID);
    managementEvent.getPayload().setUac(uac);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Case created message sent and contains expected data
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundCaseQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getCaseType()).isEqualTo("HI");

    String expectedHICaseId = actualCollectionCase.getId();

    // Check Uac updated message sent and contains expected data
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_HI_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(expectedHICaseId);
    assertThat(actualUac.getActive()).isTrue();

    // Check database HI Case created as expected
    Case actualHHCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    Case actualHICase = caseRepository.findByCaseId(UUID.fromString(expectedHICaseId)).get();
    assertThat(actualHICase.getCaseType()).isEqualTo("HI");
    assertThat(actualHICase.getArid()).isEqualTo(actualHHCase.getArid());
    assertThat(actualHICase.getEstabArid()).isEqualTo(actualHHCase.getEstabArid());

    // Check database that HI Case is linked to UacQidLink
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    testHIUacQidLink = uacQidLinks.get(0);
    assertThat(actualHICase.getCaseRef()).isEqualTo(testHIUacQidLink.getCaze().getCaseRef());

    validateEvents(eventRepository.findAll(), TEST_HI_QID);

    // Check no other messages sent
    rabbitQueueHelper.checkMessageIsNotReceived(outboundUacQueue, 2);
    rabbitQueueHelper.checkMessageIsNotReceived(outboundCaseQueue, 2);
  }

  private void validateEvents(List<Event> events, String expectedQuestionnaireId)
      throws JSONException {
    assertThat(events.size()).isEqualTo(1);

    Event event = events.get(0);
    assertThat(event.getEventChannel()).isEqualTo(QUESTIONNAIRE_LINKED_CHANNEL);
    assertThat(event.getEventSource()).isEqualTo(QUESTIONNAIRE_LINKED_SOURCE);
    assertThat(event.getEventType()).isEqualTo(EventType.QUESTIONNAIRE_LINKED);
    assertThat(event.getEventDescription()).isEqualTo("Questionnaire Linked");

    JSONObject payload = new JSONObject(event.getEventPayload());
    assertThat(payload.length()).isEqualTo(2);
    assertThat(payload.getString("caseId")).isEqualTo(TEST_CASE_ID.toString());
    assertThat(payload.getString("questionnaireId")).isEqualTo(expectedQuestionnaireId);
  }
}
