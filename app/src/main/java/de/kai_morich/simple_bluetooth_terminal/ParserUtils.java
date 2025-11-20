package de.kai_morich.simple_bluetooth_terminal;

import java.util.HashMap;
import java.util.Map;

public class ParserUtils {

    private ParserUtils() {} // prevent instantiation

    public static Map<String, Object> parse220101(String data) {
        Map<String, Object> parsedData = new HashMap<>();

        try {
            String firstBlock = "7EC21";
            String secondBlock = "7EC22";
            String thirdBlock = "7EC23";
            String fourthBlock = "7EC24";
            String fifthBlock = "7EC25";
            String sixthBlock = "7EC26";
            String seventhBlock = "7EC27";
            String eighthBlock = "7EC28";

            if (data.contains(firstBlock) && data.contains(secondBlock) && data.contains(thirdBlock)
                    && data.contains(fourthBlock) && data.contains(fifthBlock) && data.contains(sixthBlock)
                    && data.contains(seventhBlock) && data.contains(eighthBlock)) {

                // Extract blocks
                String extractedFirstData = data.substring(data.indexOf(firstBlock), data.indexOf(secondBlock)).replace(firstBlock, "");
                String extractedSecondData = data.substring(data.indexOf(secondBlock), data.indexOf(thirdBlock)).replace(secondBlock, "");
                String extractedThirdData = data.substring(data.indexOf(thirdBlock), data.indexOf(fourthBlock)).replace(thirdBlock, "");
                String extractedFourthData = data.substring(data.indexOf(fourthBlock), data.indexOf(fifthBlock)).replace(fourthBlock, "");
                String extractedFifthData = data.substring(data.indexOf(fifthBlock), data.indexOf(sixthBlock)).replace(fifthBlock, "");
                String extractedSixthData = data.substring(data.indexOf(sixthBlock), data.indexOf(seventhBlock)).replace(sixthBlock, "");
                String extractedSeventhData = data.substring(data.indexOf(seventhBlock), data.indexOf(eighthBlock)).replace(seventhBlock, "");
                String extractedEighthData = data.substring(data.indexOf(eighthBlock), data.indexOf(eighthBlock) + 18).replace(eighthBlock, "");

                // Charging bits (7th block)
                int chargingInt = Integer.parseInt(extractedSeventhData.substring(10, 12), 16);
                String chargingBits = String.format("%8s", Integer.toBinaryString(chargingInt)).replace(' ', '0');

                if (!extractedFirstData.isEmpty() && !extractedSecondData.isEmpty() && !extractedFourthData.isEmpty()) {

                    // Signed 8-bit temperature conversions
                    int batteryMinTemperature = Integer.parseInt(extractedSecondData.substring(10, 12), 16);
                    if (batteryMinTemperature >= 128) batteryMinTemperature -= 256;

                    int batteryMaxTemperature = Integer.parseInt(extractedSecondData.substring(8, 10), 16);
                    if (batteryMaxTemperature >= 128) batteryMaxTemperature -= 256;

                    int batteryInletTemperature = Integer.parseInt(extractedThirdData.substring(10, 12), 16);
                    if (batteryInletTemperature >= 128) batteryInletTemperature -= 256;

                    // Signed 16-bit battery current
                    int batteryCurrent = Integer.parseInt(extractedSecondData.substring(0, 2) + extractedSecondData.substring(2, 4), 16);
                    if (batteryCurrent >= 32768) batteryCurrent -= 65536;

                    // Cumulative energy charged
                    double cumulativeEnergyCharged = (
                            (Integer.parseInt(extractedSixthData.substring(0, 2), 16) << 24) +
                                    (Integer.parseInt(extractedSixthData.substring(2, 4), 16) << 16) +
                                    (Integer.parseInt(extractedSixthData.substring(4, 6), 16) << 8) +
                                    Integer.parseInt(extractedSixthData.substring(6, 8), 16)
                    ) / 10.0;

                    // Cumulative energy discharged
                    double cumulativeEnergyDischarged = (
                            (Integer.parseInt(extractedSixthData.substring(8, 10), 16) << 24) +
                                    (Integer.parseInt(extractedSixthData.substring(10, 12), 16) << 16) +
                                    (Integer.parseInt(extractedSixthData.substring(12, 14), 16) << 8) +
                                    Integer.parseInt(extractedSeventhData.substring(0, 2), 16)
                    ) / 10.0;

                    // Charging flags
                    int charging = (chargingBits.charAt(4) == '1' && chargingBits.charAt(5) == '0') ? 1 : 0;
                    int normalChargePort = (chargingBits.charAt(1) == '1' && extractedFirstData.substring(12, 14).equals("03")) ? 1 : 0;
                    int rapidChargePort = (chargingBits.charAt(1) == '1' && !extractedFirstData.substring(12, 14).equals("03")) ? 1 : 0;

                    // Fill parsed data
                    parsedData.put("SOC_BMS", Integer.parseInt(extractedFirstData.substring(2, 4), 16) / 2.0);
                    parsedData.put("DC_BATTERY_VOLTAGE", ((Integer.parseInt(extractedSecondData.substring(4, 6), 16) << 8)
                            + Integer.parseInt(extractedSecondData.substring(6, 8), 16)) / 10.0);
                    parsedData.put("CHARGING", charging);
                    parsedData.put("NORMAL_CHARGE_PORT", normalChargePort);
                    parsedData.put("RAPID_CHARGE_PORT", rapidChargePort);
                    parsedData.put("BATTERY_MIN_TEMPERATURE", batteryMinTemperature);
                    parsedData.put("BATTERY_MAX_TEMPERATURE", batteryMaxTemperature);
                    parsedData.put("BATTERY_INLET_TEMPERATURE", batteryInletTemperature);
                    parsedData.put("DC_BATTERY_CURRENT", batteryCurrent * 0.1);
                    parsedData.put("CUMULATIVE_ENERGY_CHARGED", cumulativeEnergyCharged);
                    parsedData.put("CUMULATIVE_ENERGY_DISCHARGED", cumulativeEnergyDischarged);
                    parsedData.put("AUX_BATTERY_VOLTAGE", Integer.parseInt(extractedFourthData.substring(10, 12), 16) / 10.0);

                    // Battery power in kW
                    parsedData.put("DC_BATTERY_POWER", (batteryCurrent * 0.1) * ((Integer.parseInt(extractedSecondData.substring(4, 6), 16) << 8)
                            + Integer.parseInt(extractedSecondData.substring(6, 8), 16)) / 1000.0);
                }
            }

        } catch (Exception e) {
            parsedData.put("error", "Parse 220101 error: " + e.getMessage());
            parsedData.put("response", data);
        }

        return parsedData;
    }

    public static Map<String, Object> parse220105(String data) {
        Map<String, Object> parsed = new HashMap<>();
        try {
            String fourthBlock = "7EC24";
            String fifthBlock = "7EC25";

            if (data.contains(fourthBlock) && data.contains(fifthBlock) && data.contains("7EC26")) {
                String extractedFourthData = extract(data, fourthBlock, fifthBlock);
                String extractedFifthData = extract(data, fifthBlock, "7EC26");

                if (!extractedFourthData.isEmpty() && !extractedFifthData.isEmpty()) {
                    parsed.put("SOC_DISPLAY", Integer.parseInt(extractedFifthData.substring(0, 2), 16) / 2.0);
                    parsed.put("SOH", ((Integer.parseInt(extractedFourthData.substring(2, 4), 16) << 8) +
                            Integer.parseInt(extractedFourthData.substring(4, 6), 16)) / 10.0);
                }
            }
        } catch (Exception e) {
            parsed.put("error", "Parse 220105 error: " + e.getMessage());
            parsed.put("response", data);
        }
        return parsed;
    }

    private static String extract(String data, String start, String end) {
        return data.substring(data.indexOf(start) + start.length(), data.indexOf(end));
    }
}