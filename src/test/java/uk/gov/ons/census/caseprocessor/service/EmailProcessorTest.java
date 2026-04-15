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
import uk.gov.ons.ssdc.common.model.entity.ActionRule;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.EmailTemplate;
import uk.gov.ons.ssdc.common.model.entity.EventType;

@ExtendWith(MockitoExtension.class)
class EmailProcessorTest {
  @Mock private MessageSender messageSender;

  @Mock private EventLogger eventLogger;

  @InjectMocks private EmailProcessor underTest;

  @Test
  void testProcess() {
    ReflectionTestUtils.setField(underTest, "emailRequestTopic", "Test topic");

    Case caze = new Case();
    caze.setId(UUID.randomUUID());
    caze.setSampleSensitive(Map.of("superSecretEmailAddress", "secret@top.secret"));

    EmailTemplate emailTemplate = new EmailTemplate();
    emailTemplate.setPackCode("Test pack code");

    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setCreatedBy("foo@bar.com");
    actionRule.setEmailTemplate(emailTemplate);
    actionRule.setEmailColumn("superSecretEmailAddress");

    underTest.process(caze, actionRule);

    ArgumentCaptor<EventDTO> eventArgCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(messageSender).sendMessage(eq("Test topic"), eventArgCaptor.capture());

    EventDTO actualEvent = eventArgCaptor.getValue();
    assertThat(actualEvent.getHeader().getTopic()).isEqualTo("Test topic");
    assertThat(actualEvent.getHeader().getCorrelationId()).isEqualTo(actionRule.getId());
    assertThat(actualEvent.getHeader().getOriginatingUser()).isEqualTo(actionRule.getCreatedBy());

    assertThat(actualEvent.getPayload().getEmailRequest().getCaseId()).isEqualTo(caze.getId());
    assertThat(actualEvent.getPayload().getEmailRequest().getPackCode())
        .isEqualTo("Test pack code");
    assertThat(actualEvent.getPayload().getEmailRequest().getEmail())
        .isEqualTo("secret@top.secret");

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            eq("Email requested by action rule for pack code Test pack code"),
            eq(EventType.ACTION_RULE_EMAIL_REQUEST),
            any(),
            any(OffsetDateTime.class));
  }
}
