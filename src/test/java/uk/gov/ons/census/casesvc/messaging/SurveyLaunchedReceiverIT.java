package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRespondentAuthenticatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementSurveyLaunchedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.List;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyLaunchedReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final String TEST_QID = easyRandom.nextObject(String.class);
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.survey-launched-queue}")
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
  public void testSurveyLaunchLogsEventSetsFlagAndEmitsCorrectCaseUpdatedEvent() throws Exception {
    // GIVEN

    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressInvalid(false);
      caze.setRefusalReceived(null);
      caze.setReceiptReceived(false);
      caze.setSurveyLaunched(false);
      caze = caseRepository.saveAndFlush(caze);

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setCaze(caze);
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac(TEST_UAC);
      uacQidLinkRepository.saveAndFlush(uacQidLink);

      ResponseManagementEvent surveyLaunchedEvent = getTestResponseManagementSurveyLaunchedEvent();
      surveyLaunchedEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());

      String json = convertObjectToJson(surveyLaunchedEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // THEN
      ResponseManagementEvent caseUpdatedEvent = rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(caseUpdatedEvent.getPayload().getCollectionCase().getId()).isEqualTo(TEST_CASE_ID);
      assertThat(caseUpdatedEvent.getPayload().getCollectionCase().isSurveyLaunched()).isTrue();

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("Survey launched");
      UacQidLink actualUacQidLink = event.getUacQidLink();
      assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
      assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
      assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
      assertThat(actualUacQidLink.isActive()).isFalse();
    }
  }

  @Test
  public void testRespondentAuthenticatedEventTypeLoggedAndRejected()
      throws InterruptedException, JSONException {
    // GIVEN
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setQid(TEST_QID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent respondentAuthenticatedEvent =
        getTestResponseManagementRespondentAuthenticatedEvent();
    respondentAuthenticatedEvent.getPayload().getResponse().setQuestionnaireId(uacQidLink.getQid());

    String json = convertObjectToJson(respondentAuthenticatedEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);
    Thread.sleep(1000);

    // THEN
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventChannel()).isEqualTo("Test channel");
    assertThat(event.getEventSource()).isEqualTo("Test source");
    assertThat(event.getEventDescription()).isEqualTo("Respondent authenticated");
    assertThat(event.getEventType()).isEqualTo(EventType.RESPONDENT_AUTHENTICATED);

    String expectedEventPayloadJson =
        convertObjectToJson(respondentAuthenticatedEvent.getPayload().getResponse());
    String actualEventPayloadJson = event.getEventPayload();
    JSONAssert.assertEquals(
        actualEventPayloadJson, expectedEventPayloadJson, JSONCompareMode.STRICT);
  }
}
