package net.osmand.plus.settings.backend;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;

import net.osmand.IProgress;
import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.aidl.ConnectedApp;
import net.osmand.data.LocationPoint;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.Version;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.download.DownloadActivity;
import net.osmand.plus.helpers.WaypointHelper;
import net.osmand.plus.importfiles.ImportHelper;
import net.osmand.plus.myplaces.FavoritesActivity;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.JsonUtils;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_ITEM_ID_SCHEME;

public class OsmAndAppCustomization {

	private static final int MAX_NAV_DRAWER_ITEMS_PER_APP = 3;

	private static final Log LOG = PlatformUtil.getLog(OsmAndAppCustomization.class);

	protected OsmandApplication app;
	protected OsmandSettings osmandSettings;

	private Map<String, Bitmap> navDrawerLogos = new HashMap<>();

	private String navDrawerFooterIntent;
	private String navDrawerFooterAppName;
	private String navDrawerFooterPackageName;

	private Set<String> featuresEnabledIds = new HashSet<>();
	private Set<String> featuresDisabledIds = new HashSet<>();
	private Set<String> featuresEnabledPatterns = new HashSet<>();
	private Set<String> featuresDisabledPatterns = new HashSet<>();
	private Set<ApplicationMode> marginAppModeUsage = new HashSet<>();
	private Map<String, Set<ApplicationMode>> widgetsVisibilityMap = new LinkedHashMap<>();
	private Map<String, Set<ApplicationMode>> widgetsAvailabilityMap = new LinkedHashMap<>();
	private CustomOsmandSettings customOsmandSettings;

	private int marginLeft;
	private int marginTop;
	private int marginRight;
	private int marginBottom;

	private boolean featuresCustomized;
	private boolean widgetsCustomized;

	private List<OsmAndAppCustomizationListener> listeners = new ArrayList<>();

	public interface OsmAndAppCustomizationListener {

		void onOsmAndSettingsCustomized();
	}

	public static class CustomOsmandSettings {
		private String sharedPreferencesName;
		private OsmandSettings settings;

		CustomOsmandSettings(OsmandApplication app, String sharedPreferencesName, Bundle bundle) {
			this.sharedPreferencesName = sharedPreferencesName;
			this.settings = new OsmandSettings(app, new net.osmand.plus.api.SettingsAPIImpl(app), sharedPreferencesName);
			if (bundle != null) {
				for (String key : bundle.keySet()) {
					Object object = bundle.get(key);
					this.settings.setPreference(key, object);
				}
			}
		}

		public OsmandSettings getSettings() {
			return settings;
		}
	}

	public void setup(OsmandApplication app) {
		this.app = app;
		this.osmandSettings = new OsmandSettings(app, new net.osmand.plus.api.SettingsAPIImpl(app));
	}

	public OsmandSettings getOsmandSettings() {
		return customOsmandSettings != null ? customOsmandSettings.getSettings() : osmandSettings;
	}

	public void customizeOsmandSettings(@NonNull String sharedPreferencesName, @Nullable Bundle bundle) {
		customOsmandSettings = new CustomOsmandSettings(app, sharedPreferencesName, bundle);
		OsmandSettings newSettings = customOsmandSettings.getSettings();
		if (Build.VERSION.SDK_INT < 19) {
			if (osmandSettings.isExternalStorageDirectorySpecifiedPre19()) {
				File externalStorageDirectory = osmandSettings.getExternalStorageDirectoryPre19();
				newSettings.setExternalStorageDirectoryPre19(externalStorageDirectory.getAbsolutePath());
			}
		} else if (osmandSettings.isExternalStorageDirectoryTypeSpecifiedV19()
				&& osmandSettings.isExternalStorageDirectorySpecifiedV19()) {
			int type = osmandSettings.getExternalStorageDirectoryTypeV19();
			String directory = osmandSettings.getExternalStorageDirectoryV19();
			newSettings.setExternalStorageDirectoryV19(type, directory);
		}
		app.setOsmandSettings(newSettings);
		notifySettingsCustomized();
	}

	public void restoreOsmandSettings() {
		app.setOsmandSettings(osmandSettings);
		notifySettingsCustomized();
	}

