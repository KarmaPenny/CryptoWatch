package com.colerobinette.cryptowatch;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;

enum Coin {BTC, ETH, LTC}

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
        return Accounts.accounts.size();
    }

    @Override
    public Object getItem(int index) {
        return Accounts.accounts.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        View view = inflater.inflate(R.layout.list_item_account, parent, false);

        CryptoAccount account = Accounts.accounts.get(index);

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

public class Accounts extends AppCompatActivity {
    static ArrayList<CryptoAccount> accounts = new ArrayList<CryptoAccount>();
    static int SelectedIndex = 0;
    public static AccountAdapter adapter = null;

    public static void Load(Context context) {
        accounts.clear();
        try {
            InputStream inputStream = context.openFileInput("accounts.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = "";
                while ( (line = bufferedReader.readLine()) != null ) {
                    CryptoAccount account = new CryptoAccount(line);
                    accounts.add(account);
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
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("accounts.txt", Context.MODE_PRIVATE));
            for (CryptoAccount account : accounts) {
                outputStreamWriter.write(account.ToString() + "\n");
            }
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("AccountsSave", e.toString());
        }
    }

    public void DeleteSelected() {
        if (SelectedIndex < accounts.size()) {
            accounts.remove(SelectedIndex);
            adapter.notifyDataSetChanged();
            Save(this);
        }
    }

    public void CopySelected() {
        if (SelectedIndex < accounts.size()) {
            CryptoAccount account = accounts.get(SelectedIndex);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(account.coin.name() + "_Address", account.address));
        }
    }

    public static double Balance(Coin coin) {
        double coins = 0;
        for (CryptoAccount account : accounts) {
            if (account.coin == coin) {
                coins += account.balance;
            }
        }
        return coins;
    }

    public static void Update() {
        for (CryptoAccount account : accounts) {
            account.Update();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);

        final ClickableListView listView = (ClickableListView) findViewById(R.id.accountsList);
        adapter = new AccountAdapter(this);

        listView.setOnNoItemClickListener(new ClickableListView.OnNoItemClickListener() {
            @Override
            public void onNoItemClicked() {
                startActivity(new Intent(getApplicationContext(), AddAccount.class));
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedIndex = position;

                PopupMenu popup = new PopupMenu(Accounts.this, v);
                popup.getMenuInflater().inflate(R.menu.menu_list_item_account, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();

                        if (id == R.id.deleteButton) {
                            DeleteSelected();
                        } else if (id == R.id.copyButton) {
                            CopySelected();
                        } else if (id == R.id.addButton) {
                            startActivity(new Intent(getApplicationContext(), AddAccount.class));
                        }

                        return true;
                    }
                });

                popup.show();
            }
        });

        listView.setAdapter(adapter);
    }
}
