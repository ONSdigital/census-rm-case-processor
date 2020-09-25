package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FieldworkHelper.shouldSendCaseToField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmUnInvalidateAddress;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RmUnInvalidateAddressService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  private static final String EVENT_DESCRIPTION = "Case address un-invalidate";

  public RmUnInvalidateAddressService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(ResponseManagementEvent rme, OffsetDateTime messageTimestamp) {
    RmUnInvalidateAddress rmUnInvalidateAddress = rme.getPayload().getRmUnInvalidateAddress();

    Case unInvalidateAddressCase = caseService.getCaseByCaseId(rmUnInvalidateAddress.getCaseId());

    unInvalidateAddressCase.setAddressInvalid(false);

    Metadata eventMetadata = null;
    if (shouldSendCaseToField(unInvalidateAddressCase)) {
      eventMetadata = buildMetadata(rme.getEvent().getType(), ActionInstructionType.UPDATE);
    }

    caseService.saveCaseAndEmitCaseUpdatedEvent(unInvalidateAddressCase, eventMetadata);

    eventLogger.logCaseEvent(
        unInvalidateAddressCase,
        rme.getEvent().getDateTime(),
        EVENT_DESCRIPTION,
        EventType.RM_UNINVALIDATE_ADDRESS,
        rme.getEvent(),
        convertObjectToJson(rmUnInvalidateAddress),
        messageTimestamp);
  }
}
