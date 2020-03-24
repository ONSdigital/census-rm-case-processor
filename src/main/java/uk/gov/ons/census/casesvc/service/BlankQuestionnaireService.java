package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.CONT_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.HH_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.IND_FORM_TYPE;
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
        new BlankQuestionnaireService.Key("HH", "U", HH_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("HH", "U", HH_FORM_TYPE, false),
        new BlankQuestionnaireService.UnreceiptCaseAndSendToField());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "U", IND_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", IND_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", HH_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("HI", "U", IND_FORM_TYPE, false),
        new BlankQuestionnaireService.UnreceiptCase());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "E", IND_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "U", IND_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", HH_FORM_TYPE, false),
        new BlankQuestionnaireService.UnreceiptCaseAndSendToField());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", IND_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("HH", "U", CONT_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", CONT_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "E", CONT_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "U", CONT_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "E", CONT_FORM_TYPE, false),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("HH", "U", CONT_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "U", CONT_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("SPG", "E", CONT_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "U", CONT_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
    rules.put(
        new BlankQuestionnaireService.Key("CE", "E", CONT_FORM_TYPE, true),
        new BlankQuestionnaireService.NoActionRequired());
  }

  public void handleBlankQuestionnaire(UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    //TODO: think we can use the case from the caller method here instead?

    Case caze = uacQidLink.getCaze();

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
        caseHasOtherValidReceiptForFormType(caze, formType, uacQidLink));
  }

  private boolean caseHasOtherValidReceiptForFormType(
      Case caze, String formType, UacQidLink uacQidLink) {
    return caze.getUacQidLinks().stream()
        .anyMatch(
            u ->
                !u.isActive()
                    && !u.isBlankQuestionnaire()
                    && mapQuestionnaireTypeToFormType(u.getQid()).equals(formType)
                    && !u.equals(uacQidLink));
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

  private class UnreceiptCase implements BlankQreRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      caseService.unreceiptCase(caze, null);
    }
  }

  private class NoActionRequired implements BlankQreRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      // No Updating, saving or emitting required
    }
  }
}
