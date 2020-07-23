package uk.gov.ons.census.casesvc.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class PrintCaseSelected {
  private long caseRef;
  private String packCode;
  private UUID actionRuleId;
  private UUID batchId;
}
