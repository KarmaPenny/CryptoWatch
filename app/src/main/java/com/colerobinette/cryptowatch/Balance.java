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

class ExchangeListing {
    public String name;
    public String symbol;
    public double change24h;
    public double balance;
    public double price;

    public ExchangeListing() {
        this.name = "";
        this.symbol = "";
        this.balance = 0;
        this.price = 0;
    }

    public ExchangeListing(String fromString) {
        String[] parts = fromString.split(",");
        name = parts[0];
        symbol = parts[1];
        change24h = Double.parseDouble(parts[2]);
        balance = Double.parseDouble(parts[3]);
        price = Double.parseDouble(parts[4]);
    }

    public void Update() {
        try {
            // get iota price from special location
            URL url = new URL("https://api.coinmarketcap.com/v1/ticker/" + name + "/");
            InputStream inputStream = url.openStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String jsonString = "";
            String line;
            while ((line = in.readLine()) != null) {
                jsonString += line;
            }
            JSONObject result = new JSONObject(jsonString.substring(2, jsonString.length() - 1));
            in.close();

            price = result.getDouble("price_usd");
            change24h = result.getDouble("percent_change_24h");
            symbol = result.getString("symbol");

            File file = new File(name + ".png");
            if (!file.exists()) {
                URL iconUrl = new URL("https://files.coinmarketcap.com/static/img/coins/128x128/" + name + ".png");
                InputStream in2 = new BufferedInputStream(iconUrl.openStream());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n = 0;
                while (-1 != (n = in2.read(buf))) {
                    out.write(buf, 0, n);
                }
                out.close();
                in.close();
                byte[] response = out.toByteArray();

                FileOutputStream fos = new FileOutputStream(Balance.actContext.getApplicationInfo().dataDir + "/" + name + ".png");
                fos.write(response);
                fos.close();
            }
        } catch (Exception e) {
            Log.e("UpdatePrices", e.toString());
        }
    }

    public double Value() {
        return price * balance;
    }

    public String ToString() {
        return name + "," + symbol + "," + String.valueOf(change24h) + "," + String.valueOf(balance) + "," + String.valueOf(price);
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
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + listing.name + ".png");
        Uri uri = Uri.fromFile(file);
        ((ImageView) view.findViewById(R.id.coinIcon)).setImageURI(uri);

        // set all the text of the listview item
        ((TextView) view.findViewById(R.id.coinName)).setText(listing.symbol);
        ((TextView) view.findViewById(R.id.coinPrice)).setText("$" + String.format("%1$,.2f", listing.price));
        ((TextView) view.findViewById(R.id.coinBalance)).setText(String.valueOf(listing.balance));
        ((TextView) view.findViewById(R.id.coinValue)).setText("$" + String.format("%1$,.2f", listing.Value()));

        TextView change = (TextView) view.findViewById(R.id.percentChange);
        if (listing.change24h > 0) {
            change.setText("+" + String.format("%1$,.2f", listing.change24h) + "%");
            change.setTextColor(Color.rgb(0, 150, 0));
        } else if (listing.change24h < 0) {
            change.setText(String.format("%1$,.2f", listing.change24h) + "%");
            change.setTextColor(Color.RED);
        } else {
            change.setText("+" + String.format("%1$,.2f", listing.change24h) + "%");
            change.setTextColor(Color.BLACK);
        }

        return view;
    }
}

public class Balance extends Activity {
    public static BalanceAdapter balanceAdapter = null;
    static String SelectedCoin = "";
    public static Map<String, ExchangeListing> listings = new LinkedHashMap<>();
    public static double totalValue = 0;
    public static Context actContext;

    public static String sortBy = "coin";
    public static boolean reverseSort = false;
    public static boolean paused = false;

