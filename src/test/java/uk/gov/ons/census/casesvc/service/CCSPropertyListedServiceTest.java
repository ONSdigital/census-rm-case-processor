package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class CCSPropertyListedServiceTest {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final UUID TEST_UAC_QID_LINK_ID = UUID.randomUUID();
  private static final UUID TEST_ACTION_PLAN_ID = UUID.randomUUID();
  private static final UUID TEST_COLLECTION_EXERCISE_ID = UUID.randomUUID();

  @Mock UacService uacService;

  @Mock EventLogger eventLogger;

  @Mock CaseService caseService;

  @InjectMocks CCSPropertyListedService underTest;

  @Test
  public void testGoodCCSPropertyListed() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();

    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(TEST_UAC_QID_LINK_ID);
    expectedUacQidLink.setCcsCase(true);

    Case expectedCase = new Case();
    expectedCase.setCaseId(TEST_CASE_ID);
    expectedCase.setCcsCase(true);
    expectedCase.setUacQidLinks(new LinkedList<>(Collections.singletonList(expectedUacQidLink)));

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    ReflectionTestUtils.setField(underTest, "actionPlanId", TEST_ACTION_PLAN_ID.toString());
    ReflectionTestUtils.setField(
        underTest, "collectionExerciseId", TEST_COLLECTION_EXERCISE_ID.toString());

    when(uacService.buildCCSUacQidLink()).thenReturn(expectedUacQidLink);
    when(caseService.buildCCSCase(
            expectedCaseId,
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            TEST_ACTION_PLAN_ID.toString(),
            TEST_COLLECTION_EXERCISE_ID.toString()))
        .thenReturn(expectedCase);
    when(caseService.saveCCSCaseWithUacQidLink(expectedCase, expectedUacQidLink))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).buildCCSUacQidLink();
    inOrder
        .verify(caseService)
        .buildCCSCase(
            expectedCaseId,
            managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            TEST_ACTION_PLAN_ID.toString(),
            TEST_COLLECTION_EXERCISE_ID.toString());
    inOrder.verify(caseService).saveCCSCaseWithUacQidLink(expectedCase, expectedUacQidLink);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder
        .verify(eventLogger)
        .logCaseEvent(
            caseCaptor.capture(),
            any(OffsetDateTime.class),
            eq("CCS Address Listed"),
            eq(EventType.CCS_ADDRESS_LISTED),
            eq(managementEvent.getEvent()),
            anyString());

    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualCase.isCcsCase()).isTrue();
    assertThat(actualCase.getUacQidLinks().size()).isEqualTo(1);

    UacQidLink actualUacQidLink = actualCase.getUacQidLinks().get(0);
    assertThat(actualUacQidLink.getId()).isEqualTo(TEST_UAC_QID_LINK_ID);
    assertThat(actualUacQidLink.isCcsCase()).isTrue();
  }
}
