package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  private InvalidAddress invalidAddress;
  private FieldCaseSelected fieldCaseSelected;
  private FulfilmentInformation fulfilmentInformation;
  private Metadata metadata;
  private NewAddress newAddress;

  @JsonProperty("CCSProperty")
  private CCSPropertyDTO ccsProperty;

  private AddressModification addressModification;
  private AddressTypeChange addressTypeChange;
  private RmCaseUpdated rmCaseUpdated;
  private RmRevalidateAddress rmRevalidateAddress;
}
