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
        return Balance.trackedIds.size();
    }

    @Override
    public Object getItem(int index) {
        return Balance.trackedIds.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the id for this index
        String id = (String) getItem(index);

        // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_balance, parent, false);

        try {
            // get the coin info for this coin
            JSONObject coinInfo = Balance.data.getJSONObject("coins").getJSONObject(id);

            // set the symbol text
            ((TextView) view.findViewById(R.id.coinName)).setText(coinInfo.getString("symbol"));

            // set the holdings text
            Double holdings = Balance.data.getJSONObject("trackedIds").getJSONObject(id).getDouble("holdings");
            ((TextView) view.findViewById(R.id.coinBalance)).setText(String.format("%1$.3f", holdings));

            // set the icon of the listview item
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
            } else if (change > 0) {
                ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_green);
            } else {
                ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.dash);
            }

        } catch (JSONException e) {
            Log.e("display", e.getMessage());
        }

        return view;
    }
}

class CoinSelectAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    public CoinSelectAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        try {
            // use selected coin as symbol to get list of mapped ids
            return Balance.data.getJSONObject("ids").getJSONArray(Balance.SelectedCoin).length();
        } catch (JSONException e) {}
        return 0;
    }

    @Override
    public Object getItem(int index) {
        try {
            // use selected coin as symbol to get list of mapped ids
            return Balance.data.getJSONObject("ids").getJSONArray(Balance.SelectedCoin).getString(index);
        } catch (JSONException e) {}
        return "";
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the id for this index
        String id = (String) getItem(index);

        // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_coin, parent, false);

        try {
            // set the name of the coin
            String coinName = Balance.data.getJSONObject("coins").getJSONObject(id).getString("name");
            ((TextView) view.findViewById(R.id.coinName)).setText(coinName);
        } catch (JSONException e) {
            Log.e("display", e.getMessage());
        }

        return view;
    }
}

public class Balance extends Activity {
    //region STATIC VARS
    public static LinkedList<String> trackedIds = new LinkedList<>(); // sorted list of tracked symbols
    public static JSONObject data = new JSONObject(); // all app data for easy saving/loading

    static BalanceAdapter balanceAdapter = null;
    static CoinSelectAdapter coinSelectAdapter = null;
    static Context actContext;

    static String SelectedCoin = ""; // symbol of the selected coin
    static boolean paused = false; // prevents updating when not active
    static boolean updating = false; // used to prevent concurrent updates
    static long updateDelay = 60000; // milliseconds between updates
    static boolean selectingCoin = false; // used to close the coin selector box with the back button
    //endregion

