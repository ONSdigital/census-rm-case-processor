package uk.gov.ons.census.casesvc.service;

import java.time.LocalDateTime;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;

@Component
public class CaseProcessor {

  private static final String SURVEY = "CENSUS";

  private final CaseRepository caseRepository;
  private final MapperFacade mapperFacade;
  private final RabbitTemplate rabbitTemplate;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public CaseProcessor(
      CaseRepository caseRepository, RabbitTemplate rabbitTemplate, MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.mapperFacade = mapperFacade;
  }

  public Case saveCase(CreateCaseSample createCaseSample) {
    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze = caseRepository.saveAndFlush(caze);
    return caze;
  }

  public void emitCaseCreatedEvent(Case caze) {
    Event event = EventHelper.createEvent(EventType.CASE_CREATED);
    Address address = createAddress(caze);
    CollectionCase collectionCase = createCollectionCase(caze, address);
    Payload payload = new Payload();
    payload.setCollectionCase(collectionCase);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", responseManagementEvent);
  }


  private Address createAddress(Case caze) {
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setEstabArid(caze.getEstabArid());
    address.setRegion(caze.getRgn().substring(0, 1));
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
    collectionCase.setActionableFrom(LocalDateTime.now().toString());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef(Long.toString(caze.getCaseRef()));
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getCaseId().toString());
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey(SURVEY);

    // Below this line is extra data potentially needed by Action Scheduler - can be ignored by RM
    collectionCase.setActionPlanId(caze.getActionPlanId());
    collectionCase.setTreatmentCode(caze.getTreatmentCode());
    collectionCase.setOa(caze.getOa());
    collectionCase.setLsoa(caze.getLsoa());
    collectionCase.setMsoa(caze.getMsoa());
    collectionCase.setLad(caze.getLad());
    collectionCase.setHtcWillingness(caze.getHtcWillingness());
    collectionCase.setHtcDigital(caze.getHtcDigital());

    return collectionCase;
  }
}
