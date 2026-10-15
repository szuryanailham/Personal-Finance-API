package com.ilham.personal_finance_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PersonalFinanceApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PersonalFinanceApiApplication.class, args);
	}

}
