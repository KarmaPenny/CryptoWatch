package com.colerobinette.cryptowatch;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

public class AddAccount extends AppCompatActivity {
    boolean clicked = false;

    public void OnAddButtonClick(View v) {
        // prevent double adding
        if (!clicked) {
            clicked = true;

            // add new account to accounts list
            TextView addressText = (TextView) findViewById(R.id.addressInput);
            Spinner coinSpinner = (Spinner) findViewById(R.id.coinSpinner);
            String address = addressText.getText().toString();
            if (address.startsWith("0x")) {
                address = address.substring(2);
            }
            String coin = coinSpinner.getSelectedItem().toString().substring(0,3);
            CryptoAccount account = new CryptoAccount(Coin.valueOf(coin), address);
            Balance.accounts.put(account.coin.name() + "-" + account.address, account);

            Balance.Save(this);

            new AsyncTask<CryptoAccount, Void, Void>() {
                @Override
                protected Void doInBackground(CryptoAccount... params) {
                    params[0].Update();
                    return null;
                }

                @Override
                protected void onPostExecute(Void empty) {
                    super.onPostExecute(empty);
                    finish();
                }
            }.execute(account);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);
    }
}
