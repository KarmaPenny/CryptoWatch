package com.colerobinette.cryptowatch;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

enum Coin {ARK, BTC, BCH, DASH, ETH, IOTA, LTC, NEO, XMR, XRP, VTC, ZEC}

class ExchangeListing {
    public Coin coin;
    public double balance;
    public double price;
    public boolean success;

    public ExchangeListing(Coin coin) {
        this.coin = coin;
        this.balance = 0;
        this.price = 0;
        success = true;
    }

    public ExchangeListing(String fromString) {
        String[] parts = fromString.split(",");
        coin = Coin.valueOf(parts[0]);
        balance = Double.parseDouble(parts[1]);
        price = Double.parseDouble(parts[2]);
        success = true;
    }

    public double Value() {
        return price * balance;
    }

    public String ToString() {
        return coin.name() + "," + String.valueOf(balance) + "," + String.valueOf(price);
    }
}

class BalanceAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    public BalanceAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return Balance.listings.size();
    }

    @Override
    public Object getItem(int index) {
        return Balance.listings.values().toArray()[index];
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        // get the balance associated with this listview item
        ExchangeListing listing = (ExchangeListing) getItem(index);

         // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_balance, parent, false);

        // set the icon of the listview item
        int icon = R.drawable.btc;
        if (listing.coin == Coin.LTC) {
            icon = R.drawable.ltc;
        } else if (listing.coin == Coin.ETH) {
            icon = R.drawable.eth;
        } else if (listing.coin == Coin.BCH) {
            icon = R.drawable.bch;
        } else if (listing.coin == Coin.ARK) {
            icon = R.drawable.ark;
        } else if (listing.coin == Coin.VTC) {
            icon = R.drawable.vtc;
        } else if (listing.coin == Coin.XMR) {
            icon = R.drawable.xmr;
        } else if (listing.coin == Coin.ZEC) {
            icon = R.drawable.zec;
        } else if (listing.coin == Coin.IOTA) {
            icon = R.drawable.iota;
        } else if (listing.coin == Coin.XRP) {
            icon = R.drawable.xrp;
        } else if (listing.coin == Coin.DASH) {
            icon = R.drawable.dash;
        } else if (listing.coin == Coin.NEO) {
            icon = R.drawable.neo;
        }
        ((ImageView) view.findViewById(R.id.coinIcon)).setImageResource(icon);

        // set all the text of the listview item
        ((TextView) view.findViewById(R.id.coinName)).setText(listing.coin.name());
        ((TextView) view.findViewById(R.id.coinPrice)).setText("$" + String.format("%1$,.2f", listing.price));
        if (!listing.success) {
            ((TextView) view.findViewById(R.id.coinPrice)).setTextColor(Color.RED);
        }
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.valueOf(listing.balance));
        ((TextView) view.findViewById(R.id.coinValue)).setText("$" + String.format("%1$,.2f", listing.Value()));
        double holdingPercentage = 0;
        if (Balance.totalValue > 0) {
            holdingPercentage = 100 * listing.Value() / Balance.totalValue;
        }
        ((TextView) view.findViewById(R.id.holdingPercent)).setText(String.format("%1$,.2f", holdingPercentage) + "%");

        // don't display item if balance is 0
//        if (listing.balance == 0) {
//            return inflater.inflate(R.layout.list_item_null, null);
//        }
        return view;
    }
}

public class Balance extends AppCompatActivity {
    public static BalanceAdapter balanceAdapter = null;
    static int SelectedIndex = 0;
    public static Map<Coin, ExchangeListing> listings = new LinkedHashMap<>();
    public static double totalValue = 0;

