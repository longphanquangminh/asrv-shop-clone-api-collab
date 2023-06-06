package com.vti.group1.shopapi.repository;

import com.vti.group1.shopapi.entity.Catalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<Catalog, Long> {
}
