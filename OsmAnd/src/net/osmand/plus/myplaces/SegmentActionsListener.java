package net.osmand.plus.myplaces;

import android.view.View;

import androidx.annotation.NonNull;

import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.plus.track.helpers.GpxSelectionHelper.GpxDisplayItem;

public interface SegmentActionsListener {

	void updateContent();

	void onChartTouch();

	void scrollBy(int px);

	void onPointSelected(TrkSegment segment, double lat, double lon);

	void openSplitInterval(GpxDisplayItem gpxItem, TrkSegment trkSegment);

	void showOptionsPopupMenu(View view, TrkSegment trkSegment, boolean confirmDeletion, GpxDisplayItem gpxItem);

	void openAnalyzeOnMap(@NonNull GpxDisplayItem gpxItem);
}
