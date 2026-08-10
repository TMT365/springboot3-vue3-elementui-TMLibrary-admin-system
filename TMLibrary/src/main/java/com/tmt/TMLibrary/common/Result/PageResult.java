package com.tmt.TMLibrary.common.Result;

import java.util.List;

// 这是一个简单的PageResult类，用于封装分页查询的结果，包含总记录数和数据列表。
public class PageResult<T> {
    private long total;
    private List<T> data;

    public PageResult() {
    }

    public PageResult(long total, List<T> data) {
        this.total = total;
        this.data = data;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "total=" + total +
                ", data=" + data +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PageResult<?> that = (PageResult<?>) o;

        if (total != that.total) return false;
        return data != null ? data.equals(that.data) : that.data == null;
    }

}