package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class PayloadDTO {
  private CollectionCase collectionCase;
  private UacDTO uac;
  private PrintCaseSelected printCaseSelected;
  private ResponseDTO response;
  private RefusalDTO refusal;
  private FulfilmentRequestDTO fulfilmentRequest;
  private UacCreatedDTO uacQidCreated;
  private String addressModification;
  private String addressTypeChange;
  private InvalidAddress invalidAddress;
  private FulfilmentInformation fulfilmentInformation;
}
