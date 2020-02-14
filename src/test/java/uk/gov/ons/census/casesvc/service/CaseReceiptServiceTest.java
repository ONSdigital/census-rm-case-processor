package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseReceiptServiceTest {
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "21";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";

  @Mock CaseService caseService;

  @Mock CaseRepository caseRepository;

  @InjectMocks CaseReceiptService underTest;

  @Test
  public void testUnactiveQidReceiptsCase() {
    // when
    Case caze = new Case();
    caze.setCaseType("HH");
    caze.setAddressLevel("U");
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    uacQidLink.setCaze(caze);

    underTest.receiptCase(uacQidLink);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).as("Case Id saved").isEqualTo(caze.getCaseId());
    assertThat(actualCase.isReceiptReceived()).as("Case Reecipted").isEqualTo(true);
  }

  @Test
  public void testUnactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    uacQidLink.setCaze(caze);

    underTest.receiptCase(uacQidLink);
    verifyZeroInteractions(caseService);
  }

  @Test
  public void testContinuationQidResultInNoReceipting() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(ENGLAND_HOUSEHOLD_CONTINUATION);
    uacQidLink.setCaze(caze);

    underTest.receiptCase(uacQidLink);
    verifyZeroInteractions(caseService);
  }

  @Test
  public void testCeEstabIndividualCaseIncrementsActualResponsesButDoesNotReceiptCase()
      throws IOException, ClassNotFoundException {
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(1);
    caze.setCeExpectedCapacity(2);
    caze.setCaseType("CE");
    caze.setAddressLevel("E");

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    uacQidLink.setCaze(caze);

    when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));

    // When
    underTest.receiptCase(uacQidLink);

    verify(caseRepository).getCaseAndLockByCaseId(caze.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(caze.getCaseId());
    assertThat(actualCase.getCeActualResponses()).isEqualTo(2);
    assertThat(actualCase.isReceiptReceived()).isFalse();
  }

  @Test
  public void
      testCeUnitndividualCaseIncrementsActualResponsesAndDoesReceiptCaseWhenCeActualResponsesEnough()
          throws IOException, ClassNotFoundException {
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(1);
    caze.setCeExpectedCapacity(2);
    caze.setCaseType("CE");
    caze.setAddressLevel("U");

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);
    uacQidLink.setCaze(caze);

    when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));

    // When
    underTest.receiptCase(uacQidLink);

    verify(caseRepository).getCaseAndLockByCaseId(caze.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(caze.getCaseId());
    assertThat(actualCase.getCeActualResponses()).isEqualTo(2);
    assertThat(actualCase.isReceiptReceived()).isTrue();
  }
}
