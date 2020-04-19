package charts;


import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.*;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.traces.ScatterTrace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Covid19Charts {

    public static Table by_region(Table in) {
        StringColumn region = StringColumn.create("REGION");
        StringColumn province_col = in.stringColumn("Province/State");
        StringColumn country_col = in.stringColumn("Country/Region");
        for (int r = 0; r < in.rowCount(); r++) {
            if (in.stringColumn(0).get(r).length() > 0) {
                region.append(country_col.get(r) + " - " + province_col.get(r));
            } else region.append(country_col.get(r));
        }
        return Table.create(region).addColumns(ArrayUtils.subarray(in.columnArray(), 4, in.columnCount()));
    }

    public static Table toTimeSeries(Table raw, String valueName) {
        Table t = by_region(raw);
        String[] dates_str = ArrayUtils.subarray(t.columnNames().toArray(new String[0]), 1, t.columnCount());
        LocalDate[] dates = Arrays.stream(dates_str).map(s -> LocalDate.parse(s, DateTimeFormatter.ofPattern("M/d/yy"))).toArray(LocalDate[]::new);

        StringColumn regionCol = StringColumn.create("REGION");
        DateColumn dateCol = DateColumn.create("DT");
        DoubleColumn valueCol = DoubleColumn.create(valueName);


        for (int r = 0; r < t.rowCount(); r++) {
            for (int c = 1; c < t.columnCount(); c++) {
                regionCol.append(t.stringColumn(0).get(r));
                dateCol.append(dates[c - 1]);
                valueCol.append(t.numberColumn(c).get(r));
            }
        }
        return Table.create(regionCol, dateCol, valueCol);
    }

    public static IntColumn d1(NumericColumn<?> c, String name) {
        IntColumn increase = IntColumn.create(name);
        int previous = 0;
        for (int r = 0; r < c.size(); r++) {
            int x = (int) c.getDouble(r);
            increase.append(x - previous);
            previous = x;
        }
        return increase;
    }

    public static Table fillMissingAtPrevious(Table t) {
        Set<String> regions = new HashSet<>(t.stringColumn("REGION").asList());
        LocalDate last_dt = t.dateColumn("DT").max();
        Table f = null;
        for (String r : regions) {
            Table region_data = t.where(t.stringColumn("REGION").isEqualTo(r)).sortAscendingOn("DT");
            LocalDate[] dates = region_data.dateColumn("DT").asObjectArray();
            LocalDate dt = region_data.dateColumn("DT").min();
            int[] prev_values = new int[t.columnCount() - 2];
            Arrays.fill(prev_values, 0);
            while (!dt.isAfter(last_dt)) {
                int idx = Arrays.binarySearch(dates, dt);
                if (idx < 0) {
                    region_data.dateColumn("DT").append(dt);
                    region_data.stringColumn("REGION").append(r);
                    for (int c = 0; c < prev_values.length; c++) {
                        region_data.intColumn(c + 2).append(prev_values[c]);
                    }
                } else {
                    for (int c = 0; c < prev_values.length; c++) {
                        IntColumn col = region_data.intColumn(c + 2);
                        if (col.isMissing(idx) || col.getInt(idx) < prev_values[c]) {
                            col.set(idx, prev_values[c]);
                        } else {
                            prev_values[c] = col.getInt(idx);
                        }
                    }
                }
                dt = dt.plusDays(1);
            }
            if (f == null) f = region_data;
            else f = f.append(region_data);
        }
        return Objects.requireNonNull(f).sortAscendingOn("DT");
    }


    public static class SwissCharts {

        public static String download_CH_html_charts() throws IOException {
            Table data = Table.read().url("https://raw.githubusercontent.com/openZH/covid_19/master/COVID19_Fallzahlen_CH_total_v2.csv");


            final Table t = fillMissingAtPrevious(
                    Table.create(data.dateColumn("date").setName("DT"),
                            data.stringColumn("abbreviation_canton_and_fl").setName("REGION"),
                            data.intColumn("ncumul_conf").setName("CONFIRMED"),
                            data.intColumn("ncumul_deceased").setName("DEAD"),
                            data.intColumn("current_hosp").setName("HOSPITALIZED"),
                            data.intColumn("current_icu").setName("ICU"),
                            data.intColumn("ncumul_released").setName("RELEASED")));
            Set<String> regions = new HashSet<>(t.stringColumn("REGION").asList());

            LocalDate last_date_in_summary = regions.stream().map(r -> t.where(t.stringColumn("REGION").isEqualTo(r)).dateColumn("DT").max()).min(Comparator.naturalOrder()).orElseThrow(RuntimeException::new);

            Table summary = null;
            for (int c = 2; c < t.columnCount(); c++) {
                Table s = t.summarize(t.columnNames().get(c), AggregateFunctions.sum).by("DT");
                if (summary == null) summary = s;
                else summary = summary.joinOn("DT").inner(s);
            }
            summary = Objects.requireNonNull(summary).where(summary.dateColumn(0).isOnOrBefore(last_date_in_summary));
            summary = summary.addColumns(d1(summary.numberColumn(1), "NEW_CONFIRMED"));
            summary = summary.addColumns(d1(summary.numberColumn(2), "NEW_DEAD"));
            summary = summary.where(summary.intColumn("NEW_CONFIRMED").isGreaterThan(-1));

            List<Figure> figures = new ArrayList<>();
            String[] dataCols = summary.columnNames().subList(1, 6).toArray(new String[0]);
            ScatterTrace.Mode[] modes = Arrays.stream(dataCols).map(c -> ScatterTrace.Mode.LINE_AND_MARKERS).toArray(ScatterTrace.Mode[]::new);
            figures.add(TableUtils.timeSeriesPlot(summary,
                    "DT",
                    new String[][]{dataCols,
                            {"NEW_CONFIRMED", "NEW_DEAD"}},
                    new ScatterTrace.Mode[][]{modes,
                            {ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS}},
                    "CH CASES: " + String.format("%,d", (int) summary.numberColumn(1).max()) + " CONFIRMED",
                    new String[]{"TOTAL_CASES", "NEW_CASES"}));

            Table maxConfirmedByRegion = t.summarize("CONFIRMED", AggregateFunctions.max)
                    .by("REGION");
            maxConfirmedByRegion = maxConfirmedByRegion.sortDescendingOn(maxConfirmedByRegion.columnNames().get(1));

            StringColumn topRegions = maxConfirmedByRegion.stringColumn(0);

            int counter = 1;
            dataCols = t.columnNames().subList(2, t.columnCount()).toArray(new String[0]);
            for (String region : topRegions.asList()) {
                Table tt = t.where(t.stringColumn("REGION").isEqualTo(region));
                tt = tt.addColumns(d1(tt.numberColumn("CONFIRMED"), "NEW_CONFIRMED"));
                tt = tt.addColumns(d1(tt.numberColumn("DEAD"), "NEW_DEAD"));
                int num_confirmed = (int) tt.numberColumn("CONFIRMED").max();
                figures.add(TableUtils.timeSeriesPlot(tt,
                        "DT",
                        new String[][]{dataCols,
                                {"NEW_CONFIRMED", "NEW_DEAD"}},
                        new ScatterTrace.Mode[][]{modes,
                                {ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS}},
                        counter + ". " + region + ": " + String.format("%,d", num_confirmed) + " CONFIRMED",
                        new String[]{"TOTAL_CASES", "NEW_CASES"}));
                counter++;
            }

            return WebUtils.toHtml("<p>COVID-19 cases in Switzerland.</p>" +
                            "<p>Source: openZH <a target=\"_blank\" href=\"https://github.com/openZH/covid_19/blob/master/COVID19_Fallzahlen_CH_total_v2.csv\">here</a>.</p>" +
                            "<p>Last reported date: " + t.dateColumn("DT").max() + ". Page updated " + Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("UTC")) + ".</p>" +
                            "<p>" +
                            "<a target=\"_blank\" href=\"https://smpawlowski.github.io/covid19/\">[Global]</a> " +
                            modelLinks() + "</p>" +
                            "<div class=\"share-page\">\n" +
                            "    Share &rarr; " +
                            "    <a target=\"_blank\" href=\"https://twitter.com/intent/tweet?url=https://smpawlowski.github.io/covid19/\">[Twitter]</a> " +
                            "    <a target=\"_blank\" href=\"https://www.facebook.com/sharer/sharer.php?u=https%3A%2F%2Fsmpawlowski.github.io%2Fcovid19%2F&amp;src=sdkpreparse\" class=\"fb-xfbml-parse-ignore\">[Facebook]</a> " +
                            "    <a target=\"_blank\" href=\"https://www.linkedin.com/shareArticle?mini=true&url=https://smpawlowski.github.io/covid19/\">[LinkedIn]</a> " +
                            "</div>",
                    figures.toArray(new Figure[0]));
        }

        public static void main(String[] args) throws IOException {
            String html = download_CH_html_charts();

            final Path path = Paths.get(args.length == 0 ? "C:/temp/switzerland.html" : args[0]);
            Files.write(path, html.getBytes());
        }
    }

    public static String columnNameContaning(Table t, String x) {
        return t.columnNames().stream().filter(n->n.contains(x)).findFirst().orElseThrow(RuntimeException::new);
    }

    @NotNull
    public static String download_html_charts() throws IOException {
        Table confirmed = toTimeSeries(Table.read().url("https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv"), "CONFIRMED");
        Table dead = toTimeSeries(Table.read().url("https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_deaths_global.csv"), "DEAD");
        Table recovered = toTimeSeries(Table.read().url("https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_recovered_global.csv"), "RECOVERED");

        Table maxConfirmedByRegion = confirmed.summarize("CONFIRMED", AggregateFunctions.max)
                .by("REGION");
        maxConfirmedByRegion = maxConfirmedByRegion.sortDescendingOn(maxConfirmedByRegion.columnNames().get(1));

        StringColumn topRegions = maxConfirmedByRegion.stringColumn(0).first(75);

        System.out.println(maxConfirmedByRegion.print());

        Table t = confirmed.joinOn("REGION", "DT")
                .fullOuter(true, dead, recovered)
                .sortAscendingOn("DT");
        t = t.where(t.doubleColumn("CONFIRMED").isGreaterThan(0));
        t = t.addColumns(t.nCol("CONFIRMED").subtract(t.nCol("RECOVERED")).subtract(t.nCol("DEAD")).setName("ACTIVE"));

        List<Figure> figures = new ArrayList<>();
        Table totalCases = t.summarize(t.column(2), t.column(3), t.column(4), t.column(5), AggregateFunctions.sum)
                .by("DT");
        String confirmed_name = columnNameContaning(totalCases,"CONFIRMED");
        totalCases = totalCases.addColumns(d1(totalCases.numberColumn(confirmed_name), "NEW CONFIRMED"));
        String dead_name = columnNameContaning(totalCases,"DEAD");
        totalCases = totalCases.addColumns(d1(totalCases.numberColumn(dead_name), "NEW DEAD"));
        System.out.println(totalCases.print());

        String[] cumul_col_names = {"CONFIRMED", "ACTIVE", "RECOVERED", "DEAD"};
        Table finalTotalCases = totalCases;
        figures.add(TableUtils.timeSeriesPlot(
                totalCases,
                "DT",
                new String[][]{Arrays.stream(cumul_col_names).map(n -> columnNameContaning(finalTotalCases,n)).toArray(String[]::new),
                        {"NEW CONFIRMED", "NEW DEAD"}},
                new ScatterTrace.Mode[][]{{ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS},
                        {ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS}},
                "GLOBAL CASES: " + String.format("%,d", (int) totalCases.numberColumn(confirmed_name).max()) + " CONFIRMED",
                new String[]{"TOTAL CASES", "NEW CASES"}));
        int counter = 1;
        for (String region : topRegions.asList()) {
            Table tt = t.where(t.stringColumn("REGION").isEqualTo(region));
            tt = tt.addColumns(d1(tt.numberColumn("CONFIRMED"), "NEW CONFIRMED"));
            tt = tt.addColumns(d1(tt.numberColumn("DEAD"), "NEW DEAD"));
            int num_confirmed = (int) tt.numberColumn("CONFIRMED").max();
            figures.add(TableUtils.timeSeriesPlot(tt,
                    "DT",
                    new String[][]{cumul_col_names,
                            {"NEW CONFIRMED", "NEW DEAD"}},
                    new ScatterTrace.Mode[][]{{ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS},
                            {ScatterTrace.Mode.LINE_AND_MARKERS, ScatterTrace.Mode.LINE_AND_MARKERS}},
                    counter + ". " + region + ": " + String.format("%,d", num_confirmed) + " CONFIRMED",
                    new String[]{"TOTAL CASES", "NEW CASES"}));
            counter++;
        }


        return WebUtils.toHtml("<p>Global COVID-19 cases followed by 75 regions with highest number of confirmed cases.</p>" +
                        "<p>" +
                        "Source:  Johns Hopkins Coronavirus Resource Center published <a target=\"_blank\" href=\"https://github.com/CSSEGISandData/2019-nCoV\">here</a> (updated daily)." + "</p>" +
                        "<p>" +
                        "Last reported date: " + t.dateColumn("DT").max() + ". Page updated " + Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("UTC")) + "." + "</p>" +
                        "<p>" +
                        modelLinks() + "</p>" +
                        "<p>" +
                        "<a target=\"_blank\" href=\"https://smpawlowski.github.io/covid19/ch\">[Switzerland]</a> " +
                        "<a target=\"_blank\" href=\"https://coronavirus.jhu.edu/map.html\">[Johns Hopkins map]</a> " +
                        "<a target=\"_blank\" href=\"https://ourworldindata.org/grapher/covid-confirmed-cases-since-100th-case\">[Our World In Data trajectories]</a> " +
                        "<a target=\"_blank\" href=\"https://ourworldindata.org/coronavirus#confirmed-covid-19-cases-by-country\">[Our World In Data cases by country]</a> " + "</p>" +
                        "<div class=\"share-page\">\n" +
                        "    Share &rarr; " +
                        "    <a target=\"_blank\" href=\"https://twitter.com/intent/tweet?url=https://smpawlowski.github.io/covid19/\">[Twitter]</a> " +
                        "    <a target=\"_blank\" href=\"https://www.facebook.com/sharer/sharer.php?u=https%3A%2F%2Fsmpawlowski.github.io%2Fcovid19%2F&amp;src=sdkpreparse\" class=\"fb-xfbml-parse-ignore\">[Facebook]</a> " +
                        "    <a target=\"_blank\" href=\"https://www.linkedin.com/shareArticle?mini=true&url=https://smpawlowski.github.io/covid19/\">[LinkedIn]</a> " +
                        "</div>",
                figures.toArray(new Figure[0]));
    }

    @NotNull
    public static String modelLinks() {
        return "<a target=\"_blank\" href=\"https://covid19-scenarios.org/\">[SEIR model scenarios]</a> " +
                "<a target=\"_blank\" href=\"https://imperialcollegelondon.github.io/covid19estimates/#/\">[Imperial College Estimates for Europe]</a> " +
                "<a target=\"_blank\" href=\"https://github.com/smpawlowski/covid19\">[Code]</a>";
    }

    public static void main(String[] args) throws IOException {
        String html = download_html_charts();

        final Path path = Paths.get(args.length == 0 ? "C:/temp/index.html" : args[0]);
        Files.write(path, html.getBytes());
    }

}
