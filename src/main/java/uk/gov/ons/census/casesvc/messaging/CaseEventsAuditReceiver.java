package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@MessageEndpoint
public class CaseEventsAuditReceiver {
    @Transactional
    @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
    public void receiveMessage(ResponseManagementEvent event) {
        if (event.getEvent().getType() == EventTypeDTO.SURVEY_LAUNCHED) {
//            eventService.processPrintCaseSelected(event);
            System.out.println("Survey launched");
        } else {
            throw new RuntimeException(); // Unexpected event type received
        }
    }
}
