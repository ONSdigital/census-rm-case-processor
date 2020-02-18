package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.isEventChannelField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RefusalService {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String ESTAB_ADDRESS_LEVEL = "E";

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public RefusalService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processRefusal(
      ResponseManagementEvent refusalEvent, OffsetDateTime messageTimestamp) {
    RefusalDTO refusal = refusalEvent.getPayload().getRefusal();
    Case caze = caseService.getCaseByCaseId(UUID.fromString(refusal.getCollectionCase().getId()));

    if (isEstabLevelAddressAndChannelIsNotField(caze.getAddressLevel(), refusalEvent)) {
      throw new IllegalArgumentException(
          String.format(
              "Refusal received for Estab level case ID '%s' from channel '%s'. "
                  + "This type of refusal should ONLY come from Field",
              caze.getCaseId(), refusalEvent.getEvent().getChannel()));
    }

    caze.setRefusalReceived(true);
    caseService.saveCaseAndEmitCaseUpdatedEvent(caze, buildMetadataForRefusal(refusalEvent));

    eventLogger.logCaseEvent(
        caze,
        refusalEvent.getEvent().getDateTime(),
        REFUSAL_RECEIVED,
        EventType.REFUSAL_RECEIVED,
        refusalEvent.getEvent(),
        convertObjectToJson(refusalEvent.getPayload().getRefusal()),
        messageTimestamp);
  }

  private Metadata buildMetadataForRefusal(ResponseManagementEvent event) {
    if (!isEventChannelField(event)) {
      return buildMetadata(event.getEvent().getType(), ActionInstructionType.CLOSE);
    }
    return buildMetadata(event.getEvent().getType(), null);
  }

  private boolean isEstabLevelAddressAndChannelIsNotField(
      String addressLevel, ResponseManagementEvent event) {
    return addressLevel.equals(ESTAB_ADDRESS_LEVEL) && !isEventChannelField(event);
  }
}
