package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
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
    // This check shouldn't be needed, but because several different shape messages are getting
    // published to the same topic according to the event dictionary, let's keep this check in
    // place so that we know we need to do something about it
    if (invalidAddressEvent.getEvent().getType() != EventTypeDTO.ADDRESS_NOT_VALID) {
      throw new RuntimeException(
          String.format("Event Type '%s' is invalid!", invalidAddressEvent.getEvent().getType()));
    }

    Case caze =
        caseService.getCaseByCaseId(
            UUID.fromString(
                invalidAddressEvent.getPayload().getInvalidAddress().getCollectionCase().getId()));
    caze.setAddressInvalid(true);
    caseService.saveAndEmitCaseUpdatedEvent(caze);

    eventLogger.logCaseEvent(
        caze,
        invalidAddressEvent.getEvent().getDateTime(),
        "Invalid address",
        EventType.ADDRESS_NOT_VALID,
        invalidAddressEvent.getEvent(),
        convertObjectToJson(invalidAddressEvent.getPayload().getInvalidAddress()));
  }
}
