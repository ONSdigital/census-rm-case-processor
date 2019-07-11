package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventProcessor;

@MessageEndpoint
public class SampleReceiver {

  private final EventProcessor eventProcessor;

  public SampleReceiver(EventProcessor eventProcessor) {
    this.eventProcessor = eventProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) throws JsonProcessingException {
    eventProcessor.processSampleReceivedMessage(createCaseSample);
  }
}
