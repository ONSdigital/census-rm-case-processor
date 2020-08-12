package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;
import static uk.gov.ons.census.casesvc.model.entity.RefusalType.HARD_REFUSAL;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
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
public class BlankQuestionnaireServiceTest {

  private static final String HOUSEHOLD_INDIVIDUAL = "21";
  private static final String HOUSEHOLD_HH_ENGLAND = "01";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";
  private static final String ENGLAND_CE_QID = "31";
  private Key key;
  private Expectation expectation;

  public BlankQuestionnaireServiceTest(Key key, Expectation expectation) {
    this.key = key;
    this.expectation = expectation;
  }

  @Parameterized.Parameters(name = "Test Key: {0}")
  public static Collection<Object[]> data() {
    Object[][] ruleToTest = {
      {new Key("HH", "U", "HH", true), new Expectation(false, false)},
      {new Key("HH", "U", "Ind", true), new Expectation(false, false)},
      {new Key("HH", "U", "CE1", true), new Expectation(false, false)},
      {new Key("HI", "U", "HH", true), new Expectation(false, false)},
      {new Key("HI", "U", "Ind", true), new Expectation(false, false)},
      {new Key("HI", "U", "CE1", true), new Expectation(false, false)},
      {new Key("CE", "E", "HH", true), new Expectation(false, false)},
      {new Key("CE", "E", "Ind", true), new Expectation(false, false)},
      {new Key("CE", "E", "CE1", true), new Expectation(false, false)},
      {new Key("CE", "U", "HH", true), new Expectation(false, false)},
      {new Key("CE", "U", "Ind", true), new Expectation(false, false)},
      {new Key("CE", "U", "CE1", true), new Expectation(false, false)},
      {new Key("SPG", "E", "HH", true), new Expectation(false, false)},
      {new Key("SPG", "E", "Ind", true), new Expectation(false, false)},
      {new Key("SPG", "E", "CE1", true), new Expectation(false, false)},
      {new Key("SPG", "U", "HH", true), new Expectation(false, false)},
      {new Key("SPG", "U", "Ind", true), new Expectation(false, false)},
      {new Key("SPG", "U", "CE1", true), new Expectation(false, false)},
      {new Key("HH", "U", "HH", false), new Expectation(true, true)},
      {new Key("HH", "U", "Ind", false), new Expectation(false, false)},
      {new Key("HH", "U", "CE1", false), new Expectation(false, false)},
      {new Key("HI", "U", "HH", false), new Expectation(false, false)},
      {new Key("HI", "U", "Ind", false), new Expectation(true, false)},
      {new Key("HI", "U", "CE1", false), new Expectation(false, false)},
      {new Key("CE", "E", "HH", false), new Expectation(false, false)},
      {new Key("CE", "E", "Ind", false), new Expectation(false, false)},
      {new Key("CE", "E", "CE1", false), new Expectation(false, false)},
      {new Key("CE", "U", "HH", false), new Expectation(false, false)},
      {new Key("CE", "U", "Ind", false), new Expectation(false, false)},
      {new Key("CE", "U", "CE1", false), new Expectation(false, false)},
      {new Key("SPG", "E", "HH", false), new Expectation(false, false)},
      {new Key("SPG", "E", "Ind", false), new Expectation(false, false)},
      {new Key("SPG", "E", "CE1", false), new Expectation(false, false)},
      {new Key("SPG", "U", "HH", false), new Expectation(true, true)},
      {new Key("SPG", "U", "Ind", false), new Expectation(false, false)},
      {new Key("SPG", "U", "CE1", false), new Expectation(false, false)},
      {new Key("HH", "U", "Cont", false), new Expectation(false, false)},
      {new Key("CE", "E", "Cont", false), new Expectation(false, false)},
      {new Key("CE", "U", "Cont", false), new Expectation(false, false)},
      {new Key("SPG", "E", "Cont", false), new Expectation(false, false)},
      {new Key("SPG", "U", "Cont", false), new Expectation(false, false)},
    };

    return Arrays.asList(ruleToTest);
  }

