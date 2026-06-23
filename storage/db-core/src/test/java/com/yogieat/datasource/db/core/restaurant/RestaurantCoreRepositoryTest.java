package com.yogieat.datasource.db.core.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RestaurantCoreRepositoryTest {

    @Mock
    private RestaurantJpaRepository restaurantJpaRepository;

    @Mock
    private RegionJpaRepository regionJpaRepository;

    @Mock
    private JPAQueryFactory jpaQueryFactory;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private RestaurantCoreRepository restaurantCoreRepository;

    @Test
    void findSyncTargetsByIds_includesAdminEditableFields() {
        RestaurantEntity restaurant = restaurantEntity();
        RegionEntity region = regionEntity();
        when(restaurantJpaRepository.findByIdInAndDeletedAtIsNull(List.of(1L)))
                .thenReturn(List.of(restaurant));
        when(regionJpaRepository.findByIdInAndDeletedAtIsNull(List.of(10L)))
                .thenReturn(List.of(region));

        List<RestaurantSyncTarget> targets = restaurantCoreRepository.findSyncTargetsByIds(List.of(1L));

        assertThat(targets).singleElement()
                .satisfies(target -> {
                    assertThat(target.id()).isEqualTo(1L);
                    assertThat(target.imageUrl()).isEqualTo("https://admin.example.com/image.jpg");
                    assertThat(target.aiMateSummaryTitle()).isEqualTo("관리자 요약");
                    assertThat(target.aiMateSummaryContents()).isEqualTo("[\"관리자\", \"요약\"]");
                    assertThat(target.categoryId()).isEqualTo(77L);
                });
    }

    private RestaurantEntity restaurantEntity() {
        RestaurantEntity restaurant = mock(RestaurantEntity.class);
        when(restaurant.getId()).thenReturn(1L);
        when(restaurant.getName()).thenReturn("와인코르크");
        when(restaurant.getRegionId()).thenReturn(10L);
        when(restaurant.getExternalId()).thenReturn("123");
        when(restaurant.getLocation()).thenReturn(new GeometryFactory()
                .createPoint(new Coordinate(127.0280, 37.4980)));
        when(restaurant.getImageUrl()).thenReturn("https://admin.example.com/image.jpg");
        when(restaurant.getAiMateSummaryTitle()).thenReturn("관리자 요약");
        when(restaurant.getAiMateSummaryContents()).thenReturn("[\"관리자\", \"요약\"]");
        when(restaurant.getCategoryId()).thenReturn(77L);
        return restaurant;
    }

    private RegionEntity regionEntity() {
        RegionEntity region = mock(RegionEntity.class);
        when(region.getId()).thenReturn(10L);
        when(region.getCode()).thenReturn("GANGNAM");
        when(region.getProvince()).thenReturn("서울");
        when(region.getDisplayName()).thenReturn("강남역");
        when(region.getLongitude()).thenReturn(127.0276);
        when(region.getLatitude()).thenReturn(37.4979);
        when(region.getStatus()).thenReturn(RegionStatus.ACTIVE);
        when(region.getSortOrder()).thenReturn(1);
        return region;
    }
}
