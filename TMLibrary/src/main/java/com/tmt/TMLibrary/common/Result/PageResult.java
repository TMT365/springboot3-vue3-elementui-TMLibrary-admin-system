package com.tmt.TMLibrary.common.Result;

import java.util.List;
import lombok.Getter;


@Getter
// 这是一个简单的PageResult类，用于封装分页查询的结果，包含总记录数和数据列表。
public class PageResult<T> {
    // total 用于展示查询到的总数
    private long total;
    // 该页所有VO数据
    private List<T> data;

    public PageResult() {
    }

    public PageResult(long total, List<T> data) {
        this.total = total;
        this.data = data;
    }
}