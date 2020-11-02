package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@Service
public class FieldCaseUpdatedService {

  private final CaseRepository caseRepository;
  private final EventLogger eventLogger;
  private final CaseService caseService;
  private static final String CE_CASE_TYPE = "CE";
  private static final String FIELD_CASE_UPDATED_DESCRIPTION = "Field case update received";

  public FieldCaseUpdatedService(
      CaseRepository caseRepository, EventLogger eventLogger, CaseService caseService) {
    this.caseRepository = caseRepository;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public void processFieldCaseUpdatedEvent(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {

    CollectionCase fieldCaseUpdatedPayload =
        responseManagementEvent.getPayload().getCollectionCase();

    Case caze = caseService.getCaseAndLockIt(fieldCaseUpdatedPayload.getId());

    if (!caze.getCaseType().equals(CE_CASE_TYPE)) {
      throw new IllegalArgumentException(
          String.format(
              "Updating expected response count for %s failed as CaseType not CE",
              caze.getCaseId().toString()));
    }

    caze.setCeExpectedCapacity(fieldCaseUpdatedPayload.getCeExpectedCapacity());

    caseService.saveCaseAndEmitCaseUpdatedEvent(
        caze, buildMetadataForFieldCaseUpdated(caze, responseManagementEvent));

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        FIELD_CASE_UPDATED_DESCRIPTION,
        EventType.FIELD_CASE_UPDATED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(responseManagementEvent.getPayload()),
        messageTimestamp);
  }

  private Metadata buildMetadataForFieldCaseUpdated(
      Case caze, ResponseManagementEvent fieldCaseUpdatedPayload) {
    ActionInstructionType actionInstructionType = null;

    if ("CE".equals(caze.getCaseType()) && "U".equals(caze.getAddressLevel())) {
      if (fieldCaseUpdatedPayload.getPayload().getCollectionCase().getCeExpectedCapacity()
          <= caze.getCeActualResponses()) {
        actionInstructionType = ActionInstructionType.CANCEL;
      } else {
        actionInstructionType = ActionInstructionType.UPDATE;
      }
    }

    return buildMetadata(fieldCaseUpdatedPayload.getEvent().getType(), actionInstructionType);
  }
}
