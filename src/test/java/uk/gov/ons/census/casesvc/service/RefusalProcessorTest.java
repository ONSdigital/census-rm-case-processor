package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestRefusal;

import java.util.HashMap;
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
import uk.gov.ons.census.casesvc.model.dto.Refusal;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class RefusalProcessorTest {

  @Mock private CaseRepository caseRepository;

  @Mock private CaseProcessor caseProcessor;

  @Mock private UacProcessor uacProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalProcessor underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    Refusal expectedRefusal = new Refusal();
    CollectionCase expectedCollectionCase = new CollectionCase();
    String expectedCaseId = UUID.randomUUID().toString();

    expectedCollectionCase.setId(expectedCaseId);
    expectedRefusal.setCollectionCase(expectedCollectionCase);

    Case expectedCase = getRandomCase();
    expectedCase.setCaseId(UUID.randomUUID());
    expectedCase.setRefusalReceived(false);

    when(caseRepository.findByCaseId(any(UUID.class))).thenReturn(Optional.of(expectedCase));
    when(caseRepository.saveAndFlush(expectedCase)).thenReturn(expectedCase);

    // WHEN
    underTest.processRefusal(expectedRefusal, new HashMap<>());

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
    String expectedMessage =
        String.format("Case Id '%s' not found!", testRefusal.getCollectionCase().getId());

    when(caseRepository.findByCaseId(any(UUID.class))).thenReturn(Optional.empty());

    // WHEN
    // THEN
    try {
      underTest.processRefusal(testRefusal, new HashMap<>());
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }
}
