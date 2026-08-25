package com.tmt.TMLibrary.mapper;
import com.tmt.TMLibrary.common.Order.OrderStatus;
import com.tmt.TMLibrary.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import com.tmt.TMLibrary.entity.OrderItem;
import java.util.List;
import com.tmt.TMLibrary.entity.OrderWithItems;
import org.apache.ibatis.annotations.Param;

/**
 * OrderMapper
 */
@Mapper
public interface OrderMapper {
    int insertOrder(Order order);

    int insertOrderItem(OrderItem orderItem);

    Order selectOrderById(@Param("id") Integer id);

    // 实体类在mapper层时，只有一个参数可以不要加@Param注解，多个参数时必须加@Param注解
    // 但是对于java的8种基本类型，String，Date等类型的参数，在mapper层时，只有一个参数可以不要加@Param注解，多个参数时必须加@Param注解
    // 比如：Integer, int, ... 如果不加@Param注解，你只能在.xml文件中使用#{arg0}、#{arg1}、#{arg2}等来获取参数值，而不能使用#{id}、#{status}等来获取参数值
    // 可读性差，建议加上@Param注解


    Order selectOrderByIdForUpdate(@Param("id") Integer id);

    /**
     * 根据ID查询订单项
     * @param id 订单项ID
     * @return 订单项
     */
    OrderItem selectOrderItemById(@Param("id") Integer id);

    /**
     * 根据订单ID查询订单项列表
     * @param orderId
     * @return
     */
    List<OrderItem> selectOrderItemsByOrderId(@Param("orderId") Integer orderId);

    /**
     * 根据订单ID查询订单及订单项
     * @param orderId 订单ID
     * @return 订单及订单项
     */
    OrderWithItems selectOrderWithItemsByOrderId(@Param("orderId") Integer orderId);

    /**
     * 根据用户ID查询订单及订单项
     * @param userId 用户ID
     * @return 订单及订单项
     */
    List<OrderWithItems> selectOrderWithItemsByUserId(@Param("userId") Integer userId);

    /**
     * 根据订单ID更新订单状态, 在以后的业务中, 订单状态可能会有很多种, 但是存java对象和字符串不如存枚举的数字
     * @param id
     * @param status
     * @return
     */
    int updateStatusById(@Param("id") Integer id, @Param("status") OrderStatus status);
}
