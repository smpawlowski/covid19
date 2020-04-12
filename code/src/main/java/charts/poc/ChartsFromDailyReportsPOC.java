package charts.poc;

import charts.TableUtils;
import charts.WebUtils;
import org.jetbrains.annotations.NotNull;
import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.traces.ScatterTrace;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChartsFromDailyReportsPOC {
	public static Table download_daily_reports() {
		LocalDate dt  = LocalDate.of(2020, 1, 22);
		LocalDate end = LocalDate.now().plusDays(1);
		Table     t   = null;
		while (!dt.isAfter(end)) {
			try {
				String           day           = dt.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
				Table            download      = Table.read().url("https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_daily_reports/" + day + ".csv");
				StringColumn     region        = StringColumn.create("REGION");
				DateColumn       date_col      = DateColumn.create("DT");
				NumericColumn<?> confirmed     = (NumericColumn<?>) download.numberColumn("Confirmed").setName("CONFIRMED");
				NumericColumn<?> dead          = (NumericColumn<?>) download.numberColumn("Deaths").setName("DEAD");
				NumericColumn<?> recovered     = (NumericColumn<?>) download.numberColumn("Recovered").setName("RECOVERED");
				String           country_name  = download.columnNames().stream().filter(n -> n.toUpperCase().contains("COUNTRY")).findFirst().orElseThrow(Exception::new);
				String           province_name = download.columnNames().stream().filter(n -> n.toUpperCase().contains("PROVINCE")).findFirst().orElseThrow(Exception::new);
				StringColumn     country       = download.stringColumn(country_name);
				StringColumn     province      = download.stringColumn(province_name);
				for (int i = 0; i < download.rowCount(); i++) {
					region.append(country.get(i) + (!(province.get(i).equals(country.get(i))) ? ": " + province.get(i) : ""));
					date_col.append(dt);
				}
				Table day_table = Table.create(date_col, region, confirmed, dead, recovered);
				if (t == null) t = day_table;
				else t.append(day_table);

			} catch (Exception e) {
				System.out.println("Download failed for " + dt);
				e.printStackTrace();
			}

			dt = dt.plusDays(1);
		}
		return t;
	}

	@NotNull
	public static String html_charts_from_daily_reports() {

		Table t = download_daily_reports();
		Table maxConfirmedByRegion = t.summarize("CONFIRMED", AggregateFunctions.max)
		                              .by("REGION");
		maxConfirmedByRegion = maxConfirmedByRegion.sortDescendingOn(maxConfirmedByRegion.columnNames().get(1));

		StringColumn topRegions = maxConfirmedByRegion.stringColumn(0).first(75);

		t = t.where(t.numberColumn("CONFIRMED").isGreaterThan(0));
		NumericColumn<?> active = t.numberColumn("CONFIRMED")
		                           .subtract(t.numberColumn("DEAD"))
		                           .subtract(t.numberColumn("RECOVERED"))
		                           .setName("ACTIVE");
		t = t.addColumns(active);

		List<Figure> figures = new ArrayList<>();
		final Table totalCases = t.summarize(t.column(2), t.column(3), t.column(4), t.column(5), AggregateFunctions.sum)
		                          .by("DT");
		System.out.println(totalCases.print());
		figures.add(TableUtils.timeSeriesPlot(totalCases,
				"DT",
				new String[]{totalCases.columnNames().get(1),
				             totalCases.columnNames().get(2),
				             totalCases.columnNames().get(3),
				             totalCases.columnNames().get(4)},
				new ScatterTrace.Mode[]{ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE},
				"GLOBAL CASES: " + String.format("%,d", (int) totalCases.numberColumn(totalCases.columnNames().stream().filter(n -> n.contains("CONFIRMED")).findFirst().orElseThrow(RuntimeException::new)).max()) + " CONFIRMED",
				"NUM_CASES"));
		int counter = 1;
		for (String region : topRegions.asList()) {
			Table tt            = t.where(t.stringColumn("REGION").isEqualTo(region));
			int   num_confirmed = (int) tt.numberColumn("CONFIRMED").max();
			figures.add(TableUtils.timeSeriesPlot(tt,
					"DT",
					new String[]{tt.columnNames().get(2),
					             tt.columnNames().get(3),
					             tt.columnNames().get(4),
					             tt.columnNames().get(5)},
					new ScatterTrace.Mode[]{ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE, ScatterTrace.Mode.LINE},
					counter + ". " + region + ": " + String.format("%,d", num_confirmed) + " CONFIRMED",
					"NUM_CASES"));
			counter++;
		}
		return WebUtils.toHtml("<p>Global COVID-19 cases followed by 75 regions with highest number of confirmed cases.</p>" +
		                       "<p>Source:  Johns Hopkins Coronavirus Resource Center published <a href=\"https://github.com/CSSEGISandData/2019-nCoV\">here</a> (updated daily).</p>" +
		                       "<p>Last reported date: " + t.dateColumn("DT").max() + ". Page updated " + Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("UTC")) + ".</p>" +
		                       "<p><a target=\"_blank\" href=\"https://coronavirus.jhu.edu/map.html\">[Johns Hopkins map]</a> " +
		                       "<a target=\"_blank\" href=\"https://ourworldindata.org/grapher/covid-confirmed-cases-since-100th-case\">[Our World In Data trajectories]</a> " +
		                       "<a target=\"_blank\" href=\"https://ourworldindata.org/coronavirus#confirmed-covid-19-cases-by-country\">[Our World In Data cases by country]</a> " +
		                       "<a target=\"_blank\" href=\"https://gabgoh.github.io/COVID/index.html\">[SEIR online calculator]</a></p>" +
		                       "<div class=\"share-page\">\n" +
		                       "    Share &rarr; " +
		                       "    <a target=\"_blank\" href=\"https://twitter.com/intent/tweet?url=https://smpawlowski.github.io/covid19/\">[Twitter]</a> " +
		                       "    <a target=\"_blank\" href=\"https://www.facebook.com/sharer/sharer.php?u=https%3A%2F%2Fsmpawlowski.github.io%2Fcovid19%2F&amp;src=sdkpreparse\" class=\"fb-xfbml-parse-ignore\">[Facebook]</a> " +
		                       "    <a target=\"_blank\" href=\"https://www.linkedin.com/shareArticle?mini=true&url=https://smpawlowski.github.io/covid19/\">[LinkedIn]</a> " +
		                       "</div>",
				figures.toArray(new Figure[0]));
	}

	private static class TestDailyReportDownload {
		public static void main(String[] args) throws IOException {
			Table t = download_daily_reports();
			t.write().csv("C:\\temp\\covid_time_series.csv");
		}
	}
}
