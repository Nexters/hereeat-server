package com.yogieat.region;

import static org.assertj.core.api.Assertions.assertThat;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.gathering.GatheringEntity;
import com.yogieat.datasource.db.core.gathering.GatheringJpaRepository;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.datasource.db.core.restaurant.RestaurantEntity;
import com.yogieat.datasource.db.core.restaurant.RestaurantJpaRepository;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.domain.value.TimeSlot;
import com.yogieat.gathering.service.GatheringAdminCriteria;
import com.yogieat.gathering.service.GatheringRepository;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegionMigrationIntegrationTest {

    @Autowired
    private RegionService regionService;

    @Autowired
    private RegionJpaRepository regionJpaRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantJpaRepository restaurantJpaRepository;

    @Autowired
    private GatheringRepository gatheringRepository;

    @Autowired
    private GatheringJpaRepository gatheringJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        regionJpaRepository.deleteAllInBatch();
        regionJpaRepository.saveAllAndFlush(seedRegions());
    }

    @Test
    @DisplayName("admin 지역 목록은 active 이면서 enum 매핑 가능한 지역만 sort_order 순으로 반환한다")
    void findActiveRegionsForAdmin_ShouldReturnOnlyActiveMappableRegions() {
        assertThat(regionJpaRepository.findAll()).hasSize(Region.values().length);

        RegionEntity hongdae = regionJpaRepository.findByCode(Region.HONGDAE.name()).orElseThrow();
        hongdae.apply(new RegionMaster(
                hongdae.getId(),
                hongdae.getCode(),
                hongdae.getDisplayName(),
                new GeoJson.Point(List.of(hongdae.getLongitude(), hongdae.getLatitude())),
                false,
                hongdae.getSortOrder(),
                hongdae.getCreatedAt(),
                hongdae.getUpdatedAt()
        ));
        regionJpaRepository.save(hongdae);

        regionJpaRepository.save(RegionEntity.of(new RegionMaster(
                null,
                "YEOKSAM",
                "역삼역",
                new GeoJson.Point(List.of(127.033, 37.5006)),
                true,
                -1,
                null,
                null
        )));

        List<RegionMaster> regions = regionService.findActiveRegions();

        assertThat(regions).extracting(RegionMaster::code)
                .doesNotContain("YEOKSAM", Region.HONGDAE.name())
                .contains(Region.GANGNAM.name());
        assertThat(regions.getFirst().code()).isEqualTo(Region.GANGNAM.name());
    }

    @Test
    @DisplayName("맛집과 모임은 저장 시 region enum과 region_id를 함께 저장한다")
    void dualWrite_ShouldPopulateRegionIdsOnWrite() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode(Region.GANGNAM.name()).orElseThrow();
        Long hongdaeRegionId = regionJpaRepository.findIdByCode(Region.HONGDAE.name()).orElseThrow();

        restaurantRepository.save(createRestaurant("ext-dual-write", Region.GANGNAM));
        RestaurantEntity dualWrittenRestaurant = restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull("ext-dual-write").orElseThrow();
        assertThat(dualWrittenRestaurant.getRegion()).isEqualTo(Region.GANGNAM);
        assertThat(dualWrittenRestaurant.getRegionId()).isEqualTo(gangnamRegionId);

        restaurantRepository.applyAdminPatch(dualWrittenRestaurant.getId(), regionOnlyPatch(Region.HONGDAE));
        RestaurantEntity patchedRestaurant = restaurantJpaRepository.findByIdAndDeletedAtIsNull(dualWrittenRestaurant.getId()).orElseThrow();
        assertThat(patchedRestaurant.getRegion()).isEqualTo(Region.HONGDAE);
        assertThat(patchedRestaurant.getRegionId()).isEqualTo(hongdaeRegionId);

        gatheringRepository.save(new Gathering(
                null,
                "dual-write-access",
                "dual-write-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.DINNER,
                Region.GANGNAM,
                4,
                null,
                null,
                null
        ));
        GatheringEntity dualWrittenGathering = gatheringJpaRepository.findByAccessKey("dual-write-access").orElseThrow();
        assertThat(dualWrittenGathering.getRegion()).isEqualTo(Region.GANGNAM);
        assertThat(dualWrittenGathering.getRegionId()).isEqualTo(gangnamRegionId);
    }

    @Test
    @DisplayName("region 기반 조회는 region_id를 우선 사용하고 legacy 컬럼은 fallback 으로만 사용한다")
    void regionReads_ShouldPreferRegionIdAndFallbackToLegacyColumn() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode(Region.GANGNAM.name()).orElseThrow();

        RestaurantEntity regionIdOnlyRestaurant = restaurantJpaRepository.save(
                RestaurantEntity.from(createRestaurant("ext-region-id-only", Region.GANGNAM), gangnamRegionId)
        );
        ReflectionTestUtils.setField(regionIdOnlyRestaurant, "region", null);
        restaurantJpaRepository.save(regionIdOnlyRestaurant);
        Long regionIdOnlyRestaurantId = regionIdOnlyRestaurant.getId();

        RestaurantEntity legacyFallbackRestaurant = restaurantJpaRepository.save(
                RestaurantEntity.from(createRestaurant("ext-legacy-fallback", Region.HONGDAE))
        );
        ReflectionTestUtils.setField(legacyFallbackRestaurant, "regionId", null);
        restaurantJpaRepository.save(legacyFallbackRestaurant);

        RestaurantEntity staleLegacyRestaurant = restaurantJpaRepository.save(
                RestaurantEntity.from(createRestaurant("ext-stale-legacy", Region.GANGNAM), gangnamRegionId)
        );
        ReflectionTestUtils.setField(staleLegacyRestaurant, "region", Region.HONGDAE);
        restaurantJpaRepository.save(staleLegacyRestaurant);
        Long staleLegacyRestaurantId = staleLegacyRestaurant.getId();

        GatheringEntity regionIdOnlyGathering = gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "region-id-only-access",
                "region-id-only-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.DINNER,
                Region.GANGNAM,
                4,
                null,
                null,
                null
        ), gangnamRegionId));
        ReflectionTestUtils.setField(regionIdOnlyGathering, "region", null);
        gatheringJpaRepository.save(regionIdOnlyGathering);

        GatheringEntity legacyFallbackGathering = gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "legacy-fallback-access",
                "legacy-fallback-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.LUNCH,
                Region.SAMGAKJI,
                3,
                null,
                null,
                null
        )));
        ReflectionTestUtils.setField(legacyFallbackGathering, "regionId", null);
        gatheringJpaRepository.save(legacyFallbackGathering);

        GatheringEntity staleLegacyGathering = gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "stale-legacy-access",
                "stale-legacy-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.DINNER,
                Region.GANGNAM,
                5,
                null,
                null,
                null
        ), gangnamRegionId));
        ReflectionTestUtils.setField(staleLegacyGathering, "region", Region.HONGDAE);
        gatheringJpaRepository.save(staleLegacyGathering);

        entityManager.flush();
        entityManager.clear();

        List<Restaurant> gangnamRestaurants = restaurantRepository.findByRegion(Region.GANGNAM);
        assertThat(gangnamRestaurants)
                .extracting(Restaurant::externalId)
                .containsExactlyInAnyOrder("ext-region-id-only", "ext-stale-legacy");
        assertThat(gangnamRestaurants)
                .extracting(Restaurant::region)
                .containsOnly(Region.GANGNAM);
        assertThat(restaurantRepository.countByRegion(Region.GANGNAM)).isEqualTo(2L);

        List<Restaurant> hongdaeRestaurants = restaurantRepository.findByRegion(Region.HONGDAE);
        assertThat(hongdaeRestaurants)
                .extracting(Restaurant::externalId)
                .containsExactly("ext-legacy-fallback");
        assertThat(hongdaeRestaurants)
                .extracting(Restaurant::region)
                .containsOnly(Region.HONGDAE);

        List<Restaurant> gangnamRecommendationCandidates = restaurantRepository.findRecommendationCandidates(
                Region.GANGNAM,
                List.of(1L),
                TimeSlot.LUNCH
        );
        assertThat(gangnamRecommendationCandidates)
                .extracting(Restaurant::id)
                .containsExactlyInAnyOrder(regionIdOnlyRestaurantId, staleLegacyRestaurantId);
        assertThat(gangnamRecommendationCandidates)
                .extracting(Restaurant::region)
                .containsOnly(Region.GANGNAM);

        List<RestaurantAdminListItemResult> gangnamAdminRestaurants = restaurantRepository.findPageRestaurants(
                RestaurantAdminListCriteria.of(null, Region.GANGNAM, null, null),
                0,
                20
        );
        assertThat(gangnamAdminRestaurants)
                .extracting(RestaurantAdminListItemResult::region)
                .containsOnly(Region.GANGNAM);
        assertThat(restaurantRepository.countAdminRestaurantList(
                RestaurantAdminListCriteria.of(null, Region.GANGNAM, null, null)
        )).isEqualTo(2L);

        List<Gathering> gangnamGatherings = gatheringRepository.findAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.GANGNAM, null, false)
        );
        assertThat(gangnamGatherings)
                .extracting(Gathering::accessKey)
                .containsExactlyInAnyOrder("region-id-only-access", "stale-legacy-access");
        assertThat(gangnamGatherings)
                .extracting(Gathering::region)
                .containsOnly(Region.GANGNAM);

        List<Gathering> samgakjiGatherings = gatheringRepository.findAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.SAMGAKJI, null, false)
        );
        assertThat(samgakjiGatherings)
                .extracting(Gathering::accessKey)
                .containsExactly("legacy-fallback-access");
        assertThat(samgakjiGatherings)
                .extracting(Gathering::region)
                .containsOnly(Region.SAMGAKJI);
        assertThat(gatheringRepository.countAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.GANGNAM, null, false)
        )).isEqualTo(2L);
    }

    private static CreateRestaurant createRestaurant(String externalId, Region region) {
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
                region,
                new GeoJson.Point(List.of(127.0, 37.5)),
                10,
                5,
                "대표 메뉴",
                12000,
                "MEDIUM",
                "요약 제목",
                "[\"요약\"]",
                TimeSlot.LUNCH
        );
    }

    private static RestaurantCommand.Patch regionOnlyPatch(Region region) {
        return new RestaurantCommand.Patch(
                null,
                null,
                null,
                null,
                region,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static List<RegionEntity> seedRegions() {
        List<RegionEntity> seededRegions = new ArrayList<>();
        Region[] regions = Region.values();
        for (int index = 0; index < regions.length; index++) {
            Region region = regions[index];
            seededRegions.add(RegionEntity.of(new RegionMaster(
                    null,
                    region.name(),
                    region.getName(),
                    region.getCoordinatesStandard(),
                    true,
                    index,
                    null,
                    null
            )));
        }
        return seededRegions;
    }
}
