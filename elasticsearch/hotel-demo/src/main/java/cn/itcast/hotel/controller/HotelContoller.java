package cn.itcast.hotel.controller;

import cn.itcast.hotel.pojo.PageResult;
import cn.itcast.hotel.pojo.RequestParams;
import cn.itcast.hotel.service.IHotelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 *
 */
@RestController
@RequestMapping("/hotel")
@CrossOrigin
@Slf4j
public class HotelContoller {
    @Autowired
    private IHotelService hotelService;

    @PostMapping("/list")
    public PageResult search(@RequestBody RequestParams params){
        log.info(params.toString());
        return hotelService.search(params);
    }

    @PostMapping("/filters")
    public Map<String, List<String>> getFilter(@RequestBody RequestParams params){
        return hotelService.filters(params);

    }
    @GetMapping("/suggestion")
    public List<String> getSuggestions(@RequestParam("key") String prefix){
        return hotelService.getSuggestions(prefix);
    }
}
