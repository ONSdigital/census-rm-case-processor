package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.OUTBOUND_CASE_SUBSCRIPTION;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UpdateSampleSensitive;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.testutils.DeleteDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.EventPoller;
import uk.gov.ons.census.caseprocessor.testutils.EventsNotFoundException;
import uk.gov.ons.census.caseprocessor.testutils.JunkDataHelper;
import uk.gov.ons.census.caseprocessor.testutils.PubsubHelper;
import uk.gov.ons.census.caseprocessor.testutils.QueueSpy;
import uk.gov.ons.census.caseprocessor.utils.ObjectMapperFactory;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.Event;
import uk.gov.ons.ssdc.common.model.entity.EventType;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class UpdateSampleSensitiveReceiverIT {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();
  private static final String UPDATE_SAMPLE_SENSITIVE_TOPIC = "event_update-sample-sensitive";
  private static final Long TEST_CASE_REF = 1234567890L;

  @Value("${queueconfig.case-update-topic}")
  private String caseUpdateTopic;

  @Autowired private PubsubHelper pubsubHelper;
  @Autowired private DeleteDataHelper deleteDataHelper;
  @Autowired private JunkDataHelper junkDataHelper;

  @Autowired private CaseRepository caseRepository;
  @Autowired private EventPoller eventPoller;

  @BeforeEach
  public void setUp() {
    pubsubHelper.purgePubsubProjectMessages(OUTBOUND_CASE_SUBSCRIPTION, caseUpdateTopic);
    deleteDataHelper.deleteAllData();
  }

  @Test
  public void testUpdateSampleSensitive()
      throws JsonProcessingException, EventsNotFoundException, InterruptedException {
    // GIVEN
    //
    try (QueueSpy<EventDTO> outboundCaseQueueSpy =
        pubsubHelper.pubsubProjectListen(OUTBOUND_CASE_SUBSCRIPTION, EventDTO.class)) {

      Case caze = new Case();
      caze.setId(TEST_CASE_ID);
      caze.setCaseRef(TEST_CASE_REF);
      caze.setSampleSensitive(Map.of("SensitiveJunk", "02071234567"));
      caze.setCollectionExercise(junkDataHelper.setupJunkCollex());
      caseRepository.saveAndFlush(caze);

      EventDTO event = prepareEvent("SensitiveJunk", "9999999");

      //  When
      pubsubHelper.sendMessageToPubsubProject(UPDATE_SAMPLE_SENSITIVE_TOPIC, event);

      List<Event> databaseEvents = eventPoller.getEvents(1);

      //  Then
      Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
      assertThat(actualCase.getSampleSensitive()).isEqualTo(Map.of("SensitiveJunk", "9999999"));

      assertThat(databaseEvents.size()).isEqualTo(1);
      Event databaseEvent = databaseEvents.get(0);
      assertThat(databaseEvent.getCaze().getId()).isEqualTo(TEST_CASE_ID);
      assertThat(databaseEvent.getType()).isEqualTo(EventType.UPDATE_SAMPLE_SENSITIVE);

      PayloadDTO actualPayload =
          objectMapper.readValue(databaseEvent.getPayload(), PayloadDTO.class);
      assertThat(actualPayload.getUpdateSampleSensitive().getSampleSensitive())
          .isEqualTo(Map.of("SensitiveJunk", "REDACTED"));

      // Get the emitted event and check the sensitive part is redacted
      EventDTO actualEvent = outboundCaseQueueSpy.checkExpectedMessageReceived();
      CaseUpdateDTO emittedCase = actualEvent.getPayload().getCaseUpdate();
      assertThat(emittedCase.getCaseId()).isEqualTo(caze.getId());
      assertThat(emittedCase.getSampleSensitive()).isEqualTo(Map.of("SensitiveJunk", "REDACTED"));
    }
  }

  @Test
  public void testUpdateSampleSensitiveSimultaneousRequestsOnSameCase()
      throws EventsNotFoundException {
    // Given
    Case caze = new Case();
    caze.setId(TEST_CASE_ID);
    caze.setCaseRef(TEST_CASE_REF);
    Map<String, String> sensitiveData = new HashMap<>();
    sensitiveData.put("sensitiveA", "Original value");
    sensitiveData.put("sensitiveB", "Original value");
    sensitiveData.put("sensitiveC", "Original value");
    sensitiveData.put("sensitiveD", "Original value");
    caze.setSampleSensitive(sensitiveData);

    CollectionExercise collectionExercise =
        junkDataHelper.setUpJunkCollexWithThisColumnValidators(
            new ColumnValidator[] {
              new ColumnValidator("sensitiveA", true, new Rule[] {new MandatoryRule()}),
              new ColumnValidator("sensitiveB", true, new Rule[] {new MandatoryRule()}),
              new ColumnValidator("sensitiveC", true, new Rule[] {new MandatoryRule()}),
              new ColumnValidator("sensitiveD", true, new Rule[] {new MandatoryRule()})
            });

    caze.setCollectionExercise(collectionExercise);
    caseRepository.saveAndFlush(caze);

    EventDTO[] events =
        new EventDTO[] {
          prepareEvent("sensitiveA", "Updated"),
          prepareEvent("sensitiveB", "Updated"),
          prepareEvent("sensitiveC", "Updated"),
          prepareEvent("sensitiveD", "Updated")
        };

    // When
    Arrays.stream(events)
        .parallel()
        .forEach(
            event -> {
              pubsubHelper.sendMessageToPubsubProject(UPDATE_SAMPLE_SENSITIVE_TOPIC, event);
            });

    eventPoller.getEvents(4);

    // Then
    Case actualCase = caseRepository.findById(TEST_CASE_ID).get();
    assertThat(actualCase.getSampleSensitive())
        .isEqualTo(
            Map.of(
                "sensitiveA",
                "Updated",
                "sensitiveB",
                "Updated",
                "sensitiveC",
                "Updated",
                "sensitiveD",
                "Updated"));
  }

  private EventDTO prepareEvent(String sampleFieldToUpdate, String newValue) {
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setUpdateSampleSensitive(new UpdateSampleSensitive());
    payloadDTO.getUpdateSampleSensitive().setCaseId(TEST_CASE_ID);
    payloadDTO.getUpdateSampleSensitive().setSampleSensitive(Map.of(sampleFieldToUpdate, newValue));

    EventDTO event = new EventDTO();
    event.setPayload(payloadDTO);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setTopic(UPDATE_SAMPLE_SENSITIVE_TOPIC);
    junkDataHelper.junkify(eventHeader);
    event.setHeader(eventHeader);
    return event;
  }
}
