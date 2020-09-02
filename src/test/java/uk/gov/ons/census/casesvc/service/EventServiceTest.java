package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.EventService.CREATE_CASE_SAMPLE_RECEIVED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {
  private static final long TEST_CASE_REF = 1234567890L;
  private static final UUID TEST_ACTION_RULE_ID = UUID.randomUUID();
  private static final UUID TEST_BATCH_ID = UUID.randomUUID();
  private static final String CREATE_BULK_CASE_SAMPLE_RECEIVED = "Create bulk case sample received";

  @Mock CaseService caseService;

  @Mock UacService uacService;

  @Mock EventLogger eventLogger;

  @InjectMocks EventService underTest;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_LF2R3BE");
    caze.setRegion("E1000");
    caze.setCaseType("HH");
    when(caseService.saveCaseSample(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    when(uacService.buildUacQidLink(eq(caze), eq(1), eq(null), any())).thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdatedEvent(any(UacQidLink.class))).thenReturn(new PayloadDTO());
    when(caseService.saveCaseAndEmitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processSampleReceivedMessage(createCaseSample, messageTimestamp);

    // Then
    verify(caseService).saveCaseSample(createCaseSample);
    verify(uacService).buildUacQidLink(eq(caze), eq(1), eq(null), any());
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLink);
    verify(caseService).saveCaseAndEmitCaseCreatedEvent(caze);

    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq(CREATE_CASE_SAMPLE_RECEIVED),
            eq(EventType.SAMPLE_LOADED),
            any(EventDTO.class),
            eq(convertObjectToJson(createCaseSample)),
            eq(messageTimestamp));
  }

  @Test
  public void testBulkProcessedNewAddress() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setBulkProcessed(true);
    Case caze = new Case();
    caze.setTreatmentCode("HH_LF2R3BE");
    caze.setRegion("E1000");
    caze.setCaseType("HH");
    when(caseService.saveCaseSample(createCaseSample)).thenReturn(caze);
    when(caseService.saveCaseAndEmitCaseCreatedEvent(any(Case.class), any(Metadata.class)))
        .thenReturn(new PayloadDTO());
    Metadata expectedMetadata =
        buildMetadata(EventTypeDTO.SAMPLE_LOADED, ActionInstructionType.CREATE);

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processSampleReceivedMessage(createCaseSample, messageTimestamp);

    // Then
    verify(caseService).saveCaseSample(createCaseSample);
    verifyNoInteractions(uacService);
    verify(caseService).saveCaseAndEmitCaseCreatedEvent(eq(caze), eq(expectedMetadata));
    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq(CREATE_BULK_CASE_SAMPLE_RECEIVED),
            eq(EventType.SAMPLE_LOADED),
            any(EventDTO.class),
            eq(convertObjectToJson(createCaseSample)),
            eq(messageTimestamp));
  }

  @Test
  public void testWelshQuestionnaire() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_QF2R1W");
    caze.setRegion("W1000");
    caze.setCaseType("HH");
    when(caseService.saveCaseSample(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    UacQidLink secondUacQidLink = new UacQidLink();
    when(uacService.buildUacQidLink(eq(caze), eq(2), eq(null), any())).thenReturn(uacQidLink);
    when(uacService.buildUacQidLink(eq(caze), eq(3), eq(null), any())).thenReturn(secondUacQidLink);
    when(uacService.saveAndEmitUacUpdatedEvent(any(UacQidLink.class))).thenReturn(new PayloadDTO());
    when(caseService.saveCaseAndEmitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processSampleReceivedMessage(createCaseSample, messageTimestamp);

    // Then
    verify(caseService).saveCaseSample(createCaseSample);
    verify(uacService, times(1)).buildUacQidLink(eq(caze), eq(2), eq(null), any());
    verify(uacService, times(2)).saveAndEmitUacUpdatedEvent(uacQidLink);
    verify(caseService).saveCaseAndEmitCaseCreatedEvent(caze);

    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq(CREATE_CASE_SAMPLE_RECEIVED),
            eq(EventType.SAMPLE_LOADED),
            any(EventDTO.class),
            eq(convertObjectToJson(createCaseSample)),
            eq(messageTimestamp));
  }

  @Test
  public void testProcessPrintCaseSelected() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    when(caseService.getCaseByCaseRef(eq(TEST_CASE_REF))).thenReturn(caze);

    // When
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.PRINT_CASE_SELECTED);
    event.setChannel("Test channel");
    event.setDateTime(OffsetDateTime.now());
    event.setSource("Test source");
    event.setTransactionId(UUID.randomUUID());
    responseManagementEvent.setEvent(event);

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    PrintCaseSelected printCaseSelected = new PrintCaseSelected();
    printCaseSelected.setActionRuleId(TEST_ACTION_RULE_ID);
    printCaseSelected.setBatchId(TEST_BATCH_ID);
    printCaseSelected.setCaseRef(TEST_CASE_REF);
    printCaseSelected.setPackCode("Test packCode");

    PayloadDTO payload = new PayloadDTO();
    payload.setPrintCaseSelected(printCaseSelected);
    responseManagementEvent.setPayload(payload);

    underTest.processPrintCaseSelected(responseManagementEvent, messageTimestamp);

    // Then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq("Case sent to printer with pack code Test packCode"),
            eq(EventType.PRINT_CASE_SELECTED),
            eq(event),
            eq(
                "{\"printCaseSelected\":{\"caseRef\":1234567890,\"packCode\":\"Test packCode\","
                    + "\"actionRuleId\":\""
                    + TEST_ACTION_RULE_ID.toString()
                    + "\",\"batchId\":\""
                    + TEST_BATCH_ID
                    + "\"}}"),
            eq(messageTimestamp));
  }

  @Test
  public void testProcessFieldCaseSelected() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    when(caseService.getCaseByCaseRef(anyLong())).thenReturn(caze);

    // When
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.FIELD_CASE_SELECTED);
    event.setChannel("Test channel");
    event.setDateTime(OffsetDateTime.now());
    event.setSource("Test source");
    event.setTransactionId(UUID.randomUUID());
    responseManagementEvent.setEvent(event);

    FieldCaseSelected fieldCaseSelected = new FieldCaseSelected();
    fieldCaseSelected.setActionRuleId(UUID.fromString("8faa551c-c04a-4b8a-a164-50c6f3d9d52a"));
    fieldCaseSelected.setCaseRef(TEST_CASE_REF);

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    PayloadDTO payload = new PayloadDTO();
    payload.setFieldCaseSelected(fieldCaseSelected);
    responseManagementEvent.setPayload(payload);

    underTest.processFieldCaseSelected(responseManagementEvent, messageTimestamp);

    // Then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            eq("Case sent for fieldwork followup"),
            eq(EventType.FIELD_CASE_SELECTED),
            eq(event),
            eq(
                "{\"fieldCaseSelected\":{\"caseRef\":1234567890,\"actionRuleId\":\"8faa551c-c04a-4b8a-a164-50c6f3d9d52a\"}}"),
            eq(messageTimestamp));
  }
}