    public static void Load(Context context) {
        try {
            // load balances from file
            InputStream inputStream = context.openFileInput("listings.txt");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = "";
                while ((line = bufferedReader.readLine()) != null ) {
                    ExchangeListing listing = new ExchangeListing(line);
                    listings.put(listing.name, listing);
                }
                inputStream.close();
            }

            // load sort settings
            inputStream = context.openFileInput("sort.txt");
            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line = bufferedReader.readLine();

                String[] parts = line.split(",");
                sortBy = parts[0];
                reverseSort = Boolean.parseBoolean(parts[1]);

                inputStream.close();
            }
        }
        catch (Exception e) {
            Log.e("BalancesLoad", e.toString());
        }
    }

    public static void Save(Context context) {
        try {
            // save balances to file
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("listings.txt", Context.MODE_PRIVATE));
            for (ExchangeListing listing : listings.values()) {
                outputStreamWriter.write(listing.ToString() + "\n");
            }
            outputStreamWriter.close();

            // save sort settings to file
            outputStreamWriter = new OutputStreamWriter(context.openFileOutput("sort.txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(sortBy + "," + reverseSort);
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
        ExchangeListing listing = listings.get(SelectedCoin);
        Intent intent= new Intent(Intent.ACTION_VIEW, Uri.parse("https://coinmarketcap.com/currencies/" + listing.name + "/"));
        startActivity(intent);
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

    public void EnterCoinName(View v) {
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
        // update listing with new balance
        String coinName = ((TextView) findViewById(R.id.coinInput)).getText().toString().replace(" ", "-");
        if (!listings.containsKey(coinName)) {
            ExchangeListing listing = new ExchangeListing();
            listing.name = coinName;
            listings.put(coinName, listing);
        }

        // Close balance box
        CloseNewCoinBox();

        // update listings
        Update(coinName);

        // prompt for balance input
        SelectedCoin = coinName;
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
        ExchangeListing listing = listings.get(SelectedCoin);
        File file = new File(Balance.actContext.getApplicationInfo().dataDir + "/" + listing.name + ".png");
        if (file.exists()) {
            file.delete();
        }
        listings.remove(listing.name);

        CloseBalanceEditBox();

        UpdateDisplay();
    }

    public void EditSelected() {
        ExchangeListing listing = listings.get(SelectedCoin);

        // set the edit title and erase the balance input text
        ((TextView) findViewById(R.id.editTitle)).setText("Enter " + listing.name.toUpperCase() + " Holdings");
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
        // update listing with new balance
        ExchangeListing listing = listings.get(SelectedCoin);
        String balance = ((TextView) findViewById(R.id.balanceInput)).getText().toString();
        listing.balance = (balance.isEmpty()) ? 0 : Double.parseDouble(balance);

        // Close balance box
        CloseBalanceEditBox();

        // update listings display
        UpdateDisplay();
    }

    public void Refresh(View v) {
        Update();
    }

    public void SortListings() {
        Comparator comparator = new Comparator<Map.Entry<String, ExchangeListing>>() {
            public int compare(Map.Entry<String, ExchangeListing> o1, Map.Entry<String, ExchangeListing> o2) {
                if (reverseSort) {
                    return (o2.getValue().name).compareTo(o1.getValue().name);
                }
                return (o1.getValue().name).compareTo(o2.getValue().name);
            }
        };
        if (sortBy.equals("holdings")) {
            comparator = new Comparator<Map.Entry<String, ExchangeListing>>() {
                public int compare(Map.Entry<String, ExchangeListing> o1, Map.Entry<String, ExchangeListing> o2) {
                    if(o1.getValue().Value() > o2.getValue().Value())
                        return (reverseSort) ? 1 : -1;
                    else if(o1.getValue().Value() < o2.getValue().Value())
                        return (reverseSort) ? -1 : 1;
                    return 0;
                }
            };
        } else if (sortBy.equals("price")) {
            comparator = new Comparator<Map.Entry<String, ExchangeListing>>() {
                public int compare(Map.Entry<String, ExchangeListing> o1, Map.Entry<String, ExchangeListing> o2) {
                    if(o1.getValue().change24h > o2.getValue().change24h)
                        return (reverseSort) ? 1 : -1;
                    else if(o1.getValue().change24h < o2.getValue().change24h)
                        return (reverseSort) ? -1 : 1;
                    return 0;
                }
            };
        }

        List<Map.Entry<String, ExchangeListing>> list = new LinkedList<>(listings.entrySet());
        Collections.sort(list, comparator);

        Map<String, ExchangeListing> result = new LinkedHashMap<>();
        for (Map.Entry<String, ExchangeListing> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        listings = result;
    }

    public void SortByValue(String value) {
        if (sortBy == value) {
            reverseSort = !reverseSort;
        } else {
            sortBy = value;
            reverseSort = false;
        }
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

    void UpdatePrices() {
        for (ExchangeListing listing : listings.values()) {
            listing.Update();
        }
    }

    void UpdateDisplay() {
        // resort listings
        SortListings();

        // save data
        Save(this);

        // Update total value
        totalValue = 0;
        double previousValue = 0;
        for (ExchangeListing listing : listings.values()) {
            totalValue += listing.Value();
            previousValue += listing.Value() / (1 + (listing.change24h / 100));
        }
        ((TextView) findViewById(R.id.valueTotal)).setText(String.format("%1$,.2f", totalValue));

        // Update 24 hour change
        TextView change = (TextView) findViewById(R.id.change24);
        if (previousValue <= 0) {
            change.setText("+" + String.format("%1$,.2f", 0) + "%");
            change.setTextColor(Color.BLACK);
        } else {
            double change24 = 100 * (totalValue - previousValue) / previousValue;
            if (change24 > 0) {
                change.setText("+" + String.format("%1$,.2f", change24) + "%");
                change.setTextColor(Color.rgb(0, 150, 0));
            } else if (change24 < 0) {
                change.setText(String.format("%1$,.2f", change24) + "%");
                change.setTextColor(Color.RED);
            } else {
                change.setText("+" + String.format("%1$,.2f", change24) + "%");
                change.setTextColor(Color.BLACK);
            }
        }

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

    void Update(final String coinName) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                listings.get(coinName).Update();
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
        }, 60000);
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
                SelectedCoin = ((ExchangeListing) listings.values().toArray()[position]).name;
                LookupSelected();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapter, View v, int position, long id) {
                SelectedCoin = ((ExchangeListing) listings.values().toArray()[position]).name;
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
