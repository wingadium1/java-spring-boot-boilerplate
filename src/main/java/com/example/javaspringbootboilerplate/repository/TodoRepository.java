package com.example.javaspringbootboilerplate.repository;

import com.example.javaspringbootboilerplate.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TodoRepository extends JpaRepository<Todo, Integer> {
}
