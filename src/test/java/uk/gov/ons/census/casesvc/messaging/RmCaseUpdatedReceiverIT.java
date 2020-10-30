package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.model.dto.ActionInstructionType.UPDATE;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.CASE_UPDATED;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RM_CASE_UPDATED;
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
import uk.gov.ons.census.casesvc.model.dto.RmCaseUpdated;
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
public class RmCaseUpdatedReceiverIT {
  @Value("${queueconfig.rm-case-updated-queue}")
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
      UUID testCaseId = UUID.randomUUID();
      Case caze = setUpMinimumGoodSkeletonCase();
      caze.setCaseId(testCaseId);
      caseRepository.saveAndFlush(caze);

      ResponseManagementEvent rme = setUpMinimumGoodRmCaseUpdatedEvent();
      RmCaseUpdated rmCaseUpdated = rme.getPayload().getRmCaseUpdated();
      rmCaseUpdated.setCaseId(testCaseId);

      String json = convertObjectToJson(rme);
      Message message =
          MessageBuilder.withBody(json.getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .build();

      // WHEN
      rabbitQueueHelper.sendMessage(inboundQueue, message);

      ResponseManagementEvent actualEmittedMessage = rhCaseQueueSpy.checkExpectedMessageReceived();

      assertThat(actualEmittedMessage.getEvent().getType()).isEqualTo(CASE_UPDATED);
      assertThat(actualEmittedMessage.getPayload().getMetadata().getCauseEventType())
          .isEqualTo(RM_CASE_UPDATED);
      assertThat(actualEmittedMessage.getPayload().getMetadata().getFieldDecision())
          .isEqualTo(UPDATE);

      CollectionCase actualUpdatedCollectionCase =
          actualEmittedMessage.getPayload().getCollectionCase();
      assertThat(actualUpdatedCollectionCase.isSkeleton()).isFalse();
      assertThat(actualUpdatedCollectionCase.getTreatmentCode())
          .isEqualTo(rmCaseUpdated.getTreatmentCode());
      assertThat(actualUpdatedCollectionCase.getOa()).isEqualTo(rmCaseUpdated.getOa());
      assertThat(actualUpdatedCollectionCase.getMsoa()).isEqualTo(rmCaseUpdated.getMsoa());
      assertThat(actualUpdatedCollectionCase.getLsoa()).isEqualTo(rmCaseUpdated.getLsoa());
      assertThat(actualUpdatedCollectionCase.getFieldCoordinatorId())
          .isEqualTo(rmCaseUpdated.getFieldCoordinatorId());
      assertThat(actualUpdatedCollectionCase.getFieldOfficerId())
          .isEqualTo(rmCaseUpdated.getFieldOfficerId());
      assertThat(actualUpdatedCollectionCase.getAddress().getEstabType())
          .isEqualTo(rmCaseUpdated.getEstabType());
      assertThat(actualUpdatedCollectionCase.getAddress().getLatitude())
          .isEqualTo(rmCaseUpdated.getLatitude());
      assertThat(actualUpdatedCollectionCase.getAddress().getLongitude())
          .isEqualTo(rmCaseUpdated.getLongitude());

      Case actualUpdatedCase = caseRepository.findById(testCaseId).get();
      assertThat(actualUpdatedCase.isSkeleton()).isFalse();
      assertThat(actualUpdatedCase.getTreatmentCode()).isEqualTo(rmCaseUpdated.getTreatmentCode());
      assertThat(actualUpdatedCase.getOa()).isEqualTo(rmCaseUpdated.getOa());
      assertThat(actualUpdatedCase.getMsoa()).isEqualTo(rmCaseUpdated.getMsoa());
      assertThat(actualUpdatedCase.getLsoa()).isEqualTo(rmCaseUpdated.getLsoa());
      assertThat(actualUpdatedCase.getFieldCoordinatorId())
          .isEqualTo(rmCaseUpdated.getFieldCoordinatorId());
      assertThat(actualUpdatedCase.getFieldOfficerId())
          .isEqualTo(rmCaseUpdated.getFieldOfficerId());
      assertThat(actualUpdatedCase.getEstabType()).isEqualTo(rmCaseUpdated.getEstabType());
      assertThat(actualUpdatedCase.getLatitude()).isEqualTo(rmCaseUpdated.getLatitude());
      assertThat(actualUpdatedCase.getLongitude()).isEqualTo(rmCaseUpdated.getLongitude());

      List<Event> events = eventRepository.findAll();
      assertThat(events.size()).isEqualTo(1);
      Event actualEvent = events.get(0);

      assertThat(actualEvent.getEventType()).isEqualTo(EventType.RM_CASE_UPDATED);
      RmCaseUpdated actualEventPayload =
          convertJsonToObject(actualEvent.getEventPayload(), RmCaseUpdated.class);
      assertThat(actualEventPayload).isEqualToComparingFieldByField(rmCaseUpdated);
    }
  }

  private Case setUpMinimumGoodSkeletonCase() {
    Case caze = new Case();
    caze.setSkeleton(true);
    caze.setCaseId(UUID.randomUUID());
    caze.setAddressLine1("TEST existing address line 1");
    caze.setAddressType("HH");
    caze.setCaseType("HH");
    caze.setAddressLevel("U");
    caze.setTownName("Springfield");
    caze.setPostcode("AB12CD");
    caze.setRegion("Enonsensenumbers");
    caze.setCaseRef(123456789L);
    caze.setHtcWillingness("1");
    caze.setHtcDigital("1");
    caze.setAbpCode("7");
    caze.setUprn("Dummy");
    caze.setEstabUprn("Dummy");
    caze.setSurvey("CENSUS");
    caze.setRefusalReceived(null);
    return caze;
  }

  private ResponseManagementEvent setUpMinimumGoodRmCaseUpdatedEvent() {
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    event.setChannel("TEST");
    event.setType(RM_CASE_UPDATED);
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    RmCaseUpdated rmCaseUpdated = new RmCaseUpdated();
    payload.setRmCaseUpdated(rmCaseUpdated);

    // Set up mandatory data on rm case updated event
    rmCaseUpdated.setTreatmentCode("TEST TreatmentCode CODE");
    rmCaseUpdated.setOa("TEST Oa CODE");
    rmCaseUpdated.setLsoa("TEST Lsoa CODE");
    rmCaseUpdated.setMsoa("TEST Msoa CODE");
    rmCaseUpdated.setLad("TEST Lad CODE");
    rmCaseUpdated.setFieldCoordinatorId("TEST FieldCoordinatorId CODE");
    rmCaseUpdated.setFieldOfficerId("TEST FieldOfficerId CODE");
    rmCaseUpdated.setLatitude("123.456");
    rmCaseUpdated.setLongitude("000.000");
    rmCaseUpdated.setEstabType("ROYAL HOUSEHOLD");
    return rme;
  }
}
