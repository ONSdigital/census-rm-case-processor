package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.NEW_ADDRESS_ENHANCED;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.NewAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseMetadata;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@Component
public class NewAddressReportedService {
  private static final Logger log = LoggerFactory.getLogger(NewAddressReportedService.class);

  private final CaseService caseService;
  private final EventLogger eventLogger;
  private final PubSubTemplate pubSubTemplate;

  @Value("${censusconfig.collectionexerciseid}")
  private UUID censusCollectionExerciseId;

  @Value("${censusconfig.actionplanid}")
  private UUID censusActionPlanId;

  @Value("${uprnconfig.dummyuprnprefix}")
  private String dummyUprnPrefix;

  @Value("${pubsub.publishtimeout}")
  private int publishTimeout;

  @Value("${pubsub.aims-new-address-topic}")
  private String aimsNewAddressTopic;

  public NewAddressReportedService(
      CaseService caseService, EventLogger eventLogger, PubSubTemplate pubSubTemplate) {
    this.caseService = caseService;
    this.eventLogger = eventLogger;
    this.pubSubTemplate = pubSubTemplate;
  }

  public void processNewAddress(
      ResponseManagementEvent newAddressEvent, OffsetDateTime messageTimestamp) {
    CollectionCase newCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();
    checkManadatoryFieldsPresent(newCollectionCase);

    Case skeletonCase = createSkeletonCase(newCollectionCase);

    skeletonCase = caseService.saveNewCaseAndStampCaseRef(skeletonCase);
    if (StringUtils.isEmpty(skeletonCase.getUprn())) {
      addDummyUprnToCase(skeletonCase);
      sendNewAddressToAims(newAddressEvent, skeletonCase.getUprn());
      caseService.saveCase(skeletonCase);
    }
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
    if (StringUtils.isEmpty(newCaseFromSourceCase.getUprn())) {
      addDummyUprnToCase(newCaseFromSourceCase);
      sendNewAddressToAims(newAddressEvent, newCaseFromSourceCase.getUprn());
    }

    Metadata metadata =
        getMetaDataToCreateFieldCaseIfConditionsMet(newCaseFromSourceCase, newAddressEvent);

    caseService.saveCaseAndEmitCaseCreatedEvent(newCaseFromSourceCase, metadata);

    eventLogger.logCaseEvent(
        newCaseFromSourceCase,
        newAddressEvent.getEvent().getDateTime(),
        "New Address reported",
        EventType.NEW_ADDRESS_REPORTED,
        newAddressEvent.getEvent(),
        JsonHelper.convertObjectToJson(newAddressEvent.getPayload()),
        messageTimestamp);
  }

  private Metadata getMetaDataToCreateFieldCaseIfConditionsMet(
      Case caze, ResponseManagementEvent newAddressEvent) {

    if (!caze.getCaseType().equals("SPG") && !caze.getCaseType().equals("CE")) {
      return null;
    }

    if (!newAddressEvent.getEvent().getChannel().equals("FIELD")) {
      return null;
    }

    if (newAddressEvent.getPayload().getNewAddress().getCollectionCase().getFieldCoordinatorId()
        == null) {
      return null;
    }

    if (newAddressEvent.getPayload().getNewAddress().getCollectionCase().getFieldOfficerId()
        == null) {
      return null;
    }

    return buildMetadata(EventTypeDTO.NEW_ADDRESS_REPORTED, ActionInstructionType.CREATE);
  }

