package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.EventHelper.isEventChannelField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
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

    Case caze = caseService.getCaseByCaseId(invalidAddress.getCollectionCase().getId());

    invalidateCase(invalidAddressEvent, messageTimestamp, caze, invalidAddress);
  }

  public void invalidateCase(
      ResponseManagementEvent rmEvent, OffsetDateTime messageTimestamp, Case caze, Object payload) {

    caze.setAddressInvalid(true);

    caseService.saveCaseAndEmitCaseUpdatedEvent(caze, buildMetadataForInvalidAddress(rmEvent));

    eventLogger.logCaseEvent(
        caze,
        rmEvent.getEvent().getDateTime(),
        "Invalid address",
        EventType.ADDRESS_NOT_VALID,
        rmEvent.getEvent(),
        convertObjectToJson(payload),
        messageTimestamp);
  }

  private Metadata buildMetadataForInvalidAddress(ResponseManagementEvent event) {
    if (!isEventChannelField(event)) {
      return buildMetadata(event.getEvent().getType(), ActionInstructionType.CANCEL);
    }
    return buildMetadata(event.getEvent().getType(), null);
  }
}
