package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.ons.census.casesvc.type.RefusalType;

@Data
@NoArgsConstructor
public class RefusalDTO {
  private RefusalType type;
  private String report;
  private String agentId;
  private CollectionCase collectionCase;
}
