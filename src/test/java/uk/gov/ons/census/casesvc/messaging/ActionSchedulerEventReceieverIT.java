package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.PrintCaseSelected;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
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
public class ActionSchedulerEventReceieverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.action-case-queue}")
  private String actionCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(actionCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testCaseSelectedForPrintLogsEvent() throws InterruptedException, IOException {
    // GIVEN
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);
    caze.setCaseRef(123);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze = caseRepository.saveAndFlush(caze);

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.PRINT_CASE_SELECTED);
    event.setChannel("Test channel");
    event.setDateTime(OffsetDateTime.now());
    event.setSource("Test source");
    event.setTransactionId(UUID.randomUUID().toString());
    responseManagementEvent.setEvent(event);

    PrintCaseSelected printCaseSelected = new PrintCaseSelected();
    printCaseSelected.setActionRuleId("Test actionRuleId");
    printCaseSelected.setBatchId("Test batchId");
    printCaseSelected.setCaseRef(caze.getCaseRef());
    printCaseSelected.setPackCode("Test packCode");

    PayloadDTO payload = new PayloadDTO();
    payload.setPrintCaseSelected(printCaseSelected);
    responseManagementEvent.setPayload(payload);

    // WHEN
    rabbitQueueHelper.sendMessage(actionCaseQueue, responseManagementEvent);
    Thread.sleep(1000);

    // check database for log event
    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);
    Event actualEvent = events.get(0);
    assertThat(caze.getCaseRef()).isEqualTo(actualEvent.getCaze().getCaseRef());
    assertThat("Test channel").isEqualTo(actualEvent.getEventChannel());
    assertThat("Test source").isEqualTo(actualEvent.getEventSource());
    assertThat(event.getTransactionId()).isEqualTo(actualEvent.getEventTransactionId().toString());
    assertThat(event.getType().toString()).isEqualTo(actualEvent.getEventType().toString());
    assertThat(event.getDateTime()).isEqualTo(actualEvent.getEventDate());

    ObjectMapper objectMapper = new ObjectMapper();
    PayloadDTO actualPayload =
        objectMapper.readValue(actualEvent.getEventPayload(), PayloadDTO.class);
    assertThat(123).isEqualTo(actualPayload.getPrintCaseSelected().getCaseRef());
    assertThat("Test packCode").isEqualTo(actualPayload.getPrintCaseSelected().getPackCode());
    assertThat("Test batchId").isEqualTo(actualPayload.getPrintCaseSelected().getBatchId());
    assertThat("Test actionRuleId")
        .isEqualTo(actualPayload.getPrintCaseSelected().getActionRuleId());
    assertThat(actualEvent.getRmEventProcessed()).isNotNull();
    assertThat("Case selected by Action Rule for print Pack Code Test packCode")
        .isEqualTo(actualEvent.getEventDescription());
  }
}