	public boolean restoreOsmand() {
		featuresCustomized = false;
		widgetsCustomized = false;
		customOsmandSettings = null;
		marginLeft = 0;
		marginTop = 0;
		marginRight = 0;
		marginBottom = 0;
		restoreOsmandSettings();

		featuresEnabledIds.clear();
		featuresDisabledIds.clear();
		featuresEnabledPatterns.clear();
		featuresDisabledPatterns.clear();
		widgetsVisibilityMap.clear();
		widgetsAvailabilityMap.clear();
		marginAppModeUsage.clear();

		return true;
	}

	// Activities

	public Class<MapActivity> getMapActivity() {
		return MapActivity.class;
	}

	public Class<FavoritesActivity> getFavoritesActivity() {
		return FavoritesActivity.class;
	}

	public Class<? extends Activity> getDownloadIndexActivity() {
		return DownloadActivity.class;
	}

	public Class<? extends Activity> getDownloadActivity() {
		return DownloadActivity.class;
	}

	public List<String> onIndexingFiles(@Nullable IProgress progress, @NonNull Map<String, String> indexFileNames) {
		return Collections.emptyList();
	}

	public String getIndexesUrl() {
		return "https://" + IndexConstants.INDEX_DOWNLOAD_DOMAIN + "/get_indexes?gzip&" + Version.getVersionAsURLParam(app);
	}

	public boolean showDownloadExtraActions() {
		return true;
	}

	public File getTracksDir() {
		return app.getAppPath(IndexConstants.GPX_RECORDED_INDEX_DIR);
	}

	public void createLayers(@NonNull Context context, @Nullable MapActivity mapActivity) {
	}

	public List<? extends LocationPoint> getWaypoints() {
		return Collections.emptyList();
	}

	public boolean isWaypointGroupVisible(int waypointType, RouteCalculationResult route) {
		if (waypointType == WaypointHelper.ALARMS) {
			return route != null && !route.getAlarmInfo().isEmpty();
		} else if (waypointType == WaypointHelper.WAYPOINTS) {
			return route != null && !route.getLocationPoints().isEmpty();
		}
		return true;
	}


	@Nullable
	public Bitmap getNavDrawerLogo() {
		ApplicationMode mode = app.getSettings().APPLICATION_MODE.get();
		Bitmap drawerLogo = navDrawerLogos.get(mode.getStringKey());
		if (drawerLogo == null) {
			String logoFileName = app.getSettings().NAV_DRAWER_LOGO.get();
			if (!Algorithms.isEmpty(logoFileName)) {
				try {
					JSONObject json = new JSONObject(logoFileName);
					for (Iterator<String> it = json.keys(); it.hasNext(); ) {
						String iconPath = json.getString(it.next());

						File iconFile = app.getAppPath(iconPath);
						if (iconFile.exists()) {
							drawerLogo = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
							navDrawerLogos.put(mode.getStringKey(), drawerLogo);
							break;
						}
					}
				} catch (JSONException e) {
					LOG.error("Failed to read json", e);
				}
			}
		}

		return drawerLogo;
	}

	@Nullable
	public String getNavDrawerLogoUrl() {
		String url = app.getSettings().NAV_DRAWER_URL.get();
		try {
			JSONObject json = new JSONObject(url);
			Map<String, String> localizedMap = JsonUtils.getLocalizedMapFromJson(json);
			url = JsonUtils.getLocalizedResFromMap(app, localizedMap, url);
		} catch (JSONException e) {
			LOG.error(e);
		}
		return url;
	}

