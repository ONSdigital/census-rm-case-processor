package uk.gov.ons.census.casesvc.messaging;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.EventType;
import uk.gov.ons.census.casesvc.model.dto.FanoutEvent;
import uk.gov.ons.census.casesvc.model.dto.Payload;
import uk.gov.ons.census.casesvc.model.dto.Uac;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.IacDispenser;
import uk.gov.ons.census.casesvc.utility.QidCreator;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@MessageEndpoint
public class SampleReceiver {
  private static final Logger log = LoggerFactory.getLogger(SampleReceiver.class);

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String SURVEY = "CENSUS";
  private static final String EVENT_CHANNEL = "RM";
  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case created";
  private static final String UAC_QID_LINKED_EVENT_DESCRIPTION = "UAC QID linked";

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

    emitCaseCreatedEvent(caze);
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

    caze.setUacQidLinks(Collections.singletonList(uacQidLink));

    String qid =
        qidCreator.createQid(createCaseSample.getTreatmentCode(), uacQidLink.getUniqueNumber());
    uacQidLink.setQid(qid);
    uacQidLinkRepository.save(uacQidLink);

    emitUacUpdatedEvent(uacQidLink, caze);

    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription(CASE_CREATED_EVENT_DESCRIPTION);
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);

    loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription(UAC_QID_LINKED_EVENT_DESCRIPTION);
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);

    return caze;
  }

  private void emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    LocalDateTime now = LocalDateTime.now();

    Event event = new Event();
    event.setChannel(EVENT_CHANNEL);
    event.setSource(EVENT_SOURCE);
    event.setDateTime(now.toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType(EventType.UAC_UPDATED);

    Uac uac = new Uac();
    uac.setActive(true);
    uac.setCaseId(caze.getCaseId().toString());
    uac.setCaseType("H"); // TODO: Fix this
    uac.setCollectionExerciseId(caze.getCollectionExerciseId());
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uac.getUac()));
    uac.setUac(uac.getUac());

    Payload payload = new Payload();
    payload.setUac(uac);
    FanoutEvent fanoutEvent = new FanoutEvent();
    fanoutEvent.setEvent(event);
    fanoutEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", fanoutEvent);
  }

  private void emitCaseCreatedEvent(Case caze) {
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
    FanoutEvent fanoutEvent = new FanoutEvent();
    fanoutEvent.setEvent(event);
    fanoutEvent.setPayload(payload);

    // OTHER STUFF WE NEED
    collectionCase.setActionPlanId(caze.getActionPlanId());
//    collectionCase.setUac(caze.getUacQidLinks().get(0).getUac());
    collectionCase.setTreatmentCode(caze.getTreatmentCode());

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", fanoutEvent);
  }
}
