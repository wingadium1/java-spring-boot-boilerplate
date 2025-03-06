package com.example.javaspringbootboilerplate.entity.otp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OTPGenerateRequest {
    private String requestId;
}
