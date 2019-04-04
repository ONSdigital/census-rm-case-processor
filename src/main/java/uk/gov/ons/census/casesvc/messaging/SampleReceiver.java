package uk.gov.ons.census.casesvc.messaging;

import java.util.Random;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.FooBar;
import uk.gov.ons.census.casesvc.model.dto.Payload;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@MessageEndpoint
public class SampleReceiver {
  private CaseRepository caseRepository;
  private RabbitTemplate rabbitTemplate;

  public SampleReceiver(CaseRepository caseRepository, RabbitTemplate rabbitTemplate) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
  }

  @Transactional
  @ServiceActivator(inputChannel = "amqpInputChannel")
  public void receiveMessage(FooBar fooBar) {
    System.out.println(fooBar.getFoo());
    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setStuff(fooBar.getFoo());
    caseRepository.save(caze);

    Event event = new Event();
    event.setChannel("rm");
    event.setDateTime("2019-04-01T12:00Z");
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType("CaseCreated");
    Address address = new Address();
    address.setAddressLine1("1 Main Street");
    address.setAddressLine2("Upper Upperingham");
    address.setAddressLine2("Lower Lowerington");
    address.setAddressType("CE");
    address.setArid("XXXXX");
    address.setCountry("E");
    address.setEstabType("XXX");
    address.setLatitude("50.863849");
    address.setLongitude("-1.229710");
    address.setPostcode("UP103UP");
    address.setTownName("Royal Midtowncastlecesteringshirehampton-upon-Twiddlebottom");
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setActionableFrom("2019-04-01T12:00Z");
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

    rabbitTemplate.convertAndSend("myfanout.exchange", "", caseCreatedEvent);

    // Enable the code below to prove that the DB txn and the Rabbit txn are part of the same txn
//    Random random = new Random();
//    int randomNumber = random.nextInt(1000);
//    if (randomNumber > 2) {
//      throw new RuntimeException();
//    }
  }
}
