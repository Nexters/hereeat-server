package com.yogieat.config.web;

import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AdminUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AdminService adminService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AdminUser.class);
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ErrorCode.ADMIN_UNAUTHORIZED);
        }

        Long adminId = extractAdminId(authentication.getPrincipal());
        return AdminUser.from(adminService.getById(adminId));
    }

    private Long extractAdminId(Object principal) {
        if (principal instanceof Long adminId) {
            return adminId;
        }
        if (principal instanceof Integer adminId) {
            return adminId.longValue();
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException e) {
                throw new CustomException(ErrorCode.ADMIN_UNAUTHORIZED);
            }
        }
        throw new CustomException(ErrorCode.ADMIN_UNAUTHORIZED);
    }
}
