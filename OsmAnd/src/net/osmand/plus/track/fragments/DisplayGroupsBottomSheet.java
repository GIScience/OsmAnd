package net.osmand.plus.track.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.track.helpers.GpxSelectionHelper.GpxDisplayItemType;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.track.helpers.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.track.helpers.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.track.helpers.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.utils.UiUtilities.CompoundButtonType;
import net.osmand.plus.base.MenuBottomSheetDialogFragment;
import net.osmand.plus.base.SelectionBottomSheet.SelectableItem;
import net.osmand.plus.base.bottomsheetmenu.SimpleBottomSheetItem;
import net.osmand.plus.track.helpers.DisplayPointsGroupsHelper;
import net.osmand.plus.track.helpers.DisplayPointsGroupsHelper.DisplayGroupsHolder;
import net.osmand.plus.track.helpers.TrackDisplayHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisplayGroupsBottomSheet extends MenuBottomSheetDialogFragment {

	public static final String TAG = DisplayGroupsBottomSheet.class.getSimpleName();

	private OsmandApplication app;
	private TrackDisplayHelper displayHelper;
	private SelectedGpxFile selectedGpxFile;

	private final List<SelectableItem> uiItems = new ArrayList<>();
	private final Map<SelectableItem, View> listViews = new HashMap<>();
	private LayoutInflater inflater;
	private LinearLayout listContainer;
	private TextView sizeIndication;
	private View stateButton;

	private DisplayPointGroupsCallback callback;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initData();
	}

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		inflater = UiUtilities.getInflater(requireContext(), nightMode);
		View view = inflater.inflate(R.layout.bottom_sheet_display_groups_visibility, null);
		sizeIndication = view.findViewById(R.id.selected_size);
		stateButton = view.findViewById(R.id.state_button);
		listContainer = view.findViewById(R.id.list);
		updateListItems();
		setupStateButton();
		items.add(new SimpleBottomSheetItem.Builder().setCustomView(view).create());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		fullUpdate();
	}

	private void initData() {
		app = requiredMyApplication();
		callback = (DisplayPointGroupsCallback) getTargetFragment();
		if (callback != null) {
			displayHelper = callback.getDisplayHelper();
			selectedGpxFile = callback.getSelectedGpx();
			initSelectableItems();
		}
	}

	private void initSelectableItems() {
		List<GpxDisplayGroup> displayGroups = displayHelper.getPointsOriginalGroups();
		DisplayGroupsHolder groupsHolder = DisplayPointsGroupsHelper.getGroups(app, displayGroups, null);
		uiItems.clear();
		for (GpxDisplayGroup group : groupsHolder.groups) {
			if (group.getType() != GpxDisplayItemType.TRACK_POINTS) {
				continue;
			}

			SelectableItem uiItem = new SelectableItem();
			List<GpxDisplayItem> groupItems = groupsHolder.itemGroups.get(group);

			String categoryName = group.getName();
			if (TextUtils.isEmpty(categoryName)) {
				categoryName = app.getString(R.string.shared_string_gpx_points);
			}
			uiItem.setTitle(categoryName);
			uiItem.setColor(group.getColor());

			int size = groupItems != null ? groupItems.size() : 0;
			uiItem.setDescription(String.valueOf(size));

			uiItem.setObject(group);
			uiItems.add(uiItem);
		}
	}

	private void updateStateButton() {
		TextView title = stateButton.findViewById(R.id.state_button_text);
		if (isAnyGroupVisible()) {
			title.setText(R.string.shared_string_hide_all);
		} else {
			title.setText(R.string.shared_string_show_all);
		}
	}

	private void updateListItems() {
		listContainer.removeAllViews();
		listViews.clear();
		for (SelectableItem item : uiItems) {
			View view = inflater.inflate(R.layout.bottom_sheet_item_with_descr_and_switch_56dp, listContainer, false);
			TextView title = view.findViewById(R.id.title);
			title.setText(item.getTitle());

			TextView description = view.findViewById(R.id.description);
			description.setText(item.getDescription());

			CompoundButton cb = view.findViewById(R.id.compound_button);
			UiUtilities.setupCompoundButton(cb, nightMode, CompoundButtonType.GLOBAL);

			view.setOnClickListener(v -> {
				if (item.getObject() instanceof GpxDisplayGroup) {
					GpxDisplayGroup group = ((GpxDisplayGroup) item.getObject());
					updateGroupVisibility(group.getName(), !cb.isChecked());
					callback.onPointGroupsVisibilityChanged();
					fullUpdate();
				}
			});

			listContainer.addView(view);
			listViews.put(item, view);
		}
	}

	private void setupStateButton() {
		stateButton.setOnClickListener(view -> {
			boolean newState = !isAnyGroupVisible();
			for (String groupName : getGroupsNames()) {
				updateGroupVisibility(groupName, newState);
			}
			callback.onPointGroupsVisibilityChanged();
			fullUpdate();
		});
	}

	private void updateGroupVisibility(String groupName, boolean isVisible) {
		if (isVisible) {
			selectedGpxFile.removeHiddenGroups(groupName);
		} else {
			selectedGpxFile.addHiddenGroups(groupName);
		}
	}

	private List<String> getGroupsNames() {
		List<String> names = new ArrayList<>();
		for (SelectableItem item : uiItems) {
			if (item.getObject() instanceof GpxDisplayGroup) {
				GpxDisplayGroup group = ((GpxDisplayGroup) item.getObject());
				names.add(group.getName());
			}
		}
		return names;
	}

	private void fullUpdate() {
		updateStateButton();
		updateGroupsNumberRatio();
		updateList();
	}

	private void updateGroupsNumberRatio() {
		int visibleCount = getVisibleGroupsNumber();
		int totalCount = uiItems.size();
		String description = getString(
				R.string.ltr_or_rtl_combine_via_slash,
				String.valueOf(visibleCount),
				String.valueOf(totalCount)
		);
		sizeIndication.setText(description);
	}

	private void updateList() {
		int defaultIconColor = ColorUtilities.getDefaultIconColor(app, nightMode);
		for (SelectableItem item : uiItems) {
			View view = listViews.get(item);
			if (view == null) {
				continue;
			}

			GpxDisplayGroup group = item.getObject() instanceof GpxDisplayGroup
					? ((GpxDisplayGroup) item.getObject())
					: null;
			boolean isVisible = group != null && !selectedGpxFile.isGroupHidden(group.getName());
			int iconId = isVisible ? R.drawable.ic_action_folder : R.drawable.ic_action_folder_hidden;
			int iconColor = item.getColor();
			if (iconColor == 0 || !isVisible) {
				iconColor = defaultIconColor;
			}
			ImageView icon = view.findViewById(R.id.icon);
			icon.setImageDrawable(getPaintedIcon(iconId, iconColor));
			CompoundButton cb = view.findViewById(R.id.compound_button);
			cb.setChecked(isVisible);
		}
	}

	private int getVisibleGroupsNumber() {
		int visibleGroupsCount = 0;
		for (SelectableItem selectableItem : uiItems) {
			GpxDisplayGroup group = selectableItem.getObject() instanceof GpxDisplayGroup
					? ((GpxDisplayGroup) selectableItem.getObject())
					: null;
			if (group != null && !selectedGpxFile.isGroupHidden(group.getName())) {
				visibleGroupsCount++;
			}
		}
		return visibleGroupsCount;
	}

	private boolean isAnyGroupVisible() {
		return getVisibleGroupsNumber() > 0;
	}

	@Override
	protected int getDismissButtonTextId() {
		return R.string.shared_string_close;
	}

	public static DisplayGroupsBottomSheet showInstance(@NonNull AppCompatActivity activity,
	                                                    @NonNull Fragment targetFragment,
	                                                    boolean usedOnMap) {
		DisplayGroupsBottomSheet fragment = new DisplayGroupsBottomSheet();
		fragment.setUsedOnMap(usedOnMap);
		fragment.setTargetFragment(targetFragment, 0);
		FragmentManager fm = activity.getSupportFragmentManager();
		fragment.show(fm, TAG);
		return fragment;
	}

	public interface DisplayPointGroupsCallback {

		void onPointGroupsVisibilityChanged();

		SelectedGpxFile getSelectedGpx();

		TrackDisplayHelper getDisplayHelper();

	}
}
