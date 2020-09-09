package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CCSPropertyDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@Service
public class CCSPropertyListedService {
  private static final String CCS_ADDRESS_LISTED = "CCS Address Listed";

  private final UacService uacService;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  public CCSPropertyListedService(
      UacService uacService,
      EventLogger eventLogger,
      CaseService caseService,
      UacQidLinkRepository uacQidLinkRepository) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processCCSPropertyListed(
      ResponseManagementEvent ccsPropertyListedEvent, OffsetDateTime messageTimestamp) {
    CCSPropertyDTO ccsProperty = ccsPropertyListedEvent.getPayload().getCcsProperty();

    Case caze =
        caseService.createCCSCase(
            ccsProperty.getCollectionCase().getId(), ccsProperty.getSampleUnit());

    uacService.createUacQidLinkedToCCSCase(caze, ccsPropertyListedEvent.getEvent());

    sendCCSCaseToFieldIfInterviewRequired(caze, ccsProperty.isInterviewRequired());

    eventLogger.logCaseEvent(
        caze,
        ccsPropertyListedEvent.getEvent().getDateTime(),
        CCS_ADDRESS_LISTED,
        EventType.CCS_ADDRESS_LISTED,
        ccsPropertyListedEvent.getEvent(),
        convertObjectToJson(ccsProperty),
        messageTimestamp);
  }

  private void sendCCSCaseToFieldIfInterviewRequired(Case caze, boolean interviewRequired) {
    if (interviewRequired) {
      caseService.saveCaseAndEmitCaseCreatedEvent(
          caze, buildMetadata(EventTypeDTO.CCS_ADDRESS_LISTED, ActionInstructionType.CREATE));
    }
  }
}
