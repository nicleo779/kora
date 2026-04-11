package com.nicleo.kora.core.query;

public class Paging {
    private Integer current;
    private Integer size;

    public static Paging of(int current, int size) {
        Paging paging = new Paging();
        paging.setCurrent(current);
        paging.setSize(size);
        return paging;
    }

    public Integer getCurrent() {
        return current;
    }

    public void setCurrent(Integer current) {
        this.current = current;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