  private Case createSkeletonCase(CollectionCase collectionCase) {
    Case skeletonCase = new Case();
    skeletonCase.setSkeleton(true);
    skeletonCase.setCaseId(collectionCase.getId());

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
    skeletonCase.setRefusalReceived(null);
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
    newCase.setCaseId(newCollectionCase.getId());
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
    newCase.setLatitude(
        getEventValOverSource(
            sourceCase.getLatitude(), newCollectionCase.getAddress().getLatitude()));
    newCase.setLongitude(
        getEventValOverSource(
            sourceCase.getLongitude(), newCollectionCase.getAddress().getLongitude()));

    // Set fields from the event if they exist
    newCase.setOrganisationName(newCollectionCase.getAddress().getOrganisationName());
    newCase.setCeExpectedCapacity(newCollectionCase.getCeExpectedCapacity());
    newCase.setTreatmentCode(newCollectionCase.getTreatmentCode());
    newCase.setUprn(newCollectionCase.getAddress().getUprn());
    // If no case type on event - set it to address type from event
    newCase.setCaseType(
        getEventValOverSource(
            newCollectionCase.getAddress().getAddressType(), newCollectionCase.getCaseType()));

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
    newCase.setMetadata(metadataFromSourceCase(sourceCase.getMetadata()));

    // Fields that need to be set
    newCase.setActionPlanId(censusActionPlanId);
    newCase.setSkeleton(true);
    newCase.setHandDelivery(false);
    newCase.setRefusalReceived(null);
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

  private CaseMetadata metadataFromSourceCase(CaseMetadata sourceMetadata) {
    CaseMetadata newCaseMetadata = new CaseMetadata();
    newCaseMetadata.setSecureEstablishment(sourceMetadata.getSecureEstablishment());
    return newCaseMetadata;
  }

  private void addDummyUprnToCase(Case newCase) {
    String dummyUprn = String.format("%s%d", dummyUprnPrefix, newCase.getCaseRef());
    newCase.setUprn(dummyUprn);
  }

  private void sendNewAddressToAims(ResponseManagementEvent newAddressEvent, String dummyUprn) {
    ResponseManagementEvent enhancedNewAddressEvent =
        buildEnhancedNewAddressEvent(newAddressEvent, dummyUprn);

    ListenableFuture<String> future =
        pubSubTemplate.publish(aimsNewAddressTopic, enhancedNewAddressEvent);
    try {
      future.get(publishTimeout, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }

    // Warn about dummy UPRN sent to AIMS out of sync with RM if the transaction rolls back.
    // There's nothing we can do about these more elegantly - PubSub needs to become transactional.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                // All we can do is log. Hopefully this is enough info to manually patch the data
                // in AIMS to get rid of any duplicate addresses
                log.with("dummy_uprn", dummyUprn)
                    .with(
                        "case_id",
                        newAddressEvent.getPayload().getNewAddress().getCollectionCase().getId())
                    .error("Transaction rolled back after PubSub message sent");
              }
            }
          });
    }
  }

  private ResponseManagementEvent buildEnhancedNewAddressEvent(
      ResponseManagementEvent newAddressEvent, String dummyUprn) {
    EventDTO event = new EventDTO();
    event.setChannel("RM");
    event.setType(NEW_ADDRESS_ENHANCED);
    event.setTransactionId(newAddressEvent.getEvent().getTransactionId());
    event.setDateTime(OffsetDateTime.now());
    event.setSource("CASE_PROCESSOR");

    CollectionCase sourceCollectionCase =
        newAddressEvent.getPayload().getNewAddress().getCollectionCase();

    Address address = new Address();
    address.setAddressLine1(sourceCollectionCase.getAddress().getAddressLine1());
    address.setAddressLine2(sourceCollectionCase.getAddress().getAddressLine2());
    address.setAddressLine3(sourceCollectionCase.getAddress().getAddressLine3());
    address.setTownName(sourceCollectionCase.getAddress().getTownName());
    address.setPostcode(sourceCollectionCase.getAddress().getPostcode());
    address.setAddressType(sourceCollectionCase.getAddress().getAddressType());
    address.setAddressLevel(sourceCollectionCase.getAddress().getAddressLevel());
    address.setLatitude(sourceCollectionCase.getAddress().getLatitude());
    address.setLongitude(sourceCollectionCase.getAddress().getLongitude());
    address.setRegion(sourceCollectionCase.getAddress().getRegion());
    address.setUprn(dummyUprn);

    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(sourceCollectionCase.getId());
    collectionCase.setCaseType(sourceCollectionCase.getCaseType());
    collectionCase.setAddress(address);
    collectionCase.setSurvey("CENSUS");

    NewAddress newAddress = new NewAddress();
    newAddress.setCollectionCase(collectionCase);

    PayloadDTO payload = new PayloadDTO();
    payload.setNewAddress(newAddress);

    ResponseManagementEvent enhancedNewAddressEvent = new ResponseManagementEvent();
    enhancedNewAddressEvent.setEvent(event);
    enhancedNewAddressEvent.setPayload(payload);

    return enhancedNewAddressEvent;
  }
}
