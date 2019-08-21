package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventService;

@MessageEndpoint
public class SampleReceiver {

  private final EventService eventService;

  public SampleReceiver(EventService eventService) {
    this.eventService = eventService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) {
    eventService.processSampleReceivedMessage(createCaseSample);
  }
}
