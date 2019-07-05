package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class PrintCaseSelected {
  private int caseRef;
  private String packCode;
  private String actionRuleId;
  private String batchId;
}
