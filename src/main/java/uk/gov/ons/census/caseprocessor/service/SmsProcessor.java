package uk.gov.ons.census.caseprocessor.service;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.SmsRequest;
import uk.gov.ons.census.caseprocessor.utils.EventHelper;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;

@Component
public class SmsProcessor {
  private final MessageSender messageSender;
  private final EventLogger eventLogger;

  @Value("${queueconfig.sms-request-topic}")
  private String smsRequestTopic;

  public SmsProcessor(MessageSender messageSender, EventLogger eventLogger) {
    this.messageSender = messageSender;
    this.eventLogger = eventLogger;
  }

  public void process(Case caze, ActionRule actionRule) {
    UUID caseId = caze.getId();
    String packCode = actionRule.getSmsTemplate().getPackCode();
    String phoneNumber = caze.getSampleSensitive().get(actionRule.getPhoneNumberColumn());

    SmsRequest smsRequest = new SmsRequest();
    smsRequest.setCaseId(caseId);
    smsRequest.setPackCode(packCode);
    smsRequest.setPhoneNumber(phoneNumber);
    smsRequest.setUacMetadata(actionRule.getUacMetadata());
    smsRequest.setScheduled(true);

    EventHeaderDTO eventHeader =
        EventHelper.createEventDTO(smsRequestTopic, actionRule.getId(), actionRule.getCreatedBy());

    EventDTO event = new EventDTO();
    PayloadDTO payload = new PayloadDTO();
    event.setHeader(eventHeader);
    event.setPayload(payload);
    payload.setSmsRequest(smsRequest);

    messageSender.sendMessage(smsRequestTopic, event);

    eventLogger.logCaseEvent(
        caze,
        String.format("SMS requested by action rule for pack code %s", packCode),
        EventType.ACTION_RULE_SMS_REQUEST,
        event,
        OffsetDateTime.now());
  }
}
