package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;
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
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
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
  private static final String TEST_QID = easyRandom.nextObject(String.class);

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
  public void testGoodQuestionnaireLinkedForUnreceiptedCase()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(false);
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    UacQidLink testUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testUacQidLink.setQid(TEST_QID);
    testUacQidLink.setActive(true);
    testUacQidLink.setCaze(null);
    testUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID().toString());
    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Uac updated message sent
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);

    // Check message contains expected data
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventType.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.isActive()).isTrue();

    // Check database Case is still unreceipted and response received not set
    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isReceiptReceived()).isFalse();

    // Check database Case is now linked to questionnaire and still unreceipted
    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findAll();
    assertThat(uacQidLinks.size()).isEqualTo(1);
    testUacQidLink = uacQidLinks.get(0);
    assertThat(testUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(testUacQidLink.isActive()).isTrue();

    // Check database for expected log event
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventType()).isEqualTo(EventType.QUESTIONNAIRE_LINKED);
    assertThat(event.getEventDescription()).isEqualTo("Questionnaire Linked");
  }

  @Test
  public void testGoodQuestionnaireLinkedAfterReceiptedCase()
      throws InterruptedException, IOException {
    testGoodQuestionnaireLinkedForReceiptedCase(true);
  }

  @Test
  public void testGoodQuestionnaireLinkedBeforeReceiptedCase()
      throws InterruptedException, IOException {
    testGoodQuestionnaireLinkedForReceiptedCase(false);
  }

  private void testGoodQuestionnaireLinkedForReceiptedCase(boolean caseReceiptedValue)
      throws IOException, InterruptedException {

    // GIVEN
    BlockingQueue<String> outboundUacQueue = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> outboundCaseQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setReceiptReceived(caseReceiptedValue);
    testCase.setUacQidLinks(null);
    testCase.setEvents(null);
    caseRepository.saveAndFlush(testCase);

    UacQidLink testUacQidLink = easyRandom.nextObject(UacQidLink.class);
    testUacQidLink.setQid(TEST_QID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);
    testUacQidLink.setEvents(null);
    uacQidLinkRepository.saveAndFlush(testUacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID().toString());
    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID.toString());
    uac.setQuestionnaireId(TEST_QID);

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN

    // Check Uac updated message sent
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundUacQueue);

    // Check message contains expected data
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventType.UAC_UPDATED);
    UacDTO actualUac = responseManagementEvent.getPayload().getUac();
    assertThat(actualUac.getQuestionnaireId()).isEqualTo(TEST_QID);
    assertThat(actualUac.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualUac.isActive()).isTrue();

    // Check Case updated message sent
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundCaseQueue);

    // Check message contains expected data
    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventType.CASE_UPDATED);
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

    // Check database for expected log event
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventType()).isEqualTo(EventType.QUESTIONNAIRE_LINKED);
    assertThat(event.getEventDescription()).isEqualTo("Questionnaire Linked");
  }
}
