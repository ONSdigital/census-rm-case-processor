package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.*;
import static uk.gov.ons.census.casesvc.utility.MetadataHelper.buildMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.utility.FieldworkHelper;

@Component
public class BlankQuestionnaireService {

  private CaseService caseService;

  private Map<BlankQuestionnaireService.Key, BiFunction<Case, EventTypeDTO, Case>> rules =
      new HashMap<>();

  public BlankQuestionnaireService(CaseService caseService) {
    this.caseService = caseService;
    setUpRules();
  }

  private void setUpRules() {
    /*
     This table is based on: https://collaborate2.ons.gov.uk/confluence/display/SDC/Process+Flow+for+Blank+PQs

     All No Action Required Rules (don't alter response flag and don't tell field) do not appear in this list
     Invalid scenarios (qid shouldn't be linked to case) are treated like No Action Required. Do Nothing.
    */
    rules.put(new Key("HH", "U", HH_FORM_TYPE, false), unreceiptCaseAndSendToField);
    rules.put(new Key("HI", "U", IND_FORM_TYPE, false), unreceiptCase);
    rules.put(new Key("SPG", "U", HH_FORM_TYPE, false), unreceiptCaseAndSendToField);
  }

  public void handleBlankQuestionnaire(
      Case caze, UacQidLink uacQidLink, EventTypeDTO causeEventType) {
    Key ruleKey = new Key(caze, uacQidLink);

    if (rules.containsKey(ruleKey)) {
      rules.get(ruleKey).apply(caze, causeEventType);
    }
  }

  BiFunction<Case, EventTypeDTO, Case> unreceiptCaseAndSendToField =
      (caze, causeEventType) -> {
        Metadata metadata = null;

        if(FieldworkHelper.shouldSendCaseToField(caze)) {
          // Only send to fieldwork if the case is not refused or address invalid
          metadata = buildMetadata(causeEventType, ActionInstructionType.UPDATE, true);
        }

        caseService.unreceiptCase(caze, metadata);

        return caze;
      };

  BiFunction<Case, EventTypeDTO, Case> unreceiptCase =
      (caze, causeEventType) -> {
        caseService.unreceiptCase(caze, null);
        return caze;
      };

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

    public Key(Case caze, UacQidLink uacQidLink) {
      caseType = caze.getCaseType();
      addressLevel = caze.getAddressLevel();
      formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
      hasOtherValidReceiptForFormType =
          caseHasOtherValidReceiptForFormType(caze, formType, uacQidLink);
    }
  }
}
