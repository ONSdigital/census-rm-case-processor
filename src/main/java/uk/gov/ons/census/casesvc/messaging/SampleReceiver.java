package uk.gov.ons.census.casesvc.messaging;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
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

@MessageEndpoint
public class SampleReceiver {
  private CaseRepository caseRepository;
  private UacQidLinkRepository uacQidLinkRepository;
  private EventRepository eventRepository;
  private RabbitTemplate rabbitTemplate;
  private IacDispenser iacDispenser;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public SampleReceiver(
      CaseRepository caseRepository,
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      IacDispenser iacDispenser) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) {
    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setAbpCode(createCaseSample.getAbpCode());
    caze.setActionPlanId(createCaseSample.getActionPlanId());
    caze.setAddressLevel(createCaseSample.getAddressLevel());
    caze.setAddressLine1(createCaseSample.getAddressLine1());
    caze.setAddressLine2(createCaseSample.getAddressLine1());
    caze.setAddressLine2(createCaseSample.getAddressLine1());
    caze.setAddressType(createCaseSample.getAddressType());
    caze.setArid(createCaseSample.getArid());
    caze.setCollectionExerciseId(createCaseSample.getCollectionExerciseId());
    caze.setEstabArid(createCaseSample.getEstabArid());
    caze.setEstabType(createCaseSample.getEstabType());
    caze.setHtcDigital(createCaseSample.getHtcDigital());
    caze.setHtcWillingness(createCaseSample.getHtcWillingness());
    caze.setLad(createCaseSample.getLad());
    caze.setLatitude(createCaseSample.getLatitude());
    caze.setLongitude(createCaseSample.getLongitude());
    caze.setLsoa(createCaseSample.getLsoa());
    caze.setMsoa(createCaseSample.getMsoa());
    caze.setOa(createCaseSample.getOa());
    caze.setOrganisationName(createCaseSample.getOrganisationName());
    caze.setPostcode(createCaseSample.getPostcode());
    caze.setRgn(createCaseSample.getRgn());
    caze.setTownName(createCaseSample.getTownName());
    caze.setTreatmentCode(createCaseSample.getTreatmentCode());
    caze.setUprn(createCaseSample.getUprn());
    caze.setState(CaseState.ACTIONABLE);
    caseRepository.save(caze);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(iacDispenser.getIacCode());
    uacQidLink.setQid(666L);
    uacQidLink.setCaze(caze);
    uacQidLinkRepository.save(uacQidLink);

    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription("Case created");
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);

    LocalDateTime now = LocalDateTime.now();

    Event event = new Event();
    event.setChannel("rm");
    event.setSource("CaseService");
    event.setDateTime(now.toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType("CaseCreated");
    Address address = new Address();
    address.setAddressLine1(createCaseSample.getAddressLine1());
    address.setAddressLine2(createCaseSample.getAddressLine2());
    address.setAddressLine3(createCaseSample.getAddressLine3());
    address.setAddressType(createCaseSample.getAddressType());
    address.setArid(createCaseSample.getArid());
    address.setCountry(createCaseSample.getRgn().substring(0, 1));
    address.setEstabType(createCaseSample.getEstabType());
    address.setLatitude(createCaseSample.getLatitude());
    address.setLongitude(createCaseSample.getLongitude());
    address.setPostcode(createCaseSample.getPostcode());
    address.setTownName(createCaseSample.getTownName());
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setActionableFrom(now.toString());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef("10000000010");
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getId().toString());
    collectionCase.setSampleUnitRef("");
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey("Census");
    Payload payload = new Payload();
    payload.setCollectionCase(collectionCase);
    CaseCreatedEvent caseCreatedEvent = new CaseCreatedEvent();
    caseCreatedEvent.setEvent(event);
    caseCreatedEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", caseCreatedEvent);

    // Enable the code below to prove that the DB txn and the Rabbit txn are part of the same txn
    //    Random random = new Random();
    //    int randomNumber = random.nextInt(1000);
    //    if (randomNumber > 2) {
    //      throw new RuntimeException();
    //    }
  }
}
