package uk.gov.ons.census.casesvc.messaging;

import java.util.Map;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.service.RefusalProcessor;

@MessageEndpoint
public class RefusalReceiver {
  private final RefusalProcessor refusalProcessor;

  public RefusalReceiver(RefusalProcessor refusalProcessor) {
    this.refusalProcessor = refusalProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "refusalInputChannel")
  public void refusalMessage(RefusalDTO refusal, @Headers Map<String, String> headers) {
    refusalProcessor.processRefusal(refusal, headers);
  }
}
