package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFieldUpdatedEvent;
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
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class FieldCaseUpdatedReceiverIT {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Value("${queueconfig.case-updated-queue}")
  private String caseUpdatedQueueName;

  @Value("${queueconfig.field-case-updated-queue}")
  private String inboundQueue;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(caseUpdatedQueueName);
    rabbitQueueHelper.purgeQueue(inboundQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testCeExpectedCapacityUpdated() throws IOException, InterruptedException {
    BlockingQueue<String> fieldOutboundQueue = rabbitQueueHelper.listen(caseUpdatedQueueName);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");
    caze.setCeActualResponses(5);
    caze.setCeExpectedCapacity(8);
    caze = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    managementEvent.getPayload().getCollectionCase().setId(TEST_CASE_ID.toString());
    managementEvent.getPayload().getCollectionCase().setCeExpectedCapacity(5);

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
        rabbitQueueHelper.checkExpectedMessageReceived(fieldOutboundQueue);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getCeExpectedCapacity()).isEqualTo(5);

    // check the metadata is included with field CANCEL decision
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.CANCEL);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(actualCase.getCeExpectedCapacity()).isEqualTo(5);

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo("Field case update received");
  }

  @Test
  public void testCeExpectedCapacityUpdatedAndNoCancelSent()
      throws IOException, InterruptedException {
    BlockingQueue<String> fieldOutboundQueue = rabbitQueueHelper.listen(caseUpdatedQueueName);

    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setReceiptReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");
    caze.setCeActualResponses(5);
    caze.setCeExpectedCapacity(8);
    caze = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    managementEvent.getPayload().getCollectionCase().setId(TEST_CASE_ID.toString());
    managementEvent.getPayload().getCollectionCase().setCeExpectedCapacity(6);

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
        rabbitQueueHelper.checkExpectedMessageReceived(fieldOutboundQueue);
    CollectionCase actualCollectionCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualCollectionCase.getId()).isEqualTo(TEST_CASE_ID.toString());
    assertThat(actualCollectionCase.getCeExpectedCapacity()).isEqualTo(6);

    // check the metadata is included with field CANCEL decision
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision()).isNull();
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);

    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(actualCase.getCeExpectedCapacity()).isEqualTo(6);

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo("Field case update received");
  }
}
