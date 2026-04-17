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
import com.yogieat.gathering.service.GatheringRepository;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.service.RegionRepository;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.domain.CreateRestaurant;
import com.yogieat.restaurant.service.RestaurantCommand;
import com.yogieat.restaurant.service.RestaurantRepository;
import java.time.LocalDate;
import java.util.List;
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
    private RegionRepository regionRepository;

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

    @Test
    @DisplayName("기동 시 enum 기반 지역 마스터가 생성되고 재동기화 시 운영 필드는 보존된다")
    void startupSync_ShouldSeedRegionsAndPreserveOperationalFieldsOnResync() {
        assertThat(regionJpaRepository.findAll()).hasSize(Region.values().length);

        RegionEntity gangnam = regionJpaRepository.findByCode(Region.GANGNAM.name()).orElseThrow();
        gangnam.apply(new RegionMaster(
                gangnam.getId(),
                gangnam.getCode(),
                "임시 지역명",
                new GeoJson.Point(List.of(0.0, 0.0)),
                false,
                99,
                gangnam.getCreatedAt(),
                gangnam.getUpdatedAt()
        ));
        regionJpaRepository.save(gangnam);

        regionService.syncRegionsFromEnumAndBackfillLegacyReferences();

        RegionEntity reloaded = regionJpaRepository.findByCode(Region.GANGNAM.name()).orElseThrow();
        assertThat(regionJpaRepository.findAll()).hasSize(Region.values().length);
        assertThat(reloaded.getDisplayName()).isEqualTo(Region.GANGNAM.getName());
        assertThat(reloaded.getLongitude()).isEqualTo(Region.GANGNAM.getCoordinatesStandard().getCoordinates().getFirst());
        assertThat(reloaded.getLatitude()).isEqualTo(Region.GANGNAM.getCoordinatesStandard().getCoordinates().get(1));
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getSortOrder()).isEqualTo(99);
    }

    @Test
    @DisplayName("admin 지역 목록은 active 이면서 enum 매핑 가능한 지역만 sort_order 순으로 반환한다")
    void findActiveRegionsForAdmin_ShouldReturnOnlyActiveMappableRegions() {
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

        regionRepository.save(new RegionMaster(
                null,
                "YEOKSAM",
                "역삼역",
                new GeoJson.Point(List.of(127.033, 37.5006)),
                true,
                -1,
                null,
                null
        ));

        List<RegionMaster> regions = regionService.findActiveRegionsForAdmin();

        assertThat(regions).extracting(RegionMaster::code)
                .doesNotContain("YEOKSAM", Region.HONGDAE.name())
                .contains(Region.GANGNAM.name());
        assertThat(regions.getFirst().code()).isEqualTo(Region.GANGNAM.name());
    }

    @Test
    @DisplayName("맛집과 모임은 region enum과 region_id를 함께 저장하고 legacy row는 backfill 된다")
    void dualWriteAndBackfill_ShouldPopulateRegionIds() {
        Long gangnamRegionId = regionJpaRepository.findIdByCode(Region.GANGNAM.name()).orElseThrow();
        Long hongdaeRegionId = regionJpaRepository.findIdByCode(Region.HONGDAE.name()).orElseThrow();
        Long jamsilRegionId = regionJpaRepository.findIdByCode(Region.JAMSIL.name()).orElseThrow();
        Long samgakjiRegionId = regionJpaRepository.findIdByCode(Region.SAMGAKJI.name()).orElseThrow();

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

        restaurantJpaRepository.save(RestaurantEntity.from(createRestaurant("ext-legacy", Region.JAMSIL)));
        gatheringJpaRepository.save(GatheringEntity.from(new Gathering(
                null,
                "legacy-access",
                "legacy-gathering",
                LocalDate.of(2026, 4, 17),
                TimeSlot.LUNCH,
                Region.SAMGAKJI,
                3,
                null,
                null,
                null
        )));

        RestaurantEntity legacyRestaurant = restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull("ext-legacy").orElseThrow();
        GatheringEntity legacyGathering = gatheringJpaRepository.findByAccessKey("legacy-access").orElseThrow();
        assertThat(legacyRestaurant.getRegionId()).isNull();
        assertThat(legacyGathering.getRegionId()).isNull();

        regionService.syncRegionsFromEnumAndBackfillLegacyReferences();

        RestaurantEntity backfilledRestaurant = restaurantJpaRepository.findByExternalIdAndDeletedAtIsNull("ext-legacy").orElseThrow();
        GatheringEntity backfilledGathering = gatheringJpaRepository.findByAccessKey("legacy-access").orElseThrow();
        assertThat(backfilledRestaurant.getRegionId()).isEqualTo(jamsilRegionId);
        assertThat(backfilledGathering.getRegionId()).isEqualTo(samgakjiRegionId);
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
}
