package com.yogieat.domain.recommend.service;

import com.yogieat.domain.category.domain.Category;
import com.yogieat.domain.category.service.CategoryService;
import com.yogieat.domain.common.GeoJson;
import com.yogieat.domain.common.Place;
import com.yogieat.domain.participant.domain.Participant;
import com.yogieat.domain.participant.domain.value.DistanceRange;
import com.yogieat.domain.participant.service.ParticipantRepository;
import com.yogieat.domain.recommend.domain.RecommendResult;
import com.yogieat.domain.recommend.domain.RecommendStatus;
import com.yogieat.domain.recommend.domain.value.PreferenceScore;
import com.yogieat.domain.recommend.domain.value.ScoredRestaurant;
import com.yogieat.domain.restaurant.domain.Restaurant;
import com.yogieat.domain.restaurant.service.RestaurantRepository;
import com.yogieat.global.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {
    private final ParticipantRepository participantRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryService categoryService;
    private final RecommendResultRepository recommendResultRepository;

    @Transactional
    public void processRecommendation(Long gatheringId, Place place) {
        try {
            log.info("Processing recommendation for gathering: {}", gatheringId);

            // 1. 중복 추천 방지
            if (recommendResultRepository.existsByGatheringId(gatheringId)) {
                return;
            }

            // 2. 참여자 조회
            List<Participant> participants = participantRepository.findByGatheringId(gatheringId);
            if (participants.isEmpty()) {
                saveFailedResult(gatheringId);
                return;
            }

            // 3. Restaurant 조회
            List<Restaurant> restaurants = restaurantRepository.findByPlace(place);
            if (restaurants.isEmpty()) {
                saveFailedResult(gatheringId);
                return;
            }

            // 4. Category 조회 및 캐싱 (Spring Cache 적용)
            Map<Long, Category> categoryMap = categoryService.findAllAsMap();

            // 5. DistanceRange 다수결 결정
            DistanceRange majorityRange = determineMajorityDistanceRange(participants);

            // 6. 선호도/불호 사전 집계 (성능 최적화: O(P×3) 한 번으로 O(R×P×3) 제거)
            Map<String, PreferenceScore> preferenceScoreMap = aggregatePreferenceScores(participants);

            // 7. Place별 중심 좌표
            GeoJson.Point centerPoint = place.getCoordinatesStandard();

            // 8. 각 Restaurant 점수 계산 (Top-K 최적화: PriorityQueue 사용)
            // Min-heap으로 상위 3개만 유지 (O(R log 3) = O(R))
            PriorityQueue<ScoredRestaurant> top3Heap = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredRestaurant::totalScore)
            );

            for (Restaurant restaurant : restaurants) {
                double totalScore = 0.0;

                // Category 조회
                Category category = categoryMap.get(restaurant.categoryId());
                if (category == null) {
                    continue; // 카테고리 정보가 없으면 스킵
                }

                String categoryName = category.largeCategory().getDisplayName();

                // 8a & 8b. 사전 집계된 선호도/불호 점수 적용 (O(1) 조회)
                PreferenceScore preferenceScore = preferenceScoreMap.getOrDefault(
                    categoryName,
                    PreferenceScore.empty()
                );
                totalScore += preferenceScore.calculateFinalScore();

                // 8c. DistanceRange 가산점
                if (majorityRange != DistanceRange.ANY && restaurant.location() != null) {
                    double distance = calculateDistance(centerPoint, restaurant.location());
                    boolean withinRange = isWithinDistanceRange(distance, majorityRange);
                    if (withinRange) {
                        totalScore += 1.0;
                    }
                }

                // 8d. 의견일치율 계산
                double maxPossibleScore = participants.size() * 3.0;
                double agreementRate = (totalScore / maxPossibleScore) * 100.0;

                ScoredRestaurant scored = new ScoredRestaurant(restaurant, totalScore, agreementRate);

                // Top-K 알고리즘: 상위 3개만 유지
                if (top3Heap.size() < 3) {
                    top3Heap.offer(scored);
                } else if (scored.totalScore() > top3Heap.peek().totalScore()) {
                    top3Heap.poll();
                    top3Heap.offer(scored);
                }
            }

            // 9. 상위 3개 추출 및 정렬 (힙에서 추출 후 내림차순 정렬)
            List<ScoredRestaurant> top3 = new ArrayList<>(top3Heap);
            top3.sort(Comparator.comparingDouble(ScoredRestaurant::totalScore).reversed());

            if (top3.isEmpty()) {
                saveFailedResult(gatheringId);
                return;
            }

            log.info("Top 3 restaurants: {}", top3.stream()
                    .map(sr -> String.format("%s(%.2f%%)", sr.restaurant().name(), sr.agreementRate()))
                    .collect(Collectors.joining(", ")));

            // 10. RecommendResult 저장
            List<RecommendResult> results = new ArrayList<>();
            for (int i = 0; i < top3.size(); i++) {
                ScoredRestaurant scored = top3.get(i);
                results.add(RecommendResult.Create.of(
                        gatheringId,
                        scored.restaurant().id(),
                        scored.agreementRate(),
                        RecommendStatus.COMPLETED,
                        i + 1, // rank: 1, 2, 3,
                        top3.get(i).totalScore()
                ));
            }

            recommendResultRepository.saveAll(results);
        } catch (Exception e) {
            log.error("Failed to process recommendation for gathering: {}", gatheringId, e);
            saveFailedResult(gatheringId);
        }
    }

    private DistanceRange determineMajorityDistanceRange(List<Participant> participants) {
        Map<DistanceRange, Long> rangeCount = participants.stream()
                .collect(Collectors.groupingBy(Participant::distanceRange, Collectors.counting()));

        return rangeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DistanceRange.ANY);
    }

    /**
     * 참여자들의 선호도와 불호를 카테고리별로 미리 집계
     * O(P × 3) 시간에 모든 선호도 점수를 계산하여 O(R × P × 3) 반복을 제거
     */
    private Map<String, PreferenceScore> aggregatePreferenceScores(List<Participant> participants) {
        Map<String, PreferenceScore> scoreMap = new HashMap<>();

        for (Participant participant : participants) {
            // 선호도 집계
            List<String> preferences = StringUtils.splitByComma(participant.preferences());
            for (int i = 0; i < preferences.size(); i++) {
                String pref = preferences.get(i);
                if (!pref.equals("상관없음")) {
                    PreferenceScore current = scoreMap.getOrDefault(pref, PreferenceScore.empty());
                    scoreMap.put(pref, current.addPreference(i + 1)); // rank는 1-based
                }
            }

            // 불호 집계
            List<String> dislikes = StringUtils.splitByComma(participant.dislikes());
            for (String dislike : dislikes) {
                if (!dislike.equals("상관없음")) {
                    PreferenceScore current = scoreMap.getOrDefault(dislike, PreferenceScore.empty());
                    scoreMap.put(dislike, current.addDislike());
                }
            }
        }

        return scoreMap;
    }

    /**
     * Haversine 공식을 사용한 두 좌표 간 거리 계산 (km)
     */
    private double calculateDistance(GeoJson.Point centerPoint, GeoJson.Point restaurantPoint) {
        double lat1 = centerPoint.getCoordinates().get(1);
        double lon1 = centerPoint.getCoordinates().get(0);
        double lat2 = restaurantPoint.getCoordinates().get(1);
        double lon2 = restaurantPoint.getCoordinates().get(0);

        double R = 6371; // 지구 반지름 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isWithinDistanceRange(double distance, DistanceRange range) {
        return switch (range) {
            case RANGE_500M -> distance <= 0.5;
            case RANGE_1KM -> distance <= 1.0;
            case ANY -> true;
        };
    }

    private void saveFailedResult(Long gatheringId) {
        RecommendResult failedResult = RecommendResult.Create.of(
                gatheringId,
                null,
                0.0,
                RecommendStatus.FAILED,
                null,
                0.0
        );
        recommendResultRepository.saveAll(List.of(failedResult));
    }
}
