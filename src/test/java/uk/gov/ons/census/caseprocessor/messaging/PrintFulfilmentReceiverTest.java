package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_UAC_METADATA;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PrintFulfilmentDTO;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.CollectionExercise;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.ExportFileTemplate;
import uk.gov.ons.census.common.model.entity.FulfilmentSurveyExportFileTemplate;
import uk.gov.ons.census.common.model.entity.FulfilmentToProcess;
import uk.gov.ons.census.common.model.entity.Survey;

@ExtendWith(MockitoExtension.class)
class PrintFulfilmentReceiverTest {
  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  @InjectMocks private PrintFulfilmentReceiver underTest;

  private static final String PACK_CODE = "PACK_CODE";

  @Test
  void testReceiveMessage() {
    // Given
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.getHeader().setMessageId(UUID.randomUUID());
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setPrintFulfilment(new PrintFulfilmentDTO());
    managementEvent.getPayload().getPrintFulfilment().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getPrintFulfilment().setPackCode(PACK_CODE);
    managementEvent.getPayload().getPrintFulfilment().setUacMetadata(TEST_UAC_METADATA);
    managementEvent
        .getPayload()
        .getPrintFulfilment()
        .setPersonalisation(Map.of("name", "Joe Bloggs"));
    Message<byte[]> message = constructMessage(managementEvent);

    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode(PACK_CODE);

    FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate =
        new FulfilmentSurveyExportFileTemplate();
    fulfilmentSurveyExportFileTemplate.setExportFileTemplate(exportFileTemplate);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(new CollectionExercise());
    expectedCase.getCollectionExercise().setSurvey(new Survey());
    expectedCase
        .getCollectionExercise()
        .getSurvey()
        .setFulfilmentExportFileTemplates(List.of(fulfilmentSurveyExportFileTemplate));

    when(fulfilmentToProcessRepository.existsByMessageId(any(UUID.class))).thenReturn(false);
    when(caseService.getCase(any(UUID.class))).thenReturn(expectedCase);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<FulfilmentToProcess> fulfilmentToProcessArgCapt =
        ArgumentCaptor.forClass(FulfilmentToProcess.class);
    verify(fulfilmentToProcessRepository).saveAndFlush(fulfilmentToProcessArgCapt.capture());
    FulfilmentToProcess fulfilmentToProcess = fulfilmentToProcessArgCapt.getValue();
    assertThat(fulfilmentToProcess.getExportFileTemplate()).isEqualTo(exportFileTemplate);
    assertThat(fulfilmentToProcess.getCaze()).isEqualTo(expectedCase);
    assertThat(fulfilmentToProcess.getUacMetadata()).isEqualTo(TEST_UAC_METADATA);
    assertThat(fulfilmentToProcess.getPersonalisation()).containsEntry("name", "Joe Bloggs");

    verify(fulfilmentToProcessRepository)
        .existsByMessageId(eq(managementEvent.getHeader().getMessageId()));

    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            eq(managementEvent),
            eq(message));
  }

  @Test
  void testReceiveMessageDuplicate() {
    // Given
    // get Logback Logger
    Logger fooLogger = (Logger) LoggerFactory.getLogger(PrintFulfilmentReceiver.class);

    // create and start a ListAppender
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();

    // add the appender to the logger
    fooLogger.addAppender(listAppender);

    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setMessageId(UUID.randomUUID());
    managementEvent.getHeader().setCorrelationId(UUID.randomUUID());
    managementEvent.getHeader().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setPrintFulfilment(new PrintFulfilmentDTO());
    managementEvent.getPayload().getPrintFulfilment().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getPrintFulfilment().setPackCode(PACK_CODE);
    managementEvent.getPayload().getPrintFulfilment().setUacMetadata(TEST_UAC_METADATA);
    managementEvent
        .getPayload()
        .getPrintFulfilment()
        .setPersonalisation(Map.of("name", "Joe Bloggs"));
    Message<byte[]> message = constructMessage(managementEvent);

    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode(PACK_CODE);

    FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate =
        new FulfilmentSurveyExportFileTemplate();
    fulfilmentSurveyExportFileTemplate.setExportFileTemplate(exportFileTemplate);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(new CollectionExercise());
    expectedCase.getCollectionExercise().setSurvey(new Survey());
    expectedCase
        .getCollectionExercise()
        .getSurvey()
        .setFulfilmentExportFileTemplates(List.of(fulfilmentSurveyExportFileTemplate));
    when(caseService.getCase(any(UUID.class))).thenReturn(expectedCase);

    when(fulfilmentToProcessRepository.existsByMessageId(any(UUID.class)))
        .thenReturn(false)
        .thenReturn(true);

    // When
    underTest.receiveMessage(message);
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<FulfilmentToProcess> fulfilmentToProcessArgCapt =
        ArgumentCaptor.forClass(FulfilmentToProcess.class);
    verify(fulfilmentToProcessRepository).saveAndFlush(fulfilmentToProcessArgCapt.capture());
    FulfilmentToProcess fulfilmentToProcess = fulfilmentToProcessArgCapt.getValue();
    assertThat(fulfilmentToProcess.getExportFileTemplate()).isEqualTo(exportFileTemplate);
    assertThat(fulfilmentToProcess.getCaze()).isEqualTo(expectedCase);
    assertThat(fulfilmentToProcess.getUacMetadata()).isEqualTo(TEST_UAC_METADATA);
    assertThat(fulfilmentToProcess.getPersonalisation()).containsEntry("name", "Joe Bloggs");

    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            eq("Print fulfilment requested"),
            eq(EventType.PRINT_FULFILMENT),
            eq(managementEvent),
            eq(message));

    List<ILoggingEvent> logsList = listAppender.list;
    assertThat(logsList.size()).isEqualTo(1);
    String expecetedLogMessage =
        String.format(
            "Received duplicate fulfilment message ID, ignoring and acking the duplicate message");
    assertThat(logsList.get(0).getMessage()).isEqualTo(expecetedLogMessage);
  }

  @Test
  public void packCodeNotAllowedRunTimeException() {
    // Given
    EventDTO managementEvent = new EventDTO();
    managementEvent.setHeader(new EventHeaderDTO());
    managementEvent.getHeader().setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    managementEvent.getHeader().setDateTime(OffsetDateTime.now(ZoneId.of("UTC")).minusHours(1));
    managementEvent.getHeader().setTopic("Test topic");
    managementEvent.getHeader().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setPrintFulfilment(new PrintFulfilmentDTO());
    managementEvent.getPayload().getPrintFulfilment().setCaseId(UUID.randomUUID());
    managementEvent.getPayload().getPrintFulfilment().setPackCode(PACK_CODE);
    managementEvent.getPayload().getPrintFulfilment().setUacMetadata(TEST_UAC_METADATA);
    managementEvent
        .getPayload()
        .getPrintFulfilment()
        .setPersonalisation(Map.of("name", "Joe Bloggs"));
    Message<byte[]> message = constructMessage(managementEvent);

    ExportFileTemplate exportFileTemplate = new ExportFileTemplate();
    exportFileTemplate.setPackCode("Different PackCode");

    FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate =
        new FulfilmentSurveyExportFileTemplate();
    fulfilmentSurveyExportFileTemplate.setExportFileTemplate(exportFileTemplate);

    Case expectedCase = new Case();
    expectedCase.setCollectionExercise(new CollectionExercise());
    Survey survey = new Survey();
    survey.setName("MyTestSurvey");
    survey.setFulfilmentExportFileTemplates(List.of(fulfilmentSurveyExportFileTemplate));
    expectedCase.getCollectionExercise().setSurvey(survey);
    when(caseService.getCase(any(UUID.class))).thenReturn(expectedCase);

    // When
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> underTest.receiveMessage(message));
    assertThat(thrown.getMessage())
        .isEqualTo("Pack code PACK_CODE is not allowed as a fulfilment on survey MyTestSurvey");
    verifyNoInteractions(eventLogger);
  }
}
