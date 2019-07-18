package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestRefusal;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.Refusal;
import uk.gov.ons.census.casesvc.service.RefusalProcessor;

@RunWith(MockitoJUnitRunner.class)
public class RefusalReceiverTest {

  @Mock private RefusalProcessor refusalProcessor;

  @InjectMocks RefusalReceiver underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    Map<String, String> headers = new HashMap<>();
    headers.put("channel", "any receipt channel");
    headers.put("source", "any receipt source");

    Refusal testRefusal = getTestRefusal();
    String expectedCaseId = testRefusal.getCollectionCase().getId();

    doNothing().when(refusalProcessor).processRefusal(any(Refusal.class), any(Map.class));

    // WHEN
    underTest.refusalMessage(testRefusal, headers);

    // THEN
    ArgumentCaptor<Refusal> refusalArgumentCaptor = ArgumentCaptor.forClass(Refusal.class);
    ArgumentCaptor<Map> headersArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(refusalProcessor)
        .processRefusal(refusalArgumentCaptor.capture(), headersArgumentCaptor.capture());

    Refusal actualRefusal = refusalArgumentCaptor.getValue();
    assertThat(actualRefusal.getCollectionCase().getId()).isEqualTo(expectedCaseId);

    Map<String, String> actualHeaders = headersArgumentCaptor.getValue();
    assertThat(actualHeaders.containsKey("source")).isTrue();
    assertThat(actualHeaders.containsKey("channel")).isTrue();
  }
}
