package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.PrintCaseSelected;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;

@RunWith(MockitoJUnitRunner.class)
public class EventProcessorTest {

  @Mock CaseProcessor caseProcessor;

  @Mock UacProcessor uacProcessor;

  @Mock EventRepository eventRepository;

  @Mock EventLogger eventLogger;

  @InjectMocks EventProcessor underTest;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_LF2R3BE");
    when(caseProcessor.saveCase(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    when(uacProcessor.generateAndSaveUacQidLink(caze, 1)).thenReturn(uacQidLink);
    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class)))
        .thenReturn(new PayloadDTO());
    when(caseProcessor.emitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    // When
    underTest.processSampleReceivedMessage(createCaseSample);

    // Then
    verify(caseProcessor).saveCase(createCaseSample);
    verify(uacProcessor).generateAndSaveUacQidLink(eq(caze), eq(1));
    verify(uacProcessor).emitUacUpdatedEvent(uacQidLink, caze);
    verify(caseProcessor).emitCaseCreatedEvent(caze);
    verify(eventLogger, times(2))
        .logEvent(eq(uacQidLink), any(String.class), any(EventType.class), any(PayloadDTO.class));
  }

  @Test
  public void testWelshQuestionnaire() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    Case caze = new Case();
    caze.setTreatmentCode("HH_QF2R1W");
    when(caseProcessor.saveCase(createCaseSample)).thenReturn(caze);
    UacQidLink uacQidLink = new UacQidLink();
    UacQidLink secondUacQidLink = new UacQidLink();
    when(uacProcessor.generateAndSaveUacQidLink(caze, 2)).thenReturn(uacQidLink);
    when(uacProcessor.generateAndSaveUacQidLink(caze, 3)).thenReturn(secondUacQidLink);
    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class)))
        .thenReturn(new PayloadDTO());
    when(caseProcessor.emitCaseCreatedEvent(any(Case.class))).thenReturn(new PayloadDTO());

    // When
    underTest.processSampleReceivedMessage(createCaseSample);

    // Then
    verify(caseProcessor).saveCase(createCaseSample);
    verify(uacProcessor, times(1)).generateAndSaveUacQidLink(eq(caze), eq(2));
    verify(uacProcessor, times(2)).emitUacUpdatedEvent(uacQidLink, caze);
    verify(caseProcessor).emitCaseCreatedEvent(caze);
    verify(eventLogger, times(3))
        .logEvent(eq(uacQidLink), any(String.class), any(EventType.class), any(PayloadDTO.class));
  }

  @Test
  public void testProcessPrintCaseSelected() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    when(caseProcessor.findCase(anyInt())).thenReturn(Optional.of(caze));

    // When
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO event = new EventDTO();
    event.setType(EventType.PRINT_CASE_SELECTED);
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
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    Event actualEvent = eventArgumentCaptor.getValue();

    assertThat(caze).isEqualTo(actualEvent.getCaze());
    assertThat("Test channel").isEqualTo(actualEvent.getEventChannel());
    assertThat("Test source").isEqualTo(actualEvent.getEventSource());
    assertThat(event.getTransactionId()).isEqualTo(actualEvent.getEventTransactionId());
    assertThat(event.getType().toString()).isEqualTo(actualEvent.getEventType().toString());
    assertThat(event.getDateTime()).isEqualTo(actualEvent.getEventDate());
    assertThat(
            "{\"printCaseSelected\":{\"caseRef\":123,\"packCode\":\"Test packCode\",\"actionRuleId\":\"Test actionRuleId\",\"batchId\":\"Test batchId\"}}")
        .isEqualTo(actualEvent.getEventPayload());
    assertThat(actualEvent.getRmEventProcessed()).isNotNull();
    assertThat("Case selected by Action Rule for print Pack Code Test packCode")
        .isEqualTo(actualEvent.getEventDescription());
  }
}
