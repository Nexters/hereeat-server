package com.yogieat.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.external.kakao.KakaoPlaceClient;
import com.yogieat.external.kakao.KakaoPlaceDetailClient;
import com.yogieat.external.kakao.result.KaKaoPlaceDocumentResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class RestaurantAdminLookupServiceTest {

    @Mock
    private ObjectProvider<KakaoPlaceClient> kakaoPlaceClientProvider;

    @Mock
    private ObjectProvider<KakaoPlaceDetailClient> kakaoPlaceDetailClientProvider;

    @Mock
    private KakaoPlaceClient kakaoPlaceClient;

    @Mock
    private KakaoPlaceDetailClient kakaoPlaceDetailClient;

    @Test
    void searchByKeyword_ShouldPrefetchPlaceDetails_WhenSearchResultsExist() {
        RestaurantAdminLookupService service =
                new RestaurantAdminLookupService(kakaoPlaceClientProvider, kakaoPlaceDetailClientProvider);
        List<KaKaoPlaceDocumentResult> results = List.of(
                document("place-1"),
                document("place-2")
        );

        when(kakaoPlaceClientProvider.getIfAvailable()).thenReturn(kakaoPlaceClient);
        when(kakaoPlaceDetailClientProvider.getIfAvailable()).thenReturn(kakaoPlaceDetailClient);
        when(kakaoPlaceClient.searchPlaces("파스타", null, 5)).thenReturn(results);

        List<KaKaoPlaceDocumentResult> actual = service.searchByKeyword(" 파스타 ", 5);

        assertThat(actual).isEqualTo(results);
        verify(kakaoPlaceDetailClient).prefetchPlaceDetailResult("place-1");
        verify(kakaoPlaceDetailClient).prefetchPlaceDetailResult("place-2");
    }

    @Test
    void searchByKeyword_ShouldNotResolveClients_WhenKeywordBlank() {
        RestaurantAdminLookupService service =
                new RestaurantAdminLookupService(kakaoPlaceClientProvider, kakaoPlaceDetailClientProvider);

        List<KaKaoPlaceDocumentResult> actual = service.searchByKeyword(" ", 5);

        assertThat(actual).isEmpty();
        verify(kakaoPlaceClientProvider, never()).getIfAvailable();
        verify(kakaoPlaceDetailClientProvider, never()).getIfAvailable();
    }

    private KaKaoPlaceDocumentResult document(String id) {
        return new KaKaoPlaceDocumentResult(
                id,
                "place",
                "category",
                "FD6",
                "food",
                "02-1234-5678",
                "address",
                "road address",
                "127.0",
                "37.0",
                "https://place.map.kakao.com/" + id,
                ""
        );
    }
}
