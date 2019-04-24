package uk.gov.ons.census.casesvc.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventType;
import uk.gov.ons.census.casesvc.model.dto.FanoutEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleReceiverIT {
  @Value("${queueconfig.inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.emit-case-event-rh-queue}")
  private String emitCaseEventRhQueue;

  @Value("${queueconfig.emit-case-event-action-queue}")
  private String emitCaseEventActionQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(emitCaseEventRhQueue);
    rabbitQueueHelper.purgeQueue(emitCaseEventActionQueue);
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> queue1 = rabbitQueueHelper.listen(emitCaseEventRhQueue);
    BlockingQueue<String> queue2 = rabbitQueueHelper.listen(emitCaseEventActionQueue);

    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setPostcode("ABC123");
    createCaseSample.setRgn("E12000009");
    createCaseSample.setTreatmentCode("HH_LF3R2E");

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, createCaseSample);

    // THEN
    ObjectMapper objectMapper = new ObjectMapper();

    String actualMessage = queue1.poll(10, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    FanoutEvent fanoutEvent = objectMapper.readValue(actualMessage, FanoutEvent.class);
    assertNotNull(fanoutEvent);
    assertEquals("RM", fanoutEvent.getEvent().getChannel());

    actualMessage = queue1.poll(10, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    fanoutEvent = objectMapper.readValue(actualMessage, FanoutEvent.class);
    assertNotNull(fanoutEvent);
    assertEquals("RM", fanoutEvent.getEvent().getChannel());

    actualMessage = queue2.poll(10, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    fanoutEvent = objectMapper.readValue(actualMessage, FanoutEvent.class);
    assertNotNull(fanoutEvent);

    boolean haveSeenCaseCreatedEvent = false;
    boolean haveSeenUacUpdatedEvent = false;

    if (fanoutEvent.getEvent().getType() == EventType.CASE_CREATED) {
      haveSeenCaseCreatedEvent = true;
    } else if (fanoutEvent.getEvent().getType() == EventType.UAC_UPDATED) {
      haveSeenUacUpdatedEvent = true;
    }

    actualMessage = queue2.poll(10, TimeUnit.SECONDS);
    assertNotNull("Did not receive message before timeout", actualMessage);
    fanoutEvent = objectMapper.readValue(actualMessage, FanoutEvent.class);
    assertNotNull(fanoutEvent);

    if (fanoutEvent.getEvent().getType() == EventType.CASE_CREATED) {
      haveSeenCaseCreatedEvent = true;
    } else if (fanoutEvent.getEvent().getType() == EventType.UAC_UPDATED) {
      haveSeenUacUpdatedEvent = true;
    }

    assertTrue(haveSeenCaseCreatedEvent);
    assertTrue(haveSeenUacUpdatedEvent);

    List<Case> caseList = caseRepository.findAll();
    assertEquals(1, caseList.size());
    assertEquals("ABC123", caseList.get(0).getPostcode());
  }
}
