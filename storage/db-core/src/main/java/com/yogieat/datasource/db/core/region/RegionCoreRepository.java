package com.yogieat.datasource.db.core.region;

import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.service.RegionRepository;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RegionCoreRepository implements RegionRepository {

    private static final String BACKFILL_RESTAURANT_REGION_IDS_SQL = """
            update t_restaurant restaurant
               set region_id = (
                   select region.id
                     from t_region region
                    where region.code = restaurant.region
               )
             where restaurant.region is not null
               and restaurant.region_id is null
               and exists (
                   select 1
                     from t_region region
                    where region.code = restaurant.region
               )
            """;

    private static final String BACKFILL_GATHERING_REGION_IDS_SQL = """
            update t_gathering gathering
               set region_id = (
                   select region.id
                     from t_region region
                    where region.code = gathering.region
               )
             where gathering.region is not null
               and gathering.region_id is null
               and exists (
                   select 1
                     from t_region region
                    where region.code = gathering.region
               )
            """;

    private static final String FIND_UNMATCHED_RESTAURANT_REGIONS_SQL = """
            select distinct restaurant.region
              from t_restaurant restaurant
             where restaurant.region is not null
               and restaurant.region_id is null
               and not exists (
                   select 1
                     from t_region region
                    where region.code = restaurant.region
               )
            """;

    private static final String FIND_UNMATCHED_GATHERING_REGIONS_SQL = """
            select distinct gathering.region
              from t_gathering gathering
             where gathering.region is not null
               and gathering.region_id is null
               and not exists (
                   select 1
                     from t_region region
                    where region.code = gathering.region
               )
            """;

    private final RegionJpaRepository regionJpaRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final EntityManager entityManager;

    @Override
    public Optional<RegionMaster> findByCode(String code) {
        return regionJpaRepository.findByCode(code)
                .map(RegionEntity::toDomain);
    }

    @Override
    public List<RegionMaster> findAllActiveOrderBySortOrder() {
        return regionJpaRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(RegionEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public RegionMaster save(RegionMaster regionMaster) {
        RegionEntity entity = regionMaster.id() == null
                ? regionJpaRepository.findByCode(regionMaster.code()).orElse(null)
                : regionJpaRepository.findById(regionMaster.id())
                        .orElseGet(() -> regionJpaRepository.findByCode(regionMaster.code()).orElse(null));

        if (entity == null) {
            return RegionEntity.toDomain(regionJpaRepository.save(RegionEntity.of(regionMaster)));
        }

        entity.apply(regionMaster);
        RegionEntity savedEntity = regionJpaRepository.save(entity);
        return RegionEntity.toDomain(savedEntity);
    }

    @Override
    public int backfillRestaurantRegionIds() {
        logUnmatchedLegacyRegions("restaurant", FIND_UNMATCHED_RESTAURANT_REGIONS_SQL);
        entityManager.flush();
        int updatedCount = namedParameterJdbcTemplate.getJdbcTemplate().update(BACKFILL_RESTAURANT_REGION_IDS_SQL);
        entityManager.clear();
        return updatedCount;
    }

    @Override
    public int backfillGatheringRegionIds() {
        logUnmatchedLegacyRegions("gathering", FIND_UNMATCHED_GATHERING_REGIONS_SQL);
        entityManager.flush();
        int updatedCount = namedParameterJdbcTemplate.getJdbcTemplate().update(BACKFILL_GATHERING_REGION_IDS_SQL);
        entityManager.clear();
        return updatedCount;
    }

    private void logUnmatchedLegacyRegions(String tableName, String sql) {
        List<String> unmatchedRegionCodes = namedParameterJdbcTemplate.getJdbcTemplate().queryForList(sql, String.class);
        for (String unmatchedRegionCode : unmatchedRegionCodes == null ? Collections.<String>emptyList() : unmatchedRegionCodes) {
            log.warn(
                    "Skipping {} legacy region backfill because no matching region master exists: {}",
                    tableName,
                    unmatchedRegionCode
            );
        }
    }
}
