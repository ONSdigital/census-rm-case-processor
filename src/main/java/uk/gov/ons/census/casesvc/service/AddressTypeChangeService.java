package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressTypeChange;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class AddressTypeChangeService {
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final InvalidAddressService invalidAddressService;

  public AddressTypeChangeService(
      CaseService caseService,
      EventLogger eventLogger,
      InvalidAddressService invalidAddressService) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.invalidAddressService = invalidAddressService;
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

    if (!StringUtils.isEmpty(addressTypeChange.getCollectionCase().getCeExpectedCapacity())) {
      newCase.setCeExpectedCapacity(
          Integer.parseInt(addressTypeChange.getCollectionCase().getCeExpectedCapacity()));
    }

    newCase.setUprn(oldCase.getUprn());
    newCase.setOrganisationName(oldCase.getOrganisationName());
    newCase.setAddressLine1(oldCase.getAddressLine1());
    newCase.setAddressLine2(oldCase.getAddressLine2());
    newCase.setAddressLine3(oldCase.getAddressLine3());
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
}
