package com.yogieat.datasource.db.core.gathering;

import static com.yogieat.datasource.db.core.gathering.QGatheringEntity.gatheringEntity;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yogieat.gathering.domain.Gathering;
import com.yogieat.gathering.service.GatheringAdminListCriteria;
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
    public Gathering save(Gathering gathering) {
        GatheringEntity entity = GatheringEntity.from(gathering);
        GatheringEntity savedEntity = gatheringJpaRepository.save(entity);
        return GatheringEntity.toDomain(savedEntity);
    }

    @Override
    public List<Gathering> findAdminGatherings(
            GatheringAdminListCriteria criteria,
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
    public List<Gathering> findAdminGatherings(GatheringAdminListCriteria criteria) {
        return createAdminGatheringQuery(criteria)
                .orderBy(gatheringEntity.createdAt.desc(), gatheringEntity.id.desc())
                .fetch()
                .stream()
                .map(GatheringEntity::toDomain)
                .toList();
    }

    @Override
    public long countAdminGatherings(GatheringAdminListCriteria criteria) {
        Long total = createAdminGatheringQuery(criteria)
                .select(gatheringEntity.count())
                .fetchOne();

        return total == null ? 0L : total;
    }

    private JPAQuery<GatheringEntity> createAdminGatheringQuery(
            GatheringAdminListCriteria criteria
    ) {
        return jpaQueryFactory.selectFrom(gatheringEntity)
                .where(buildAdminGatheringConditions(criteria));
    }

    private BooleanExpression[] buildAdminGatheringConditions(
            GatheringAdminListCriteria criteria
    ) {
        List<BooleanExpression> conditions = new ArrayList<>();

        if (!criteria.includeDeleted()) {
            conditions.add(gatheringEntity.deletedAt.isNull());
        }

        if (criteria.region() != null) {
            conditions.add(gatheringEntity.region.eq(criteria.region()));
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
}
