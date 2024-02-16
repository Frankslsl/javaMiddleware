package com.sunlei.jpa.jpademo;

import com.sunlei.jpa.jpademo.pojo.DemoEntity;
import com.sunlei.jpa.jpademo.service.Iservice;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;

@SpringBootTest
@Slf4j
class JpaDemoApplicationTests {
    @Autowired
    Iservice iservice;

    @Test
    void insertTest() {
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY:mm:DD");
        String format = simpleDateFormat.format(date);
        DemoEntity demoEntity = new DemoEntity(1, "sunlei", "hello", date);
        Object save = iservice.save(demoEntity);
        log.info(save.toString());
    }

    @Test
    void getTest(){
        DemoEntity hello = iservice.findByTitleIgnoreCase("hello");
        log.info(hello.toString());
        List<DemoEntity> byAuthorIgnoreCase = iservice.findByAuthorIgnoreCase("sunlei", Sort.by("aid").descending());
        Sort.TypedSort<DemoEntity> entityTypedSort = Sort.sort(DemoEntity.class);
        Sort by = entityTypedSort.by(DemoEntity::getAid);

    }

}
