package charts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.tablesaw.plotly.components.Figure;

public class WebUtils {


	private static String getDefaultHtmlTableHeaderString() {
		return "<html>\n" +
		       "<head>\n" +
		       "<script src=\"https://cdn.plot.ly/plotly-latest.min.js\"></script>\n" +
		       "<meta name=\"google-site-verification\" content=\"C7OtmpihlobLAt4XdOw8seKHz_7g-Ls0JzGUtVnDoQU\" />\n" +
		       "<style>\n" +
		       "table, th, td {\n" +
		       "    border: 1px solid black;\n" +
		       "    border-collapse: collapse;\n" +
		       "}\n" +
		       "th, td {\n" +
		       "    padding: 5px;\n" +
		       "    font-family: helvetica;\n" +
		       "    font-size: 80%;" +
		       "}\n" +
		       "th {\n" +
		       "    text-align: left;\n" +
		       "}\n" +
		       "</style>\n" +
		       "</head>\n" +
		       "<body>\n";

	}

	public static String toHtml(@Nullable String bodyBeforeFigures, @NotNull Figure... fig) {
		String[] divName = new String[fig.length];
		for (int i = 0; i < fig.length; i++) {
			divName[i] = "div" + i;
		}
		StringBuilder builder = new StringBuilder(fig.length * 4 + 10);
		builder.append(getDefaultHtmlTableHeaderString());
		if (bodyBeforeFigures != null) {
			builder.append(bodyBeforeFigures);
		}
		for (String d : divName) {
			builder.append("    <div id='").append(d).append("' ></div>\n");
		}
		for (int i = 0; i < fig.length; i++) {
			builder.append(fig[i].asJavascript(divName[i]));
		}
		builder.append("</body>\n" +
		               "</html>");
		return builder.toString();
	}


}
