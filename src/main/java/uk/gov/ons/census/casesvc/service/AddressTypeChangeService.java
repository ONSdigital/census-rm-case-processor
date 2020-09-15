package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressTypeChange;
import uk.gov.ons.census.casesvc.model.dto.AddressTypeChangeAddress;
import uk.gov.ons.census.casesvc.model.dto.AddressTypeChangeDetails;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class AddressTypeChangeService {
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final InvalidAddressService invalidAddressService;
  private final Set<String> estabTypes;

  public AddressTypeChangeService(
      CaseService caseService,
      EventLogger eventLogger,
      InvalidAddressService invalidAddressService,
      @Value("${estabtypes}") Set<String> estabTypes) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.invalidAddressService = invalidAddressService;
    this.estabTypes = estabTypes;
  }

  public void processMessage(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    AddressTypeChange addressTypeChange =
        responseManagementEvent.getPayload().getAddressTypeChange();

    if (addressTypeChange.getCollectionCase().getId().equals(addressTypeChange.getNewCaseId())) {
      throw new RuntimeException("Old Case ID cannot equal New Case ID");
    }

    Case oldCase = caseService.getCaseByCaseId(addressTypeChange.getCollectionCase().getId());

    if (oldCase.getCaseType().equals("HI")) {
      throw new RuntimeException("Cannot change case of type HI");
    }
    validateModification(addressTypeChange.getCollectionCase().getAddress());

    invalidateOldCase(responseManagementEvent, messageTimestamp, addressTypeChange, oldCase);

    createNewCase(responseManagementEvent, messageTimestamp, addressTypeChange, oldCase);
  }

  private void invalidateOldCase(
      ResponseManagementEvent responseManagementEvent,
      OffsetDateTime messageTimestamp,
      AddressTypeChange addressTypeChange,
      Case oldCase) {

    invalidAddressService.invalidateCase(
        responseManagementEvent, messageTimestamp, oldCase, addressTypeChange);

    eventLogger.logCaseEvent(
        oldCase,
        responseManagementEvent.getEvent().getDateTime(),
        "Address type changed",
        EventType.ADDRESS_TYPE_CHANGED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(addressTypeChange),
        messageTimestamp);
  }

  private void createNewCase(
      ResponseManagementEvent responseManagementEvent,
      OffsetDateTime messageTimestamp,
      AddressTypeChange addressTypeChange,
      Case oldCase) {
    Case newCase = new Case();
    newCase.setSkeleton(true);
    newCase.setCaseId(addressTypeChange.getNewCaseId());
    newCase.setCaseType(addressTypeChange.getCollectionCase().getAddress().getAddressType());
    newCase.setAddressType(addressTypeChange.getCollectionCase().getAddress().getAddressType());
    newCase.setAddressLevel(deriveAddressLevel(oldCase.getCaseType(), newCase.getCaseType()));
    newCase.setRegion(oldCase.getRegion());
    newCase.setCollectionExerciseId(oldCase.getCollectionExerciseId());
    newCase.setActionPlanId(oldCase.getActionPlanId());
    newCase.setSurvey(oldCase.getSurvey());
    newCase.setUprn(oldCase.getUprn());
    newCase.setTownName(oldCase.getTownName());
    newCase.setPostcode(oldCase.getPostcode());
    newCase.setLatitude(oldCase.getLatitude());
    newCase.setLongitude(oldCase.getLongitude());
    newCase.setOa(oldCase.getOa());
    newCase.setLsoa(oldCase.getLsoa());
    newCase.setMsoa(oldCase.getMsoa());
    newCase.setLad(oldCase.getLad());
    newCase.setHtcWillingness(oldCase.getHtcWillingness());
    newCase.setHtcDigital(oldCase.getHtcDigital());

    setModifiableFieldsOnNewCase(addressTypeChange.getCollectionCase(), oldCase, newCase);

    newCase = caseService.saveNewCaseAndStampCaseRef(newCase);
    caseService.emitCaseCreatedEvent(newCase);

    eventLogger.logCaseEvent(
        newCase,
        responseManagementEvent.getEvent().getDateTime(),
        "Address type changed",
        EventType.ADDRESS_TYPE_CHANGED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(addressTypeChange),
        messageTimestamp);
  }

  private void setModifiableFieldsOnNewCase(
      AddressTypeChangeDetails addressTypeChangeDetails, Case oldCase, Case newCase) {
    if (!StringUtils.isEmpty(addressTypeChangeDetails.getCeExpectedCapacity())) {
      newCase.setCeExpectedCapacity(
          Integer.parseInt(addressTypeChangeDetails.getCeExpectedCapacity()));
    }

    if (addressTypeChangeDetails.getAddress().getEstabType() != null) {
      addressTypeChangeDetails.getAddress().getEstabType().ifPresent(newCase::setEstabType);
    }

    if (addressTypeChangeDetails.getAddress().getAddressLine1() != null) {
      addressTypeChangeDetails.getAddress().getAddressLine1().ifPresent(newCase::setAddressLine1);
    } else {
      newCase.setAddressLine1(oldCase.getAddressLine1());
    }

    if (addressTypeChangeDetails.getAddress().getAddressLine2() != null) {
      addressTypeChangeDetails
          .getAddress()
          .getAddressLine2()
          .ifPresentOrElse(newCase::setAddressLine2, () -> newCase.setAddressLine2(null));
    } else {
      newCase.setAddressLine2(oldCase.getAddressLine2());
    }

    if (addressTypeChangeDetails.getAddress().getAddressLine3() != null) {
      addressTypeChangeDetails
          .getAddress()
          .getAddressLine3()
          .ifPresentOrElse(newCase::setAddressLine3, () -> newCase.setAddressLine3(null));
    } else {
      newCase.setAddressLine3(oldCase.getAddressLine3());
    }

    if (addressTypeChangeDetails.getAddress().getOrganisationName() != null) {
      addressTypeChangeDetails
          .getAddress()
          .getOrganisationName()
          .ifPresentOrElse(newCase::setOrganisationName, () -> newCase.setOrganisationName(null));
    } else {
      newCase.setOrganisationName(oldCase.getOrganisationName());
    }
  }

  private String deriveAddressLevel(String oldCaseType, String newCaseType) {

    if (oldCaseType.equals("CE") && newCaseType.equals("SPG")) {
      return "U";
    } else if (oldCaseType.equals("SPG") && newCaseType.equals("HH")) {
      return "U";
    } else if (oldCaseType.equals("HH") && newCaseType.equals("SPG")) {
      return "U";
    } else if (oldCaseType.equals("SPG") && newCaseType.equals("CE")) {
      return "E";
    } else if (oldCaseType.equals("HH") && newCaseType.equals("CE")) {
      return "E";
    } else if (oldCaseType.equals("CE") && newCaseType.equals("HH")) {
      return "U";
    } else {
      throw new RuntimeException("Invalid Case Type change");
    }
  }

  private void validateModification(AddressTypeChangeAddress address) {
    if (address.getAddressLine1() != null && !address.getAddressLine1().isPresent()) {
      throw new RuntimeException("Mandatory address line 1 cannot be set to null");
    }

    if (address.getAddressLine1() != null
        && address.getAddressLine1().isPresent()
        && StringUtils.isEmpty(address.getAddressLine1().get())) {
      throw new RuntimeException("Mandatory address line 1 is empty");
    }

    if (address.getEstabType() != null && !address.getEstabType().isPresent()) {
      throw new RuntimeException("Mandatory estab type cannot be set to null");
    }

    if (address.getEstabType() != null
        && address.getEstabType().isPresent()
        && !estabTypes.contains(address.getEstabType().get())) {
      throw new RuntimeException("Estab Type not valid");
    }
  }
}
