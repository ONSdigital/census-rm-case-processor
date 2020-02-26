package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseReceiptServiceTest {
  private static final String HOUSEHOLD_INDIVIDUAL = "21";
  private static final String HOUSEHOLD_HH_ENGLAND = "01";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String ENGLAND_CE_QID = "31";

  @Mock CaseService caseService;
  @Mock CaseRepository caseRepository;
  @InjectMocks CaseReceiptService underTest;

  @Test
  public void test_HH_U_HH() {
    testRecipting("HH", "U", HOUSEHOLD_HH_ENGLAND, false, true, ActionInstructionType.CLOSE);
  }

  @Test(expected = RuntimeException.class)
  public void test_HH_U_Ind() {
    testRecipting("HH", "U", HOUSEHOLD_INDIVIDUAL, false, false, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_HH_U_CE1() {
    testRecipting("HH", "U", ENGLAND_CE_QID, false, false, null);
  }

  @Test
  public void testHH_U_Cont() {
    testContinuationQidResultInNoChangesOrEmitting("HH", "U", ENGLAND_HOUSEHOLD_CONTINUATION);
  }

  @Test
  public void test_HI_U_Ind() {
    testRecipting("HI", "U", HOUSEHOLD_INDIVIDUAL, false, true, ActionInstructionType.NONE);
  }

  @Test(expected = RuntimeException.class)
  public void test_HI_U_CE1() {
    testRecipting("HI", "U", ENGLAND_CE_QID, false, false, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_HI_U_Cont() {
    testRecipting("HI", "U", ENGLAND_HOUSEHOLD_CONTINUATION, false, false, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_CE_E_HH() {
    testRecipting("CE", "E", HOUSEHOLD_HH_ENGLAND, false, false, null);
  }

  @Test
  public void test_CE_E_I() {
    testRecipting("CE", "E", HOUSEHOLD_INDIVIDUAL, true, false, ActionInstructionType.UPDATE);
  }

  @Test
  public void test_CE_E_CE1() {
    testRecipting("CE", "E", ENGLAND_CE_QID, false, true, ActionInstructionType.UPDATE);
  }

  @Test(expected = RuntimeException.class)
  public void test_CE_E_Cont() {
    testRecipting("CE", "E", ENGLAND_HOUSEHOLD_CONTINUATION, false, false, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_CE_U_HH() {
    testRecipting("CE", "U", HOUSEHOLD_HH_ENGLAND, false, false, null);
  }

  @Test
  public void CE_U_Ind_Does_Not_Receipt() {
    testActualResponseGreaterEqualToReceipting(0, 2, false, ActionInstructionType.UPDATE);
  }

  @Test
  public void CE_U_Ind_Does_Receipt() {
    testActualResponseGreaterEqualToReceipting(1, 2, true, ActionInstructionType.UPDATE);
  }

  @Test(expected = RuntimeException.class)
  public void test_CE_U_CE1() {
    testRecipting("CE", "U", ENGLAND_CE_QID, false, true, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_CE_U_Cont() {
    testRecipting("CE", "U", ENGLAND_HOUSEHOLD_CONTINUATION, false, false, null);
  }

  @Test
  public void SPG_E_HH() {
    testContinuationQidResultInNoChangesOrEmitting("SPG", "E", HOUSEHOLD_HH_ENGLAND);
  }

  @Test
  public void SPG_E_Ind() {
    testContinuationQidResultInNoChangesOrEmitting("SPG", "E", HOUSEHOLD_INDIVIDUAL);
  }

  @Test(expected = RuntimeException.class)
  public void test_SPG_E_CE1() {
    testRecipting("SPG", "E", ENGLAND_CE_QID, false, true, null);
  }

  @Test(expected = RuntimeException.class)
  public void test_SPG_U_Cont() {
    testRecipting("SPG", "E", ENGLAND_HOUSEHOLD_CONTINUATION, false, true, null);
  }

  @Test
  public void SPG_U_HH() {
    testRecipting("SPG", "U", HOUSEHOLD_HH_ENGLAND, false, true, ActionInstructionType.CLOSE);
  }

  @Test
  public void SPG_U_I() {
    testContinuationQidResultInNoChangesOrEmitting("SPG", "U", HOUSEHOLD_INDIVIDUAL);
  }

  @Test(expected = RuntimeException.class)
  public void test_SPG_U_CE1() {
    testRecipting("SPG", "U", ENGLAND_CE_QID, false, true, null);
  }

  @Test
  public void SPG_U_Cont() {
    testContinuationQidResultInNoChangesOrEmitting("SPG", "U", ENGLAND_HOUSEHOLD_CONTINUATION);
  }

  @Test(expected = RuntimeException.class)
  public void testUnmappedThrowsRunTimeException() {
    testRecipting("BL", "A", "H", false, false, null);
  }

  @Test
  public void testUnactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // Given
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
    uacQidLink.setCaze(caze);

    // When
    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);

    // Then
    verifyZeroInteractions(caseService);
    verifyZeroInteractions(caseRepository);
  }

  private void testRecipting(
      String caseType,
      String addressLevel,
      String qid,
      boolean expectIncrement,
      boolean expectReceipt,
      ActionInstructionType expectedActionInstructionType) {

    // Given
    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(0);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);

    if (expectIncrement) {
      when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));
    }

    // When
    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).as("Case Id saved").isEqualTo(caze.getCaseId());
    assertThat(actualCase.isReceiptReceived()).as("Case Receipted").isEqualTo(expectReceipt);

    if (expectIncrement) {
      verify(caseRepository).getCaseAndLockByCaseId(caze.getCaseId());
      assertThat(actualCase.getCeActualResponses()).isEqualTo(1);
    }

    Metadata metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(expectedActionInstructionType);
  }

  private void testActualResponseGreaterEqualToReceipting(
      int actualResponses,
      int expectedCapacity,
      boolean receiptExpected,
      ActionInstructionType expectedActionInstructionType) {
    // Given
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(actualResponses);
    caze.setCeExpectedCapacity(expectedCapacity);
    caze.setCaseType("CE");
    caze.setAddressLevel("U");

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
    uacQidLink.setCaze(caze);

    when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));

    // When
    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);

    // Then
    verify(caseRepository).getCaseAndLockByCaseId(caze.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(caze.getCaseId());
    assertThat(actualCase.getCeActualResponses()).isEqualTo(actualResponses + 1);
    assertThat(actualCase.isReceiptReceived()).isEqualTo(receiptExpected);

    Metadata metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(expectedActionInstructionType);
  }

  public void testContinuationQidResultInNoChangesOrEmitting(
      String caseType, String addressLevel, String qid) {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setTreatmentCode("NotACE_code");

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);

    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);
    verifyZeroInteractions(caseService);
    verifyZeroInteractions(caseRepository);
  }
}