	public boolean setNavDrawerLogo(String uri, @Nullable String packageName, @Nullable String intent) {
		String connectedAppDirPath = IndexConstants.PLUGINS_DIR + packageName;
		File connectedAppDir = app.getAppPath(connectedAppDirPath);
		if (TextUtils.isEmpty(uri)) {
			app.getSettings().NAV_DRAWER_LOGO.resetToDefault();
			Algorithms.removeAllFiles(connectedAppDir);
		} else {
			try {
				Uri fileUri = Uri.parse(uri);
				InputStream is = app.getContentResolver().openInputStream(fileUri);
				if (is != null) {
					String iconName = ImportHelper.getNameFromContentUri(app, fileUri);
					if (!connectedAppDir.exists()) {
						connectedAppDir.mkdirs();
					}
					OutputStream fout = null;
					if (!Algorithms.isEmpty(iconName)) {
						fout = new FileOutputStream(new File(connectedAppDir, iconName));
					}
					try {
						if (fout != null) {
							Algorithms.streamCopy(is, fout);
						}
					} finally {
						Algorithms.closeStream(is);
						Algorithms.closeStream(fout);
					}
					JSONObject json = new JSONObject();
					json.put("", connectedAppDirPath + "/" + iconName);
					app.getSettings().NAV_DRAWER_LOGO.set(json.toString());
				}
			} catch (FileNotFoundException e) {
				LOG.error(e);
				return false;
			} catch (JSONException e) {
				LOG.error("Failed to read json", e);
			} catch (IOException e) {
				LOG.error("Failed to write file", e);
			}
			if (!Algorithms.isEmpty(intent)) {
				app.getSettings().NAV_DRAWER_LOGO.set(intent);
			}
		}
		return true;
	}

	public boolean setNavDrawerFooterParams(String uri, @Nullable String packageName, @Nullable String intent) {
		navDrawerFooterAppName = uri;
		navDrawerFooterIntent = intent;
		navDrawerFooterPackageName = packageName;
		return true;
	}

	public String getNavFooterAppName() {
		return navDrawerFooterAppName;
	}

	public void setFeaturesEnabledIds(@NonNull Collection<String> ids) {
		featuresEnabledIds.clear();
		featuresEnabledIds.addAll(ids);
		setFeaturesCustomized();
	}

	public void setFeaturesDisabledIds(@NonNull Collection<String> ids) {
		featuresDisabledIds.clear();
		featuresDisabledIds.addAll(ids);
		setFeaturesCustomized();
	}

	public void setFeaturesEnabledPatterns(@NonNull Collection<String> patterns) {
		featuresEnabledPatterns.clear();
		featuresEnabledPatterns.addAll(patterns);
		setFeaturesCustomized();
	}

	public void setFeaturesDisabledPatterns(@NonNull Collection<String> patterns) {
		featuresDisabledPatterns.clear();
		featuresDisabledPatterns.addAll(patterns);
		setFeaturesCustomized();
	}

	public Set<ApplicationMode> regWidgetVisibility(@NonNull String widgetId, @Nullable List<String> appModeKeys) {
		HashSet<ApplicationMode> set = getAppModesSet(appModeKeys);
		widgetsVisibilityMap.put(widgetId, set);
		setWidgetsCustomized();
		return set;
	}

	public Set<ApplicationMode> regWidgetAvailability(@NonNull String widgetId, @Nullable List<String> appModeKeys) {
		HashSet<ApplicationMode> set = getAppModesSet(appModeKeys);
		widgetsAvailabilityMap.put(widgetId, set);
		setWidgetsCustomized();
		return set;
	}

	public void setMapMargins(int left, int top, int right, int bottom, List<String> appModeKeys) {
		marginLeft = left;
		marginTop = top;
		marginRight = right;
		marginBottom = bottom;
		marginAppModeUsage.addAll(getAppModesSet(appModeKeys));
	}

	public void updateMapMargins(MapActivity mapActivity) {
		if (isMapMarginAvailable()) {
			mapActivity.setMargins(marginLeft, marginTop, marginRight, marginBottom);
		} else {
			mapActivity.setMargins(0, 0, 0, 0);
		}
	}

	boolean isMapMarginAvailable() {
		return marginAppModeUsage.contains(app.getSettings().getApplicationMode());
	}

	public boolean isWidgetVisible(@NonNull String key, ApplicationMode appMode) {
		Set<ApplicationMode> set = widgetsVisibilityMap.get(key);
		if (set == null) {
			return false;
		}
		return set.contains(appMode);
	}

	public boolean isWidgetAvailable(@NonNull String key, ApplicationMode appMode) {
		Set<ApplicationMode> set = widgetsAvailabilityMap.get(key);
		if (set == null) {
			return true;
		}
		return set.contains(appMode);
	}

	public boolean setNavDrawerLogoWithParams(String imageUri, @Nullable String packageName, @Nullable String intent) {
		return setNavDrawerLogo(imageUri, packageName, intent);
	}

