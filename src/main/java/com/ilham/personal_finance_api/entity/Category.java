package com.ilham.personal_finance_api.entity;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categories")


public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    
    private UUID id;

    private String name;

    private String type;

    @OneToMany(mappedBy = "category")
    private List<Transaction> transactions;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

}
