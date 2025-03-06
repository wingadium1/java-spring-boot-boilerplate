package com.example.javaspringbootboilerplate.service;

import com.example.javaspringbootboilerplate.config.RedisInterfaceConst;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@Log4j2
public class OTPService {

    private static final int OTP_LENGTH = 6;
    private static  final int OTP_EXPIRE_TIME = 5; //in minute

    @Autowired
    @Qualifier("redisTxTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    public void generateOtp(String requestId) throws Exception {
        String otp = generateRandomOTP();

        String otpRequestIdKey = RedisInterfaceConst.SINGLE_PREFIX + "otp:" + requestId;

        Boolean otpExisted = redisTemplate.hasKey(otpRequestIdKey);
        if (otpExisted) {
            throw new Exception("OTP Already requested");
        }

        String hashedOtp = BCrypt.hashpw(otp, BCrypt.gensalt());

        redisTemplate.opsForValue().set(otpRequestIdKey, hashedOtp);
        redisTemplate.expire(otpRequestIdKey, Duration.ofMinutes(OTP_EXPIRE_TIME));

        sendSMS(otp);
    }

    public boolean verifyOtp(String requestId, String otp) {
        String otpRequestIdKey = RedisInterfaceConst.SINGLE_PREFIX + "otp:" + requestId;

        Object otpHashedObject = redisTemplate.opsForValue().get(otpRequestIdKey);

        if (otpHashedObject ==null){
            throw  new RuntimeException("OTP does not existed");
        }

        String otpHashed = (String) otpHashedObject;

        boolean checkpw = BCrypt.checkpw(otp, otpHashed);

        if (checkpw) {
            redisTemplate.delete(otpRequestIdKey);
            return  true;
        }

        return false;
    }

    private String generateRandomOTP(){
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            int randomDigt = random.nextInt(0, 10);
            stringBuilder.append(randomDigt);
        }
        return stringBuilder.toString();
    }

    private void sendSMS(String otp){
        log.info("Sending SMS OTP:  " + otp);
    }
}
