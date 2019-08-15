package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
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
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleReceiverIT {

  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionSchedulerQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    rabbitQueueHelper.purgeQueue(actionSchedulerQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws InterruptedException, IOException, JSONException {
    // GIVEN
    BlockingQueue<String> rhCaseMessages = rabbitQueueHelper.listen(rhCaseQueue);
    BlockingQueue<String> rhUacMessages = rabbitQueueHelper.listen(rhUacQueue);
    BlockingQueue<String> actionMessages = rabbitQueueHelper.listen(actionSchedulerQueue);

    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setPostcode("ABC123");
    createCaseSample.setRegion("E12000009");
    createCaseSample.setTreatmentCode("HH_LF3R2E");

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, createCaseSample);

    // THEN
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(rhCaseMessages);
    assertEquals(EventType.CASE_CREATED, responseManagementEvent.getEvent().getType());
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(rhUacMessages);
    assertEquals(EventType.UAC_UPDATED, responseManagementEvent.getEvent().getType());

    List<EventType> eventTypesSeenDTO = new LinkedList<>();
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(actionMessages);
    eventTypesSeenDTO.add(responseManagementEvent.getEvent().getType());
    responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(actionMessages);
    eventTypesSeenDTO.add(responseManagementEvent.getEvent().getType());

    assertThat(
        eventTypesSeenDTO, containsInAnyOrder(EventType.CASE_CREATED, EventType.UAC_UPDATED));

    List<Case> caseList = caseRepository.findAll();
    assertEquals(1, caseList.size());
    assertEquals("ABC123", caseList.get(0).getPostcode());

    List<Event> eventList = eventRepository.findAll();
    assertThat(eventList.size()).isEqualTo(1);
    Event actualEvent = eventList.get(0);

    ObjectMapper objectMapper = new ObjectMapper();

    String p = actualEvent.getEventPayload();

    CreateCaseSample actualcreateCaseSample =
        objectMapper.readValue(actualEvent.getEventPayload(), CreateCaseSample.class);

    assertThat(actualcreateCaseSample).isEqualTo(createCaseSample);
  }
}
