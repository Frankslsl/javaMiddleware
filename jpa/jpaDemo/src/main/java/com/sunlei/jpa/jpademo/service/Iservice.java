package com.sunlei.jpa.jpademo.service;

import com.sunlei.jpa.jpademo.pojo.DemoEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.relational.core.sql.In;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 *
 */
public interface Iservice extends ListPagingAndSortingRepository<DemoEntity, Integer>, QuerydslPredicateExecutor<DemoEntity> {
    List<DemoEntity> findByAuthorIgnoreCase(@Nullable String author, Sort sort);
    List<DemoEntity> findByAuthorLike(String author);
    DemoEntity findByTitleIgnoreCase(String title);
    @Query("select d from DemoEntity d where d.title like ?1")
    DemoEntity findByTitleLike(String title);
    @Query("select d from DemoEntity d where d.title like ?1")
    DemoEntity sunlei(String title);
}
