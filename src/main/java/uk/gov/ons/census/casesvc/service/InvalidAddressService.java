package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
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
        convertObjectToJson(invalidAddressEvent.getPayload()));
  }
}
