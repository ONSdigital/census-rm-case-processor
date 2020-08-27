package uk.gov.ons.census.casesvc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmCaseUpdated;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseMetadata;
import uk.gov.ons.census.casesvc.model.entity.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

import static uk.gov.ons.census.casesvc.utility.FieldworkHelper.shouldSendCaseToField;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@Service
public class RmCaseUpdatedService {
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final Set<String> estabTypes;

  private static final String EVENT_DESCRIPTION = "Case details updated";

  public RmCaseUpdatedService(
      CaseService caseService,
      EventLogger eventLogger,
      @Value("${estabtypes}") Set<String> estabTypes) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.estabTypes = estabTypes;
  }

  public void processMessage(
      Message<ResponseManagementEvent> message, OffsetDateTime messageTimestamp) {
    ResponseManagementEvent rme = message.getPayload();
    RmCaseUpdated rmCaseUpdated = rme.getPayload().getRmCaseUpdated();
    Case updatedCase = caseService.getCaseByCaseId(rmCaseUpdated.getCaseId());

    validateRmCaseUpdated(rmCaseUpdated);

    updateCase(updatedCase, rmCaseUpdated);

    // Check the case now has all mandatory fields
    validateCase(updatedCase);

    // Only remove the skeleton flag once the case has passed validation
    updatedCase.setSkeleton(false);

    Metadata eventMetadata = null;
    if (shouldSendCaseToField(updatedCase, rme.getEvent().getChannel())) {
      eventMetadata = new Metadata();
      eventMetadata.setCauseEventType(rme.getEvent().getType());
      eventMetadata.setFieldDecision(ActionInstructionType.CREATE);
    }

    caseService.saveCaseAndEmitCaseUpdatedEvent(updatedCase, eventMetadata);
    eventLogger.logCaseEvent(
        updatedCase,
        rme.getEvent().getDateTime(),
        EVENT_DESCRIPTION,
        EventType.RM_CASE_UPDATED,
        rme.getEvent(),
        convertObjectToJson(rmCaseUpdated),
        messageTimestamp);
  }

  private void updateCase(Case caseToUpdate, RmCaseUpdated rmCaseUpdated) {
    // Mandatory update values
    caseToUpdate.setTreatmentCode(rmCaseUpdated.getTreatmentCode());
    caseToUpdate.setEstabType(rmCaseUpdated.getEstabType());
    caseToUpdate.setOa(rmCaseUpdated.getOa());
    caseToUpdate.setLsoa(rmCaseUpdated.getLsoa());
    caseToUpdate.setMsoa(rmCaseUpdated.getMsoa());
    caseToUpdate.setLad(rmCaseUpdated.getLad());
    caseToUpdate.setFieldCoordinatorId(rmCaseUpdated.getFieldCoordinatorId());
    caseToUpdate.setFieldOfficerId(rmCaseUpdated.getFieldOfficerId());
    caseToUpdate.setLatitude(rmCaseUpdated.getLatitude());
    caseToUpdate.setLongitude(rmCaseUpdated.getLongitude());

    // Optional update values
    rmCaseUpdated.getAddressLine1().ifPresent(caseToUpdate::setAddressLine1);
    rmCaseUpdated.getAddressLine2().ifPresent(caseToUpdate::setAddressLine2);
    rmCaseUpdated.getAddressLine3().ifPresent(caseToUpdate::setAddressLine3);
    rmCaseUpdated.getTownName().ifPresent(caseToUpdate::setTownName);
    rmCaseUpdated.getPostcode().ifPresent(caseToUpdate::setPostcode);
    rmCaseUpdated.getCeExpectedCapacity().ifPresent(caseToUpdate::setCeExpectedCapacity);
    rmCaseUpdated.getUprn().ifPresent(caseToUpdate::setUprn);
    rmCaseUpdated.getEstabUprn().ifPresent(caseToUpdate::setEstabUprn);
    rmCaseUpdated.getAbpCode().ifPresent(caseToUpdate::setAbpCode);
    rmCaseUpdated.getOrganisationName().ifPresent(caseToUpdate::setOrganisationName);
    rmCaseUpdated.getHtcWillingness().ifPresent(caseToUpdate::setHtcWillingness);
    rmCaseUpdated.getHtcDigital().ifPresent(caseToUpdate::setHtcDigital);
    rmCaseUpdated.getPrintBatch().ifPresent(caseToUpdate::setPrintBatch);

    if (rmCaseUpdated.getSecureEstablishment().isPresent()) {
      CaseMetadata caseMetadata;
      if (caseToUpdate.getMetadata() != null) {
        caseMetadata = caseToUpdate.getMetadata();
      } else {
        caseMetadata = new CaseMetadata();
        caseToUpdate.setMetadata(caseMetadata);
      }
      caseMetadata.setSecureEstablishment(rmCaseUpdated.getSecureEstablishment().get());
    }

    caseToUpdate.setHandDelivery(
        caseService.isTreatmentCodeDirectDelivered(rmCaseUpdated.getTreatmentCode()));
  }

  private void validateRmCaseUpdated(RmCaseUpdated rmCaseUpdated) {
    if (StringUtils.isEmpty(rmCaseUpdated.getTreatmentCode())
        || StringUtils.isEmpty(rmCaseUpdated.getOa())
        || StringUtils.isEmpty(rmCaseUpdated.getLsoa())
        || StringUtils.isEmpty(rmCaseUpdated.getMsoa())
        || StringUtils.isEmpty(rmCaseUpdated.getLad())
        || StringUtils.isEmpty(rmCaseUpdated.getFieldCoordinatorId())
        || StringUtils.isEmpty(rmCaseUpdated.getFieldOfficerId())
        || StringUtils.isEmpty(rmCaseUpdated.getLatitude())
        || StringUtils.isEmpty(rmCaseUpdated.getLongitude())) {
      throw new RuntimeException("Rm Case Updated message missing mandatory field(s)");
    }

    if (!estabTypes.contains(rmCaseUpdated.getEstabType())) {
      throw new RuntimeException("Estab Type not valid");
    }

    new BigDecimal(rmCaseUpdated.getLatitude());
    new BigDecimal(rmCaseUpdated.getLongitude());
  }

  private void validateCase(Case caze) {
    if (StringUtils.isEmpty(caze.getUprn())
        || StringUtils.isEmpty(caze.getEstabUprn())
        || StringUtils.isEmpty(caze.getEstabType())
        || StringUtils.isEmpty(caze.getAbpCode())
        || StringUtils.isEmpty(caze.getAddressLine1())
        || StringUtils.isEmpty(caze.getTownName())
        || StringUtils.isEmpty(caze.getPostcode())
        || StringUtils.isEmpty(caze.getLatitude())
        || StringUtils.isEmpty(caze.getLongitude())
        || StringUtils.isEmpty(caze.getOa())
        || StringUtils.isEmpty(caze.getMsoa())
        || StringUtils.isEmpty(caze.getLsoa())
        || StringUtils.isEmpty(caze.getLad())
        || StringUtils.isEmpty(caze.getRegion())
        || StringUtils.isEmpty(caze.getAddressLevel())
        || StringUtils.isEmpty(caze.getAddressType())
        || StringUtils.isEmpty(caze.getCaseType())
        || StringUtils.isEmpty(caze.getHtcWillingness())
        || StringUtils.isEmpty(caze.getHtcDigital())
        || StringUtils.isEmpty(caze.getFieldCoordinatorId())
        || StringUtils.isEmpty(caze.getFieldOfficerId())) {
      throw new RuntimeException("Case missing mandatory fields after RM Case Updated");
    }
  }
}
