package com.back.together02be.stock.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.back.together02be.stock.entity.Stock;
import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findAll();

	Optional<Stock> findByStockCode(String stockCode);

	boolean existsByStockCode(String stockCode);

}
