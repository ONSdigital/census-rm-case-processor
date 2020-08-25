package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressTypeChanged;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class AddressTypeChangedService {
  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final InvalidAddressService invalidAddressService;

  public AddressTypeChangedService(
      CaseService caseService,
      EventLogger eventLogger,
      InvalidAddressService invalidAddressService) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.invalidAddressService = invalidAddressService;
  }

  public void processMessage(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {
    AddressTypeChanged addressTypeChanged =
        responseManagementEvent.getPayload().getAddressTypeChanged();

    if (addressTypeChanged.getCollectionCase().getId().equals(addressTypeChanged.getNewCaseId())) {
      throw new RuntimeException("Old Case ID cannot equal New Case ID");
    }

    Case oldCase = caseService.getCaseByCaseId(addressTypeChanged.getCollectionCase().getId());

    if (oldCase.getCaseType().equals("HI")) {
      throw new RuntimeException("Cannot change case of type HI");
    }
    invalidateOldCase(responseManagementEvent, messageTimestamp, addressTypeChanged, oldCase);

    createNewCase(responseManagementEvent, messageTimestamp, addressTypeChanged, oldCase);
  }

  private void invalidateOldCase(
      ResponseManagementEvent responseManagementEvent,
      OffsetDateTime messageTimestamp,
      AddressTypeChanged addressTypeChanged,
      Case oldCase) {

    invalidAddressService.invalidateCase(
        responseManagementEvent, messageTimestamp, oldCase, addressTypeChanged);

    eventLogger.logCaseEvent(
        oldCase,
        responseManagementEvent.getEvent().getDateTime(),
        "Address type changed",
        EventType.ADDRESS_TYPE_CHANGED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(addressTypeChanged),
        messageTimestamp);
  }

  private void createNewCase(
      ResponseManagementEvent responseManagementEvent,
      OffsetDateTime messageTimestamp,
      AddressTypeChanged addressTypeChanged,
      Case oldCase) {
    Case newCase = new Case();
    newCase.setSkeleton(true);
    newCase.setCaseId(addressTypeChanged.getNewCaseId());
    newCase.setCaseType(addressTypeChanged.getCollectionCase().getAddress().getAddressType());
    newCase.setAddressType(addressTypeChanged.getCollectionCase().getAddress().getAddressType());
    newCase.setAddressLevel(deriveAddressLevel(oldCase.getCaseType(), newCase.getCaseType()));
    newCase.setRegion(oldCase.getRegion());
    newCase.setCollectionExerciseId(oldCase.getCollectionExerciseId());
    newCase.setActionPlanId(oldCase.getActionPlanId());
    newCase.setSurvey(oldCase.getSurvey());

    if (!StringUtils.isEmpty(addressTypeChanged.getCollectionCase().getCeExpectedCapacity())) {
      newCase.setCeExpectedCapacity(
          Integer.parseInt(addressTypeChanged.getCollectionCase().getCeExpectedCapacity()));
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
        convertObjectToJson(addressTypeChanged),
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
