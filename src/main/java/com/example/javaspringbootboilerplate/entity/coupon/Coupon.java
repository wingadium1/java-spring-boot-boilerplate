package com.example.javaspringbootboilerplate.entity.coupon;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Coupon {
    private String code;
    private int numberOfSlot;
    private int numberOfRemainSlot;
}
