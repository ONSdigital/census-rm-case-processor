package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class RefusalServiceTest {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock
  private CaseService caseService;

  @Mock
  private EventLogger eventLogger;

  @InjectMocks
  RefusalService underTest;

  @Test
  public void testRefusalForCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    collectionCase.setRefusalReceived(false);
    Case testCase = getRandomCase();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN

    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    verifyNoMoreInteractions(caseService);

    assertThat(actualCase.isRefusalReceived()).isTrue();
    inOrder
        .verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test(expected = RuntimeException.class)
  public void testRefusalNotFromFieldForEstabAddressLevelCaseThrowsException() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    managementEvent.getEvent().setChannel("NOT FROM FIELD");
    managementEvent.getPayload().getRefusal().getCollectionCase().setId(TEST_CASE_ID.toString());

    Case testCase = getRandomCase();
    testCase.setAddressLevel("E");
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);
    String expectedErrorMessage = String.format(
        "Refusal received for Estab level case ID '%s' from channel '%s'. "
            + "This type of refusal should ONLY come from Field",
        testCase.getCaseId(), managementEvent.getEvent().getChannel());

    // WHEN
    try {
      underTest.processRefusal(managementEvent, messageTimestamp);
    } catch (RuntimeException expectedException) {
      // THEN
      assertThat(expectedException.getMessage()).isEqualTo(expectedErrorMessage);

      verify(caseService, times(1)).getCaseByCaseId(any(UUID.class));
      verifyNoMoreInteractions(caseService);
      verifyZeroInteractions(eventLogger);

      throw expectedException;
    }
  }
}