	public boolean changePluginStatus(String pluginId, int newState) {
		if (newState == 0) {
			for (OsmandPlugin plugin : OsmandPlugin.getEnabledPlugins()) {
				if (plugin.getId().equals(pluginId)) {
					OsmandPlugin.enablePlugin(null, app, plugin, false);
				}
			}
			return true;
		}

		if (newState == 1) {
			for (OsmandPlugin plugin : OsmandPlugin.getAvailablePlugins()) {
				if (plugin.getId().equals(pluginId)) {
					OsmandPlugin.enablePlugin(null, app, plugin, true);
				}
			}
			return true;
		}

		return false;
	}

	public boolean setNavDrawerItems(String appPackage, List<NavDrawerItem> items) {
		if (!TextUtils.isEmpty(appPackage) && items != null) {
			clearNavDrawerItems(appPackage);
			if (items.isEmpty()) {
				return true;
			}
			List<NavDrawerItem> newItems = new ArrayList<>(MAX_NAV_DRAWER_ITEMS_PER_APP);
			boolean success = true;
			for (int i = 0; i < items.size() && i <= MAX_NAV_DRAWER_ITEMS_PER_APP; i++) {
				NavDrawerItem item = items.get(i);
				if (!TextUtils.isEmpty(item.name) && !TextUtils.isEmpty(item.uri)) {
					newItems.add(item);
				} else {
					success = false;
					break;
				}
			}
			if (success) {
				saveNavDrawerItems(appPackage, newItems);
			}
			return success;
		}
		return false;
	}

