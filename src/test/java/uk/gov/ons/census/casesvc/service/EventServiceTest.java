package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.EventService.CREATE_CASE_SAMPLE_RECEIVED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Optional;
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
    when(caseService.saveCase(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    when(uacService.generateAndSaveUacQidLink(caze, 1)).thenReturn(uacQidLink);
    when(uacService.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class)))
        .thenReturn(new PayloadDTO());
    when(caseService.emitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    // When
    underTest.processSampleReceivedMessage(createCaseSample);

    // Then
    verify(caseService).saveCase(createCaseSample);
    verify(uacService).generateAndSaveUacQidLink(eq(caze), eq(1));
    verify(uacService).emitUacUpdatedEvent(uacQidLink, caze);
    verify(caseService).emitCaseCreatedEvent(caze);

    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq(CREATE_CASE_SAMPLE_RECEIVED),
            eq(EventType.SAMPLE_LOADED),
            any(EventDTO.class),
            eq(convertObjectToJson(createCaseSample)));
  }

  @Test
  public void testWelshQuestionnaire() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_QF2R1W");
    when(caseService.saveCase(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    UacQidLink secondUacQidLink = new UacQidLink();
    when(uacService.generateAndSaveUacQidLink(caze, 2)).thenReturn(uacQidLink);
    when(uacService.generateAndSaveUacQidLink(caze, 3)).thenReturn(secondUacQidLink);
    when(uacService.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class)))
        .thenReturn(new PayloadDTO());
    when(caseService.emitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    // When
    underTest.processSampleReceivedMessage(createCaseSample);

    // Then
    verify(caseService).saveCase(createCaseSample);
    verify(uacService, times(1)).generateAndSaveUacQidLink(eq(caze), eq(2));
    verify(uacService, times(2)).emitUacUpdatedEvent(uacQidLink, caze);
    verify(caseService).emitCaseCreatedEvent(caze);

    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq(CREATE_CASE_SAMPLE_RECEIVED),
            eq(EventType.SAMPLE_LOADED),
            any(EventDTO.class),
            eq(convertObjectToJson(createCaseSample)));
  }

  @Test
  public void testProcessPrintCaseSelected() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    when(caseService.findCase(anyInt())).thenReturn(Optional.of(caze));

    // When
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.PRINT_CASE_SELECTED);
    event.setChannel("Test channel");
    event.setDateTime(OffsetDateTime.now());
    event.setSource("Test source");
    event.setTransactionId(UUID.randomUUID());
    responseManagementEvent.setEvent(event);

    PrintCaseSelected printCaseSelected = new PrintCaseSelected();
    printCaseSelected.setActionRuleId("Test actionRuleId");
    printCaseSelected.setBatchId("Test batchId");
    printCaseSelected.setCaseRef(123);
    printCaseSelected.setPackCode("Test packCode");

    PayloadDTO payload = new PayloadDTO();
    payload.setPrintCaseSelected(printCaseSelected);
    responseManagementEvent.setPayload(payload);

    underTest.processPrintCaseSelected(responseManagementEvent);

    // Then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(caze),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq("Case selected by Action Rule for print Pack Code Test packCode"),
            eq(EventType.PRINT_CASE_SELECTED),
            eq(event),
            eq(
                "{\"printCaseSelected\":{\"caseRef\":123,\"packCode\":\"Test packCode\","
                    + "\"actionRuleId\":\"Test actionRuleId\",\"batchId\":\"Test batchId\"}}"));
  }
}
