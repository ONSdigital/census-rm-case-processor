package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.EventType.PRINT_CASE_SELECTED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

@Component
public class EventProcessor {
  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case created";
  private static final String UAC_QID_LINKED_EVENT_DESCRIPTION = "UAC QID linked";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final CaseProcessor caseProcessor;
  private final UacProcessor uacProcessor;
  private final EventRepository eventRepository;

  public EventProcessor(
      CaseProcessor caseProcessor, UacProcessor uacProcessor, EventRepository eventRepository) {
    this.caseProcessor = caseProcessor;
    this.uacProcessor = uacProcessor;
    this.eventRepository = eventRepository;
  }

  public void processSampleReceivedMessage(CreateCaseSample createCaseSample)
      throws JsonProcessingException {
    Case caze = caseProcessor.saveCase(createCaseSample);
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacProcessor.saveUacQidLink(caze, questionnaireType);
    PayloadDTO uacPayloadDTO = uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
    PayloadDTO casePayloadDTO = caseProcessor.emitCaseCreatedEvent(caze);
    uacProcessor.logEvent(
        uacQidLink, CASE_CREATED_EVENT_DESCRIPTION, EventType.CASE_CREATED, casePayloadDTO);
    uacProcessor.logEvent(
        uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION, EventType.UAC_UPDATED, uacPayloadDTO);

    if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacProcessor.saveUacQidLink(caze, 3);
      uacPayloadDTO = uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
      uacProcessor.logEvent(
          uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION, EventType.UAC_UPDATED, uacPayloadDTO);
    }
  }

  public void processPrintCaseSelected(ResponseManagementEvent responseManagementEvent) {
    Optional<Case> cazeResult =
        caseProcessor.findCase(
            responseManagementEvent.getPayload().getPrintCaseSelected().getCaseRef());

    if (cazeResult.isEmpty()) {
      throw new RuntimeException(); // This case should definitely exist
    }

    Case caze = cazeResult.get();
    Event event = new Event();
    event.setId(UUID.randomUUID());
    event.setCaseId(caze.getCaseId());
    event.setCaze(caze);
    event.setEventChannel(responseManagementEvent.getEvent().getChannel());
    event.setEventDate(responseManagementEvent.getEvent().getDateTime());
    event.setEventSource(responseManagementEvent.getEvent().getSource());
    event.setEventTransactionId(
        UUID.fromString(responseManagementEvent.getEvent().getTransactionId()));
    event.setEventType(PRINT_CASE_SELECTED);
    event.setRmEventProcessed(OffsetDateTime.now());
    event.setEventDescription(
        String.format(
            "Case selected by Action Rule for print Pack Code %s",
            responseManagementEvent.getPayload().getPrintCaseSelected().getPackCode()));
    try {
      event.setEventPayload(objectMapper.writeValueAsString(responseManagementEvent.getPayload()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e); // Very unlikely that we would be unable to serialize JSON
    }

    eventRepository.save(event);
  }
}
