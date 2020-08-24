package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;

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
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class DeactivateUacReceiverIT {
  private static final String TEST_QID = "012345678";

  @Value("${queueconfig.rh-uac-queue}")
  private String caseRhUacQueue;

  @Value("${queueconfig.deactivate-uac-queue}")
  private String deactivateUacQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;
  @Autowired private EventRepository eventRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(deactivateUacQueue);
    rabbitQueueHelper.purgeQueue(caseRhUacQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
  }

  @Test
  public void testDeactivateUacReceiver() throws Exception {
    try (QueueSpy uacRhQueue = rabbitQueueHelper.listen(caseRhUacQueue)) {
      // GIVEN
      ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
      EventDTO eventDTO = new EventDTO();
      eventDTO.setType(EventTypeDTO.DEACTIVATE_UAC);
      responseManagementEvent.setEvent(eventDTO);

      PayloadDTO payloadDTO = new PayloadDTO();
      UacDTO deactivateUacDto = new UacDTO();
      deactivateUacDto.setQuestionnaireId(TEST_QID);
      payloadDTO.setUac(deactivateUacDto);
      responseManagementEvent.setPayload(payloadDTO);

      UacQidLink uacQidLink = new UacQidLink();
      uacQidLink.setId(UUID.randomUUID());
      uacQidLink.setQid(TEST_QID);
      uacQidLink.setUac("test_uac");
      uacQidLinkRepository.save(uacQidLink);

      // WHEN
      rabbitQueueHelper.sendMessage(deactivateUacQueue, responseManagementEvent);

      // THEN
      ResponseManagementEvent actualResponseManagementEvent =
          uacRhQueue.checkExpectedMessageReceived();

      UacDTO uac = actualResponseManagementEvent.getPayload().getUac();
      assertThat(uac.getQuestionnaireId()).isEqualTo(TEST_QID);
      assertFalse(uac.getActive());

      UacQidLink sentUacQidLinkUpdated = uacQidLinkRepository.findByQid(TEST_QID).get();

      assertFalse(sentUacQidLinkUpdated.isActive());

      Event event = eventRepository.findAll().get(0);
      assertThat(event.getEventType()).isEqualTo(EventType.DEACTIVATE_UAC);
    }
  }
}
