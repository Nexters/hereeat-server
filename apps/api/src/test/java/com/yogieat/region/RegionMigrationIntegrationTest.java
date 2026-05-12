package com.yogieat.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
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
import com.yogieat.region.domain.RegionSummary;
import com.yogieat.region.service.RegionCommand;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.domain.Restaurant;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.service.RestaurantAdminListCriteria;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
    @DisplayName("앱 지역 목록은 active DB region 을 sort_order 순으로 모두 반환한다")
    void findActiveRegionsForApp_ShouldReturnAllActiveDbRegions() {
        RegionEntity hongdae = regionJpaRepository.findByCodeAndDeletedAtIsNull("HONGDAE").orElseThrow();
        hongdae.apply(new RegionMaster(
                hongdae.getId(),
                hongdae.getCode(),
                hongdae.getProvince(),
                hongdae.getDisplayName(),
                new GeoJson.Point(List.of(hongdae.getLongitude(), hongdae.getLatitude())),
                false,
                hongdae.getSortOrder(),
                hongdae.getCreatedAt(),
                hongdae.getUpdatedAt()
        ));
        regionJpaRepository.save(hongdae);

        List<RegionMaster> regions = regionService.findActiveRegions();

        assertThat(regions)
                .extracting(RegionMaster::code)
                .containsExactly(
                        "GANGNAM",
                        "GONGDEOK",
                        "EULJIRO3GA",
                        "SADANG",
                        "JONGNO3GA",
                        "JAMSIL",
                        "SAMGAKJI",
                        "SEONGSU"
                );
    }

    @Test
    @DisplayName("admin region 생성과 dashboard 조회는 DB 전용 region과 맛집 수를 반영한다")
    void createRegionAndDashboard_ShouldSupportDbOnlyRegion() {
        RegionMaster yeoksam = regionService.createRegion(new RegionCommand.Create(
                "YEOKSAM",
                "서울",
                "역삼역",
                new GeoJson.Point(List.of(127.033, 37.5006)),
                true,
                null
        ));

        restaurantRepository.save(createRestaurant("ext-yeoksam", null), yeoksam.id());

        List<RegionMaster> adminRegions = regionService.findAllRegions();
        assertThat(adminRegions)
                .extracting(RegionMaster::code)
                .contains("YEOKSAM");

        List<RegionSummary> activeSummaries = regionService.findActiveRegionSummaries();
        assertThat(activeSummaries)
                .extracting(summary -> summary.region().code())
                .contains("YEOKSAM");

        assertThat(regionService.findActiveRegions())
                .extracting(RegionMaster::code)
                .contains("YEOKSAM");

        RegionSummary yeoksamSummary = regionService.findRegionDashboard(null).stream()
                .filter(summary -> "YEOKSAM".equals(summary.region().code()))
                .findFirst()
                .orElseThrow();
        assertThat(yeoksamSummary.region().displayName()).isEqualTo("역삼역");
        assertThat(yeoksamSummary.restaurantCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("admin region 부분 수정은 전달된 필드만 반영한다")
    void updateRegion_ShouldApplyOnlyPatchedFields() {
        RegionMaster yeoksam = regionService.createRegion(new RegionCommand.Create(
                "YEOKSAM",
                "서울",
                "역삼역",
                new GeoJson.Point(List.of(127.033, 37.5006)),
                true,
                8
        ));

        regionService.updateRegion(yeoksam.id(), new RegionCommand.Patch(
                null,
                "경기",
                "역삼",
                null,
                false,
                3
        ));

        RegionSummary updatedRegion = regionService.getRegionSummaryById(yeoksam.id());
        assertThat(updatedRegion.region().code()).isEqualTo("YEOKSAM");
        assertThat(updatedRegion.region().province()).isEqualTo("경기");
        assertThat(updatedRegion.region().displayName()).isEqualTo("역삼");
        assertThat(updatedRegion.region().coordinatesStandard().getCoordinates()).containsExactly(127.033, 37.5006);
        assertThat(updatedRegion.region().active()).isFalse();
        assertThat(updatedRegion.region().sortOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("soft delete 된 region 은 region 조회 경로에서 제외된다")
    void deleteRegion_ShouldExcludeDeletedRegionFromRegionReads() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode("GANGNAM").orElseThrow();
        restaurantJpaRepository.save(RestaurantEntity.from(createRestaurant("ext-soft-delete", Region.fromString("GANGNAM")), gangnamRegionId));

        regionService.deleteRegionById(gangnamRegionId);

        assertThatThrownBy(() -> regionService.getRegionById(gangnamRegionId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_LOCATION_NAME);

        assertThat(regionService.findAllRegions())
                .extracting(RegionMaster::code)
                .doesNotContain(Region.fromString("GANGNAM").name());
        assertThat(regionService.findActiveRegionSummaries())
                .extracting(summary -> summary.region().code())
                .doesNotContain(Region.fromString("GANGNAM").name());
        assertThat(restaurantRepository.findByRegion(Region.fromString("GANGNAM"))).isEmpty();
    }

    @Test
    @DisplayName("맛집과 모임은 저장 시 region_id를 기록하고 조회 시 region 을 복원한다")
    void regionIdWriteAndRead_ShouldUseRegionId() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode("GANGNAM").orElseThrow();
        Long hongdaeRegionId = regionJpaRepository.findIdByCode("HONGDAE").orElseThrow();

        restaurantRepository.save(createRestaurant("ext-dual-write", Region.fromString("GANGNAM")));
        RestaurantEntity dualWrittenRestaurant = restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull("ext-dual-write").orElseThrow();
        assertThat(dualWrittenRestaurant.getRegionId()).isEqualTo(gangnamRegionId);

        Restaurant patchedRestaurantResult = restaurantRepository.applyAdminPatch(
                dualWrittenRestaurant.getId(),
                regionOnlyPatch(Region.fromString("HONGDAE"))
        );
        RestaurantEntity patchedRestaurant = restaurantJpaRepository.findByIdAndDeletedAtIsNull(dualWrittenRestaurant.getId()).orElseThrow();
        assertThat(patchedRestaurant.getRegionId()).isEqualTo(hongdaeRegionId);
        assertThat(patchedRestaurantResult.region()).isEqualTo(Region.fromString("HONGDAE"));

        Gathering savedGathering = gatheringRepository.save(new Gathering(
                null,
                "dual-write-access",
                "dual-write-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.DINNER,
                Region.fromString("GANGNAM"),
                4,
                null,
                null,
                null
        ));
        GatheringEntity dualWrittenGathering = gatheringJpaRepository.findByAccessKey("dual-write-access").orElseThrow();
        assertThat(dualWrittenGathering.getRegionId()).isEqualTo(gangnamRegionId);
        assertThat(savedGathering.region()).isEqualTo(Region.fromString("GANGNAM"));
    }

    @Test
    @DisplayName("region 기반 조회는 region_id만으로 동작한다")
    void regionReads_ShouldUseRegionIdOnly() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode("GANGNAM").orElseThrow();
        Long hongdaeRegionId = regionJpaRepository.findIdByCode("HONGDAE").orElseThrow();

        RestaurantEntity regionIdOnlyRestaurant = restaurantJpaRepository.save(
                RestaurantEntity.from(createRestaurant("ext-region-id-only", Region.fromString("GANGNAM")), gangnamRegionId)
        );
        Long regionIdOnlyRestaurantId = regionIdOnlyRestaurant.getId();

        restaurantJpaRepository.save(
                RestaurantEntity.from(createRestaurant("ext-hongdae-only", Region.fromString("HONGDAE")), hongdaeRegionId)
        );

        GatheringEntity regionIdOnlyGathering = gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "region-id-only-access",
                "region-id-only-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.DINNER,
                Region.fromString("GANGNAM"),
                4,
                null,
                null,
                null
        ), gangnamRegionId));

        gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "hongdae-access",
                "hongdae-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.LUNCH,
                Region.fromString("HONGDAE"),
                3,
                null,
                null,
                null
        ), hongdaeRegionId));

        entityManager.flush();
        entityManager.clear();

        List<Restaurant> gangnamRestaurants = restaurantRepository.findByRegion(Region.fromString("GANGNAM"));
        assertThat(gangnamRestaurants)
                .extracting(Restaurant::externalId)
                .containsExactly("ext-region-id-only");
        assertThat(gangnamRestaurants)
                .extracting(Restaurant::region)
                .containsOnly(Region.fromString("GANGNAM"));
        assertThat(restaurantRepository.countByRegion(Region.fromString("GANGNAM"))).isEqualTo(1L);

        List<Restaurant> hongdaeRestaurants = restaurantRepository.findByRegion(Region.fromString("HONGDAE"));
        assertThat(hongdaeRestaurants)
                .extracting(Restaurant::externalId)
                .containsExactly("ext-hongdae-only");
        assertThat(hongdaeRestaurants)
                .extracting(Restaurant::region)
                .containsOnly(Region.fromString("HONGDAE"));

        List<Restaurant> gangnamRecommendationCandidates = restaurantRepository.findRecommendationCandidates(
                Region.fromString("GANGNAM"),
                List.of(1L),
                TimeSlot.LUNCH
        );
        assertThat(gangnamRecommendationCandidates)
                .extracting(Restaurant::id)
                .containsExactly(regionIdOnlyRestaurantId);
        assertThat(gangnamRecommendationCandidates)
                .extracting(Restaurant::region)
                .containsOnly(Region.fromString("GANGNAM"));

        List<RestaurantAdminListItemResult> gangnamAdminRestaurants = restaurantRepository.findPageRestaurants(
                RestaurantAdminListCriteria.of(null, Region.fromString("GANGNAM"), null, null),
                0,
                20
        );
        assertThat(gangnamAdminRestaurants)
                .extracting(RestaurantAdminListItemResult::region)
                .containsOnly(Region.fromString("GANGNAM"));
        assertThat(restaurantRepository.countAdminRestaurantList(
                RestaurantAdminListCriteria.of(null, Region.fromString("GANGNAM"), null, null)
        )).isEqualTo(1L);
        RestaurantAdminResult.Detail adminRestaurantDetail = restaurantRepository
                .findAdminRestaurantDetailById(regionIdOnlyRestaurantId)
                .orElseThrow();
        assertThat(adminRestaurantDetail.region()).isEqualTo(Region.fromString("GANGNAM"));

        List<Gathering> gangnamGatherings = gatheringRepository.findAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.fromString("GANGNAM"), null, false)
        );
        assertThat(gangnamGatherings)
                .extracting(Gathering::accessKey)
                .containsExactly("region-id-only-access");
        assertThat(gangnamGatherings)
                .extracting(Gathering::region)
                .containsOnly(Region.fromString("GANGNAM"));

        List<Gathering> hongdaeGatherings = gatheringRepository.findAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.fromString("HONGDAE"), null, false)
        );
        assertThat(hongdaeGatherings)
                .extracting(Gathering::accessKey)
                .containsExactly("hongdae-access");
        assertThat(hongdaeGatherings)
                .extracting(Gathering::region)
                .containsOnly(Region.fromString("HONGDAE"));
        assertThat(gatheringRepository.countAdminGatherings(
                GatheringAdminCriteria.List.of(null, Region.fromString("GANGNAM"), null, false)
        )).isEqualTo(1L);
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
                TimeSlot.LUNCH,
                null,
                "010-0000-0000",
                null,
                null,
                Boolean.TRUE
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
                null,
                null,
                null,
                null
        );
    }

    private static List<RegionEntity> seedRegions() {
        return List.of(
                region("HONGDAE", "서울", "홍대입구역", 126.92378, 37.55684, true, 0),
                region("GANGNAM", "서울", "강남역", 127.0276, 37.4979, true, 1),
                region("GONGDEOK", "서울", "공덕역", 126.9507, 37.54437, true, 2),
                region("EULJIRO3GA", "서울", "을지로3가역", 126.99224, 37.56623, true, 3),
                region("SADANG", "서울", "사당역", 126.98231, 37.47625, true, 4),
                region("JONGNO3GA", "서울", "종로3가역", 126.99171, 37.5727, true, 5),
                region("JAMSIL", "서울", "잠실역", 127.10128, 37.51379, true, 6),
                region("SAMGAKJI", "서울", "삼각지역", 126.97346, 37.53453, true, 7),
                region("KONKUK", "서울", "건대입구역", 126.9334, 37.5407, false, 8),
                region("YEOUIDO", "서울", "여의도역", 126.9242, 37.5216, false, 9),
                region("GOSTERM", "서울", "고속터미널역", 127.0047, 37.5047, false, 10),
                region("SEONGSU", "서울", "성수역", 127.0556, 37.5447, true, 11),
                region("SEOMYEON", "부산", "서면역", 129.0593, 35.1579, false, 12),
                region("PANGYO", "경기", "판교역", 127.1112, 37.3947, false, 13),
                region("JEONPO", "부산", "전포역", 129.0632, 35.1549, false, 13),
                region("BUSAN", "부산", "부산역", 129.0421, 35.115, false, 14)
        );
    }

    private static RegionEntity region(
            String code,
            String province,
            String displayName,
            double longitude,
            double latitude,
            boolean active,
            int sortOrder
    ) {
        return RegionEntity.of(new RegionMaster(
                null,
                code,
                province,
                displayName,
                new GeoJson.Point(List.of(longitude, latitude)),
                active,
                sortOrder,
                null,
                null
        ));
    }
}
