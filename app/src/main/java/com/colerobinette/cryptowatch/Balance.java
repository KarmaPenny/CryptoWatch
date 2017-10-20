package com.colerobinette.cryptowatch;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TabHost;
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

enum Coin {BTC, BCH, ETH, LTC}

class CryptoAccount {
    public String address;
    public Coin coin;
    public double balance;
    public boolean success;

    public CryptoAccount(Coin coin, String address) {
        this.address = address;
        this.coin = coin;
        this.balance = 0;
        this.success = true;
    }

    public CryptoAccount(String fromString) {
        String[] parts = fromString.split("-");
        coin = Coin.valueOf(parts[0]);
        address = parts[1];
        balance = Double.parseDouble(parts[2]);
        success = true;
    }

    public double Standardize(double value) {
        if (coin == Coin.ETH) {
            return value / 1000000000000000000d;
        }
        return value / 100000000d;
    }

    public String ToString() {
        return coin.name() + "-" + address + "-" + String.valueOf(balance);
    }

    void Update() {
        try {
            URL url = new URL("https://api.blockcypher.com/v1/" + coin.name().toLowerCase() + "/main/addrs/" + address + "/balance");
            if (coin == Coin.BCH) {
                url = new URL("https://api.blocktrail.com/v1/bcc/address/" + address + "?api_key=a8e039afc1af0be708c4cda1db00703508c39bfc");
            }
            InputStream inputStream = url.openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String jsonString = "";
            String line;
            while ((line = in.readLine()) != null) {
                jsonString += line;
            }
            JSONObject result = new JSONObject(jsonString);
            balance = Standardize(result.getDouble("balance"));
            success = true;
        } catch (Exception e) {
            Log.e("UpdateAccount", e.toString());
            success = false;
        }
    }
}

class AccountAdapter extends BaseAdapter {
    private LayoutInflater inflater;

    public AccountAdapter(Context context) {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return Balance.accounts.size();
    }

    @Override
    public Object getItem(int index) {
        return Balance.accounts.values().toArray()[index];
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        View view = inflater.inflate(R.layout.list_item_account, parent, false);

        CryptoAccount account = (CryptoAccount) getItem(index);

        TextView coinText = (TextView) view.findViewById(R.id.coinText);
        coinText.setText(account.coin.name());
        TextView addressText = (TextView) view.findViewById(R.id.address);
        if (!account.success) {
            addressText.setTextColor(Color.RED);
        }
        addressText.setText(account.address);

        return view;
    }
}

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
        ExchangeListing listing = (ExchangeListing) getItem(index);

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
        double coins = Balance.Balance(listing.coin);
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.valueOf(coins));
        ((TextView) view.findViewById(R.id.coinValue)).setText("$" + String.format("%1$,.2f", coins * listing.price));

        if (coins == 0) {
            return inflater.inflate(R.layout.list_item_null, null);
        }
        return view;
    }
}

public class Balance extends AppCompatActivity {
    public static BalanceAdapter balanceAdapter = null;
    public static AccountAdapter accountAdapter = null;
    static int SelectedIndex = 0;
    public static Map<String, CryptoAccount> accounts = new LinkedHashMap<>();
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

        // load accounts from file
        try {
            InputStream inputStream = context.openFileInput("accounts.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = "";
                while ( (line = bufferedReader.readLine()) != null ) {
                    CryptoAccount account = new CryptoAccount(line);
                    accounts.put(account.coin.name() + "-" + account.address, account);
                }

                inputStream.close();
            }
        }
        catch (Exception e) {
            Log.e("AccountsLoad", e.toString());
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

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("accounts.txt", Context.MODE_PRIVATE));
            for (CryptoAccount account : accounts.values()) {
                outputStreamWriter.write(account.ToString() + "\n");
            }
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("AccountsSave", e.toString());
        }
    }

    public static double Balance(Coin coin) {
        double coins = 0;
        for (CryptoAccount account : accounts.values()) {
            if (account.coin == coin) {
                coins += account.balance;
            }
        }
        return coins;
    }

    public void DeleteSelected() {
        if (SelectedIndex < accounts.size()) {
            CryptoAccount account = (CryptoAccount) Balance.accounts.values().toArray()[SelectedIndex];
            accounts.remove(account.coin.name() + "-" + account.address);
            UpdateDisplay();
        }
    }

    public void CopySelected() {
        if (SelectedIndex < accounts.size()) {
            CryptoAccount account = (CryptoAccount) Balance.accounts.values().toArray()[SelectedIndex];
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(account.coin.name() + "_Address", account.address));
        }
    }

    public void AddAccount(View v) {
        startActivity(new Intent(this, AddAccount.class));
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
        Save(this);

        // update list views
        balanceAdapter.notifyDataSetChanged();
        accountAdapter.notifyDataSetChanged();;

        // update total value
        double totalValue = 0;
        for (Coin coin : Coin.values()) {
            totalValue += listings.get(coin).price * Balance(coin);
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
                for (CryptoAccount account : accounts.values()) {
                    account.Update();
                }
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

        // setup tabs
        TabHost host = (TabHost)findViewById(R.id.tabHost);
        host.setup();

        TabHost.TabSpec spec = host.newTabSpec("Balances");
        spec.setContent(R.id.balanceTab);
        spec.setIndicator("Balances");
        host.addTab(spec);

        spec = host.newTabSpec("Addresses");
        spec.setContent(R.id.addressesTab);
        spec.setIndicator("Addresses");
        host.addTab(spec);

        // setup balances list
        ListView listView = (ListView) findViewById(R.id.balancesList);
        balanceAdapter = new BalanceAdapter(this);
        listView.setAdapter(balanceAdapter);

        // setup addresses list
        listView = (ListView) findViewById(R.id.accountsList);
        accountAdapter = new AccountAdapter(this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedIndex = position;

                PopupMenu popup = new PopupMenu(Balance.this, v);
                popup.getMenuInflater().inflate(R.menu.menu_list_item_account, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();

                        if (id == R.id.deleteButton) {
                            DeleteSelected();
                        } else if (id == R.id.copyButton) {
                            CopySelected();
                        }

                        return true;
                    }
                });

                popup.show();
            }
        });

        listView.setAdapter(accountAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDisplay();
        if (ShouldUpdate()) {
            Update();
        }
    }
}
