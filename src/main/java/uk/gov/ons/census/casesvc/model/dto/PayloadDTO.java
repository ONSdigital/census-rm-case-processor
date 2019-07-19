package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
public class PayloadDTO {

  //  @JsonInclude(Include.NON_NULL)
  //  private RefusalType type;
  //
  //  @JsonInclude(Include.NON_NULL)
  //  private String report;
  //
  //  @JsonInclude(Include.NON_NULL)
  //  private String agentId;

  @JsonInclude(Include.NON_NULL)
  private CollectionCase collectionCase;

  @JsonInclude(Include.NON_NULL)
  private UacDTO uac;

  @JsonInclude(Include.NON_NULL)
  private PrintCaseSelected printCaseSelected;
}
