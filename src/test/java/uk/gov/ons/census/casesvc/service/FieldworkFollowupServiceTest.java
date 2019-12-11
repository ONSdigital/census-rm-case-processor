package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.FieldWorkFollowup;
import uk.gov.ons.census.casesvc.model.entity.Case;

@RunWith(MockitoJUnitRunner.class)
public class FieldworkFollowupServiceTest {
  private static final String TEST_EXCHANGE = "TEST_EXCHANGE";
  private static final String TEST_BINDING = "TEST_BINDING";

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks FieldworkFollowupService underTest;

  @Test
  public void testBuildAndSendFieldworkFollowup() {
    // Given
    Case expectedCase = getRandomCase();

    ReflectionTestUtils.setField(underTest, "actionFieldBinding", TEST_BINDING);
    ReflectionTestUtils.setField(underTest, "outboundExchange", TEST_EXCHANGE);

    // When
    underTest.buildAndSendFieldWorkFollowUp(expectedCase);

    // Then
    ArgumentCaptor<FieldWorkFollowup> rmeArgumentCaptor =
        ArgumentCaptor.forClass(FieldWorkFollowup.class);
    verify(rabbitTemplate)
        .convertAndSend(eq(TEST_EXCHANGE), eq(TEST_BINDING), rmeArgumentCaptor.capture());

    FieldWorkFollowup fieldWorkFollowup = rmeArgumentCaptor.getValue();

    assertThat(fieldWorkFollowup.getCaseId()).isEqualTo(expectedCase.getCaseId().toString());
  }
}
