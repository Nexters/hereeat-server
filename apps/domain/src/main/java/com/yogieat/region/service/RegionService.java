package com.yogieat.region.service;

import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionSummary;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegionService {

    private final RegionRepository regionRepository;
    private final RegionValidator regionValidator;

    @Transactional(readOnly = true)
    public RegionMaster getRegionById(Long id) {
        return regionRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_LOCATION_NAME));
    }

    @Transactional(readOnly = true)
    public RegionSummary getRegionSummaryById(Long id) {
        return regionRepository.findRegionSummaryById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_LOCATION_NAME));
    }

    @Transactional(readOnly = true)
    public List<RegionMaster> findActiveRegions() {
        return regionRepository.findAllActiveOrderBySortOrder().stream()
                .filter(regionMaster -> Region.fromString(regionMaster.code()) != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegionMaster> findAllRegions() {
        return regionRepository.findAllOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public List<RegionSummary> findRegionDashboard() {
        return regionRepository.findAllRegionSummariesOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public List<RegionSummary> findActiveRegionSummaries() {
        return regionRepository.findAllActiveRegionSummariesOrderBySortOrder();
    }

    @Transactional(readOnly = true)
    public RegionMaster getActiveRegionByDisplayName(String displayName) {
        return regionRepository.findActiveByDisplayName(displayName)
                .orElseThrow(() -> {
                    log.warn("Active region not found by displayName: {}", displayName);
                    return new CustomException(ErrorCode.INVALID_LOCATION_NAME);
                });
    }

    @Transactional
    public RegionMaster createRegion(RegionCommand.Create command) {
        regionValidator.validateCreate(command);

        int sortOrder = command.sortOrder() != null
                ? command.sortOrder()
                : regionRepository.nextSortOrder();

        RegionMaster region = new RegionMaster(
                null,
                command.code(),
                command.province(),
                command.displayName(),
                command.coordinatesStandard(),
                command.active(),
                sortOrder,
                null,
                null
        );
        return regionRepository.save(region);
    }

    @Transactional
    public RegionMaster updateRegion(Long id, RegionCommand.Patch command) {
        RegionMaster currentRegion = getRegionById(id);
        regionValidator.validatePatch(currentRegion, command);

        RegionMaster updatedRegion = new RegionMaster(
                currentRegion.id(),
                command.code() != null ? command.code() : currentRegion.code(),
                command.province() != null ? command.province() : currentRegion.province(),
                command.displayName() != null ? command.displayName() : currentRegion.displayName(),
                command.coordinatesStandard() != null ? command.coordinatesStandard() : currentRegion.coordinatesStandard(),
                command.active() != null ? command.active() : currentRegion.active(),
                command.sortOrder() != null ? command.sortOrder() : currentRegion.sortOrder(),
                currentRegion.createdAt(),
                currentRegion.updatedAt()
        );

        return regionRepository.update(updatedRegion);
    }

    @Transactional
    public void deleteRegionById(Long id) {
        getRegionById(id);
        regionRepository.deleteById(id);
    }
}
