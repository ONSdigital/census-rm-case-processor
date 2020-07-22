package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createTestAddressTypeChangeJson;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.AddressModification;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.NewAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class AddressReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.address-inbound-queue}")
  private String addressReceiver;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${censusconfig.collectionexerciseid}")
  private String censuscollectionExerciseId;

  @Value("${censusconfig.actionplanid}")
  private String censusActionPlanId;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(addressReceiver);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testInvalidAddress() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressInvalid(false);
      caze = caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent = new ResponseManagementEvent();
      managementEvent.setEvent(new EventDTO());
      managementEvent.getEvent().setDateTime(OffsetDateTime.now());
      managementEvent.getEvent().setChannel("Test channel");
      managementEvent.getEvent().setSource("Test source");
      managementEvent.getEvent().setType(EventTypeDTO.ADDRESS_NOT_VALID);
      managementEvent.setPayload(new PayloadDTO());
      managementEvent.getPayload().setInvalidAddress(new InvalidAddress());
      managementEvent
          .getPayload()
          .getInvalidAddress()
          .setCollectionCase(new CollectionCaseCaseId());
      managementEvent
          .getPayload()
          .getInvalidAddress()
          .getCollectionCase()
          .setId(caze.getCaseId().toString());

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, message);

      // THEN

      // check the emitted eventDTO
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
      CollectionCase actualPayloadCase = responseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(caze.getCaseId().toString());
      assertThat(actualPayloadCase.getAddressInvalid()).isTrue();

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
      assertThat(actualCase.isAddressInvalid()).isTrue();

      // check the metadata is included with field CANCEL decision
      assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(ActionInstructionType.CANCEL);
      assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(EventTypeDTO.ADDRESS_NOT_VALID);

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("Invalid address");
      assertThat(event.getEventType()).isEqualTo(EventType.ADDRESS_NOT_VALID);
    }
  }

  @Test
  public void testAddressModifiedEventType() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressInvalid(false);
      caze = caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent = new ResponseManagementEvent();
      managementEvent.setEvent(new EventDTO());
      managementEvent.getEvent().setDateTime(OffsetDateTime.now());
      managementEvent.getEvent().setChannel("Test channel");
      managementEvent.getEvent().setSource("Test source");
      managementEvent.getEvent().setType(ADDRESS_MODIFIED);
      managementEvent.setPayload(new PayloadDTO());
      managementEvent.getPayload().setAddressModification(new AddressModification());
      managementEvent
          .getPayload()
          .getAddressModification()
          .setCollectionCase(new CollectionCaseCaseId());
      managementEvent
          .getPayload()
          .getAddressModification()
          .getCollectionCase()
          .setId(caze.getCaseId().toString());
      managementEvent.getPayload().getAddressModification().setNewAddress(new Address());
      managementEvent
          .getPayload()
          .getAddressModification()
          .getNewAddress()
          .setAddressLine1("modified address line 1");
      managementEvent
          .getPayload()
          .getAddressModification()
          .getNewAddress()
          .setAddressLine2("modified address line 2");
      managementEvent
          .getPayload()
          .getAddressModification()
          .getNewAddress()
          .setAddressLine3("modified address line 3");
      managementEvent
          .getPayload()
          .getAddressModification()
          .getNewAddress()
          .setTownName("modified town name");
      managementEvent
          .getPayload()
          .getAddressModification()
          .getNewAddress()
          .setEstabType("HOSPITAL");

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, message);

      // THEN

      // check the emitted eventDTO
      ResponseManagementEvent responseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
      CollectionCase actualPayloadCase = responseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(caze.getCaseId().toString());

      assertThat(actualPayloadCase.getAddress().getAddressLine1())
          .isEqualTo("modified address line 1");
      assertThat(actualPayloadCase.getAddress().getAddressLine2())
          .isEqualTo("modified address line 2");
      assertThat(actualPayloadCase.getAddress().getAddressLine3())
          .isEqualTo("modified address line 3");
      assertThat(actualPayloadCase.getAddress().getTownName()).isEqualTo("modified town name");

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

      assertThat(actualCase.getAddressLine1()).isEqualTo("modified address line 1");
      assertThat(actualCase.getAddressLine2()).isEqualTo("modified address line 2");
      assertThat(actualCase.getAddressLine3()).isEqualTo("modified address line 3");
      assertThat(actualCase.getTownName()).isEqualTo("modified town name");

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("Address modified");
      assertThat(event.getEventType()).isEqualTo(EventType.ADDRESS_MODIFIED);
    }
  }

  @Test
  public void testAddressTypeChangeEventTypeLoggedOnly() throws Exception {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressTypeChange(createTestAddressTypeChangeJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressTypeChange()),
        ADDRESS_TYPE_CHANGED,
        EventType.ADDRESS_TYPE_CHANGED,
        "Address type changed");
  }

  @Test
  public void testNewAddressCreatesSkeletonCase() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Address address = new Address();
      address.setAddressLevel("E");
      address.setAddressType("HH");
      address.setRegion("W");

      CollectionCase collectionCase = new CollectionCase();
      collectionCase.setId(UUID.randomUUID().toString());
      collectionCase.setAddress(address);

      NewAddress newAddress = new NewAddress();
      newAddress.setCollectionCase(collectionCase);

      EventDTO eventDTO = new EventDTO();
      eventDTO.setType(NEW_ADDRESS_REPORTED);
      eventDTO.setDateTime(OffsetDateTime.now());

      ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
      responseManagementEvent.setEvent(eventDTO);

      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setNewAddress(newAddress);
      responseManagementEvent.setPayload(payloadDTO);

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, responseManagementEvent);

      // THEN
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualResponseManagementEvent.getEvent().getType())
          .isEqualTo(EventTypeDTO.CASE_CREATED);
      CollectionCase actualPayloadCase =
          actualResponseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(collectionCase.getId());
      assertThat(actualPayloadCase.isSkeleton()).isTrue().as("Is Skeleton Case");

      Case actualCase = caseRepository.findById(UUID.fromString(collectionCase.getId())).get();
      assertThat(actualCase.getCollectionExerciseId()).isEqualTo(censuscollectionExerciseId);
      assertThat(actualCase.getActionPlanId()).isEqualTo(censusActionPlanId);
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
      assertThat(actualCase.getCaseType()).isEqualTo("HH");
      assertThat(actualCase.isSkeleton()).isTrue().as("Is Skeleton Case In DB");

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("New Address reported");
      assertThat(event.getEventType()).isEqualTo(EventType.NEW_ADDRESS_REPORTED);
    }
  }

  @Test
  public void testNewAddressCreatedFromBasicEventAndSourceCase() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case sourceCase = easyRandom.nextObject(Case.class);
      sourceCase.setReceiptReceived(false);
      sourceCase.setSurvey("CENSUS");
      sourceCase.setUacQidLinks(null);
      sourceCase.setEvents(null);
      sourceCase.setCaseType("HH");
      sourceCase.setAddressLevel("U");
      sourceCase.setRefusalReceived(null);
      sourceCase.setCollectionExerciseId(censuscollectionExerciseId);
      sourceCase.getMetadata().setSecureEstablishment(true);
      sourceCase = caseRepository.saveAndFlush(sourceCase);

      Address address = new Address();
      address.setAddressLevel("E");
      address.setAddressType("HH");
      address.setRegion("W");

      CollectionCase collectionCase = new CollectionCase();
      collectionCase.setId(UUID.randomUUID().toString());
      collectionCase.setAddress(address);

      NewAddress newAddress = new NewAddress();
      newAddress.setCollectionCase(collectionCase);
      newAddress.setSourceCaseId(sourceCase.getCaseId().toString());

      EventDTO eventDTO = new EventDTO();
      eventDTO.setType(NEW_ADDRESS_REPORTED);
      eventDTO.setDateTime(OffsetDateTime.now());

      ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
      responseManagementEvent.setEvent(eventDTO);

      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setNewAddress(newAddress);
      responseManagementEvent.setPayload(payloadDTO);

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, responseManagementEvent);

      // THEN
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualResponseManagementEvent.getEvent().getType())
          .isEqualTo(EventTypeDTO.CASE_CREATED);
      CollectionCase actualPayloadCase =
          actualResponseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(collectionCase.getId());

      Case actualCase = caseRepository.findById(UUID.fromString(collectionCase.getId())).get();
      assertThat(actualCase.getCollectionExerciseId()).isEqualTo(censuscollectionExerciseId);
      assertThat(actualCase.getActionPlanId()).isEqualTo(censusActionPlanId);
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
      assertThat(actualCase.isSkeleton()).isTrue();
      assertThat(actualCase.getCaseType()).isEqualTo(collectionCase.getAddress().getAddressType());
      assertThat(actualCase.getAddressLine1()).isEqualTo(sourceCase.getAddressLine1());
      assertThat(actualCase.getAddressLine2()).isEqualTo(sourceCase.getAddressLine2());
      assertThat(actualCase.getAddressLine3()).isEqualTo(sourceCase.getAddressLine3());
      assertThat(actualCase.getPostcode()).isEqualTo(sourceCase.getPostcode());
      assertThat(actualCase.getTownName()).isEqualTo(sourceCase.getTownName());
      assertThat(actualCase.getCollectionExerciseId())
          .isEqualTo(sourceCase.getCollectionExerciseId());
      assertThat(actualCase.getEstabType()).isEqualTo(sourceCase.getEstabType());
      assertThat(actualCase.getFieldCoordinatorId()).isEqualTo(sourceCase.getFieldCoordinatorId());
      assertThat(actualCase.getFieldOfficerId()).isEqualTo(sourceCase.getFieldOfficerId());

      assertThat(actualCase.getOrganisationName()).isNull();
      assertThat(actualCase.getLatitude()).isEqualTo(sourceCase.getLatitude());
      assertThat(actualCase.getLongitude()).isEqualTo(sourceCase.getLongitude());
      assertThat(actualCase.getUprn()).isEqualTo("999" + actualCase.getCaseRef());
      assertThat(actualCase.getTreatmentCode()).isNull();

      assertThat(actualCase.getEstabUprn()).isEqualTo(sourceCase.getEstabUprn());
      assertThat(actualCase.getMetadata().getSecureEstablishment()).isTrue();

      assertThat(actualCase.isReceiptReceived()).isFalse();
      assertThat(actualCase.getRefusalReceived()).isNull();
      assertThat(actualCase.isAddressInvalid()).isFalse();
      assertThat(actualCase.isHandDelivery()).isFalse();

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("New Address reported");
      assertThat(event.getEventType()).isEqualTo(EventType.NEW_ADDRESS_REPORTED);
    }
  }

  @Test
  public void testNewAddressCreatedFromEventWithDetailsAndSourceCase() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case sourceCase = easyRandom.nextObject(Case.class);
      sourceCase.setReceiptReceived(false);
      sourceCase.setSurvey("CENSUS");
      sourceCase.setUacQidLinks(null);
      sourceCase.setEvents(null);
      sourceCase.setCaseType("HH");
      sourceCase.setAddressLevel("U");
      sourceCase.setCollectionExerciseId(censuscollectionExerciseId);
      sourceCase.getMetadata().setSecureEstablishment(false);
      sourceCase.setRefusalReceived(null);
      sourceCase = caseRepository.saveAndFlush(sourceCase);

      Address address = new Address();
      address.setAddressLevel("E");
      address.setAddressType("HH");
      address.setRegion("W");

      address.setAddressLine1("123");
      address.setAddressLine2("Fake Street");
      address.setAddressLine3("Made up district");
      address.setTownName("Nowheresville");
      address.setPostcode("AB12 3CD");
      address.setEstabType("HH");
      address.setOrganisationName("Super Org");
      address.setLatitude("12.34");
      address.setLongitude("56.78");
      address.setUprn("uprn01");

      CollectionCase collectionCase = new CollectionCase();
      collectionCase.setId(UUID.randomUUID().toString());
      collectionCase.setAddress(address);
      collectionCase.setFieldCoordinatorId("1234");
      collectionCase.setFieldOfficerId("5678");
      collectionCase.setCeExpectedCapacity(1);
      collectionCase.setCaseType("SPG");
      collectionCase.setTreatmentCode("TREAT");

      NewAddress newAddress = new NewAddress();
      newAddress.setCollectionCase(collectionCase);
      newAddress.setSourceCaseId(sourceCase.getCaseId().toString());

      EventDTO eventDTO = new EventDTO();
      eventDTO.setType(NEW_ADDRESS_REPORTED);
      eventDTO.setDateTime(OffsetDateTime.now());
      eventDTO.setChannel("FIELD");

      ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
      responseManagementEvent.setEvent(eventDTO);

      PayloadDTO payloadDTO = new PayloadDTO();
      payloadDTO.setNewAddress(newAddress);
      responseManagementEvent.setPayload(payloadDTO);

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, responseManagementEvent);

      // THEN
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualResponseManagementEvent.getEvent().getType())
          .isEqualTo(EventTypeDTO.CASE_CREATED);
      CollectionCase actualPayloadCase =
          actualResponseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(collectionCase.getId());

      Case actualCase = caseRepository.findById(UUID.fromString(collectionCase.getId())).get();
      assertThat(actualCase.getCollectionExerciseId()).isEqualTo(censuscollectionExerciseId);
      assertThat(actualCase.getActionPlanId()).isEqualTo(censusActionPlanId);
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
      assertThat(actualCase.getCaseType()).isEqualTo(collectionCase.getCaseType());
      assertThat(actualCase.isSkeleton()).isTrue();

      assertThat(actualCase.getAddressLine1())
          .isEqualTo(collectionCase.getAddress().getAddressLine1());
      assertThat(actualCase.getAddressLine2())
          .isEqualTo(collectionCase.getAddress().getAddressLine2());
      assertThat(actualCase.getAddressLine3())
          .isEqualTo(collectionCase.getAddress().getAddressLine3());
      assertThat(actualCase.getPostcode()).isEqualTo(collectionCase.getAddress().getPostcode());
      assertThat(actualCase.getTownName()).isEqualTo(collectionCase.getAddress().getTownName());
      assertThat(actualCase.getCollectionExerciseId())
          .isEqualTo(sourceCase.getCollectionExerciseId());
      assertThat(actualCase.getEstabType()).isEqualTo(collectionCase.getAddress().getEstabType());
      assertThat(actualCase.getFieldCoordinatorId())
          .isEqualTo(collectionCase.getFieldCoordinatorId());
      assertThat(actualCase.getFieldOfficerId()).isEqualTo(collectionCase.getFieldOfficerId());

      assertThat(actualCase.getOrganisationName())
          .isEqualTo(collectionCase.getAddress().getOrganisationName());
      assertThat(actualCase.getLatitude()).isEqualTo(collectionCase.getAddress().getLatitude());
      assertThat(actualCase.getLongitude()).isEqualTo(collectionCase.getAddress().getLongitude());
      assertThat(actualCase.getUprn()).isEqualTo(collectionCase.getAddress().getUprn());
      assertThat(actualCase.getCaseType()).isEqualTo(collectionCase.getCaseType());
      assertThat(actualCase.getTreatmentCode()).isEqualTo(collectionCase.getTreatmentCode());

      assertThat(actualCase.getEstabUprn()).isEqualTo(sourceCase.getEstabUprn());
      assertThat(actualCase.getMetadata().getSecureEstablishment()).isFalse();

      assertThat(actualCase.isReceiptReceived()).isFalse();
      assertThat(actualCase.getRefusalReceived()).isNull();
      assertThat(actualCase.isAddressInvalid()).isFalse();
      assertThat(actualCase.isHandDelivery()).isFalse();

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("New Address reported");
      assertThat(event.getEventType()).isEqualTo(EventType.NEW_ADDRESS_REPORTED);
    }
  }

  public void testEventTypeLoggedOnly(
      PayloadDTO payload,
      String expectedEventPayloadJson,
      EventTypeDTO eventTypeDTO,
      EventType eventType,
      String eventDescription)
      throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressInvalid(false);
      caze = caseRepository.saveAndFlush(caze);

      ResponseManagementEvent managementEvent = new ResponseManagementEvent();
      managementEvent.setEvent(new EventDTO());
      managementEvent.getEvent().setDateTime(OffsetDateTime.now());
      managementEvent.getEvent().setChannel("Test channel");
      managementEvent.getEvent().setSource("Test source");
      managementEvent.getEvent().setType(eventTypeDTO);
      managementEvent.setPayload(payload);

      String json = convertObjectToJson(managementEvent);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbitQueueHelper.sendMessage(addressReceiver, message);

      // Check no message emitted
      rhCaseQueueSpy.checkMessageIsNotReceived(3);

      // Check case not changed
      Optional<Case> actualCaseOpt = caseRepository.findById(caze.getCaseId());
      Case actualCase = actualCaseOpt.get();
      assertThat(actualCase.isAddressInvalid()).isFalse();

      // Event logged is as expected
      List<Event> events = eventRepository.findAll(Sort.by(ASC, "rmEventProcessed"));
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventChannel()).isEqualTo("Test channel");
      assertThat(event.getEventSource()).isEqualTo("Test source");
      assertThat(event.getEventDescription()).isEqualTo(eventDescription);
      assertThat(event.getEventType()).isEqualTo(eventType);

      String actualEventPayloadJson = event.getEventPayload();
      JSONAssert.assertEquals(actualEventPayloadJson, expectedEventPayloadJson, STRICT);
    }
  }
}
