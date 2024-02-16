package cn.itcast.hotel.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 */
@Data
@NoArgsConstructor
public class RequestParams {
    private String key;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String city;
    private String startName;
    private String brand;
    private Integer minPrice;
    private Integer maxPrice;
    private String location;
}
