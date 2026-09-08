package com.tmt.TMLibrary.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 将第三方返回的IP数据封装成VO对象，只保留必要字段
 */
@Getter
@Setter
public class IpApiVo {
    private String status;
    private String message;
    private String country;
    private String countryCode;
    private String region;
    private String regionCode;
    private String city;
    private String query;
    private String timeZone;

    public String AcquiredIp() {
        if (status.equalsIgnoreCase("success"))
            return String.format("%s(%s) - %s(%s) - %s [%s]", country, countryCode, region, regionCode, city, timeZone);
        else
            return query;
    }
}
