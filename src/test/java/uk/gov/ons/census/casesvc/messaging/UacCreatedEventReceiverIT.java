package uk.gov.ons.census.casesvc.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
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
  public void testReceiveRmUacCreatedEvent() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue)) {
      // Given
      Case linkedCase = getRandomCase();
      caseRepository.saveAndFlush(linkedCase);

      ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);

      // When
      String uacCreatedEventJson = convertObjectToJson(uacCreatedEvent);
      Message message =
          MessageBuilder.withBody(uacCreatedEventJson.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();
      rabbitQueueHelper.sendMessage(uacQidCreatedQueue, message);

      // Then
      // Check the UAC Updated event is emitted
      ResponseManagementEvent uacUpdatedEvent =
          rabbitQueueHelper.checkExpectedMessageReceived(rhUacQueueSpy);
      assertEquals(
          uacCreatedEvent.getPayload().getUacQidCreated().getCaseId().toString(),
          uacUpdatedEvent.getPayload().getUac().getCaseId());

      // Check the Uac Qid Link is created
      Optional<UacQidLink> actualUacQidLink =
          uacQidLinkRepository.findByQid(uacCreatedEvent.getPayload().getUacQidCreated().getQid());
      assertTrue(actualUacQidLink.isPresent());
      assertEquals(
          uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
          actualUacQidLink.get().getUac());
      assertEquals(linkedCase.getCaseId(), actualUacQidLink.get().getCaze().getCaseId());
    }
  }
}
