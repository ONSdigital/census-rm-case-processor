package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.entity.RefusalType.EXTRAORDINARY_REFUSAL;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.convertJsonToObject;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class RefusalReceiverIT {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.refusal-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

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
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHardRefusalEmitsMessageAndLogsEventForCase() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Case caze = getRandomCase();
      caze.setCaseId(TEST_CASE_ID);
      caze.setRefusalReceived(null);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressLevel("U");
      caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent =
          getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());
      RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();
      expectedRefusal.getCollectionCase().setId(TEST_CASE_ID);

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      OffsetDateTime cazeCreatedTime = actualCase.getCreatedDateTime();
      OffsetDateTime cazeLastUpdated = actualCase.getLastUpdated();

      EventDTO eventDTO = responseManagementEvent.getEvent();
      assertThat(eventDTO.getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
      assertThat(eventDTO.getSource()).isEqualTo("CASE_SERVICE");
      assertThat(eventDTO.getChannel()).isEqualTo("RM");

      CollectionCase collectionCase = responseManagementEvent.getPayload().getCollectionCase();
      assertThat(collectionCase.getRefusalReceived()).isEqualTo(RefusalTypeDTO.HARD_REFUSAL);
      assertThat(collectionCase.getCreatedDateTime()).isEqualTo(cazeCreatedTime);
      assertThat(collectionCase.getLastUpdated()).isEqualTo(cazeLastUpdated);

      assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL);

      // check the metadata is included with field CANCEL decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.CANCEL);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);

      RefusalDTO actualRefusal =
          convertJsonToObject(events.get(0).getEventPayload(), RefusalDTO.class);
      assertThat(actualRefusal.getType()).isEqualTo(expectedRefusal.getType());
      assertThat(actualRefusal.getAgentId()).isEqualTo(expectedRefusal.getAgentId());
      assertThat(actualRefusal.getCallId()).isEqualTo(expectedRefusal.getCallId());
      assertThat(actualRefusal.getCollectionCase().getId())
          .isEqualTo(expectedRefusal.getCollectionCase().getId());
    }
  }

  @Test
  public void testCaseAlreadySetToExtraordinaryNotUpdatedByAHardRefusal() throws Exception {
    // As per
    // https://collaborate2.ons.gov.uk/confluence/pages/viewpage.action?spaceKey=SDC&title=Refusal+status+changes+and+Non+Compliance
    // If a case has already been marked as Extraordinary Refusal and we receive a Hard Refusal for
    // it we will not update the case, or emit
    //  Just record the event.

    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Case caze = getRandomCase();
      caze.setCaseId(TEST_CASE_ID);
      caze.setRefusalReceived(EXTRAORDINARY_REFUSAL);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressLevel("U");
      caseRepository.saveAndFlush(caze);

      OffsetDateTime cazeCreatedTime =
          caseRepository.findById(TEST_CASE_ID).get().getCreatedDateTime();

      ResponseManagementEvent managementEvent =
          getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());

      RefusalDTO refusalDTO = managementEvent.getPayload().getRefusal();
      refusalDTO.getCollectionCase().setId(TEST_CASE_ID);

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // THEN
      rhCaseQueueSpy.checkMessageIsNotReceived(5);

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
      assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.EXTRAORDINARY_REFUSAL);
      assertThat(actualCase.getLastUpdated()).isNotEqualTo(cazeCreatedTime);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);

      RefusalDTO actualRefusal =
          convertJsonToObject(events.get(0).getEventPayload(), RefusalDTO.class);
      assertThat(actualRefusal.getType()).isEqualTo(RefusalTypeDTO.HARD_REFUSAL);
      assertThat(actualRefusal.getAgentId()).isEqualTo(refusalDTO.getAgentId());
      assertThat(actualRefusal.getCallId()).isEqualTo(refusalDTO.getCallId());
      assertThat(actualRefusal.getCollectionCase().getId())
          .isEqualTo(refusalDTO.getCollectionCase().getId());
    }
  }

  @Test
  public void testHardRefusalWithIsHouseholderAsTrueContainsContactAndAddressInfo()
      throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Case caze = getRandomCase();
      caze.setCaseId(TEST_CASE_ID);
      caze.setRefusalReceived(null);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressLevel("U");
      caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent =
          getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());
      RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();
      expectedRefusal.getCollectionCase().setId(TEST_CASE_ID);
      expectedRefusal.setHouseholder(true);

      Contact contactHouseholder = managementEvent.getPayload().getRefusal().getContact();
      contactHouseholder.setTitle("Mr");
      contactHouseholder.setForename("Testy");
      contactHouseholder.setSurname("Test");

      Address addressHouseholder = managementEvent.getPayload().getRefusal().getAddress();
      addressHouseholder.setAddressLine1("1a main street");
      addressHouseholder.setAddressLine2("upper upperingham");
      addressHouseholder.setAddressLine3("");
      addressHouseholder.setTownName("upton");
      addressHouseholder.setPostcode("UP103UP");
      addressHouseholder.setRegion("E");
      addressHouseholder.setUprn("123456789");

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      OffsetDateTime cazeCreatedTime = actualCase.getCreatedDateTime();
      OffsetDateTime cazeLastUpdated = actualCase.getLastUpdated();

      EventDTO eventDTO = responseManagementEvent.getEvent();
      assertThat(eventDTO.getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
      assertThat(eventDTO.getSource()).isEqualTo("CASE_SERVICE");
      assertThat(eventDTO.getChannel()).isEqualTo("RM");

      CollectionCase collectionCase = responseManagementEvent.getPayload().getCollectionCase();
      assertThat(collectionCase.getRefusalReceived()).isEqualTo(RefusalTypeDTO.HARD_REFUSAL);
      assertThat(collectionCase.getCreatedDateTime()).isEqualTo(cazeCreatedTime);
      assertThat(collectionCase.getLastUpdated()).isEqualTo(cazeLastUpdated);

      assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL);

      // check the metadata is included with field CANCEL decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.CANCEL);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);

      RefusalDTO actualRefusal =
          convertJsonToObject(events.get(0).getEventPayload(), RefusalDTO.class);
      assertThat(actualRefusal.getType()).isEqualTo(expectedRefusal.getType());
      assertThat(actualRefusal.getAgentId()).isEqualTo(expectedRefusal.getAgentId());
      assertThat(actualRefusal.getCallId()).isEqualTo(expectedRefusal.getCallId());
      assertThat(actualRefusal.getCollectionCase().getId())
          .isEqualTo(expectedRefusal.getCollectionCase().getId());
      assertThat(actualRefusal.isHouseholder()).isEqualTo(true);

      assertThat(actualRefusal.getContact().getTitle()).isEqualTo(contactHouseholder.getTitle());
      assertThat(actualRefusal.getContact().getForename())
          .isEqualTo(contactHouseholder.getForename());
      assertThat(actualRefusal.getContact().getSurname())
          .isEqualTo(contactHouseholder.getSurname());

      assertThat(actualRefusal.getAddress().getAddressLine1())
          .isEqualTo(addressHouseholder.getAddressLine1());
    }
  }

  @Test
  public void testHardRefusalWithIsHouseholderAsFalseDoesNotContainContactAndAddressInfo()
      throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Case caze = getRandomCase();
      caze.setCaseId(TEST_CASE_ID);
      caze.setRefusalReceived(null);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressLevel("U");
      caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent =
          getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
      managementEvent.getEvent().setTransactionId(UUID.randomUUID());
      RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();
      expectedRefusal.getCollectionCase().setId(TEST_CASE_ID);
      expectedRefusal.setHouseholder(false);
      expectedRefusal.setContact(null);
      expectedRefusal.setAddress(null);

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      OffsetDateTime cazeCreatedTime = actualCase.getCreatedDateTime();
      OffsetDateTime cazeLastUpdated = actualCase.getLastUpdated();

      EventDTO eventDTO = responseManagementEvent.getEvent();
      assertThat(eventDTO.getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
      assertThat(eventDTO.getSource()).isEqualTo("CASE_SERVICE");
      assertThat(eventDTO.getChannel()).isEqualTo("RM");

      CollectionCase collectionCase = responseManagementEvent.getPayload().getCollectionCase();
      assertThat(collectionCase.getRefusalReceived()).isEqualTo(RefusalTypeDTO.HARD_REFUSAL);
      assertThat(collectionCase.getCreatedDateTime()).isEqualTo(cazeCreatedTime);
      assertThat(collectionCase.getLastUpdated()).isEqualTo(cazeLastUpdated);

      assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL);

      // check the metadata is included with field CANCEL decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.CANCEL);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);

      RefusalDTO actualRefusal =
          convertJsonToObject(events.get(0).getEventPayload(), RefusalDTO.class);
      assertThat(actualRefusal.getType()).isEqualTo(expectedRefusal.getType());
      assertThat(actualRefusal.getAgentId()).isEqualTo(expectedRefusal.getAgentId());
      assertThat(actualRefusal.getCallId()).isEqualTo(expectedRefusal.getCallId());
      assertThat(actualRefusal.getCollectionCase().getId())
          .isEqualTo(expectedRefusal.getCollectionCase().getId());
      assertThat(actualRefusal.isHouseholder()).isEqualTo(false);

      assertThat(actualRefusal.getContact()).isNull();

      assertThat(actualRefusal.getAddress()).isNull();
    }
  }
}
