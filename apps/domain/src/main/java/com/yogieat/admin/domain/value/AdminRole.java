package com.yogieat.admin.domain.value;

public enum AdminRole {
    SUPER_ADMIN, // 최고 관리자: 모든 권한
    ADMIN, // 관리자: 조회/수정 권한
    VIEWER // 뷰어: 조회만 가능
}
