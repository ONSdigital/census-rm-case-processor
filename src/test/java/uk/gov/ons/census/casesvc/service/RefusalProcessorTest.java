package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class RefusalProcessorTest {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private UacQidLinkRepository uacQidLinkRepository;

  @Mock private CaseRepository caseRepository;

  @Mock private CaseProcessor caseProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalProcessor underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    collectionCase.setRefusalReceived(false);
    Case testCase = getRandomCase();
    RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();

    when(caseRepository.findByCaseId(TEST_CASE_ID)).thenReturn(Optional.of(testCase));

    // WHEN
    underTest.processRefusal(managementEvent);

    // THEN
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.isRefusalReceived()).isTrue();

    verify(caseProcessor, times(1)).emitCaseUpdatedEvent(testCase);
    verify(eventLogger, times(1))
        .logRefusalEvent(
            testCase,
            REFUSAL_RECEIVED,
            EventType.REFUSAL_RECEIVED,
            expectedRefusal,
            managementEvent.getEvent());
  }

  @Test(expected = RuntimeException.class)
  public void shouldThrowRuntimeExceptionWhenCaseNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getRefusal().getCollectionCase().setId(TEST_CASE_ID.toString());
    String expectedErrorMessage = String.format("Case Id '%s' not found!", TEST_CASE_ID.toString());

    when(caseRepository.findByCaseId(TEST_CASE_ID)).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processRefusal(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testNullDateTime() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getRefusal().getCollectionCase().setId(TEST_CASE_ID.toString());
    RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();

    // Given
    Case expectedCase = getRandomCase();
    UacQidLink expectedUacQidLink = expectedCase.getUacQidLinks().get(0);
    expectedUacQidLink.setCaze(expectedCase);

    managementEvent.getEvent().setDateTime(null);

    when(caseRepository.findByCaseId(TEST_CASE_ID)).thenReturn(Optional.of(expectedCase));

    String expectedErrorMessage =
        String.format(
            "Date time not found in refusal request event for Case Id '%s",
            expectedRefusal.getCollectionCase().getId());

    try {
      // WHEN
      underTest.processRefusal(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
