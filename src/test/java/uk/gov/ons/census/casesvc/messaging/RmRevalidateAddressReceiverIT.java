package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.dto.ActionInstructionType.UPDATE;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.CASE_UPDATED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RM_REVALIDATE_ADDRESS;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.convertJsonToObject;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

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
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmRevalidateAddress;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.QueueSpy;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class RmRevalidateAddressReceiverIT {
  @Value("${queueconfig.rm-revalidate-address-queue}")
  private String inboundQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testHappyPath() throws Exception {
    try (QueueSpy rhCaseQueueSpy = rabbitQueueHelper.listen(rhCaseQueue)) {
      // Given
      Case invalidAddressCase = new Case();
      UUID testCaseId = UUID.randomUUID();

      invalidAddressCase.setCaseId(testCaseId);
      invalidAddressCase.setAddressInvalid(true);

      invalidAddressCase.setAddressLine1("TEST existing address line 1");
      invalidAddressCase.setEstabType("TEST ESTAB");
      invalidAddressCase.setAddressType("HH");
      invalidAddressCase.setCaseType("HH");
      invalidAddressCase.setAddressLevel("U");
      invalidAddressCase.setTownName("Springfield");
      invalidAddressCase.setPostcode("AB12CD");
      invalidAddressCase.setRegion("E");
      invalidAddressCase.setCaseRef(123456789L);
      invalidAddressCase.setHtcWillingness("1");
      invalidAddressCase.setHtcDigital("1");
      invalidAddressCase.setAbpCode("7");
      invalidAddressCase.setUprn("Dummy");
      invalidAddressCase.setEstabUprn("Dummy");
      invalidAddressCase.setSurvey("CENSUS");

      caseRepository.saveAndFlush(invalidAddressCase);

      ResponseManagementEvent rme = new ResponseManagementEvent();

      EventDTO event = new EventDTO();
      event.setChannel("TEST");
      event.setType(RM_REVALIDATE_ADDRESS);
      rme.setEvent(event);

      PayloadDTO payload = new PayloadDTO();
      rme.setPayload(payload);

      RmRevalidateAddress rmRevalidateAddress = new RmRevalidateAddress();
      payload.setRmRevalidateAddress(rmRevalidateAddress);

      rmRevalidateAddress.setCaseId(testCaseId);

      String json = convertObjectToJson(rme);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // When
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      // Then
      ResponseManagementEvent actualEmittedMessageToRh =
          rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualEmittedMessageToRh.getEvent().getType()).isEqualTo(CASE_UPDATED);
      assertThat(actualEmittedMessageToRh.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(RM_REVALIDATE_ADDRESS);
      assertThat(actualEmittedMessageToRh.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(UPDATE);

      CollectionCase actualUpdatedCollectionCase =
          actualEmittedMessageToRh.getPayload().getCollectionCase();
      assertThat(actualUpdatedCollectionCase.getAddressInvalid()).isFalse();
      assertThat(actualUpdatedCollectionCase.getId()).isEqualTo(testCaseId);

      Case actualUpdatedCase = caseRepository.findById(testCaseId).get();
      assertThat(actualUpdatedCase.isAddressInvalid()).isFalse();

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event actualEvent = events.get(0);

      assertThat(actualEvent.getEventType()).isEqualTo(EventType.RM_REVALIDATE_ADDRESS);
      RmRevalidateAddress actualEventPayload =
          convertJsonToObject(actualEvent.getEventPayload(), RmRevalidateAddress.class);
      assertThat(actualEventPayload).isEqualToComparingFieldByField(rmRevalidateAddress);
    }
  }
}
