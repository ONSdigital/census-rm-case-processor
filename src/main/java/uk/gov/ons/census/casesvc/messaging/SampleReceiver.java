package uk.gov.ons.census.casesvc.messaging;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.iac.IacDispenser;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.Payload;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.QidCreator;

@MessageEndpoint
public class SampleReceiver {
  private CaseRepository caseRepository;
  private UacQidLinkRepository uacQidLinkRepository;
  private EventRepository eventRepository;
  private RabbitTemplate rabbitTemplate;
  private IacDispenser iacDispenser;
  private QidCreator qidCreator;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public SampleReceiver(
      CaseRepository caseRepository,
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      IacDispenser iacDispenser,
      QidCreator qidCreator) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
    this.qidCreator = qidCreator;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) {

    Case caze = persistToDatabase(createCaseSample);

    CaseCreatedEvent caseCreatedEvent = prepareEventToEmit(caze);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", caseCreatedEvent);
  }

  private Case persistToDatabase(CreateCaseSample createCaseSample) {
    MapperFactory mapperFactory = new DefaultMapperFactory.Builder().build();

    MapperFacade mapperFacade = mapperFactory.getMapperFacade();

    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(iacDispenser.getIacCode());
    uacQidLink.setCaze(caze);
    uacQidLink = uacQidLinkRepository.saveAndFlush(uacQidLink);

    int questionnaireType = 99;
    int trancheIdentifier = 2;
    uacQidLink.setQid(
        qidCreator.createQid(questionnaireType, trancheIdentifier, uacQidLink.getUniqueNumber()));
    uacQidLinkRepository.save(uacQidLink);

    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription("Case created");
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);

    return caze;
  }

  private CaseCreatedEvent prepareEventToEmit(Case caze) {
    LocalDateTime now = LocalDateTime.now();

    Event event = new Event();
    event.setChannel("rm");
    event.setSource("CaseService");
    event.setDateTime(now.toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType("CaseCreated");
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setCountry(caze.getRgn().substring(0, 1));
    address.setEstabType(caze.getEstabType());
    address.setLatitude(caze.getLatitude());
    address.setLongitude(caze.getLongitude());
    address.setPostcode(caze.getPostcode());
    address.setTownName(caze.getTownName());
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setActionableFrom(now.toString());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef(Long.toString(caze.getCaseRef()));
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getCaseId().toString());
    collectionCase.setSampleUnitRef("");
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey("Census");
    Payload payload = new Payload();
    payload.setCollectionCase(collectionCase);
    CaseCreatedEvent caseCreatedEvent = new CaseCreatedEvent();
    caseCreatedEvent.setEvent(event);
    caseCreatedEvent.setPayload(payload);

    return caseCreatedEvent;
  }
}
