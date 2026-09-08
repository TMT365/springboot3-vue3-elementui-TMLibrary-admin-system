package com.tmt.TMLibrary.entity;

import java.util.List;
import lombok.Data;
/**
 * <p>这个类用于表示包含订单项的订单，一个UUID的订单ID，可以有很多不同的订单项。</p>
 * OrderWithItems
 */
@Data
public class OrderWithItems {
    // orderNumber 单独拿出来作为items的归类依据, 这里的orderNumber也要写，不写会出现org.apache.ibatis.reflection.ReflectionException
    private Long orderNumber;
    private Order order;
    private List<OrderItem> items; 
}
