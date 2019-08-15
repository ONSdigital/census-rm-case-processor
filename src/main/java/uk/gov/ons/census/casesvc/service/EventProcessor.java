package uk.gov.ons.census.casesvc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.ons.census.casesvc.model.entity.EventType.PRINT_CASE_SELECTED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@Component
public class EventProcessor {
  public static final String CREATE_CASE_SAMPLE_RECEIVED = "Create case sample received";
  public static final String CREATE_CASE_SOURCE = "CASE_SERVICE";
  public static final String CREATE_CASE_CHANNEL = "RM";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final CaseProcessor caseProcessor;
  private final UacProcessor uacProcessor;
  private final EventRepository eventRepository;
  private final EventLogger eventLogger;

  public EventProcessor(
      CaseProcessor caseProcessor,
      UacProcessor uacProcessor,
      EventRepository eventRepository,
      EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.uacProcessor = uacProcessor;
    this.eventRepository = eventRepository;
    this.eventLogger = eventLogger;
  }

  public void processSampleReceivedMessage(CreateCaseSample createCaseSample) {
    Case caze = caseProcessor.saveCase(createCaseSample);
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacProcessor.saveUacQidLink(caze, questionnaireType);
    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
    caseProcessor.emitCaseCreatedEvent(caze);

    eventLogger.logEvent(
        uacQidLink,
        CREATE_CASE_SAMPLE_RECEIVED,
        EventType.SAMPLE_LOADED,
        convertObjectToJson(createCaseSample));

    if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacProcessor.saveUacQidLink(caze, 3);
      uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
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
