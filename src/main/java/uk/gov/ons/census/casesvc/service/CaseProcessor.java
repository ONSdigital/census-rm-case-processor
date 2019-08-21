package uk.gov.ons.census.casesvc.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.RandomCaseRefGenerator;

@Component
public class CaseProcessor {
  private static final Logger log = LoggerFactory.getLogger(CaseProcessor.class);
  private static final String CASE_NOT_FOUND_ERROR = "Case not found error";
  private static final String SURVEY = "CENSUS";
  static final String CASE_UPDATE_ROUTING_KEY = "event.case.update";

  private final CaseRepository caseRepository;
  private final MapperFacade mapperFacade;
  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  public CaseProcessor(
      CaseRepository caseRepository, RabbitTemplate rabbitTemplate, MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.mapperFacade = mapperFacade;
  }

  public Case saveCase(CreateCaseSample createCaseSample) {
    int caseRef = getUniqueCaseRef();

    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseRef(caseRef);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze.setCreatedDateTime(OffsetDateTime.now());
    caze.setReceiptReceived(false);
    caze = caseRepository.saveAndFlush(caze);
    return caze;
  }

  public int getUniqueCaseRef() {
    int caseRef = RandomCaseRefGenerator.getCaseRef();

    // Check for collisions
    if (caseRepository.existsById(caseRef)) {
      throw new RuntimeException();
    }
    return caseRef;
  }

  public Optional<Case> findCase(int caseRef) {
    return caseRepository.findById(caseRef);
  }

  public PayloadDTO emitCaseCreatedEvent(Case caze) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventType.CASE_CREATED);
    ResponseManagementEvent responseManagementEvent = prepareCaseEvent(caze, eventDTO);
    rabbitTemplate.convertAndSend(
        outboundExchange, CASE_UPDATE_ROUTING_KEY, responseManagementEvent);
    return responseManagementEvent.getPayload();
  }

  public void emitCaseUpdatedEvent(Case caze) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventType.CASE_UPDATED);
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
    collectionCase.setActionableFrom(OffsetDateTime.now());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef(Long.toString(caze.getCaseRef()));
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getCaseId().toString());
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey(SURVEY);
    collectionCase.setReceiptReceived(caze.isReceiptReceived());
    collectionCase.setRefusalReceived(caze.isRefusalReceived());

    // Below this line is extra data potentially needed by Action Scheduler - can be ignored by RM
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

    return collectionCase;
  }

  public Case getCaseByCaseId(UUID caseId) {
    Optional<Case> cazeResult = caseRepository.findByCaseId(caseId);

    if (cazeResult.isEmpty()) {
      log.error(CASE_NOT_FOUND_ERROR);
      throw new RuntimeException(String.format("Case ID '%s' not present", caseId));
    }
    return cazeResult.get();
  }
}
