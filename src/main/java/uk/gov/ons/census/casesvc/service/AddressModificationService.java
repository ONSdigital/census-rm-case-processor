package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.AddressModification;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@Service
public class AddressModificationService {
  private static final Set<String> ESTAB_TYPES =
      Set.of(
          "HALL OF RESIDENCE",
          "CARE HOME",
          "HOSPITAL",
          "HOSPICE",
          "MENTAL HEALTH HOSPITAL",
          "MEDICAL CARE OTHER",
          "BOARDING SCHOOL",
          "LOW/MEDIUM SECURE MENTAL HEALTH",
          "HIGH SECURE MENTAL HEALTH",
          "HOTEL",
          "YOUTH HOSTEL",
          "HOSTEL",
          "MILITARY SLA",
          "MILITARY US",
          "RELIGIOUS COMMUNITY",
          "RESIDENTIAL CHILDRENS HOME",
          "EDUCATION OTHER",
          "PRISON",
          "IMMIGRATION REMOVAL CENTRE",
          "APPROVED PREMISES",
          "ROUGH SLEEPER",
          "STAFF ACCOMMODATION",
          "CAMPHILL",
          "HOLIDAY PARK",
          "HOUSEHOLD",
          "SHELTERED ACCOMMODATION",
          "RESIDENTIAL CARAVAN",
          "RESIDENTIAL BOAT",
          "GATED APARTMENTS",
          "MOD HOUSEHOLDS",
          "FOREIGN OFFICES",
          "CASTLES",
          "GRT SITE",
          "MILITARY SFA",
          "EMBASSY",
          "ROYAL HOUSEHOLD",
          "CARAVAN SITE",
          "MARINA",
          "TRAVELLING PERSONS",
          "TRANSIENT PERSONS");

  private final CaseService caseService;
  private final EventLogger eventLogger;

  public AddressModificationService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processMessage(
      ResponseManagementEvent responseManagementEvent, OffsetDateTime messageTimestamp) {

    AddressModification addressModification =
        responseManagementEvent.getPayload().getAddressModification();

    validate(addressModification);

    Case caze =
        caseService.getCaseByCaseId(
            UUID.fromString(addressModification.getCollectionCase().getId()));

    caze.setEstabType(addressModification.getNewAddress().getEstabType());
    caze.setTownName(addressModification.getNewAddress().getTownName());
    caze.setAddressLine1(addressModification.getNewAddress().getAddressLine1());
    caze.setAddressLine2(addressModification.getNewAddress().getAddressLine2());
    caze.setAddressLine3(addressModification.getNewAddress().getAddressLine3());
    caze.setOrganisationName(addressModification.getNewAddress().getOrganisationName());

    caseService.saveCaseAndEmitCaseUpdatedEvent(caze, null);

    eventLogger.logCaseEvent(
        caze,
        responseManagementEvent.getEvent().getDateTime(),
        "Modified address",
        EventType.ADDRESS_MODIFIED,
        responseManagementEvent.getEvent(),
        convertObjectToJson(addressModification),
        messageTimestamp);
  }

  private void validate(AddressModification addressModification) {

    if (StringUtils.isEmpty(addressModification.getNewAddress().getTownName())
        || StringUtils.isEmpty(addressModification.getNewAddress().getAddressLine1())) {
      throw new RuntimeException("Mandatory field is empty");
    }

    if (!ESTAB_TYPES.contains(addressModification.getNewAddress().getEstabType())) {
        throw new RuntimeException("Estab Type not valid");
    }


  }
}
