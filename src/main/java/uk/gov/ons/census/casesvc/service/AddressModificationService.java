package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
      @Value("${approvedlistofgovernmentauthorisedpremises}") Set<String> estabTypes) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.estabTypes = estabTypes;
  }

  public void processMessage(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    AddressModification addressModification =
        responseManagementEvent.getPayload().getAddressModification();

    validate(addressModification);

    Case caze = caseService.getCaseByCaseId(addressModification.getCollectionCase().getId());

    if (addressModification.getNewAddress().getEstabType() != null
        && addressModification.getNewAddress().getEstabType().isPresent()) {
      caze.setEstabType(addressModification.getNewAddress().getEstabType().get());
    }

    if (addressModification.getNewAddress().getTownName() != null
        && addressModification.getNewAddress().getTownName().isPresent()) {
      caze.setTownName(addressModification.getNewAddress().getTownName().get());
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

  private void validate(AddressModification addressModification) {
    if (addressModification.getNewAddress().getTownName() != null
        && !addressModification.getNewAddress().getTownName().isPresent()) {
      throw new RuntimeException("Mandatory town name cannot be set to null");
    }

    if (addressModification.getNewAddress().getTownName() != null
        && addressModification.getNewAddress().getTownName().isPresent()
        && StringUtils.isEmpty(addressModification.getNewAddress().getTownName().get())) {
      throw new RuntimeException("Mandatory town name is empty");
    }

    if (addressModification.getNewAddress().getAddressLine1() != null
        && !addressModification.getNewAddress().getAddressLine1().isPresent()) {
      throw new RuntimeException("Mandatory address line 1 cannot be set to null");
    }

    if (addressModification.getNewAddress().getAddressLine1() != null
        && addressModification.getNewAddress().getAddressLine1().isPresent()
        && StringUtils.isEmpty(addressModification.getNewAddress().getAddressLine1().get())) {
      throw new RuntimeException("Mandatory address line 1 is empty");
    }

    if (addressModification.getNewAddress().getEstabType() != null
        && !addressModification.getNewAddress().getEstabType().isPresent()) {
      throw new RuntimeException("Mandatory estab type cannot be set to null");
    }

    if (addressModification.getNewAddress().getEstabType() != null
        && addressModification.getNewAddress().getEstabType().isPresent()
        && !estabTypes.contains(addressModification.getNewAddress().getEstabType().get())) {
      throw new RuntimeException("Estab Type not valid");
    }
  }
}