  @Test
  public void blankQuestionnaireTableTests() {
    runBlankQreTestCaseNotYetReceipted(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType,
        this.key.hasOtherValidReceiptForFormType,
        this.expectation);
  }

  @Test
  public void alreadyReceiptedBlankQuestionnaireTableTests() {
    runBlankQreTestCaseAlreadyReceipted(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType,
        this.key.hasOtherValidReceiptForFormType,
        this.expectation);
  }

  @Test
  public void refusedCasesAreNotSentToFieldTableTests() {
    runRefusedCaseIsNeverSentToField(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType,
        this.key.hasOtherValidReceiptForFormType,
        this.expectation);
  }

  @Test
  public void addressInvalidCasesAreNotSentToFieldTableTests() {
    runAddressInvalidCaseIsNeverSentToField(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType,
        this.key.hasOtherValidReceiptForFormType,
        this.expectation);
  }

  private void runBlankQreTestCaseNotYetReceipted(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      Expectation expectation) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setRefusalReceived(null);
    caze.setAddressInvalid(false);
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLink.setActive(true);
    caze.setUacQidLinks(List.of(uacQidLink));
    when(uacService.findByQid(eq(qid))).thenReturn(uacQidLink);

    if (hasOtherValidReceiptForFormType) {
      String otherQid = getQid(formType) + "1";
      UacQidLink otherUacQidLink = new UacQidLink();
      otherUacQidLink.setQid(otherQid);
      otherUacQidLink.setCaze(caze);
      otherUacQidLink.setActive(false);
      caze.setUacQidLinks(List.of(uacQidLink, otherUacQidLink));
      when(uacService.findByQid(eq(otherQid))).thenReturn(otherUacQidLink);
    }

    if (expectation.invalid) {
      checkExceptionIsRaised(caseService, underTest, caze, uacQidLink);
      return;
    }

    underTest.handleBlankQuestionnaire(caze, uacQidLink, RESPONSE_RECEIVED);

    if (!expectation.unreceiptCase && !expectation.sendToField) {
      verifyNoInteractions(caseService);
      verifyNoInteractions(caseRepository);
      return;
    }

    if (expectation.unreceiptCase && expectation.sendToField) {
      ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
      verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
      assertThat(metadataArgumentCaptor.getValue().getFieldDecision())
          .isEqualTo(ActionInstructionType.UPDATE);
      return;
    }

