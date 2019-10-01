package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
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
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.CcsToFwmt;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class CCSPropertyListedIT {

  private static String FIELD_QUEUE = "Action.Field";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;

  @Value("${queueconfig.ccs-property-listed-queue}")
  private String ccsPropertyListedQueue;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(FIELD_QUEUE);
    rabbitQueueHelper.purgeQueue(ccsPropertyListedQueue);
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void CCSSubmittedToFieldIT() throws IOException, InterruptedException {

    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(FIELD_QUEUE);

    CCSPropertyDTO ccsPropertyDTO = new CCSPropertyDTO();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    ccsPropertyDTO.setCollectionCase(collectionCase);

    SampleUnitDTO sampleUnitDTO = new SampleUnitDTO();
    sampleUnitDTO.setAddressType("HH");
    sampleUnitDTO.setEstabType("");
    sampleUnitDTO.setAddressLevel("U");
    sampleUnitDTO.setOrganisationName("");
    sampleUnitDTO.setAddressLine1("1 main street");
    sampleUnitDTO.setAddressLine2("upper uppingham");
    sampleUnitDTO.setAddressLine3("swing low");
    sampleUnitDTO.setTownName("Upton");
    sampleUnitDTO.setPostcode("ENG 4EV");
    sampleUnitDTO.setLatitude("50.863849");
    sampleUnitDTO.setLongitude("-1.229710");
    sampleUnitDTO.setFieldCoordinatorId("Field Mouse 1");
    sampleUnitDTO.setFieldOfficerId("007");
    ccsPropertyDTO.setSampleUnit(sampleUnitDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setType(EventTypeDTO.CCS_ADDRESS_LISTED);
    eventDTO.setSource("FIELDWORK_GATEWAY");
    eventDTO.setChannel("FIELD");
    eventDTO.setDateTime(OffsetDateTime.now());
    eventDTO.setTransactionId(UUID.randomUUID());

    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    payload.setCcsProperty(ccsPropertyDTO);
    responseManagementEvent.setPayload(payload);
    responseManagementEvent.setEvent(eventDTO);

    rabbitQueueHelper.sendMessage(ccsPropertyListedQueue, responseManagementEvent);

    CcsToFwmt ccsToFwmt = rabbitQueueHelper.checkCcsFwmtEmitted(outboundQueue);

    assertThat(ccsToFwmt.getCaseId()).isEqualTo(TEST_CASE_ID.toString());
  }
}
