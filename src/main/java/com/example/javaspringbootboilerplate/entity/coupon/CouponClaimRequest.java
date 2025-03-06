package com.example.javaspringbootboilerplate.entity.coupon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CouponClaimRequest {
    private String userId;
    private String email;
    private String couponCode;
}