    if (expectation.unreceiptCase && !expectation.sendToField) {
      verify(caseService).unreceiptCase(any(), eq(null));
      return;
    }
  }

  private void checkExceptionIsRaised(
      CaseService caseService,
      BlankQuestionnaireService underTest,
      Case caze,
      UacQidLink uacQidLink) {
    try {
      underTest.handleBlankQuestionnaire(caze, uacQidLink, RESPONSE_RECEIVED);
    } catch (RuntimeException rte) {
      assertThat(rte).isInstanceOf(RuntimeException.class);
      assertThat(rte.getMessage()).endsWith(" does not map to any known processing rule");
      verifyNoInteractions(caseService);
      return;
    }

    fail("Expected RuntimeException to be thrown");
  }

  private void runBlankQreTestCaseAlreadyReceipted(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      Expectation expectation) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setRefusalReceived(null);
    caze.setAddressInvalid(false);
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLink.setActive(false);
    caze.setUacQidLinks(List.of(uacQidLink));
    when(uacService.findByQid(eq(qid))).thenReturn(uacQidLink);

    if (hasOtherValidReceiptForFormType) {
      String otherQid = getQid(formType) + "1";
      UacQidLink otherUacQidLink = new UacQidLink();
      otherUacQidLink.setQid(otherQid);
      otherUacQidLink.setCaze(caze);
      otherUacQidLink.setActive(false);
      caze.setUacQidLinks(List.of(uacQidLink, otherUacQidLink));
      when(uacService.findByQid(eq(otherQid))).thenReturn(otherUacQidLink);
    }

    if (expectation.invalid) {
      checkExceptionIsRaised(caseService, underTest, caze, uacQidLink);
      return;
    }

    underTest.handleBlankQuestionnaire(caze, uacQidLink, RESPONSE_RECEIVED);

    if (!expectation.unreceiptCase && !expectation.sendToField) {
      verifyNoInteractions(caseService);
      verifyNoInteractions(caseRepository);
      return;
    }

    if (expectation.sendToField) {
      ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
      verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
      assertThat(metadataArgumentCaptor.getValue().getFieldDecision())
          .isEqualTo(ActionInstructionType.UPDATE);
    }
  }

  private void runRefusedCaseIsNeverSentToField(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      Expectation expectation) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setRefusalReceived(HARD_REFUSAL);
    caze.setAddressInvalid(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLink.setActive(false);
    caze.setUacQidLinks(List.of(uacQidLink));
    when(uacService.findByQid(eq(qid))).thenReturn(uacQidLink);

    if (hasOtherValidReceiptForFormType) {
      String otherQid = getQid(formType) + "1";
      UacQidLink otherUacQidLink = new UacQidLink();
      otherUacQidLink.setQid(otherQid);
      otherUacQidLink.setCaze(caze);
      otherUacQidLink.setActive(false);
      caze.setUacQidLinks(List.of(uacQidLink, otherUacQidLink));
      when(uacService.findByQid(eq(otherQid))).thenReturn(otherUacQidLink);
    }

    if (expectation.invalid) {
      checkExceptionIsRaised(caseService, underTest, caze, uacQidLink);
      return;
    }

    underTest.handleBlankQuestionnaire(caze, uacQidLink, RESPONSE_RECEIVED);

    if (!expectation.unreceiptCase) {
      verifyNoInteractions(caseService);
      verifyNoInteractions(caseRepository);
      return;
    }

    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
    assertThat(metadataArgumentCaptor.getValue()).isNull();
  }

  private void runAddressInvalidCaseIsNeverSentToField(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      Expectation expectation) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
    caze.setRefusalReceived(null);
    caze.setAddressInvalid(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(qid);
    uacQidLink.setCaze(caze);
    uacQidLink.setActive(false);
    caze.setUacQidLinks(List.of(uacQidLink));
    when(uacService.findByQid(eq(qid))).thenReturn(uacQidLink);

    if (hasOtherValidReceiptForFormType) {
      String otherQid = getQid(formType) + "1";
      UacQidLink otherUacQidLink = new UacQidLink();
      otherUacQidLink.setQid(otherQid);
      otherUacQidLink.setCaze(caze);
      otherUacQidLink.setActive(false);
      caze.setUacQidLinks(List.of(uacQidLink, otherUacQidLink));
      when(uacService.findByQid(eq(otherQid))).thenReturn(otherUacQidLink);
    }

    if (expectation.invalid) {
      checkExceptionIsRaised(caseService, underTest, caze, uacQidLink);
      return;
    }

    underTest.handleBlankQuestionnaire(caze, uacQidLink, RESPONSE_RECEIVED);

    if (!expectation.unreceiptCase) {
      verifyNoInteractions(caseService);
      verifyNoInteractions(caseRepository);
      return;
    }

    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
    assertThat(metadataArgumentCaptor.getValue()).isNull();
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
  private static class Key {

    private String caseType;
    private String addressLevel;
    private String formType;
    private boolean hasOtherValidReceiptForFormType;

    public String toString() {
      return caseType + "_" + addressLevel + "_" + formType + "_" + hasOtherValidReceiptForFormType;
    }
  }

  private static class Expectation {

    boolean unreceiptCase;
    boolean sendToField;
    boolean invalid;

    public Expectation(boolean unreceiptCase, boolean sendToField) {
      this.unreceiptCase = unreceiptCase;
      this.sendToField = sendToField;
    }

    public Expectation(boolean invalid) {
      this.invalid = true;
    }
  }
}
