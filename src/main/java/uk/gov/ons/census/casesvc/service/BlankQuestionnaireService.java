package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.CONT_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.HH_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.IND_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

  private Map<BlankQuestionnaireService.Key, BlankQuestionnaireRule> rules = new HashMap<>();

  public BlankQuestionnaireService(CaseService caseService) {
    this.caseService = caseService;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/display/SDC/Process+Flow+for+Blank+PQs
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

  public void handleBlankQuestionnaire(
      Case caze, UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    BlankQuestionnaireService.Key ruleKey = makeRulesKey(caze, uacQidLink);

    if (!rules.containsKey(ruleKey)) {
      throw new RuntimeException(ruleKey.toString() + " does not map to any known processing rule");
    }

    BlankQuestionnaireRule rule = rules.get(ruleKey);
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
                    && Objects.equals(mapQuestionnaireTypeToFormType(u.getQid()), formType)
                    && !u.equals(uacQidLink));
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

  private interface BlankQuestionnaireRule {

    void run(Case caze, EventTypeDTO causeEventType);
  }

  private class UnreceiptCaseAndSendToField implements BlankQuestionnaireRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      Metadata metadata = null;
      if (!caze.isRefusalReceived() && !caze.isAddressInvalid()) {
        // Only send to fieldwork if the case is not refused or address invalid
        metadata = buildMetadata(causeEventType, ActionInstructionType.UPDATE, true);
      }
      caseService.unreceiptCase(caze, metadata);
    }
  }

  private class UnreceiptCase implements BlankQuestionnaireRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      caseService.unreceiptCase(caze, null);
    }
  }

  private class NoActionRequired implements BlankQuestionnaireRule {

    @Override
    public void run(Case caze, EventTypeDTO causeEventType) {
      // No Updating, saving or emitting required
    }
  }
}
