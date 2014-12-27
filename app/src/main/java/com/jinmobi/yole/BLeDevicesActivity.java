package com.jinmobi.yole;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.Toolbar;

import com.lorentzos.flingswipe.SwipeFlingAdapterView;

import java.util.ArrayList;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class BLeDevicesActivity extends Activity {

    private static final int MAX_VISIBLE = 2; // so at most 2 cards are 'Empty'

    private ArrayList<String> al;
    private ArrayAdapter<String> arrayAdapter;

    /**
     *  We use the excellent Swipecards library which is Copyright 2014 Dionysis Lorentzos.
     *  Licensed under the Apache License, Version 2.0.
     *
     *  Downloaded from https://github.com/Diolor/Swipecards
     */
    @InjectView(R.id.frame) SwipeFlingAdapterView flingContainer;

    @InjectView(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_devices);
        ButterKnife.inject(this);

        // for navigation button set in layout
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //no op for now
            }
        });
        toolbar.setNavigationContentDescription("No Op");

        setActionBar(toolbar);

        // Fling Swipecards
        al = new ArrayList<>();
        al.add("php");
//        al.add("c");
//        al.add("python");
//        al.add("java");
        arrayAdapter = new ArrayAdapter<>(this, R.layout.item, R.id.itemText, al );

        flingContainer.setMaxVisible(MAX_VISIBLE);
        flingContainer.setAdapter(arrayAdapter);
        flingContainer.setFlingListener(new SwipeFlingAdapterView.onFlingListener() {
            @Override
            public void removeFirstObjectInAdapter() {
                // this is the simplest way to delete an object from the Adapter (/AdapterView)
                Log.d("LIST", "removed object!");
                al.add(al.size(), al.get(0)); // clk: add first object to end before removing
                al.remove(0);
                arrayAdapter.notifyDataSetChanged();
            }

            @Override
            public void onLeftCardExit(Object dataObject) {
                //Do something on the left!
                //You also have access to the original object.
                //If you want to use it just cast it (String) dataObject
                //makeToast(MyActivity.this, "Left!");
            }

            @Override
            public void onRightCardExit(Object dataObject) {
                //makeToast(MyActivity.this, "Right!");
            }

            @Override
            public void onAdapterAboutToEmpty(int itemsInAdapter) {
                // Ask for more data here

                String EMPTY = getResources().getString(R.string.empty);

                // clk: For now, a hack around seen current behavior of the library so that
                // it would show a second card underneath first if itemsInAdapter is 0.
                // TODO: look at fixing the library if possible
                if (itemsInAdapter == 0) {
                    al.add(EMPTY);
                    Log.d("LIST", "notified 0");
                }

                // add an 'Empty' card
                al.add(EMPTY);
                arrayAdapter.notifyDataSetChanged();
                Log.d("LIST", "notified");
            }

            @Override
            public void onScroll(float scrollProgressPercent) {
                View view = flingContainer.getSelectedView();
                if (view == null) return; // clk: to avoid NPE seen in flinging cards speed 'stress' test
                view.findViewById(R.id.item_swipe_right_indicator).setAlpha(scrollProgressPercent < 0 ? -scrollProgressPercent : 0);
                view.findViewById(R.id.item_swipe_left_indicator).setAlpha(scrollProgressPercent > 0 ? scrollProgressPercent : 0);
            }
        });

        // Optionally add an OnItemClickListener
        flingContainer.setOnItemClickListener(new SwipeFlingAdapterView.OnItemClickListener() {
            @Override
            public void onItemClicked(int itemPosition, Object dataObject) {
                if ("Empty".equals(dataObject)) makeToast(BLeDevicesActivity.this, "Clicked on Empty!");
                else makeToast(BLeDevicesActivity.this, "Clicked!");
            }
        });

    }

    static void makeToast(Context ctx, String s){
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ble_devices, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
