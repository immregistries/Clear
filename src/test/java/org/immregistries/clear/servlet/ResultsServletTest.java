package org.immregistries.clear.servlet;

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.immregistries.clear.model.Jurisdiction;
import org.junit.Test;

public class ResultsServletTest {

    @Test
    public void shouldAssignConsecutiveRanksForDistinctUpdateCounts() throws Exception {
        Class<?> rowClass = Class.forName("org.immregistries.clear.servlet.ResultsServlet$RankedRow");
        Constructor<?> constructor = rowClass.getDeclaredConstructor();
        constructor.setAccessible(true);

        Object texas = constructor.newInstance();
        setField(rowClass, texas, "jurisdiction", jurisdiction("TX", "TX ImmTrac"));
        setField(rowClass, texas, "updates", Integer.valueOf(1503355));

        Object arizona = constructor.newInstance();
        setField(rowClass, arizona, "jurisdiction", jurisdiction("AZ", "AZ ASIIS"));
        setField(rowClass, arizona, "updates", Integer.valueOf(75));

        List<?> rows = Arrays.asList(texas, arizona);
        Comparator<Object> comparator = new Comparator<Object>() {
            @Override
            public int compare(Object left, Object right) {
                return Integer.compare(getIntField(rowClass, left, "updates"), getIntField(rowClass, right, "updates"));
            }
        };

        Method method = ResultsServlet.class.getDeclaredMethod("rankAndRenderTable", List.class, Comparator.class,
                String.class, PrintWriter.class, String.class);
        method.setAccessible(true);

        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        method.invoke(null, rows, comparator, "", out, "updates");
        out.flush();

        String html = buffer.toString();
        assertTrue(html.contains("<td>1</td><td>TX ImmTrac</td><td>1,503,355</td>"));
        assertTrue(html.contains("<td>2</td><td>AZ ASIIS</td><td>75</td>"));
    }

    private static Jurisdiction jurisdiction(String mapLink, String displayLabel) {
        Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setMapLink(mapLink);
        jurisdiction.setDisplayLabel(displayLabel);
        return jurisdiction;
    }

    private static void setField(Class<?> type, Object target, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getIntField(Class<?> type, Object target, String fieldName) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}