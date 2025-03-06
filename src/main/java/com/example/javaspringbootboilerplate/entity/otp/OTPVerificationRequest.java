package com.example.javaspringbootboilerplate.entity.otp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OTPVerificationRequest {
    private String requestId;
    private String otp;
}
