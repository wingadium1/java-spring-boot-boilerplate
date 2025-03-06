package com.example.javaspringbootboilerplate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "todos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Todo extends AbstractBaseEntity {
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name= "isComplete")
    private  boolean isComplete;
}
