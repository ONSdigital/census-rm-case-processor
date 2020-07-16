package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.entity.RefusalType.EXTRAORDINARY_REFUSAL;
import static uk.gov.ons.census.casesvc.utility.EventHelper.isEventChannelField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RefusalService {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String HARD_REFUSAL_FOR_ALREADY_EXTRAORDINARY_REFUSED_CASE =
      "Hard Refusal Received for case already marked Extraordinary refused";

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RefusalService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(
      ResponseManagementEvent refusalEvent, OffsetDateTime messageTimestamp) {
    RefusalDTO refusalDto = refusalEvent.getPayload().getRefusal();

    Case refusedCase = caseService.getCaseByCaseId(refusalDto.getCollectionCase().getId());

    if (justLogRefusalIfConditionsMet(refusedCase, refusalEvent, messageTimestamp, refusalDto)) {
      return;
    }

    refusedCase.setRefusalReceived(
        uk.gov.ons.census.casesvc.model.entity.RefusalType.valueOf(refusalDto.getType().name()));
    caseService.saveCaseAndEmitCaseUpdatedEvent(refusedCase, buildMetadataForRefusal(refusalEvent));

    logRefusalCaseEvent(refusalEvent, refusedCase, messageTimestamp, REFUSAL_RECEIVED);
  }

  private boolean justLogRefusalIfConditionsMet(
      Case refusedCase,
      ResponseManagementEvent refusalEvent,
      OffsetDateTime messageTimestamp,
      RefusalDTO refusalDto) {

    if (refusalDto.getType() == RefusalTypeDTO.HARD_REFUSAL
        && refusedCase.getRefusalReceived() != null
        && refusedCase.getRefusalReceived() == EXTRAORDINARY_REFUSAL) {
      logRefusalCaseEvent(
          refusalEvent,
          refusedCase,
          messageTimestamp,
          HARD_REFUSAL_FOR_ALREADY_EXTRAORDINARY_REFUSED_CASE);
      return true;
    }

    return false;
  }

  private Metadata buildMetadataForRefusal(ResponseManagementEvent event) {
    if (!isEventChannelField(event)) {
      return buildMetadata(event.getEvent().getType(), ActionInstructionType.CANCEL);
    }
    return buildMetadata(event.getEvent().getType(), null);
  }

  private void logRefusalCaseEvent(
      ResponseManagementEvent refusalEvent,
      Case refusedCase,
      OffsetDateTime messageTimestamp,
      String description) {
    eventLogger.logCaseEvent(
        refusedCase,
        refusalEvent.getEvent().getDateTime(),
        description,
        EventType.REFUSAL_RECEIVED,
        refusalEvent.getEvent(),
        convertObjectToJson(refusalEvent.getPayload().getRefusal()),
        messageTimestamp);
  }
}
