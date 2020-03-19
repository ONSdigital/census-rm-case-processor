package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.isEventChannelField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Component
public class InvalidAddressService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public InvalidAddressService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(
      ResponseManagementEvent invalidAddressEvent, OffsetDateTime messageTimestamp) {
    InvalidAddress invalidAddress = invalidAddressEvent.getPayload().getInvalidAddress();

    Case caze =
        caseService.getCaseByCaseId(UUID.fromString(invalidAddress.getCollectionCase().getId()));

    caze.setAddressInvalid(true);

    caseService.saveCaseAndEmitCaseUpdatedEvent(
        caze, buildMetadataForInvalidAddress(invalidAddressEvent));

    eventLogger.logCaseEvent(
        caze,
        invalidAddressEvent.getEvent().getDateTime(),
        "Invalid address",
        EventType.ADDRESS_NOT_VALID,
        invalidAddressEvent.getEvent(),
        convertObjectToJson(invalidAddress),
        messageTimestamp);
  }

  private Metadata buildMetadataForInvalidAddress(ResponseManagementEvent event) {
    if (!isEventChannelField(event)) {
      return buildMetadata(event.getEvent().getType(), ActionInstructionType.CLOSE);
    }
    return buildMetadata(event.getEvent().getType(), null);
  }
}
