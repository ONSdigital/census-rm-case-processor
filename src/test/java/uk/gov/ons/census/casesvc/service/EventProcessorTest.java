package uk.gov.ons.census.casesvc.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EventProcessorTest {

    @Mock
    CaseProcessor caseProcessor;

    @Mock UacProcessor uacProcessor;

    @InjectMocks EventProcessor underTest;

    @Test
    public void testHappyPath() {
        // Given
        CreateCaseSample createCaseSample = new CreateCaseSample();
        Case caze = new Case();
        caze.setTreatmentCode("HH_LF2R3BE");
        when(caseProcessor.saveCase(createCaseSample)).thenReturn(caze);
        UacQidLink uacQidLink = new UacQidLink();
        when(uacProcessor.saveUacQidLink(caze, 1)).thenReturn(uacQidLink);

        // When
        underTest.processSampleReceivedMessage(createCaseSample);

        // Then
        verify(caseProcessor).saveCase(createCaseSample);
        verify(uacProcessor).saveUacQidLink(eq(caze), eq(1));
        verify(uacProcessor).emitUacUpdatedEvent(uacQidLink, caze);
        verify(caseProcessor).emitCaseCreatedEvent(caze);
        verify(uacProcessor, times(2)).logEvent(eq(uacQidLink), any(String.class));
    }

    @Test
    public void testWelshQuestionnaire() {
        // Given
        CreateCaseSample createCaseSample = new CreateCaseSample();
        Case caze = new Case();
        caze.setTreatmentCode("HH_QF2R1W");
        when(caseProcessor.saveCase(createCaseSample)).thenReturn(caze);
        UacQidLink uacQidLink = new UacQidLink();
        UacQidLink secondUacQidLink = new UacQidLink();
        when(uacProcessor.saveUacQidLink(caze, 1)).thenReturn(uacQidLink);
        when(uacProcessor.saveUacQidLink(caze, 2)).thenReturn(uacQidLink);

        // When
        underTest.processSampleReceivedMessage(createCaseSample);

        // Then
        verify(caseProcessor).saveCase(createCaseSample);
        verify(uacProcessor, times(1)).saveUacQidLink(eq(caze), eq(2));
        verify(uacProcessor).emitUacUpdatedEvent(uacQidLink, caze);
        verify(caseProcessor).emitCaseCreatedEvent(caze);
        verify(uacProcessor, times(2)).logEvent(eq(uacQidLink), any(String.class));
    }
}
