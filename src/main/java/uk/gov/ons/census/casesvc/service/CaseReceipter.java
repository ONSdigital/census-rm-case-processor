package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.QuestionnaireTypeHelper.iscontinuationQuestionnaireTypes;

import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Component
public class CaseReceipter {
  private final CaseService caseService;

  public CaseReceipter(CaseService caseService) {
    this.caseService = caseService;
  }

  public void handleReceipting(Case caze, UacQidLink uacQidLink) {
    if (uacQidLink.isActive()) return;

    if (caze.isReceiptReceived()) return;

    if (!iscontinuationQuestionnaireTypes(uacQidLink.getQid())) {
      caze.setReceiptReceived(true);
      caseService.saveAndEmitCaseUpdatedEvent(caze);
    }
  }
}
