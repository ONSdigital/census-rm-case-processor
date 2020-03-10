package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class FieldCaseSelected {
  private long caseRef;
  private String actionRuleId;
}
