package com.yogieat.region.service;

import com.yogieat.common.GeoJson;
import com.yogieat.common.GeoUtils;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.region.domain.RegionMaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionValidator {

    private static final int MAX_CODE_LENGTH = 30;
    private static final int MAX_PROVINCE_LENGTH = 50;
    private static final int MAX_DISPLAY_NAME_LENGTH = 255;
    private static final String CODE_PATTERN = "^[A-Z0-9_]+$";
    private static final String REQUIRED_CODE_REASON = "지역 코드는 필수입니다";
    private static final String INVALID_CODE_REASON = "지역 코드는 대문자, 숫자, 언더스코어만 사용할 수 있습니다";
    private static final String DUPLICATE_CODE_REASON = "이미 존재하는 지역 코드입니다";
    private static final String REQUIRED_PROVINCE_REASON = "시도 구분은 필수입니다";
    private static final String INVALID_PROVINCE_LENGTH_REASON = "시도 구분은 최대 " + MAX_PROVINCE_LENGTH + "자까지 가능합니다";
    private static final String REQUIRED_DISPLAY_NAME_REASON = "지역명은 필수입니다";
    private static final String INVALID_DISPLAY_NAME_LENGTH_REASON = "지역명은 최대 " + MAX_DISPLAY_NAME_LENGTH + "자까지 가능합니다";
    private static final String DUPLICATE_DISPLAY_NAME_REASON = "이미 존재하는 지역명입니다";
    private static final String INVALID_COORDINATES_REASON = "지역 좌표가 올바르지 않습니다";
    private static final String INVALID_SORT_ORDER_REASON = "sortOrder는 0 이상이어야 합니다";

    private final RegionRepository regionRepository;

    public void validateCreate(RegionCommand.Create command) {
        if (command == null) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        validateCode(command.code());
        validateProvince(command.province());
        validateDisplayName(command.displayName());
        validateCoordinates(command.coordinatesStandard());
        validateSortOrder(command.sortOrder());
    }

    public void validatePatch(RegionMaster currentRegion, RegionCommand.Patch command) {
        if (currentRegion == null || command == null) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        if (command.code() != null) {
            validateCodeFormat(command.code());
            if (!command.code().equals(currentRegion.code()) && regionRepository.existsByCode(command.code())) {
                throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, DUPLICATE_CODE_REASON);
            }
        }

        if (command.province() != null) {
            validateProvinceFormat(command.province());
        }

        if (command.displayName() != null) {
            validateDisplayNameFormat(command.displayName());
            if (!command.displayName().equals(currentRegion.displayName())
                    && regionRepository.existsByDisplayName(command.displayName())) {
                throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, DUPLICATE_DISPLAY_NAME_REASON);
            }
        }

        if (command.coordinatesStandard() != null) {
            validateCoordinates(command.coordinatesStandard());
        }

        validateSortOrder(command.sortOrder());
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, REQUIRED_CODE_REASON);
        }
        validateCodeFormat(code);
        if (regionRepository.existsByCode(code)) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, DUPLICATE_CODE_REASON);
        }
    }

    private void validateProvince(String province) {
        if (province == null || province.isBlank()) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, REQUIRED_PROVINCE_REASON);
        }
        validateProvinceFormat(province);
    }

    private void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, REQUIRED_DISPLAY_NAME_REASON);
        }
        validateDisplayNameFormat(displayName);
        if (regionRepository.existsByDisplayName(displayName)) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, DUPLICATE_DISPLAY_NAME_REASON);
        }
    }

    private void validateCodeFormat(String code) {
        if (code.length() > MAX_CODE_LENGTH || !code.matches(CODE_PATTERN)) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, INVALID_CODE_REASON);
        }
    }

    private void validateProvinceFormat(String province) {
        if (province.length() > MAX_PROVINCE_LENGTH) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, INVALID_PROVINCE_LENGTH_REASON);
        }
    }

    private void validateDisplayNameFormat(String displayName) {
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, INVALID_DISPLAY_NAME_LENGTH_REASON);
        }
    }

    private void validateCoordinates(GeoJson.Point coordinatesStandard) {
        if (!GeoUtils.isValidPoint(coordinatesStandard)) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, INVALID_COORDINATES_REASON);
        }
    }

    private void validateSortOrder(Integer sortOrder) {
        if (sortOrder != null && sortOrder < 0) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, INVALID_SORT_ORDER_REASON);
        }
    }
}
