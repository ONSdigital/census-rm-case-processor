package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@Component
public class BlankQuestionnaireService {
  private final CaseService caseService;
  private static final String HH = "H";

  private Map<BlankQuestionnaireService.Key, BlankQreRule> rules = new HashMap<>();

  public BlankQuestionnaireService(CaseService caseService) {
    this.caseService = caseService;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: TODO
    */

    rules.put(
        new BlankQuestionnaireService.Key("HH", "U", HH, false),
        new BlankQuestionnaireService.UnreceiptCaseAndSendToField());
  }

  public void handleBlankQuestionnaire(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Case caze = uacQidLink.getCaze();

    if (caze.isReceiptReceived()) return;

    BlankQuestionnaireService.Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    BlankQreRule rule = rules.get(ruleKey);
    rule.run(caze, causeEventType);
  }

  private BlankQuestionnaireService.Key makeRulesKey(Case caze, UacQidLink uacQidLink) {
    String formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());

    return new BlankQuestionnaireService.Key(
        caze.getCaseType(),
        caze.getAddressLevel(),
        formType,
        caseHasOtherValidReceiptForFormType(caze, formType));
  }

  private boolean caseHasOtherValidReceiptForFormType(Case caze, String formType) {
    return caze.getUacQidLinks().stream()
        .anyMatch(
            u ->
                !u.isActive()
                    && !u.isBlankQuestionnaire()
                    && mapQuestionnaireTypeToFormType(u.getQid()).equals(formType));
    // TODO deal with possible NPE
  }

  @AllArgsConstructor
  @EqualsAndHashCode
  @ToString
  private class Key {
    private String caseType;
    private String addressLevel;
    private String formType;
    private boolean hasOtherValidReceiptForFormType;
  }

  private interface BlankQreRule {
    void run(Case caze, EventTypeDTO causeEventType);
  }

  private class UnreceiptCaseAndSendToField implements BlankQreRule {
    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      Metadata metadata = buildMetadata(causeEventType, ActionInstructionType.UPDATE, true);
      caseService.unreceiptCase(caze, metadata);
    }
  }
}
