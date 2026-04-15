package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SmsConfirmation;
import uk.gov.ons.census.caseprocessor.service.CaseService;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EventType;
import uk.gov.ons.ssdc.common.model.entity.UacQidLink;

@MessageEndpoint
public class SmsFulfilmentReceiver {

  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  private static final String SMS_FULFILMENT_DESCRIPTION = "SMS fulfilment request received";
  private static final String SCHEDULED_SMS_DESCRIPTION = "Scheduled SMS request received";

  public SmsFulfilmentReceiver(
      UacService uacService, CaseService caseService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "smsConfirmationInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());
    SmsConfirmation smsFulfilment = event.getPayload().getSmsConfirmation();

    Case caze = caseService.getCase(smsFulfilment.getCaseId());

    if (smsFulfilment.getQid() != null) {
      // Check the QID does not already exist
      if (uacService.existsByQid(smsFulfilment.getQid())) {

        // If it does exist, check if it is linked to the given case
        UacQidLink existingUacQidLink = uacService.findByQid(smsFulfilment.getQid());
        if (existingUacQidLink.getCaze().getId().equals(smsFulfilment.getCaseId())) {

          // If the QID is already linked to the given case this must be duplicate event, ignore
          return;
        }

        // If not then something has gone wrong, error out
        throw new RuntimeException(
            "SMS fulfilment QID "
                + smsFulfilment.getQid()
                + " is already linked to a different case");
      }
      uacService.createLinkAndEmitNewUacQid(
          caze,
          smsFulfilment.getUac(),
          smsFulfilment.getQid(),
          smsFulfilment.getUacMetadata(),
          event.getHeader().getCorrelationId(),
          event.getHeader().getOriginatingUser());
    }

    if (smsFulfilment.isScheduled()) {
      eventLogger.logCaseEvent(
          caze, SCHEDULED_SMS_DESCRIPTION, EventType.ACTION_RULE_SMS_CONFIRMATION, event, message);
    } else {
      eventLogger.logCaseEvent(
          caze, SMS_FULFILMENT_DESCRIPTION, EventType.SMS_FULFILMENT, event, message);
    }
  }
}
