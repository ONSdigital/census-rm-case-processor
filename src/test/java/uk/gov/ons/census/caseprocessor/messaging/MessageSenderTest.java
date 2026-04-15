package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.model.dto.CaseUpdateDTO;
import uk.gov.ons.census.caseprocessor.model.repository.MessageToSendRepository;
import uk.gov.ons.census.caseprocessor.utils.JsonHelper;
import uk.gov.ons.ssdc.common.model.entity.MessageToSend;

@ExtendWith(MockitoExtension.class)
public class MessageSenderTest {

  @Mock MessageToSendRepository messageToSendRepository;

  @InjectMocks MessageSender underTest;

  @Test
  public void testSendMessage() {
    String destinationTopic = "test-topic";
    CaseUpdateDTO caseUpdate = new CaseUpdateDTO();
    caseUpdate.setCaseId(UUID.randomUUID());

    underTest.sendMessage(destinationTopic, caseUpdate);

    ArgumentCaptor<MessageToSend> messageToSendArgumentCaptor =
        ArgumentCaptor.forClass(MessageToSend.class);
    verify(messageToSendRepository).save(messageToSendArgumentCaptor.capture());

    MessageToSend actualMessageSent = messageToSendArgumentCaptor.getValue();
    assertThat(actualMessageSent.getDestinationTopic()).isEqualTo(destinationTopic);
    assertThat(actualMessageSent.getMessageBody())
        .isEqualTo(JsonHelper.convertObjectToJson(caseUpdate));
  }
}
