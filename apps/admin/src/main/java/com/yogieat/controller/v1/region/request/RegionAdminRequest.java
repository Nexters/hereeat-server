package com.yogieat.controller.v1.region.request;

import com.yogieat.common.GeoJson;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.region.service.RegionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class RegionAdminRequest {

    public record Create(
            @NotBlank String code,
            @NotBlank String province,
            @NotBlank String displayName,
            @NotNull CoordinatesRequest coordinatesStandard,
            RegionStatus status,
            Integer sortOrder
    ) {
        public static RegionCommand.Create toCommand(Create request) {
            if (request == null) {
                throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
            }

            return new RegionCommand.Create(
                    normalizeCode(request.code()),
                    trimOrNull(request.province()),
                    trimOrNull(request.displayName()),
                    parseCoordinates(request.coordinatesStandard()),
                    request.status() == null ? RegionStatus.ACTIVE : request.status(),
                    request.sortOrder()
            );
        }
    }

    public record Patch(
            String code,
            String province,
            String displayName,
            CoordinatesRequest coordinatesStandard,
            RegionStatus status,
            Integer sortOrder
    ) {
        public static RegionCommand.Patch toCommand(Patch request) {
            if (request == null) {
                return RegionCommand.Patch.empty();
            }

            return new RegionCommand.Patch(
                    normalizeCode(request.code()),
                    trimOrNull(request.province()),
                    trimOrNull(request.displayName()),
                    parseOptionalCoordinates(request.coordinatesStandard()),
                    request.status(),
                    request.sortOrder()
            );
        }
    }

    public record CoordinatesRequest(List<Double> coordinates) {
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.strip().toUpperCase();
    }

    private static String trimOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static GeoJson.Point parseCoordinates(CoordinatesRequest coordinatesRequest) {
        if (coordinatesRequest == null || coordinatesRequest.coordinates() == null) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        List<Double> coordinates = coordinatesRequest.coordinates();
        if (coordinates.size() != 2) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        Double longitude = coordinates.getFirst();
        Double latitude = coordinates.get(1);
        if (longitude == null || latitude == null) {
            throw new CustomException(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH);
        }

        return new GeoJson.Point(List.of(longitude, latitude));
    }

    private static GeoJson.Point parseOptionalCoordinates(CoordinatesRequest coordinatesRequest) {
        if (coordinatesRequest == null) {
            return null;
        }
        return parseCoordinates(coordinatesRequest);
    }
}
