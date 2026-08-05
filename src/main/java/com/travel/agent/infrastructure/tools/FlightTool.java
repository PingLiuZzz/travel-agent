package com.travel.agent.infrastructure.tools;

import com.travel.agent.domain.flight.Flight;
import com.travel.agent.infrastructure.external.FlightClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 航班搜索工具（Function Calling）。
 */
@Component
public class FlightTool {

    private final FlightClient flightClient;

    @Autowired
    public FlightTool(FlightClient flightClient) {
        this.flightClient = flightClient;
    }

    @Tool("搜索航班：根据出发城市、到达城市、日期查询可选航班及价格")
    public List<Flight> searchFlights(String fromCity, String toCity, String date) {
        return flightClient.search(fromCity, toCity, date);
    }
}
