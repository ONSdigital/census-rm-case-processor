package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.EventService.CREATE_CASE_SAMPLE_RECEIVED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FieldCaseSelected;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.PrintCaseSelected;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

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
    when(caseService.saveCaseSample(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    when(uacService.buildUacQidLink(caze, 1)).thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdatedEvent(any(UacQidLink.class))).thenReturn(new PayloadDTO());
    when(caseService.saveCaseAndEmitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processSampleReceivedMessage(createCaseSample, messageTimestamp);

    // Then
    verify(caseService).saveCaseSample(createCaseSample);
    verify(uacService).buildUacQidLink(eq(caze), eq(1));
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
  public void testWelshQuestionnaire() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_QF2R1W");
    when(caseService.saveCaseSample(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    UacQidLink secondUacQidLink = new UacQidLink();
    when(uacService.buildUacQidLink(caze, 2)).thenReturn(uacQidLink);
    when(uacService.buildUacQidLink(caze, 3)).thenReturn(secondUacQidLink);
    when(uacService.saveAndEmitUacUpdatedEvent(any(UacQidLink.class))).thenReturn(new PayloadDTO());
    when(caseService.saveCaseAndEmitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.processSampleReceivedMessage(createCaseSample, messageTimestamp);

    // Then
    verify(caseService).saveCaseSample(createCaseSample);
    verify(uacService, times(1)).buildUacQidLink(eq(caze), eq(2));
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
    when(caseService.getCaseByCaseRef(eq(123L))).thenReturn(caze);

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
    printCaseSelected.setActionRuleId("Test actionRuleId");
    printCaseSelected.setBatchId("Test batchId");
    printCaseSelected.setCaseRef(123);
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
                "{\"printCaseSelected\":{\"caseRef\":123,\"packCode\":\"Test packCode\","
                    + "\"actionRuleId\":\"Test actionRuleId\",\"batchId\":\"Test batchId\"}}"),
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
    fieldCaseSelected.setActionRuleId("Test actionRuleId");
    fieldCaseSelected.setCaseRef(123);

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
            eq("{\"fieldCaseSelected\":{\"caseRef\":123,\"actionRuleId\":\"Test actionRuleId\"}}"),
            eq(messageTimestamp));
  }
}
