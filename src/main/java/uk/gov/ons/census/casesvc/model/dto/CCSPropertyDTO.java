package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class CCSPropertyDTO {
  private CollectionCase collectionCase;
  private SampleUnitDTO sampleUnit;
  private UacDTO uac;
  private RefusalDTO refusal;
}
