package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class UnaddressedReceiverTestIT {
  @Value("${queueconfig.unaddressed-inbound-queue}")
  private String unaddressedQueue;

  @Value("${queueconfig.rh-uac-queue}")
  private String rhUacQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(unaddressedQueue);
    rabbitQueueHelper.purgeQueue(rhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws Exception {
    try (QueueSpy rhUacQueueSpy = rabbitQueueHelper.listen(rhUacQueue)) {
      // GIVEN
      CreateUacQid createUacQid = new CreateUacQid();
      createUacQid.setQuestionnaireType("21");
      createUacQid.setBatchId(UUID.randomUUID());

      // WHEN
      rabbitQueueHelper.sendMessage(unaddressedQueue, createUacQid);

      // THEN
      ResponseManagementEvent responseManagementEvent =
          rhUacQueueSpy.checkExpectedMessageReceived();
      assertEquals(EventTypeDTO.UAC_UPDATED, responseManagementEvent.getEvent().getType());
      assertThat(responseManagementEvent.getPayload().getUac().getQuestionnaireId())
          .startsWith("21");
    }
  }
}
