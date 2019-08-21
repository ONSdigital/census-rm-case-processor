package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.createEventDTO;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

@Component
public class EventProcessor {
  public static final String CREATE_CASE_SAMPLE_RECEIVED = "Create case sample received";

  private final CaseProcessor caseProcessor;
  private final UacProcessor uacProcessor;
  private final EventLogger eventLogger;

  public EventProcessor(
      CaseProcessor caseProcessor, UacProcessor uacProcessor, EventLogger eventLogger) {
    this.caseProcessor = caseProcessor;
    this.uacProcessor = uacProcessor;
    this.eventLogger = eventLogger;
  }

  public void processSampleReceivedMessage(CreateCaseSample createCaseSample) {
    Case caze = caseProcessor.saveCase(createCaseSample);
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacProcessor.generateAndSaveUacQidLink(caze, questionnaireType);
    uacProcessor.emitUacUpdatedEvent(uacQidLink, caze);
    caseProcessor.emitCaseCreatedEvent(caze);

    eventLogger.logUacQidEvent(
        uacQidLink,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        CREATE_CASE_SAMPLE_RECEIVED,
        EventType.SAMPLE_LOADED,
        createEventDTO(EventTypeDTO.SAMPLE_LOADED),
        convertObjectToJson(createCaseSample));

    if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacProcessor.generateAndSaveUacQidLink(caze, 3);
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
    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        OffsetDateTime.now(),
        String.format(
            "Case selected by Action Rule for print Pack Code %s",
            responseManagementEvent.getPayload().getPrintCaseSelected().getPackCode()),
        EventType.PRINT_CASE_SELECTED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(responseManagementEvent.getPayload()));
  }
}
