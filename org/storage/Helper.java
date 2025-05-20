package org.storage;

public class Helper {

    public static boolean isNumber(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void invalidAction() {
        System.out.println("Invalid command or insufficient arguments.");
    }
}
