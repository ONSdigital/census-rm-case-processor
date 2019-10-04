package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.json.JSONException;
import org.json.JSONObject;
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
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
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
  public void CCSSubmittedToFieldIT() throws IOException, InterruptedException, JSONException {

    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(FIELD_QUEUE);

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setCollectionCase(collectionCase);

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
    ccsPropertyDTO.setSampleUnit(sampleUnitDTO);

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

    // Check database that HI Case is linked to UacQidLink
    List<UacQidLink> actualUacQidLinks = uacQidLinkRepository.findAll();
    assertThat(actualUacQidLinks.size()).isEqualTo(1);
    UacQidLink actualUacQidLink = actualUacQidLinks.get(0);
    assertThat(actualUacQidLink.getQid().substring(0, 2)).isEqualTo("71");
    assertThat(actualUacQidLink.isCcsCase()).isTrue();

    validateEvents(eventRepository.findAll());
  }

  private void validateEvents(List<Event> events) throws JSONException {
    assertThat(events.size()).isEqualTo(1);

    Event event = events.get(0);
    assertThat(event.getEventChannel()).isEqualTo(CCS_PROPERTY_LISTED_CHANNEL);
    assertThat(event.getEventSource()).isEqualTo(CCS_PROPERTY_LISTED_SOURCE);
    assertThat(event.getEventType()).isEqualTo(EventType.CCS_ADDRESS_LISTED);
    assertThat(event.getEventDescription()).isEqualTo("CCS Address Listed");

    JSONObject payload = new JSONObject(event.getEventPayload());
    assertThat(payload.length()).isEqualTo(2);

    JSONObject collectionCasePayload = payload.getJSONObject("collectionCase");
    assertThat(collectionCasePayload.length()).isEqualTo(1);
    assertThat(collectionCasePayload.getString("id")).isEqualTo(TEST_CASE_ID.toString());

    JSONObject sampleUnitPayload = payload.getJSONObject("sampleUnit");
    assertThat(sampleUnitPayload.length()).isEqualTo(13);
    assertThat(sampleUnitPayload.getString("addressType")).isEqualTo("HH");
    assertThat(sampleUnitPayload.getString("estabType")).isEqualTo("test estab type");
    assertThat(sampleUnitPayload.getString("addressLevel")).isEqualTo("U");
    assertThat(sampleUnitPayload.getString("organisationName")).isEqualTo("test org name");
    assertThat(sampleUnitPayload.getString("addressLine1")).isEqualTo("1 main street");
    assertThat(sampleUnitPayload.getString("addressLine2")).isEqualTo("upper upperingham");
    assertThat(sampleUnitPayload.getString("addressLine3")).isEqualTo("swing low");
    assertThat(sampleUnitPayload.getString("townName")).isEqualTo("upton");
    assertThat(sampleUnitPayload.getString("postcode")).isEqualTo("UP103UP");
    assertThat(sampleUnitPayload.getString("latitude")).isEqualTo("50.863849");
    assertThat(sampleUnitPayload.getString("longitude")).isEqualTo("-1.229710");
    assertThat(sampleUnitPayload.getString("fieldcoordinatorId")).isEqualTo("Field Mouse 1");
    assertThat(sampleUnitPayload.getString("fieldofficerId")).isEqualTo("007");
  }
}
