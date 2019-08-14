package uk.gov.ons.census.casesvc.messaging;

import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.BeforeClass;
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
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFulfilmentRequestedEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UacCreatedEventReceiverIT {

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Value("${queueconfig.uac-qid-created-queue}")
  private String uacQidCreatedQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(uacQidCreatedQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testUacUpdatedEventIsEmitted() throws IOException, InterruptedException {
    // Given
    Case linkedCase = getRandomCase();
    caseRepository.saveAndFlush(linkedCase);
    BlockingQueue<String> uacEventQueue = rabbitQueueHelper.listen(rhUacQueue);

    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);

    // When
    String uacCreatedEventJson = convertObjectToJson(uacCreatedEvent);
    Message message =
        MessageBuilder.withBody(uacCreatedEventJson.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();
    rabbitQueueHelper.sendMessage(uacQidCreatedQueue, message);

    // Then
    ResponseManagementEvent uacUpdatedEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(uacEventQueue);
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId().toString(),
        uacUpdatedEvent.getPayload().getUac().getCaseId());
  }
}
