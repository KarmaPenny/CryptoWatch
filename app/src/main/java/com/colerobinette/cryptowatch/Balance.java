package com.colerobinette.cryptowatch;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

class ExchangeListing {
    public Coin coin;
    public double price;
    public boolean success;

    public ExchangeListing(Coin coin) {
        this.coin = coin;
        this.price = 0;
        success = true;
    }

    public ExchangeListing(String fromString) {
        String[] parts = fromString.split("-");
        coin = Coin.valueOf(parts[0]);
        price = Double.parseDouble(parts[1]);
        success = true;
    }

    public String ToString() {
        return coin.name() + "-" + String.valueOf(price);
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
        ExchangeListing listing = (ExchangeListing) Balance.listings.values().toArray()[index];

         // get the view associated with this listview item
        View view = inflater.inflate(R.layout.list_item_balance, parent, false);

        // set the icon of the listview item
        int icon = R.drawable.bitcoin;
        if (listing.coin == Coin.LTC) {
            icon = R.drawable.litecoin;
        } else if (listing.coin == Coin.ETH) {
            icon = R.drawable.ether;
        }
        ((ImageView) view.findViewById(R.id.coinIcon)).setImageResource(icon);

        // set all the text of the listview item
        ((TextView) view.findViewById(R.id.coinName)).setText(listing.coin.name());
        ((TextView) view.findViewById(R.id.coinPrice)).setText("$" + String.format("%1$,.2f", listing.price));
        if (!listing.success) {
            ((TextView) view.findViewById(R.id.coinPrice)).setTextColor(Color.RED);
        }
        double coins = Accounts.Balance(listing.coin);
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.valueOf(coins));
        ((TextView) view.findViewById(R.id.coinValue)).setText("$" + String.format("%1$,.2f", coins * listing.price));

        return view;
    }
}

public class Balance extends AppCompatActivity {

    static BalanceAdapter adapter = null;

    public static Map<Coin, ExchangeListing> listings = new LinkedHashMap<>();

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

    void UpdatePrices() {
        try {
            ArrayList<String> coins = new ArrayList<>();
            for (Coin coin : Coin.values()) {
                coins.add(coin.name());
            }
            String coinsString = TextUtils.join(",", coins);
            URL url = new URL("https://min-api.cryptocompare.com/data/pricemulti?fsyms=" + coinsString + "&tsyms=USD&e=Coinbase");
            InputStream inputStream = url.openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String jsonString = "";
            String line;
            while ((line = in.readLine()) != null) {
                jsonString += line;
            }
            JSONObject result = new JSONObject(jsonString);
            for (Coin coin : Coin.values()) {
                if (listings.containsKey(coin)) {
                    listings.get(coin).price = result.getJSONObject(coin.name()).getDouble("USD");
                    listings.get(coin).success = true;
                }
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
        Accounts.Save(this);
        Save(this);

        // update list views
        adapter.notifyDataSetChanged();
        if (Accounts.adapter != null) {
            Accounts.adapter.notifyDataSetChanged();
        }

        // update total value
        double totalValue = 0;
        for (Coin coin : Coin.values()) {
            totalValue += listings.get(coin).price * Accounts.Balance(coin);
        }
        ((TextView) findViewById(R.id.valueTotal)).setText("$" + String.format("%1$,.2f", totalValue));
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

        Date next_update = new Date(last_update.getTime() + 180000);
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
                Accounts.Update();
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

        Balance.Load(this);
        Accounts.Load(this);

        ListView listView = (ListView) findViewById(R.id.balancesList);
        adapter = new BalanceAdapter(this);
        listView.setAdapter(adapter);

        UpdateDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ShouldUpdate()) {
            Update();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_balance, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            Update();
            return true;
        } else if (id == R.id.action_accounts) {
            startActivity(new Intent(this, Accounts.class));
        } else if (id == R.id.action_donate) {
            startActivity(new Intent(this, Donate.class));
        }

        return super.onOptionsItemSelected(item);
    }
}
