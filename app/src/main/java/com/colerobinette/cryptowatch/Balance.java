package com.colerobinette.cryptowatch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class BalanceAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    public BalanceAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return Balance.trackedSymbols.size();
    }

    @Override
    public Object getItem(int index) {
        return Balance.trackedSymbols.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the symbol for this index
        String symbol = (String) getItem(index);

        // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_balance, parent, false);

        try {
            // set the symbol text
            ((TextView) view.findViewById(R.id.coinName)).setText(symbol);

            // set the holdings text
            Double holdings = Balance.data.getJSONObject("trackedCoins").getJSONObject(symbol).getDouble("holdings");
            ((TextView) view.findViewById(R.id.coinBalance)).setText(String.format("%1$.3f", holdings));

            // get the coin info for this coin
            JSONObject coinInfo = Balance.data.getJSONObject("coins").getJSONObject(symbol);

            // set the icon of the listview item
            String id = coinInfo.getString("id");
            File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
            Uri uri = Uri.fromFile(file);
            ((ImageView) view.findViewById(R.id.coinIcon)).setImageURI(uri);

            // set the price text
            Double price = coinInfo.getDouble("price_usd");
            ((TextView) view.findViewById(R.id.coinPrice)).setText("$" + String.format("%1$,.2f", price));

            // set the value of the holdings text
            ((TextView) view.findViewById(R.id.coinValue)).setText("$" + String.format("%1$,.2f", price * holdings));

            // set the percent changed text
            double change = coinInfo.getDouble(Balance.data.getString("timeFrame"));
            TextView changeText = (TextView) view.findViewById(R.id.percentChange);
            if (change > 0) {
                changeText.setText("+" + String.format("%1$,.2f", change) + "%");
                changeText.setTextColor(Color.rgb(0, 150, 0));
            } else if (change < 0) {
                changeText.setText(String.format("%1$,.2f", change) + "%");
                changeText.setTextColor(Color.RED);
            } else {
                changeText.setText("+" + String.format("%1$,.2f", change) + "%");
                changeText.setTextColor(Color.BLACK);
            }

            // set change arrow
            if (change < 0) {
                ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_red);
            } else {
                ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_green);
            }

        } catch (JSONException e) {
            Log.e("display", e.getMessage());
        }

        return view;
    }
}

public class Balance extends Activity {
    public static BalanceAdapter balanceAdapter = null;
    public static Context actContext;

    public static LinkedList<String> trackedSymbols = new LinkedList<>(); // sorted list of tracked symbols
    static String SelectedCoin = ""; // symbol of the selected coin
    public static boolean paused = false; // prevents updating when not active
    public static boolean updating = false; // used to prevent concurrent updates
    public static long updateDelay = 60000; // milliseconds between updates

    public static JSONObject data = new JSONObject(); // all app data for easy saving/loading

