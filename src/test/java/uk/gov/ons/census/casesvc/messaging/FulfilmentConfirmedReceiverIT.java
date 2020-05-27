package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.convertJsonToObject;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.List;
import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentInformation;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
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
public class FulfilmentConfirmedReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.fulfilment-confirmed-queue}")
  private String inboundQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testQMFulfilmentConfirmedLogged() throws InterruptedException {
    // GIVEN
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setQid("123456789");
    uacQidLinkRepository.saveAndFlush(uacQidLink);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentConfirmedEvent();
    managementEvent.getEvent().setChannel("QM");
    managementEvent.getPayload().getFulfilmentInformation().setQuestionnaireId(uacQidLink.getQid());

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    Thread.sleep(1000);

    // THEN
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    FulfilmentInformation actualFulfilmentInformation =
        convertJsonToObject(event.getEventPayload(), FulfilmentInformation.class);
    assertThat(actualFulfilmentInformation.getQuestionnaireId()).isEqualTo(uacQidLink.getQid());
    assertThat(actualFulfilmentInformation.getFulfilmentCode()).isEqualTo("ABC_XYZ_123");
    assertThat(event.getEventType()).isEqualTo(EventType.FULFILMENT_CONFIRMED);
    assertThat(event.getEventDescription())
        .isEqualTo("Fulfilment Confirmed Received for pack code ABC_XYZ_123");
    assertThat(event.getUacQidLink().getId()).isEqualTo(uacQidLink.getId());
  }

  @Test
  public void testPPOFulfilmentConfirmedLogged() throws InterruptedException {
    // GIVEN
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setCaseRef(123L);
    caze = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent managementEvent = getTestResponseManagementFulfilmentConfirmedEvent();
    managementEvent.getEvent().setChannel("PPO");
    managementEvent
        .getPayload()
        .getFulfilmentInformation()
        .setCaseRef(caze.getCaseRef().toString());

    // WHEN
    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    Thread.sleep(1000);

    // THEN
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event event = events.get(0);
    FulfilmentInformation actualFulfilmentInformation =
        convertJsonToObject(event.getEventPayload(), FulfilmentInformation.class);
    assertThat(actualFulfilmentInformation.getCaseRef()).isEqualTo(caze.getCaseRef().toString());
    assertThat(actualFulfilmentInformation.getFulfilmentCode()).isEqualTo("ABC_XYZ_123");
    assertThat(event.getEventType()).isEqualTo(EventType.FULFILMENT_CONFIRMED);
    assertThat(event.getEventDescription())
        .isEqualTo("Fulfilment Confirmed Received for pack code ABC_XYZ_123");
    assertThat(event.getCaze().getCaseId()).isEqualTo(caze.getCaseId());
  }

  private ResponseManagementEvent getTestResponseManagementFulfilmentConfirmedEvent() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();

    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setType(EventTypeDTO.FULFILMENT_CONFIRMED);

    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setFulfilmentInformation(new FulfilmentInformation());
    managementEvent.getPayload().getFulfilmentInformation().setFulfilmentCode("ABC_XYZ_123");

    return managementEvent;
  }
}
