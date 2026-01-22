package com.yogieat.domain.common;

import lombok.Getter;

@Getter
public enum Place {
    HONGIK_UNIV("홍대입구역"),
    GANGNAM("강남역");

    private final String name;

    Place(String name) {
        this.name = name;
    }
}
