package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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

  @Value("${censusconfig.collectionexerciseid}")
  private String censusCollectionExerciseId;

  @Value("${censusconfig.actionplanid}")
  private String censusActionPlanId;

  public NewAddressReportedService(CaseService caseService, EventLogger eventLogger) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  public void processNewAddress(
      ResponseManagementEvent newAddressEvent, OffsetDateTime messageTimestamp) {
    CollectionCase newCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    checkManadatoryFieldsPresent(newCollectionCase);

    Case skeletonCase = createSkeletonCase(newCollectionCase);

    skeletonCase = caseService.saveNewCaseAndStampCaseRef(skeletonCase);
    caseService.emitCaseCreatedEvent(skeletonCase);

    eventLogger.logCaseEvent(
        skeletonCase,
        newAddressEvent.getEvent().getDateTime(),
        "New Address reported",
        EventType.NEW_ADDRESS_REPORTED,
        newAddressEvent.getEvent(),
        JsonHelper.convertObjectToJson(newAddressEvent.getPayload()),
        messageTimestamp);
  }

  public void processNewAddressFromSourceId(
      ResponseManagementEvent newAddressEvent, OffsetDateTime messageTimestamp, UUID sourceCaseId) {
    CollectionCase newCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    checkManadatoryFieldsPresent(newCollectionCase);

    Case sourceCase = caseService.getCaseByCaseId(sourceCaseId);
    Case newCaseFromSourceCase = buildCaseFromSourceCaseAndEvent(newCollectionCase, sourceCase);

    newCaseFromSourceCase = caseService.saveNewCaseAndStampCaseRef(newCaseFromSourceCase);
    caseService.emitCaseCreatedEvent(newCaseFromSourceCase);

    eventLogger.logCaseEvent(
        newCaseFromSourceCase,
        newAddressEvent.getEvent().getDateTime(),
        "New Address reported",
        EventType.NEW_ADDRESS_REPORTED,
        newAddressEvent.getEvent(),
        JsonHelper.convertObjectToJson(newAddressEvent.getPayload()),
        messageTimestamp);
  }

  private Case createSkeletonCase(CollectionCase collectionCase) {
    Case skeletonCase = new Case();
    skeletonCase.setSkeleton(true);
    skeletonCase.setCaseId(UUID.fromString(collectionCase.getId()));

    if (StringUtils.isEmpty(collectionCase.getCaseType())) {
      skeletonCase.setCaseType(collectionCase.getAddress().getAddressType());
    } else {
      skeletonCase.setCaseType(collectionCase.getCaseType());
    }

    if (StringUtils.isEmpty(collectionCase.getCollectionExerciseId())) {
      skeletonCase.setCollectionExerciseId(censusCollectionExerciseId);
    } else {
      skeletonCase.setCollectionExerciseId(collectionCase.getCollectionExerciseId());
    }

    skeletonCase.setAddressLine1(collectionCase.getAddress().getAddressLine1());
    skeletonCase.setAddressLine2(collectionCase.getAddress().getAddressLine2());
    skeletonCase.setAddressLine3(collectionCase.getAddress().getAddressLine3());
    skeletonCase.setTownName(collectionCase.getAddress().getTownName());
    skeletonCase.setPostcode(collectionCase.getAddress().getPostcode());
    skeletonCase.setLatitude(collectionCase.getAddress().getLatitude());
    skeletonCase.setLongitude(collectionCase.getAddress().getLongitude());
    skeletonCase.setUprn(collectionCase.getAddress().getUprn());
    skeletonCase.setEstabType(collectionCase.getAddress().getEstabType());
    skeletonCase.setRegion(collectionCase.getAddress().getRegion());
    skeletonCase.setAddressLevel(collectionCase.getAddress().getAddressLevel());
    skeletonCase.setAddressType(collectionCase.getAddress().getAddressType());
    skeletonCase.setEstabType(collectionCase.getAddress().getEstabType());
    skeletonCase.setOrganisationName(collectionCase.getAddress().getOrganisationName());
    skeletonCase.setFieldCoordinatorId(collectionCase.getFieldCoordinatorId());
    skeletonCase.setFieldOfficerId(collectionCase.getFieldOfficerId());
    skeletonCase.setCeExpectedCapacity(collectionCase.getCeExpectedCapacity());

    skeletonCase.setActionPlanId(censusActionPlanId);
    skeletonCase.setSurvey("CENSUS");
    skeletonCase.setHandDelivery(false);
    skeletonCase.setRefusalReceived(false);
    skeletonCase.setReceiptReceived(false);
    skeletonCase.setAddressInvalid(false);
    skeletonCase.setCeActualResponses(0);

    return skeletonCase;
  }

  // https://collaborate2.ons.gov.uk/confluence/display/SDC/Handle+New+Address+Reported+Events
  private void checkManadatoryFieldsPresent(CollectionCase newCollectionCase) {

    if (StringUtils.isEmpty(newCollectionCase.getId())) {
      throw new RuntimeException("missing id in newAddress CollectionCase");
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getAddressType())) {
      throw new RuntimeException("missing addressType in newAddress CollectionCase Address");
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getAddressLevel())) {
      throw new RuntimeException("missing addressLevel in newAddress CollectionCase Address");
    }

    if (StringUtils.isEmpty(newCollectionCase.getAddress().getRegion())) {
      throw new RuntimeException("missing region in newAddress CollectionCase Address");
    }
  }

  private Case buildCaseFromSourceCaseAndEvent(CollectionCase newCollectionCase, Case sourceCase) {

    Case newCase = new Case();

    // Set mandatory fields from the event
    newCase.setCaseRef(null);
    newCase.setCaseId(UUID.fromString(newCollectionCase.getId()));
    newCase.setRegion(newCollectionCase.getAddress().getRegion());
    newCase.setAddressLevel(newCollectionCase.getAddress().getAddressLevel());
    newCase.setAddressType(newCollectionCase.getAddress().getAddressType());

    // Set fields that come from source case, unless they are in the event
    newCase.setAddressLine1(
        getEventValOverSource(
            sourceCase.getAddressLine1(), newCollectionCase.getAddress().getAddressLine1()));
    newCase.setAddressLine2(
        getEventValOverSource(
            sourceCase.getAddressLine2(), newCollectionCase.getAddress().getAddressLine2()));
    newCase.setAddressLine3(
        getEventValOverSource(
            sourceCase.getAddressLine3(), newCollectionCase.getAddress().getAddressLine3()));
    newCase.setTownName(
        getEventValOverSource(
            sourceCase.getTownName(), newCollectionCase.getAddress().getTownName()));
    newCase.setPostcode(
        getEventValOverSource(
            sourceCase.getPostcode(), newCollectionCase.getAddress().getPostcode()));
    newCase.setCollectionExerciseId(
        getEventValOverSource(
            sourceCase.getCollectionExerciseId(), newCollectionCase.getCollectionExerciseId()));
    newCase.setEstabType(
        getEventValOverSource(
            sourceCase.getEstabType(), newCollectionCase.getAddress().getEstabType()));
    newCase.setFieldCoordinatorId(
        getEventValOverSource(
            sourceCase.getFieldCoordinatorId(), newCollectionCase.getFieldCoordinatorId()));
    newCase.setFieldOfficerId(
        getEventValOverSource(
            sourceCase.getFieldOfficerId(), newCollectionCase.getFieldOfficerId()));

    // Set fields empty/null/blank unless they come from the event
    newCase.setOrganisationName(
        getEventValOverSource(null, newCollectionCase.getAddress().getOrganisationName()));
    newCase.setLatitude(getEventValOverSource(null, newCollectionCase.getAddress().getLatitude()));
    newCase.setLongitude(
        getEventValOverSource(null, newCollectionCase.getAddress().getLongitude()));
    newCase.setUprn(getEventValOverSource(null, newCollectionCase.getAddress().getUprn()));
    newCase.setCeExpectedCapacity(
        getEventValOverSource(null, newCollectionCase.getCeExpectedCapacity()));
    newCase.setCaseType(getEventValOverSource(null, newCollectionCase.getCaseType()));
    newCase.setTreatmentCode(getEventValOverSource(null, newCollectionCase.getTreatmentCode()));

    // Fields that do not come on the event but come from source case
    newCase.setEstabUprn(sourceCase.getEstabUprn());
    newCase.setAbpCode(sourceCase.getAbpCode());
    newCase.setHtcDigital(sourceCase.getHtcDigital());
    newCase.setHtcWillingness(sourceCase.getHtcWillingness());
    newCase.setLad(sourceCase.getLad());
    newCase.setLsoa(sourceCase.getLsoa());
    newCase.setMsoa(sourceCase.getMsoa());
    newCase.setOa(sourceCase.getOa());
    newCase.setPrintBatch(sourceCase.getPrintBatch());
    newCase.setSurvey(sourceCase.getSurvey());

    // Fields that need to be set
    newCase.setActionPlanId(censusActionPlanId);
    newCase.setHandDelivery(false);
    newCase.setRefusalReceived(false);
    newCase.setReceiptReceived(false);
    newCase.setAddressInvalid(false);
    newCase.setCeActualResponses(0);

    return newCase;
  }

  private <T> T getEventValOverSource(T baseValue, T eventValue) {
    if (eventValue != null) {
      return eventValue;
    } else {
      return baseValue;
    }
  }
}
