package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.isEventChannelField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RefusalService {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String ESTAB_INDIVIDUAL_REFUSAL_RECEIVED =
      "Refusal received for individual on Estab";
  private static final String HARD_REFUSAL_FOR_ALREADY_EXTRAORDINARY_REFUSED_CASE =
      "Hard Refusal Received for case already marked Extraordinary refused";
  private static final String ESTAB_ADDRESS_LEVEL = "E";

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RefusalService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(
      ResponseManagementEvent refusalEvent, OffsetDateTime messageTimestamp) {
    RefusalDTO refusalDto = refusalEvent.getPayload().getRefusal();

    if ((refusalDto.getType() != RefusalType.EXTRAORDINARY_REFUSAL)
        && (refusalDto.getType() != RefusalType.HARD_REFUSAL)) {
      throw new RuntimeException("Unexpected refusal type " + refusalDto.getType());
    }

    Case refusedCase =
        caseService.getCaseByCaseId(UUID.fromString(refusalDto.getCollectionCase().getId()));

    if (justLogRefusalIfConditionsMet(refusedCase, refusalEvent, messageTimestamp, refusalDto)) {
      return;
    }

    refusedCase.setRefusalReceived(refusalDto.getType());
    caseService.saveCaseAndEmitCaseUpdatedEvent(refusedCase, buildMetadataForRefusal(refusalEvent));

    logRefusalCaseEvent(refusalEvent, refusedCase, messageTimestamp, REFUSAL_RECEIVED);
  }

  private boolean justLogRefusalIfConditionsMet(
      Case refusedCase,
      ResponseManagementEvent refusalEvent,
      OffsetDateTime messageTimestamp,
      RefusalDTO refusalDto) {

    if (isEstabLevelAddressAndChannelIsNotField(refusedCase.getAddressLevel(), refusalEvent)) {
      logRefusalCaseEvent(
          refusalEvent, refusedCase, messageTimestamp, ESTAB_INDIVIDUAL_REFUSAL_RECEIVED);
      return true;
    }

    if (refusalDto.getType() == RefusalType.HARD_REFUSAL
        && refusedCase.getRefusalReceived() != null
        && refusedCase.getRefusalReceived().equals(RefusalType.EXTRAORDINARY_REFUSAL)) {
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

  private boolean isEstabLevelAddressAndChannelIsNotField(
      String addressLevel, ResponseManagementEvent event) {
    return addressLevel.equals(ESTAB_ADDRESS_LEVEL) && !isEventChannelField(event);
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
