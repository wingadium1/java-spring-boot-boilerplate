package com.example.javaspringbootboilerplate.entity.coupon;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
//@Builder
@NoArgsConstructor
public class CouponClaimResponse {
    private String message;
    private String couponCode;
    private int remainingSlots;
    private String error;
    private ClaimResult result;

    public static CouponClaimResponse success(String couponCode, int remainingSlots) {
        CouponClaimResponse claimResponse =  new CouponClaimResponse();
        claimResponse.setMessage("Coupon claimed successfully");
        claimResponse.setResult(ClaimResult.SUCCESS);
        claimResponse.setCouponCode(couponCode);
        claimResponse.setRemainingSlots(remainingSlots);
        return claimResponse;
    }

    public static CouponClaimResponse failure(ClaimResult result, String message) {
        CouponClaimResponse claimResponse =  new CouponClaimResponse();
        claimResponse.setError((message.isEmpty()) ? result.getMessage() : message);
        claimResponse.setResult(result);
        return claimResponse;
    }

    public static CouponClaimResponse failure(ClaimResult claimResult) {
        return failure(claimResult,"");
    }
}
