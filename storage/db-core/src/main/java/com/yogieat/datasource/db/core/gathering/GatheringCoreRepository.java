package com.yogieat.datasource.db.core.gathering;

import static com.yogieat.datasource.db.core.gathering.QGatheringEntity.gatheringEntity;
import static com.yogieat.datasource.db.core.participant.QParticipantEntity.participantEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.common.Region;
import com.yogieat.datasource.db.core.region.RegionJpaRepository;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.result.GatheringAdminItemResult;
import com.yogieat.gathering.service.GatheringAdminCriteria;
import com.yogieat.gathering.service.GatheringRepository;
import java.util.ArrayList;
import java.util.List;
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
                .map(GatheringEntity::toDomain);
    }

    @Override
    public Optional<Gathering> findByAccessKey(String accessKey) {
        return gatheringJpaRepository.findByAccessKey(accessKey)
                .map(GatheringEntity::toDomain);
    }

    @Override
    public Optional<Gathering> findByAccessKeyForUpdate(String accessKey) {
        return gatheringJpaRepository.findByAccessKeyForUpdate(accessKey)
                .map(GatheringEntity::toDomain);
    }

    @Override
    public Gathering save(Gathering gathering) {
        GatheringEntity entity = GatheringEntity.from(
                gathering,
                resolveRegionId(gathering.region())
        );
        GatheringEntity savedEntity = gatheringJpaRepository.save(entity);
        return GatheringEntity.toDomain(savedEntity);
    }

    @Override
    public List<Gathering> findAdminGatherings(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    ) {
        return createAdminGatheringQuery(criteria)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(GatheringEntity::toDomain)
                .toList();
    }

    @Override
    public List<GatheringAdminItemResult> findAdminGatheringsWithParticipantCount(
            GatheringAdminCriteria.List criteria,
            int page,
            int size
    ) {
        NumberExpression<Long> participantCount = participantEntity.id.count();

        return jpaQueryFactory
                .select(gatheringEntity, participantCount)
                .from(gatheringEntity)
                .leftJoin(participantEntity)
                .on(participantEntity.gatheringId.eq(gatheringEntity.id))
                .where(buildAdminGatheringConditions(criteria))
                .groupBy(gatheringEntity.id)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch()
                .stream()
                .map(tuple -> {
                    Long count = tuple.get(participantCount);
                    return new GatheringAdminItemResult(
                            GatheringEntity.toDomain(tuple.get(gatheringEntity)),
                            count == null ? 0L : count
                    );
                })
                .toList();
    }

    @Override
    public List<Gathering> findAdminGatherings(GatheringAdminCriteria.List criteria) {
        return createAdminGatheringQuery(criteria)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .fetch()
                .stream()
                .map(GatheringEntity::toDomain)
                .toList();
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
        return regionJpaRepository.findIdByCode(region.name()).orElse(null);
    }

    private BooleanExpression regionCondition(Region region) {
        if (region == null) {
            return null;
        }

        Long regionId = resolveRegionId(region);
        if (regionId == null) {
            return gatheringEntity.region.eq(region);
        }

        return gatheringEntity.regionId.eq(regionId)
                .or(
                        gatheringEntity.regionId.isNull()
                                .and(gatheringEntity.region.eq(region))
                );
    }
}
