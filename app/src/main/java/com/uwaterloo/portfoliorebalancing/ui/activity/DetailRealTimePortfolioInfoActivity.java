package com.uwaterloo.portfoliorebalancing.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.uwaterloo.portfoliorebalancing.R;
import com.uwaterloo.portfoliorebalancing.ui.PriceMarkerView;
import com.uwaterloo.portfoliorebalancing.ui.activity.AsyncTaskActivity;
import com.uwaterloo.portfoliorebalancing.util.GraphData;
import com.uwaterloo.portfoliorebalancing.model.Simulation;
import com.uwaterloo.portfoliorebalancing.model.SimulationStrategy;
import com.uwaterloo.portfoliorebalancing.model.Tick;
import com.uwaterloo.portfoliorebalancing.util.AppUtils;
import com.uwaterloo.portfoliorebalancing.util.PortfolioRebalanceUtil;
import com.uwaterloo.portfoliorebalancing.util.SimulationConstants;
import com.uwaterloo.portfoliorebalancing.util.StockHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by joyce on 2016-07-21.
 */
public class DetailRealTimePortfolioInfoActivity extends AsyncTaskActivity {
    private final String API_KEY = "zrLZruHPruMca17gnA-z";
    protected LineChart mPortfolioChart;
    protected Simulation mSimulation;
    protected Context mContext;
    protected boolean newSimulation;
    protected TextView mSimulationType;
    protected TextView mStartingBal;

    private CalculateRealTimeSimulationAsyncTask loadData = null;

    @Override
    protected void stopAsyncTask() {
        if (loadData != null) {
            loadData.cancel(true);
            loadData = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portfolio_detail);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        long simulationId = -1;
        mContext = this;

        mSimulationType = (TextView) findViewById(R.id.simulation_type);
        mStartingBal = (TextView) findViewById(R.id.simulation_start_bal);

        Intent intent = getIntent();

        if (intent != null) {
            newSimulation = intent.getBooleanExtra("newSimulation", true);
            simulationId = intent.getLongExtra("simulationId", -1);
        }
        if (simulationId != -1) {
            mSimulation = Simulation.findById(Simulation.class, simulationId);
        }

        TextView nameView = (TextView) findViewById(R.id.simulation_name);
        nameView.setText(mSimulation.getName());

        mSimulationType.setText(mSimulation.getType() == SimulationConstants.HISTORICAL_DATA ? "Historical data" : "Real time data");
        mStartingBal.setText(String.valueOf(mSimulation.getBank()));

        LinearLayout ll = (LinearLayout) findViewById(R.id.layout);

        mPortfolioChart = new LineChart(this);
        ll.addView(mPortfolioChart, 1);
        mPortfolioChart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        PriceMarkerView mv2 = new PriceMarkerView(this, R.layout.price_marker_view);
        mPortfolioChart.setMarkerView(mv2);

        mPortfolioChart.setDragEnabled(true);
        mPortfolioChart.setScaleEnabled(true);
        //mPortfolioChart.setScaleXEnabled(false);
        mPortfolioChart.setDrawGridBackground(false);
        mPortfolioChart.getAxisRight().setEnabled(false);
        mPortfolioChart.setBackgroundColor(Color.rgb(255, 255, 255));

        XAxis xPortfolioXAxis = mPortfolioChart.getXAxis();
        xPortfolioXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis portfolioYAxis = mPortfolioChart.getAxisLeft();
        portfolioYAxis.setStartAtZero(false);
        portfolioYAxis.setDrawGridLines(false);

        loadData = new CalculateRealTimeSimulationAsyncTask();
        loadData.execute(mSimulation);
    }

