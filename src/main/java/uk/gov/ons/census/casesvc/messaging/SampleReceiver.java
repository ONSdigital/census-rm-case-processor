package uk.gov.ons.census.casesvc.messaging;

import java.time.LocalDateTime;
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
import uk.gov.ons.census.casesvc.model.entity.CaseStatus;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@MessageEndpoint
public class SampleReceiver {
  private CaseRepository caseRepository;
  private RabbitTemplate rabbitTemplate;
  private IacDispenser iacDispenser;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public SampleReceiver(
      CaseRepository caseRepository, RabbitTemplate rabbitTemplate, IacDispenser iacDispenser) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
  }

  @Transactional
  @ServiceActivator(inputChannel = "amqpInputChannel")
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
    caze.setLad18cd(createCaseSample.getLad18cd());
    caze.setLatitude(createCaseSample.getLatitude());
    caze.setLongitude(createCaseSample.getLongitude());
    caze.setLsoa11cd(createCaseSample.getLsoa11cd());
    caze.setMsoa11cd(createCaseSample.getMsoa11cd());
    caze.setOa11cd(createCaseSample.getOa11cd());
    caze.setOrganisationName(createCaseSample.getOrganisationName());
    caze.setPostcode(createCaseSample.getPostcode());
    caze.setRgn10cd(createCaseSample.getRgn10cd());
    caze.setTownName(createCaseSample.getTownName());
    caze.setTreatmentCode(createCaseSample.getTreatmentCode());
    caze.setUprn(createCaseSample.getUprn());
    caze.setStatus(CaseStatus.NOTSTARTED);
    caze.setUacCode(iacDispenser.getIacCode());
    caseRepository.save(caze);

    LocalDateTime now = LocalDateTime.now();

    Event event = new Event();
    event.setChannel("rm");
    event.setDateTime(now.toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType("CaseCreated");
    Address address = new Address();
    address.setAddressLine1(createCaseSample.getAddressLine1());
    address.setAddressLine2(createCaseSample.getAddressLine2());
    address.setAddressLine3(createCaseSample.getAddressLine3());
    address.setAddressType(createCaseSample.getAddressType());
    address.setArid(createCaseSample.getArid());
    address.setCountry("E");
    address.setEstabType(createCaseSample.getEstabType());
    address.setLatitude(createCaseSample.getLatitude());
    address.setLongitude(createCaseSample.getLongitude());
    address.setPostcode(createCaseSample.getPostcode());
    address.setTownName(createCaseSample.getTownName());
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setActionableFrom(now.toString());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef("10000000010");
    collectionCase.setCollectionExerciseId(UUID.randomUUID().toString());
    collectionCase.setId(UUID.randomUUID().toString());
    collectionCase.setSampleUnitRef("");
    collectionCase.setState("actionable");
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
