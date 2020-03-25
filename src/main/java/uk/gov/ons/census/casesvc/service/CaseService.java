package uk.gov.ons.census.casesvc.service;

import java.time.OffsetDateTime;
import java.util.*;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseMetadata;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.CaseRefGenerator;
import uk.gov.ons.census.casesvc.utility.EventHelper;

@Service
public class CaseService {
  private static final String CENSUS_SURVEY = "CENSUS";
  private static final String CCS_SURVEY = "CCS";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_CASE_TYPE = "HI";
  public static final String CASE_UPDATE_ROUTING_KEY = "event.case.update";

  @Value("${directdeliverytreatmentcodes}")
  private List<String> directDeliveryTreatmentCodes;

  private final CaseRepository caseRepository;
  private final MapperFacade mapperFacade;
  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  @Value("${ccsconfig.action-plan-id}")
  private String actionPlanId;

  @Value("${ccsconfig.collection-exercise-id}")
  private String collectionExerciseId;

  @Value("${caserefgeneratorkey}")
  private byte[] caserefgeneratorkey;

  public CaseService(
      CaseRepository caseRepository, RabbitTemplate rabbitTemplate, MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.mapperFacade = mapperFacade;
  }

  public Case saveCase(Case caze) {
    return caseRepository.saveAndFlush(caze);
  }

  public Case saveNewCaseAndStampCaseRef(Case caze) {
    caze = caseRepository.saveAndFlush(caze);
    caze.setCaseRef(
        CaseRefGenerator.getCaseRef(caze.getSecretSequenceNumber(), caserefgeneratorkey));
    caze = caseRepository.saveAndFlush(caze);

    return caze;
  }

  public Case saveCaseSample(CreateCaseSample createCaseSample) {
    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseType(createCaseSample.getAddressType());
    caze.setCaseId(UUID.randomUUID());
    caze.setCreatedDateTime(OffsetDateTime.now());
    caze.setReceiptReceived(false);
    caze.setSurvey(CENSUS_SURVEY);
    caze.setCeActualResponses(0);
    caze.setHandDelivery(isTreatmentCodeDirectDelivered(createCaseSample.getTreatmentCode()));

    CaseMetadata metadata = new CaseMetadata();
    Boolean secureEstablishment = Boolean.valueOf(createCaseSample.getSecureEstablishment() == 1);
    metadata.setSecureEstablishment(secureEstablishment);
    caze.setMetadata(metadata);

    return saveNewCaseAndStampCaseRef(caze);
  }

  public boolean isTreatmentCodeDirectDelivered(String treatmentCode) {
    return directDeliveryTreatmentCodes.contains(treatmentCode);
  }

  public Case createCCSCase(
      String caseId, SampleUnitDTO sampleUnit, boolean isRefused, boolean isInvalidAddress) {
    Case caze = mapperFacade.map(sampleUnit, Case.class);
    caze.setCaseType(sampleUnit.getAddressType());
    caze.setCaseId(UUID.fromString(caseId));
    caze.setActionPlanId(actionPlanId);
    caze.setCollectionExerciseId(collectionExerciseId);
    caze.setCreatedDateTime(OffsetDateTime.now());
    caze.setRefusalReceived(isRefused);
    caze.setAddressInvalid(isInvalidAddress);
    caze.setSurvey(CCS_SURVEY);

    return saveNewCaseAndStampCaseRef(caze);
  }

  public PayloadDTO saveCaseAndEmitCaseCreatedEvent(Case caze) {
    return saveCaseAndEmitCaseCreatedEvent(caze, null);
  }

  public PayloadDTO saveCaseAndEmitCaseCreatedEvent(Case caze, Metadata metadata) {
    return saveCaseAndEmitCaseCreatedEvent(caze, null, metadata);
  }

  public PayloadDTO saveCaseAndEmitCaseCreatedEvent(
      Case caze, FulfilmentRequestDTO fulfilmentRequest, Metadata metadata) {
    caseRepository.saveAndFlush(caze);

    return emitCaseCreatedEvent(caze, fulfilmentRequest, metadata);
  }

