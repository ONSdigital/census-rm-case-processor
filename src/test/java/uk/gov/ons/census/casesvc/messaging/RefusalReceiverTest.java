package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RefusalProcessor;

@RunWith(MockitoJUnitRunner.class)
public class RefusalReceiverTest {

  @Mock private RefusalProcessor refusalProcessor;

  @InjectMocks RefusalReceiver underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    String expectedCaseId = managementEvent.getPayload().getCollectionCase().getId();

    // WHEN
    underTest.receiveMessage(managementEvent);

    // THEN
    ArgumentCaptor<ResponseManagementEvent> managementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(refusalProcessor).processRefusal(managementEventArgumentCaptor.capture());

    String actualCaseId =
        managementEventArgumentCaptor.getValue().getPayload().getCollectionCase().getId();
    assertThat(actualCaseId).isEqualTo(expectedCaseId);
  }
}
