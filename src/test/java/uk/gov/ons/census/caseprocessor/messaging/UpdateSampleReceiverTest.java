package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.UpdateSample;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.EventType;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.LengthRule;
import uk.gov.ons.ssdc.common.validation.Rule;

@ExtendWith(MockitoExtension.class)
public class UpdateSampleReceiverTest {

  @Mock private CaseService caseService;
  @Mock private EventLogger eventLogger;

  @InjectMocks UpdateSampleReceiver underTest;

  @Test
  void testUpdateSampleReceiverUpdatesExistingSampleData() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.getHeader().setCorrelationId(TEST_CORRELATION_ID);
    managementEvent.getHeader().setOriginatingUser(TEST_ORIGINATING_USER);
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setUpdateSample(new UpdateSample());
    managementEvent.getPayload().getUpdateSample().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getUpdateSample().setSample(Map.of("testSampleField", "Updated"));
    Message<byte[]> message = constructMessage(managementEvent);

    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setSampleValidationRules(
        new ColumnValidator[] {
          new ColumnValidator("testSampleField", false, new Rule[] {new LengthRule(30)})
        });
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(collex);
    Map<String, String> sampleData = new HashMap<>();
    sampleData.put("testSampleField", "Test");
    expectedCase.setSample(sampleData);
    expectedCase.setSampleSensitive(new HashMap<>());

    when(caseService.getCaseAndLockForUpdate(any(UUID.class))).thenReturn(expectedCase);

    // when
    underTest.receiveMessage(message);

    // then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);

    verify(caseService)
        .saveCaseAndEmitCaseUpdate(
            caseArgumentCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getSample()).isEqualTo(Map.of("testSampleField", "Updated"));

    verify(eventLogger)
        .logCaseEvent(
            expectedCase, "Sample data updated", EventType.UPDATE_SAMPLE, managementEvent, message);
  }

  @Test
  void testCannotUpdateSampleReceiverCreateNewSampleData() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setUpdateSample(new UpdateSample());
    managementEvent.getPayload().getUpdateSample().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getUpdateSample().setSample(Map.of("newThing", "abc123"));
    Message<byte[]> message = constructMessage(managementEvent);

    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setSampleValidationRules(
        new ColumnValidator[] {
          new ColumnValidator("PHONE_NUMBER", false, new Rule[] {new LengthRule(30)})
        });
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(collex);
    Map<String, String> existingSampleData = new HashMap<>();
    existingSampleData.put("testThing", "xyz666");
    expectedCase.setSample(existingSampleData);
    when(caseService.getCaseAndLockForUpdate(any(UUID.class))).thenReturn(expectedCase);

    // When, then throws
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));
    assertThat(thrown.getMessage())
        .isEqualTo("Column name 'newThing' is not within defined sample");

    verify(caseService, never()).saveCaseAndEmitCaseUpdate(any(), any(), any());
    verify(eventLogger, never()).logCaseEvent(any(), any(), any(), any(), any(Message.class));
  }

  @Test
  void testCannotUseUpdateSampleMessageKeyOnExistingSensitiveKey() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setUpdateSample(new UpdateSample());
    managementEvent.getPayload().getUpdateSample().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getUpdateSample().setSample(Map.of("testThing", "abc123"));
    managementEvent.getPayload().getUpdateSample().setSample(Map.of("mobileNumber", "999999999"));
    Message<byte[]> message = constructMessage(managementEvent);

    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setSampleValidationRules(
        new ColumnValidator[] {
          new ColumnValidator("testThing", true, new Rule[] {new LengthRule(4)})
        });
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(collex);
    Map<String, String> existingSampleData = new HashMap<>();
    existingSampleData.put("testThing", "xyz666");
    expectedCase.setSample(existingSampleData);
    Map<String, String> existingSensitiveSampleData = new HashMap<>();
    existingSensitiveSampleData.put("mobileNumber", "111111111");
    expectedCase.setSampleSensitive(existingSensitiveSampleData);
    when(caseService.getCaseAndLockForUpdate(any(UUID.class))).thenReturn(expectedCase);

    // When, then throws
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));
    assertThat(thrown.getMessage())
        .isEqualTo("Column name 'mobileNumber' is not within defined sample");

    verify(caseService, never()).saveCaseAndEmitCaseUpdate(any(), any(), any());
    verify(eventLogger, never()).logCaseEvent(any(), any(), any(), any(), any(Message.class));
  }

  @Test
  void testUpdateSampleReceiverFailsValidation() {
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setUpdateSample(new UpdateSample());
    managementEvent.getPayload().getUpdateSample().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getUpdateSample().setSample(Map.of("testSampleField", "Testing"));
    Message<byte[]> message = constructMessage(managementEvent);

    // Given
    Survey survey = new Survey();
    survey.setId(UUID.randomUUID());
    survey.setSampleValidationRules(
        new ColumnValidator[] {
          new ColumnValidator("testSampleField", false, new Rule[] {new LengthRule(4)})
        });
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());
    collex.setSurvey(survey);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(collex);
    Map<String, String> existingSampleData = new HashMap<>();
    existingSampleData.put("testSampleField", "Test");
    expectedCase.setSample(existingSampleData);
    when(caseService.getCaseAndLockForUpdate(any(UUID.class))).thenReturn(expectedCase);

    // When, then throws
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "UPDATE_SAMPLE event: Column 'testSampleField' Failed validation for Rule 'LengthRule' validation error: Exceeded max length of 4");

    verify(caseService, never()).saveCase(any());
    verify(eventLogger, never()).logCaseEvent(any(), any(), any(), any(), any(Message.class));
  }
}