	private void clearNavDrawerItems(String appPackage) {
		try {
			JSONObject allItems = new JSONObject(app.getSettings().API_NAV_DRAWER_ITEMS_JSON.get());
			allItems.put(appPackage, new JSONArray());
			app.getSettings().API_NAV_DRAWER_ITEMS_JSON.set(allItems.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void saveNavDrawerItems(String appPackage, List<NavDrawerItem> items) {
		try {
			JSONArray jArray = new JSONArray();
			for (NavDrawerItem item : items) {
				JSONObject obj = new JSONObject();
				obj.put(NavDrawerItem.NAME_KEY, item.name);
				obj.put(NavDrawerItem.URI_KEY, item.uri);
				obj.put(NavDrawerItem.ICON_NAME_KEY, item.iconName);
				obj.put(NavDrawerItem.FLAGS_KEY, item.flags);
				jArray.put(obj);
			}
			JSONObject allItems = new JSONObject(app.getSettings().API_NAV_DRAWER_ITEMS_JSON.get());
			allItems.put(appPackage, jArray);
			app.getSettings().API_NAV_DRAWER_ITEMS_JSON.set(allItems.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void registerNavDrawerItems(final Activity activity, ContextMenuAdapter adapter) {
		PackageManager pm = activity.getPackageManager();
		for (Map.Entry<String, List<NavDrawerItem>> entry : getNavDrawerItems().entrySet()) {
			String appPackage = entry.getKey();
			for (NavDrawerItem item : entry.getValue()) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.uri));
				if (intent.resolveActivity(pm) == null) {
					intent = pm.getLaunchIntentForPackage(appPackage);
				}
				if (intent != null) {
					if (item.flags != -1) {
						intent.addFlags(item.flags);
					}
					final Intent finalIntent = intent;
					int iconId = AndroidUtils.getDrawableId(app, item.iconName);
					adapter.addItem(new ContextMenuItem.ItemBuilder()
							.setId(item.getId())
							.setTitle(item.name)
							.setIcon(iconId != 0 ? iconId : ContextMenuItem.INVALID_ID)
							.setListener(new ContextMenuAdapter.ItemClickListener() {
								@Override
								public boolean onContextMenuClick(ArrayAdapter<ContextMenuItem> adapter, int itemId, int position, boolean isChecked, int[] viewCoordinates) {
									activity.startActivity(finalIntent);
									return true;
								}
							})
							.createItem());
				}
			}
		}
	}

	private Map<String, List<NavDrawerItem>> getNavDrawerItems() {
		Map<String, List<NavDrawerItem>> res = new LinkedHashMap<>();
		try {
			JSONObject allItems = new JSONObject(app.getSettings().API_NAV_DRAWER_ITEMS_JSON.get());
			for (Iterator<?> it = allItems.keys(); it.hasNext(); ) {
				String appPackage = (String) it.next();
				ConnectedApp connectedApp = app.getAidlApi().getConnectedApp(appPackage);
				if (connectedApp != null && connectedApp.isEnabled()) {
					JSONArray jArray = allItems.getJSONArray(appPackage);
					List<NavDrawerItem> list = new ArrayList<>();
					for (int i = 0; i < jArray.length(); i++) {
						JSONObject obj = jArray.getJSONObject(i);
						list.add(new NavDrawerItem(
								obj.optString(NavDrawerItem.NAME_KEY),
								obj.optString(NavDrawerItem.URI_KEY),
								obj.optString(NavDrawerItem.ICON_NAME_KEY),
								obj.optInt(NavDrawerItem.FLAGS_KEY, -1)
						));
					}
					res.put(appPackage, list);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return res;
	}

	@NonNull
	private HashSet<ApplicationMode> getAppModesSet(@Nullable List<String> appModeKeys) {
		HashSet<ApplicationMode> set = new HashSet<>();
		List<ApplicationMode> values = ApplicationMode.allPossibleValues();
		if (appModeKeys == null) {
			set.addAll(values);
		} else {
			for (String key : appModeKeys) {
				ApplicationMode am = ApplicationMode.valueOfStringKey(key, null);
				if (am != null) {
					set.add(am);
				}
			}
		}
		for (ApplicationMode m : values) {
			// add derived modes
			if (set.contains(m.getParent())) {
				set.add(m);
			}
		}
		return set;
	}

	public boolean isFeatureEnabled(@NonNull String id) {
		if (!featuresCustomized) {
			return true;
		}
		if (featuresEnabledIds.contains(id)) {
			return true;
		}
		if (featuresDisabledIds.contains(id)) {
			return false;
		}
		if (isMatchesPattern(id, featuresEnabledPatterns)) {
			return true;
		}
		return !isMatchesPattern(id, featuresDisabledPatterns);
	}

	public boolean isOsmandCustomized() {
		return areWidgetsCustomized() || areFeaturesCustomized() || areSettingsCustomized();
	}

	public boolean areWidgetsCustomized() {
		return widgetsCustomized;
	}

	public boolean areFeaturesCustomized() {
		return featuresCustomized;
	}

	public boolean areSettingsCustomized() {
		return customOsmandSettings != null;
	}

	public boolean areSettingsCustomizedForPreference(String sharedPreferencesName) {
		if (customOsmandSettings != null && customOsmandSettings.sharedPreferencesName.equals(sharedPreferencesName)) {
			return true;
		}
		return OsmandSettings.areSettingsCustomizedForPreference(sharedPreferencesName, app);
	}

	private void setFeaturesCustomized() {
		featuresCustomized = true;
	}

	private void setWidgetsCustomized() {
		widgetsCustomized = true;
	}

	private boolean isMatchesPattern(@NonNull String id, @NonNull Set<String> patterns) {
		for (String pattern : patterns) {
			if (id.startsWith(pattern)) {
				return true;
			}
		}
		return false;
	}

	private void notifySettingsCustomized() {
		app.runInUIThread(new Runnable() {

			@Override
			public void run() {
				for (OsmAndAppCustomizationListener l : listeners) {
					l.onOsmAndSettingsCustomized();
				}
			}
		});
	}

	public void addListener(OsmAndAppCustomizationListener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(OsmAndAppCustomizationListener listener) {
		this.listeners.remove(listener);
	}

	public static class NavDrawerItem {

		static final String NAME_KEY = "name";
		static final String URI_KEY = "uri";
		static final String ICON_NAME_KEY = "icon_name";
		static final String FLAGS_KEY = "flags";

		private String name;
		private String uri;
		private String iconName;
		private int flags;

		public NavDrawerItem(String name, String uri, String iconName, int flags) {
			this.name = name;
			this.uri = uri;
			this.iconName = iconName;
			this.flags = flags;
		}

		public String getId() {
			return DRAWER_ITEM_ID_SCHEME + name;
		}
	}
}