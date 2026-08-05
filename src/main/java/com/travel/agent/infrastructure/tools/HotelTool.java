package com.travel.agent.infrastructure.tools;

import com.travel.agent.domain.hotel.HotelBooking;
import com.travel.agent.infrastructure.external.HotelClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 酒店预订工具（Function Calling）。
 *
 * <p>安全约定：仅生成"待确认"订单，前端二次确认后才真正下单。
 */
@Component
public class HotelTool {

    private final HotelClient hotelClient;

    @Autowired
    public HotelTool(HotelClient hotelClient) {
        this.hotelClient = hotelClient;
    }

    @Tool("预订酒店：根据城市、酒店名、入住/退房日期、房型生成待确认订单（不直接支付）")
    public HotelBooking bookHotel(String city, String hotelName, String checkIn, String checkOut, String roomType) {
        return hotelClient.book(city, hotelName, checkIn, checkOut, roomType);
    }
}
