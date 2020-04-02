package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.time.OffsetDateTime;
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
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentInformation;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
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
public class UndeliveredMailReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final long TEST_CASE_REF = easyRandom.nextLong();
  private static final String TEST_QID = easyRandom.nextObject(String.class);
  private static final String TEST_UAC = easyRandom.nextObject(String.class);

  @Value("${queueconfig.undelivered-mail-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(actionQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testUndeliveredMailWithQid() throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(actionQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setSurvey("CENSUS");
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setCaze(caze);
    uacQidLink.setQid(TEST_QID);
    uacQidLink.setUac(TEST_UAC);
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setQuestionnaireId(TEST_QID);

    String json = convertObjectToJson(event);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // check the emitted eventDTO
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.UPDATE);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    CollectionCase actualCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event loggedEvent = events.get(0);
    assertThat(loggedEvent.getEventDescription()).isEqualTo("Undelivered mail reported");
    UacQidLink actualUacQidLink = loggedEvent.getUacQidLink();
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
    assertThat(actualUacQidLink.getUac()).isEqualTo(TEST_UAC);
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
  }

  @Test
  public void testUndeliveredMailWithCaseRef() throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(actionQueue);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setCaseRef(TEST_CASE_REF);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setSurvey("CENSUS");
    caseRepository.saveAndFlush(caze);

    ResponseManagementEvent event = new ResponseManagementEvent();
    event.setEvent(new EventDTO());
    event.getEvent().setDateTime(OffsetDateTime.now());
    event.getEvent().setType(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    event.setPayload(new PayloadDTO());
    event.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    event.getPayload().getFulfilmentInformation().setCaseRef(Long.toString(TEST_CASE_REF));

    String json = convertObjectToJson(event);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // check the emitted eventDTO
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.UPDATE);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.UNDELIVERED_MAIL_REPORTED);
    CollectionCase actualCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCase.getCaseRef()).isEqualTo(Long.toString(TEST_CASE_REF));
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event loggedEvent = events.get(0);
    assertThat(loggedEvent.getEventDescription()).isEqualTo("Undelivered mail reported");
    Case actualEventCase = loggedEvent.getCaze();
    assertThat(actualEventCase.getCaseRef()).isEqualTo(TEST_CASE_REF);
  }
}
