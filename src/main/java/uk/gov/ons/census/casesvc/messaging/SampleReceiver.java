package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
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
  public void receiveMessage(Message<CreateCaseSample> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    eventService.processSampleReceivedMessage(message.getPayload(), messageTimestamp);
  }
}
