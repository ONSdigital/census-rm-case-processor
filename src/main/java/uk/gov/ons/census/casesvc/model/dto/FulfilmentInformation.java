package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class FulfilmentInformation {
  String productCode;
  String caseRef;
  String questionnaireId;
}