    //region SAVE/LOAD
    void Load() {
        try {
            // load data from file
            InputStream inputStream = openFileInput("data.json");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                data = new JSONObject(bufferedReader.readLine());
                inputStream.close();
            }

            // load from legacy save file format
//            String line = "";
//            inputStream = openFileInput("listings.txt");
//            if ( inputStream != null ) {
//                JSONObject trackedIds = new JSONObject();
//                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
//                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
//                while ((line = bufferedReader.readLine()) != null) {
//                    String[] parts = line.split(",");
//                    String symbol = parts[1];
//                    double holdings = Double.parseDouble(parts[3]);
//                    String id = data.getJSONObject("ids").getJSONArray(symbol).getString(0);
//                    JSONObject coin = new JSONObject();
//                    coin.put("holdings", holdings);
//                    trackedIds.put(id, coin);
//                    DownloadIcon(id);
//                }
//                data.put("trackedIds", trackedIds);
//                inputStream.close();
//            }

            // import v1.0 data if it exists
            if (data.has("trackedCoins")) {
                // convert tracked coins to tracked ids
                JSONObject trackedIds = new JSONObject();
                JSONArray symbols = data.getJSONObject("trackedCoins").names();
                if (symbols != null) {
                    for (int i = 0; i < symbols.length(); i++) {
                        String symbol = symbols.getString(i);
                        String id = data.getJSONObject("coins").getJSONObject(symbol).getString("id");
                        JSONObject trackedInfo = data.getJSONObject("trackedCoins").getJSONObject(symbol);
                        trackedIds.put(id, trackedInfo);
                    }
                }
                data.put("trackedIds", trackedIds);
                data.remove("trackedCoins");

                // remap coin info from symbol to id
                JSONObject coins = new JSONObject();
                JSONArray keys = data.getJSONObject("coins").names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        String id = data.getJSONObject("coins").getJSONObject(key).getString("id");
                        JSONObject coinInfo = data.getJSONObject("coins").getJSONObject(key);
                        coins.put(id, coinInfo);
                    }
                }
                data.put("coins", coins);
            }
        }
        catch (Exception e) {
            Log.e("ERROR", e.getMessage(), e);
        }
    }

    void Save() {
        try {
            // save data to file
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput("data.json", Context.MODE_PRIVATE));
            outputStreamWriter.write(data.toString());
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("BalancesSave", e.toString());
        }
    }
    //endregion

    //region TITLE BUTTONS
    public void OpenReadme(View v) {
        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KarmaPenny/CryptoWatch/blob/master/README.md"));
        startActivity(intent);
    }

    public void Refresh(View v) {
        Update();
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
    //endregion

    //region SORTING
    void Sort() {
        try {
            trackedIds.clear();
            JSONArray ids = Balance.data.getJSONObject("trackedIds").names();
            if (ids == null) { return; }
            for (int i = 0; i < ids.length(); i++) {
                trackedIds.add(ids.getString(i));
            }

            Comparator comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = 0;
                    try {
                        String lsymbol = data.getJSONObject("coins").getJSONObject(lhs).getString("symbol");
                        String rsymbol = data.getJSONObject("coins").getJSONObject(rhs).getString("symbol");
                        result = rsymbol.compareTo(lsymbol);
                        if (data.getBoolean("sortDesc")) {
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
                            double holdings1 = data.getJSONObject("trackedIds").getJSONObject(lhs).getDouble("holdings");
                            double holdings2 = data.getJSONObject("trackedIds").getJSONObject(rhs).getDouble("holdings");
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

            Collections.sort(trackedIds, comparator);
        } catch (JSONException e) {}
    }

    public void SortByValue(String value) {
        try {
            if (data.getString("sortBy").equals(value)) {
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
    //endregion

    //region NEW COIN INPUT
    public void OpenCoinInputBox(View v) {
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

        JSONArray ids = new JSONArray();
        try {
            // get list of ids mapped to this symbol
            ids = data.getJSONObject("ids").getJSONArray(symbol);
        } catch (JSONException e) {
            Log.e("ERROR", e.getMessage(), e);
        }

        // if there is only one id mapped to this symbol then prompt for balance input
        if (ids.length() == 1) {
            // Set selected coin to the mapped id
            try {
                SelectedCoin = ids.getString(0);
            } catch (JSONException e) {
                Log.e("ERROR", e.getMessage(), e);
            }

            // download the icon for this coin id
            DownloadIcon(SelectedCoin);

            // open balance box
            OpenBalanceInputBox();
        }
        // if there is more than one mapped id with this symbol
        else {
            // set selected coin to the provided symbol
            SelectedCoin = symbol;

            // prompt user to select which mapped coin to add
            OpenCoinSelectorBox();
        }
    }

    public void OpenCoinSelectorBox() {
        // set to true so pressing the back button will close the selector box
        selectingCoin = true;

        // update coin selector list
        coinSelectAdapter.notifyDataSetChanged();

        // disable input
        ToggleInput(false);

        // Set title of coin selection box
        ((TextView) findViewById(R.id.chooseCoinTitle)).setText("Which " + SelectedCoin + "?");

        // show the coin selector box
        findViewById(R.id.coinSelector).setVisibility(FrameLayout.VISIBLE);
    }

    public void ChooseSelected() {
        // close the selector box
        CloseCoinSelectorBox();

        // download the icon for this coin id
        DownloadIcon(SelectedCoin);

        // open balance box
        OpenBalanceInputBox();
    }

    public void CloseCoinSelectorBox() {
        // set to false so pressing the back button will exit the app
        selectingCoin = false;

        // enable input again
        ToggleInput(true);

        // hide the coin selector box
        findViewById(R.id.coinSelector).setVisibility(FrameLayout.GONE);
    }

    public void CloseNewCoinBox() {
        // close keyboard
        EditText editText = (EditText) findViewById(R.id.coinInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        // hide the edit box
        findViewById(R.id.newCoinBox).setVisibility(FrameLayout.GONE);

        // enable input again
        ToggleInput(true);
    }
    //endregion

    //region BALANCE INPUT
    public void OpenBalanceInputBox() {
        // set the edit title and erase the balance input text
        try {
            String coinName = data.getJSONObject("coins").getJSONObject(SelectedCoin).getString("name");
            ((TextView) findViewById(R.id.editTitle)).setText("Enter " + coinName + " Holdings");
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
        } catch (JSONException e) {
            Log.e("ERROR", e.getMessage(), e);
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
            data.getJSONObject("trackedIds").remove(SelectedCoin);
        } catch (JSONException e) {}

        CloseBalanceEditBox();

        UpdateDisplay();
    }

    public void SaveBalance() {
        // get holdings from input box
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();

        // Close balance box
        CloseBalanceEditBox();

        // add the holdings input to the previous holdings
        try {
            double prevHoldings = 0;
            if (data.getJSONObject("trackedIds").has(SelectedCoin)) {
                prevHoldings = data.getJSONObject("trackedIds").getJSONObject(SelectedCoin).getDouble("holdings");
            }

            double newHoldings = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

            JSONObject coin = new JSONObject();
            coin.put("holdings", prevHoldings + newHoldings);
            data.getJSONObject("trackedIds").put(SelectedCoin, coin);
        } catch (JSONException e) {
            Log.e("ERROR", e.getMessage(), e);
        }

        // update listings display
        UpdateDisplay();
    }

    public void CloseBalanceEditBox() {
        // close keyboard
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        // hide the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);

        // enable input again
        ToggleInput(true);
    }
    //endregion

    public void LookupSelected() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://coinmarketcap.com/currencies/" + SelectedCoin));
        startActivity(intent);
    }

    public void ToggleInput(boolean toggle) {
        // toggle buttons on/off
        findViewById(R.id.balancesList).setEnabled(toggle);
        findViewById(R.id.refreshButton).setEnabled(toggle);
        findViewById(R.id.helpButton).setEnabled(toggle);
        findViewById(R.id.sortCoinButton).setEnabled(toggle);
        findViewById(R.id.sortHoldingsButton).setEnabled(toggle);
        findViewById(R.id.sortPriceButton).setEnabled(toggle);

        // toggle grey overlay on/off
        if (toggle) {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.GONE);
        } else {
            findViewById(R.id.greyOverlay).setVisibility(LinearLayout.VISIBLE);
        }
    }

    void DownloadIcon(final String id) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
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

    //region UPDATE
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

            // record the time of this update so we know when to update next
            RecordUpdateTime();

            // perform update in the background
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
                        JSONObject ids = new JSONObject(); // map each symbol to list of ids
                        JSONObject coinsData = new JSONObject();
                        for (int i = 0; i < coinsInfo.length(); i++) {
                            // add coin info to coins map using id
                            JSONObject coinInfo = coinsInfo.getJSONObject(i);
                            coinsData.put(coinInfo.getString("id"), coinInfo);

                            // get the symbol of this coin
                            String coinSymbol = coinInfo.getString("symbol");

                            // create list in symbols map if this symbol is not already in it
                            if (!ids.has(coinSymbol)) {
                                ids.put(coinSymbol, new JSONArray());
                            }

                            // add this coin's id to the symbol map
                            ids.getJSONArray(coinSymbol).put(coinInfo.getString("id"));
                        }
                        data.put("ids", ids);
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

    void RecordUpdateTime() {
        try {
            Date now = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            data.put("lastUpdate", df.format(now));
        } catch (JSONException e) {
            Log.e("Update Time", e.getMessage());
        }
    }

    void UpdateDisplay() {
        try {
            // save data
            Save();

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
            JSONArray ids = data.getJSONObject("trackedIds").names();
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    double holdings = data.getJSONObject("trackedIds").getJSONObject(id).getDouble("holdings");
                    double price = data.getJSONObject("coins").getJSONObject(id).getDouble("price_usd");
                    double change = data.getJSONObject("coins").getJSONObject(id).getDouble(timeFrame);

                    totalValue += price * holdings;
                    previousValue += price * holdings / (1 + (change / 100));
                }
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
            double percentChange = (previousValue == 0) ? 0 : 100 * (totalValue - previousValue) / previousValue;
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
            ImageView portfolioArrow = (ImageView) findViewById(R.id.portfolioChangeArrow);
            if (percentChange < 0) {
                portfolioArrow.setImageResource(R.drawable.arrow_red);
            } else if (percentChange > 0){
                portfolioArrow.setImageResource(R.drawable.arrow_green);
            } else {
                portfolioArrow.setImageResource(R.drawable.dash);
            }
        } catch (Exception e) {
            Log.e("UpdateDisplay", e.getMessage(), e);
        }

        // update list views
        balanceAdapter.notifyDataSetChanged();

        // stop animating refresh button
        final ImageView refreshButton = (ImageView) findViewById(R.id.refreshButton);
        refreshButton.setAnimation(null);

        // release lock on updating
        updating = false;
    }
    //endregion

    //region ACTIVITY METHODS
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        actContext = this;
        setContentView(R.layout.activity_balance);

        // load data from file
        Load();

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
                OpenBalanceInputBox();
                return true;
            }
        });

        listView.setAdapter(balanceAdapter);

        // setup choose coin list
        final ListView coinListView = (ListView) findViewById(R.id.collidingCoins);
        coinSelectAdapter = new CoinSelectAdapter(this);

        coinListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = (String) coinSelectAdapter.getItem(position);
                ChooseSelected();
            }
        });

        coinListView.setAdapter(coinSelectAdapter);

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
    public void onBackPressed() {
        // close the selector box if it is open instead of exiting activity
        if (selectingCoin) {
            CloseCoinSelectorBox();
        } else {
            super.onBackPressed();
        }
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
    //endregion
}
