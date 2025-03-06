package com.example.javaspringbootboilerplate.service;

import com.example.javaspringbootboilerplate.config.RedisInterfaceConst;
import com.example.javaspringbootboilerplate.entity.coupon.ClaimResult;
import com.example.javaspringbootboilerplate.entity.coupon.CouponClaimResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
public class HighValueCouponService {
    @Autowired
    @Qualifier("redisTxTemplate")
    private RedisTemplate<String, Object> template;
    private ObjectMapper objectMapper;

    private static final String COUPON_CLAIM_QUEUE_PREFIX = RedisInterfaceConst.LIST_PREFIX + "coupon:claim:queue:";
    private static final String COUPON_RESULT_PREFIX = RedisInterfaceConst.LIST_PREFIX + "coupon:claim:result:";
    private static final long MAX_WAIT_TIME = 2000; // 2 seconds max wait time
    private static final long POLLING_INTERVAL = 100; // 100ms polling interval

    @Value("${coupon.queue.processor.enabled:true}")
    private boolean queueProcessorEnabled;

    @Autowired
    public HighValueCouponService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startQueueProcessor() {
//        if (queueProcessorEnabled) {
//            log.info("Enable QueueProcessor");
//            new Thread(this::processClaimQueue).start();
//        }
    }

    // Data class for claim request
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class ClaimRequest {
        private String couponCode;
        private String userId;
        private String requestId;
        private long timestamp;
    }

    public CouponClaimResponse claimCoupon(String userId, String couponCode, String email) throws Exception {
        try {
            // Generate unique request ID
            String requestId = UUID.randomUUID().toString();

            // Create claim request
            ClaimRequest request = new ClaimRequest(couponCode, userId, requestId, System.currentTimeMillis());
            String requestJson = objectMapper.writeValueAsString(request);

            // Push to claim queue
            String queueKey = COUPON_CLAIM_QUEUE_PREFIX + couponCode;
            template.opsForList().rightPush(queueKey, requestJson);

            // Wait and poll for result
            return waitForResult(requestId, couponCode);

        } catch (Exception e) {
            log.error("Error while queueing coupon claim", e);
            return CouponClaimResponse.failure(ClaimResult.ERROR, "System error occurred");
        }
    }

    private CouponClaimResponse waitForResult(String requestId, String couponCode) {
        String resultKey = COUPON_RESULT_PREFIX + requestId;
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < MAX_WAIT_TIME) {
            Object result = template.opsForValue().get(resultKey);
            if (result != null) {
                try {
                    // Clean up result key
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
                // Process all active coupon queues
                Set<String> queueKeys = template.keys(COUPON_CLAIM_QUEUE_PREFIX + "*");
                if (queueKeys != null) {
                    for (String queueKey : queueKeys) {
                        log.info("processClaimQueue found key: " + queueKey);
                        processQueue(queueKey);
                    }
                }
                Thread.sleep(500); // Process every 1 second
            } catch (Exception e) {
                log.error("Error in queue processor", e);
            }
        }
    }

    private void processQueue(String queueKey) {
        String couponCode = queueKey.substring(COUPON_CLAIM_QUEUE_PREFIX.length());
        log.info("process coupon code: " + couponCode  + " at queueKey" + queueKey );
        String couponCounterKey = RedisInterfaceConst.SINGLE_PREFIX + "coupon:" + couponCode + ":counter";
        String userSetKey = RedisInterfaceConst.LIST_PREFIX + "coupon:" + couponCode + ":couponslots:" + "userId";

        while (true) {
            // Pop claim request from queue
            Object requestJson = template.opsForList().leftPop(queueKey);
            if (requestJson == null) break;

            try {
                ClaimRequest request = objectMapper.readValue(requestJson.toString(), ClaimRequest.class);
                CouponClaimResponse response = processClaimRequest(request, couponCounterKey, userSetKey);

                // Store result
                String resultKey = COUPON_RESULT_PREFIX + request.getRequestId();
                template.opsForValue().set(resultKey, objectMapper.writeValueAsString(response), 30, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("Error processing claim request", e);
            }
        }
    }

    private CouponClaimResponse processClaimRequest(ClaimRequest request, String couponCounterKey, String userSetKey) {
        return template.execute(new SessionCallback<CouponClaimResponse>() {
            @Override
            public CouponClaimResponse execute(RedisOperations operations) throws DataAccessException {
                // Watch the keys
                String userId = request.getUserId();
                String couponCode = request.getCouponCode();
                operations.watch(Arrays.asList(couponCounterKey, userSetKey));

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

                // Start transaction
                operations.multi();

                // Add commands to transaction queue
                operations.opsForValue().decrement(couponCounterKey);
                operations.opsForSet().add(userSetKey, userId);

                // Execute transaction
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
    }
}
