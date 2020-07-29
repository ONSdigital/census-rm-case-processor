package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(Parameterized.class)
public class CaseReceiptServiceTableTest {
  private static final String HOUSEHOLD_INDIVIDUAL = "21";
  private static final String HOUSEHOLD_HH_ENGLAND = "01";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String ENGLAND_CE_QID = "31";
  private static final String EQ_EVENT_CHANNEL = "EQ";
  private Key key;
  private Expectation expectation;
  private static final ActionInstructionType UPDATE = ActionInstructionType.UPDATE;
  private static final ActionInstructionType CANCEL = ActionInstructionType.CANCEL;

  public CaseReceiptServiceTableTest(Key key, Expectation expectation) {
    this.key = key;
    this.expectation = expectation;
  }

  @Parameterized.Parameters(name = "Test Key: {0}")
  public static Collection<Object[]> data() {
    Object[][] ruleToTest = {
      {new Key("HH", "U", "HH"), new Expectation("N", "Y", CANCEL)},
      {new Key("HH", "U", "CE1"), new Expectation("N", "N", null)},
      {new Key("HH", "U", "Cont"), new Expectation("N", "N", null)},
      {new Key("HI", "U", "HH"), new Expectation("N", "Y", null)},
      {new Key("HI", "U", "Ind"), new Expectation("N", "Y", null)},
      {new Key("HI", "U", "CE1"), new Expectation("N", "N", null)},
      {new Key("HI", "U", "Cont"), new Expectation("N", "N", null)},
      {new Key("CE", "E", "HH"), new Expectation("Y", "N", UPDATE)},
      {new Key("CE", "E", "Ind"), new Expectation("Y", "N", UPDATE)},
      {new Key("CE", "E", "CE1"), new Expectation("N", "Y", UPDATE)},
      {new Key("CE", "E", "Cont"), new Expectation("N", "N", null)},
      {new Key("CE", "U", "HH"), new Expectation("Y", "Y AR >= ER", CANCEL)},
      {new Key("CE", "U", "HH"), new Expectation("Y", "N AR < ER", UPDATE)},
      {new Key("CE", "U", "HH"), new Expectation("Y", "N ER = null", UPDATE)},
      {new Key("CE", "U", "Ind"), new Expectation("Y", "Y AR >= ER", CANCEL)},
      {new Key("CE", "U", "Ind"), new Expectation("Y", "N AR < ER", UPDATE)},
      {new Key("CE", "U", "Ind"), new Expectation("Y", "N ER = null", UPDATE)},
      {new Key("CE", "U", "CE1"), new Expectation("N", "N", null)},
      {new Key("CE", "U", "Cont"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "HH"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "Ind"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "CE1"), new Expectation("N", "N", null)},
      {new Key("SPG", "E", "Cont"), new Expectation("N", "N", null)},
      {new Key("SPG", "U", "HH"), new Expectation("N", "Y", CANCEL)},
      {new Key("SPG", "U", "Ind"), new Expectation("N", "N", null)},
      {new Key("SPG", "U", "CE1"), new Expectation("N", "N", null)},
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
      Integer capacity) {

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

    when(caseService.getCaseAndLockIt(any())).thenReturn(caze);

    try {
      underTest.receiptCase(uacQidLink, getReceiptEventDTO());
    } catch (RuntimeException ex) {
      if (expectation.isRunTimeExceptionExpected()) {
        verifyNoInteractions(caseService);
        verifyNoInteractions(caseRepository);
        return;
      }

      fail("Unexpected exception" + ex.getMessage() + " for " + key.toString());
    }

    if (!expectReceipt && !expectIncrement) {
      verifyNoInteractions(caseService);
      verifyNoInteractions(caseRepository);
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
      verify(caseService).getCaseAndLockIt(eq(caze.getCaseId()));
      assertThat(actualCase.getCeActualResponses()).isEqualTo(1);
    }

    Metadata metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata.getCauseEventType()).isEqualTo(RESPONSE_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(expectedFieldInstruction);
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

  private EventDTO getReceiptEventDTO() {
    EventDTO receiptEventDTO = new EventDTO();
    receiptEventDTO.setDateTime(OffsetDateTime.now());
    receiptEventDTO.setTransactionId(UUID.randomUUID());
    receiptEventDTO.setType(RESPONSE_RECEIVED);
    receiptEventDTO.setChannel(EQ_EVENT_CHANNEL);
    return receiptEventDTO;
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
    Integer expectedCapacity = 0;

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

        case "N ER = null":
          expectedReceipt = false;
          expectedCapacity = null;
          break;

        default:
          fail("Unrecognised Expected Receipt param: " + receiptStr);
      }
    }

    public Expectation() {
      this.expectMappingException = true;
    }

    public boolean isRunTimeExceptionExpected() {
      return expectMappingException;
    }
  }
}
