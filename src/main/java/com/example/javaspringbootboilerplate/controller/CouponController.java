package com.example.javaspringbootboilerplate.controller;

import com.example.javaspringbootboilerplate.entity.coupon.*;
import com.example.javaspringbootboilerplate.service.CouponService;
import com.example.javaspringbootboilerplate.service.HighValueCouponBySlotService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Log4j2
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService couponService;
    private final HighValueCouponBySlotService highValueCouponService;

    @Autowired
    public CouponController(CouponService couponService, HighValueCouponBySlotService highValueCouponService) {
        this.couponService = couponService;
        this.highValueCouponService = highValueCouponService;
    }

    @PostMapping
    public ResponseEntity<Coupon> createCoupon(@RequestBody CouponCreationInfo couponCreationInfo) {
        return ResponseEntity.ok(couponService.createCoupon(couponCreationInfo));
    }


    @PostMapping("/highValue")
    public ResponseEntity<Coupon> createHighValueCoupon(@RequestBody CouponCreationInfo couponCreationInfo) {
        return ResponseEntity.ok(highValueCouponService.createCoupon(couponCreationInfo));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Coupon> getCoupon(@PathVariable String code) {
        return couponService.getCoupon(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/highValue/{code}")
    public ResponseEntity<Coupon> getHighValueCoupon(@PathVariable String code) {
        return highValueCouponService.getCoupon(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/claim")
    public ResponseEntity<CouponClaimResponse> claimCoupon(@RequestBody CouponClaimRequest claimRequest) {
        try {
            CouponClaimResponse couponClaimResponse = couponService.claimCoupon(claimRequest.getUserId(), claimRequest.getCouponCode(), claimRequest.getEmail());

            if (couponClaimResponse.getResult() == ClaimResult.SUCCESS) {
                log.info("Success: " + claimRequest.getUserId() + " remaining: " + couponClaimResponse.getRemainingSlots());
                return ResponseEntity.ok(couponClaimResponse);
            } else {
                return ResponseEntity.internalServerError().body(couponClaimResponse);
            }
        } catch (Exception e) {

            String message="No Message on error";
            StackTraceElement[] stackTrace = e.getStackTrace();
            if(stackTrace!=null && stackTrace.length>0) {
                message="";
                for (StackTraceElement el : stackTrace) {
                    message += "\n" + el.toString();
                }
            }
            log.error(message);
            return ResponseEntity.internalServerError().body(CouponClaimResponse.failure(ClaimResult.ERROR));
        }
    }

    @PostMapping("/highValue/claim")
    public ResponseEntity<CouponClaimResponse> claimHighValueCoupon(@RequestBody CouponClaimRequest claimRequest) {
        try {
            CouponClaimResponse couponClaimResponse = highValueCouponService.claimCoupon(claimRequest.getUserId(), claimRequest.getCouponCode(), claimRequest.getEmail());

            if (couponClaimResponse.getResult() == ClaimResult.SUCCESS) {
                log.info("Success: " + claimRequest.getUserId() + " remaining: " + couponClaimResponse.getRemainingSlots());
                return ResponseEntity.ok(couponClaimResponse);
            } else {
                return ResponseEntity.internalServerError().body(couponClaimResponse);
            }
        } catch (Exception e) {

            String message="No Message on error";
            StackTraceElement[] stackTrace = e.getStackTrace();
            if(stackTrace!=null && stackTrace.length>0) {
                message="";
                for (StackTraceElement el : stackTrace) {
                    message += "\n" + el.toString();
                }
            }
            log.error(message);
            return ResponseEntity.internalServerError().body(CouponClaimResponse.failure(ClaimResult.ERROR));
        }
    }
}