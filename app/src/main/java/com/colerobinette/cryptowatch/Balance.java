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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
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

        // set the symbol text
        ((TextView) view.findViewById(R.id.coinName)).setText(Balance.GetCoinSymbol(id));

        // set the holdings text
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.format("%1$.3f", Balance.GetCoinHoldings(id)));

        // set the icon of the listview item
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
        Uri uri = Uri.fromFile(file);
        ((ImageView) view.findViewById(R.id.coinIcon)).setImageURI(uri);

        // set the price text
        ((TextView) view.findViewById(R.id.coinPrice)).setText(Balance.GetCurrencySign() + String.format("%1$,.2f", Balance.GetCoinPrice(id)));

        // set the value of the holdings text
        ((TextView) view.findViewById(R.id.coinValue)).setText(Balance.GetCurrencySign() + String.format("%1$,.2f", Balance.GetCoinValue(id)));

        // set the percent changed text
        double change = Balance.GetCoinPriceChange(id);
        TextView changeText = (TextView) view.findViewById(R.id.percentChange);
        if (change > 0) {
            changeText.setText("+" + String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.rgb(0, 150, 0));
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_green);
        } else if (change < 0) {
            changeText.setText(String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.RED);
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.arrow_red);
        } else {
            changeText.setText("+" + String.format("%1$,.2f", change) + "%");
            changeText.setTextColor(Color.BLACK);
            ((ImageView) view.findViewById(R.id.changeArrow)).setImageResource(R.drawable.dash);
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
        return Balance.NumCoinsWithSymbol(Balance.SelectedCoin);
    }

    @Override
    public Object getItem(int index) {
        return Balance.GetCoinId(Balance.SelectedCoin, index);
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

        // set the name of the coin
        ((TextView) view.findViewById(R.id.coinName)).setText(Balance.GetCoinName(id));

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

    static boolean addToExistingBalance = false;

    static Map<String, String> currencies = new HashMap<>();
    //endregion

    //region DATA ACCESSORS
    public static String GetTimeFrame() {
        try {
            if (data.has("timeFrame")) {
                return data.getString("timeFrame");
            }
        } catch (Exception e) {}
        return "percent_change_24h"; // use 24 hour time frame by default
    }

    public static void NextTimeFrame() {
        try {
            String value = GetTimeFrame();
            if (value.equals("percent_change_24h")) {
                data.put("timeFrame", "percent_change_7d");
            } else if (value.equals("percent_change_7d")) {
                data.put("timeFrame", "percent_change_1h");
            } else {
                data.put("timeFrame", "percent_change_24h");
            }
        } catch (Exception e) {}
    }

    public static String GetSortBy() {
        try {
            if (data.has("sortBy")) {
                return data.getString("sortBy");
            }
        } catch (Exception e) {}
        return "coin"; // sort by coin by default
    }

    public static void SetSortBy(String value) {
        try {
            data.put("sortBy", value);
        } catch (Exception e) {}
    }

    public static String GetCurrency() {
        try {
            if (data.has("currencyCode")) {
                return data.getString("currencyCode");
            }
        } catch (Exception e) {}
        return "USD"; // use USD by default
    }

    public static void SetCurrency(String value) {
        try {
            data.put("currencyCode", value);
        } catch (Exception e) {}
    }

    public static String GetCurrencySign() {
        if (currencies.containsKey(GetCurrency())) {
            return currencies.get(GetCurrency());
        }
        return "";
    }

    public static boolean GetSortDesc() {
        try {
            if (data.has("sortDesc")) {
                return data.getBoolean("sortDesc");
            }
        } catch (Exception e) {}
        return true; // sort desc by default
    }

    public static void SetSortDesc(boolean value) {
        try {
            data.put("sortDesc", value);
        } catch (Exception e) {}
    }

    public static Date GetLastUpdate() {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return df.parse(data.getString("lastUpdate"));
        } catch (Exception e) {}
        return new Date(0); // use 1970 as default last update time
    }

    public static void SetLastUpdate() {
        try {
            Date now = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            data.put("lastUpdate", df.format(now));
        } catch (Exception e) {}
    }

    public static LinkedList<String> GetTrackedIds() {
        LinkedList<String> result = new LinkedList<>();
        try {
            for (int i = 0; i < data.getJSONObject("trackedIds").length(); i++){
                result.add(data.getJSONObject("trackedIds").names().getString(i));
            }
        } catch (Exception e) {}
        return result;
    }

    public static void RemoveTrackedId(String id) {
        try {
            data.getJSONObject("trackedIds").remove(id);
        } catch (Exception e) {}
    }

    public static int NumCoinsWithSymbol(String symbol) {
        try {
            return data.getJSONObject("ids").getJSONArray(symbol).length();
        } catch (Exception e) {}
        return 0;
    }

    public static String GetCoinId(String symbol, int index) {
        try {
            return data.getJSONObject("ids").getJSONArray(symbol).getString(index);
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String GetCoinSymbol(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getString("symbol");
        } catch (Exception e) {}
        return "ERROR";
    }

    public static String GetCoinName(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getString("name");
        } catch (Exception e) {}
        return "ERROR";
    }

    public static Double GetCoinPrice(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getJSONObject("quotes").getJSONObject(GetCurrency()).getDouble("price");
        } catch (Exception e) {}
        return 0d;
    }

    public static Double GetCoinPriceChange(String id) {
        try {
            return data.getJSONObject("coins").getJSONObject(id).getJSONObject("quotes").getJSONObject(GetCurrency()).getDouble(GetTimeFrame());
        } catch (Exception e) {}
        return 0d;
    }

    public static Double GetCoinHoldings(String id) {
        try {
            return data.getJSONObject("trackedIds").getJSONObject(id).getDouble("holdings");
        } catch (Exception e) {}
        return 0d;
    }

    public static void SetCoinHoldings(String id, double holdings) {
        try {
            // create tracked id list if it does not exist
            if (!data.has("trackedIds")) {
                data.put("trackedIds", new JSONObject());
            }

            // begin tracking id if it is not already
            if (!data.getJSONObject("trackedIds").has(id)) {
                data.getJSONObject("trackedIds").put(id, new JSONObject());
            }

            // set holdings for id
            data.getJSONObject("trackedIds").getJSONObject(id).put("holdings", holdings);
        } catch (Exception e) {}
    }

    public static Double GetCoinValue(String id) {
        return GetCoinPrice(id) * GetCoinHoldings(id);
    }
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

    public void OpenCurrencyBox(View v) {
        // set the edit title and erase the balance input text
        ((TextView) findViewById(R.id.currencyInput)).setText("");

        // disable input
        ToggleInput(false);

        // show the edit box
        findViewById(R.id.setCurrencyBox).setVisibility(FrameLayout.VISIBLE);

        // show keyboard for balance input
        EditText editText = (EditText) findViewById(R.id.currencyInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void ChangeCurrency() {
        // get currency from input box
        String currency = ((TextView) findViewById(R.id.currencyInput)).getText().toString();

        // Close balance box
        CloseCurrencyBox();

        // update the currency
        SetCurrency(currency);

        // update listings
        Update();
    }

    public void CloseCurrencyBox() {
        // close keyboard
        EditText editText = (EditText) findViewById(R.id.currencyInput);
        editText.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);

        // hide the edit box
        findViewById(R.id.setCurrencyBox).setVisibility(FrameLayout.GONE);

        // enable input again
        ToggleInput(true);
    }

    public void Refresh(View v) {
        Update();
    }

    public void ChangeTimeFrame(View v) {
        // change to the next time frame
        NextTimeFrame();

        // update the display
        UpdateDisplay();
    }
    //endregion

    //region SORTING
    void Sort() {
        // populated tracked ids list
        trackedIds = GetTrackedIds();

        // create comparators for the sort
        Comparator comparator = new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                int result = GetCoinSymbol(rhs).compareTo(GetCoinSymbol(lhs));
                if (GetSortDesc()) {
                    return result * -1;
                }
                return result;
            }
        };
        if (GetSortBy().equals("holdings")) {
            comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = Double.compare(GetCoinValue(lhs), GetCoinValue(rhs));
                    if (GetSortDesc()) {
                        result *= -1;
                    }
                    return result;
                }
            };
        } else if (GetSortBy().equals("price")) {
            comparator = new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int result = Double.compare(GetCoinPriceChange(lhs), GetCoinPriceChange(rhs));
                    if (GetSortDesc()) {
                        result *= -1;
                    }
                    return result;
                }
            };
        }

        // sort the track ids list
        Collections.sort(trackedIds, comparator);
    }

    public void SortByValue(String value) {
        // if already sorting by this value
        if (GetSortBy().equals(value)) {
            // reverse the sort direction
            SetSortDesc(!GetSortDesc());
        }
        // if not already sorting by the value
        else {
            // sort desc by the value
            SetSortBy(value);
            SetSortDesc(true);
        }

        // update the display
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

        // if there is only one id mapped to this symbol then prompt for balance input
        if (NumCoinsWithSymbol(symbol) == 1) {
            // Set selected coin to the mapped id
            SelectedCoin = GetCoinId(symbol, 0);

            // download the icon for this coin id
            DownloadIcon(SelectedCoin);

            // add to the existing balance
            addToExistingBalance = true;

            // open balance box
            OpenBalanceInputBox();
        }
        // if there is more than one mapped id with this symbol
        else if (NumCoinsWithSymbol(symbol) > 1) {
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

        // add to the existing balance
        addToExistingBalance = true;

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
        if (addToExistingBalance) {
            ((TextView) findViewById(R.id.editTitle)).setText("Add to " + GetCoinName(SelectedCoin) + " Holdings");
            ((TextView) findViewById(R.id.balanceInput)).setText("");
        } else {
            ((TextView) findViewById(R.id.editTitle)).setText("Set " + GetCoinName(SelectedCoin) + " Holdings");
            ((TextView) findViewById(R.id.balanceInput)).setText(Double.toString(GetCoinHoldings(SelectedCoin)));
        }

        // disable input
        ToggleInput(false);

        // show the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.VISIBLE);

        // show keyboard for balance input
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.requestFocus();
        editText.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void DeleteSelected(View v) {
        // delete the coin icon
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + SelectedCoin + ".png");
        if (file.exists()) {
            file.delete();
        }

        // remove from tracked coins list
        RemoveTrackedId(SelectedCoin);

        // close the edit box
        CloseBalanceEditBox();

        // update the display
        UpdateDisplay();
    }

    public void SaveBalance() {
        // get holdings from input box
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();

        // Close balance box
        CloseBalanceEditBox();

        // add the holdings input to the previous holdings
        double prevHoldings = GetCoinHoldings(SelectedCoin);
        double newHoldings = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

        // set holdings for selected coin
        if (addToExistingBalance) {
            SetCoinHoldings(SelectedCoin, prevHoldings + newHoldings);
        } else {
            SetCoinHoldings(SelectedCoin, newHoldings);
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
        Log.d("DOWNLOAD ICON", "id = " + id);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    // download the icon if we do not already have a copy
                    File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + id + ".png");
                    if (!file.exists()) {
                        URL iconUrl = new URL("https://s2.coinmarketcap.com/static/img/coins/128x128/" + id + ".png");
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
                    Log.e("Failed to download icon", e.getMessage(), e);
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
        Date last_update = GetLastUpdate();
        Date next_update = new Date(last_update.getTime() + updateDelay);
        Date now = Calendar.getInstance().getTime();
        if (now.after(next_update)) {
            return true;
        }
        return false;
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
            SetLastUpdate();

            // perform update in the background
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        // download all coin info from coinmarketcap.com
                        int start = 1;
                        JSONObject ids = new JSONObject(); // map each symbol to list of ids
                        JSONObject coinsData = new JSONObject(); // collect all coin data

                        while (true) {
                            try {
                                URL url = new URL("https://api.coinmarketcap.com/v2/ticker/?convert=" + GetCurrency() + "&limit=100&start=" + start);
                                InputStream inputStream = url.openStream();
                                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder jsonBuilder = new StringBuilder("");
                                String line;
                                while ((line = in.readLine()) != null) {
                                    jsonBuilder.append(line);
                                }
                                JSONObject result = new JSONObject(jsonBuilder.toString());
                                in.close();

                                // no more data so stop
                                if (result.isNull("data")) {
                                    break;
                                }

                                // add coin info to coin data
                                JSONObject coinsInfo = result.getJSONObject("data");
                                Iterator<String> keys = coinsInfo.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();

                                    // add coin info to coins map using id
                                    JSONObject coinInfo = coinsInfo.getJSONObject(key);
                                    coinsData.put(key, coinInfo);

                                    // get the symbol of this coin
                                    String coinSymbol = coinInfo.getString("symbol");

                                    // create list in symbols map if this symbol is not already in it
                                    if (!ids.has(coinSymbol)) {
                                        ids.put(coinSymbol, new JSONArray());
                                    }

                                    // add this coin's id to the symbol map
                                    ids.getJSONArray(coinSymbol).put(key);
                                }

                                // increment page start
                                start += 100;
                            } catch (Exception e) { break; }
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

    void UpdateDisplay() {
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
        String arrow = (GetSortDesc()) ? "↓" : "↑";
        if (GetSortBy().equals("coin")) {
            coinHeader.setText("Coin" + arrow);
        } else if (GetSortBy().equals("holdings")) {
            holdingsHeader.setText("Holdings" + arrow);
        } else if (GetSortBy().equals("price")) {
            priceHeader.setText("Price" + arrow);
        }

        // Update total value and previous value (used for percent change)
        double totalValue = 0;
        double previousValue = 0;
        for (int i = 0; i < trackedIds.size(); i++) {
            String id = trackedIds.get(i);
            totalValue += GetCoinValue(id);

            // exclude coins that lost 100% of their value from portfolio price change calculation
            if (GetCoinPriceChange(id) != -100d) {
                previousValue += GetCoinValue(id) / (1 + (GetCoinPriceChange(id) / 100));
            }
        }
        ((TextView) findViewById(R.id.valueTotal)).setText(String.format("%1$,.2f", totalValue));

        // Update portfolio title
        String portfolioTitle = "Total Portfolio Value (" + GetCurrency() + ")";
        ((TextView) findViewById(R.id.portfolioValueTitle)).setText(portfolioTitle);
        ((TextView) findViewById(R.id.smallDollarSign)).setText(GetCurrencySign());

        // Update change title
        String changeTitle = "24hr Change";
        if (GetTimeFrame().equals("percent_change_1h")) {
            changeTitle = "1hr Change";
        } else if (GetTimeFrame().equals("percent_change_7d")) {
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
        } else if (percentChange > 0) {
            portfolioArrow.setImageResource(R.drawable.arrow_green);
        } else {
            portfolioArrow.setImageResource(R.drawable.dash);
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

        // initialize currencies mappings
        currencies.put("USD", "$");
        currencies.put("AUD", "$");
        currencies.put("BRL", "R$");
        currencies.put("CAD", "$");
        currencies.put("CHF", "");
        currencies.put("CLP", "$");
        currencies.put("CNY", "¥");
        currencies.put("CZK", "Kč");
        currencies.put("DKK", "kr");
        currencies.put("EUR", "€");
        currencies.put("GBP", "£");
        currencies.put("HKD", "$");
        currencies.put("HUF", "Ft");
        currencies.put("IDR", "Rp");
        currencies.put("ILS", "₪");
        currencies.put("INR", "₹");
        currencies.put("JPY", "¥");
        currencies.put("KRW", "₩");
        currencies.put("MXN", "$");
        currencies.put("MYR", "RM");
        currencies.put("NOK", "kr");
        currencies.put("NZD", "$");
        currencies.put("PHP", "₱");
        currencies.put("PKR", "₨");
        currencies.put("PLN", "zł");
        currencies.put("RUB", "\u20BD");
        currencies.put("SEK", "kr");
        currencies.put("SGD", "$");
        currencies.put("THB", "฿");
        currencies.put("TRY", "₺");
        currencies.put("TWD", "$");
        currencies.put("ZAR", "R");

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
                addToExistingBalance = false; // overwrite existing balance
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

        // setup currency box
        BalanceEditText currencyInput = (BalanceEditText) findViewById(R.id.currencyInput);

        // save balance and close input box when submit is pressed on keyboard
        currencyInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    ChangeCurrency();
                    return true;
                }
                return false;
            }
        });

        currencyInput.setKeyImeChangeListener(new BalanceEditText.KeyImeChange() {
            @Override
            public void onKeyIme(int keyCode, KeyEvent event) {
                if (KeyEvent.KEYCODE_BACK == event.getKeyCode()) {
                    CloseCurrencyBox();
                }
            }
        });

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
