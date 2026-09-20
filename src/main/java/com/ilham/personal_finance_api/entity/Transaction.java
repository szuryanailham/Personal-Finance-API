package com.ilham.personal_finance_api.entity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
     private UUID id;

    @Column(unique = true, nullable = false)
    private String transactionCode;

    private String transactionName;

     @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

     
     @ManyToOne 
     @JoinColumn(name = "category_id")
     private Category category;
     private BigDecimal amount;
     private String description;
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

     

}
