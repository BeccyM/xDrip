package com.eveningoutpost.dexdrip.utils;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.GlucoseData;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.LibreBlock;
import com.eveningoutpost.dexdrip.Models.ReadingData;
import com.eveningoutpost.dexdrip.UtilityModels.Constants;
import com.eveningoutpost.dexdrip.UtilityModels.Pref;
import com.eveningoutpost.dexdrip.NFCReaderX;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.xdrip;
import com.eveningoutpost.dexdrip.Models.UserError.Log;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.LineChartView;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class LibreTrendGraph extends AppCompatActivity {

    private static final String TAG = "LibreTrendGraph";
    private static LibreTrendGraph mInstance;
    private LineChartView chart;
    private LineChartData data;
    private final boolean doMgdl = Pref.getString("units", "mgdl").equals("mgdl");
    
    public void closeNow(View view) {
        try {
            finish();
        } catch (Exception e) {
            Log.d(TAG, "Error finishing " + e.toString());
        }
    }

    private ArrayList<Float> getLatestBg(LibreBlock libreBlock) {
        ReadingData readingData = NFCReaderX.getTrend(libreBlock);
        if(readingData == null) {
            Log.e(TAG, "NFCReaderX.getTrend returned null");
            return null;
        }
        ArrayList<Float> ret = new ArrayList<Float>();
        
        if(readingData.trend.size() == 0 || readingData.trend.get(0).glucoseLevelRaw == 0) {
            Log.e(TAG, "libreBlock exists but no trend data exists, or first value is zero ");
            return null;
        }
        List<BgReading> latestReading = BgReading.latestForGraph (1, libreBlock.timestamp - 1000, libreBlock.timestamp + 1000);
        if(latestReading == null || latestReading.size() == 0) {
            Log.e(TAG, "libreBlock exists but no matching bg record exists");
            return null;
        }
        
        double factor = latestReading.get(0).calculated_value / readingData.trend.get(0).glucoseLevelRaw;
        if(factor == 0) {
            // We don't have the calculated value, but we do have the raw value. (No calibration exists)
            // I want to show raw data.
            Log.w(TAG, "Bg data was not calculated, working on raw data");
            factor = latestReading.get(0).raw_data / readingData.trend.get(0).glucoseLevelRaw;
        }
        
        for (GlucoseData data : readingData.trend) {
            ret.add(new Float(factor * data.glucoseLevelRaw));
        }
        
        return ret;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_libre_trend);
        JoH.fixActionBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCharts();
    }
    
    public void setupCharts() {
        
       final TextView trendView = (TextView) findViewById(R.id.textLibreHeader);
         
        chart = (LineChartView) findViewById(R.id.libre_chart);
        List<Line> lines = new ArrayList<Line>();

        List<PointValue> lineValues = new ArrayList<PointValue>();
        final float conversion_factor_mmol = (float) (doMgdl ? 1 : Constants.MGDL_TO_MMOLL);

        LibreBlock libreBlock= LibreBlock.getLatestForTrend();
        if(libreBlock == null) {
            trendView.setText("No libre data to display");
            return;
        }
        String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date((long) libreBlock.timestamp));

        ArrayList<Float> bg_data = getLatestBg(libreBlock);
        if(bg_data == null) {
            trendView.setText("Error displaying data for " + time);
            return;
        }
        
        trendView.setText("Scan from " + time);
        float min = 1000;
        float max = 0;
        int i = 0;
        for(float bg : bg_data ) {
            if(min > bg) {
                min = bg;
            }
            if(max < bg) {
                max = bg;
            }
            
            lineValues.add(new PointValue(-i, bg * conversion_factor_mmol));
            i++;
        }

        Line trendLine = new Line(lineValues);
        trendLine.setColor(ChartUtils.COLOR_RED);
        trendLine.setHasLines(false);
        trendLine.setHasPoints(true);
        lines.add(trendLine);
        
        final int MIN_GRAPH = 20;
        if(max - min < MIN_GRAPH)
        {
            // On relative flat trend the graph can look very noise althouth with the right resolution it is not that way.
            // I will add two dummy invisible points that will cause the graph to look with bigger Y range.
            float average = (max + min) /2;
            List<PointValue> dummyPointValues = new ArrayList<PointValue>();
            Line dummyPointLine = new Line(dummyPointValues);
            dummyPointValues.add(new PointValue(0, (average - MIN_GRAPH / 2) * conversion_factor_mmol));
            dummyPointValues.add(new PointValue(0, (average + MIN_GRAPH / 2) * conversion_factor_mmol));
            dummyPointLine.setColor(ChartUtils.COLOR_RED);
            dummyPointLine.setHasLines(false);
            dummyPointLine.setHasPoints(false);
            lines.add(dummyPointLine);
        }

        Axis axisX = new Axis();
        Axis axisY = new Axis().setHasLines(true);
        axisX.setTextSize(16);
        axisY.setTextSize(16);
        axisX.setName("Time from last scan");
        axisY.setName("Glucose " + (doMgdl ? "mg/dl" : "mmol/l"));

        data = new LineChartData(lines);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        chart.setLineChartData(data);

    }

}

