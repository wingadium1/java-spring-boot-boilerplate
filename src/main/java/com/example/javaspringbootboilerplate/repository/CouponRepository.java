package com.example.javaspringbootboilerplate.repository;

import com.example.javaspringbootboilerplate.entity.coupon.Coupon;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRepository extends CrudRepository<Coupon, String> {
}
