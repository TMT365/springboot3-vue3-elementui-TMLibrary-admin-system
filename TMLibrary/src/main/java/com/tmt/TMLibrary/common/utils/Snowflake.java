package com.tmt.TMLibrary.common.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 *<p>Snowflake 算法用来生成订单 id, 可以确保在分布式和高并发场景下订单ID唯一， 实现原理： Snowflake 算法
 * 将64位的long型ID分为四个部分。</p>
 * <strong>1. 符号位（1位）</strong>
 * <p>始终为0，用于标识ID是否是正数</p>
 * <strong>2. 时间戳 （41位）</strong>
 * <p>时间戳占据了整个ID的41位，精确到毫秒级，可以支持69年的时间戳。这使得雪花算法能够支持未来数十年的唯一性。时间戳部分还提供了排序的功能，可以根据时间戳来对数据进行排序</p>
 * <strong>3. 机器ID（10位）</strong>
 * <p>工作机器ID占据了ID的10位，可以支持最多1024个工作节点。这使得在同一台机器上运行的不同应用程序实例可以使用不同的工作机器ID来生成唯一的ID。</p>
 * <strong>4. 序列号（12位）</strong>
 * <p>序列号占据了ID的12位，可以支持每个节点每毫秒产生4096个唯一的ID。这使得在同一台机器上运行的不同应用程序实例可以生成唯一的ID，
 * 即使在毫秒级别内也能保证唯一性。</p>
 * <pre>
 *     1位                    41位                          5位  5位        12位
 *     0 -  000000000000000000000000000000000000000000 - 00000 00000 - 000000000000
 *   符号位                    时间戳                         机器ID        序列号
 * 注：上述划分中的工作机器ID位数（10位或5位）可能会因不同的实现方式而有所差异。在Twitter原始的雪花算法设计中，可能并没有直接使用10位来表示工作机器ID，而是将机器ID部分进一步细分为数据中心ID和工作机器ID，其中每个部分可能占用更少的位数（如各5位）。
 * </pre>
 * <strong>5. 注意点 </strong>
 * <p>在这个代码中，首先定义了雪花算法的各个组成部分，包括时间戳、工作机器id、数据中心id和序列号。然后，根据这些组成部分计算出一个唯一的ID。
 * 在生成ID的过程中，需要考虑时间戳的回拨问题，如果当前时间小于上一次生成ID的时间戳，那么就抛出一个异常。同时，在同一毫秒内，如果生成的ID数量达到上限（2^12个），那么就等待下一毫秒再生成。</p>
 */
public class Snowflake {
    // 设置开始时间戳 2026-09-01
    private final long timestampStart = ZonedDateTime.of(2026,9,1,0,0,0,0,ZoneId.of("UTC+8")).toInstant().toEpochMilli();

    // 工作机器ID位数
    private final long workerIdBits = 5L;
    // 数据中心ID位数
    private final long datacenterIdBits = 5L;
    // 支持最大的工作机器ID
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // 支持最大的数据中心ID
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    // 序列号id要占的位数
    private final long sequenceBits = 12L;
    // 工作机器id要左移的位数 (12)
    private final long workerIdShift = sequenceBits;
    // 数据中心id要左移的位数 (5 + 12)
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    // 时间戳要左移的位数(5 + 5 + 12)
    private final long timestampShift = sequenceBits + workerIdBits + datacenterIdBits;
    // 生成序列id的最大编码, (0b111111111111=0xfff=4096)
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);
    // 生成的序列id
    private long sequence = 0L;
    // 生成的工作机器id (0 ~ 31)
    private long workerId = 0L;
    // 生成的数据中心id (0 ~ 31)
    private long datacenterId = 0L;
    // 上一次生成成功的时间戳
    private long lastTimestamp = -1L;

    public Snowflake(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("workerId can't be greater than %d and less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenterId can't be greater than %d and less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    // 生成随机序列 (线程安全)
    public synchronized long nextId() {
        // 获取现在的时间戳，减去项目开始时间戳
        long timestampCurrent = timeGen();

        if (timestampCurrent < lastTimestamp ) {
            // 如果当前时间戳小于上次时间戳，说明系统时间回退了，抛出异常
            throw new RuntimeException(
                    String.format(
                            "Clock moved backwards.  Refusing to generate id for %d milliseconds, Because of the timestampCurrent[%d] less than lastTimestamp[%d]",
                            (lastTimestamp - timestampCurrent), timestampCurrent, lastTimestamp));
        }

        if (lastTimestamp == timestampCurrent) {
            // 如果时间戳相等说明，在多线程高并发模式下同一个时间点有多个请求
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 说明当前毫秒内的序列已经满了，要过渡到下一个毫秒内
                timestampCurrent = tilNextMillis(lastTimestamp);
            }
        }
        // 时间戳改变，毫秒内序列重置
        else {
            sequence = 0L;
        }

        // 更新lastTimestamp
        lastTimestamp = timestampCurrent;
        // 产生随机序列
        return  (lastTimestamp - timestampStart) << timestampShift
                | (datacenterId << datacenterIdShift)
                | workerId << workerIdShift
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        // 重新获取时间戳
        long timestampCurrent = timeGen();
        while (timestampCurrent <= lastTimestamp) {
            timestampCurrent = timeGen();
        }
        return timestampCurrent;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

}
