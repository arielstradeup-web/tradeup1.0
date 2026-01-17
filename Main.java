import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.util.Locale;

import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;

public class Main {

    // ===== CAPITAL =====
    static double cash = 10_000_000; // capital départ
    static double btc = 0;
    static double safe = 0;

    static double startCapital = cash;

    // ===== TRADING =====
    static final double BUY_PERCENT = 0.05; // 5%
    static final double SELL_PERCENT = 0.30;
    static final double FEE = 0.001;

    // SAFE
    static final double SAFE_RATIO = 0.30; // 30% du gain
    static int lastPercentSaved = 0;

    static final int DELAY = 1_000;

    static double lastPrice = 0;
    static int tick = 0;
    static long startTime = System.currentTimeMillis();

    // ===== GRAPH =====
    static XYSeries capitalSeries = new XYSeries("Capital");
    static XYSeries safeSeries = new XYSeries("Safe");

    static XYSeriesCollection capitalData =
            new XYSeriesCollection(capitalSeries);
    static XYSeriesCollection safeData =
            new XYSeriesCollection(safeSeries);

    // ===== FORMAT =====
    static String formatNormal(double n){
        return NumberFormat.getInstance(Locale.US)
                .format(Math.round(n));
    }

    public static void main(String[] args) {

        System.out.println("=== BOT BTC ===");
        startGraph();

        while(true){
            try{

                double price = getBTCPrice();
                tick++;

                double total = cash + btc * price;

                double minutes =
                        (System.currentTimeMillis()-startTime)/60000.0;

                // ===== LOG =====
                System.out.println("\nMinute: "
                        + new DecimalFormat("0.0").format(minutes));
                System.out.println("Tick: "+tick);
                System.out.println("BTC PRICE: "+price);

                // % CHANGE BTC
                showPriceChange(price);

                System.out.println("Money in BTC: "
                        + formatNormal(btc*price));
                System.out.println("Cash: "
                        + formatNormal(cash));
                System.out.println("Safe: "
                        + formatNormal(safe));
                System.out.println("TOTAL: "
                        + formatNormal(total));

                // GRAPH
                capitalSeries.add(minutes, total);
                safeSeries.add(minutes, safe);

                tradeLogic(price,total);
                manageSafe(total);

                Thread.sleep(DELAY);

            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    // ===== STRATEGIE =====
    static void tradeLogic(double price,double total){

        if(lastPrice==0){
            lastPrice=price;
            System.out.println("IA: Observation...");
            return;
        }

        double diff=(price-lastPrice)/lastPrice;

        if(diff>0.000075){
            buy(price);
        }
        else if(diff<-0.000075){
            sell(price);
        }
        else{
            System.out.println("IA: HOLD");
        }

        lastPrice=price;
    }

    // ===== SAFE LOGIC =====
    static void manageSafe(double total){

        double percentGain =
                ((total - startCapital)/startCapital)*100;

        int step = (int)percentGain;

        if(step > lastPercentSaved){

            double gainValue =
                    startCapital * (step/100.0);

            double protect = gainValue * SAFE_RATIO;

            if(cash >= protect){
                cash -= protect;
                safe += protect;

                System.out.println("🛡 SAFE + "
                        + formatNormal(protect)
                        + " (from +" + step + "%)");

                lastPercentSaved = step;
            }
        }
    }

    // ===== ACTIONS =====
    static void buy(double price){
        if(cash<=0)return;

        double amount=cash*BUY_PERCENT;
        double fee=amount*FEE;
        double net=amount-fee;

        btc+=net/price;
        cash-=amount;

        System.out.println("🟢 BUY 5%");
    }

    static void sell(double price){
        if(btc<=0)return;

        double qty=btc*SELL_PERCENT;
        double value=qty*price;
        double fee=value*FEE;
        double net=value-fee;

        cash+=net;
        btc-=qty;

        System.out.println("🔴 SELL 30%");
    }

    // ===== GRAPH =====
    static void startGraph(){

        JFreeChart capChart=
                ChartFactory.createXYLineChart(
                        "CAPITAL",
                        "Minutes",
                        "Argent",
                        capitalData);

        JFreeChart safeChart=
                ChartFactory.createXYLineChart(
                        "SAFE",
                        "Minutes",
                        "Argent",
                        safeData);

        formatAxis(capChart);
        formatAxis(safeChart);

        JPanel panel=new JPanel();
        panel.setLayout(new java.awt.GridLayout(1,2));
        panel.add(new ChartPanel(capChart));
        panel.add(new ChartPanel(safeChart));

        JFrame f=new JFrame("BOT GRAPH LIVE");
        f.setContentPane(panel);
        f.setSize(1100,600);
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    static void formatAxis(JFreeChart chart){

        XYPlot p = chart.getXYPlot();

        // Axe X = minutes
        NumberAxis x = (NumberAxis) p.getDomainAxis();
        x.setNumberFormatOverride(
                new DecimalFormat("0.0"));

        // Axe Y = argent
        NumberAxis y = (NumberAxis) p.getRangeAxis();

        // 🔥 FIX SAFE SCALE
        y.setAutoRange(true);
        y.setAutoRangeIncludesZero(true);
        y.setLowerBound(0);

        // Format propre (k)
        y.setNumberFormatOverride(new NumberFormat() {

            public StringBuffer format(double n,
                                       StringBuffer b,
                                       java.text.FieldPosition f){
                if(n >= 1000){
                    b.append(String.format("%.1fk", n/1000));
                }else{
                    b.append((int)n);
                }
                return b;
            }

            public StringBuffer format(long n,
                                       StringBuffer b,
                                       java.text.FieldPosition f){
                if(n >= 1000){
                    b.append(String.format("%.1fk", n/1000));
                }else{
                    b.append(n);
                }
                return b;
            }

            public Number parse(String s,
                                java.text.ParsePosition p){
                return null;
            }
        });
    }


    // ===== PRICE CHANGE LOG =====
    static void showPriceChange(double price){

        if(lastPrice==0){
            lastPrice=price;
            System.out.println("⏳ Loading BTC...");
            return;
        }

        double change =
                ((price-lastPrice)/lastPrice)*100;

        if(change>0){
            System.out.println("🟢 BTC UP "
                    + new DecimalFormat("0.00").format(change)
                    + "% 📈");
        }
        else if(change<0){
            System.out.println("🔴 BTC DOWN "
                    + new DecimalFormat("0.00").format(change)
                    + "% 📉");
        }
        else{
            System.out.println("⚪ BTC STABLE");
        }
    }

    // ===== BTC PRICE =====
    static double getBTCPrice() throws Exception{

        String api=
                "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd";

        URL url=new URL(api);
        HttpURLConnection c=
                (HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET");

        BufferedReader r=
                new BufferedReader(
                        new InputStreamReader(
                                c.getInputStream()));

        String line;
        StringBuilder res=new StringBuilder();

        while((line=r.readLine())!=null){
            res.append(line);
        }
        r.close();

        String json=res.toString();
        String part=json.split(":")[2]
                .replace("}}","");

        return Double.parseDouble(part);
    }
}
