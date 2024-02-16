package com.sunlei.jpa.jpademo.pojo;

import com.querydsl.core.annotations.QueryEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

/**
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "artical")
@QueryEntity
public class DemoEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Integer aid;
    private String author;
    private String title;
    private Date createTime;
}
