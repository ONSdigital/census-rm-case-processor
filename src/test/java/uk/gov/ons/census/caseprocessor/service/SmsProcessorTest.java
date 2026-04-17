package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.common.model.entity.ActionRule;
import uk.gov.ons.census.common.model.entity.Case;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.SmsTemplate;

@ExtendWith(MockitoExtension.class)
class SmsProcessorTest {
  @Mock private MessageSender messageSender;

  @Mock private EventLogger eventLogger;

  @InjectMocks private SmsProcessor underTest;

  @Test
  void testProcess() {
    ReflectionTestUtils.setField(underTest, "smsRequestTopic", "Test topic");

    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setSampleSensitive(Map.of("superSecretPhoneNumber", "0987654321"));

    SmsTemplate smsTemplate = new SmsTemplate();
    smsTemplate.setPackCode("Test pack code");

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setCreatedBy("foo@bar.com");
    actionRule.setSmsTemplate(smsTemplate);
    actionRule.setPhoneNumberColumn("superSecretPhoneNumber");

    underTest.process(caze, actionRule);

    ArgumentCaptor<EventDTO> eventArgCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(messageSender).sendMessage(eq("Test topic"), eventArgCaptor.capture());

    EventDTO actualEvent = eventArgCaptor.getValue();
    assertThat(actualEvent.getHeader().getTopic()).isEqualTo("Test topic");
    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(actionRule.getId());
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(actionRule.getCreatedBy());

    assertThat(actualEvent.getPayload().getSmsRequest().getCaseId()).isEqualTo(caze.getId());
    assertThat(actualEvent.getPayload().getSmsRequest().getPackCode()).isEqualTo("Test pack code");
    assertThat(actualEvent.getPayload().getSmsRequest().getPhoneNumber()).isEqualTo("0987654321");

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("SMS requested by action rule for pack code Test pack code"),
            eq(EventType.ACTION_RULE_SMS_REQUEST),
            any(),
            any(OffsetDateTime.class));
  }
}
