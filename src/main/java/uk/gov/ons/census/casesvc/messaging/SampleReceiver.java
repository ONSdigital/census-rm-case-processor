package uk.gov.ons.census.casesvc.messaging;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.EventType;
import uk.gov.ons.census.casesvc.model.dto.Payload;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.IacDispenser;
import uk.gov.ons.census.casesvc.utility.QidCreator;

@MessageEndpoint
public class SampleReceiver {
  private static final Logger log = LoggerFactory.getLogger(SampleReceiver.class);

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String SURVEY = "CENSUS";
  private static final String EVENT_CHANNEL = "RM";
  private static final String EVENT_DESCRIPTION = "Case created";

  private final CaseRepository caseRepository;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final EventRepository eventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final IacDispenser iacDispenser;
  private final QidCreator qidCreator;
  private final MapperFacade mapperFacade;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public SampleReceiver(
      CaseRepository caseRepository,
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      IacDispenser iacDispenser,
      QidCreator qidCreator,
      MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
    this.qidCreator = qidCreator;
    this.mapperFacade = mapperFacade;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) {

    Case caze = persistToDatabase(createCaseSample);

    CaseCreatedEvent caseCreatedEvent = prepareEventToEmit(caze);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", caseCreatedEvent);
  }

  private Case persistToDatabase(CreateCaseSample createCaseSample) {

    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze = caseRepository.saveAndFlush(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(iacDispenser.getIacCode());
    uacQidLink.setCaze(caze);
    uacQidLink = uacQidLinkRepository.saveAndFlush(uacQidLink);

    String qid =
        qidCreator.createQid(createCaseSample.getTreatmentCode(), uacQidLink.getUniqueNumber());
    uacQidLink.setQid(qid);
    uacQidLinkRepository.save(uacQidLink);

    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription(EVENT_DESCRIPTION);
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);

    return caze;
  }

  private CaseCreatedEvent prepareEventToEmit(Case caze) {
    LocalDateTime now = LocalDateTime.now();

    Event event = new Event();
    event.setChannel(EVENT_CHANNEL);
    event.setSource(EVENT_SOURCE);
    event.setDateTime(now.toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType(EventType.CASE_CREATED);
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setRegion(caze.getRgn().substring(0, 1));
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
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey(SURVEY);
    Payload payload = new Payload();
    payload.setCollectionCase(collectionCase);
    CaseCreatedEvent caseCreatedEvent = new CaseCreatedEvent();
    caseCreatedEvent.setEvent(event);
    caseCreatedEvent.setPayload(payload);

    return caseCreatedEvent;
  }
}
