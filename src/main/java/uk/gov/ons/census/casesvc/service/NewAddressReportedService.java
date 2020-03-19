package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@Component
public class NewAddressReportedService {
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public NewAddressReportedService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processNewAddress(
      ResponseManagementEvent newAddressEvent, OffsetDateTime messageTimestamp) {
    CollectionCase newCollectionCase =
        newAddressEvent.getPayload().getNewAddressReported().getCollectionCase();
    validDateNewAddressCollectionCaseForMandatoryFields(newCollectionCase);

    Case skellingtonCase = createNewSkellingtonCase(newCollectionCase);

    skellingtonCase = caseService.saveNewCaseAndStampCaseRef(skellingtonCase);
    caseService.emitCaseCreatedEvent(skellingtonCase);

    eventLogger.logCaseEvent(
        skellingtonCase,
        newAddressEvent.getEvent().getDateTime(),
        "New Address reported",
        EventType.NEW_ADDRESS_REPORTED,
        newAddressEvent.getEvent(),
        JsonHelper.convertObjectToJson(newAddressEvent.getPayload()),
        messageTimestamp);
  }

  private Case createNewSkellingtonCase(CollectionCase collectionCase) {
    Case skeliingtonCase = new Case();
    skeliingtonCase.setSkellingtonCase(true);
    skeliingtonCase.setCaseId(UUID.fromString(collectionCase.getId()));
    skeliingtonCase.setCollectionExerciseId(collectionCase.getCollectionExerciseId());
    skeliingtonCase.setAddressLine1(collectionCase.getAddress().getAddressLine1());
    skeliingtonCase.setAddressLine2(collectionCase.getAddress().getAddressLine2());
    skeliingtonCase.setAddressLine3(collectionCase.getAddress().getAddressLine3());
    skeliingtonCase.setTownName(collectionCase.getAddress().getTownName());
    skeliingtonCase.setPostcode(collectionCase.getAddress().getPostcode());
    skeliingtonCase.setArid(collectionCase.getAddress().getArid());
    skeliingtonCase.setLatitude(collectionCase.getAddress().getLatitude());
    skeliingtonCase.setLongitude(collectionCase.getAddress().getLongitude());
    skeliingtonCase.setUprn(collectionCase.getAddress().getUprn());
    skeliingtonCase.setRegion(collectionCase.getAddress().getRegion());
    skeliingtonCase.setActionPlanId(collectionCase.getActionPlanId()); // This is essential
    skeliingtonCase.setTreatmentCode(collectionCase.getTreatmentCode()); // This is essential
    skeliingtonCase.setAddressLevel(collectionCase.getAddress().getAddressLevel());
    skeliingtonCase.setAbpCode(collectionCase.getAddress().getApbCode());
    skeliingtonCase.setCaseType(collectionCase.getCaseType());
    skeliingtonCase.setAddressType(collectionCase.getAddress().getAddressType());
    skeliingtonCase.setUprn(collectionCase.getAddress().getUprn());
    skeliingtonCase.setEstabArid(collectionCase.getAddress().getEstabArid());
    skeliingtonCase.setEstabType(collectionCase.getAddress().getEstabType());
    skeliingtonCase.setOrganisationName(collectionCase.getAddress().getOrganisationName());
    skeliingtonCase.setOa(collectionCase.getOa());
    skeliingtonCase.setLsoa(collectionCase.getLsoa());
    skeliingtonCase.setMsoa(collectionCase.getMsoa());
    skeliingtonCase.setLad(collectionCase.getLad());
    skeliingtonCase.setHtcWillingness(collectionCase.getHtcWillingness());
    skeliingtonCase.setHtcDigital(collectionCase.getHtcDigital());
    skeliingtonCase.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    skeliingtonCase.setFieldOfficerId(collectionCase.getFieldOfficerId());
    skeliingtonCase.setCeExpectedCapacity(collectionCase.getCeExpectedCapacity());
    skeliingtonCase.setCeActualResponses(collectionCase.getCeActualResponses());
    skeliingtonCase.setHandDelivery(collectionCase.isHandDelivery());

    skeliingtonCase.setSurvey("CENSUS");
    skeliingtonCase.setRefusalReceived(false);
    skeliingtonCase.setReceiptReceived(false);
    skeliingtonCase.setAddressInvalid(false);
    skeliingtonCase.setUndeliveredAsAddressed(false);
    return skeliingtonCase;
  }

  // https://collaborate2.ons.gov.uk/confluence/display/SDC/Handle+New+Address+Reported+Events
  // Only a small number of mandatory fields to create a skellington case
  private UUID validDateNewAddressCollectionCaseForMandatoryFields(
      CollectionCase newCollectionCase) {
    UUID newCaseId;

    try {
      newCaseId = UUID.fromString(newCollectionCase.getId());
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(
          "Expected NewAddress CollectionCase Id to be a valid UUID, got: "
              + newCollectionCase.getId());
    }

    List<String> valiidCaseTypes = Arrays.asList(new String[] {"HH", "HI", "CE", "SPG"});

    if (!valiidCaseTypes.contains(newCollectionCase.getCaseType())) {
      throw new RuntimeException(
          "Unexpected newAddress CollectionCase caseType: " + newCollectionCase.getCaseType());
    }

    String newAddressLevel = newCollectionCase.getAddress().getAddressLevel();

    if (!newAddressLevel.equals("E") && !newAddressLevel.equals("U")) {
      throw new RuntimeException(
          "Unexpected a valid address level in newAddress CollectionCase Address, received: "
              + newAddressLevel);
    }

    String newRegion = newCollectionCase.getAddress().getRegion();
    List<String> validRegions = Arrays.asList(new String[] {"E", "W", "N"});

    if (!validRegions.contains(newRegion)) {
      throw new RuntimeException("Invalid newAddress collectionCase Address Region: " + newRegion);
    }

    return newCaseId;
  }
}
