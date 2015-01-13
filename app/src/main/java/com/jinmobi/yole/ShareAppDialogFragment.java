package com.jinmobi.yole;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.widget.Toast;


/**
 * A dialog fragment to display a QR Code for app in Google Play
 */
public class ShareAppDialogFragment extends DialogFragment {
    public static final String GOOGLEPLAYSTORE_APP_URL
            = "http://play.google.com/store/apps/details?id=com.google.android.apps.maps";


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder((getActivity()));
        LayoutInflater inflater = getActivity().getLayoutInflater();
        alertDialogBuilder = alertDialogBuilder.setView(
                inflater.inflate(R.layout.dialog_share_app, null));


        alertDialogBuilder = alertDialogBuilder.setNegativeButton(R.string.button_dismiss,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                });
        alertDialogBuilder = alertDialogBuilder.setPositiveButton(R.string.button_share_app,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // start activity to send email to friend to download app in Play Store
                        //  must use SENDTO, not SEND
                        Intent intentShare = new Intent(Intent.ACTION_SENDTO);
                        //  specify mailto: data scheme so only email apps should handle
                        //  don't set type text/plain or html, otherwise likely no app triggered
                        intentShare.setData(Uri.parse("mailto:"));

                        String appName = getResources().getString(R.string.app_name);
                        intentShare.putExtra(Intent.EXTRA_SUBJECT,
                                "Download " + appName + " appfrom Google Play Store");
                        String link = "<a href=" + GOOGLEPLAYSTORE_APP_URL + ">"
                                + appName + "</a>";
                        intentShare.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(link));

                        if (intentShare.resolveActivity(getActivity().getPackageManager()) != null)
                            startActivity(intentShare);
                        else
                            Toast.makeText(getActivity(), "No installed apps found to send message",
                                    Toast.LENGTH_LONG).show();

                        dismiss();
                    }
                });

        return alertDialogBuilder.create();
    }
}
