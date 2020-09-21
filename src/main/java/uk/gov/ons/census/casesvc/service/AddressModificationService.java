package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.AddressModificationValidator.validateAddressModification;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressModification;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class AddressModificationService {
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final Set<String> estabTypes;

  public AddressModificationService(
      CaseService caseService,
      EventLogger eventLogger,
      @Value("${estabtypes}") Set<String> estabTypes) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.estabTypes = estabTypes;
  }

  public void processMessage(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    AddressModification addressModification =
        responseManagementEvent.getPayload().getAddressModification();

    Case caze = caseService.getCaseByCaseId(addressModification.getCollectionCase().getId());

    validateAddressModification(estabTypes, addressModification.getNewAddress());

    modifyCaseAddress(caze, addressModification);
    caseService.saveCaseAndEmitCaseUpdatedEvent(caze, null);

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        "Address modified",
        EventType.ADDRESS_MODIFIED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(addressModification),
        messageTimestamp);
  }

  private void modifyCaseAddress(Case caze, AddressModification addressModification) {
    // Note on deserialized Optionals from JSON:
    // An Optional.empty value implies we received a null value for that field in the JSON
    // A null pointer value implies that field was not present in the JSON we received at all

    if (addressModification.getNewAddress().getEstabType() != null
        && addressModification.getNewAddress().getEstabType().isPresent()) {
      caze.setEstabType(addressModification.getNewAddress().getEstabType().get());
    }

    if (addressModification.getNewAddress().getAddressLine1() != null
        && addressModification.getNewAddress().getAddressLine1().isPresent()) {
      caze.setAddressLine1(addressModification.getNewAddress().getAddressLine1().get());
    }

    if (addressModification.getNewAddress().getAddressLine2() != null) {
      if (addressModification.getNewAddress().getAddressLine2().isPresent()) {
        caze.setAddressLine2(addressModification.getNewAddress().getAddressLine2().get());
      } else {
        caze.setAddressLine2(null);
      }
    }

    if (addressModification.getNewAddress().getAddressLine3() != null) {
      if (addressModification.getNewAddress().getAddressLine3().isPresent()) {
        caze.setAddressLine3(addressModification.getNewAddress().getAddressLine3().get());
      } else {
        caze.setAddressLine3(null);
      }
    }

    if (addressModification.getNewAddress().getOrganisationName() != null) {
      if (addressModification.getNewAddress().getOrganisationName().isPresent()) {
        caze.setOrganisationName(addressModification.getNewAddress().getOrganisationName().get());
      } else {
        caze.setOrganisationName(null);
      }
    }
  }
}
