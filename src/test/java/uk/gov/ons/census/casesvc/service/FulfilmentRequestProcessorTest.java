package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentRequestProcessorTest {

  @Mock private CaseProcessor caseProcessor;

  @Mock private CaseRepository caseRepository;

  @Mock private EventLogger eventLogger;

  @InjectMocks FulfilmentRequestProcessor underTest;

  @Test
  public void testGoodFulfilmentRequest() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();

    // Given
    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());

    when(caseRepository.findByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(Optional.of(expectedCase));

    // when
    underTest.processFulfilmentRequest(managementEvent);

    // then
    verify(eventLogger, times(1))
        .logFulfilmentRequestedEvent(
            expectedCase,
            expectedCase.getCaseId(),
            managementEvent.getEvent().getDateTime(),
            "Fulfilment Request Received",
            FULFILMENT_REQUESTED,
            expectedFulfilmentRequest,
            managementEvent.getEvent());
  }

  @Test(expected = RuntimeException.class)
  public void testCaseIdNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(UUID.randomUUID().toString());
    UUID expectedCaseIdNotFound =
        UUID.fromString(managementEvent.getPayload().getFulfilmentRequest().getCaseId());
    String expectedErrorMessage = String.format("Case ID '%s' not found!", expectedCaseIdNotFound);

    try {
      // WHEN
      underTest.processFulfilmentRequest(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testNullDateTime() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    EventDTO event = managementEvent.getEvent();
    event.setDateTime(null);

    // Given
    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());
    UUID caseId = expectedCase.getCaseId();

    when(caseRepository.findByCaseId(caseId)).thenReturn(Optional.of(expectedCase));

    String expectedErrorMessage =
        String.format("Date time not found in fulfilment request event for case '%s", caseId);

    try {
      // WHEN
      underTest.processFulfilmentRequest(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }
}
