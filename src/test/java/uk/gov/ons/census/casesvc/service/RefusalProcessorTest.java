package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
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

  @Mock private UacQidLinkRepository uacQidLinkRepository;
  @Mock private CaseRepository caseRepository;

  @Mock private CaseProcessor caseProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalProcessor underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    Case testCase = getRandomCase();
    testCase.setRefusalReceived(false);
    UacQidLink expectedUacQidLink = testCase.getUacQidLinks().get(0);
    Case expectedCase = expectedUacQidLink.getCaze();
    RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.of(expectedUacQidLink));

    // WHEN
    underTest.processRefusal(managementEvent);

    // THEN
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.isRefusalReceived()).isTrue();

    verify(caseProcessor, times(1)).emitCaseUpdatedEvent(expectedCase);
    verify(eventLogger, times(1))
        .logRefusalEvent(
            expectedUacQidLink,
            REFUSAL_RECEIVED,
            EventType.CASE_UPDATED,
            expectedRefusal,
            managementEvent.getEvent(),
            expectedRefusal.getResponseDateTime());
  }

  @Test
  public void shouldThrowRuntimeExceptionWhenCaseNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    String expectedQuestionnaireId = managementEvent.getPayload().getRefusal().getQuestionnaireId();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", expectedQuestionnaireId);

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processRefusal(managementEvent);
    } catch (RuntimeException e) {
      // THEN
      assertThat(e.getMessage()).isEqualTo(expectedErrorMessage);
    }
  }
}
