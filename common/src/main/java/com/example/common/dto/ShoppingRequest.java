package com.example.common.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public class ShoppingRequest {
    @NotBlank
    private String request;
    private String hidding;
    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    private double price;
    private String dislike;

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getHidding() {
        return hidding;
    }

    public void setHidding(String hidding) {
        this.hidding = hidding;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getDislike() {
        return dislike;
    }

    public void setDislike(String dislike) {
        this.dislike = dislike;
    }
}
