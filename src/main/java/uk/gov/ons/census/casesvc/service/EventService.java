package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.createEventDTO;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper;

@Service
public class EventService {
  public static final String CREATE_CASE_SAMPLE_RECEIVED = "Create case sample received";

  private final CaseService caseService;
  private final UacService uacService;
  private final EventLogger eventLogger;

  public EventService(CaseService caseService, UacService uacService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  public void processSampleReceivedMessage(CreateCaseSample createCaseSample) {
    Case caze = caseService.saveCase(createCaseSample);
    int questionnaireType =
        QuestionnaireTypeHelper.calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = uacService.generateAndSaveUacQidLink(caze, questionnaireType);
    uacService.emitUacUpdatedEvent(uacQidLink, caze);
    caseService.emitCaseCreatedEvent(caze);

    eventLogger.logCaseEvent(
        caze,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        CREATE_CASE_SAMPLE_RECEIVED,
        EventType.SAMPLE_LOADED,
        createEventDTO(EventTypeDTO.SAMPLE_LOADED),
        convertObjectToJson(createCaseSample));

    if (QuestionnaireTypeHelper.isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = uacService.generateAndSaveUacQidLink(caze, 3);
      uacService.emitUacUpdatedEvent(uacQidLink, caze);
    }
  }

  public void processPrintCaseSelected(ResponseManagementEvent responseManagementEvent) {
    Optional<Case> cazeResult =
        caseService.findCase(
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
