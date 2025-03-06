package com.example.javaspringbootboilerplate.controller;

import com.example.javaspringbootboilerplate.entity.otp.OTPGenerateRequest;
import com.example.javaspringbootboilerplate.entity.otp.OTPVerificationRequest;
import com.example.javaspringbootboilerplate.service.OTPService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/otp")
@Log4j2
public class OTPControler {

    @Autowired
    private OTPService otpService;

    @PostMapping("/generate")
    public ResponseEntity<Object> otpRequest(@RequestBody OTPGenerateRequest otpGenerateRequest) {
        try {
            otpService.generateOtp(otpGenerateRequest.getRequestId());
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
        return ResponseEntity.ok("OTP Generate");
    }

    @PostMapping("/verify")
    public ResponseEntity<Object> otpVerifyRequest(@RequestBody OTPVerificationRequest otpVerificationRequest) {
        try {
            boolean result = otpService.verifyOtp(otpVerificationRequest.getRequestId(), otpVerificationRequest.getOtp());
            if (result) {
                return ResponseEntity.ok("OTP Verified");
            } else {
                return ResponseEntity.internalServerError().body("Wrong OTP");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
