package uk.gov.ons.census.casesvc.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.jeasy.random.EasyRandom;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;

@RunWith(MockitoJUnitRunner.class)
public class UacQidCacheTest {
  private static final int CACHE_FETCH = 2;
  private static final int CACHE_MIN = 1;

  @Mock UacQidServiceClient uacQidServiceClient;

  @InjectMocks UacQidCache underTest;

  @Test
  @Ignore
  public void testCachingTopUp() {
    // given
    ReflectionTestUtils.setField(underTest, "cacheFetch", CACHE_FETCH);
    ReflectionTestUtils.setField(underTest, "cacheMin", CACHE_MIN);
    ReflectionTestUtils.setField(underTest, "uacQidGetTimout", 10);

    List<UacQidDTO> uacQids1 = populateUacQidList(1, CACHE_FETCH);
    when(uacQidServiceClient.getUacQids(anyInt(), anyInt())).thenReturn(uacQids1);

    List<UacQidDTO> actualUacQidDtos1 = new ArrayList<>();

    // when  - 1st call as cache is empty
    actualUacQidDtos1.add(underTest.getUacQidPair(1));
    verify(uacQidServiceClient, times(1)).getUacQids(eq(1), eq(CACHE_FETCH));

    // when - some more are called this should top it up
    actualUacQidDtos1.add(underTest.getUacQidPair(1));
    actualUacQidDtos1.add(underTest.getUacQidPair(1));
    actualUacQidDtos1.add(underTest.getUacQidPair(1));

    verify(uacQidServiceClient, atLeast(2)).getUacQids(eq(1), eq(CACHE_FETCH));
    assertThat(actualUacQidDtos1.get(0)).isEqualTo(uacQids1.get(0));
  }

  @Test
  public void testToppingUpRecoversFromFailure() {
    // given
    ReflectionTestUtils.setField(underTest, "cacheFetch", CACHE_FETCH);
    ReflectionTestUtils.setField(underTest, "cacheMin", CACHE_MIN);
    ReflectionTestUtils.setField(underTest, "uacQidGetTimout", 2);

    List<UacQidDTO> uacQids1 = populateUacQidList(1, CACHE_FETCH);

    when(uacQidServiceClient.getUacQids(1, CACHE_FETCH))
        .thenThrow(new RuntimeException("api failed"))
        .thenReturn(uacQids1);

    // when
    try {
      underTest.getUacQidPair(1);
    } catch (RuntimeException e) {
      // then
      UacQidDTO actualUacQidDTO = underTest.getUacQidPair(1);
      assertThat(actualUacQidDTO).isEqualTo(uacQids1.get(0));

      return;
    }

    fail("Expected Exception");
  }

  private List<UacQidDTO> populateUacQidList(int questionnaireType, int cacheSize) {
    EasyRandom easyRandom = new EasyRandom();
    List<UacQidDTO> uacQidDTOS = new ArrayList<>();

    for (int i = 0; i < cacheSize; i++) {
      UacQidDTO uacQidDTO = new UacQidDTO();
      uacQidDTO.setQid(questionnaireType + easyRandom.nextObject(String.class));
      uacQidDTO.setUac(easyRandom.nextObject(String.class));
      uacQidDTOS.add(uacQidDTO);
    }

    return uacQidDTOS;
  }
}
