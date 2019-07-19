package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestRefusal;

import java.util.HashMap;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.Refusal;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class RefusalProcessorTest {

  @Mock private UacQidLinkRepository uacQidLinkRepository;
  @Mock private CaseRepository caseRepository;

  @Mock private CaseProcessor caseProcessor;

  @Mock private UacProcessor uacProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalProcessor underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    Case testCase = getRandomCase();
    Optional<UacQidLink> uacQidLink = Optional.of(testCase.getUacQidLinks().get(0));

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(uacQidLink);

    // WHEN
    underTest.processRefusal(getTestRefusal(), new HashMap<>());

    // THEN
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.isRefusalReceived()).isTrue();
  }

  @Test
  public void shouldThrowRuntimeExceptionWhenCaseNotFound() {
    // GIVEN
    Refusal testRefusal = getTestRefusal();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", testRefusal.getQuestionnaire_Id());

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processRefusal(testRefusal, new HashMap<>());
    } catch (RuntimeException e) {
      // THEN
      assertThat(e.getMessage()).isEqualTo(expectedErrorMessage);
    }
  }
}
