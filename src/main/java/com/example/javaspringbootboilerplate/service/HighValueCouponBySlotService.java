package com.example.javaspringbootboilerplate.service;

import com.example.javaspringbootboilerplate.config.RedisInterfaceConst;
import com.example.javaspringbootboilerplate.entity.coupon.ClaimResult;
import com.example.javaspringbootboilerplate.entity.coupon.Coupon;
import com.example.javaspringbootboilerplate.entity.coupon.CouponClaimResponse;
import com.example.javaspringbootboilerplate.entity.coupon.CouponCreationInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HighValueCouponBySlotService {

    private final RedisTemplate<String, Object> template;
    private final ObjectMapper objectMapper;

    private static final String COUPON = "coupon2:";
    private static final String COUPON_CLAIM_QUEUE_PREFIX = RedisInterfaceConst.LIST_PREFIX + COUPON + "claim:queue:";
    private static final String COUPON_RESULT_PREFIX = RedisInterfaceConst.LIST_PREFIX + COUPON + "claim:result:";
    private static final long MAX_WAIT_TIME = 2000; // 2 seconds max wait time
    private static final long POLLING_INTERVAL = 100; // 100ms polling interval

    @Value("${coupon.queue.processor.enabled:true}")
    private boolean queueProcessorEnabled;

    @Autowired
    public HighValueCouponBySlotService(@Qualifier("redisTxTemplate") RedisTemplate<String, Object> template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startQueueProcessor() {
        if (queueProcessorEnabled) {
            log.info("Enable QueueProcessor");
            new Thread(this::processClaimQueue).start();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ClaimRequest {
        private String couponCode;
        private String userId;
        private String requestId;
        private long timestamp;
    }

    public Optional<Coupon> getCoupon(String couponCode) {
        Object couponData = template.opsForHash().get(RedisInterfaceConst.LIST_PREFIX + COUPON + couponCode, "details");

        if (couponData == null) {
            return Optional.empty();
        }

        try {
            Coupon coupon = objectMapper.convertValue(couponData, Coupon.class);
            coupon.setNumberOfRemainSlot(countAvailableSlots(coupon));
            return Optional.of(coupon);
        } catch (Exception e) {
            log.error("Error deserializing coupon data", e);
            return Optional.empty();
        }
    }

    public int countAvailableSlots(Coupon coupon) {
        String couponSlotPrefix = RedisInterfaceConst.SINGLE_PREFIX + COUPON + coupon.getCode() + ":slot";
        return Math.toIntExact(template.opsForZSet().count(couponSlotPrefix, 0, 0));
    }

    public Coupon createCoupon(CouponCreationInfo info) {
        return new SessionCallback<>() {
            @Override
            public Coupon execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                Coupon coupon = new Coupon(info.getCode(), info.getNumberOfSlot(), info.getNumberOfSlot());
                String couponCodeKey = RedisInterfaceConst.LIST_PREFIX + COUPON + info.getCode();
                String couponSlotPrefix = RedisInterfaceConst.SINGLE_PREFIX + COUPON + info.getCode() + ":slot";
                for (int i = 0; i < coupon.getNumberOfSlot(); i++) {
                    operations.opsForZSet().add(couponSlotPrefix, i, 0);
                }
                template.opsForHash().put(couponCodeKey, "details", coupon);

                operations.exec();
                return coupon;
            }
        }.execute(template);
    }

    public CouponClaimResponse claimCoupon(String userId, String couponCode, String email) {
        try {
            String requestId = UUID.randomUUID().toString();
            ClaimRequest request = new ClaimRequest(couponCode, userId, requestId, System.currentTimeMillis());
            String requestJson = objectMapper.writeValueAsString(request);

            String queueKey = COUPON_CLAIM_QUEUE_PREFIX + couponCode;
            template.opsForList().rightPush(queueKey, requestJson);

            return waitForClaimResult(requestId);

        } catch (Exception e) {
            log.error("Error while queueing coupon claim", e);
            return CouponClaimResponse.failure(ClaimResult.ERROR, "System error occurred");
        }
    }

    private CouponClaimResponse waitForClaimResult(String requestId) {
        String resultKey = COUPON_RESULT_PREFIX + requestId;
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
            Object result = template.opsForValue().get(resultKey);
            if (result != null) {
                try {
                    template.delete(resultKey);
                    log.info("result: " + result.toString());
                    return objectMapper.readValue(result.toString(), CouponClaimResponse.class);
                } catch (Exception e) {
                    log.error("Error parsing result", e);
                    break;
                }
            }
            try {
                Thread.sleep(POLLING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return CouponClaimResponse.failure(ClaimResult.ERROR, "Request timeout");
    }

    private void processClaimQueue() {
        while (queueProcessorEnabled) {
            try {
                Set<String> queueKeys = template.keys(COUPON_CLAIM_QUEUE_PREFIX + "*");
                for (String queueKey : queueKeys) {
                    log.info("processClaimQueue found key: " + queueKey);
                    processQueue(queueKey);
                }
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("Error in queue processor", e);
            }
        }
    }

    private void processQueue(String queueKey) {
        String couponCode = queueKey.substring(COUPON_CLAIM_QUEUE_PREFIX.length());
        log.info("process coupon code: " + couponCode + " at queueKey" + queueKey);
        String userSetKey = RedisInterfaceConst.LIST_PREFIX + COUPON + couponCode + ":couponslots:" + "userId";

        while (true) {
            Object requestJson = template.opsForList().leftPop(queueKey);
            if (requestJson == null) break;

            try {
                ClaimRequest request = objectMapper.readValue(requestJson.toString(), ClaimRequest.class);
                CouponClaimResponse response = processClaimRequest(request, userSetKey);

                String resultKey = COUPON_RESULT_PREFIX + request.getRequestId();
                template.opsForValue().set(resultKey, objectMapper.writeValueAsString(response), 30, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("Error processing claim request", e);
            }
        }
    }

    private CouponClaimResponse processClaimRequest(ClaimRequest request, String userSetKey) {
        return template.execute(new SessionCallback<CouponClaimResponse>() {
            @Override
            public CouponClaimResponse execute(RedisOperations operations) throws DataAccessException {
                String userId = request.getUserId();
                String couponCode = request.getCouponCode();

                String couponSlotPrefix = RedisInterfaceConst.SINGLE_PREFIX + COUPON + couponCode + ":slot";

                operations.watch(Arrays.asList(couponSlotPrefix, userSetKey));

                if (Boolean.TRUE.equals(operations.opsForSet().isMember(userSetKey, userId))) {
                    operations.unwatch();
                    return CouponClaimResponse.failure(ClaimResult.ALREADY_CLAIMED, "user already claimed this coupon");
                }

                log.info("User has not claimed this coupon yet");

                int numberOfRemainSlot = Math.toIntExact(operations.opsForZSet().count(couponSlotPrefix, 0, 0));
                log.info("Number of remaining slots: " + numberOfRemainSlot);
                if (numberOfRemainSlot == 0) {
                    operations.unwatch();
                    return CouponClaimResponse.failure(ClaimResult.NO_SLOTS);
                }

                operations.multi();

                Set<String> range = operations.opsForZSet().rangeByScore(couponSlotPrefix, 0, 0);
                log.info("range set size of " + couponSlotPrefix + " is " + range.size());
                String firstElement = range.isEmpty() ? null : range.iterator().next();
				log.info("found Element: " + firstElement);
                operations.opsForZSet().remove(couponSlotPrefix, firstElement);
                operations.opsForSet().add(userSetKey, userId);

                List<Object> results = operations.exec();

                if (results.isEmpty()) {
                    return CouponClaimResponse.failure(ClaimResult.ERROR, "Transaction failed, please try again");
                }

                int remainingSlots = Math.toIntExact(template.opsForZSet().count(couponSlotPrefix, 0, 0));
                return CouponClaimResponse.success(couponCode, remainingSlots);
            }
        });
    }
}