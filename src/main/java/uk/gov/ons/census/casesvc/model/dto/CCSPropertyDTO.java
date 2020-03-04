package uk.gov.ons.census.casesvc.model.dto;

import java.util.List;
import lombok.Data;

@Data
public class CCSPropertyDTO {
  private CollectionCase collectionCase;
  private SampleUnitDTO sampleUnit;
  private List<UacDTO> uac;
  private RefusalDTO refusal;
  private InvalidAddress invalidAddress;
}
