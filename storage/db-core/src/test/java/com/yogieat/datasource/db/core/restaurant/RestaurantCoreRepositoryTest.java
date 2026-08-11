package com.yogieat.datasource.db.core.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.sync.domain.RestaurantSyncTarget;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("saveOrRevive는 소프트 삭제된 row가 있으면 새로 insert하지 않고 되살린다")
    void saveOrRevive_ShouldReviveSoftDeletedEntity_WhenExternalIdBelongsToDeletedRow() {
        String externalId = "1440647231";
        CreateRestaurant createRestaurant = createRestaurantFixture(externalId);
        RestaurantEntity deletedEntity = mock(RestaurantEntity.class);
        when(deletedEntity.getId()).thenReturn(2012L);
        when(deletedEntity.getDeletedAt()).thenReturn(LocalDateTime.now().minusMonths(3));
        when(deletedEntity.getRegionId()).thenReturn(9L);
        when(restaurantJpaRepository.findByExternalId(externalId)).thenReturn(Optional.of(deletedEntity));

        Restaurant result = restaurantCoreRepository.saveOrRevive(createRestaurant, 9L);

        assertThat(result.id()).isEqualTo(2012L);
        verify(deletedEntity).applyRevive(createRestaurant, 9L);
        verify(restaurantJpaRepository, never()).save(any(RestaurantEntity.class));
    }

    @Test
    @DisplayName("saveOrRevive는 external_id가 없으면 새로 insert한다")
    void saveOrRevive_ShouldInsertNewEntity_WhenExternalIdDoesNotExist() {
        String externalId = "838112827";
        CreateRestaurant createRestaurant = createRestaurantFixture(externalId);
        RestaurantEntity savedEntity = mock(RestaurantEntity.class);
        when(savedEntity.getId()).thenReturn(64L);
        when(restaurantJpaRepository.findByExternalId(externalId)).thenReturn(Optional.empty());
        when(restaurantJpaRepository.save(any(RestaurantEntity.class))).thenReturn(savedEntity);

        Restaurant result = restaurantCoreRepository.saveOrRevive(createRestaurant, 8L);

        assertThat(result.id()).isEqualTo(64L);
        verify(restaurantJpaRepository).save(any(RestaurantEntity.class));
    }

    @Test
    @DisplayName("saveOrRevive는 external_id가 삭제되지 않은 채 존재하면 되살리지 않고 새로 insert한다")
    void saveOrRevive_ShouldInsertNewEntity_WhenExistingEntityIsNotDeleted() {
        String externalId = "838112827";
        CreateRestaurant createRestaurant = createRestaurantFixture(externalId);
        RestaurantEntity activeEntity = mock(RestaurantEntity.class);
        when(activeEntity.getDeletedAt()).thenReturn(null);
        RestaurantEntity savedEntity = mock(RestaurantEntity.class);
        when(savedEntity.getId()).thenReturn(99L);
        when(restaurantJpaRepository.findByExternalId(externalId)).thenReturn(Optional.of(activeEntity));
        when(restaurantJpaRepository.save(any(RestaurantEntity.class))).thenReturn(savedEntity);

        Restaurant result = restaurantCoreRepository.saveOrRevive(createRestaurant, 8L);

        assertThat(result.id()).isEqualTo(99L);
        verify(activeEntity, never()).applyRevive(any(), any());
        verify(restaurantJpaRepository).save(any(RestaurantEntity.class));
    }

    private CreateRestaurant createRestaurantFixture(String externalId) {
        return new CreateRestaurant(
                externalId,
                1L,
                "restaurant-" + externalId,
                "서울시 어딘가",
                4.3,
                "image-url",
                "map-url",
                "대표 리뷰",
                "설명",
                Region.fromString("HONGDAE"),
                new GeoJson.Point(List.of(127.0, 37.5)),
                10,
                5,
                "대표 메뉴",
                12000,
                "MEDIUM",
                "요약 제목",
                "[\"요약\"]",
                TimeSlot.LUNCH,
                null,
                "010-0000-0000",
                null,
                null,
                Boolean.TRUE
        );
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
