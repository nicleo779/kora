package com.example.simple.dto;

public class UserFilter {
    private AgeRange range;

    public UserFilter(AgeRange range) {
        this.range = range;
    }

    public AgeRange getRange() {
        return range;
    }

    public void setRange(AgeRange range) {
        this.range = range;
    }
}
