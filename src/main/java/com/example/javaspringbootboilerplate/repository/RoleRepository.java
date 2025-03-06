package com.example.javaspringbootboilerplate.repository;

import com.example.javaspringbootboilerplate.entity.Role;
import com.example.javaspringbootboilerplate.util.Constants;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(Constants.RoleEnum name);
}
