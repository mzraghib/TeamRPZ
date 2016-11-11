package com.kircherelectronics.accelerationexplorer.plot;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Arrays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.PositionMetrics;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetrics;
import com.androidplot.ui.XLayoutStyle;
import com.androidplot.ui.YLayoutStyle;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;

/*
 * Acceleration Explorer
 * Copyright (C) 2013-2015, Kaleb Kircher - Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Bar plot is responsible for plotting data on a bar graph. It is capable of
 * dynamically adding and removing plots as required by the user. However, the
 * total number of plots that could be plotted and names of the plots must be
 * known in advance.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class DynamicBarPlot
{
	private Context context;

	// RMS Noise levels bar chart series
	private SimpleXYSeries levelsSeries = null;

	private String seriesTitle;
	
	// RMS Noise levels bar chart
	private XYPlot plot = null;


	/**
	 * Initialize a new DynamicBarPlot.
	 * 
	 * @param noiseLevelsPlot
	 *            The plot.
	 * @param seriesTitle
	 *            The name of the plot.
	 */
	public DynamicBarPlot(XYPlot noiseLevelsPlot, String seriesTitle, Context context)
	{
		super();

		this.context = context;
		this.plot = noiseLevelsPlot;
		this.seriesTitle = seriesTitle;

		initPlot();
	}

	/**
	 * Add data to the plot.
	 * 
	 * @param seriesNumbers
	 *            The data to be plotted.
	 */
	public synchronized void onDataAvailable(Number[] seriesNumbers)
	{
		levelsSeries.setModel(Arrays.asList(seriesNumbers),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		plot.redraw();
	}

	/**
	 * Initialize the plot.
	 */
	private void initPlot()
	{
		levelsSeries = new SimpleXYSeries(seriesTitle);
		levelsSeries.useImplicitXVals();

		plot
				.addSeries(
						levelsSeries,
						new BarFormatter(Color.rgb(0, 153, 204), Color.rgb(0,
								153, 204)));

		// This needs to be changed with the number of plots, must be >= 1
		plot.setDomainStepValue(4);

		plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, .02);
		plot.setRangeValueFormat(new DecimalFormat("#.###"));

		// Fir the range. If we did not do this, the plot would
		// auto-range which can be visually confusing in the case of dynamic
		// plots.
		plot.setRangeBoundaries(0, 0.12, BoundaryMode.FIXED);

		// use our custom domain value formatter:
		plot.setDomainValueFormat(new DomainIndexFormat());

		// update our domain and range axis labels:
		plot.setDomainLabel("Output");
		plot.getDomainLabelWidget().pack();
		plot.setRangeLabel("RMS Amplitude");
		plot.getRangeLabelWidget().pack();
		plot.setGridPadding(15, 0, 15, 0);

		plot.getGraphWidget().setGridBackgroundPaint(null);
		plot.getGraphWidget().setBackgroundPaint(null);
		plot.getGraphWidget().setBorderPaint(null);
		plot.getGraphWidget().setDomainOriginLinePaint(null);

		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setColor(Color.rgb(119, 119, 119));
		paint.setStrokeWidth(2);

		plot.getGraphWidget().setRangeOriginLinePaint(paint);
		// levelsPlot.getGraphWidget().setRangeValueFormat(new
		// NoiseRangeFormat());

		plot.setBorderPaint(null);
		plot.setBackgroundPaint(null);

		// get a ref to the BarRenderer so we can make some changes to it:
		BarRenderer barRenderer = (BarRenderer) plot
				.getRenderer(BarRenderer.class);
		if (barRenderer != null)
		{
			// make our bars a little thicker than the default so they can be
			// seen better:
			barRenderer.setBarWidth(100);
		}
		
		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setColor(Color.rgb(119, 119, 119));
		paint.setStrokeWidth(10);

		this.plot.getGraphWidget().setDomainOriginLinePaint(paint);
		this.plot.getGraphWidget().setRangeOriginLinePaint(paint);

		this.plot.setBorderPaint(null);
		this.plot.setBackgroundPaint(null);
		
		Resources r = context.getResources();
		float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
				r.getDisplayMetrics());
		
		plot.getLegendWidget().getTextPaint().setTextSize(px);
		
		plot.getDomainLabelWidget().getLabelPaint().setTextSize(px);
		plot.getDomainLabelWidget().setSize(
				new SizeMetrics(0.05f, SizeLayoutType.RELATIVE, 0.08f,
						SizeLayoutType.RELATIVE));
		plot.getDomainLabelWidget().setPositionMetrics(
				new PositionMetrics(0.07f, XLayoutStyle.RELATIVE_TO_LEFT, 0,
						YLayoutStyle.RELATIVE_TO_BOTTOM,
						AnchorPosition.LEFT_BOTTOM));
		
		plot.getDomainLabelWidget().setClippingEnabled(false);

		plot.getRangeLabelWidget().getLabelPaint().setTextSize(px);
		plot.getRangeLabelWidget().setSize(
				new SizeMetrics(0.2f, SizeLayoutType.RELATIVE, 0.06f,
						SizeLayoutType.RELATIVE));
		plot.getRangeLabelWidget()
				.setPositionMetrics(
						new PositionMetrics(0.01f,
								XLayoutStyle.RELATIVE_TO_LEFT, 0.0f,
								YLayoutStyle.RELATIVE_TO_CENTER,
								AnchorPosition.CENTER));
		
		plot.getRangeLabelWidget().setClippingEnabled(false);
		
		px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				r.getDisplayMetrics());

		plot.getTitleWidget().getLabelPaint().setTextSize(px);

		plot.getTitleWidget().setPositionMetrics(
				new PositionMetrics(0.0f, XLayoutStyle.ABSOLUTE_FROM_CENTER,
						-0.06f, YLayoutStyle.RELATIVE_TO_TOP,
						AnchorPosition.TOP_MIDDLE));
		
		plot.getTitleWidget().setSize(
				new SizeMetrics(0.15f, SizeLayoutType.RELATIVE, 0.5f,
						SizeLayoutType.RELATIVE));
		
		px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7,
				r.getDisplayMetrics());

		plot.getGraphWidget().getDomainLabelPaint().setTextSize(px);
		plot.getGraphWidget().getRangeLabelPaint().setTextSize(px);

		plot.getGraphWidget().position(0.0f, XLayoutStyle.RELATIVE_TO_LEFT,
				0.02f, YLayoutStyle.RELATIVE_TO_TOP);

		plot.getGraphWidget().setSize(
				new SizeMetrics(0.9f, SizeLayoutType.RELATIVE, 0.99f,
						SizeLayoutType.RELATIVE));

		px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28,
				r.getDisplayMetrics());

		plot.getGraphWidget().setRangeLabelWidth(px);

		px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
				r.getDisplayMetrics());

		plot.getGraphWidget().setDomainLabelWidth(px);

		plot.getLegendWidget().getTextPaint().setTextSize(px);

		plot.getLegendWidget().position(-0.4f, XLayoutStyle.RELATIVE_TO_CENTER,
				-0.13f, YLayoutStyle.RELATIVE_TO_BOTTOM);

		plot.getLegendWidget().setSize(
				new SizeMetrics(0.15f, SizeLayoutType.RELATIVE, 0.5f,
						SizeLayoutType.RELATIVE));

		this.plot.redraw();
	}

	/**
	 * A simple formatter to convert bar indexes into sensor names.
	 */
	private class DomainIndexFormat extends Format
	{

		@Override
		public StringBuffer format(Object obj, StringBuffer toAppendTo,
				FieldPosition pos)
		{
			Number num = (Number) obj;

			// using num.intValue() will floor the value, so we add 0.5 to round
			// instead:
			int roundNum = (int) (num.floatValue() + 0.5f);
			switch (roundNum)
			{
			case 0:
				toAppendTo.append("Accel");
				break;
			case 1:
				toAppendTo.append("LPF");
				break;
			case 2:
				toAppendTo.append("Mean");
				break;
			case 3:
				toAppendTo.append("Median");
				break;
			default:
				toAppendTo.append("Unknown");
			}
			return toAppendTo;
		}

		@Override
		public Object parseObject(String string, ParsePosition position)
		{
			return null;
		}
	}
}
