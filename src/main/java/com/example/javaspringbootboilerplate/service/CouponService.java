package com.example.javaspringbootboilerplate.service;

import com.example.javaspringbootboilerplate.config.RedisInterfaceConst;
import com.example.javaspringbootboilerplate.entity.coupon.ClaimResult;
import com.example.javaspringbootboilerplate.entity.coupon.Coupon;
import com.example.javaspringbootboilerplate.entity.coupon.CouponClaimResponse;
import com.example.javaspringbootboilerplate.entity.coupon.CouponCreationInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
public class CouponService {
    @Autowired
    @Qualifier("redisTxTemplate")
    private RedisTemplate<String, Object> template;

    // Get coupon details
    public Optional<Coupon> getCoupon(String couponCode) {
        Object couponData = template.opsForHash().get(RedisInterfaceConst.LIST_PREFIX + "coupon:" + couponCode, "details");
        String couponCounterKey = RedisInterfaceConst.SINGLE_PREFIX + "coupon:" + couponCode + ":counter";

        Object countObject = template.opsForValue().get(couponCounterKey);
        if (countObject == null) {
            return Optional.empty();
        }
        int numberOfRemainingSlot = (int) countObject;
        if (couponData == null) {
            return Optional.empty();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Optional<Coupon> optionalCoupon = Optional.of(mapper.convertValue(couponData, Coupon.class));
            Coupon coupon = optionalCoupon.get();
            coupon.setNumberOfRemainSlot(numberOfRemainingSlot);
            return Optional.of(coupon);
        } catch (Exception e) {
            log.error("Error deserializing coupon data", e);
            return Optional.empty();
        }
    }

    public Coupon createCoupon(CouponCreationInfo info) {
        return template.execute(new SessionCallback<>() {
            @Override
            public Coupon execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                Coupon coupon = new Coupon(info.getCode(), info.getNumberOfSlot(), info.getNumberOfSlot());
                String couponCodeKey = RedisInterfaceConst.LIST_PREFIX + "coupon:" + info.getCode();
                String couponCounterKey = RedisInterfaceConst.SINGLE_PREFIX + "coupon:" + info.getCode() + ":counter";
                operations.opsForValue().set(couponCounterKey, info.getNumberOfSlot());
                template.opsForHash().put(couponCodeKey, "details", coupon);

                operations.exec();
                return coupon;
            }
        });
    }

    public CouponClaimResponse claimCoupon(String userId, String couponCode, String email) throws Exception {
        String userSetKey = RedisInterfaceConst.LIST_PREFIX + "coupon:" + couponCode + ":couponslots:" + "userId";
        String couponCounterKey = RedisInterfaceConst.SINGLE_PREFIX + "coupon:" + couponCode + ":counter";

        // find the coupon by using coupon code from redis
        // key - value : coupon_code -> coupon

        Optional<Coupon> couponObject = getCoupon(couponCode);
        if (couponObject.isEmpty()) {
            return CouponClaimResponse.failure(ClaimResult.COUPON_NOT_FOUND, "coupon not found");
        }

        log.info("Start claim coupon");
        while (true) { // Retry loop
            try {
                return template.execute(new SessionCallback<>() {
                    @Override
                    public CouponClaimResponse execute(RedisOperations operations) throws DataAccessException {
                        // optimistic lock
                        // operations.watch(userSetKey);
                        operations.watch(Arrays.asList(couponCounterKey, userSetKey));
                        // query to redis to check the user id already have claimed a coupon
                        // key-value: user_id + coupon_code -> coupon slot
                        // check null or exist with couponSlotData
                        // throw an Status
                        if (Boolean.TRUE.equals(operations.opsForSet().isMember(userSetKey, userId))) {
                            operations.unwatch(); // release lock
                            return CouponClaimResponse.failure(ClaimResult.ALREADY_CLAIMED, "user already claimed this coupon");
                        }

                        log.info("User have not claim this coupon yet");

                        Object countObject = operations.opsForValue().get(couponCounterKey);
                        if (countObject == null || (int) countObject <= 0) {
                            operations.unwatch();
                            return CouponClaimResponse.failure(ClaimResult.NO_SLOTS);
                        }
                        int numberOfRemainSlot = (int) countObject;
                        log.info("Number of remain slot:" + numberOfRemainSlot);

                        // use atomic action (transaction) to reduce number remain slot + add the information of user to coupon slot
                        // sync.multi
                        operations.multi();
                        operations.opsForValue().decrement(couponCounterKey);
                        operations.opsForSet().add(userSetKey, userId);

                        //end the transaction
                        List<Object> results = operations.exec();

                        // another case
                        if (results == null || results.isEmpty()) {
                            // Transaction failed due to concurrent modification
                            return CouponClaimResponse.failure(ClaimResult.ERROR, "Transaction failed, please try again");
                        }
                        // success case -> return success remaining +

                        Object newCountObj = operations.opsForValue().get(couponCounterKey);
                        int remainingSlots = (int) newCountObj;
                        return CouponClaimResponse.success(couponCode, remainingSlots);

                    }
                });
            } catch (Exception e) {
                log.error("Error while claiming coupon", e);
                return CouponClaimResponse.failure(ClaimResult.ERROR, "System error occurred");
            }
        }
    }
}
