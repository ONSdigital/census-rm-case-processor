package uk.gov.ons.census.casesvc.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class CaseReceipterTest {
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "21";
  private static final String ENGLAND_HOUSEHOLD_CONTINUATION = "11";

  @Mock CaseService caseService;

  @InjectMocks CaseReceipter underTest;

  @Test
  public void testLinkingUnactiveQidReceiptsCase() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(false);
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);

    underTest.handleReceipting(caze, uacQidLink);

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).as("Case Id saved").isEqualTo(caze.getCaseId());
    assertThat(actualCase.isReceiptReceived()).as("Case Reecipted").isEqualTo(true);
  }

  @Test
  public void testLinkingUnactiveQidDoesNotReceiptsCaseAlreadyReceipted() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(true);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(false);
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);

    underTest.handleReceipting(caze, uacQidLink);
    verifyZeroInteractions(caseService);
  }

  @Test
  public void testLinkingaActiveQidtoUnreceiptCaseDoesntReceipt() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(true);
    uacQidLink.setQid(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND);

    underTest.handleReceipting(caze, uacQidLink);
    verifyZeroInteractions(caseService);
  }

  @Test
  public void testContinuationQidResultInNoReceipting() {
    // when
    Case caze = new Case();
    caze.setCaseId(UUID.randomUUID());
    caze.setReceiptReceived(false);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(true);
    uacQidLink.setQid(ENGLAND_HOUSEHOLD_CONTINUATION);

    underTest.handleReceipting(caze, uacQidLink);
    verifyZeroInteractions(caseService);
  }
}
