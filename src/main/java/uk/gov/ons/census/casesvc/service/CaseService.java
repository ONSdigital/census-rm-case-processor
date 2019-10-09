package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
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
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.RandomCaseRefGenerator;

@Service
public class CaseService {
  private static final Logger log = LoggerFactory.getLogger(CaseService.class);
  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String SURVEY = "CENSUS";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_CASE_TYPE = "HI";
  public static final String CASE_UPDATE_ROUTING_KEY = "event.case.update";
  private static final String HOUSEHOLD_RESPONSE_ADDRESS_TYPE = "HH";

  private final CaseRepository caseRepository;
  private final MapperFacade mapperFacade;
  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  @Value("${ccsconfig.action-plan-id}")
  private String actionPlanId;

  @Value("${ccsconfig.collection-exercise-id}")
  private String collectionExerciseId;

  public CaseService(
      CaseRepository caseRepository, RabbitTemplate rabbitTemplate, MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.mapperFacade = mapperFacade;
  }

  public Case saveCase(CreateCaseSample createCaseSample) {
    int caseRef = getUniqueCaseRef();

    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseRef(caseRef);
    caze.setCaseType(HOUSEHOLD_RESPONSE_ADDRESS_TYPE);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze.setCreatedDateTime(OffsetDateTime.now());
    caze.setReceiptReceived(false);
    caze = caseRepository.saveAndFlush(caze);
    return caze;
  }

  public Case createCCSCase(String caseId, SampleUnitDTO sampleUnit, boolean isRefused) {
    int caseRef = getUniqueCaseRef();

    Case caze = mapperFacade.map(sampleUnit, Case.class);
    caze.setCaseRef(caseRef);
    caze.setCaseType(sampleUnit.getAddressType());
    caze.setCaseId(UUID.fromString(caseId));
    caze.setActionPlanId(actionPlanId);
    caze.setCollectionExerciseId(collectionExerciseId);
    caze.setState(CaseState.ACTIONABLE);
    caze.setCreatedDateTime(OffsetDateTime.now());
    caze.setRefusalReceived(isRefused);
    caze.setCcsCase(true);

    caseRepository.saveAndFlush(caze);

    return caze;
  }

  public int getUniqueCaseRef() {
    int caseRef = RandomCaseRefGenerator.getCaseRef();

    // Check for collisions
    if (caseRepository.existsById(caseRef)) {
      throw new RuntimeException(String.format("Case ref '%s' collision", caseRef));
    }
    return caseRef;
  }

  public PayloadDTO saveAndEmitCaseCreatedEvent(Case caze) {
    return saveAndEmitCaseCreatedEvent(caze, null);
  }

  public PayloadDTO saveAndEmitCaseCreatedEvent(Case caze, FulfilmentRequestDTO fulfilmentRequest) {
    caseRepository.saveAndFlush(caze);

    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_CREATED);
    ResponseManagementEvent responseManagementEvent = prepareCaseEvent(caze, eventDTO);

    // This has been added in to allow Action Scheduler to process fulfilments for individuals
    responseManagementEvent.getPayload().setFulfilmentRequest(fulfilmentRequest);

    rabbitTemplate.convertAndSend(
        outboundExchange, CASE_UPDATE_ROUTING_KEY, responseManagementEvent);
    return responseManagementEvent.getPayload();
  }

  public void saveAndEmitCaseUpdatedEvent(Case caze) {
    caseRepository.saveAndFlush(caze);

    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_UPDATED);
    ResponseManagementEvent responseManagementEvent = prepareCaseEvent(caze, eventDTO);
    rabbitTemplate.convertAndSend(
        outboundExchange, CASE_UPDATE_ROUTING_KEY, responseManagementEvent);
  }

  private ResponseManagementEvent prepareCaseEvent(Case caze, EventDTO eventDTO) {
    Address address = createAddress(caze);
    CollectionCase collectionCase = createCollectionCase(caze, address);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setCollectionCase(collectionCase);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(eventDTO);
    responseManagementEvent.setPayload(payloadDTO);
    return responseManagementEvent;
  }

  private Address createAddress(Case caze) {
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setEstabArid(caze.getEstabArid());
    address.setRegion(caze.getRegion().substring(0, 1));
    address.setEstabType(caze.getEstabType());
    address.setLatitude(caze.getLatitude());
    address.setLongitude(caze.getLongitude());
    address.setPostcode(caze.getPostcode());
    address.setTownName(caze.getTownName());
    address.setApbCode(caze.getAbpCode());
    address.setOrganisationName(caze.getOrganisationName());
    address.setUprn(caze.getUprn());
    address.setAddressLevel(caze.getAddressLevel());

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
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey(SURVEY);
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
    collectionCase.setReceiptReceived(caze.isReceiptReceived());
    collectionCase.setRefusalReceived(caze.isRefusalReceived());
    collectionCase.setAddressInvalid(caze.isAddressInvalid());
    collectionCase.setUndeliveredAsAddressed(caze.isUndeliveredAsAddressed());
    // Yes. You can add stuff to the bottom of this list if you like.

    return collectionCase;
  }

  public Case prepareIndividualResponseCaseFromParentCase(Case parentCase) {
    Case individualResponseCase = new Case();

    individualResponseCase.setCaseId(UUID.randomUUID());
    individualResponseCase.setCaseRef(getUniqueCaseRef());
    individualResponseCase.setState(CaseState.ACTIONABLE);
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
    individualResponseCase.setTownName(parentCase.getTownName());
    individualResponseCase.setPostcode(parentCase.getPostcode());
    individualResponseCase.setLatitude(parentCase.getLatitude());
    individualResponseCase.setLongitude(parentCase.getLongitude());
    individualResponseCase.setOa(parentCase.getOa());
    individualResponseCase.setLsoa(parentCase.getLsoa());
    individualResponseCase.setMsoa(parentCase.getMsoa());
    individualResponseCase.setLad(parentCase.getLad());
    individualResponseCase.setRegion(parentCase.getRegion());

    return individualResponseCase;
  }

  public Case getCaseByCaseId(UUID caseId) {
    Optional<Case> cazeResult = caseRepository.findByCaseId(caseId);

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case ID '%s' not present", caseId));
    }
    return cazeResult.get();
  }

  public Case getCaseByCaseRef(int caseRef) {
    Optional<Case> caseOptional = caseRepository.findById(caseRef);

    if (caseOptional.isEmpty()) {
      throw new RuntimeException(String.format("Case ref '%s' not present", caseRef));
    }

    return caseOptional.get();
  }
}
