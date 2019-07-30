package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.ReceiptProcessor.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptProcessorTest {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final String TEST_QUESTIONNAIRE_ID = "123";

  @Mock private CaseProcessor caseProcessor;

  @Mock private UacQidLinkRepository uacQidLinkRepository;

  @Mock private CaseRepository caseRepository;

  @Mock private UacProcessor uacProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks
  ReceiptProcessor underTest;

  @Test
  public void testGoodReceipt() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptDTO expectedReceipt = managementEvent.getPayload().getReceipt();

    // Given
    Case expectedCase = getRandomCase();
    UacQidLink expectedUacQidLink = expectedCase.getUacQidLinks().get(0);
    expectedUacQidLink.setCaze(expectedCase);

    managementEvent.getPayload().getReceipt().setResponseDateTime(OffsetDateTime.now());

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.of(expectedUacQidLink));

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacProcessor, times(1)).emitUacUpdatedEvent(expectedUacQidLink, expectedCase, false);
    verify(eventLogger, times(1))
        .logReceiptEvent(
            expectedUacQidLink,
            QID_RECEIPTED,
            EventType.UAC_UPDATED,
            expectedReceipt,
            managementEvent.getEvent(),
            expectedReceipt.getResponseDateTime());
  }

  @Test(expected = RuntimeException.class)
  public void testReceiptedQidNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    String expectedQuestionnaireId = managementEvent.getPayload().getReceipt().getQuestionnaireId();
    String expectedErrorMessage =
        String.format("Questionnaire Id '%s' not found!", expectedQuestionnaireId);

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());

    try {
      // WHEN
      underTest.processReceipt(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }

    fail("Questionnaire Id runtime exception did not throw!");
  }
}
