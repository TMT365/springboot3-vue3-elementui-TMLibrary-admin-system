package com.tmt.TMLibrary.entity;

import java.util.List;
import lombok.Data;
/**
 * <p>这个类用于表示包含订单项的订单，一个UUID的订单ID，可以有很多不同的订单项。</p>
 * OrderWithItems
 */
@Data
public class OrderWithItems {
    private Order order;
    private List<OrderItem> items; 
}
