package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.PubSubHelper;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class AddressReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final UUID NEW_TEST_CASE_ID = UUID.randomUUID();
  private static final String AIMS_SUBSCRIPTION = "aims-subscription";

  @Value("${queueconfig.address-inbound-queue}")
  private String addressReceiver;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${censusconfig.collectionexerciseid}")
  private UUID censuscollectionExerciseId;

  @Value("${censusconfig.actionplanid}")
  private UUID censusActionPlanId;

  @Value("${spring.cloud.gcp.pubsub.project-id}")
  private String aimsProjectId;

  @Value("${pubsub.aims-new-address-topic}")
  private String aimsNewAddressTopic;

  @Value("${spring.cloud.gcp.pubsub.emulator-host}")
  private String pubsubEmulatorHost;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private PubSubTemplate pubSubTemplate;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(addressReceiver);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();

    setupPubsubTopicAndSubscription();
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
      managementEvent.getPayload().getInvalidAddress().getCollectionCase().setId(caze.getCaseId());

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
      assertThat(actualPayloadCase.getId()).isEqualTo(caze.getCaseId());
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
          .setId(caze.getCaseId());
      ModifiedAddress newAddress = new ModifiedAddress();
      managementEvent.getPayload().getAddressModification().setNewAddress(newAddress);
      newAddress.setAddressLine1(Optional.of("modified address line 1"));
      newAddress.setAddressLine2(Optional.of("modified address line 2"));
      newAddress.setAddressLine3(Optional.of("modified address line 3"));
      newAddress.setOrganisationName(Optional.of("modified org name"));
      newAddress.setEstabType(Optional.of("HOSPITAL"));

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
      assertThat(actualPayloadCase.getId()).isEqualTo(caze.getCaseId());

      assertThat(actualPayloadCase.getAddress().getAddressLine1())
          .isEqualTo("modified address line 1");
      assertThat(actualPayloadCase.getAddress().getAddressLine2())
          .isEqualTo("modified address line 2");
      assertThat(actualPayloadCase.getAddress().getAddressLine3())
          .isEqualTo("modified address line 3");
      assertThat(actualPayloadCase.getAddress().getOrganisationName())
          .isEqualTo("modified org name");
      assertThat(actualPayloadCase.getAddress().getEstabType()).isEqualTo("HOSPITAL");

      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");

      assertThat(actualCase.getAddressLine1()).isEqualTo("modified address line 1");
      assertThat(actualCase.getAddressLine2()).isEqualTo("modified address line 2");
      assertThat(actualCase.getAddressLine3()).isEqualTo("modified address line 3");
      assertThat(actualCase.getOrganisationName()).isEqualTo("modified org name");
      assertThat(actualCase.getEstabType()).isEqualTo("HOSPITAL");

      // check database for log eventDTO
      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event event = events.get(0);
      assertThat(event.getEventDescription()).isEqualTo("Address modified");
      assertThat(event.getEventType()).isEqualTo(EventType.ADDRESS_MODIFIED);
    }
  }

  @Test
  public void testAddressTypeChangedHappyPath() throws Exception {
    // GIVEN
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      EasyRandom easyRandom = new EasyRandom();
      Case caze = easyRandom.nextObject(Case.class);
      caze.setCaseId(TEST_CASE_ID);
      caze.setCaseType("HH");
      caze.setSurvey("CENSUS");
      caze.setUacQidLinks(null);
      caze.setEvents(null);
      caze.setAddressInvalid(false);
      caze.setRegion("W");
      caze = caseRepository.saveAndFlush(caze);

      ResponseManagementEvent rme = new ResponseManagementEvent();

      EventDTO event = new EventDTO();
      rme.setEvent(event);
      event.setDateTime(OffsetDateTime.now());
      event.setType(ADDRESS_TYPE_CHANGED);
      event.setChannel("CC");

      PayloadDTO payload = new PayloadDTO();
      rme.setPayload(payload);

      AddressTypeChanged addressTypeChanged = new AddressTypeChanged();
      payload.setAddressTypeChanged(addressTypeChanged);
      addressTypeChanged.setNewCaseId(NEW_TEST_CASE_ID);

      AddressTypeChangedDetails addressTypeChangedDetails = new AddressTypeChangedDetails();
      addressTypeChanged.setCollectionCase(addressTypeChangedDetails);
      addressTypeChangedDetails.setCeExpectedCapacity("20");
      addressTypeChangedDetails.setId(TEST_CASE_ID);

      Address address = new Address();
      addressTypeChangedDetails.setAddress(address);
      address.setAddressType("SPG");

      String json = convertObjectToJson(rme);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(addressReceiver, message);

      // THEN
      ResponseManagementEvent oldCaseEvent = null;
      ResponseManagementEvent newCaseEvent = null;
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();
      if (actualResponseManagementEvent
          .getPayload()
          .getCollectionCase()
          .getCaseType()
          .equals("HH")) {
        oldCaseEvent = actualResponseManagementEvent;
      } else if (actualResponseManagementEvent
          .getPayload()
          .getCollectionCase()
          .getCaseType()
          .equals("SPG")) {
        newCaseEvent = actualResponseManagementEvent;
      } else {
        assert (false);
      }
      actualResponseManagementEvent = rhCaseQueueSpy.checkExpectedMessageReceived();

      if (oldCaseEvent == null
          && actualResponseManagementEvent
              .getPayload()
              .getCollectionCase()
              .getCaseType()
              .equals("HH")) {
        oldCaseEvent = actualResponseManagementEvent;

      } else if (actualResponseManagementEvent
          .getPayload()
          .getCollectionCase()
          .getCaseType()
          .equals("SPG")) {
        newCaseEvent = actualResponseManagementEvent;
      } else {
        assert (false);
      }

      assertThat(oldCaseEvent.getPayload().getCollectionCase().getAddressInvalid()).isTrue();
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddressInvalid()).isFalse();
      assertThat(oldCaseEvent.getPayload().getCollectionCase().getId()).isEqualTo(TEST_CASE_ID);
      assertThat(newCaseEvent.getPayload().getCollectionCase().getId()).isEqualTo(NEW_TEST_CASE_ID);

      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getAddressLine1())
          .isEqualTo(caze.getAddressLine1());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getAddressLine2())
          .isEqualTo(caze.getAddressLine2());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getAddressLine3())
          .isEqualTo(caze.getAddressLine3());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getRegion())
          .isEqualTo(caze.getRegion());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getCollectionExerciseId())
          .isEqualTo(caze.getCollectionExerciseId());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getActionPlanId())
          .isEqualTo(caze.getActionPlanId());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getSurvey())
          .isEqualTo(caze.getSurvey());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getUprn())
          .isEqualTo(caze.getUprn());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getOrganisationName())
          .isEqualTo(caze.getOrganisationName());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getTownName())
          .isEqualTo(caze.getTownName());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getPostcode())
          .isEqualTo(caze.getPostcode());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getLatitude())
          .isEqualTo(caze.getLatitude());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getAddress().getLongitude())
          .isEqualTo(caze.getLongitude());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getOa()).isEqualTo(caze.getOa());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getLsoa()).isEqualTo(caze.getLsoa());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getMsoa()).isEqualTo(caze.getMsoa());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getLad()).isEqualTo(caze.getLad());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getHtcWillingness())
          .isEqualTo(caze.getHtcWillingness());
      assertThat(newCaseEvent.getPayload().getCollectionCase().getHtcDigital())
          .isEqualTo(caze.getHtcDigital());

      assertThat(oldCaseEvent.getEvent().getType()).isEqualTo(CASE_UPDATED);
      assertThat(newCaseEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_CREATED);

      assertThat(newCaseEvent.getPayload().getCollectionCase().getCeExpectedCapacity())
          .isEqualTo(20);
      assertThat(newCaseEvent.getPayload().getCollectionCase().isSkeleton()).isTrue();

      Case oldCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(oldCase.isAddressInvalid()).isTrue();

      Case newCase = caseRepository.findById(NEW_TEST_CASE_ID).get();
      assertThat(newCase.getAddressLine1()).isEqualTo(caze.getAddressLine1());
      assertThat(newCase.getAddressLine2()).isEqualTo(caze.getAddressLine2());
      assertThat(newCase.getAddressLine3()).isEqualTo(caze.getAddressLine3());
      assertThat(newCase.getRegion()).isEqualTo(caze.getRegion());
      assertThat(newCase.getCollectionExerciseId()).isEqualTo(caze.getCollectionExerciseId());
      assertThat(newCase.getActionPlanId()).isEqualTo(caze.getActionPlanId());
      assertThat(newCase.getSurvey()).isEqualTo(caze.getSurvey());
      assertThat(newCase.getUprn()).isEqualTo(caze.getUprn());
      assertThat(newCase.getOrganisationName()).isEqualTo(caze.getOrganisationName());
      assertThat(newCase.getTownName()).isEqualTo(caze.getTownName());
      assertThat(newCase.getPostcode()).isEqualTo(caze.getPostcode());
      assertThat(newCase.getLatitude()).isEqualTo(caze.getLatitude());
      assertThat(newCase.getLongitude()).isEqualTo(caze.getLongitude());
      assertThat(newCase.getOa()).isEqualTo(caze.getOa());
      assertThat(newCase.getLsoa()).isEqualTo(caze.getLsoa());
      assertThat(newCase.getMsoa()).isEqualTo(caze.getMsoa());
      assertThat(newCase.getLad()).isEqualTo(caze.getLad());
      assertThat(newCase.getHtcWillingness()).isEqualTo(caze.getHtcWillingness());
      assertThat(newCase.getHtcDigital()).isEqualTo(caze.getHtcDigital());
      assertThat(newCase.getCeExpectedCapacity()).isEqualTo(20);
      assertThat(newCase.isSkeleton()).isTrue();

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(3);
      Event addressInvalidEvent = null;
      Event oldAddressTypeChangedEvent = null;
      Event newAddressTypeChangedEvent = null;

      for (Event eventItem : events) {
        if (eventItem.getEventType().equals(EventType.ADDRESS_NOT_VALID)) {
          addressInvalidEvent = eventItem;
        }
        if (eventItem.getEventType().equals(EventType.ADDRESS_TYPE_CHANGED)) {
          if (eventItem.getCaze().getCaseId().equals(TEST_CASE_ID)) {
            oldAddressTypeChangedEvent = eventItem;
          } else if (eventItem.getCaze().getCaseId().equals(NEW_TEST_CASE_ID)) {
            newAddressTypeChangedEvent = eventItem;
          }
        }
      }
      assertThat(addressInvalidEvent).isNotNull();
      assertThat(oldAddressTypeChangedEvent).isNotNull();
      assertThat(newAddressTypeChangedEvent).isNotNull();
      assertThat(addressInvalidEvent.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
      assertThat(addressInvalidEvent.getEventDescription()).isEqualTo("Invalid address");
      assertThat(oldAddressTypeChangedEvent.getEventDescription())
          .isEqualTo("Address type changed");
      assertThat(newAddressTypeChangedEvent.getEventDescription())
          .isEqualTo("Address type changed");

      JSONAssert.assertEquals(
          addressInvalidEvent.getEventPayload(), convertObjectToJson(addressTypeChanged), STRICT);
      JSONAssert.assertEquals(
          oldAddressTypeChangedEvent.getEventPayload(),
          convertObjectToJson(addressTypeChanged),
          STRICT);
      JSONAssert.assertEquals(
          newAddressTypeChangedEvent.getEventPayload(),
          convertObjectToJson(addressTypeChanged),
          STRICT);
    }
  }

  // TEST FAILING? Try running `gcloud auth application-default revoke`
  @Test
  public void testNewAddressCreatesSkeletonCase() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // GIVEN
      Address address = new Address();
      address.setAddressLevel("E");
      address.setAddressType("HH");
      address.setRegion("W");

      CollectionCase collectionCase = new CollectionCase();
      collectionCase.setId(UUID.randomUUID());
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
      // TEST FAILING? Try running `gcloud auth application-default revoke`
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualResponseManagementEvent.getEvent().getType())
          .isEqualTo(EventTypeDTO.CASE_CREATED);
      CollectionCase actualPayloadCase =
          actualResponseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(collectionCase.getId());
      assertThat(actualPayloadCase.isSkeleton()).isTrue().as("Is Skeleton Case");

      Case actualCase = caseRepository.findById(collectionCase.getId()).get();
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

      // check pubsub for message to AIMS
      ResponseManagementEvent rmEventToAims =
          PubSubHelper.subscribe(pubSubTemplate, AIMS_SUBSCRIPTION).poll(20, TimeUnit.SECONDS);
      assertThat(rmEventToAims.getEvent().getType()).isEqualTo(NEW_ADDRESS_ENHANCED);
      assertThat(
              rmEventToAims.getPayload().getNewAddress().getCollectionCase().getAddress().getUprn())
          .startsWith("999");
    }
  }

  // TEST FAILING? Try running `gcloud auth application-default revoke`
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
      collectionCase.setId(UUID.randomUUID());
      collectionCase.setAddress(address);

      NewAddress newAddress = new NewAddress();
      newAddress.setCollectionCase(collectionCase);
      newAddress.setSourceCaseId(sourceCase.getCaseId());

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
      // TEST FAILING? Try running `gcloud auth application-default revoke`
      ResponseManagementEvent actualResponseManagementEvent =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualResponseManagementEvent.getEvent().getType())
          .isEqualTo(EventTypeDTO.CASE_CREATED);
      CollectionCase actualPayloadCase =
          actualResponseManagementEvent.getPayload().getCollectionCase();
      assertThat(actualPayloadCase.getId()).isEqualTo(collectionCase.getId());

      Case actualCase = caseRepository.findById(collectionCase.getId()).get();
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

      // check pubsub for message to AIMS
      ResponseManagementEvent rmEventToAims =
          PubSubHelper.subscribe(pubSubTemplate, AIMS_SUBSCRIPTION).poll(20, TimeUnit.SECONDS);
      assertThat(rmEventToAims.getEvent().getType()).isEqualTo(NEW_ADDRESS_ENHANCED);
      assertThat(
              rmEventToAims.getPayload().getNewAddress().getCollectionCase().getAddress().getUprn())
          .startsWith("999");
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
      address.setUprn("uprn01"); // NOTE: This means no message to AIMS

      CollectionCase collectionCase = new CollectionCase();
      collectionCase.setId(UUID.randomUUID());
      collectionCase.setAddress(address);
      collectionCase.setFieldCoordinatorId("1234");
      collectionCase.setFieldOfficerId("5678");
      collectionCase.setCeExpectedCapacity(1);
      collectionCase.setCaseType("SPG");
      collectionCase.setTreatmentCode("TREAT");

      NewAddress newAddress = new NewAddress();
      newAddress.setCollectionCase(collectionCase);
      newAddress.setSourceCaseId(sourceCase.getCaseId());

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

      Case actualCase = caseRepository.findById(collectionCase.getId()).get();
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

  @Data
  @AllArgsConstructor
  private class SubscriptionTopic {
    private String topic;
  }

  private void setupPubsubTopicAndSubscription() {
    RestTemplate restTemplate = new RestTemplate();

    String subscriptionUrl =
        "http://"
            + pubsubEmulatorHost
            + "/v1/projects/"
            + aimsProjectId
            + "/subscriptions/"
            + AIMS_SUBSCRIPTION;

    try {
      restTemplate.delete(subscriptionUrl);
    } catch (HttpClientErrorException exception) {
      if (exception.getRawStatusCode() != 404) {
        throw exception;
      }
    }

    // There's no concept of a 'purge' with pubsub. Crudely, we have to delete, and in so doing
    // we expose other problems with the timing of everything in the integration tests. Sleeps are
    // unavoidable.
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      // Ignored
    }

    String topicUrl =
        "http://"
            + pubsubEmulatorHost
            + "/v1/projects/"
            + aimsProjectId
            + "/topics/"
            + aimsNewAddressTopic;

    try {
      restTemplate.delete(topicUrl);
    } catch (HttpClientErrorException exception) {
      if (exception.getRawStatusCode() != 404) {
        throw exception;
      }
    }

    // There's no concept of a 'purge' with pubsub. Crudely, we have to delete, and in so doing
    // we expose other problems with the timing of everything in the integration tests. Sleeps are
    // unavoidable.
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      // Ignored
    }

    try {
      restTemplate.put(topicUrl, null);
    } catch (HttpClientErrorException exception) {
      if (exception.getRawStatusCode() != 409) {
        throw exception;
      }
    }

    try {
      restTemplate.put(
          subscriptionUrl,
          new SubscriptionTopic("projects/" + aimsProjectId + "/topics/" + aimsNewAddressTopic));
    } catch (HttpClientErrorException exception) {
      if (exception.getRawStatusCode() != 409) {
        throw exception;
      }
    }
  }
}
