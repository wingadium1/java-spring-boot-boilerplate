package com.example.javaspringbootboilerplate.entity.coupon;

import lombok.Getter;

@Getter
public enum ClaimResult {
    SUCCESS("Coupon claimed successfully"),
    COUPON_NOT_FOUND("Coupon not found"),
    ALREADY_CLAIMED("User has already claimed this coupon"),
    NO_SLOTS("No more slots available"),
    ERROR("System error occurred");

    private final String message;

    ClaimResult(String message) {
        this.message = message;
    }
}
