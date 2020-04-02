package uk.gov.ons.census.casesvc.utility;

import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;

public class MetadataHelper {

  public static Metadata buildMetadata(
      EventTypeDTO eventType, ActionInstructionType actionInstructionType) {
    return buildMetadata(eventType, actionInstructionType, null);
  }

  public static Metadata buildMetadata(
      EventTypeDTO eventType,
      ActionInstructionType actionInstructionType,
      Boolean blankQuestionnaireReceived) {
    Metadata metadata = new Metadata();
    metadata.setCauseEventType(eventType);
    metadata.setFieldDecision(actionInstructionType);
    metadata.setBlankQuestionnaireReceived(blankQuestionnaireReceived);
    return metadata;
  }
}
