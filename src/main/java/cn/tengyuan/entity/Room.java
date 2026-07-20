package cn.tengyuan.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Room {

    private int id;

    private String name;

    private BigDecimal money;

    private String password;

    private String number;

    private int subsidy;
}
