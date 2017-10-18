package com.colerobinette.cryptowatch;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

public class AddAccount extends AppCompatActivity {

    public void OnAddButtonClick(View v) {
        TextView addressText = (TextView) findViewById(R.id.addressInput);
        Spinner coinSpinner = (Spinner) findViewById(R.id.coinSpinner);

        String address = addressText.getText().toString();
        if (address.startsWith("0x")) {
            address = address.substring(2);
        }
        String coin = coinSpinner.getSelectedItem().toString();

        CryptoAccount account = new CryptoAccount(Coin.valueOf(coin), address);
        Accounts.accounts.add(account);
        Accounts.Save(this);

        new AsyncTask<CryptoAccount, Void, Void>() {
            @Override
            protected Void doInBackground(CryptoAccount... params) {
                params[0].Update();
                return null;
            }

            @Override
            protected void onPostExecute(Void empty) {
                super.onPostExecute(empty);
                Accounts.adapter.notifyDataSetChanged();
                finish();
            }
        }.execute(account);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);
    }
}
