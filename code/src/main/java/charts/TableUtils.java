package charts;

import org.jetbrains.annotations.Nullable;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.NumericColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.components.Figure;
import tech.tablesaw.plotly.components.Layout;
import tech.tablesaw.plotly.traces.ScatterTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;


public class TableUtils {

	private static final int[] CHART_DIMENSIONS = {1280, 720};

	public static Layout.LayoutBuilder applySettings(Layout.LayoutBuilder lb) {
		return lb.width(CHART_DIMENSIONS[0])
		         .height(CHART_DIMENSIONS[1]);
	}

	public static Figure timeSeriesPlot(Table t,
	                                    String xColName,
	                                    String[] yColNames,
	                                    @Nullable ScatterTrace.Mode[] modes,
	                                    @Nullable String title,
	                                    @Nullable String yTitle) {
		Layout.LayoutBuilder l = applySettings(Layout.builder(title != null ? title : "", xColName)
		                                             .yAxis(Axis.builder().title(yTitle != null ? yTitle : "").build()));

		ScatterTrace[] traces = new ScatterTrace[yColNames.length];
		for (int i = 0; i < traces.length; i++) {
			ScatterTrace.Mode mode = modes != null && modes.length > 0 ? modes.length > 1 ? modes[i] : modes[0] : ScatterTrace.Mode.LINE;
			traces[i] = ScatterTrace.builder(t.column(xColName), t.numberColumn(yColNames[i]))
			                        .showLegend(true)
			                        .name(yColNames[i])
			                        .mode(mode)
			                        .build();
		}
		return new Figure(l.build(), traces);
	}


	public static Figure timeSeriesPlot(Table t,
	                                    String xColName,
	                                    String[][] yColNames,
	                                    @Nullable ScatterTrace.Mode[][] modes,
	                                    @Nullable String title,
	                                    @Nullable String[] yTitle) {
		Layout.LayoutBuilder l = applySettings(Layout.builder(title != null ? title : "", xColName)
		                                             .yAxis(Axis.builder().title(yTitle != null ? yTitle[0] : "").build()));

		if (yColNames.length >= 2)
			l.yAxis2(Axis.builder().title(yTitle != null ? yTitle[1] : "Y2").side(Axis.Side.right).overlaying(ScatterTrace.YAxis.Y).build());
		List<ScatterTrace> traces = new ArrayList<>();
		for (int y = 0; y < yColNames.length; y++) {
			for (int i = 0; i < yColNames[y].length; i++) {
				ScatterTrace.Mode mode = modes != null && modes.length > 0 ? modes.length > 1 ? modes[y][i] : modes[0][0] : ScatterTrace.Mode.LINE;
				ScatterTrace.ScatterBuilder builder = ScatterTrace.builder(t.column(xColName), t.numberColumn(yColNames[y][i]))
				                                                  .showLegend(true)
				                                                  .name(yColNames[y][i])
				                                                  .mode(mode);
				if (y == 0) builder.yAxis(ScatterTrace.YAxis.Y);
				if (y == 1) builder.yAxis(ScatterTrace.YAxis.Y2);
				traces.add(builder.build());
			}
		}
		return new Figure(l.build(), traces.toArray(new ScatterTrace[0]));
	}

	public static IntColumn apply(IntColumn col, IntUnaryOperator f) {
		for(int i=0; i<col.size(); i++) {
			col.set(i, f.applyAsInt(col.getInt(i)));
		}
		return col;
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
}