    public class CalculateRealTimeSimulationAsyncTask extends AsyncTask<Simulation, Void, GraphData> {
        @Override
        protected GraphData doInBackground(Simulation... params) {
            Simulation simulation = params[0];
            String endDate = AppUtils.getCurrentDateString();
            Log.v("SIMULATION ENDDATE", endDate);
            String[] symbols = simulation.getSymbols();
            long simulationId = simulation.getId();

            List<List<Tick>> simulationTicks = new ArrayList<>();
            List<List<Tick>> stockTicks = new ArrayList<>();

            List<String> dates = new ArrayList<>();

            for (int i=0; i<symbols.length; i++) {
                stockTicks.add(new ArrayList<Tick>());
                String symbol = symbols[i];
                int dateCounter = 0;
                try {
                    String url = "https://quandl.com/api/v3/datasets/WIKI/" + symbol + ".csv?api_key=" + API_KEY + "&start_date=" + simulation.getStartDate() + "&end_date=" + endDate + "&order=asc";
                    Log.v("SIMULATION url", url);
                    URL quandl = new URL(url);
                    URLConnection connection = quandl.openConnection();
                    InputStreamReader is = new InputStreamReader(connection.getInputStream());
                    BufferedReader br = new BufferedReader(is);
                    String[] labels = br.readLine().split(",");
                    double prevClose = 0;
                    while (true) {
                        String line = br.readLine();
                        if (line == null || line.equals("")) {
                            break;
                        }
                        String[] stockInfo = line.split(",");
                        Log.v("SIMULATION Stock info", Arrays.toString(stockInfo));
                        String date = stockInfo[0];
                        prevClose = Double.parseDouble(stockInfo[4]);

                        if (i == 0) {
                            dates.add(date);
                        }
                        // TODO: Fix the error when the dates do not line up
                        if (i != 0 && !date.equals(dates.get(dateCounter))) {
                            Log.v("SIMULATION date", date + " " + dates.get(dateCounter));
                            int dateIndex = -1;
                            for (int d=dateCounter + 1; d < dates.size(); d++) {
                                if (dates.get(d) == date) {
                                    dateIndex = d;
                                    break;
                                }
                            }
                            if (dateIndex < 0) {
                                continue;
                            }
                            else {
                                for (int d=dateCounter; d<dateIndex; d++) {
                                    Tick copyTick = new Tick(symbol, prevClose, dates.get(d), simulationId);
                                    copyTick.save();
                                    stockTicks.get(i).add(copyTick);
                                }
                                dateCounter = dateIndex;
                            }
                        }
                        // The stock prices are in reverse chronological order.
                        Tick tick = new Tick(symbol, prevClose, date, simulationId);
                        tick.save();
                        stockTicks.get(i).add(tick);
                        dateCounter ++;
                    }
                    mSimulation.save();
                } catch (Exception e) {
                    Log.e("Exception", e.toString());
                    return null;
                }
            }

            List<SimulationStrategy> strategies = simulation.getSimulationStrategies();
            if (stockTicks.get(0).size() != 0) {
                for (SimulationStrategy strategy : strategies) {
                    List<Tick> portfolioTicks = PortfolioRebalanceUtil.calculatePortfolioValue(
                            stockTicks, simulation.getWeights(), strategy.getStrategy(),
                            simulation.getBank(), simulation.getAccount(), 0, strategy.getFloor(),
                            strategy.getMultiplier(), strategy.getOptionPrice(), strategy.getStrike()
                    );
                    simulationTicks.add(portfolioTicks);
                }
            }

            return new GraphData(strategies, simulationTicks, stockTicks);
        }

        @Override
        protected void onPostExecute(GraphData graphData) {
            if (graphData != null) {
                List<List<Tick>> stockTicks = graphData.getStockTicks();
                List<List<Tick>> simulationTicks = graphData.getSimulationTicks();
                List<SimulationStrategy> strategyList = graphData.getSimulationStrategies();

                int portfolioSize = stockTicks.size();
                int numTicks = stockTicks.get(0).size();
                if (numTicks == 0) {
                    Toast toast = Toast.makeText(mContext, "No data available since start date!", Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }

                List<String> xVals = new ArrayList<>();
                for (int i = 0; i < numTicks; i++) {
                    xVals.add(simulationTicks.get(0).get(i).getDate());
                }

                List<LineDataSet> portfolioSets = new ArrayList<>();
                List<LineDataSet> stockSets = new ArrayList<>();

                for (int j = 0; j < simulationTicks.size(); j++) {
                    List<Tick> simTicks = simulationTicks.get(j);
                    List<Entry> portfolioList = new ArrayList<>();
                    for (int i = 0; i < numTicks; i++) {
                        portfolioList.add(new Entry((float) simTicks.get(i).getPrice(), i));
                    }
                    LineDataSet balanceSet = new LineDataSet(portfolioList, SimulationConstants.getSimulationStrategyInfoShort(strategyList.get(j)));
                    balanceSet.setColor(ContextCompat.getColor(mContext, StockHelper.getSimulationColorResource(j)));
                    balanceSet.setCircleColorHole(ContextCompat.getColor(mContext, StockHelper.getSimulationColorResource(j)));
                    balanceSet.setCircleColor(ContextCompat.getColor(mContext, StockHelper.getSimulationColorResource(j)));
                    balanceSet.setCircleSize(2f);
                    balanceSet.setDrawHorizontalHighlightIndicator(false);
                    balanceSet.setDrawValues(false);
                    portfolioSets.add(balanceSet);
                }

                // TODO: add a shared preference to allow users to customize number of stocks shown
                int numStocksShown = portfolioSize > 5 ? 5 : portfolioSize;
                for (int i = 0; i<numStocksShown; i++) {
                    List<Entry> stockEntryList = new ArrayList<>();
                    String symbol = stockTicks.get(i).get(0).getSymbol();
                    for (int j = 0; j < numTicks; j++) {
                        stockEntryList.add(new Entry((float) stockTicks.get(i).get(j).getPrice(), j));
                    }
                    LineDataSet lineDataSet = new LineDataSet(stockEntryList, symbol);
                    lineDataSet.setColor(ContextCompat.getColor(mContext, StockHelper.getStockColorResource(i)));
                    lineDataSet.setCircleColor(ContextCompat.getColor(mContext, StockHelper.getStockColorResource(i)));
                    lineDataSet.setCircleColorHole(ContextCompat.getColor(mContext, StockHelper.getStockColorResource(i)));
                    lineDataSet.setCircleSize(2f);
                    lineDataSet.setDrawHorizontalHighlightIndicator(false);
                    lineDataSet.setDrawValues(false);
                    stockSets.add(lineDataSet);
                }
                mPortfolioChart.getAxisLeft().setSpaceTop(30f);
                mPortfolioChart.getAxisLeft().setSpaceBottom(30f);
                mPortfolioChart.setData(new LineData(xVals, portfolioSets));
                mPortfolioChart.animateXY(2000, 2000);
                mPortfolioChart.invalidate();
            }
            else {
                Log.e("SIMULATION error", "Failed to fetch data!");
                Toast toast = Toast.makeText(mContext, "Failed to fetch data!", Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }
}
