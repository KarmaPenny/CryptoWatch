package com.colerobinette.cryptowatch;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class Donate extends AppCompatActivity {
    public void CopyBTC() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("donate_btc", "3KpH3d7eBwWNihMj9Z64qNG1sjj1mLFfom"));
    }

    public void CopyLTC() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("donate_btc", "LRAXnHBzDwjQpaebBykoK87WB8XtMsEtD8"));
    }

    public void CopyETH() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("donate_btc", "0x013f07D2ad0dbA2F67800b223f78a56E616961F9"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donate);
    }
}
