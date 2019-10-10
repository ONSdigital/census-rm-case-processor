package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.CcsToFwmt;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalType;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
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
public class CCSPropertyListedIT {
  private static String FIELD_QUEUE = "Action.Field";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final String CCS_PROPERTY_LISTED_CHANNEL = "FIELD";
  private static final String CCS_PROPERTY_LISTED_SOURCE = "FIELDWORK_GATEWAY";
  private static final String TEST_QID = "71000000000121";

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;

  @Value("${queueconfig.ccs-property-listed-queue}")
  private String ccsPropertyListedQueue;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(FIELD_QUEUE);
    rabbitQueueHelper.purgeQueue(ccsPropertyListedQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testCCSSubmittedToFieldIT() throws IOException, InterruptedException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(FIELD_QUEUE);

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setCollectionCase(collectionCase);
    ccsPropertyDTO.setSampleUnit(setUpSampleUnitDTO());

    EventDTO eventDTO = new EventDTO();
    eventDTO.setType(EventTypeDTO.CCS_ADDRESS_LISTED);
    eventDTO.setSource("FIELDWORK_GATEWAY");
    eventDTO.setChannel("FIELD");
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    payload.setCcsProperty(ccsPropertyDTO);
    responseManagementEvent.setPayload(payload);
    responseManagementEvent.setEvent(eventDTO);

    // When
    rabbitQueueHelper.sendMessage(ccsPropertyListedQueue, responseManagementEvent);

    // Then
    CcsToFwmt ccsToFwmt = rabbitQueueHelper.checkCcsFwmtEmitted(outboundQueue);
    assertThat(ccsToFwmt.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isCcsCase()).isTrue();

    List<UacQidLink> actualUacQidLinks = uacQidLinkRepository.findAll();
    assertThat(actualUacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = actualUacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid().substring(0, 2)).isEqualTo("71");
    assertThat(actualUacQidLink.isCcsCase()).isTrue();

    validateEvents(eventRepository.findAll(), ccsPropertyDTO);
  }

  @Test
  public void testCCSListedEventWithQidSet() throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(FIELD_QUEUE);
    EasyRandom easyRandom = new EasyRandom();

    UacQidLink uacQidLink = easyRandom.nextObject(UacQidLink.class);
    uacQidLink.setQid(TEST_QID);
    uacQidLink.setCaze(null);
    uacQidLink.setCcsCase(true);
    uacQidLink.setEvents(null);
    uacQidLinkRepository.save(uacQidLink);

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setCollectionCase(collectionCase);

    ccsPropertyDTO.setSampleUnit(setUpSampleUnitDTO());

    UacDTO uacDTO = new UacDTO();
    uacDTO.setQuestionnaireId(TEST_QID);
    ccsPropertyDTO.setUac(uacDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setType(EventTypeDTO.CCS_ADDRESS_LISTED);
    eventDTO.setSource("FIELDWORK_GATEWAY");
    eventDTO.setChannel("FIELD");
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    payload.setCcsProperty(ccsPropertyDTO);
    responseManagementEvent.setPayload(payload);
    responseManagementEvent.setEvent(eventDTO);

    // When
    rabbitQueueHelper.sendMessage(ccsPropertyListedQueue, responseManagementEvent);

    // Then
    rabbitQueueHelper.checkMessageIsNotReceived(outboundQueue, 5);

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isCcsCase()).isTrue();

    List<UacQidLink> actualUacQidLinks = uacQidLinkRepository.findAll();
    assertThat(actualUacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = actualUacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid()).isEqualTo(TEST_QID);
    assertThat(actualUacQidLink.getQid().substring(0, 2)).isEqualTo("71");
    assertThat(actualUacQidLink.isCcsCase()).isTrue();
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);

    validateEvents(eventRepository.findAll(), ccsPropertyDTO);
  }

  @Test
  public void testCCSListedEventForRefusal() throws IOException, InterruptedException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(FIELD_QUEUE);

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    ccsPropertyDTO.setUac(null);
    ccsPropertyDTO.setSampleUnit(setUpSampleUnitDTO());

    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setCollectionCase(collectionCase);

    RefusalDTO refusal = new RefusalDTO();
    refusal.setType(RefusalType.HARD_REFUSAL);
    refusal.setAgentId("test agent");
    refusal.setReport("test report");
    ccsPropertyDTO.setRefusal(refusal);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setType(EventTypeDTO.CCS_ADDRESS_LISTED);
    eventDTO.setSource("FIELDWORK_GATEWAY");
    eventDTO.setChannel("FIELD");
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    payload.setCcsProperty(ccsPropertyDTO);
    responseManagementEvent.setPayload(payload);
    responseManagementEvent.setEvent(eventDTO);

    // When
    rabbitQueueHelper.sendMessage(ccsPropertyListedQueue, responseManagementEvent);

    // Then
    rabbitQueueHelper.checkMessageIsNotReceived(outboundQueue, 5);
    System.out.println(1);

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.isCcsCase()).isTrue();
    assertThat(actualCase.isRefusalReceived()).isTrue();

    List<UacQidLink> actualUacQidLinks = uacQidLinkRepository.findAll();
    assertThat(actualUacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = actualUacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid().substring(0, 2)).isEqualTo("71");
    assertThat(actualUacQidLink.isCcsCase()).isTrue();
    assertThat(actualUacQidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);

    validateEvents(eventRepository.findAll(), ccsPropertyDTO);
  }

  private SampleUnitDTO setUpSampleUnitDTO() {
    SampleUnitDTO sampleUnitDTO = new SampleUnitDTO();
    sampleUnitDTO.setAddressType("HH");
    sampleUnitDTO.setEstabType("test estab type");
    sampleUnitDTO.setAddressLevel("U");
    sampleUnitDTO.setOrganisationName("test org name");
    sampleUnitDTO.setAddressLine1("1 main street");
    sampleUnitDTO.setAddressLine2("upper upperingham");
    sampleUnitDTO.setAddressLine3("swing low");
    sampleUnitDTO.setTownName("upton");
    sampleUnitDTO.setPostcode("UP103UP");
    sampleUnitDTO.setLatitude("50.863849");
    sampleUnitDTO.setLongitude("-1.229710");
    sampleUnitDTO.setFieldCoordinatorId("Field Mouse 1");
    sampleUnitDTO.setFieldOfficerId("007");

    return sampleUnitDTO;
  }

  private void validateEvents(List<Event> events, CCSPropertyDTO expectedCCSPropertyDto)
      throws IOException {
    assertThat(events.size()).isEqualTo(1);

    Event event = events.get(0);
    assertThat(event.getEventChannel()).isEqualTo(CCS_PROPERTY_LISTED_CHANNEL);
    assertThat(event.getEventSource()).isEqualTo(CCS_PROPERTY_LISTED_SOURCE);
    assertThat(event.getEventType()).isEqualTo(EventType.CCS_ADDRESS_LISTED);
    assertThat(event.getEventDescription()).isEqualTo("CCS Address Listed");

    ObjectMapper objectMapper = new ObjectMapper();
    CCSPropertyDTO actualCCSPropertyDTO =
        objectMapper.readValue(event.getEventPayload(), CCSPropertyDTO.class);

    assertThat(actualCCSPropertyDTO).isEqualTo(expectedCCSPropertyDto);
  }
}
