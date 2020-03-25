package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(Parameterized.class)
public class CaseReceiptServiceTest {
  private static final String HOUSEHOLD_INDIVIDUAL = "21";
  private static final String HOUSEHOLD_HH_ENGLAND = "01";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String ENGLAND_CE_QID = "31";
  private Key key;
  private Expectation expectation;

  public CaseReceiptServiceTest(Key key, Expectation expectation) {
    this.key = key;
    this.expectation = expectation;
  }

  @Parameterized.Parameters(name = "Test Key: {0}")
  public static Collection<Object[]> data() {
    Object[][] ruleToTest = {
      {new Key("HH", "U", "HH"), new Expectation("N", "Y", ActionInstructionType.CLOSE)},
      {new Key("HH", "U", "Ind"), new Expectation(RuntimeException.class)},
      {new Key("HH", "U", "CE1"), new Expectation(RuntimeException.class)},
      {new Key("HH", "U", "Cont"), new Expectation("N", "N", null)},
      {new Key("HI", "U", "HH"), new Expectation(RuntimeException.class)},
      {new Key("HI", "U", "Ind"), new Expectation("N", "Y", null)},
      {new Key("HI", "U", "CE1"), new Expectation(RuntimeException.class)},
      {new Key("HI", "U", "Cont"), new Expectation(RuntimeException.class)},
      {new Key("CE", "E", "HH"), new Expectation(RuntimeException.class)},
      {new Key("CE", "E", "Ind"), new Expectation("Y", "N", ActionInstructionType.UPDATE)},
      {new Key("CE", "E", "CE1"), new Expectation("N", "Y", ActionInstructionType.UPDATE)},
      {new Key("CE", "E", "Cont"), new Expectation(RuntimeException.class)},
      {new Key("CE", "E", "HH"), new Expectation(RuntimeException.class)},
      {new Key("CE", "U", "Ind"), new Expectation("Y", "Y AR >= ER", ActionInstructionType.CLOSE)},
      {new Key("CE", "U", "Ind"), new Expectation("Y", "N AR < ER", ActionInstructionType.UPDATE)},
      {new Key("CE", "U", "CE1"), new Expectation(RuntimeException.class)},
      {new Key("CE", "U", "Cont"), new Expectation(RuntimeException.class)},
      {new Key("SPG", "E", "HH"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "Ind"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "CE1"), new Expectation(RuntimeException.class)},
      {new Key("SPG", "E", "Cont"), new Expectation(RuntimeException.class)},
      {new Key("SPG", "U", "HH"), new Expectation("N", "Y", ActionInstructionType.CLOSE)},
      {new Key("SPG", "U", "Ind"), new Expectation("N", "N", null)},
      {new Key("SPG", "U", "CE1"), new Expectation(RuntimeException.class)},
      {new Key("SPG", "U", "Cont"), new Expectation("N", "N", null)}
    };

    return Arrays.asList(ruleToTest);
  }

  @Test
  public void receiptingTableTests() {
    runReceiptingTest(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType.equals("CE1") ? "CE_treatmentCode" : "NotACE_TreatmentCode",
        this.expectation.expectIncrement,
        this.expectation.expectedReceipt,
        this.expectation.expectedFieldInstruction,
        this.expectation.expectedCapacity);
  }

  private void runReceiptingTest(
      String caseType,
      String addressLevel,
      String qid,
      String treatmentCode,
      boolean expectIncrement,
      boolean expectReceipt,
      ActionInstructionType expectedFieldInstruction,
      int capacity) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService underTest = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);
    caze.setCeActualResponses(0);
    caze.setTreatmentCode(treatmentCode);
    caze.setCeExpectedCapacity(capacity);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);

    when(caseRepository.getCaseAndLockByCaseId(any())).thenReturn(Optional.of(caze));

    try {
      underTest.receiptCase(uacQidLink, RESPONSE_RECEIVED);
    } catch (RuntimeException ex) {
      if (expectation.isRunTimeExceptionExpected()) {
        verifyZeroInteractions(caseService);
        verifyZeroInteractions(caseRepository);
        return;
      }

      fail("Unexepcted exception" + ex.getMessage() + " for " + key.toString());
    }

    if (!expectReceipt && !expectIncrement) {
      verifyZeroInteractions(caseService);
      verifyZeroInteractions(caseRepository);
      return;
    }

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
    assertThat(metadata.getFieldDecision()).isEqualTo(expectedFieldInstruction);
  }

  @Test
  public void testInactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // when
    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
    uacQidLink.setCaze(caze);

    caseReceiptService.receiptCase(uacQidLink, RESPONSE_RECEIVED);
    verifyZeroInteractions(caseService);
  }

  @Test
  public void testQidMarkedBlankQuestionnaireDoesNotReceiptCase() {
    // when
    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);

    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
    uacQidLink.setBlankQuestionnaire(true);
    uacQidLink.setCaze(caze);

    caseReceiptService.receiptCase(uacQidLink, RESPONSE_RECEIVED);
    verifyZeroInteractions(caseService);
  }

  private String getQid(String qidType) {
    switch (qidType) {
      case "HH":
        return HOUSEHOLD_HH_ENGLAND;
      case "Ind":
        return HOUSEHOLD_INDIVIDUAL;
      case "Cont":
        return ENGLAND_HOUSEHOLD_CONTINUATION;
      case "CE1":
        return ENGLAND_CE_QID;
      default:
        fail("Unknown Qid Type: " + qidType);
    }

    return null;
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  private static class Key {
    private String caseType;
    private String addressLevel;
    private String formType;

    public String toString() {
      return caseType + "_" + addressLevel + "_" + formType;
    }
  }

  private static class Expectation {
    boolean expectIncrement;
    boolean expectedReceipt;
    ActionInstructionType expectedFieldInstruction;
    boolean expectMappingException;
    int expectedCapacity = 0;

    public Expectation(
        String incrementStr, String receiptStr, ActionInstructionType expectedFieldInstruction) {
      this.expectIncrement = incrementStr.equals("Y");
      this.expectedFieldInstruction = expectedFieldInstruction;
      this.expectMappingException = false;
      expectedCapacity = 0;

      switch (receiptStr) {
        case "Y":
          expectedReceipt = true;
          break;

        case "N":
          expectedReceipt = false;
          break;

        case "Y AR >= ER":
          expectedReceipt = true;
          expectedCapacity = 1;
          break;

        case "N AR < ER":
          expectedReceipt = false;
          expectedCapacity = 2;
          break;

        default:
          fail("Unrecognised Expected Receipt param: " + receiptStr);
      }
    }

    public Expectation(Class<RuntimeException> runtimeExceptionClass) {
      this.expectMappingException = true;
    }

    public boolean isRunTimeExceptionExpected() {
      return expectMappingException;
    }
  }
}
