package uk.gov.ons.census.caseprocessor.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.messaging.MessageSender;
import uk.gov.ons.census.caseprocessor.model.dto.DeactivateUacDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class DeactivateUacProcessorTest {

  @Mock private MessageSender messageSender;

  @InjectMocks private DeactivateUacProcessor underTest;

  @Test
  public void testProcessDeactivateUacRow() {
    // Given
    ReflectionTestUtils.setField(underTest, "deactivateUacTopic", "testTopic");
    ReflectionTestUtils.setField(underTest, "pubsubProject", "Test project");

    Case caze = new Case();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid("0123456789");
    caze.setUacQidLinks(List.of(uacQidLink));

    // When
    underTest.process(caze, TEST_CORRELATION_ID);

    // Then
    ArgumentCaptor<EventDTO> eventArgumentCaptor = ArgumentCaptor.forClass(EventDTO.class);
    verify(messageSender)
        .sendMessage(eq("projects/Test project/topics/testTopic"), eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getHeader().getCorrelationId())
        .isEqualTo(TEST_CORRELATION_ID);

    DeactivateUacDTO actualDeactivateUac =
        eventArgumentCaptor.getValue().getPayload().getDeactivateUac();
    assertThat(actualDeactivateUac.getQid()).isEqualTo(uacQidLink.getQid());
  }
}
