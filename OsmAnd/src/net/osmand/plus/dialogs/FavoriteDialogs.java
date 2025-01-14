package net.osmand.plus.dialogs;


import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.osmand.plus.utils.AndroidUtils;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.myplaces.FavouritesDbHelper;
import net.osmand.plus.myplaces.FavouritesDbHelper.FavoriteGroup;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.myplaces.FavoritesListFragment.FavouritesAdapter;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.mapcontextmenu.editors.FavoritePointEditor;
import net.osmand.plus.mapcontextmenu.editors.FavoritePointEditorFragment;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class FavoriteDialogs {
	public static final String KEY_FAVORITE = "favorite";
	
	public static Dialog createReplaceFavouriteDialog(final Activity activity, final Bundle args) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		boolean nightMode = app.getDaynightHelper().isNightModeForMapControls();
		Context themedContext = UiUtilities.getThemedContext(activity, nightMode);
		final FavouritesDbHelper helper = app.getFavorites();
		final List<FavouritePoint> points = new ArrayList<FavouritePoint>(helper.getFavouritePoints());
		final FavouritesAdapter favouritesAdapter = new FavouritesAdapter(activity, points,false);
		final Dialog[] dlgHolder = new Dialog[1];
		OnItemClickListener click = new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				confirmReplace(activity, args, helper, favouritesAdapter, dlgHolder, position);
			}
			
		};
		favouritesAdapter.sortByDefault(true);
		
		if(points.size() == 0){
			Toast.makeText(themedContext, activity.getString(R.string.fav_points_not_exist), Toast.LENGTH_SHORT).show();
			return null;
		}
		return showFavoritesDialog(themedContext, favouritesAdapter, click, null, dlgHolder, true);
	}
	
	private static void confirmReplace(final Activity activity, final Bundle args, final FavouritesDbHelper helper,
			final FavouritesAdapter favouritesAdapter, final Dialog[] dlgHolder, int position) {
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		boolean nightMode = app.getDaynightHelper().isNightModeForMapControls();
		Context themedContext = UiUtilities.getThemedContext(activity, nightMode);
		AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
		builder.setTitle(R.string.update_existing);
		final FavouritePoint fp = favouritesAdapter.getItem(position);
		builder.setMessage(activity.getString(R.string.replace_favorite_confirmation, fp.getName()));
		builder.setNegativeButton(R.string.shared_string_no, null);
		builder.setPositiveButton(R.string.shared_string_yes, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (dlgHolder != null && dlgHolder.length > 0 && dlgHolder[0] != null) {
					dlgHolder[0].dismiss();
				}
				FavouritePoint point = (FavouritePoint) args.getSerializable(KEY_FAVORITE);
				if (point != null && helper.editFavourite(fp, point.getLatitude(), point.getLongitude())) {
					helper.deleteFavourite(point);
					if (activity instanceof MapActivity) {
						MapActivity mapActivity = (MapActivity) activity;
						Fragment fragment = mapActivity.getSupportFragmentManager()
								.findFragmentByTag(FavoritePointEditor.TAG);
						if (fragment instanceof FavoritePointEditorFragment) {
							((FavoritePointEditorFragment) fragment).exitEditing();
						}
						mapActivity.getContextMenu()
								.show(new LatLon(point.getLatitude(), point.getLongitude()), fp.getPointDescription(activity), fp);
						mapActivity.refreshMap();
					}
				}
			}
		});
		builder.show();
	}
	
	public static void prepareAddFavouriteDialog(Activity activity, Dialog dialog, Bundle args, double lat, double lon, PointDescription desc) {
		final Resources resources = activity.getResources();
		String name = desc == null ? "" : desc.getName();
		if(name.length() == 0) {
			name = resources.getString(R.string.add_favorite_dialog_default_favourite_name);
		}
		OsmandApplication app = (OsmandApplication) activity.getApplication();
		final FavouritePoint point = new FavouritePoint(lat, lon, name, app.getSettings().LAST_FAV_CATEGORY_ENTERED.get());
		args.putSerializable(KEY_FAVORITE, point);
		final EditText editText =  (EditText) dialog.findViewById(R.id.Name);
		editText.setText(point.getName());
		editText.selectAll();
		editText.requestFocus();
		final AutoCompleteTextView cat =  (AutoCompleteTextView) dialog.findViewById(R.id.Category);
		cat.setText(point.getCategory());
		AndroidUtils.softKeyboardDelayed(activity, editText);
	}
	
	public  static Dialog createAddFavouriteDialog(final Activity activity, final Bundle args) {
		final OsmandApplication app = (OsmandApplication) activity.getApplication();
		boolean nightMode = app.getDaynightHelper().isNightModeForMapControls();
		final Context themedContext = UiUtilities.getThemedContext(activity, nightMode);
		AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
		builder.setTitle(R.string.favourites_context_menu_edit);
		final View v = UiUtilities.getInflater(activity, nightMode).inflate(R.layout.favorite_edit_dialog, null, false);
		final FavouritesDbHelper helper = app.getFavorites();
		builder.setView(v);
		final EditText editText =  (EditText) v.findViewById(R.id.Name);
		final EditText description = (EditText) v.findViewById(R.id.description);
		final AutoCompleteTextView cat =  (AutoCompleteTextView) v.findViewById(R.id.Category);
		List<FavoriteGroup> gs = helper.getFavoriteGroups();
		final String[] list = new String[gs.size()];
		for (int i = 0; i < list.length; i++) {
			list[i] = gs.get(i).getName();
		}
		cat.setAdapter(new ArrayAdapter<String>(activity, R.layout.list_textview, list));
		if (app.accessibilityEnabled()) {
			final TextView textButton = (TextView)v.findViewById(R.id.TextButton);
			textButton.setClickable(true);
			textButton.setFocusable(true);
			textButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					AlertDialog.Builder b = new AlertDialog.Builder(themedContext);
					b.setTitle(R.string.access_category_choice);
					b.setItems(list, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							cat.setText(list[which]);
						}
					});
					b.setNegativeButton(R.string.shared_string_cancel, null);
					b.show();
				}
			});
		}

		builder.setNegativeButton(R.string.shared_string_cancel, null);
		builder.setNeutralButton(R.string.update_existing, new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Don't use showDialog because it is impossible to refresh favorite items list
				Dialog dlg = createReplaceFavouriteDialog(activity, args);
				if(dlg != null) {
					dlg.show();
				}
				// mapActivity.showDialog(DIALOG_REPLACE_FAVORITE);
			}
			
		});
		builder.setPositiveButton(R.string.shared_string_add, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final FavouritePoint point = (FavouritePoint) args.getSerializable(KEY_FAVORITE);
				String categoryStr = cat.getText().toString().trim();
				final FavouritesDbHelper helper = app.getFavorites();
				app.getSettings().LAST_FAV_CATEGORY_ENTERED.set(categoryStr);
				point.setName(editText.getText().toString().trim());
				point.setDescription(description.getText().toString().trim());
				point.setCategory(categoryStr);
				AlertDialog.Builder bld = FavouritesDbHelper.checkDuplicates(point, helper, activity);
				if(bld != null) {
					bld.setPositiveButton(R.string.shared_string_ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							addFavorite(activity, point, helper);							
						}
					});
					bld.show();
				} else {
					addFavorite(activity, point, helper);
				}
			}

			protected void addFavorite(final Activity activity, FavouritePoint point, final FavouritesDbHelper helper) {
				boolean added = helper.addFavourite(point);
				if (added) {
					Toast.makeText(activity, MessageFormat.format(
							activity.getString(R.string.add_favorite_dialog_favourite_added_template), point.getName()), Toast.LENGTH_SHORT)
							.show();
				}
				if (activity instanceof MapActivity) {
					((MapActivity) activity).getMapView().refreshMap(true);
				}
			}
		});
		return builder.create();
    }
	
	public static final AlertDialog showFavoritesDialog(
			final Context uiContext,
			final FavouritesAdapter favouritesAdapter, final OnItemClickListener click,
			final OnDismissListener dismissListener, final Dialog[] dialogHolder, final boolean sortByDist) {
		ListView listView = new ListView(uiContext);
		AlertDialog.Builder bld = new AlertDialog.Builder(uiContext);
		favouritesAdapter.sortByDefault(sortByDist);
		listView.setAdapter(favouritesAdapter);
		listView.setOnItemClickListener(click);
		bld.setPositiveButton(sortByDist ? R.string.sort_by_name :
			R.string.sort_by_distance, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				showFavoritesDialog(uiContext, favouritesAdapter, click, dismissListener, dialogHolder, !sortByDist);
			}
		});
		bld.setNegativeButton(R.string.shared_string_cancel, null);
		bld.setView(listView);
		AlertDialog dlg = bld.show();
		if(dialogHolder != null) {
			dialogHolder[0] = dlg;
		}
		dlg.setOnDismissListener(dismissListener);
		return dlg;
	}
	
}
