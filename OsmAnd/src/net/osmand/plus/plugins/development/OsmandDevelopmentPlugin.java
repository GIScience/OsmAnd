package net.osmand.plus.plugins.development;

import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_BUILDS_ID;
import static net.osmand.aidlapi.OsmAndCustomizationConstants.PLUGIN_OSMAND_DEV;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.activities.ContributionVersionActivity;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dashboard.tools.DashFragmentData;
import net.osmand.plus.settings.fragments.BaseSettingsFragment.SettingsScreenType;
import net.osmand.plus.views.layers.base.OsmandMapLayer.DrawSettings;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget;

public class OsmandDevelopmentPlugin extends OsmandPlugin {

	public OsmandDevelopmentPlugin(OsmandApplication app) {
		super(app);
		//ApplicationMode.regWidgetVisibility("fps", new ApplicationMode[0]);
	}

	@Override
	public String getId() {
		return PLUGIN_OSMAND_DEV;
	}

	@Override
	public CharSequence getDescription() {
		return app.getString(R.string.osmand_development_plugin_description);
	}

	@Override
	public String getName() {
		return app.getString(R.string.debugging_and_development);
	}

	@Override
	public String getHelpFileName() {
		return "feature_articles/development_plugin.html";
	}

	@Override
	public void registerLayers(@NonNull Context context, @Nullable MapActivity mapActivity) {
		if (mapActivity != null) {
			registerWidget(mapActivity);
		}
	}

	@Override
	public void registerOptionsMenuItems(final MapActivity mapActivity, ContextMenuAdapter helper) {
		if (Version.isDeveloperVersion(mapActivity.getMyApplication())) {
			helper.addItem(new ContextMenuItem.ItemBuilder()
					.setId(DRAWER_BUILDS_ID)
					.setTitleId(R.string.version_settings, mapActivity)
					.setIcon(R.drawable.ic_action_apk)
					.setListener(new ContextMenuAdapter.ItemClickListener() {
						@Override
						public boolean onContextMenuClick(ArrayAdapter<ContextMenuItem> adapter, int itemId, int pos, boolean isChecked, int[] viewCoordinates) {
							final Intent mapIntent = new Intent(mapActivity, ContributionVersionActivity.class);
							mapActivity.startActivityForResult(mapIntent, 0);
							return true;
						}
					}).createItem());
		}

	}

	@Override
	public void updateLayers(@NonNull Context context, @Nullable MapActivity mapActivity) {
		if (mapActivity != null) {
			if (isActive()) {
				registerWidget(mapActivity);
			} else {
				MapInfoLayer mapInfoLayer = mapActivity.getMapLayers().getMapInfoLayer();
				if (mapInfoLayer != null && mapInfoLayer.getSideWidget(FPSTextInfoWidget.class) != null) {
					mapInfoLayer.removeSideWidget(mapInfoLayer.getSideWidget(FPSTextInfoWidget.class));
					mapInfoLayer.recreateControls();
				}
			}
		}
	}

	public static class FPSTextInfoWidget extends TextInfoWidget {

		private OsmandMapTileView mv;

		public FPSTextInfoWidget(OsmandMapTileView mv, Activity activity) {
			super(activity);
			this.mv = mv;
		}

		@Override
		public boolean updateInfo(DrawSettings drawSettings) {
			if (!mv.isMeasureFPS()) {
				mv.setMeasureFPS(true);
			}
			setText("", Integer.toString((int) mv.getFPS()) + "/"
					+ Integer.toString((int) mv.getSecondaryFPS())
					+ " FPS");
			return true;
		}
	}


	private void registerWidget(@NonNull MapActivity activity) {
		MapInfoLayer mapInfoLayer = activity.getMapLayers().getMapInfoLayer();
		final OsmandMapTileView mv = activity.getMapView();
		if (mapInfoLayer != null && mapInfoLayer.getSideWidget(FPSTextInfoWidget.class) == null) {
			FPSTextInfoWidget fps = new FPSTextInfoWidget(mv, activity);
			fps.setIcons(R.drawable.widget_fps_day, R.drawable.widget_fps_night);
			mapInfoLayer.registerSideWidget(fps, R.drawable.ic_action_fps,
					R.string.map_widget_fps_info, "fps", false, 50);
			mapInfoLayer.recreateControls();
		}
	}

	@Override
	public SettingsScreenType getSettingsScreenType() {
		return SettingsScreenType.DEVELOPMENT_SETTINGS;
	}

	@Override
	public int getLogoResourceId() {
		return R.drawable.ic_action_laptop;
	}

	@Override
	public Drawable getAssetResourceImage() {
		return app.getUIUtilities().getIcon(R.drawable.osmand_development);
	}

	@Override
	public DashFragmentData getCardFragment() {
		return DashSimulateFragment.FRAGMENT_DATA;
	}

	@Override
	public void disable(OsmandApplication app) {
		if (app.getSettings().OSM_USE_DEV_URL.get()) {
			app.getSettings().OSM_USE_DEV_URL.set(false);
			app.getOsmOAuthHelper().resetAuthorization();
		}
		super.disable(app);
	}
}