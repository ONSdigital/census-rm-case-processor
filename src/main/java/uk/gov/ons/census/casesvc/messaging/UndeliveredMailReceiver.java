package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;
import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.service.UacService;

@MessageEndpoint
public class UndeliveredMailReceiver {
  private static final String LOG_EVENT_DESCRIPTION = "Undelivered mail reported";
  private final UacService uacService;
  private final CaseService caseService;
  private final EventLogger eventLogger;

  public UndeliveredMailReceiver(
      UacService uacService, CaseService caseService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.caseService = caseService;
    this.eventLogger = eventLogger;
  }

  @Transactional
  @ServiceActivator(inputChannel = "undeliveredMailInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    ResponseManagementEvent event = message.getPayload();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    String questionnaireId = event.getPayload().getFulfilmentInformation().getQuestionnaireId();

    Case caze;
    UacQidLink uacQidLink = null;

    if (!StringUtils.isEmpty(questionnaireId)) {
      uacQidLink = uacService.findByQid(questionnaireId);
      caze = uacQidLink.getCaze();
    } else {
      caze =
          caseService.getCaseByCaseRef(
              Long.parseLong(event.getPayload().getFulfilmentInformation().getCaseRef()));
    }

    if (shouldEmitCaseUpdated(caze)) {
      caseService.saveCaseAndEmitCaseUpdatedEvent(
          caze, buildMetadata(event.getEvent().getType(), ActionInstructionType.UPDATE));
    }

    if (uacQidLink != null) {
      eventLogger.logUacQidEvent(
          uacQidLink,
          event.getEvent().getDateTime(),
          LOG_EVENT_DESCRIPTION,
          EventType.UNDELIVERED_MAIL_REPORTED,
          event.getEvent(),
          convertObjectToJson(event.getPayload().getFulfilmentInformation()),
          messageTimestamp);
    } else {
      eventLogger.logCaseEvent(
          caze,
          event.getEvent().getDateTime(),
          LOG_EVENT_DESCRIPTION,
          EventType.UNDELIVERED_MAIL_REPORTED,
          event.getEvent(),
          convertObjectToJson(event.getPayload().getFulfilmentInformation()),
          messageTimestamp);
    }
  }

  private boolean shouldEmitCaseUpdated(Case caze) {
    return StringUtils.isEmpty(caze.getRefusalReceived())
        && !caze.isAddressInvalid()
        && !caze.isReceiptReceived()
        && !(caze.getRegion().startsWith("N") && caze.getCaseType().equals("CE"));
  }
}
