package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class FieldCaseSelected {
  private int caseRef;
  private String actionRuleId;
}
