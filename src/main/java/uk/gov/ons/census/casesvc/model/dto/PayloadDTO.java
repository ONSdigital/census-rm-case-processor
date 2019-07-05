package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class PayloadDTO {

  private CollectionCase collectionCase;

  private UacDTO uac;

  private PrintCaseSelected printCaseSelected;
}
