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
    // HH	U	HH	N	Y	Close
    testRecipting("HH", "U", HOUSEHOLD_HH_ENGLAND, false, true);
  }

  @Test
  public void testHH_U_Cont() {
    // HH	U	Cont	N	N	None
    testContinuationQidResultInNoChangesOrEmitting("HH", "U", ENGLAND_HOUSEHOLD_CONTINUATION);
  }

  @Test
  public void test_HI_U_Ind() {
    // HI	U	Ind	N	Y	None
    testRecipting("HI", "U", HOUSEHOLD_INDIVIDUAL, false, true);
  }

  @Test
  public void test_CE_E_I() {
    // CE	E	Ind	Y	N	Update
    testRecipting("CE", "E", HOUSEHOLD_INDIVIDUAL, true, false);
  }

  @Test
  public void test_CE_E_CE1() {
    //    CE	E	CE1	N	Y Update
    testRecipting("CE", "E", ENGLAND_CE_QID, "CE_treatmentCode", false, true);
  }

  @Test
  public void CE_U_Ind_Does_Not_Receipt() {
    // CE	U	Ind	Y	Y if AR >= ER	Update
    testActualResponseGreaterEqualToReceipting(0, 2, false);
  }

  @Test
  public void CE_U_Ind_Does_Receipt() {
    // CE	U	Ind	Y	Y if AR >= ER	Update
    testActualResponseGreaterEqualToReceipting(1, 2, true);
  }

  @Test
  public void SPG_E_HH() {
    // SPG	E	HH	N	N	N
    testContinuationQidResultInNoChangesOrEmitting("SPG", "E", HOUSEHOLD_HH_ENGLAND);
  }

  @Test
  public void SPG_E_Ind() {
    // SPG	E	Ind	N	N	N
    testContinuationQidResultInNoChangesOrEmitting("SPG", "E", HOUSEHOLD_INDIVIDUAL);
  }

  @Test
  public void SPG_U_HH() {
    // SPG	U	HH	N	Y	Close
    testRecipting("SPG", "U", HOUSEHOLD_HH_ENGLAND, false, true);
  }

  @Test
  public void SPG_U_I() {
    // SPG	U	Ind	N	N	None
    testContinuationQidResultInNoChangesOrEmitting("SPG", "U", HOUSEHOLD_INDIVIDUAL);
  }

  @Test
  public void SPG_U_Cont() {
    testContinuationQidResultInNoChangesOrEmitting("SPG", "U", ENGLAND_HOUSEHOLD_CONTINUATION);
  }

  @Test(expected = RuntimeException.class)
  public void testUnmappedThrowsRunTimeException() {
    testRecipting("BL", "A", "H", false, false);
  }

  private void testRecipting(
      String caseType,
      String addressLevel,
      String qid,
      boolean incrementActualRespsonesExpected,
      boolean receiptExpected) {
    testRecipting(
        caseType,
        addressLevel,
        qid,
        "empty_treatment_code",
        incrementActualRespsonesExpected,
        receiptExpected);
  }

  private void testRecipting(
      String caseType,
      String addressLevel,
      String qid,
      String treatmentCode,
      boolean expectIncrement,
      boolean expectReceipt) {

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(0);
    caze.setTreatmentCode(treatmentCode);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);

    if (expectIncrement) {
      when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));
    }

    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);

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
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CLOSE);
  }

  @Test
  public void testUnactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
    uacQidLink.setCaze(caze);

    underTest.receiptCase(uacQidLink, EventTypeDTO.RESPONSE_RECEIVED);
    verifyZeroInteractions(caseService);
  }

  private void testActualResponseGreaterEqualToReceipting(
      int actualResponses, int expectedCapacity, boolean receiptExpected) {
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

    verify(caseRepository).getCaseAndLockByCaseId(caze.getCaseId());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), any());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(caze.getCaseId());
    assertThat(actualCase.getCeActualResponses()).isEqualTo(actualResponses + 1);
    assertThat(actualCase.isReceiptReceived()).isEqualTo(receiptExpected);
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
