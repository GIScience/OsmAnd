package net.osmand.plus.download;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.view.*;
import android.widget.*;
import net.osmand.access.AccessibleToast;
import net.osmand.map.OsmandRegions;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by Denis
 * on 09.09.2014.
 */
public class UpdatesIndexFragment extends ListFragment {

	private OsmandRegions osmandRegions;
	private java.text.DateFormat format;
	private UpdateIndexAdapter listAdapter;
	private int updateColor;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		format = getMyApplication().getResourceManager().getDateFormat();
		updateColor = getResources().getColor(R.color.color_update);
		osmandRegions = getMyApplication().getResourceManager().getOsmandRegions();
		List<IndexItem> indexItems = new ArrayList<IndexItem>();
		if (BaseDownloadActivity.downloadListIndexThread != null) {
			indexItems =  new ArrayList<IndexItem>(DownloadActivity.downloadListIndexThread.getItemsToUpdate());
		}
		createListView(indexItems);
		setHasOptionsMenu(true);
	}

	private void createListView(List<IndexItem> indexItems) {
		if (indexItems.size() == 0) {
			indexItems.add(new IndexItem(getString(R.string.everything_up_to_date), "", 0, "", 0, 0, null));
		}
		listAdapter = new UpdateIndexAdapter(getDownloadActivity(), R.layout.download_index_list_item, indexItems);
		listAdapter.sort(new Comparator<IndexItem>() {
			@Override
			public int compare(IndexItem indexItem, IndexItem indexItem2) {
				return indexItem.getVisibleName(getMyApplication(), osmandRegions).compareTo(indexItem2.getVisibleName(getMyApplication(), osmandRegions));
			}
		});
		setListAdapter(listAdapter);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public void updateItemsList(List<IndexItem> items) {
		if(listAdapter == null){
			return;
		}

		createListView(new ArrayList<IndexItem>(items));
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final CheckBox ch = (CheckBox) v.findViewById(R.id.check_download_item);
		onItemSelected(ch, position);
	}

	private void onItemSelected(CheckBox ch, int position){
		final IndexItem e = (IndexItem) getListAdapter().getItem(position);
		if (ch.isChecked()) {
			ch.setChecked(!ch.isChecked());
			getDownloadActivity().getEntriesToDownload().remove(e);
			getDownloadActivity().updateDownloadButton(true);
			return;
		}

		List<DownloadEntry> download = e.createDownloadEntry(getMyApplication(), e.getType(), new ArrayList<DownloadEntry>());
		if (download.size() > 0) {
			getDownloadActivity().getEntriesToDownload().put(e, download);
			getDownloadActivity().updateDownloadButton(true);
			ch.setChecked(!ch.isChecked());
		}
	}

	public DownloadActivity getDownloadActivity() {
		return (DownloadActivity) getActivity();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		ActionBar actionBar = getDownloadActivity().getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

		if (getMyApplication().getAppCustomization().showDownloadExtraActions()) {
			MenuItem item = menu.add(0, DownloadIndexFragment.RELOAD_ID, 0, R.string.update_downlod_list);
			item.setIcon(isLightActionBar() ? R.drawable.ic_action_refresh_light :
				R.drawable.ic_action_refresh_dark);
			MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			SubMenu s = menu.addSubMenu(0, DownloadIndexFragment.MORE_ID, 0, R.string.default_buttons_other_actions);
			s.add(0, DownloadIndexFragment.SELECT_ALL_ID, 0, R.string.select_all);
			s.add(0, DownloadIndexFragment.DESELECT_ALL_ID, 0, R.string.deselect_all);

//			s.setIcon(isLightActionBar() ? R.drawable.abs__ic_menu_moreoverflow_holo_light
//					: R.drawable.abs__ic_menu_moreoverflow_holo_dark);
			MenuItemCompat.setShowAsAction(s.getItem(), MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		}
	}

	public OsmandApplication getMyApplication() {
		return getDownloadActivity().getMyApplication();
	}

	public boolean isLightActionBar() {
		return ((OsmandApplication) getActivity().getApplication()).getSettings().isLightActionBar();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == DownloadIndexFragment.RELOAD_ID) {
			// re-create the thread
			DownloadActivity.downloadListIndexThread.runReloadIndexFiles();
			return true;
		} else if (item.getItemId() == DownloadIndexFragment.SELECT_ALL_ID) {
			selectAll();
			return true;
		} else if (item.getItemId() == DownloadIndexFragment.FILTER_EXISTING_REGIONS) {
			filterExisting();
			return true;
		} else if (item.getItemId() == DownloadIndexFragment.DESELECT_ALL_ID) {
			deselectAll();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void selectAll() {
		int selected = 0;
		for (int i = 0; i < listAdapter.getCount(); i++) {
			IndexItem es = listAdapter.getItem(i);
			if (!getDownloadActivity().getEntriesToDownload().containsKey(es)) {
				selected++;
				getDownloadActivity().getEntriesToDownload().put(es, es.createDownloadEntry(getMyApplication(),
						getDownloadActivity().getDownloadType(), new ArrayList<DownloadEntry>(1)));

			}
		}
		AccessibleToast.makeText(getDownloadActivity(), MessageFormat.format(getString(R.string.items_were_selected), selected), Toast.LENGTH_SHORT).show();
		listAdapter.notifyDataSetInvalidated();
		if (selected > 0) {
			getDownloadActivity().updateDownloadButton(true);
		}
	}

	public void deselectAll() {
		DownloadActivity.downloadListIndexThread.getEntriesToDownload().clear();
		listAdapter.notifyDataSetInvalidated();

		getDownloadActivity().findViewById(R.id.DownloadButton).setVisibility(View.GONE);
	}

	private void filterExisting() {
		final Map<String, String> listAlreadyDownloaded =  DownloadActivity.downloadListIndexThread.getDownloadedIndexFileNames();

		final List<IndexItem> filtered = new ArrayList<IndexItem>();
		for (IndexItem fileItem : listAdapter.getIndexFiles()) {
			if(fileItem.isAlreadyDownloaded(listAlreadyDownloaded)){
				filtered.add(fileItem);
			}
		}
		listAdapter.setIndexFiles(filtered);
		listAdapter.notifyDataSetChanged();
	}

	private class UpdateIndexAdapter extends ArrayAdapter<IndexItem> {
		List<IndexItem> items;

		public UpdateIndexAdapter(Context context, int resource, List<IndexItem> items) {
			super(context, resource, items);
			this.items = items;
		}

		public List<IndexItem> getIndexFiles() {
			return items;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View v = convertView;

			if (v == null) {
				LayoutInflater inflater = (LayoutInflater) getDownloadActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.update_index_list_item, null);
			}

			TextView name = (TextView) v.findViewById(R.id.download_item);
			TextView description = (TextView) v.findViewById(R.id.download_descr);
			final CheckBox ch = (CheckBox) v.findViewById(R.id.check_download_item);
			IndexItem e = items.get(position);
			if (e.getFileName() == getString(R.string.everything_up_to_date)){
				name.setText(e.getFileName());
				description.setText("");
				ch.setVisibility(View.INVISIBLE);
				return v;
			} else {
				ch.setVisibility(View.VISIBLE);
			}
			String eName = e.getVisibleName(getMyApplication(), osmandRegions);

			name.setText(eName.trim()); //$NON-NLS-1$
			String d = e.getDate(format) + "\n" + e.getSizeDescription(getMyApplication());
			description.setText(d);

			ch.setChecked(getDownloadActivity().getEntriesToDownload().containsKey(e));
			ch.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ch.setChecked(!ch.isChecked());
					onItemSelected(ch, position);
				}
			});

			if (e.getDate(format) != null) {
				Map<String, String> indexActivatedFileNames = getDownloadActivity().getIndexActivatedFileNames();
				Map<String, String> indexFileNames = getDownloadActivity().getIndexFileNames();

				if (indexActivatedFileNames != null && indexFileNames != null){
					String sfName = e.getTargetFileName();
					final boolean updatableResource = indexActivatedFileNames.containsKey(sfName);
					if (updatableResource && !DownloadActivity.downloadListIndexThread.checkIfItemOutdated(e)) {
						name.setText(name.getText() + "\n" + getResources().getString(R.string.local_index_installed) + " : "
								+ indexActivatedFileNames.get(sfName));
						name.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
					} else if (e.getDate(format).equals(indexFileNames.get(sfName))) {
						name.setText(name.getText() + "\n" + getResources().getString(R.string.local_index_installed) + " : "
								+ indexFileNames.get(sfName));
						name.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
					} else if (updatableResource) {
						name.setText(name.getText() + "\n" + getResources().getString(R.string.local_index_installed) + " : "
								+ indexActivatedFileNames.get(sfName));
						name.setTextColor(updateColor); // LIGHT_BLUE
						name.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
					} else {
						name.setText(name.getText() + "\n" + getResources().getString(R.string.local_index_installed) + " : "
								+ indexFileNames.get(sfName));
						name.setTextColor(updateColor); // LIGHT_BLUE
						name.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
					}
				}
			}


			return v;
		}

		public void setIndexFiles(List<IndexItem> filtered) {
			clear();
			for (IndexItem item : filtered){
				add(item);
			}
			sort(new Comparator<IndexItem>() {
				@Override
				public int compare(IndexItem indexItem, IndexItem indexItem2) {
					return indexItem.getVisibleName(getMyApplication(), osmandRegions).compareTo(indexItem2.getVisibleName(getMyApplication(), osmandRegions));
				}
			});
		}
	}

}
