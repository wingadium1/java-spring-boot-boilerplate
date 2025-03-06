package com.example.javaspringbootboilerplate.repository;

import com.example.javaspringbootboilerplate.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettingRepository extends JpaRepository<Setting, UUID> {
}
