package com.ecodrive.ui;

import java.util.ArrayList;
import java.util.List;

public class ScenarioManager {

    public static class Scenario {
        public String name;
        public String destination;
        public double avgSpeed;
        public String description;

        public Scenario(String name, String destination, double avgSpeed, String description) {
            this.name = name;
            this.destination = destination;
            this.avgSpeed = avgSpeed;
            this.description = description;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static List<Scenario> getScenarios() {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("Urban Congestion (Delhi)", "Connaught Place, Delhi", 12.0, "Heavy stop-and-go traffic in a dense urban core."));
        list.add(new Scenario("Highway Cruise (Expressway)", "Agra, Uttar Pradesh", 95.0, "High-speed sustained driving with minimal braking."));
        list.add(new Scenario("Mountain Climb (Mussoorie)", "Mussoorie, Uttarakhand", 25.0, "Steep incline and frequent sharp turns."));
        list.add(new Scenario("Desert Heat (Jaisalmer)", "Jaisalmer, Rajasthan", 70.0, "High ambient temperature affecting air density and AC load."));
        list.add(new Scenario("Coastal Humidity (Mumbai)", "Marine Drive, Mumbai", 35.0, "High humidity levels impacting aerodynamic drag slightly."));
        list.add(new Scenario("Winter Cold (Shimla)", "Shimla, Himachal Pradesh", 30.0, "Freezing temperatures increasing air density and friction."));
        list.add(new Scenario("Eco-Driving (Hypermiling)", "Indira Nagar, Bangalore", 45.0, "Optimized speed for maximum fuel efficiency."));
        list.add(new Scenario("Aggressive Sport Mode", "Bandra, Mumbai", 85.0, "Rapid acceleration and high-speed bursts."));
        list.add(new Scenario("Rural Dirt Road", "Almora, Uttarakhand", 20.0, "Uneven terrain and high rolling resistance."));
        list.add(new Scenario("Monsoon Rain Drive", "Cherrapunji, Meghalaya", 30.0, "Wet roads and high atmospheric resistance."));
        list.add(new Scenario("IT Park Shuttle", "Electronic City, Bangalore", 28.0, "Moderate urban traffic with timed signals."));
        list.add(new Scenario("Airport Express Link", "IGI Airport, Delhi", 75.0, "Fluid highway-like transit within city limits."));
        list.add(new Scenario("School Zone Crawl", "Vasant Vihar, Delhi", 15.0, "Very frequent stops and low speed limits."));
        list.add(new Scenario("Late Night Empty Roads", "Chandigarh", 65.0, "Uninterrupted flow and cooler night air."));
        list.add(new Scenario("Weekend Hill Getaway", "Nainital", 40.0, "Varying grades and scenic winding roads."));
        list.add(new Scenario("Industrial Area Haul", "Okhla, Delhi", 22.0, "Heavy vehicle presence and rough patches."));
        list.add(new Scenario("Cross-City Transit", "Salt Lake, Kolkata", 32.0, "Mixed urban conditions and multiple intersections."));
        list.add(new Scenario("Short Grocery Run", "Local Market", 10.0, "Short distance, engine potentially not at optimal temp."));
        list.add(new Scenario("Suburban Commute", "Gurgaon to Delhi", 55.0, "Commuter flow with high-speed stretches."));
        list.add(new Scenario("Extreme Congestion (Market)", "Chandni Chowk, Delhi", 5.0, "Maximum idling and minimal movement."));
        list.add(new Scenario("Ring Road Bypass", "Outer Ring Road, Delhi", 60.0, "Continuous movement around the city perimeter."));
        list.add(new Scenario("Coastal Highway", "Goa Coastline", 50.0, "Flat terrain with high salt/moisture content."));
        list.add(new Scenario("High Altitude (Leh)", "Leh, Ladakh", 35.0, "Thin air (low density) significantly reducing drag."));
        list.add(new Scenario("Interstate Trucking", "Mumbai to Ahmedabad", 60.0, "Sustained moderate speed over long distance."));
        return list;
    }
}
