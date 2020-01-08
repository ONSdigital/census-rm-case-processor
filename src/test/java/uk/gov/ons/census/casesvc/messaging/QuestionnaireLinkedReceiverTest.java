package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QuestionnaireLinkedService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class QuestionnaireLinkedReceiverTest {

  @Test
  public void testQuestionnaireLinking() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    QuestionnaireLinkedService questionnaireLinkedService = mock(QuestionnaireLinkedService.class);
    OffsetDateTime expectedEventDateTime = managementEvent.getEvent().getDateTime();

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    QuestionnaireLinkedReceiver questionnaireLinkedReceiver =
        new QuestionnaireLinkedReceiver(questionnaireLinkedService);
    questionnaireLinkedReceiver.receiveMessage(message);

    ArgumentCaptor<ResponseManagementEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(questionnaireLinkedService).processQuestionnaireLinked(eventArgumentCaptor.capture(), eq(expectedDate));

    OffsetDateTime actualEventDateTime = eventArgumentCaptor.getValue().getEvent().getDateTime();
    assertThat(actualEventDateTime).isEqualTo(expectedEventDateTime);
  }
}
