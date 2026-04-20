package uk.gov.ons.census.caseprocessor.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.EmailRequest;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.utils.EventHelper;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;

@Component
public class EmailProcessor {
  private final MessageSender messageSender;
  private final EventLogger eventLogger;

  @Value("${queueconfig.email-request-topic}")
  private String emailRequestTopic;

  public EmailProcessor(MessageSender messageSender, EventLogger eventLogger) {
    this.messageSender = messageSender;
    this.eventLogger = eventLogger;
  }

  public void process(Case caze, ActionRule actionRule) {
    UUID caseId = caze.getId();
    String packCode = actionRule.getEmailTemplate().getPackCode();
    String email = caze.getSampleSensitive().get(actionRule.getEmailColumn());

    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(caseId);
    emailRequest.setPackCode(packCode);
    emailRequest.setEmail(email);
    emailRequest.setUacMetadata(actionRule.getUacMetadata());
    emailRequest.setScheduled(true);

    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO(
            emailRequestTopic, actionRule.getId(), actionRule.getCreatedBy());

    EventDTO event = new EventDTO();
    PayloadDTO payload = new PayloadDTO();
    event.setHeader(eventHeader);
    event.setPayload(payload);
    payload.setEmailRequest(emailRequest);

    messageSender.sendMessage(emailRequestTopic, event);

    eventLogger.logCaseEvent(
        caze,
        String.format("Email requested by action rule for pack code %s", packCode),
        EventType.ACTION_RULE_EMAIL_REQUEST,
        event,
        OffsetDateTime.now());
  }
}