  public PayloadDTO emitCaseCreatedEvent(Case caze) {
    return emitCaseCreatedEvent(caze, null, null);
  }

  public PayloadDTO emitCaseCreatedEvent(Case caze, FulfilmentRequestDTO fulfilmentRequest) {
    return emitCaseCreatedEvent(caze, fulfilmentRequest, null);
  }

  public PayloadDTO emitCaseCreatedEvent(
      Case caze, FulfilmentRequestDTO fulfilmentRequest, Metadata metadata) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_CREATED);
    ResponseManagementEvent responseManagementEvent = prepareCaseEvent(caze, eventDTO);

    // This has been added in to allow Action Scheduler to process fulfilments for individuals
    responseManagementEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    // We need this gubbins to make Fieldwork Adapter work
    responseManagementEvent.getPayload().setMetadata(metadata);

    rabbitTemplate.convertAndSend(
        outboundExchange, CASE_UPDATE_ROUTING_KEY, responseManagementEvent);
    return responseManagementEvent.getPayload();
  }

  public void saveCaseAndEmitCaseUpdatedEvent(Case caze) {
    saveCaseAndEmitCaseUpdatedEvent(caze, null);
  }

  public void saveCaseAndEmitCaseUpdatedEvent(Case caze, Metadata metadata) {
    caseRepository.saveAndFlush(caze);

    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_UPDATED);
    ResponseManagementEvent responseManagementEvent = prepareCaseEvent(caze, eventDTO, metadata);
    rabbitTemplate.convertAndSend(
        outboundExchange, CASE_UPDATE_ROUTING_KEY, responseManagementEvent);
  }

  private ResponseManagementEvent prepareCaseEvent(
      Case caze, EventDTO eventDTO, Metadata metadata) {
    Address address = createAddress(caze);
    CollectionCase collectionCase = createCollectionCase(caze, address);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setCollectionCase(collectionCase);
    payloadDTO.setMetadata(metadata);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(eventDTO);
    responseManagementEvent.setPayload(payloadDTO);
    return responseManagementEvent;
  }

  private ResponseManagementEvent prepareCaseEvent(Case caze, EventDTO eventDTO) {
    return prepareCaseEvent(caze, eventDTO, null);
  }

  private Address createAddress(Case caze) {
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setEstabArid(caze.getEstabArid());
    address.setEstabType(caze.getEstabType());
    address.setLatitude(caze.getLatitude());
    address.setLongitude(caze.getLongitude());
    address.setPostcode(caze.getPostcode());
    address.setTownName(caze.getTownName());
    address.setApbCode(caze.getAbpCode());
    address.setOrganisationName(caze.getOrganisationName());
    address.setUprn(caze.getUprn());
    address.setAddressLevel(caze.getAddressLevel());
    if (caze.getRegion() != null) {
      address.setRegion(caze.getRegion().substring(0, 1));
    }
    return address;
  }

  private CollectionCase createCollectionCase(Case caze, Address address) {
    CollectionCase collectionCase = new CollectionCase();

    // These are the mandatory fields required by RH, as documented in the event dictionary
    collectionCase.setActionableFrom(OffsetDateTime.now());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef(Long.toString(caze.getCaseRef()));
    collectionCase.setCaseType(caze.getCaseType());
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getCaseId().toString());
    collectionCase.setSurvey(caze.getSurvey());
    // Stop. No. Don't put anything else here unless it's in the event dictionary. Look down!

    // Below this line is extra data potentially needed by Action Scheduler - will be ignored by RH
    collectionCase.setActionPlanId(caze.getActionPlanId());
    collectionCase.setTreatmentCode(caze.getTreatmentCode());
    collectionCase.setOa(caze.getOa());
    collectionCase.setLsoa(caze.getLsoa());
    collectionCase.setMsoa(caze.getMsoa());
    collectionCase.setLad(caze.getLad());
    collectionCase.setHtcWillingness(caze.getHtcWillingness());
    collectionCase.setHtcDigital(caze.getHtcDigital());
    collectionCase.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    collectionCase.setFieldOfficerId(caze.getFieldOfficerId());
    collectionCase.setCeExpectedCapacity(caze.getCeExpectedCapacity());
    collectionCase.setCeActualResponses(caze.getCeActualResponses());
    collectionCase.setReceiptReceived(caze.isReceiptReceived());
    collectionCase.setRefusalReceived(caze.isRefusalReceived());
    collectionCase.setAddressInvalid(caze.isAddressInvalid());
    collectionCase.setUndeliveredAsAddressed(caze.isUndeliveredAsAddressed());
    collectionCase.setHandDelivery(caze.isHandDelivery());
    collectionCase.setMetadata(caze.getMetadata());
    // Yes. You can add stuff to the bottom of this list if you like.

    return collectionCase;
  }

  public Case prepareIndividualResponseCaseFromParentCase(Case parentCase, UUID caseId) {
    Case individualResponseCase = new Case();

    individualResponseCase.setCaseId(caseId);
    individualResponseCase.setCreatedDateTime(OffsetDateTime.now());
    individualResponseCase.setAddressType(parentCase.getAddressType());
    individualResponseCase.setCaseType(HOUSEHOLD_INDIVIDUAL_RESPONSE_CASE_TYPE);
    individualResponseCase.setCollectionExerciseId(parentCase.getCollectionExerciseId());
    individualResponseCase.setActionPlanId(parentCase.getActionPlanId());
    individualResponseCase.setArid(parentCase.getArid());
    individualResponseCase.setEstabArid(parentCase.getEstabArid());
    individualResponseCase.setUprn(parentCase.getUprn());
    individualResponseCase.setEstabType(parentCase.getEstabType());
    individualResponseCase.setAbpCode(parentCase.getAbpCode());
    individualResponseCase.setOrganisationName(parentCase.getOrganisationName());
    individualResponseCase.setAddressLine1(parentCase.getAddressLine1());
    individualResponseCase.setAddressLine2(parentCase.getAddressLine2());
    individualResponseCase.setAddressLine3(parentCase.getAddressLine3());
    individualResponseCase.setAddressLevel(parentCase.getAddressLevel());
    individualResponseCase.setTownName(parentCase.getTownName());
    individualResponseCase.setPostcode(parentCase.getPostcode());
    individualResponseCase.setLatitude(parentCase.getLatitude());
    individualResponseCase.setLongitude(parentCase.getLongitude());
    individualResponseCase.setOa(parentCase.getOa());
    individualResponseCase.setLsoa(parentCase.getLsoa());
    individualResponseCase.setMsoa(parentCase.getMsoa());
    individualResponseCase.setLad(parentCase.getLad());
    individualResponseCase.setRegion(parentCase.getRegion());
    individualResponseCase.setSurvey(
        parentCase.getSurvey()); // Should only ever be "CENSUS" from the parent case

    return individualResponseCase;
  }

  public Case getCaseByCaseId(UUID caseId) {
    Optional<Case> cazeResult = caseRepository.findById(caseId);

    if (cazeResult.isEmpty()) {
      throw new RuntimeException(String.format("Case ID '%s' not present", caseId));
    }
    return cazeResult.get();
  }

  public Case getCaseByCaseRef(long caseRef) {
    Optional<Case> caseOptional = caseRepository.findByCaseRef(caseRef);

    if (caseOptional.isEmpty()) {
      throw new RuntimeException(String.format("Case ref '%s' not present", caseRef));
    }

    return caseOptional.get();
  }

  public void unreceiptCase(Case caze, Metadata metadata) {
    caze.setReceiptReceived(false);
    saveCaseAndEmitCaseUpdatedEvent(caze, metadata);
  }
}
