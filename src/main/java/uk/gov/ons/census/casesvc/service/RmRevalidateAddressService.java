package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FieldworkHelper.shouldSendRevalidateAddressCaseToField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmRevalidateAddress;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class RmRevalidateAddressService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  private static final String EVENT_DESCRIPTION = "Case address revalidate";

  public RmRevalidateAddressService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(ResponseManagementEvent rme, OffsetDateTime messageTimestamp) {
    RmRevalidateAddress rmRevalidateAddress = rme.getPayload().getRmRevalidateAddress();

    Case revalidateAddressCase = caseService.getCaseByCaseId(rmRevalidateAddress.getCaseId());

    revalidateAddressCase.setAddressInvalid(false);

    Metadata eventMetadata = null;
    if (shouldSendRevalidateAddressCaseToField(
        revalidateAddressCase, rme.getEvent().getChannel())) {
      eventMetadata = buildMetadata(rme.getEvent().getType(), ActionInstructionType.UPDATE);
    }

    caseService.saveCaseAndEmitCaseUpdatedEvent(revalidateAddressCase, eventMetadata);

    eventLogger.logCaseEvent(
        revalidateAddressCase,
        rme.getEvent().getDateTime(),
        EVENT_DESCRIPTION,
        EventType.RM_REVALIDATE_ADDRESS,
        rme.getEvent(),
        convertObjectToJson(rmRevalidateAddress),
        messageTimestamp);
  }
}
