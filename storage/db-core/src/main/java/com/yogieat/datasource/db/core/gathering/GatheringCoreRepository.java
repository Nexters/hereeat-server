package com.yogieat.datasource.db.core.gathering;

import static com.yogieat.datasource.db.core.gathering.QGatheringEntity.gatheringEntity;
import static com.yogieat.datasource.db.core.participant.QParticipantEntity.participantEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.common.GeoJson;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.datasource.db.core.region.RegionEntity;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.result.GatheringAdminItemResult;
import com.yogieat.gathering.service.GatheringAdminCriteria;
import com.yogieat.gathering.service.GatheringRepository;
import java.util.ArrayList;
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
public class GatheringCoreRepository implements GatheringRepository {
    private final GatheringJpaRepository gatheringJpaRepository;
    private final RegionJpaRepository regionJpaRepository;
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<Gathering> findById(Long id) {
        return gatheringJpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Gathering> findByAccessKey(String accessKey) {
        return gatheringJpaRepository.findByAccessKey(accessKey)
                .map(this::toDomain);
    }

    @Override
    public Optional<Gathering> findByAccessKeyForUpdate(String accessKey) {
        return gatheringJpaRepository.findByAccessKeyForUpdate(accessKey)
                .map(this::toDomain);
    }

    @Override
    public Gathering save(Gathering gathering) {
        GatheringEntity entity = GatheringEntity.from(
                gathering,
                requireRegionId(gathering.region())
        );
        GatheringEntity savedEntity = gatheringJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<Gathering> findAdminGatherings(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    ) {
        List<GatheringEntity> entities = createAdminGatheringQuery(criteria)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        return toDomainGatherings(entities);
    }

    @Override
    public List<GatheringAdminItemResult> findAdminGatheringsWithParticipantCount(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    ) {
        NumberExpression<Long> participantCount = participantEntity.id.count();
        List<com.querydsl.core.Tuple> tuples = jpaQueryFactory
                .select(gatheringEntity, participantCount)
                .from(gatheringEntity)
                .leftJoin(participantEntity)
                .on(participantEntity.gatheringId.eq(gatheringEntity.id))
                .where(buildAdminGatheringConditions(criteria))
                .groupBy(gatheringEntity.id)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        Map<Long, Region> regionMap = resolveRegionMap(
                tuples.stream()
                        .map(tuple -> tuple.get(gatheringEntity))
                        .map(GatheringEntity::getRegionId)
                        .toList()
        );

        return tuples
                .stream()
                .map(tuple -> {
                    GatheringEntity entity = tuple.get(gatheringEntity);
                    Long count = tuple.get(participantCount);
                    return new GatheringAdminItemResult(
                            GatheringEntity.toDomain(entity, regionMap.get(entity.getRegionId())),
                            count == null ? 0L : count
                    );
                })
                .toList();
    }

    @Override
    public List<Gathering> findAdminGatherings(GatheringAdminCriteria.List criteria) {
        List<GatheringEntity> entities = createAdminGatheringQuery(criteria)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .fetch();
        return toDomainGatherings(entities);
    }

    @Override
    public long countAdminGatherings(GatheringAdminCriteria.List criteria) {
        Long total = createAdminGatheringQuery(criteria)
                .select(gatheringEntity.count())
                .fetchOne();

        return total == null ? 0L : total;
    }

    private JPAQuery<GatheringEntity> createAdminGatheringQuery(
            GatheringAdminCriteria.List criteria
    ) {
        return jpaQueryFactory.selectFrom(gatheringEntity)
                .where(buildAdminGatheringConditions(criteria));
    }

    private BooleanExpression[] buildAdminGatheringConditions(
            GatheringAdminCriteria.List criteria
    ) {
        List<BooleanExpression> conditions = new ArrayList<>();

        if (!criteria.includeDeleted()) {
            conditions.add(gatheringEntity.deletedAt.isNull());
        }

        if (criteria.region() != null) {
            conditions.add(regionCondition(criteria.region()));
        }

        if (criteria.timeSlot() != null) {
            conditions.add(gatheringEntity.timeSlot.eq(criteria.timeSlot()));
        }

        BooleanExpression keywordCondition = createKeywordCondition(criteria.keyword());
        if (keywordCondition != null) {
            conditions.add(keywordCondition);
        }

        return conditions.toArray(BooleanExpression[]::new);
    }

    private BooleanExpression createKeywordCondition(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalizedKeyword = keyword.strip();
        BooleanExpression condition =
                gatheringEntity.title.containsIgnoreCase(normalizedKeyword)
                        .or(gatheringEntity.accessKey.containsIgnoreCase(normalizedKeyword));

        if (normalizedKeyword.chars().allMatch(Character::isDigit)) {
            try {
                condition = condition.or(gatheringEntity.id.eq(Long.valueOf(normalizedKeyword)));
            } catch (NumberFormatException ignored) {
                // no-op
            }
        }

        return condition;
    }

    private Long resolveRegionId(Region region) {
        if (region == null) {
            return null;
        }
        return regionJpaRepository.findIdByCode(region.code()).orElse(null);
    }

    private Long requireRegionId(Region region) {
        Long regionId = resolveRegionId(region);
        if (regionId == null) {
            throw new CustomException(ErrorCode.INVALID_LOCATION_NAME);
        }
        return regionId;
    }

    private BooleanExpression regionCondition(Region region) {
        if (region == null) {
            return null;
        }

        Long regionId = resolveRegionId(region);
        if (regionId == null) {
            return gatheringEntity.regionId.isNull().and(gatheringEntity.regionId.isNotNull());
        }

        return gatheringEntity.regionId.eq(regionId);
    }

    private Gathering toDomain(GatheringEntity entity) {
        return GatheringEntity.toDomain(entity, resolveRegion(entity.getRegionId()));
    }

    private List<Gathering> toDomainGatherings(List<GatheringEntity> entities) {
        Map<Long, Region> regionMap = resolveRegionMap(
                entities.stream()
                        .map(GatheringEntity::getRegionId)
                        .toList()
        );

        return entities.stream()
                .map(entity -> GatheringEntity.toDomain(entity, regionMap.get(entity.getRegionId())))
                .toList();
    }

    private Region resolveRegion(Long regionId) {
        if (regionId == null) {
            return null;
        }

        return regionJpaRepository.findByIdAndDeletedAtIsNull(regionId)
                .map(this::toRegion)
                .orElse(null);
    }

    private Map<Long, Region> resolveRegionMap(Collection<Long> regionIds) {
        List<Long> distinctRegionIds = regionIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctRegionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Region> regionMap = new HashMap<>();
        regionJpaRepository.findByIdInAndDeletedAtIsNull(distinctRegionIds)
                .forEach(regionEntity -> regionMap.put(regionEntity.getId(), toRegion(regionEntity)));
        return regionMap;
    }

    private Region toRegion(RegionEntity regionEntity) {
        if (regionEntity == null) {
            return null;
        }
        return Region.of(
                regionEntity.getCode(),
                regionEntity.getDisplayName(),
                new GeoJson.Point(List.of(regionEntity.getLongitude(), regionEntity.getLatitude()))
        );
    }
}
