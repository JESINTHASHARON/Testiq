package com.api.test.api_verifier.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class DateUtils {

    public static boolean isToday(long ts) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTimeInMillis(ts);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isYesterday(long ts) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c2.add(Calendar.DAY_OF_YEAR, -1);
        c1.setTimeInMillis(ts);

        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    public static long toTimestamp(String dateStr) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr).getTime();
        } catch (Exception e) {
            return -1;
        }
    }
}
