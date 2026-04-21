package com.yogieat.datasource.db.core.region;

import static com.yogieat.datasource.db.core.restaurant.QRestaurantEntity.restaurantEntity;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionSummary;
import com.yogieat.region.service.RegionRepository;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RegionCoreRepository implements RegionRepository {

    private final RegionJpaRepository regionJpaRepository;
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<RegionMaster> findById(Long id) {
        return regionJpaRepository.findById(id)
                .map(RegionEntity::toDomain);
    }

    @Override
    public Optional<RegionSummary> findRegionSummaryById(Long id) {
        return regionJpaRepository.findById(id)
                .map(regionEntity -> new RegionSummary(
                        RegionEntity.toDomain(regionEntity),
                        findRestaurantCountMap(List.of(regionEntity.getId())).getOrDefault(regionEntity.getId(), 0L)
                ));
    }

    @Override
    public List<RegionMaster> findAllOrderBySortOrder() {
        return regionJpaRepository.findAllByOrderBySortOrderAsc().stream()
                .map(RegionEntity::toDomain)
                .toList();
    }

    @Override
    public List<RegionMaster> findAllActiveOrderBySortOrder() {
        return regionJpaRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(RegionEntity::toDomain)
                .toList();
    }

    @Override
    public List<RegionSummary> findAllRegionSummariesOrderBySortOrder() {
        return toRegionSummaries(regionJpaRepository.findAllByOrderBySortOrderAsc());
    }

    @Override
    public List<RegionSummary> findAllActiveRegionSummariesOrderBySortOrder() {
        return toRegionSummaries(regionJpaRepository.findAllByActiveTrueOrderBySortOrderAsc());
    }

    @Override
    public Optional<RegionMaster> findActiveByDisplayName(String displayName) {
        return regionJpaRepository.findByDisplayNameAndActiveTrue(displayName)
                .map(RegionEntity::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return regionJpaRepository.existsByCode(code);
    }

    @Override
    public boolean existsByDisplayName(String displayName) {
        return regionJpaRepository.existsByDisplayName(displayName);
    }

    @Override
    public int nextSortOrder() {
        return regionJpaRepository.findNextSortOrder();
    }

    @Override
    public RegionMaster save(RegionMaster regionMaster) {
        RegionEntity savedEntity = regionJpaRepository.save(RegionEntity.of(regionMaster));
        return RegionEntity.toDomain(savedEntity);
    }

    private List<RegionSummary> toRegionSummaries(List<RegionEntity> regionEntities) {
        Map<Long, Long> restaurantCountMap = findRestaurantCountMap(
                regionEntities.stream()
                        .map(RegionEntity::getId)
                        .toList()
        );

        return regionEntities.stream()
                .map(regionEntity -> new RegionSummary(
                        RegionEntity.toDomain(regionEntity),
                        restaurantCountMap.getOrDefault(regionEntity.getId(), 0L)
                ))
                .toList();
    }

    private Map<Long, Long> findRestaurantCountMap(Collection<Long> regionIds) {
        List<Long> distinctRegionIds = regionIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctRegionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        NumberExpression<Long> restaurantCount = restaurantEntity.id.count();
        List<Tuple> tuples = jpaQueryFactory
                .select(restaurantEntity.regionId, restaurantCount)
                .from(restaurantEntity)
                .where(
                        restaurantEntity.deletedAt.isNull(),
                        restaurantEntity.regionId.in(distinctRegionIds)
                )
                .groupBy(restaurantEntity.regionId)
                .fetch();

        Map<Long, Long> restaurantCountMap = new HashMap<>();
        for (Tuple tuple : tuples) {
            Long regionId = tuple.get(restaurantEntity.regionId);
            Long count = tuple.get(restaurantCount);
            if (regionId != null && count != null) {
                restaurantCountMap.put(regionId, count);
            }
        }
        return restaurantCountMap;
    }
}
