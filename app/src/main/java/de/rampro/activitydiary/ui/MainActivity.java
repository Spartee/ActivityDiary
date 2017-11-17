/*
 * ActivityDiary
 *
 * Copyright (C) 2017-2017 Raphael Mack http://www.raphael-mack.de
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rampro.activitydiary.ui;

import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import de.rampro.activitydiary.ActivityDiaryApplication;
import de.rampro.activitydiary.R;
import de.rampro.activitydiary.db.ActivityDiaryContract;
import de.rampro.activitydiary.helpers.ActivityHelper;
import de.rampro.activitydiary.helpers.FuzzyTimeSpanFormatter;
import de.rampro.activitydiary.model.DiaryActivity;

/*
 * MainActivity to show most of the UI, based on switching the fragements
 *
 * */
public class MainActivity extends BaseActivity implements View.OnClickListener, SelectRecyclerViewAdapter.SelectListener, ActivityHelper.DataChangedListener {
    private StaggeredGridLayoutManager gaggeredGridLayoutManager;
    private TextView durationLabel;
    private TextView mNoteTextView;

    SelectRecyclerViewAdapter rcAdapter;

    DiaryActivity mCurrentActivity;
    Uri mCurrentDiaryUri;

    private class QHandler extends AsyncQueryHandler{
        /* Access only allowed via ActivityHelper.helper singleton */
        private QHandler(){
            super(ActivityDiaryApplication.getAppContext().getContentResolver());
        }
    }

    private QHandler mQHandler = new QHandler();
    String m_Text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View contentView = inflater.inflate(R.layout.activity_main_content, null, false);

        setContent(contentView);
        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.select_recycler);

        int rows;
        Configuration configuration = getResources().getConfiguration();

        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.listPreferredItemHeightSmall, value, true);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        rows = (int)Math.floor(configuration.screenHeightDp / value.getDimension(metrics)) - 1;

        gaggeredGridLayoutManager = new StaggeredGridLayoutManager(rows, StaggeredGridLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(gaggeredGridLayoutManager);

        View selector = contentView.findViewById(R.id.activity_background);
        selector.setOnClickListener(this);

        getSupportActionBar().setSubtitle(getResources().getString(R.string.activity_subtitle_main));

        rcAdapter = new SelectRecyclerViewAdapter(MainActivity.this, ActivityHelper.helper.activities);
        recyclerView.setAdapter(rcAdapter);

        FloatingActionButton floatingActionButton =
                (FloatingActionButton) findViewById(R.id.floating_action_button);

        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle the click.
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.dialog_title_note);

// Set up the input
                final EditText input = new EditText(MainActivity.this);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                input.setSingleLine(false);
                input.setMinLines(4);
                input.setText(m_Text);
                builder.setView(input);

// Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        m_Text = input.getText().toString();
                        ContentValues values = new ContentValues();
                        values.put(ActivityDiaryContract.Diary.NOTE, m_Text);

                        mQHandler.startUpdate(0,
                                null,
                                mCurrentDiaryUri,
                                values,
                                null, null);
                        mNoteTextView.setText(m_Text);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });

        floatingActionButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Handle the click.
                Toast.makeText(MainActivity.this, "You long-clicked the FAB! You really expect some feature here? -> Tell me what, please...", Toast.LENGTH_SHORT).show();
                return true;
            }

        });

        durationLabel = (TextView) contentView.findViewById(R.id.duration_label);
        mNoteTextView =  (TextView) contentView.findViewById(R.id.note);

        onActivityChanged();
        floatingActionButton.show();
    /* TODO #25: add a search box in the toolbar to filter / fuzzy search
    * see http://www.vogella.com/tutorials/AndroidActionBar/article.html and https://developer.android.com/training/appbar/action-views.html*/
    }

    @Override
    public void onResume() {
        mNavigationView.getMenu().findItem(R.id.nav_main).setChecked(true);
        ActivityHelper.helper.registerDataChangeListener(this);
        super.onResume();
        onActivityChanged(); // refresh mainly the duration_label
    }

    @Override
    public void onPause() {
        ActivityHelper.helper.unregisterDataChangeListener(this);

        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view){
        Toast.makeText(this, "You clicked on the current activity! Boom!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onItemClick(int adapterPosition) {
        ActivityHelper.helper.setCurrentActivity(ActivityHelper.helper.activities.get(adapterPosition));
    }

    public void onActivityChanged(){
        DiaryActivity newAct = ActivityHelper.helper.getCurrentActivity();
        if(mCurrentActivity != newAct) {
            mNoteTextView.setText("--");
        }
        mCurrentActivity = newAct;
        if(mCurrentActivity != null) {
            mCurrentDiaryUri = ActivityHelper.helper.getCurrentDiaryUri();

            ((TextView) findViewById(R.id.activity_name)).setText(mCurrentActivity.getName());
            findViewById(R.id.activity_background).setBackgroundColor(mCurrentActivity.getColor());
            /* TODO #34: set also text color */
            String duration = getResources().getString(R.string.duration_description);
            duration += " ";
            duration += FuzzyTimeSpanFormatter.format(ActivityHelper.helper.getCurrentActivityStartTime(), new Date());
            durationLabel.setText(duration);
        }else{
            /* This should be really seldom, actually only at very first start or if something went wrong.
             * In those cases we keep the default text from the xml. */
            mCurrentDiaryUri = null;
        }
    }

    /**
     * Called when the data has changed.
     */
    @Override
    public void onActivityDataChanged() {
        /* TODO: this could be done more fine grained here... */
        rcAdapter.notifyDataSetChanged();
    }

}