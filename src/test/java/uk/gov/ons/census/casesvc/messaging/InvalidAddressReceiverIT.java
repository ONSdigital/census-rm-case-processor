package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.skyscreamer.jsonassert.JSONCompareMode.STRICT;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_MODIFIED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_TYPE_CHANGED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.NEW_ADDRESS_REPORTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createNewAddressReportedJson;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createTestAddressModifiedJson;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.createTestAddressTypeChangeJson;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.jeasy.random.EasyRandom;
import org.json.JSONException;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class InvalidAddressReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.invalid-address-inbound-queue}")
  private String invalidAddressInboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(invalidAddressInboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

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
    managementEvent.getEvent().setType(EventTypeDTO.ADDRESS_NOT_VALID);
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setInvalidAddress(new InvalidAddress());
    managementEvent.getPayload().getInvalidAddress().setCollectionCase(new CollectionCaseCaseId());
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
    rabbitQueueHelper.sendMessage(invalidAddressInboundQueue, message);

    // check the emitted eventDTO
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);

    assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    CollectionCase actualPayloadCase = responseManagementEvent.getPayload().getCollectionCase();
    assertThat(actualPayloadCase.getId()).isEqualTo(caze.getCaseId().toString());
    assertThat(actualPayloadCase.getAddressInvalid()).isTrue();

    // check database for log eventDTO
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    assertThat(event.getEventDescription()).isEqualTo("Invalid address");
    assertThat(event.getEventType()).isEqualTo(EventType.ADDRESS_NOT_VALID);
  }

  @Test
  public void testAddressModifiedEventTypeLoggedOnly() throws InterruptedException, JSONException {
    PayloadDTO payload = new PayloadDTO();
    payload.setAddressModification(createTestAddressModifiedJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getAddressModification()),
        ADDRESS_MODIFIED,
        EventType.ADDRESS_MODIFIED,
        "Address modified");
  }

  @Test
  public void testAddressTypeChangeEventTypeLoggedOnly()
      throws InterruptedException, JSONException {
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
  public void testNewAddressReportedEventTypeLoggedOnly()
      throws InterruptedException, JSONException {
    PayloadDTO payload = new PayloadDTO();
    payload.setNewAddressReported(createNewAddressReportedJson(TEST_CASE_ID));

    testEventTypeLoggedOnly(
        payload,
        JsonHelper.convertObjectToJson(payload.getNewAddressReported()),
        NEW_ADDRESS_REPORTED,
        EventType.NEW_ADDRESS_REPORTED,
        "New Address reported");
  }

  public void testEventTypeLoggedOnly(
      PayloadDTO payload,
      String expectedEventPayloadJson,
      EventTypeDTO eventTypeDTO,
      EventType eventType,
      String eventDescription)
      throws InterruptedException, JSONException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

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
    rabbitQueueHelper.sendMessage(invalidAddressInboundQueue, message);

    // Check no message emitted
    rabbitQueueHelper.checkMessageIsNotReceived(outboundQueue, 3);

    // Check case not changed
    Optional<Case> actualCaseOpt = caseRepository.findByCaseId(caze.getCaseId());
    Case actualCase = actualCaseOpt.get();
    assertThat(actualCase.isAddressInvalid()).isFalse();

    // Event logged is as expected
    List<Event> events = eventRepository.findAll(new Sort(ASC, "rmEventProcessed"));
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
