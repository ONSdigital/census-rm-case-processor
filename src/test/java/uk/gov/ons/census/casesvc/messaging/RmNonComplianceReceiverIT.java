package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.*;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.NonCompliancelTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class RmNonComplianceReceiverIT {

  @Value("${queueconfig.rm-non-compliance-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      UUID testCaseId = UUID.randomUUID();
      Case caze = new Case();
      caze.setSurvey("CENSUS");
      caze.setCaseRef(123435L);
      caze.setCaseId(testCaseId);
      caseRepository.saveAndFlush(caze);

      ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
      EventDTO eventDTO = new EventDTO();
      responseManagementEvent.setEvent(eventDTO);
      eventDTO.setType(SELECTED_FOR_NON_COMPLIANCE);
      eventDTO.setSource("NON_COMPLIANCE");
      eventDTO.setChannel("NC");
      eventDTO.setDateTime(OffsetDateTime.now());
      eventDTO.setTransactionId(UUID.randomUUID());

      PayloadDTO payloadDTO = new PayloadDTO();
      responseManagementEvent.setPayload(payloadDTO);
      CollectionCase collectionCase = new CollectionCase();
      payloadDTO.setCollectionCase(collectionCase);
      collectionCase.setId(testCaseId);
      collectionCase.setNonComplianceStatus(NonCompliancelTypeDTO.NCF);

      String json = convertObjectToJson(responseManagementEvent);

      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      ResponseManagementEvent actualEmittedMessage = rhCaseQueueSpy.checkExpectedMessageReceived();
      assertThat(actualEmittedMessage.getEvent().getType()).isEqualTo(CASE_UPDATED);

      CollectionCase actualUpdatedCollectionCase =
          actualEmittedMessage.getPayload().getCollectionCase();
      assertThat(actualUpdatedCollectionCase.getId()).isEqualTo(testCaseId);
      assertThat(actualUpdatedCollectionCase.getMetadata().getNonCompliance())
          .isEqualTo(NonCompliancelTypeDTO.NCF);

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event loggedEvent = events.get(0);
      assertThat(loggedEvent.getEventType()).isEqualTo(EventType.SELECTED_FOR_NON_COMPLIANCE);
    }
  }
}
