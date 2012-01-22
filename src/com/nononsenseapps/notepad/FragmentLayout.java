package com.nononsenseapps.notepad;

import java.util.ArrayList;
import java.util.Collection;

import com.nononsenseapps.notepad.interfaces.Refresher;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.widget.TextView.OnEditorActionListener;

/**
 * Showing a single fragment in an activity.
 */
public class FragmentLayout extends FragmentActivity implements
		OnSharedPreferenceChangeListener, OnEditorDeleteListener {
	public static boolean lightTheme = false;
	public static boolean shouldRestart = false;
	public static boolean LANDSCAPE_MODE;
	public static boolean AT_LEAST_ICS;
	public static boolean AT_LEAST_HC;
	
	public static OnEditorDeleteListener ONDELETELISTENER = null;
	
	private Refresher list;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Must set theme before this
		super.onCreate(savedInstanceState);
		
		LANDSCAPE_MODE = getResources().getBoolean(R.bool.useLandscapeView);
		AT_LEAST_ICS = getResources().getBoolean(R.bool.atLeastIceCreamSandwich);
		AT_LEAST_HC = getResources().getBoolean(R.bool.atLeastHoneycomb);

		// Setting theme here
		readAndSetSettings();

		Log.d("FragmentActivity", "onCreate before");
		// XML makes sure notes list is displayed. And editor too in landscape
		if (lightTheme)
			setContentView(R.layout.fragment_layout_light);
		else
			setContentView(R.layout.fragment_layout_dark);
		Log.d("FragmentActivity", "onCreate after");
		
		// Set this as delete listener
		NotesListFragment list = (NotesListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.noteslistfragment);
		
		list.setOnDeleteListener(this);
		
		this.list = list;
		// So editor can access it
		ONDELETELISTENER = this;
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		Log.d("FragmentLayout", "On New Intent list: " + list);
		if (this.list != null) {
			Log.d("FragmentLayout", "Calling refresh");
			list.refresh();
		}
	}

	@Override
	protected void onResume() {
		Log.d("FragmentLayout", "onResume");
		if (shouldRestart) {
			Log.d("FragmentLayout", "Should refresh");
			restartAndRefresh();
		}
		super.onResume();
	}

	public void restartAndRefresh() {
		Log.d("FragmentLayout", "Should restart and refresh");
		shouldRestart = false;
		Intent intent = getIntent();
		overridePendingTransition(0, 0);
		intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
		finish();
		overridePendingTransition(0, 0);
		startActivity(intent);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		// Need to restart to allow themes and such to go into effect
		if (key.equals(NotesPreferenceActivity.KEY_SORT_ORDER)
				|| key.equals(NotesPreferenceActivity.KEY_SORT_TYPE)
				|| key.equals(NotesPreferenceActivity.KEY_THEME)) {
			shouldRestart = true;
		}
	}

	private void readAndSetSettings() {
		// Read settings and set
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		lightTheme = prefs.getBoolean(NotesPreferenceActivity.KEY_THEME, false);
		setTypeOfTheme();

		String sortType = prefs.getString(
				NotesPreferenceActivity.KEY_SORT_TYPE,
				NotePad.Notes.DEFAULT_SORT_TYPE);
		String sortOrder = prefs.getString(
				NotesPreferenceActivity.KEY_SORT_ORDER,
				NotePad.Notes.DEFAULT_SORT_ORDERING);

		NotePad.Notes.SORT_ORDER = sortType + " " + sortOrder;
		Log.d("ReadingSettings", "sortOrder is: " + NotePad.Notes.SORT_ORDER);

		// We want to be notified of future changes
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	private void setTypeOfTheme() {
		if (lightTheme) {
			setTheme(R.style.ThemeHoloLightDarkActonBar);
		} else {
			setTheme(R.style.ThemeHolo);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// both list and editor should be notified
		NotesListFragment list = (NotesListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.noteslistfragment);
		NotesEditorFragment editor = (NotesEditorFragment) getSupportFragmentManager()
				.findFragmentById(R.id.editor);
		switch (item.getItemId()) {
		case R.id.menu_delete:
			if (editor != null)
				editor.setNoSave();
			//delete note
			if (list != null)
				list.onDelete();
			
			break;
		case R.id.menu_preferences:
			Log.d("NotesListFragment", "onOptionsSelection pref");
			showPrefs();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void showPrefs() {
		// launch a new activity to display the dialog
		Intent intent = new Intent();
		intent.setClass(this, NotesPreferenceActivity.class);
		startActivity(intent);
	}

	/**
	 * This is a secondary activity, to show what the user has selected when the
	 * screen is not large enough to show it all in one activity.
	 */
	public static class NotesEditorActivity extends FragmentActivity implements onNewNoteCreatedListener {
		private NotesEditorFragment editorFragment;
		private long currentId = -1;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			// Make sure to set themes before this
			super.onCreate(savedInstanceState);

			Log.d("NotesEditorActivity", "onCreate");

			if (FragmentLayout.lightTheme) {
				setTheme(R.style.ThemeHoloLightDarkActonBar);
			} else {
				setTheme(R.style.ThemeHolo);
			}

			// Set up navigation (adds nice arrow to icon)
			ActionBar actionBar = getSupportActionBar();
			actionBar.setDisplayHomeAsUpEnabled(true);

			if (LANDSCAPE_MODE ) {
				// If the screen is now in landscape mode, we can show the
				// dialog in-line with the list so we don't need this activity.
				Log.d("NotesEditorActivity",
						"Landscape mode detected, killing myself");
				finish();
				return;
			}

			Log.d("NotesEditorActivity", "Time to show the note!");
			// if (savedInstanceState == null) {
			// During initial setup, plug in the details fragment.
			editorFragment = new NotesEditorFragment();
			editorFragment.setOnNewNoteCreatedListener(this);
			editorFragment.setArguments(getIntent().getExtras());
			getSupportFragmentManager().beginTransaction()
					.add(android.R.id.content, editorFragment).commit();
			// }
		}

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case R.id.menu_delete:
				editorFragment.setNoSave();
				FragmentLayout.deleteNote(getContentResolver(), currentId);
				setResult(FragmentActivity.RESULT_CANCELED);
				finish();
				return true;
			case R.id.menu_revert:
				setResult(FragmentActivity.RESULT_CANCELED);
				finish();
				break;
			}
			return super.onOptionsItemSelected(item);
		}

		@Override
		public void onPause() {
			super.onPause();
			Log.d("NotesEditorActivity", "onPause");
			if (isFinishing()) {
				Log.d("NotesEditorActivity",
						"onPause, telling list to display list");
				SharedPreferences.Editor prefEditor = PreferenceManager
						.getDefaultSharedPreferences(this).edit();
				prefEditor.putBoolean(NotesListFragment.SHOWLISTKEY, true);
				prefEditor.commit();
			}
		}

		@Override
		public void onResume() {
			super.onResume();
			Log.d("NotesEditorActivity", "onResume telling list to display me");
			SharedPreferences.Editor prefEditor = PreferenceManager
					.getDefaultSharedPreferences(this).edit();
			prefEditor.putBoolean(NotesListFragment.SHOWLISTKEY, false);
			prefEditor.commit();
		}

		@Override
		public void onNewNoteCreated(long id) {
			this.currentId  = id;
		}

		@Override
		public void onNewNoteDeleted(long id) {
			// We don't care about this here
		}
	}

	@Override
	public void onEditorDelete(long id) {
		deleteNote(getContentResolver(), id);
	}
	
	/**
	 * Calls deleteNotes wrapped in ArrayList
	 * @param id
	 */
	public static void deleteNote(ContentResolver resolver, long id) {
		ArrayList<Long> idList = new ArrayList<Long>();
		idList.add(id);
		deleteNotes(resolver, idList);
	}
	
	/**
	 * Delete all notes given from database
	 * @param ids
	 */
	public static void deleteNotes(ContentResolver resolver, Iterable<Long> ids) {
		for(long id:ids) {
			resolver.delete(NotesEditorFragment.getUriFrom(id), null, null);
		}
	}
	
	@Override
	public void onMultiDelete(Collection<Long> ids, long curId) {
		if (ids.contains(curId)) {
			Log.d("FragmentLayout", "id was contained in multidelete, setting no save first");
			NotesEditorFragment editor = (NotesEditorFragment) getSupportFragmentManager()
					.findFragmentById(R.id.editor);
			if (editor != null) {
				editor.setNoSave();
			}
		}
		Log.d("FragmentLayout", "deleting notes...");
		deleteNotes(getContentResolver(), ids);
	}
	

//	public static class NotesPreferencesDialog extends FragmentActivity {
//		@Override
//		protected void onCreate(Bundle savedInstanceState) {
//			super.onCreate(savedInstanceState);
//
//			if (FragmentLayout.lightTheme) {
//				setTheme(R.style.ThemeHoloDialogNoActionBar);
//			} else {
//				setTheme(R.style.ThemeHoloDialogNoActionBar);
//			}
//
//			// Display the fragment as the main content.
//			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//			ft.replace(android.R.id.content, new NotesPreferenceActivity());
//			ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
//			ft.commit();
//		}
//
//		@Override
//		public boolean onOptionsItemSelected(MenuItem item) {
//			switch (item.getItemId()) {
//			case android.R.id.home:
//				finish();
//				break;
//			}
//			return super.onOptionsItemSelected(item);
//		}
//	}
}