    public static void Load(Context context) {
        // create balance for each coin type
        for (Coin coin : Coin.values()) {
            listings.put(coin, new ExchangeListing(coin));
        }

        // load balances from file
        try {
            InputStream inputStream = context.openFileInput("listings.txt");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = "";
                while ((line = bufferedReader.readLine()) != null ) {
                    ExchangeListing listing = new ExchangeListing(line);
                    listings.put(listing.coin, listing);
                }
                inputStream.close();
            }
        }
        catch (Exception e) {
            Log.e("BalancesLoad", e.toString());
        }
    }

    public static void Save(Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("listings.txt", Context.MODE_PRIVATE));
            for (ExchangeListing listing : listings.values()) {
                outputStreamWriter.write(listing.ToString() + "\n");
            }
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("BalancesSave", e.toString());
        }
    }

    public void LookupSelected() {
        ExchangeListing listing = (ExchangeListing) Balance.listings.values().toArray()[SelectedIndex];

        String coinName = "bitcoin";
        if (listing.coin == Coin.LTC) {
            coinName = "litecoin";
        } else if (listing.coin == Coin.ETH) {
            coinName = "ethereum";
        } else if (listing.coin == Coin.BCH) {
            coinName = "bitcoin-cash";
        } else if (listing.coin == Coin.ARK) {
            coinName = "ark";
        } else if (listing.coin == Coin.VTC) {
            coinName = "vertcoin";
        } else if (listing.coin == Coin.XMR) {
            coinName = "monero";
        } else if (listing.coin == Coin.ZEC) {
            coinName = "zcash";
        } else if (listing.coin == Coin.IOTA) {
            coinName = "iota";
        } else if (listing.coin == Coin.XRP) {
            coinName = "ripple";
        } else if (listing.coin == Coin.DASH) {
            coinName = "dash";
        }

        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://coinmarketcap.com/currencies/" + coinName + "/"));
        startActivity(intent);
    }

    public void CloseBalanceEditBox() {
        // disable the balance list
        findViewById(R.id.balancesList).setEnabled(true);

        // disabled the refresh button
        findViewById(R.id.refreshButton).setEnabled(true);

        // hide the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);

        // close keyboard
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void EditSelected() {
        ExchangeListing listing = (ExchangeListing) Balance.listings.values().toArray()[SelectedIndex];

        // set the edit title and erase the balance input text
        ((TextView) findViewById(R.id.editTitle)).setText(listing.coin.name() + " Balance");
        ((TextView) findViewById(R.id.balanceInput)).setText("");

        // disable the balance list
        findViewById(R.id.balancesList).setEnabled(false);

        // disabled the refresh button
        findViewById(R.id.refreshButton).setEnabled(false);

        // show the edit box
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.VISIBLE);

        // show keyboard for balance input
        EditText editText = (EditText) findViewById(R.id.balanceInput);
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    public void SaveBalance() {
        // update listing with new balance
        ExchangeListing listing = (ExchangeListing) Balance.listings.values().toArray()[SelectedIndex];
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();
        listing.balance = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

        // Close balance box
        CloseBalanceEditBox();

        // update listings display
        UpdateDisplay();
    }

    public void CancelEdit(View v) {
        findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);
    }

    public void Refresh(View v) {
        Update();
    }

    void UpdatePrices() {
        try {
            ArrayList<String> coins = new ArrayList<>();
            for (Coin coin : Coin.values()) {
                coins.add(coin.name());
            }
            String coinsString = TextUtils.join(",", coins);
            URL url = new URL("https://min-api.cryptocompare.com/data/pricemulti?fsyms=" + coinsString + "&tsyms=USD");
            InputStream inputStream = url.openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String jsonString = "";
            String line;
            while ((line = in.readLine()) != null) {
                jsonString += line;
            }
            in.close();
            JSONObject result = new JSONObject(jsonString);
            for (Coin coin : Coin.values()) {
                if (listings.containsKey(coin)) {
                    listings.get(coin).price = result.getJSONObject(coin.name()).getDouble("USD");
                    listings.get(coin).success = true;
                }
            }

            // get iota price from special location
            url = new URL("https://api.coinmarketcap.com/v1/ticker/iota/");
            inputStream = url.openStream();
            in = new BufferedReader(new InputStreamReader(inputStream));
            jsonString = "";
            while ((line = in.readLine()) != null) {
                jsonString += line;
            }
            result = new JSONObject(jsonString.substring(2, jsonString.length() - 1));
            if (listings.containsKey(Coin.IOTA)) {
                listings.get(Coin.IOTA).price = result.getDouble("price_usd");
                listings.get(Coin.IOTA).success = true;
            }

        } catch (Exception e) {
            Log.e("UpdatePrices", e.toString());
            for (Coin coin : Coin.values()) {
                if (listings.containsKey(coin)) {
                    listings.get(coin).success = false;
                }
            }
        }
    }

    void UpdateDisplay() {
        // save data
        Save(this);

        // Update total value
        totalValue = 0;
        for (Coin coin : Coin.values()) {
            totalValue += listings.get(coin).Value();
        }
        ((TextView) findViewById(R.id.valueTotal)).setText("$" + String.format("%1$,.2f", totalValue));

        // update list views
        balanceAdapter.notifyDataSetChanged();
    }

    void RecordUpdateTime() {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("last_update.txt", Context.MODE_PRIVATE));
            Date now = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String last_update = df.format(now);
            outputStreamWriter.write(last_update);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("RecordUpdateTime", e.toString());
        }
    }

    boolean ShouldUpdate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date last_update;

        //read last update time
        try {
            InputStream inputStream = this.openFileInput("last_update.txt");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                last_update = df.parse(bufferedReader.readLine());
                inputStream.close();
            } else {
                return true;
            }
        }
        catch (Exception e) {
            return true;
        }

        Date next_update = new Date(last_update.getTime() + 60000);
        Date now = Calendar.getInstance().getTime();
        if (now.after(next_update)) {
            return true;
        }
        return false;
    }

    void Update() {
        RecordUpdateTime();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                UpdatePrices();
                return null;
            }

            @Override
            protected void onPostExecute(Void empty) {
                super.onPostExecute(empty);
                UpdateDisplay();
            }
        }.execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_balance);

        // load data from file
        Load(this);

        // setup balances list
        ListView listView = (ListView) findViewById(R.id.balancesList);
        balanceAdapter = new BalanceAdapter(this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedIndex = position;
                LookupSelected();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedIndex = position;
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDisplay();
        if (ShouldUpdate()) {
            Update();
        }
    }

//    @Override
//    public void onBackPressed() {
//        // if edit box is open then hide it instead of exit
//        if (findViewById(R.id.editBalanceBox).getVisibility() == FrameLayout.VISIBLE) {
//            findViewById(R.id.editBalanceBox).setVisibility(FrameLayout.GONE);
//            return;
//        }
//
//        super.onBackPressed();
//    }
}
