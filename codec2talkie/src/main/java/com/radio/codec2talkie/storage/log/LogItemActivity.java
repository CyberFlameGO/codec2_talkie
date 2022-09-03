package com.radio.codec2talkie.storage.log;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radio.codec2talkie.R;
import com.radio.codec2talkie.storage.station.StationItemAdapter;
import com.radio.codec2talkie.storage.position.PositionItemViewModel;

import java.util.List;

public class LogItemActivity extends AppCompatActivity {
    private static final String TAG = LogItemActivity.class.getSimpleName();

    private String _groupName;
    private LogItemViewModel _logItemViewModel;
    private PositionItemViewModel _positionItemViewModel;

    private LiveData<List<LogItem>> _logItemLiveData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_view);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // get group name to decide if filtering should be enabled
        Bundle bundle = getIntent().getExtras();
        _groupName = null;
        if (bundle != null) {
            _groupName = (String)bundle.get("groupName");
        }

        // view models
        _logItemViewModel = new ViewModelProvider(this).get(LogItemViewModel.class);
        _positionItemViewModel = new ViewModelProvider(this).get(PositionItemViewModel.class);

        // log items
        RecyclerView logItemRecyclerView = findViewById(R.id.log_item_recyclerview);
        logItemRecyclerView.setHasFixedSize(true);

        // log lines list adapter
        final LogItemAdapter adapter = new LogItemAdapter(new LogItemAdapter.LogItemDiff(), _groupName == null);
        logItemRecyclerView.setAdapter(adapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        logItemRecyclerView.setLayoutManager(linearLayoutManager);
        logItemRecyclerView.addItemDecoration(new DividerItemDecoration(logItemRecyclerView.getContext(), DividerItemDecoration.VERTICAL));

        // log groups
        RecyclerView logItemGroupRecyclerView = findViewById(R.id.log_item_group_recyclerview);
        logItemGroupRecyclerView.setHasFixedSize(true);

        // groups adapter
        final StationItemAdapter adapterGroup = new StationItemAdapter(new StationItemAdapter.LogItemGroupDiff());
        adapterGroup.setClickListener(v -> {
            TextView itemView = v.findViewById(R.id.log_view_group_item_title);
            _logItemLiveData.removeObservers(this);
            _groupName = itemView.getText().toString();
            _logItemLiveData = _logItemViewModel.getData(_groupName);
            _logItemLiveData.observe(this, adapter::submitList);
            setTitle(_groupName);
        });
        logItemGroupRecyclerView.setAdapter(adapterGroup);
        LinearLayoutManager linearLayoutManagerGroup = new LinearLayoutManager(this);
        logItemGroupRecyclerView.setLayoutManager(linearLayoutManagerGroup);
        logItemGroupRecyclerView.addItemDecoration(new DividerItemDecoration(logItemGroupRecyclerView.getContext(), DividerItemDecoration.VERTICAL));

        _logItemViewModel.getLastPositions().observe(this, adapterGroup::submitList);

        // launch with filter if group name is provided
        if (_groupName == null) {
            logItemGroupRecyclerView.setVisibility(View.GONE);
            findViewById(R.id.log_item_textview).setVisibility(View.GONE);
            findViewById(R.id.log_item_group_textview).setVisibility(View.GONE);
            _logItemLiveData = _logItemViewModel.getAllData();
            _logItemLiveData.observe(this, adapter::submitList);
            setTitle(R.string.aprs_log_view_title);
        } else {
            _logItemLiveData = _logItemViewModel.getData(_groupName);
            _logItemLiveData.observe(this, adapter::submitList);
            setTitle(_groupName);
        }

        // register live scroll
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                int msgCount = adapter.getItemCount();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                //Log.i(TAG, " " + positionStart + " " + itemCount + " " + lastVisiblePosition + " " + msgCount);
                if (lastVisiblePosition == RecyclerView.NO_POSITION || (positionStart == msgCount - itemCount && lastVisiblePosition == positionStart - 1)) {
                    logItemRecyclerView.scrollToPosition(msgCount - 1);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_view_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (_groupName != null) {
            menu.findItem(R.id.log_view_menu_stations).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            finish();
            return true;
        }  else if (itemId == R.id.log_view_menu_clear_all) {
            deleteLogItems(-1);
            return true;
        }  else if (itemId == R.id.log_view_menu_clear_1h) {
            deleteLogItems(1);
            return true;
        }  else if (itemId == R.id.log_view_menu_clear_12h) {
            deleteLogItems(12);
            return true;
        }  else if (itemId == R.id.log_view_menu_clear_1d) {
            deleteLogItems(24);
            return true;
        }  else if (itemId == R.id.log_view_menu_clear_7d) {
            deleteLogItems(24*7);
            return true;
        } else if (itemId == R.id.log_view_menu_stations) {
            Intent logItemIntent = new Intent(this, LogItemActivity.class);
            logItemIntent.putExtra("groupName", getString(R.string.log_view_station_history));
            startActivity(logItemIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteLogItems(int hours) {
        DialogInterface.OnClickListener deleteAllDialogClickListener = (dialog, which) -> {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (_groupName == null) {
                    if (hours == -1) {
                        _logItemViewModel.deleteAllLogItems();
                        _positionItemViewModel.deleteAllPositionItems();
                    } else {
                        _logItemViewModel.deleteLogItemsOlderThanHours(hours);
                        _positionItemViewModel.deletePositionItemsOlderThanHours(hours);
                    }
                } else {
                    _logItemViewModel.deleteLogItems(_groupName);
                    _positionItemViewModel.deletePositionItems(_groupName);
                }
            }
        };
        String alertMessage = getString(R.string.log_item_activity_delete_all_title);
        if (hours != -1) {
            alertMessage = String.format(getString(R.string.log_item_activity_delete_hours_title), hours);
        }
        if (_groupName != null) {
            alertMessage = getString(R.string.log_item_activity_delete_group_title);
            alertMessage = String.format(alertMessage, _groupName);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(alertMessage)
                .setPositiveButton(getString(R.string.yes), deleteAllDialogClickListener)
                .setNegativeButton(getString(R.string.no), deleteAllDialogClickListener).show();
    }
}