    public static void Load(Context context) {
        try {
            // set defaults
            data.put("sortBy", "coin");
            data.put("sortDesc", false);
            data.put("timeFrame", "percent_change_24h");
            data.put("coins", new JSONObject());
            data.put("trackedCoins", new JSONObject());

            // load data from file
            InputStream inputStream = context.openFileInput("data.json");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                data = new JSONObject(bufferedReader.readLine());
                inputStream.close();
            }

            // load from old save file format
//            String line = "";
//            inputStream = context.openFileInput("listings.txt");
//            if ( inputStream != null ) {
//                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
//                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//                while ((line = bufferedReader.readLine()) != null) {
//                    String[] parts = line.split(",");
//                    String symbol = parts[1];
//                    double holdings = Double.parseDouble(parts[3]);
//                    JSONObject coin = new JSONObject();
//                    coin.put("holdings", holdings);
//                    data.getJSONObject("trackedCoins").put(symbol, coin);
//                }
//                inputStream.close();
//            }
        }
        catch (Exception e) {
            Log.e("BalancesLoad", e.toString());
        }
    }

    public static void Save(Context context) {
        try {
            // save data to file
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("data.json", Context.MODE_PRIVATE));
            outputStreamWriter.write(data.toString());
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("BalancesSave", e.toString());
        }
    }

    public void OpenReadme(View v) {
        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KarmaPenny/CryptoWatch/blob/master/README.md"));
        startActivity(intent);
    }

    public void LookupSelected() {
        try {
            String id = data.getJSONObject("coins").getJSONObject(SelectedCoin).getString("id");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://coinmarketcap.com/currencies/" + id));
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void ToggleInput(boolean toggle) {
        // toggle buttons on/off
        findViewById(R.id.balancesList).setEnabled(toggle);
        findViewById(R.id.refreshButton).setEnabled(toggle);
        findViewById(R.id.sortCoinButton).setEnabled(toggle);
        findViewById(R.id.sortHoldingsButton).setEnabled(toggle);
        findViewById(R.id.sortPriceButton).setEnabled(toggle);

        if (toggle) {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.GONE);
        } else {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.VISIBLE);
        }
    }

    public void CloseNewCoinBox() {
        // enable input again
        ToggleInput(true);

        // hide the edit box
        findViewById(R.id.newCoinBox).setVisibility(FrameLayout.GONE);

        // close keyboard
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void EnterCoinSymbol(View v) {
        // set the edit title and erase the balance input text
        ((TextView) findViewById(R.id.coinInput)).setText("");

        // disable input
        ToggleInput(false);

        // show the edit box
        findViewById(R.id.newCoinBox).setVisibility(FrameLayout.VISIBLE);

        // show keyboard for balance input
        EditText editText = (EditText) findViewById(R.id.coinInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void AddCoin() {
        // get symbol from coin input box
        String symbol = ((TextView) findViewById(R.id.coinInput)).getText().toString();

        // Close balance box
        CloseNewCoinBox();

        // Download the icon for this coin
        DownloadIcon(symbol);

        // prompt for balance input
        SelectedCoin = symbol;
        EditSelected();
    }

    public void CloseBalanceEditBox() {
        // enable input again
        ToggleInput(true);

        // hide the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);

        // close keyboard
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void DeleteSelected(View v) {
        // delete the coin icon
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + SelectedCoin + ".png");
        if (file.exists()) {
            file.delete();
        }

        // remove from tracked coins list
        try {
            data.getJSONObject("trackedCoins").remove(SelectedCoin);
        } catch (JSONException e) {}

        CloseBalanceEditBox();

        UpdateDisplay();
    }

    public void EditSelected() {
        // set the edit title and erase the balance input text
        ((TextView) findViewById(R.id.editTitle)).setText("Enter " + SelectedCoin + " Holdings");
        ((TextView) findViewById(R.id.balanceInput)).setText("");

        // disable input
        ToggleInput(false);

        // show the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.VISIBLE);

        // show keyboard for balance input
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void SaveBalance() {
        // get holdings from input box
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();

        // add the holdings input to the previous holdings
        try {
            double prevHoldings = 0;
            if (data.getJSONObject("trackedCoins").has(SelectedCoin)) {
                prevHoldings = data.getJSONObject("trackedCoins").getJSONObject(SelectedCoin).getDouble("holdings");
            }

            double newHoldings = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

            JSONObject coin = new JSONObject();
            coin.put("holdings", prevHoldings + newHoldings);
            data.getJSONObject("trackedCoins").put(SelectedCoin, coin);
        } catch (JSONException e) {}

        // Close balance box
        CloseBalanceEditBox();

        // update listings display
        UpdateDisplay();
    }

    public void Refresh(View v) {
        Update();
    }

    void Sort() {
        try {
            trackedSymbols.clear();
            JSONArray symbols = Balance.data.getJSONObject("trackedCoins").names();
            for (int i = 0; i < symbols.length(); i++) {
                trackedSymbols.add(symbols.getString(i));
            }

            Comparator comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = lhs.compareTo(rhs);
                    try {
                        if (!data.getBoolean("sortDesc")) {
                            return result * -1;
                        }
                    } catch (JSONException e) {}
                    return result;
                }
            };
            if (data.getString("sortBy").equals("holdings")) {
                comparator = new Comparator<String>() {
                    @Override
                    public int compare(String lhs, String rhs) {
                        int result = 0;
                        try {
                            double price1 = data.getJSONObject("coins").getJSONObject(lhs).getDouble("price_usd");
                            double price2 = data.getJSONObject("coins").getJSONObject(rhs).getDouble("price_usd");
                            double holdings1 = data.getJSONObject("trackedCoins").getJSONObject(lhs).getDouble("holdings");
                            double holdings2 = data.getJSONObject("trackedCoins").getJSONObject(rhs).getDouble("holdings");
                            double value1 = price1 * holdings1;
                            double value2 = price2 * holdings2;

                            result = Double.compare(value1, value2);
                            if (data.getBoolean("sortDesc")) {
                                result *= -1;
                            }
                        } catch (JSONException e) {}
                        return result;
                    }
                };
            } else if (data.getString("sortBy").equals("price")) {
                comparator = new Comparator<String>() {
                    @Override
                    public int compare(String lhs, String rhs) {
                        int result = 0;
                        try {
                            String timeFrame = data.getString("timeFrame");
                            double change1 = data.getJSONObject("coins").getJSONObject(lhs).getDouble(timeFrame);
                            double change2 = data.getJSONObject("coins").getJSONObject(rhs).getDouble(timeFrame);

                            result = Double.compare(change1, change2);
                            if (data.getBoolean("sortDesc")) {
                                result *= -1;
                            }
                        } catch (JSONException e) {}
                        return result;
                    }
                };
            }

            Collections.sort(trackedSymbols, comparator);
        } catch (JSONException e) {}
    }

    public void SortByValue(String value) {
        try {
            if (data.getString("sortBy") == value) {
                boolean sortDesc = data.getBoolean("sortDesc");
                data.put("sortDesc", !sortDesc);
            } else {
                data.put("sortBy", value);
                data.put("sortDesc", true);
            }
        } catch (JSONException e) {}
        UpdateDisplay();
    }

    public void SortByCoin(View v) {
        SortByValue("coin");
    }

    public void SortByHoldings(View v) {
        SortByValue("holdings");
    }

    public void SortByPrice(View v) {
        SortByValue("price");
    }

    public void ChangeTimeFrame(View v) {
        try {
            String value = data.getString("timeFrame");
            if (value.equals("percent_change_1h")) {
                data.put("timeFrame", "percent_change_24h");
            } else if (value.equals("percent_change_24h")) {
                data.put("timeFrame", "percent_change_7d");
            } else if (value.equals("percent_change_7d")) {
                data.put("timeFrame", "percent_change_1h");
            }
        } catch (JSONException e) {
            Log.d("TIMEFRAME", e.getMessage());
        }
        UpdateDisplay();
    }

    void UpdateDisplay() {
        try {
            // save data
            Save(this);

            // build a sorted list of tracked symbols for displaying
            Sort();

            // update sort arrows
            TextView coinHeader = (TextView) findViewById(R.id.sortCoinButton);
            TextView holdingsHeader = (TextView) findViewById(R.id.sortHoldingsButton);
            TextView priceHeader = (TextView) findViewById(R.id.sortPriceButton);
            coinHeader.setText("Coin");
            holdingsHeader.setText("Holdings");
            priceHeader.setText("Price");
            String arrow = (data.getBoolean("sortDesc")) ? "↓" : "↑";
            if (data.getString("sortBy").equals("coin")) {
                coinHeader.setText("Coin" + arrow);
            } else if (data.getString("sortBy").equals("holdings")) {
                holdingsHeader.setText("Holdings" + arrow);
            } else if (data.getString("sortBy").equals("price")) {
                priceHeader.setText("Price" + arrow);
            }

            // Update total value and previous value (used for percent change)
            double totalValue = 0;
            double previousValue = 0;
            String timeFrame = data.getString("timeFrame");
            JSONArray trackedCoins = data.getJSONObject("trackedCoins").names();
            for (int i = 0; i < trackedCoins.length(); i++) {
                String symbol = trackedCoins.getString(i);
                double holdings = data.getJSONObject("trackedCoins").getJSONObject(symbol).getDouble("holdings");
                double price = data.getJSONObject("coins").getJSONObject(symbol).getDouble("price_usd");
                double change = data.getJSONObject("coins").getJSONObject(symbol).getDouble(timeFrame);

                totalValue += price * holdings;
                previousValue += price * holdings / (1 + (change / 100));
            }
            ((TextView) findViewById(R.id.valueTotal)).setText(String.format("%1$,.2f", totalValue));

            // Update change title
            String changeTitle = "1hr Change";
            if (data.getString("timeFrame").equals("percent_change_24h")) {
                changeTitle = "24hr Change";
            } else if (data.getString("timeFrame").equals("percent_change_7d")) {
                changeTitle = "7d Change";
            }
            ((TextView) findViewById(R.id.changeTitle)).setText(changeTitle);

            // Update change value
            TextView changeText = (TextView) findViewById(R.id.change);
            if (previousValue <= 0) {
                changeText.setText("+" + String.format("%1$,.2f", 0) + "%");
                changeText.setTextColor(Color.BLACK);
            } else {
                double percentChange = 100 * (totalValue - previousValue) / previousValue;
                if (percentChange > 0) {
                    changeText.setText("+" + String.format("%1$,.2f", percentChange) + "%");
                    changeText.setTextColor(Color.rgb(0, 150, 0));
                } else if (percentChange < 0) {
                    changeText.setText(String.format("%1$,.2f", percentChange) + "%");
                    changeText.setTextColor(Color.RED);
                } else {
                    changeText.setText("+" + String.format("%1$,.2f", percentChange) + "%");
                    changeText.setTextColor(Color.BLACK);
                }

                // set portfolio change arrow
                if (percentChange < 0) {
                    ((ImageView) findViewById(R.id.portfolioChangeArrow)).setImageResource(R.drawable.arrow_red);
                } else {
                    ((ImageView) findViewById(R.id.portfolioChangeArrow)).setImageResource(R.drawable.arrow_green);
                }
            }
        } catch (Exception e) {
            Log.e("UpdateDisplay", e.getMessage());
        }

        // update list views
        balanceAdapter.notifyDataSetChanged();

        // stop animating refresh button
        final ImageView refreshButton = (ImageView) findViewById(R.id.refreshButton);
        refreshButton.setAnimation(null);

        // release lock on updating
        updating = false;
    }

    void RecordUpdateTime() {
        try {
            Date now = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            data.put("lastUpdate", df.format(now));
        } catch (JSONException e) {
            Log.e("Update Time", e.getMessage());
        }
    }

    boolean ShouldUpdate() {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date last_update = df.parse(data.getString("lastUpdate"));
            Date next_update = new Date(last_update.getTime() + updateDelay);
            Date now = Calendar.getInstance().getTime();
            if (now.after(next_update)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    void Update() {
        if (!updating) {
            // prevent concurrent updates
            updating = true;

            // animate refresh button
            RotateAnimation anim = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            anim.setInterpolator(new LinearInterpolator());
            anim.setRepeatCount(Animation.INFINITE);
            anim.setDuration(1000);
            final ImageView refreshButton = (ImageView) findViewById(R.id.refreshButton);
            refreshButton.startAnimation(anim);

            RecordUpdateTime();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        // download all coin info from coinmarketcap.com
                        URL url = new URL("https://api.coinmarketcap.com/v1/ticker/?limit=0");
                        InputStream inputStream = url.openStream();
                        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                        StringBuilder jsonBuilder = new StringBuilder("");
                        String line;
                        while ((line = in.readLine()) != null) {
                            jsonBuilder.append(line);
                        }
                        JSONObject result = new JSONObject("{\"coins\":" + jsonBuilder.toString() + "}");
                        in.close();

                        // add coin info to coin data
                        JSONArray coinsInfo = result.getJSONArray("coins");
                        JSONObject coinsData = new JSONObject();
                        for (int i = 0; i < coinsInfo.length(); i++) {
                            JSONObject coinInfo = coinsInfo.getJSONObject(i);
                            coinsData.put(coinInfo.getString("symbol"), coinInfo);
                        }
                        data.put("coins", coinsData);
                    } catch (Exception e) {
                        Log.e("UpdatePrices", e.toString());
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void empty) {
                    super.onPostExecute(empty);
                    UpdateDisplay();
                }
            }.execute();
        }
    }

    void DownloadIcon(final String symbol) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    // get id of coin with this symbol
                    String id = data.getJSONObject("coins").getJSONObject(symbol).getString("id");

                    // download the icon if we do not already have a copy
                    File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
                    if (!file.exists()) {
                        URL iconUrl = new URL("https://files.coinmarketcap.com/static/img/coins/128x128/" + id + ".png");
                        InputStream in = new BufferedInputStream(iconUrl.openStream());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int n = 0;
                        while (-1 != (n = in.read(buf))) {
                            out.write(buf, 0, n);
                        }
                        out.close();
                        in.close();
                        byte[] response = out.toByteArray();

                        FileOutputStream fos = new FileOutputStream(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
                        fos.write(response);
                        fos.close();
                    }
                } catch (Exception e) {
                    Log.e("Failed to download icon", e.getMessage());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void empty) {
                super.onPostExecute(empty);
                UpdateDisplay();
            }
        }.execute();
    }

    Handler handler = new Handler();
    void AutoUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!paused) {
                    Update();
                }
                AutoUpdate();
            }
        }, updateDelay);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        actContext = this;
        setContentView(R.layout.activity_balance);

        // load data from file
        Load(this);

        // setup balances list
        ListView listView = (ListView) findViewById(R.id.balancesList);
        balanceAdapter = new BalanceAdapter(this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) balanceAdapter.getItem(position);
                LookupSelected();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) balanceAdapter.getItem(position);
                EditSelected();
                return true;
            }
        });

        listView.setAdapter(balanceAdapter);

        // setup edit box
        BalanceEditText balanceInput = (BalanceEditText) findViewById(R.id.balanceInput);

        // save balance and close input box when submit is pressed on keyboard
        balanceInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    SaveBalance();
                    return true;
                }
                return false;
            }
        });

        balanceInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseBalanceEditBox();
                }
            }
        });

        // setup new coin box
        BalanceEditText newCoinInput = (BalanceEditText) findViewById(R.id.coinInput);

        // save balance and close input box when submit is pressed on keyboard
        newCoinInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    AddCoin();
                    return true;
                }
                return false;
            }
        });

        newCoinInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseNewCoinBox();
                }
            }
        });

        // Auto Update
        AutoUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDisplay();
        if (ShouldUpdate()) {
            Update();
        }
        paused = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
    }
}
