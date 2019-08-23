package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.persistence.criteria.CriteriaBuilder;

@Data
public class PayloadDTO {

  @JsonInclude(Include.NON_NULL)
  private CollectionCase collectionCase;

  @JsonInclude(Include.NON_NULL)
  private UacDTO uac;

  @JsonInclude(Include.NON_NULL)
  private PrintCaseSelected printCaseSelected;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("response")
  private ReceiptDTO receipt;

  @JsonInclude(Include.NON_NULL)
  private RefusalDTO refusal;

  @JsonInclude(Include.NON_NULL)
  private FulfilmentRequestDTO fulfilmentRequest;

  @JsonInclude(Include.NON_NULL)
  private UacCreatedDTO uacQidCreated;

  @JsonInclude(Include.NON_NULL)
  private InvalidAddress invalidAddress;

}
