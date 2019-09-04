package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
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

  public void processMessage(ResponseManagementEvent invalidAddressEvent) {
    InvalidAddress invalidAddress = invalidAddressEvent.getPayload().getInvalidAddress();
    EventTypeDTO eventType = invalidAddressEvent.getEvent().getType();

    Case caze =
        caseService.getCaseByCaseId(UUID.fromString(invalidAddress.getCollectionCase().getId()));

    // Log unexpected event type and ack message
    if (eventType != EventTypeDTO.ADDRESS_NOT_VALID) {
      eventLogger.logCaseEvent(
          caze,
          invalidAddressEvent.getEvent().getDateTime(),
          String.format("Unexpected event type '%s'", eventType),
          EventType.UNEXPECTED_EVENT_TYPE,
          invalidAddressEvent.getEvent(),
          convertObjectToJson(invalidAddress));
      return;
    }

    caze.setAddressInvalid(true);
    caseService.saveAndEmitCaseUpdatedEvent(caze);

    eventLogger.logCaseEvent(
        caze,
        invalidAddressEvent.getEvent().getDateTime(),
        "Invalid address",
        EventType.ADDRESS_NOT_VALID,
        invalidAddressEvent.getEvent(),
        convertObjectToJson(invalidAddress));
  }
}
