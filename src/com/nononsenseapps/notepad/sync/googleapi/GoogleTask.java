/*
 * Copyright (C) 2012 Jonas Kalderstam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nononsenseapps.notepad.sync.googleapi;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Comparator;

import org.json.JSONException;
import org.json.JSONObject;

import com.nononsenseapps.notepad.NotePad;
import com.nononsenseapps.notepad.NotePad.Notes;
import com.nononsenseapps.util.BiMap;

import android.content.ContentValues;
import android.util.Log;

public class GoogleTask {

	public static final Comparator<GoogleTask> LOCALORDER = new Comparator<GoogleTask>() {
		@Override
		public int compare(final GoogleTask lhs, final GoogleTask rhs) {
			return lhs.truepos.compareTo(rhs.truepos);
		}
	};

	public static class RemoteOrder implements Comparator<GoogleTask> {

		final ArrayList<GoogleTask> allTasks;
		final ArrayList<GoogleTask> moddedTasks;

		public RemoteOrder(final ArrayList<GoogleTask> moddedTasks,
				ArrayList<GoogleTask> allTasks) {
			this.allTasks = allTasks;
			this.moddedTasks = moddedTasks;
		}

		/**
		 * Returns the indendation level of a child with the specified parent.
		 * if remoteParent is null or empty, returns 0;
		 */
		private int getLevel(final GoogleTask remoteTask) {
			final String remoteParent = remoteTask.remoteparent;
			if (remoteParent == null || remoteParent.isEmpty())
				return 0;

			// Find parent of parent
			String ancestor = remoteParent;
			String currentAncestor = ancestor;
			int indent = 1;
			boolean notDone = true;
			Log.d("remotesort", "size of allTasks: " + allTasks.size());
			Log.d("remotesort", "size of moddedTasks: " + moddedTasks.size());
			while (notDone) {
				Log.d("remotesort", "ancestor: " + ancestor);
				Log.d("remotesort", "current: " + currentAncestor);
				if (ancestor == null) {
					notDone = false;
					Log.d("remotesort", "ancestor null, done!");
					break;
				} else {
					currentAncestor = ancestor;
					boolean found = false;
					for (final GoogleTask task : moddedTasks) {
						Log.d("remotesort", "Checking: " + task.id);
						if (ancestor.equals(task.id)) {
							found = true;
							if (task.indent > -1) {
								indent += task.indent;
								notDone = false;
								Log.d("remotesort",
										"setting ancestor because we found: "
												+ task.id);
								ancestor = null;
							} else if (task.remoteparent == null
									|| task.remoteparent.isEmpty()) {
								task.indent = 0;
								notDone = false;
								ancestor = null;
								Log.d("remotesort",
										"setting ancestor to null since we found the top");
							} else {
								indent += 1;
								ancestor = task.remoteparent;
								Log.d("remotesort", "setting ancestor to "
										+ task.remoteparent);
							}
							break;
						}
					}
					if (!found) {
						for (final GoogleTask task : allTasks) {
							Log.d("remotesort", "Checking all: " + task.id);
							if (ancestor.equals(task.id)) {
								found = true;
								if (task.indent > -1) {
									indent += task.indent;
									notDone = false;
									Log.d("remotesort",
											"setting ancestor because we found  all: "
													+ task.id);
									ancestor = null;
								} else if (task.remoteparent == null
										|| task.remoteparent.isEmpty()) {
									task.indent = 0;
									notDone = false;
									ancestor = null;
									Log.d("remotesort",
											"setting ancestor to null since we found the top  all");
								} else {
									indent += 1;
									ancestor = task.remoteparent;
									Log.d("remotesort",
											"setting ancestor to  all "
													+ task.remoteparent);
								}
								break;
							}
						}
					}
				}

				if (remoteTask.id.equals(ancestor) || currentAncestor.equals(ancestor) || remoteParent.equals(ancestor)) {
					Log.d("remotesort", "Could not find ancestor, fuck it");
					remoteTask.remoteparent = null;
					return 0;
				}
//					throw new InvalidParameterException(
//							"Tried sorting on remote parent but could not find "
//									+ remoteParent + " in allTasks!");
//
//				if (remoteParent.equals(ancestor))
//					throw new InvalidParameterException(
//							"Detected infinite parent loop in structure! "
//									+ remoteParent + " == " + ancestor);
			}

			return indent;
		}

		@Override
		public int compare(GoogleTask lhs, GoogleTask rhs) {
			if (lhs.deleted > 0 && rhs.deleted > 0)
				return 0;
			if (lhs.deleted > 0)
				return 1;
			if (rhs.deleted > 0)
				return -1;

			Log.d("remotesort", "Comparing: " + lhs.title + " and " + rhs.title);
			final int leftLevel = getLevel(lhs);
			final int rightLevel = getLevel(rhs);

			if (leftLevel == rightLevel) {
				// Share parents, compare their positions
				return lhs.position.compareTo(rhs.position);
			} else if (leftLevel < rightLevel) {
				return -1;
			} else {
				return 1;
			}
		}
	}

	private static final String TAG = "GoogleTask";
	public static final String ID = "id";
	public static final String TITLE = "title";
	public static final String UPDATED = "updated";
	public static final String NOTES = "notes";
	public static final String STATUS = "status";
	public static final String DUE = "due";
	public static final String DELETED = "deleted";
	public static final String COMPLETED = "completed";
	public static final String NEEDSACTION = "needsAction";
	public static final String PARENT = "parent";
	public static final String PREVIOUS = "previous";
	public static final String POSITION = "position";
	public static final String HIDDEN = "hidden";
	public String id = null;
	public String etag = "";
	public String title = null;
	public String updated = null;
	public String notes = null;
	public String status = null;
	public String dueDate = null;
	public Long localprevious = null;
	public Long localparent = null;
	public String remoteparent = null;
	public String remoteprevious = null;
	public String position = null;

	public int modified = 0;

	public long dbId = -1;
	public int deleted = 0;
	public int hidden = 0;
	public long listdbid = -1;
	public boolean didRemoteInsert = false;

	public String truepos = null;

	public JSONObject json = null;
	public boolean moveUploaded = false;
	private int indent = -1;

	public GoogleTask() {
	}

	public GoogleTask(JSONObject jsonTask) throws JSONException {
		id = jsonTask.getString(ID);
		title = jsonTask.getString(TITLE);
		updated = jsonTask.getString(UPDATED);
		// etag = jsonTask.getString("etag");
		if (jsonTask.has(NOTES))
			notes = jsonTask.getString(NOTES);
		status = jsonTask.getString(STATUS);
		if (jsonTask.has(PARENT))
			remoteparent = jsonTask.getString(PARENT);
		else
			remoteparent = null;
		// if (jsonTask.has(PREVIOUS))
		// remoteprevious = jsonTask.getString(PREVIOUS);
		position = jsonTask.getString(POSITION);
		if (jsonTask.has(DUE))
			dueDate = jsonTask.getString(DUE);
		if (jsonTask.has(DELETED) && jsonTask.getBoolean(DELETED))
			deleted = 1;
		else
			deleted = 0;
		if (jsonTask.has(HIDDEN) && jsonTask.getBoolean(HIDDEN))
			hidden = 1;
		else
			hidden = 0;

		json = jsonTask;
	}

	/**
	 * Special tricks because google api actually want 'null' while JSONObject
	 * doesnt allow them. do not include read-only fields
	 * 
	 * @return
	 */
	public String toJSON() {
		String returnString = "";
		try {
			JSONObject json = new JSONObject();
			String nullAppendage = "";
			// if (id != null)
			// json.put(ID, id);

			json.put(TITLE, title);
			json.put(NOTES, notes);
			if (remoteparent != null)
				json.put(PARENT, remoteparent);
			if (remoteprevious != null)
				json.put(PREVIOUS, remoteprevious);

			if (dueDate != null && !dueDate.equals(""))
				json.put(DUE, dueDate);
			else
				nullAppendage += ", \"" + DUE + "\": null";

			json.put(STATUS, status);
			if (status != null && status.equals(NEEDSACTION)) {
				// We must reset this also in this case
				nullAppendage += ", \"" + COMPLETED + "\": null";
			}

			nullAppendage += "}";

			String jsonString = json.toString();
			returnString = jsonString.substring(0, jsonString.length() - 1)
					+ nullAppendage;

		} catch (JSONException e) {
			Log.d(TAG, e.getLocalizedMessage());
		}

		return returnString;
	}

	/**
	 * 
	 * @return Only the parent and previous fields (if not null)
	 */
	public String toMoveJSON() {
		String returnString = "";
		try {
			JSONObject json = new JSONObject();
			if (remoteparent != null)
				json.put(PARENT, remoteparent);
			if (remoteprevious != null)
				json.put(PREVIOUS, remoteprevious);

			return json.toString();
		} catch (JSONException e) {
			Log.d(TAG, e.getLocalizedMessage());
		}

		return returnString;
	}

	/**
	 * Returns a ContentValues hashmap suitable for database insertion in the
	 * Lists table Includes modified flag and list id as specified in the
	 * arguments
	 * 
	 * @return
	 */
	public ContentValues toNotesContentValues(int modified, long listDbId,
			BiMap<Long, String> idMap) {
		ContentValues values = new ContentValues();
		if (title != null)
			values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
		if (dueDate != null)
			values.put(NotePad.Notes.COLUMN_NAME_DUE_DATE, dueDate);
		if (status != null)
			values.put(NotePad.Notes.COLUMN_NAME_GTASKS_STATUS, status);
		if (notes != null)
			values.put(NotePad.Notes.COLUMN_NAME_NOTE, notes);

		if (dbId > -1)
			values.put(NotePad.Notes._ID, dbId);

		values.put(NotePad.Notes.COLUMN_NAME_LIST, listDbId);
		values.put(NotePad.Notes.COLUMN_NAME_MODIFIED, modified);
		values.put(NotePad.Notes.COLUMN_NAME_DELETED, deleted);
		// values.put(NotePad.Notes.COLUMN_NAME_POSITION, position);
		// Make sure position values are set properly
		if (dbId == -1 || !moveUploaded) {
			if (remoteparent != null) {
				// Do not join these IFs, because I actually do not want null
				// there
				// otherwise
				if (idMap.containsValue(remoteparent))
					values.put(Notes.COLUMN_NAME_PARENT,
							idMap.getKey(remoteparent));
			} else {
				values.putNull(NotePad.Notes.COLUMN_NAME_PARENT);
			}
			// if (remoteprevious != null) {
			// // Do not join these IFs, because I actually do not want null
			// // there
			// // otherwise
			// if (idMap.containsValue(remoteprevious))
			// values.put(Notes.COLUMN_NAME_PARENT,
			// idMap.getKey(remoteprevious));
			// } else {
			// values.putNull(NotePad.Notes.COLUMN_NAME_PREVIOUS);
			// }
		}

		values.put(NotePad.Notes.COLUMN_NAME_HIDDEN, hidden);

		// values.put(NotePad.Notes.COLUMN_NAME_POSSUBSORT, possort);
		// values.put(NotePad.Notes.COLUMN_NAME_INDENTLEVEL, indentLevel);

		return values;
	}

	/**
	 * The parentIndex and previousIndex can be set to valid backreference
	 * indices to indicate the id of the parent and previous of this note. If
	 * set to null, already set values will be used which might be null.
	 */
	public ContentValues toNotesBackRefContentValues(Integer listIdIndex,
			BiMap<Long, String> idMap, BiMap<String, Integer> remoteToIndex) {
		ContentValues values = new ContentValues();
		if (listIdIndex != null)
			values.put(NotePad.Notes.COLUMN_NAME_LIST, listIdIndex);
		/*
		 * If the parent doesnt exist in the database, we must take the position
		 * from previous insert operations Otherwise, they have been set in
		 * tovalues
		 * 
		 * And of course, only worth doing anything if we did not actually
		 * upload the position ourselves successfully
		 */
		if (dbId == -1 || !moveUploaded) {
			if (remoteparent != null && !remoteparent.isEmpty() && !idMap.containsValue(remoteparent))
				values.put(Notes.COLUMN_NAME_PARENT,
						remoteToIndex.get(remoteparent));

			// if (!idMap.containsValue(remoteprevious))
			// values.put(Notes.COLUMN_NAME_PREVIOUS,
			// remoteToIndex.get(remoteprevious));
		}

		return values;
	}

	public ContentValues toGTasksContentValues(String accountName) {
		ContentValues values = new ContentValues();
		values.put(NotePad.GTasks.COLUMN_NAME_DB_ID, dbId);
		values.put(NotePad.GTasks.COLUMN_NAME_ETAG, etag);
		values.put(NotePad.GTasks.COLUMN_NAME_GOOGLE_ACCOUNT, accountName);
		values.put(NotePad.GTasks.COLUMN_NAME_GTASKS_ID, id);
		values.put(NotePad.GTasks.COLUMN_NAME_UPDATED, updated);
		return values;
	}

	public ContentValues toGTasksBackRefContentValues(int pos) {
		ContentValues values = new ContentValues();
		values.put(NotePad.GTasks.COLUMN_NAME_DB_ID, pos);
		return values;
	}

	/**
	 * Returns true if the task has the same remote id or same database id.
	 */
	@Override
	public boolean equals(Object o) {
		boolean equal = false;
		if (GoogleTask.class.isInstance(o)) {
			// It's a list!
			GoogleTask task = (GoogleTask) o;
			if (dbId != -1 && dbId == task.dbId) {
				equal = true;
			}
			if (id != null && id.equals(task.id)) {
				equal = true;
			}
		}
		return equal;
	}

}
