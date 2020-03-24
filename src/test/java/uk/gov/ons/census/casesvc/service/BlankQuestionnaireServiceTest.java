package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.RESPONSE_RECEIVED;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
      {new Key("HH", "U", "HH", false), new Expectation(true, true)},
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
        this.expectation.unreceiptCase,
        this.expectation.sendToField);
  }

  @Test
  public void alreadyReceiptedBlankQuestionnaireTableTests() {
    runBlankQreTestCaseAlreadyReceipted(
        this.key.caseType,
        this.key.addressLevel,
        getQid(this.key.formType),
        this.key.formType,
        this.key.hasOtherValidReceiptForFormType,
        this.expectation.unreceiptCase,
        this.expectation.sendToField);
  }

  private void runBlankQreTestCaseNotYetReceipted(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      boolean unreceiptCase,
      boolean sendToField) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
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

    underTest.handleBlankQuestionnaire(uacQidLink, RESPONSE_RECEIVED);

    if (!unreceiptCase && !sendToField) {
      verifyZeroInteractions(caseService);
      verifyZeroInteractions(caseRepository);
      return;
    }

    if (sendToField) {
      ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
      verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
      assertThat(metadataArgumentCaptor.getValue().getFieldDecision())
          .isEqualTo(ActionInstructionType.UPDATE);
    }
  }

  private void runBlankQreTestCaseAlreadyReceipted(
      String caseType,
      String addressLevel,
      String qid,
      String formType,
      boolean hasOtherValidReceiptForFormType,
      boolean unreceiptCase,
      boolean sendToField) {

    CaseService caseService = mock(CaseService.class);
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacService uacService = mock(UacService.class);
    BlankQuestionnaireService underTest = new BlankQuestionnaireService(caseService);

    Case caze = new Case();
    caze.setCaseType(caseType);
    caze.setAddressLevel(addressLevel);
    caze.setCaseId(UUID.randomUUID());
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

    underTest.handleBlankQuestionnaire(uacQidLink, RESPONSE_RECEIVED);

    if (!unreceiptCase && !sendToField) {
      verifyZeroInteractions(caseService);
      verifyZeroInteractions(caseRepository);
      return;
    }

    if (sendToField) {
      ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
      verify(caseService).unreceiptCase(any(), metadataArgumentCaptor.capture());
      assertThat(metadataArgumentCaptor.getValue().getFieldDecision())
          .isEqualTo(ActionInstructionType.UPDATE);
    }
  }

  //
  //
  //  @Test
  //  public void testUnactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
  //    // when
  //    CaseService caseService = mock(CaseService.class);
  //    CaseRepository caseRepository = mock(CaseRepository.class);
  //    CaseReceiptService caseReceiptService = new CaseReceiptService(caseService, caseRepository);
  //
  //    Case caze = new Case();
  //    caze.setCaseId(UUID.randomUUID());
  //    caze.setReceiptReceived(true);
  //
  //    UacQidLink uacQidLink = new UacQidLink();
  //    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL);
  //    uacQidLink.setCaze(caze);
  //
  //    caseReceiptService.receiptCase(uacQidLink, RESPONSE_RECEIVED);
  //    verifyZeroInteractions(caseService);
  //  }

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
    private boolean hasOtherValidReceiptForFormType;

    public String toString() {
      return caseType + "_" + addressLevel + "_" + formType + "_" + hasOtherValidReceiptForFormType;
    }
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  private static class Expectation {

    boolean unreceiptCase;
    boolean sendToField;
  }
}
